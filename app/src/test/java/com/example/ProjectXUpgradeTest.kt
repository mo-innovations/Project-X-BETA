package com.example

import com.example.input.GamepadInputEngine
import com.example.model.GamepadState
import com.example.update.AppUpdater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ProjectXUpgradeTest {

    @Test
    fun `app updater compares semantic and run-number versions accurately`() {
        assertTrue(AppUpdater.isNewerVersion("v1.0.2", "1.0.1"))
        assertTrue(AppUpdater.isNewerVersion("v1.0.10", "1.0.2"))
        assertTrue(AppUpdater.isNewerVersion("1.1.0", "1.0.99"))
        assertFalse(AppUpdater.isNewerVersion("v1.0.1", "1.0.1"))
        assertFalse(AppUpdater.isNewerVersion("v1.0.1", "1.0.2"))
    }

    @Test
    fun `encodeBinaryFrameInto reuses single direct ByteBuffer identically`() {
        val directBuf = ByteBuffer.allocateDirect(GamepadInputEngine.BINARY_FRAME_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
        val state1 = GamepadState(
            leftStickX = 0.75f,
            leftStickY = -0.5f,
            rightTrigger = 0.9f,
            buttonsMask = GamepadState.BTN_A or GamepadState.BTN_DPAD_UP or GamepadState.BTN_DPAD_RIGHT,
            timestampMs = 5000L
        )
        val expected1 = GamepadInputEngine.encodeBinaryFrame(state1, sequence = 11, timestampMs = 5000.0)
        GamepadInputEngine.encodeBinaryFrameInto(directBuf, state1, sequence = 11, timestampMs = 5000.0)

        val actual1 = ByteArray(directBuf.remaining())
        directBuf.get(actual1)
        assertArrayEquals(expected1, actual1)

        // Reuse same direct buffer for second frame (zeroed neutral state)
        val state2 = GamepadState(timestampMs = 5016L)
        val expected2 = GamepadInputEngine.encodeBinaryFrame(state2, sequence = 12, timestampMs = 5016.0)
        GamepadInputEngine.encodeBinaryFrameInto(directBuf, state2, sequence = 12, timestampMs = 5016.0)

        val actual2 = ByteArray(directBuf.remaining())
        directBuf.get(actual2)
        assertArrayEquals(expected2, actual2)
    }
}
