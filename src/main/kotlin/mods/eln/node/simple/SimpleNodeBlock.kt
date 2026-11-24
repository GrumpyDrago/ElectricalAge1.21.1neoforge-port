package mods.eln.node.simple

import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import mods.eln.misc.Utils.entityLivingViewDirection
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.BlockHitResult

abstract class SimpleNodeBlock protected constructor(properties: Properties) : Block(properties), EntityBlock {
    var descriptorKey: String? = null
    fun setDescriptorKey(descriptorKey: String?): SimpleNodeBlock {
        this.descriptorKey = descriptorKey
        return this
    }

    fun setDescriptor(descriptor: DescriptorBase): SimpleNodeBlock {
        descriptorKey = descriptor.descriptorKey
        return this
    }

    init {
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.FACING)
    }

    open fun getFrontForPlacement(e: LivingEntity?): Direction {
        return entityLivingViewDirection(e!!).inverse
    }

    abstract fun newNode(): SimpleNode?

    fun getNode(world: Level, pos: BlockPos): SimpleNode? = getEntity(world, pos)?.node

    fun getEntity(world: Level, pos: BlockPos): SimpleNodeEntity? = world.getBlockEntity(pos) as? SimpleNodeEntity

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level.isClientSide || placer == null) return
        val node = newNode()!!
        node.descriptorKey = descriptorKey
        val front = getFrontForPlacement(placer)
        node.onBlockPlacedBy(Coordinate(pos.x, pos.y, pos.z, level), front, placer, stack)
        level.setBlock(pos, state.setValue(BlockStateProperties.FACING, front.toForge()), UPDATE_CLIENTS)
    }

    override fun onDestroyedByPlayer(state: BlockState, level: Level, pos: BlockPos, player: Player, willHarvest: Boolean, fluid: FluidState): Boolean {
        if (!level.isClientSide) getNode(level, pos)?.removedByPlayer = player as ServerPlayer
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!newState.`is`(this)) getEntity(level, pos)?.onBreakBlock()
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, neighborBlock: Block, neighborPos: BlockPos, movedByPiston: Boolean) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston)
        if (!level.isClientSide) getEntity(level, pos)?.onNeighborBlockChange()
    }

    // client server
    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        val entity = getEntity(level, pos) ?: return InteractionResult.PASS
        val done = entity.onBlockActivated(
            player,
            fromIntMinecraftSide(hit.direction.get3DDataValue()),
            (hit.location.x - pos.x).toFloat(),
            (hit.location.y - pos.y).toFloat(),
            (hit.location.z - pos.z).toFloat()
        )
        return if (done) InteractionResult.sidedSuccess(level.isClientSide) else InteractionResult.PASS
    }
}
