package mods.eln.misc

import mods.eln.GuiHandler
import mods.eln.compat.IInventory
import mods.eln.gui.ISlotSkin.SlotSkin
import mods.eln.gui.SlotWithSkin
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

open class BasicContainer(player: Player, protected var inventory: IInventory, slot: Array<Slot>) :
    AbstractContainerMenu(GuiHandler.menuType, GuiHandler.containerId) {
    init {
        for (s in slot) {
            addSlot(s)
        }
        bindPlayerInventory(player.inventory)
    }

    override fun stillValid(player: Player): Boolean {
        return inventory.isUseableByPlayer(player)
    }

    private fun bindPlayerInventory(inventoryPlayer: Inventory) {
        for (i in 0..2) {
            for (j in 0..8) {
                addSlot(SlotWithSkin(inventoryPlayer, j + i * 9 + 9, j * 18, i * 18, SlotSkin.medium))
            }
        }
        for (i in 0..8) {
            addSlot(SlotWithSkin(inventoryPlayer, i, i * 18, 58, SlotSkin.medium))
        }
    }

    override fun quickMoveStack(player: Player, slotId: Int): ItemStack {
        val slot = slots[slotId]
        if (!slot.hasItem()) return ItemStack.EMPTY
        val stack = slot.item
        val invSize = inventory.sizeInventory
        if (slotId < invSize) {
            moveItemStackTo(stack, invSize, slots.size, true)
        } else if (!moveItemStackTo(stack, 0, invSize, true)) {
            if (slotId < invSize + 27) {
                moveItemStackTo(stack, invSize + 27, slots.size, false)
            } else {
                moveItemStackTo(stack, invSize, invSize + 27, false)
            }
        }
        if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        return ItemStack.EMPTY
    }
}
