package com.example

import com.example.input.GamepadInputEngine
import com.example.input.PhysicalGamepadHandler
import com.example.model.GamepadState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GamepadInputTest {

    @Test
    fun `applyDeadzone zeroes inner stick noise and rescales outer range`() {
        assertEquals(0f, PhysicalGamepadHandler.applyDeadzone(0.10f, 0.12f), 0.0001f)
        assertEquals(0f, PhysicalGamepadHandler.applyDeadzone(-0.12f, 0.12f), 0.0001f)
        assertEquals(1f, PhysicalGamepadHandler.applyDeadzone(1f, 0.12f), 0.0001f)
        assertEquals(-1f, PhysicalGamepadHandler.applyDeadzone(-1f, 0.12f), 0.0001f)
    }

    @Test
    fun `encodeBinaryFrame produces 38-byte little endian Xbox Cloud gamepad packet`() {
        val state = GamepadState(
            leftStickX = 1.0f,
            leftStickY = 0f,
            rightStickX = -1.0f,
            rightStickY = 0f,
            leftTrigger = 1.0f,
            rightTrigger = 0f,
            buttonsMask = GamepadState.BTN_A or GamepadState.BTN_RB,
            timestampMs = 123456789L
        )
        val bytes = GamepadInputEngine.encodeBinaryFrame(state, sequence = 42)
        assertEquals(GamepadInputEngine.BINARY_FRAME_SIZE, bytes.size)

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(GamepadInputEngine.REPORT_TYPE_GAMEPAD, buf.short) // 0x0002
        assertEquals(42, buf.int) // sequence
        assertEquals(123456789.0, buf.double, 0.001) // timestampMs
        assertEquals(1.toByte(), buf.get()) // gamepadCount = 1
        assertEquals(0.toByte(), buf.get()) // gamepadIndex = 0
        val expectedXboxMask = ((1 shl 4) or (1 shl 13)).toShort()
        assertEquals(expectedXboxMask, buf.short) // Xbox Cloud button bitmask
        assertEquals(32767.toShort(), buf.short) // LX
        assertEquals(0.toShort(), buf.short) // LY
        assertEquals((-32767).toShort(), buf.short) // RX
        assertEquals(0.toShort(), buf.short) // RY
        assertEquals(65535.toShort(), buf.short) // LT
        assertEquals(0.toShort(), buf.short) // RT
        assertEquals(GamepadInputEngine.PHYSICALITY_ALL_STANDARD, buf.int) // physicalPhysicality
        assertEquals(0, buf.int) // virtualPhysicality
    }
}
