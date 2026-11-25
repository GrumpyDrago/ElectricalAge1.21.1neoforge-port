package mods.eln.registration

import mods.eln.Eln
import mods.eln.compat.registryPath
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.RegisterEvent

/**
 * Collects everything the mod registers. Content is created in one pass ([Eln.createContent]) while registries are open,
 * and handed over as each registry's [RegisterEvent] arrives.
 */
object ElnRegistry {
    private class Entry<T : Any>(val registry: ResourceKey<out Registry<T>>, val path: String, val value: T)

    private val entries = ArrayList<Entry<*>>()
    private val itemPaths = HashSet<String>()
    private val blockItems = HashMap<Block, Item>()
    private val namedStacks = LinkedHashMap<String, ItemStack>()

    @JvmStatic
    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(Eln.MODID, path)

    @JvmStatic
    fun <T : Any> register(registry: ResourceKey<out Registry<T>>, path: String, value: T): T {
        entries.add(Entry(registry, path, value))
        return value
    }

    @JvmStatic
    fun <T : Item> registerItem(path: String, item: T): T {
        itemPaths.add(path)
        register(Registries.ITEM, path, item)
        return item
    }

    @JvmStatic
    fun <T : Block> registerBlock(path: String, block: T): T {
        register(Registries.BLOCK, path, block)
        return block
    }

    /** Registers a block together with a plain item for it. */
    @JvmStatic
    fun <T : Block> registerBlockWithItem(path: String, block: T): T {
        registerBlock(path, block)
        blockItems[block] = registerItem(path, BlockItem(block, Item.Properties()))
        return block
    }

    /** The item made by [registerBlockWithItem]. */
    @JvmStatic
    fun itemOf(block: Block): Item = blockItems.getValue(block)

    /** [path], or a variant of it carrying [damage] if another item already took it. */
    @JvmStatic
    fun uniquePath(path: String, damage: Int): String = if (path in itemPaths) "${path}_$damage" else path

    /** Makes a stack available to [findStack] under its English name. */
    @JvmStatic
    fun registerName(name: String, stack: ItemStack) {
        namedStacks[name] = stack
    }

    @JvmStatic
    fun findStack(name: String, count: Int): ItemStack? = namedStacks[name]?.copyWithCount(count)

    @JvmStatic
    val names: Set<String>
        get() = namedStacks.keys

    private fun <T : Any> hand(event: RegisterEvent, entry: Entry<T>) {
        event.register(entry.registry, id(entry.path)) { entry.value }
    }

    fun onRegister(event: RegisterEvent) {
        for (entry in entries) {
            if (entry.registry == event.registryKey) hand(event, entry)
        }
    }

    @JvmStatic
    fun pathFor(name: String): String = registryPath(name)
}
