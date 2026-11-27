package com.example.model

enum class ConsoleModel(val displayName: String, val shortTag: String) {
    SERIES_X("Xbox Series X", "XSX"),
    SERIES_S("Xbox Series S", "XSS"),
    ONE_X("Xbox One X", "X1X"),
    ONE_S("Xbox One S", "X1S"),
    XBOX_ONE("Xbox One", "XB1")
}

enum class ConsolePowerState(val label: String) {
    ONLINE("ONLINE"),
    STANDBY("STANDBY"),
    OFFLINE("OFFLINE")
}

enum class BackgroundMotionMode(val label: String) {
    STATIC("Static"),
    MOVING("Moving")
}

enum class BackgroundAnimationStyle(val label: String) {
    WAVES("Waves"),
    RIPPLES("Ripples"),
    MIDNIGHT("Midnight"),
    MOTES("Motes")
}

enum class BackgroundColorPreset(
    val label: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val defaultHue: Float,
    val defaultIntensity: Float
) {
    GREEN("Green", 0xFF22C55EL, 0xFF107C10L, 142f, 0.72f),
    BLUE("Blue", 0xFF3B82F6L, 0xFF1D4ED8L, 217f, 0.72f),
    RED("Red", 0xFFEF4444L, 0xFFB91C1CL, 0f, 0.70f),
    YELLOW("Yellow", 0xFFFACC15L, 0xFFCA8A04L, 48f, 0.65f),
    WHITE("White", 0xFFE2E8F0L, 0xFF94A3B8L, 210f, 0.50f),
    BLACK("Black", 0xFF334155L, 0xFF0F172AL, 215f, 0.25f),
    CUSTOM("Custom", 0xFF22C55EL, 0xFF107C10L, 165f, 0.72f)
}

data class XboxConsole(
    val id: String,
    val name: String,
    val model: ConsoleModel,
    val liveId: String,
    val lanIp: String,
    val powerState: ConsolePowerState,
    val isRegisteredFromXHome: Boolean = false
)

data class RecentGame(
    val titleId: String,
    val name: String,
    val deviceLabel: String,
    val lastPlayedLabel: String,
    val consoleId: String? = null,
    val imageUrl: String = ""
)

data class AchievementItem(
    val id: String,
    val titleName: String,
    val name: String,
    val description: String,
    val gamerscore: Int,
    val isUnlocked: Boolean,
    val unlockedAtLabel: String,
    val iconUrl: String = ""
)

data class SavedAccountSummary(
    val xuid: String,
    val gamertag: String,
    val gamerscore: String = "",
    val avatarUrl: String = "",
    val hasStreamingToken: Boolean = false
)

enum class StreamResolution(
    val label: String,
    val width: Int,
    val height: Int,
    val subtitle: String
) {
    P540("540p", 960, 540, "Ultra smooth on congested Wi-Fi"),
    P720("720p", 1280, 720, "Galaxy A22 / Tab A7 Optimal"),
    P900("900p", 1600, 900, "Balanced HD+ clarity"),
    P1080("1080p", 1920, 1080, "Max Full HD")
}

enum class StreamFps(val label: String, val fps: Int) {
    FPS_30("30 FPS", 30),
    FPS_45("45 FPS", 45),
    FPS_60("60 FPS", 60)
}

enum class StreamCodecProfile(
    val label: String,
    val subtitle: String
) {
    LOW_LATENCY_BASELINE("Low-Latency H.264", "Baseline • Zero B-frame buffer lag"),
    HIGH_QUALITY_MAIN("High Quality H.264", "Main/High • Sharper detail on 5GHz")
}

enum class StreamAspectRatio(val label: String) {
    FIT("Fit 16:9"),
    COVER("Zoom Fill"),
    STRETCH("Stretch Full")
}

enum class HudStatsDetailMode(val label: String) {
    DETAILED("Detailed HUD"),
    MINIMAL("Compact HUD"),
    HIDDEN("Hidden")
}

data class StreamSettings(
    val resolution: StreamResolution = StreamResolution.P720,
    val targetFps: StreamFps = StreamFps.FPS_60,
    val maxBitrateMbps: Int = 12,
    val codecProfile: StreamCodecProfile = StreamCodecProfile.LOW_LATENCY_BASELINE,
    val lowLatencyJitterBuffer: Boolean = true,
    val aspectRatio: StreamAspectRatio = StreamAspectRatio.FIT,
    val hudMode: HudStatsDetailMode = HudStatsDetailMode.DETAILED,
    val audioVolume: Float = 1.0f,
    val virtualPadOpacity: Float = 0.80f,
    val virtualPadScale: Float = 1.0f,
    val clarityBoostLevel: Int = 1,
    val useBetterXCloudTouch: Boolean = false,
    val skipSplashVideos: Boolean = true
) {
    val maxBitrateKbps: Int get() = (maxBitrateMbps.coerceIn(2, 20)) * 1000
    val minBitrateKbps: Int get() = (maxBitrateKbps * 0.5f).toInt().coerceAtLeast(1500)
    val startBitrateKbps: Int get() = (maxBitrateKbps * 0.78f).toInt().coerceAtLeast(2000)
    val summaryLabel: String
        get() = "${resolution.label} @ ${targetFps.fps} • ${maxBitrateMbps}Mbps"
}

enum class StreamQualityPreset(
    val title: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val targetFps: Int,
    val minBitrateKbps: Int,
    val maxBitrateKbps: Int
) {
    HD_720P_60(
        title = "720p @ 60 FPS",
        subtitle = "8–12 Mbps • Galaxy A22 / Tab A7 Optimal",
        width = 1280,
        height = 720,
        targetFps = 60,
        minBitrateKbps = 8000,
        maxBitrateKbps = 12000
    ),
    FHD_1080P_60(
        title = "1080p @ 60 FPS",
        subtitle = "14–18 Mbps • High-Bandwidth LAN",
        width = 1920,
        height = 1080,
        targetFps = 60,
        minBitrateKbps = 14000,
        maxBitrateKbps = 18000
    )
}

enum class DashboardTab(val label: String) {
    HOME("Home"),
    CONSOLES("Consoles"),
    GAMES("Recent"),
    SETTINGS("Settings"),
    ACCOUNT("Account")
}

enum class ConnectionMode {
    IDLE,
    SIGNALING,
    STREAMING_XHOME,
    STREAMING_LOOPBACK
}

enum class LogSeverity {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

data class TerminalLogEntry(
    val timestamp: String,
    val tag: String,
    val message: String,
    val severity: LogSeverity = LogSeverity.INFO
)

data class TelemetrySnapshot(
    val fps: Int = 0,
    val bitrateKbps: Int = 0,
    val jitterMs: Float = 0f,
    val packetLossPct: Float = 0f,
    val decodeTimeMs: Float = 0f,
    val rttMs: Int = 0,
    val decoderName: String = "Hardware H.264 (Standby)",
    val resolutionLabel: String = "0x0",
    val iceCandidateType: String = "None",
    val inputHz: Int = 0
)

data class DeviceCodeSession(
    val userCode: String,
    val verificationUri: String,
    val deviceCode: String,
    val expiresInSec: Int,
    val intervalSec: Int
)

sealed interface AuthState {
    data object SignedOut : AuthState
    data class AwaitingUserCode(val session: DeviceCodeSession) : AuthState
    data class SignedIn(
        val gamertag: String,
        val userHash: String,
        val xuid: String = "",
        val gamerscore: String = "",
        val avatarUrl: String = "",
        val hasStreamingToken: Boolean
    ) : AuthState
    data class Error(val message: String) : AuthState
}

data class PhysicalControllerInfo(
    val deviceId: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int
)

data class GamepadState(
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val buttonsMask: Int = 0,
    val timestampMs: Long = 0L
) {
    companion object {
        const val BTN_A = 1 shl 0
        const val BTN_B = 1 shl 1
        const val BTN_X = 1 shl 2
        const val BTN_Y = 1 shl 3
        const val BTN_LB = 1 shl 4
        const val BTN_RB = 1 shl 5
        const val BTN_VIEW = 1 shl 6
        const val BTN_MENU = 1 shl 7
        const val BTN_LSTICK = 1 shl 8
        const val BTN_RSTICK = 1 shl 9
        const val BTN_DPAD_UP = 1 shl 10
        const val BTN_DPAD_DOWN = 1 shl 11
        const val BTN_DPAD_LEFT = 1 shl 12
        const val BTN_DPAD_RIGHT = 1 shl 13
        const val BTN_NEXUS = 1 shl 14
    }
}
