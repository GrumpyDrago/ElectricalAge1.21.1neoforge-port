package mods.eln.node.six

import mods.eln.Eln
import mods.eln.compat.blockExists
import mods.eln.compat.isOpaqueCubeState
import mods.eln.compat.orNull
import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import mods.eln.misc.Utils.isCreative
import mods.eln.misc.Utils.println
import mods.eln.node.NodeBlock
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Holds up to six elements, one on the inside of each face of the block.
 */
class SixNodeBlock : NodeBlock(baseProperties().strength(0.3f, 1.0f).dynamicShape()) {

    init {
        registerDefaultState(defaultBlockState().setValue(COVERED, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(COVERED)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = SixNodeEntity(pos, state)

    override fun canBeReplaced(state: BlockState, context: net.minecraft.world.item.context.BlockPlaceContext): Boolean = false

    override fun canBeReplaced(state: BlockState, fluid: net.minecraft.world.level.material.Fluid): Boolean = false

    override fun getCloneItemStack(state: BlockState, target: HitResult, level: LevelReader, pos: BlockPos, player: Player): ItemStack {
        val entity = level.getBlockEntity(pos) as? SixNodeEntity
        if (entity != null && target is BlockHitResult) {
            val render = entity.elementRenderList[hitSide(entity, target).int]
            if (render != null) {
                findClosestMatchingHotbarStack(player, render.sixNodeDescriptor)?.let { return it.copy() }
                return render.sixNodeDescriptor.newCreativeTabStack()
            }
        }
        return ItemStack.EMPTY
    }

    private fun findClosestMatchingHotbarStack(player: Player, descriptor: SixNodeDescriptor): ItemStack? {
        val inventory = player.inventory ?: return null
        val currentSlot = inventory.selected
        var bestStack: ItemStack? = null
        var bestDistance = Int.MAX_VALUE
        for (slot in 0 until 9) {
            val stack = inventory.getItem(slot).orNull() ?: continue
            if (!descriptor.checkSameItemStack(stack)) continue
            val distance = hotbarDistance(currentSlot, slot)
            if (distance < bestDistance) {
                bestDistance = distance
                bestStack = stack
            }
        }
        return bestStack
    }

    private fun hotbarDistance(a: Int, b: Int): Int {
        val direct = Math.abs(a - b)
        return Math.min(direct, 9 - direct)
    }

    // ---------------------------------------------------------------- shape

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        if (state.getValue(COVERED)) return Shapes.block()
        val entity = level.getBlockEntity(pos) as? SixNodeEntity ?: return Shapes.block()
        if (hasVolume(entity)) return Shapes.block()
        return SHAPES[entity.sideMask()]
    }

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        if (state.getValue(COVERED)) return Shapes.block()
        val entity = level.getBlockEntity(pos) as? SixNodeEntity ?: return Shapes.empty()
        return if (hasVolume(entity)) Shapes.block() else Shapes.empty()
    }

    override fun getOcclusionShape(state: BlockState, level: BlockGetter, pos: BlockPos): VoxelShape = Shapes.empty()

    override fun getVisualShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = Shapes.empty()

    override fun getLightBlock(state: BlockState, level: BlockGetter, pos: BlockPos): Int = if (state.getValue(COVERED)) 15 else 0

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = !state.getValue(COVERED)

    fun hasVolume(entity: SixNodeEntity): Boolean = entity.level != null && entity.hasVolume()

    override fun hitSide(level: Level, hit: BlockHitResult): Direction {
        val entity = level.getBlockEntity(hit.blockPos) as? SixNodeEntity ?: return super.hitSide(level, hit)
        return hitSide(entity, hit)
    }

    /** Which of the six elements was hit. */
    fun hitSide(entity: SixNodeEntity, hit: BlockHitResult): Direction {
        val face = fromIntMinecraftSide(hit.direction.get3DDataValue())!!
        if (entity.isCovered) return face
        if (hasVolume(entity)) return Direction.YN
        val p = hit.location.subtract(Vec3.atLowerCornerOf(hit.blockPos))
        val mask = entity.sideMask()
        var best: Direction? = null
        var bestDistance = Double.MAX_VALUE
        for (d in Direction.values()) {
            if (mask and (1 shl d.int) == 0) continue
            val distance = when (d) {
                Direction.XN -> p.x
                Direction.XP -> 1 - p.x
                Direction.YN -> p.y
                Direction.YP -> 1 - p.y
                Direction.ZN -> p.z
                Direction.ZP -> 1 - p.z
            }
            // In corners several elements are equally close; the one facing the player wins.
            val score = distance + if (d.inverse === face) -1e-3 else 0.0
            if (score < bestDistance) {
                bestDistance = score
                best = d
            }
        }
        return best ?: face
    }

    /** Outline of the element that was hit, relative to the block. */
    fun outline(entity: SixNodeEntity, hit: BlockHitResult): VoxelShape =
        if (entity.isCovered || hasVolume(entity)) Shapes.block() else SIDE_SHAPES[hitSide(entity, hit).int]

    // ---------------------------------------------------------------- breaking

    override fun onDestroyedByPlayer(state: BlockState, level: Level, pos: BlockPos, player: Player, willHarvest: Boolean, fluid: FluidState): Boolean {
        if (level.isClientSide) return false
        val tileEntity = level.getBlockEntity(pos) as? SixNodeEntity ?: return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)
        val sixNode = tileEntity.node as? SixNode ?: return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)
        if (sixNode.sixNodeCacheBlock !== Blocks.AIR) {
            if (!isCreative(player as ServerPlayer)) {
                sixNode.dropItem(ItemStack(sixNode.sixNodeCacheBlock))
            }
            sixNode.changeCover(Blocks.AIR)
            return false
        }
        val breakDirection = resolveBreakDirection(level, pos, player, tileEntity, sixNode) ?: return false
        if (!sixNode.playerAskToBreakSubBlock(player as ServerPlayer, breakDirection)) return false
        return if (sixNode.ifSideRemain) false else super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)
    }

    override fun onBlockBroken(level: Level, pos: BlockPos) {
        if (!level.isClientSide) {
            val sixNode = (level.getBlockEntity(pos) as? SixNodeEntity)?.node as? SixNode
            if (sixNode != null) {
                for (direction in Direction.values()) {
                    if (sixNode.getSideEnable(direction)) {
                        println("SixNodeBlock.breakBlock deleting side=$direction at $pos")
                        sixNode.deleteSubBlock(null, direction)
                    }
                }
            }
        }
        super.onBlockBroken(level, pos)
    }

    override fun onNeighborBlockChange(level: Level, pos: BlockPos, neighborBlock: Block) {
        val sixNode = (level.getBlockEntity(pos) as? SixNodeEntity)?.node as? SixNode ?: return
        for (direction in Direction.values()) {
            if (sixNode.getSideEnable(direction)) {
                if (!getIfOtherBlockIsSolid(level, pos.x, pos.y, pos.z, direction)) {
                    println("SixNodeBlock.onNeighborBlockChange deleting side=$direction at $pos dueTo=${neighborBlock.javaClass.simpleName}")
                    sixNode.deleteSubBlock(null, direction)
                }
            }
        }
        if (!sixNode.ifSideRemain) {
            level.removeBlock(pos, false)
        } else {
            super.onNeighborBlockChange(level, pos, neighborBlock)
        }
    }

    private fun resolveBreakDirection(level: Level, pos: BlockPos, player: Player, entity: SixNodeEntity, sixNode: SixNode): Direction? {
        // Server player state can lag a few ticks behind under low TPS; add margin so the raytrace still resolves a side.
        val start = player.eyePosition
        val end = start.add(player.getViewVector(1f).scale(8.0))
        val hit = getShape(level.getBlockState(pos), level, pos, CollisionContext.of(player)).clip(start, end, pos)
        if (hit != null) {
            val side = hitSide(entity, hit)
            if (sixNode.getSideEnable(side)) return side
        }

        // Fallback: pick the enabled face most directly facing the player's look vector.
        val look = player.getViewVector(1f)
        return Direction.values().filter { sixNode.getSideEnable(it) }.maxByOrNull { faceFacingPlayerScore(it, look) }
    }

    private fun faceFacingPlayerScore(direction: Direction, look: Vec3): Double {
        // The element on a face is seen when looking towards that face.
        return when (direction) {
            Direction.XN -> -look.x
            Direction.XP -> look.x
            Direction.YN -> -look.y
            Direction.YP -> look.y
            Direction.ZN -> -look.z
            Direction.ZP -> look.z
        }
    }

    fun getIfOtherBlockIsSolid(world: Level, x: Int, y: Int, z: Int, direction: Direction): Boolean {
        val vect = intArrayOf(x, y, z)
        direction.applyTo(vect, 1)
        // During chunk load, neighboring chunks can still be unavailable. Treat that as
        // "unknown" instead of "air" so attached six-node parts do not self-delete
        // before their support block has actually loaded.
        if (!world.blockExists(vect[0], vect[1], vect[2])) return true
        return world.getBlockState(BlockPos(vect[0], vect[1], vect[2])).isOpaqueCubeState
    }

    val nodeUuid: String
        get() = "s"

    companion object {
        /** A cover block is placed over the elements. */
        @JvmField
        val COVERED: BooleanProperty = BooleanProperty.create("covered")

        private const val T = 2.0

        private val SIDE_SHAPES: Array<VoxelShape> = Array(6) {
            when (Direction.fromInt(it)!!) {
                Direction.XN -> box(0.0, 0.0, 0.0, T, 16.0, 16.0)
                Direction.XP -> box(16.0 - T, 0.0, 0.0, 16.0, 16.0, 16.0)
                Direction.YN -> box(0.0, 0.0, 0.0, 16.0, T, 16.0)
                Direction.YP -> box(0.0, 16.0 - T, 0.0, 16.0, 16.0, 16.0)
                Direction.ZN -> box(0.0, 0.0, 0.0, 16.0, 16.0, T)
                Direction.ZP -> box(0.0, 0.0, 16.0 - T, 16.0, 16.0, 16.0)
            }
        }

        private val SHAPES: Array<VoxelShape> = Array(64) { mask ->
            var shape = Shapes.empty()
            for (i in 0 until 6) if (mask and (1 shl i) != 0) shape = Shapes.or(shape, SIDE_SHAPES[i])
            if (shape.isEmpty) Shapes.create(AABB(0.4, 0.4, 0.4, 0.6, 0.6, 0.6)) else shape
        }
    }
}
