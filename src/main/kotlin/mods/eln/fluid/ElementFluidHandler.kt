package mods.eln.fluid

import mods.eln.compat.registries
import mods.eln.misc.INBTTReady
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.templates.FluidTank

/**
 * Use one of these if you want your block to support fluids!
 *
 * See the steam turbine for an example.
 *
 * @param tankSize Tank size, in mB.
 */
open class ElementFluidHandler(tankSize: Int) : IElnFluidHandler, INBTTReady {
    private var filter: ((Fluid) -> Boolean)? = null
    private var fluidHeat = 0f

    @JvmField
    var tank = FluidTank(tankSize)

    /** Which fluids are let in. */
    fun setFilter(filter: (Fluid) -> Boolean) {
        this.filter = filter
    }

    open val heatEnergyPerMilliBucket: Float
        get() {
            if (fluidHeat == 0f && !tank.isEmpty) setHeatEnergyPerMilliBucket(tank.fluid.fluid)
            return fluidHeat
        }

    private fun setHeatEnergyPerMilliBucket(fluid: Fluid) {
        fluidHeat = FuelRegistry.heatEnergyPerMilliBucket(fluid).toFloat()
    }

    override fun fill(from: Direction?, resource: FluidStack?, doFill: Boolean): Int {
        if (resource == null || resource.isEmpty) return 0
        // A non empty tank only takes more of the same.
        if (!tank.isEmpty) return tank.fill(resource, doFill)
        if (filter?.invoke(resource.fluid) == false) return 0
        if (doFill) setHeatEnergyPerMilliBucket(resource.fluid)
        return tank.fill(resource, doFill)
    }

    override fun drain(from: Direction?, resource: FluidStack?, doDrain: Boolean): FluidStack? {
        if (resource == null || !FluidStack.isSameFluidSameComponents(resource, tank.fluid)) return null
        return tank.drain(resource.amount, doDrain)
    }

    override fun drain(from: Direction?, maxDrain: Int, doDrain: Boolean): FluidStack? = tank.drain(maxDrain, doDrain)

    override fun canFill(from: Direction?, stack: FluidStack): Boolean =
        if (!tank.isEmpty) FluidStack.isSameFluid(tank.fluid, stack) else filter?.invoke(stack.fluid) ?: true

    override fun tankFor(from: Direction?): FluidTank? = tank

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        tank.readFromNBT(registries(), nbt.getCompound(str + "tank"))
        fluidHeat = nbt.getFloat(str + "fhm")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.put(str + "tank", tank.writeToNBT(registries(), CompoundTag()))
        nbt.putFloat(str + "fhm", fluidHeat)
    }
}
