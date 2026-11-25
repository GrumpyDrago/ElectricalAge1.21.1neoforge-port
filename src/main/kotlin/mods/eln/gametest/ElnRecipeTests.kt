package mods.eln.gametest

import mods.eln.Eln
import mods.eln.craft.MachineRecipes
import mods.eln.integration.CraftableItems
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.SmeltingRecipe
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder(Eln.MODID)
@PrefixGameTestTemplate(false)
object ElnRecipeTests {
    private const val EMPTY = "empty"

    private fun path(item: Item) = BuiltInRegistries.ITEM.getKey(item).path

    /** Puts things into a crafting grid as the recipes say, to see that no other recipe is in the way. */
    @GameTest(template = EMPTY)
    @JvmStatic
    fun craftingGridMakesWhatRecipesSay(helper: GameTestHelper) {
        val level = helper.level
        val failures = ArrayList<String>()
        for (holder in level.recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (holder.id().namespace != Eln.MODID) continue
            val recipe = holder.value()
            val stacks = recipe.ingredients.map { it.items.firstOrNull()?.copy() ?: ItemStack.EMPTY }
            val input = if (recipe is ShapedRecipe) CraftingInput.of(recipe.width, recipe.height, stacks)
            else CraftingInput.of(3, 3, List(9) { stacks.getOrElse(it) { ItemStack.EMPTY } })
            val expected = recipe.getResultItem(level.registryAccess())
            val made = level.recipeManager.getRecipeFor(RecipeType.CRAFTING, input, level)
                .map { it.value().assemble(input, level.registryAccess()) }.orElse(ItemStack.EMPTY)
            if (!ItemStack.isSameItem(expected, made)) failures.add("${holder.id()} makes $made")
        }
        failures.forEach { Eln.logger.error("CONFLICT {}", it) }
        helper.assertTrue(failures.isEmpty(), failures.joinToString())
        helper.succeed()
    }

    /** Recipe viewers only list what is in the creative tabs by themselves. */
    @GameTest(template = EMPTY)
    @JvmStatic
    fun hiddenItemsThatCanBeMadeAreListed(helper: GameTestHelper) {
        val level = helper.level
        val tabs = BuiltInRegistries.CREATIVE_MODE_TAB.filter { BuiltInRegistries.CREATIVE_MODE_TAB.getKey(it)!!.namespace == Eln.MODID }
        val parameters = CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess())
        val shown = tabs.flatMap { it.buildContents(parameters); it.displayItems }
        helper.assertTrue(shown.size > 100, "the creative tabs are empty")
        val more = CraftableItems.notIn(shown, level.recipeManager, level.registryAccess()).map { path(it.item) }
        val cables = listOf("signal", "signal_bus", "low_voltage", "medium_voltage", "high_voltage", "very_high_voltage", "low_current", "medium_current", "high_current")
        val tabbed = shown.map { path(it.item) }
        for (cable in cables) helper.assertTrue((more + tabbed).count { it == "${cable}_cable" } == 1, "the $cable cable is not listed once")
        helper.assertTrue("conduit" !in more, "the conduit is listed")
        Eln.logger.info("LISTED {}", more)
        helper.succeed()
    }

    /** What survival players are not meant to get, or get in other ways than by recipes. */
    private val withoutRecipe = setOf(
        // For creative mode and development.
        "electrical_source", "signal_source", "current_source", "creative_cable", "creative_power_capacitor",
        "creative_power_inductor", "creative_power_resistor", "portable_nan", "unreleasedium", "the_nope_wand",
        "falstad_import_tool", "tutorial_sign", "replicator_spawn_egg",
        // Left over from others, or filled elsewhere.
        "copper_molten_metal_pile", "aluminum_molten_metal_pile", "blown_lead_fuse", "wire_scraps", "data_logger_print",
        "hot_water_bucket", "cold_water_bucket",
        // Not finished, or replaced by something else.
        "removed_from_mod_copper_thermal_cable", "conduit", "signal_20h_inductor", "signal_switch_6211", "signal_switch_with_led",
        "power_inductor_80", "power_capacitor_84", "legacy_dc_dc_converter", "ferrite_ingot", "nibble_memory_chip",
        // More wires than the combiner takes.
        "copper_8c_18_awg_cable_300v", "copper_8c_20_awg_cable_300v",
        // Off unless configured.
        "mqtt_signal_controller", "modbus_rtu",
        // In December only.
        "christmas_tree", "holiday_candle", "string_lights"
    )

    /** Everything else can be made of what is found in the world. */
    @GameTest(template = EMPTY)
    @JvmStatic
    fun everythingCanBeMade(helper: GameTestHelper) {
        class Way(val needs: List<List<Item>>, val makes: List<Item>)

        val ways = ArrayList<Way>()
        for (holder in helper.level.recipeManager.recipes) {
            val recipe = holder.value()
            if (recipe !is CraftingRecipe && recipe !is SmeltingRecipe) continue
            val result = recipe.getResultItem(helper.level.registryAccess())
            if (result.isEmpty) continue
            // Barriers stand in for tags without members.
            ways.add(Way(recipe.ingredients.filter { !it.isEmpty }.map { i -> i.items.map { it.item }.filter { it !== Items.BARRIER } }, listOf(result.item)))
        }
        for (kind in MachineRecipes.all()) for (recipe in kind.recipes) {
            val needs = (recipe.inputs + recipe.tools + listOf(kind.machines)).map { choice -> choice.map { it.item } }
            ways.add(Way(needs, recipe.outputs.map { it.item }))
        }
        val (own, others) = BuiltInRegistries.ITEM.partition { BuiltInRegistries.ITEM.getKey(it).namespace == Eln.MODID }
        val have = others.filter { BuiltInRegistries.ITEM.getKey(it).namespace == "minecraft" }.toHashSet()
        // Tree resin comes from the tree resin collector.
        have.addAll(own.filter { path(it).endsWith("_ore") || path(it) == "tree_resin" })
        do {
            val found = ways.filter { way -> way.needs.all { choice -> choice.any { it in have } } }.flatMap { it.makes }
        } while (have.addAll(found))
        val missing = own.filter { it !in have }.map(::path).filter { it !in withoutRecipe && !it.endsWith("_cable_melted") }
        missing.forEach { Eln.logger.error("UNOBTAINABLE {}", it) }
        helper.assertTrue(missing.isEmpty(), missing.joinToString())
        helper.succeed()
    }
}
