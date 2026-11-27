package com.example.network

import com.example.model.AchievementItem
import com.example.model.ConsoleModel
import com.example.model.ConsolePowerState
import com.example.model.RecentGame
import com.example.model.StreamQualityPreset
import com.example.model.XboxConsole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class IceCandidate(
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val sdp: String
)

data class XHomeSessionInfo(
    val sessionPath: String,
    val state: String,
    val effectiveBaseUrl: String = XHomeSignalingClient.DEFAULT_BASE_URL
)

data class XHomeSdpExchangeResult(
    val answerSdp: String,
    val iceCandidates: List<IceCandidate>
)

/**
 * xHome WebRTC signaling client & Xbox Live TitleHub/Achievements fetcher.
 * Protocol headers, session payload, configuration step, keepalive, and ICE format
 * aligned with open-source Xbox streaming clients (Geocld/XStreaming & Better-xCloud).
 */
class XHomeSignalingClient(
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    suspend fun fetchRegisteredConsoles(
        gsToken: String,
        baseUrl: String = DEFAULT_BASE_URL
    ): Result<List<XboxConsole>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/v6/servers/home")
                    .header("Authorization", "Bearer $gsToken")
                    .header("X-MS-Device-Info", MS_DEVICE_INFO_HEADER)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("Console discovery failed (HTTP ${response.code})")
                    }
                    val root = JSONObject(raw)
                    val results = root.optJSONArray("results") ?: JSONArray()
                    buildList {
                        for (i in 0 until results.length()) {
                            val obj = results.getJSONObject(i)
                            val serverId = obj.optString("serverId")
                            val deviceName = obj.optString("deviceName", "Xbox Console")
                            val consoleType = obj.optString("consoleType", "")
                            val powerStateRaw = obj.optString("powerState", "ConnectedStandby")
                            val model = resolveConsoleModel(consoleType, deviceName)
                            val powerState = when {
                                powerStateRaw.equals("On", ignoreCase = true) -> ConsolePowerState.ONLINE
                                powerStateRaw.contains("Standby", ignoreCase = true) -> ConsolePowerState.STANDBY
                                else -> ConsolePowerState.OFFLINE
                            }
                            add(
                                XboxConsole(
                                    id = serverId.ifBlank { "xhome-$i" },
                                    name = deviceName,
                                    model = model,
                                    liveId = serverId,
                                    lanIp = "",
                                    powerState = powerState,
                                    isRegisteredFromXHome = true
                                )
                            )
                        }
                    }
                }
            }
        }

    suspend fun fetchRecentlyPlayedGames(
        xuid: String,
        xblAuthHeader: String
    ): Result<List<RecentGame>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://titlehub.xboxlive.com/users/xuid($xuid)/titles/titlehistory/decoration/detail,image,achievement")
                .header("Authorization", xblAuthHeader)
                .header("x-xbl-contract-version", "2")
                .header("Accept-Language", "en-US")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("TitleHub history failed (HTTP ${response.code})")
                }
                val titles = JSONObject(raw).optJSONArray("titles") ?: JSONArray()
                buildList {
                    for (i in 0 until minOf(titles.length(), 16)) {
                        val obj = titles.getJSONObject(i)
                        val titleId = obj.optString("titleId", "title-$i")
                        val name = obj.optString("name", "").ifBlank { continue }
                        val devicesArr = obj.optJSONArray("devices")
                        val deviceLabel = resolveDevicePlatformLabel(devicesArr, obj)
                        val lastPlayedIso = obj.optJSONObject("titleHistory")?.optString("lastTimePlayed", "").orEmpty()
                        val datePart = lastPlayedIso.substringBefore("T").ifBlank { "Recent" }
                        val boxArtUrl = extractTitleBoxArtUrl(obj)
                        add(
                            RecentGame(
                                titleId = titleId,
                                name = name,
                                deviceLabel = deviceLabel,
                                lastPlayedLabel = datePart,
                                imageUrl = boxArtUrl
                            )
                        )
                    }
                }
            }
        }
    }

    private fun extractTitleBoxArtUrl(titleObj: JSONObject): String {
        val images = titleObj.optJSONArray("images")
        if (images != null && images.length() > 0) {
            val preferredTags = listOf("BoxArt", "BrandedKeyArt", "Poster", "Tile", "SuperHeroArt")
            for (tag in preferredTags) {
                for (j in 0 until images.length()) {
                    val img = images.optJSONObject(j) ?: continue
                    val typeVal = img.optString("type")
                    val purposeVal = img.optString("purpose")
                    if (typeVal.equals(tag, ignoreCase = true) || purposeVal.equals(tag, ignoreCase = true)) {
                        val normalized = XboxImageCache.normalizeXboxImageUrl(img.optString("url", ""))
                        if (normalized.startsWith("https://", ignoreCase = true)) return normalized
                    }
                }
            }
            for (j in 0 until images.length()) {
                val img = images.optJSONObject(j) ?: continue
                val normalized = XboxImageCache.normalizeXboxImageUrl(img.optString("url", ""))
                if (normalized.startsWith("https://", ignoreCase = true)) return normalized
            }
        }
        return XboxImageCache.normalizeXboxImageUrl(titleObj.optString("displayImage", ""))
            .takeIf { it.startsWith("https://", ignoreCase = true) }
            .orEmpty()
    }

    suspend fun fetchAchievements(
        xuid: String,
        xblAuthHeader: String
    ): Result<List<AchievementItem>> = withContext(Dispatchers.IO) {
        runCatching {
            // Query recent unlocked achievements first sorted descending by UnlockTime
            val unlockedDirect = queryDirectAchievements(
                xuid = xuid,
                xblAuthHeader = xblAuthHeader,
                unlockedOnly = true
            )
            val rawDirect = if (unlockedDirect.isNotEmpty()) {
                unlockedDirect
            } else {
                queryDirectAchievements(
                    xuid = xuid,
                    xblAuthHeader = xblAuthHeader,
                    unlockedOnly = false
                )
            }

            if (rawDirect.isNotEmpty()) {
                // Fill any blank icon URLs using TitleHub box art map so every card gets real art
                val needsArt = rawDirect.any { it.item.iconUrl.isBlank() }
                val titleArtMap = if (needsArt) {
                    fetchTitleArtMap(xuid, xblAuthHeader)
                } else {
                    emptyMap()
                }
                return@runCatching rawDirect.map { parsed ->
                    val item = parsed.item
                    if (item.iconUrl.isNotBlank()) {
                        item
                    } else {
                        val fallbackUrl = titleArtMap[parsed.titleId].orEmpty()
                            .ifBlank { titleArtMap[item.titleName.lowercase()].orEmpty() }
                        item.copy(iconUrl = fallbackUrl)
                    }
                }
            }

            // Fallback: TitleHub per-title achievement summary across recent apps/games
            val historyReq = Request.Builder()
                .url("https://titlehub.xboxlive.com/users/xuid($xuid)/titles/titlehistory/decoration/achievement,detail,image")
                .header("Authorization", xblAuthHeader)
                .header("x-xbl-contract-version", "2")
                .header("Accept-Language", "en-US")
                .get()
                .build()

            httpClient.newCall(historyReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Achievements request failed (HTTP ${resp.code})")
                }
                val titles = JSONObject(raw).optJSONArray("titles") ?: JSONArray()
                buildList {
                    for (i in 0 until minOf(titles.length(), 15)) {
                        val t = titles.getJSONObject(i)
                        val titleName = t.optString("name", "").ifBlank { continue }
                        val ach = t.optJSONObject("achievement") ?: continue
                        val earned = ach.optInt("currentAchievements", 0)
                        val total = ach.optInt("totalAchievements", 0)
                        val currentGs = ach.optInt("currentGamerscore", 0)
                        val totalGs = ach.optInt("totalGamerscore", 0)
                        if (total <= 0 && totalGs <= 0) continue

                        add(
                            AchievementItem(
                                id = "title-ach-${t.optString("titleId", "$i")}",
                                titleName = titleName,
                                name = "$earned / $total Achievements",
                                description = "Earned $currentGs of $totalGs Gamerscore in $titleName",
                                gamerscore = currentGs,
                                isUnlocked = earned > 0,
                                unlockedAtLabel = t.optJSONObject("titleHistory")
                                    ?.optString("lastTimePlayed", "")
                                    ?.substringBefore("T")
                                    .orEmpty()
                                    .ifBlank { "Recent" },
                                iconUrl = extractTitleBoxArtUrl(t)
                            )
                        )
                    }
                }
            }
        }
    }

    private data class ParsedAchievement(
        val item: AchievementItem,
        val rawUnlockIso: String,
        val titleId: String
    )

    private fun queryDirectAchievements(
        xuid: String,
        xblAuthHeader: String,
        unlockedOnly: Boolean
    ): List<ParsedAchievement> {
        val filterQuery = if (unlockedOnly) "&unlockedOnly=true" else ""
        val directReq = Request.Builder()
            .url("https://achievements.xboxlive.com/users/xuid($xuid)/achievements?orderBy=UnlockTime$filterQuery&maxItems=36")
            .header("Authorization", xblAuthHeader)
            .header("x-xbl-contract-version", "2")
            .header("Accept-Language", "en-US")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(directReq).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val raw = response.body?.string().orEmpty()
                val arr = JSONObject(raw).optJSONArray("achievements") ?: return@use emptyList()
                val parsed = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("name", "").ifBlank { continue }
                        val progressState = obj.optString("progressState", "")
                        val unlocked = progressState.equals("Achieved", ignoreCase = true)
                        val desc = if (unlocked) {
                            obj.optString("description", "")
                        } else {
                            obj.optString("lockedDescription", obj.optString("description", ""))
                        }.ifBlank { "Xbox Achievement" }

                        val assoc = obj.optJSONArray("titleAssociations")?.optJSONObject(0)
                        val titleName = assoc?.optString("name", "Xbox Game").orEmpty().ifBlank { "Xbox Game" }
                        val titleId = assoc?.optString("id", "").orEmpty()

                        var score = 0
                        val rewards = obj.optJSONArray("rewards")
                        if (rewards != null) {
                            for (r in 0 until rewards.length()) {
                                val rw = rewards.optJSONObject(r) ?: continue
                                if (rw.optString("type").equals("Gamerscore", ignoreCase = true)) {
                                    score = rw.optString("value", "0").toIntOrNull() ?: rw.optInt("value", 0)
                                    break
                                }
                            }
                        }

                        var rawIconUrl = ""
                        val mediaAssets = obj.optJSONArray("mediaAssets")
                        if (mediaAssets != null) {
                            for (m in 0 until mediaAssets.length()) {
                                val asset = mediaAssets.optJSONObject(m) ?: continue
                                val candidate = XboxImageCache.normalizeXboxImageUrl(asset.optString("url", ""))
                                if (candidate.startsWith("https://", ignoreCase = true)) {
                                    if (asset.optString("type").equals("Icon", ignoreCase = true) || rawIconUrl.isBlank()) {
                                        rawIconUrl = candidate
                                    }
                                }
                            }
                        }

                        val rawTimeUnlocked = obj.optJSONObject("progression")?.optString("timeUnlocked", "").orEmpty()
                        val validIso = rawTimeUnlocked.takeIf {
                            it.isNotBlank() && !it.startsWith("0001-01-01")
                        }.orEmpty()
                        val unlockedLabel = validIso.substringBefore("T").ifBlank {
                            if (unlocked) "Unlocked" else "In progress"
                        }

                        add(
                            ParsedAchievement(
                                item = AchievementItem(
                                    id = "${obj.optString("id", "$i")}-${titleId.ifBlank { titleName }}",
                                    titleName = titleName,
                                    name = name,
                                    description = desc,
                                    gamerscore = score,
                                    isUnlocked = unlocked,
                                    unlockedAtLabel = unlockedLabel,
                                    iconUrl = rawIconUrl
                                ),
                                rawUnlockIso = validIso,
                                titleId = titleId
                            )
                        )
                    }
                }
                // Enforce newest unlocks first even if Xbox returns mixed order
                parsed.sortedWith(
                    compareByDescending<ParsedAchievement> { it.item.isUnlocked }
                        .thenByDescending { it.rawUnlockIso }
                ).take(24)
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchTitleArtMap(xuid: String, xblAuthHeader: String): Map<String, String> {
        val req = Request.Builder()
            .url("https://titlehub.xboxlive.com/users/xuid($xuid)/titles/titlehistory/decoration/image")
            .header("Authorization", xblAuthHeader)
            .header("x-xbl-contract-version", "2")
            .header("Accept-Language", "en-US")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyMap()
                val raw = resp.body?.string().orEmpty()
                val titles = JSONObject(raw).optJSONArray("titles") ?: return@use emptyMap()
                val map = mutableMapOf<String, String>()
                for (i in 0 until titles.length()) {
                    val t = titles.optJSONObject(i) ?: continue
                    val art = extractTitleBoxArtUrl(t)
                    if (art.isBlank()) continue
                    val tid = t.optString("titleId", "")
                    val tname = t.optString("name", "").lowercase()
                    if (tid.isNotBlank()) map[tid] = art
                    if (tname.isNotBlank()) map[tname] = art
                }
                map
            }
        }.getOrDefault(emptyMap())
    }

    /**
     * Initiates an xHome WebRTC streaming session aligned with Geocld/XStreaming & Better-xCloud.
     * Automatically tries regional host + fallback `uks.core.gssv-play-prodxhome.xboxlive.com` / `useIceConnection`
     * if the console reports a transient Agent error.
     */
    suspend fun initiateHomePlaySession(
        gsToken: String,
        serverId: String,
        @Suppress("UNUSED_PARAMETER") preset: StreamQualityPreset,
        baseUrl: String = DEFAULT_BASE_URL
    ): Result<XHomeSessionInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val primaryBase = baseUrl.trimEnd('/')
            val attempts = buildList {
                add(primaryBase to false)
                val fallbackHost = if (primaryBase.contains("uks.core.gssv-play-prodxhome")) {
                    DEFAULT_BASE_URL
                } else {
                    XHOME_REGIONAL_FALLBACK_URL
                }
                add(fallbackHost to false)
                add(primaryBase to true)
            }.distinct()

            var lastError: Throwable? = null
            for ((index, attempt) in attempts.withIndex()) {
                val (attemptBase, useIce) = attempt
                val res = runCatching {
                    attemptPlayProvision(
                        gsToken = gsToken,
                        serverId = serverId,
                        cleanBase = attemptBase,
                        useIceConnection = useIce
                    )
                }
                if (res.isSuccess) {
                    return@runCatching res.getOrThrow()
                }
                lastError = res.exceptionOrNull()
                if (index < attempts.lastIndex) {
                    delay(650L)
                }
            }
            throw (lastError ?: IllegalStateException("Unable to start Xbox Remote Play session"))
        }
    }

    private suspend fun attemptPlayProvision(
        gsToken: String,
        serverId: String,
        cleanBase: String,
        useIceConnection: Boolean
    ): XHomeSessionInfo {
        val tzOffsetMinutes = -(TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000)

        val payload = JSONObject().apply {
            put("titleId", "")
            put("systemUpdateGroup", "")
            put("clientSessionId", "")
            put("settings", JSONObject().apply {
                put("nanoVersion", "V3;WebrtcTransport.dll")
                put("enableTextToSpeech", false)
                put("highContrast", 0)
                put("locale", "en-US")
                put("useIceConnection", useIceConnection)
                put("timezoneOffsetMinutes", tzOffsetMinutes)
                put("sdkType", "web")
                put("osName", "windows")
            })
            put("serverId", serverId)
            put("fallbackRegionNames", JSONArray())
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$cleanBase/v5/sessions/home/play")
            .header("Authorization", "Bearer $gsToken")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-MS-Device-Info", MS_DEVICE_INFO_HEADER)
            .header("Cache-Control", "no-cache")
            .post(payload)
            .build()

        val sessionPath = httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = formatGssvFailure(raw, response.code)
                error("xHome play rejected (HTTP ${response.code}): $detail")
            }
            JSONObject(raw).getString("sessionPath").trimStart('/')
        }

        // Send initial configuration step per XStreaming/Better-xCloud
        postSessionConfiguration(gsToken, cleanBase, sessionPath)

        // Poll until ReadyToConnect / Provisioned
        var currentState = "Provisioning"
        repeat(28) {
            val stateReq = Request.Builder()
                .url("$cleanBase/$sessionPath/state")
                .header("Authorization", "Bearer $gsToken")
                .header("X-MS-Device-Info", MS_DEVICE_INFO_HEADER)
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            var rawFailure = ""
            httpClient.newCall(stateReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful && raw.isNotBlank()) {
                    val json = JSONObject(raw)
                    currentState = json.optString("state", currentState)
                    if (currentState.equals("Failed", ignoreCase = true)) {
                        rawFailure = raw
                    }
                } else if (resp.code == 404 || resp.code == 410) {
                    error("xHome session expired on console (HTTP ${resp.code})")
                }
            }
            if (currentState == "ReadyToConnect" || currentState == "Provisioned") {
                return XHomeSessionInfo(
                    sessionPath = sessionPath,
                    state = currentState,
                    effectiveBaseUrl = cleanBase
                )
            }
            if (currentState.equals("Failed", ignoreCase = true)) {
                error(formatGssvFailure(rawFailure, 0))
            }
            delay(750L)
        }
        error("Timed out waiting for console provisioning (last state: $currentState)")
    }

    private fun postSessionConfiguration(
        gsToken: String,
        cleanBase: String,
        sessionPath: String
    ) {
        val body = JSONObject().apply {
            put("osName", "windows")
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val req = Request.Builder()
            .url("$cleanBase/${sessionPath.trimStart('/')}/configuration")
            .header("Authorization", "Bearer $gsToken")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-MS-Device-Info", MS_DEVICE_INFO_HEADER)
            .post(body)
            .build()
        runCatching { httpClient.newCall(req).execute().close() }
    }

    suspend fun sendKeepalive(
        gsToken: String,
        sessionPath: String,
        baseUrl: String = DEFAULT_BASE_URL
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = sessionPath.trimStart('/')
        val req = Request.Builder()
            .url("$cleanBase/$cleanPath/keepalive")
            .header("Authorization", "Bearer $gsToken")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-MS-Device-Info", MS_DEVICE_INFO_HEADER)
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        runCatching {
            httpClient.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun formatGssvFailure(rawBody: String, httpCode: Int): String {
        val rawLower = rawBody.lowercase()
        if (rawLower.contains("-2106654717") || rawLower.contains("startstreamingsessionv2command")) {
            return "Xbox refused Remote Play (-2106654717). Your console's Remote Play agent needs a reset: on your Xbox go to Settings > Devices & connections > Remote features, toggle Remote features OFF then back ON, and hard restart the console (hold power button 10s)."
        }
        if (rawLower.contains("consoleispoweredoff") || rawLower.contains("notready")) {
            return "Your Xbox is asleep or offline. Wake the console first and ensure Instant-On sleep mode is enabled."
        }
        val parsed = runCatching {
            val json = JSONObject(rawBody)
            val errDetails = json.optJSONObject("errorDetails") ?: json
            val code = errDetails.optString("code", "").ifBlank { json.optString("code", "") }
            val msg = errDetails.optString("message", "").ifBlank { json.optString("message", "") }
            when {
                code.isNotBlank() && msg.isNotBlank() -> "[$code] $msg"
                code.isNotBlank() -> "[$code]"
                msg.isNotBlank() -> msg
                else -> ""
            }
        }.getOrDefault("")

        return if (parsed.isNotBlank()) {
            "xHome error: $parsed. On Xbox verify Settings > Devices & connections > Remote features."
        } else {
            "xHome streaming failed${if (httpCode > 0) " (HTTP $httpCode)" else ""}. Verify Remote features & NAT Type on your Xbox."
        }
    }

    suspend fun exchangeSdpAndIce(
        gsToken: String,
        sessionPath: String,
        offerSdp: String,
        localCandidates: List<IceCandidate>,
        preferredLanIp: String,
        baseUrl: String = DEFAULT_BASE_URL
    ): Result<XHomeSdpExchangeResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = baseUrl.trimEnd('/')
            val cleanPath = sessionPath.trimStart('/')

            // 1. Send SDP Offer
            val sdpPayload = JSONObject().apply {
                put("messageType", "offer")
                put("sdp", offerSdp)
                put("requestId", "offer-1")
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val sdpPost = Request.Builder()
                .url("$cleanBase/$cleanPath/sdp")
                .header("Authorization", "Bearer $gsToken")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-MS-Device-Info", MS_DEVICE_INFO_HEADER)
                .post(sdpPayload)
                .build()

            httpClient.newCall(sdpPost).execute().use { resp ->
                val errBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("SDP offer upload failed (HTTP ${resp.code}): ${formatGssvFailure(errBody, resp.code)}")
                }
            }

            // 2. Poll SDP Answer
            var answerSdp = ""
            repeat(24) {
                val sdpGet = Request.Builder()
                    .url("$cleanBase/$cleanPath/sdp")
                    .header("Authorization", "Bearer $gsToken")
                    .header("X-MS-Device-Info", MS_DEVICE_INFO_HEADER)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
                httpClient.newCall(sdpGet).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.isSuccessful && raw.isNotBlank()) {
                        answerSdp = extractAnswerSdp(raw)
                    }
                }
                if (answerSdp.isNotBlank()) return@repeat
                delay(450L)
            }
            if (answerSdp.isBlank()) error("Timed out waiting for xHome SDP answer")

            // 3. Upload prioritized local ICE candidates in XStreaming / GSSV format
            val prioritizedLocal = IceCandidatePrioritizer.prioritizeLocalLan(
                candidates = localCandidates,
                preferredLanIp = preferredLanIp
            ).take(10)

            prioritizedLocal.forEach { c ->
                val candidateInner = JSONObject().apply {
                    put("candidate", c.sdp)
                    put("sdpMid", c.sdpMid)
                    put("sdpMLineIndex", c.sdpMLineIndex)
                }.toString()
                val icePayload = JSONObject().apply {
                    put("messageType", "iceCandidate")
                    put("candidate", candidateInner)
                }.toString().toRequestBody(JSON_MEDIA_TYPE)

                val icePost = Request.Builder()
                    .url("$cleanBase/$cleanPath/ice")
                    .header("Authorization", "Bearer $gsToken")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-MS-Device-Info", MS_DEVICE_INFO_HEADER)
                    .post(icePayload)
                    .build()
                runCatching { httpClient.newCall(icePost).execute().close() }
            }

            // 4. Fetch remote ICE candidates & sort local LAN host candidates first
            val remoteCandidates = mutableListOf<IceCandidate>()
            repeat(10) {
                val iceGet = Request.Builder()
                    .url("$cleanBase/$cleanPath/ice")
                    .header("Authorization", "Bearer $gsToken")
                    .header("X-MS-Device-Info", MS_DEVICE_INFO_HEADER)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
                httpClient.newCall(iceGet).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.isSuccessful && raw.isNotBlank()) {
                        remoteCandidates.addAll(extractRemoteIceCandidates(raw))
                    }
                }
                if (remoteCandidates.isNotEmpty()) return@repeat
                delay(350L)
            }

            XHomeSdpExchangeResult(
                answerSdp = answerSdp,
                iceCandidates = IceCandidatePrioritizer.prioritizeLocalLan(
                    candidates = remoteCandidates.distinctBy { it.sdp },
                    preferredLanIp = preferredLanIp
                )
            )
        }
    }

    private fun extractAnswerSdp(raw: String): String {
        return runCatching {
            val json = JSONObject(raw)
            val direct = json.optString("sdp", "")
            if (direct.isNotBlank()) return@runCatching direct

            val objExchange = json.optJSONObject("exchangeResponse")
            if (objExchange != null) {
                return@runCatching objExchange.optString("sdp", "")
            }

            val strExchange = json.optString("exchangeResponse", "")
            if (strExchange.startsWith("{")) {
                return@runCatching JSONObject(strExchange).optString("sdp", "")
            }
            ""
        }.getOrDefault("")
    }

    private fun extractRemoteIceCandidates(raw: String): List<IceCandidate> {
        val list = mutableListOf<IceCandidate>()
        runCatching {
            val trimmed = raw.trim()
            val arr: JSONArray? = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("candidates")
                        ?: obj.optJSONArray("exchangeResponse")
                        ?: obj.optString("exchangeResponse", "")
                            .takeIf { it.startsWith("[") }
                            ?.let { JSONArray(it) }
                }
                else -> null
            }
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val cand = item.optString("candidate", "")
                    if (cand.isNotBlank() && !cand.contains("a=end-of-candidates")) {
                        list.add(
                            IceCandidate(
                                item.optString("sdpMid", "0"),
                                item.optInt("sdpMLineIndex", 0),
                                cand
                            )
                        )
                    }
                }
            }
        }
        return list
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://xhome.gssv-play-prod.xboxlive.com"
        const val XHOME_REGIONAL_FALLBACK_URL = "https://uks.core.gssv-play-prodxhome.xboxlive.com"
        const val BASE_URL = DEFAULT_BASE_URL
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MS_DEVICE_INFO_HEADER =
            """{"appInfo":{"env":{"clientAppId":"www.xbox.com","clientAppType":"browser","clientAppVersion":"21.1.98","clientSdkVersion":"8.5.3","httpEnvironment":"prod","sdkInstallId":""}},"dev":{"hw":{"make":"Microsoft","model":"Edge","sdktype":"web"},"os":{"name":"windows","ver":"10","platform":"desktop"},"displayInfo":{"dimensions":{"widthInPixels":1280,"heightInPixels":720},"pixelDensity":{"dpiX":1,"dpiY":1}}}}"""

        internal fun resolveConsoleModel(consoleType: String, deviceName: String): ConsoleModel {
            return when {
                consoleType.contains("Anaconda", ignoreCase = true) ||
                    consoleType.contains("XboxSeriesX", ignoreCase = true) -> ConsoleModel.SERIES_X
                consoleType.contains("Lockhart", ignoreCase = true) ||
                    consoleType.contains("XboxSeriesS", ignoreCase = true) -> ConsoleModel.SERIES_S
                consoleType.contains("Scorpio", ignoreCase = true) ||
                    consoleType.contains("XboxOneX", ignoreCase = true) -> ConsoleModel.ONE_X
                consoleType.contains("Edmonton", ignoreCase = true) ||
                    consoleType.contains("XboxOneS", ignoreCase = true) -> ConsoleModel.ONE_S
                consoleType.contains("Durango", ignoreCase = true) ||
                    consoleType.equals("XboxOne", ignoreCase = true) -> ConsoleModel.XBOX_ONE
                deviceName.contains("Series S", ignoreCase = true) -> ConsoleModel.SERIES_S
                deviceName.contains("Series X", ignoreCase = true) ||
                    deviceName.contains("Series", ignoreCase = true) -> ConsoleModel.SERIES_X
                deviceName.contains("One X", ignoreCase = true) -> ConsoleModel.ONE_X
                deviceName.contains("One S", ignoreCase = true) -> ConsoleModel.ONE_S
                deviceName.contains("Xbox One", ignoreCase = true) -> ConsoleModel.XBOX_ONE
                else -> ConsoleModel.SERIES_X
            }
        }

        internal fun resolveDevicePlatformLabel(
            devicesArr: JSONArray?,
            titleObj: JSONObject? = null
        ): String {
            val titleHistory = titleObj?.optJSONObject("titleHistory")
            val currentDevice = titleHistory?.optString("currentDevice", "").orEmpty().trim()
            if (currentDevice.isNotBlank()) {
                when {
                    currentDevice.contains("XboxSeries", ignoreCase = true) ||
                        currentDevice.contains("Scarlett", ignoreCase = true) -> return "XboxSeries"
                    currentDevice.contains("XboxOne", ignoreCase = true) ||
                        currentDevice.contains("Durango", ignoreCase = true) -> return "XboxOne"
                    currentDevice.contains("Xbox360", ignoreCase = true) -> return "Xbox 360"
                    currentDevice.equals("Xbox", ignoreCase = true) -> return "Xbox"
                }
            }

            if (devicesArr == null || devicesArr.length() == 0) return "Xbox"
            val rawDevices = buildList {
                for (i in 0 until devicesArr.length()) {
                    val v = devicesArr.optString(i, "").trim()
                    if (v.isNotBlank()) add(v)
                }
            }
            if (rawDevices.isEmpty()) return "Xbox"
            return when {
                rawDevices.any {
                    it.equals("XboxSeries", ignoreCase = true) ||
                        it.contains("Scarlett", ignoreCase = true)
                } -> "XboxSeries"
                rawDevices.any {
                    it.equals("XboxOne", ignoreCase = true) ||
                        it.equals("GameCoreXboxOne", ignoreCase = true)
                } -> "XboxOne"
                rawDevices.any { it.contains("Xbox", ignoreCase = true) } -> "Xbox"
                rawDevices.any {
                    it.equals("PC", ignoreCase = true) ||
                        it.equals("Win32", ignoreCase = true)
                } -> "PC"
                else -> rawDevices.first()
            }
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}

object IceCandidatePrioritizer {
    fun isPrivateLanAddress(sdpCandidate: String): Boolean {
        return sdpCandidate.contains("192.168.") ||
            sdpCandidate.contains(" 10.") ||
            Regex("\\s172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(sdpCandidate)
    }

    fun isHostCandidate(sdpCandidate: String): Boolean = sdpCandidate.contains(" typ host")

    fun prioritizeLocalLan(
        candidates: List<IceCandidate>,
        preferredLanIp: String
    ): List<IceCandidate> {
        val cleanIp = preferredLanIp.trim()
        return candidates.sortedBy { candidate ->
            val sdp = candidate.sdp
            when {
                cleanIp.isNotEmpty() && sdp.contains(cleanIp) && isHostCandidate(sdp) -> 0
                isHostCandidate(sdp) && isPrivateLanAddress(sdp) -> 1
                isHostCandidate(sdp) -> 2
                sdp.contains(" typ srflx") -> 3
                else -> 4
            }
        }
    }
}
