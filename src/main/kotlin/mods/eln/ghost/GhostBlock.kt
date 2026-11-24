package mods.eln.ghost

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import mods.eln.node.transparent.TransparentNodeBlock
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Invisible filler for machines larger than one block. Behaviour comes from the [GhostElement] at its position.
 */
class GhostBlock : Block(
    BlockBehaviour.Properties.of().strength(0.5f).noOcclusion().noLootTable().dynamicShape()
        .pushReaction(PushReaction.BLOCK)
        .isSuffocating { _, _, _ -> false }
        .isViewBlocking { _, _, _ -> false }
        .isRedstoneConductor { _, _, _ -> false }
        .isValidSpawn { _, _, _, _ -> false }
) {
    init {
        registerDefaultState(defaultBlockState().setValue(TYPE, tCube))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(TYPE)
    }

    fun stateFor(type: Int): BlockState = defaultBlockState().setValue(TYPE, type)

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        when (state.getValue(TYPE)) {
            tFloor -> FLOOR
            tLadder -> LADDER
            else -> Shapes.block()
        }

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        when (state.getValue(TYPE)) {
            tFloor -> FLOOR
            tLadder -> Shapes.empty()
            else -> {
                val element = if (level is Level && !level.isClientSide) getElement(level, pos) else null
                val entity = element?.observatorCoordonate?.let { (level as Level).getBlockEntity(BlockPos(it.x, it.y, it.z)) }
                if (entity is TransparentNodeEntity) TransparentNodeBlock.collisionShape(entity, pos) else Shapes.block()
            }
        }

    override fun getOcclusionShape(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape = Shapes.empty()

    override fun getLightBlock(state: BlockState, level: BlockGetter, pos: BlockPos): Int = 0

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    override fun isLadder(state: BlockState, level: LevelReader, pos: BlockPos, entity: LivingEntity): Boolean =
        state.getValue(TYPE) == tLadder

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun getCloneItemStack(state: BlockState, target: HitResult, level: LevelReader, pos: BlockPos, player: Player): ItemStack = ItemStack.EMPTY

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!level.isClientSide && !newState.`is`(this)) getElement(level, pos)?.breakBlock()
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (!level.isClientSide) {
            val element = getElement(level, pos)
            if (element != null) {
                val done = element.onBlockActivated(
                    player,
                    fromIntMinecraftSide(hit.direction.get3DDataValue())!!,
                    (hit.location.x - pos.x).toFloat(),
                    (hit.location.y - pos.y).toFloat(),
                    (hit.location.z - pos.z).toFloat()
                )
                return if (done) InteractionResult.CONSUME else InteractionResult.PASS
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    fun getElement(level: Level, pos: BlockPos): GhostElement? = Eln.ghostManager.getGhost(Coordinate(pos.x, pos.y, pos.z, level))

    val nodeUuid: String
        get() = "g"

    companion object {
        const val tCube = 0
        const val tFloor = 1
        const val tLadder = 2

        @JvmField
        val TYPE: IntegerProperty = IntegerProperty.create("type", 0, 2)
        private val FLOOR = box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0)
        private val LADDER = box(0.0, 0.0, 0.0, 0.16, 0.16, 0.16)
    }
}
