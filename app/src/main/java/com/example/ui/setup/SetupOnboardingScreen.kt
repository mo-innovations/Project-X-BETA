package com.example.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuthState
import com.example.ui.components.ConsoleModelThumbnail
import com.example.ui.components.PillTone
import com.example.ui.components.StatusPill
import com.example.ui.components.XalDeviceCodeAuthModal
import com.example.ui.components.XboxCachedArtworkBox
import com.example.ui.components.XboxGamerpicAvatar
import com.example.ui.components.XboxIcons
import com.example.ui.components.XboxTile
import com.example.ui.components.XboxWavesBackground
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ProjectXUiState

/**
 * First-time launch setup screen replacing the heavy boot video.
 * 1. Welcomes the user and prompts Xbox Live sign-in.
 * 2. Syncs consoles + recently played games and displays a "ready to go" summary before entering the dashboard.
 */
@Composable
fun SetupOnboardingScreen(
    state: ProjectXUiState,
    onStartSignIn: () -> Unit,
    onCheckAuthNow: () -> Unit,
    onRetryAuth: () -> Unit,
    onSignOut: () -> Unit,
    onDismissAuthModal: () -> Unit,
    onFinishSetup: () -> Unit
) {
    if (state.showAuthModal) {
        XalDeviceCodeAuthModal(
            authState = state.authState,
            onCheckNow = onCheckAuthNow,
            onRetry = onRetryAuth,
            onSignOut = onSignOut,
            onDismiss = onDismissAuthModal
        )
    }

    val signedIn = state.authState as? AuthState.SignedIn
    val isSyncing = state.isRefreshingConsoles || state.isRefreshingGames

    XboxWavesBackground(
        motionMode = state.backgroundMotionMode,
        animationStyle = state.backgroundAnimationStyle,
        colorPreset = state.backgroundColorPreset,
        customHue = state.backgroundCustomHue,
        customIntensity = state.backgroundCustomIntensity
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("setup_onboarding_root"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (signedIn == null) {
                    // Step 1: Welcome & Get Started
                    XboxTile(
                        focused = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_welcome_card")
                    ) {
                        StatusPill(text = "FIRST-TIME SETUP", tone = PillTone.EMERALD)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Welcome to Project X",
                            color = TextPrimary,
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Welcome, thanks for choosing our app. Press the button below to get started and sign in with your Xbox Live account.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onStartSignIn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = ObsidianBase
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("setup_get_started_button")
                        ) {
                            Text("Get Started & Sign In", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onFinishSetup,
                            border = BorderStroke(1.dp, BentoBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("setup_skip_button")
                        ) {
                            Text("Skip for Now (Local LAN Only)", color = TextSecondary)
                        }
                    }
                } else {
                    // Step 2: Synced Ready Screen
                    XboxTile(
                        focused = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_ready_card")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            XboxGamerpicAvatar(
                                avatarUrl = signedIn.avatarUrl,
                                cacheKey = "avatar_${signedIn.xuid.ifBlank { signedIn.gamertag }}",
                                initialText = signedIn.gamertag.firstOrNull()?.uppercaseChar()?.toString() ?: "X",
                                isSignedIn = true,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = signedIn.gamertag,
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                if (signedIn.gamerscore.isNotBlank()) {
                                    Text(
                                        text = "Gamerscore G ${signedIn.gamerscore}",
                                        color = EmeraldPrimary,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (isSyncing) "Syncing your Xbox profile..." else "That's it, you're ready to go, enjoy!",
                            color = TextPrimary,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isSyncing) {
                                "Fetching your saved consoles and recently played games."
                            } else {
                                "Everything's set up. Your consoles and recent games are ready below."
                            },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (isSyncing) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = EmeraldPrimary,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Syncing account data...", color = EmeraldPrimary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // Synced Consoles Preview
                    if (state.consoles.isNotEmpty()) {
                        XboxTile(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Your Consoles (${state.consoles.size})",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.consoles.forEach { console ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ObsidianBase)
                                            .border(1.dp, BentoBorder, RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ConsoleModelThumbnail(
                                            model = console.model,
                                            modifier = Modifier
                                                .width(56.dp)
                                                .height(42.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = console.name,
                                                color = TextPrimary,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${console.model.displayName} • ${console.lanIp.ifBlank { "xHome Auto" }}",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        StatusPill(text = console.powerState.label, tone = PillTone.EMERALD)
                                    }
                                }
                            }
                        }
                    }

                    // Synced Recently Played Games Preview
                    if (state.recentGames.isNotEmpty()) {
                        XboxTile(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Recently Played (${state.recentGames.size})",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(end = 6.dp)
                            ) {
                                items(state.recentGames, key = { it.titleId }) { game ->
                                    Column(
                                        modifier = Modifier
                                            .width(108.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ObsidianBase)
                                            .border(1.dp, BentoBorder, RoundedCornerShape(10.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        XboxCachedArtworkBox(
                                            imageUrl = game.imageUrl,
                                            cacheKey = "title_${game.titleId}",
                                            fallbackIcon = XboxIcons.Gamepad,
                                            maxDimPx = 160,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = game.name,
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onFinishSetup,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = ObsidianBase
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("setup_finish_button")
                    ) {
                        Text("Enter Dashboard", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
