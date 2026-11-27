package com.example.ui.stream

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.R
import com.example.model.ConnectionMode
import com.example.model.GamepadState
import com.example.model.HudStatsDetailMode
import com.example.model.StreamAspectRatio
import com.example.model.StreamFps
import com.example.model.StreamResolution
import com.example.model.StreamSettings
import com.example.model.TelemetrySnapshot
import com.example.ui.components.XboxIcons
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSubtleFill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.RedDanger
import com.example.ui.theme.RedSubtleFill
import com.example.ui.theme.SidebarSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import org.webrtc.SurfaceViewRenderer

/**
 * Native Greenlight WebRTC viewport on pure #000000 AMOLED canvas.
 * Powered by hardware MediaCodec H.264 decoding directly onto SurfaceViewRenderer.
 * Zero browser/Chromium overhead - lightweight, silky smooth on Galaxy A22 4G and Tab A7.
 */
@Composable
fun StreamViewportScreen(
    connectionMode: ConnectionMode,
    telemetry: TelemetrySnapshot,
    streamSettings: StreamSettings = StreamSettings(),
    showVirtualOverlay: Boolean,
    targetConsoleName: String = "",
    targetServerId: String = "",
    gamepadStateProvider: () -> GamepadState = { GamepadState() },
    onVirtualGamepadState: (GamepadState) -> Unit,
    onToggleVirtualOverlay: () -> Unit,
    onAttachRenderer: (SurfaceViewRenderer, StreamAspectRatio) -> Unit = { _, _ -> },
    onUpdateRendererAspectRatio: (SurfaceViewRenderer, StreamAspectRatio) -> Unit = { _, _ -> },
    onDetachRenderer: (SurfaceViewRenderer) -> Unit = {},
    onUpdateResolution: (StreamResolution) -> Unit = {},
    onUpdateFps: (StreamFps) -> Unit = {},
    onUpdateBitrateMbps: (Int) -> Unit = {},
    onUpdateAspectRatio: (StreamAspectRatio) -> Unit = {},
    onUpdateAudioVolume: (Float) -> Unit = {},
    onUpdateHudMode: (HudStatsDetailMode) -> Unit = {},
    onUpdatePadOpacity: (Float) -> Unit = {},
    onUpdatePadScale: (Float) -> Unit = {},
    onUpdateClarityBoost: (Int) -> Unit = {},
    onUpdateUseBetterXCloudTouch: (Boolean) -> Unit = {},
    onStreamStatsJson: (String) -> Unit = {},
    onUpdateInputHz: (Int) -> Unit = {},
    onWebRumble: (Long, Float, Float) -> Unit = { _, _, _ -> },
    onWebLog: (String) -> Unit = {},
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isQuickTuneOpen by remember { mutableStateOf(false) }

    BackHandler {
        if (isQuickTuneOpen) {
            isQuickTuneOpen = false
        } else {
            onDisconnect()
        }
    }

    // Immersive sticky landscape + keep screen on + force normal media speaker/BT audio routing
    DisposableEffect(activity) {
        val prevOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val prevVolumeStream = activity?.volumeControlStream ?: AudioManager.USE_DEFAULT_STREAM_TYPE
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val prevAudioMode = audioManager?.mode ?: AudioManager.MODE_NORMAL

        if (audioManager != null) {
            runCatching {
                audioManager.mode = AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
        }
        if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity.volumeControlStream = AudioManager.STREAM_MUSIC
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (audioManager != null) {
                runCatching { audioManager.mode = prevAudioMode }
            }
            if (activity != null) {
                activity.requestedOrientation = prevOrientation
                activity.volumeControlStream = prevVolumeStream
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .testTag("stream_viewport_root")
    ) {
        // Aspect-Ratio Enforced Video Container (Fit 16:9, Cover, or Stretch)
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val targetAspect = 16f / 9f
            val availW = maxWidth
            val availH = maxHeight
            val availAspect = if (availH.value > 0f) availW.value / availH.value else targetAspect

            val aspectBoxModifier = when (streamSettings.aspectRatio) {
                StreamAspectRatio.FIT -> {
                    val constrained = if (availAspect > targetAspect) {
                        Modifier
                            .height(availH)
                            .width(availH * targetAspect)
                    } else {
                        Modifier
                            .width(availW)
                            .height(availW / targetAspect)
                    }
                    constrained.testTag("stream_aspect_container_fit")
                }

                StreamAspectRatio.COVER -> Modifier
                    .fillMaxSize()
                    .testTag("stream_aspect_container_cover")

                StreamAspectRatio.STRETCH -> Modifier
                    .fillMaxSize()
                    .testTag("stream_aspect_container_stretch")
            }

            Box(
                modifier = aspectBoxModifier.background(AmoledBlack),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            setEnableHardwareScaler(true)
                            onAttachRenderer(this, streamSettings.aspectRatio)
                        }
                    },
                    update = { renderer ->
                        onUpdateRendererAspectRatio(renderer, streamSettings.aspectRatio)
                    },
                    onRelease = { renderer ->
                        onDetachRenderer(renderer)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Signaling / Connecting Overlay
        if (connectionMode == ConnectionMode.SIGNALING) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SidebarSurface.copy(alpha = 0.88f))
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    color = EmeraldPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Connecting via Greenlight WebRTC...",
                    color = TextPrimary,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp
                )
            }
        }

        // On-Screen Virtual Gamepad Overlay
        if (showVirtualOverlay) {
            VirtualControllerOverlay(
                onStateChanged = onVirtualGamepadState,
                opacity = streamSettings.virtualPadOpacity,
                scale = streamSettings.virtualPadScale,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top HUD Bar (Detailed / Compact / Hidden)
        if (streamSettings.hudMode == HudStatsDetailMode.HIDDEN) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SidebarSurface.copy(alpha = 0.72f))
                        .border(1.dp, BentoBorder, RoundedCornerShape(10.dp))
                        .clickable { isQuickTuneOpen = !isQuickTuneOpen }
                        .padding(horizontal = 10.dp)
                        .testTag("stream_tune_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Stream Quick Tune",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(RedSubtleFill.copy(alpha = 0.85f))
                        .border(1.dp, RedDanger.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onDisconnect)
                        .padding(horizontal = 10.dp)
                        .testTag("disconnect_stream_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_disconnect_stream),
                        tint = RedDanger,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            val modeTag = if (connectionMode == ConnectionMode.STREAMING_LOOPBACK) "LOOPBACK" else "Greenlight"
            val mbpsText = "%.1f".format(telemetry.bitrateKbps / 1000f)
            val jitterText = "%.1f".format(telemetry.jitterMs)
            val dtText = "%.1f".format(telemetry.decodeTimeMs)
            val lossText = "%.1f".format(telemetry.packetLossPct)

            val fpsColor = when {
                telemetry.fps >= 55 -> EmeraldPrimary
                telemetry.fps >= 35 -> AmberWarning
                telemetry.fps == 0 -> TextSecondary
                else -> RedDanger
            }
            val pingColor = when {
                telemetry.rttMs == 0 -> TextSecondary
                telemetry.rttMs <= 25 -> EmeraldPrimary
                telemetry.rttMs <= 60 -> AmberWarning
                else -> RedDanger
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 10.dp, end = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SidebarSurface.copy(alpha = 0.86f))
                    .border(1.dp, BentoBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("stream_hud_telemetry")
                ) {
                    Text(
                        text = modeTag,
                        color = EmeraldPrimary,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${telemetry.fps} FPS",
                        color = fpsColor,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "$mbpsText Mbps",
                        color = TextPrimary,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp
                    )
                    if (streamSettings.hudMode == HudStatsDetailMode.DETAILED) {
                        Text(
                            text = "${telemetry.rttMs}ms",
                            color = pingColor,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "JIT ${jitterText}ms",
                            color = TextSecondary,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "DT ${dtText}ms",
                            color = TextSecondary,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "LOSS $lossText%",
                            color = if (telemetry.packetLossPct > 1.5f) RedDanger else TextSecondary,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = "${telemetry.inputHz}Hz",
                        color = if (telemetry.inputHz > 0) EmeraldPrimary else AmberWarning,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleVirtualOverlay)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showVirtualOverlay) XboxIcons.VisibilityOff else XboxIcons.Gamepad,
                        contentDescription = stringResource(R.string.cd_toggle_overlay),
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showVirtualOverlay) "Hide Pad" else "Show Pad",
                        color = TextSecondary,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isQuickTuneOpen) EmeraldSubtleFill else Color.Transparent)
                        .clickable { isQuickTuneOpen = !isQuickTuneOpen }
                        .padding(horizontal = 8.dp)
                        .testTag("stream_tune_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Greenlight Quick Tune",
                        tint = if (isQuickTuneOpen) EmeraldPrimary else TextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Tune",
                        color = if (isQuickTuneOpen) EmeraldPrimary else TextPrimary,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RedSubtleFill)
                        .clickable(onClick = onDisconnect)
                        .padding(horizontal = 8.dp)
                        .testTag("disconnect_stream_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_disconnect_stream),
                        tint = RedDanger,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "EXIT",
                        color = RedDanger,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Live Quick Tune Overlay Drawer
        if (isQuickTuneOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 12.dp, bottom = 12.dp)
                    .widthIn(max = 380.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SidebarSurface.copy(alpha = 0.95f))
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
                    .testTag("stream_quick_tune_drawer")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Greenlight • Live Tune",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        IconButton(
                            onClick = { isQuickTuneOpen = false },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close quick tune", tint = TextSecondary)
                        }
                    }

                    Text("Resolution & Target FPS", color = TextSecondary, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        StreamResolution.entries.forEach { res ->
                            val selected = streamSettings.resolution == res
                            OutlinedButton(
                                onClick = { onUpdateResolution(res) },
                                border = BorderStroke(1.dp, if (selected) EmeraldPrimary else BentoBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                                    contentColor = if (selected) EmeraldPrimary else TextPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                            ) {
                                Text(res.label, fontSize = 10.sp)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        StreamFps.entries.forEach { fps ->
                            val selected = streamSettings.targetFps == fps
                            OutlinedButton(
                                onClick = { onUpdateFps(fps) },
                                border = BorderStroke(1.dp, if (selected) EmeraldPrimary else BentoBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                                    contentColor = if (selected) EmeraldPrimary else TextPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                            ) {
                                Text(fps.label, fontSize = 10.sp)
                            }
                        }
                    }

                    Text(
                        text = "Live Bitrate Cap: ${streamSettings.maxBitrateMbps} Mbps",
                        color = TextPrimary,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = streamSettings.maxBitrateMbps.toFloat(),
                        onValueChange = { onUpdateBitrateMbps(it.toInt().coerceIn(2, 20)) },
                        valueRange = 2f..20f,
                        steps = 17,
                        colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary, inactiveTrackColor = BentoBorder)
                    )

                    Text(
                        text = "Audio Volume: ${(streamSettings.audioVolume * 100).toInt()}%",
                        color = TextPrimary,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = streamSettings.audioVolume,
                        onValueChange = onUpdateAudioVolume,
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary, inactiveTrackColor = BentoBorder)
                    )

                    Text("Aspect Ratio", color = TextSecondary, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        StreamAspectRatio.entries.forEach { ratio ->
                            val selected = streamSettings.aspectRatio == ratio
                            OutlinedButton(
                                onClick = { onUpdateAspectRatio(ratio) },
                                border = BorderStroke(1.dp, if (selected) EmeraldPrimary else BentoBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                                    contentColor = if (selected) EmeraldPrimary else TextPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .testTag("tune_aspect_${ratio.name.lowercase()}")
                            ) {
                                Text(ratio.label, fontSize = 10.sp)
                            }
                        }
                    }

                    Text("HUD Telemetry Detail", color = TextSecondary, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        HudStatsDetailMode.entries.forEach { mode ->
                            val selected = streamSettings.hudMode == mode
                            OutlinedButton(
                                onClick = { onUpdateHudMode(mode) },
                                border = BorderStroke(1.dp, if (selected) EmeraldPrimary else BentoBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                                    contentColor = if (selected) EmeraldPrimary else TextPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                            ) {
                                Text(mode.label.substringBefore(" "), fontSize = 10.sp)
                            }
                        }
                    }

                    Text(
                        text = "Touch Pad Opacity: ${(streamSettings.virtualPadOpacity * 100).toInt()}% • Scale: ${(streamSettings.virtualPadScale * 100).toInt()}%",
                        color = TextPrimary,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = streamSettings.virtualPadOpacity,
                        onValueChange = onUpdatePadOpacity,
                        valueRange = 0.25f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary, inactiveTrackColor = BentoBorder)
                    )
                    Slider(
                        value = streamSettings.virtualPadScale,
                        onValueChange = onUpdatePadScale,
                        valueRange = 0.70f..1.35f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary, inactiveTrackColor = BentoBorder)
                    )
                }
            }
        }
    }
}
