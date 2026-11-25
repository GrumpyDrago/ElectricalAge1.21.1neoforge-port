package mods.eln.fluid

import net.minecraft.core.Direction
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.templates.FluidTank

/** Tanks of a machine, accessed from a side. */
interface IElnFluidHandler {
    fun fill(from: Direction?, resource: FluidStack?, doFill: Boolean): Int
    fun drain(from: Direction?, resource: FluidStack?, doDrain: Boolean): FluidStack?
    fun drain(from: Direction?, maxDrain: Int, doDrain: Boolean): FluidStack?
    fun canFill(from: Direction?, stack: FluidStack): Boolean
    fun tankFor(from: Direction?): FluidTank?

    /** What other mods see on this side, or null if the side has no tank. */
    fun forSide(side: Direction?): IFluidHandler? {
        val tank = tankFor(side) ?: return null
        val owner = this
        return object : IFluidHandler {
            override fun getTanks(): Int = 1
            override fun getFluidInTank(index: Int): FluidStack = tank.fluid
            override fun getTankCapacity(index: Int): Int = tank.capacity
            override fun isFluidValid(index: Int, stack: FluidStack): Boolean = owner.canFill(side, stack)
            override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int =
                owner.fill(side, resource, action.execute())

            override fun drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack =
                owner.drain(side, resource, action.execute()) ?: FluidStack.EMPTY

            override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack =
                owner.drain(side, maxDrain, action.execute()) ?: FluidStack.EMPTY
        }
    }

    companion object {
        fun action(execute: Boolean): IFluidHandler.FluidAction =
            if (execute) IFluidHandler.FluidAction.EXECUTE else IFluidHandler.FluidAction.SIMULATE
    }
}

fun FluidTank.fill(resource: FluidStack, doFill: Boolean): Int = fill(resource, IElnFluidHandler.action(doFill))

fun FluidTank.drain(amount: Int, doDrain: Boolean): FluidStack? =
    drain(amount, IElnFluidHandler.action(doDrain)).takeUnless { it.isEmpty }
