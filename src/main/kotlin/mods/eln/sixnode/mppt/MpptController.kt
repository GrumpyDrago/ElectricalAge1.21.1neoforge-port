package mods.eln.sixnode.mppt

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.gui.GuiHelper
import mods.eln.gui.GuiScreenEln
import mods.eln.gui.GuiTextFieldEln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Obj3D
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeDescriptor
import mods.eln.node.six.SixNodeElement
import mods.eln.node.six.SixNodeElementRender
import mods.eln.node.six.SixNodeEntity
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.PowerSource
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.State
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sim.nbt.NbtElectricalGateOutput
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sixnode.protection.PartsDescriptor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.text.ParseException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Perturb and observe: every [PERIOD] it nudges the load it puts on the panel and keeps going while the power rises,
 * turning back when it falls. Its input is a resistance, which is what a buck converter looks like from the panel.
 */
class MpptLogic(val maxInputVoltage: Double, val maxInputCurrent: Double) {
    enum class Mode { IDLE, TRACKING, LIMITED, OVERVOLTAGE }

    var mode = Mode.IDLE
        private set
    /** The resistance it presents to the panel. */
    var inputResistance = PROBE_RESISTANCE
        private set
    private var direction = -1.0
    private var lastPower = 0.0
    private var timer = 0.0

    /** Advances by [dt]; returns true when [inputResistance] changed. [outputLimited] is true when the battery takes
     * less than it is given: full, or at the current limit. */
    fun step(dt: Double, inputVoltage: Double, outputLimited: Boolean): Boolean {
        timer += dt
        if (timer < PERIOD) return false
        timer = 0.0
        val before = inputResistance
        val power = inputVoltage * inputVoltage / inputResistance
        when {
            inputVoltage > maxInputVoltage -> {
                mode = Mode.OVERVOLTAGE
                inputResistance = MnaConst.highImpedance
            }
            inputVoltage < MIN_INPUT_VOLTAGE -> {
                mode = Mode.IDLE
                inputResistance = PROBE_RESISTANCE
            }
            outputLimited -> {
                // Take less from the panel by moving towards its open-circuit voltage.
                mode = Mode.LIMITED
                inputResistance *= 1.1
            }
            else -> {
                if (mode != Mode.TRACKING) direction = -1.0
                else if (power < lastPower) direction = -direction
                mode = Mode.TRACKING
                inputResistance *= 1 + STEP * direction
            }
        }
        if (mode != Mode.OVERVOLTAGE) {
            inputResistance = inputResistance.coerceIn(max(inputVoltage / maxInputCurrent, 0.01), PROBE_RESISTANCE * 10)
        }
        lastPower = power
        return inputResistance != before
    }

    fun writeToNBT(nbt: CompoundTag) {
        nbt.putDouble("inputResistance", inputResistance)
    }

    fun readFromNBT(nbt: CompoundTag) {
        if (nbt.contains("inputResistance")) inputResistance = nbt.getDouble("inputResistance")
        if (!(inputResistance > 0) || inputResistance >= MnaConst.highImpedance) inputResistance = PROBE_RESISTANCE
    }

    companion object {
        const val PERIOD = 0.1
        const val STEP = 0.03
        const val MIN_INPUT_VOLTAGE = 2.0
        const val PROBE_RESISTANCE = 1000.0

        /** What it charges each battery of a bank to in automatic mode, as a fraction of its nominal voltage. Fills
         * every chemistry to over 90 % without overcharging any, and matches the battery management system. */
        const val AUTO_CHARGE_RATIO = 1.08

        /**
         * How many batteries of [batteryVoltage] are in series, from the voltage of the bank at rest, as real
         * controllers detect a 12, 24 or 48 V system; 0 when there is no battery. Rest voltages of a bank of n run
         * from about 0.84 n (nearly flat) to 1.1 n (full). The ranges of n and n + 1 nearly meet, so it errs towards
         * reading a nearly flat bank as one battery short, which only keeps it from charging, rather than a full
         * one as one battery too many, which would overcharge it.
         */
        /**
         * The nominal voltage of the bank: a single half-voltage battery (the capacity oriented ones, 25 V) when it
         * is below what any 50 V battery gives, else [bankSize] batteries of [batteryVoltage]; 0 without a battery.
         */
        fun bankVoltage(restVoltage: Double, batteryVoltage: Double): Double {
            if (restVoltage < 0.3 * batteryVoltage) return 0.0
            if (restVoltage < 1.15 * batteryVoltage / 2) return batteryVoltage / 2
            return bankSize(restVoltage, batteryVoltage) * batteryVoltage
        }

        fun bankSize(restVoltage: Double, batteryVoltage: Double): Int {
            if (restVoltage < 0.3 * batteryVoltage) return 0
            return max(1, ceil(restVoltage / (1.15 * batteryVoltage)).toInt())
        }
    }
}

/** A [PowerSource] that also tells the voltage of what it charges without its own current: the battery at rest. */
class ChargerSource(name: String, pin: State) : PowerSource(name, pin) {
    /** The open-circuit voltage of the circuit it feeds, and the resistance it feeds it through. */
    var restVoltage = 0.0
        private set
    var restResistance = MnaConst.highImpedance
        private set
    private var maxVoltage = 0.0
    private var maxCurrent = 0.0

    override fun setMaximumVoltage(maximumVoltage: Double) {
        super.setMaximumVoltage(maximumVoltage)
        maxVoltage = maximumVoltage
    }

    override fun setMaximumCurrent(maximumCurrent: Double) {
        super.setMaximumCurrent(maximumCurrent)
        maxCurrent = maximumCurrent
    }

    override fun rootSystemPreStepProcess() {
        val t = aPin.subSystem.getTh(aPin, this)
        restVoltage = if (t.voltage.isNaN()) 0.0 else t.voltage
        restResistance = if (t.resistance.isNaN()) MnaConst.highImpedance else t.resistance
        var u = (sqrt(restVoltage * restVoltage + 4 * getPower() * restResistance) + restVoltage) / 2
        u = min(min(u, maxVoltage), restVoltage + restResistance * maxCurrent)
        if (u.isNaN()) u = 0.0
        if (u < restVoltage) u = restVoltage
        setVoltage(u)
    }
}

class MpptControllerDescriptor(
    name: String, obj: Obj3D,
    val maxInputVoltage: Double,
    val maxInputCurrent: Double,
    val maxOutputCurrent: Double,
    val defaultChargeVoltage: Double
) : PartsDescriptor(name, MpptControllerElement::class.java, MpptControllerRender::class.java, obj,
    listOf("case", "ledsun", "ledcharge", "ledfull")) {

    /** Efficiency at high load, and what it uses to run. */
    val efficiency = 0.975
    val standbyPower = 3.0

    fun draw(mode: MpptLogic.Mode) {
        val lit = HashSet<String>()
        if (mode != MpptLogic.Mode.IDLE) lit.add("ledsun")
        if (mode == MpptLogic.Mode.TRACKING && System.currentTimeMillis() / 500 % 2 == 0L) lit.add("ledcharge")
        if (mode == MpptLogic.Mode.LIMITED) lit.add("ledfull")
        drawParts(lit)
    }

    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.addAll(tr("Charges a battery from solar panels or a wind turbine\nat their maximum power point, whatever the battery voltage.").split("\n"))
        list.add(tr("Front: + of the panels or turbine. Back: + of the battery."))
        list.add(tr("Right: common negative, for the - of both. Left: power signal."))
        list.add(Utils.plotVolt(tr("Maximum input voltage: "), maxInputVoltage))
        list.add(Utils.plotAmpere(tr("Maximum output current: "), maxOutputCurrent))
        list.add(Utils.plotPercent(tr("Efficiency: "), efficiency))
    }
}

class MpptControllerElement(sixNode: SixNode, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElement(sixNode, side, descriptor), mods.eln.node.ComputerControllable {

    override fun computerValues(): Map<String, Any?> = linkedMapOf(
        "mode" to logic.mode.name.lowercase(),
        "inputVoltage" to inLoad.voltage,
        "inputPower" to inputPower,
        "outputVoltage" to outLoad.voltage,
        "outputPower" to outputPower,
        "chargeVoltage" to effectiveChargeVoltage,
        "batteries" to bankSize,
        "energy" to energy
    )
    private val desc = descriptor as MpptControllerDescriptor

    val inLoad = NbtElectricalLoad("inLoad")
    val outLoad = NbtElectricalLoad("outLoad")
    /** The common negative of panels and battery. It is tied to ground inside, as the input and output are
     * measured from ground, so grounded and floating batteries both work. */
    val negativeLoad = NbtElectricalLoad("negativeLoad")
    val inResistor = Resistor(inLoad, null)
    val outSource = ChargerSource("outSource", outLoad)
    val powerOut = NbtElectricalGateOutput("powerOut")
    val powerProcess = NbtElectricalGateOutputProcess("powerProcess", powerOut)
    val logic = MpptLogic(desc.maxInputVoltage, desc.maxInputCurrent)
    /** The voltage it charges to; 0 is automatic, from the batteries it finds. */
    var chargeVoltage = 0.0
    var currentLimit = desc.maxOutputCurrent
    /** Batteries in series found in automatic mode; 0 when none is connected. */
    var bankSize = 0
        private set
    /** The nominal voltage of the bank found in automatic mode; 0 when none is connected. */
    var bankVoltage = 0.0
        private set
    val effectiveChargeVoltage
        get() = if (chargeVoltage > 0) chargeVoltage
        else min(bankVoltage * MpptLogic.AUTO_CHARGE_RATIO, desc.maxInputVoltage)
    /** Whether a battery is on the output: false too when its negative is not connected. */
    val batteryConnected get() = outSource.restResistance < 1000 && outSource.restVoltage > 0.3 * Eln.LVU
    private var shownMode = logic.mode
    private var shownBank = 0
    private var commanded = 0.0

    /** Energy delivered to the battery, in J. */
    var energy = 0.0
        private set

    init {
        electricalLoadList.add(inLoad)
        electricalLoadList.add(outLoad)
        electricalLoadList.add(negativeLoad)
        electricalLoadList.add(powerOut)
        electricalComponentList.add(VoltageSource("ground", negativeLoad, null).setVoltage(0.0))
        electricalComponentList.add(inResistor)
        electricalComponentList.add(outSource)
        electricalComponentList.add(Resistor(outLoad, null).pullDown())
        electricalComponentList.add(powerProcess)
        electricalProcessList.add(IProcess { dt -> step(dt) })
    }

    val inputPower get() = inLoad.voltage * inLoad.voltage / inResistor.resistance
    val outputPower get() = abs(outSource.voltage * outSource.current)

    private fun step(dt: Double) {
        val delivered = outputPower
        energy += delivered * dt
        bankSize = if (batteryConnected) MpptLogic.bankSize(outSource.restVoltage, Eln.LVU) else 0
        bankVoltage = if (batteryConnected) MpptLogic.bankVoltage(outSource.restVoltage, Eln.LVU) else 0.0
        val target = effectiveChargeVoltage
        // With no battery there is nowhere to put the power: take little from the panels, as when full.
        val limited = target <= 0 || !batteryConnected || (commanded > 5 && delivered < 0.9 * commanded)
        if (logic.step(dt, inLoad.voltage, limited)) inResistor.resistance = logic.inputResistance
        commanded = if (target > 0) max(0.0, inputPower * desc.efficiency - desc.standbyPower) else 0.0
        outSource.power = commanded
        outSource.setMaximumVoltage(target)
        outSource.setMaximumCurrent(currentLimit)
        powerProcess.setOutputNormalized((delivered / (max(target, Eln.LVU) * desc.maxOutputCurrent)).coerceIn(0.0, 1.0))
        if (logic.mode != shownMode || bankSize != shownBank) {
            shownMode = logic.mode
            shownBank = bankSize
            needPublish()
        }
    }

    override fun initialize() {
        Eln.instance.meduimVoltageCableDescriptor.applyTo(inLoad)
        Eln.instance.meduimVoltageCableDescriptor.applyTo(outLoad)
        Eln.instance.meduimVoltageCableDescriptor.applyTo(negativeLoad)
        inResistor.resistance = logic.inputResistance
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int): ElectricalLoad? = when (lrdu) {
        front -> inLoad
        front.inverse() -> outLoad
        front.left() -> powerOut
        front.right() -> negativeLoad
        else -> null
    }

    override fun getConnectionMask(lrdu: LRDU): Int = when (lrdu) {
        front, front.inverse(), front.right() -> NodeBase.maskElectricalPower
        front.left() -> NodeBase.maskElectricalOutputGate
        else -> 0
    }

    override fun multiMeterString(): String = Utils.plotVolt("Uin:", inLoad.voltage) + Utils.plotPower("Pin:", inputPower) +
        Utils.plotVolt("Uout:", outLoad.voltage) + Utils.plotPower("Pout:", outputPower)

    override fun getWaila(): Map<String, String> {
        val info = LinkedHashMap<String, String>()
        info[tr("Mode")] = when (logic.mode) {
            MpptLogic.Mode.IDLE -> tr("Waiting for input")
            MpptLogic.Mode.OVERVOLTAGE -> tr("Input voltage too high")
            else -> when {
                !batteryConnected -> tr("No battery: connect its + to the back and its - to the right")
                logic.mode == MpptLogic.Mode.TRACKING -> tr("Tracking the maximum power point")
                else -> tr("Battery full or at the current limit")
            }
        }
        info[tr("Charge voltage")] = when {
            chargeVoltage > 0 -> Utils.plotVolt("", chargeVoltage)
            bankVoltage > 0 -> tr("Automatic: %1$ V for a %2$ V bank", "%.1f".format(effectiveChargeVoltage), "%.0f".format(bankVoltage))
            else -> tr("Automatic")
        }
        info[tr("Input")] = Utils.plotVolt("", inLoad.voltage) + Utils.plotPower(" ", inputPower)
        info[tr("Output")] = Utils.plotVolt("", outLoad.voltage) + Utils.plotPower(" ", outputPower)
        info[tr("Energy delivered")] = Utils.plotEnergy("", energy)
        return info
    }

    override fun hasGui() = true

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try {
            stream.writeByte(logic.mode.ordinal)
            stream.writeFloat(chargeVoltage.toFloat())
            stream.writeFloat(effectiveChargeVoltage.toFloat())
            stream.writeFloat(currentLimit.toFloat())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            when (stream.readByte().toInt()) {
                SET_CHARGE_VOLTAGE -> chargeVoltage = stream.readDouble()
                    .let { if (it > 0) it.coerceIn(1.0, desc.maxInputVoltage) else 0.0 }
                SET_CURRENT_LIMIT -> currentLimit = stream.readDouble().coerceIn(0.1, desc.maxOutputCurrent)
            }
            needPublish()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        logic.readFromNBT(nbt)
        // testing.1 saved a fixed 52.5 V by default; those become automatic.
        if (nbt.contains("chargeVoltage")) chargeVoltage = nbt.getDouble("chargeVoltage")
            .let { if (abs(it - Eln.LVU * 1.05) < 1e-6) 0.0 else it }
        if (nbt.contains("currentLimit")) currentLimit = nbt.getDouble("currentLimit")
        energy = nbt.getDouble("energy")
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        logic.writeToNBT(nbt)
        nbt.putDouble("chargeVoltage", chargeVoltage)
        nbt.putDouble("currentLimit", currentLimit)
        nbt.putDouble("energy", energy)
    }

    companion object {
        const val SET_CHARGE_VOLTAGE = 1
        const val SET_CURRENT_LIMIT = 2
    }
}

class MpptControllerRender(entity: SixNodeEntity, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElementRender(entity, side, descriptor) {
    private val desc = descriptor as MpptControllerDescriptor
    var mode = MpptLogic.Mode.IDLE
    var chargeVoltage = 0f
    var effectiveChargeVoltage = 0f
    var currentLimit = 0f

    override fun draw() {
        super.draw()
        front!!.glRotateOnX()
        desc.draw(mode)
    }

    override fun publishUnserialize(stream: DataInputStream) {
        super.publishUnserialize(stream)
        try {
            mode = MpptLogic.Mode.entries[stream.readByte().toInt()]
            chargeVoltage = stream.readFloat()
            effectiveChargeVoltage = stream.readFloat()
            currentLimit = stream.readFloat()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun getCableRender(lrdu: LRDU): CableRenderDescriptor? = when (lrdu) {
        front, front!!.inverse() -> Eln.instance.meduimVoltageCableDescriptor.render
        front!!.left() -> Eln.instance.signalCableDescriptor.render
        front!!.right() -> Eln.instance.meduimVoltageCableDescriptor.render
        else -> null
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen = MpptControllerGui(this)
}

class MpptControllerGui(private val render: MpptControllerRender) : GuiScreenEln() {
    private lateinit var voltage: GuiTextFieldEln
    private lateinit var current: GuiTextFieldEln

    override fun newHelper() = GuiHelper(this, 170, 58)

    override fun initGui() {
        super.initGui()
        voltage = newGuiTextField(112, 8, 50)
        voltage.setText(render.chargeVoltage)
        voltage.setObserver(this)
        voltage.setComment(tr("The battery voltage it charges up to.\n0 finds it from the batteries:\n1.08 times their voltage.").split("\n").toTypedArray())
        current = newGuiTextField(112, 26, 50)
        current.setText(render.currentLimit)
        current.setObserver(this)
        current.setComment(arrayOf(tr("The most current it gives the battery.")))
    }

    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y)
        helper.drawString(8, 10, 0x404040, tr("Charge voltage (V)"))
        helper.drawString(8, 28, 0x404040, tr("Current limit (A)"))
        helper.drawString(8, 44, 0x404040, when {
            render.chargeVoltage > 0 -> tr("Charging to %1$ V", "%.1f".format(render.chargeVoltage))
            render.effectiveChargeVoltage > 0 -> tr("Automatic: charging to %1$ V", "%.1f".format(render.effectiveChargeVoltage))
            else -> tr("Automatic: no battery found")
        })
    }

    override fun textFieldNewValue(textField: GuiTextFieldEln, value: String) {
        val v = try {
            NumberFormat.getInstance().parse(value).toDouble()
        } catch (_: ParseException) {
            return
        }
        if (textField === voltage) render.clientSetDouble(MpptControllerElement.SET_CHARGE_VOLTAGE.toByte(), v)
        if (textField === current) render.clientSetDouble(MpptControllerElement.SET_CURRENT_LIMIT.toByte(), v)
    }
}
