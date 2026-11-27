package com.example.ui.stream

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import com.example.model.GamepadState
import kotlin.math.abs
import kotlin.math.hypot

private enum class PadZone {
    LEFT_STICK,
    RIGHT_STICK,
    DPAD,
    BTN_A,
    BTN_B,
    BTN_X,
    BTN_Y,
    LT,
    LB,
    RT,
    RB,
    VIEW,
    NEXUS,
    MENU
}

/**
 * Lightweight single-Canvas multi-touch Xbox virtual controller overlay.
 * Uses per-pointer zone locking so analog sticks & triggers never drop out
 * if thumbs slide outside the outer ring during fast gameplay (e.g. Forza steering).
 */
@Composable
fun VirtualControllerOverlay(
    onStateChanged: (GamepadState) -> Unit,
    opacity: Float = 0.80f,
    scale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val cleanOpacity = opacity.coerceIn(0.25f, 1.0f)
    val cleanScale = scale.coerceIn(0.70f, 1.35f)

    var activeState by remember { mutableStateOf(GamepadState()) }
    val pointerLocks = remember { mutableMapOf<PointerId, PadZone>() }
    val latestOnStateChanged by rememberUpdatedState(onStateChanged)

    DisposableEffect(Unit) {
        onDispose {
            pointerLocks.clear()
            activeState = GamepadState()
            latestOnStateChanged(GamepadState())
        }
    }

    val labelPaint = remember(cleanOpacity) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((230 * cleanOpacity).toInt().coerceIn(40, 255), 232, 238, 234)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(cleanScale) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val geometry = VirtualPadGeometry(w, h, cleanScale)

                        if (event.type == PointerEventType.Exit) {
                            continue
                        }

                        val pressedPositions = mutableMapOf<PointerId, Offset>()
                        event.changes.forEach { change ->
                            val stillPressed = change.pressed && !change.changedToUpIgnoreConsumed()
                            if (stillPressed) {
                                pressedPositions[change.id] = change.position
                                change.consume()
                            } else {
                                pointerLocks.remove(change.id)
                            }
                        }

                        // Prune any stale pointer IDs that are no longer pressed
                        val staleIds = pointerLocks.keys.filter { it !in pressedPositions }
                        staleIds.forEach { pointerLocks.remove(it) }

                        // Lock newly pressed pointers to whichever zone they touched first
                        for ((id, pos) in pressedPositions) {
                            if (!pointerLocks.containsKey(id)) {
                                detectHitZone(pos, geometry)?.let { zone ->
                                    pointerLocks[id] = zone
                                }
                            }
                        }

                        val newState = computeLockedGamepadState(
                            positions = pressedPositions,
                            locks = pointerLocks,
                            geometry = geometry
                        )
                        if (newState != activeState) {
                            activeState = newState
                            latestOnStateChanged(newState)
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val unit = minOf(w, h) * cleanScale
        labelPaint.textSize = unit * 0.036f

        val layout = VirtualPadGeometry(w, h, cleanScale)

        // Shoulder & Trigger Pills (Top-Left LB/LT, Top-Right RB/RT)
        drawShoulderPill(
            center = layout.ltCenter,
            width = layout.shoulderWidth,
            height = layout.shoulderHeight,
            label = "LT",
            pressed = activeState.leftTrigger > 0.5f,
            opacity = cleanOpacity,
            paint = labelPaint
        )
        drawShoulderPill(
            center = layout.lbCenter,
            width = layout.shoulderWidth,
            height = layout.shoulderHeight,
            label = "LB",
            pressed = (activeState.buttonsMask and GamepadState.BTN_LB) != 0,
            opacity = cleanOpacity,
            paint = labelPaint
        )
        drawShoulderPill(
            center = layout.rtCenter,
            width = layout.shoulderWidth,
            height = layout.shoulderHeight,
            label = "RT",
            pressed = activeState.rightTrigger > 0.5f,
            opacity = cleanOpacity,
            paint = labelPaint
        )
        drawShoulderPill(
            center = layout.rbCenter,
            width = layout.shoulderWidth,
            height = layout.shoulderHeight,
            label = "RB",
            pressed = (activeState.buttonsMask and GamepadState.BTN_RB) != 0,
            opacity = cleanOpacity,
            paint = labelPaint
        )

        // Left Analog Stick
        drawThumbstick(
            center = layout.leftStickCenter,
            outerRadius = layout.stickRadius,
            normX = activeState.leftStickX,
            normY = activeState.leftStickY,
            opacity = cleanOpacity
        )

        // D-Pad Cross
        drawDpad(
            center = layout.dpadCenter,
            armRadius = layout.dpadRadius,
            mask = activeState.buttonsMask,
            opacity = cleanOpacity
        )

        // Right Analog Stick
        drawThumbstick(
            center = layout.rightStickCenter,
            outerRadius = layout.stickRadius,
            normX = activeState.rightStickX,
            normY = activeState.rightStickY,
            opacity = cleanOpacity
        )

        // ABXY Face Buttons
        drawFaceButton(
            center = layout.btnA,
            radius = layout.faceRadius,
            label = "A",
            accent = Color(0xFF22C55E),
            pressed = (activeState.buttonsMask and GamepadState.BTN_A) != 0,
            opacity = cleanOpacity,
            paint = labelPaint
        )
        drawFaceButton(
            center = layout.btnB,
            radius = layout.faceRadius,
            label = "B",
            accent = Color(0xFFEF4444),
            pressed = (activeState.buttonsMask and GamepadState.BTN_B) != 0,
            opacity = cleanOpacity,
            paint = labelPaint
        )
        drawFaceButton(
            center = layout.btnX,
            radius = layout.faceRadius,
            label = "X",
            accent = Color(0xFF38BDF8),
            pressed = (activeState.buttonsMask and GamepadState.BTN_X) != 0,
            opacity = cleanOpacity,
            paint = labelPaint
        )
        drawFaceButton(
            center = layout.btnY,
            radius = layout.faceRadius,
            label = "Y",
            accent = Color(0xFFF59E0B),
            pressed = (activeState.buttonsMask and GamepadState.BTN_Y) != 0,
            opacity = cleanOpacity,
            paint = labelPaint
        )

        // Center View / Nexus / Menu
        drawSmallPillButton(
            center = layout.btnView,
            radius = layout.smallRadius,
            label = "≡",
            pressed = (activeState.buttonsMask and GamepadState.BTN_VIEW) != 0,
            accent = Color(0xFF8A9990),
            opacity = cleanOpacity,
            paint = labelPaint
        )
        drawSmallPillButton(
            center = layout.btnNexus,
            radius = layout.smallRadius * 1.15f,
            label = "X",
            pressed = (activeState.buttonsMask and GamepadState.BTN_NEXUS) != 0,
            accent = Color(0xFF22C55E),
            opacity = cleanOpacity,
            paint = labelPaint
        )
        drawSmallPillButton(
            center = layout.btnMenu,
            radius = layout.smallRadius,
            label = "☰",
            pressed = (activeState.buttonsMask and GamepadState.BTN_MENU) != 0,
            accent = Color(0xFF8A9990),
            opacity = cleanOpacity,
            paint = labelPaint
        )
    }
}

private class VirtualPadGeometry(w: Float, h: Float, scale: Float = 1.0f) {
    private val unit = minOf(w, h) * scale
    val stickRadius = unit * 0.145f
    val dpadRadius = unit * 0.125f
    val faceRadius = unit * 0.058f
    val smallRadius = unit * 0.042f
    val shoulderWidth = unit * 0.165f
    val shoulderHeight = unit * 0.088f

    val ltCenter = Offset(w * 0.11f, h * 0.16f)
    val lbCenter = Offset(w * 0.11f, h * 0.29f)
    val rtCenter = Offset(w * 0.89f, h * 0.16f)
    val rbCenter = Offset(w * 0.89f, h * 0.29f)

    val leftStickCenter = Offset(w * 0.14f, h * 0.62f)
    val dpadCenter = Offset(w * 0.29f, h * 0.80f)
    val rightStickCenter = Offset(w * 0.71f, h * 0.80f)

    private val abxyCenter = Offset(w * 0.86f, h * 0.62f)
    private val abxySpread = faceRadius * 1.95f
    val btnA = Offset(abxyCenter.x, abxyCenter.y + abxySpread)
    val btnB = Offset(abxyCenter.x + abxySpread, abxyCenter.y)
    val btnX = Offset(abxyCenter.x - abxySpread, abxyCenter.y)
    val btnY = Offset(abxyCenter.x, abxyCenter.y - abxySpread)

    val btnView = Offset(w * 0.43f, h * 0.88f)
    val btnNexus = Offset(w * 0.50f, h * 0.88f)
    val btnMenu = Offset(w * 0.57f, h * 0.88f)
}

private fun detectHitZone(pos: Offset, g: VirtualPadGeometry): PadZone? {
    val stickHit = g.stickRadius * 1.55f
    if (hypot(pos.x - g.leftStickCenter.x, pos.y - g.leftStickCenter.y) <= stickHit) {
        return PadZone.LEFT_STICK
    }
    if (hypot(pos.x - g.rightStickCenter.x, pos.y - g.rightStickCenter.y) <= stickHit) {
        return PadZone.RIGHT_STICK
    }

    val faceHit = g.faceRadius * 1.45f
    if (hypot(pos.x - g.btnA.x, pos.y - g.btnA.y) <= faceHit) return PadZone.BTN_A
    if (hypot(pos.x - g.btnB.x, pos.y - g.btnB.y) <= faceHit) return PadZone.BTN_B
    if (hypot(pos.x - g.btnX.x, pos.y - g.btnX.y) <= faceHit) return PadZone.BTN_X
    if (hypot(pos.x - g.btnY.x, pos.y - g.btnY.y) <= faceHit) return PadZone.BTN_Y

    if (hypot(pos.x - g.dpadCenter.x, pos.y - g.dpadCenter.y) <= g.dpadRadius * 1.35f) {
        return PadZone.DPAD
    }

    if (isInsideRect(pos, g.ltCenter, g.shoulderWidth * 1.3f, g.shoulderHeight * 1.35f)) return PadZone.LT
    if (isInsideRect(pos, g.lbCenter, g.shoulderWidth * 1.3f, g.shoulderHeight * 1.35f)) return PadZone.LB
    if (isInsideRect(pos, g.rtCenter, g.shoulderWidth * 1.3f, g.shoulderHeight * 1.35f)) return PadZone.RT
    if (isInsideRect(pos, g.rbCenter, g.shoulderWidth * 1.3f, g.shoulderHeight * 1.35f)) return PadZone.RB

    val smallHit = g.smallRadius * 1.65f
    if (hypot(pos.x - g.btnView.x, pos.y - g.btnView.y) <= smallHit) return PadZone.VIEW
    if (hypot(pos.x - g.btnNexus.x, pos.y - g.btnNexus.y) <= smallHit) return PadZone.NEXUS
    if (hypot(pos.x - g.btnMenu.x, pos.y - g.btnMenu.y) <= smallHit) return PadZone.MENU

    return null
}

private fun computeLockedGamepadState(
    positions: Map<PointerId, Offset>,
    locks: Map<PointerId, PadZone>,
    geometry: VirtualPadGeometry
): GamepadState {
    var lx = 0f
    var ly = 0f
    var rx = 0f
    var ry = 0f
    var lt = 0f
    var rt = 0f
    var mask = 0

    for ((id, pos) in positions) {
        when (locks[id]) {
            PadZone.LEFT_STICK -> {
                val dx = pos.x - geometry.leftStickCenter.x
                val dy = pos.y - geometry.leftStickCenter.y
                val dist = hypot(dx, dy)
                if (dist > geometry.stickRadius && dist > 0.001f) {
                    lx = (dx / dist).coerceIn(-1f, 1f)
                    ly = (dy / dist).coerceIn(-1f, 1f)
                } else {
                    lx = (dx / geometry.stickRadius).coerceIn(-1f, 1f)
                    ly = (dy / geometry.stickRadius).coerceIn(-1f, 1f)
                }
            }
            PadZone.RIGHT_STICK -> {
                val dx = pos.x - geometry.rightStickCenter.x
                val dy = pos.y - geometry.rightStickCenter.y
                val dist = hypot(dx, dy)
                if (dist > geometry.stickRadius && dist > 0.001f) {
                    rx = (dx / dist).coerceIn(-1f, 1f)
                    ry = (dy / dist).coerceIn(-1f, 1f)
                } else {
                    rx = (dx / geometry.stickRadius).coerceIn(-1f, 1f)
                    ry = (dy / geometry.stickRadius).coerceIn(-1f, 1f)
                }
            }
            PadZone.DPAD -> {
                val ddx = pos.x - geometry.dpadCenter.x
                val ddy = pos.y - geometry.dpadCenter.y
                val dist = hypot(ddx, ddy)
                if (dist > geometry.dpadRadius * 0.14f) {
                    if (abs(ddx) > abs(ddy) * 0.42f) {
                        mask = mask or if (ddx < 0f) GamepadState.BTN_DPAD_LEFT else GamepadState.BTN_DPAD_RIGHT
                    }
                    if (abs(ddy) > abs(ddx) * 0.42f) {
                        mask = mask or if (ddy < 0f) GamepadState.BTN_DPAD_UP else GamepadState.BTN_DPAD_DOWN
                    }
                }
            }
            PadZone.BTN_A -> mask = mask or GamepadState.BTN_A
            PadZone.BTN_B -> mask = mask or GamepadState.BTN_B
            PadZone.BTN_X -> mask = mask or GamepadState.BTN_X
            PadZone.BTN_Y -> mask = mask or GamepadState.BTN_Y
            PadZone.LT -> lt = 1f
            PadZone.LB -> mask = mask or GamepadState.BTN_LB
            PadZone.RT -> rt = 1f
            PadZone.RB -> mask = mask or GamepadState.BTN_RB
            PadZone.VIEW -> mask = mask or GamepadState.BTN_VIEW
            PadZone.NEXUS -> mask = mask or GamepadState.BTN_NEXUS
            PadZone.MENU -> mask = mask or GamepadState.BTN_MENU
            null -> {}
        }
    }

    return GamepadState(
        leftStickX = lx,
        leftStickY = ly,
        rightStickX = rx,
        rightStickY = ry,
        leftTrigger = lt,
        rightTrigger = rt,
        buttonsMask = mask,
        timestampMs = System.currentTimeMillis()
    )
}

private fun isInsideRect(p: Offset, center: Offset, w: Float, h: Float): Boolean {
    return p.x in (center.x - w / 2f)..(center.x + w / 2f) &&
        p.y in (center.y - h / 2f)..(center.y + h / 2f)
}

private fun DrawScope.drawThumbstick(
    center: Offset,
    outerRadius: Float,
    normX: Float,
    normY: Float,
    opacity: Float
) {
    drawCircle(
        color = Color(0xFF121815).copy(alpha = 0.28f * opacity),
        radius = outerRadius,
        center = center
    )
    drawCircle(
        color = Color(0xFF22C55E).copy(alpha = 0.55f * opacity),
        radius = outerRadius,
        center = center,
        style = Stroke(width = 2.5f)
    )
    val knobCenter = Offset(
        center.x + normX * outerRadius * 0.55f,
        center.y + normY * outerRadius * 0.55f
    )
    drawCircle(
        color = Color(0xFF22C55E).copy(alpha = 0.65f * opacity),
        radius = outerRadius * 0.42f,
        center = knobCenter
    )
}

private fun DrawScope.drawDpad(
    center: Offset,
    armRadius: Float,
    mask: Int,
    opacity: Float
) {
    val barThickness = armRadius * 0.62f
    drawRoundRect(
        color = Color(0xFF121815).copy(alpha = 0.30f * opacity),
        topLeft = Offset(center.x - armRadius, center.y - barThickness / 2f),
        size = Size(armRadius * 2f, barThickness),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawRoundRect(
        color = Color(0xFF121815).copy(alpha = 0.30f * opacity),
        topLeft = Offset(center.x - barThickness / 2f, center.y - armRadius),
        size = Size(barThickness, armRadius * 2f),
        cornerRadius = CornerRadius(12f, 12f)
    )
    val activeColor = Color(0xFF22C55E).copy(alpha = 0.85f * opacity)
    if ((mask and GamepadState.BTN_DPAD_UP) != 0) {
        drawCircle(activeColor, barThickness * 0.35f, Offset(center.x, center.y - armRadius * 0.65f))
    }
    if ((mask and GamepadState.BTN_DPAD_DOWN) != 0) {
        drawCircle(activeColor, barThickness * 0.35f, Offset(center.x, center.y + armRadius * 0.65f))
    }
    if ((mask and GamepadState.BTN_DPAD_LEFT) != 0) {
        drawCircle(activeColor, barThickness * 0.35f, Offset(center.x - armRadius * 0.65f, center.y))
    }
    if ((mask and GamepadState.BTN_DPAD_RIGHT) != 0) {
        drawCircle(activeColor, barThickness * 0.35f, Offset(center.x + armRadius * 0.65f, center.y))
    }
}

private fun DrawScope.drawFaceButton(
    center: Offset,
    radius: Float,
    label: String,
    accent: Color,
    pressed: Boolean,
    opacity: Float,
    paint: Paint
) {
    drawCircle(
        color = if (pressed) accent.copy(alpha = 0.78f * opacity) else Color(0xFF121815).copy(alpha = 0.38f * opacity),
        radius = radius,
        center = center
    )
    drawCircle(
        color = accent.copy(alpha = 0.85f * opacity),
        radius = radius,
        center = center,
        style = Stroke(width = 3f)
    )
    drawContext.canvas.nativeCanvas.drawText(
        label,
        center.x,
        center.y + paint.textSize * 0.35f,
        paint
    )
}

private fun DrawScope.drawShoulderPill(
    center: Offset,
    width: Float,
    height: Float,
    label: String,
    pressed: Boolean,
    opacity: Float,
    paint: Paint
) {
    val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)
    val cr = CornerRadius(height / 2f, height / 2f)
    drawRoundRect(
        color = if (pressed) Color(0xFF22C55E).copy(alpha = 0.68f * opacity) else Color(0xFF121815).copy(alpha = 0.38f * opacity),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = cr
    )
    drawRoundRect(
        color = Color(0xFF22C55E).copy(alpha = 0.60f * opacity),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = cr,
        style = Stroke(width = 2f)
    )
    drawContext.canvas.nativeCanvas.drawText(
        label,
        center.x,
        center.y + paint.textSize * 0.35f,
        paint
    )
}

private fun DrawScope.drawSmallPillButton(
    center: Offset,
    radius: Float,
    label: String,
    pressed: Boolean,
    accent: Color,
    opacity: Float,
    paint: Paint
) {
    drawCircle(
        color = if (pressed) accent.copy(alpha = 0.75f * opacity) else Color(0xFF121815).copy(alpha = 0.38f * opacity),
        radius = radius,
        center = center
    )
    drawCircle(
        color = accent.copy(alpha = 0.80f * opacity),
        radius = radius,
        center = center,
        style = Stroke(width = 2f)
    )
    drawContext.canvas.nativeCanvas.drawText(
        label,
        center.x,
        center.y + paint.textSize * 0.35f,
        paint
    )
}
