package mods.eln.lightblock

import mods.eln.misc.Coordinate
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Invisible block that emits light for a while, see [LightBlockEntity].
 */
class LightBlock : Block(
    BlockBehaviour.Properties.of().replaceable().noCollission().noOcclusion().noLootTable().air()
        .strength(0f)
        .lightLevel { it.getValue(LIGHT) }
), EntityBlock {

    init {
        registerDefaultState(defaultBlockState().setValue(LIGHT, 0))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(LIGHT)
    }

    fun stateFor(light: Int): BlockState = defaultBlockState().setValue(LIGHT, light.coerceIn(0, 15))

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = Shapes.empty()

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = LightBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        if (level.isClientSide) null else BlockEntityTicker { _, _, _, entity -> (entity as? LightBlockEntity)?.updateEntity() }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!newState.`is`(this)) {
            val coord = Coordinate(pos.x, pos.y, pos.z, level)
            for (o in LightBlockEntity.observers) {
                o.lightBlockDestructor(coord)
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    companion object {
        @JvmField
        val LIGHT: IntegerProperty = BlockStateProperties.LEVEL
    }
}
