package mods.eln.sim.renewable

import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.PowerSource
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sim.mna.process.PowerSourceBipole
import mods.eln.sim.mna.state.State
import net.minecraft.nbt.CompoundTag
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * The current-voltage curve of a photovoltaic panel, by the usual single-exponential approximation: nearly the
 * short-circuit current up to the knee, then falling to nothing at the open-circuit voltage. Light scales the
 * current and, logarithmically, the voltage.
 */
class PvCurve(val openCircuitVoltage: Double, val maximumPower: Double) {
    /** The width of the knee; 0.07 of the open-circuit voltage gives a fill factor near 0.76, as silicon panels have. */
    private val knee = SHAPE * openCircuitVoltage
    /** Short-circuit current in full light, chosen so that the maximum power point is [maximumPower]. */
    val shortCircuitCurrent: Double

    init {
        var best = 0.0
        for (i in 1 until 1000) {
            val v = openCircuitVoltage * i / 1000
            best = max(best, v * normalisedCurrent(v, openCircuitVoltage))
        }
        shortCircuitCurrent = maximumPower / best
    }

    private fun normalisedCurrent(v: Double, voc: Double): Double {
        if (voc <= 0) return 0.0
        return (1 - (exp(v / knee) - 1) / (exp(voc / knee) - 1)).coerceAtLeast(0.0)
    }

    /** Open-circuit voltage at a [light] level from 0 to 1. */
    fun openCircuitVoltage(light: Double): Double =
        if (light < 1e-3) 0.0 else (openCircuitVoltage * (1 + LIGHT_VOLTAGE * ln(light))).coerceAtLeast(0.0)

    fun current(v: Double, light: Double): Double = shortCircuitCurrent * light * normalisedCurrent(v, openCircuitVoltage(light))

    /** The voltage and power at the maximum power point. */
    fun maximumPowerPoint(light: Double): Pair<Double, Double> {
        val voc = openCircuitVoltage(light)
        var bestV = 0.0
        var bestP = 0.0
        for (i in 1 until 400) {
            val v = voc * i / 400
            val p = v * current(v, light)
            if (p > bestP) {
                bestP = p
                bestV = v
            }
        }
        return bestV to bestP
    }

    companion object {
        const val SHAPE = 0.07
        const val LIGHT_VOLTAGE = 0.05
    }
}

/**
 * A solar panel that follows a [PvCurve] instead of giving its power at any voltage. Connected straight to a
 * battery it works at the battery's voltage, below its maximum power point; an MPPT charge controller gets the rest.
 *
 * [setPower] still takes the power the panel would make in this light at its maximum power point.
 */
class PvCurveSource(
    private val aPin: State, private val bPin: State,
    private val aSrc: VoltageSource, private val bSrc: VoltageSource,
    openCircuitVoltage: Double, maximumPower: Double
) : PowerSourceBipole(aPin, bPin, aSrc, bSrc) {
    val curve = PvCurve(openCircuitVoltage, maximumPower)
    private var light = 0.0
    var voltage = 0.0
        private set
    var current = 0.0
        private set

    override fun setPower(P: Double) {
        super.setPower(P)
        light = (P / curve.maximumPower).coerceIn(0.0, 2.0)
    }

    /** What it makes now, rather than what it could. */
    override fun getPower(): Double = voltage * current

    override fun rootSystemPreStepProcess() {
        val a = aPin.subSystem.getTh(aPin, aSrc)
        val b = bPin.subSystem.getTh(bPin, bSrc)
        if (a.voltage.isNaN()) { a.voltage = 0.0; a.resistance = MnaConst.highImpedance }
        if (b.voltage.isNaN()) { b.voltage = 0.0; b.resistance = MnaConst.highImpedance }
        val vth = a.voltage - b.voltage
        val rth = max(a.resistance + b.resistance, 1e-6)
        val voc = curve.openCircuitVoltage(light)

        // The circuit takes (V - vth) / rth; the panel gives current(V). Find where they meet.
        fun excess(v: Double) = curve.current(v, light) - (v - vth) / rth
        val v = when {
            // Pushed above its open-circuit voltage: the blocking diode stops any current.
            vth >= voc -> vth
            // Pulled below zero: the bypass diode holds it there.
            excess(0.0) <= 0 -> 0.0
            else -> {
                var lo = 0.0
                var hi = voc
                repeat(40) {
                    val mid = (lo + hi) / 2
                    if (excess(mid) > 0) lo = mid else hi = mid
                }
                (lo + hi) / 2
            }
        }
        val i = if (vth >= voc) 0.0 else (vth - v) / rth
        aSrc.voltage = a.voltage - i * a.resistance
        bSrc.voltage = b.voltage + i * b.resistance
        voltage = v
        current = -i
    }
}

/**
 * The power coefficient of a three-bladed rotor against tip speed ratio and pitch, by the usual empirical fit
 * (Heier). It peaks near 0.48 at a tip speed ratio of 8 with the blades flat.
 */
object RotorAerodynamics {
    fun powerCoefficient(tipSpeedRatio: Double, pitchDegrees: Double): Double {
        if (tipSpeedRatio <= 0) return 0.0
        val b = pitchDegrees
        val inv = 1 / (tipSpeedRatio + 0.08 * b) - 0.035 / (b * b * b + 1)
        if (inv <= 0) return 0.0
        val cp = 0.5176 * (116 * inv - 0.4 * b - 5) * exp(-21 * inv) + 0.0068 * tipSpeedRatio
        return cp.coerceAtLeast(0.0)
    }

    const val OPTIMAL_TIP_SPEED_RATIO = 8.1
    const val AIR_DENSITY = 1.225
    const val MAX_CP = 0.48
}

/**
 * A wind turbine with a permanent-magnet generator: the rotor speeds up until the torque of the generator, which
 * grows with the current it delivers, balances that of the wind. The blades pitch to hold the rated speed in strong
 * wind and feather above the cut-out speed.
 */
class PmgWindSource(
    name: String, pin: State,
    /** Electrical power at [ratedWind], in W. */
    val ratedPower: Double,
    val ratedWind: Double,
    /** Voltage of the battery bank it is made for. */
    val systemVoltage: Double,
    val cutOutWind: Double
) : PowerSource(name, pin) {
    /** Sized so that the rotor gives the rated power at the rated wind at the best tip speed ratio. */
    val radius = Math.sqrt(ratedPower / (0.5 * RotorAerodynamics.AIR_DENSITY * ratedWind.pow(3) * RotorAerodynamics.MAX_CP * 0.92) / PI)
    val ratedSpeed = RotorAerodynamics.OPTIMAL_TIP_SPEED_RATIO * ratedWind / radius
    /** EMF per rad/s: 1.4 times the system voltage at the rated speed. */
    private val emfConstant = 1.4 * systemVoltage / ratedSpeed
    private val windingResistance = 0.08 * systemVoltage * systemVoltage / ratedPower
    /** Inertia for a time constant of a few seconds. */
    private val inertia = 2 * 4.0 * ratedPower / (ratedSpeed * ratedSpeed)

    var wind = 0.0
        private set
    var speed = 0.0
        private set
    var pitch = 0.0
        private set
    /** The current it delivers; the voltage source's own current has the MNA's sign. */
    var outputCurrent = 0.0
        private set
    private var gust = 0.0
    private val random = Random(System.nanoTime())

    fun setWind(meanWind: Double) {
        wind = max(0.0, meanWind * (1 + gust))
    }

    val emf get() = emfConstant * speed

    override fun rootSystemPreStepProcess() {
        val t = aPin.subSystem.getTh(aPin, this)
        val e = emf
        // A rectifier: current only flows out.
        if (t.voltage.isNaN() || e <= t.voltage) {
            outputCurrent = 0.0
            setVoltage(if (t.voltage.isNaN()) 0.0 else t.voltage)
            return
        }
        outputCurrent = (e - t.voltage) / (windingResistance + t.resistance)
        setVoltage(t.voltage + outputCurrent * t.resistance)
    }

    /** The mechanics, once per electrical step. */
    val rotor = IProcess { dt -> stepRotor(dt) }

    private fun stepRotor(dt: Double) {
        gust = (gust * (1 - 0.2 * dt) + (random.nextDouble() - 0.5) * 0.08 * Math.sqrt(dt)).coerceIn(-0.3, 0.3)
        val area = PI * radius * radius
        val feathered = wind > cutOutWind
        // Pitch control: hold the rated speed, feather in a storm, flatten out when it is slow.
        val targetPitch = when {
            feathered -> 90.0
            speed > ratedSpeed * 1.02 -> pitch + 25 * dt * (speed / ratedSpeed - 1) * 10
            else -> pitch - 4 * dt
        }
        pitch = targetPitch.coerceIn(0.0, 90.0)
        val lambda = if (wind > 0.1) speed * radius / wind else 0.0
        val aeroPower = 0.5 * RotorAerodynamics.AIR_DENSITY * area * wind.pow(3) * RotorAerodynamics.powerCoefficient(lambda, pitch)
        // The fit gives almost nothing at low tip speed ratios, where real blades still give start-up torque.
        val startTorque = 0.02 * area * wind * wind * (1 - pitch / 90) * max(0.0, 1 - lambda / 4)
        val aeroTorque = (if (speed > 0.5) aeroPower / speed else 0.0) + startTorque
        val generatorTorque = emfConstant * outputCurrent
        val friction = 0.01 * ratedPower / ratedSpeed * (if (speed > 0) 1 else 0) + (if (feathered) 0.2 * ratedPower / ratedSpeed else 0.0)
        speed = max(0.0, speed + (aeroTorque - generatorTorque - friction) / inertia * dt)
        speed = min(speed, ratedSpeed * 2)
    }

    val electricalPower get() = abs(getVoltage() * outputCurrent)

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        super.readFromNBT(nbt, str)
        speed = nbt.getDouble(str + "rotorSpeed")
        pitch = nbt.getDouble(str + "rotorPitch")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        super.writeToNBT(nbt, str)
        nbt.putDouble(str + "rotorSpeed", speed)
        nbt.putDouble(str + "rotorPitch", pitch)
    }
}
