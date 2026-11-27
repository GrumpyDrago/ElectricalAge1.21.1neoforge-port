package mods.eln.gridnode.substation

import mods.eln.Eln
import mods.eln.compat.client.GL11
import mods.eln.gridnode.GridDescriptor
import mods.eln.gridnode.GridElement
import mods.eln.gridnode.GridRender
import mods.eln.gui.GuiButtonEln
import mods.eln.gui.GuiHelper
import mods.eln.gui.GuiScreenEln
import mods.eln.gui.IGuiObject
import mods.eln.i18n.I18N.tr
import mods.eln.item.Lockable
import mods.eln.item.Lockout
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Obj3D
import mods.eln.misc.Utils
import mods.eln.misc.UtilsClient
import mods.eln.node.ComputerControllable
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.sim.ArcFlash
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.process.TransformerInterSystemProcess
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.protection.OvercurrentRelayLogic
import mods.eln.sixnode.protection.TripCurve
import mods.eln.sound.SoundCommand
import net.minecraft.client.gui.screens.Screen
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max

/**
 * A substation joins two levels of the grid through a transformer, with a circuit breaker on each side:
 *
 * - The distribution substation takes the 51.2 kV of T2 transmission lines (left) down to the 12.8 kV of T1
 *   distribution lines (right), for up to 2 MW.
 * - The step-up substation takes the 3.2 kV of a power station's supply cables (front, at the ground) up to 51.2 kV
 *   transmission lines (left), for up to 1 MW.
 *
 * Its breakers are protection relays: they trip on overcurrent (inverse time, at once on a short circuit) and close
 * again once by themselves, to clear passing faults. The transformer trips both on overheating or overvoltage, rather
 * than exploding, and a padlock locks both open.
 */
class SubstationDescriptor(
    name: String, obj: Obj3D, cableTexture: String, cableDescriptor: ElectricalCableDescriptor,
    val highVoltage: Double,
    val lowVoltage: Double,
    /** True when the low voltage side takes grid lines; false when it takes supply cables at the front. */
    val lowSideIsGrid: Boolean,
    val ratedPower: Double,
    private val lowCableOf: () -> ElectricalCableDescriptor?
) : GridDescriptor(name, obj, SubstationElement::class.java, SubstationRender::class.java, cableTexture, cableDescriptor, 96) {
    val highCurrent get() = ratedPower / highVoltage
    val lowCurrent get() = ratedPower / lowVoltage
    val lowCable get() = lowCableOf()

    val lampHv = obj.getPart("lamphv")
    val tripHv = obj.getPart("triphv")
    val lampLv = obj.getPart("lamplv")
    val tripLv = obj.getPart("triplv")

    override fun rotationIsFixed() = true

    override fun hasCustomIcon() = true

    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.add(tr("%1$ kV to %2$ kV, up to %3$ MW.", Utils.plotValue(highVoltage / 1000), Utils.plotValue(lowVoltage / 1000),
            Utils.plotValue(ratedPower / 1e6)))
        list.add(tr("Left: transmission lines (high voltage cable)."))
        if (lowSideIsGrid) list.add(tr("Right: distribution lines (high voltage cable)."))
        else list.add(tr("Front, at the ground: 3.2 kV supply cables."))
        list.add(tr("Each side has a protected circuit breaker."))
    }
}

class SubstationElement(node: TransparentNode, descriptor: TransparentNodeDescriptor) :
    GridElement(node, descriptor, (descriptor as GridDescriptor).connectRange), Lockable, ComputerControllable {
    private val desc = descriptor as SubstationDescriptor

    val highLoad = NbtElectricalLoad("highLoad")
    private val highWinding = NbtElectricalLoad("highWinding")
    private val lowWinding = NbtElectricalLoad("lowWinding")
    val lowLoad = NbtElectricalLoad("lowLoad")
    val highBreaker = Resistor(highLoad, highWinding)
    val lowBreaker = Resistor(lowWinding, lowLoad)
    private val highSource = VoltageSource("highSource", highWinding, null)
    private val lowSource = VoltageSource("lowSource", lowWinding, null)
    private val transformer = TransformerInterSystemProcess(highWinding, lowWinding, highSource, lowSource)

    /** The protection of each breaker: standard inverse, at the rated current, one reclose after five seconds. */
    val highRelay = relay(desc.highCurrent)
    val lowRelay = relay(desc.lowCurrent)

    /** The rise of the transformer's oil above ambient, in degrees: 50 at its rated load. */
    var oilRise = 0.0
        private set
    var overheated = false
        private set
    var overvoltage = false
        private set
    /** Energy passed from the high to the low side, in J; negative the other way. */
    var energy = 0.0
        private set

    override val lockout = Lockout()

    private var highWasClosed = false
    private var lowWasClosed = false

    private fun relay(rated: Double) = OvercurrentRelayLogic().apply {
        defaultPickup = rated
        curve = TripCurve.STANDARD_INVERSE
        tms = 0.2
        instantaneous = 8.0
        recloseShots = 1
        recloseDelay = 5.0
        close()
    }

    init {
        for (load in listOf(highLoad, highWinding, lowWinding, lowLoad)) electricalLoadList.add(load)
        electricalComponentList.add(highBreaker)
        electricalComponentList.add(lowBreaker)
        electricalComponentList.add(highSource)
        electricalComponentList.add(lowSource)
        // Keeps the line sides at the ground when their breaker is open; small next to the megawatts going through.
        electricalComponentList.add(Resistor(highLoad, null).apply { resistance = LINE_PULL_DOWN })
        electricalComponentList.add(Resistor(lowLoad, null).apply { resistance = LINE_PULL_DOWN })
        transformer.setRatio(desc.lowVoltage / desc.highVoltage)
        electricalProcessList.add(IProcess { dt -> step(dt) })
        slowProcessList.add(NodePeriodicPublishProcess(node, 1.0, 0.5))
    }

    private fun step(dt: Double) {
        val highCurrent = if (highWasClosed) highBreaker.current else 0.0
        val lowCurrent = if (lowWasClosed) lowBreaker.current else 0.0
        energy += highWinding.voltage * highCurrent * dt

        // Copper losses heat the oil, which the radiators cool, with a time constant of a minute.
        val load = max(abs(highCurrent) / desc.highCurrent, abs(lowCurrent) / desc.lowCurrent)
        oilRise += dt * (RATED_RISE * load * load - oilRise) / OIL_TIME
        if (oilRise > TRIP_RISE) overheated = true
        if (abs(highLoad.voltage) > desc.highVoltage * 1.2 || abs(lowLoad.voltage) > desc.lowVoltage * 1.2) overvoltage = true

        val blocked = lockout.isLocked || overheated || overvoltage
        highRelay.step(dt, highCurrent, blocked)
        lowRelay.step(dt, lowCurrent, blocked)
        applyBreakers(highCurrent, lowCurrent)
    }

    private fun applyBreakers(highCurrent: Double = 0.0, lowCurrent: Double = 0.0) {
        val high = highRelay.closed
        val low = lowRelay.closed
        if (high != highWasClosed) {
            if (!high) ArcFlash.interrupt(coordinate(), highLoad.voltage, highCurrent, desc.highCurrent)
            highWasClosed = high
            setBreaker(highBreaker, high)
            breakerSound()
        }
        if (low != lowWasClosed) {
            if (!low) ArcFlash.interrupt(coordinate(), lowLoad.voltage, lowCurrent, desc.lowCurrent)
            lowWasClosed = low
            setBreaker(lowBreaker, low)
            breakerSound()
        }
    }

    private fun setBreaker(breaker: Resistor, closed: Boolean) {
        breaker.resistance = if (closed) BREAKER_RESISTANCE else BREAKER_OPEN
    }

    private fun breakerSound() {
        play(SoundCommand("minecraft:block.iron_door.close").mulVolume(1f, 0.6f).mediumRange())
        needPublish()
    }

    /** The operator closes a breaker: refused while locked out, overheated or overvoltage, and then false. */
    fun closeBreaker(high: Boolean): Boolean {
        if (lockout.isLocked) return false
        if (overheated && oilRise < RESET_RISE) overheated = false
        if (overvoltage && abs(highLoad.voltage) <= desc.highVoltage * 1.2 && abs(lowLoad.voltage) <= desc.lowVoltage * 1.2) overvoltage = false
        if (overheated || overvoltage) return false
        (if (high) highRelay else lowRelay).close()
        applyBreakers()
        return true
    }

    fun openBreaker(high: Boolean) {
        val current = if (high) highBreaker.current else lowBreaker.current
        (if (high) highRelay else lowRelay).open()
        applyBreakers(if (high) current else 0.0, if (high) 0.0 else current)
    }

    override fun lockoutChanged() {
        if (lockout.isLocked) {
            openBreaker(true)
            openBreaker(false)
        }
        needPublish()
    }

    override fun initialize() {
        highLoad.serialResistance = LINE_RESISTANCE
        highWinding.serialResistance = WINDING_RESISTANCE
        lowWinding.serialResistance = WINDING_RESISTANCE
        val cable = desc.lowCable
        if (desc.lowSideIsGrid || cable == null) lowLoad.serialResistance = LINE_RESISTANCE else cable.applyTo(lowLoad)
        highWasClosed = highRelay.closed
        lowWasClosed = lowRelay.closed
        setBreaker(highBreaker, highWasClosed)
        setBreaker(lowBreaker, lowWasClosed)
        super.initialize()
    }

    override fun disconnectJob() {
        super.disconnectJob()
        Eln.simulator.mna.removeProcess(transformer)
    }

    override fun connectJob() {
        Eln.simulator.mna.addProcess(transformer)
        super.connectJob()
    }

    override fun getGridElectricalLoad(side: Direction): ElectricalLoad? = when (side) {
        front.left() -> highLoad
        front.right() -> if (desc.lowSideIsGrid) lowLoad else null
        else -> null
    }

    override fun getElectricalLoad(side: Direction, lrdu: LRDU): ElectricalLoad? =
        if (!desc.lowSideIsGrid && side == front && lrdu == LRDU.Down) lowLoad else null

    override fun getConnectionMask(side: Direction, lrdu: LRDU): Int =
        if (!desc.lowSideIsGrid && side == front && lrdu == LRDU.Down) NodeBase.maskElectricalPower else 0

    override fun getThermalLoad(side: Direction, lrdu: LRDU): ThermalLoad? = null

    override fun thermoMeterString(side: Direction): String = Utils.plotCelsius("Oil +", oilRise)

    public override fun getCablePoint(side: Direction, i: Int): Vec3 {
        if (i >= 2) throw AssertionError("Invalid cable point index")
        val idx = if (side == front.right()) 1 else 0
        return (if (i == 0) desc.plus else desc.gnd)[idx].boundingBox().centre()
    }

    private fun power() = highWinding.voltage * (if (highWasClosed) highBreaker.current else 0.0)

    private fun stateName(relay: OvercurrentRelayLogic): String = when {
        lockout.isLocked -> tr("Locked out")
        relay.state == OvercurrentRelayLogic.State.CLOSED -> tr("Closed")
        relay.state == OvercurrentRelayLogic.State.RECLOSING -> tr("Waiting to reclose")
        relay.state == OvercurrentRelayLogic.State.LOCKED_OUT -> tr("Tripped at %1$ A", "%.0f".format(relay.lastTripCurrent))
        else -> tr("Open")
    }

    override fun multiMeterString(side: Direction): String =
        Utils.plotVolt("HV:", highLoad.voltage) + Utils.plotAmpere("", highBreaker.current) +
            Utils.plotVolt("  LV:", lowLoad.voltage) + Utils.plotAmpere("", lowBreaker.current)

    override fun getWaila(): Map<String, String> {
        val info = LinkedHashMap<String, String>()
        info[tr("High voltage")] = Utils.plotVolt("", highLoad.voltage) + " " + stateName(highRelay)
        info[tr("Low voltage")] = Utils.plotVolt("", lowLoad.voltage) + " " + stateName(lowRelay)
        info[tr("Power")] = Utils.plotPower("", power())
        info[tr("Transformer")] = Utils.plotCelsius("", oilRise) + " " + tr("above ambient") +
            if (overheated) " " + tr("(overheated)") else if (overvoltage) " " + tr("(overvoltage)") else ""
        if (lockout.isLocked) info[tr("Lockout")] = lockout.describe()
        return info
    }

    override fun hasGui() = true

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try {
            stream.writeByte(highRelay.state.ordinal)
            stream.writeByte(lowRelay.state.ordinal)
            stream.writeFloat(highLoad.voltage.toFloat())
            stream.writeFloat(lowLoad.voltage.toFloat())
            stream.writeFloat(highBreaker.current.toFloat())
            stream.writeFloat(lowBreaker.current.toFloat())
            stream.writeFloat(power().toFloat())
            stream.writeFloat(oilRise.toFloat())
            stream.writeByte((if (overheated) 1 else 0) or (if (overvoltage) 2 else 0) or (if (lockout.isLocked) 4 else 0))
            stream.writeFloat((max(abs(highBreaker.current) / desc.highCurrent, abs(lowBreaker.current) / desc.lowCurrent)).toFloat())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun networkUnserialize(stream: DataInputStream): Byte {
        when (super.networkUnserialize(stream).toInt()) {
            CLOSE_HIGH -> closeBreaker(true)
            CLOSE_LOW -> closeBreaker(false)
            OPEN_HIGH -> openBreaker(true)
            OPEN_LOW -> openBreaker(false)
            else -> return unserializeNulldId
        }
        needPublish()
        return unserializeNulldId
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        if (nbt.contains("high")) highRelay.readFromNBT(nbt.getCompound("high"))
        if (nbt.contains("low")) lowRelay.readFromNBT(nbt.getCompound("low"))
        highRelay.defaultPickup = desc.highCurrent
        lowRelay.defaultPickup = desc.lowCurrent
        oilRise = nbt.getDouble("oilRise")
        overheated = nbt.getBoolean("overheated")
        overvoltage = nbt.getBoolean("overvoltage")
        energy = nbt.getDouble("energy")
        lockout.readFromNBT(nbt)
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.put("high", CompoundTag().also { highRelay.writeToNBT(it) })
        nbt.put("low", CompoundTag().also { lowRelay.writeToNBT(it) })
        nbt.putDouble("oilRise", oilRise)
        nbt.putBoolean("overheated", overheated)
        nbt.putBoolean("overvoltage", overvoltage)
        nbt.putDouble("energy", energy)
        lockout.writeToNBT(nbt)
    }

    override fun computerValues(): Map<String, Any?> = linkedMapOf(
        "highVoltage" to highLoad.voltage,
        "lowVoltage" to lowLoad.voltage,
        "highCurrent" to highBreaker.current,
        "lowCurrent" to lowBreaker.current,
        "power" to power(),
        "energy" to energy,
        "highBreaker" to highRelay.state.name.lowercase(),
        "lowBreaker" to lowRelay.state.name.lowercase(),
        "oilRise" to oilRise,
        "overheated" to overheated,
        "overvoltage" to overvoltage,
        "locked" to lockout.isLocked,
        "ratedPower" to desc.ratedPower
    )

    override fun computerActions() = listOf(
        "open(side): opens the \"high\" or \"low\" breaker",
        "close(side): closes it; false while locked out, overheated or overvoltage"
    )

    override fun computerCall(action: String, args: List<Any?>): Any? {
        val high = when (args.firstOrNull()) {
            "high" -> true
            "low" -> false
            else -> throw IllegalArgumentException("say \"high\" or \"low\"")
        }
        return when (action) {
            "open" -> { openBreaker(high); needPublish(); true }
            "close" -> closeBreaker(high).also { needPublish() }
            else -> throw IllegalArgumentException("no action $action")
        }
    }

    companion object {
        const val CLOSE_HIGH = 1
        const val CLOSE_LOW = 2
        const val OPEN_HIGH = 3
        const val OPEN_LOW = 4

        const val RATED_RISE = 50.0
        const val TRIP_RISE = 75.0
        const val RESET_RISE = 40.0
        const val OIL_TIME = 60.0

        const val LINE_RESISTANCE = 0.05
        const val WINDING_RESISTANCE = 0.02
        const val BREAKER_RESISTANCE = 0.01
        const val BREAKER_OPEN = 1e10
        const val LINE_PULL_DOWN = 1e7
    }
}

class SubstationRender(entity: TransparentNodeEntity, descriptor: TransparentNodeDescriptor) : GridRender(entity, descriptor) {
    val desc = descriptor as SubstationDescriptor
    var highState = OvercurrentRelayLogic.State.CLOSED
    var lowState = OvercurrentRelayLogic.State.CLOSED
    var highVoltage = 0f
    var lowVoltage = 0f
    var highCurrent = 0f
    var lowCurrent = 0f
    var power = 0f
    var oilRise = 0f
    var flags = 0
    private var load = mods.eln.misc.SlewLimiter(0.5f)

    init {
        addLoopedSound(object : mods.eln.sound.LoopedSound("eln:Transformer", coordinate(),
            net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR) {
            override fun getVolume() = Math.max(0f, (load.position - 0.1f) / 0.9f)
        })
    }

    override fun draw() {
        super.draw()
        // The lamps turn with the model, as the grid render turns it for its front.
        val angle = when (front) {
            Direction.XP -> 180f
            Direction.ZN -> 270f
            Direction.ZP -> 90f
            else -> 0f
        }
        GL11.glPushMatrix()
        GL11.glRotatef(angle, 0f, 1f, 0f)
        lamp(desc.lampHv, desc.tripHv, highState)
        lamp(desc.lampLv, desc.tripLv, lowState)
        GL11.glPopMatrix()
    }

    private fun lamp(closedLamp: Obj3D.Obj3DPart?, tripLamp: Obj3D.Obj3DPart?, state: OvercurrentRelayLogic.State) {
        if (state == OvercurrentRelayLogic.State.CLOSED) UtilsClient.drawLight(closedLamp) else closedLamp?.let {
            GL11.glColor4f(0.3f, 0.3f, 0.3f, 1f); it.draw(); GL11.glColor4f(1f, 1f, 1f, 1f)
        }
        if (state == OvercurrentRelayLogic.State.LOCKED_OUT ||
            (state == OvercurrentRelayLogic.State.RECLOSING && System.currentTimeMillis() / 400 % 2 == 0L)) {
            UtilsClient.drawLight(tripLamp)
        } else tripLamp?.let {
            GL11.glColor4f(0.3f, 0.3f, 0.3f, 1f); it.draw(); GL11.glColor4f(1f, 1f, 1f, 1f)
        }
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            highState = OvercurrentRelayLogic.State.entries[stream.readByte().toInt()]
            lowState = OvercurrentRelayLogic.State.entries[stream.readByte().toInt()]
            highVoltage = stream.readFloat()
            lowVoltage = stream.readFloat()
            highCurrent = stream.readFloat()
            lowCurrent = stream.readFloat()
            power = stream.readFloat()
            oilRise = stream.readFloat()
            flags = stream.readByte().toInt()
            load.target = stream.readFloat()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun refresh(deltaT: Float) {
        super.refresh(deltaT)
        load.step(deltaT)
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen = SubstationGui(this)
}

class SubstationGui(private val render: SubstationRender) : GuiScreenEln() {
    private lateinit var high: GuiButtonEln
    private lateinit var low: GuiButtonEln

    override fun newHelper() = GuiHelper(this, 220, 112)

    override fun initGui() {
        super.initGui()
        high = newGuiButton(8, 84, 98, "")
        low = newGuiButton(114, 84, 98, "")
    }

    private fun closed(state: OvercurrentRelayLogic.State) = state == OvercurrentRelayLogic.State.CLOSED

    override fun preDraw(f: Float, x: Int, y: Int) {
        super.preDraw(f, x, y)
        high.displayString = if (closed(render.highState)) tr("Open HV breaker") else tr("Close HV breaker")
        low.displayString = if (closed(render.lowState)) tr("Open LV breaker") else tr("Close LV breaker")
    }

    private fun state(s: OvercurrentRelayLogic.State): String = when (s) {
        OvercurrentRelayLogic.State.CLOSED -> "§2" + tr("Closed")
        OvercurrentRelayLogic.State.RECLOSING -> "§6" + tr("Waiting to reclose")
        OvercurrentRelayLogic.State.LOCKED_OUT -> "§c" + tr("Tripped")
        else -> tr("Open")
    }

    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y)
        val d = render.desc
        helper.drawString(8, 6, 0x404040, tr("High voltage side (%1$ kV)", Utils.plotValue(d.highVoltage / 1000)))
        helper.drawString(8, 17, 0x404040, Utils.plotVolt("", render.highVoltage.toDouble()) + Utils.plotAmpere("  ", render.highCurrent.toDouble()) + "  " + state(render.highState))
        helper.drawString(8, 31, 0x404040, tr("Low voltage side (%1$ kV)", Utils.plotValue(d.lowVoltage / 1000)))
        helper.drawString(8, 42, 0x404040, Utils.plotVolt("", render.lowVoltage.toDouble()) + Utils.plotAmpere("  ", render.lowCurrent.toDouble()) + "  " + state(render.lowState))
        helper.drawString(8, 56, 0x404040, tr("Power: %1$ of %2$", Utils.plotPower("", render.power.toDouble()).trim(), Utils.plotPower("", d.ratedPower).trim()))
        val warn = when {
            render.flags and 4 != 0 -> "§c" + tr("Locked out")
            render.flags and 1 != 0 -> "§c" + tr("Tripped: transformer overheated")
            render.flags and 2 != 0 -> "§c" + tr("Tripped: overvoltage")
            else -> ""
        }
        helper.drawString(8, 67, 0x404040, tr("Oil: %1$ above ambient", Utils.plotCelsius("", render.oilRise.toDouble()).trim()) + "  " + warn)
    }

    override fun guiObjectEvent(`object`: IGuiObject) {
        super.guiObjectEvent(`object`)
        if (`object` === high) render.clientSendId((if (closed(render.highState)) SubstationElement.OPEN_HIGH else SubstationElement.CLOSE_HIGH).toByte())
        if (`object` === low) render.clientSendId((if (closed(render.lowState)) SubstationElement.OPEN_LOW else SubstationElement.CLOSE_LOW).toByte())
    }
}
