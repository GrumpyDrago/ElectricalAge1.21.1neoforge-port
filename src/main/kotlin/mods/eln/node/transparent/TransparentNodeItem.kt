@file:Suppress("NAME_SHADOWING")
package mods.eln.node.transparent

import mods.eln.Eln
import mods.eln.compat.*
import mods.eln.compat.IItemRenderer.ItemRenderType
import mods.eln.compat.IItemRenderer.ItemRendererHelper
import mods.eln.compat.client.GL11
import mods.eln.generic.BlockDescriptorItem
import mods.eln.generic.DescriptorItem
import mods.eln.generic.GenericItemBlockUsingDamage
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction.Companion.fromIntMinecraftSide
import mods.eln.misc.Utils.addChatMessage
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.phys.shapes.CollisionContext

/** The family of all transparent nodes. */
class TransparentNodeItem : GenericItemBlockUsingDamage<TransparentNodeDescriptor>() {

    override fun newItem(damage: Int, descriptor: TransparentNodeDescriptor): DescriptorItem = TransparentNodeDescriptorItem(this, damage, descriptor)

    fun placeBlockAt(stack: ItemStack, player: Player, world: Level, x: Int, y: Int, z: Int, side: Int): Boolean {
        var x = x
        var y = y
        var z = z
        if (world.isClientSide) return false
        val descriptor = getDescriptor(stack)
        val direction = fromIntMinecraftSide(side)!!.inverse
        val front = descriptor.getFrontFromPlace(direction, player)
        val v = intArrayOf(descriptor.spawnDeltaX, descriptor.spawnDeltaY, descriptor.spawnDeltaZ)
        front!!.rotateFromXN(v)
        x += v[0]
        y += v[1]
        z += v[2]
        val coord = Coordinate(x, y, z, world)
        val error = descriptor.checkCanPlace(coord, front)
        if (error != null) {
            addChatMessage(player, error)
            return false
        }
        val ghostgroup = descriptor.getGhostGroupFront(front)
        ghostgroup?.plot(coord, coord, descriptor.ghostGroupUuid)
        val node = TransparentNode()
        node.onBlockPlacedBy(coord, front, player, stack)
        world.setBlock(BlockPos(x, y, z), Eln.transparentNodeBlock.stateFor(descriptor.tileEntityMetaTag), 3)
        node.syncLight()
        node.checkCanStay(true)
        return true
    }
}

/** A transparent node in an inventory. */
class TransparentNodeDescriptorItem(private val family: TransparentNodeItem, elnId: Int, descriptor: TransparentNodeDescriptor) :
    BlockDescriptorItem<TransparentNodeDescriptor>(family, elnId, descriptor) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        val place = BlockPlaceContext(context)
        if (!place.canPlace()) return InteractionResult.FAIL
        val pos = place.clickedPos
        val stack = context.itemInHand
        val level = context.level
        if (!player.mayUseItemAt(pos, context.clickedFace, stack)) return InteractionResult.FAIL
        val state = Eln.transparentNodeBlock.stateFor(descriptor.tileEntityMetaTag)
        if (!level.isUnobstructed(state, pos, CollisionContext.of(player))) return InteractionResult.FAIL
        if (level.isClientSide) return InteractionResult.SUCCESS
        if (family.placeBlockAt(stack, player, level, pos.x, pos.y, pos.z, context.clickedFace.get3DDataValue())) {
            val sound = SoundType.METAL
            level.playSound(null, pos, sound.placeSound, SoundSource.BLOCKS, (sound.volume + 1f) / 2f, sound.pitch * 0.8f)
            stack.consume(1, player)
        }
        return InteractionResult.CONSUME
    }

    override fun handleRenderType(item: ItemStack, type: ItemRenderType): Boolean = descriptor.handleRenderType(item, type)

    override fun shouldUseRenderHelper(type: ItemRenderType, item: ItemStack, helper: ItemRendererHelper): Boolean =
        descriptor.shouldUseRenderHelper(type, item, helper)

    override fun renderItem(type: ItemRenderType, item: ItemStack, vararg data: Any) {
        if (descriptor.shouldUseRenderHelperEln(type, item, null)) {
            when (type) {
                ItemRenderType.ENTITY -> GL11.glTranslatef(0.00f, 0.3f, 0.0f)
                ItemRenderType.EQUIPPED_FIRST_PERSON -> GL11.glTranslatef(0.50f, 1f, 0.5f)
                ItemRenderType.EQUIPPED -> GL11.glTranslatef(0.50f, 1f, 0.5f)
                ItemRenderType.INVENTORY -> GL11.glRotatef(90f, 0f, 1f, 0f)
                else -> {
                }
            }
        } else if (type == ItemRenderType.EQUIPPED_FIRST_PERSON && descriptor.shouldUseRenderHelper(type, item, ItemRendererHelper.EQUIPPED_BLOCK)) {
            // These are drawn around the origin, which would be below the edge of the screen.
            GL11.glTranslatef(0.5f, 0.8f, 0.5f)
        }
        descriptor.renderItem(type, item, *data)
    }
}
