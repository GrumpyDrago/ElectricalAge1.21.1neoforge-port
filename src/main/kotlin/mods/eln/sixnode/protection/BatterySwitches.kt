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

/** Draws a six-node part from named parts; LEDs listed in [lit] are drawn at full brightness. */
open class PartsDescriptor(name: String, element: Class<*>, render: Class<*>, obj: Obj3D, partNames: List<String>) :
    SixNodeDescriptor(name, element, render) {

    protected val parts = partNames.associateWith { obj.getPart(it) }

    init {
        voltageLevelColor = VoltageLevelColor.Neutral
    }

    override fun setParent(item: Item, damage: Int) {
        super.setParent(item, damage)
        Data.addWiring(newItemStack())
    }

    fun drawParts(lit: Set<String>, vararg skip: String) {
        for ((n, p) in parts) {
            if (n in skip) continue
            when {
                n in lit -> UtilsClient.drawLight(p)
                isLamp(n) -> drawDim(p)
                else -> p?.draw()
            }
        }
    }

    companion object {
        /** LEDs and display segments, which are dark unless lit. */
        fun isLamp(part: String) = part.startsWith("led") || part.startsWith("seg")

        fun drawDim(part: mods.eln.misc.Obj3D.Obj3DPart?) {
            if (part == null) return
            mods.eln.compat.client.GL11.glColor4f(0.3f, 0.3f, 0.3f, 1f)
            part.draw()
            mods.eln.compat.client.GL11.glColor4f(1f, 1f, 1f, 1f)
        }
    }

    open fun drawItem() = drawParts(emptySet())

    override fun handleRenderType(item: ItemStack, type: ItemRenderType) = true

    override fun shouldUseRenderHelper(type: ItemRenderType, item: ItemStack, helper: ItemRendererHelper) =
        type != ItemRenderType.INVENTORY

    override fun shouldUseRenderHelperEln(type: ItemRenderType?, item: ItemStack?, helper: ItemRendererHelper?) =
        type != ItemRenderType.INVENTORY

    override fun renderItem(type: ItemRenderType, item: ItemStack, vararg data: Any) {
        if (type == ItemRenderType.INVENTORY) super.renderItem(type, item, *data) else drawItem()
    }
}

// ---------------------------------------------------------------- Battery management system

/**
 * Keeps a battery inside its safe window: it disconnects the load when the battery runs low (and lets a charger
 * through), stops charging when it is full (and still lets the load draw), and opens on overcurrent.
 */
class BatteryManagementLogic {
    enum class State { NORMAL, LOW, HIGH, FAULT }

    var nominalVoltage = 50.0
    /** Fractions of the nominal voltage. */
    var lowCutoff = 0.9
    var reconnect = 1.0
    var highCutoff = 1.08
    var highResume = 1.03
    /** Amps; 0 to use [defaultCurrentLimit]. */
    var currentLimit = 0.0
    var defaultCurrentLimit = 1.0

    var state = State.NORMAL
    var closed = true
        private set
    private var faultTimer = 0.0

    val effectiveCurrentLimit get() = if (currentLimit > 0) currentLimit else defaultCurrentLimit

    /** [battery] and [load] are the voltages on both sides; [current] flows from battery to load. */
    fun step(dt: Double, battery: Double, load: Double, current: Double) {
        if (abs(current) > effectiveCurrentLimit && closed) {
            state = State.FAULT
            faultTimer = FAULT_RETRY
        }
        when (state) {
            State.FAULT -> {
                closed = false
                faultTimer -= dt
                if (faultTimer <= 0) state = State.NORMAL
            }
            State.NORMAL -> {
                closed = true
                if (battery < lowCutoff * nominalVoltage) state = State.LOW
                else if (battery > highCutoff * nominalVoltage) state = State.HIGH
            }
            State.LOW -> {
                // Open to the load, but a charger on the load side may push current in.
                closed = if (closed) current < -MIN_CURRENT else load > battery + DEAD_BAND
                if (battery >= reconnect * nominalVoltage && !closed) state = State.NORMAL
            }
            State.HIGH -> {
                // Open to the charger, but a load may still draw.
                closed = if (closed) current > MIN_CURRENT else battery > load + DEAD_BAND
                if (battery < highResume * nominalVoltage) state = State.NORMAL
            }
        }
    }

    /** A rough state of charge, from the voltage between the two cutoffs. */
    fun chargeEstimate(battery: Double): Double =
        ((battery / nominalVoltage - lowCutoff) / (highResume - lowCutoff)).coerceIn(0.0, 1.0)

    fun writeToNBT(nbt: CompoundTag) {
        nbt.putDouble("nominal", nominalVoltage)
        nbt.putDouble("low", lowCutoff)
        nbt.putDouble("high", highCutoff)
        nbt.putDouble("limit", currentLimit)
        nbt.putByte("state", state.ordinal.toByte())
        nbt.putBoolean("closed", closed)
    }

    fun readFromNBT(nbt: CompoundTag) {
        if (nbt.contains("nominal")) nominalVoltage = nbt.getDouble("nominal")
        if (nbt.contains("low")) lowCutoff = nbt.getDouble("low")
        if (nbt.contains("high")) highCutoff = nbt.getDouble("high")
        currentLimit = nbt.getDouble("limit")
        state = State.entries.getOrElse(nbt.getByte("state").toInt()) { State.NORMAL }
        closed = !nbt.contains("closed") || nbt.getBoolean("closed")
    }

    companion object {
        const val MIN_CURRENT = 0.05
        const val DEAD_BAND = 0.5
        const val FAULT_RETRY = 5.0
    }
}

class BatteryManagementDescriptor(name: String, obj: Obj3D) : PartsDescriptor(
    name, BatteryManagementElement::class.java, BatteryManagementRender::class.java, obj,
    listOf("case", "seg0", "seg1", "seg2", "seg3", "seg4", "ledok", "ledlow", "ledfault")
) {
    fun draw(state: BatteryManagementLogic.State, charge: Float) {
        val lit = HashSet<String>()
        when (state) {
            BatteryManagementLogic.State.NORMAL -> lit.add("ledok")
            BatteryManagementLogic.State.LOW, BatteryManagementLogic.State.HIGH -> lit.add("ledlow")
            BatteryManagementLogic.State.FAULT -> if (System.currentTimeMillis() / 300 % 2 == 0L) lit.add("ledfault")
        }
        val segments = Math.round(charge * 5)
        for (i in 0 until 5) if (i < segments) lit.add("seg$i")
        drawParts(lit)
    }

    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.addAll(tr("Goes between a battery and what it powers.\nCuts the load off before the battery is flat,\nstops charging when it is full,\nand opens on overcurrent.").split("\n"))
        list.add(tr("Front: battery. Back: load and charger. Left: charge signal."))
        list.add(tr("Put a cable in it to rate the contacts."))
    }

    override fun getFrontFromPlace(side: Direction, player: Player): LRDU? = super.getFrontFromPlace(side, player)?.inverse()
}

class BatteryManagementElement(sixNode: SixNode, side: Direction, descriptor: SixNodeDescriptor) :
    SwitchgearElement(sixNode, side, descriptor) {

    val batteryLoad = NbtElectricalLoad("batteryLoad")
    val loadLoad = NbtElectricalLoad("loadLoad")
    val contact = Resistor(batteryLoad, loadLoad)
    val chargeOut = NbtElectricalGateOutput("chargeOut")
    val chargeProcess = NbtElectricalGateOutputProcess("chargeProcess", chargeOut)
    val logic = BatteryManagementLogic()
    private var wasClosed = false
    private var shownState = logic.state
    private var shownCharge = -1

    init {
        electricalLoadList.add(batteryLoad)
        electricalLoadList.add(loadLoad)
        electricalLoadList.add(chargeOut)
        electricalComponentList.add(contact)
        electricalComponentList.add(Resistor(batteryLoad, null).pullDown())
        electricalComponentList.add(Resistor(loadLoad, null).pullDown())
        electricalComponentList.add(chargeProcess)
        electricalProcessList.add(IProcess { time ->
            if (cable == null) return@IProcess
            logic.defaultCurrentLimit = ratedCurrent
            logic.step(time, batteryLoad.voltage, loadLoad.voltage, contact.current)
            apply()
        })
    }

    private fun apply() {
        if (logic.closed != wasClosed) {
            wasClosed = logic.closed
            setContact(contact, wasClosed)
            clickSound()
        }
        val charge = logic.chargeEstimate(batteryLoad.voltage)
        chargeProcess.setOutputNormalized(charge)
        val shown = Math.round(charge * 5).toInt()
        if (logic.state != shownState || shown != shownCharge) {
            shownState = logic.state
            shownCharge = shown
            needPublish()
        }
    }

    override fun cableChanged() {
        applyCable(batteryLoad, loadLoad)
        logic.defaultCurrentLimit = ratedCurrent.coerceAtLeast(0.1)
        wasClosed = logic.closed && cable != null
        setContact(contact, wasClosed)
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int): ElectricalLoad? = when (lrdu) {
        front -> batteryLoad
        front.inverse() -> loadLoad
        front.left() -> chargeOut
        else -> null
    }

    override fun getConnectionMask(lrdu: LRDU): Int = when (lrdu) {
        front, front.inverse() -> if (hasCable) NodeBase.maskElectricalPower else 0
        front.left() -> NodeBase.maskElectricalOutputGate
        else -> 0
    }

    override fun multiMeterString(): String = Utils.plotVolt("Ubat:", batteryLoad.voltage) +
        Utils.plotVolt("Uload:", loadLoad.voltage) + Utils.plotAmpere("I:", contact.current)

    override fun getWaila(): Map<String, String> {
        val info = LinkedHashMap<String, String>()
        info[tr("State")] = when (logic.state) {
            BatteryManagementLogic.State.NORMAL -> tr("Normal")
            BatteryManagementLogic.State.LOW -> tr("Battery low, load cut off")
            BatteryManagementLogic.State.HIGH -> tr("Battery full, charging stopped")
            BatteryManagementLogic.State.FAULT -> tr("Overcurrent, retrying")
        }
        info[tr("Battery voltage")] = Utils.plotVolt("", batteryLoad.voltage)
        info[tr("Current")] = Utils.plotAmpere("", contact.current)
        info[tr("Charge estimate")] = Utils.plotPercent("", logic.chargeEstimate(batteryLoad.voltage))
        return info
    }

    override fun serializeState(stream: DataOutputStream) {
        stream.writeByte(logic.state.ordinal)
        stream.writeFloat(logic.chargeEstimate(batteryLoad.voltage).toFloat())
        stream.writeFloat(logic.nominalVoltage.toFloat())
        stream.writeFloat(logic.lowCutoff.toFloat())
        stream.writeFloat(logic.highCutoff.toFloat())
        stream.writeFloat(logic.currentLimit.toFloat())
    }

    override fun command(id: Int, stream: DataInputStream) {
        when (id) {
            SET_NOMINAL -> logic.nominalVoltage = stream.readDouble().coerceIn(1.0, 100000.0)
            SET_LOW -> logic.lowCutoff = (stream.readDouble() / 100).coerceIn(0.5, 0.99)
            SET_HIGH -> logic.highCutoff = (stream.readDouble() / 100).coerceIn(1.01, 1.25)
            SET_LIMIT -> logic.currentLimit = stream.readDouble().coerceAtLeast(0.0)
        }
        logic.highResume = (logic.highCutoff - 0.05).coerceAtLeast(1.0)
        logic.reconnect = (logic.lowCutoff + 0.1).coerceAtMost(1.0)
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        logic.readFromNBT(nbt)
        logic.highResume = (logic.highCutoff - 0.05).coerceAtLeast(1.0)
        logic.reconnect = (logic.lowCutoff + 0.1).coerceAtMost(1.0)
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        logic.writeToNBT(nbt)
    }

    companion object {
        const val SET_NOMINAL = 1
        const val SET_LOW = 2
        const val SET_HIGH = 3
        const val SET_LIMIT = 4
    }
}

class BatteryManagementRender(entity: SixNodeEntity, side: Direction, descriptor: SixNodeDescriptor) :
    SwitchgearRender(entity, side, descriptor) {
    private val desc = descriptor as BatteryManagementDescriptor
    var state = BatteryManagementLogic.State.NORMAL
    var charge = 0f
    var nominal = 50f
    var low = 0.9f
    var high = 1.08f
    var limit = 0f

    override fun draw() {
        super.draw()
        front!!.glRotateOnX()
        desc.draw(state, charge)
    }

    override fun unserializeState(stream: DataInputStream) {
        state = BatteryManagementLogic.State.entries[stream.readByte().toInt()]
        charge = stream.readFloat()
        nominal = stream.readFloat()
        low = stream.readFloat()
        high = stream.readFloat()
        limit = stream.readFloat()
    }

    override fun getCableRender(lrdu: LRDU): CableRenderDescriptor? = when (lrdu) {
        front, front!!.inverse() -> cableRender
        front!!.left() -> signalRender
        else -> null
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen = SettingsGui(player, inventory).apply {
        addFields(this)
    }

    private fun addFields(gui: SettingsGui) {
        gui.addField(SettingsGui.Field(tr("Battery (V)"), tr("The nominal voltage of the battery."), { nominal.toDouble() }) {
            clientSetDouble(BatteryManagementElement.SET_NOMINAL.toByte(), it)
        })
        gui.addField(SettingsGui.Field(tr("Low cutoff (%)"), tr("Cuts the load off below this share\nof the nominal voltage."), { low * 100.0 }) {
            clientSetDouble(BatteryManagementElement.SET_LOW.toByte(), it)
        })
        gui.addField(SettingsGui.Field(tr("High cutoff (%)"), tr("Stops charging above this share\nof the nominal voltage."), { high * 100.0 }) {
            clientSetDouble(BatteryManagementElement.SET_HIGH.toByte(), it)
        })
        gui.addField(SettingsGui.Field(tr("Current limit (A)"), tr("Opens above this current.\n0 uses the current of the cable."), { limit.toDouble() }) {
            clientSetDouble(BatteryManagementElement.SET_LIMIT.toByte(), it)
        })
    }
}

// ---------------------------------------------------------------- Automatic transfer switch

/** Feeds a load from the mains, and from a backup source while the mains are out of range. */
class TransferSwitchLogic {
    enum class Source { NONE, PRIMARY, BACKUP }
    enum class Mode { AUTO, PRIMARY, BACKUP, OFF }

    var nominalVoltage = 50.0
    /** Allowed deviation, as a fraction of the nominal voltage. */
    var tolerance = 0.15
    var transferDelay = 0.5
    var retransferDelay = 10.0
    var mode = Mode.AUTO

    var source = Source.NONE
        private set
    private var badTime = 0.0
    private var goodTime = 0.0

    fun inRange(voltage: Double) = abs(voltage - nominalVoltage) <= tolerance * nominalVoltage

    fun step(dt: Double, primary: Double, backup: Double) {
        val primaryOk = inRange(primary)
        val backupOk = inRange(backup)
        when (mode) {
            Mode.PRIMARY -> { source = Source.PRIMARY; return }
            Mode.BACKUP -> { source = Source.BACKUP; return }
            Mode.OFF -> { source = Source.NONE; return }
            Mode.AUTO -> {}
        }
        goodTime = if (primaryOk) goodTime + dt else 0.0
        when (source) {
            Source.PRIMARY -> {
                badTime = if (primaryOk) 0.0 else badTime + dt
                if (badTime >= transferDelay) {
                    source = if (backupOk) Source.BACKUP else Source.NONE
                    badTime = 0.0
                }
            }
            Source.BACKUP -> {
                // Back to the mains once they have been good for a while, or at once if the backup fails.
                if (goodTime >= retransferDelay || (!backupOk && primaryOk)) source = Source.PRIMARY
                else if (!backupOk) source = Source.NONE
            }
            Source.NONE -> {
                // Healthy mains are preferred, once they have been good for the transfer delay.
                if (primaryOk) {
                    if (goodTime >= transferDelay) source = Source.PRIMARY
                } else if (backupOk) {
                    source = Source.BACKUP
                }
            }
        }
    }

    fun writeToNBT(nbt: CompoundTag) {
        nbt.putDouble("nominal", nominalVoltage)
        nbt.putDouble("tolerance", tolerance)
        nbt.putDouble("transferDelay", transferDelay)
        nbt.putDouble("retransferDelay", retransferDelay)
        nbt.putByte("mode", mode.ordinal.toByte())
        nbt.putByte("source", source.ordinal.toByte())
    }

    fun readFromNBT(nbt: CompoundTag) {
        if (nbt.contains("nominal")) nominalVoltage = nbt.getDouble("nominal")
        if (nbt.contains("tolerance")) tolerance = nbt.getDouble("tolerance")
        if (nbt.contains("transferDelay")) transferDelay = nbt.getDouble("transferDelay")
        if (nbt.contains("retransferDelay")) retransferDelay = nbt.getDouble("retransferDelay")
        mode = Mode.entries.getOrElse(nbt.getByte("mode").toInt()) { Mode.AUTO }
        source = Source.entries.getOrElse(nbt.getByte("source").toInt()) { Source.NONE }
    }
}

class TransferSwitchDescriptor(name: String, obj: Obj3D) : PartsDescriptor(
    name, TransferSwitchElement::class.java, TransferSwitchRender::class.java, obj,
    listOf("case", "handle", "ledprimary", "ledbackup")
) {
    private val handle = parts["handle"]
    val speed = handle?.getFloat("speed") ?: 0.06f
    private val alphaPrimary = handle?.getFloat("alphaPrimary") ?: -45f
    private val alphaBackup = handle?.getFloat("alphaBackup") ?: 45f

    /** [position] from -1 (mains) through 0 (off) to 1 (backup). */
    fun draw(position: Float, source: TransferSwitchLogic.Source) {
        val lit = when (source) {
            TransferSwitchLogic.Source.PRIMARY -> setOf("ledprimary")
            TransferSwitchLogic.Source.BACKUP -> setOf("ledbackup")
            TransferSwitchLogic.Source.NONE -> emptySet()
        }
        drawParts(lit, "handle")
        val angle = if (position < 0) -position * alphaPrimary else position * alphaBackup
        handle?.draw(angle, 1f, 0f, 0f)
    }

    override fun drawItem() = draw(0f, TransferSwitchLogic.Source.NONE)

    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.addAll(tr("Feeds a load from the mains, and switches it\nto a backup source when the mains fail.\nWith a battery and a charger it makes a UPS.").split("\n"))
        list.add(tr("Front: load. Back: mains. Left: backup. Right: on-backup signal."))
        list.add(tr("Put a cable in it to rate the contacts."))
    }
}

class TransferSwitchElement(sixNode: SixNode, side: Direction, descriptor: SixNodeDescriptor) :
    SwitchgearElement(sixNode, side, descriptor) {

    val outLoad = NbtElectricalLoad("outLoad")
    val primaryLoad = NbtElectricalLoad("primaryLoad")
    val backupLoad = NbtElectricalLoad("backupLoad")
    val primaryContact = Resistor(primaryLoad, outLoad)
    val backupContact = Resistor(backupLoad, outLoad)
    val signalOut = NbtElectricalGateOutput("signalOut")
    val signalProcess = NbtElectricalGateOutputProcess("signalProcess", signalOut)
    val logic = TransferSwitchLogic()
    private var applied = TransferSwitchLogic.Source.NONE
    /** Break before make: the old contact opens one step before the new one closes. */
    private var pending: TransferSwitchLogic.Source? = null

    init {
        electricalLoadList.add(outLoad)
        electricalLoadList.add(primaryLoad)
        electricalLoadList.add(backupLoad)
        electricalLoadList.add(signalOut)
        electricalComponentList.add(primaryContact)
        electricalComponentList.add(backupContact)
        for (l in listOf(outLoad, primaryLoad, backupLoad)) electricalComponentList.add(Resistor(l, null).pullDown())
        electricalComponentList.add(signalProcess)
        electricalProcessList.add(IProcess { time ->
            if (cable == null) return@IProcess
            pending?.let {
                pending = null
                connect(it)
                return@IProcess
            }
            logic.step(time, primaryLoad.voltage, backupLoad.voltage)
            if (logic.source != applied) {
                if (applied != TransferSwitchLogic.Source.NONE && logic.source != TransferSwitchLogic.Source.NONE) {
                    connect(TransferSwitchLogic.Source.NONE)
                    pending = logic.source
                } else {
                    connect(logic.source)
                }
            }
        })
    }

    private fun connect(source: TransferSwitchLogic.Source) {
        applied = source
        setContact(primaryContact, source == TransferSwitchLogic.Source.PRIMARY)
        setContact(backupContact, source == TransferSwitchLogic.Source.BACKUP)
        signalProcess.setOutputNormalized(if (source == TransferSwitchLogic.Source.BACKUP) 1.0 else 0.0)
        clickSound()
        needPublish()
    }

    override fun cableChanged() {
        applyCable(outLoad, primaryLoad, backupLoad)
        setContact(primaryContact, applied == TransferSwitchLogic.Source.PRIMARY)
        setContact(backupContact, applied == TransferSwitchLogic.Source.BACKUP)
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int): ElectricalLoad? = when (lrdu) {
        front -> outLoad
        front.inverse() -> primaryLoad
        front.left() -> backupLoad
        front.right() -> signalOut
        else -> null
    }

    override fun getConnectionMask(lrdu: LRDU): Int = when (lrdu) {
        front, front.inverse(), front.left() -> if (hasCable) NodeBase.maskElectricalPower else 0
        front.right() -> NodeBase.maskElectricalOutputGate
        else -> 0
    }

    override fun multiMeterString(): String = Utils.plotVolt("Umains:", primaryLoad.voltage) +
        Utils.plotVolt("Ubackup:", backupLoad.voltage) + Utils.plotVolt("Uout:", outLoad.voltage)

    private fun sourceName(s: TransferSwitchLogic.Source) = when (s) {
        TransferSwitchLogic.Source.PRIMARY -> tr("Mains")
        TransferSwitchLogic.Source.BACKUP -> tr("Backup")
        TransferSwitchLogic.Source.NONE -> tr("None")
    }

    override fun getWaila(): Map<String, String> {
        val info = LinkedHashMap<String, String>()
        info[tr("Source")] = sourceName(applied)
        info[tr("Mains")] = Utils.plotVolt("", primaryLoad.voltage)
        info[tr("Backup")] = Utils.plotVolt("", backupLoad.voltage)
        info[tr("Output current")] = Utils.plotAmpere("", primaryContact.current + backupContact.current)
        return info
    }

    override fun serializeState(stream: DataOutputStream) {
        stream.writeByte(applied.ordinal)
        stream.writeByte(logic.mode.ordinal)
        stream.writeFloat(logic.nominalVoltage.toFloat())
        stream.writeFloat(logic.tolerance.toFloat())
        stream.writeFloat(logic.transferDelay.toFloat())
        stream.writeFloat(logic.retransferDelay.toFloat())
    }

    override fun command(id: Int, stream: DataInputStream) {
        when (id) {
            SET_NOMINAL -> logic.nominalVoltage = stream.readDouble().coerceIn(1.0, 100000.0)
            SET_TOLERANCE -> logic.tolerance = (stream.readDouble() / 100).coerceIn(0.01, 0.5)
            SET_TRANSFER -> logic.transferDelay = stream.readDouble().coerceIn(0.05, 60.0)
            SET_RETRANSFER -> logic.retransferDelay = stream.readDouble().coerceIn(0.05, 3600.0)
            NEXT_MODE -> logic.mode = TransferSwitchLogic.Mode.entries[(logic.mode.ordinal + 1) % TransferSwitchLogic.Mode.entries.size]
        }
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        logic.readFromNBT(nbt)
        applied = logic.source
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        logic.writeToNBT(nbt)
    }

    companion object {
        const val SET_NOMINAL = 1
        const val SET_TOLERANCE = 2
        const val SET_TRANSFER = 3
        const val SET_RETRANSFER = 4
        const val NEXT_MODE = 5
    }
}

class TransferSwitchRender(entity: SixNodeEntity, side: Direction, descriptor: SixNodeDescriptor) :
    SwitchgearRender(entity, side, descriptor) {
    private val desc = descriptor as TransferSwitchDescriptor
    private val handle = RcInterpolator(desc.speed)
    private var boot = true
    var source = TransferSwitchLogic.Source.NONE
    var mode = TransferSwitchLogic.Mode.AUTO
    var nominal = 50f
    var tolerance = 0.15f
    var transfer = 0.5f
    var retransfer = 10f

    private fun target() = when (source) {
        TransferSwitchLogic.Source.PRIMARY -> -1f
        TransferSwitchLogic.Source.BACKUP -> 1f
        TransferSwitchLogic.Source.NONE -> 0f
    }

    override fun draw() {
        super.draw()
        front!!.glRotateOnX()
        desc.draw(handle.get(), source)
    }

    override fun refresh(deltaT: Float) {
        handle.target = target()
        handle.step(deltaT)
    }

    override fun unserializeState(stream: DataInputStream) {
        source = TransferSwitchLogic.Source.entries[stream.readByte().toInt()]
        mode = TransferSwitchLogic.Mode.entries[stream.readByte().toInt()]
        nominal = stream.readFloat()
        tolerance = stream.readFloat()
        transfer = stream.readFloat()
        retransfer = stream.readFloat()
        if (boot) handle.setValue(target())
        boot = false
    }

    override fun getCableRender(lrdu: LRDU): CableRenderDescriptor? = when (lrdu) {
        front, front!!.inverse(), front!!.left() -> cableRender
        front!!.right() -> signalRender
        else -> null
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen = SettingsGui(player, inventory).also { gui ->
        gui.addField(SettingsGui.Field(tr("Voltage (V)"), tr("The voltage of the mains and the backup."), { nominal.toDouble() }) {
            clientSetDouble(TransferSwitchElement.SET_NOMINAL.toByte(), it)
        })
        gui.addField(SettingsGui.Field(tr("Tolerance (%)"), tr("A source further than this from the\nnominal voltage counts as failed."), { tolerance * 100.0 }) {
            clientSetDouble(TransferSwitchElement.SET_TOLERANCE.toByte(), it)
        })
        gui.addField(SettingsGui.Field(tr("Transfer after (s)"), tr("How long the mains must fail\nbefore it switches to the backup."), { transfer.toDouble() }) {
            clientSetDouble(TransferSwitchElement.SET_TRANSFER.toByte(), it)
        })
        gui.addField(SettingsGui.Field(tr("Return after (s)"), tr("How long the mains must be back\nbefore it switches back to them."), { retransfer.toDouble() }) {
            clientSetDouble(TransferSwitchElement.SET_RETRANSFER.toByte(), it)
        })
        gui.addButton(SettingsGui.Button(8, 61, 110, {
            when (mode) {
                TransferSwitchLogic.Mode.AUTO -> tr("Mode: automatic")
                TransferSwitchLogic.Mode.PRIMARY -> tr("Mode: mains only")
                TransferSwitchLogic.Mode.BACKUP -> tr("Mode: backup only")
                TransferSwitchLogic.Mode.OFF -> tr("Mode: off")
            }
        }) { clientSend(TransferSwitchElement.NEXT_MODE) })
    }
}
