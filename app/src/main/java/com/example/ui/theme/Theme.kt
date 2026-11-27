package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ProjectXColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = ObsidianBase,
    primaryContainer = EmeraldSubtleFill,
    onPrimaryContainer = EmeraldPrimary,
    secondary = EmeraldHover,
    onSecondary = TextPrimary,
    secondaryContainer = BentoCardElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberWarning,
    onTertiary = ObsidianBase,
    tertiaryContainer = AmberSubtleFill,
    onTertiaryContainer = AmberWarning,
    background = ObsidianBase,
    onBackground = TextPrimary,
    surface = BentoCardSurface,
    onSurface = TextPrimary,
    surfaceVariant = SidebarSurface,
    onSurfaceVariant = TextSecondary,
    outline = BentoBorder,
    outlineVariant = BentoBorder,
    error = RedDanger,
    onError = TextPrimary,
    errorContainer = RedSubtleFill,
    onErrorContainer = RedDanger
)

val ProjectXShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun ProjectXTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ProjectXColorScheme,
        typography = ProjectXTypography,
        shapes = ProjectXShapes,
        content = content
    )
}
