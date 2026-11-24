package mods.eln.node

import mods.eln.compat.*

import net.minecraft.world.item.ItemStack

interface ISixNodeCache {
    fun accept(stack: ItemStack): Boolean
}
