package mods.eln.config

import mods.eln.Eln
import mods.eln.Other
import mods.eln.misc.Utils
import java.util.UUID

/** Keeps the settings in fields, where most of the code looks for them. */
object ConfigHandler {
    fun apply(config: JsonConfig) {
        val eln = Eln.instance

        Eln.modbusEnable = config.getBooleanOrElse("integrations.modbus.enabled", false)
        Eln.modbusPort = config.getIntOrElse("integrations.modbus.port", 1502)
        Eln.mqttEnabled = config.getBooleanOrElse("integrations.mqtt.enabled", false)
        eln.ComputerProbeEnable = config.getBooleanOrElse("integrations.computerProbe.enabled", true)
        eln.ElnToOtherEnergyConverterEnable = config.getBooleanOrElse("integrations.energyExporter.enabled", true)

        Eln.debugEnabled = config.getBooleanOrElse("debug.logging.enabled", false)
        Eln.simSnapshotEnabled = config.getBooleanOrElse("debug.logging.simSnapshot", false)

        Eln.explosionEnable = config.getBooleanOrElse("gameplay.hazards.explosionsEnabled", false)
        Eln.arcFlashEnable = config.getBooleanOrElse("gameplay.hazards.arcFlashEnabled", true)
        Eln.enableFestivities = config.getBooleanOrElse("gameplay.seasonal.enableFestiveItems", true)
        Eln.verticalIronCableCrafting = config.getBooleanOrElse("gameplay.crafting.verticalIronCableCrafting", false)

        val watchdog = "simulation.watchdog.destruction"
        Eln.watchdogThermalEnabled = config.getBooleanOrElse("$watchdog.thermal", true)
        Eln.watchdogResistorHeatEnabled = config.getBooleanOrElse("$watchdog.resistorHeat", false)
        Eln.watchdogVoltageEnabled = config.getBooleanOrElse("$watchdog.voltage", true)
        Eln.watchdogShaftSpeedEnabled = config.getBooleanOrElse("$watchdog.shaftSpeed", true)
        Eln.watchdogOtherEnabled = config.getBooleanOrElse("$watchdog.other", true)

        Eln.versionCheckEnabled = config.getBooleanOrElse("updates.versionCheck.enabled", true)
        Eln.analyticsEnabled = config.getBooleanOrElse("analytics.enabled", true)
        Eln.analyticsURL = config.getStringOrElse("analytics.endpointUrl", "http://eln.ja13.org/stat")
        Eln.analyticsPlayerUUIDOptIn = config.getBooleanOrElse("analytics.playerUuidOptIn", false)
        if (Eln.analyticsEnabled) {
            if (config.getStringOrElse("analytics.playerUuid", "").isEmpty()) {
                config.setString("analytics.playerUuid", UUID.randomUUID().toString())
            }
            Eln.playerUUID = config.getStringOrElse("analytics.playerUuid", "")
        }

        Eln.directPoles = config.getBooleanOrElse("world.poles.directPlacementEnabled", true)

        val generators = "balance.generators"
        eln.heatTurbinePowerFactor = config.getDoubleOrElse("$generators.heatTurbinePowerFactor", 1.0)
        eln.solarPanelPowerFactor = config.getDoubleOrElse("$generators.solarPanelPowerFactor", 1.0)
        eln.windTurbinePowerFactor = config.getDoubleOrElse("$generators.windTurbinePowerFactor", 1.0)
        eln.waterTurbinePowerFactor = config.getDoubleOrElse("$generators.waterTurbinePowerFactor", 1.0)
        eln.fuelGeneratorPowerFactor = config.getDoubleOrElse("$generators.fuelGeneratorPowerFactor", 1.0)
        eln.fuelHeatFurnacePowerFactor = config.getDoubleOrElse("$generators.fuelHeatFurnacePowerFactor", 1.0)
        eln.autominerRange = config.getIntOrElse("machines.autominer.maxRangeBlocks", 10)
        eln.fuelGeneratorTankCapacity = config.getDoubleOrElse("machines.fuelGenerator.tankCapacitySecondsAtNominalPower", 20.0 * 60.0)

        Other.wattsToEu = config.getDoubleOrElse("balance.integrationConversion.wattsToEu", 1.0 / 3.0)
        Other.wattsToOC = config.getDoubleOrElse("balance.integrationConversion.wattsToOc", 1.0 / 3.0 / 2.5)
        Other.wattsToRf = config.getDoubleOrElse("balance.integrationConversion.wattsToRf", 1.0 / 3.0 * 4)
        eln.plateConversionRatio = config.getIntOrElse("balance.materials.platesPerIngot", 1)
        Eln.shaftEnergyFactor = config.getDoubleOrElse("balance.mechanics.shaftEnergyFactor", 0.05)
        Eln.flywheelMass = config.getDoubleOrElse("balance.mechanics.flywheelMass", 50.0).coerceIn(1.0, 1000.0)
        Eln.cablePowerFactor = config.getDoubleOrElse("balance.cables.powerFactor", 1.0).coerceIn(0.5, 4.0)

        eln.stdBatteryHalfLife = config.getDoubleOrElse("items.batteries.standardHalfLifeDays", 2.0) * Utils.minecraftDay
        eln.batteryCapacityFactor = config.getDoubleOrElse("balance.storage.batteryCapacityFactor", 1.0)

        eln.replicatorPop = config.getBooleanOrElse("entities.replicator.enabled", false)
        eln.maxReplicators = config.getIntOrElse("entities.replicator.maxCount", 100)
        eln.killMonstersAroundLamps = config.getBooleanOrElse("entities.mobSpawning.preventNearLamps", true)
        eln.killMonstersAroundLampsRange = config.getIntOrElse("entities.mobSpawning.preventNearLampsRange", 9)

        Eln.genCopper = config.getBooleanOrElse("worldgen.ores.copper.enabled", true)
        Eln.genLead = config.getBooleanOrElse("worldgen.ores.lead.enabled", true)
        Eln.genTungsten = config.getBooleanOrElse("worldgen.ores.tungsten.enabled", true)
        Eln.genCinnabar = false

        Eln.oredictTungsten = config.getBooleanOrElse("integrations.oredict.tungstenEnabled", false)
        Eln.dictTungstenOre = if (Eln.oredictTungsten) "oreTungsten" else "oreElnTungsten"
        Eln.dictTungstenDust = if (Eln.oredictTungsten) "dustTungsten" else "dustElnTungsten"
        Eln.dictTungstenIngot = if (Eln.oredictTungsten) "ingotTungsten" else "ingotElnTungsten"
        Eln.oredictChips = config.getBooleanOrElse("integrations.oredict.chipsEnabled", true)
        Eln.dictCheapChip = if (Eln.oredictChips) "circuitBasic" else "circuitElnBasic"
        Eln.dictAdvancedChip = if (Eln.oredictChips) "circuitAdvanced" else "circuitElnAdvanced"

        fun life(lamp: String, default: Double) = config.getDoubleOrElse("lighting.lamps.$lamp.nominalLifeInHours", default)
        fun infinite(lamp: String) = config.getBooleanOrElse("lighting.lamps.$lamp.infiniteLife", false)
        eln.incandescentLampLife = life("incandescent", 16.0)
        eln.economicLampLife = life("economic", 64.0)
        eln.carbonLampLife = life("carbon", 6.0)
        eln.ledLampLife = life("led", 512.0)
        eln.halogenLampLife = life("halogen", 128.0)
        Eln.incandescentLampInfiniteLife = infinite("incandescent")
        Eln.ecoLampInfiniteLife = infinite("economic")
        Eln.ledLampInfiniteLife = infinite("led")
        Eln.halogenLampInfiniteLife = infinite("halogen")
        Eln.allowSwingingLamps = config.getBooleanOrElse("lighting.swingingLamps", true)

        eln.addOtherModOreToXRay = config.getBooleanOrElse("tools.xrayScanner.autoDiscoverOreDictionaryOres", true)
        eln.xRayScannerRange = config.getDoubleOrElse("tools.xrayScanner.rangeBlocks", 5.0).coerceIn(4.0, 10.0).toFloat()
        eln.xRayScannerCanBeCrafted = config.getBooleanOrElse("tools.xrayScanner.canBeCrafted", true)

        eln.electricalFrequency = config.getDoubleOrElse("simulation.electrical.frequency", 20.0)
        eln.electricalInterSystemOverSampling = config.getIntOrElse("simulation.electrical.interSystemOverSampling", 50)
        eln.thermalFrequency = config.getDoubleOrElse("simulation.thermal.frequency", 400.0)
        Eln.roomMaxAxisSpanBlocks = config.getIntOrElse("simulation.roomDetection.maxAxisSpanBlocks", 24)
        Eln.roomMaxVolumeBlocks = config.getIntOrElse("simulation.roomDetection.maxVolumeBlocks", 4096)
        Eln.cableThermalSpikeLimiterEnabled = config.getBooleanOrElse("simulation.thermal.cableSpikeLimiter.enabled", true)
        Eln.cableThermalSpikeLimitFactor = config.getDoubleOrElse("simulation.thermal.cableSpikeLimiter.factor", 20.0)
        Eln.lavaAmbientRampEnabled = config.getBooleanOrElse("simulation.thermal.ambient.lavaRampEnabled", true)
        Eln.undergroundBiomeTemperatureMultiplier =
            config.getDoubleOrElse("simulation.thermal.ambient.undergroundBiomeTemperatureMultiplier", 0.2)

        Eln.wirelessTxRange = config.getIntOrElse("wireless.transmitter.maxRangeBlocks", 32)

        Eln.wailaEasyMode = config.getBooleanOrElse("ui.waila.easyMode", false)
        Eln.noSymbols = config.getBooleanOrElse("ui.icons.noSymbols", false)
        Eln.noVoltageBackground = config.getBooleanOrElse("ui.icons.noVoltageBackground", false)
        Eln.maxSoundDistance = config.getDoubleOrElse("ui.audio.maxSoundDistance", 16.0)
        Eln.soundChannels = config.getIntOrElse("ui.audio.soundChannels", 200)
    }
}
