package mods.eln.compat

import mods.eln.registration.ElnRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block

/**
 * Material names in the style "ingotCopper", mapped to item tags ("c:ingots/copper").
 *
 * The names are used throughout recipes and configuration. The mod's own items are put into the matching tags by the
 * data generator, from what was passed to [registerOre].
 */
object OreDict {
    private val own = LinkedHashMap<String, MutableList<ItemStack>>()

    private val prefixes = listOf(
        "ingot" to "ingots", "dust" to "dusts", "plate" to "plates", "nugget" to "nuggets", "block" to "storage_blocks",
        "ore" to "ores", "raw" to "raw_materials", "gem" to "gems", "crystal" to "gems", "dye" to "dyes"
    )

    private val special = mapOf(
        "logWood" to "minecraft:logs",
        "treeWood" to "minecraft:logs",
        "treeLeaves" to "minecraft:leaves",
        "plankWood" to "minecraft:planks",
        "slabWood" to "minecraft:wooden_slabs",
        "stickWood" to "c:rods/wooden",
        "blockGlass" to "c:glass_blocks",
        "paneGlass" to "c:glass_panes",
        "materialString" to "c:strings",
        "itemRubber" to "c:rubber",
        "materialResin" to "c:resin",
        "quicksilver" to "c:quicksilver",
        "cobblestone" to "c:cobblestones",
        "stone" to "c:stones"
    )

    private fun snake(s: String): String =
        s.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase().replace(Regex("[^a-z0-9_]"), "_")

    fun tagId(name: String): ResourceLocation {
        if (':' in name) return ResourceLocation.parse(name.lowercase())
        special[name]?.let { return ResourceLocation.parse(it) }
        for ((prefix, folder) in prefixes) {
            if (name.startsWith(prefix) && name.length > prefix.length && name[prefix.length].isUpperCase()) {
                return ResourceLocation.fromNamespaceAndPath("c", folder + "/" + snake(name.substring(prefix.length)))
            }
        }
        if (name.contains("Eln")) return ResourceLocation.fromNamespaceAndPath("eln", snake(name))
        return ResourceLocation.fromNamespaceAndPath("c", snake(name))
    }

    @JvmStatic
    fun tag(name: String): TagKey<Item> = TagKey.create(Registries.ITEM, tagId(name))

    @JvmStatic
    fun registerOre(name: String, stack: ItemStack) {
        own.getOrPut(name) { ArrayList() }.add(stack)
    }

    /** For the mod's own blocks; [Block.asItem] must not be called (it caches) until items are registered. */
    @JvmStatic
    fun registerOre(name: String, block: Block) = registerOre(name, ItemStack(ElnRegistry.itemOf(block)))

    /** Everything the mod itself registered, for the tag data generator. */
    val registered: Map<String, List<ItemStack>>
        get() = own

    /** The members of the tag; requires tags to be loaded. */
    @JvmStatic
    fun getOres(name: String): List<ItemStack> {
        val holders = BuiltInRegistries.ITEM.getTag(tag(name))
        if (holders.isEmpty) return own[name] ?: emptyList()
        return holders.get().map { ItemStack(it) }
    }

    @JvmStatic
    fun doesOreNameExist(name: String): Boolean = getOres(name).isNotEmpty()

    /** Kinds of things where those of the same material stand in for each other. Other common tags are too broad. */
    private val materials = setOf(
        "ores", "raw_materials", "ingots", "nuggets", "dusts", "plates", "rods", "gems", "storage_blocks", "sands", "cobblestones"
    )

    /** Whether things with this tag can replace each other in machines, as with the ore dictionary. */
    fun isMaterial(tag: TagKey<Item>): Boolean {
        val id = tag.location()
        return if (id.namespace == "c") '/' in id.path && id.path.substringBefore('/') in materials else id == LOGS
    }

    private val LOGS = ResourceLocation.parse("minecraft:logs")

    /** The item and everything that can replace it in machines. */
    fun likelyInputs(item: Item): Set<Item> {
        val items = linkedSetOf(item)
        for (tag in ItemStack(item).tags.filter(::isMaterial)) {
            for (holder in BuiltInRegistries.ITEM.getTagOrEmpty(tag)) items.add(holder.value())
        }
        return items
    }

    @JvmStatic
    fun matches(stack: ItemStack, name: String): Boolean = stack.`is`(tag(name))
}
