package com.example.input

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.GamepadState
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Provides dual-rumble vibration feedback and Xbox Cloud Gaming 38-byte Little-Endian
 * binary gamepad framing utilities (used for protocol tests and W3C Gamepad JS bridging).
 */
class GamepadInputEngine(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun triggerRumble(intensity: Float = 0.45f, durationMs: Long = 35L) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val amplitude = (intensity.coerceIn(0.05f, 1f) * 255).toInt()
        v.vibrate(VibrationEffect.createOneShot(durationMs.coerceIn(10L, 800L), amplitude))
    }

    fun triggerDualRumble(durationMs: Long, strongMagnitude: Float, weakMagnitude: Float) {
        val peak = maxOf(strongMagnitude, weakMagnitude)
        if (peak <= 0.02f) return
        triggerRumble(intensity = peak, durationMs = durationMs)
    }

    data class VibrationCommand(
        val gamepadIndex: Int,
        val strongMotor: Float,
        val weakMotor: Float,
        val durationMs: Long
    )

    companion object {
        const val REPORT_HEADER_SIZE = 14
        const val CLIENT_METADATA_FRAME_SIZE = 15
        const val BINARY_FRAME_SIZE = 38
        const val PHYSICALITY_ALL_STANDARD = 0x000FFFFF

        const val REPORT_TYPE_METADATA: Short = 0x0001
        const val REPORT_TYPE_GAMEPAD: Short = 0x0002
        const val REPORT_TYPE_CLIENT_METADATA: Short = 0x0008
        const val REPORT_TYPE_SERVER_METADATA: Short = 0x0010
        const val REPORT_TYPE_VIBRATION: Short = 0x0080

        fun encodeClientMetadataFrame(
            sequence: Int,
            timestampMs: Double = System.currentTimeMillis().toDouble(),
            maxTouchPoints: Byte = 0
        ): ByteArray {
            val buf = ByteBuffer.allocate(CLIENT_METADATA_FRAME_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(REPORT_TYPE_CLIENT_METADATA)
            buf.putInt(sequence)
            buf.putDouble(timestampMs)
            buf.put(maxTouchPoints)
            return buf.array()
        }

        /**
         * Authentic Xbox Game Streaming WebRTC Gamepad Report (38 bytes, Little Endian):
         * [0..1]   Report type (UInt16: 0x0002)
         * [2..5]   Sequence number (UInt32)
         * [6..13]  Timestamp ms (Float64 / Double)
         * [14]     Gamepad count (UInt8 = 1)
         * [15]     Gamepad index (UInt8 = 0)
         * [16..17] Xbox Cloud Button Bitmask (UInt16)
         * [18..19] Left Stick X (Int16 -32767..32767)
         * [20..21] Left Stick Y (Int16 -32767..32767)
         * [22..23] Right Stick X (Int16 -32767..32767)
         * [24..25] Right Stick Y (Int16 -32767..32767)
         * [26..27] Left Trigger (UInt16 0..65535)
         * [28..29] Right Trigger (UInt16 0..65535)
         * [30..33] Physical physicality (UInt32)
         * [34..37] Virtual physicality (UInt32)
         */
        fun encodeBinaryFrame(
            state: GamepadState,
            sequence: Int,
            timestampMs: Double = if (state.timestampMs > 0L) state.timestampMs.toDouble() else System.currentTimeMillis().toDouble(),
            reportPhysical: Boolean = true
        ): ByteArray {
            val buf = ByteBuffer.allocate(BINARY_FRAME_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(REPORT_TYPE_GAMEPAD)
            buf.putInt(sequence)
            buf.putDouble(timestampMs)
            buf.put(1.toByte()) // gamepadCount = 1
            buf.put(0.toByte()) // gamepadIndex = 0
            buf.putShort(mapToXboxCloudButtonMask(state.buttonsMask))
            buf.putShort(floatAxisToShort(state.leftStickX))
            buf.putShort(floatAxisToShort(-state.leftStickY))
            buf.putShort(floatAxisToShort(state.rightStickX))
            buf.putShort(floatAxisToShort(-state.rightStickY))
            buf.putShort(floatTriggerToUShort(state.leftTrigger))
            buf.putShort(floatTriggerToUShort(state.rightTrigger))
            buf.putInt(if (reportPhysical) PHYSICALITY_ALL_STANDARD else 0)
            buf.putInt(if (reportPhysical) 0 else PHYSICALITY_ALL_STANDARD)
            return buf.array()
        }

        fun isServerMetadataPacket(data: ByteArray): Boolean {
            if (data.size < 2) return false
            val reportType = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getShort(0).toInt()
            return (reportType and REPORT_TYPE_SERVER_METADATA.toInt()) != 0
        }

        fun parseVibrationPacket(data: ByteArray): VibrationCommand? {
            if (data.size < REPORT_HEADER_SIZE + 9) return null
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val reportType = buf.getShort(0).toInt()
            if ((reportType and REPORT_TYPE_VIBRATION.toInt()) == 0) return null
            // Skip 14-byte report header + rumbleType(1) + gamepadIndex(1)
            val gamepadIndex = data[REPORT_HEADER_SIZE + 1].toInt() and 0xFF
            val strongPct = (data[REPORT_HEADER_SIZE + 2].toInt() and 0xFF) / 100f
            val weakPct = (data[REPORT_HEADER_SIZE + 3].toInt() and 0xFF) / 100f
            val durationMs = (buf.getShort(REPORT_HEADER_SIZE + 6).toInt() and 0xFFFF).toLong().coerceIn(20L, 800L)
            return VibrationCommand(
                gamepadIndex = gamepadIndex,
                strongMotor = strongPct.coerceIn(0f, 1f),
                weakMotor = weakPct.coerceIn(0f, 1f),
                durationMs = durationMs
            )
        }

        internal fun mapToXboxCloudButtonMask(localMask: Int): Short {
            var xbox = 0
            if ((localMask and GamepadState.BTN_NEXUS) != 0) xbox = xbox or (1 shl 1)
            if ((localMask and GamepadState.BTN_MENU) != 0) xbox = xbox or (1 shl 2)
            if ((localMask and GamepadState.BTN_VIEW) != 0) xbox = xbox or (1 shl 3)
            if ((localMask and GamepadState.BTN_A) != 0) xbox = xbox or (1 shl 4)
            if ((localMask and GamepadState.BTN_B) != 0) xbox = xbox or (1 shl 5)
            if ((localMask and GamepadState.BTN_X) != 0) xbox = xbox or (1 shl 6)
            if ((localMask and GamepadState.BTN_Y) != 0) xbox = xbox or (1 shl 7)
            if ((localMask and GamepadState.BTN_DPAD_UP) != 0) xbox = xbox or (1 shl 8)
            if ((localMask and GamepadState.BTN_DPAD_DOWN) != 0) xbox = xbox or (1 shl 9)
            if ((localMask and GamepadState.BTN_DPAD_LEFT) != 0) xbox = xbox or (1 shl 10)
            if ((localMask and GamepadState.BTN_DPAD_RIGHT) != 0) xbox = xbox or (1 shl 11)
            if ((localMask and GamepadState.BTN_LB) != 0) xbox = xbox or (1 shl 12)
            if ((localMask and GamepadState.BTN_RB) != 0) xbox = xbox or (1 shl 13)
            if ((localMask and GamepadState.BTN_LSTICK) != 0) xbox = xbox or (1 shl 14)
            if ((localMask and GamepadState.BTN_RSTICK) != 0) xbox = xbox or (1 shl 15)
            return xbox.toShort()
        }

        private fun floatAxisToShort(value: Float): Short {
            return (value.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }

        private fun floatTriggerToUShort(value: Float): Short {
            return (value.coerceIn(0f, 1f) * 65535f).toInt().toShort()
        }
    }
}
