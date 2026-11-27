package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Crisp ~750ms animated glowing Xbox Sphere / Neon X launch loader
 * replacing the boxy Android 12+ square splash icon. Zero video overhead.
 */
@Composable
fun XboxIntroSplash(
    onFinished: () -> Unit
) {
    val scale = remember { Animatable(0.76f) }
    val ringExpand = remember { Animatable(0.45f) }
    val glowAlpha = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }
        launch {
            glowAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260, easing = LinearEasing)
            )
        }
        launch {
            ringExpand.animateTo(
                targetValue = 1.65f,
                animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing)
            )
        }
        delay(540L)
        overlayAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 220, easing = LinearEasing)
        )
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(Color(0xFF050907))
            .testTag("xbox_intro_splash"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val baseRadius = minOf(size.width, size.height) * 0.13f * scale.value
            val alpha = glowAlpha.value

            // Ambient emerald radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF22C55E).copy(alpha = 0.28f * alpha),
                        Color(0xFF107C10).copy(alpha = 0.10f * alpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 3.2f
                ),
                radius = baseRadius * 3.2f,
                center = center
            )

            // Expanding subtle pulse ring
            val pulseAlpha = ((1.65f - ringExpand.value) / 1.2f).coerceIn(0f, 1f) * 0.45f * alpha
            drawCircle(
                color = Color(0xFF22C55E).copy(alpha = pulseAlpha),
                radius = baseRadius * ringExpand.value,
                center = center,
                style = Stroke(width = 3.5f)
            )

            // Outer Sphere border & dark obsidian core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF132419), Color(0xFF09120D)),
                    center = Offset(center.x - baseRadius * 0.25f, center.y - baseRadius * 0.25f),
                    radius = baseRadius * 1.4f
                ),
                radius = baseRadius,
                center = center
            )
            drawCircle(
                color = Color(0xFF22C55E).copy(alpha = 0.90f * alpha),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 4.5f)
            )

            // Curved Xbox Sphere 'X' ribbons inside the orb
            val r = baseRadius * 0.66f
            val strokeW = baseRadius * 0.19f
            val leftArc = Path().apply {
                moveTo(center.x - r * 0.62f, center.y - r * 0.68f)
                quadraticTo(
                    center.x + r * 0.06f,
                    center.y - r * 0.08f,
                    center.x + r * 0.68f,
                    center.y + r * 0.64f
                )
            }
            val rightArc = Path().apply {
                moveTo(center.x + r * 0.62f, center.y - r * 0.68f)
                quadraticTo(
                    center.x - r * 0.06f,
                    center.y - r * 0.08f,
                    center.x - r * 0.68f,
                    center.y + r * 0.64f
                )
            }

            drawPath(
                path = leftArc,
                color = Color(0xFF4ADE80).copy(alpha = alpha),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            drawPath(
                path = rightArc,
                color = Color(0xFF22C55E).copy(alpha = alpha),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
    }
}
