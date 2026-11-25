package mods.eln.simplenode.energyconverter

import mods.eln.Other
import mods.eln.compat.*
import mods.eln.misc.Direction
import mods.eln.node.simple.SimpleNodeEntity
import mods.eln.registration.SingleNodeRegistration
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.energy.IEnergyStorage
import java.io.DataInputStream
import java.io.IOException

/** Turns electrical power into NeoForge energy (FE). */
class EnergyConverterElnToOtherEntity(pos: BlockPos, state: BlockState) :
    SimpleNodeEntity(SingleNodeRegistration.energyConverter, pos, state, EnergyConverterElnToOtherNode.getNodeUuidStatic()) {
    @JvmField
    var selectedResistance = 0.0

    @JvmField
    var hasChanges = false

    override fun newGuiDraw(side: Direction, player: Player): Screen = EnergyConverterElnToOtherGui(this)

    override fun serverPublishUnserialize(stream: DataInputStream) {
        super.serverPublishUnserialize(stream)
        try {
            selectedResistance = stream.readDouble()
            hasChanges = true
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private val converter: EnergyConverterElnToOtherNode?
        get() = if (level?.isClientSide != false) null else node as? EnergyConverterElnToOtherNode

    /** Other mods may also draw the energy themselves. */
    val energyStorage: IEnergyStorage = object : IEnergyStorage {
        override fun receiveEnergy(toReceive: Int, simulate: Boolean): Int = 0

        override fun extractEnergy(toExtract: Int, simulate: Boolean): Int {
            val node = converter ?: return 0
            val extract = minOf(toExtract, energyStored).coerceAtLeast(0)
            if (!simulate) node.drawEnergy(extract.toDouble(), Other.wattsToRf)
            return extract
        }

        override fun getEnergyStored(): Int = converter?.availableEnergyInModUnits(Other.wattsToRf)?.toInt()?.coerceAtLeast(0) ?: 0
        override fun getMaxEnergyStored(): Int = converter?.let { (it.energyBufferMax * Other.wattsToRf).toInt() } ?: 0
        override fun canExtract(): Boolean = true
        override fun canReceive(): Boolean = false
    }

    /** Offers the buffered energy to the neighbours in equal shares. */
    fun pushEnergy() {
        val node = converter ?: return
        val level = level ?: return
        val sinks = net.minecraft.core.Direction.entries.mapNotNull {
            level.getCapability(Capabilities.EnergyStorage.BLOCK, blockPos.relative(it), it.opposite)
        }.filter { it.canReceive() }
        if (sinks.isEmpty()) return
        val share = (node.availableEnergyInModUnits(Other.wattsToRf) / sinks.size).toInt()
        if (share <= 0) return
        var used = 0
        for (sink in sinks) used += sink.receiveEnergy(share, false)
        if (used > 0) node.drawEnergy(used.toDouble(), Other.wattsToRf)
    }
}
