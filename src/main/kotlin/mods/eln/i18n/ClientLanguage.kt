package mods.eln.i18n

import net.minecraft.client.Minecraft

internal object ClientLanguage {
    fun current(): String = Minecraft.getInstance().languageManager.selected
}
