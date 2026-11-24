package mods.eln.item

import net.minecraft.core.Holder
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ArmorMaterial

open class ElnArmorItem(val englishName: String, material: Holder<ArmorMaterial>, type: Type, properties: Properties) :
    ArmorItem(material, type, properties) {
}
