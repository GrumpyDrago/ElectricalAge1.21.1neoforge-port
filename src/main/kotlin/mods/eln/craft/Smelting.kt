package mods.eln.craft

import mods.eln.compat.ClientHooks
import mods.eln.compat.currentServer
import mods.eln.compat.orNull
import mods.eln.compat.registries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.Level

/** Furnace recipes of the running game. */
object Smelting {
    private fun level(): Level? = currentServer?.overworld() ?: ClientHooks.level()
    private fun manager(): RecipeManager? = level()?.recipeManager

    /** What a furnace makes of this, or null. */
    @JvmStatic
    fun result(input: ItemStack?): ItemStack? {
        if (input == null || input.isEmpty) return null
        val level = level() ?: return null
        val holder = level.recipeManager.getRecipeFor(RecipeType.SMELTING, SingleRecipeInput(input), level).orElse(null) ?: return null
        return holder.value.assemble(SingleRecipeInput(input), registries()).orNull()
    }

    /** One (input, output) pair for every furnace recipe. */
    fun all(): List<Pair<ItemStack, ItemStack>> {
        val manager = manager() ?: return emptyList()
        return manager.getAllRecipesFor(RecipeType.SMELTING).mapNotNull { holder ->
            val input = holder.value.ingredients.firstOrNull()?.items?.firstOrNull() ?: return@mapNotNull null
            input to holder.value.getResultItem(registries())
        }
    }
}
