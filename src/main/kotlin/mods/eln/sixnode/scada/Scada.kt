package mods.eln.sixnode.scada

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.compat.tagCompound
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.gridnode.substation.SubstationElement
import mods.eln.gui.GuiButtonEln
import mods.eln.gui.GuiHelper
import mods.eln.gui.GuiScreenEln
import mods.eln.gui.GuiTextFieldEln
import mods.eln.gui.IGuiObject
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Obj3D
import mods.eln.misc.Utils
import mods.eln.misc.UtilsClient
import mods.eln.node.ComputerControllable
import mods.eln.node.NodeBase
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeDescriptor
import mods.eln.node.six.SixNodeElement
import mods.eln.node.six.SixNodeElementRender
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sixnode.protection.OvercurrentRelayElement
import mods.eln.sixnode.protection.PartsDescriptor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/*
 * SCADA (supervisory control and data acquisition): a control room view of a whole power system. A monitor shows the
 * devices linked to it, with their readings and alarms, and switches breakers from afar. Devices get onto a monitor in
 * two ways: linked one by one with the SCADA linker, or reported over the air by a SCADA transmitter placed near them,
 * on the monitor's channel.
 */

/** A device on a monitor: a block, and the face of the part for parts on faces (-1 for a whole block). */
data class DeviceRef(val x: Int, val y: Int, val z: Int, val dimension: Int, val face: Int) {
    fun write(): CompoundTag = CompoundTag().apply {
        putInt("x", x); putInt("y", y); putInt("z", z); putInt("dim", dimension); putInt("face", face)
    }

    fun element(): Any? {
        val node = NodeManager.instance?.getNodeFromCoordonate(Coordinate(x, y, z, dimension)) ?: return null
        return when (node) {
            is SixNode -> if (face >= 0) Direction.fromInt(face)?.let { node.getElement(it) } else null
            is TransparentNode -> node.element
            else -> null
        }
    }

    companion object {
        fun read(t: CompoundTag) = DeviceRef(t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getInt("dim"), t.getInt("face"))

        fun of(element: Any): DeviceRef? = when (element) {
            is SixNodeElement -> element.sixNode?.coordinate?.let { DeviceRef(it.x, it.y, it.z, it.dimension, element.side.int) }
            is TransparentNodeElement -> element.node?.coordinate?.let { DeviceRef(it.x, it.y, it.z, it.dimension, -1) }
            else -> null
        }
    }
}

/** A switch on a monitor's line: its label, whether it is closed, and what clicking it does. */
class Control(val label: String, val closed: Boolean, val toggle: () -> Unit)

/** What a monitor makes of a device. */
object ScadaDevices {
    fun name(element: Any): String = when (element) {
        is SixNodeElement -> element.sixNodeElementDescriptor.name
        is TransparentNodeElement -> element.descriptor?.name ?: "?"
        else -> "?"
    }

    fun info(element: Any): List<String> {
        if (element is mods.eln.transparentnode.battery.BatteryElement) return batteryInfo(element)
        return waila(element)
    }

    /** A battery's name already says its chemistry: life and charge on one line, then the temperature of its cells. */
    private fun batteryInfo(b: mods.eln.transparentnode.battery.BatteryElement): List<String> {
        val values = b.computerValues()
        val lines = ArrayList<String>()
        lines.add(tr("Life: %1$  Charge: %2$", Utils.plotPercentFine("", b.batteryProcess.life).trim(),
            Utils.plotPercent("", b.batteryProcess.charge).trim()))
        lines.add(tr("Temperature: %1$", Utils.plotCelsius("", (values["temperature"] as? Double) ?: 0.0).trim()))
        if (b.vented) lines.add(tr("Vented: cool it with a water bucket"))
        return lines
    }

    private fun waila(element: Any): List<String> = when (element) {
        is SixNodeElement -> element.getWaila() ?: emptyMap()
        is TransparentNodeElement -> element.getWaila()
        else -> emptyMap()
    }.filterKeys { !it.contains("Subsystem") }.map { (k, v) -> "$k: " + v.replace(Regex("§."), "").trim() }

    /** Something an operator should look at: a trip, a vented battery, an overheated transformer. */
    fun alarm(element: Any): Boolean {
        val values = (element as? ComputerControllable)?.computerValues() ?: return false
        if (values["vented"] == true || values["overheated"] == true || values["overvoltage"] == true) return true
        if (values.values.any { it == "locked_out" || it == "tripped" }) return true
        @Suppress("UNCHECKED_CAST")
        val breakers = values["breakers"] as? List<Map<String, Any?>>
        return breakers?.any { it["state"] == "tripped" } == true
    }

    fun controls(element: Any): List<Control> {
        val c = element as? ComputerControllable ?: return emptyList()
        val values = c.computerValues()
        return when (element) {
            is SubstationElement -> listOf(true, false).map { high ->
                val closed = values[if (high) "highBreaker" else "lowBreaker"] == "closed"
                Control(if (high) "HV" else "LV", closed) { c.computerCall(if (closed) "open" else "close", listOf(if (high) "high" else "low")) }
            }
            is OvercurrentRelayElement -> {
                val closed = values["closed"] == true
                listOf(Control(tr("Breaker"), closed) { c.computerCall(if (closed) "trip" else "close", emptyList()) })
            }
            else -> if (c.computerActions().any { it.startsWith("setClosed") }) {
                val closed = values["closed"] == true
                listOf(Control(tr("Switch"), closed) { c.computerCall("setClosed", listOf(!closed)) })
            } else emptyList()
        }
    }
}

// ---------------------------------------------------------------- Linker

/** Use it on devices to collect them, then on a SCADA monitor to put them on it; that empties it again. */
class ScadaLinkerDescriptor(name: String) : GenericItemUsingDamageDescriptor(name) {
    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        val n = itemStack?.tagCompound?.getList(LIST, Tag.TAG_COMPOUND.toInt())?.size ?: 0
        list.addAll(tr("Use it on devices to collect them, then on\na SCADA monitor to show them there.").split("\n"))
        list.add(tr("%1$ devices collected.", n))
    }

    companion object {
        const val LIST = "scadaDevices"

        /** [player] used a linker [stack] on [element]; true when handled. */
        @JvmStatic
        fun use(element: Any?, player: Player, stack: ItemStack?): Boolean {
            if (stack == null || element == null) return false
            val linker = Eln.scadaLinkerElement ?: return false
            if (!linker.checkSameItemStack(stack)) return false
            val tag = stack.tagCompound ?: CompoundTag()
            val list = tag.getList(LIST, Tag.TAG_COMPOUND.toInt())
            if (element is ScadaMonitorElement) {
                val added = element.addDevices((0 until list.size).map { DeviceRef.read(list.getCompound(it)) })
                Utils.addChatMessage(player, tr("%1$ devices added to the monitor.", added))
                list.clear()
            } else {
                val ref = DeviceRef.of(element) ?: return false
                if ((0 until list.size).none { DeviceRef.read(list.getCompound(it)) == ref }) list.add(ref.write())
                Utils.addChatMessage(player, tr("Collected %1$ (%2$ in the linker).", ScadaDevices.name(element), list.size))
            }
            tag.put(LIST, list)
            stack.tagCompound = tag
            return true
        }
    }
}

// ---------------------------------------------------------------- Transmitter

class ScadaTransmitterDescriptor(name: String, obj: Obj3D) :
    PartsDescriptor(name, ScadaTransmitterElement::class.java, ScadaTransmitterRender::class.java, obj, listOf("case", "antenna", "led")) {
    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.addAll(tr("Reports the devices within %1$ blocks to\nSCADA monitors on its channel, wirelessly.", RANGE).split("\n"))
    }

    companion object {
        const val RANGE = 2
    }
}

class ScadaTransmitterElement(sixNode: SixNode, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElement(sixNode, side, descriptor) {
    var channel = "SCADA"
        private set

    /** The devices near it, other than SCADA parts, up to eight. */
    fun devices(): List<DeviceRef> {
        val c = sixNode?.coordinate ?: return emptyList()
        val r = ScadaTransmitterDescriptor.RANGE
        val found = ArrayList<DeviceRef>()
        for (node in NodeManager.instance?.nodeList ?: return emptyList()) {
            val n = node.coordinate
            if (n.dimension != c.dimension || Math.abs(n.x - c.x) > r || Math.abs(n.y - c.y) > r || Math.abs(n.z - c.z) > r) continue
            val elements: List<Any> = when (node) {
                is SixNode -> Direction.entries.mapNotNull { node.getElement(it) }
                is TransparentNode -> listOfNotNull(node.element)
                else -> emptyList()
            }
            for (e in elements) {
                if (e is ScadaTransmitterElement || e is ScadaMonitorElement) continue
                if (e !is ComputerControllable && e !is TransparentNodeElement) continue
                DeviceRef.of(e)?.let { found.add(it) }
                if (found.size >= 8) return found
            }
        }
        return found
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int): ElectricalLoad? = null
    override fun getThermalLoad(lrdu: LRDU, mask: Int): ThermalLoad? = null
    override fun getConnectionMask(lrdu: LRDU) = 0
    override fun multiMeterString() = ""
    override fun thermoMeterString() = ""
    override fun initialize() {}
    override fun hasGui() = true

    override fun getWaila(): Map<String, String> = linkedMapOf(tr("Channel") to channel, tr("Devices") to devices().size.toString())

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try { stream.writeUTF(channel) } catch (e: IOException) { e.printStackTrace() }
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            if (stream.readByte().toInt() == SET_CHANNEL) channel = stream.readUTF().take(32)
            needPublish()
        } catch (e: IOException) { e.printStackTrace() }
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        if (nbt.contains("channel")) channel = nbt.getString("channel")
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putString("channel", channel)
    }

    companion object {
        const val SET_CHANNEL = 1
    }
}

class ScadaTransmitterRender(entity: SixNodeEntity, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElementRender(entity, side, descriptor) {
    private val desc = descriptor as ScadaTransmitterDescriptor
    var channel = ""

    override fun draw() {
        super.draw()
        front!!.glRotateOnX()
        desc.drawParts(if (System.currentTimeMillis() / 700 % 3 == 0L) setOf("led") else emptySet())
    }

    override fun publishUnserialize(stream: DataInputStream) {
        super.publishUnserialize(stream)
        try { channel = stream.readUTF() } catch (e: IOException) { e.printStackTrace() }
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen = ChannelGui(channel) {
        clientSetString(ScadaTransmitterElement.SET_CHANNEL.toByte(), it)
    }
}

/** A screen with a channel name. */
class ChannelGui(private val initial: String, private val send: (String) -> Unit) : GuiScreenEln() {
    private lateinit var field: GuiTextFieldEln

    override fun newHelper() = GuiHelper(this, 160, 30)

    override fun initGui() {
        super.initGui()
        field = newGuiTextField(60, 9, 92)
        field.setText(initial)
        field.setObserver(this)
    }

    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y)
        helper.drawString(8, 11, 0x404040, tr("Channel"))
    }

    override fun textFieldNewValue(textField: GuiTextFieldEln, value: String) {
        if (textField === field) send(value)
    }
}

// ---------------------------------------------------------------- Monitor

class ScadaMonitorDescriptor(name: String, obj: Obj3D) :
    PartsDescriptor(name, ScadaMonitorElement::class.java, ScadaMonitorRender::class.java, obj, listOf("case", "screen", "ledok", "ledalarm")) {
    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.addAll(tr("A control room screen: readings, alarms and\nbreaker switches of the devices linked to it\nwith a SCADA linker, or reported by SCADA\ntransmitters on its channel.").split("\n"))
    }

    fun draw(alarms: Int, devices: Int) {
        val lit = HashSet<String>()
        if (devices > 0) lit.add("screen")
        if (alarms > 0) {
            if (System.currentTimeMillis() / 400 % 2 == 0L) lit.add("ledalarm")
        } else if (devices > 0) lit.add("ledok")
        drawParts(lit)
    }
}

/** One line of a monitor, as sent to the screen. */
class MonitorLine(val name: String, val info: List<String>, val alarm: Boolean, val controls: List<Pair<String, Boolean>>, val linked: Boolean)

class ScadaMonitorElement(sixNode: SixNode, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElement(sixNode, side, descriptor) {
    /** Devices linked to it by hand. */
    private val linked = ArrayList<DeviceRef>()
    var channel = "SCADA"
        private set
    private var timer = 0.0
    private var current: List<Pair<DeviceRef, Any>> = emptyList()
    var alarms = 0
        private set

    init {
        slowProcessList.add(IProcess { dt ->
            timer += dt
            if (timer >= 1.0) {
                timer = 0.0
                refresh()
                needPublish()
            }
        })
    }

    fun addDevices(refs: List<DeviceRef>): Int {
        var n = 0
        for (r in refs) if (r !in linked && linked.size < MAX_DEVICES) { linked.add(r); n++ }
        refresh()
        needPublish()
        return n
    }

    /** The transmitters on its channel, in its dimension and within range. */
    private fun transmitted(): List<DeviceRef> {
        if (channel.isEmpty()) return emptyList()
        val c = sixNode?.coordinate ?: return emptyList()
        val out = ArrayList<DeviceRef>()
        for (node in NodeManager.instance?.nodeList ?: return emptyList()) {
            if (node !is SixNode || node.coordinate.dimension != c.dimension || node.coordinate.trueDistanceTo(c) > WIRELESS_RANGE) continue
            for (dir in Direction.entries) {
                val t = node.getElement(dir) as? ScadaTransmitterElement ?: continue
                if (t.channel == channel) out.addAll(t.devices())
            }
        }
        return out
    }

    private fun refresh() {
        val refs = (linked + transmitted()).distinct().take(MAX_DEVICES)
        current = refs.mapNotNull { r -> r.element()?.let { r to it } }
        alarms = current.count { ScadaDevices.alarm(it.second) }
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int): ElectricalLoad? = null
    override fun getThermalLoad(lrdu: LRDU, mask: Int): ThermalLoad? = null
    override fun getConnectionMask(lrdu: LRDU) = 0
    override fun multiMeterString() = ""
    override fun thermoMeterString() = ""
    override fun initialize() {}
    override fun hasGui() = true

    override fun getWaila(): Map<String, String> = linkedMapOf(
        tr("Devices") to current.size.toString(), tr("Alarms") to alarms.toString(), tr("Channel") to channel
    )

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try {
            stream.writeUTF(channel)
            stream.writeByte(alarms)
            stream.writeByte(current.size)
            for ((ref, e) in current) {
                stream.writeUTF(ScadaDevices.name(e))
                val info = ScadaDevices.info(e).take(3)
                stream.writeByte(info.size)
                for (l in info) stream.writeUTF(l.take(60))
                stream.writeBoolean(ScadaDevices.alarm(e))
                val controls = ScadaDevices.controls(e)
                stream.writeByte(controls.size)
                for (c in controls) { stream.writeUTF(c.label); stream.writeBoolean(c.closed) }
                stream.writeBoolean(ref in linked)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            when (stream.readByte().toInt()) {
                SET_CHANNEL -> channel = stream.readUTF().take(32)
                CONTROL -> {
                    val v = stream.readInt()
                    val device = current.getOrNull(v shr 8)?.second
                    if (device != null) ScadaDevices.controls(device).getOrNull(v and 0xFF)?.toggle?.invoke()
                }
                REMOVE -> {
                    val ref = current.getOrNull(stream.readInt())?.first
                    linked.remove(ref)
                }
            }
            refresh()
            needPublish()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        if (nbt.contains("channel")) channel = nbt.getString("channel")
        linked.clear()
        val list = nbt.getList("devices", Tag.TAG_COMPOUND.toInt())
        for (i in 0 until list.size) linked.add(DeviceRef.read(list.getCompound(i)))
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putString("channel", channel)
        nbt.put("devices", ListTag().apply { linked.forEach { add(it.write()) } })
    }

    companion object {
        const val SET_CHANNEL = 1
        const val CONTROL = 2
        const val REMOVE = 3
        const val MAX_DEVICES = 16
        const val WIRELESS_RANGE = 256.0
    }
}

class ScadaMonitorRender(entity: SixNodeEntity, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElementRender(entity, side, descriptor) {
    private val desc = descriptor as ScadaMonitorDescriptor
    var channel = ""
    var alarms = 0
    var lines: List<MonitorLine> = emptyList()

    override fun draw() {
        super.draw()
        front!!.glRotateOnX()
        desc.draw(alarms, lines.size)
    }

    override fun publishUnserialize(stream: DataInputStream) {
        super.publishUnserialize(stream)
        try {
            channel = stream.readUTF()
            alarms = stream.readByte().toInt()
            lines = List(stream.readByte().toInt()) {
                val name = stream.readUTF()
                val info = List(stream.readByte().toInt()) { stream.readUTF() }
                val alarm = stream.readBoolean()
                val controls = List(stream.readByte().toInt()) { stream.readUTF() to stream.readBoolean() }
                MonitorLine(name, info, alarm, controls, stream.readBoolean())
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun getCableRender(lrdu: LRDU): CableRenderDescriptor? = null

    override fun newGuiDraw(side: Direction, player: Player): Screen = ScadaMonitorGui(this)
}

/** The monitor's screen: a page of devices, each with its readings, its alarm and its switches. */
class ScadaMonitorGui(private val render: ScadaMonitorRender) : GuiScreenEln() {
    private lateinit var channel: GuiTextFieldEln
    private lateinit var prev: GuiButtonEln
    private lateinit var next: GuiButtonEln
    private val controlButtons = ArrayList<Triple<GuiButtonEln, Int, Int>>()
    private val removeButtons = ArrayList<Pair<GuiButtonEln, Int>>()
    private var page = 0
    private var builtFor = -1

    override fun newHelper() = GuiHelper(this, WIDTH, 30 + ROWS * ROW + 26)

    override fun initGui() {
        super.initGui()
        channel = newGuiTextField(60, 8, 90)
        channel.setText(render.channel)
        channel.setObserver(this)
        prev = newGuiButton(8, 30 + ROWS * ROW + 2, 40, "<")
        next = newGuiButton(WIDTH - 48, 30 + ROWS * ROW + 2, 40, ">")
        buildButtons()
    }

    private fun buildButtons() {
        for ((b, _, _) in controlButtons) helper.remove(b)
        for ((b, _) in removeButtons) helper.remove(b)
        controlButtons.clear()
        removeButtons.clear()
        val first = page * ROWS
        for (row in 0 until ROWS) {
            val i = first + row
            val line = render.lines.getOrNull(i) ?: break
            val y = 30 + row * ROW
            line.controls.forEachIndexed { k, _ ->
                controlButtons.add(Triple(newGuiButton(WIDTH - 8 - 52 * (line.controls.size - k) - (if (line.linked) 22 else 0), y + 4, 50, ""), i, k))
            }
            if (line.linked) removeButtons.add(newGuiButton(WIDTH - 28, y + 4, 20, "x") to i)
        }
        builtFor = render.lines.size * 100 + page
    }

    override fun preDraw(f: Float, x: Int, y: Int) {
        super.preDraw(f, x, y)
        val pages = Math.max(1, (render.lines.size + ROWS - 1) / ROWS)
        if (page >= pages) page = pages - 1
        if (builtFor != render.lines.size * 100 + page) buildButtons()
        for ((b, i, k) in controlButtons) {
            val c = render.lines.getOrNull(i)?.controls?.getOrNull(k) ?: continue
            b.displayString = (if (c.second) "§2" else "§c") + c.first
        }
        prev.visible = page > 0
        next.visible = page < pages - 1
    }

    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y)
        helper.drawString(8, 10, 0x404040, tr("Channel"))
        val status = if (render.alarms > 0) "§c" + tr("%1$ alarms", render.alarms) else "§2" + tr("All normal")
        helper.drawString(160, 10, 0x404040, status)
        if (render.lines.isEmpty()) {
            helper.drawString(8, 34, 0x404040, tr("No devices: link them with a SCADA linker,"))
            helper.drawString(8, 44, 0x404040, tr("or put SCADA transmitters on this channel near them."))
        }
        for (row in 0 until ROWS) {
            val line = render.lines.getOrNull(page * ROWS + row) ?: break
            val y = 30 + row * ROW
            helper.drawRect(6, y, WIDTH - 6, y + ROW - 2, if (line.alarm) 0x40FF0000 else 0x20000000)
            helper.drawString(10, y + 2, if (line.alarm) 0xC00000 else 0x202020, (if (line.alarm) "! " else "") + line.name)
            line.info.take(2).forEachIndexed { k, l -> helper.drawString(14, y + 12 + 9 * k, 0x505050, l) }
        }
    }

    override fun textFieldNewValue(textField: GuiTextFieldEln, value: String) {
        if (textField === channel) render.clientSetString(ScadaMonitorElement.SET_CHANNEL.toByte(), value)
    }

    override fun guiObjectEvent(`object`: IGuiObject) {
        super.guiObjectEvent(`object`)
        if (`object` === prev) page--
        if (`object` === next) page++
        for ((b, i, k) in controlButtons) if (`object` === b) render.clientSetInt(ScadaMonitorElement.CONTROL.toByte(), (i shl 8) or k)
        for ((b, i) in removeButtons) if (`object` === b) render.clientSetInt(ScadaMonitorElement.REMOVE.toByte(), i)
    }

    companion object {
        const val WIDTH = 300
        const val ROWS = 5
        const val ROW = 34
    }
}
