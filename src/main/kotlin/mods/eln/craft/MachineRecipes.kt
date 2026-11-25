package mods.eln.craft

import mods.eln.Eln
import mods.eln.compat.OreDict
import mods.eln.i18n.I18N.tr
import mods.eln.misc.RecipesList
import mods.eln.transparentnode.FabricatorDescriptor
import mods.eln.transparentnode.FabricatorOperation
import mods.eln.transparentnode.FabricatorProcess
import net.minecraft.world.item.ItemStack

/** One thing a machine does, for recipe viewers. Each input is a choice of items. */
class MachineRecipe(
    val inputs: List<List<ItemStack>>,
    val outputs: List<ItemStack>,
    /** What is needed but not used up. */
    val tools: List<List<ItemStack>> = emptyList(),
    /** In joules, or 0 if it depends on the machine. */
    val energy: Double = 0.0,
    val chance: Double = 1.0
)

/** Machines that do the same things. */
class MachineKind(
    val id: String,
    val title: String,
    val machines: List<ItemStack>,
    val recipes: List<MachineRecipe>,
    /** Where the recipes come from, for machines with such a list. */
    val list: RecipesList? = null
)

/** Everything machines of the mod can do; crafting table and furnace recipes are found in the recipe manager. */
object MachineRecipes {
    fun all(): List<MachineKind> = with(Eln.instance) {
        listOf(
            kind("macerator", tr("Macerator"), maceratorRecipes),
            kind("plate_machine", tr("Plate Machine"), plateMachineRecipes, listOf(Eln.findItemStack("Rolling Shaft Machine", 1))),
            kind("compressor", tr("Compressor"), compressorRecipes),
            kind("magnetizer", tr("Magnetizer"), magnetiserRecipes),
            kind("arc_furnace", tr("Arc Furnace"), arcFurnaceRecipes),
            fabricator()
        )
    }

    private fun kind(id: String, title: String, list: RecipesList, more: List<ItemStack> = emptyList()): MachineKind {
        val recipes = list.recipes.mapNotNull { recipe ->
            // Those that others always come before have none.
            val inputs = list.getInputs(recipe).ifEmpty { return@mapNotNull null }
            MachineRecipe(listOf(inputs), recipe.output.map { it.copy() }, energy = recipe.energy)
        }
        return MachineKind(id, title, list.machines.map { it.copy() } + more, recipes, list)
    }

    private fun transparentNodes() = Eln.transparentNodeItem.subItemList.toSortedMap().values

    private fun fabricator() = MachineKind(
        "fabricator", tr("Fabricator"),
        transparentNodes().filterIsInstance<FabricatorDescriptor>().map { it.newItemStack(1) },
        FabricatorOperation.entries.map {
            MachineRecipe(
                listOf(listOf(Eln.siliconWafer.newItemStack(1)), listOf(Eln.plateCopper.newItemStack(1))),
                listOf(it.outputItem.copyWithCount(it.perSheet)),
                energy = FabricatorProcess.ENERGY, chance = it.yieldPercentage
            )
        }
    )
}
