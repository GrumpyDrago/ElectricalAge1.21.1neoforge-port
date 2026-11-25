package mods.eln.transparentnode.floodlight

import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.i18n.I18N
import mods.eln.item.ConfigCopyToolDescriptor
import mods.eln.item.IConfigurable
import mods.eln.item.LampDescriptor
import mods.eln.misc.*
import mods.eln.misc.Utils.getItemObject
import mods.eln.misc.Utils.plotAmpere
import mods.eln.misc.Utils.plotPower
import mods.eln.misc.Utils.plotValue
import mods.eln.misc.Utils.plotVolt
import mods.eln.node.AutoAcceptInventoryProxy
import mods.eln.node.NodeBase
import mods.eln.node.published
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.node.transparent.TransparentNodeElementInventory
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.MonsterPopFreeProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalGateInput
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.process.destruct.VoltageStateWatchDog
import mods.eln.sim.process.destruct.WorldExplosion
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class FloodlightElement(transparentNode: TransparentNode, transparentNodeDescriptor: TransparentNodeDescriptor) : TransparentNodeElement(transparentNode, transparentNodeDescriptor), IConfigurable {

    companion object {
        const val HORIZONTAL_ADJUST_EVENT: Byte = 0
        const val VERTICAL_ADJUST_EVENT: Byte = 1
        const val BEAM_ADJUST_EVENT: Byte = 2
    }

    override val inventory = TransparentNodeElementInventory(2, 64, this)

    private val acceptingInventory = AutoAcceptInventoryProxy(inventory)
        .acceptIfEmpty(FloodlightContainer.LAMP_SLOT_1_ID, LampDescriptor::class.java)
        .acceptIfEmpty(FloodlightContainer.LAMP_SLOT_2_ID, LampDescriptor::class.java)

    override val descriptor = transparentNodeDescriptor as FloodlightDescriptor

    private var initialPlacement = true

    val motorized = descriptor.motorized

    var rotationAxis: HybridNodeDirection = (descriptor.placementSide).toHybridNodeDirection()
    lateinit var blockFacing: HybridNodeDirection

    var powered = false

    var swivelAngle by published(0.0)
    var headAngle by published(0.0)
    var beamWidth by published(0.0)

    var lightRange = 0

    val electricalLoad = NbtElectricalLoad("electricalLoad")
    private val lamp1Resistor: Resistor = Resistor(electricalLoad, null)
    private val lamp2Resistor: Resistor = Resistor(electricalLoad, null)

    val swivelControl = NbtElectricalGateInput("swivelControl")
    val headControl = NbtElectricalGateInput("headControl")
    val beamControl = NbtElectricalGateInput("beamControl")

    private val voltageWatchdog = VoltageStateWatchDog(electricalLoad)

    private val watchdogProcess = voltageWatchdog.setDestroys(WorldExplosion(this).machineExplosion())
    private val floodlightProcess = FloodlightProcess(this)
    private val monsterPopProcess = MonsterPopFreeProcess(transparentNode.coordinate, Eln.instance.killMonstersAroundLampsRange)

    init {
        if (motorized) {
            electricalLoadList.add(swivelControl)
            electricalLoadList.add(headControl)
            electricalLoadList.add(beamControl)
        }

        // NOTE: Power factor (0.005) comes from MV cable registration
        electricalLoad.serialResistance = ((Eln.MVU * Eln.MVU) / Eln.instance.MVP()) * 0.005
        voltageWatchdog.setNominalVoltage(Eln.MVU)

        slowProcessList.add(watchdogProcess)
        slowProcessList.add(floodlightProcess)
        slowProcessList.add(monsterPopProcess)
    }

    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu {
        return FloodlightContainer(player, inventory)
    }

    override fun hasGui(): Boolean {
        return true
    }

    override fun onBlockActivated(player: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        if (Utils.isPlayerUsingWrench(player)) {
            blockFacing = blockFacing.left(rotationAxis)
            reconnect()
            needPublish()
            return true
        }

        val currentEquippedItem = getItemObject(player.currentEquippedItem)

        if (currentEquippedItem is LampDescriptor) {
            if (currentEquippedItem.socket == FloodlightContainer.LAMP_SOCKET_TYPE) {
                return acceptingInventory.take(player.currentEquippedItem, this, true, true)
            }
        }

        return false
    }

    override fun getLightOpacity(): Float {
        return 1f
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        initialPlacement = nbt.getBoolean("initialPlacement")
        rotationAxis = HybridNodeDirection.fromInt(nbt.getInt("rotationAxis"))!!
        blockFacing = HybridNodeDirection.fromInt(nbt.getInt("blockFacing"))!!
        powered = nbt.getBoolean("powered")
        swivelAngle = nbt.getDouble("swivelAngle")
        headAngle = nbt.getDouble("headAngle")
        beamWidth = nbt.getDouble("beamWidth")
        lightRange = nbt.getInt("lightRange")
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putBoolean("initialPlacement", initialPlacement)
        nbt.putInt("rotationAxis", rotationAxis.int)
        nbt.putInt("blockFacing", blockFacing.int)
        nbt.putBoolean("powered", powered)
        nbt.putDouble("swivelAngle", swivelAngle)
        nbt.putDouble("headAngle", headAngle)
        nbt.putDouble("beamWidth", beamWidth)
        nbt.putInt("lightRange", lightRange)
    }

    override fun initialize() {
        if (initialPlacement) {
            blockFacing = front.toHybridNodeDirection()
            front = rotationAxis.toStandardDirection()
            initialPlacement = false
        }
        electricalLoadList.add(electricalLoad)
        electricalComponentList.add(lamp1Resistor)
        electricalComponentList.add(lamp2Resistor)
        computeInventory()
        connect()
    }

    override fun connectJob() {
        electricalLoadList.add(electricalLoad)
        electricalComponentList.add(lamp1Resistor)
        electricalComponentList.add(lamp2Resistor)
        super.connectJob()
    }

    override fun disconnectJob() {
        super.disconnectJob()
        electricalLoadList.remove(electricalLoad)
        electricalComponentList.remove(lamp1Resistor)
        electricalComponentList.remove(lamp2Resistor)
    }

    override fun inventoryChange(inventory: IInventory?) {
        disconnect()
        computeInventory()
        connect()
        needPublish()
    }

    private fun computeInventory() {
        val lamp1Stack = inventory.getStackInSlot(FloodlightContainer.LAMP_SLOT_1_ID)
        val lamp2Stack = inventory.getStackInSlot(FloodlightContainer.LAMP_SLOT_2_ID)

        when (lamp1Stack) {
            null -> lamp1Resistor.highImpedance()
            else -> lamp1Resistor.resistance = (getItemObject(lamp1Stack) as LampDescriptor).r
        }

        when (lamp2Stack) {
            null -> lamp2Resistor.highImpedance()
            else -> lamp2Resistor.resistance = (getItemObject(lamp2Stack) as LampDescriptor).r
        }
    }

    override fun getElectricalLoad(side: Direction, lrdu: LRDU): ElectricalLoad? {
        if (lrdu.toHybridNodeLRDU().normalizeLRDU(rotationAxis, side) != HybridNodeLRDU.Down) return null

        return if (motorized) when (side.toHybridNodeDirection()) {
            blockFacing.back() -> electricalLoad
            blockFacing.right(rotationAxis) -> headControl
            blockFacing.left(rotationAxis) -> swivelControl
            blockFacing.front() -> beamControl
            else -> null
        }
        else when (side.toHybridNodeDirection()) {
            blockFacing.back() -> electricalLoad
            else -> null
        }
    }

    override fun getThermalLoad(side: Direction, lrdu: LRDU): ThermalLoad? {
        return null
    }

    override fun getConnectionMask(side: Direction, lrdu: LRDU): Int {
        if (lrdu.toHybridNodeLRDU().normalizeLRDU(rotationAxis, side) != HybridNodeLRDU.Down) return 0

        return if (motorized) when (side.toHybridNodeDirection()) {
            blockFacing.back() -> NodeBase.maskElectricalPower
            blockFacing.right(rotationAxis) -> NodeBase.maskElectricalGate
            blockFacing.left(rotationAxis) -> NodeBase.maskElectricalGate
            blockFacing.front() -> NodeBase.maskElectricalGate
            else -> 0
        }
        else when (side.toHybridNodeDirection()) {
            blockFacing.back() -> NodeBase.maskElectricalPower
            else -> 0
        }
    }

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try {
            stream.writeInt(rotationAxis.int)
            stream.writeInt(blockFacing.int)
            stream.writeBoolean(motorized)
            stream.writeBoolean(powered)
            stream.writeDouble(swivelAngle)
            stream.writeDouble(headAngle)
            stream.writeDouble(beamWidth)
            Utils.serialiseItemStack(stream, inventory.getStackInSlot(FloodlightContainer.LAMP_SLOT_1_ID))
            Utils.serialiseItemStack(stream, inventory.getStackInSlot(FloodlightContainer.LAMP_SLOT_2_ID))
        }
        catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun networkUnserialize(stream: DataInputStream): Byte {
        when (super.networkUnserialize(stream)) {
            HORIZONTAL_ADJUST_EVENT -> swivelAngle = stream.readDouble()
            VERTICAL_ADJUST_EVENT -> headAngle = stream.readDouble()
            BEAM_ADJUST_EVENT -> beamWidth = stream.readDouble()
        }
        return unserializeNulldId
    }

    override fun multiMeterString(side: Direction): String {
        return plotVolt("U:", electricalLoad.voltage) + plotAmpere("I:", electricalLoad.current)
    }

    override fun thermoMeterString(side: Direction): String {
        return ""
    }

    override fun getWaila(): Map<String, String> {
        val info: MutableMap<String, String> = LinkedHashMap()

        info[I18N.tr("Power Consumption")] = plotPower("", electricalLoad.voltage * electricalLoad.current)

        val lamp1Stack = inventory.getStackInSlot(FloodlightContainer.LAMP_SLOT_1_ID)
        val lamp2Stack = inventory.getStackInSlot(FloodlightContainer.LAMP_SLOT_2_ID)

        if (lamp1Stack != null) info[I18N.tr("Bulb 1")] = lamp1Stack.plainName
        else info[I18N.tr("Bulb 1")] = I18N.tr("None")

        if (lamp2Stack != null) info[I18N.tr("Bulb 2")] = lamp2Stack.plainName
        else info[I18N.tr("Bulb 2")] = I18N.tr("None")

        if (Eln.wailaEasyMode) {
            info[I18N.tr("Voltage")] = plotVolt("", electricalLoad.voltage)

            if (lamp1Stack != null) {
                val lamp1Descriptor = getItemObject(lamp1Stack) as LampDescriptor
                info[I18N.tr("Bulb 1 Life Left")] = plotValue(lamp1Descriptor.getLifeInTag(lamp1Stack)) + I18N.tr(" Hours")
            }
            if (lamp2Stack != null) {
                val lamp2Descriptor = getItemObject(lamp2Stack) as LampDescriptor
                info[I18N.tr("Bulb 2 Life Left")] = plotValue(lamp2Descriptor.getLifeInTag(lamp2Stack)) + I18N.tr(" Hours")
            }
        }

        return info
    }

    override fun readConfigTool(compound: CompoundTag, invoker: Player) {
        if (ConfigCopyToolDescriptor.readGenDescriptor(compound, "lamp1", inventory, FloodlightContainer.LAMP_SLOT_1_ID, invoker)) inventoryChange(inventory)
        if (ConfigCopyToolDescriptor.readGenDescriptor(compound, "lamp2", inventory, FloodlightContainer.LAMP_SLOT_2_ID, invoker)) inventoryChange(inventory)
    }

    override fun writeConfigTool(compound: CompoundTag, invoker: Player) {
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "lamp1", inventory.getStackInSlot(FloodlightContainer.LAMP_SLOT_1_ID))
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "lamp2", inventory.getStackInSlot(FloodlightContainer.LAMP_SLOT_2_ID))
    }

}