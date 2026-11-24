package mods.eln.node.transparent

import mods.eln.compat.*
import mods.eln.registration.ElnBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.fluids.capability.IFluidHandler

/**
 * Block entity for transparent nodes with tanks; see the fluid capability registration in [mods.eln.Eln].
 */
class TransparentNodeEntityWithFluid(pos: BlockPos, state: BlockState) : TransparentNodeEntity(ElnBlockEntities.transparentNodeWithFluid, pos, state) {
    fun fluidHandler(side: Direction?): IFluidHandler? {
        if (worldObj.isClientSide) return null
        return (node as? TransparentNode)?.fluidHandler?.forSide(side)
    }
}
