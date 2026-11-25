package mods.eln.node.transparent

import mods.eln.registration.ElnBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

/** Block entity for transparent nodes with tanks. */
class TransparentNodeEntityWithFluid(pos: BlockPos, state: BlockState) : TransparentNodeEntity(ElnBlockEntities.transparentNodeWithFluid, pos, state)
