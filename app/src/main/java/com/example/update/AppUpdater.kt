package com.example.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object AppUpdater {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/mo-innovations/Project-X-BETA/releases/latest"
    private const val UPDATES_SUBDIR = "updates"
    private const val UPDATE_APK_NAME = "projectx-update.apk"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun checkForUpdates(
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Project-X-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@runCatching UpdateCheckResult.Error(
                        "Update check failed (HTTP ${response.code})"
                    )
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@runCatching UpdateCheckResult.Error("Empty response from GitHub")
                }

                val root = JSONObject(body)
                val tagName = root.optString("tag_name").trim()
                val cleanRemote = normalizeVersion(tagName)
                if (cleanRemote.isBlank()) {
                    return@runCatching UpdateCheckResult.Error("No release tag found")
                }

                if (!isNewerVersion(cleanRemote, normalizeVersion(currentVersion))) {
                    return@runCatching UpdateCheckResult.UpToDate
                }

                val assets = root.optJSONArray("assets") ?: JSONArray()
                var apkUrl = ""
                var apkSize = 0L
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    val downloadUrl = asset.optString("browser_download_url")
                    if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                        apkUrl = downloadUrl
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }

                if (apkUrl.isBlank()) {
                    return@runCatching UpdateCheckResult.Error(
                        "Release $tagName has no APK asset yet"
                    )
                }

                UpdateCheckResult.UpdateAvailable(
                    ReleaseInfo(
                        tagName = tagName,
                        versionName = cleanRemote,
                        apkDownloadUrl = apkUrl,
                        apkSizeBytes = apkSize
                    )
                )
            }
        }.getOrElse { err ->
            UpdateCheckResult.Error(err.message ?: "Could not check for updates")
        }
    }

    suspend fun downloadApk(
        context: Context,
        release: ReleaseInfo,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, UPDATES_SUBDIR).apply { mkdirs() }
            val targetFile = File(updatesDir, UPDATE_APK_NAME)
            val tempFile = File(updatesDir, "$UPDATE_APK_NAME.tmp")
            if (tempFile.exists()) tempFile.delete()

            val request = Request.Builder()
                .url(release.apkDownloadUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "Project-X-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("APK download failed (HTTP ${response.code})")
                }
                val body = response.body ?: error("Empty APK response")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastReportedPct = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0L) {
                                val fraction = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                val pct = (fraction * 100f).toInt()
                                if (pct != lastReportedPct) {
                                    lastReportedPct = pct
                                    withContext(Dispatchers.Main) {
                                        onProgress(fraction)
                                    }
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            withContext(Dispatchers.Main) {
                onProgress(1f)
            }
            targetFile
        }
    }

    fun installUpdate(context: Context, apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists() && apkFile.length() > 0L) { "Update APK file not found" }
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    internal fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V")
    }

    internal fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = normalizeVersion(remote)
            .split(".", "-", "_")
            .mapNotNull { it.toIntOrNull() }
        val currentParts = normalizeVersion(current)
            .split(".", "-", "_")
            .mapNotNull { it.toIntOrNull() }
        if (remoteParts.isEmpty()) return false
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
