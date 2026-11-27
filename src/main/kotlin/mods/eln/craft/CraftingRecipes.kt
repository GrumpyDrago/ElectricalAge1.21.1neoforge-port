package mods.eln.craft

import net.minecraft.core.NonNullList
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.DyeItem
import net.minecraft.world.item.crafting.CookingBookCategory
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.ShapedRecipePattern
import net.minecraft.world.item.crafting.ShapelessRecipe
import net.minecraft.world.item.crafting.SmeltingRecipe
import net.minecraft.world.level.ItemLike
import mods.eln.misc.Utils
import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.i18n.I18N
import mods.eln.misc.Recipe
import mods.eln.misc.RecipesList
import mods.eln.misc.Utils.println
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack
import java.util.*
import kotlin.collections.HashSet

object CraftingRecipes {

    private val crafting = ArrayList<RecipeHolder<*>>()
    private var collectCrafting = false
    private val ids = HashSet<String>()

    /** All crafting table and furnace recipes of the mod. */
    fun buildCrafting(): List<RecipeHolder<*>> {
        crafting.clear()
        ids.clear()
        collectCrafting = true
        try {
            itemCrafting()
        } finally {
            collectCrafting = false
        }
        return ArrayList(crafting)
    }

    /** Fills the recipe lists of the machines; tags must be loaded. */
    fun buildMachines() = itemCrafting()

    private fun recipeId(output: ItemStack, kind: String): ResourceLocation {
        val base = kind + "/" + BuiltInRegistries.ITEM.getKey(output.item).path
        var path = base
        var n = 2
        while (!ids.add(path)) path = base + "_" + n++
        return ResourceLocation.fromNamespaceAndPath(Eln.MODID, path)
    }

    private fun ingredient(o: Any): Ingredient = when (o) {
        is String -> Ingredient.of(OreDict.tag(o))
        // An empty ingredient cannot be sent to clients.
        is ItemStack -> if (o.isEmpty) throw IllegalArgumentException("Empty ingredient") else Ingredient.of(o.item)
        is ItemLike -> Ingredient.of(o)
        else -> throw IllegalArgumentException("Not an ingredient: $o")
    }

    private fun addRecipe(output: ItemStack, vararg params: Any) {
        if (!collectCrafting || output.isEmpty) return
        val rows = ArrayList<String>()
        var i = 0
        while (i < params.size && params[i] is String) rows.add(params[i++] as String)
        val key = HashMap<Char, Ingredient>()
        while (i + 1 < params.size) {
            key[params[i] as Char] = ingredient(params[i + 1])
            i += 2
        }
        val width = rows.maxOf { it.length }
        val padded = rows.map { it.padEnd(width) }
        val used = padded.flatMap { it.toList() }.filter { it != ' ' }.toSet()
        val pattern = ShapedRecipePattern.of(key.filterKeys { it in used }, padded)
        crafting.add(RecipeHolder(recipeId(output, "crafting"), ShapedRecipe("", CraftingBookCategory.MISC, pattern, output)))
    }

    private fun addShapelessRecipe(output: ItemStack, vararg params: Any) {
        if (!collectCrafting || output.isEmpty) return
        val ingredients = NonNullList.create<Ingredient>()
        for (p in params) ingredients.add(ingredient(p))
        crafting.add(RecipeHolder(recipeId(output, "crafting"), ShapelessRecipe("", CraftingBookCategory.MISC, output, ingredients)))
    }

    private fun addSmelting(input: ItemStack, output: ItemStack, experience: Float = 0.3f) {
        if (!collectCrafting || output.isEmpty) return
        crafting.add(
            RecipeHolder(
                recipeId(output, "smelting"),
                SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(input.item), output, experience, 200)
            )
        )
    }

    private fun itemCrafting() {
        with(Eln.instance) {
            for (list in listOf(maceratorRecipes, compressorRecipes, plateMachineRecipes, arcFurnaceRecipes, magnetiserRecipes)) {
                list.recipes.clear()
            }
        }

        //
        recipeEnergyConverter()
        recipeComputerProbe()

        recipeArmor()
        recipeTool()

        recipeGround()
        recipeElectricalSource()
        recipeElectricalCable()
        recipeThermalCable()
        recipeLampSocket()
        recipeLampSupply()
        recipePowerSocket()
        recipePassiveComponent()
        recipeSwitch()
        recipeWirelessSignal()
        recipeElectricalRelay()
        recipeElectricalDataLogger()
        recipeElectricalGateSource()
        recipeElectricalBreaker()
        recipeFuses()
        recipeElectricalVuMeter()
        recipeElectricalEnvironmentalSensor()
        recipeElectricalRedstone()
        recipeElectricalGate()
        recipeElectricalAlarm()
        recipeSixNodeCache()
        recipeElectricalSensor()
        recipeThermalSensor()
        recipeSixNodeMisc()


        recipeTurret()
        recipeMachine()
        recipeFabricator()
        recipeRailroad()
        recipeChips()
        recipeTransformer()
        recipeHeatFurnace()
        recipeTurbine()
        recipeBattery()
        recipeElectricalFurnace()
        recipeAutoMiner()
        recipeSolarPanel()

        recipeThermalDissipatorPassiveAndActive()
        recipeElectricalAntenna()
        recipeEggIncubator()
        recipeBatteryCharger()
        recipeTransporter()
        recipeWindTurbine()
        recipeFuelGenerator()

        recipeHeatingCorp()
        recipeRegulatorItem()
        recipeLampItem()
        recipeProtection()
        recipeCombustionChamber()
        recipeFerromagneticCore()
        recipeIngot()
        recipeDust()
        recipeElectricalMotor()
        recipeSolarTracker()
        recipeDynamo()
        recipeWindRotor()
        recipeMeter()
        recipeExpansion()
        recipeElectricalDrill()
        recipeOreScanner()
        recipeMiningPipe()
        recipeTreeResinAndRubber()
        recipeRawCable()
        recipeGraphite()
        recipeMiscItem()
        recipeBatteryItem()
        recipeElectricalTool()
        recipePortableCapacitor()

        recipeFurnace()
        recipeArcFurnace()
        recipeMacerator()
        recipeCompressor()
        recipePlateMachine()
        recipeMagnetizer()
        recipeFuelBurnerItem()
        recipeDisplays()

        recipeECoal()

        recipeGridDevices()
        recipeMaceratorModOres()
        recipeRawMaterials()
        craftBrush()
        val cal: Calendar = Calendar.getInstance()
        val month: Int = cal.get(Calendar.MONTH) + 1
        if(month == 12 ) {
            recipeChristmas()
        }

    }

    private fun findItemStack(name: String): ItemStack {
        return Eln.findItemStack(name, 1)
    }

    private fun firstExistingOre(vararg oreNames: String): String {
        for (oreName in oreNames) {
            if (OreDict.doesOreNameExist(oreName)) {
                return oreName
            }
        }
        return ""
    }

    private fun craftBrush() {
        val emptyStack: ItemStack = findItemStack("White Brush")
        Eln.whiteDesc!!.setLife(emptyStack, 0)
        for (wool in listOf("minecraft:wool")) {
            addShapelessRecipe(emptyStack.copy(), wool, findItemStack("Iron Cable"))
        }
        for (idx in 0..15) {
            val name = Eln.brushSubNames[idx]
            addShapelessRecipe(Eln.findItemStack(name, 1), DyeItem.byColor(DyeColor.byId(15 - idx)), emptyStack.copy())
        }
    }

    private fun recipeGround() {
        addRecipe(findItemStack("Ground Cable"), " C ", " C ", "CCC", 'C', findItemStack("Copper Cable"))
    }

    private fun recipeElectricalSource() {
    }

    private fun recipeElectricalCable() {
        addRecipe(
            Eln.instance.signalCableDescriptor.newItemStack(2),
            "R",
            "C",
            "C",
            'C',
            findItemStack("Iron Cable"),
            'R',
            "itemRubber"
        )
        addRecipe(
            Eln.instance.lowVoltageCableDescriptor.newItemStack(2),  //Low Voltage Cable
            "R", "C", "C", 'C', findItemStack("Copper Cable"), 'R', "itemRubber"
        )
        addRecipe(
            Eln.instance.lowCurrentCableDescriptor.newItemStack(4), "RC ", "   ", "   ", 'C', findItemStack("Copper Cable"),
            'R', "itemRubber"
        )
        addRecipe(
            Eln.instance.meduimVoltageCableDescriptor.newItemStack(2),  //Meduim Voltage Cable (Medium Voltage Cable)
            "R", "C", 'C', Eln.instance.lowVoltageCableDescriptor.newItemStack(1), 'R', "itemRubber"
        )
        addRecipe(
            Eln.instance.mediumCurrentCableDescriptor.newItemStack(4), "RC ", "RC ", "   ", 'C', findItemStack(
                "Copper " +
                        "Cable"
            ), 'R', "itemRubber"
        )
        addRecipe(
            Eln.instance.highVoltageCableDescriptor.newItemStack(2),  //High Voltage Cable
            "R", "C", 'C', Eln.instance.meduimVoltageCableDescriptor.newItemStack(1), 'R', "itemRubber"
        )
        addRecipe(
            Eln.instance.highCurrentCableDescriptor.newItemStack(4), "RC ", "RC ", "RC ", 'C', "ingotCopper", 'R',
            "itemRubber"
        )
        addRecipe(
            Eln.instance.signalCableDescriptor.newItemStack(12),  //Signal Wire
            "RRR", "CCC", "RRR", 'C', ItemStack(Items.IRON_INGOT), 'R', "itemRubber"
        )
        addRecipe(
            Eln.instance.signalBusCableDescriptor.newItemStack(1), "R", "C", 'C', Eln.instance.signalCableDescriptor.newItemStack(1), 'R',
            "itemRubber"
        )
        addRecipe(
            Eln.instance.lowVoltageCableDescriptor.newItemStack(12), "RRR", "CCC", "RRR", 'C', "ingotCopper", 'R',
            "itemRubber"
        )
        addRecipe(
            Eln.instance.veryHighVoltageCableDescriptor.newItemStack(12), "RRR", "CCC", "RRR", 'C', "ingotAlloy", 'R',
            "itemRubber"
        )
    }

    private fun recipeThermalCable() {
        addRecipe(
            Eln.findItemStack("Copper Thermal Cable", 12), "SSS", "CCC", "SSS", 'S',
            ItemStack(Blocks.COBBLESTONE), 'C', "ingotCopper"
        )
        addRecipe(
            Eln.findItemStack("Copper Thermal Cable", 1), "S", "C", 'S', ItemStack(Blocks.COBBLESTONE), 'C',
            findItemStack("Copper Cable")
        )
    }

    private fun recipeLampSocket() {
        addRecipe(
            Eln.findItemStack("Lamp Socket A", 3), "G ", "IG", "G ", 'G', ItemStack(Blocks.GLASS_PANE), 'I',
            findItemStack("Iron Cable")
        )
        addRecipe(
            Eln.findItemStack("Lamp Socket B Projector", 3), " G", "GI", " G", 'G',
            ItemStack(Blocks.GLASS_PANE), 'I', ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            Eln.findItemStack("Street Light", 1), "G", "I", "I", 'G', ItemStack(Blocks.GLASS_PANE), 'I',
            ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            Eln.findItemStack("Robust Lamp Socket", 3), "GIG", 'G', ItemStack(Blocks.GLASS_PANE), 'I',
            ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            Eln.findItemStack("Flat Lamp Socket", 3), "IGI", 'G', ItemStack(Blocks.GLASS_PANE), 'I',
            findItemStack("Iron Cable")
        )
        addRecipe(
            Eln.findItemStack("Simple Lamp Socket", 3), " I ", "GGG", 'G', ItemStack(Blocks.GLASS_PANE), 'I',
            ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            Eln.findItemStack("Fluorescent Lamp Socket", 3), " I ", "G G", 'G', findItemStack("Iron Cable"), 'I',
            ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            Eln.findItemStack("Suspended Lamp Socket", 2), "I", "G", 'G', findItemStack("Robust Lamp Socket"), 'I',
            findItemStack("Iron Cable")
        )
        addRecipe(
            Eln.findItemStack("Long Suspended Lamp Socket", 2), "I", "I", "G", 'G', findItemStack(
                "Robust Lamp " +
                        "Socket"
            ), 'I', findItemStack("Iron Cable")
        )
        addRecipe(
            Eln.findItemStack("Suspended Lamp Socket (No Swing)", 4), "I", "G", 'G', findItemStack(
                "Robust Lamp " +
                        "Socket"
            ), 'I', ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            Eln.findItemStack("Long Suspended Lamp Socket (No Swing)", 4), "I", "I", "G", 'G', findItemStack(
                "Robust Lamp Socket"
            ), 'I', ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            Eln.findItemStack("Sconce Lamp Socket", 2), "GCG", "GIG", 'G', ItemStack(Blocks.GLASS_PANE), 'C',
            "dustCoal", 'I', ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            findItemStack("50V Emergency Lamp"), "cbc", " l ", " g ", 'c', findItemStack("Low Voltage Cable"),
            'b', findItemStack("Portable Battery Pack"), 'l', findItemStack("50V LED Bulb"), 'g',
            ItemStack(Blocks.GLASS_PANE)
        )
        addRecipe(
            findItemStack("200V Emergency Lamp"), "cbc", " l ", " g ", 'c', findItemStack(
                "Medium Voltage " +
                        "Cable"
            ), 'b', findItemStack("Portable Battery Pack"), 'l', findItemStack("200V LED Bulb"), 'g',
            ItemStack(Blocks.GLASS_PANE)
        )
        // Floodlights
        addRecipe(
            findItemStack("Basic Floodlight"), " PG", "MBP", "ICI",
            'P', findItemStack("Iron Plate"), 'G', ItemStack(Blocks.GLASS_PANE), 'M', findItemStack("Electrical Motor"),
            'B', findItemStack("Machine Block"), 'I', ItemStack(Items.IRON_INGOT), 'C', findItemStack("Medium Voltage Cable")
        )
        addRecipe(
            findItemStack("Motorized Floodlight"), " PG", "MBP", "ICI",
            'P', findItemStack("Iron Plate"), 'G', ItemStack(Blocks.GLASS_PANE), 'M', findItemStack("Advanced Electrical Motor"),
            'B', findItemStack("Advanced Machine Block"), 'I', ItemStack(Items.IRON_INGOT), 'C', findItemStack("Medium Voltage Cable")
        )
    }

    private fun recipeLampSupply() {
        addRecipe(
            Eln.findItemStack("Lamp Supply", 1), " I ", "ICI", " I ", 'C', "ingotCopper", 'I',
            ItemStack(Items.IRON_INGOT)
        )
    }

    private fun recipePowerSocket() {
        addRecipe(
            Eln.findItemStack("Type J Socket", 16), "RUR", "ACA", 'R', "itemRubber", 'U', findItemStack(
                "Copper " +
                        "Plate"
            ), 'A', findItemStack("Alloy Plate"), 'C', findItemStack("Low Voltage Cable")
        )
        addRecipe(
            Eln.findItemStack("Type E Socket", 16), "RUR", "ACA", 'R', "itemRubber", 'U', findItemStack(
                "Copper" +
                        " Plate"
            ), 'A', findItemStack("Alloy Plate"), 'C', findItemStack("Medium Voltage Cable")
        )
    }

    private fun recipePassiveComponent() {
        addRecipe(
            Eln.findItemStack("Signal Diode", 4), " RB", " IR", " RB", 'R', ItemStack(Items.REDSTONE), 'I',
            findItemStack("Iron Cable"), 'B', "itemRubber"
        )
        addRecipe(
            Eln.findItemStack("10A Diode", 3), " RB", "IIR", " RB", 'R', ItemStack(Items.REDSTONE), 'I',
            findItemStack("Iron Cable"), 'B', "itemRubber"
        )
        addRecipe(findItemStack("25A Diode"), "D", "D", "D", 'D', findItemStack("10A Diode"))
        addRecipe(
            findItemStack("Power Capacitor"), "cPc", "III", 'I', ItemStack(Items.IRON_INGOT), 'c',
            findItemStack("Iron Cable"), 'P', "plateIron"
        )
        addRecipe(
            findItemStack("Power Inductor"), "   ", "cIc", "   ", 'I', ItemStack(Items.IRON_INGOT), 'c',
            findItemStack("Copper Cable")
        )
        addRecipe(
            findItemStack("Variable inductor"), " S ", "cIc", " c ", 'I', ItemStack(Items.IRON_INGOT), 'c',
            findItemStack("Copper Cable"), 'S', findItemStack("Signal Cable")
        )
        addRecipe(
            findItemStack("Power Resistor"), "   ", "cCc", "   ", 'c', findItemStack("Copper Cable"), 'C',
            findItemStack("Coal Dust")
        )
        addRecipe(
            findItemStack("Thermistor"), "   ", "cCc", "   ", 'c', findItemStack("Copper Cable"), 'C',
            findItemStack("Copper Ingot")
        )
        addRecipe(
            findItemStack("Rheostat"), " R ", " MS", "cmc", 'R', findItemStack("Power Resistor"), 'c',
            findItemStack("Copper Cable"), 'm', findItemStack("Machine Block"), 'M', findItemStack("Electrical Motor"),
            'S', findItemStack("Signal Cable")
        )
        addRecipe(
            findItemStack("NTC Thermistor"), "   ", "csc", "   ", 's', "dustSilicon", 'c', findItemStack(
                "Copper Cable"
            )
        )
        addRecipe(
            findItemStack("Large Rheostat"), "   ", " D ", "CRC", 'R', findItemStack("Rheostat"), 'C',
            findItemStack("Copper Thermal Cable"), 'D', findItemStack("Small Passive Thermal Dissipator")
        )
    }

    private fun recipeSwitch() {
        addRecipe(
            findItemStack("Low Voltage Switch"), "  I", " I ", "CAC", 'R', ItemStack(Items.REDSTONE), 'A',
            "itemRubber", 'I', findItemStack("Copper Cable"), 'C', findItemStack("Low Voltage Cable")
        )
        addRecipe(
            findItemStack("Medium Voltage Switch"), "  I", "AIA", "CAC", 'R', ItemStack(Items.REDSTONE),
            'A', "itemRubber", 'I', findItemStack("Copper Cable"), 'C', findItemStack("Medium Voltage Cable")
        )
        addRecipe(
            findItemStack("High Voltage Switch"), "AAI", "AIA", "CAC", 'R', ItemStack(Items.REDSTONE), 'A',
            "itemRubber", 'I', findItemStack("Copper Cable"), 'C', findItemStack("High Voltage Cable")
        )
        addRecipe(
            findItemStack("Very High Voltage Switch"), "AAI", "AIA", "CAC", 'R', ItemStack(Items.REDSTONE),
            'A', "itemRubber", 'I', findItemStack("Copper Cable"), 'C', findItemStack("Very High Voltage Cable")
        )
    }

    private fun recipeElectricalRelay() {
        addRecipe(
            findItemStack("Low Voltage Relay"), "GGG", "OIO", "CRC", 'R', ItemStack(Items.REDSTONE), 'O',
            findItemStack("Iron Cable"), 'G', ItemStack(Blocks.GLASS_PANE), 'A', "itemRubber", 'I', findItemStack(
                "Copper Cable"
            ), 'C', findItemStack("Low Voltage Cable")
        )
        addRecipe(
            findItemStack("Medium Voltage Relay"), "GGG", "OIO", "CRC", 'R', ItemStack(Items.REDSTONE), 'O',
            findItemStack("Iron Cable"), 'G', ItemStack(Blocks.GLASS_PANE), 'A', "itemRubber", 'I', findItemStack(
                "Copper Cable"
            ), 'C', findItemStack("Medium Voltage Cable")
        )
        addRecipe(
            findItemStack("High Voltage Relay"), "GGG", "OIO", "CRC", 'R', ItemStack(Items.REDSTONE), 'O',
            findItemStack("Iron Cable"), 'G', ItemStack(Blocks.GLASS_PANE), 'A', "itemRubber", 'I', findItemStack(
                "Copper Cable"
            ), 'C', findItemStack("High Voltage Cable")
        )
        addRecipe(
            findItemStack("Very High Voltage Relay"), "GGG", "OIO", "CRC", 'R', ItemStack(Items.REDSTONE),
            'O', findItemStack("Iron Cable"), 'G', ItemStack(Blocks.GLASS_PANE), 'A', "itemRubber", 'I',
            findItemStack("Copper Cable"), 'C', findItemStack("Very High Voltage Cable")
        )
        addRecipe(
            findItemStack("Signal Relay"), "GGG", "OIO", "CRC", 'R', ItemStack(Items.REDSTONE), 'O',
            findItemStack("Iron Cable"), 'G', ItemStack(Blocks.GLASS_PANE), 'I', findItemStack("Copper Cable"), 'C',
            findItemStack("Signal Cable")
        )
        addRecipe(
            findItemStack("Low Current Relay"), "GGG", "OIO", "CRC", 'R', ItemStack(Items.REDSTONE), 'O',
            findItemStack("Iron Cable"), 'G', ItemStack(Blocks.GLASS_PANE), 'A', "itemRubber", 'I', findItemStack(
                "Copper Cable"
            ), 'C', findItemStack("Low Current Cable")
        )
        addRecipe(
            findItemStack("Medium Current Relay"), "GGG", "OIO", "CRC", 'R', ItemStack(Items.REDSTONE), 'O',
            findItemStack("Iron Cable"), 'G', ItemStack(Blocks.GLASS_PANE), 'A', "itemRubber", 'I', findItemStack(
                "Copper Cable"
            ), 'C', findItemStack("Medium Current Cable")
        )
        addRecipe(
            findItemStack("High Current Relay"), "GGG", "OIO", "CRC", 'R', ItemStack(Items.REDSTONE), 'O',
            findItemStack("Iron Cable"), 'G', ItemStack(Blocks.GLASS_PANE), 'A', "itemRubber", 'I', findItemStack(
                "Copper Cable"
            ), 'C', findItemStack("High Current Cable")
        )
    }

    private fun recipeWirelessSignal() {
        addRecipe(
            findItemStack("Wireless Signal Transmitter"), " S ", " R ", "ICI", 'R',
            ItemStack(Items.REDSTONE), 'I', findItemStack("Iron Cable"), 'C', Eln.dictCheapChip, 'S', findItemStack(
                "Signal Antenna"
            )
        )
        addRecipe(
            findItemStack("Wireless Signal Repeater"), "S S", "R R", "ICI", 'R', ItemStack(Items.REDSTONE),
            'I', findItemStack("Iron Cable"), 'C', Eln.dictCheapChip, 'S', findItemStack("Signal Antenna")
        )
        addRecipe(
            findItemStack("Wireless Signal Receiver"), " S ", "ICI", 'R', ItemStack(Items.REDSTONE), 'I',
            findItemStack("Iron Cable"), 'C', Eln.dictCheapChip, 'S', findItemStack("Signal Antenna")
        )
    }

    private fun recipeChips() {
        addRecipe(
            findItemStack("NOT Chip"), "   ", "cCr", "   ", 'C', Eln.dictCheapChip, 'r',
            ItemStack(Items.REDSTONE), 'c', findItemStack("Copper Cable")
        )
        addRecipe(
            findItemStack("AND Chip"), " c ", "cCc", " c ", 'C', Eln.dictCheapChip, 'c', findItemStack(
                "Copper " +
                        "Cable"
            )
        )
        addRecipe(
            findItemStack("NAND Chip"), " c ", "cCr", " c ", 'C', Eln.dictCheapChip, 'r',
            ItemStack(Items.REDSTONE), 'c', findItemStack("Copper Cable")
        )
        addRecipe(
            findItemStack("OR Chip"), " r ", "rCr", " r ", 'C', Eln.dictCheapChip, 'r',
            ItemStack(Items.REDSTONE)
        )
        addRecipe(
            findItemStack("NOR Chip"), " r ", "rCc", " r ", 'C', Eln.dictCheapChip, 'r',
            ItemStack(Items.REDSTONE), 'c', findItemStack("Copper Cable")
        )
        addRecipe(
            findItemStack("XOR Chip"), " rr", "rCr", " rr", 'C', Eln.dictCheapChip, 'r',
            ItemStack(Items.REDSTONE)
        )
        addRecipe(
            findItemStack("XNOR Chip"), " rr", "rCc", " rr", 'C', Eln.dictCheapChip, 'r',
            ItemStack(Items.REDSTONE), 'c', findItemStack("Copper Cable")
        )
        addRecipe(
            findItemStack("PAL Chip"), "rcr", "cCc", "rcr", 'C', Eln.dictAdvancedChip, 'r',
            ItemStack(Items.REDSTONE), 'c', findItemStack("Copper Cable")
        )
        addRecipe(
            findItemStack("Schmitt Trigger Chip"), "   ", "cCc", "   ", 'C', Eln.dictAdvancedChip, 'c',
            findItemStack("Copper Cable")
        )
        addRecipe(
            findItemStack("D Flip Flop Chip"), "   ", "cCc", " p ", 'C', Eln.dictAdvancedChip, 'p', findItemStack(
                "Copper Plate"
            ), 'c', findItemStack("Copper Cable")
        )
        addRecipe(
            findItemStack("Oscillator Chip"), "pdp", "cCc", "   ", 'C', Eln.dictAdvancedChip, 'p', findItemStack(
                "Copper Plate"
            ), 'c', findItemStack("Copper Cable"), 'd', findItemStack("Dielectric")
        )
        addRecipe(
            findItemStack("JK Flip Flop Chip"), " p ", "cCc", " p ", 'C', Eln.dictAdvancedChip, 'p', findItemStack(
                "Copper Plate"
            ), 'c', findItemStack("Copper Cable")
        )
        addRecipe(
            findItemStack("Amplifier"), "  r", "cCc", "   ", 'r', ItemStack(Items.REDSTONE), 'c',
            findItemStack("Copper Cable"), 'C', Eln.dictAdvancedChip
        )
        addRecipe(
            findItemStack("Voltage controlled amplifier"), " sr", "cCc", "   ", 'r', ItemStack(Items.REDSTONE), 'c',
            findItemStack("Copper Cable"), 'C', Eln.dictAdvancedChip, 's', findItemStack("Signal Cable")
        )
        addRecipe(
            findItemStack("OpAmp"), "  r", "cCc", " c ", 'r', ItemStack(Items.REDSTONE), 'c',
            findItemStack("Copper Cable"), 'C', Eln.dictAdvancedChip
        )
        addRecipe(
            findItemStack("Configurable summing unit"), " cr", "cCc", " c ", 'r', ItemStack(Items.REDSTONE),
            'c', findItemStack("Copper Cable"), 'C', Eln.dictAdvancedChip
        )
        addRecipe(
            findItemStack("Sample and hold"), " rr", "cCc", " c ", 'r', ItemStack(Items.REDSTONE), 'c',
            findItemStack("Copper Cable"), 'C', Eln.dictAdvancedChip
        )
        addRecipe(
            findItemStack("Voltage controlled sine oscillator"), "rrr", "cCc", "   ", 'r',
            ItemStack(Items.REDSTONE), 'c', findItemStack("Copper Cable"), 'C', Eln.dictAdvancedChip
        )
        addRecipe(
            findItemStack("Voltage controlled sawtooth oscillator"), "   ", "cCc", "rrr", 'r',
            ItemStack(Items.REDSTONE), 'c', findItemStack("Copper Cable"), 'C', Eln.dictAdvancedChip
        )
        addRecipe(
            findItemStack("PID Regulator"), "rrr", "cCc", "rcr", 'r', ItemStack(Items.REDSTONE), 'c',
            findItemStack("Copper Cable"), 'C', Eln.dictAdvancedChip
        )
        addRecipe(
            findItemStack("Lowpass filter"), "CdC", "cDc", " s ", 'd', findItemStack("Dielectric"), 'c',
            findItemStack("Copper Cable"), 'C', findItemStack("Copper Plate"), 'D', findItemStack("Coal Dust"), 's',
            Eln.dictCheapChip
        )
    }

    private fun recipeTransformer() {
        addRecipe(
            findItemStack("DC-DC Converter"), "C C", "III", 'C', findItemStack("Copper Cable"), 'I',
            ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            findItemStack("Variable DC-DC Converter"), "CBC", "III", 'C', findItemStack("Copper Cable"), 'I',
            ItemStack(Items.IRON_INGOT), 'B', Eln.dictCheapChip
        )
    }

    private fun recipeHeatFurnace() {
        addRecipe(
            findItemStack("Stone Heat Furnace"), "BBB", "BIB", "BiB", 'B', ItemStack(Blocks.STONE), 'i',
            findItemStack("Copper Thermal Cable"), 'I', findItemStack("Combustion Chamber")
        )
        addRecipe(
            findItemStack("Fuel Heat Furnace"), "IcI", "mCI", "IiI", 'c', findItemStack("Cheap Chip"), 'm',
            findItemStack("Electrical Motor"), 'C', ItemStack(Items.CAULDRON), 'I', ItemStack(Items.IRON_INGOT),
            'i', findItemStack("Copper Thermal Cable")
        )
    }

    private fun recipeTurbine() {
        addRecipe(
            findItemStack("50V Turbine"), " m ", "HMH", " E ", 'M', findItemStack("Machine Block"), 'E',
            findItemStack("Low Voltage Cable"), 'H', findItemStack("Copper Thermal Cable"), 'm', findItemStack(
                "Electrical Motor"
            )
        )
        addRecipe(
            findItemStack("200V Turbine"), "ImI", "HMH", "IEI", 'I', "itemRubber", 'M', findItemStack(
                "Advanced" +
                        " Machine Block"
            ), 'E', findItemStack("Medium Voltage Cable"), 'H', findItemStack("Copper Thermal Cable"),
            'm', findItemStack("Advanced Electrical Motor")
        )
        addRecipe(
            findItemStack("Generator"), "mmm", "ama", " ME", 'm', findItemStack("Advanced Electrical Motor"),
            'M', findItemStack("Advanced Machine Block"), 'a', firstExistingOre("ingotAluminum", "ingotIron"), 'E',
            findItemStack("High Voltage Cable")
        )
        addRecipe(
            findItemStack("Shaft Motor"), "imi", " ME", 'i', "ingotIron", 'M', findItemStack(
                "Advanced Machine " +
                        "Block"
            ), 'm', findItemStack("Advanced Electrical Motor"), 'E', findItemStack("Very High Voltage Cable")
        )
        addShapelessRecipe(findItemStack("Polarized Shaft Generator"), findItemStack("Generator"), findItemStack("High Voltage Cable"))
        addShapelessRecipe(findItemStack("Polarized Shaft Motor"), findItemStack("Shaft Motor"), findItemStack("High Voltage Cable"))
        addRecipe(
            findItemStack("Steam Turbine"), " a ", "aAa", " M ", 'a', firstExistingOre(
                "ingotAluminum",
                "ingotIron"
            ), 'A', firstExistingOre("blockAluminum", "blockIron"), 'M', findItemStack(
                "Advanced Machine " +
                        "Block"
            )
        )
        addRecipe(
            findItemStack("Gas Turbine"), "msH", "sSs", " M ", 'm', findItemStack("Advanced Electrical Motor"),
            'H', findItemStack("Copper Thermal Cable"), 's', firstExistingOre("ingotSteel", "ingotIron"), 'S',
            firstExistingOre("blockSteel", "blockIron"), 'M', findItemStack("Advanced Machine Block")
        )
        addRecipe(
            findItemStack("Radial Motor"), " r ", "rSr", " rM", 'r', "plateAlloy", 'S', firstExistingOre(
                "blockSteel", "blockIron"
            ), 'M', findItemStack("Advanced Machine Block")
        )
        addRecipe(
            findItemStack("Large Shaft Motor"),
            "CHC",
            "RWR",
            "CMC",
            'C', findItemStack("Motor Cowling"),
            'H', findItemStack("High Current Cable"),
            'R', findItemStack("Reinforced Shaft"),
            'W', findItemStack("Heavy Winding"),
            'M', findItemStack("Advanced Machine Block")
        )
        addRecipe(
            findItemStack("Large Generator"),
            "CMC",
            "RWR",
            "CHC",
            'C', findItemStack("Motor Cowling"),
            'H', findItemStack("High Current Cable"),
            'R', findItemStack("Reinforced Shaft"),
            'W', findItemStack("Heavy Winding"),
            'M', findItemStack("Advanced Machine Block")
        )
        addRecipe(
            findItemStack("Large Gas Turbine"),
            "CTC",
            "RHR",
            "MAM",
            'C', findItemStack("Motor Cowling"),
            'T', findItemStack("Turbine Blade Set"),
            'R', findItemStack("Reinforced Shaft"),
            'H', findItemStack("Heavy Winding"),
            'M', findItemStack("Copper Thermal Cable"),
            'A', findItemStack("Advanced Machine Block")
        )
        addRecipe(
            findItemStack("Large Steam Turbine"),
            "TTT",
            "RMR",
            "SAS",
            'T', findItemStack("Turbine Blade Set"),
            'R', findItemStack("Reinforced Shaft"),
            'M', findItemStack("Copper Thermal Cable"),
            'S', firstExistingOre("ingotAluminum", "ingotSteel", "ingotIron"),
            'A', findItemStack("Advanced Machine Block")
        )
        addRecipe(findItemStack("Joint"), "   ", "iii", " m ", 'i', "ingotIron", 'm', findItemStack("Machine Block"))
        addRecipe(findItemStack("Crank Shaft"), "  i", "iii", " m ", 'i', "ingotIron", 'm', findItemStack("Machine Block"))
        addRecipe(
            findItemStack("Joint hub"), " i ", "iii", " m ", 'i', "ingotIron", 'm', findItemStack(
                "Machine " +
                        "Block"
            )
        )
        addRecipe(
            findItemStack("Flywheel"), "PPP", "PmP", "PPP", 'P', "ingotLead", 'm', findItemStack(
                "Machine " +
                        "Block"
            )
        )
        addRecipe(
            findItemStack("Tachometer"), "p  ", "iii", "cm ", 'i', "ingotIron", 'm', findItemStack(
                "Machine " +
                        "Block"
            ), 'p', findItemStack("Electrical Probe Chip"), 'c', findItemStack("Signal Cable")
        )
        addRecipe(
            findItemStack("Clutch"), "iIi", " c ", 'i', "ingotIron", 'I', "plateIron", 'c', findItemStack(
                "Machine Block"
            )
        )
        addRecipe(
            findItemStack("Fixed Shaft"), "iBi", " c ", 'i', "ingotIron", 'B', "blockIron", 'c', findItemStack(
                "Machine Block"
            )
        )
        addRecipe(
            findItemStack("Rolling Shaft Machine"), "IiI", "IcI", "IiI", 'i', "ingotIron", 'I', "plateIron", 'c', findItemStack(
                "Machine Block")
        )
    }

    private fun recipeBattery() {
        addRecipe(
            findItemStack("Cost Oriented Battery"), "C C", "PPP", "PPP", 'C',
            findItemStack("Low Voltage Cable"), 'P', "ingotLead", 'I', ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            findItemStack("Capacity Oriented Battery"), "PBP", 'B', findItemStack("Cost Oriented Battery"), 'P',
            "ingotLead"
        )
        addRecipe(
            findItemStack("Voltage Oriented Battery"), "PBP", 'B', findItemStack("Cost Oriented Battery"), 'P',
            findItemStack("Iron Cable")
        )
        addRecipe(
            findItemStack("Current Oriented Battery"), "PBP", 'B', findItemStack("Cost Oriented Battery"), 'P',
            "ingotCopper"
        )
        addRecipe(
            findItemStack("Life Oriented Battery"), "PBP", 'B', findItemStack("Cost Oriented Battery"), 'P',
            ItemStack(Items.GOLD_INGOT)
        )
        addRecipe(
            findItemStack("Experimental Battery"), " S ", "LDV", " C ", 'S', findItemStack(
                "Capacity Oriented " +
                        "Battery"
            ), 'L', findItemStack("Life Oriented Battery"), 'V', findItemStack("Voltage Oriented Battery"), 'C',
            findItemStack("Current Oriented Battery"), 'D', ItemStack(Items.DIAMOND)
        )
        addRecipe(
            findItemStack("Single-use Battery"), "ppp", "III", "ppp", 'C', findItemStack("Low Voltage Cable"),
            'p', ItemStack(Items.COAL), 'I', "ingotCopper"
        )
        addRecipe(
            findItemStack("Single-use Battery"), "ppp", "III", "ppp", 'C', findItemStack("Low Voltage Cable"),
            'p', ItemStack(Items.CHARCOAL), 'I', "ingotCopper"
        )
    }

    private fun recipeGridDevices() {
        for (oreName in arrayOf<String>("ingotAluminum", "ingotSteel")) {
            run {
                addRecipe(findItemStack("Utility Pole"), "WWW", "IWI", " W ", 'W', "logWood", 'I', oreName)
                addRecipe(findItemStack("Direct Utility Pole"), "WWW", "IWI", " WI", 'W', "logWood", 'I', oreName)
            }
        }
        // Really?
            addRecipe(findItemStack("Utility Pole"), "WWW", "IWI", " W ", 'I', "ingotIron", 'W', "logWood")
        addRecipe(
            findItemStack("Utility Pole w/DC-DC Converter"), "HHH", " TC", " PH", 'P', findItemStack(
                "Utility " +
                        "Pole"
            ), 'H', findItemStack("High Voltage Cable"), 'C', findItemStack("Optimal Ferromagnetic Core"), 'T',
            findItemStack("DC-DC Converter")
        )

        // I don't care what you think, if your modpack lacks steel then you don't *need* this much power.
        // Or just use the new Arc furnace. Other mod's steel methods are slow and tedious and require huge multiblocks.
        // Feel free to add alternate non-iron recipes, though. Here, or by minetweaker.
        for (type in arrayOf<String>("Aluminum", "Steel")) {
            val blockType = "block$type"
            val ingotType = "ingot$type"
            run {
                addRecipe(
                    findItemStack("Transmission Tower"), "ii ", "mi ", " B ", 'i', ingotType, 'B', blockType,
                    'm', findItemStack("Machine Block")
                )
                addRecipe(
                    findItemStack("Grid DC-DC Converter"), "i i", "mtm", "imi", 'i', ingotType, 't',
                    findItemStack("DC-DC Converter"), 'm', findItemStack("Advanced Machine Block")
                )
                addRecipe(
                    findItemStack("Grid Switch"), "AGA", "MRM", "AGA", 'A', ingotType, 'G', findItemStack(
                        "Gold" +
                                " Plate"
                    ), 'M', findItemStack("Advanced Electrical Motor"), 'R', findItemStack("Rubber")
                )
            }
        }
    }

    private fun recipeElectricalFurnace() {
        addRecipe(
            findItemStack("Electrical Furnace"), "III", "IFI", "ICI", 'C', findItemStack("Low Voltage Cable"),
            'F', ItemStack(Blocks.FURNACE), 'I', ItemStack(Items.IRON_INGOT)
        )
        addShapelessRecipe(
            Eln.findItemStack("Canister of Water", 1), findItemStack("Inert Canister"),
            ItemStack(Items.WATER_BUCKET)
        )
    }

    private fun recipeSixNodeMisc() {
        addRecipe(
            findItemStack("Analog Watch"), "crc", "III", 'c', findItemStack("Iron Cable"), 'r',
            ItemStack(Items.REDSTONE), 'I', findItemStack("Iron Cable")
        )
        addRecipe(
            findItemStack("Digital Watch"), "rcr", "III", 'c', findItemStack("Iron Cable"), 'r',
            ItemStack(Items.REDSTONE), 'I', findItemStack("Iron Cable")
        )
        addRecipe(
            findItemStack("Hub"), "I I", " c ", "I I", 'c', findItemStack("Copper Cable"), 'I', findItemStack(
                "Iron Cable"
            )
        )
        addRecipe(
            findItemStack("Energy Meter"), "IcI", "IRI", "IcI", 'c', findItemStack("Copper Cable"), 'R',
            Eln.dictCheapChip, 'I', findItemStack("Iron Cable")
        )
        addRecipe(
            findItemStack("Advanced Energy Meter"), " c ", "PRP", " c ", 'c', findItemStack("Copper Cable"),
            'R', Eln.dictAdvancedChip, 'P', findItemStack("Iron Plate")
        )
        addRecipe(
            findItemStack("MQTT Energy Meter"), "ICI", "RMR", "ICI",
            'I', findItemStack("Iron Cable"), 'C', Eln.dictAdvancedChip,
            'R', ItemStack(Items.REDSTONE), 'M', findItemStack("Advanced Energy Meter")
        )
    }

    private fun recipeAutoMiner() {
        addRecipe(
            findItemStack("Auto Miner"), "MCM", "BOB", " P ", 'C', Eln.dictAdvancedChip, 'O', findItemStack(
                "Ore " +
                        "Scanner"
            ), 'B', findItemStack("Advanced Machine Block"), 'M', findItemStack("Advanced Electrical Motor"),
            'P', findItemStack("Mining Pipe")
        )
    }

    private fun recipeWindTurbine() {
        addRecipe(
            findItemStack("Wind Turbine"), " I ", "IMI", " B ", 'B', findItemStack("Machine Block"), 'I',
            "plateIron", 'M', findItemStack("Electrical Motor")
        )
        /*addRecipe(findItemStack("Large Wind Turbine"), //todo add recipe to large wind turbine
            "TTT",
            "TCT",
            "TTT",
            'T', findItemStack("Wind Turbine"),
            'C', findItemStack("Advanced Machine Block")); */
        addRecipe(
            findItemStack("Water Turbine"), "  I", "BMI", "  I", 'I', "plateIron", 'B', findItemStack(
                "Machine " +
                        "Block"
            ), 'M', findItemStack("Electrical Motor")
        )
    }

    private fun recipeFuelGenerator() {
        addRecipe(
            findItemStack("50V Fuel Generator"), "III", " BA", "CMC", 'I', "plateIron", 'B', findItemStack(
                "Machine Block"
            ), 'A', findItemStack("Analogic Regulator"), 'C', findItemStack("Low Voltage Cable"),
            'M', findItemStack("Electrical Motor")
        )
        addRecipe(
            findItemStack("200V Fuel Generator"), "III", " BA", "CMC", 'I', "plateIron", 'B', findItemStack(
                "Advanced Machine Block"
            ), 'A', findItemStack("Analogic Regulator"), 'C', findItemStack(
                "Medium " +
                        "Voltage Cable"
            ), 'M', findItemStack("Advanced Electrical Motor")
        )
    }

    private fun recipeSolarPanel() {
        addRecipe(
            findItemStack("Small Solar Panel"), "LLL", "CSC", "III", 'S', "plateSilicon", 'L', findItemStack(
                "Lapis Dust"
            ), 'I', ItemStack(Items.IRON_INGOT), 'C', findItemStack("Low Voltage Cable")
        )
        addRecipe(
            findItemStack("Small Rotating Solar Panel"), "ISI", "I I", 'S', findItemStack("Small Solar Panel"),
            'M', findItemStack("Electrical Motor"), 'I', ItemStack(Items.IRON_INGOT)
        )
        for (metal in arrayOf<String>("blockSteel", "blockAluminum", "casingMachineAdvanced")) {
            for (panel in arrayOf<String>("Small Solar Panel", "Small Rotating Solar Panel")) {
                addRecipe(findItemStack("2x3 Solar Panel"), "PPP", "PPP", "I I", 'P', findItemStack(panel), 'I', metal)
            }
        }
        addRecipe(
            findItemStack("2x3 Rotating Solar Panel"), "ISI", "IMI", "I I", 'S', findItemStack(
                "2x3 Solar " +
                        "Panel"
            ), 'M', findItemStack("Electrical Motor"), 'I', ItemStack(Items.IRON_INGOT)
        )
    }

    private fun recipeThermalDissipatorPassiveAndActive() {
        addRecipe(
            findItemStack("Small Passive Thermal Dissipator"), "I I", "III", "CIC", 'I', "ingotCopper", 'C',
            findItemStack("Copper Thermal Cable")
        )
        addRecipe(
            findItemStack("Small Active Thermal Dissipator"), "RMR", " D ", 'D', findItemStack(
                "Small Passive " +
                        "Thermal Dissipator"
            ), 'M', findItemStack("Electrical Motor"), 'R', "itemRubber"
        )
        addRecipe(
            findItemStack("200V Active Thermal Dissipator"), "RMR", " D ", 'D', findItemStack(
                "Small Passive " +
                        "Thermal Dissipator"
            ), 'M', findItemStack("Advanced Electrical Motor"), 'R', "itemRubber"
        )
        addRecipe(
            findItemStack("Thermal Heat Exchanger"), "STS", "CCC", "SAS", 'S', Items.IRON_INGOT,  // This should
            // be steel and then iron if DNE, but aaaaaaaaie the code no cooperate.
            'T', findItemStack("Copper Thermal Cable"), 'C', findItemStack("Copper Plate"), 'A', findItemStack(
                "Advanced Machine Block"
            )
        )
    }

    private fun recipeHeatingCorp() {
        addRecipe(
            findItemStack("Small 50V Copper Heating Element"), "C C", "CCC", "C C", 'C', findItemStack(
                "Copper " +
                        "Cable"
            )
        )
        addRecipe(findItemStack("50V Copper Heating Element"), "CC", 'C', findItemStack("Small 50V Copper Heating Element"))
        addRecipe(findItemStack("Small 200V Copper Heating Element"), "CC", 'C', findItemStack("50V Copper Heating Element"))
        addRecipe(
            findItemStack("200V Copper Heating Element"), "CC", 'C', findItemStack("Small 200V Copper Heating Element")
        )
        addRecipe(findItemStack("Small 50V Iron Heating Element"), "C C", "CCC", "C C", 'C', findItemStack("Iron Cable"))
        addRecipe(findItemStack("50V Iron Heating Element"), "CC", 'C', findItemStack("Small 50V Iron Heating Element"))
        addRecipe(findItemStack("Small 200V Iron Heating Element"), "CC", 'C', findItemStack("50V Iron Heating Element"))
        addRecipe(findItemStack("200V Iron Heating Element"), "CC", 'C', findItemStack("Small 200V Iron Heating Element"))
        addRecipe(
            findItemStack("Small 50V Tungsten Heating Element"), "C C", "CCC", "C C", 'C', findItemStack(
                "Tungsten" +
                        " Cable"
            )
        )
        addRecipe(
            findItemStack("50V Tungsten Heating Element"), "CC", 'C', findItemStack(
                "Small 50V Tungsten Heating Element"
            )
        )
        addRecipe(
            findItemStack("Small 200V Tungsten Heating Element"), "CC", 'C', findItemStack(
                "50V Tungsten Heating Element"
            )
        )
        addRecipe(
            findItemStack("200V Tungsten Heating Element"), "CC", 'C', findItemStack(
                "Small 200V Tungsten Heating Element"
            )
        )
        addRecipe(
            findItemStack("Small 800V Tungsten Heating Element"), "CC", 'C', findItemStack(
                "200V Tungsten Heating Element"
            )
        )
        addRecipe(
            findItemStack("800V Tungsten Heating Element"), "CC", 'C', findItemStack(
                "Small 800V Tungsten Heating Element"
            )
        )
        addRecipe(
            findItemStack("Small 3.2kV Tungsten Heating Element"), "CC", 'C', findItemStack(
                "800V Tungsten Heating Element"
            )
        )
        addRecipe(
            findItemStack("3.2kV Tungsten Heating Element"), "CC", 'C', findItemStack(
                "Small 3.2kV Tungsten " +
                        "Heating Element"
            )
        )
    }

    private fun recipeRegulatorItem() {
        addRecipe(
            Eln.findItemStack("On/OFF Regulator 10 Percent", 1), "R R", " R ", " I ", 'R',
            ItemStack(Items.REDSTONE), 'I', findItemStack("Iron Cable")
        )
        addRecipe(
            Eln.findItemStack("On/OFF Regulator 1 Percent", 1), "RRR", " I ", 'R', ItemStack(Items.REDSTONE),
            'I', findItemStack("Iron Cable")
        )
        addRecipe(
            Eln.findItemStack("Analogic Regulator", 1), "R R", " C ", " I ", 'R', ItemStack(Items.REDSTONE),
            'I', findItemStack("Iron Cable"), 'C', Eln.dictCheapChip
        )
    }

    private fun recipeLampItem() {
        // Tungsten
        addRecipe(
            Eln.findItemStack("Small 50V Incandescent Light Bulb", 4), " G ", "GFG", " S ", 'G',
            ItemStack(Blocks.GLASS_PANE), 'F', Eln.dictTungstenIngot, 'S', findItemStack("Copper Cable")
        )
        addRecipe(
            Eln.findItemStack("50V Incandescent Light Bulb", 4), " G ", "GFG", " S ", 'G',
            ItemStack(Blocks.GLASS_PANE), 'F', Eln.dictTungstenIngot, 'S', findItemStack("Low Voltage Cable")
        )
        addRecipe(
            Eln.findItemStack("200V Incandescent Light Bulb", 4), " G ", "GFG", " S ", 'G',
            ItemStack(Blocks.GLASS_PANE), 'F', Eln.dictTungstenIngot, 'S', findItemStack("Medium Voltage Cable")
        )
        // CARBON
        addRecipe(
            Eln.findItemStack("Small 50V Carbon Incandescent Light Bulb", 4), " G ", "GFG", " S ", 'G',
            ItemStack(Blocks.GLASS_PANE), 'F', ItemStack(Items.COAL), 'S', findItemStack("Copper Cable")
        )
        addRecipe(
            Eln.findItemStack("Small 50V Carbon Incandescent Light Bulb", 4), " G ", "GFG", " S ", 'G',
            ItemStack(Blocks.GLASS_PANE), 'F', ItemStack(Items.CHARCOAL), 'S', findItemStack("Copper Cable")
        )
        addRecipe(
            Eln.findItemStack("50V Carbon Incandescent Light Bulb", 4), " G ", "GFG", " S ", 'G',
            ItemStack(Blocks.GLASS_PANE), 'F', ItemStack(Items.COAL), 'S', findItemStack("Low Voltage Cable")
        )
        addRecipe(
            Eln.findItemStack("50V Carbon Incandescent Light Bulb", 4), " G ", "GFG", " S ", 'G',
            ItemStack(Blocks.GLASS_PANE), 'F', ItemStack(Items.CHARCOAL), 'S', findItemStack(
                "Low Voltage " +
                        "Cable"
            )
        )
        addRecipe(
            Eln.findItemStack("Small 50V Economic Light Bulb", 4), " G ", "GFG", " S ", 'G',
            ItemStack(Blocks.GLASS_PANE), 'F', ItemStack(Items.GLOWSTONE_DUST), 'S', findItemStack(
                "Copper " +
                        "Cable"
            )
        )
        addRecipe(
            Eln.findItemStack("50V Economic Light Bulb", 4), " G ", "GFG", " S ", 'G',
            ItemStack(Blocks.GLASS_PANE), 'F', ItemStack(Items.GLOWSTONE_DUST), 'S', findItemStack(
                "Low Voltage " +
                        "Cable"
            )
        )
        addRecipe(
            Eln.findItemStack("200V Economic Light Bulb", 4), " G ", "GFG", " S ", 'G',
            ItemStack(Blocks.GLASS_PANE), 'F', ItemStack(Items.GLOWSTONE_DUST), 'S', findItemStack(
                "Medium " +
                        "Voltage Cable"
            )
        )
        addRecipe(
            Eln.findItemStack("50V Farming Lamp", 2), "GGG", "FFF", "GSG", 'G', ItemStack(Blocks.GLASS_PANE),
            'F', Eln.dictTungstenIngot, 'S', findItemStack("Low Voltage Cable")
        )
        addRecipe(
            Eln.findItemStack("200V Farming Lamp", 2), "GGG", "FFF", "GSG", 'G', ItemStack(Blocks.GLASS_PANE),
            'F', Eln.dictTungstenIngot, 'S', findItemStack("Medium Voltage Cable")
        )
        addRecipe(
            Eln.findItemStack("50V LED Bulb", 2), "GGG", "SSS", " C ", 'G', ItemStack(Blocks.GLASS_PANE), 'S',
            findItemStack("Silicon Ingot"), 'C', findItemStack("Low Voltage Cable")
        )
        addRecipe(
            Eln.findItemStack("200V LED Bulb", 2), "GGG", "SSS", " C ", 'G', ItemStack(Blocks.GLASS_PANE), 'S',
            findItemStack("Silicon Ingot"), 'C', findItemStack("Medium Voltage Cable")
        )
        // Halogen Bulbs
        addRecipe(
            Eln.findItemStack("50V Halogen Bulb", 2), "GGG", "CTC", "GGG", 'G', ItemStack(Blocks.GLASS_PANE),
            'C', findItemStack("Low Voltage Cable"), 'T', findItemStack("Tungsten Ingot")
        )
        addRecipe(
            Eln.findItemStack("200V Halogen Bulb", 2), "GGG", "CTC", "GGG", 'G', ItemStack(Blocks.GLASS_PANE),
            'C', findItemStack("Medium Voltage Cable"), 'T', findItemStack("Tungsten Ingot")
        )
    }

    private fun recipeProtection() {
        addRecipe(
            Eln.findItemStack("Overvoltage Protection", 4), "SCD", 'S', findItemStack("Electrical Probe Chip"), 'C',
            Eln.dictCheapChip, 'D', ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("Overheating Protection", 4), "SCD", 'S', findItemStack("Thermal Probe Chip"), 'C',
            Eln.dictCheapChip, 'D', ItemStack(Items.REDSTONE)
        )
    }

    private fun recipeCombustionChamber() {
        addRecipe(findItemStack("Combustion Chamber"), " L ", "L L", " L ", 'L', ItemStack(Blocks.STONE))
        addRecipe(
            Eln.findItemStack("Thermal Insulation", 4), "WSW", "SWS", "WSW", 'S', ItemStack(Blocks.STONE), 'W',
            "minecraft:wool"
        )
    }

    private fun recipeFerromagneticCore() {
        addRecipe(findItemStack("Cheap Ferromagnetic Core"), "LLL", "L  ", "LLL", 'L', findItemStack("Iron Cable"))
        addRecipe(
            findItemStack("Average Ferromagnetic Core"), "PCP", 'C', findItemStack("Cheap Ferromagnetic Core"),
            'P', "plateIron"
        )
        addRecipe(
            findItemStack("Optimal Ferromagnetic Core"), " P ", "PCP", " P ", 'C', findItemStack(
                "Average " +
                        "Ferromagnetic Core"
            ), 'P', "plateIron"
        )
    }

    private fun recipeIngot() {
        // Done
    }

    private fun recipeDust() {
        addShapelessRecipe(
            Eln.findItemStack("Alloy Dust", 6), "dustIron", "dustCoal", Eln.dictTungstenDust, Eln.dictTungstenDust,
            Eln.dictTungstenDust, Eln.dictTungstenDust
        )
        addShapelessRecipe(
            Eln.findItemStack("Inert Canister", 1), findItemStack("Lapis Dust"), findItemStack(
                "Lapis " +
                        "Dust"
            ), findItemStack("Lapis Dust"), findItemStack("Lapis Dust"), findItemStack("Diamond Dust"),
            findItemStack("Lapis Dust"), findItemStack("Lapis Dust"), findItemStack("Lapis Dust"), findItemStack(
                "Lapis" +
                        " Dust"
            )
        )
    }

    private fun recipeElectricalMotor() {
        addRecipe(
            findItemStack("Electrical Motor"), " C ", "III", "C C", 'I', findItemStack("Iron Cable"), 'C',
            findItemStack("Low Voltage Cable")
        )

        addRecipe(
            findItemStack("Advanced Electrical Motor"), "RCR", "MIM", "CRC", 'M', findItemStack(
                "Advanced " +
                        "Magnet"
            ), 'I', ItemStack(Items.IRON_INGOT), 'R', ItemStack(Items.REDSTONE), 'C', findItemStack(
                "Medium Voltage Cable"
            )
        )
    }

    private fun recipeSolarTracker() {
        addRecipe(
            Eln.findItemStack("Solar Tracker", 4), "VVV", "RQR", "III", 'Q', ItemStack(Items.QUARTZ), 'V',
            ItemStack(Blocks.GLASS_PANE), 'R', ItemStack(Items.REDSTONE), 'G', ItemStack(Items.GOLD_INGOT),
            'I', ItemStack(Items.IRON_INGOT)
        )
    }

    private fun recipeDynamo() {
    }

    private fun recipeWindRotor() {
    }

    /** The parts added on the expansion-testing branch. */
    private fun recipeExpansion() {
        val probe = findItemStack("Electrical Probe Chip")
        val relay = findItemStack("High Voltage Relay")
        addRecipe(
            findItemStack("Overcurrent Protection Relay"), " P ", "CBC", 'P', probe, 'C', Eln.dictCheapChip,
            'B', findItemStack("Electrical Breaker")
        )
        addRecipe(
            findItemStack("Battery Management System"), "PCP", " R ", 'P', probe, 'C', Eln.dictCheapChip, 'R', relay
        )
        addRecipe(
            findItemStack("Automatic Transfer Switch"), "RCR", " P ", 'R', relay, 'C', Eln.dictCheapChip, 'P', probe
        )
        addRecipe(
            findItemStack("MPPT Charge Controller"), "IAI", "cPc", "III", 'I', "plateIron", 'A', Eln.dictAdvancedChip,
            'c', findItemStack("Copper Cable"), 'P', probe
        )
        addRecipe(
            findItemStack("Oscilloscope"), "GGG", "PCP", "III", 'G', ItemStack(Blocks.GLASS_PANE), 'P', probe,
            'C', Eln.dictAdvancedChip, 'I', "plateIron"
        )
        addRecipe(
            findItemStack("Thermal Camera"), "RAR", "RTR", "RCR", 'A', ItemStack(Items.AMETHYST_SHARD),
            'T', findItemStack("Thermal Probe Chip"), 'C', Eln.dictCheapChip, 'R', "itemRubber"
        )

        val medium = findItemStack("Medium Voltage Cable")
        addRecipe(Eln.findItemStack("3200V Supply Cable", 2), "CRC", "CRC", 'C', findItemStack("Very High Voltage Cable"), 'R', "itemRubber")
        val protection = findItemStack("Overcurrent Protection Relay")
        addRecipe(
            findItemStack("SCADA Monitor"), "GGG", "GAG", "III", 'G', ItemStack(Blocks.GLASS_PANE), 'A', Eln.dictAdvancedChip,
            'I', "plateIron"
        )
        addRecipe(
            findItemStack("SCADA Transmitter"), " R ", "ICI", 'R', ItemStack(Items.LIGHTNING_ROD), 'I', "plateIron", 'C', Eln.dictCheapChip
        )
        addRecipe(findItemStack("SCADA Linker"), "C", "S", 'C', Eln.dictCheapChip, 'S', ItemStack(Items.STICK))
        addRecipe(
            findItemStack("Distribution Substation"), "R R", "HGH", "SSS", 'R', protection,
            'H', findItemStack("High Voltage Cable"), 'G', findItemStack("Grid DC-DC Converter"), 'S', ItemStack(Items.SMOOTH_STONE)
        )
        addRecipe(
            findItemStack("Step-Up Substation"), "R R", "VGH", "SSS", 'R', protection, 'V', findItemStack("3200V Supply Cable"),
            'H', findItemStack("High Voltage Cable"), 'G', findItemStack("Grid DC-DC Converter"), 'S', ItemStack(Items.SMOOTH_STONE)
        )
        addRecipe(
            findItemStack("Lockout Padlock"), " N ", "N N", "IRI", 'N', ItemStack(Items.IRON_NUGGET),
            'I', ItemStack(Items.IRON_INGOT), 'R', ItemStack(Items.RED_DYE)
        )
        addRecipe(
            findItemStack("200V Breaker Box"), "IBI", "BCB", "IBI", 'I', "plateIron", 'B', findItemStack("Electrical Breaker"),
            'C', medium
        )
        addRecipe(Eln.findItemStack("200V Power Bundle Cable", 2), "MMM", "MRM", "MMM", 'M', medium, 'R', "itemRubber")
        // Supply cables: four cables of their voltage twisted together, in rubber.
        for ((supply, cable) in listOf("50V Supply Cable" to "Low Voltage Cable", "200V Supply Cable" to "Medium Voltage Cable",
            "800V Supply Cable" to "High Voltage Cable")) {
            addRecipe(Eln.findItemStack(supply, 2), "CRC", "CRC", 'C', findItemStack(cable), 'R', "itemRubber")
        }
        addRecipe(Eln.findItemStack("50V Power Bundle Cable", 2), "LLL", "LRL", "LLL", 'L', findItemStack("Low Voltage Cable"), 'R', "itemRubber")
        addRecipe(
            findItemStack("50V Breaker Box"), "IBI", "BCB", "IBI", 'I', "plateIron", 'B', findItemStack("Electrical Breaker"),
            'C', findItemStack("Low Voltage Cable")
        )
        val high = findItemStack("High Voltage Cable")
        addRecipe(
            findItemStack("800V Breaker Box"), "IBI", "BCB", "IBI", 'I', "plateIron", 'B', findItemStack("Electrical Breaker"),
            'C', high
        )
        addRecipe(Eln.findItemStack("800V Power Bundle Cable", 2), "HHH", "HRH", "HHH", 'H', high, 'R', "itemRubber")

        val cable = findItemStack("Low Voltage Cable")
        addRecipe(
            findItemStack("Lead-Acid Battery"), "C C", "PSP", "PPP", 'C', cable, 'P', "ingotLead",
            'S', ItemStack(Items.GUNPOWDER)
        )
        addRecipe(
            findItemStack("Nickel-Cadmium Battery"), "C C", "IRI", "IRI", 'C', cable, 'I', ItemStack(Items.IRON_INGOT),
            'R', ItemStack(Items.REDSTONE)
        )
        val lithium: Any = if (OreDict.doesOreNameExist("dustLithium")) "dustLithium" else findItemStack("Lapis Dust")
        addRecipe(
            findItemStack("Lithium-Ion Battery"), "CBC", "LGL", "LGL", 'C', cable, 'B', Eln.dictCheapChip,
            'L', lithium, 'G', ItemStack(Items.COAL)
        )
        addRecipe(
            findItemStack("LiFePO4 Battery"), "CBC", "LIL", "LIL", 'C', cable, 'B', Eln.dictCheapChip,
            'L', lithium, 'I', ItemStack(Items.IRON_INGOT)
        )

        for (metal in arrayOf("blockSteel", "blockAluminum", "casingMachineAdvanced")) {
            addRecipe(
                findItemStack("Monocrystalline Solar Panel"), "SSS", "SSS", "I I", 'S', "plateSilicon", 'I', metal
            )
        }
        addRecipe(
            findItemStack("Permanent-Magnet Wind Turbine"), "PIP", "IMI", " B ", 'P', "plateIron",
            'I', findItemStack("Alloy Plate"), 'M', findItemStack("Advanced Electrical Motor"),
            'B', findItemStack("Advanced Machine Block")
        )
    }

    private fun recipeMeter() {
        addRecipe(
            findItemStack("MultiMeter"), "RGR", "RER", "RCR", 'G', ItemStack(Blocks.GLASS_PANE), 'C',
            findItemStack("Electrical Probe Chip"), 'E', ItemStack(Items.REDSTONE), 'R', "itemRubber"
        )
        addRecipe(
            findItemStack("Thermometer"), "RGR", "RER", "RCR", 'G', ItemStack(Blocks.GLASS_PANE), 'C',
            findItemStack("Thermal Probe Chip"), 'E', ItemStack(Items.REDSTONE), 'R', "itemRubber"
        )
        addShapelessRecipe(findItemStack("AllMeter"), findItemStack("MultiMeter"), findItemStack("Thermometer"))
        addRecipe(
            findItemStack("Wireless Analyser"), " S ", "RGR", "RER", 'G', ItemStack(Blocks.GLASS_PANE), 'S',
            findItemStack("Signal Antenna"), 'E', ItemStack(Items.REDSTONE), 'R', "itemRubber"
        )
        addRecipe(
            findItemStack("Config Copy Tool"), "wR", "RC", 'w', findItemStack("Wrench"), 'R',
            ItemStack(Items.REDSTONE), 'C', Eln.dictAdvancedChip
        )
    }

    private fun recipeElectricalDrill() {
        addRecipe(
            findItemStack("Cheap Electrical Drill"), "CMC", " T ", " P ", 'T', findItemStack("Mining Pipe"),
            'C', Eln.dictCheapChip, 'M', findItemStack("Electrical Motor"), 'P', ItemStack(Items.IRON_PICKAXE)
        )
        addRecipe(
            findItemStack("Average Electrical Drill"), "RCR", " D ", " d ", 'R', Items.REDSTONE, 'C',
            Eln.dictCheapChip, 'D', findItemStack("Cheap Electrical Drill"), 'd', ItemStack(Items.DIAMOND)
        )
        addRecipe(
            findItemStack("Fast Electrical Drill"), "MCM", " T ", " P ", 'T', findItemStack("Mining Pipe"), 'C',
            Eln.dictAdvancedChip, 'M', findItemStack("Advanced Electrical Motor"), 'P', ItemStack(Items.DIAMOND_PICKAXE)
        )
        addRecipe(
            findItemStack("Turbo Electrical Drill"), "RCR", " F ", " D ", 'F', findItemStack(
                "Fast Electrical " +
                        "Drill"
            ), 'C', Eln.dictAdvancedChip, 'R', findItemStack("Graphite Rod"), 'D', findItemStack("Synthetic Diamond")
        )
        addRecipe(
            findItemStack("Irresponsible Electrical Drill"), "DDD", "DFD", "DDD", 'F', findItemStack(
                "Turbo " +
                        "Electrical Drill"
            ), 'D', findItemStack("Synthetic Diamond")
        )
    }

    private fun recipeOreScanner() {
        addRecipe(
            findItemStack("Ore Scanner"), "IGI", "RCR", "IGI", 'C', Eln.dictCheapChip, 'R',
            ItemStack(Items.REDSTONE), 'I', findItemStack("Iron Cable"), 'G', ItemStack(Items.GOLD_INGOT)
        )
    }

    private fun recipeMiningPipe() {
        addRecipe(Eln.findItemStack("Mining Pipe", 12), "A", "A", 'A', "ingotAlloy")
    }

    private fun recipeTreeResinAndRubber() {
        addRecipe(findItemStack("Tree Resin Collector"), "W W", "WW ", 'W', "plankWood")
        addRecipe(findItemStack("Tree Resin Collector"), "W W", " WW", 'W', "plankWood")
    }

    private fun recipeRawCable() {
        addRecipe(Eln.findItemStack("Copper Cable", 12), "III", 'I', "ingotCopper")

        if (Eln.verticalIronCableCrafting) {
            addRecipe(Eln.findItemStack("Iron Cable", 12), "I  ", "I  ", "I  ", 'I', ItemStack(Items.IRON_INGOT))
        } else {
            addRecipe(Eln.findItemStack("Iron Cable", 12), "III", 'I', ItemStack(Items.IRON_INGOT))
        }

        addRecipe(Eln.findItemStack("Tungsten Cable", 6), "III", 'I', Eln.dictTungstenIngot)
        /*addRecipe(findItemStack("T1 Transmission Cable", 6),
            "III",
            'I', firstExistingOre("ingotSteel", "Arc Metal Ingot"));
        addRecipe(findItemStack("T2 Transmission Cable", 6),
            "III",
            'I', firstExistingOre("ingotAluminium", "ingotAluminum", "Arc Clay Ingot"));
*/
    }

    private fun recipeGraphite() {
        addRecipe(
            Eln.findItemStack("Creative Cable", 1), "I", "S", 'S', findItemStack("unreleasedium"), 'I',
            findItemStack("Synthetic Diamond")
        )
        addRecipe(ItemStack(Eln.arcClayBlock), "III", "III", "III", 'I', findItemStack("Arc Clay Ingot"))
        addRecipe(Eln.findItemStack("Arc Clay Ingot", 9), "I", 'I', ItemStack(Eln.arcClayBlock))
        addRecipe(ItemStack(Eln.arcMetalBlock), "III", "III", "III", 'I', findItemStack("Arc Metal Ingot"))
        addRecipe(Eln.findItemStack("Arc Metal Ingot", 9), "I", 'I', ItemStack(Eln.arcMetalBlock))
        addRecipe(Eln.findItemStack("Graphite Rod", 2), "I", 'I', findItemStack("2x Graphite Rods"))
        addRecipe(Eln.findItemStack("Graphite Rod", 3), "I", 'I', findItemStack("3x Graphite Rods"))
        addRecipe(Eln.findItemStack("Graphite Rod", 4), "I", 'I', findItemStack("4x Graphite Rods"))
        addShapelessRecipe(
            findItemStack("2x Graphite Rods"), findItemStack("Graphite Rod"), findItemStack(
                "Graphite " +
                        "Rod"
            )
        )
        addShapelessRecipe(
            findItemStack("3x Graphite Rods"), findItemStack("Graphite Rod"), findItemStack(
                "Graphite " +
                        "Rod"
            ), findItemStack("Graphite Rod")
        )
        addShapelessRecipe(
            findItemStack("3x Graphite Rods"), findItemStack("Graphite Rod"), findItemStack(
                "2x " +
                        "Graphite Rods"
            )
        )
        addShapelessRecipe(
            findItemStack("4x Graphite Rods"), findItemStack("Graphite Rod"), findItemStack(
                "Graphite " +
                        "Rod"
            ), findItemStack("Graphite Rod"), findItemStack("Graphite Rod")
        )
        addShapelessRecipe(
            findItemStack("4x Graphite Rods"), findItemStack("2x Graphite Rods"), findItemStack(
                "Graphite Rod"
            ), findItemStack("Graphite Rod")
        )
        addShapelessRecipe(
            findItemStack("4x Graphite Rods"), findItemStack("2x Graphite Rods"), findItemStack(
                "2x " +
                        "Graphite Rods"
            )
        )
        addShapelessRecipe(
            findItemStack("4x Graphite Rods"), findItemStack("3x Graphite Rods"), findItemStack(
                "Graphite Rod"
            )
        )
        addShapelessRecipe(ItemStack(Items.DIAMOND, 2), findItemStack("Synthetic Diamond"))
    }

    private fun recipeBatteryItem() {
        addRecipe(
            findItemStack("Portable Battery"), " I ", "IPI", "IPI", 'P', "ingotLead", 'I',
            ItemStack(Items.IRON_INGOT)
        )
        addShapelessRecipe(
            findItemStack("Portable Battery Pack"), findItemStack("Portable Battery"), findItemStack(
                "Portable Battery"
            ), findItemStack("Portable Battery"), findItemStack("Portable Battery")
        )
    }

    private fun recipeElectricalTool() {
        addRecipe(
            findItemStack("Small Flashlight"), "GLG", "IBI", " I ", 'L', findItemStack(
                "50V Incandescent Light " +
                        "Bulb"
            ), 'B', findItemStack("Portable Battery"), 'G', ItemStack(Blocks.GLASS_PANE), 'I',
            ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            findItemStack("Improved Flashlight"), "GLG", "IBI", " I ", 'L', findItemStack("50V LED Bulb"), 'B',
            findItemStack("Portable Battery Pack"), 'G', ItemStack(Blocks.GLASS_PANE), 'I',
            ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            findItemStack("Portable Electrical Mining Drill"), " T ", "IBI", " I ", 'T', findItemStack(
                "Average" +
                        " Electrical Drill"
            ), 'B', findItemStack("Portable Battery"), 'I', ItemStack(Items.IRON_INGOT)
        )
        addRecipe(
            findItemStack("Portable Electrical Axe"), " T ", "IMI", "IBI", 'T', ItemStack(Items.IRON_AXE),
            'B', findItemStack("Portable Battery"), 'M', findItemStack("Electrical Motor"), 'I',
            ItemStack(Items.IRON_INGOT)
        )
        if (Eln.instance.xRayScannerCanBeCrafted) {
            addRecipe(
                findItemStack("X-Ray Scanner"), "PGP", "PCP", "PBP", 'C', Eln.dictAdvancedChip, 'B', findItemStack(
                    "Portable Battery"
                ), 'P', findItemStack("Iron Cable"), 'G', findItemStack("Ore Scanner")
            )
        }
    }

    private fun recipeECoal() {
        addRecipe(
            findItemStack("E-Coal Helmet"), "PPP", "PCP", 'P', "plateCoal", 'C', findItemStack(
                "Portable " +
                        "Condensator"
            )
        )
        addRecipe(
            findItemStack("E-Coal Boots"), " C ", "P P", "P P", 'P', "plateCoal", 'C', findItemStack(
                "Portable " +
                        "Condensator"
            )
        )
        addRecipe(
            findItemStack("E-Coal Chestplate"), "P P", "PCP", "PPP", 'P', "plateCoal", 'C', findItemStack(
                "Portable Condensator"
            )
        )
        addRecipe(
            findItemStack("E-Coal Leggings"), "PPP", "PCP", "P P", 'P', "plateCoal", 'C', findItemStack(
                "Portable Condensator"
            )
        )
    }

    private fun recipePortableCapacitor() {
        addRecipe(
            findItemStack("Portable Condensator"), " r ", "cDc", " r ", 'r', ItemStack(Items.REDSTONE), 'c',
            findItemStack("Iron Cable"), 'D', findItemStack("Dielectric")
        )
        addShapelessRecipe(
            findItemStack("Portable Condensator Pack"), findItemStack("Portable Condensator"),
            findItemStack("Portable Condensator"), findItemStack("Portable Condensator"), findItemStack(
                "Portable " +
                        "Condensator"
            )
        )
    }

    private fun recipeMiscItem() {
        addRecipe(
            findItemStack("Cheap Chip"), " R ", "RSR", " R ", 'S', "ingotSilicon", 'R',
            ItemStack(Items.REDSTONE)
        )
        addRecipe(
            findItemStack("Advanced Chip"), "LRL", "RCR", "LRL", 'C', Eln.dictCheapChip, 'L', "ingotSilicon", 'R',
            ItemStack(Items.REDSTONE)
        )
        addRecipe(
            findItemStack("Machine Block"), "rLr", "LcL", "rLr", 'L', findItemStack("Iron Cable"), 'c',
            findItemStack("Copper Cable"), 'r', findItemStack("Tree Resin")
        )
        addRecipe(
            findItemStack("Advanced Machine Block"), "rCr", "CcC", "rCr", 'C', "plateAlloy", 'r',
            findItemStack("Tree Resin"), 'c', findItemStack("Copper Cable")
        )
        addRecipe(
            findItemStack("Electrical Probe Chip"), " R ", "RCR", " R ", 'C', findItemStack(
                "High Voltage " +
                        "Cable"
            ), 'R', ItemStack(Items.REDSTONE)
        )
        addRecipe(
            findItemStack("Thermal Probe Chip"), " C ", "RIR", " C ", 'G', ItemStack(Items.GOLD_INGOT), 'I',
            findItemStack("Iron Cable"), 'C', "ingotCopper", 'R', ItemStack(Items.REDSTONE)
        )
        addRecipe(findItemStack("Signal Antenna"), "c", "c", 'c', findItemStack("Iron Cable"))
        addRecipe(
            findItemStack("Machine Booster"), "m", "c", "m", 'm', findItemStack("Electrical Motor"), 'c',
            Eln.dictAdvancedChip
        )
        addRecipe(findItemStack("Wrench"), " c ", "cc ", "  c", 'c', findItemStack("Iron Cable"))
        addRecipe(
            findItemStack("Player Filter"), " g", "gc", " g", 'g', ItemStack(Blocks.GLASS_PANE), 'c',
            ItemStack(Items.GREEN_DYE)
        )
        addRecipe(
            findItemStack("Monster Filter"), " g", "gc", " g", 'g', ItemStack(Blocks.GLASS_PANE), 'c',
            ItemStack(Items.RED_DYE)
        )
        addRecipe(
            findItemStack("Animal Filter"), " g", "gc", " g", 'g', ItemStack(Blocks.GLASS_PANE), 'c',
            ItemStack(Items.LAPIS_LAZULI)
        )
        addRecipe(Eln.findItemStack("Casing", 1), "ppp", "p p", "ppp", 'p', findItemStack("Iron Cable"))
        addRecipe(findItemStack("Iron Clutch Plate"), " t ", "tIt", " t ", 'I', "plateIron", 't', Eln.dictTungstenDust)
        addRecipe(findItemStack("Gold Clutch Plate"), " t ", "tGt", " t ", 'G', "plateGold", 't', Eln.dictTungstenDust)
        addRecipe(
            findItemStack("Copper Clutch Plate"),
            " t ",
            "tCt",
            " t ",
            'C',
            "plateCopper",
            't',
            Eln.dictTungstenDust
        )
        addRecipe(findItemStack("Lead Clutch Plate"), " t ", "tLt", " t ", 'L', "plateLead", 't', Eln.dictTungstenDust)
        addRecipe(findItemStack("Coal Clutch Plate"), " t ", "tCt", " t ", 'C', "plateCoal", 't', Eln.dictTungstenDust)
        addRecipe(Eln.findItemStack("Clutch Pin", 4), "s", "s", 's', firstExistingOre("ingotSteel", "ingotAlloy"))
        addRecipe(
            findItemStack("Motor Cowling"),
            "ISI",
            "MAM",
            "ISI",
            'I',
            findItemStack("Iron Plate"),
            'S',
            firstExistingOre("ingotSteel", "ingotAluminum", "ingotAlloy"),
            'M',
            findItemStack("Machine Block"),
            'A',
            findItemStack("Advanced Machine Block")
        )
        addRecipe(
            findItemStack("Heavy Winding"),
            "cRc",
            "RTR",
            "cRc",
            'c',
            findItemStack("Copper Cable"),
            'R',
            findItemStack("Tree Resin"),
            'T',
            "itemRubber"
        )
        addRecipe(
            findItemStack("Reinforced Shaft"),
            " s ",
            "sFs",
            "AMA",
            's',
            firstExistingOre("ingotSteel", "ingotAlloy", "ingotIron"),
            'F',
            findItemStack("Fixed Shaft"),
            'A',
            findItemStack("Advanced Machine Block"),
            'M',
            firstExistingOre("blockSteel", "blockAlloy", "blockIron")
        )
        addRecipe(
            findItemStack("Turbine Blade Set"),
            "SIS",
            "BAB",
            "SIS",
            'S',
            firstExistingOre("ingotAluminum", "ingotSteel"),
            'I',
            findItemStack("Iron Plate"),
            'B',
            "plateAlloy",
            'A',
            findItemStack("Advanced Machine Block")
        )
    }

    private fun recipeMacerator() {
        val f = 4000f
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.COAL_ORE, 1), ItemStack(Items.COAL, 3),
                1.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Copper Ore"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Copper " +
                                "Dust", 2
                    )
                ), 1.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.IRON_ORE), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Iron " +
                                "Dust", 2
                    )
                ), 1.5 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.GOLD_ORE), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Gold " +
                                "Dust", 2
                    )
                ), 3.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Lead Ore"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Lead Dust",
                        2
                    )
                ), 2.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Tungsten Ore"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Tungsten " +
                                "Dust", 2
                    )
                ), 2.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Items.COAL), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Coal " +
                                "Dust", 1
                    )
                ), 1.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Items.CHARCOAL), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Coal " +
                                "Dust", 1
                    )
                ), 1.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.SAND, 1), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Silicon " +
                                "Dust", 1
                    )
                ), 3.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Cinnabar Ore"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Cinnabar " +
                                "Dust", 1
                    )
                ), 2.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Items.LAPIS_LAZULI), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Lapis " +
                                "Dust", 1
                    )
                ), 2.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Items.DIAMOND, 1), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Diamond" +
                                " Dust", 1
                    )
                ), 2.0 * f
            )
        )

        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Copper Ingot"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Copper " +
                                "Dust", 1
                    )
                ), 0.5 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Items.IRON_INGOT), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Iron " +
                                "Dust", 1
                    )
                ), 0.5 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Items.GOLD_INGOT), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Gold " +
                                "Dust", 1
                    )
                ), 0.5 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Lead Ingot"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Lead Dust",
                        1
                    )
                ), 0.5 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Tungsten Ingot"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Tungsten Dust", 1
                    )
                ), 0.5 * f
            )
        )

        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.COBBLESTONE),
                arrayOf<ItemStack>(ItemStack(Blocks.GRAVEL)), 1.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.GRAVEL),
                arrayOf<ItemStack>(ItemStack(Items.FLINT)), 1.0 * f
            )
        )

        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.DIRT), arrayOf<ItemStack>(ItemStack(Blocks.SAND)),
                1.0 * f
            )
        )

        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("E-Coal Helmet"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Coal " +
                                "Dust", 16
                    )
                ), 10.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("E-Coal Boots"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Coal " +
                                "Dust", 12
                    )
                ), 10.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("E-Coal Chestplate"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Coal" +
                                " Dust", 24
                    )
                ), 10.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("E-Coal Leggings"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Coal " +
                                "Dust", 24
                    )
                ), 10.0 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Cost Oriented Battery"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Lead Dust", 6
                    )
                ), 12.5 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Life Oriented Battery"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Lead Dust", 6
                    )
                ), 12.5 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Current Oriented Battery"),
                arrayOf<ItemStack>(Eln.findItemStack("Lead Dust", 6)), 12.5 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Voltage Oriented Battery"),
                arrayOf<ItemStack>(Eln.findItemStack("Lead Dust", 6)), 12.5 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Capacity Oriented Battery"),
                arrayOf<ItemStack>(Eln.findItemStack("Lead Dust", 6)), 12.5 * f
            )
        )
        Eln.instance.maceratorRecipes.addRecipe(
            Recipe(
                findItemStack("Single-use Battery"), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Copper Dust", 3
                    )
                ), 10.0 * f
            )
        )
    }

    private fun recipeArcFurnace() {
        val f = 200000f
        val smeltf = 5000f
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.IRON_ORE, 1),
                arrayOf<ItemStack>(ItemStack(Items.IRON_INGOT, 2)), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.GOLD_ORE, 1),
                arrayOf<ItemStack>(ItemStack(Items.GOLD_INGOT, 2)), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.COAL_ORE, 1),
                arrayOf<ItemStack>(ItemStack(Items.COAL, 2)), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.REDSTONE_ORE, 1),
                arrayOf<ItemStack>(ItemStack(Items.REDSTONE, 6)), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.LAPIS_ORE, 1),
                arrayOf<ItemStack>(ItemStack(Blocks.LAPIS_BLOCK, 1)), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.DIAMOND_ORE, 1),
                arrayOf<ItemStack>(ItemStack(Items.DIAMOND, 2)), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.EMERALD_ORE, 1),
                arrayOf<ItemStack>(ItemStack(Items.EMERALD, 2)), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                ItemStack(Blocks.NETHER_QUARTZ_ORE, 1),
                arrayOf<ItemStack>(ItemStack(Items.QUARTZ, 2)), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Copper Ore", 1), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Copper " +
                                "Ingot", 2
                    )
                ), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Lead Ore", 1), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Lead " +
                                "Ingot", 2
                    )
                ), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Tungsten Ore", 1), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Tungsten Ingot", 2
                    )
                ), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Alloy Dust", 1), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Alloy " +
                                "Ingot", 1
                    )
                ), smeltf.toDouble()
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                ItemStack(Items.CLAY_BALL, 2), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Arc " +
                                "Clay Ingot", 1
                    )
                ), 2.0 * f
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                ItemStack(Items.IRON_INGOT, 1), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Arc" +
                                " Metal Ingot", 1
                    )
                ), 1.0 * f
            )
        )
        Eln.instance.arcFurnaceRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Canister of Water", 1), arrayOf<ItemStack>(
                    Eln.findItemStack(
                        "Canister of Arc Water", 1
                    )
                ), 7000000.0
            )
        ) //hardcoded 7MJ to prevent overunity
    }

    /** What ores drop nowadays becomes the same as the ore. */
    private fun recipeRawMaterials() {
        val f = 4000.0
        val smelt = 5000.0
        val tungsten = Eln.dictTungstenOre.replaceFirst("ore", "raw")
        with(Eln.instance) {
            rawMaterial(maceratorRecipes, "rawCopper", Eln.findItemStack("Copper Dust", 2), 1.0 * f)
            rawMaterial(maceratorRecipes, "rawIron", Eln.findItemStack("Iron Dust", 2), 1.5 * f)
            rawMaterial(maceratorRecipes, "rawGold", Eln.findItemStack("Gold Dust", 2), 3.0 * f)
            rawMaterial(maceratorRecipes, "rawLead", Eln.findItemStack("Lead Dust", 2), 2.0 * f)
            rawMaterial(maceratorRecipes, tungsten, Eln.findItemStack("Tungsten Dust", 2), 2.0 * f)
            rawMaterial(maceratorRecipes, "rawCinnabar", Eln.findItemStack("Cinnabar Dust", 1), 2.0 * f)
            rawMaterial(arcFurnaceRecipes, "rawCopper", Eln.findItemStack("Copper Ingot", 2), smelt)
            rawMaterial(arcFurnaceRecipes, "rawIron", ItemStack(Items.IRON_INGOT, 2), smelt)
            rawMaterial(arcFurnaceRecipes, "rawGold", ItemStack(Items.GOLD_INGOT, 2), smelt)
            rawMaterial(arcFurnaceRecipes, "rawLead", Eln.findItemStack("Lead Ingot", 2), smelt)
            rawMaterial(arcFurnaceRecipes, tungsten, Eln.findItemStack("Tungsten Ingot", 2), smelt)
        }
    }

    private fun rawMaterial(list: RecipesList, name: String, output: ItemStack, energy: Double) {
        // Some only exist with other mods. One stands for all with the tag.
        val input = OreDict.getOres(name).firstOrNull() ?: return
        list.addRecipe(Recipe(input.copyWithCount(1), output, energy))
    }

    private fun recipeMaceratorModOres() {
        val f = 4000f
        recipeMaceratorModOre(f * 3f, "oreCertusQuartz", "dustCertusQuartz", 3)
        recipeMaceratorModOre(f * 1.5f, "crystalCertusQuartz", "dustCertusQuartz", 1)
        recipeMaceratorModOre(f * 3f, "oreNetherQuartz", "dustNetherQuartz", 3)
        recipeMaceratorModOre(f * 1.5f, "crystalNetherQuartz", "dustNetherQuartz", 1)
        recipeMaceratorModOre(f * 1.5f, "crystalFluix", "dustFluix", 1)
    }

    private fun recipeMaceratorModOre(f: Float, inputName: String, outputName: String, outputCount: Int) {
        if (!OreDict.doesOreNameExist(inputName)) {
            Utils.println("No entries for oredict: $inputName")
            return
        }
        if (!OreDict.doesOreNameExist(outputName)) {
            Utils.println("No entries for oredict: $outputName")
            return
        }
        val inOres = OreDict.getOres(inputName)
        val outOres = OreDict.getOres(outputName)
        if (inOres.size == 0) {
            Utils.println("No ores in oredict entry: $inputName")
        }
        if (outOres.size == 0) {
            Utils.println("No ores in oredict entry: $outputName")
            return
        }
        val output = outOres[0].copy()
        output.count = outputCount
        Utils.println("Adding mod recipe from $inputName to $outputName")
        for (input in inOres) {
            Eln.instance.maceratorRecipes.addRecipe(Recipe(input, output, f.toDouble()))
        }
    }

    private fun recipePlateMachine() {
        val f = 10000f
        Eln.instance.plateMachineRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Copper Ingot", Eln.instance.plateConversionRatio), findItemStack(
                    "Copper Plate"
                ), 1.0 * f
            )
        )
        Eln.instance.plateMachineRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Lead Ingot", Eln.instance.plateConversionRatio), findItemStack(
                    "Lead Plate"
                ), 1.0 * f
            )
        )
        Eln.instance.plateMachineRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Silicon Ingot", 4), findItemStack("Silicon Plate"),
                1.0 * f
            )
        )
        Eln.instance.plateMachineRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Alloy Ingot", Eln.instance.plateConversionRatio), findItemStack(
                    "Alloy Plate"
                ), 1.0 * f
            )
        )
        Eln.instance.plateMachineRecipes.addRecipe(
            Recipe(
                ItemStack(Items.IRON_INGOT, Eln.instance.plateConversionRatio),
                findItemStack("Iron Plate"), 1.0 * f
            )
        )
        Eln.instance.plateMachineRecipes.addRecipe(
            Recipe(
                ItemStack(Items.GOLD_INGOT, Eln.instance.plateConversionRatio),
                findItemStack("Gold Plate"), 1.0 * f
            )
        )
    }

    private fun recipeCompressor() {
        Eln.instance.compressorRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("4x Graphite Rods", 1), findItemStack(
                    "Synthetic " +
                            "Diamond"
                ), 80000.0
            )
        )
        Eln.instance.compressorRecipes.addRecipe(Recipe(Eln.findItemStack("Coal Dust", 4), findItemStack("Coal Plate"), 40000.0))
        Eln.instance.compressorRecipes.addRecipe(Recipe(Eln.findItemStack("Coal Plate", 4), findItemStack("Graphite Rod"), 80000.0))
        Eln.instance.compressorRecipes.addRecipe(Recipe(ItemStack(Blocks.SAND), findItemStack("Dielectric"), 2000.0))
        Eln.instance.compressorRecipes.addRecipe(Recipe(ItemStack(Blocks.OAK_LOG), findItemStack("Tree Resin"), 3000.0))
    }

    private fun recipeMagnetizer() {
        Eln.instance.magnetiserRecipes.addRecipe(
            Recipe(
                ItemStack(Items.IRON_INGOT, 2), arrayOf<ItemStack>(
                    findItemStack(
                        "Basic Magnet"
                    )
                ), 5000.0
            )
        )
        Eln.instance.magnetiserRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Alloy Ingot", 2), arrayOf<ItemStack>(
                    findItemStack(
                        "Advanced Magnet"
                    )
                ), 15000.0
            )
        )
        Eln.instance.magnetiserRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Copper Dust", 1),
                arrayOf<ItemStack>(ItemStack(Items.REDSTONE)), 5000.0
            )
        )
        Eln.instance.magnetiserRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Basic Magnet", 3), arrayOf<ItemStack>(
                    findItemStack(
                        "Optimal Ferromagnetic Core"
                    )
                ), 5000.0
            )
        )
        Eln.instance.magnetiserRecipes.addRecipe(
            Recipe(
                Eln.findItemStack("Inert Canister", 1),
                arrayOf<ItemStack>(ItemStack(Items.ENDER_PEARL)), 150000.0
            )
        )
    }

    private fun recipeFuelBurnerItem() {
        addRecipe(
            findItemStack("Small Fuel Burner"), "   ", " Cc", "   ", 'C', findItemStack("Combustion Chamber"),
            'c', findItemStack("Copper Thermal Cable")
        )
        addRecipe(
            findItemStack("Medium Fuel Burner"), "   ", " Cc", " C ", 'C', findItemStack("Combustion Chamber"),
            'c', findItemStack("Copper Thermal Cable")
        )
        addRecipe(
            findItemStack("Big Fuel Burner"), "   ", "CCc", "CC ", 'C', findItemStack("Combustion Chamber"),
            'c', findItemStack("Copper Thermal Cable")
        )
    }

    private fun recipeFurnace() {
        var `in`: ItemStack
        `in` = findItemStack("Copper Ore")
        addSmelting(`in`, findItemStack("Copper Ingot"))
        `in` = findItemStack("dustCopper")
        addSmelting(`in`, findItemStack("Copper Ingot"))
        `in` = findItemStack("Lead Ore")
        addSmelting(`in`, findItemStack("ingotLead"))
        `in` = findItemStack("dustLead")
        addSmelting(`in`, findItemStack("ingotLead"))
        `in` = findItemStack("Tungsten Ore")
        addSmelting(`in`, findItemStack("Tungsten Ingot"))
        `in` = findItemStack("Tungsten Dust")
        addSmelting(`in`, findItemStack("Tungsten Ingot"))
        `in` = findItemStack("dustIron")
        addSmelting(`in`, ItemStack(Items.IRON_INGOT))
        `in` = findItemStack("dustGold")
        addSmelting(`in`, ItemStack(Items.GOLD_INGOT))
        `in` = findItemStack("Tree Resin")
        addSmelting(`in`, Eln.findItemStack("Rubber", 2))
        `in` = findItemStack("Alloy Dust")
        addSmelting(`in`, findItemStack("Alloy Ingot"))
        `in` = findItemStack("Silicon Dust")
        addSmelting(`in`, findItemStack("Silicon Ingot"))
        `in` = findItemStack("dustCinnabar")
        addSmelting(`in`, findItemStack("Mercury"))
    }

    private fun recipeElectricalSensor() {
        addRecipe(
            Eln.findItemStack("Voltage Probe", 1), "SC", 'S', findItemStack("Electrical Probe Chip"), 'C',
            findItemStack("Signal Cable")
        )
        addRecipe(
            Eln.findItemStack("Electrical Probe", 1), "SCS", 'S', findItemStack("Electrical Probe Chip"), 'C',
            findItemStack("Signal Cable")
        )
    }

    private fun recipeThermalSensor() {
        addRecipe(
            Eln.findItemStack("Thermal Probe", 1), "SCS", 'S', findItemStack("Thermal Probe Chip"), 'C',
            findItemStack("Signal Cable")
        )
        addRecipe(
            Eln.findItemStack("Temperature Probe", 1), "SC", 'S', findItemStack("Thermal Probe Chip"), 'C',
            findItemStack("Signal Cable")
        )
    }

    private fun recipeTransporter() {
        addRecipe(
            Eln.findItemStack("Experimental Transporter", 1), "RMR", "RMR", " R ", 'M', findItemStack(
                "Advanced " +
                        "Machine Block"
            ), 'C', findItemStack("High Voltage Cable"), 'R', Eln.dictAdvancedChip
        )
    }

    private fun recipeTurret() {
        addRecipe(
            Eln.findItemStack("800V Defence Turret", 1), " R ", "CMC", " c ", 'M', findItemStack(
                "Advanced Machine " +
                        "Block"
            ), 'C', Eln.dictAdvancedChip, 'c', Eln.instance.highVoltageCableDescriptor.newItemStack(), 'R',
            ItemStack(Blocks.REDSTONE_BLOCK)
        )
    }

    private fun recipeMachine() {
        addRecipe(
            Eln.findItemStack("50V Macerator", 1), "IRI", "FMF", "IcI", 'M', findItemStack("Machine Block"), 'c',
            findItemStack("Electrical Motor"), 'F', ItemStack(Items.FLINT), 'I', findItemStack("Iron Cable"), 'R',
            ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("200V Macerator", 1), "ICI", "DMD", "IcI", 'M', findItemStack(
                "Advanced Machine " +
                        "Block"
            ), 'C', Eln.dictAdvancedChip, 'c', findItemStack("Advanced Electrical Motor"), 'D',
            ItemStack(Items.DIAMOND), 'I', "ingotAlloy"
        )
        addRecipe(
            Eln.findItemStack("50V Compressor", 1), "IRI", "FMF", "IcI", 'M', findItemStack("Machine Block"), 'c',
            findItemStack("Electrical Motor"), 'F', "plateIron", 'I', findItemStack("Iron Cable"), 'R',
            ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("200V Compressor", 1), "ICI", "DMD", "IcI", 'M', findItemStack(
                "Advanced Machine " +
                        "Block"
            ), 'C', Eln.dictAdvancedChip, 'c', findItemStack("Advanced Electrical Motor"), 'D', "plateAlloy", 'I',
            "ingotAlloy"
        )
        addRecipe(
            Eln.findItemStack("50V Plate Machine", 1), "IRI", "IMI", "IcI", 'M', findItemStack("Machine Block"),
            'c', findItemStack("Electrical Motor"), 'I', findItemStack("Iron Cable"), 'R', ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("200V Plate Machine", 1), "DCD", "DMD", "DcD", 'M', findItemStack(
                "Advanced Machine " +
                        "Block"
            ), 'C', Eln.dictAdvancedChip, 'c', findItemStack("Advanced Electrical Motor"), 'D', "plateAlloy", 'I',
            "ingotAlloy"
        )
        addRecipe(
            Eln.findItemStack("50V Magnetizer", 1), "IRI", "cMc", "III", 'M', findItemStack("Machine Block"), 'c',
            findItemStack("Electrical Motor"), 'I', findItemStack("Iron Cable"), 'R', ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("200V Magnetizer", 1), "ICI", "cMc", "III", 'M', findItemStack(
                "Advanced Machine " +
                        "Block"
            ), 'C', Eln.dictAdvancedChip, 'c', findItemStack("Advanced Electrical Motor"), 'I', "ingotAlloy"
        )
        addRecipe(
            Eln.findItemStack("Old 800V Arc Furnace", 1), "ICI", "DMD", "IcI", 'M', findItemStack(
                "Advanced Machine" +
                        " Block"
            ), 'C', findItemStack("3x Graphite Rods"), 'c', findItemStack("Synthetic Diamond"), 'D', "plateGold",
            'I', "ingotAlloy"
        )
    }

    private fun recipeFabricator() {
        addRecipe(
            findItemStack("Fabricator"), "PCP", "GMG", "PcP", 'P', "plateAlloy",
            'C', Eln.dictAdvancedChip, 'G', "blockGlass",
            'M', findItemStack("Advanced Machine Block"), 'c', findItemStack("Advanced Electrical Motor")
        )
        addShapelessRecipe(Eln.findItemStack("Silicon Wafer", 4), "plateSilicon")
    }

    private fun recipeRailroad() {
        addRecipe(
            findItemStack("Electric Minecart"), " c ", "PVP", "PmP", 'c', findItemStack("Copper Cable"), 'P', "plateIron",
            'V', ItemStack(Items.MINECART), 'm', findItemStack("Advanced Electrical Motor")
        )
        addRecipe(
            Eln.findItemStack("Overhead Lines", 4), "III", "I I", "CCC", 'I', findItemStack("Iron Cable"),
            'C', findItemStack("Copper Cable")
        )
    }

    private fun recipeElectricalGate() {
        addShapelessRecipe(findItemStack("Electrical Timer"), ItemStack(Items.REPEATER), Eln.dictCheapChip)
        addRecipe(
            Eln.findItemStack("Signal Processor", 1), "IcI", "cCc", "IcI", 'I', ItemStack(Items.IRON_INGOT),
            'c', findItemStack("Signal Cable"), 'C', Eln.dictCheapChip
        )
    }

    private fun recipeElectricalRedstone() {
        addRecipe(
            Eln.findItemStack("Redstone-to-Voltage Converter", 1), "TCS", 'S', findItemStack("Signal Cable"), 'C',
            Eln.dictCheapChip, 'T', ItemStack(Blocks.REDSTONE_TORCH)
        )
        addRecipe(
            Eln.findItemStack("Voltage-to-Redstone Converter", 1), "CTR", 'R', ItemStack(Items.REDSTONE), 'C',
            Eln.dictCheapChip, 'T', ItemStack(Blocks.REDSTONE_TORCH)
        )
    }

    private fun recipeElectricalEnvironmentalSensor() {
        addShapelessRecipe(
            findItemStack("Electrical Daylight Sensor"), ItemStack(Blocks.DAYLIGHT_DETECTOR),
            findItemStack("Redstone-to-Voltage Converter")
        )
        addShapelessRecipe(
            findItemStack("Electrical Light Sensor"), ItemStack(Blocks.DAYLIGHT_DETECTOR),
            ItemStack(Items.QUARTZ), findItemStack("Redstone-to-Voltage Converter")
        )
        addRecipe(
            findItemStack("Humidity Sensor"), " r ", "rRr", " r ", 'R', ItemStack(Items.REDSTONE),
            'r', "itemRubber"
        )
        addRecipe(
            findItemStack("Thermometer Sensor"), " q ", "rRr", " r ", 'R', ItemStack(Items.REDSTONE),
            'r', "itemRubber", 'q', ItemStack(Items.QUARTZ)
        )
        addShapelessRecipe(
            findItemStack("Electrical Weather Sensor"), findItemStack("Humidity Sensor"), ItemStack(Blocks.DAYLIGHT_DETECTOR),
            Eln.dictCheapChip
        )
        addRecipe(
            findItemStack("Electrical Anemometer Sensor"), " I ", " R ", "I I", 'R',
            ItemStack(Items.REDSTONE), 'I', findItemStack("Iron Cable")
        )
        addRecipe(
            findItemStack("Electrical Entity Sensor"), " G ", "GRG", " G ", 'G',
            ItemStack(Blocks.GLASS_PANE), 'R', ItemStack(Items.REDSTONE)
        )
        addRecipe(
            findItemStack("Electrical Fire Detector"), "cbr", "p p", "r r", 'c', findItemStack("Signal Cable"),
            'b', Eln.dictCheapChip, 'r', "itemRubber", 'p', "plateCopper"
        )
        addRecipe(
            findItemStack("Electrical Fire Buzzer"), "rar", "p p", "r r", 'a', Eln.dictAdvancedChip, 'r',
            "itemRubber", 'p', "plateCopper"
        )
        addShapelessRecipe(findItemStack("Scanner"), ItemStack(Items.COMPARATOR), Eln.dictAdvancedChip)
    }

    private fun recipeElectricalVuMeter() {
        addRecipe(
            Eln.findItemStack("Analog vuMeter", 1), "WWW", "RIr", "WSW", 'W', "plankWood",
            'R', ItemStack(Items.REDSTONE), 'I', findItemStack("Iron Cable"), 'r', ItemStack(Items.RED_DYE), 'S', findItemStack("Signal Cable")
        )
        addRecipe(
            Eln.findItemStack("LED vuMeter", 1), " W ", "WTW", " S ", 'W', "plankWood",
            'T', ItemStack(Blocks.REDSTONE_TORCH), 'S', findItemStack("Signal Cable")
        )
        addRecipe(
            Eln.findItemStack("Multicolor LED vuMeter", 1), " W ", "WRW", " S ", 'W', "plankWood",
            'R', ItemStack(Items.REDSTONE), 'S', findItemStack("Signal Cable")
        )
    }

    private fun recipeElectricalBreaker() {
        addRecipe(
            Eln.findItemStack("Electrical Breaker", 1), "crC", 'c', findItemStack("Overvoltage Protection"), 'C',
            findItemStack("Overheating Protection"), 'r', findItemStack("High Voltage Relay")
        )
    }

    private fun recipeFuses() {
        addRecipe(Eln.findItemStack("Electrical Fuse Holder", 1), "i", " ", "i", 'i', findItemStack("Iron Cable"))
        addRecipe(
            Eln.findItemStack("Lead Fuse for low voltage cables", 4), "rcr", 'r', findItemStack("itemRubber"), 'c',
            findItemStack("Low Voltage Cable")
        )
        addRecipe(
            Eln.findItemStack("Lead Fuse for medium voltage cables", 4), "rcr", 'r', findItemStack("itemRubber"),
            'c', findItemStack("Medium Voltage Cable")
        )
        addRecipe(
            Eln.findItemStack("Lead Fuse for high voltage cables", 4), "rcr", 'r', findItemStack("itemRubber"), 'c',
            findItemStack("High Voltage Cable")
        )
        addRecipe(
            Eln.findItemStack("Lead Fuse for very high voltage cables", 4), "rcr", 'r', findItemStack("itemRubber"),
            'c', findItemStack("Very High Voltage Cable")
        )
    }

    private fun recipeElectricalGateSource() {
        addRecipe(
            Eln.findItemStack("Signal Trimmer", 1), "RsR", "rRr", " c ", 'M', findItemStack("Machine Block"), 'c',
            findItemStack("Signal Cable"), 'r', "itemRubber", 's', ItemStack(Items.STICK), 'R',
            ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("Signal Switch", 3), " r ", "rRr", " c ", 'M', findItemStack("Machine Block"), 'c',
            findItemStack("Signal Cable"), 'r', "itemRubber", 'I', findItemStack("Iron Cable"), 'R',
            ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("Signal Button", 3), " R ", "rRr", " c ", 'M', findItemStack("Machine Block"), 'c',
            findItemStack("Signal Cable"), 'r', "itemRubber", 'I', findItemStack("Iron Cable"), 'R',
            ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("Wireless Switch", 3),
            " a ",
            "rCr",
            " r ",
            'M',
            findItemStack("Machine Block"),
            'c',
            findItemStack("Signal Cable"),
            'C',
            Eln.dictCheapChip,
            'a',
            findItemStack("Signal Antenna"),
            'r',
            "itemRubber",
            'I',
            findItemStack("Iron Cable"),
            'R',
            ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("Wireless Button", 3),
            " a ",
            "rCr",
            " R ",
            'M',
            findItemStack("Machine Block"),
            'c',
            findItemStack("Signal Cable"),
            'C',
            Eln.dictCheapChip,
            'a',
            findItemStack("Signal Antenna"),
            'r',
            "itemRubber",
            'I',
            findItemStack("Iron Cable"),
            'R',
            ItemStack(Items.REDSTONE)
        )
    }

    private fun recipeElectricalDataLogger() {
        addRecipe(
            Eln.findItemStack("Data Logger", 1), "RRR", "RGR", "RCR", 'R', "itemRubber", 'C', Eln.dictCheapChip, 'G',
            ItemStack(Blocks.GLASS_PANE)
        )
        addRecipe(
            Eln.findItemStack("Modern Data Logger", 1), "RRR", "RGR", "RCR", 'R', "itemRubber", 'C',
            Eln.dictAdvancedChip, 'G', ItemStack(Blocks.GLASS_PANE)
        )
        addRecipe(
            Eln.findItemStack("Industrial Data Logger", 1), "RRR", "GGG", "RCR", 'R', "itemRubber", 'C',
            Eln.dictAdvancedChip, 'G', ItemStack(Blocks.GLASS_PANE)
        )
    }

    private fun recipeSixNodeCache() {
    }

    private fun recipeElectricalAlarm() {
        addRecipe(
            Eln.findItemStack("Nuclear Alarm", 1), "ITI", "IMI", "IcI", 'c', findItemStack("Signal Cable"), 'T',
            ItemStack(Blocks.REDSTONE_TORCH), 'I', findItemStack("Iron Cable"), 'M', ItemStack(Blocks.NOTE_BLOCK)
        )
        addRecipe(
            Eln.findItemStack("Standard Alarm", 1), "MTM", "IcI", "III", 'c', findItemStack("Signal Cable"), 'T',
            ItemStack(Blocks.REDSTONE_TORCH), 'I', findItemStack("Iron Cable"), 'M', ItemStack(Blocks.NOTE_BLOCK)
        )
    }

    private fun recipeElectricalAntenna() {
        addRecipe(
            Eln.findItemStack("Low Power Transmitter Antenna", 1), "R i", "CI ", "R i", 'C', Eln.dictCheapChip, 'i',
            ItemStack(Items.IRON_INGOT), 'I', "plateIron", 'R', ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("Low Power Receiver Antenna", 1), "i  ", " IC", "i  ", 'C', Eln.dictCheapChip, 'I',
            "plateIron", 'i', ItemStack(Items.IRON_INGOT), 'R', ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("Medium Power Transmitter Antenna", 1), "c I", "CI ", "c I", 'C', Eln.dictAdvancedChip,
            'c', Eln.dictCheapChip, 'I', "plateIron", 'R', ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("Medium Power Receiver Antenna", 1), "I  ", " IC", "I  ", 'C', Eln.dictAdvancedChip, 'I',
            "plateIron", 'R', ItemStack(Items.REDSTONE)
        )

        addRecipe(
            Eln.findItemStack("High Power Transmitter Antenna", 1), "C I", "CI ", "C I", 'C', Eln.dictAdvancedChip, 'c',
            Eln.dictCheapChip, 'I', "plateIron", 'R', ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("High Power Receiver Antenna", 1), "I D", " IC", "I D", 'C', Eln.dictAdvancedChip, 'I',
            "plateIron", 'R', ItemStack(Items.REDSTONE), 'D', ItemStack(Items.DIAMOND)
        )
    }

    private fun recipeBatteryCharger() {
        addRecipe(
            Eln.findItemStack("Weak 50V Battery Charger", 1), "RIR", "III", "RcR", 'c', findItemStack(
                "Low Voltage " +
                        "Cable"
            ), 'I', findItemStack("Iron Cable"), 'R', ItemStack(Items.REDSTONE)
        )
        addRecipe(
            Eln.findItemStack("50V Battery Charger", 1), "RIR", "ICI", "RcR", 'C', Eln.dictCheapChip, 'c',
            findItemStack("Low Voltage Cable"), 'I', findItemStack("Iron Cable"), 'R', ItemStack(Items.REDSTONE)
        )

        addRecipe(
            Eln.findItemStack("200V Battery Charger", 1), "RIR", "ICI", "RcR", 'C', Eln.dictAdvancedChip, 'c',
            findItemStack("Medium Voltage Cable"), 'I', findItemStack("Iron Cable"), 'R', ItemStack(Items.REDSTONE)
        )
    }

    private fun recipeEggIncubator() {
        addRecipe(
            Eln.findItemStack("50V Egg Incubator", 1), "IGG", "E G", "CII", 'C', Eln.dictCheapChip, 'E', findItemStack(
                "Small 50V Tungsten Heating Element"
            ), 'I', ItemStack(Items.IRON_INGOT), 'G',
            ItemStack(Blocks.GLASS_PANE)
        )
    }

    private fun recipeEnergyConverter() {
        if (Eln.instance.ElnToOtherEnergyConverterEnable) {
            addRecipe(
                ItemStack(Eln.instance.elnToOtherBlockConverter), "III", "cCR", "III", 'C', Eln.dictAdvancedChip, 'c',
                findItemStack("High Voltage Cable"), 'I', findItemStack("Iron Cable"), 'R',
                ItemStack(Items.GOLD_INGOT)
            )
        }
    }

    private fun recipeComputerProbe() {
        if (Eln.instance.ComputerProbeEnable) {
            addRecipe(
                ItemStack(Eln.instance.computerProbeBlock), "cIw", "ICI", "WIc", 'C', Eln.dictAdvancedChip, 'c',
                findItemStack("Signal Cable"), 'I', findItemStack("Iron Cable"), 'w', findItemStack(
                    "Wireless Signal " +
                            "Receiver"
                ), 'W', findItemStack("Wireless Signal Transmitter")
            )
        }
    }

    private fun recipeArmor() {
        addRecipe(ItemStack(Eln.helmetCopper), "CCC", "C C", 'C', "ingotCopper")
        addRecipe(ItemStack(Eln.chestplateCopper), "C C", "CCC", "CCC", 'C', "ingotCopper")
        addRecipe(ItemStack(Eln.legsCopper), "CCC", "C C", "C C", 'C', "ingotCopper")
        addRecipe(ItemStack(Eln.bootsCopper), "C C", "C C", 'C', "ingotCopper")
    }

    private fun recipeTool() {
        addRecipe(ItemStack(Eln.shovelCopper), "i", "s", "s", 'i', "ingotCopper", 's', ItemStack(Items.STICK))
        addRecipe(ItemStack(Eln.axeCopper), "ii", "is", " s", 'i', "ingotCopper", 's', ItemStack(Items.STICK))
        addRecipe(ItemStack(Eln.hoeCopper), "ii", " s", " s", 'i', "ingotCopper", 's', ItemStack(Items.STICK))
        addRecipe(
            ItemStack(Eln.pickaxeCopper), "iii", " s ", " s ", 'i', "ingotCopper", 's',
            ItemStack(Items.STICK)
        )
        addRecipe(ItemStack(Eln.swordCopper), "i", "i", "s", 'i', "ingotCopper", 's', ItemStack(Items.STICK))
    }

    private fun recipeDisplays() {
        addRecipe(
            Eln.findItemStack("Digital Display", 1), "   ", "rrr", "iii", 'r', ItemStack(Items.REDSTONE), 'i',
            findItemStack("Iron Cable")
        )

        addRecipe(
            Eln.findItemStack("Nixie Tube", 1), " g ", "grg", "iii", 'g', ItemStack(Blocks.GLASS_PANE), 'r',
            ItemStack(Items.REDSTONE), 'i', findItemStack("Iron Cable")
        )
    }

    private fun recipeChristmas(){
        addShapelessRecipe(Eln.findItemStack("Christmas Tree", 1), findItemStack("String Lights"), ItemStack(Blocks.SPRUCE_SAPLING), findItemStack("String Lights"))
        addRecipe(
            Eln.findItemStack("Holiday Candle", 1), " g ", "gbg", " i ", 'g', ItemStack(Blocks.GLASS_PANE), 'b',
            findItemStack("200V LED Bulb"), 'i', "ingotIron"
        )
        addShapelessRecipe(Eln.findItemStack("String Lights", 2), findItemStack("200V LED Bulb"), "materialString")
    }

}
