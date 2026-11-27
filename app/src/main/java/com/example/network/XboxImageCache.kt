package com.example.network

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Ultra-lightweight disk + memory thumbnail cache for Xbox game box art, achievement icons, and gamerpics.
 * Downscales images to compact JPEG files (<= 192px, ~8-15 KB each) inside cacheDir/xbox_art
 * so Galaxy A22 / Tab A7 devices use minimal storage and RAM with zero third-party image libraries.
 */
object XboxImageCache {

    private const val CACHE_DIR_NAME = "xbox_art"
    private const val MAX_DISK_FILES = 40

    private val memoryCache = object : LruCache<String, ImageBitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 2).coerceAtLeast(1024)
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    fun memoryLookup(key: String): ImageBitmap? = memoryCache.get(key)

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    fun trimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            memoryCache.evictAll()
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            memoryCache.trimToSize(memoryCache.maxSize() / 4)
        } else {
            memoryCache.trimToSize(memoryCache.maxSize() / 2)
        }
    }

    suspend fun loadOrFetchSmall(
        context: Context,
        url: String,
        cacheKey: String,
        maxDimPx: Int = 192
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank() || cacheKey.isBlank()) return@withContext null
        val safeKey = sanitizeKey(cacheKey)
        memoryCache.get(safeKey)?.let { return@withContext it }

        val dir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val file = File(dir, "$safeKey.jpg")

        if (file.exists() && file.length() > 100L) {
            decodeFile(file, maxDimPx)?.let { bmp ->
                val img = bmp.asImageBitmap()
                memoryCache.put(safeKey, img)
                return@withContext img
            }
        }

        val normalizedUrl = normalizeXboxImageUrl(url)
        val sizedUrl = buildSmallStoreImageUrl(normalizedUrl, maxDimPx)
        val decodedBytes = fetchBytesWithFallback(sizedUrl, normalizedUrl) ?: return@withContext null

        val decoded = decodeDownsampled(decodedBytes, maxDimPx) ?: return@withContext null
        val scaled = scaleDownIfNeeded(decoded, maxDimPx)

        runCatching {
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            if (scaled !== decoded) decoded.recycle()
            pruneCacheIfNeeded(dir)
        }

        val img = scaled.asImageBitmap()
        memoryCache.put(safeKey, img)
        img
    }

    private fun fetchBytesWithFallback(primaryUrl: String, fallbackUrl: String): ByteArray? {
        downloadBytes(primaryUrl)?.let { return it }
        if (fallbackUrl != primaryUrl) {
            return downloadBytes(fallbackUrl)
        }
        return null
    }

    private fun downloadBytes(targetUrl: String): ByteArray? {
        val req = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-A225F) AppleWebKit/537.36 (KHTML, like Gecko) Mobile")
            .header("Accept", "image/webp,image/jpeg,image/png,*/*")
            .get()
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.bytes()?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    internal fun normalizeXboxImageUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""
        return when {
            trimmed.startsWith("http://images-eds.xboxlive.com", ignoreCase = true) ->
                "https://images-eds-ssl.xboxlive.com" + trimmed.substring("http://images-eds.xboxlive.com".length)
            trimmed.startsWith("http://", ignoreCase = true) ->
                "https://" + trimmed.substring("http://".length)
            else -> trimmed
        }
    }

    private fun buildSmallStoreImageUrl(url: String, maxDimPx: Int): String {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return trimmed
        if (trimmed.contains("store-images.s-microsoft.com", ignoreCase = true) && !trimmed.contains("w=")) {
            val sep = if (trimmed.contains("?")) "&" else "?"
            return "$trimmed${sep}w=$maxDimPx&h=$maxDimPx&q=80"
        }
        if (trimmed.contains("images-eds-ssl.xboxlive.com", ignoreCase = true) && !trimmed.contains("w=")) {
            // images-eds-ssl Akamai image server only accepts standard buckets (64, 208, 424) and rejects q=
            val bucket = if (maxDimPx <= 64) 64 else 208
            val sep = if (trimmed.contains("?")) "&" else "?"
            return "$trimmed${sep}w=$bucket&h=$bucket&format=png"
        }
        return trimmed
    }

    private fun decodeFile(file: File, maxDimPx: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    private fun decodeDownsampled(bytes: ByteArray, maxDimPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun computeSampleSize(width: Int, height: Int, targetMax: Int): Int {
        var sample = 1
        val maxOrig = maxOf(width, height)
        while ((maxOrig / (sample * 2)) >= targetMax) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimPx: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimPx) return bitmap
        val ratio = maxDimPx.toFloat() / maxSide.toFloat()
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun pruneCacheIfNeeded(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_DISK_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_DISK_FILES)
            .forEach { it.delete() }
    }

    private fun sanitizeKey(raw: String): String {
        return raw.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)
    }
}

@Composable
fun rememberCachedXboxImage(
    url: String,
    cacheKey: String,
    maxDimPx: Int = 192
): ImageBitmap? {
    val context = LocalContext.current
    val safeKey = remember(cacheKey) {
        cacheKey.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)
    }
    var image by remember(url, safeKey) {
        mutableStateOf(if (safeKey.isNotBlank()) XboxImageCache.memoryLookup(safeKey) else null)
    }

    LaunchedEffect(url, safeKey) {
        if (url.isNotBlank() && safeKey.isNotBlank()) {
            val loaded = XboxImageCache.loadOrFetchSmall(
                context = context,
                url = url,
                cacheKey = safeKey,
                maxDimPx = maxDimPx
            )
            if (loaded != null) {
                image = loaded
            }
        }
    }
    return image
}
