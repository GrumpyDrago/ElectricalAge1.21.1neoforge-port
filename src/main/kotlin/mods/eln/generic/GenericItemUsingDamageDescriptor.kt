package mods.eln.generic

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.compat.*

import mods.eln.misc.RealisticEnum
import mods.eln.misc.UtilsClient
import mods.eln.misc.VoltageLevelColor
import net.minecraft.world.level.block.Block
import mods.eln.generic.GenericCreativeTab
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import mods.eln.compat.IItemRenderer.ItemRenderType
import mods.eln.compat.IItemRenderer.ItemRendererHelper

open class GenericItemUsingDamageDescriptor {

    @JvmOverloads
    constructor(name: String, iconName: String = name) {
        setDefaultIcon(iconName)
        this.name = name
        byName[name] = this
    }

    var IconName: String? = null
    open var icon: IIcon? = null
    @JvmField
    var name: String
    @JvmField
    var voltageLevelColor = VoltageLevelColor.None
    @JvmField
    var parentItem: Item? = null
    @JvmField
    var parentItemDamage = 0
    open var creativeTab: GenericCreativeTab? = null
    var hidden: Boolean = false
    fun setDefaultIcon(name: String) {
        IconName = "eln:" + name.replace(" ".toRegex(), "").lowercase()
        icon = IIcon(IconName!!)
    }

    open fun getDefaultNBT(): CompoundTag? = null

    open fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {}

    open fun addRealismContext(list: List<*>?): RealisticEnum? = null

    open fun onItemRightClick(s: ItemStack, w: Level, p: Player): ItemStack {
        return s
    }

    fun getSubItems(list: MutableList<ItemStack>) = list.add(newItemStack(1))


    open fun getName(stack: ItemStack): String? {
        return name
    }

    open fun setParent(item: Item?, damage: Int) {
        parentItem = item
        parentItemDamage = damage
    }


    open fun newItemStack(size: Int): ItemStack {
        val stack = ItemStack(parentItem!!, size)
        stack.tagCompound = getDefaultNBT()
        return stack
    }

    fun newItemStack(): ItemStack {
        return newItemStack(1)
    }

    open fun hideFromCreative(): GenericItemUsingDamageDescriptor {
        hidden = true
        return this
    }

    fun isHidden(): Boolean = hidden

    fun checkSameItemStack(stack: ItemStack?): Boolean {
        if (stack == null) return false
        return stack.item === this.parentItem
    }

    /**
     * Callback for item usage. If the item does something special on right clicking, he will have one of those. Return
     * True if something happen and false if it don't. This is for ITEMS, not BLOCKS
     */
    open fun onItemUse(stack: ItemStack?, player: Player?, world: Level?, x: Int, y: Int, z: Int, side: Int, vx: Float, vy: Float, vz: Float): Boolean {
        return false
    }

    open fun handleRenderType(item: ItemStack?, type: ItemRenderType?): Boolean {
        return voltageLevelColor != VoltageLevelColor.None
    }

    open fun shouldUseRenderHelper(type: ItemRenderType?, item: ItemStack?, helper: ItemRendererHelper?): Boolean {
        return false
    }

    open fun renderItem(type: ItemRenderType?, item: ItemStack?, vararg data: Any?) {
        if (icon == null) return
        voltageLevelColor.drawIconBackground(type!!)
        // remove "eln:" to add the full path replace("eln:", "textures/block/") + ".png";
        val icon = icon!!.iconName.substring(4)
        UtilsClient.drawIcon(type, rl("eln", "textures/item/$icon.png"))
    }

    open fun onUpdate(stack: ItemStack, world: Level, entity: Entity, par4: Int, par5: Boolean) {}
    /** A copy of the stack's data, created if missing. Store changes with updateTag. */
    protected fun getNbt(stack: ItemStack): CompoundTag {
        val nbt = stack.tagCompound
        if (nbt != null) return nbt
        val fresh = getDefaultNBT() ?: CompoundTag()
        stack.tagCompound = fresh
        return fresh
    }

    open fun getStrVsBlock(stack: ItemStack, block: Block?): Float {
        return 0.2f
    }

    open fun onBlockDestroyed(stack: ItemStack, w: Level, block: Block, x: Int, y: Int, z: Int, entity: LivingEntity): Boolean {
        return false
    }

    open fun onDroppedByPlayer(item: ItemStack, player: Player?): Boolean {
        return true
    }

    open fun onEntitySwing(entityLiving: LivingEntity?, stack: ItemStack?): Boolean {
        return false
    }

    companion object {
        var byName = HashMap<String, GenericItemUsingDamageDescriptor>()
        @JvmField
        var INVALID_NAME = "\$NO_DESCRIPTOR"
        @JvmStatic
        fun getByName(name: String?): GenericItemUsingDamageDescriptor? {
            return byName[name]
        }

        @JvmStatic
        fun getDescriptor(stack: ItemStack?): GenericItemUsingDamageDescriptor? {
            if (stack == null) return null
            return (stack.item as? SharedDescriptorItem)?.descriptor
        }

        @JvmStatic
        fun getDescriptor(stack: ItemStack?, extendClass: Class<*>): GenericItemUsingDamageDescriptor? {
            val desc = getDescriptor(stack) ?: return null
            return if (!extendClass.isAssignableFrom(desc.javaClass)) null else desc
        }
    }

}
