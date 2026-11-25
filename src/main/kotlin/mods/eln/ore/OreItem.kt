package mods.eln.ore

import mods.eln.compat.IItemRenderer
import mods.eln.compat.registryPath
import mods.eln.generic.BlockDescriptorItem
import mods.eln.generic.DescriptorItem
import mods.eln.generic.GenericItemBlockUsingDamage
import mods.eln.registration.ElnRegistry
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.shapes.CollisionContext

/** The family of ores. */
class OreItem : GenericItemBlockUsingDamage<OreDescriptor>() {
    override fun newItem(damage: Int, descriptor: OreDescriptor): DescriptorItem {
        ElnRegistry.registerBlock(registryPath(descriptor.name), descriptor.block)
        val item = OreDescriptorItem(this, damage, descriptor)
        Item.BY_BLOCK[descriptor.block] = item
        return item
    }
}

class OreDescriptorItem(family: OreItem, elnId: Int, descriptor: OreDescriptor) :
    BlockDescriptorItem<OreDescriptor>(family, elnId, descriptor) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val place = BlockPlaceContext(context)
        if (!place.canPlace()) return InteractionResult.FAIL
        val level = context.level
        val pos = place.clickedPos
        val state = descriptor.block.defaultBlockState()
        val player = context.player
        if (player != null && !player.mayUseItemAt(pos, context.clickedFace, context.itemInHand)) return InteractionResult.FAIL
        if (!level.isUnobstructed(state, pos, CollisionContext.empty())) return InteractionResult.FAIL
        if (!level.setBlock(pos, state, 11)) return InteractionResult.FAIL
        val sound = state.soundType
        level.playSound(player, pos, sound.placeSound, SoundSource.BLOCKS, (sound.volume + 1f) / 2f, sound.pitch * 0.8f)
        context.itemInHand.consume(1, player)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun handleRenderType(item: ItemStack, type: IItemRenderer.ItemRenderType): Boolean = false

    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType, item: ItemStack, helper: IItemRenderer.ItemRendererHelper): Boolean = false

    override fun renderItem(type: IItemRenderer.ItemRenderType, item: ItemStack, vararg data: Any) {}
}
