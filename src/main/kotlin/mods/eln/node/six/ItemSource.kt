package mods.eln.node.six

import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

/** A six node element that makes items, which hoppers and pipes can take. */
interface ItemSource {
    /** What could be taken now; not to be changed. */
    fun availableItems(): ItemStack

    /** Takes some of what is available. */
    fun takeItems(count: Int)
}

/** The item sources in a block, one slot for each face of it. */
class ItemSourceHandler(private val node: SixNode) : IItemHandler {
    private fun source(slot: Int) = node.sideElementList.getOrNull(slot) as? ItemSource

    override fun getSlots(): Int = node.sideElementList.size

    override fun getStackInSlot(slot: Int): ItemStack = source(slot)?.availableItems() ?: ItemStack.EMPTY

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        val source = source(slot) ?: return ItemStack.EMPTY
        val available = source.availableItems()
        val count = minOf(amount, available.count, available.maxStackSize)
        if (count <= 0) return ItemStack.EMPTY
        if (!simulate) source.takeItems(count)
        return available.copyWithCount(count)
    }

    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack = stack

    override fun isItemValid(slot: Int, stack: ItemStack): Boolean = false

    override fun getSlotLimit(slot: Int): Int = 64
}
