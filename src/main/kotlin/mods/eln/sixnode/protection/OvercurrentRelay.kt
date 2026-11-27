package mods.eln.sixnode.protection

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.compat.IItemRenderer.ItemRenderType
import mods.eln.compat.IItemRenderer.ItemRendererHelper
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Obj3D
import mods.eln.misc.RcInterpolator
import mods.eln.misc.Utils
import mods.eln.misc.UtilsClient
import mods.eln.misc.VoltageLevelColor
import mods.eln.node.NodeBase
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeDescriptor
import mods.eln.node.six.SixNodeEntity
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateInput
import mods.eln.sim.nbt.NbtElectricalGateOutput
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.wiki.Data
import net.minecraft.client.gui.screens.Screen
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs
import kotlin.math.pow

/** Inverse-time curves of IEC 60255, and a definite-time one. */
enum class TripCurve(val shortName: String) {
    STANDARD_INVERSE("SI"),
    VERY_INVERSE("VI"),
    EXTREMELY_INVERSE("EI"),
    DEFINITE_TIME("DT");

    /** Seconds to trip at [multiple] times the pickup current, with time multiplier [tms]; infinite below pickup. */
    fun tripTime(multiple: Double, tms: Double): Double {
        if (multiple <= 1.0) return Double.POSITIVE_INFINITY
        return when (this) {
            STANDARD_INVERSE -> tms * 0.14 / (multiple.pow(0.02) - 1)
            VERY_INVERSE -> tms * 13.5 / (multiple - 1)
            EXTREMELY_INVERSE -> tms * 80.0 / (multiple * multiple - 1)
            DEFINITE_TIME -> tms
        }
    }

    fun displayName(): String = when (this) {
        STANDARD_INVERSE -> tr("Standard inverse")
        VERY_INVERSE -> tr("Very inverse")
        EXTREMELY_INVERSE -> tr("Extremely inverse")
        DEFINITE_TIME -> tr("Definite time")
    }
}

/**
 * The relay without the world around it: an inverse-time overcurrent element, an instantaneous element and an
 * auto-recloser, as on distribution feeders.
 */
class OvercurrentRelayLogic {
    enum class State { CLOSED, OPEN, RECLOSING, LOCKED_OUT }

    /** Pickup current in A; 0 to use [defaultPickup]. */
    var pickup = 0.0
    var defaultPickup = 1.0
    var tms = 0.1
    var curve = TripCurve.STANDARD_INVERSE
    /** Trips at once at this multiple of the pickup current; 0 for off. */
    var instantaneous = 8.0
    /** How many times it closes again after a trip before it locks out. */
    var recloseShots = 0
    var recloseDelay = 5.0
    /** After this long without a trip, the shots count from zero again. */
    var reclaimTime = 30.0

    var state = State.OPEN
    /** How far towards a trip the time element has got, 0 to 1. */
    var progress = 0.0
        private set
    var shotsUsed = 0
        private set
    private var timer = 0.0
    var lastTripCurrent = 0.0
        private set

    val effectivePickup: Double
        get() = if (pickup > 0) pickup else defaultPickup

    val closed: Boolean
        get() = state == State.CLOSED

    /** Seconds until it trips at [current], or infinity. */
    fun timeToTrip(current: Double): Double {
        val m = abs(current) / effectivePickup
        if (instantaneous > 0 && m >= instantaneous) return 0.0
        val t = curve.tripTime(m, tms)
        return if (t.isInfinite()) t else t * (1 - progress)
    }

    /** Advances by [dt] seconds with [current] flowing; [blocked] is the remote trip input. */
    fun step(dt: Double, current: Double, blocked: Boolean) {
        when (state) {
            State.CLOSED -> {
                if (blocked) {
                    state = State.OPEN
                    progress = 0.0
                    return
                }
                val m = abs(current) / effectivePickup
                if (instantaneous > 0 && m >= instantaneous) {
                    trip(current)
                    return
                }
                val t = curve.tripTime(m, tms)
                if (t.isFinite()) {
                    progress += dt / t
                    if (progress >= 1.0) {
                        trip(current)
                        return
                    }
                } else {
                    // An induction disc runs back once the current falls below pickup.
                    progress = (progress - dt / RESET_TIME).coerceAtLeast(0.0)
                }
                if (shotsUsed > 0) {
                    timer -= dt
                    if (timer <= 0) shotsUsed = 0
                }
            }
            State.RECLOSING -> {
                if (blocked) {
                    state = State.OPEN
                    return
                }
                timer -= dt
                if (timer <= 0) {
                    shotsUsed++
                    state = State.CLOSED
                    progress = 0.0
                    timer = reclaimTime
                }
            }
            State.OPEN, State.LOCKED_OUT -> {}
        }
    }

    private fun trip(current: Double) {
        lastTripCurrent = abs(current)
        progress = 0.0
        if (shotsUsed < recloseShots) {
            state = State.RECLOSING
            timer = recloseDelay
        } else {
            state = State.LOCKED_OUT
        }
    }

    /** The operator closes it, which also resets a lockout. */
    fun close() {
        state = State.CLOSED
        progress = 0.0
        shotsUsed = 0
    }

    fun open() {
        state = State.OPEN
        progress = 0.0
    }

    fun writeToNBT(nbt: CompoundTag) {
        nbt.putDouble("pickup", pickup)
        nbt.putDouble("tms", tms)
        nbt.putByte("curve", curve.ordinal.toByte())
        nbt.putDouble("instantaneous", instantaneous)
        nbt.putByte("shots", recloseShots.toByte())
        nbt.putDouble("recloseDelay", recloseDelay)
        nbt.putByte("state", state.ordinal.toByte())
        nbt.putDouble("progress", progress)
        nbt.putByte("shotsUsed", shotsUsed.toByte())
        nbt.putDouble("timer", timer)
    }

    fun readFromNBT(nbt: CompoundTag) {
        pickup = nbt.getDouble("pickup")
        tms = if (nbt.contains("tms")) nbt.getDouble("tms") else 0.1
        curve = TripCurve.entries.getOrElse(nbt.getByte("curve").toInt()) { TripCurve.STANDARD_INVERSE }
        instantaneous = if (nbt.contains("instantaneous")) nbt.getDouble("instantaneous") else 8.0
        recloseShots = nbt.getByte("shots").toInt()
        recloseDelay = if (nbt.contains("recloseDelay")) nbt.getDouble("recloseDelay") else 5.0
        state = State.entries.getOrElse(nbt.getByte("state").toInt()) { State.OPEN }
        progress = nbt.getDouble("progress")
        shotsUsed = nbt.getByte("shotsUsed").toInt()
        timer = nbt.getDouble("timer")
    }

    companion object {
        const val RESET_TIME = 2.0
        const val MAX_SHOTS = 3
    }
}

class OvercurrentRelayDescriptor(name: String, obj: Obj3D) :
    SixNodeDescriptor(name, OvercurrentRelayElement::class.java, OvercurrentRelayRender::class.java) {

    private val case = obj.getPart("case")
    private val lever = obj.getPart("lever")
    private val flag = obj.getPart("flag")
    private val ledReady = obj.getPart("ledready")
    private val ledReclose = obj.getPart("ledreclose")
    private val ledTrip = obj.getPart("ledtrip")
    val leverSpeed = lever?.getFloat("speed") ?: 0.08f
    private val alphaOff = lever?.getFloat("alphaOff") ?: 35f
    private val alphaOn = lever?.getFloat("alphaOn") ?: -35f

    init {
        voltageLevelColor = VoltageLevelColor.Neutral
    }

    override fun setParent(item: Item, damage: Int) {
        super.setParent(item, damage)
        Data.addWiring(newItemStack())
    }

    /** [on] from 0 (open) to 1 (closed); [state] picks the lit LED. */
    fun draw(on: Float, state: OvercurrentRelayLogic.State?) {
        case?.draw()
        lever?.draw(on * (alphaOn - alphaOff) + alphaOff, 0f, 1f, 0f)
        if (state == OvercurrentRelayLogic.State.LOCKED_OUT || state == OvercurrentRelayLogic.State.RECLOSING) flag?.draw()
        when (state) {
            OvercurrentRelayLogic.State.CLOSED -> UtilsClient.drawLight(ledReady)
            OvercurrentRelayLogic.State.RECLOSING ->
                if (System.currentTimeMillis() / 400 % 2 == 0L) UtilsClient.drawLight(ledReclose) else PartsDescriptor.drawDim(ledReclose)
            OvercurrentRelayLogic.State.LOCKED_OUT -> UtilsClient.drawLight(ledTrip)
            else -> {}
        }
        if (state != OvercurrentRelayLogic.State.CLOSED) PartsDescriptor.drawDim(ledReady)
        if (state != OvercurrentRelayLogic.State.RECLOSING) PartsDescriptor.drawDim(ledReclose)
        if (state != OvercurrentRelayLogic.State.LOCKED_OUT) PartsDescriptor.drawDim(ledTrip)
    }

    override fun handleRenderType(item: ItemStack, type: ItemRenderType) = true

    override fun shouldUseRenderHelper(type: ItemRenderType, item: ItemStack, helper: ItemRendererHelper) =
        type != ItemRenderType.INVENTORY

    override fun shouldUseRenderHelperEln(type: ItemRenderType?, item: ItemStack?, helper: ItemRendererHelper?) =
        type != ItemRenderType.INVENTORY

    override fun renderItem(type: ItemRenderType, item: ItemStack, vararg data: Any) {
        if (type == ItemRenderType.INVENTORY) super.renderItem(type, item, *data) else draw(0f, null)
    }

    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.addAll(tr("A breaker driven by an overcurrent relay, as on power lines.\nTrips on inverse-time curves, at once on a short circuit,\nand can close again by itself a few times.").split("\n"))
        list.add(tr("Put a cable in it to rate the contacts."))
        list.add(tr("Left: closed signal output. Right: remote trip input."))
    }

    override fun getFrontFromPlace(side: Direction, player: Player): LRDU? = super.getFrontFromPlace(side, player)?.inverse()
}

class OvercurrentRelayElement(sixNode: SixNode, side: Direction, descriptor: SixNodeDescriptor) :
    SwitchgearElement(sixNode, side, descriptor), mods.eln.node.ComputerControllable {

    override fun computerValues(): Map<String, Any?> = linkedMapOf(
        "state" to logic.state.name.lowercase(),
        "closed" to (logic.closed && !lockout.isLocked),
        "current" to Math.abs(contact.current),
        "pickup" to logic.effectivePickup,
        "lastTripCurrent" to logic.lastTripCurrent,
        "locked" to lockout.isLocked
    )

    override fun computerActions() = listOf("trip(): opens it", "close(): closes or resets it; false without a cable or locked out")

    override fun computerCall(action: String, args: List<Any?>): Any? = when (action) {
        "trip" -> { logic.open(); applyState(); needPublish(); true }
        "close" -> if (cable == null || lockout.isLocked) false else { logic.close(); applyState(); needPublish(); true }
        else -> throw IllegalArgumentException("no action $action")
    }

    val aLoad = NbtElectricalLoad("aLoad")
    val bLoad = NbtElectricalLoad("bLoad")
    val contact = Resistor(aLoad, bLoad)
    val statusOut = NbtElectricalGateOutput("statusOut")
    val statusProcess = NbtElectricalGateOutputProcess("statusProcess", statusOut)
    val remoteIn = NbtElectricalGateInput("remoteIn")
    val logic = OvercurrentRelayLogic()

    private var shownState = logic.state
    private var wasClosed = false

    init {
        electricalLoadList.add(aLoad)
        electricalLoadList.add(bLoad)
        electricalLoadList.add(statusOut)
        electricalLoadList.add(remoteIn)
        electricalComponentList.add(contact)
        electricalComponentList.add(Resistor(aLoad, null).pullDown())
        electricalComponentList.add(Resistor(bLoad, null).pullDown())
        electricalComponentList.add(statusProcess)
        electricalProcessList.add(IProcess { time -> step(time) })
    }

    private fun step(time: Double) {
        if (cable == null) {
            if (logic.state == OvercurrentRelayLogic.State.CLOSED) logic.open()
        } else {
            logic.defaultPickup = ratedCurrent
            logic.step(time, contact.current, remoteIn.connectedComponents.isNotEmpty() && remoteIn.stateHigh())
        }
        applyState()
    }

    internal fun applyState() {
        val closed = logic.closed && cable != null
        if (closed != wasClosed) {
            wasClosed = closed
            setContact(contact, closed)
            clickSound()
        }
        statusProcess.setOutputNormalized(
            when (logic.state) {
                OvercurrentRelayLogic.State.CLOSED -> 1.0
                OvercurrentRelayLogic.State.RECLOSING -> 0.5
                else -> 0.0
            }
        )
        if (logic.state != shownState) {
            shownState = logic.state
            needPublish()
        }
    }

    override fun cableChanged() {
        applyCable(aLoad, bLoad)
        logic.defaultPickup = ratedCurrent.coerceAtLeast(0.1)
        wasClosed = logic.closed && cable != null
        setContact(contact, wasClosed)
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int): ElectricalLoad? = when (lrdu) {
        front -> aLoad
        front.inverse() -> bLoad
        front.left() -> statusOut
        front.right() -> remoteIn
        else -> null
    }

    override fun getConnectionMask(lrdu: LRDU): Int = when (lrdu) {
        front, front.inverse() -> if (hasCable) NodeBase.maskElectricalAll else 0
        front.left() -> NodeBase.maskElectricalOutputGate
        front.right() -> NodeBase.maskElectricalInputGate
        else -> 0
    }

    override fun multiMeterString(): String =
        Utils.plotVolt("Ua:", aLoad.voltage) + Utils.plotVolt("Ub:", bLoad.voltage) + Utils.plotAmpere("I:", contact.current)

    private fun stateName(): String = when (logic.state) {
        OvercurrentRelayLogic.State.CLOSED -> tr("Closed")
        OvercurrentRelayLogic.State.OPEN -> tr("Open")
        OvercurrentRelayLogic.State.RECLOSING -> tr("Waiting to reclose")
        OvercurrentRelayLogic.State.LOCKED_OUT -> tr("Locked out")
    }

    override fun getWaila(): Map<String, String> {
        val info = LinkedHashMap<String, String>()
        addLockout(info)
        info[tr("State")] = stateName()
        info[tr("Current")] = Utils.plotAmpere("", contact.current)
        info[tr("Pickup")] = Utils.plotAmpere("", logic.effectivePickup)
        info[tr("Curve")] = logic.curve.displayName()
        val t = logic.timeToTrip(contact.current)
        if (logic.closed && t.isFinite()) info[tr("Trips in")] = Utils.plotTime("", t)
        if (logic.lastTripCurrent > 0) info[tr("Last trip")] = Utils.plotAmpere("", logic.lastTripCurrent)
        return info
    }

    override fun serializeState(stream: DataOutputStream) {
        stream.writeByte(logic.state.ordinal)
        stream.writeFloat(logic.pickup.toFloat())
        stream.writeFloat(logic.defaultPickup.toFloat())
        stream.writeFloat(logic.tms.toFloat())
        stream.writeByte(logic.curve.ordinal)
        stream.writeFloat(logic.instantaneous.toFloat())
        stream.writeByte(logic.recloseShots)
        stream.writeFloat(logic.recloseDelay.toFloat())
    }

    override fun command(id: Int, stream: DataInputStream) {
        when (id) {
            SET_PICKUP -> logic.pickup = stream.readDouble().coerceAtLeast(0.0)
            SET_TMS -> logic.tms = stream.readDouble().coerceIn(0.01, 10.0)
            SET_INSTANTANEOUS -> logic.instantaneous = stream.readDouble().let { if (it < 1.5) 0.0 else it.coerceAtMost(50.0) }
            SET_RECLOSE_DELAY -> logic.recloseDelay = stream.readDouble().coerceIn(0.2, 120.0)
            NEXT_CURVE -> logic.curve = TripCurve.entries[(logic.curve.ordinal + 1) % TripCurve.entries.size]
            NEXT_SHOTS -> logic.recloseShots = (logic.recloseShots + 1) % (OvercurrentRelayLogic.MAX_SHOTS + 1)
            OPERATE -> if (logic.closed) logic.open() else if (cable != null) logic.close()
        }
        applyState()
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        logic.readFromNBT(nbt)
        shownState = logic.state
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        logic.writeToNBT(nbt)
    }

    companion object {
        const val SET_PICKUP = 1
        const val SET_TMS = 2
        const val SET_INSTANTANEOUS = 3
        const val SET_RECLOSE_DELAY = 4
        const val NEXT_CURVE = 5
        const val NEXT_SHOTS = 6
        const val OPERATE = 7
    }
}

class OvercurrentRelayRender(entity: SixNodeEntity, side: Direction, descriptor: SixNodeDescriptor) :
    SwitchgearRender(entity, side, descriptor) {

    private val desc = descriptor as OvercurrentRelayDescriptor
    private val lever = RcInterpolator(desc.leverSpeed)
    private var boot = true

    var state = OvercurrentRelayLogic.State.OPEN
    var pickup = 0f
    var defaultPickup = 0f
    var tms = 0.1f
    var curve = TripCurve.STANDARD_INVERSE
    var instantaneous = 0f
    var shots = 0
    var recloseDelay = 5f

    override fun draw() {
        super.draw()
        front!!.glRotateOnX()
        desc.draw(lever.get(), state)
    }

    override fun refresh(deltaT: Float) {
        lever.target = if (state == OvercurrentRelayLogic.State.CLOSED) 1f else 0f
        lever.step(deltaT)
    }

    override fun unserializeState(stream: DataInputStream) {
        state = OvercurrentRelayLogic.State.entries[stream.readByte().toInt()]
        pickup = stream.readFloat()
        defaultPickup = stream.readFloat()
        tms = stream.readFloat()
        curve = TripCurve.entries[stream.readByte().toInt()]
        instantaneous = stream.readFloat()
        shots = stream.readByte().toInt()
        recloseDelay = stream.readFloat()
        if (boot) lever.setValue(if (state == OvercurrentRelayLogic.State.CLOSED) 1f else 0f)
        boot = false
    }

    override fun getCableRender(lrdu: LRDU): CableRenderDescriptor? = when (lrdu) {
        front, front!!.inverse() -> cableRender
        front!!.left(), front!!.right() -> signalRender
        else -> null
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen = OvercurrentRelayGui(player, this)
}

class OvercurrentRelayGui(player: Player, private val render: OvercurrentRelayRender) :
    SettingsGui(player, render.inventory) {

    init {
        fields.add(Field(tr("Pickup (A)"), tr("Current above which it starts timing.\n0 uses the current of the cable."),
            { render.pickup.toDouble() }) { render.clientSetDouble(OvercurrentRelayElement.SET_PICKUP.toByte(), it) })
        fields.add(Field(tr("Time multiplier"), tr("Scales the trip time of the curve.\nFor definite time, the delay in seconds."),
            { render.tms.toDouble() }) { render.clientSetDouble(OvercurrentRelayElement.SET_TMS.toByte(), it) })
        fields.add(Field(tr("Instant trip (x)"), tr("Trips at once at this many times the pickup.\n0 turns it off."),
            { render.instantaneous.toDouble() }) { render.clientSetDouble(OvercurrentRelayElement.SET_INSTANTANEOUS.toByte(), it) })
        fields.add(Field(tr("Reclose delay (s)"), tr("How long it stays open before closing again."),
            { render.recloseDelay.toDouble() }) { render.clientSetDouble(OvercurrentRelayElement.SET_RECLOSE_DELAY.toByte(), it) })
        buttons.add(Button(8, 61, 78, { tr("Curve: %1$", render.curve.shortName) }) { render.clientSend(OvercurrentRelayElement.NEXT_CURVE) })
        buttons.add(Button(90, 61, 78, { tr("Recloses: %1$", render.shots) }) { render.clientSend(OvercurrentRelayElement.NEXT_SHOTS) })
        buttons.add(Button(132, 42, 38, {
            when (render.state) {
                OvercurrentRelayLogic.State.CLOSED -> tr("Open")
                OvercurrentRelayLogic.State.LOCKED_OUT -> tr("Reset")
                else -> tr("Close")
            }
        }) { render.clientSend(OvercurrentRelayElement.OPERATE) })
    }
}
