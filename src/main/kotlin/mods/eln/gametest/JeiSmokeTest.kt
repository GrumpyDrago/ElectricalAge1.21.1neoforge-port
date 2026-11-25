package mods.eln.gametest

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.runtime.IJeiRuntime
import mods.eln.Eln
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/** Looks at the recipes in JEI, where that is installed. */
@JeiPlugin
class JeiSmokeTest : IModPlugin {
    override fun getPluginUid(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(Eln.MODID, "smoke_test")

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        runtime = jeiRuntime
    }

    override fun onRuntimeUnavailable() {
        runtime = null
    }

    companion object {
        private var runtime: IJeiRuntime? = null

        /** What a right click in the list of items does. Returns the categories. */
        fun showUses(stack: ItemStack): List<String> = show(stack, RecipeIngredientRole.INPUT, RecipeIngredientRole.CATALYST)

        /** What a left click does. */
        fun showRecipes(stack: ItemStack): List<String> = show(stack, RecipeIngredientRole.OUTPUT)

        private fun show(stack: ItemStack, vararg roles: RecipeIngredientRole): List<String> {
            val jei = runtime ?: return emptyList()
            val focuses = roles.map { jei.jeiHelpers.focusFactory.createFocus(it, VanillaTypes.ITEM_STACK, stack) }
            jei.recipesGui.show(focuses)
            return jei.recipeManager.createRecipeCategoryLookup().limitFocus(focuses).get().toList().map { category ->
                val recipes = jei.recipeManager.createRecipeLookup(category.recipeType).limitFocus(focuses).get().count()
                "${category.recipeType.uid} ($recipes)"
            }
        }

        fun clickAreas(screen: AbstractContainerScreen<*>): Int {
            val helper = runtime?.screenHelper ?: return 0
            return (0 until screen.xSize).sumOf { x -> (0 until screen.ySize).count { y -> helper.getGuiClickableArea(screen, x.toDouble(), y.toDouble()).findAny().isPresent } }
        }
    }
}
