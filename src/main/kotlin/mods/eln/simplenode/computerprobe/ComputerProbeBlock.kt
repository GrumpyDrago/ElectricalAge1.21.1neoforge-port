package mods.eln.simplenode.computerprobe

import mods.eln.node.simple.SimpleNode
import mods.eln.node.simple.SimpleNodeBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState

class ComputerProbeBlock : SimpleNodeBlock(BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.METAL)) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ComputerProbeEntity(pos, state)

    override fun newNode(): SimpleNode = ComputerProbeNode()
}
