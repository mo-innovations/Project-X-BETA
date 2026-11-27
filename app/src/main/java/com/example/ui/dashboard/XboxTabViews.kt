package com.example.ui.dashboard

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AchievementItem
import com.example.model.AuthState
import com.example.model.BackgroundAnimationStyle
import com.example.model.BackgroundColorPreset
import com.example.model.BackgroundMotionMode
import com.example.model.ConnectionMode
import com.example.model.ConsolePowerState
import com.example.model.HudStatsDetailMode
import com.example.model.RecentGame
import com.example.model.SavedAccountSummary
import com.example.model.StreamAspectRatio
import com.example.model.StreamCodecProfile
import com.example.model.StreamFps
import com.example.model.StreamQualityPreset
import com.example.model.StreamResolution
import com.example.ui.components.ConsoleModelThumbnail
import com.example.ui.components.LiveTelemetryDrawer
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.components.XboxCachedArtworkBox
import com.example.ui.components.XboxGamerpicAvatar
import com.example.ui.components.XboxIcons
import com.example.ui.components.XboxTile
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardElevated
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSubtleFill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.RedDanger
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.XboxGreen
import com.example.viewmodel.ProjectXUiState

@Composable
fun ConsolesTabView(
    state: ProjectXUiState,
    onSelectConsole: (String) -> Unit,
    onStreamConsole: (String) -> Unit,
    onWakeConsole: (String) -> Unit,
    onScanLan: (String) -> Unit,
    onUpdateIp: (String, String) -> Unit,
    onUpdateLiveId: (String, String) -> Unit,
    onRemoveConsole: (String) -> Unit,
    onOpenRegister: () -> Unit,
    onSyncConsoles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("consoles_tab_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Saved Consoles", color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Manage Xbox Series X|S & Xbox One LAN profiles",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSyncConsoles,
                    enabled = !state.isRefreshingConsoles,
                    border = BorderStroke(1.dp, BentoBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Sync consoles", tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync", color = TextPrimary)
                }
                Button(
                    onClick = onOpenRegister,
                    colors = ButtonDefaults.buttonColors(containerColor = XboxGreen, contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("consoles_register_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Register")
                }
            }
        }

        if (!state.wolStatusMessage.isNullOrBlank()) {
            Text(
                text = state.wolStatusMessage,
                color = EmeraldPrimary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp
            )
        }

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EmeraldPrimary,
            unfocusedBorderColor = BentoBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = EmeraldPrimary,
            unfocusedLabelColor = TextSecondary,
            focusedContainerColor = ObsidianBase,
            unfocusedContainerColor = ObsidianBase
        )

        if (state.consoles.isEmpty()) {
            XboxTile(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("consoles_empty_state")
            ) {
                Icon(
                    imageVector = XboxIcons.Console,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No consoles registered yet",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap Register to add your Xbox Series X|S or Xbox One, or tap Sync after signing in with Xbox Live.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        state.consoles.forEach { console ->
            val isSelected = console.id == state.selectedConsoleId
            val powerTone = when (console.powerState) {
                ConsolePowerState.ONLINE -> PillTone.EMERALD
                ConsolePowerState.STANDBY -> PillTone.AMBER
                ConsolePowerState.OFFLINE -> PillTone.RED
            }

            XboxTile(
                focused = isSelected,
                onClick = { onSelectConsole(console.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConsoleModelThumbnail(
                        model = console.model,
                        modifier = Modifier
                            .width(96.dp)
                            .height(72.dp)
                            .testTag("console_model_photo_${console.id}")
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = console.name,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = console.model.displayName,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    StatusPill(text = console.powerState.label, tone = powerTone)
                    IconButton(
                        onClick = { onRemoveConsole(console.id) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_console_${console.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Remove console",
                            tint = RedDanger,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = console.lanIp,
                        onValueChange = { onUpdateIp(console.id, it) },
                        label = { Text("LAN IP (Auto/Manual)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = console.liveId,
                        onValueChange = { onUpdateLiveId(console.id, it) },
                        label = { Text("LiveID") },
                        singleLine = true,
                        colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onScanLan(console.id) },
                        border = BorderStroke(1.dp, BentoBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("scan_console_${console.id}")
                    ) {
                        Icon(XboxIcons.Wifi, contentDescription = "Scan LAN", tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan LAN", color = TextPrimary, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onWakeConsole(console.id) },
                        enabled = !state.isWakingConsole,
                        border = BorderStroke(1.dp, BentoBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        if (state.isWakingConsole) {
                            CircularProgressIndicator(color = EmeraldPrimary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(XboxIcons.Power, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Wake", color = TextPrimary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onStreamConsole(console.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianBase),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stream", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentGamesTabView(
    recentGames: List<RecentGame>,
    authState: AuthState,
    isRefreshing: Boolean,
    onRefreshGames: () -> Unit,
    onClearHistory: () -> Unit,
    onLaunchGame: (RecentGame) -> Unit,
    onSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("games_tab_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Recently Played", color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Compact box art cached locally on device",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recentGames.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearHistory,
                        border = BorderStroke(1.dp, BentoBorder),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Clear", color = TextSecondary)
                    }
                }
                Button(
                    onClick = if (authState is AuthState.SignedIn) onRefreshGames else onSignIn,
                    enabled = !isRefreshing,
                    colors = ButtonDefaults.buttonColors(containerColor = XboxGreen, contentColor = TextPrimary),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (authState is AuthState.SignedIn) "Sync" else "Sign In")
                }
            }
        }

        if (recentGames.isEmpty()) {
            XboxTile(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = XboxIcons.Gamepad,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No recently played games synced yet",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (authState is AuthState.SignedIn) {
                        "Tap Sync above to pull your Xbox Live title history & compact box art."
                    } else {
                        "Sign in with your Xbox account to pull your recently played games, or start a stream."
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            recentGames.forEach { game ->
                XboxTile(
                    onClick = { onLaunchGame(game) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        XboxCachedArtworkBox(
                            imageUrl = game.imageUrl,
                            cacheKey = "title_${game.titleId}",
                            fallbackIcon = XboxIcons.Gamepad,
                            maxDimPx = 192,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = game.name,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${game.deviceLabel} • ${game.lastPlayedLabel}",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        StatusPill(text = "PLAY", tone = PillTone.EMERALD)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTabView(
    state: ProjectXUiState,
    onSelectPreset: (StreamQualityPreset) -> Unit,
    onStartLoopbackTest: () -> Unit,
    onDeadzoneChanged: (Float) -> Unit,
    onRumbleChanged: (Boolean) -> Unit,
    onVirtualOverlayChanged: () -> Unit,
    onToggleTelemetryDrawer: () -> Unit,
    onAmbientMusicChanged: (Boolean) -> Unit = {},
    onCustomAmbientTrackSelected: (String, String) -> Unit = { _, _ -> },
    onClearCustomAmbientTrack: () -> Unit = {},
    onBgMotionChanged: (BackgroundMotionMode) -> Unit = {},
    onBgAnimationStyleChanged: (BackgroundAnimationStyle) -> Unit = {},
    onBgColorPresetChanged: (BackgroundColorPreset) -> Unit = {},
    onBgCustomColorChanged: (Float, Float) -> Unit = { _, _ -> },
    onSelectResolution: (StreamResolution) -> Unit = {},
    onSelectFps: (StreamFps) -> Unit = {},
    onBitrateMbpsChanged: (Int) -> Unit = {},
    onCodecProfileChanged: (StreamCodecProfile) -> Unit = {},
    onLowLatencyJitterChanged: (Boolean) -> Unit = {},
    onAspectRatioChanged: (StreamAspectRatio) -> Unit = {},
    onHudModeChanged: (HudStatsDetailMode) -> Unit = {},
    onAudioVolumeChanged: (Float) -> Unit = {},
    onPadOpacityChanged: (Float) -> Unit = {},
    onPadScaleChanged: (Float) -> Unit = {},
    onClarityBoostChanged: (Int) -> Unit = {},
    onUseBetterXCloudTouchChanged: (Boolean) -> Unit = {},
    onSkipSplashVideosChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment ?: "Custom Audio Track" }
            onCustomAmbientTrackSelected(uri.toString(), displayName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_tab_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Stream & Controls", color = TextPrimary, style = MaterialTheme.typography.headlineMedium)

        // Appearance & Ambient Audio (Series X Background + PS5-Style Celestial Synth / Custom Audio)
        XboxTile(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_appearance_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Appearance & Ambient Audio", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                StatusPill(
                    text = if (state.backgroundMotionMode == BackgroundMotionMode.STATIC) "0% GPU" else "MOVING",
                    tone = PillTone.EMERALD
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Ambient Background Music", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (state.customAmbientTrackUri.isNotBlank()) {
                            "Custom Track: ${state.customAmbientTrackName}"
                        } else {
                            "PS5-style celestial shimmer & celesta ambience (~0% CPU)"
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = state.ambientMusicEnabled,
                    onCheckedChange = onAmbientMusicChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBase, checkedTrackColor = EmeraldPrimary),
                    modifier = Modifier.testTag("ambient_music_switch")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                    border = BorderStroke(1.dp, BentoBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("pick_custom_ambient_button")
                ) {
                    Text("Use Custom MP3 / Audio", color = EmeraldPrimary, fontSize = 12.sp)
                }
                if (state.customAmbientTrackUri.isNotBlank()) {
                    OutlinedButton(
                        onClick = onClearCustomAmbientTrack,
                        border = BorderStroke(1.dp, BentoBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("reset_custom_ambient_button")
                    ) {
                        Text("Built-In", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Series X Background Mode", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BackgroundMotionMode.entries.forEach { mode ->
                    val selected = state.backgroundMotionMode == mode
                    val tag = if (mode == BackgroundMotionMode.STATIC) "bg_mode_static" else "bg_mode_moving"
                    OutlinedButton(
                        onClick = { onBgMotionChanged(mode) },
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) EmeraldPrimary else BentoBorder
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                            contentColor = if (selected) EmeraldPrimary else TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag(tag)
                    ) {
                        Text(
                            text = if (mode == BackgroundMotionMode.STATIC) "Static (Optimal)" else "Moving Waves",
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Series X Animation Style", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BackgroundAnimationStyle.entries.forEach { style ->
                    val selected = state.backgroundAnimationStyle == style
                    OutlinedButton(
                        onClick = { onBgAnimationStyleChanged(style) },
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) EmeraldPrimary else BentoBorder
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                            contentColor = if (selected) EmeraldPrimary else TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("bg_style_${style.name.lowercase()}")
                    ) {
                        Text(
                            text = style.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Series X Wave Colors", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BackgroundColorPreset.entries.forEach { preset ->
                    val selected = state.backgroundColorPreset == preset
                    val swatchColor = if (preset == BackgroundColorPreset.CUSTOM) {
                        Color.hsv(((state.backgroundCustomHue % 360f) + 360f) % 360f, 0.78f, 0.85f)
                    } else {
                        Color(preset.primaryHex)
                    }
                    Row(
                        modifier = Modifier
                            .testTag("bg_color_${preset.name.lowercase()}")
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) EmeraldSubtleFill else ObsidianBase)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) EmeraldPrimary else BentoBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onBgColorPresetChanged(preset) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(1.dp, TextSecondary.copy(alpha = 0.6f), CircleShape)
                        )
                        Text(
                            text = preset.label,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Custom Color Hue: ${state.backgroundCustomHue.toInt()}°",
                color = TextPrimary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            )
            Slider(
                value = state.backgroundCustomHue,
                onValueChange = { hue -> onBgCustomColorChanged(hue, state.backgroundCustomIntensity) },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = EmeraldPrimary,
                    activeTrackColor = EmeraldPrimary,
                    inactiveTrackColor = BentoBorder
                ),
                modifier = Modifier.testTag("bg_hue_slider")
            )

            Text(
                text = "Glow Intensity: ${(state.backgroundCustomIntensity * 100).toInt()}%",
                color = TextPrimary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            )
            Slider(
                value = state.backgroundCustomIntensity,
                onValueChange = { intensity -> onBgCustomColorChanged(state.backgroundCustomHue, intensity) },
                valueRange = 0.15f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = EmeraldPrimary,
                    activeTrackColor = EmeraldPrimary,
                    inactiveTrackColor = BentoBorder
                ),
                modifier = Modifier.testTag("bg_intensity_slider")
            )
        }

        XboxTile(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Video Quality & Better xHome", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                StatusPill(text = state.streamSettings.summaryLabel, tone = PillTone.EMERALD)
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Quick Presets (preserved for one-tap setup + test compatibility)
            StreamQualityPreset.entries.forEach { preset ->
                val selected = preset == state.qualityPreset
                val tag = if (preset == StreamQualityPreset.HD_720P_60) "preset_720p" else "preset_1080p"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) EmeraldSubtleFill else ObsidianBase)
                        .border(
                            1.5.dp,
                            if (selected) EmeraldPrimary else BentoBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectPreset(preset) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag(tag),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (selected) EmeraldPrimary else TextMuted, CircleShape)
                            .padding(3.dp)
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(preset.title, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(preset.subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Custom Resolution", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StreamResolution.entries.forEach { res ->
                    val selected = state.streamSettings.resolution == res
                    OutlinedButton(
                        onClick = { onSelectResolution(res) },
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) EmeraldPrimary else BentoBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                            contentColor = if (selected) EmeraldPrimary else TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("res_chip_${res.name.lowercase()}")
                    ) {
                        Text(res.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Target Frame Rate", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StreamFps.entries.forEach { fps ->
                    val selected = state.streamSettings.targetFps == fps
                    OutlinedButton(
                        onClick = { onSelectFps(fps) },
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) EmeraldPrimary else BentoBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                            contentColor = if (selected) EmeraldPrimary else TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("fps_chip_${fps.fps}")
                    ) {
                        Text(fps.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Max Bitrate: ${state.streamSettings.maxBitrateMbps} Mbps (Target ${(state.streamSettings.startBitrateKbps / 1000f).toInt()} Mbps)",
                color = TextPrimary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            )
            Slider(
                value = state.streamSettings.maxBitrateMbps.toFloat(),
                onValueChange = { onBitrateMbpsChanged(it.toInt().coerceIn(2, 20)) },
                valueRange = 2f..20f,
                steps = 17,
                colors = SliderDefaults.colors(
                    thumbColor = EmeraldPrimary,
                    activeTrackColor = EmeraldPrimary,
                    inactiveTrackColor = BentoBorder
                ),
                modifier = Modifier.testTag("bitrate_slider")
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text("H.264 Codec Profile", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StreamCodecProfile.entries.forEach { profile ->
                    val selected = state.streamSettings.codecProfile == profile
                    OutlinedButton(
                        onClick = { onCodecProfileChanged(profile) },
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) EmeraldPrimary else BentoBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                            contentColor = if (selected) EmeraldPrimary else TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("codec_profile_${profile.name.lowercase()}")
                    ) {
                        Text(
                            text = if (profile == StreamCodecProfile.LOW_LATENCY_BASELINE) "Low-Latency" else "High Quality",
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Low-Latency Jitter Buffer", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Minimizes frame queuing delay on Helio G80 / SD 662",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = state.streamSettings.lowLatencyJitterBuffer,
                    onCheckedChange = onLowLatencyJitterChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBase, checkedTrackColor = EmeraldPrimary),
                    modifier = Modifier.testTag("low_latency_jitter_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Screen Aspect Ratio", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StreamAspectRatio.entries.forEach { ratio ->
                    val selected = state.streamSettings.aspectRatio == ratio
                    OutlinedButton(
                        onClick = { onAspectRatioChanged(ratio) },
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) EmeraldPrimary else BentoBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                            contentColor = if (selected) EmeraldPrimary else TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("aspect_chip_${ratio.name.lowercase()}")
                    ) {
                        Text(ratio.label, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("In-Stream Stats Overlay (Better xCloud HUD)", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HudStatsDetailMode.entries.forEach { mode ->
                    val selected = state.streamSettings.hudMode == mode
                    OutlinedButton(
                        onClick = { onHudModeChanged(mode) },
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) EmeraldPrimary else BentoBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                            contentColor = if (selected) EmeraldPrimary else TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("hud_mode_${mode.name.lowercase()}")
                    ) {
                        Text(mode.label.substringBefore(" "), fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Stream Audio Volume Boost: ${(state.streamSettings.audioVolume * 100).toInt()}%",
                color = TextPrimary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            )
            Slider(
                value = state.streamSettings.audioVolume,
                onValueChange = onAudioVolumeChanged,
                valueRange = 0f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = EmeraldPrimary,
                    activeTrackColor = EmeraldPrimary,
                    inactiveTrackColor = BentoBorder
                ),
                modifier = Modifier.testTag("audio_volume_slider")
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("Clarity Boost (Sharpening & Contrast)", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(0 to "Off", 1 to "Low", 2 to "Med", 3 to "High").forEach { (lvl, label) ->
                    val selected = state.streamSettings.clarityBoostLevel == lvl
                    OutlinedButton(
                        onClick = { onClarityBoostChanged(lvl) },
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) EmeraldPrimary else BentoBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) EmeraldSubtleFill else ObsidianBase,
                            contentColor = if (selected) EmeraldPrimary else TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("clarity_boost_$lvl")
                    ) {
                        Text(label, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Use Better xCloud Touch Pad", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Toggle between native Project X pad and Better xCloud's built-in touch overlays",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = state.streamSettings.useBetterXCloudTouch,
                    onCheckedChange = onUseBetterXCloudTouchChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBase, checkedTrackColor = EmeraldPrimary),
                    modifier = Modifier.testTag("bx_touch_switch")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Skip Xbox Splash Videos", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Fast-skip rocket launch intro animations when connecting",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = state.streamSettings.skipSplashVideos,
                    onCheckedChange = onSkipSplashVideosChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBase, checkedTrackColor = EmeraldPrimary),
                    modifier = Modifier.testTag("skip_splash_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onStartLoopbackTest,
                enabled = state.connectionMode != ConnectionMode.SIGNALING,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BentoCardElevated,
                    contentColor = EmeraldPrimary
                ),
                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("loopback_test_button")
            ) {
                Icon(XboxIcons.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run ${state.streamSettings.targetFps.fps} FPS WebRTC Loopback Self-Test", fontWeight = FontWeight.SemiBold)
            }
        }

        XboxTile(modifier = Modifier.fillMaxWidth()) {
            Text("Controller & Input", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (state.connectedControllers.isEmpty()) {
                    "Touch overlay active • Connect Bluetooth/USB Xbox pad anytime"
                } else {
                    "Connected: ${state.connectedControllers.joinToString { it.name }}"
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Stick Deadzone: ${(state.stickDeadzone * 100).toInt()}%",
                color = TextPrimary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            )
            Slider(
                value = state.stickDeadzone,
                onValueChange = onDeadzoneChanged,
                valueRange = 0.05f..0.30f,
                colors = SliderDefaults.colors(
                    thumbColor = EmeraldPrimary,
                    activeTrackColor = EmeraldPrimary,
                    inactiveTrackColor = BentoBorder
                )
            )
            Text(
                text = "Touch Pad Opacity: ${(state.streamSettings.virtualPadOpacity * 100).toInt()}%",
                color = TextPrimary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            )
            Slider(
                value = state.streamSettings.virtualPadOpacity,
                onValueChange = onPadOpacityChanged,
                valueRange = 0.25f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = EmeraldPrimary,
                    activeTrackColor = EmeraldPrimary,
                    inactiveTrackColor = BentoBorder
                ),
                modifier = Modifier.testTag("pad_opacity_slider")
            )
            Text(
                text = "Touch Pad Size Scale: ${(state.streamSettings.virtualPadScale * 100).toInt()}%",
                color = TextPrimary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            )
            Slider(
                value = state.streamSettings.virtualPadScale,
                onValueChange = onPadScaleChanged,
                valueRange = 0.70f..1.35f,
                colors = SliderDefaults.colors(
                    thumbColor = EmeraldPrimary,
                    activeTrackColor = EmeraldPrimary,
                    inactiveTrackColor = BentoBorder
                ),
                modifier = Modifier.testTag("pad_scale_slider")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Haptic Rumble", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.rumbleEnabled,
                    onCheckedChange = onRumbleChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBase, checkedTrackColor = EmeraldPrimary)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Virtual On-Screen Pad", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.showVirtualOverlay,
                    onCheckedChange = { onVirtualOverlayChanged() },
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBase, checkedTrackColor = EmeraldPrimary)
                )
            }
        }

        LiveTelemetryDrawer(
            expanded = state.isTelemetryDrawerExpanded,
            connectionMode = state.connectionMode,
            telemetry = state.telemetry,
            logs = state.logs,
            onToggleExpand = onToggleTelemetryDrawer
        )
    }
}

@Composable
fun AccountTabView(
    authState: AuthState,
    savedAccounts: List<SavedAccountSummary>,
    achievements: List<AchievementItem>,
    isRefreshingAchievements: Boolean,
    consolesCount: Int,
    recentGamesCount: Int,
    onOpenAuthModal: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onSyncAll: () -> Unit,
    onRefreshAchievements: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_tab_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Xbox Account", color = TextPrimary, style = MaterialTheme.typography.headlineMedium)

        // Active Account Profile Card
        val signedIn = authState as? AuthState.SignedIn
        XboxTile(
            focused = signedIn != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val initial = signedIn?.gamertag?.firstOrNull()?.uppercaseChar()?.toString() ?: "X"
                XboxGamerpicAvatar(
                    avatarUrl = signedIn?.avatarUrl.orEmpty(),
                    cacheKey = if (signedIn != null) "avatar_${signedIn.xuid.ifBlank { signedIn.gamertag }}" else "",
                    initialText = initial,
                    isSignedIn = signedIn != null,
                    modifier = Modifier.size(58.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = signedIn?.gamertag ?: "Not Signed In",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (signedIn != null) {
                            val gs = if (signedIn.gamerscore.isNotBlank()) " • G ${signedIn.gamerscore}" else ""
                            "Saved: $consolesCount console(s), $recentGamesCount recent$gs"
                        } else {
                            "Sign in with Microsoft Xbox Live to sync consoles, gamerpic & achievements"
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (signedIn != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSignOut,
                        border = BorderStroke(1.dp, RedDanger.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Sign Out", color = RedDanger)
                    }
                    Button(
                        onClick = onSyncAll,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianBase),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Sync Account", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Button(
                    onClick = onOpenAuthModal,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianBase),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("account_sign_in_button")
                ) {
                    Text("Sign In with Microsoft Xbox Live", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Multi-Account Switcher Card
        XboxTile(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Saved Accounts", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Add and switch between Xbox Live profiles",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(
                    onClick = onAddAccount,
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("account_add_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Account", color = EmeraldPrimary, fontSize = 12.sp)
                }
            }

            if (savedAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    savedAccounts.forEach { acc ->
                        val acctId = acc.xuid.ifBlank { acc.gamertag }
                        val isActive = signedIn != null &&
                            ((signedIn.xuid.isNotBlank() && signedIn.xuid.equals(acc.xuid, ignoreCase = true)) ||
                                signedIn.gamertag.equals(acc.gamertag, ignoreCase = true))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) EmeraldSubtleFill else ObsidianBase)
                                .border(
                                    width = if (isActive) 1.5.dp else 1.dp,
                                    color = if (isActive) EmeraldPrimary else BentoBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { if (!isActive) onSwitchAccount(acctId) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("switch_account_$acctId"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            XboxGamerpicAvatar(
                                avatarUrl = acc.avatarUrl,
                                cacheKey = "avatar_$acctId",
                                initialText = acc.gamertag.firstOrNull()?.uppercaseChar()?.toString() ?: "X",
                                isSignedIn = isActive,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = acc.gamertag,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (acc.gamerscore.isNotBlank()) "G ${acc.gamerscore}" else "Xbox Live Profile",
                                    color = TextSecondary,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp
                                )
                            }
                            if (isActive) {
                                StatusPill(text = "ACTIVE", tone = PillTone.EMERALD)
                            } else {
                                StatusPill(text = "SWITCH", tone = PillTone.NEUTRAL)
                            }
                            IconButton(
                                onClick = { onRemoveAccount(acctId) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Remove saved account",
                                    tint = RedDanger,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Xbox Achievements Across Games & Apps
        XboxTile(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("account_achievements_section")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Achievements", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (signedIn != null) "Recent unlocks across Xbox & PC titles" else "Sign in to load your achievements",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (signedIn != null) {
                    OutlinedButton(
                        onClick = onRefreshAchievements,
                        enabled = !isRefreshingAchievements,
                        border = BorderStroke(1.dp, BentoBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        if (isRefreshingAchievements) {
                            CircularProgressIndicator(color = EmeraldPrimary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Sync achievements", tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync", color = TextPrimary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (achievements.isEmpty()) {
                Text(
                    text = if (signedIn != null) "Tap Sync to fetch your achievements across your played games." else "Sign in above to view unlocked achievements.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    achievements.forEach { ach ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianBase)
                                .border(1.dp, BentoBorder, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            XboxCachedArtworkBox(
                                imageUrl = ach.iconUrl,
                                cacheKey = "ach_${ach.id}",
                                fallbackIcon = Icons.Filled.Star,
                                maxDimPx = 128,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ach.name,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${ach.titleName} • ${ach.unlockedAtLabel}",
                                    color = EmeraldPrimary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = ach.description,
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (ach.gamerscore > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                StatusPill(text = "${ach.gamerscore}G", tone = PillTone.EMERALD)
                            }
                        }
                    }
                }
            }
        }
    }
}
