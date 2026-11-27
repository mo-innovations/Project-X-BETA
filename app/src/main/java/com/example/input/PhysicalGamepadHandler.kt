package com.example.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.model.GamepadState
import com.example.model.PhysicalControllerInfo
import kotlin.math.abs

/**
 * Detects physical Bluetooth and USB-OTG Xbox controllers via InputManager
 * and translates Android MotionEvent/KeyEvent streams into normalized GamepadState.
 */
class PhysicalGamepadHandler(context: Context) {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private var listener: InputManager.InputDeviceListener? = null

    fun scanConnectedControllers(): List<PhysicalControllerInfo> {
        return buildList {
            for (id in inputManager.inputDeviceIds) {
                val device = inputManager.getInputDevice(id) ?: continue
                if (isGamepadDevice(device)) {
                    add(
                        PhysicalControllerInfo(
                            deviceId = device.id,
                            name = device.name ?: "Xbox Controller",
                            vendorId = device.vendorId,
                            productId = device.productId
                        )
                    )
                }
            }
        }
    }

    fun registerListener(onControllersChanged: (List<PhysicalControllerInfo>) -> Unit) {
        unregisterListener()
        val deviceListener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                onControllersChanged(scanConnectedControllers())
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                onControllersChanged(scanConnectedControllers())
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                onControllersChanged(scanConnectedControllers())
            }
        }
        listener = deviceListener
        inputManager.registerInputDeviceListener(deviceListener, Handler(Looper.getMainLooper()))
        onControllersChanged(scanConnectedControllers())
    }

    fun unregisterListener() {
        listener?.let {
            inputManager.unregisterInputDeviceListener(it)
            listener = null
        }
    }

    companion object {
        fun isGamepadDevice(device: InputDevice): Boolean {
            if (device.isVirtual) return false
            val lowerName = (device.name ?: "").lowercase()
            if (lowerName.contains("sensor") ||
                lowerName.contains("accel") ||
                lowerName.contains("gyro") ||
                lowerName.contains("touch") ||
                lowerName.contains("fingerprint")
            ) {
                return false
            }
            val sources = device.sources
            val hasGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val hasKeys = runCatching {
                device.hasKeys(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B).any { it }
            }.getOrDefault(false)
            return hasGamepad && hasKeys
        }

        fun applyDeadzone(value: Float, deadzone: Float): Float {
            val clampedDeadzone = deadzone.coerceIn(0f, 0.45f)
            val magnitude = abs(value)
            if (magnitude <= clampedDeadzone) return 0f
            val sign = if (value >= 0f) 1f else -1f
            return ((magnitude - clampedDeadzone) / (1f - clampedDeadzone)).coerceIn(0f, 1f) * sign
        }

        fun handleMotionEvent(
            event: MotionEvent,
            current: GamepadState,
            deadzone: Float
        ): GamepadState? {
            if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
                (event.source and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
            ) {
                return null
            }

            val lx = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_X), deadzone)
            val ly = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Y), deadzone)
            val rx = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Z), deadzone)
            val ry = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_RZ), deadzone)

            val lt = maxOf(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE)
            ).coerceIn(0f, 1f)
            val rt = maxOf(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS)
            ).coerceIn(0f, 1f)

            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

            var mask = current.buttonsMask and
                (GamepadState.BTN_DPAD_UP or
                    GamepadState.BTN_DPAD_DOWN or
                    GamepadState.BTN_DPAD_LEFT or
                    GamepadState.BTN_DPAD_RIGHT).inv()

            if (hatX < -0.5f) mask = mask or GamepadState.BTN_DPAD_LEFT
            if (hatX > 0.5f) mask = mask or GamepadState.BTN_DPAD_RIGHT
            if (hatY < -0.5f) mask = mask or GamepadState.BTN_DPAD_UP
            if (hatY > 0.5f) mask = mask or GamepadState.BTN_DPAD_DOWN

            return current.copy(
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

        fun handleKeyEvent(event: KeyEvent, current: GamepadState): GamepadState? {
            val bit = when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> GamepadState.BTN_A
                KeyEvent.KEYCODE_BUTTON_B -> GamepadState.BTN_B
                KeyEvent.KEYCODE_BUTTON_X -> GamepadState.BTN_X
                KeyEvent.KEYCODE_BUTTON_Y -> GamepadState.BTN_Y
                KeyEvent.KEYCODE_BUTTON_L1 -> GamepadState.BTN_LB
                KeyEvent.KEYCODE_BUTTON_R1 -> GamepadState.BTN_RB
                KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadState.BTN_VIEW
                KeyEvent.KEYCODE_BUTTON_START -> GamepadState.BTN_MENU
                KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadState.BTN_LSTICK
                KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadState.BTN_RSTICK
                KeyEvent.KEYCODE_BUTTON_MODE -> GamepadState.BTN_NEXUS
                KeyEvent.KEYCODE_DPAD_UP -> GamepadState.BTN_DPAD_UP
                KeyEvent.KEYCODE_DPAD_DOWN -> GamepadState.BTN_DPAD_DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> GamepadState.BTN_DPAD_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadState.BTN_DPAD_RIGHT
                else -> return null
            }

            val updatedMask = when (event.action) {
                KeyEvent.ACTION_DOWN -> current.buttonsMask or bit
                KeyEvent.ACTION_UP -> current.buttonsMask and bit.inv()
                else -> return null
            }
            return current.copy(
                buttonsMask = updatedMask,
                timestampMs = System.currentTimeMillis()
            )
        }
    }
}
