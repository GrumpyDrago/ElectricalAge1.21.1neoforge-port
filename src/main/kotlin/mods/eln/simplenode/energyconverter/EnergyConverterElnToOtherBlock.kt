package mods.eln.simplenode.energyconverter

import mods.eln.node.simple.SimpleNode
import mods.eln.node.simple.SimpleNodeBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState

class EnergyConverterElnToOtherBlock(descriptor: EnergyConverterElnToOtherDescriptor) :
    SimpleNodeBlock(BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.METAL)) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = EnergyConverterElnToOtherEntity(pos, state)

    override fun newNode(): SimpleNode = EnergyConverterElnToOtherNode()

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        if (level.isClientSide) null else BlockEntityTicker { _, _, _, entity -> (entity as? EnergyConverterElnToOtherEntity)?.pushEnergy() }

    init {
        setDescriptor(descriptor)
    }
}
