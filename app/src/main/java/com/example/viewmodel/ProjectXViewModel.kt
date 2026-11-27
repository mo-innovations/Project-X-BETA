package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AmbientMusicPlayer
import com.example.input.GamepadInputEngine
import com.example.input.PhysicalGamepadHandler
import com.example.model.AchievementItem
import com.example.model.AuthState
import com.example.model.BackgroundAnimationStyle
import com.example.model.BackgroundColorPreset
import com.example.model.BackgroundMotionMode
import com.example.model.ConnectionMode
import com.example.model.ConsoleModel
import com.example.model.ConsolePowerState
import com.example.model.DashboardTab
import com.example.model.DeviceCodeSession
import com.example.model.GamepadState
import com.example.model.HudStatsDetailMode
import com.example.model.LogSeverity
import com.example.model.PhysicalControllerInfo
import com.example.model.RecentGame
import com.example.model.SavedAccountSummary
import com.example.model.StreamAspectRatio
import com.example.model.StreamCodecProfile
import com.example.model.StreamFps
import com.example.model.StreamQualityPreset
import com.example.model.StreamResolution
import com.example.model.StreamSettings
import com.example.model.TelemetrySnapshot
import com.example.model.TerminalLogEntry
import com.example.model.XboxConsole
import com.example.network.WakeOnLanSender
import com.example.network.XHomeSignalingClient
import com.example.network.XboxAuthRepository
import com.example.network.XboxImageCache
import com.example.webrtc.XHomeWebRtcEngine
import org.webrtc.SurfaceViewRenderer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ProjectXUiState(
    val setupCompleted: Boolean = true,
    val ambientMusicEnabled: Boolean = true,
    val customAmbientTrackUri: String = "",
    val customAmbientTrackName: String = "",
    val backgroundMotionMode: BackgroundMotionMode = BackgroundMotionMode.STATIC,
    val backgroundAnimationStyle: BackgroundAnimationStyle = BackgroundAnimationStyle.WAVES,
    val backgroundColorPreset: BackgroundColorPreset = BackgroundColorPreset.GREEN,
    val backgroundCustomHue: Float = BackgroundColorPreset.GREEN.defaultHue,
    val backgroundCustomIntensity: Float = BackgroundColorPreset.GREEN.defaultIntensity,
    val selectedTab: DashboardTab = DashboardTab.HOME,
    val consoles: List<XboxConsole> = emptyList(),
    val selectedConsoleId: String? = null,
    val recentGames: List<RecentGame> = emptyList(),
    val achievements: List<AchievementItem> = emptyList(),
    val savedAccounts: List<SavedAccountSummary> = emptyList(),
    val qualityPreset: StreamQualityPreset = StreamQualityPreset.HD_720P_60,
    val streamSettings: StreamSettings = StreamSettings(),
    val connectionMode: ConnectionMode = ConnectionMode.IDLE,
    val authState: AuthState = AuthState.SignedOut,
    val showAuthModal: Boolean = false,
    val showRegisterConsoleModal: Boolean = false,
    val telemetry: TelemetrySnapshot = TelemetrySnapshot(),
    val logs: List<TerminalLogEntry> = initialLogs(),
    val isTelemetryDrawerExpanded: Boolean = false,
    val connectedControllers: List<PhysicalControllerInfo> = emptyList(),
    val stickDeadzone: Float = 0.12f,
    val rumbleEnabled: Boolean = true,
    val showVirtualOverlay: Boolean = true,
    val wolStatusMessage: String? = null,
    val streamErrorMessage: String? = null,
    val isRefreshingConsoles: Boolean = false,
    val isRefreshingGames: Boolean = false,
    val isRefreshingAchievements: Boolean = false,
    val isWakingConsole: Boolean = false
) {
    val selectedConsole: XboxConsole?
        get() = consoles.firstOrNull { it.id == selectedConsoleId } ?: consoles.firstOrNull()

    val isStreaming: Boolean
        get() = connectionMode == ConnectionMode.STREAMING_XHOME ||
            connectionMode == ConnectionMode.STREAMING_LOOPBACK
}

private fun initialLogs(): List<TerminalLogEntry> = listOf(
    TerminalLogEntry(
        timestamp = "00:00:00",
        tag = "Core",
        message = "Project X initialized | Hardware H.264 Ready | WAKE_LOCK enabled",
        severity = LogSeverity.SUCCESS
    )
)

class ProjectXViewModel(application: Application) : AndroidViewModel(application) {

    private val configPrefs = application.getSharedPreferences("project_x_store", Context.MODE_PRIVATE)
    private val authRepo = XboxAuthRepository(application)
    private val signalingClient = XHomeSignalingClient()
    private val physicalHandler = PhysicalGamepadHandler(application)
    private val inputEngine = GamepadInputEngine(application)

    private val initialAuth = authRepo.loadSavedAuthState()
    private val initialAcctKey = accountKey(initialAuth)
    private val initialConsoles = loadPersistedConsoles(initialAcctKey)
    private val initialBgPreset = loadPersistedBgColorPreset()
    private val initialCustomAudioUri = configPrefs.getString("custom_ambient_uri", "").orEmpty()
    private val initialCustomAudioName = configPrefs.getString("custom_ambient_name", "").orEmpty()
    private val ambientPlayer = AmbientMusicPlayer(viewModelScope)

    @Volatile
    private var isInForeground = false

    private val _uiState = MutableStateFlow(
        ProjectXUiState(
            setupCompleted = configPrefs.getBoolean("setup_completed", false) || (initialAuth is AuthState.SignedIn),
            ambientMusicEnabled = configPrefs.getBoolean("ambient_music_enabled", true),
            customAmbientTrackUri = initialCustomAudioUri,
            customAmbientTrackName = initialCustomAudioName,
            backgroundMotionMode = loadPersistedBgMotionMode(),
            backgroundAnimationStyle = loadPersistedBgAnimationStyle(),
            backgroundColorPreset = initialBgPreset,
            backgroundCustomHue = configPrefs.getFloat("bg_custom_hue", initialBgPreset.defaultHue),
            backgroundCustomIntensity = configPrefs.getFloat("bg_custom_intensity", initialBgPreset.defaultIntensity),
            consoles = initialConsoles,
            selectedConsoleId = configPrefs.getString("selected_console_id", null)
                ?.takeIf { id -> initialConsoles.any { it.id == id } }
                ?: initialConsoles.firstOrNull()?.id,
            recentGames = loadPersistedRecentGames(initialAcctKey),
            achievements = loadPersistedAchievements(initialAcctKey),
            savedAccounts = authRepo.loadSavedAccounts(),
            qualityPreset = loadPersistedPreset(),
            streamSettings = loadPersistedStreamSettings(),
            authState = initialAuth
        )
    )
    val uiState: StateFlow<ProjectXUiState> = _uiState.asStateFlow()

    @Volatile
    var currentGamepadState: GamepadState = GamepadState()
        private set

    private var authPollJob: Job? = null
    private val authWakeChannel = Channel<Unit>(Channel.CONFLATED)
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Volatile
    private var activeRtcEngine: XHomeWebRtcEngine? = null
    private var keepaliveJob: Job? = null

    init {
        if (initialCustomAudioUri.isNotBlank()) {
            ambientPlayer.configureCustomTrack(application, initialCustomAudioUri)
        }
        physicalHandler.registerListener { controllers ->
            _uiState.update { state ->
                val autoHideOverlay = if (controllers.isNotEmpty() && state.connectedControllers.isEmpty()) {
                    false
                } else {
                    state.showVirtualOverlay
                }
                state.copy(
                    connectedControllers = controllers,
                    showVirtualOverlay = autoHideOverlay
                )
            }
            if (controllers.isNotEmpty()) {
                appendLog("Input", "Gamepad connected: ${controllers.first().name}", LogSeverity.SUCCESS)
            }
        }
        if (initialAuth is AuthState.SignedIn) {
            prefetchAvatar(initialAuth)
            if (initialAuth.avatarUrl.isBlank()) {
                refreshProfile()
            }
        }
        appendLog("Core", "Greenlight Native WebRTC engine initialized", LogSeverity.SUCCESS)
    }

    fun completeSetup() {
        configPrefs.edit().putBoolean("setup_completed", true).apply()
        _uiState.update { it.copy(setupCompleted = true) }
        appendLog("Setup", "First-time setup completed", LogSeverity.SUCCESS)
    }

    fun setAppInForeground(inForeground: Boolean) {
        isInForeground = inForeground
        syncAmbientMusic()
    }

    fun setAmbientMusicEnabled(enabled: Boolean) {
        configPrefs.edit().putBoolean("ambient_music_enabled", enabled).apply()
        _uiState.update { it.copy(ambientMusicEnabled = enabled) }
        syncAmbientMusic()
    }

    fun setCustomAmbientTrack(uriString: String, displayName: String) {
        val cleanName = displayName.trim().ifBlank { "Custom Audio Track" }
        configPrefs.edit()
            .putString("custom_ambient_uri", uriString)
            .putString("custom_ambient_name", cleanName)
            .putBoolean("ambient_music_enabled", true)
            .apply()
        ambientPlayer.configureCustomTrack(getApplication(), uriString)
        _uiState.update {
            it.copy(
                ambientMusicEnabled = true,
                customAmbientTrackUri = uriString,
                customAmbientTrackName = cleanName
            )
        }
        syncAmbientMusic()
        appendLog("Audio", "Loaded custom ambient track: $cleanName", LogSeverity.SUCCESS)
    }

    fun clearCustomAmbientTrack() {
        configPrefs.edit()
            .remove("custom_ambient_uri")
            .remove("custom_ambient_name")
            .apply()
        ambientPlayer.configureCustomTrack(getApplication(), "")
        _uiState.update {
            it.copy(customAmbientTrackUri = "", customAmbientTrackName = "")
        }
        syncAmbientMusic()
        appendLog("Audio", "Switched back to built-in PS5-style celestial ambience", LogSeverity.INFO)
    }

    fun setBackgroundMotionMode(mode: BackgroundMotionMode) {
        configPrefs.edit().putString("bg_motion_mode", mode.name).apply()
        _uiState.update { it.copy(backgroundMotionMode = mode) }
    }

    fun setBackgroundAnimationStyle(style: BackgroundAnimationStyle) {
        configPrefs.edit().putString("bg_animation_style", style.name).apply()
        _uiState.update { it.copy(backgroundAnimationStyle = style) }
    }

    fun setBackgroundColorPreset(preset: BackgroundColorPreset) {
        val nextHue = if (preset == BackgroundColorPreset.CUSTOM) {
            _uiState.value.backgroundCustomHue
        } else {
            preset.defaultHue
        }
        val nextIntensity = if (preset == BackgroundColorPreset.CUSTOM) {
            _uiState.value.backgroundCustomIntensity
        } else {
            preset.defaultIntensity
        }
        configPrefs.edit()
            .putString("bg_color_preset", preset.name)
            .putFloat("bg_custom_hue", nextHue)
            .putFloat("bg_custom_intensity", nextIntensity)
            .apply()
        _uiState.update {
            it.copy(
                backgroundColorPreset = preset,
                backgroundCustomHue = nextHue,
                backgroundCustomIntensity = nextIntensity
            )
        }
    }

    fun setBackgroundCustomColor(hue: Float, intensity: Float) {
        val cleanHue = hue.coerceIn(0f, 360f)
        val cleanIntensity = intensity.coerceIn(0.15f, 1.0f)
        configPrefs.edit()
            .putString("bg_color_preset", BackgroundColorPreset.CUSTOM.name)
            .putFloat("bg_custom_hue", cleanHue)
            .putFloat("bg_custom_intensity", cleanIntensity)
            .apply()
        _uiState.update {
            it.copy(
                backgroundColorPreset = BackgroundColorPreset.CUSTOM,
                backgroundCustomHue = cleanHue,
                backgroundCustomIntensity = cleanIntensity
            )
        }
    }

    private fun syncAmbientMusic() {
        val s = _uiState.value
        ambientPlayer.setActive(s.ambientMusicEnabled && isInForeground && !s.isStreaming)
    }

    fun onAppResumed() {
        val currentAuth = _uiState.value.authState
        if (currentAuth is AuthState.AwaitingUserCode) {
            checkAuthNow()
        }
    }

    fun selectTab(tab: DashboardTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectConsole(consoleId: String) {
        configPrefs.edit().putString("selected_console_id", consoleId).apply()
        _uiState.update { it.copy(selectedConsoleId = consoleId, wolStatusMessage = null) }
        val selected = _uiState.value.selectedConsole
        if (selected != null) {
            appendLog("Console", "Focused ${selected.name} (${selected.lanIp})", LogSeverity.INFO)
        }
    }

    fun openRegisterConsoleModal() {
        _uiState.update { it.copy(showRegisterConsoleModal = true) }
    }

    fun dismissRegisterConsoleModal() {
        _uiState.update { it.copy(showRegisterConsoleModal = false) }
    }

    fun registerConsole(
        name: String,
        model: ConsoleModel,
        liveId: String,
        lanIp: String
    ) {
        val cleanName = name.trim().ifBlank { "${model.displayName} (LAN)" }
        val cleanLiveId = liveId.trim().uppercase()
        val cleanIp = lanIp.trim().ifBlank { "" }
        val newConsole = XboxConsole(
            id = "custom-${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            model = model,
            liveId = cleanLiveId,
            lanIp = cleanIp,
            powerState = ConsolePowerState.STANDBY,
            isRegisteredFromXHome = false
        )
        val updated = _uiState.value.consoles + newConsole
        persistConsoles(updated, accountKey(_uiState.value.authState))
        configPrefs.edit().putString("selected_console_id", newConsole.id).apply()
        _uiState.update {
            it.copy(
                consoles = updated,
                selectedConsoleId = newConsole.id,
                showRegisterConsoleModal = false,
                wolStatusMessage = "Registered ${newConsole.name}"
            )
        }
        appendLog("Console", "Registered & saved: ${newConsole.name} (${newConsole.liveId})", LogSeverity.SUCCESS)
    }

    fun removeConsole(consoleId: String) {
        val updated = _uiState.value.consoles.filterNot { it.id == consoleId }
        val nextSelected = if (_uiState.value.selectedConsoleId == consoleId) {
            updated.firstOrNull()?.id
        } else {
            _uiState.value.selectedConsoleId
        }
        persistConsoles(updated, accountKey(_uiState.value.authState))
        configPrefs.edit().putString("selected_console_id", nextSelected).apply()
        _uiState.update {
            it.copy(
                consoles = updated,
                selectedConsoleId = nextSelected,
                wolStatusMessage = null
            )
        }
        appendLog("Console", "Removed console from saved list", LogSeverity.INFO)
    }

    fun updateConsoleLanIp(consoleId: String, newIp: String) {
        val updated = _uiState.value.consoles.map { console ->
            if (console.id == consoleId) console.copy(lanIp = newIp) else console
        }
        persistConsoles(updated, accountKey(_uiState.value.authState))
        _uiState.update { it.copy(consoles = updated) }
    }

    fun updateConsoleLiveId(consoleId: String, newLiveId: String) {
        val updated = _uiState.value.consoles.map { console ->
            if (console.id == consoleId) console.copy(liveId = newLiveId.uppercase()) else console
        }
        persistConsoles(updated, accountKey(_uiState.value.authState))
        _uiState.update { it.copy(consoles = updated) }
    }

    fun selectQualityPreset(preset: StreamQualityPreset) {
        val nextSettings = _uiState.value.streamSettings.copy(
            resolution = if (preset == StreamQualityPreset.FHD_1080P_60) StreamResolution.P1080 else StreamResolution.P720,
            targetFps = StreamFps.FPS_60,
            maxBitrateMbps = if (preset == StreamQualityPreset.FHD_1080P_60) 16 else 12
        )
        configPrefs.edit().putString("quality_preset", preset.name).apply()
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(qualityPreset = preset, streamSettings = nextSettings) }
        appendLog("Codec", "Quality set to ${nextSettings.summaryLabel}", LogSeverity.INFO)
    }

    fun setStreamResolution(resolution: StreamResolution) {
        val nextSettings = _uiState.value.streamSettings.copy(resolution = resolution)
        val syncedPreset = if (resolution == StreamResolution.P1080) {
            StreamQualityPreset.FHD_1080P_60
        } else {
            StreamQualityPreset.HD_720P_60
        }
        persistStreamSettings(nextSettings)
        configPrefs.edit().putString("quality_preset", syncedPreset.name).apply()
        _uiState.update { it.copy(streamSettings = nextSettings, qualityPreset = syncedPreset) }
        appendLog("Codec", "Stream resolution set to ${resolution.label}", LogSeverity.INFO)
    }

    fun setStreamFps(fps: StreamFps) {
        val nextSettings = _uiState.value.streamSettings.copy(targetFps = fps)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
        appendLog("Codec", "Target FPS set to ${fps.label}", LogSeverity.INFO)
    }

    fun setStreamBitrateMbps(mbps: Int) {
        val cleanMbps = mbps.coerceIn(2, 20)
        val nextSettings = _uiState.value.streamSettings.copy(maxBitrateMbps = cleanMbps)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
    }

    fun setStreamCodecProfile(profile: StreamCodecProfile) {
        val nextSettings = _uiState.value.streamSettings.copy(codecProfile = profile)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
        appendLog("Codec", "H.264 profile: ${profile.label}", LogSeverity.INFO)
    }

    fun setLowLatencyJitterBuffer(enabled: Boolean) {
        val nextSettings = _uiState.value.streamSettings.copy(lowLatencyJitterBuffer = enabled)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
    }

    fun setStreamAspectRatio(ratio: StreamAspectRatio) {
        val nextSettings = _uiState.value.streamSettings.copy(aspectRatio = ratio)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
    }

    fun setHudStatsMode(mode: HudStatsDetailMode) {
        val nextSettings = _uiState.value.streamSettings.copy(hudMode = mode)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
    }

    fun setStreamAudioVolume(volume: Float) {
        val clean = volume.coerceIn(0f, 2f)
        val nextSettings = _uiState.value.streamSettings.copy(audioVolume = clean)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
    }

    fun setVirtualPadOpacity(opacity: Float) {
        val clean = opacity.coerceIn(0.25f, 1.0f)
        val nextSettings = _uiState.value.streamSettings.copy(virtualPadOpacity = clean)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
    }

    fun setVirtualPadScale(scale: Float) {
        val clean = scale.coerceIn(0.70f, 1.35f)
        val nextSettings = _uiState.value.streamSettings.copy(virtualPadScale = clean)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
    }

    fun setClarityBoostLevel(level: Int) {
        val clean = level.coerceIn(0, 3)
        val nextSettings = _uiState.value.streamSettings.copy(clarityBoostLevel = clean)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
    }

    fun setUseBetterXCloudTouch(enabled: Boolean) {
        val nextSettings = _uiState.value.streamSettings.copy(useBetterXCloudTouch = enabled)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
    }

    fun setSkipSplashVideos(enabled: Boolean) {
        val nextSettings = _uiState.value.streamSettings.copy(skipSplashVideos = enabled)
        persistStreamSettings(nextSettings)
        _uiState.update { it.copy(streamSettings = nextSettings) }
    }

    fun toggleTelemetryDrawer() {
        _uiState.update { it.copy(isTelemetryDrawerExpanded = !it.isTelemetryDrawerExpanded) }
    }

    fun setStickDeadzone(deadzone: Float) {
        _uiState.update { it.copy(stickDeadzone = deadzone.coerceIn(0.05f, 0.30f)) }
    }

    fun setRumbleEnabled(enabled: Boolean) {
        _uiState.update { it.copy(rumbleEnabled = enabled) }
    }

    fun toggleVirtualOverlay() {
        val nextVisible = !_uiState.value.showVirtualOverlay
        _uiState.update { it.copy(showVirtualOverlay = nextVisible) }
    }

    fun sendWakeOnLan(consoleId: String? = _uiState.value.selectedConsoleId) {
        val console = _uiState.value.consoles.firstOrNull { it.id == consoleId }
            ?: _uiState.value.selectedConsole
            ?: return
        if (_uiState.value.isWakingConsole) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isWakingConsole = true,
                    wolStatusMessage = "Sending SmartGlass Wake to ${console.name}..."
                )
            }
            appendLog("WoL", "Sending SmartGlass UDP:5050 -> ${console.lanIp.ifBlank { "LAN Broadcast" }} (${console.liveId})", LogSeverity.INFO)

            WakeOnLanSender.wakeAndVerify(
                targetIp = console.lanIp,
                liveId = console.liveId,
                onProgress = { statusText ->
                    _uiState.update { it.copy(wolStatusMessage = statusText) }
                },
                cloudOnlineCheck = {
                    val gsToken = authRepo.getStreamingToken() ?: return@wakeAndVerify false
                    val list = signalingClient.fetchRegisteredConsoles(gsToken, authRepo.getXHomeBaseUrl())
                        .getOrNull() ?: return@wakeAndVerify false
                    list.any {
                        it.liveId.equals(console.liveId, ignoreCase = true) &&
                            it.powerState == ConsolePowerState.ONLINE
                    }
                }
            ).onSuccess { result ->
                val resolvedIp = result.discoveredIp ?: console.lanIp
                val nextPower = if (result.verifiedOnline) {
                    ConsolePowerState.ONLINE
                } else {
                    ConsolePowerState.STANDBY
                }
                val updated = _uiState.value.consoles.map {
                    if (it.id == console.id) {
                        it.copy(powerState = nextPower, lanIp = resolvedIp)
                    } else {
                        it
                    }
                }
                persistConsoles(updated, accountKey(_uiState.value.authState))
                val msg = if (result.verifiedOnline) {
                    val ipTag = if (resolvedIp.isNotBlank()) " at $resolvedIp" else ""
                    "${console.name} confirmed ONLINE$ipTag (${result.verificationSource})"
                } else {
                    "Wake sent (${result.bytesSent}B), no ONLINE reply yet. Verify Instant-On & Remote Features on Xbox."
                }
                _uiState.update {
                    it.copy(
                        consoles = updated,
                        isWakingConsole = false,
                        wolStatusMessage = msg
                    )
                }
                appendLog("WoL", msg, if (result.verifiedOnline) LogSeverity.SUCCESS else LogSeverity.WARN)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isWakingConsole = false,
                        wolStatusMessage = err.message ?: "Wake-on-LAN failed"
                    )
                }
                appendLog("WoL", "Failed: ${err.message}", LogSeverity.ERROR)
            }
        }
    }

    fun scanConsoleLan(consoleId: String? = _uiState.value.selectedConsoleId) {
        val console = _uiState.value.consoles.firstOrNull { it.id == consoleId }
            ?: _uiState.value.selectedConsole
            ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(wolStatusMessage = "Scanning LAN UDP:5050 for ${console.name}...") }
            val found = WakeOnLanSender.discoverConsolesOnLan(preferredIp = console.lanIp)
            val match = found.firstOrNull { it.liveId.equals(console.liveId, ignoreCase = true) }
                ?: found.firstOrNull()
            if (match != null) {
                val updated = _uiState.value.consoles.map {
                    if (it.id == console.id) {
                        it.copy(lanIp = match.ipAddress, powerState = ConsolePowerState.ONLINE)
                    } else {
                        it
                    }
                }
                persistConsoles(updated, accountKey(_uiState.value.authState))
                _uiState.update {
                    it.copy(
                        consoles = updated,
                        wolStatusMessage = "Found ${console.name} ONLINE at ${match.ipAddress}"
                    )
                }
                appendLog("LAN", "SmartGlass UDP discovered ${console.name} at ${match.ipAddress}", LogSeverity.SUCCESS)
            } else {
                _uiState.update {
                    it.copy(wolStatusMessage = "No LAN UDP reply from ${console.name} (ensure phone & Xbox are on same Wi-Fi)")
                }
            }
        }
    }

    fun openAuthModal() {
        _uiState.update { it.copy(showAuthModal = true) }
        val current = _uiState.value.authState
        if (current is AuthState.SignedOut || current is AuthState.Error) {
            startDeviceCodeSignIn()
        }
    }

    fun openAddAccountModal() {
        _uiState.update { it.copy(showAuthModal = true) }
        startDeviceCodeSignIn()
    }

    fun dismissAuthModal() {
        authPollJob?.cancel()
        authPollJob = null
        _uiState.update { state ->
            val resetAuth = if (state.authState is AuthState.AwaitingUserCode || state.authState is AuthState.Error) {
                authRepo.loadSavedAuthState()
            } else {
                state.authState
            }
            state.copy(showAuthModal = false, authState = resetAuth)
        }
    }

    fun startDeviceCodeSignIn() {
        authPollJob?.cancel()
        authPollJob = viewModelScope.launch {
            _uiState.update { it.copy(authState = AuthState.SignedOut) }
            appendLog("XAL", "Requesting Microsoft device code (login.live.com)...", LogSeverity.INFO)
            authRepo.startDeviceCodeFlow()
                .onSuccess { session ->
                    _uiState.update { it.copy(authState = AuthState.AwaitingUserCode(session)) }
                    appendLog("XAL", "Device code ready: ${session.userCode} (${session.expiresInSec / 60}m)", LogSeverity.SUCCESS)
                    runPollingLoop(session, pollImmediatelyFirst = false)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(authState = AuthState.Error(err.message ?: "Could not reach login.live.com")) }
                    appendLog("XAL", "Device code request failed: ${err.message}", LogSeverity.ERROR)
                }
        }
    }

    /**
     * Wakes the single running poll coroutine via CONFLATED Channel instead of cancelling
     * in-flight HTTP calls, preventing Microsoft's single-use device_code from being race-consumed.
     */
    fun checkAuthNow() {
        val awaiting = _uiState.value.authState as? AuthState.AwaitingUserCode ?: return
        if (authPollJob?.isActive == true) {
            authWakeChannel.trySend(Unit)
        } else {
            authPollJob = viewModelScope.launch {
                runPollingLoop(awaiting.session, pollImmediatelyFirst = true)
            }
        }
    }

    private suspend fun runPollingLoop(
        session: DeviceCodeSession,
        pollImmediatelyFirst: Boolean
    ) {
        while (authWakeChannel.tryReceive().isSuccess) {
            // Drain stale wake signals before starting
        }
        authRepo.pollForDeviceTokenAndExchange(
            session = session,
            pollImmediatelyFirst = pollImmediatelyFirst,
            wakeSignal = authWakeChannel
        ).onSuccess { signedIn ->
            handleSignedIn(signedIn)
        }.onFailure { err ->
            if (_uiState.value.authState !is AuthState.SignedIn) {
                _uiState.update { it.copy(authState = AuthState.Error(err.message ?: "Auth failed")) }
                appendLog("XAL", "Sign-in error: ${err.message}", LogSeverity.ERROR)
            }
        }
    }

    private fun handleSignedIn(signedIn: AuthState.SignedIn) {
        val acctKey = accountKey(signedIn)
        val mergedConsoles = (_uiState.value.consoles + loadPersistedConsoles(acctKey))
            .distinctBy { it.liveId.uppercase() }
        persistConsoles(mergedConsoles, acctKey)

        _uiState.update {
            it.copy(
                authState = signedIn,
                savedAccounts = authRepo.loadSavedAccounts(),
                consoles = mergedConsoles,
                selectedConsoleId = it.selectedConsoleId
                    ?.takeIf { id -> mergedConsoles.any { c -> c.id == id } }
                    ?: mergedConsoles.firstOrNull()?.id,
                recentGames = loadPersistedRecentGames(acctKey),
                achievements = loadPersistedAchievements(acctKey),
                showAuthModal = false
            )
        }
        appendLog("XAL", "Signed in as ${signedIn.gamertag}; synced account storage", LogSeverity.SUCCESS)
        prefetchAvatar(signedIn)

        if (signedIn.hasStreamingToken) {
            refreshConsolesFromXHome()
        }
        refreshRecentlyPlayedGames()
        refreshAchievements()
    }

    fun switchAccount(xuidOrTag: String) {
        viewModelScope.launch {
            appendLog("XAL", "Switching active Xbox account...", LogSeverity.INFO)
            authRepo.switchActiveAccount(xuidOrTag)
                .onSuccess { switched ->
                    val key = accountKey(switched)
                    val consoles = loadPersistedConsoles(key)
                    _uiState.update {
                        it.copy(
                            authState = switched,
                            savedAccounts = authRepo.loadSavedAccounts(),
                            consoles = consoles,
                            selectedConsoleId = consoles.firstOrNull()?.id,
                            recentGames = loadPersistedRecentGames(key),
                            achievements = loadPersistedAchievements(key),
                            wolStatusMessage = "Switched to ${switched.gamertag}"
                        )
                    }
                    prefetchAvatar(switched)
                    if (switched.hasStreamingToken) {
                        refreshConsolesFromXHome()
                    }
                    refreshRecentlyPlayedGames()
                    refreshAchievements()
                }
                .onFailure { err ->
                    appendLog("XAL", "Account switch failed: ${err.message}", LogSeverity.ERROR)
                }
        }
    }

    fun removeSavedAccount(xuidOrTag: String) {
        val nextAuth = authRepo.removeSavedAccount(xuidOrTag)
        val nextKey = accountKey(nextAuth)
        val consoles = loadPersistedConsoles(nextKey)
        _uiState.update {
            it.copy(
                authState = nextAuth,
                savedAccounts = authRepo.loadSavedAccounts(),
                consoles = consoles,
                selectedConsoleId = consoles.firstOrNull()?.id,
                recentGames = loadPersistedRecentGames(nextKey),
                achievements = loadPersistedAchievements(nextKey)
            )
        }
        if (nextAuth is AuthState.SignedIn) {
            prefetchAvatar(nextAuth)
        }
        appendLog("XAL", "Removed saved Xbox account", LogSeverity.WARN)
    }

    fun signOut() {
        authRepo.clearSession()
        val nextAuth = authRepo.loadSavedAuthState()
        val nextKey = accountKey(nextAuth)
        val consoles = loadPersistedConsoles(nextKey)
        _uiState.update {
            it.copy(
                authState = nextAuth,
                savedAccounts = authRepo.loadSavedAccounts(),
                consoles = consoles,
                selectedConsoleId = consoles.firstOrNull()?.id,
                recentGames = loadPersistedRecentGames(nextKey),
                achievements = loadPersistedAchievements(nextKey)
            )
        }
        appendLog("XAL", "Signed out active account", LogSeverity.WARN)
    }

    fun refreshProfile() {
        viewModelScope.launch {
            authRepo.refreshProfile().onSuccess { updated ->
                _uiState.update {
                    it.copy(
                        authState = updated,
                        savedAccounts = authRepo.loadSavedAccounts()
                    )
                }
                prefetchAvatar(updated)
            }
        }
    }

    fun refreshConsolesFromXHome() {
        val gsToken = authRepo.getStreamingToken()
        if (gsToken.isNullOrBlank()) {
            openAuthModal()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingConsoles = true) }
            appendLog("xHome", "Syncing consoles & probing LAN UDP:5050...", LogSeverity.INFO)

            val lanDiscovered = WakeOnLanSender.discoverConsolesOnLan()
            signalingClient.fetchRegisteredConsoles(gsToken, authRepo.getXHomeBaseUrl())
                .onSuccess { remoteList ->
                    val existing = _uiState.value.consoles
                    val mergedRemote = remoteList.map { remote ->
                        val localMatch = existing.firstOrNull {
                            it.liveId.equals(remote.liveId, ignoreCase = true)
                        }
                        val lanMatch = lanDiscovered.firstOrNull {
                            it.liveId.equals(remote.liveId, ignoreCase = true)
                        } ?: lanDiscovered.firstOrNull { lan ->
                            !lan.name.isNullOrBlank() && lan.name.equals(remote.name, ignoreCase = true)
                        }
                        val bestIp = lanMatch?.ipAddress?.takeIf { it.isNotBlank() }
                            ?: localMatch?.lanIp?.takeIf { it.isNotBlank() }
                            ?: ""
                        val bestPower = if (lanMatch != null) ConsolePowerState.ONLINE else remote.powerState
                        remote.copy(lanIp = bestIp, powerState = bestPower)
                    }
                    val customLocal = existing.filter { local ->
                        mergedRemote.none { it.liveId.equals(local.liveId, ignoreCase = true) }
                    }
                    val combined = mergedRemote + customLocal
                    persistConsoles(combined, accountKey(_uiState.value.authState))
                    _uiState.update {
                        it.copy(
                            consoles = combined,
                            selectedConsoleId = it.selectedConsoleId
                                ?.takeIf { id -> combined.any { c -> c.id == id } }
                                ?: combined.firstOrNull()?.id,
                            isRefreshingConsoles = false
                        )
                    }
                    appendLog("xHome", "Synced ${mergedRemote.size} console(s); LAN replies: ${lanDiscovered.size}", LogSeverity.SUCCESS)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isRefreshingConsoles = false) }
                    appendLog("xHome", "Console sync failed: ${err.message}", LogSeverity.ERROR)
                }
        }
    }

    fun refreshRecentlyPlayedGames() {
        val xblHeader = authRepo.getXblAuthHeader().orEmpty()
        val xuid = authRepo.getSavedXuid()
        if (xblHeader.isBlank() || xuid.isBlank()) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingGames = true) }
            signalingClient.fetchRecentlyPlayedGames(xuid, xblHeader)
                .onSuccess { remoteGames ->
                    val localStreamEntries = _uiState.value.recentGames.filter {
                        it.titleId.startsWith("stream-")
                    }
                    val merged = (remoteGames + localStreamEntries)
                        .distinctBy { it.titleId }
                        .take(12)
                    persistRecentGames(merged, accountKey(_uiState.value.authState))
                    _uiState.update { it.copy(recentGames = merged, isRefreshingGames = false) }
                    appendLog("Xbox", "Synced ${remoteGames.size} recent game(s) & caching compact box art", LogSeverity.SUCCESS)
                    // Pre-cache small 192px JPEG thumbnails onto phone storage
                    val ctx = getApplication<Application>()
                    remoteGames.forEach { game ->
                        if (game.imageUrl.isNotBlank()) {
                            launch {
                                XboxImageCache.loadOrFetchSmall(
                                    context = ctx,
                                    url = game.imageUrl,
                                    cacheKey = "title_${game.titleId}",
                                    maxDimPx = 192
                                )
                            }
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isRefreshingGames = false) }
                    appendLog("Xbox", "Recent games sync failed: ${err.message}", LogSeverity.ERROR)
                }
        }
    }

    fun refreshAchievements() {
        val xblHeader = authRepo.getXblAuthHeader().orEmpty()
        val xuid = authRepo.getSavedXuid()
        if (xblHeader.isBlank() || xuid.isBlank()) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingAchievements = true) }
            signalingClient.fetchAchievements(xuid, xblHeader)
                .onSuccess { list ->
                    persistAchievements(list, accountKey(_uiState.value.authState))
                    _uiState.update { it.copy(achievements = list, isRefreshingAchievements = false) }
                    appendLog("Xbox", "Synced ${list.size} achievement(s)", LogSeverity.SUCCESS)
                    val ctx = getApplication<Application>()
                    list.take(15).forEach { ach ->
                        if (ach.iconUrl.isNotBlank()) {
                            launch {
                                XboxImageCache.loadOrFetchSmall(
                                    context = ctx,
                                    url = ach.iconUrl,
                                    cacheKey = "ach_${ach.id}",
                                    maxDimPx = 128
                                )
                            }
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isRefreshingAchievements = false) }
                    appendLog("Xbox", "Achievements sync failed: ${err.message}", LogSeverity.ERROR)
                }
        }
    }

    fun clearRecentGames() {
        persistRecentGames(emptyList(), accountKey(_uiState.value.authState))
        _uiState.update { it.copy(recentGames = emptyList()) }
    }

    fun startXHomeStream(consoleId: String? = _uiState.value.selectedConsoleId) {
        val console = _uiState.value.consoles.firstOrNull { it.id == consoleId }
            ?: _uiState.value.selectedConsole

        _uiState.update {
            it.copy(
                connectionMode = ConnectionMode.SIGNALING,
                streamErrorMessage = null
            )
        }
        viewModelScope.launch {
            if (console != null && console.powerState != ConsolePowerState.ONLINE) {
                appendLog("WoL", "Waking ${console.name} before starting Greenlight WebRTC stream...", LogSeverity.INFO)
                runCatching { WakeOnLanSender.sendWakePacket(console.lanIp, console.liveId) }
                delay(700L)
            }

            if (console != null) {
                recordLocalStreamLaunch(console)
            }

            val gsToken = authRepo.getStreamingToken().orEmpty()
            val serverId = console?.liveId.orEmpty().ifBlank { console?.id.orEmpty() }

            val engine = XHomeWebRtcEngine(
                context = getApplication(),
                scope = viewModelScope,
                gamepadProvider = { currentGamepadState },
                onTelemetry = { snapshot ->
                    _uiState.update { it.copy(telemetry = snapshot) }
                },
                onVibration = { cmd ->
                    if (_uiState.value.rumbleEnabled) {
                        inputEngine.triggerDualRumble(cmd.durationMs, cmd.strongMotor, cmd.weakMotor)
                    }
                },
                onLog = { tag, msg, severity ->
                    appendLog(tag, msg, severity)
                },
                onConnectionClosed = { reason ->
                    appendLog("WebRTC", "Stream disconnected: $reason", LogSeverity.WARN)
                    stopStreaming()
                }
            )
            activeRtcEngine?.stopAndRelease()
            activeRtcEngine = engine

            if (gsToken.isNotBlank() && serverId.isNotBlank()) {
                appendLog("Signaling", "Provisioning xHome session on Xbox console ($serverId)...", LogSeverity.INFO)
                val sessionRes = signalingClient.initiateHomePlaySession(
                    gsToken = gsToken,
                    serverId = serverId,
                    preset = _uiState.value.qualityPreset
                )

                if (sessionRes.isSuccess) {
                    val sessionInfo = sessionRes.getOrThrow()
                    appendLog("Signaling", "Session ready (${sessionInfo.state}). Generating WebRTC offer...", LogSeverity.SUCCESS)
                    val offerRes = engine.createXHomeOffer(
                        settings = _uiState.value.streamSettings,
                        physicalPadConnected = _uiState.value.connectedControllers.isNotEmpty()
                    )

                    if (offerRes.isSuccess) {
                        val (offerSdp, localCandidates) = offerRes.getOrThrow()
                        val exchangeRes = signalingClient.exchangeSdpAndIce(
                            gsToken = gsToken,
                            sessionPath = sessionInfo.sessionPath,
                            offerSdp = offerSdp,
                            localCandidates = localCandidates,
                            preferredLanIp = console?.lanIp.orEmpty(),
                            baseUrl = sessionInfo.effectiveBaseUrl
                        )

                        if (exchangeRes.isSuccess) {
                            val exchangeData = exchangeRes.getOrThrow()
                            engine.setRemoteAnswerAndCandidates(
                                answerSdp = exchangeData.answerSdp,
                                remoteCandidates = exchangeData.iceCandidates
                            )
                            _uiState.update {
                                it.copy(
                                    connectionMode = ConnectionMode.STREAMING_XHOME,
                                    telemetry = TelemetrySnapshot(
                                        decoderName = "Hardware H.264 (Greenlight)",
                                        resolutionLabel = "${it.streamSettings.resolution.width}x${it.streamSettings.resolution.height}",
                                        iceCandidateType = "WebRTC"
                                    )
                                )
                            }
                            syncAmbientMusic()
                            startKeepaliveLoop(gsToken, sessionInfo.sessionPath, sessionInfo.effectiveBaseUrl)
                            appendLog("WebRTC", "Greenlight stream connected (${console?.name ?: "Xbox"})", LogSeverity.SUCCESS)
                            return@launch
                        } else {
                            val err = exchangeRes.exceptionOrNull()?.message ?: "SDP Exchange failed"
                            appendLog("Signaling", err, LogSeverity.ERROR)
                            _uiState.update { it.copy(connectionMode = ConnectionMode.IDLE, streamErrorMessage = err) }
                            engine.stopAndRelease()
                            return@launch
                        }
                    } else {
                        val err = offerRes.exceptionOrNull()?.message ?: "Failed to create local offer"
                        appendLog("WebRTC", err, LogSeverity.ERROR)
                        _uiState.update { it.copy(connectionMode = ConnectionMode.IDLE, streamErrorMessage = err) }
                        engine.stopAndRelease()
                        return@launch
                    }
                } else {
                    val err = sessionRes.exceptionOrNull()?.message ?: "Failed to initiate session"
                    appendLog("Signaling", err, LogSeverity.ERROR)
                    _uiState.update { it.copy(connectionMode = ConnectionMode.IDLE, streamErrorMessage = err) }
                    engine.stopAndRelease()
                    return@launch
                }
            } else {
                appendLog("Stream", "No active Xbox Live streaming token. Starting Greenlight WebRTC loopback self-test...", LogSeverity.WARN)
                val loopbackRes = engine.startLoopbackTest(
                    settings = _uiState.value.streamSettings,
                    physicalPadConnected = _uiState.value.connectedControllers.isNotEmpty()
                )
                if (loopbackRes.isSuccess) {
                    _uiState.update {
                        it.copy(
                            connectionMode = ConnectionMode.STREAMING_LOOPBACK,
                            telemetry = TelemetrySnapshot(
                                decoderName = "Hardware H.264 (Greenlight Loopback)",
                                resolutionLabel = "${it.streamSettings.resolution.width}x${it.streamSettings.resolution.height}",
                                iceCandidateType = "host"
                            )
                        )
                    }
                    syncAmbientMusic()
                    appendLog("Loopback", "Greenlight WebRTC Loopback Self-Test active", LogSeverity.SUCCESS)
                } else {
                    val err = loopbackRes.exceptionOrNull()?.message ?: "Loopback failed"
                    _uiState.update { it.copy(connectionMode = ConnectionMode.IDLE, streamErrorMessage = err) }
                    engine.stopAndRelease()
                }
            }
        }
    }

    fun startLoopbackSelfTest() {
        val activeSettings = _uiState.value.streamSettings
        _uiState.update {
            it.copy(
                connectionMode = ConnectionMode.SIGNALING,
                streamErrorMessage = null
            )
        }
        viewModelScope.launch {
            val engine = XHomeWebRtcEngine(
                context = getApplication(),
                scope = viewModelScope,
                gamepadProvider = { currentGamepadState },
                onTelemetry = { snapshot ->
                    _uiState.update { it.copy(telemetry = snapshot) }
                },
                onVibration = { cmd ->
                    if (_uiState.value.rumbleEnabled) {
                        inputEngine.triggerDualRumble(cmd.durationMs, cmd.strongMotor, cmd.weakMotor)
                    }
                },
                onLog = { tag, msg, severity ->
                    appendLog(tag, msg, severity)
                },
                onConnectionClosed = { reason ->
                    appendLog("WebRTC", "Stream disconnected: $reason", LogSeverity.WARN)
                    stopStreaming()
                }
            )
            activeRtcEngine?.stopAndRelease()
            activeRtcEngine = engine

            val loopbackRes = engine.startLoopbackTest(
                settings = activeSettings,
                physicalPadConnected = _uiState.value.connectedControllers.isNotEmpty()
            )
            if (loopbackRes.isSuccess) {
                _uiState.update {
                    it.copy(
                        connectionMode = ConnectionMode.STREAMING_LOOPBACK,
                        telemetry = TelemetrySnapshot(
                            decoderName = "Hardware H.264 (Greenlight Loopback)",
                            resolutionLabel = "${activeSettings.resolution.width}x${activeSettings.resolution.height}",
                            iceCandidateType = "host"
                        )
                    )
                }
                syncAmbientMusic()
                appendLog("Loopback", "Started Greenlight WebRTC Loopback Self-Test (${activeSettings.targetFps.fps} FPS)", LogSeverity.SUCCESS)
            } else {
                val err = loopbackRes.exceptionOrNull()?.message ?: "Loopback failed"
                _uiState.update { it.copy(connectionMode = ConnectionMode.IDLE, streamErrorMessage = err) }
                engine.stopAndRelease()
            }
        }
    }

    fun stopStreaming() {
        keepaliveJob?.cancel()
        keepaliveJob = null
        activeRtcEngine?.stopAndRelease()
        activeRtcEngine = null
        currentGamepadState = GamepadState()
        _uiState.update {
            it.copy(
                connectionMode = ConnectionMode.IDLE,
                telemetry = TelemetrySnapshot()
            )
        }
        syncAmbientMusic()
        appendLog("Stream", "Disconnected stream and released WebRTC resources", LogSeverity.WARN)
    }

    private fun startKeepaliveLoop(gsToken: String, sessionPath: String, baseUrl: String) {
        keepaliveJob?.cancel()
        keepaliveJob = viewModelScope.launch {
            while (isActive) {
                delay(12000L)
                signalingClient.sendKeepalive(gsToken, sessionPath, baseUrl)
            }
        }
    }

    fun attachSurfaceRenderer(renderer: SurfaceViewRenderer, aspectRatio: StreamAspectRatio) {
        activeRtcEngine?.attachSurfaceRenderer(renderer, aspectRatio)
    }

    fun updateRendererAspectRatio(renderer: SurfaceViewRenderer, aspectRatio: StreamAspectRatio) {
        activeRtcEngine?.updateRendererAspectRatio(renderer, aspectRatio)
    }

    fun detachSurfaceRenderer(renderer: SurfaceViewRenderer) {
        activeRtcEngine?.detachSurfaceRenderer(renderer)
    }

    fun clearStreamError() {
        _uiState.update { it.copy(streamErrorMessage = null) }
    }

    fun updateTelemetryFromWebView(rawJson: String) {
        runCatching {
            val obj = JSONObject(rawJson)
            _uiState.update { state ->
                state.copy(
                    telemetry = state.telemetry.copy(
                        fps = obj.optInt("fps", state.telemetry.fps),
                        bitrateKbps = obj.optInt("bitrateKbps", state.telemetry.bitrateKbps),
                        jitterMs = obj.optDouble("jitterMs", state.telemetry.jitterMs.toDouble()).toFloat(),
                        packetLossPct = obj.optDouble("packetLossPct", state.telemetry.packetLossPct.toDouble()).toFloat(),
                        decodeTimeMs = obj.optDouble("decodeTimeMs", state.telemetry.decodeTimeMs.toDouble()).toFloat(),
                        rttMs = obj.optInt("rttMs", state.telemetry.rttMs),
                        decoderName = obj.optString("decoderName", state.telemetry.decoderName),
                        resolutionLabel = obj.optString("resolutionLabel", state.telemetry.resolutionLabel),
                        iceCandidateType = obj.optString("iceCandidateType", state.telemetry.iceCandidateType)
                    )
                )
            }
        }
    }

    fun updateInputHz(hz: Int) {
        _uiState.update { state ->
            state.copy(telemetry = state.telemetry.copy(inputHz = hz.coerceAtLeast(0)))
        }
    }

    fun triggerRumbleFromWeb(durationMs: Long, strong: Float, weak: Float) {
        if (!_uiState.value.rumbleEnabled) return
        inputEngine.triggerDualRumble(durationMs, strong, weak)
    }

    fun logWebEvent(msg: String) {
        if (msg.isNotBlank()) {
            appendLog("BetterXCloud", msg, LogSeverity.INFO)
        }
    }

    fun updateVirtualGamepadState(newState: GamepadState) {
        val prevMask = currentGamepadState.buttonsMask
        currentGamepadState = newState
        if (_uiState.value.rumbleEnabled && (newState.buttonsMask and prevMask.inv()) != 0) {
            inputEngine.triggerRumble(intensity = 0.25f, durationMs = 18L)
        }
    }

    fun onPhysicalMotionEvent(event: MotionEvent): Boolean {
        val updated = PhysicalGamepadHandler.handleMotionEvent(
            event = event,
            current = currentGamepadState,
            deadzone = _uiState.value.stickDeadzone
        ) ?: return false
        currentGamepadState = updated
        return true
    }

    fun onPhysicalKeyEvent(event: KeyEvent): Boolean {
        val updated = PhysicalGamepadHandler.handleKeyEvent(
            event = event,
            current = currentGamepadState
        ) ?: return false
        currentGamepadState = updated
        return true
    }

    private fun prefetchAvatar(signedIn: AuthState.SignedIn) {
        if (signedIn.avatarUrl.isBlank()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            XboxImageCache.loadOrFetchSmall(
                context = ctx,
                url = signedIn.avatarUrl,
                cacheKey = "avatar_${signedIn.xuid.ifBlank { signedIn.gamertag }}",
                maxDimPx = 160
            )
        }
    }

    private fun recordLocalStreamLaunch(console: XboxConsole) {
        val entry = RecentGame(
            titleId = "stream-${console.id}",
            name = "Remote Play • ${console.name}",
            deviceLabel = console.model.displayName,
            lastPlayedLabel = "Just now",
            consoleId = console.id
        )
        val merged = (listOf(entry) + _uiState.value.recentGames)
            .distinctBy { it.titleId }
            .take(12)
        persistRecentGames(merged, accountKey(_uiState.value.authState))
        _uiState.update { it.copy(recentGames = merged) }
    }

    private fun appendLog(tag: String, message: String, severity: LogSeverity) {
        if (_uiState.value.isStreaming && severity == LogSeverity.INFO) return
        val entry = TerminalLogEntry(
            timestamp = timeFormatter.format(Date()),
            tag = tag,
            message = message,
            severity = severity
        )
        _uiState.update { state ->
            state.copy(logs = (state.logs + entry).takeLast(50))
        }
    }

    private fun accountKey(authState: AuthState): String {
        return when (authState) {
            is AuthState.SignedIn -> authState.xuid.ifBlank { authState.gamertag }
            else -> "guest"
        }
    }

    private fun loadPersistedPreset(): StreamQualityPreset {
        val name = configPrefs.getString("quality_preset", StreamQualityPreset.HD_720P_60.name)
        return runCatching { StreamQualityPreset.valueOf(name ?: "") }
            .getOrDefault(StreamQualityPreset.HD_720P_60)
    }

    private fun loadPersistedStreamSettings(): StreamSettings {
        val presetFallback = loadPersistedPreset()
        val defaultRes = if (presetFallback == StreamQualityPreset.FHD_1080P_60) {
            StreamResolution.P1080
        } else {
            StreamResolution.P720
        }
        val res = runCatching {
            StreamResolution.valueOf(configPrefs.getString("stream_res", defaultRes.name).orEmpty())
        }.getOrDefault(defaultRes)
        val fps = runCatching {
            StreamFps.valueOf(configPrefs.getString("stream_fps", StreamFps.FPS_60.name).orEmpty())
        }.getOrDefault(StreamFps.FPS_60)
        val codec = runCatching {
            StreamCodecProfile.valueOf(configPrefs.getString("stream_codec", StreamCodecProfile.LOW_LATENCY_BASELINE.name).orEmpty())
        }.getOrDefault(StreamCodecProfile.LOW_LATENCY_BASELINE)
        val aspect = runCatching {
            StreamAspectRatio.valueOf(configPrefs.getString("stream_aspect", StreamAspectRatio.FIT.name).orEmpty())
        }.getOrDefault(StreamAspectRatio.FIT)
        val hud = runCatching {
            HudStatsDetailMode.valueOf(configPrefs.getString("stream_hud", HudStatsDetailMode.DETAILED.name).orEmpty())
        }.getOrDefault(HudStatsDetailMode.DETAILED)

        return StreamSettings(
            resolution = res,
            targetFps = fps,
            maxBitrateMbps = configPrefs.getInt("stream_bitrate_mbps", 12).coerceIn(2, 20),
            codecProfile = codec,
            lowLatencyJitterBuffer = configPrefs.getBoolean("stream_low_latency", true),
            aspectRatio = aspect,
            hudMode = hud,
            audioVolume = configPrefs.getFloat("stream_vol", 1.0f).coerceIn(0f, 2f),
            virtualPadOpacity = configPrefs.getFloat("pad_opacity", 0.80f).coerceIn(0.25f, 1.0f),
            virtualPadScale = configPrefs.getFloat("pad_scale", 1.0f).coerceIn(0.70f, 1.35f),
            clarityBoostLevel = configPrefs.getInt("stream_clarity_boost", 1).coerceIn(0, 3),
            useBetterXCloudTouch = configPrefs.getBoolean("stream_bx_touch", false),
            skipSplashVideos = configPrefs.getBoolean("stream_skip_splash", true)
        )
    }

    private fun persistStreamSettings(settings: StreamSettings) {
        configPrefs.edit()
            .putString("stream_res", settings.resolution.name)
            .putString("stream_fps", settings.targetFps.name)
            .putInt("stream_bitrate_mbps", settings.maxBitrateMbps)
            .putString("stream_codec", settings.codecProfile.name)
            .putBoolean("stream_low_latency", settings.lowLatencyJitterBuffer)
            .putString("stream_aspect", settings.aspectRatio.name)
            .putString("stream_hud", settings.hudMode.name)
            .putFloat("stream_vol", settings.audioVolume)
            .putFloat("pad_opacity", settings.virtualPadOpacity)
            .putFloat("pad_scale", settings.virtualPadScale)
            .putInt("stream_clarity_boost", settings.clarityBoostLevel)
            .putBoolean("stream_bx_touch", settings.useBetterXCloudTouch)
            .putBoolean("stream_skip_splash", settings.skipSplashVideos)
            .apply()
    }

    private fun loadPersistedBgMotionMode(): BackgroundMotionMode {
        val name = configPrefs.getString("bg_motion_mode", BackgroundMotionMode.STATIC.name)
        return runCatching { BackgroundMotionMode.valueOf(name ?: "") }
            .getOrDefault(BackgroundMotionMode.STATIC)
    }

    private fun loadPersistedBgAnimationStyle(): BackgroundAnimationStyle {
        val name = configPrefs.getString("bg_animation_style", BackgroundAnimationStyle.WAVES.name)
        return runCatching { BackgroundAnimationStyle.valueOf(name ?: "") }
            .getOrDefault(BackgroundAnimationStyle.WAVES)
    }

    private fun loadPersistedBgColorPreset(): BackgroundColorPreset {
        val name = configPrefs.getString("bg_color_preset", BackgroundColorPreset.GREEN.name)
        return runCatching { BackgroundColorPreset.valueOf(name ?: "") }
            .getOrDefault(BackgroundColorPreset.GREEN)
    }

    private fun loadPersistedConsoles(acctKey: String): List<XboxConsole> {
        val raw = configPrefs.getString("user_consoles_json_$acctKey", null)
            ?: configPrefs.getString("user_consoles_json_guest", null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val model = runCatching {
                        ConsoleModel.valueOf(obj.optString("model", ConsoleModel.SERIES_X.name))
                    }.getOrDefault(ConsoleModel.SERIES_X)
                    val power = runCatching {
                        ConsolePowerState.valueOf(obj.optString("powerState", ConsolePowerState.STANDBY.name))
                    }.getOrDefault(ConsolePowerState.STANDBY)
                    add(
                        XboxConsole(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            model = model,
                            liveId = obj.getString("liveId"),
                            lanIp = obj.optString("lanIp", ""),
                            powerState = power,
                            isRegisteredFromXHome = obj.optBoolean("isRegisteredFromXHome", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistConsoles(consoles: List<XboxConsole>, acctKey: String) {
        val arr = JSONArray()
        consoles.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("model", c.model.name)
                    put("liveId", c.liveId)
                    put("lanIp", c.lanIp)
                    put("powerState", c.powerState.name)
                    put("isRegisteredFromXHome", c.isRegisteredFromXHome)
                }
            )
        }
        configPrefs.edit()
            .putString("user_consoles_json_$acctKey", arr.toString())
            .putString("user_consoles_json_guest", arr.toString())
            .apply()
    }

    private fun loadPersistedRecentGames(acctKey: String): List<RecentGame> {
        val raw = configPrefs.getString("recent_games_$acctKey", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        RecentGame(
                            titleId = obj.getString("titleId"),
                            name = obj.getString("name"),
                            deviceLabel = obj.optString("deviceLabel", "Xbox"),
                            lastPlayedLabel = obj.optString("lastPlayedLabel", "Recently"),
                            consoleId = obj.optString("consoleId", "").ifBlank { null },
                            imageUrl = obj.optString("imageUrl", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistRecentGames(games: List<RecentGame>, acctKey: String) {
        val arr = JSONArray()
        games.forEach { g ->
            arr.put(
                JSONObject().apply {
                    put("titleId", g.titleId)
                    put("name", g.name)
                    put("deviceLabel", g.deviceLabel)
                    put("lastPlayedLabel", g.lastPlayedLabel)
                    if (g.consoleId != null) put("consoleId", g.consoleId)
                    if (g.imageUrl.isNotBlank()) put("imageUrl", g.imageUrl)
                }
            )
        }
        configPrefs.edit().putString("recent_games_$acctKey", arr.toString()).apply()
    }

    private fun loadPersistedAchievements(acctKey: String): List<AchievementItem> {
        val raw = configPrefs.getString("achievements_$acctKey", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        AchievementItem(
                            id = obj.getString("id"),
                            titleName = obj.optString("titleName", "Xbox Game"),
                            name = obj.optString("name", "Achievement"),
                            description = obj.optString("description", ""),
                            gamerscore = obj.optInt("gamerscore", 0),
                            isUnlocked = obj.optBoolean("isUnlocked", true),
                            unlockedAtLabel = obj.optString("unlockedAtLabel", "Unlocked"),
                            iconUrl = obj.optString("iconUrl", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistAchievements(items: List<AchievementItem>, acctKey: String) {
        val arr = JSONArray()
        items.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("titleName", a.titleName)
                    put("name", a.name)
                    put("description", a.description)
                    put("gamerscore", a.gamerscore)
                    put("isUnlocked", a.isUnlocked)
                    put("unlockedAtLabel", a.unlockedAtLabel)
                    if (a.iconUrl.isNotBlank()) put("iconUrl", a.iconUrl)
                }
            )
        }
        configPrefs.edit().putString("achievements_$acctKey", arr.toString()).apply()
    }

    override fun onCleared() {
        super.onCleared()
        ambientPlayer.stopAndRelease()
        physicalHandler.unregisterListener()
    }
}
