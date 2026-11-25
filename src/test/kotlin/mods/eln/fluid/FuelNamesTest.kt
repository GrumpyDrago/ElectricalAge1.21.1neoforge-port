package mods.eln.fluid

import mods.eln.Eln
import mods.eln.config.JsonConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FuelNamesTest {
    private fun registry(): FuelRegistry {
        val file = Files.createTempDirectory("eln-fuel-names-test").toFile().resolve("Eln.cfg")
        Eln.config = JsonConfig(file).also { it.load() }
        FuelRegistry.init(file)
        return FuelRegistry
    }

    private fun names() = with(registry()) { FuelNames((dieselList + gasolineList + gasList + steamList).toList()) }

    @Test
    fun fluidsOfOtherModsAreRecognised() {
        val names = names()
        fun expect(name: String?, id: String, vararg tags: String) = assertEquals(name, names.find(id, tags.toList()), id)

        for (mod in listOf("immersivepetroleum", "mekanismdieselgenerators", "modern_industrialization", "pneumaticcraft")) {
            expect("diesel", "$mod:diesel", "c:diesel")
        }
        expect("diesel", "oritech:still_diesel", "c:diesel")
        expect("diesel_sulfur", "immersivepetroleum:diesel_sulfur", "c:diesel", "c:diesel_sulfur")
        expect("gasoline", "immersivepetroleum:gasoline", "c:gasoline")
        expect("biofuel", "oritech:still_biofuel", "c:biodiesel", "c:biofuel")
        expect("biofuel", "xycraft_machines:bio_fuel")
        expect("bioethanol", "createaddition:bioethanol", "c:biofuel")
        expect("oil", "immersivepetroleum:crudeoil", "c:crude_oil")
        expect("oil", "mekanismdieselgenerators:crude_oil", "c:crude_oil", "c:oil")
        expect("aromatics", "immersivepetroleum:benzol", "c:benzol")
        expect("lpg", "immersivepetroleum:petroleum_gas", "c:petroleum_gas", "c:gaseous")
        expect("naturalgas", "mekanismdieselgenerators:methane", "c:methane")
        expect("naturalgas", "mekanism:ethene", "c:ethene")
        expect("heavyoil", "modern_industrialization:heavy_fuel", "c:heavy_fuel")
        expect("plantoil", "immersiveengineering:plantoil", "c:plantoil")
        expect("steam", "bigreactors:steam", "c:steam")
        expect("rocket_fuel", "enderio:rocket_fuel")
        // Only known by their tag.
        expect("gasoline", "somemod:go_juice", "c:gasoline")
        expect("diesel", "somemod:go_juice", "c:fuels/diesel")
    }

    @Test
    fun otherFluidsAreNoFuel() {
        val names = names()
        for (id in listOf("minecraft:water", "minecraft:lava", "eln:hot_water", "mekanism:brine", "create:chocolate")) {
            assertEquals(null, names.find(id, listOf("minecraft:water", "c:water", "c:gaseous", "somemod:diesel")), id)
        }
    }

    @Test
    fun configurationCanNameFluidsById() {
        assertEquals("some_mod:juice", FuelNames(listOf("diesel", "some_mod:juice")).find("some_mod:juice", listOf("c:diesel")))
    }

    @Test
    fun everyFuelHasEnergyUnderItsName() {
        with(registry()) {
            for (name in dieselList + gasolineList + gasList + steamList) assertTrue(heatEnergyPerMilliBucket(name) > 0, name)
        }
    }

    @Test
    fun enginesTakeRefinedDiesel() {
        val engine = registry().engineList
        for (name in listOf("gasoline", "kerosene", "ethanol", "diesel", "biodiesel", "diesel_sulfur", "high_power_biodiesel")) {
            assertNotNull(engine.firstOrNull { it == name }, name)
        }
        for (name in listOf("oil", "creosote", "bitumen", "heavyoil", "plantoil")) assertTrue(name !in engine, name)
    }
}
