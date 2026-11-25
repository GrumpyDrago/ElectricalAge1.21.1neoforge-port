package mods.eln.datagen

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import mods.eln.Eln
import mods.eln.compat.OreDict
import mods.eln.generic.DescriptorItem
import mods.eln.misc.ElnAssets
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.data.event.GatherDataEvent
import java.nio.file.Path
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.CompletableFuture

/**
 * Writes item models, block states, loot tables and tags for everything the mod registers.
 *
 * Run with `./gradlew runData` after adding items or blocks.
 */
object ElnDataGenerator {
    fun gather(event: GatherDataEvent) {
        event.generator.addProvider(true, DataProvider.Factory { output -> ElnProvider(output) })
    }
}

private class ElnProvider(private val output: PackOutput) : DataProvider {
    private val files = LinkedHashMap<Path, JsonObject>()
    private val missing = ArrayList<String>()
    private val textures = ElnAssets.list("textures", "png")

    override fun getName(): String = "Electrical Age assets and data"

    private fun asset(path: String, json: JsonObject) {
        files[output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve("eln/$path.json")] = json
    }

    private fun data(path: String, json: JsonObject) {
        files[output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve("$path.json")] = json
    }

    private fun obj(vararg entries: Pair<String, Any>): JsonObject {
        val o = JsonObject()
        for ((k, v) in entries) when (v) {
            is String -> o.addProperty(k, v)
            is Number -> o.addProperty(k, v)
            is Boolean -> o.addProperty(k, v)
            is JsonObject -> o.add(k, v)
            is JsonArray -> o.add(k, v)
            else -> throw IllegalArgumentException(k)
        }
        return o
    }

    private fun arr(vararg values: Number): JsonArray = JsonArray().also { a -> values.forEach { a.add(it) } }

    private fun strings(values: Collection<String>): JsonArray = JsonArray().also { a -> values.forEach { a.add(it) } }

    private fun view(rotation: JsonArray, translation: JsonArray, scale: Double): JsonObject =
        obj("rotation" to rotation, "translation" to translation, "scale" to arr(scale, scale, scale))

    private fun texture(folder: String, name: String): String? {
        // Icon names come without the underscores some of the files have.
        fun base(path: String) = path.substringAfterLast('/').removeSuffix(".png")
        fun squash(text: String) = text.replace("_", "").replace("-", "")
        val candidates = textures.filter { it.startsWith("textures/$folder/") }
        val file = candidates.firstOrNull { base(it) == name } ?: candidates.firstOrNull { squash(base(it)) == squash(name) }
        return file?.let { "eln:" + it.removePrefix("textures/").removeSuffix(".png") }
    }

    private fun flatModel(texture: String?, parent: String = "minecraft:item/generated"): JsonObject =
        obj("parent" to parent, "textures" to obj("layer0" to (texture ?: "minecraft:item/barrier")))

    private val customModel: JsonObject
        get() = obj(
            "parent" to "minecraft:builtin/entity",
            "gui_light" to "front",
            // ElnItemRenderer positions the rest itself.
            "display" to obj("fixed" to view(arr(0, 180, 0), arr(0, 0, 0), 0.75))
        )

    private fun itemModels() {
        for (item in BuiltInRegistries.ITEM) {
            val id = BuiltInRegistries.ITEM.getKey(item)
            if (id.namespace != Eln.MODID) continue
            val path = "models/item/${id.path}"
            when {
                item is DescriptorItem && BuiltInRegistries.BLOCK.containsKey(id) -> asset(path, obj("parent" to "eln:block/${id.path}"))
                item is DescriptorItem && item.hasCustomRenderer() -> asset(path, customModel)
                item is DescriptorItem -> {
                    val name = item.iconName.substringAfter(':')
                    val texture = texture(item.iconFolder, name) ?: texture("item", name) ?: texture("block", name)
                    if (texture == null) missing.add("${id.path} ($name)")
                    asset(path, flatModel(texture))
                }
                BuiltInRegistries.BLOCK.containsKey(id) -> asset(path, obj("parent" to "eln:block/${id.path}"))
                id.path.endsWith("_spawn_egg") -> asset(path, obj("parent" to "minecraft:item/template_spawn_egg"))
                else -> {
                    val name = id.path.replace("e_coal", "ecoal")
                    val texture = texture("item", name)
                    if (texture == null) missing.add(id.path)
                    val tool = listOf("sword", "pickaxe", "axe", "shovel", "hoe").any { id.path.endsWith(it) }
                    asset(path, flatModel(texture, if (tool) "minecraft:item/handheld" else "minecraft:item/generated"))
                }
            }
        }
    }

    private fun single(block: String) =
        obj("variants" to obj("" to obj("model" to "eln:block/$block")))

    private fun blocks() {
        for (name in listOf("arc_clay_block", "arc_metal_block", "copper_ore", "lead_ore", "tungsten_ore", "cinnabar_ore")) {
            val texture = texture("block", name) ?: texture("block", name.replace("_", ""))
            if (texture == null) missing.add("block $name")
            asset("blockstates/$name", single(name))
            asset("models/block/$name", obj("parent" to "minecraft:block/cube_all", "textures" to obj("all" to (texture ?: "minecraft:block/stone"))))
        }
        asset("blockstates/energy_converter", single("energy_converter"))
        asset("models/block/energy_converter", obj("parent" to "minecraft:block/cube_all", "textures" to obj("all" to "eln:block/elntoic2lvu_side")))
        asset("blockstates/computer_probe", single("computer_probe"))
        asset(
            "models/block/computer_probe",
            obj(
                "parent" to "minecraft:block/cube",
                "textures" to obj(
                    "west" to "eln:block/computerprobe_xn", "east" to "eln:block/computerprobe_xp",
                    "north" to "eln:block/computerprobe_zn", "south" to "eln:block/computerprobe_zp",
                    "down" to "eln:block/computerprobe_yn", "up" to "eln:block/computerprobe_yp",
                    "particle" to "eln:block/computerprobe_xn"
                )
            )
        )
        // Drawn by block entity renderers, or not at all; the models only provide particles.
        for ((name, particle) in listOf(
            "six_node" to "minecraft:block/iron_block", "transparent_node" to "minecraft:block/iron_block",
            "ghost_block" to "minecraft:block/iron_block", "light_block" to "minecraft:block/iron_block",
            "hot_water" to "minecraft:block/water_still", "cold_water" to "minecraft:block/water_still"
        )) {
            asset("blockstates/$name", single(name))
            asset("models/block/$name", obj("textures" to obj("particle" to particle)))
        }
    }

    private fun drops(block: String, item: String = block) {
        val pool = obj(
            "rolls" to 1.0,
            "bonus_rolls" to 0.0,
            "entries" to JsonArray().also { it.add(obj("type" to "minecraft:item", "name" to "eln:$item")) },
            "conditions" to JsonArray().also { it.add(obj("condition" to "minecraft:survives_explosion")) }
        )
        data(
            "eln/loot_table/blocks/$block",
            obj("type" to "minecraft:block", "pools" to JsonArray().also { it.add(pool) }, "random_sequence" to "eln:blocks/$block")
        )
    }

    private fun tag(registry: String, id: ResourceLocation, values: Collection<String>) {
        data("${id.namespace}/tags/$registry/${id.path}", obj("values" to strings(values)))
    }

    private fun tags() {
        val ores = listOf("copper_ore", "lead_ore", "tungsten_ore", "cinnabar_ore")
        val cubes = ores + listOf("arc_clay_block", "arc_metal_block")
        for (block in cubes + listOf("energy_converter", "computer_probe")) drops(block)

        tag("block", ResourceLocation.withDefaultNamespace("mineable/pickaxe"), cubes.map { "eln:$it" })
        tag("block", ResourceLocation.withDefaultNamespace("needs_stone_tool"), listOf("eln:copper_ore", "eln:lead_ore", "eln:cinnabar_ore"))
        tag("block", ResourceLocation.withDefaultNamespace("needs_iron_tool"), listOf("eln:tungsten_ore"))
        tag("block", c("ores"), ores.map { "eln:$it" })
        for (ore in ores) tag("block", c("ores/" + ore.removeSuffix("_ore")), listOf("eln:$ore"))
        tag("block", c("storage_blocks"), listOf("eln:arc_clay_block", "eln:arc_metal_block"))

        val items = TreeMap<ResourceLocation, TreeSet<String>>()
        for ((name, stacks) in OreDict.registered) {
            val id = OreDict.tagId(name)
            for (stack: ItemStack in stacks) {
                val key = BuiltInRegistries.ITEM.getKey(stack.item).toString()
                items.getOrPut(id) { TreeSet() }.add(key)
                // "c:ingots/copper" is also an ingot.
                if (id.namespace == "c" && '/' in id.path) {
                    items.getOrPut(c(id.path.substringBefore('/'))) { TreeSet() }.add("#$id")
                }
            }
        }
        for ((id, values) in items) tag("item", id, values)

        for ((slot, suffix) in listOf("head" to "helmet", "chest" to "chestplate", "leg" to "leggings", "foot" to "boots")) {
            tag("item", ResourceLocation.withDefaultNamespace(slot + "_armor"), listOf("eln:copper_$suffix", "eln:e_coal_$suffix"))
        }
        for (tool in listOf("sword", "pickaxe", "axe", "shovel", "hoe")) {
            tag("item", ResourceLocation.withDefaultNamespace(tool + "s"), listOf("eln:copper_$tool"))
        }
        // Behaves like water for swimming, drowning and so on.
        val fluids = listOf("eln:hot_water", "eln:flowing_hot_water", "eln:cold_water", "eln:flowing_cold_water")
        tag("fluid", ResourceLocation.withDefaultNamespace("water"), fluids)
    }

    private fun c(path: String) = ResourceLocation.fromNamespaceAndPath("c", path)

    override fun run(cache: CachedOutput): CompletableFuture<*> {
        itemModels()
        blocks()
        tags()
        if (missing.isNotEmpty()) Eln.logger.warn("No texture for: {}", missing.joinToString())
        return CompletableFuture.allOf(*files.map { (path, json) -> DataProvider.saveStable(cache, json, path) }.toTypedArray())
    }
}
