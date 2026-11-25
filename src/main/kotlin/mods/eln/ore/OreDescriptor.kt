package mods.eln.ore

import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.wiki.Data
import net.minecraft.world.item.Item

/** An ore block. Where it generates is defined by the placed features in data/eln/worldgen. */
class OreDescriptor(name: String?, var metadata: Int) : GenericItemBlockUsingDamageDescriptor(name) {
    val block = OreBlock()

    override fun setParent(item: Item, damage: Int) {
        super.setParent(item, damage)
        Data.addOre(newItemStack())
    }
}
