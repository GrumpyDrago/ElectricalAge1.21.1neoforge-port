package mods.eln.server

import mods.eln.Eln
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/** World-wide state: the wind, and what wears out. */
class SaveConfig : SavedData() {
    @JvmField
    var heatFurnaceFuel = true
    var infiniteIncandescentLife = false
    var infiniteEcoBulbLife = false
    var infiniteLedBulbLife = false
    var infiniteHalogenBulbLife = false
    var batteryAging = true
    @JvmField
    var infinitePortableBattery = false

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        Eln.wind.writeToNBT(tag, "wind")
        tag.putBoolean("heatFurnaceFuel", heatFurnaceFuel)
        tag.putBoolean("infiniteIncandescentBulbLife", infiniteIncandescentLife)
        tag.putBoolean("infiniteEcoBulbLife", infiniteEcoBulbLife)
        tag.putBoolean("infiniteLedBulbLife", infiniteLedBulbLife)
        tag.putBoolean("infiniteHalogenBulbLife", infiniteHalogenBulbLife)
        tag.putBoolean("batteryAging", batteryAging)
        tag.putBoolean("infinitPortableBattery", infinitePortableBattery)
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
                    SaveConfig().apply {
                        if (tag.contains("heatFurnaceFuel")) heatFurnaceFuel = tag.getBoolean("heatFurnaceFuel")
                        if (tag.contains("infiniteIncandescentBulbLife")) infiniteIncandescentLife = tag.getBoolean("infiniteIncandescentBulbLife")
                        if (tag.contains("infiniteEcoBulbLife")) infiniteEcoBulbLife = tag.getBoolean("infiniteEcoBulbLife")
                        if (tag.contains("infiniteLedBulbLife")) infiniteLedBulbLife = tag.getBoolean("infiniteLedBulbLife")
                        if (tag.contains("infiniteHalogenBulbLife")) infiniteHalogenBulbLife = tag.getBoolean("infiniteHalogenBulbLife")
                        if (tag.contains("batteryAging")) batteryAging = tag.getBoolean("batteryAging")
                        if (tag.contains("infinitPortableBattery")) infinitePortableBattery = tag.getBoolean("infinitPortableBattery")
                    }
                },
                "eln_config"
            )
            instance = config
            return config
        }
    }
}
