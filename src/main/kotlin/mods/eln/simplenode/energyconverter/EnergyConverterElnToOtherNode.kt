package mods.eln.simplenode.energyconverter

import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.simple.SimpleNode
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.nbt.NbtResistor
import net.minecraft.server.level.ServerPlayer
import net.minecraft.nbt.CompoundTag
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

class EnergyConverterElnToOtherNode : SimpleNode() {
    var load = NbtElectricalLoad("load")
    var powerInResistor = NbtResistor("powerInResistor", load, null)
    var electricalProcess = ElectricalProcess()
    var energyBuffer = 0.0
    var energyBufferMax = 0.0
    var inStdVoltage = 0.0
    var inPowerMax = 0.0
    var selectedOhms = 0.0

    init {
        powerInResistor.resistance = MnaConst.highImpedance
    }

    override fun getSideConnectionMask(side: Direction, lrdu: LRDU): Int {
        return maskElectricalPower
    }

    override fun getThermalLoad(side: Direction, lrdu: LRDU, mask: Int): ThermalLoad? {
        return null
    }

    override fun getElectricalLoad(side: Direction, lrdu: LRDU, mask: Int): ElectricalLoad {
        return load
    }

    override fun initialize() {
        electricalLoadList.add(load)
        electricalComponentList.add(powerInResistor)
        electricalProcessList.add(electricalProcess)
        Eln.applySmallRs(load)
        load.setAsPrivate()
        val descriptor = getDescriptor() as? EnergyConverterElnToOtherDescriptor ?: return
        descriptor.applyTo(this)
        connect()
    }

    inner class ElectricalProcess : IProcess {
        var timeout = 0.0
        override fun process(time: Double) {
            var power = powerInResistor.power
            if (!power.isFinite()) power = 0.0
            energyBuffer += power * time
            timeout -= time
            if (timeout < 0) {
                timeout = 0.05
                if (energyBufferMax - energyBuffer <= 0) {
                    powerInResistor.highImpedance()
                } else {
                    if (selectedOhms < 10.0) {
                        powerInResistor.resistance = MnaConst.highImpedance
                    } else {
                        powerInResistor.resistance = max(Eln.getSmallRs(), selectedOhms)
                    }
                }
            }
        }
    }

    /**
     * @param conversionRatio Conversion Ratio (Input mod conversion ratio here)
     * @return energy buffer in that mod unit (Units are RF/EU/OC, etc.)
     */
    fun availableEnergyInModUnits(conversionRatio: Double): Double {
        return energyBuffer * conversionRatio
    }

    /**
     * @param otherModEnergy Energy from other mod (RF/EU/OC, etc.) to draw
     * @param conversionRatio Conversion Ratio (Input mod conversion ratio here)
     * @return Watts drawn from the energy buffer
     */
    fun drawEnergy(otherModEnergy: Double, conversionRatio: Double): Double {
        val drawEnergy = otherModEnergy / conversionRatio
        energyBuffer -= drawEnergy
        return drawEnergy
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putDouble("energyBuffer", energyBuffer)
        nbt.putDouble("selectedOhms", selectedOhms)
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        energyBuffer = nbt.getDouble("energyBuffer")
        selectedOhms = if (nbt.contains("selectedOhms")) {
            nbt.getDouble("selectedOhms")
        } else {
            MnaConst.highImpedance
        }
    }

    override fun hasGui(side: Direction): Boolean {
        return true
    }

    override fun publishSerialize(stream: DataOutputStream) {
        super.publishSerialize(stream)
        try {
            stream.writeDouble(selectedOhms)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun networkUnserialize(stream: DataInputStream, player: ServerPlayer?) {
        try {
            when (stream.readByte()) {
                NetworkType.SET_OHMS.id -> {
                    val ohms = stream.readDouble()
                    if (ohms in 10.0 .. MnaConst.highImpedance) {
                        selectedOhms = ohms
                    }
                    needPublish()
                }
                else -> {}
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override val nodeUuid = "ElnToOther"

    companion object {
        @JvmStatic
        fun getNodeUuidStatic() = "ElnToOther"
    }
}

enum class NetworkType(val id: Byte) {
    SET_OHMS(1)
}
