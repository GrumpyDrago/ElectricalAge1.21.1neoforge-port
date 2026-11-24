package mods.eln.node.transparent

import mods.eln.node.NodeBlock
import mods.eln.node.NodeBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * A block-filling machine or device.
 */
class TransparentNodeBlock : NodeBlock(baseProperties().dynamicShape()) {

    init {
        registerDefaultState(defaultBlockState().setValue(FLUID, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(FLUID)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        if (state.getValue(FLUID)) TransparentNodeEntityWithFluid(pos, state) else TransparentNodeEntity(pos, state)

    fun stateFor(tag: EntityMetaTag): BlockState = defaultBlockState().setValue(FLUID, tag == EntityMetaTag.Fluid)

    override fun onDestroyedByPlayer(state: BlockState, level: Level, pos: BlockPos, player: Player, willHarvest: Boolean, fluid: FluidState): Boolean {
        if (!level.isClientSide) {
            val node = (level.getBlockEntity(pos) as? NodeBlockEntity)?.node
            if (node is TransparentNode) node.removedByPlayer = player as ServerPlayer
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)
    }

    override fun getCloneItemStack(state: BlockState, target: HitResult, level: LevelReader, pos: BlockPos, player: Player): ItemStack {
        val entity = level.getBlockEntity(pos) as? TransparentNodeEntity ?: return ItemStack.EMPTY
        return entity.elementRender?.transparentNodeDescriptor?.newItemStack() ?: ItemStack.EMPTY
    }

    override fun getLightBlock(state: BlockState, level: BlockGetter, pos: BlockPos): Int = 0

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    override fun getOcclusionShape(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape = Shapes.empty()

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val entity = level.getBlockEntity(pos) as? TransparentNodeEntity ?: return Shapes.block()
        if (entity.level == null) return Shapes.block()
        return collisionShape(entity, pos)
    }

    val nodeUuid: String
        get() = "t"

    companion object {
        /** The element handles fluids. */
        @JvmField
        val FLUID: BooleanProperty = BooleanProperty.create("fluid")

        /** The part of this machine's collision that lies in the block at [pos]. */
        fun collisionShape(entity: TransparentNodeEntity, pos: BlockPos): VoxelShape {
            val list = ArrayList<AABB?>()
            val region = AABB(pos).inflate(0.5)
            entity.addCollisionBoxesToList(region, list, mods.eln.misc.Coordinate(pos.x, pos.y, pos.z, entity.level!!))
            var shape = Shapes.empty()
            for (box in list) {
                if (box != null) shape = Shapes.or(shape, Shapes.create(box.move(-pos.x.toDouble(), -pos.y.toDouble(), -pos.z.toDouble())))
            }
            return shape
        }
    }
}
