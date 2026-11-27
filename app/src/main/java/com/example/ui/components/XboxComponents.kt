package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AuthState
import com.example.model.ConnectionMode
import com.example.model.ConsoleModel
import com.example.model.DashboardTab
import com.example.network.rememberCachedXboxImage
import com.example.ui.theme.AmberSubtleFill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSubtleFill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBase
import com.example.ui.theme.RedDanger
import com.example.ui.theme.RedSubtleFill
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.XboxGlassSurface
import com.example.ui.theme.XboxGreen
import com.example.ui.theme.XboxTileSurface
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PillTone {
    EMERALD,
    AMBER,
    RED,
    NEUTRAL
}

@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (tone) {
        PillTone.EMERALD -> EmeraldSubtleFill to EmeraldPrimary
        PillTone.AMBER -> AmberSubtleFill to AmberWarning
        PillTone.RED -> RedSubtleFill to RedDanger
        PillTone.NEUTRAL -> Color(0xFF1B2520) to TextSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
fun XboxTile(
    focused: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val borderWidth = if (focused) 2.dp else 1.dp
    val borderColor = if (focused) EmeraldPrimary else BentoBorder
    val baseModifier = modifier
        .clip(shape)
        .background(XboxTileSurface)
        .border(borderWidth, borderColor, shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(14.dp)

    Column(modifier = baseModifier, content = content)
}

@Composable
fun ConsoleModelThumbnail(
    model: ConsoleModel,
    modifier: Modifier = Modifier
) {
    val resId = when (model) {
        ConsoleModel.SERIES_X -> R.drawable.img_console_series_x
        ConsoleModel.SERIES_S -> R.drawable.img_console_series_s
        ConsoleModel.ONE_X -> R.drawable.img_console_one_x
        ConsoleModel.ONE_S -> R.drawable.img_console_one_s
        ConsoleModel.XBOX_ONE -> R.drawable.img_console_one
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF080D0A))
            .border(1.dp, BentoBorder, RoundedCornerShape(10.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = model.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun XboxCachedArtworkBox(
    imageUrl: String,
    cacheKey: String,
    fallbackIcon: ImageVector,
    maxDimPx: Int = 192,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberCachedXboxImage(url = imageUrl, cacheKey = cacheKey, maxDimPx = maxDimPx)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(EmeraldSubtleFill)
            .border(1.dp, EmeraldPrimary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun XboxGamerpicAvatar(
    avatarUrl: String,
    cacheKey: String,
    initialText: String,
    isSignedIn: Boolean,
    modifier: Modifier = Modifier
) {
    val avatarBitmap = rememberCachedXboxImage(url = avatarUrl, cacheKey = cacheKey, maxDimPx = 160)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(XboxGlassSurface)
            .border(
                width = 2.dp,
                color = if (isSignedIn) EmeraldPrimary else XboxGreen,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap,
                contentDescription = "Xbox Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initialText,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

/**
 * Authentic Xbox console top bar:
 * - Left: Gamer profile ring (real Gamerpic), Gamertag, and Gamerscore/XBL pill
 * - Center (wide) or below (compact): 6 working circular Xbox console tabs
 * - Right: LAN status & live clock
 */
@Composable
fun XboxTopBar(
    isCompact: Boolean,
    selectedTab: DashboardTab,
    authState: AuthState,
    activeLanIp: String,
    connectionMode: ConnectionMode,
    onSelectTab: (DashboardTab) -> Unit,
    onOpenRegisterConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    var clockText by remember { mutableStateOf(formatNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockText = formatNow()
            delay(30_000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = if (isCompact) 12.dp else 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Profile Pill
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onSelectTab(DashboardTab.ACCOUNT) }
                    .padding(end = 8.dp)
                    .testTag("profile_pill_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val signedIn = authState as? AuthState.SignedIn
                val initial = signedIn?.gamertag?.firstOrNull()?.uppercaseChar()?.toString() ?: "X"
                XboxGamerpicAvatar(
                    avatarUrl = signedIn?.avatarUrl.orEmpty(),
                    cacheKey = if (signedIn != null) "avatar_${signedIn.xuid.ifBlank { signedIn.gamertag }}" else "",
                    initialText = initial,
                    isSignedIn = signedIn != null,
                    modifier = Modifier.size(38.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = signedIn?.gamertag ?: "Guest",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (signedIn != null) TextPrimary else XboxTileSurface)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (signedIn != null) "XBL" else "SIGN IN",
                                color = if (signedIn != null) ObsidianBase else EmeraldPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    val subline = when {
                        signedIn != null && signedIn.gamerscore.isNotBlank() ->
                            "G ${signedIn.gamerscore}"
                        activeLanIp.isNotBlank() ->
                            "LAN $activeLanIp"
                        else -> "xHome Local"
                    }
                    Text(
                        text = subline,
                        color = TextSecondary,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            // Center Xbox Icon Bar on Tablets (>= 600dp)
            if (!isCompact) {
                XboxNavIconRow(
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab,
                    onOpenRegisterConsole = onOpenRegisterConsole
                )
            }

            // Right: Status & Time Cluster
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (connectionMode == ConnectionMode.SIGNALING) {
                    StatusPill(text = "CONNECTING", tone = PillTone.AMBER)
                } else {
                    Icon(
                        imageVector = XboxIcons.Wifi,
                        contentDescription = "Local Wi-Fi",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = clockText,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                )
            }
        }

        // Compact phone icon bar right underneath the profile header (all 48dp touch targets)
        if (isCompact) {
            XboxNavIconRow(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                onOpenRegisterConsole = onOpenRegisterConsole,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun XboxNavIconRow(
    selectedTab: DashboardTab,
    onSelectTab: (DashboardTab) -> Unit,
    onOpenRegisterConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        XboxNavIconButton(
            icon = Icons.Filled.Home,
            label = DashboardTab.HOME.label,
            selected = selectedTab == DashboardTab.HOME,
            onClick = { onSelectTab(DashboardTab.HOME) },
            tag = "tab_home"
        )
        XboxNavIconButton(
            icon = XboxIcons.Console,
            label = DashboardTab.CONSOLES.label,
            selected = selectedTab == DashboardTab.CONSOLES,
            onClick = { onSelectTab(DashboardTab.CONSOLES) },
            tag = "tab_consoles"
        )
        XboxNavIconButton(
            icon = XboxIcons.Gamepad,
            label = DashboardTab.GAMES.label,
            selected = selectedTab == DashboardTab.GAMES,
            onClick = { onSelectTab(DashboardTab.GAMES) },
            tag = "tab_games"
        )
        XboxNavIconButton(
            icon = Icons.Filled.Add,
            label = "Register",
            selected = false,
            onClick = onOpenRegisterConsole,
            tag = "tab_register"
        )
        XboxNavIconButton(
            icon = Icons.Filled.Settings,
            label = DashboardTab.SETTINGS.label,
            selected = selectedTab == DashboardTab.SETTINGS,
            onClick = { onSelectTab(DashboardTab.SETTINGS) },
            tag = "tab_settings"
        )
        XboxNavIconButton(
            icon = Icons.Filled.Person,
            label = DashboardTab.ACCOUNT.label,
            selected = selectedTab == DashboardTab.ACCOUNT,
            onClick = { onSelectTab(DashboardTab.ACCOUNT) },
            tag = "tab_account"
        )
    }
}

@Composable
private fun XboxNavIconButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (selected) XboxGreen else XboxGlassSurface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) EmeraldPrimary else BentoBorder,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) TextPrimary else TextSecondary,
            modifier = Modifier.size(21.dp)
        )
    }
}

private fun formatNow(): String {
    return SimpleDateFormat("hh:mm a", Locale.US).format(Date()).lowercase(Locale.US)
}
