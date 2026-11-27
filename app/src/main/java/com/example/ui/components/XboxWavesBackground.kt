package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.model.BackgroundAnimationStyle
import com.example.model.BackgroundColorPreset
import com.example.model.BackgroundMotionMode
import com.example.ui.theme.ObsidianBase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2.0 * PI).toFloat()

// Precomputed mote seeds (xRatio, yRatio, radiusScale, phaseOffset, speedMult) — zero per-frame allocations
private val MOTE_X = floatArrayOf(
    0.08f, 0.14f, 0.22f, 0.29f, 0.36f, 0.43f, 0.51f, 0.58f,
    0.64f, 0.72f, 0.79f, 0.85f, 0.92f, 0.12f, 0.26f, 0.40f,
    0.55f, 0.69f, 0.83f, 0.18f, 0.47f, 0.61f, 0.76f, 0.90f
)
private val MOTE_Y = floatArrayOf(
    0.12f, 0.28f, 0.18f, 0.54f, 0.80f, 0.38f, 0.10f, 0.68f,
    0.34f, 0.84f, 0.16f, 0.48f, 0.62f, 0.72f, 0.44f, 0.22f,
    0.56f, 0.24f, 0.76f, 0.88f, 0.64f, 0.86f, 0.42f, 0.28f
)
private val MOTE_RADIUS = floatArrayOf(
    0.011f, 0.022f, 0.008f, 0.012f, 0.018f, 0.014f, 0.016f, 0.009f,
    0.021f, 0.019f, 0.010f, 0.017f, 0.020f, 0.008f, 0.013f, 0.015f,
    0.011f, 0.012f, 0.016f, 0.009f, 0.014f, 0.011f, 0.010f, 0.008f
)
private val MOTE_PHASE = floatArrayOf(
    0.2f, 1.1f, 2.3f, 0.7f, 3.4f, 1.8f, 4.2f, 2.9f,
    0.5f, 3.8f, 1.4f, 4.9f, 2.1f, 3.1f, 0.9f, 4.5f,
    1.6f, 3.6f, 0.4f, 2.7f, 4.1f, 1.3f, 2.5f, 3.9f
)

/**
 * Series X Dynamic / Static Canvas Backgrounds optimized for Helio G80 (Galaxy A22) & SD 662 (Tab A7):
 * - 4 visual styles matching official Xbox Series X references: Waves, Ripples, Midnight, Motes.
 * - Static mode runs ZERO background animations (0% idle GPU/CPU cost).
 * - Moving mode uses a seamless 22s 2π cycle with pre-allocated Paths & primitive mote arrays.
 */
@Composable
fun XboxWavesBackground(
    motionMode: BackgroundMotionMode = BackgroundMotionMode.STATIC,
    animationStyle: BackgroundAnimationStyle = BackgroundAnimationStyle.WAVES,
    colorPreset: BackgroundColorPreset = BackgroundColorPreset.GREEN,
    customHue: Float = BackgroundColorPreset.GREEN.defaultHue,
    customIntensity: Float = BackgroundColorPreset.GREEN.defaultIntensity,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val phase = if (motionMode == BackgroundMotionMode.MOVING) {
        val transition = rememberInfiniteTransition(label = "series_x_dynamic_bg")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = TWO_PI,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 22_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "bg_phase"
        )
        animated
    } else {
        0.55f // Balanced static hero composition when animation is paused
    }

    val (primaryColor, secondaryColor, baseCanvas) = remember(colorPreset, customHue, customIntensity) {
        resolveSeriesXColors(colorPreset, customHue, customIntensity)
    }

    val path1 = remember { Path() }
    val path2 = remember { Path() }
    val path3 = remember { Path() }
    val highlightPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseCanvas)
            .drawBehind {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawBehind

                when (animationStyle) {
                    BackgroundAnimationStyle.WAVES -> drawSeriesXWaves(
                        w = w,
                        h = h,
                        phase = phase,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        path1 = path1,
                        path2 = path2,
                        path3 = path3,
                        highlightPath = highlightPath
                    )

                    BackgroundAnimationStyle.RIPPLES -> drawSeriesXRipples(
                        w = w,
                        h = h,
                        phase = phase,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )

                    BackgroundAnimationStyle.MIDNIGHT -> drawSeriesXMidnight(
                        w = w,
                        h = h,
                        phase = phase,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        isBlackPreset = colorPreset == BackgroundColorPreset.BLACK
                    )

                    BackgroundAnimationStyle.MOTES -> drawSeriesXMotes(
                        w = w,
                        h = h,
                        phase = phase,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )
                }

                // Bottom contrast scrim so tiles & text remain crisp across bright presets
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x99050907),
                            Color(0xE8050907)
                        ),
                        startY = h * 0.45f,
                        endY = h
                    )
                )
            },
        content = content
    )
}

private fun DrawScope.drawSeriesXWaves(
    w: Float,
    h: Float,
    phase: Float,
    primaryColor: Color,
    secondaryColor: Color,
    path1: Path,
    path2: Path,
    path3: Path,
    highlightPath: Path
) {
    val dx1 = sin(phase) * w * 0.045f
    val dy1 = cos(phase) * h * 0.038f
    val dx2 = sin(phase + 1.15f) * w * 0.035f
    val dy2 = cos(phase + 0.85f) * h * 0.032f

    // Soft atmospheric bloom behind the main upper sweeping S-ribbon
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.22f),
                secondaryColor.copy(alpha = 0.12f),
                Color.Transparent
            ),
            center = Offset(w * 0.62f + dx1, h * 0.34f + dy1),
            radius = maxOf(w, h) * 0.72f
        )
    )

    // Back deep ribbon shadow fold
    path1.reset()
    path1.moveTo(w * 0.02f + dx2, -h * 0.08f)
    path1.cubicTo(
        w * 0.06f + dx1, h * 0.36f + dy1,
        w * 0.32f - dx2, h * 0.46f - dy2,
        w * 0.78f + dx1, h * 0.48f + dy1
    )
    path1.cubicTo(
        w * 1.02f, h * 0.50f + dy2,
        w * 1.06f, h * 0.82f,
        w * 1.02f, h * 1.08f
    )
    path1.lineTo(w * 0.72f + dx2, h * 1.08f)
    path1.cubicTo(
        w * 0.70f + dx1, h * 0.72f + dy1,
        w * 0.48f - dx1, h * 0.64f - dy1,
        w * 0.18f + dx2, h * 0.56f + dy2
    )
    path1.cubicTo(
        -w * 0.04f, h * 0.48f,
        -w * 0.04f, h * 0.12f,
        w * 0.02f + dx2, -h * 0.08f
    )
    path1.close()

    drawPath(
        path = path1,
        brush = Brush.linearGradient(
            colors = listOf(
                secondaryColor.copy(alpha = 0.14f),
                secondaryColor.copy(alpha = 0.34f),
                primaryColor.copy(alpha = 0.16f)
            ),
            start = Offset(w * 0.1f, 0f),
            end = Offset(w * 0.95f, h * 0.9f)
        )
    )

    // Main Series X glossy cascading S-ribbon (matches Waves-dynamic-background)
    path2.reset()
    path2.moveTo(w * 0.14f + dx1, -h * 0.05f)
    path2.cubicTo(
        w * 0.15f + dx2, h * 0.25f + dy1,
        w * 0.36f - dx1, h * 0.34f + dy2,
        w * 0.74f + dx2, h * 0.32f - dy1
    )
    path2.cubicTo(
        w * 0.98f, h * 0.30f - dy2,
        w * 1.03f, h * 0.65f,
        w * 0.95f + dx1, h * 1.05f
    )
    path2.lineTo(w * 0.78f + dx1, h * 1.05f)
    path2.cubicTo(
        w * 0.80f + dx2, h * 0.68f + dy1,
        w * 0.68f - dx2, h * 0.48f - dy1,
        w * 0.38f + dx1, h * 0.49f + dy2
    )
    path2.cubicTo(
        w * 0.16f + dx1, h * 0.49f + dy1,
        w * 0.04f, h * 0.24f,
        w * 0.05f + dx2, -h * 0.05f
    )
    path2.close()

    drawPath(
        path = path2,
        brush = Brush.linearGradient(
            colors = listOf(
                secondaryColor.copy(alpha = 0.26f),
                primaryColor.copy(alpha = 0.44f),
                secondaryColor.copy(alpha = 0.18f)
            ),
            start = Offset(w * 0.1f, h * 0.05f),
            end = Offset(w * 0.92f, h * 0.85f)
        )
    )

    // Lower-left secondary folded ribbon with crisp glowing rim (matches Waves reference #2)
    path3.reset()
    path3.moveTo(-w * 0.05f, h * 0.78f + dy2)
    path3.cubicTo(
        w * 0.22f + dx2, h * 0.72f - dy1,
        w * 0.46f - dx1, h * 0.74f + dy1,
        w * 0.60f + dx1, h * 1.05f
    )
    path3.lineTo(-w * 0.05f, h * 1.05f)
    path3.close()

    drawPath(
        path = path3,
        brush = Brush.linearGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.32f),
                secondaryColor.copy(alpha = 0.16f)
            ),
            start = Offset(0f, h * 0.74f),
            end = Offset(w * 0.55f, h)
        )
    )

    // Specular edge highlight along the lower-left crest
    highlightPath.reset()
    highlightPath.moveTo(-w * 0.05f, h * 0.78f + dy2)
    highlightPath.cubicTo(
        w * 0.22f + dx2, h * 0.72f - dy1,
        w * 0.46f - dx1, h * 0.74f + dy1,
        w * 0.60f + dx1, h * 1.05f
    )
    drawPath(
        path = highlightPath,
        color = primaryColor.copy(alpha = 0.45f),
        style = Stroke(width = 3.2f)
    )
}

private fun DrawScope.drawSeriesXRipples(
    w: Float,
    h: Float,
    phase: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    val origin = Offset(
        x = w * 0.12f + sin(phase) * w * 0.02f,
        y = h * 0.38f + cos(phase) * h * 0.02f
    )
    val maxRadius = maxOf(w, h) * 1.15f

    // Base radial depth matching Ripples-dynamic-background.webp
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                secondaryColor.copy(alpha = 0.32f),
                primaryColor.copy(alpha = 0.18f),
                Color.Transparent
            ),
            center = origin,
            radius = maxRadius
        )
    )

    val ringCount = 6
    val progress = phase / TWO_PI
    for (i in 0 until ringCount) {
        val t = (i.toFloat() / ringCount + progress) % 1.0f
        // Smooth radial expansion easing so rings glide gently from origin outward
        val easedRadius = maxRadius * (0.10f + 0.92f * t)
        val edgeFade = sin(t * PI.toFloat()).coerceIn(0f, 1f)

        // Outer soft crest shadow + bright inner ripple highlight
        drawCircle(
            color = primaryColor.copy(alpha = 0.20f * edgeFade),
            radius = easedRadius,
            center = origin,
            style = Stroke(width = maxOf(w, h) * 0.065f)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.16f * edgeFade),
            radius = easedRadius + maxOf(w, h) * 0.032f,
            center = origin,
            style = Stroke(width = maxOf(w, h) * 0.025f)
        )
    }
}

private fun DrawScope.drawSeriesXMidnight(
    w: Float,
    h: Float,
    phase: Float,
    primaryColor: Color,
    secondaryColor: Color,
    isBlackPreset: Boolean
) {
    val midnightDeepBlue = if (isBlackPreset) Color(0xFF0C1526) else Color(0xFF0A245C)

    // Blob 1: upper glow cloud
    val c1 = Offset(
        x = w * 0.34f + sin(phase) * w * 0.08f,
        y = h * 0.08f + cos(phase * 1.0f) * h * 0.06f
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.36f),
                secondaryColor.copy(alpha = 0.16f),
                Color.Transparent
            ),
            center = c1,
            radius = maxOf(w, h) * 0.56f
        )
    )

    // Blob 2: left warm bloom
    val c2 = Offset(
        x = w * 0.14f + cos(phase + 1.2f) * w * 0.07f,
        y = h * 0.58f + sin(phase + 0.9f) * h * 0.07f
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.30f),
                secondaryColor.copy(alpha = 0.14f),
                Color.Transparent
            ),
            center = c2,
            radius = maxOf(w, h) * 0.58f
        )
    )

    // Blob 3: right deep emerald/accent veil
    val c3 = Offset(
        x = w * 0.88f + sin(phase + 2.3f) * w * 0.06f,
        y = h * 0.52f + cos(phase + 1.8f) * h * 0.08f
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                secondaryColor.copy(alpha = 0.30f),
                Color.Transparent
            ),
            center = c3,
            radius = maxOf(w, h) * 0.52f
        )
    )

    // Blob 4: deep midnight valley column (Signature Midnight-dynamic-background silhouette)
    val c4 = Offset(
        x = w * 0.62f + cos(phase + 2.8f) * w * 0.06f,
        y = h * 0.62f + sin(phase + 2.1f) * h * 0.05f
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                midnightDeepBlue.copy(alpha = 0.55f),
                Color.Transparent
            ),
            center = c4,
            radius = maxOf(w, h) * 0.50f
        )
    )
}

private fun DrawScope.drawSeriesXMotes(
    w: Float,
    h: Float,
    phase: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    // Subtle deep forest mist pockets behind motes (matches Motes-dynamic-background.webp)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                secondaryColor.copy(alpha = 0.24f),
                Color.Transparent
            ),
            center = Offset(w * 0.45f + sin(phase) * w * 0.04f, h * 0.42f + cos(phase) * h * 0.04f),
            radius = maxOf(w, h) * 0.78f
        )
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.14f),
                Color.Transparent
            ),
            center = Offset(w * 0.75f - sin(phase) * w * 0.03f, h * 0.65f),
            radius = maxOf(w, h) * 0.52f
        )
    )

    val minDim = minOf(w, h)
    for (i in MOTE_X.indices) {
        val p = phase + MOTE_PHASE[i]
        val cx = w * MOTE_X[i] + sin(p) * w * 0.028f
        val cy = h * MOTE_Y[i] + cos(p * 1.0f + 0.6f) * h * 0.032f
        val baseR = minDim * MOTE_RADIUS[i]
        val twinkle = 0.62f + 0.38f * sin(p * 2.0f)

        // Soft outer bokeh halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.42f * twinkle),
                    secondaryColor.copy(alpha = 0.14f * twinkle),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = baseR * 3.4f
            ),
            radius = baseR * 3.4f,
            center = Offset(cx, cy)
        )
        // Bright glowing mote center
        drawCircle(
            color = primaryColor.copy(alpha = (0.78f * twinkle).coerceIn(0.25f, 0.95f)),
            radius = baseR * 0.85f,
            center = Offset(cx, cy)
        )
    }
}

internal fun resolveSeriesXColors(
    preset: BackgroundColorPreset,
    customHue: Float,
    customIntensity: Float
): Triple<Color, Color, Color> {
    val intensity = customIntensity.coerceIn(0.15f, 1.0f)
    return when (preset) {
        BackgroundColorPreset.BLACK -> Triple(
            Color(preset.primaryHex).copy(alpha = 0.55f * intensity + 0.15f),
            Color(preset.secondaryHex),
            Color(0xFF000000)
        )
        BackgroundColorPreset.WHITE -> Triple(
            Color(preset.primaryHex).copy(alpha = 0.55f * intensity + 0.18f),
            Color(preset.secondaryHex).copy(alpha = 0.65f * intensity + 0.15f),
            Color(0xFF0B0E11)
        )
        BackgroundColorPreset.CUSTOM -> {
            val h = ((customHue % 360f) + 360f) % 360f
            val primary = Color.hsv(hue = h, saturation = 0.78f, value = 0.45f + 0.52f * intensity)
            val secondary = Color.hsv(hue = h, saturation = 0.85f, value = 0.28f + 0.35f * intensity)
            Triple(primary, secondary, ObsidianBase)
        }
        else -> {
            val basePrimary = Color(preset.primaryHex)
            val baseSecondary = Color(preset.secondaryHex)
            val scale = 0.45f + 0.55f * intensity
            Triple(
                basePrimary.copy(alpha = scale.coerceIn(0.2f, 1f)),
                baseSecondary.copy(alpha = scale.coerceIn(0.2f, 1f)),
                ObsidianBase
            )
        }
    }
}
