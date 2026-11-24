package mods.eln.fluid

import mods.eln.compat.*

import mods.eln.misc.INBTTReady
import mods.eln.misc.Utils
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.capability.templates.FluidTank
import java.lang.Exception

data class TankData(val tank: FluidTank, val fluidWhitelist: MutableList<Fluid> = mutableListOf(), var fractionalDemandMb: Double = 0.0):
    INBTTReady {

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        tank.readFromNBT(registries(), nbt.getCompound("${str}tank"))
        fluidWhitelist.clear()
        for (name in nbt.getString("${str}whitelist").split("|")) {
            if (name.isEmpty()) continue
            val fluid = FluidNames.find(name)
            if (fluid != null) fluidWhitelist.add(fluid) else Utils.println("Error, could not find fluid $name")
        }
        fractionalDemandMb = nbt.getDouble("${str}demandMb")
        tank.capacity = nbt.getInt("${str}capacity")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        val tag = CompoundTag()
        tank.writeToNBT(registries(), tag)
        nbt.put("${str}tank", tag)
        nbt.putString("${str}whitelist", fluidWhitelist.joinToString("|") { FluidNames.idOf(it) })
        nbt.putDouble("${str}demandMb", fractionalDemandMb)
        nbt.putInt("${str}capacity", tank.capacity)
    }

    override fun toString(): String {
        return "TankData(${tank.fluidAmount}/${tank.capacity}mB of ${tank.fluid}, whitelist: ${fluidWhitelist}, ${fractionalDemandMb}mB spare"
    }
}
