package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AmbientMusicPlayer
import com.example.input.GamepadInputEngine
import com.example.model.AchievementItem
import com.example.model.AuthState
import com.example.model.BackgroundAnimationStyle
import com.example.model.BackgroundColorPreset
import com.example.model.BackgroundMotionMode
import com.example.model.ConsoleModel
import com.example.model.ConsolePowerState
import com.example.model.DashboardTab
import com.example.model.GamepadState
import com.example.model.HudStatsDetailMode
import com.example.model.RecentGame
import com.example.model.SavedAccountSummary
import com.example.model.StreamAspectRatio
import com.example.model.StreamCodecProfile
import com.example.model.StreamFps
import com.example.model.StreamQualityPreset
import com.example.model.StreamResolution
import com.example.model.StreamSettings
import com.example.model.XboxConsole
import com.example.network.WakeOnLanSender
import com.example.network.XHomeSignalingClient
import com.example.network.XboxImageCache
import com.example.ui.components.resolveSeriesXColors
import com.example.ui.dashboard.XboxDashboardScreen
import com.example.ui.setup.SetupOnboardingScreen
import com.example.ui.stream.StreamViewportScreen
import com.example.ui.theme.ProjectXTheme
import com.example.viewmodel.ProjectXUiState
import com.example.viewmodel.ProjectXViewModel
import com.example.webrtc.SdpCodecFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context returns Project X`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Project X", appName)
    }

    @Test
    fun `smartglass wake and discovery packets have valid headers`() {
        val magic = WakeOnLanSender.buildMagicPacket("F4000C56F1879C10")
        assertEquals(0xDD.toByte(), magic[0])
        assertEquals(0x02.toByte(), magic[1])

        val discovery = WakeOnLanSender.buildDiscoveryPacket()
        assertEquals(0xDD.toByte(), discovery[0])
        assertEquals(0x00.toByte(), discovery[1])
    }

    @Test
    fun `viewmodel starts with no fake consoles and deletes down to zero`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        app.getSharedPreferences("project_x_store", Context.MODE_PRIVATE).edit().clear().commit()

        val vm1 = ProjectXViewModel(app)
        assertEquals(0, vm1.uiState.value.consoles.size)

        vm1.registerConsole(
            name = "Den Series X",
            model = ConsoleModel.SERIES_X,
            liveId = "FD00998877665544",
            lanIp = "192.168.1.77"
        )
        assertEquals(1, vm1.uiState.value.consoles.size)

        val vm2 = ProjectXViewModel(app)
        assertTrue(vm2.uiState.value.consoles.any { it.name == "Den Series X" && it.liveId == "FD00998877665544" })

        val addedId = vm2.uiState.value.consoles.first { it.name == "Den Series X" }.id
        vm2.removeConsole(addedId)
        assertEquals(0, vm2.uiState.value.consoles.size)

        val vm3 = ProjectXViewModel(app)
        assertEquals(0, vm3.uiState.value.consoles.size)
    }

    @Test
    fun `xbox dashboard empty home and tabs switch cleanly`() {
        var uiState by mutableStateOf(ProjectXUiState(setupCompleted = true))
        var registerClicked = false
        var loopbackClicked = false

        composeTestRule.setContent {
            ProjectXTheme {
                XboxDashboardScreen(
                    state = uiState,
                    onSelectTab = { uiState = uiState.copy(selectedTab = it) },
                    onSelectConsole = { uiState = uiState.copy(selectedConsoleId = it) },
                    onRegisterConsole = { _, _, _, _ -> },
                    onRemoveConsole = {},
                    onUpdateConsoleIp = { _, _ -> },
                    onUpdateConsoleLiveId = { _, _ -> },
                    onSendWakeOnLan = {},
                    onSelectQualityPreset = { uiState = uiState.copy(qualityPreset = it) },
                    onStartXHomeStream = {},
                    onStartLoopbackTest = { loopbackClicked = true },
                    onOpenAuthModal = {},
                    onDismissAuthModal = {},
                    onCheckAuthNow = {},
                    onRetryAuth = {},
                    onSignOut = {},
                    onOpenRegisterModal = {
                        registerClicked = true
                        uiState = uiState.copy(showRegisterConsoleModal = true)
                    },
                    onDismissRegisterModal = { uiState = uiState.copy(showRegisterConsoleModal = false) },
                    onRefreshConsoles = {},
                    onRefreshRecentGames = {},
                    onClearRecentGames = {},
                    onToggleTelemetryDrawer = {},
                    onDeadzoneChanged = {},
                    onRumbleChanged = {},
                    onVirtualOverlayChanged = {},
                    onClearStreamError = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hero_register_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(registerClicked)

        uiState = uiState.copy(showRegisterConsoleModal = false)
        composeTestRule.waitForIdle()

        // Switch to Saved Consoles tab (should show empty state)
        composeTestRule.onNodeWithTag("tab_consoles").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("consoles_tab_view").assertIsDisplayed()
        composeTestRule.onNodeWithTag("consoles_empty_state").assertIsDisplayed()
        assertEquals(DashboardTab.CONSOLES, uiState.selectedTab)

        // Switch to Recently Played Games tab
        composeTestRule.onNodeWithTag("tab_games").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("games_tab_view").assertIsDisplayed()
        assertEquals(DashboardTab.GAMES, uiState.selectedTab)

        // Switch to Settings tab and test 1080p toggle + Loopback test button
        composeTestRule.onNodeWithTag("tab_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("preset_1080p").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(StreamQualityPreset.FHD_1080P_60, uiState.qualityPreset)

        composeTestRule.onNodeWithTag("loopback_test_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(loopbackClicked)

        // Switch to Account tab
        composeTestRule.onNodeWithTag("tab_account").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("account_tab_view").assertIsDisplayed()
        composeTestRule.onNodeWithText("Xbox Account").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_add_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_achievements_section").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `xbox dashboard registered console shows model image and multi-account achievements`() {
        val sampleConsole = XboxConsole(
            id = "console-1",
            name = "Big Champ",
            model = ConsoleModel.ONE_X,
            liveId = "F4000C56F1879C10",
            lanIp = "192.168.1.50",
            powerState = ConsolePowerState.STANDBY
        )
        var uiState by mutableStateOf(
            ProjectXUiState(
                setupCompleted = true,
                consoles = listOf(sampleConsole),
                selectedConsoleId = sampleConsole.id,
                authState = AuthState.SignedIn(
                    gamertag = "PlayerOne",
                    userHash = "uhs1",
                    xuid = "1001",
                    gamerscore = "25591",
                    avatarUrl = "",
                    hasStreamingToken = true
                ),
                savedAccounts = listOf(
                    SavedAccountSummary("1001", "PlayerOne", "25591"),
                    SavedAccountSummary("1002", "PlayerTwo", "4200")
                ),
                achievements = listOf(
                    AchievementItem(
                        id = "ach-1",
                        titleName = "Halo Infinite",
                        name = "Spartan Legend",
                        description = "Complete campaign",
                        gamerscore = 50,
                        isUnlocked = true,
                        unlockedAtLabel = "2026-11-25"
                    )
                )
            )
        )
        var wolClicked = false
        var deletedId: String? = null
        var switchedTo: String? = null

        composeTestRule.setContent {
            ProjectXTheme {
                XboxDashboardScreen(
                    state = uiState,
                    onSelectTab = { uiState = uiState.copy(selectedTab = it) },
                    onSelectConsole = { uiState = uiState.copy(selectedConsoleId = it) },
                    onRegisterConsole = { _, _, _, _ -> },
                    onRemoveConsole = { id ->
                        deletedId = id
                        uiState = uiState.copy(
                            consoles = uiState.consoles.filterNot { it.id == id },
                            selectedConsoleId = null
                        )
                    },
                    onUpdateConsoleIp = { _, _ -> },
                    onUpdateConsoleLiveId = { _, _ -> },
                    onSendWakeOnLan = { wolClicked = true },
                    onSelectQualityPreset = { uiState = uiState.copy(qualityPreset = it) },
                    onStartXHomeStream = {},
                    onStartLoopbackTest = {},
                    onOpenAuthModal = {},
                    onSwitchAccount = { switchedTo = it },
                    onDismissAuthModal = {},
                    onCheckAuthNow = {},
                    onRetryAuth = {},
                    onSignOut = {},
                    onOpenRegisterModal = {},
                    onDismissRegisterModal = {},
                    onRefreshConsoles = {},
                    onRefreshRecentGames = {},
                    onClearRecentGames = {},
                    onToggleTelemetryDrawer = {},
                    onDeadzoneChanged = {},
                    onRumbleChanged = {},
                    onVirtualOverlayChanged = {},
                    onClearStreamError = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hero_play_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wol_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(wolClicked)

        // Check Account tab multi-account switch & achievements
        composeTestRule.onNodeWithTag("tab_account").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_account_1002").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("1002", switchedTo)
        composeTestRule.onNodeWithText("Spartan Legend").performScrollTo().assertIsDisplayed()

        // Check Consoles tab model photo + delete down to zero
        composeTestRule.onNodeWithTag("tab_consoles").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("console_model_photo_console-1", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_console_console-1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("console-1", deletedId)
        composeTestRule.onNodeWithTag("consoles_empty_state").assertIsDisplayed()
    }

    @Test
    fun `ambient pad synthesis, https image normalization, and 5 console models work`() {
        val pcm = AmbientMusicPlayer.synthesizeSoothingPad()
        assertEquals(44_100 * 28 * 2, pcm.size)
        assertTrue(pcm.any { it != 0.toShort() })
        assertTrue(pcm.all { it in -32_000..32_000 })

        BackgroundColorPreset.entries.forEach { preset ->
            val (p, s, b) = resolveSeriesXColors(preset, customHue = 280f, customIntensity = 0.8f)
            assertTrue(p.alpha > 0f && s.alpha >= 0f && b.alpha == 1f)
        }

        assertEquals(
            "https://images-eds-ssl.xboxlive.com/image?url=abc",
            XboxImageCache.normalizeXboxImageUrl("http://images-eds.xboxlive.com/image?url=abc")
        )
        assertEquals(
            "https://store-images.s-microsoft.com/image/xyz",
            XboxImageCache.normalizeXboxImageUrl("http://store-images.s-microsoft.com/image/xyz")
        )

        assertEquals(ConsoleModel.SERIES_X, XHomeSignalingClient.resolveConsoleModel("XboxSeriesX", "Living Room"))
        assertEquals(ConsoleModel.SERIES_S, XHomeSignalingClient.resolveConsoleModel("Lockhart", "Bedroom"))
        assertEquals(ConsoleModel.ONE_X, XHomeSignalingClient.resolveConsoleModel("XboxOneX", "Den"))
        assertEquals(ConsoleModel.ONE_S, XHomeSignalingClient.resolveConsoleModel("Edmonton", "Guest"))
        assertEquals(ConsoleModel.XBOX_ONE, XHomeSignalingClient.resolveConsoleModel("XboxOne", "OG"))
    }

    @Test
    fun `first-time setup onboarding shows welcome then synced ready summary`() {
        var uiState by mutableStateOf(
            ProjectXUiState(setupCompleted = false)
        )
        var finished = false
        var signInClicked = false

        composeTestRule.setContent {
            ProjectXTheme {
                SetupOnboardingScreen(
                    state = uiState,
                    onStartSignIn = { signInClicked = true },
                    onCheckAuthNow = {},
                    onRetryAuth = {},
                    onSignOut = {},
                    onDismissAuthModal = {},
                    onFinishSetup = { finished = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setup_welcome_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_get_started_button").performClick()
        assertTrue(signInClicked)

        uiState = uiState.copy(
            authState = AuthState.SignedIn(
                gamertag = "SetupTester",
                userHash = "uhs",
                xuid = "999",
                gamerscore = "1500",
                hasStreamingToken = true
            ),
            consoles = listOf(
                XboxConsole("c1", "Living Room XSX", ConsoleModel.SERIES_X, "FD00112233445566", "192.168.1.40", ConsolePowerState.ONLINE)
            ),
            recentGames = listOf(
                RecentGame("g1", "Forza Horizon 5", "Xbox Series X", "Today")
            )
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("setup_ready_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Living Room XSX").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_finish_button").performScrollTo()
        composeTestRule.onNodeWithText("Recently Played (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Forza Horizon 5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_finish_button").performClick()
        assertTrue(finished)
    }

    @Test
    fun `settings tab toggles ambient music and series x background options`() {
        var uiState by mutableStateOf(ProjectXUiState(setupCompleted = true, selectedTab = DashboardTab.SETTINGS))

        composeTestRule.setContent {
            ProjectXTheme {
                XboxDashboardScreen(
                    state = uiState,
                    onSelectTab = { uiState = uiState.copy(selectedTab = it) },
                    onSelectConsole = {},
                    onRegisterConsole = { _, _, _, _ -> },
                    onRemoveConsole = {},
                    onUpdateConsoleIp = { _, _ -> },
                    onUpdateConsoleLiveId = { _, _ -> },
                    onSendWakeOnLan = {},
                    onSelectQualityPreset = {},
                    onStartXHomeStream = {},
                    onStartLoopbackTest = {},
                    onOpenAuthModal = {},
                    onDismissAuthModal = {},
                    onCheckAuthNow = {},
                    onRetryAuth = {},
                    onSignOut = {},
                    onOpenRegisterModal = {},
                    onDismissRegisterModal = {},
                    onRefreshConsoles = {},
                    onRefreshRecentGames = {},
                    onClearRecentGames = {},
                    onToggleTelemetryDrawer = {},
                    onDeadzoneChanged = {},
                    onRumbleChanged = {},
                    onVirtualOverlayChanged = {},
                    onClearStreamError = {},
                    onAmbientMusicChanged = { uiState = uiState.copy(ambientMusicEnabled = it) },
                    onBgMotionChanged = { uiState = uiState.copy(backgroundMotionMode = it) },
                    onBgAnimationStyleChanged = { uiState = uiState.copy(backgroundAnimationStyle = it) },
                    onBgColorPresetChanged = { uiState = uiState.copy(backgroundColorPreset = it) },
                    onBgCustomColorChanged = { h, i ->
                        uiState = uiState.copy(
                            backgroundColorPreset = BackgroundColorPreset.CUSTOM,
                            backgroundCustomHue = h,
                            backgroundCustomIntensity = i
                        )
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ambient_music_switch").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertFalse(uiState.ambientMusicEnabled)

        composeTestRule.onNodeWithTag("bg_mode_moving").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(BackgroundMotionMode.MOVING, uiState.backgroundMotionMode)

        composeTestRule.onNodeWithTag("bg_style_ripples").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(BackgroundAnimationStyle.RIPPLES, uiState.backgroundAnimationStyle)

        composeTestRule.onNodeWithTag("bg_style_midnight").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(BackgroundAnimationStyle.MIDNIGHT, uiState.backgroundAnimationStyle)

        composeTestRule.onNodeWithTag("bg_style_motes").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(BackgroundAnimationStyle.MOTES, uiState.backgroundAnimationStyle)

        composeTestRule.onNodeWithTag("bg_color_blue").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(BackgroundColorPreset.BLUE, uiState.backgroundColorPreset)
    }

    @Test
    fun `xbox cloud binary input framing and custom sdp codec filter match specification`() {
        val meta = GamepadInputEngine.encodeClientMetadataFrame(sequence = 1, timestampMs = 100.0)
        assertEquals(GamepadInputEngine.CLIENT_METADATA_FRAME_SIZE, meta.size)
        val metaBuf = ByteBuffer.wrap(meta).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(GamepadInputEngine.REPORT_TYPE_CLIENT_METADATA, metaBuf.getShort(0))
        assertEquals(1, metaBuf.getInt(2))

        val padFrame = GamepadInputEngine.encodeBinaryFrame(
            state = GamepadState(
                leftStickX = 0.5f,
                rightTrigger = 1.0f,
                buttonsMask = GamepadState.BTN_A or GamepadState.BTN_NEXUS
            ),
            sequence = 7,
            timestampMs = 250.0
        )
        assertEquals(GamepadInputEngine.BINARY_FRAME_SIZE, padFrame.size)
        val padBuf = ByteBuffer.wrap(padFrame).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(GamepadInputEngine.REPORT_TYPE_GAMEPAD, padBuf.getShort(0))
        assertEquals(7, padBuf.getInt(2))
        assertEquals(1.toByte(), padBuf.get(14))
        assertEquals(0.toByte(), padBuf.get(15))
        val expectedMask = ((1 shl 4) or (1 shl 1)).toShort()
        assertEquals(expectedMask, padBuf.getShort(16))

        val sampleSdp = """
            v=0
            o=- 123 2 IN IP4 127.0.0.1
            s=-
            t=0 0
            m=video 9 UDP/TLS/RTP/SAVPF 96 98 100
            c=IN IP4 0.0.0.0
            a=rtpmap:96 VP8/90000
            a=rtpmap:98 H264/90000
            a=fmtp:98 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=4d0032
            a=rtpmap:100 H264/90000
            a=fmtp:100 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f
        """.trimIndent()

        val filteredBaseline = SdpCodecFilter.enforceH264ProfileAndBitrate(
            sampleSdp,
            StreamSettings(
                resolution = StreamResolution.P900,
                targetFps = StreamFps.FPS_45,
                maxBitrateMbps = 14,
                codecProfile = StreamCodecProfile.LOW_LATENCY_BASELINE
            )
        )
        assertFalse(filteredBaseline.contains("VP8"))
        assertTrue(filteredBaseline.contains("m=video 9 UDP/TLS/RTP/SAVPF 100 98"))
        assertTrue(filteredBaseline.contains("b=AS:14000"))
        assertTrue(filteredBaseline.contains("x-google-max-bitrate=14000"))
        assertTrue(filteredBaseline.contains("max-fr=45"))

        val filteredMain = SdpCodecFilter.enforceH264ProfileAndBitrate(
            sampleSdp,
            StreamSettings(
                resolution = StreamResolution.P1080,
                targetFps = StreamFps.FPS_60,
                maxBitrateMbps = 18,
                codecProfile = StreamCodecProfile.HIGH_QUALITY_MAIN
            )
        )
        assertTrue(filteredMain.contains("m=video 9 UDP/TLS/RTP/SAVPF 98 100"))
        assertTrue(filteredMain.contains("b=AS:18000"))
        assertTrue(filteredMain.contains("max-fr=60"))
    }

    @Test
    fun `settings tab customizes resolution, fps, codec profile, aspect ratio, and hud mode`() {
        var uiState by mutableStateOf(ProjectXUiState(setupCompleted = true, selectedTab = DashboardTab.SETTINGS))

        composeTestRule.setContent {
            ProjectXTheme {
                XboxDashboardScreen(
                    state = uiState,
                    onSelectTab = { uiState = uiState.copy(selectedTab = it) },
                    onSelectConsole = {},
                    onRegisterConsole = { _, _, _, _ -> },
                    onRemoveConsole = {},
                    onUpdateConsoleIp = { _, _ -> },
                    onUpdateConsoleLiveId = { _, _ -> },
                    onSendWakeOnLan = {},
                    onSelectQualityPreset = {},
                    onStartXHomeStream = {},
                    onStartLoopbackTest = {},
                    onOpenAuthModal = {},
                    onDismissAuthModal = {},
                    onCheckAuthNow = {},
                    onRetryAuth = {},
                    onSignOut = {},
                    onOpenRegisterModal = {},
                    onDismissRegisterModal = {},
                    onRefreshConsoles = {},
                    onRefreshRecentGames = {},
                    onClearRecentGames = {},
                    onToggleTelemetryDrawer = {},
                    onDeadzoneChanged = {},
                    onRumbleChanged = {},
                    onVirtualOverlayChanged = {},
                    onClearStreamError = {},
                    onSelectResolution = {
                        uiState = uiState.copy(streamSettings = uiState.streamSettings.copy(resolution = it))
                    },
                    onSelectFps = {
                        uiState = uiState.copy(streamSettings = uiState.streamSettings.copy(targetFps = it))
                    },
                    onCodecProfileChanged = {
                        uiState = uiState.copy(streamSettings = uiState.streamSettings.copy(codecProfile = it))
                    },
                    onAspectRatioChanged = {
                        uiState = uiState.copy(streamSettings = uiState.streamSettings.copy(aspectRatio = it))
                    },
                    onHudModeChanged = {
                        uiState = uiState.copy(streamSettings = uiState.streamSettings.copy(hudMode = it))
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("res_chip_p900").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(StreamResolution.P900, uiState.streamSettings.resolution)

        composeTestRule.onNodeWithTag("fps_chip_45").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(StreamFps.FPS_45, uiState.streamSettings.targetFps)

        composeTestRule.onNodeWithTag("codec_profile_high_quality_main").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(StreamCodecProfile.HIGH_QUALITY_MAIN, uiState.streamSettings.codecProfile)

        composeTestRule.onNodeWithTag("aspect_chip_cover").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(StreamAspectRatio.COVER, uiState.streamSettings.aspectRatio)

        composeTestRule.onNodeWithTag("hud_mode_minimal").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(HudStatsDetailMode.MINIMAL, uiState.streamSettings.hudMode)
    }

    @Test
    fun `stream settings and greenlight sdp codec parameters work`() {
        val hdSettings = StreamSettings(
            resolution = StreamResolution.P1080,
            targetFps = StreamFps.FPS_60,
            maxBitrateMbps = 16,
            codecProfile = StreamCodecProfile.HIGH_QUALITY_MAIN,
            aspectRatio = StreamAspectRatio.COVER,
            clarityBoostLevel = 2,
            audioVolume = 1.4f,
            useBetterXCloudTouch = true,
            skipSplashVideos = true
        )

        assertEquals(16000, hdSettings.maxBitrateKbps)
        assertEquals(8000, hdSettings.minBitrateKbps)
        assertEquals(12480, hdSettings.startBitrateKbps)
        assertTrue(hdSettings.summaryLabel.contains("1080p"))
        assertTrue(hdSettings.summaryLabel.contains("60"))
        assertTrue(hdSettings.summaryLabel.contains("16Mbps"))

        val mockSdp = "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\na=rtpmap:96 H264/90000\r\na=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1\r\n"
        val filtered = SdpCodecFilter.enforceH264ProfileAndBitrate(mockSdp, hdSettings)
        assertTrue(filtered.contains("H264"))
        assertTrue(filtered.contains("x-google-max-bitrate=16000"))
    }

    @Test
    fun `stream viewport aspect ratio switches between Fit 16_9 and Zoom Fill in Quick Tune`() {
        var settings by mutableStateOf(StreamSettings(aspectRatio = StreamAspectRatio.FIT))

        composeTestRule.setContent {
            ProjectXTheme {
                StreamViewportScreen(
                    connectionMode = com.example.model.ConnectionMode.STREAMING_LOOPBACK,
                    telemetry = com.example.model.TelemetrySnapshot(fps = 60, bitrateKbps = 12000),
                    streamSettings = settings,
                    showVirtualOverlay = true,
                    onVirtualGamepadState = {},
                    onToggleVirtualOverlay = {},
                    onUpdateAspectRatio = { settings = settings.copy(aspectRatio = it) },
                    onDisconnect = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stream_aspect_container_fit").assertIsDisplayed()

        composeTestRule.onNodeWithTag("stream_tune_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stream_quick_tune_drawer").assertIsDisplayed()

        composeTestRule.onNodeWithTag("tune_aspect_cover").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(StreamAspectRatio.COVER, settings.aspectRatio)
        composeTestRule.onNodeWithTag("stream_aspect_container_cover").assertIsDisplayed()
    }
}
