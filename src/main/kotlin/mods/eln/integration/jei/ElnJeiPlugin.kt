package mods.eln.integration.jei

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.constants.RecipeTypes
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.builder.IRecipeSlotBuilder
import mezz.jei.api.gui.handlers.IGuiClickableArea
import mezz.jei.api.gui.handlers.IGuiContainerHandler
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.AbstractRecipeCategory
import mezz.jei.api.registration.IGuiHandlerRegistration
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import mods.eln.Eln
import mods.eln.craft.MachineKind
import mods.eln.craft.MachineRecipe
import mods.eln.craft.MachineRecipes
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Utils
import mods.eln.transparentnode.eggincubator.EggIncubatorDescriptor
import mods.eln.transparentnode.electricalfurnace.ElectricalFurnaceDescriptor
import mods.eln.transparentnode.electricalfurnace.ElectricalFurnaceGuiDraw
import mods.eln.transparentnode.electricalmachine.ElectricalMachineGuiDraw
import mods.eln.transparentnode.heatfurnace.HeatFurnaceDescriptor
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import kotlin.math.max

/** Shows in JEI what the machines can do. JEI starts anew, and asks again, whenever the recipes change. */
@JeiPlugin
class ElnJeiPlugin : IModPlugin {
    private var categories = emptyList<MachineCategory>()

    override fun getPluginUid(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(Eln.MODID, "jei")

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        val helper = registration.jeiHelpers.guiHelper
        categories = MachineRecipes.all().filter { it.machines.isNotEmpty() }.map { MachineCategory(it, helper) }
        categories.forEach { registration.addRecipeCategories(it) }
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        for (category in categories) registration.addRecipes(category.recipeType, category.kind.recipes)
        Eln.logger.info("Showing {} machine recipes in JEI", categories.sumOf { it.kind.recipes.size })
        registration.addItemStackInfo(
            machines { it is EggIncubatorDescriptor },
            Component.literal(tr("Hatches eggs. The chicks come out on their own."))
        )
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        for (category in categories) for (machine in category.kind.machines) registration.addRecipeCatalyst(machine, category.recipeType)
        for (machine in machines { it is ElectricalFurnaceDescriptor }) registration.addRecipeCatalyst(machine, RecipeTypes.SMELTING)
        for (machine in machines { it is HeatFurnaceDescriptor }) registration.addRecipeCatalyst(machine, RecipeTypes.FUELING)
    }

    /** The progress arrows of the machines lead to their recipes. */
    override fun registerGuiHandlers(registration: IGuiHandlerRegistration) {
        registration.addGuiContainerHandler(ElectricalMachineGuiDraw::class.java, object : IGuiContainerHandler<ElectricalMachineGuiDraw> {
            override fun getGuiClickableAreas(screen: ElectricalMachineGuiDraw, mouseX: Double, mouseY: Double): Collection<IGuiClickableArea> {
                val category = categories.firstOrNull { it.kind.list != null && it.kind.list === screen.recipes } ?: return emptyList()
                return listOf(IGuiClickableArea.createBasic(67, 11, 22, 16, category.recipeType))
            }
        })
        registration.addRecipeClickArea(ElectricalFurnaceGuiDraw::class.java, 40, 57, 22, 16, RecipeTypes.SMELTING)
    }

    private fun machines(which: (Any) -> Boolean): List<ItemStack> =
        Eln.transparentNodeItem.subItemList.toSortedMap().values.filter(which).map { it.newItemStack(1) }
}

class MachineCategory(val kind: MachineKind, helper: IGuiHelper) : AbstractRecipeCategory<MachineRecipe>(
    RecipeType.create(Eln.MODID, kind.id, MachineRecipe::class.java),
    Component.literal(kind.title), helper.createDrawableItemStack(kind.machines[0]), width(kind), height(kind)
) {
    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: MachineRecipe, focuses: IFocusGroup) {
        val left = left(recipe)
        val needed = recipe.inputs.map { RecipeIngredientRole.INPUT to it } + recipe.tools.map { RecipeIngredientRole.CATALYST to it }
        needed.forEachIndexed { i, (role, choice) ->
            val slot: IRecipeSlotBuilder = builder.addSlot(role, left + i * SLOT + 1, 1).setStandardSlotBackground().addItemStacks(choice)
            if (role == RecipeIngredientRole.CATALYST) {
                slot.addRichTooltipCallback { _, tooltip -> tooltip.add(Component.literal(tr("Not used up"))) }
            }
        }
        val x = left + needed.size * SLOT + ARROW
        recipe.outputs.forEachIndexed { i, stack ->
            builder.addOutputSlot(x + i * SLOT + 1, 1).setStandardSlotBackground().addItemStack(stack)
        }
    }

    override fun createRecipeExtras(builder: IRecipeExtrasBuilder, recipe: MachineRecipe, focuses: IFocusGroup) {
        builder.addRecipeArrow(left(recipe) + (recipe.inputs.size + recipe.tools.size) * SLOT + (ARROW - 24) / 2, 1)
        val text = text(recipe)
        if (text.isNotEmpty()) {
            builder.addText(Component.literal(text), width, 10).setPosition(0, height - 10).alignHorizontalCenter().setColor(0xFF808080.toInt())
        }
    }

    private fun left(recipe: MachineRecipe) = (width - slots(recipe) * SLOT - ARROW) / 2

    companion object {
        private const val SLOT = 18
        private const val ARROW = 32
        private const val MIN_WIDTH = 110

        private fun slots(recipe: MachineRecipe) = recipe.inputs.size + recipe.tools.size + recipe.outputs.size

        private fun text(recipe: MachineRecipe): String = listOfNotNull(
            if (recipe.energy > 0) tr("Energy: %1$", Utils.plotEnergy(recipe.energy).trim()) else null,
            if (recipe.chance < 1) tr("Success: %1$", Utils.plotPercent("", recipe.chance).trim()) else null
        ).joinToString("   ")

        private fun height(kind: MachineKind) = if (kind.recipes.any { text(it).isNotEmpty() }) SLOT + 12 else SLOT

        private fun width(kind: MachineKind): Int {
            val font = Minecraft.getInstance().font
            return kind.recipes.maxOfOrNull { max(slots(it) * SLOT + ARROW, font.width(text(it)) + 4) }?.coerceAtLeast(MIN_WIDTH) ?: MIN_WIDTH
        }
    }
}
