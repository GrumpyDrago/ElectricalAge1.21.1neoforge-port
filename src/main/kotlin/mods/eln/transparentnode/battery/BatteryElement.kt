package mods.eln.transparentnode.battery

import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sim.nbt.NbtBatteryProcess
import mods.eln.sim.nbt.NbtBatterySlowProcess
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.nbt.NbtThermalLoad
import mods.eln.sim.process.destruct.ThermalLoadWatchDog
import mods.eln.sim.IProcess
import mods.eln.sim.process.destruct.IDestructible
import mods.eln.sound.SoundCommand
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import mods.eln.sim.process.heater.ElectricalLoadHeatThermalLoad
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import java.io.DataOutputStream
import java.io.IOException
import java.util.*

class BatteryElement(transparentNode: TransparentNode, descriptor: TransparentNodeDescriptor) : TransparentNodeElement(transparentNode, descriptor),
    mods.eln.node.ComputerControllable {
    override fun computerValues(): Map<String, Any?> {
        val ambient = try { getAmbientTemperatureCelsius() } catch (_: IllegalStateException) { 20.0 }
        return linkedMapOf(
            "charge" to batteryProcess.charge,
            "life" to batteryProcess.life,
            "voltage" to batteryProcess.u,
            "current" to batteryProcess.dischargeCurrent,
            "power" to batteryProcess.u * batteryProcess.dischargeCurrent,
            "energy" to batteryProcess.energy,
            "temperature" to thermalLoad.temperatureCelsius + ambient,
            "temperatureLimit" to descriptor.thermalWarmLimit,
            "vented" to vented,
            "chemistry" to descriptor.chemistry?.name?.lowercase()
        )
    }

    override var descriptor: BatteryDescriptor = descriptor as BatteryDescriptor
    var positiveLoad = NbtElectricalLoad("positiveLoad")
    var negativeLoad = NbtElectricalLoad("negativeLoad")
    var voltageSource = VoltageSource("volSrc", positiveLoad, negativeLoad)
    var thermalLoad = NbtThermalLoad("thermalLoad")
    var negativeETProcess = ElectricalLoadHeatThermalLoad(negativeLoad, thermalLoad)
    var thermalWatchdog = ambientAwareThermalWatchdog(ThermalLoadWatchDog(thermalLoad))
    var batteryProcess = NbtBatteryProcess(positiveLoad, negativeLoad, this.descriptor.UfCharge, 0.0, voltageSource, thermalLoad)
    var dischargeResistor = Resistor(positiveLoad, negativeLoad)
    var batterySlowProcess = NbtBatterySlowProcess(node!!, batteryProcess, thermalLoad).also { it.onDestroy = { vent() } }
    val chemistryProcess = this.descriptor.chemistry?.let { BatteryChemistryProcess(this, it) }
    var fromItemStack = false
    var fromItemstackCharge = 0.0
    var fromItemstackLife = 0.0
    override fun getElectricalLoad(side: Direction, lrdu: LRDU): ElectricalLoad? {
        if (lrdu != LRDU.Down) return null
        if (side == front.left()) return positiveLoad
        return if (side == front.right()) negativeLoad else null
    }

    override fun getThermalLoad(side: Direction, lrdu: LRDU): ThermalLoad? {
        if (lrdu != LRDU.Down) return null
        if (side == front.left()) return thermalLoad
        return if (side == front.right()) thermalLoad else null
    }

    override fun getConnectionMask(side: Direction, lrdu: LRDU): Int {
        if (lrdu != LRDU.Down) return 0
        if (side == front.left()) return NodeBase.maskElectricalPower
        return if (side == front.right()) NodeBase.maskElectricalPower else 0
    }

    override fun multiMeterString(side: Direction): String {
        var str = ""
        str += Utils.plotVolt("Ubat:", batteryProcess.u)
        str += Utils.plotAmpere("I:", batteryProcess.dischargeCurrent)
        str += Utils.plotPercent("Charge:", batteryProcess.charge)
        // batteryProcess.life is a percentage from 1.0 to 0.0.
        str += Utils.plotPercent("Life:", batteryProcess.life)
        return str
    }

    override fun thermoMeterString(side: Direction): String {
        return plotAmbientCelsius("Tbat:", thermalLoad.temperatureCelsius)
    }

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try {
            stream.writeFloat((batteryProcess.u * batteryProcess.dischargeCurrent).toFloat())
            stream.writeFloat(batteryProcess.energy.toFloat())
            stream.writeShort((batteryProcess.life * 1000).toInt())
            node!!.lrduCubeMask.getTranslate(Direction.YN).serialize(stream)
            stream.writeBoolean(vented)
            stream.writeByte((heatLevel() * 255).toInt().coerceIn(0, 255))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun initialize() {
        descriptor.applyTo(batteryProcess)
        descriptor.applyTo(thermalLoad)
        descriptor.applyTo(dischargeResistor)
        descriptor.applyTo(batterySlowProcess)
        applyConnection()
        dischargeResistor.resistance = MnaConst.highImpedance
        if (fromItemStack) {
            batteryProcess.life = fromItemstackLife
            batteryProcess.charge = fromItemstackCharge
            fromItemStack = false
        }
        connect()
    }

    override fun reconnect() {
        disconnect()
        connect()
    }

    override fun onBlockActivated(player: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        if (player.mainHandItem.item != Items.WATER_BUCKET) return false
        coolWithWater()
        if (!player.abilities.instabuild) player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.BUCKET))
        return true
    }

    // ---------------------------------------------------------------- Venting

    /**
     * Overheated, overcharged or reversed, a battery vents instead of exploding: its safety vent opens and it cuts
     * itself off, glowing red and smoking, and loses some of its life. It stays off until it is cooled with a bucket
     * of water, or broken and replaced. (Explosions of big battery banks could lag a world, and are no fun to lose a
     * base to.)
     */
    var vented = false
        private set
    private var smokeTimer = 0.0

    fun vent() {
        if (vented) return
        vented = true
        batteryProcess.changeLife(Math.max(0.1, batteryProcess.life * (1 - VENT_LIFE_LOSS)))
        // Venting lets out the gas of an overcharge.
        if (batteryProcess.charge > 1.0) batteryProcess.charge = 1.0
        applyConnection()
        play(SoundCommand("minecraft:block.fire.extinguish").mulVolume(0.8f, 0.6f).smallRange())
        needPublish()
    }

    /** Pours a bucket of water over it: it is back at the ambient temperature and, if it had vented, back on. */
    fun coolWithWater() {
        thermalLoad.temperatureCelsius = 0.0
        val wasVented = vented
        vented = false
        applyConnection()
        play(SoundCommand("minecraft:block.fire.extinguish").mulVolume(1f, 1.2f).smallRange())
        val c = node!!.coordinate
        (c.world() as? ServerLevel)?.sendParticles(ParticleTypes.CLOUD, c.x + 0.5, c.y + 0.9, c.z + 0.5,
            if (wasVented) 20 else 8, 0.3, 0.2, 0.3, 0.02)
        needPublish()
    }

    /** The serial resistance of the cells, with the effect of the cold; cut off while vented. */
    fun applyConnection(coldFactor: Double = chemistryProcess?.appliedFactor ?: 1.0) {
        val r = if (vented) MnaConst.highImpedance else descriptor.electricalRs * coldFactor
        positiveLoad.serialResistance = r
        negativeLoad.serialResistance = r
    }

    /** How close the cells are to their limit, from 0 (20 degrees below it) to 1 (at it). */
    fun heatLevel(): Double {
        val ambient = try { getAmbientTemperatureCelsius() } catch (_: IllegalStateException) { 20.0 }
        val celsius = thermalLoad.temperatureCelsius + ambient
        return ((celsius - (descriptor.thermalWarmLimit - 20)) / 20).coerceIn(0.0, 1.0)
    }

    private fun smoke(dt: Double) {
        smokeTimer += dt
        if (smokeTimer < 0.5) return
        smokeTimer = 0.0
        val heat = heatLevel()
        if (!vented && heat < 0.6) return
        val c = node!!.coordinate
        val level = c.world() as? ServerLevel ?: return
        if (vented) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x + 0.5, c.y + 0.9, c.z + 0.5, 3, 0.25, 0.1, 0.25, 0.01)
            if (Math.random() < 0.15) level.sendParticles(ParticleTypes.LAVA, c.x + 0.5, c.y + 0.9, c.z + 0.5, 1, 0.2, 0.0, 0.2, 0.0)
        } else {
            level.sendParticles(ParticleTypes.SMOKE, c.x + 0.5, c.y + 0.9, c.z + 0.5, 1, 0.25, 0.05, 0.25, 0.005)
        }
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        vented = nbt.getBoolean("vented")
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putBoolean("vented", vented)
    }

    override fun hasGui(): Boolean {
        return true
    }

    override fun readItemStackNBT(nbt: CompoundTag?) {
        super.readItemStackNBT(nbt)
        fromItemstackCharge = nbt?.getDouble("charge")?: descriptor.getChargeInTag(this.descriptor.newItemStack())
        fromItemstackLife = nbt?.getDouble("life")?: descriptor.getLifeInTag(this.descriptor.newItemStack())
        fromItemStack = true
    }

    override fun getItemStackNBT(): CompoundTag {
        val nbt = CompoundTag()
        nbt.putDouble("charge", batteryProcess.charge)
        nbt.putDouble("life", batteryProcess.life)
        return nbt
    }

    override fun getWaila(): Map<String, String> {
        val info: MutableMap<String, String> = HashMap()
        info[tr("Charge")] = Utils.plotPercent("", batteryProcess.charge)
        info[tr("Energy")] = Utils.plotEnergy("", batteryProcess.energy)
        info[tr("Life")] = Utils.plotPercent("", batteryProcess.life)
        if (Eln.wailaEasyMode) {
            info[tr("Voltage")] = Utils.plotVolt("", batteryProcess.u)
            info[tr("Current")] = Utils.plotAmpere("", batteryProcess.dischargeCurrent)
            info[tr("Temperature")] = plotAmbientCelsius("", thermalLoad.temperatureCelsius)
        }
        if (vented) info[tr("Vented")] = tr("Overheated and cut off: cool it with a water bucket")
        this.descriptor.chemistry?.let { info[tr("Chemistry")] = it.displayName() }
        chemistryProcess?.let { info[tr("Cell temperature")] = Utils.plotCelsius("", it.celsius) }
        info[tr("Subsystem Matrix Size")] = Utils.renderSubSystemWaila(positiveLoad.subSystem)
        return info
    }

    init {
        electricalLoadList.add(positiveLoad)
        electricalLoadList.add(negativeLoad)
        electricalComponentList.add(Resistor(positiveLoad, null))
        electricalComponentList.add(Resistor(negativeLoad, null))
        electricalComponentList.add(dischargeResistor)
        electricalComponentList.add(voltageSource)
        thermalLoadList.add(thermalLoad)
        electricalProcessList.add(batteryProcess)
        thermalFastProcessList.add(negativeETProcess)
        slowProcessList.add(batterySlowProcess)
        slowProcessList.add(NodePeriodicPublishProcess(transparentNode, 1.0, 0.0))
        batteryProcess.IMax = this.descriptor.IMax
        slowProcessList.add(thermalWatchdog)
        chemistryProcess?.let { slowProcessList.add(it) }
        slowProcessList.add(IProcess { dt -> smoke(dt) })
        thermalWatchdog
            .setMaximumTemperature(this.descriptor.thermalWarmLimit)
            .setDestroys(object : IDestructible {
                override fun destructImpl() = vent()
                override fun describe() = "battery venting at ${node?.coordinate}"
            })
    }

    companion object {
        /** Life lost each time it vents. */
        const val VENT_LIFE_LOSS = 0.1
    }
}
