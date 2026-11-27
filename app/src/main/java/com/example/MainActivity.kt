package com.example

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.XboxIntroSplash
import com.example.ui.dashboard.XboxDashboardScreen
import com.example.ui.setup.SetupOnboardingScreen
import com.example.ui.stream.StreamViewportScreen
import com.example.ui.theme.ProjectXTheme
import com.example.viewmodel.ProjectXViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ProjectXViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            ProjectXTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var showIntroSplash by rememberSaveable { mutableStateOf(true) }

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        !state.setupCompleted -> {
                            SetupOnboardingScreen(
                                state = state,
                                onStartSignIn = viewModel::openAuthModal,
                                onCheckAuthNow = viewModel::checkAuthNow,
                                onRetryAuth = viewModel::startDeviceCodeSignIn,
                                onSignOut = viewModel::signOut,
                                onDismissAuthModal = viewModel::dismissAuthModal,
                                onFinishSetup = viewModel::completeSetup
                            )
                        }

                        state.isStreaming -> {
                            // Dispose dashboard tree completely when streaming to keep RAM under 95 MB
                            val focusedConsole = state.selectedConsole
                            StreamViewportScreen(
                                connectionMode = state.connectionMode,
                                telemetry = state.telemetry,
                                streamSettings = state.streamSettings,
                                showVirtualOverlay = state.showVirtualOverlay,
                                targetConsoleName = focusedConsole?.name.orEmpty(),
                                targetServerId = focusedConsole?.liveId.orEmpty(),
                                gamepadStateProvider = { viewModel.currentGamepadState },
                                onVirtualGamepadState = viewModel::updateVirtualGamepadState,
                                onToggleVirtualOverlay = viewModel::toggleVirtualOverlay,
                                onAttachRenderer = viewModel::attachSurfaceRenderer,
                                onUpdateRendererAspectRatio = viewModel::updateRendererAspectRatio,
                                onDetachRenderer = viewModel::detachSurfaceRenderer,
                                onUpdateResolution = viewModel::setStreamResolution,
                                onUpdateFps = viewModel::setStreamFps,
                                onUpdateBitrateMbps = viewModel::setStreamBitrateMbps,
                                onUpdateAspectRatio = viewModel::setStreamAspectRatio,
                                onUpdateAudioVolume = viewModel::setStreamAudioVolume,
                                onUpdateHudMode = viewModel::setHudStatsMode,
                                onUpdatePadOpacity = viewModel::setVirtualPadOpacity,
                                onUpdatePadScale = viewModel::setVirtualPadScale,
                                onUpdateClarityBoost = viewModel::setClarityBoostLevel,
                                onUpdateUseBetterXCloudTouch = viewModel::setUseBetterXCloudTouch,
                                onStreamStatsJson = viewModel::updateTelemetryFromWebView,
                                onUpdateInputHz = viewModel::updateInputHz,
                                onWebRumble = viewModel::triggerRumbleFromWeb,
                                onWebLog = viewModel::logWebEvent,
                                onDisconnect = viewModel::stopStreaming
                            )
                        }

                        else -> {
                            XboxDashboardScreen(
                                state = state,
                                onSelectTab = viewModel::selectTab,
                                onSelectConsole = viewModel::selectConsole,
                                onRegisterConsole = viewModel::registerConsole,
                                onRemoveConsole = viewModel::removeConsole,
                                onUpdateConsoleIp = viewModel::updateConsoleLanIp,
                                onUpdateConsoleLiveId = viewModel::updateConsoleLiveId,
                                onSendWakeOnLan = viewModel::sendWakeOnLan,
                                onScanConsoleLan = viewModel::scanConsoleLan,
                                onSelectQualityPreset = viewModel::selectQualityPreset,
                                onStartXHomeStream = viewModel::startXHomeStream,
                                onStartLoopbackTest = viewModel::startLoopbackSelfTest,
                                onOpenAuthModal = viewModel::openAuthModal,
                                onOpenAddAccount = viewModel::openAddAccountModal,
                                onSwitchAccount = viewModel::switchAccount,
                                onRemoveSavedAccount = viewModel::removeSavedAccount,
                                onDismissAuthModal = viewModel::dismissAuthModal,
                                onCheckAuthNow = viewModel::checkAuthNow,
                                onRetryAuth = viewModel::startDeviceCodeSignIn,
                                onSignOut = viewModel::signOut,
                                onOpenRegisterModal = viewModel::openRegisterConsoleModal,
                                onDismissRegisterModal = viewModel::dismissRegisterConsoleModal,
                                onRefreshConsoles = viewModel::refreshConsolesFromXHome,
                                onRefreshRecentGames = viewModel::refreshRecentlyPlayedGames,
                                onRefreshAchievements = viewModel::refreshAchievements,
                                onRefreshProfile = viewModel::refreshProfile,
                                onClearRecentGames = viewModel::clearRecentGames,
                                onToggleTelemetryDrawer = viewModel::toggleTelemetryDrawer,
                                onDeadzoneChanged = viewModel::setStickDeadzone,
                                onRumbleChanged = viewModel::setRumbleEnabled,
                                onVirtualOverlayChanged = viewModel::toggleVirtualOverlay,
                                onClearStreamError = viewModel::clearStreamError,
                                onAmbientMusicChanged = viewModel::setAmbientMusicEnabled,
                                onCustomAmbientTrackSelected = viewModel::setCustomAmbientTrack,
                                onClearCustomAmbientTrack = viewModel::clearCustomAmbientTrack,
                                onBgMotionChanged = viewModel::setBackgroundMotionMode,
                                onBgAnimationStyleChanged = viewModel::setBackgroundAnimationStyle,
                                onBgColorPresetChanged = viewModel::setBackgroundColorPreset,
                                onBgCustomColorChanged = viewModel::setBackgroundCustomColor,
                                onSelectResolution = viewModel::setStreamResolution,
                                onSelectFps = viewModel::setStreamFps,
                                onBitrateMbpsChanged = viewModel::setStreamBitrateMbps,
                                onCodecProfileChanged = viewModel::setStreamCodecProfile,
                                onLowLatencyJitterChanged = viewModel::setLowLatencyJitterBuffer,
                                onAspectRatioChanged = viewModel::setStreamAspectRatio,
                                onHudModeChanged = viewModel::setHudStatsMode,
                                onAudioVolumeChanged = viewModel::setStreamAudioVolume,
                                onPadOpacityChanged = viewModel::setVirtualPadOpacity,
                                onPadScaleChanged = viewModel::setVirtualPadScale,
                                onClarityBoostChanged = viewModel::setClarityBoostLevel,
                                onUseBetterXCloudTouchChanged = viewModel::setUseBetterXCloudTouch,
                                onSkipSplashVideosChanged = viewModel::setSkipSplashVideos
                            )
                        }
                    }

                    if (showIntroSplash) {
                        XboxIntroSplash(onFinished = { showIntroSplash = false })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
        viewModel.setAppInForeground(true)
    }

    override fun onPause() {
        super.onPause()
        viewModel.setAppInForeground(false)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (viewModel.uiState.value.isStreaming && viewModel.onPhysicalMotionEvent(ev)) {
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.uiState.value.isStreaming && viewModel.onPhysicalKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
