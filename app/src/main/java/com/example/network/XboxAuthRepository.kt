package com.example.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.model.AuthState
import com.example.model.DeviceCodeSession
import com.example.model.SavedAccountSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Manages Microsoft XAL / Live OAuth2 device-code authentication,
 * XSTS token exchange (xHome GSSV + Xbox Live Profile/Achievements/TitleHub),
 * and multi-account EncryptedSharedPreferences persistence.
 */
class XboxAuthRepository(
    context: Context,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    prefsOverride: SharedPreferences? = null
) {
    private val prefs: SharedPreferences by lazy {
        prefsOverride ?: createEncryptedPrefs(context.applicationContext)
    }

    private val pollMutex = Mutex()
    private val pendingOAuthByCode = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    @Volatile
    private var completedDeviceCode: String? = null

    @Volatile
    private var completedSignIn: AuthState.SignedIn? = null

    private data class StoredAccountRecord(
        val xuid: String,
        val gamertag: String,
        val userHash: String,
        val gamerscore: String,
        val avatarUrl: String,
        val refreshToken: String,
        val gsToken: String,
        val xblAuthHeader: String,
        val xhomeBaseUri: String
    ) {
        fun toSignedIn(): AuthState.SignedIn = AuthState.SignedIn(
            gamertag = gamertag.ifBlank { "Xbox Profile" },
            userHash = userHash,
            xuid = xuid,
            gamerscore = gamerscore,
            avatarUrl = avatarUrl,
            hasStreamingToken = gsToken.isNotBlank()
        )

        fun toSummary(): SavedAccountSummary = SavedAccountSummary(
            xuid = xuid,
            gamertag = gamertag.ifBlank { "Xbox Profile" },
            gamerscore = gamerscore,
            avatarUrl = avatarUrl,
            hasStreamingToken = gsToken.isNotBlank()
        )
    }

    fun loadSavedAuthState(): AuthState {
        migrateLegacySingleAccountIfNeeded()
        val active = getActiveRecord()
        if (active != null && (active.userHash.isNotBlank() || active.gamertag.isNotBlank())) {
            return active.toSignedIn()
        }
        val first = loadAccountRecords().firstOrNull()
        return first?.toSignedIn() ?: AuthState.SignedOut
    }

    fun loadSavedAccounts(): List<SavedAccountSummary> {
        migrateLegacySingleAccountIfNeeded()
        return loadAccountRecords().map { it.toSummary() }
    }

    fun getStreamingToken(): String? = getActiveRecord()?.gsToken?.ifBlank { null }
        ?: prefs.getString(KEY_GS_TOKEN, null)

    fun getXHomeBaseUrl(): String =
        getActiveRecord()?.xhomeBaseUri?.takeIf { it.startsWith("https://") }
            ?: prefs.getString(KEY_XHOME_BASE_URI, null)?.takeIf { it.startsWith("https://") }
            ?: XHomeSignalingClient.DEFAULT_BASE_URL

    fun getXblAuthHeader(): String? = getActiveRecord()?.xblAuthHeader?.ifBlank { null }
        ?: prefs.getString(KEY_XBL_AUTH_HEADER, null)

    fun getSavedXuid(): String = getActiveRecord()?.xuid?.ifBlank { null }
        ?: prefs.getString(KEY_XUID, "").orEmpty()

    fun getRefreshToken(): String? = getActiveRecord()?.refreshToken?.ifBlank { null }
        ?: prefs.getString(KEY_REFRESH_TOKEN, null)

    suspend fun switchActiveAccount(xuidOrTag: String): Result<AuthState.SignedIn> =
        withContext(Dispatchers.IO) {
            runCatching {
                val records = loadAccountRecords()
                val target = records.firstOrNull {
                    it.xuid.equals(xuidOrTag, ignoreCase = true) ||
                        it.gamertag.equals(xuidOrTag, ignoreCase = true)
                } ?: error("Saved account not found")

                prefs.edit().putString(KEY_ACTIVE_XUID, target.xuid.ifBlank { target.gamertag }).apply()
                mirrorRecordToLegacyKeys(target)

                if (target.refreshToken.isNotBlank()) {
                    // Attempt quiet refresh so streaming & XBL tokens are fresh when switching
                    val refreshed = runCatching {
                        refreshWithToken(target.refreshToken)
                    }.getOrNull()
                    if (refreshed != null) {
                        return@runCatching refreshed
                    }
                }
                target.toSignedIn()
            }
        }

    fun removeSavedAccount(xuidOrTag: String): AuthState {
        val remaining = loadAccountRecords().filterNot {
            it.xuid.equals(xuidOrTag, ignoreCase = true) ||
                it.gamertag.equals(xuidOrTag, ignoreCase = true)
        }
        saveAccountRecords(remaining)
        val activeKey = prefs.getString(KEY_ACTIVE_XUID, "").orEmpty()
        val removedActive = activeKey.equals(xuidOrTag, ignoreCase = true) || remaining.none {
            it.xuid.equals(activeKey, ignoreCase = true) || it.gamertag.equals(activeKey, ignoreCase = true)
        }
        return if (remaining.isEmpty()) {
            clearLegacyKeys()
            prefs.edit().remove(KEY_ACTIVE_XUID).apply()
            AuthState.SignedOut
        } else if (removedActive) {
            val next = remaining.first()
            prefs.edit().putString(KEY_ACTIVE_XUID, next.xuid.ifBlank { next.gamertag }).apply()
            mirrorRecordToLegacyKeys(next)
            next.toSignedIn()
        } else {
            loadSavedAuthState()
        }
    }

    fun clearSession() {
        val active = getActiveRecord()
        if (active != null) {
            removeSavedAccount(active.xuid.ifBlank { active.gamertag })
        } else {
            clearLegacyKeys()
            prefs.edit().remove(KEY_ACTIVE_XUID).apply()
        }
    }

    suspend fun startDeviceCodeFlow(): Result<DeviceCodeSession> = withContext(Dispatchers.IO) {
        runCatching {
            val formBody = FormBody.Builder()
                .add("client_id", XBOX_APP_CLIENT_ID)
                .add("scope", "service::user.auth.xboxlive.com::MBI_SSL")
                .add("response_type", "device_code")
                .build()

            val request = Request.Builder()
                .url(LIVE_DEVICE_CODE_URL)
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Device code request failed (HTTP ${response.code})")
                }
                val json = JSONObject(rawBody)
                val rawExpires = json.optInt("expires_in", 900)
                val rawInterval = json.optInt("interval", 5)
                DeviceCodeSession(
                    userCode = json.getString("user_code"),
                    verificationUri = json.optString("verification_uri", "https://www.microsoft.com/link"),
                    deviceCode = json.getString("device_code"),
                    expiresInSec = rawExpires.coerceAtLeast(900),
                    intervalSec = rawInterval.coerceIn(4, 15)
                )
            }
        }
    }

    /**
     * Serialized token poll so concurrent pollers/resume callbacks NEVER double-consume
     * Microsoft's single-use device_code. Once Microsoft returns access_token + refresh_token,
     * they are cached in NonCancellable immediately before running XBL/XSTS exchanges.
     */
    suspend fun pollOnceOrExchange(session: DeviceCodeSession): Result<AuthState.SignedIn?> =
        withContext(Dispatchers.IO) {
            pollMutex.withLock {
                if (completedDeviceCode == session.deviceCode && completedSignIn != null) {
                    return@withLock Result.success(completedSignIn)
                }

                runCatching {
                    // Reuse captured OAuth tokens if Microsoft already issued them for this device_code
                    val cached = pendingOAuthByCode[session.deviceCode]
                    val (accessToken, refreshToken) = if (cached != null) {
                        cached
                    } else {
                        val formBody = FormBody.Builder()
                            .add("client_id", XBOX_APP_CLIENT_ID)
                            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                            .add("device_code", session.deviceCode)
                            .build()

                        val request = Request.Builder()
                            .url(LIVE_TOKEN_URL)
                            .post(formBody)
                            .build()

                        var fetchedAccess: String? = null
                        var fetchedRefresh: String? = null
                        var invalidGrantError = false

                        try {
                            httpClient.newCall(request).execute().use { response ->
                                val rawBody = response.body?.string().orEmpty()
                                val json = runCatching { JSONObject(rawBody.ifBlank { "{}" }) }
                                    .getOrDefault(JSONObject())
                                if (response.isSuccessful && json.has("access_token")) {
                                    fetchedAccess = json.getString("access_token")
                                    fetchedRefresh = json.optString("refresh_token", "")
                                } else {
                                    val err = json.optString("error", "authorization_pending")
                                    when (err) {
                                        "authorization_pending", "slow_down" -> Unit
                                        "expired_token" -> error("Code expired on Microsoft servers. Tap Retry to get a new code.")
                                        "access_denied" -> error("Sign-in was cancelled on Microsoft.")
                                        "invalid_grant" -> invalidGrantError = true
                                        else -> if (err.isNotBlank()) error("Microsoft sign-in failed: $err")
                                    }
                                }
                            }
                        } catch (_: IOException) {
                            return@runCatching null
                        }

                        if (invalidGrantError) {
                            if (completedDeviceCode == session.deviceCode && completedSignIn != null) {
                                return@runCatching completedSignIn
                            }
                            val racedTokens = pendingOAuthByCode[session.deviceCode]
                            if (racedTokens != null) {
                                racedTokens
                            } else {
                                error("Microsoft device code expired or was already used. Tap Retry for a fresh code.")
                            }
                        } else {
                            val tok = fetchedAccess ?: return@runCatching null
                            val pair = tok to fetchedRefresh.orEmpty()
                            pendingOAuthByCode[session.deviceCode] = pair
                            pair
                        }
                    }

                    // Perform XBL/XSTS exchange non-cancellably so switching activities never aborts mid-handshake
                    withContext(kotlinx.coroutines.NonCancellable) {
                        val signedIn = runCatching {
                            exchangeTokensForXHome(
                                liveAccessToken = accessToken,
                                refreshToken = refreshToken
                            )
                        }.recoverCatching { firstErr ->
                            // Single transient retry using captured refreshToken/accessToken without re-polling device_code
                            delay(600L)
                            if (refreshToken.isNotBlank()) {
                                runCatching { refreshWithToken(refreshToken) }
                                    .getOrElse { exchangeTokensForXHome(accessToken, refreshToken) }
                            } else {
                                throw firstErr
                            }
                        }.getOrThrow()

                        completedDeviceCode = session.deviceCode
                        completedSignIn = signedIn
                        pendingOAuthByCode.remove(session.deviceCode)
                        signedIn
                    }
                }
            }
        }

    suspend fun pollForDeviceTokenAndExchange(
        session: DeviceCodeSession,
        pollImmediatelyFirst: Boolean = false,
        wakeSignal: kotlinx.coroutines.channels.ReceiveChannel<Unit>? = null
    ): Result<AuthState.SignedIn> = withContext(Dispatchers.IO) {
        runCatching {
            val deadlineMs = System.currentTimeMillis() + (session.expiresInSec * 1000L)
            val intervalMs = (session.intervalSec * 1000L).coerceAtLeast(4500L)
            var isFirst = pollImmediatelyFirst

            while (System.currentTimeMillis() < deadlineMs) {
                if (!isFirst) {
                    if (wakeSignal != null) {
                        kotlinx.coroutines.withTimeoutOrNull(intervalMs) {
                            wakeSignal.receive()
                        }
                    } else {
                        delay(intervalMs)
                    }
                }
                isFirst = false

                val signedIn = pollOnceOrExchange(session).getOrThrow()
                if (signedIn != null) {
                    return@runCatching signedIn
                }
            }

            error("Sign-in code expired after ${session.expiresInSec / 60} minutes. Tap Retry for a new code.")
        }
    }

    suspend fun refreshExistingSession(): Result<AuthState.SignedIn> = withContext(Dispatchers.IO) {
        runCatching {
            val savedRefresh = getRefreshToken()
                ?: error("No saved refresh token available")
            refreshWithToken(savedRefresh)
        }
    }

    suspend fun refreshProfile(): Result<AuthState.SignedIn> = withContext(Dispatchers.IO) {
        runCatching {
            val active = getActiveRecord() ?: error("Not signed in")
            var xblHeader = active.xblAuthHeader
            var xuid = active.xuid
            if ((xblHeader.isBlank() || xuid.isBlank()) && active.refreshToken.isNotBlank()) {
                return@runCatching refreshWithToken(active.refreshToken)
            }
            if (xblHeader.isBlank() || xuid.isBlank()) {
                error("Missing Xbox Live profile credentials")
            }
            var profile = fetchProfileDetails(xuid, xblHeader)
            if (profile.avatarUrl.isBlank() && active.refreshToken.isNotBlank()) {
                // Token might have expired; refresh silently and retry profile fetch
                return@runCatching refreshWithToken(active.refreshToken)
            }
            val updated = active.copy(
                gamerscore = profile.gamerscore.ifBlank { active.gamerscore },
                avatarUrl = profile.avatarUrl.ifBlank { active.avatarUrl }
            )
            upsertAndActivateRecord(updated)
            updated.toSignedIn()
        }
    }

    private fun refreshWithToken(savedRefresh: String): AuthState.SignedIn {
        val formBody = FormBody.Builder()
            .add("client_id", XBOX_APP_CLIENT_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", savedRefresh)
            .add("scope", "service::user.auth.xboxlive.com::MBI_SSL")
            .build()

        val request = Request.Builder()
            .url(LIVE_TOKEN_URL)
            .post(formBody)
            .build()

        var accessToken = ""
        var newRefreshToken = savedRefresh
        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Refresh token exchange failed (HTTP ${response.code})")
            }
            val json = JSONObject(rawBody)
            accessToken = json.getString("access_token")
            newRefreshToken = json.optString("refresh_token", savedRefresh)
        }

        return exchangeTokensForXHome(accessToken, newRefreshToken)
    }

    private fun exchangeTokensForXHome(
        liveAccessToken: String,
        refreshToken: String
    ): AuthState.SignedIn {
        // 1. User Authenticate -> RPS Ticket -> Xbox User Token
        val userAuthBody = JSONObject().apply {
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
            put("Properties", JSONObject().apply {
                put("AuthMethod", "RPS")
                put("SiteName", "user.auth.xboxlive.com")
                put("RpsTicket", "d=$liveAccessToken")
            })
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val userAuthReq = Request.Builder()
            .url(XBL_USER_AUTH_URL)
            .header("x-xbl-contract-version", "1")
            .post(userAuthBody)
            .build()

        val userToken = try {
            httpClient.newCall(userAuthReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Xbox User Auth failed (HTTP ${resp.code})")
                JSONObject(raw).getString("Token")
            }
        } catch (e: IOException) {
            error("Timed out reaching user.auth.xboxlive.com: ${e.message}")
        }

        // 2. XSTS Authorize for GSSV (Xbox Home Streaming Relying Party)
        val gssvXsts = requestXsts(userToken, "http://gssv.xboxlive.com/")
        val xstsToken = gssvXsts.token
        val userHash = gssvXsts.userHash
        var xuid = gssvXsts.xuid
        var gamertag = gssvXsts.gamertag

        // 3. XSTS Authorize for Xbox Live Profile, Gamerpic, Achievements & TitleHub
        var xblAuthHeader = ""
        var gamerscore = ""
        var avatarUrl = ""
        runCatching {
            val xblXsts = requestXsts(userToken, "http://xboxlive.com")
            if (xuid.isBlank()) xuid = xblXsts.xuid
            if (gamertag == "Xbox Player" && xblXsts.gamertag.isNotBlank()) gamertag = xblXsts.gamertag
            xblAuthHeader = "XBL3.0 x=${xblXsts.userHash};${xblXsts.token}"
            if (xuid.isNotBlank()) {
                val profile = fetchProfileDetails(xuid, xblAuthHeader)
                gamerscore = profile.gamerscore
                avatarUrl = profile.avatarUrl
            }
        }

        // 4. Exchange XSTS token for xHome Streaming GS Token + Regional Base URI
        var gsToken = ""
        var xhomeBaseUri = XHomeSignalingClient.DEFAULT_BASE_URL
        runCatching {
            val loginBody = JSONObject().apply {
                put("token", xstsToken)
                put("offeringId", "xhome")
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val gssvLoginReq = Request.Builder()
                .url("${XHomeSignalingClient.DEFAULT_BASE_URL}/v2/login/user")
                .header("Content-Type", "application/json; charset=utf-8")
                .post(loginBody)
                .build()

            httpClient.newCall(gssvLoginReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("xHome GSSV login failed (HTTP ${resp.code})")
                val json = JSONObject(raw)
                gsToken = json.optString("gsToken", "")
                val regions = json.optJSONObject("offeringSettings")?.optJSONArray("regions")
                if (regions != null && regions.length() > 0) {
                    for (i in 0 until regions.length()) {
                        val r = regions.getJSONObject(i)
                        val uri = r.optString("baseUri", "")
                        if (r.optBoolean("isDefault", false) && uri.startsWith("https://")) {
                            xhomeBaseUri = uri.trimEnd('/')
                            break
                        }
                        if (i == 0 && uri.startsWith("https://")) {
                            xhomeBaseUri = uri.trimEnd('/')
                        }
                    }
                }
            }
        }

        val record = StoredAccountRecord(
            xuid = xuid,
            gamertag = gamertag,
            userHash = userHash,
            gamerscore = gamerscore,
            avatarUrl = avatarUrl,
            refreshToken = refreshToken,
            gsToken = gsToken,
            xblAuthHeader = xblAuthHeader,
            xhomeBaseUri = xhomeBaseUri
        )
        upsertAndActivateRecord(record)
        return record.toSignedIn()
    }

    private data class XstsResult(
        val token: String,
        val userHash: String,
        val xuid: String,
        val gamertag: String
    )

    private data class ProfileInfo(
        val gamerscore: String,
        val avatarUrl: String
    )

    private fun requestXsts(userToken: String, relyingParty: String): XstsResult {
        val xstsBody = JSONObject().apply {
            put("RelyingParty", relyingParty)
            put("TokenType", "JWT")
            put("Properties", JSONObject().apply {
                put("SandboxId", "RETAIL")
                put("UserTokens", JSONArray().put(userToken))
            })
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val xstsReq = Request.Builder()
            .url(XSTS_AUTH_URL)
            .header("x-xbl-contract-version", "1")
            .post(xstsBody)
            .build()

        var token = ""
        var userHash = ""
        var xuid = ""
        var gamertag = "Xbox Player"
        try {
            httpClient.newCall(xstsReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("XSTS authorization failed (HTTP ${resp.code})")
                val json = JSONObject(raw)
                token = json.getString("Token")
                val xuiArray = json.getJSONObject("DisplayClaims").getJSONArray("xui")
                if (xuiArray.length() > 0) {
                    val xui = xuiArray.getJSONObject(0)
                    userHash = xui.optString("uhs", "")
                    xuid = xui.optString("xid", "")
                    gamertag = xui.optString("gtg", gamertag)
                }
            }
        } catch (e: IOException) {
            error("Timed out reaching xsts.auth.xboxlive.com: ${e.message}")
        }
        return XstsResult(token = token, userHash = userHash, xuid = xuid, gamertag = gamertag)
    }

    private fun fetchProfileDetails(xuid: String, xblAuthHeader: String): ProfileInfo {
        val req = Request.Builder()
            .url("https://profile.xboxlive.com/users/xuid($xuid)/profile/settings?settings=Gamerscore,GameDisplayPicRaw,AppDisplayPicRaw,PublicGamerpic")
            .header("Authorization", xblAuthHeader)
            .header("x-xbl-contract-version", "2")
            .header("Accept", "application/json")
            .get()
            .build()

        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use ProfileInfo("", "")
            val raw = resp.body?.string().orEmpty()
            val users = JSONObject(raw).optJSONArray("profileUsers") ?: return@use ProfileInfo("", "")
            if (users.length() == 0) return@use ProfileInfo("", "")
            val settings = users.getJSONObject(0).optJSONArray("settings") ?: return@use ProfileInfo("", "")
            var gamerscore = ""
            var gamePic = ""
            var appPic = ""
            var publicPic = ""
            for (i in 0 until settings.length()) {
                val item = settings.getJSONObject(i)
                when (item.optString("id")) {
                    "Gamerscore" -> gamerscore = item.optString("value", "")
                    "GameDisplayPicRaw" -> gamePic = item.optString("value", "")
                    "AppDisplayPicRaw" -> appPic = item.optString("value", "")
                    "PublicGamerpic" -> publicPic = item.optString("value", "")
                }
            }
            val rawPic = gamePic.ifBlank { appPic }.ifBlank { publicPic }
            ProfileInfo(
                gamerscore = gamerscore,
                avatarUrl = XboxImageCache.normalizeXboxImageUrl(rawPic)
            )
        }
    }

    private fun migrateLegacySingleAccountIfNeeded() {
        if (prefs.contains(KEY_SAVED_ACCOUNTS_JSON)) return
        val gt = prefs.getString(KEY_GAMERTAG, null).orEmpty()
        val xuid = prefs.getString(KEY_XUID, null).orEmpty()
        val uhs = prefs.getString(KEY_USER_HASH, null).orEmpty()
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null).orEmpty()
        if (gt.isNotBlank() || refresh.isNotBlank()) {
            val legacy = StoredAccountRecord(
                xuid = xuid,
                gamertag = gt.ifBlank { "Xbox Profile" },
                userHash = uhs,
                gamerscore = prefs.getString(KEY_GAMERSCORE, "").orEmpty(),
                avatarUrl = prefs.getString(KEY_AVATAR_URL, "").orEmpty(),
                refreshToken = refresh,
                gsToken = prefs.getString(KEY_GS_TOKEN, "").orEmpty(),
                xblAuthHeader = prefs.getString(KEY_XBL_AUTH_HEADER, "").orEmpty(),
                xhomeBaseUri = prefs.getString(KEY_XHOME_BASE_URI, XHomeSignalingClient.DEFAULT_BASE_URL).orEmpty()
            )
            saveAccountRecords(listOf(legacy))
            prefs.edit().putString(KEY_ACTIVE_XUID, legacy.xuid.ifBlank { legacy.gamertag }).apply()
        }
    }

    private fun getActiveRecord(): StoredAccountRecord? {
        val records = loadAccountRecords()
        if (records.isEmpty()) return null
        val activeKey = prefs.getString(KEY_ACTIVE_XUID, "").orEmpty()
        return records.firstOrNull {
            it.xuid.equals(activeKey, ignoreCase = true) ||
                it.gamertag.equals(activeKey, ignoreCase = true)
        } ?: records.firstOrNull()
    }

    private fun upsertAndActivateRecord(record: StoredAccountRecord) {
        val key = record.xuid.ifBlank { record.gamertag }
        val existing = loadAccountRecords().filterNot {
            (record.xuid.isNotBlank() && it.xuid.equals(record.xuid, ignoreCase = true)) ||
                it.gamertag.equals(record.gamertag, ignoreCase = true)
        }
        val updated = listOf(record) + existing
        saveAccountRecords(updated)
        prefs.edit().putString(KEY_ACTIVE_XUID, key).apply()
        mirrorRecordToLegacyKeys(record)
    }

    private fun loadAccountRecords(): List<StoredAccountRecord> {
        val raw = prefs.getString(KEY_SAVED_ACCOUNTS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        StoredAccountRecord(
                            xuid = obj.optString("xuid", ""),
                            gamertag = obj.optString("gamertag", "Xbox Profile"),
                            userHash = obj.optString("userHash", ""),
                            gamerscore = obj.optString("gamerscore", ""),
                            avatarUrl = obj.optString("avatarUrl", ""),
                            refreshToken = obj.optString("refreshToken", ""),
                            gsToken = obj.optString("gsToken", ""),
                            xblAuthHeader = obj.optString("xblAuthHeader", ""),
                            xhomeBaseUri = obj.optString("xhomeBaseUri", XHomeSignalingClient.DEFAULT_BASE_URL)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveAccountRecords(records: List<StoredAccountRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("xuid", r.xuid)
                put("gamertag", r.gamertag)
                put("userHash", r.userHash)
                put("gamerscore", r.gamerscore)
                put("avatarUrl", r.avatarUrl)
                put("refreshToken", r.refreshToken)
                put("gsToken", r.gsToken)
                put("xblAuthHeader", r.xblAuthHeader)
                put("xhomeBaseUri", r.xhomeBaseUri)
            })
        }
        prefs.edit().putString(KEY_SAVED_ACCOUNTS_JSON, arr.toString()).apply()
    }

    private fun mirrorRecordToLegacyKeys(r: StoredAccountRecord) {
        prefs.edit()
            .putString(KEY_REFRESH_TOKEN, r.refreshToken)
            .putString(KEY_USER_HASH, r.userHash)
            .putString(KEY_XUID, r.xuid)
            .putString(KEY_GAMERSCORE, r.gamerscore)
            .putString(KEY_AVATAR_URL, r.avatarUrl)
            .putString(KEY_XBL_AUTH_HEADER, r.xblAuthHeader)
            .putString(KEY_GS_TOKEN, r.gsToken)
            .putString(KEY_XHOME_BASE_URI, r.xhomeBaseUri)
            .putString(KEY_GAMERTAG, r.gamertag)
            .apply()
    }

    private fun clearLegacyKeys() {
        prefs.edit()
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_HASH)
            .remove(KEY_XUID)
            .remove(KEY_GAMERSCORE)
            .remove(KEY_AVATAR_URL)
            .remove(KEY_XBL_AUTH_HEADER)
            .remove(KEY_GS_TOKEN)
            .remove(KEY_XHOME_BASE_URI)
            .remove(KEY_GAMERTAG)
            .apply()
    }

    companion object {
        private const val XBOX_APP_CLIENT_ID = "000000004C12AE6F"
        private const val LIVE_DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf"
        private const val LIVE_TOKEN_URL = "https://login.live.com/oauth20_token.srf"
        private const val XBL_USER_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate"
        private const val XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val PREFS_FILE = "project_x_secure_auth"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_HASH = "user_hash"
        private const val KEY_XUID = "xuid"
        private const val KEY_GAMERSCORE = "gamerscore"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_XBL_AUTH_HEADER = "xbl_auth_header"
        private const val KEY_GS_TOKEN = "gs_token"
        private const val KEY_XHOME_BASE_URI = "xhome_base_uri"
        private const val KEY_GAMERTAG = "gamertag"
        private const val KEY_SAVED_ACCOUNTS_JSON = "saved_accounts_json"
        private const val KEY_ACTIVE_XUID = "active_account_xuid"

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            return runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.getOrElse {
                context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            }
        }
    }
}
