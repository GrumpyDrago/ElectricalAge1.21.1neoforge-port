package mods.eln.node

import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.BlockHitResult

/**
 * Block whose behaviour is delegated to the [Node] at the same position.
 */
abstract class NodeBlock(properties: Properties) : Block(properties), EntityBlock {

    init {
        registerDefaultState(defaultBlockState().setValue(LIGHT, 0))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(LIGHT)
    }

    protected fun nodeEntity(level: BlockGetter, pos: BlockPos): NodeBlockEntity? = level.getBlockEntity(pos) as? NodeBlockEntity

    override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: net.minecraft.core.Direction): Int =
        nodeEntity(level, pos)?.isProvidingWeakPower(fromIntMinecraftSide(direction.get3DDataValue())) ?: 0

    override fun canConnectRedstone(state: BlockState, level: BlockGetter, pos: BlockPos, direction: net.minecraft.core.Direction?): Boolean =
        nodeEntity(level, pos)?.canConnectRedstone(Direction.XN) ?: false

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.ENTITYBLOCK_ANIMATED

    //server
    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        super.onPlace(state, level, pos, oldState, movedByPiston)
        if (!level.isClientSide && !oldState.`is`(this)) nodeEntity(level, pos)?.onBlockAdded()
    }

    //server
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!newState.`is`(this)) onBlockBroken(level, pos)
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    /** The block is about to disappear; its block entity is still there. */
    protected open fun onBlockBroken(level: Level, pos: BlockPos) {
        nodeEntity(level, pos)?.onBreakBlock()
    }

    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, neighborBlock: Block, neighborPos: BlockPos, movedByPiston: Boolean) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston)
        if (!level.isClientSide) onNeighborBlockChange(level, pos, neighborBlock)
    }

    protected open fun onNeighborBlockChange(level: Level, pos: BlockPos, neighborBlock: Block) {
        nodeEntity(level, pos)?.onNeighborBlockChange()
    }

    /** The side of the node a hit on this block refers to. */
    open fun hitSide(level: Level, hit: BlockHitResult): Direction = fromIntMinecraftSide(hit.direction.get3DDataValue())!!

    //client server
    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        val entity = nodeEntity(level, pos) ?: return InteractionResult.PASS
        val done = entity.onBlockActivated(
            player,
            hitSide(level, hit),
            (hit.location.x - pos.x).toFloat(),
            (hit.location.y - pos.y).toFloat(),
            (hit.location.z - pos.z).toFloat()
        )
        return if (done) InteractionResult.sidedSuccess(level.isClientSide) else InteractionResult.PASS
    }

    companion object {
        /** Light emitted by the node. */
        @JvmField
        val LIGHT: IntegerProperty = BlockStateProperties.LEVEL

        fun baseProperties(): BlockBehaviour.Properties = BlockBehaviour.Properties.of()
            .strength(1.0f, 1.0f)
            .noOcclusion()
            .noLootTable()
            .isSuffocating { _, _, _ -> false }
            .isViewBlocking { _, _, _ -> false }
            .isRedstoneConductor { _, _, _ -> false }
            .isValidSpawn { _, _, _, _ -> false }
            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
            .lightLevel { it.getValue(LIGHT) }

        /** Stores the light a node emits in its block state. */
        fun setLight(level: LevelAccessor, pos: BlockPos, light: Int) {
            val state = level.getBlockState(pos)
            if (state.hasProperty(LIGHT) && state.getValue(LIGHT) != light) {
                level.setBlock(pos, state.setValue(LIGHT, light), UPDATE_CLIENTS or UPDATE_KNOWN_SHAPE)
            }
        }
    }
}
