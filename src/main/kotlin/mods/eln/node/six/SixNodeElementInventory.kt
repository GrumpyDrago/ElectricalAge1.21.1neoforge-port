package mods.eln.node.six

import mods.eln.compat.*

import mods.eln.misc.INBTTReady
import mods.eln.misc.Utils.readFromNBT
import mods.eln.misc.Utils.writeToNBT
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

class SixNodeElementInventory : IInventory, INBTTReady {
    var sixnodeRender: SixNodeElementRender? = null
    var sixNodeElement: SixNodeElement? = null
    var stackLimit: Int

    constructor(size: Int, stackLimit: Int, sixnodeRender: SixNodeElementRender?) {
        inv = arrayOfNulls(size)
        this.stackLimit = stackLimit
        this.sixnodeRender = sixnodeRender
    }

    constructor(size: Int, stackLimit: Int, sixNodeElement: SixNodeElement?) {
        inv = arrayOfNulls(size)
        this.stackLimit = stackLimit
        this.sixNodeElement = sixNodeElement
    }

    private var inv: Array<ItemStack?>
    override fun getSizeInventory(): Int {
        return inv.size
    }

    override fun getStackInSlot(slot: Int): ItemStack? {
        return if (slot >= inv.size) null else inv[slot]
    }

    override fun decrStackSize(slot: Int, amt: Int): ItemStack? {
        var stack = getStackInSlot(slot)
        if (stack != null) {
            if (stack.count <= amt) {
                setInventorySlotContents(slot, null)
            } else {
                stack = stack.splitStack(amt)
                if (stack.count == 0) {
                    setInventorySlotContents(slot, null)
                }
            }
        }
        return stack
    }

    override fun getStackInSlotOnClosing(slot: Int): ItemStack? {
        val stack = getStackInSlot(slot)
        if (stack != null) {
            setInventorySlotContents(slot, null)
        }
        return stack
    }

    override fun setInventorySlotContents(slot: Int, stack: ItemStack?) {
        try {
            inv[slot] = stack
            if (stack != null && stack.count > inventoryStackLimit) {
                stack.count = inventoryStackLimit
            }
        } catch (e: Exception) {
            // TODO: handle exception
        }
    }

    override fun getInventoryName(): String {
        return "tco.SixNodeInventory"
    }

    override fun getInventoryStackLimit(): Int {
        return stackLimit
    }

    override fun isUseableByPlayer(player: Player): Boolean {
        val element = sixNodeElement ?: return true
        val node = element.sixNode ?: return false
        return node.getElement(element.side) === element && node.isUsableBy(player)
    }

    override fun openInventory() {}
    override fun closeInventory() {}
    override fun markDirty() {
        if (sixNodeElement != null && !sixNodeElement!!.sixNode!!.isDestructing) {
            sixNodeElement!!.inventoryChanged()
        }
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        readFromNBT(nbt, str, this)
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        writeToNBT(nbt, str, this)
    }

    override fun isItemValidForSlot(i: Int, itemstack: ItemStack): Boolean {
        return false
    }

    override fun hasCustomInventoryName(): Boolean {
        return false
    }
}
