package mods.eln.simplenode.computerprobe

import mods.eln.node.simple.SimpleNodeEntity
import mods.eln.registration.SingleNodeRegistration
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class ComputerProbeEntity(pos: BlockPos, state: BlockState) :
    SimpleNodeEntity(SingleNodeRegistration.computerProbe, pos, state, ComputerProbeNode.getNodeUuidStatic())
