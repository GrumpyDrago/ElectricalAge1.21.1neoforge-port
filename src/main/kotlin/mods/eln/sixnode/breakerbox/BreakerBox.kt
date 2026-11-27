package mods.eln.sixnode.breakerbox

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.gui.GuiButtonEln
import mods.eln.gui.GuiHelper
import mods.eln.gui.GuiScreenEln
import mods.eln.gui.IGuiObject
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Obj3D
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.NodeConnection
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeDescriptor
import mods.eln.node.six.SixNodeElement
import mods.eln.node.six.SixNodeElementRender
import mods.eln.node.six.SixNodeEntity
import mods.eln.sim.ArcFlash
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.misc.VoltageLevelColor
import mods.eln.sim.process.destruct.VoltageStateWatchDog
import mods.eln.sim.process.destruct.WorldExplosion
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricalcable.PowerBundle
import mods.eln.sixnode.protection.PartsDescriptor
import mods.eln.sound.SoundCommand
import net.minecraft.client.gui.screens.Screen
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max

/**
 * Sixteen miniature circuit breakers, like those of a house. Each trips by heat on an overload, the sooner the bigger
 * it is (a thermal element), and at once on a short circuit (a magnetic one), as a type B breaker does. Real breakers
 * take minutes on a small overload; these take at most [MAX_TRIP_TIME], so a bad circuit shows up quickly.
 */
class BreakerBoxLogic {
    enum class State { ON, OFF, TRIPPED }

    val state = Array(LINES) { State.ON }
    val rating = IntArray(LINES) { RATINGS.toList().indexOf(DEFAULT_RATING) }
    /** How far each thermal element is towards tripping, 0 to 1. */
    val heat = DoubleArray(LINES)
    /** The current each last tripped at. */
    val tripCurrent = DoubleArray(LINES)

    fun ratedCurrent(line: Int) = RATINGS[rating[line]]

    fun closed(line: Int) = state[line] == State.ON

    /** Advances breaker [line] carrying [current] by [dt]; returns true when it trips. */
    fun step(line: Int, current: Double, dt: Double): Boolean {
        val ratio = abs(current) / ratedCurrent(line)
        if (state[line] != State.ON) {
            heat[line] = max(0.0, heat[line] - dt / COOL_TIME)
            return false
        }
        if (ratio >= MAGNETIC_TRIP) return trip(line, current)
        heat[line] = if (ratio > THERMAL_PICKUP) heat[line] + dt / tripTime(ratio)
        else max(0.0, heat[line] - dt / COOL_TIME)
        return if (heat[line] >= 1) trip(line, current) else false
    }

    private fun trip(line: Int, current: Double): Boolean {
        state[line] = State.TRIPPED
        tripCurrent[line] = abs(current)
        return true
    }

    /** The handle: on goes off, off or tripped goes on (a tripped breaker resets by being switched on). */
    fun toggle(line: Int) {
        state[line] = if (state[line] == State.ON) State.OFF else State.ON
    }

    fun cycleRating(line: Int, up: Boolean) {
        rating[line] = Math.floorMod(rating[line] + if (up) 1 else -1, RATINGS.size)
    }

    /** Seconds it takes to trip at [ratio] times its rating from cold; 0 for at once, infinite for never. */
    fun tripTime(ratio: Double): Double = when {
        ratio >= MAGNETIC_TRIP -> 0.0
        ratio <= THERMAL_PICKUP -> Double.POSITIVE_INFINITY
        // From MAX_TRIP_TIME just above the pickup down to nothing at the magnetic trip.
        else -> MAX_TRIP_TIME * Math.pow((MAGNETIC_TRIP - ratio) / (MAGNETIC_TRIP - THERMAL_PICKUP), 1.5)
    }

    fun writeToNBT(nbt: CompoundTag) {
        nbt.putByteArray("states", ByteArray(LINES) { state[it].ordinal.toByte() })
        nbt.putByteArray("ratings", ByteArray(LINES) { rating[it].toByte() })
        for (i in 0 until LINES) nbt.putDouble("heat$i", heat[i])
    }

    fun readFromNBT(nbt: CompoundTag) {
        val s = nbt.getByteArray("states")
        val r = nbt.getByteArray("ratings")
        for (i in 0 until LINES) {
            if (i < s.size) state[i] = State.entries.getOrElse(s[i].toInt()) { State.OFF }
            if (i < r.size) rating[i] = r[i].toInt().coerceIn(0, RATINGS.size - 1)
            heat[i] = nbt.getDouble("heat$i")
        }
    }

    companion object {
        const val LINES = PowerBundle.LINES
        /** Ratings in amps; the bundle lines are made for 20 A. */
        val RATINGS = doubleArrayOf(2.0, 4.0, 6.0, 10.0, 13.0, 16.0, 20.0)
        const val DEFAULT_RATING = 16.0
        /** Up to this many times its rating it never trips. */
        const val THERMAL_PICKUP = 1.13
        /** The longest it takes to trip, in seconds, just above [THERMAL_PICKUP]; about 3.4 s at twice its rating. */
        const val MAX_TRIP_TIME = 5.0
        /** Seconds for a hot element to cool back down. */
        const val COOL_TIME = 30.0
        /** From this many times its rating it trips at once. */
        const val MAGNETIC_TRIP = 5.0
    }
}

/**
 * A breaker box for one voltage: [nominalVoltage] is what it is made for (it burns out above 1.3 times that), [cable]
 * the cable of that voltage, whose resistance its supply and contacts have, and [bundle] the bundle drawn out of it.
 */
class BreakerBoxDescriptor(
    name: String, obj: Obj3D,
    val nominalVoltage: Double,
    private val cableOf: () -> ElectricalCableDescriptor,
    private val bundleOf: () -> ElectricalCableDescriptor?
) : PartsDescriptor(
    name, BreakerBoxElement::class.java, BreakerBoxRender::class.java, obj,
    listOf("case") + (0 until BreakerBoxLogic.LINES).flatMap { listOf("up$it", "down$it", "led$it") }
) {
    val cable get() = cableOf()
    val bundle get() = bundleOf()

    init {
        voltageLevelColor = VoltageLevelColor.fromVoltage(nominalVoltage)
    }

    fun draw(states: Array<BreakerBoxLogic.State>) {
        val lit = HashSet<String>()
        val skip = ArrayList<String>()
        for (i in states.indices) {
            if (states[i] == BreakerBoxLogic.State.ON) skip.add("down$i") else skip.add("up$i")
            if (states[i] == BreakerBoxLogic.State.TRIPPED) lit.add("led$i")
        }
        drawParts(lit, *skip.toTypedArray())
    }

    override fun drawItem() = draw(Array(BreakerBoxLogic.LINES) { BreakerBoxLogic.State.ON })

    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.addAll(tr("Sixteen circuit breakers that feed the lines\nof a power bundle cable from one supply.").split("\n"))
        list.add(tr("Front: supply. Back: power bundle cable."))
        list.add(Utils.plotVolt(tr("Nominal voltage: "), nominalVoltage))
        list.add(tr("Each trips on a lasting overload or at once on a short circuit."))
        list.add(tr("Breaking a short circuit can make an arc flash."))
    }
}

class BreakerBoxElement(sixNode: SixNode, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElement(sixNode, side, descriptor), PowerBundle {
    private val desc = descriptor as BreakerBoxDescriptor

    val inLoad = NbtElectricalLoad("inLoad")
    override val bundleLoads = Array(BreakerBoxLogic.LINES) { NbtElectricalLoad("line$it") }
    val contacts = Array(BreakerBoxLogic.LINES) { Resistor(inLoad, bundleLoads[it]) }
    val logic = BreakerBoxLogic()

    init {
        electricalLoadList.add(inLoad)
        electricalComponentList.add(Resistor(inLoad, null).pullDown())
        for (i in bundleLoads.indices) {
            electricalLoadList.add(bundleLoads[i])
            electricalComponentList.add(contacts[i])
            electricalComponentList.add(Resistor(bundleLoads[i], null).pullDown())
        }
        electricalProcessList.add(IProcess { dt -> step(dt) })
        // Fed above what it is made for, it burns out.
        slowProcessList.add(VoltageStateWatchDog(inLoad).setNominalVoltage(desc.nominalVoltage)
            .setDestroys(WorldExplosion(this).machineExplosion()))
    }

    private fun step(dt: Double) {
        var changed = false
        for (i in contacts.indices) {
            val current = if (logic.closed(i)) contacts[i].current else 0.0
            if (logic.step(i, current, dt)) {
                open(i, current)
                changed = true
            }
        }
        if (changed) {
            play(SoundCommand("minecraft:block.lever.click").mulVolume(0.5f, 1.4f).smallRange())
            needPublish()
        }
    }

    private fun open(line: Int, current: Double) {
        ArcFlash.interrupt(coordinate, max(abs(inLoad.voltage), abs(bundleLoads[line].voltage)), current,
            logic.ratedCurrent(line))
        contacts[line].resistance = OPEN_RESISTANCE
    }

    /** Works the handle of breaker [line]: off if on, on if off or tripped. */
    fun toggle(line: Int) {
        if (logic.closed(line)) open(line, contacts[line].current)
        logic.toggle(line)
        applyContact(line)
        play(SoundCommand("minecraft:block.lever.click").mulVolume(0.3f, 0.8f).smallRange())
        needPublish()
    }

    private fun applyContact(line: Int) {
        if (logic.closed(line)) desc.cable.applyTo(contacts[line])
        else contacts[line].resistance = OPEN_RESISTANCE
    }

    override fun initialize() {
        desc.cable.applyTo(inLoad)
        for (i in bundleLoads.indices) {
            desc.cable.applyTo(bundleLoads[i])
            applyContact(i)
        }
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int): ElectricalLoad? = when (lrdu) {
        front -> inLoad
        front.inverse() -> bundleLoads[PowerBundle.lineFor(mask)]
        else -> null
    }

    override fun getThermalLoad(lrdu: LRDU, mask: Int) = null

    override fun getConnectionMask(lrdu: LRDU): Int = when (lrdu) {
        front -> NodeBase.maskElectricalPower
        front.inverse() -> NodeBase.maskPowerBundle
        else -> 0
    }

    override fun newConnectionAt(connection: NodeConnection?, isA: Boolean) {
        PowerBundle.connectOtherLines(this, connection ?: return, isA)
    }

    override fun multiMeterString(): String = Utils.plotVolt("U:", inLoad.voltage) + Utils.plotAmpere("I:", inLoad.current)

    override fun thermoMeterString(): String = ""

    override fun getWaila(): Map<String, String> {
        val info = LinkedHashMap<String, String>()
        info[tr("Supply")] = Utils.plotVolt("", inLoad.voltage) + Utils.plotAmpere(" ", inLoad.current)
        for (i in 0 until BreakerBoxLogic.LINES) {
            val current = abs(contacts[i].current)
            val line = when (logic.state[i]) {
                BreakerBoxLogic.State.TRIPPED -> tr("Tripped at %1$ A", "%.0f".format(logic.tripCurrent[i]))
                BreakerBoxLogic.State.OFF -> continue
                BreakerBoxLogic.State.ON -> if (current < 0.05) continue
                else Utils.plotAmpere("", current) + " / " + Utils.plotAmpere("", logic.ratedCurrent(i))
            }
            info[PowerBundle.colorName(i)] = line
        }
        return info
    }

    override fun hasGui() = true

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try {
            for (i in 0 until BreakerBoxLogic.LINES) {
                stream.writeByte(logic.state[i].ordinal)
                stream.writeByte(logic.rating[i])
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            val id = stream.readByte().toInt()
            when (id) {
                TOGGLE, RATING_UP, RATING_DOWN -> {
                    val line = stream.readInt()
                    if (line !in 0 until BreakerBoxLogic.LINES) return
                    when (id) {
                        TOGGLE -> toggle(line)
                        RATING_UP -> logic.cycleRating(line, true)
                        RATING_DOWN -> logic.cycleRating(line, false)
                    }
                }
            }
            needPublish()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        logic.readFromNBT(nbt)
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        logic.writeToNBT(nbt)
    }

    companion object {
        const val TOGGLE = 1
        const val RATING_UP = 2
        const val RATING_DOWN = 3
        /** An open breaker: far above the pull-down of the line, so a dead line reads 0 V and not half the supply. */
        const val OPEN_RESISTANCE = mods.eln.sim.mna.misc.MnaConst.ultraImpedance
    }
}

class BreakerBoxRender(entity: SixNodeEntity, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElementRender(entity, side, descriptor) {
    private val desc = descriptor as BreakerBoxDescriptor
    val states = Array(BreakerBoxLogic.LINES) { BreakerBoxLogic.State.ON }
    val ratings = IntArray(BreakerBoxLogic.LINES)

    override fun draw() {
        super.draw()
        front!!.glRotateOnX()
        desc.draw(states)
    }

    override fun publishUnserialize(stream: DataInputStream) {
        super.publishUnserialize(stream)
        try {
            for (i in 0 until BreakerBoxLogic.LINES) {
                states[i] = BreakerBoxLogic.State.entries[stream.readByte().toInt()]
                ratings[i] = stream.readByte().toInt()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun getCableRender(lrdu: LRDU): CableRenderDescriptor? = when (lrdu) {
        front -> desc.cable.render
        front!!.inverse() -> desc.bundle?.render
        else -> null
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen = BreakerBoxGui(this)
}

/** Two columns of eight breakers: a color tag, the handle (on, off or tripped) and the rating. */
class BreakerBoxGui(private val render: BreakerBoxRender) : GuiScreenEln() {
    private val handles = arrayOfNulls<GuiButtonEln>(BreakerBoxLogic.LINES)
    private val ratings = arrayOfNulls<GuiButtonEln>(BreakerBoxLogic.LINES)

    override fun newHelper() = GuiHelper(this, 2 * COLUMN + 8, 14 + 8 * ROW + 4)

    private fun x(line: Int) = 6 + (line / 8) * COLUMN
    private fun y(line: Int) = 14 + (line % 8) * ROW

    override fun initGui() {
        super.initGui()
        for (i in 0 until BreakerBoxLogic.LINES) {
            handles[i] = newGuiButton(x(i) + 14, y(i), 52, "")
            ratings[i] = newGuiButton(x(i) + 68, y(i), 34, "")
        }
    }

    override fun preDraw(f: Float, x: Int, y: Int) {
        super.preDraw(f, x, y)
        for (i in 0 until BreakerBoxLogic.LINES) {
            handles[i]!!.displayString = when (render.states[i]) {
                BreakerBoxLogic.State.ON -> tr("On")
                BreakerBoxLogic.State.OFF -> tr("Off")
                BreakerBoxLogic.State.TRIPPED -> "§c" + tr("Tripped")
            }
            val rating = BreakerBoxLogic.RATINGS.getOrElse(render.ratings[i]) { 0.0 }
            ratings[i]!!.displayString = "%.0f A".format(rating)
        }
    }

    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y)
        helper.drawString(6, 3, 0x404040, tr("Shift-click a rating to lower it."))
        for (i in 0 until BreakerBoxLogic.LINES) {
            val rgb = PowerBundle.colorRgb[i]
            helper.drawRect(x(i), y(i) + 5, x(i) + 10, y(i) + 15, 0xFF000000.toInt())
            helper.drawRect(x(i) + 1, y(i) + 6, x(i) + 9, y(i) + 14, 0xFF000000.toInt() or rgb)
        }
    }

    override fun guiObjectEvent(`object`: IGuiObject) {
        super.guiObjectEvent(`object`)
        for (i in 0 until BreakerBoxLogic.LINES) {
            if (`object` === handles[i]) render.clientSetInt(BreakerBoxElement.TOGGLE.toByte(), i)
            if (`object` === ratings[i]) {
                val down = Screen.hasShiftDown()
                render.clientSetInt((if (down) BreakerBoxElement.RATING_DOWN else BreakerBoxElement.RATING_UP).toByte(), i)
            }
        }
    }

    companion object {
        const val COLUMN = 108
        const val ROW = 21
    }
}
