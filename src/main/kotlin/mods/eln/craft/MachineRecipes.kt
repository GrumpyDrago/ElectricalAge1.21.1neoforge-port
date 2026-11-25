package mods.eln.craft

import mods.eln.Eln
import mods.eln.compat.OreDict
import mods.eln.i18n.I18N.tr
import mods.eln.item.RollerWheelDescriptor
import mods.eln.misc.RecipesList
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableMaterial
import mods.eln.transparentnode.FabricatorDescriptor
import mods.eln.transparentnode.FabricatorOperation
import mods.eln.transparentnode.FabricatorProcess
import mods.eln.transparentnode.WireMachineDescriptor
import mods.eln.transparentnode.WireMachineKind
import mods.eln.transparentnode.WireMachineRules
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil

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
            fabricator(),
            MachineKind("wire_roller", tr("Wire Roller"), wireMachines(WireMachineKind.ROLLER), roller()),
            MachineKind("wire_insulator", tr("Wire Insulator"), wireMachines(WireMachineKind.INSULATOR), insulator()),
            MachineKind("wire_combiner", tr("Wire Combiner"), wireMachines(WireMachineKind.COMBINER), combiner())
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

    private fun wireMachines(kind: WireMachineKind) =
        transparentNodes().filterIsInstance<WireMachineDescriptor>().filter { it.kind == kind }.map { it.newItemStack(1) }

    private fun UtilityCableDescriptor.ofLength(meters: Double) = newItemStack(1).also { setRemainingLengthMeters(it, meters) }

    private fun rubber() = OreDict.getOres(WireMachineRules.RUBBER)

    private fun roller(): List<MachineRecipe> {
        val wheels = Eln.sharedItem.subItemList.toSortedMap().values.filterIsInstance<RollerWheelDescriptor>().map { it.newItemStack(1) }
        val count = ceil(WireMachineRules.INGOTS_PER_WIRE).toInt()
        val ingots = UtilityCableMaterial.entries.associateWith { material ->
            WireMachineRules.ingotNames(material).flatMap(OreDict::getOres).distinctBy { it.item }.map { it.copyWithCount(count) }
        }
        return WireMachineRules.bareWires().mapNotNull { wire ->
            val input = ingots.getValue(wire.material).ifEmpty { return@mapNotNull null }
            MachineRecipe(listOf(input), listOf(wire.ofLength(wire.defaultLengthMeters())), listOf(wheels, wheels))
        }
    }

    private fun insulator(): List<MachineRecipe> {
        val meters = WireMachineRules.METERS_PER_RUBBER
        val rubber = rubber().ifEmpty { return emptyList() }
        val wires = WireMachineRules.bareWires().mapNotNull { bare ->
            val cable = WireMachineRules.insulated(bare) ?: return@mapNotNull null
            MachineRecipe(listOf(listOf(bare.ofLength(meters)), rubber), listOf(cable.ofLength(meters)))
        }
        val bundles = bundles().mapNotNull { (_, cable) ->
            val result = WireMachineRules.insulatedBundle(cable.material, cable.sizeLabel, cable.conductorCount) ?: return@mapNotNull null
            MachineRecipe(listOf(listOf(bundle(cable) ?: return@mapNotNull null), rubber), listOf(result.ofLength(meters)))
        }
        return wires + bundles.distinctBy { it.outputs[0].item }
    }

    private fun combiner(): List<MachineRecipe> = bundles().mapNotNull { (wire, cable) ->
        val input = listOf(wire.ofLength(WireMachineRules.METERS_PER_RUBBER))
        MachineRecipe(List(cable.conductorCount) { input }, listOf(bundle(cable) ?: return@mapNotNull null))
    }

    private fun bundle(cable: UtilityCableDescriptor) =
        Eln.instance.woundWireBundleDescriptor?.let { WireMachineRules.bundle(it, cable, WireMachineRules.METERS_PER_RUBBER) }

    /** The insulated wires that can be combined, and the cables they are combined for. */
    private fun bundles(): List<Pair<UtilityCableDescriptor, UtilityCableDescriptor>> =
        UtilityCableDescriptor.allDescriptors().filter { it.insulated && !it.melted && it.conductorCount == 1 }.flatMap { wire ->
            (2..WireMachineRules.MAX_COMBINED).mapNotNull { count -> WireMachineRules.combined(wire, count).firstOrNull()?.let { wire to it } }
        }
}
