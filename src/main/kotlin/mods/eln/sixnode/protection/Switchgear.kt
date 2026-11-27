package mods.eln.sixnode.protection

import mods.eln.compat.IInventory
import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.gui.GuiButtonEln
import mods.eln.gui.GuiContainerEln
import mods.eln.gui.GuiHelperContainer
import mods.eln.gui.GuiTextFieldEln
import mods.eln.gui.HelperStdContainer
import mods.eln.gui.IGuiObject
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeDescriptor
import mods.eln.node.six.SixNodeElement
import mods.eln.node.six.SixNodeElementInventory
import mods.eln.node.six.SixNodeElementRender
import mods.eln.node.six.SixNodeEntity
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sixnode.electricalbreaker.ElectricalBreakerContainer
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.text.ParseException

/**
 * Switching devices whose contacts are rated by the cable put in their slot, like the electrical breaker: the cable
 * sets the resistance of the contacts and the current they are made for.
 */
abstract class SwitchgearElement(sixNode: SixNode, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElement(sixNode, side, descriptor), mods.eln.item.Lockable {

    override val lockout = mods.eln.item.Lockout()

    /** Locked out, every contact opens; unlocked, they go back to what the device wants. */
    override fun lockoutChanged() {
        cableChanged()
        needPublish()
    }

    override fun readFromNBT(nbt: net.minecraft.nbt.CompoundTag) {
        super.readFromNBT(nbt)
        lockout.readFromNBT(nbt)
    }

    override fun writeToNBT(nbt: net.minecraft.nbt.CompoundTag) {
        super.writeToNBT(nbt)
        lockout.writeToNBT(nbt)
    }

    /** The lockout line for Jade, if locked. */
    protected fun addLockout(info: MutableMap<String, String>) {
        if (lockout.isLocked) info[mods.eln.i18n.I18N.tr("Lockout")] = lockout.describe()
    }

    override val inventory = SixNodeElementInventory(1, 64, this)

    var cable: ElectricalCableDescriptor? = null
        private set

    /** The current the contacts are made for, from the cable; 0 without a cable. */
    val ratedCurrent: Double
        get() = cable?.electricalMaximalCurrent ?: 0.0

    private fun cableInSlot(): ElectricalCableDescriptor? {
        val stack = inventory.getStackInSlot(ElectricalBreakerContainer.cableSlotId)
        return Eln.sixNodeItem.getDescriptor(stack) as? ElectricalCableDescriptor
    }

    /** Gives [loads] the resistance of the cable, or disconnects them without one. */
    protected fun applyCable(vararg loads: NbtElectricalLoad) {
        cable = cableInSlot()
        for (load in loads) {
            val c = cable
            if (c == null) load.highImpedance() else c.applyTo(load)
        }
    }

    /** Closes [contact] with the resistance of the cable, or opens it. */
    protected fun setContact(contact: Resistor, wanted: Boolean) {
        val closed = wanted && !lockout.isLocked
        val c = cable
        if (!closed && c != null && contact.resistance < mods.eln.sim.mna.misc.MnaConst.highImpedance) {
            val a = (contact.aPin as? mods.eln.sim.mna.state.VoltageState)?.voltage ?: 0.0
            val b = (contact.bPin as? mods.eln.sim.mna.state.VoltageState)?.voltage ?: 0.0
            mods.eln.sim.ArcFlash.interrupt(coordinate, Math.max(Math.abs(a), Math.abs(b)), contact.current, ratedCurrent)
        }
        if (c == null || !closed) contact.highImpedance() else c.applyTo(contact)
    }

    /** The cable changed: apply it to the loads and contacts. */
    protected abstract fun cableChanged()

    override fun initialize() {
        cableChanged()
    }

    override fun inventoryChanged() {
        cableChanged()
        reconnect()
    }

    protected val hasCable: Boolean
        get() = inventory.getStackInSlot(ElectricalBreakerContainer.cableSlotId) != null

    override fun hasGui() = true

    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu =
        ElectricalBreakerContainer(player, inventory)

    final override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try {
            Utils.serialiseItemStack(stream, inventory.getStackInSlot(ElectricalBreakerContainer.cableSlotId))
            serializeState(stream)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /** What the render shows and the GUI edits, after the cable. */
    protected abstract fun serializeState(stream: DataOutputStream)

    final override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            command(stream.readByte().toInt(), stream)
            needPublish()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /** A setting from the GUI. */
    protected abstract fun command(id: Int, stream: DataInputStream)

    protected fun clickSound() {
        play(mods.eln.sound.SoundCommand("minecraft:block.lever.click").mulVolume(0.3f, 0.6f).smallRange())
    }
}

abstract class SwitchgearRender(entity: SixNodeEntity, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElementRender(entity, side, descriptor) {

    override val inventory = SixNodeElementInventory(1, 64, this)

    var cableRender: CableRenderDescriptor? = null
        private set

    final override fun publishUnserialize(stream: DataInputStream) {
        super.publishUnserialize(stream)
        try {
            val stack: ItemStack? = Utils.unserialiseItemStack(stream)
            val cable = if (stack == null) null else
                ElectricalCableDescriptor.getDescriptor(stack, ElectricalCableDescriptor::class.java) as? ElectricalCableDescriptor
            cableRender = cable?.render
            unserializeState(stream)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    protected abstract fun unserializeState(stream: DataInputStream)

    protected val signalRender: CableRenderDescriptor
        get() = Eln.instance.signalCableDescriptor.render
}

/**
 * A settings screen with a column of labelled number fields and a row of buttons, above the cable slot and the
 * player's inventory.
 */
open class SettingsGui(player: Player, inventory: IInventory) :
    GuiContainerEln(ElectricalBreakerContainer(player, inventory)) {

    class Field(val label: String, val comment: String, val value: () -> Double, val send: (Double) -> Unit) {
        lateinit var box: GuiTextFieldEln
    }

    class Button(val x: Int, val y: Int, val width: Int, val text: () -> String, val click: () -> Unit) {
        lateinit var button: GuiButtonEln
    }

    protected val fields = ArrayList<Field>()
    protected val buttons = ArrayList<Button>()

    fun addField(field: Field) = fields.add(field)
    fun addButton(button: Button) = buttons.add(button)

    override fun newHelper(): GuiHelperContainer = HelperStdContainer(this)

    override fun initGui() {
        super.initGui()
        fields.forEachIndexed { i, f ->
            f.box = newGuiTextField(FIELD_X, 4 + i * ROW, FIELD_WIDTH)
            f.box.setText(format(f.value()))
            f.box.setComment(f.comment.split("\n").toTypedArray())
        }
        for (b in buttons) b.button = newGuiButton(b.x, b.y, b.width, b.text())
    }

    override fun guiObjectEvent(`object`: IGuiObject) {
        super.guiObjectEvent(`object`)
        for (f in fields) {
            if (`object` === f.box) {
                try {
                    f.send(NumberFormat.getInstance().parse(f.box.text).toDouble())
                } catch (_: ParseException) {
                }
            }
        }
        for (b in buttons) if (`object` === b.button) b.click()
    }

    override fun preDraw(f: Float, x: Int, y: Int) {
        super.preDraw(f, x, y)
        for (b in buttons) b.button.displayString = b.text()
    }

    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y)
        fields.forEachIndexed { i, field -> drawString(8, 6 + i * ROW, field.label) }
    }

    companion object {
        const val ROW = 14
        const val FIELD_X = 100
        const val FIELD_WIDTH = 28

        fun format(value: Double): String = if (value == Math.rint(value) && Math.abs(value) < 1e9)
            value.toLong().toString() else String.format("%.3g", value)
    }
}
