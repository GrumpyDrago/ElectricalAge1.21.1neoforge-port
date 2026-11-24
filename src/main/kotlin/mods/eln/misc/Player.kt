package mods.eln.misc

import mods.eln.compat.*

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.server.level.ServerPlayer

/**
 * Contains utilities for dealing with player entities.
 */

fun Player.totalItemsCarried(stack: ItemStack): Int {
    return inventory.items
        .filter { it.isItemEqual(stack) }
        .sumOf { it.count }
}

fun Player.removeMultipleItems(stack: ItemStack, count: Int) {
    if (Utils.isCreative(this as ServerPlayer)) return
    assert(count <= totalItemsCarried(stack))
    var left = count
    try {
        inventory.items.reversed().forEach { invStack ->
            if (invStack.isItemEqual(stack)) {
                left -= invStack.split(invStack.count.coerceAtMost(left)).count
                if (left == 0) return
            }
        }
    } finally {
        inventory.setChanged()
        inventoryMenu.broadcastChanges()
    }
}
