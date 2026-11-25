package mods.eln.node.six

import mods.eln.generic.DescriptorItem
import mods.eln.node.ISixNodeCache
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape

class SixNodeCacheStd : ISixNodeCache {
    /** Plain full cubes can be put over a six node. */
    override fun accept(stack: ItemStack): Boolean {
        if (stack.item is DescriptorItem) return false
        val state = Block.byItem(stack.item).defaultBlockState()
        if (state.isAir || state.hasBlockEntity()) return false
        return state.renderShape == RenderShape.MODEL && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
    }
}
