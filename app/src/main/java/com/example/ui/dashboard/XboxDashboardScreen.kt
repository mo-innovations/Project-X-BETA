package com.example.ui.dashboard

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AuthState
import com.example.model.BackgroundAnimationStyle
import com.example.model.BackgroundColorPreset
import com.example.model.BackgroundMotionMode
import com.example.model.ConnectionMode
import com.example.model.ConsoleModel
import com.example.model.ConsolePowerState
import com.example.model.DashboardTab
import com.example.model.HudStatsDetailMode
import com.example.model.RecentGame
import com.example.model.StreamAspectRatio
import com.example.model.StreamCodecProfile
import com.example.model.StreamFps
import com.example.model.StreamQualityPreset
import com.example.model.StreamResolution
import com.example.model.XboxConsole
import com.example.ui.components.ConsoleModelThumbnail
import com.example.ui.components.PillTone
import com.example.ui.components.RegisterConsoleModal
import com.example.ui.components.StatusPill
import com.example.ui.components.XalDeviceCodeAuthModal
import com.example.ui.components.XboxCachedArtworkBox
import com.example.ui.components.XboxIcons
import com.example.ui.components.XboxTopBar
import com.example.ui.components.XboxWavesBackground
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.RedDanger
import com.example.ui.theme.RedSubtleFill
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.XboxTileSurface
import com.example.viewmodel.ProjectXUiState

@Composable
fun XboxDashboardScreen(
    state: ProjectXUiState,
    onSelectTab: (DashboardTab) -> Unit,
    onSelectConsole: (String) -> Unit,
    onRegisterConsole: (String, ConsoleModel, String, String) -> Unit,
    onRemoveConsole: (String) -> Unit,
    onUpdateConsoleIp: (String, String) -> Unit,
    onUpdateConsoleLiveId: (String, String) -> Unit,
    onSendWakeOnLan: (String?) -> Unit,
    onScanConsoleLan: (String?) -> Unit = {},
    onSelectQualityPreset: (StreamQualityPreset) -> Unit,
    onStartXHomeStream: (String?) -> Unit,
    onStartLoopbackTest: () -> Unit,
    onOpenAuthModal: () -> Unit,
    onOpenAddAccount: () -> Unit = onOpenAuthModal,
    onSwitchAccount: (String) -> Unit = {},
    onRemoveSavedAccount: (String) -> Unit = {},
    onDismissAuthModal: () -> Unit,
    onCheckAuthNow: () -> Unit,
    onRetryAuth: () -> Unit,
    onSignOut: () -> Unit,
    onOpenRegisterModal: () -> Unit,
    onDismissRegisterModal: () -> Unit,
    onRefreshConsoles: () -> Unit,
    onRefreshRecentGames: () -> Unit,
    onRefreshAchievements: () -> Unit = {},
    onRefreshProfile: () -> Unit = {},
    onClearRecentGames: () -> Unit,
    onToggleTelemetryDrawer: () -> Unit,
    onDeadzoneChanged: (Float) -> Unit,
    onRumbleChanged: (Boolean) -> Unit,
    onVirtualOverlayChanged: () -> Unit,
    onClearStreamError: () -> Unit,
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
    BackHandler(enabled = state.selectedTab != DashboardTab.HOME) {
        onSelectTab(DashboardTab.HOME)
    }

    if (state.showAuthModal) {
        XalDeviceCodeAuthModal(
            authState = state.authState,
            onCheckNow = onCheckAuthNow,
            onRetry = onRetryAuth,
            onSignOut = onSignOut,
            onDismiss = onDismissAuthModal
        )
    }

    if (state.showRegisterConsoleModal) {
        RegisterConsoleModal(
            onRegister = onRegisterConsole,
            onSyncFromAccount = onRefreshConsoles,
            onDismiss = onDismissRegisterModal
        )
    }

    XboxWavesBackground(
        motionMode = state.backgroundMotionMode,
        animationStyle = state.backgroundAnimationStyle,
        colorPreset = state.backgroundColorPreset,
        customHue = state.backgroundCustomHue,
        customIntensity = state.backgroundCustomIntensity
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 600.dp
            val scrollState = rememberScrollState()

            LaunchedEffect(state.selectedTab) {
                scrollState.scrollTo(0)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                XboxTopBar(
                    isCompact = !isWide,
                    selectedTab = state.selectedTab,
                    authState = state.authState,
                    activeLanIp = state.selectedConsole?.lanIp.orEmpty(),
                    connectionMode = state.connectionMode,
                    onSelectTab = onSelectTab,
                    onOpenRegisterConsole = onOpenRegisterModal
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = if (isWide) 24.dp else 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    state.streamErrorMessage?.let { errMsg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(RedSubtleFill)
                                .border(1.dp, RedDanger.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = errMsg,
                                color = RedDanger,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onClearStreamError, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss error", tint = RedDanger)
                            }
                        }
                    }

                    when (state.selectedTab) {
                        DashboardTab.HOME -> XboxHomeContent(
                            state = state,
                            isWide = isWide,
                            onSelectTab = onSelectTab,
                            onSelectConsole = onSelectConsole,
                            onStartStream = onStartXHomeStream,
                            onWakeConsole = onSendWakeOnLan,
                            onOpenRegisterModal = onOpenRegisterModal,
                            onOpenAuthModal = onOpenAuthModal,
                            onSyncConsoles = onRefreshConsoles
                        )

                        DashboardTab.CONSOLES -> ConsolesTabView(
                            state = state,
                            onSelectConsole = onSelectConsole,
                            onStreamConsole = onStartXHomeStream,
                            onWakeConsole = onSendWakeOnLan,
                            onScanLan = onScanConsoleLan,
                            onUpdateIp = onUpdateConsoleIp,
                            onUpdateLiveId = onUpdateConsoleLiveId,
                            onRemoveConsole = onRemoveConsole,
                            onOpenRegister = onOpenRegisterModal,
                            onSyncConsoles = onRefreshConsoles
                        )

                        DashboardTab.GAMES -> RecentGamesTabView(
                            recentGames = state.recentGames,
                            authState = state.authState,
                            isRefreshing = state.isRefreshingGames,
                            onRefreshGames = onRefreshRecentGames,
                            onClearHistory = onClearRecentGames,
                            onLaunchGame = { game ->
                                onStartXHomeStream(game.consoleId ?: state.selectedConsoleId)
                            },
                            onSignIn = onOpenAuthModal
                        )

                        DashboardTab.SETTINGS -> SettingsTabView(
                            state = state,
                            onSelectPreset = onSelectQualityPreset,
                            onStartLoopbackTest = onStartLoopbackTest,
                            onDeadzoneChanged = onDeadzoneChanged,
                            onRumbleChanged = onRumbleChanged,
                            onVirtualOverlayChanged = onVirtualOverlayChanged,
                            onToggleTelemetryDrawer = onToggleTelemetryDrawer,
                            onAmbientMusicChanged = onAmbientMusicChanged,
                            onCustomAmbientTrackSelected = onCustomAmbientTrackSelected,
                            onClearCustomAmbientTrack = onClearCustomAmbientTrack,
                            onBgMotionChanged = onBgMotionChanged,
                            onBgAnimationStyleChanged = onBgAnimationStyleChanged,
                            onBgColorPresetChanged = onBgColorPresetChanged,
                            onBgCustomColorChanged = onBgCustomColorChanged,
                            onSelectResolution = onSelectResolution,
                            onSelectFps = onSelectFps,
                            onBitrateMbpsChanged = onBitrateMbpsChanged,
                            onCodecProfileChanged = onCodecProfileChanged,
                            onLowLatencyJitterChanged = onLowLatencyJitterChanged,
                            onAspectRatioChanged = onAspectRatioChanged,
                            onHudModeChanged = onHudModeChanged,
                            onAudioVolumeChanged = onAudioVolumeChanged,
                            onPadOpacityChanged = onPadOpacityChanged,
                            onPadScaleChanged = onPadScaleChanged,
                            onClarityBoostChanged = onClarityBoostChanged,
                            onUseBetterXCloudTouchChanged = onUseBetterXCloudTouchChanged,
                            onSkipSplashVideosChanged = onSkipSplashVideosChanged
                        )

                        DashboardTab.ACCOUNT -> AccountTabView(
                            authState = state.authState,
                            savedAccounts = state.savedAccounts,
                            achievements = state.achievements,
                            isRefreshingAchievements = state.isRefreshingAchievements,
                            consolesCount = state.consoles.size,
                            recentGamesCount = state.recentGames.size,
                            onOpenAuthModal = onOpenAuthModal,
                            onAddAccount = onOpenAddAccount,
                            onSwitchAccount = onSwitchAccount,
                            onRemoveAccount = onRemoveSavedAccount,
                            onSyncAll = {
                                onRefreshProfile()
                                onRefreshConsoles()
                                onRefreshRecentGames()
                                onRefreshAchievements()
                            },
                            onRefreshAchievements = onRefreshAchievements,
                            onSignOut = onSignOut
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun XboxHomeContent(
    state: ProjectXUiState,
    isWide: Boolean,
    onSelectTab: (DashboardTab) -> Unit,
    onSelectConsole: (String) -> Unit,
    onStartStream: (String?) -> Unit,
    onWakeConsole: (String?) -> Unit,
    onOpenRegisterModal: () -> Unit,
    onOpenAuthModal: () -> Unit,
    onSyncConsoles: () -> Unit
) {
    val activeConsole = state.selectedConsole

    // Upper Xbox Showcase Section
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isWide) 18.dp else 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (activeConsole != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(text = activeConsole.model.shortTag, tone = PillTone.EMERALD)
                        val powerTone = when (activeConsole.powerState) {
                            ConsolePowerState.ONLINE -> PillTone.EMERALD
                            ConsolePowerState.STANDBY -> PillTone.AMBER
                            ConsolePowerState.OFFLINE -> PillTone.RED
                        }
                        StatusPill(text = activeConsole.powerState.label, tone = powerTone)
                        StatusPill(text = state.streamSettings.summaryLabel, tone = PillTone.NEUTRAL)
                    }

                    Text(
                        text = activeConsole.name,
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "LAN ${activeConsole.lanIp.ifBlank { "Auto" }} • LiveID ${activeConsole.liveId}",
                        color = TextSecondary,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))
                ConsoleModelThumbnail(
                    model = activeConsole.model,
                    modifier = Modifier
                        .width(124.dp)
                        .height(94.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onStartStream(activeConsole.id) },
                    enabled = state.connectionMode == ConnectionMode.IDLE,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianBase),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("hero_play_button")
                ) {
                    if (state.connectionMode == ConnectionMode.SIGNALING) {
                        CircularProgressIndicator(color = ObsidianBase, strokeWidth = 2.5.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.cd_start_stream), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.hero_title), fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onWakeConsole(activeConsole.id) },
                    enabled = !state.isWakingConsole,
                    border = BorderStroke(1.dp, BentoBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = XboxTileSurface, contentColor = TextPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("wol_button")
                ) {
                    if (state.isWakingConsole) {
                        CircularProgressIndicator(color = EmeraldPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(XboxIcons.Power, contentDescription = stringResource(R.string.cd_send_wol), tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.isWakingConsole) "Waking..." else "Wake")
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = "XHOME READY", tone = PillTone.EMERALD)
                StatusPill(text = state.streamSettings.summaryLabel, tone = PillTone.NEUTRAL)
            }

            Text(
                text = "No Consoles Registered",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = "Register your Xbox Series X|S or Xbox One console, or sign in with Xbox Live to sync consoles.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenRegisterModal,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = ObsidianBase),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("hero_register_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_register_console), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Register Console", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = if (state.authState is AuthState.SignedIn) onSyncConsoles else onOpenAuthModal,
                    border = BorderStroke(1.dp, BentoBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = XboxTileSurface, contentColor = TextPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("hero_sync_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.authState is AuthState.SignedIn) "Sync Account" else "Sign In")
                }
            }
        }

        if (!state.wolStatusMessage.isNullOrBlank()) {
            Text(
                text = state.wolStatusMessage,
                color = EmeraldPrimary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                modifier = Modifier.testTag("wol_status_text")
            )
        }
    }

    // Tier 1: Xbox Horizontal Shelf
    Text(
        text = "Jump back in",
        color = TextSecondary,
        style = MaterialTheme.typography.titleSmall
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 12.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth()
    ) {
        items(state.consoles, key = { it.id }) { console ->
            val isFocused = console.id == state.selectedConsoleId
            ConsoleShelfTile(
                console = console,
                focused = isFocused,
                onClick = { onSelectConsole(console.id) }
            )
        }

        item(key = "register_shelf_tile") {
            Box(
                modifier = Modifier
                    .width(126.dp)
                    .height(126.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(XboxTileSurface)
                    .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenRegisterModal)
                    .padding(12.dp)
                    .testTag("shelf_register_tile")
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_register_console),
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Register\nConsole",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (state.recentGames.isNotEmpty()) {
            items(state.recentGames, key = { "recent-${it.titleId}" }) { game ->
                RecentGameShelfTile(
                    game = game,
                    onClick = { onStartStream(game.consoleId ?: activeConsole?.id) }
                )
            }
        } else if (state.authState !is AuthState.SignedIn) {
            item(key = "recent_signin_prompt") {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(126.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(XboxTileSurface)
                        .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenAuthModal)
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = XboxIcons.Gamepad,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Sign in for\nRecent Games",
                            color = TextSecondary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }

    // Tier 2: Bottom 4 Quick Action Cards
    val cards = listOf(
        BottomTileSpec(
            title = "Saved Consoles",
            subtitle = if (state.consoles.isEmpty()) "None registered" else "${state.consoles.size} console(s)",
            icon = XboxIcons.Console,
            accent = Brush.linearGradient(listOf(Color(0xFF173D24), XboxTileSurface)),
            tag = "card_consoles",
            onClick = { onSelectTab(DashboardTab.CONSOLES) }
        ),
        BottomTileSpec(
            title = "Recently Played",
            subtitle = if (state.recentGames.isEmpty()) "View or sync games" else "${state.recentGames.size} recent title(s)",
            icon = XboxIcons.Gamepad,
            accent = Brush.linearGradient(listOf(Color(0xFF1A2B3C), XboxTileSurface)),
            tag = "card_recent_games",
            onClick = { onSelectTab(DashboardTab.GAMES) }
        ),
        BottomTileSpec(
            title = "Stream & Controls",
            subtitle = state.streamSettings.summaryLabel,
            icon = Icons.Filled.Settings,
            accent = Brush.linearGradient(listOf(Color(0xFF282414), XboxTileSurface)),
            tag = "card_settings",
            onClick = { onSelectTab(DashboardTab.SETTINGS) }
        ),
        BottomTileSpec(
            title = if (state.authState is AuthState.SignedIn) state.authState.gamertag else "Xbox Account",
            subtitle = if (state.authState is AuthState.SignedIn) {
                "${state.savedAccounts.size} account(s) • ${state.achievements.size} achv"
            } else {
                "Sign in to save & sync"
            },
            icon = Icons.Filled.Person,
            accent = Brush.linearGradient(listOf(Color(0xFF142A24), XboxTileSurface)),
            tag = "card_account",
            onClick = { onSelectTab(DashboardTab.ACCOUNT) }
        )
    )

    if (isWide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            cards.forEach { card ->
                XboxBottomActionTile(spec = card, modifier = Modifier.weight(1f))
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                XboxBottomActionTile(spec = cards[0], modifier = Modifier.weight(1f))
                XboxBottomActionTile(spec = cards[1], modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                XboxBottomActionTile(spec = cards[2], modifier = Modifier.weight(1f))
                XboxBottomActionTile(spec = cards[3], modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ConsoleShelfTile(
    console: XboxConsole,
    focused: Boolean,
    onClick: () -> Unit
) {
    val widthDp = if (focused) 158.dp else 136.dp
    val heightDp = if (focused) 144.dp else 126.dp
    val borderColor = if (focused) EmeraldPrimary else BentoBorder
    val borderWidth = if (focused) 2.5.dp else 1.dp

    Column(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (focused) {
                        listOf(Color(0xFF163D22), XboxTileSurface)
                    } else {
                        listOf(XboxTileSurface, XboxTileSurface)
                    }
                )
            )
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag("console_option_${console.model.shortTag.lowercase()}"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConsoleModelThumbnail(
                model = console.model,
                modifier = Modifier
                    .width(64.dp)
                    .height(48.dp)
            )
            StatusPill(
                text = console.model.shortTag,
                tone = if (focused) PillTone.EMERALD else PillTone.NEUTRAL
            )
        }

        Column {
            Text(
                text = console.name,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = console.lanIp.ifBlank { "xHome" },
                color = TextSecondary,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RecentGameShelfTile(
    game: RecentGame,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .height(126.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(XboxTileSurface)
            .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            XboxCachedArtworkBox(
                imageUrl = game.imageUrl,
                cacheKey = "title_${game.titleId}",
                fallbackIcon = XboxIcons.Gamepad,
                maxDimPx = 192,
                modifier = Modifier.size(46.dp)
            )
            StatusPill(text = "RECENT", tone = PillTone.NEUTRAL)
        }
        Column {
            Text(
                text = game.name,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = game.lastPlayedLabel,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

private data class BottomTileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Brush,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
private fun XboxBottomActionTile(
    spec: BottomTileSpec,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(spec.accent)
            .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = spec.onClick)
            .padding(14.dp)
            .testTag(spec.tag),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column {
            Text(
                text = spec.title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.subtitle,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
