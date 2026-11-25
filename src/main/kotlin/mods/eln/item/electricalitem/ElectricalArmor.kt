package mods.eln.item.electricalitem

import mods.eln.compat.*
import mods.eln.i18n.I18N.tr
import mods.eln.item.ElnArmorItem
import mods.eln.item.electricalinterface.IItemEnergyBattery
import mods.eln.misc.Utils
import mods.eln.wiki.Data
import net.minecraft.core.Holder
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ArmorMaterial
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.ItemAttributeModifiers
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

/** Armor that protects as long as it has energy, and spends energy instead of wearing out. */
class ElectricalArmor(
    name: String,
    material: Holder<ArmorMaterial>,
    type: Type,
    var energyStorage: Double,
    var chargePower: Double,
    var ratioMax: Double,
    var ratioMaxEnergy: Double,
    var energyPerDamage: Double
) : ElnArmorItem(name, material, type, Properties().durability(100)), IItemEnergyBattery {

    private fun protection(stack: ItemStack): Double = min(1.0, getEnergy(stack) / ratioMaxEnergy) * ratioMax * 20

    override fun getDefaultAttributeModifiers(stack: ItemStack): ItemAttributeModifiers = ItemAttributeModifiers.builder()
        .add(
            Attributes.ARMOR,
            AttributeModifier(
                ResourceLocation.fromNamespaceAndPath("eln", "ecoal_armor." + type.getName()),
                protection(stack), AttributeModifier.Operation.ADD_VALUE
            ),
            EquipmentSlotGroup.bySlot(type.slot)
        ).build()

    override fun <T : LivingEntity> damageItem(stack: ItemStack, amount: Int, entity: T?, onBroken: Consumer<Item>): Int {
        val energy = max(0.0, getEnergy(stack) - amount * energyPerDamage)
        setEnergy(stack, energy)
        Utils.println("armor hit  damage=$amount energy=$energy energyLost=${amount * energyPerDamage}")
        return 0
    }

    override fun isValidRepairItem(stack: ItemStack, repair: ItemStack): Boolean = false

    override fun isBarVisible(stack: ItemStack): Boolean = true

    override fun getBarWidth(stack: ItemStack): Int = (13 * getEnergy(stack) / energyStorage).toInt().coerceIn(0, 13)

    val defaultNBT: CompoundTag
        get() {
            val nbt = CompoundTag()
            nbt.putDouble("energy", 0.0)
            nbt.putBoolean("powerOn", false)
            return nbt
        }

    fun getPowerOn(stack: ItemStack): Boolean = stack.tagCompound?.getBoolean("powerOn") ?: false

    fun setPowerOn(stack: ItemStack, value: Boolean) = stack.updateTag { it.putBoolean("powerOn", value) }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, context, tooltip, flag)
        tooltip.add(Component.literal(tr("Charge power: %1\$W", chargePower.toInt())))
        tooltip.add(
            Component.literal(
                tr("Stored energy: %1\$J (%2$%)", getEnergy(stack), (getEnergy(stack) / energyStorage * 100).toInt())
            )
        )
    }

    override fun getEnergy(stack: ItemStack): Double = stack.tagCompound?.getDouble("energy") ?: 0.0

    override fun setEnergy(stack: ItemStack, value: Double) = stack.updateTag { it.putDouble("energy", value) }

    override fun getEnergyMax(stack: ItemStack): Double = energyStorage

    override fun getChargePower(stack: ItemStack): Double = chargePower

    override fun getDischagePower(stack: ItemStack): Double = 0.0

    override fun getPriority(stack: ItemStack): Int = 0

    override fun electricalItemUpdate(stack: ItemStack, time: Double) {}

    override fun isEnchantable(stack: ItemStack): Boolean = false

    init {
        Data.addPortable(ItemStack(this))
    }
}
