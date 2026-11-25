package mods.eln.server

import mods.eln.Eln
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/** World-wide state: currently the wind. */
class SaveConfig : SavedData() {
    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        Eln.wind.writeToNBT(tag, "wind")
        return tag
    }

    override fun isDirty(): Boolean = true

    companion object {
        @JvmField
        var instance: SaveConfig? = null

        @JvmStatic
        fun load(server: MinecraftServer): SaveConfig {
            val config = server.overworld().dataStorage.computeIfAbsent(
                Factory(::SaveConfig) { tag, _ ->
                    Eln.wind.readFromNBT(tag, "wind")
                    SaveConfig()
                },
                "eln_config"
            )
            instance = config
            return config
        }
    }
}
