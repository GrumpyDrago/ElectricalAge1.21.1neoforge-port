package mods.eln.compat.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object Keyboard {
    const val KEY_ESCAPE = GLFW.GLFW_KEY_ESCAPE
    const val KEY_RETURN = GLFW.GLFW_KEY_ENTER
    const val KEY_NUMPADENTER = GLFW.GLFW_KEY_KP_ENTER
    const val KEY_BACK = GLFW.GLFW_KEY_BACKSPACE
    const val KEY_TAB = GLFW.GLFW_KEY_TAB
    const val KEY_UP = GLFW.GLFW_KEY_UP
    const val KEY_DOWN = GLFW.GLFW_KEY_DOWN
    const val KEY_LEFT = GLFW.GLFW_KEY_LEFT
    const val KEY_RIGHT = GLFW.GLFW_KEY_RIGHT
    const val KEY_C = GLFW.GLFW_KEY_C
    const val KEY_P = GLFW.GLFW_KEY_P
    const val KEY_V = GLFW.GLFW_KEY_V
    const val KEY_LSHIFT = GLFW.GLFW_KEY_LEFT_SHIFT
    const val KEY_RSHIFT = GLFW.GLFW_KEY_RIGHT_SHIFT
    const val KEY_LCONTROL = GLFW.GLFW_KEY_LEFT_CONTROL
    const val KEY_RCONTROL = GLFW.GLFW_KEY_RIGHT_CONTROL

    @JvmStatic
    fun isKeyDown(key: Int): Boolean = InputConstants.isKeyDown(Minecraft.getInstance().window.window, key)

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun enableRepeatEvents(on: Boolean) {
    }
}
