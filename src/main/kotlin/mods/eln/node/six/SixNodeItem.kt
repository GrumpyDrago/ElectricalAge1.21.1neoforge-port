@file:Suppress("NAME_SHADOWING")
package mods.eln.node.six

import mods.eln.Eln
import mods.eln.compat.*
import mods.eln.compat.IItemRenderer.ItemRenderType
import mods.eln.compat.IItemRenderer.ItemRendererHelper
import mods.eln.compat.client.GL11
import mods.eln.generic.BlockDescriptorItem
import mods.eln.generic.DescriptorItem
import mods.eln.generic.GenericItemBlockUsingDamage
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import mods.eln.misc.LRDU
import mods.eln.misc.Utils.addChatMessage
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SoundType

/** The family of all six-node elements. */
class SixNodeItem : GenericItemBlockUsingDamage<SixNodeDescriptor>() {

    override fun newItem(damage: Int, descriptor: SixNodeDescriptor): DescriptorItem = SixNodeDescriptorItem(this, damage, descriptor)

    /**
     * The player clicked on [side] of the block at x, y, z with this stack.
     */
    fun onItemUse(stack: ItemStack, player: Player, world: Level, x: Int, y: Int, z: Int, side: Int): Boolean {
        var pos = BlockPos(x, y, z)
        var side = side
        val state = world.getBlockState(pos)
        if (state.`is`(Blocks.SNOW) && state.canBeReplaced()) {
            side = 1
        } else if (!state.canBeReplaced() || state.`is`(Eln.sixNodeBlock)) {
            pos = pos.relative(Direction.from3DDataValue(side))
        }
        if (stack.isEmpty) return false
        val descriptor = getDescriptor(stack)
        if (!player.mayUseItemAt(pos, Direction.from3DDataValue(side), stack)) return false
        if (!world.isInWorldBounds(pos)) return false
        if (world.isClientSide) return true
        if (placeBlockAt(stack, player, world, pos.x, pos.y, pos.z, side)) {
            val sound = SoundType.METAL
            world.playSound(null, pos, sound.placeSound, SoundSource.BLOCKS, (sound.volume + 1f) / 2f, sound.pitch * 0.8f)
            stack.consume(1, player)
        }
        return true
    }

    /**
     * Returns true if the given stack may go on the given side of the given block.
     */
    fun canPlaceItemBlockOnSide(world: Level, x: Int, y: Int, z: Int, side: Int, player: Player, stack: ItemStack): Boolean {
        if (!isStackValidToPlace(stack)) return false
        val direction = fromIntMinecraftSide(side)!!
        val descriptor = getDescriptor(stack)
        return descriptor.canBePlacedOnSide(player, Coordinate(x, y, z, world), direction.inverse)
    }

    fun isStackValidToPlace(stack: ItemStack?): Boolean = getDescriptor(stack) != null

    fun placeBlockAt(stack: ItemStack, player: Player, world: Level, x: Int, y: Int, z: Int, side: Int): Boolean {
        if (world.isClientSide) return false
        if (!isStackValidToPlace(stack)) return false
        val direction = fromIntMinecraftSide(side)!!.inverse
        val pos = BlockPos(x, y, z)
        val oldState = world.getBlockState(pos)
        val block = Eln.sixNodeBlock
        if (oldState.`is`(block)) {
            val sixNode = (world.getBlockEntity(pos) as? SixNodeEntity)?.node as? SixNode
            if (sixNode == null) {
                world.setBlockToAir(x, y, z)
                return false
            }
            if (!sixNode.getSideEnable(direction) && block.getIfOtherBlockIsSolid(world, x, y, z, direction)) {
                return sixNode.createSubBlock(stack, direction, player)
            }
        } else if (oldState.canBeReplaced()) {
            val coord = Coordinate(x, y, z, world)
            val descriptor = getDescriptor(stack)
            val error = descriptor.checkCanPlace(coord, direction, LRDU.Up)
            if (error != null) {
                addChatMessage(player, error)
                return false
            }
            if (block.getIfOtherBlockIsSolid(world, x, y, z, direction)) {
                descriptor.getGhostGroup(direction, LRDU.Up)?.plot(coord, coord, descriptor.ghostGroupUuid)
                val sixNode = SixNode()
                sixNode.onBlockPlacedBy(Coordinate(x, y, z, world), direction, player, stack)
                sixNode.createSubBlock(stack, direction, player)
                world.setBlock(pos, block.defaultBlockState(), 3)
                sixNode.syncLight()
                return true
            }
        }
        return false
    }
}

/** A six-node element in an inventory. */
class SixNodeDescriptorItem(private val family: SixNodeItem, elnId: Int, descriptor: SixNodeDescriptor) :
    BlockDescriptorItem<SixNodeDescriptor>(family, elnId, descriptor) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        val pos = context.clickedPos
        val side = context.clickedFace.get3DDataValue()
        if (context.level.isClientSide && !family.canPlaceItemBlockOnSide(context.level, pos.x, pos.y, pos.z, side, player, context.itemInHand)) {
            return InteractionResult.FAIL
        }
        val done = family.onItemUse(context.itemInHand, player, context.level, pos.x, pos.y, pos.z, side)
        return if (done) InteractionResult.sidedSuccess(context.level.isClientSide) else InteractionResult.FAIL
    }

    override fun handleRenderType(item: ItemStack, type: ItemRenderType): Boolean = descriptor.handleRenderType(item, type)

    override fun shouldUseRenderHelper(type: ItemRenderType, item: ItemStack, helper: ItemRendererHelper): Boolean =
        descriptor.shouldUseRenderHelper(type, item, helper)

    override fun renderItem(type: ItemRenderType, item: ItemStack, vararg data: Any) {
        if (descriptor.shouldUseRenderHelperEln(type, item, null)) {
            when (type) {
                ItemRenderType.ENTITY -> GL11.glRotatef(90f, 0f, 0f, 1f)
                ItemRenderType.EQUIPPED_FIRST_PERSON -> {
                    GL11.glRotatef(160f, 0f, 1f, 0f)
                    GL11.glTranslatef(-0.70f, 1f, -0.7f)
                    GL11.glScalef(1.8f, 1.8f, 1.8f)
                    GL11.glRotatef(-90f, 1f, 0f, 0f)
                }
                ItemRenderType.EQUIPPED -> {
                    GL11.glRotatef(180f, 0f, 1f, 0f)
                    GL11.glTranslatef(-0.70f, 1f, -0.7f)
                    GL11.glScalef(1.5f, 1.5f, 1.5f)
                }
                ItemRenderType.INVENTORY -> {
                    GL11.glRotatef(-90f, 0f, 1f, 0f)
                    GL11.glRotatef(-90f, 1f, 0f, 0f)
                }
                else -> {
                }
            }
        }
        descriptor.renderItem(type, item, *data)
    }
}
