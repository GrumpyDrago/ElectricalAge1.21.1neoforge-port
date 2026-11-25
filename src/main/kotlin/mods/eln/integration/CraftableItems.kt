package mods.eln.integration

import mods.eln.Eln
import mods.eln.craft.MachineRecipes
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeManager

/** What can be made of the mod's items; some of it is in no creative tab. */
object CraftableItems {
    /** In the order of registration. */
    fun of(recipes: RecipeManager, registries: HolderLookup.Provider): List<Item> {
        val made = HashSet<Item>()
        for (holder in recipes.recipes) made.add(holder.value().getResultItem(registries)?.item ?: continue)
        for (kind in MachineRecipes.all()) for (recipe in kind.recipes) recipe.outputs.mapTo(made) { it.item }
        return BuiltInRegistries.ITEM.filter { it in made && BuiltInRegistries.ITEM.getKey(it).namespace == Eln.MODID }
    }

    /** What is missing from a list of everything that was taken from the creative tabs. */
    fun notIn(shown: Collection<ItemStack>, recipes: RecipeManager, registries: HolderLookup.Provider): List<ItemStack> {
        val items = shown.mapTo(HashSet()) { it.item }
        return of(recipes, registries).filter { it !in items }.map { ItemStack(it) }
    }
}
