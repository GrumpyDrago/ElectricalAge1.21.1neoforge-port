package mods.eln.transparentnode.battery

import mods.eln.i18n.I18N.tr
import mods.eln.misc.FunctionTable
import mods.eln.server.SaveConfig
import mods.eln.sim.IProcess
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * How a kind of battery behaves beyond its capacity and power: the shape of its voltage over the charge, how
 * temperature and state of charge wear it, how cold raises its internal resistance, and whether it can run away.
 *
 * Voltages are fractions of the nominal voltage, at charges of 0, 0.1, ..., 1.2 (past 1 is overcharge).
 */
enum class BatteryChemistry(
    val renderSpec: String,
    voltageCurve: DoubleArray,
    /** Internal resistance at -20 °C, as a multiple of the one at 20 °C. */
    private val coldResistance: Double,
    /** Extra wear at high charge, at full charge, as a multiple of the wear at nominal current. */
    private val highChargeWear: Double,
    /** Extra wear when left nearly empty, at no charge (sulphation of lead-acid cells). */
    private val deepDischargeWear: Double,
    /** Charging below freezing plates lithium on the anode. */
    val coldChargeDamage: Boolean,
    /** Above this temperature the cells heat themselves up; null for chemistries that don't run away. */
    val runawayTemperature: Double?
) {
    LEAD_ACID("leadacid", doubleArrayOf(0.0, 0.8, 0.88, 0.92, 0.95, 0.97, 0.99, 1.01, 1.03, 1.045, 1.06, 1.15, 1.4),
        3.0, 0.0, 3.0, false, null),
    NICKEL_CADMIUM("nicd", doubleArrayOf(0.0, 0.85, 0.94, 0.97, 0.98, 0.99, 1.0, 1.01, 1.02, 1.035, 1.06, 1.18, 1.45),
        1.5, 0.0, 0.0, false, null),
    LITHIUM_ION("liion", doubleArrayOf(0.0, 0.84, 0.92, 0.95, 0.97, 0.99, 1.0, 1.02, 1.04, 1.07, 1.1, 1.22, 1.5),
        4.0, 1.0, 0.5, true, 80.0),
    LITHIUM_IRON_PHOSPHATE("lifepo4", doubleArrayOf(0.0, 0.88, 0.985, 0.995, 1.0, 1.005, 1.01, 1.015, 1.02, 1.03, 1.05, 1.2, 1.5),
        5.0, 0.2, 0.2, true, null);

    val voltageFunction = FunctionTable(voltageCurve, 1.2)

    fun displayName(): String = when (this) {
        LEAD_ACID -> tr("Lead-acid")
        NICKEL_CADMIUM -> tr("Nickel-cadmium")
        LITHIUM_ION -> tr("Lithium-ion")
        LITHIUM_IRON_PHOSPHATE -> tr("Lithium iron phosphate")
    }

    /** How much higher the internal resistance is at [celsius] than at room temperature. */
    fun resistanceFactor(celsius: Double): Double =
        if (celsius >= 20) 1.0 else 1 + (coldResistance - 1) * ((20 - celsius) / 40).coerceAtMost(2.0)

    /**
     * Wear beyond that of the current, as a multiple of the wear at nominal current: chemical ageing doubles every
     * 10 °C above 25 °C (Arrhenius), and the state of charge adds its own.
     */
    fun extraWear(celsius: Double, charge: Double, charging: Boolean): Double {
        var wear = if (celsius > 25) 2.0.pow((celsius - 25) / 10) - 1 else 0.0
        if (charge > 0.9) wear += highChargeWear * (charge - 0.9) / 0.1
        if (charge < 0.3) wear += deepDischargeWear * (0.3 - charge) / 0.3
        if (coldChargeDamage && charging && celsius < 0) wear += 4.0
        return wear
    }

    /** Heat the cells make themselves above [runawayTemperature], in W, for a battery of [nominalPower]. */
    fun runawayPower(celsius: Double, nominalPower: Double): Double {
        val onset = runawayTemperature ?: return 0.0
        if (celsius <= onset) return 0.0
        return nominalPower * 0.02 * 2.0.pow((celsius - onset) / 8)
    }

    fun addInformation(list: MutableList<String>, nominalVoltage: Double) {
        list.add(tr("Chemistry: %1$", displayName()))
        list.add(tr("Full at %1$ V, empty at %2$ V", "%.1f".format(voltageFunction.getValue(1.0) * nominalVoltage),
            "%.1f".format(voltageFunction.getValue(0.1) * nominalVoltage)))
        when (this) {
            LEAD_ACID -> list.add(tr("Cheap and sturdy; wears out when left flat."))
            NICKEL_CADMIUM -> list.add(tr("High current, flat voltage, works in the cold."))
            LITHIUM_ION -> {
                list.add(tr("Stores the most; can run away above %1$ °C.", runawayTemperature!!.toInt()))
                list.add(tr("Do not charge below freezing."))
            }
            LITHIUM_IRON_PHOSPHATE -> list.add(tr("Very long life and safe; very flat voltage."))
        }
    }
}

/** What a battery of a [BatteryChemistry] does on top of the usual battery: temperature, wear and runaway. */
class BatteryChemistryProcess(private val battery: BatteryElement, private val chemistry: BatteryChemistry) : IProcess {
    private var appliedFactor = 1.0
    private var ambient = 20.0
    private var ambientAge = AMBIENT_REFRESH

    /** The temperature of the cells, in °C. */
    var celsius = 20.0
        private set

    override fun process(time: Double) {
        if (ambientAge++ >= AMBIENT_REFRESH) {
            ambientAge = 0
            ambient = try {
                battery.getAmbientTemperatureCelsius()
            } catch (_: IllegalStateException) {
                ambient
            }
        }
        celsius = battery.thermalLoad.temperatureCelsius + ambient
        val d = battery.descriptor

        val factor = chemistry.resistanceFactor(celsius)
        if (abs(factor / appliedFactor - 1) > 0.05) {
            appliedFactor = factor
            battery.positiveLoad.serialResistance = d.electricalRs * factor
            battery.negativeLoad.serialResistance = d.electricalRs * factor
        }

        val process = battery.batteryProcess
        if (d.lifeEnable && SaveConfig.instance?.batteryAging != false) {
            val charging = process.dischargeCurrent < -0.01 * d.electricalStdP / d.electricalU
            val wear = chemistry.extraWear(celsius, process.charge, charging)
            if (wear > 0) process.changeLife(max(0.1, process.life - wear * d.lifeNominalLost * time))
        }

        val runaway = chemistry.runawayPower(celsius, d.electricalStdP)
        if (runaway > 0) battery.thermalLoad.movePowerTo(runaway)
    }

    companion object {
        const val AMBIENT_REFRESH = 20
    }
}
