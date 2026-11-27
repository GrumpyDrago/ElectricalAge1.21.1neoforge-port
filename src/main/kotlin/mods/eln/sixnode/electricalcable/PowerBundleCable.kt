package mods.eln.sixnode.electricalcable

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.NodeConnection
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeDescriptor
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.node.six.SixNodeElement
import mods.eln.sixnode.breakerbox.BreakerBoxElement
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.process.destruct.VoltageStateWatchDog
import mods.eln.sim.process.destruct.WorldExplosion
import mods.eln.sim.process.heater.ElectricalLoadHeatThermalLoad
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.math.abs

/** A part with the 8 lines of a power bundle on one side: bundle cables and the breaker box. */
interface PowerBundle {
    val bundleLoads: Array<NbtElectricalLoad>

    companion object {
        const val LINES = 8

        /**
         * The brush color of each line: black (unpainted cables), red, orange, yellow, green, blue, purple and white.
         * Cables of other colors do not join a bundle.
         */
        val LINE_DYES = intArrayOf(0, 1, 14, 11, 2, 4, 5, 15)

        /** The name of [line]. */
        fun colorName(line: Int): String = when (LINE_DYES.getOrElse(line) { -1 }) {
            0 -> tr("Black")
            1 -> tr("Red")
            14 -> tr("Orange")
            11 -> tr("Yellow")
            2 -> tr("Green")
            4 -> tr("Blue")
            5 -> tr("Purple")
            else -> tr("White")
        }

        /** The RGB of each line, as cables of its brush color are drawn. */
        val colorRgb = intArrayOf(0x333333, 0xFF0D0D, 0xFF994D, 0xE6CC1A, 0x33801A, 0x3333FF, 0xB30DFF, 0xFFFFFF)

        /**
         * The connection itself joins line 0, through [getElectricalLoad]; when two bundles meet, this joins the
         * other 7. Called on both sides, it acts on one of them.
         */
        fun connectOtherLines(self: PowerBundle, connection: NodeConnection, isA: Boolean) {
            if (!isA) return
            val other = connection.N2 as? SixNode ?: return
            val element = other.getElement(connection.dir2.applyLRDU(connection.lrdu2)) as? PowerBundle ?: return
            for (i in 1 until LINES) {
                val c = ElectricalConnection(self.bundleLoads[i], element.bundleLoads[i])
                Eln.simulator.addElectricalComponent(c)
                connection.addConnection(c)
            }
        }

        /**
         * Whether a breaker box is on the bundle network of [start]: follows the bundle cables it connects to, in
         * neighbouring blocks and on the other faces of its own block, up to [SEARCH_LIMIT] of them.
         */
        fun reachesBreakerBox(start: SixNodeElement): Boolean {
            val seen = HashSet<SixNodeElement>()
            val queue = ArrayDeque<SixNodeElement>()
            queue.add(start)
            seen.add(start)
            while (queue.isNotEmpty() && seen.size <= SEARCH_LIMIT) {
                val element = queue.removeFirst()
                val node = element.sixNode ?: continue
                for (c in node.nodeConnectionList) {
                    for (other in connected(c, element)) {
                        if (other is BreakerBoxElement) return true
                        if (other is PowerBundleCableElement && seen.add(other)) queue.add(other)
                    }
                }
            }
            return false
        }

        /** The element at the other end of [c] from [element], if [element] is one of its ends. */
        private fun connected(c: NodeConnection, element: SixNodeElement): List<SixNodeElement> {
            val internal = c.N1 === c.N2
            // Within a block the connection names the faces of the two elements; between blocks, the face of the
            // block and the side of the element on it.
            val a = (c.N1 as? SixNode)?.getElement(if (internal) c.dir1 else c.dir1.applyLRDU(c.lrdu1))
            val b = (c.N2 as? SixNode)?.getElement(if (internal) c.dir2 else c.dir2.applyLRDU(c.lrdu2))
            return when {
                a === element && b != null -> listOf(b)
                b === element && a != null -> listOf(a)
                else -> emptyList()
            }
        }

        const val SEARCH_LIMIT = 4096

        /** The line a part with connection [mask] joins: that of its color (-1 for none), or line 0 for another bundle. */
        fun lineFor(mask: Int): Int = if (mask and NodeBase.maskElectricalPower == 0) 0
        else LINE_DYES.indexOf((mask shr NodeBase.maskColorShift) and 0xF)
    }
}

class PowerBundleCableDescriptor(name: String, render: CableRenderDescriptor) :
    ElectricalCableDescriptor(name, render, "", false) {
    init {
        ElementClass = PowerBundleCableElement::class.java
    }

    @Suppress("UNCHECKED_CAST")
    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, rawList: MutableList<Any?>?, par4: Boolean) {
        val list = rawList as MutableList<String>
        list.addAll(tr("Eight insulated circuits in one cable. A power\ncable painted red, orange, yellow, green, blue,\npurple or white joins the line of its color;\nunpainted cables join the black line.").split("\n"))
        list.addAll(tr("Does not connect to machines: run a cable\nof the right color out of it.").split("\n"))
        list.add(tr("Each line: %1$ V, %2$ A.", Utils.plotValue(electricalNominalVoltage), Utils.plotValue(electricalMaximalCurrent / 1.2)))
        list.add(tr("The lines share their heat: a full bundle carries less per line."))
    }

    override fun getNodeMask(): Int = NodeBase.maskPowerBundle
}

/**
 * Eight separate power lines in one cable. It joins other bundles line for line, the breaker box, and power cables
 * on the line of their brush color, but no machine: [NodeBase.maskPowerBundle] is only shared with power cables.
 */
class PowerBundleCableElement(sixNode: SixNode?, side: Direction?, descriptor: SixNodeDescriptor?) :
    ElectricalCableElement(sixNode, side, descriptor), PowerBundle {

    override val bundleLoads = Array(PowerBundle.LINES) { NbtElectricalLoad("line$it") }

    init {
        // The lines replace the single load of a cable, with its heater and watchdog.
        electricalLoadList.remove(electricalLoad)
        thermalSlowProcessList.remove(heater)
        slowProcessList.remove(voltageWatchdog)
        colorCare = 0
        color = 15
        for (line in bundleLoads) {
            line.setCanBeSimplifiedByLine(true)
            electricalLoadList.add(line)
            val heater = ElectricalLoadHeatThermalLoad(line, thermalLoad)
                .limitTemperatureRate(this.descriptor.thermalSelfHeatingRateLimit)
            thermalSlowProcessList.add(IProcess { dt -> if (!fedByBreakerBox) heater.process(dt) })
            slowProcessList.add(VoltageStateWatchDog(line).setNominalVoltage(this.descriptor.electricalNominalVoltage)
                .setDestroys(WorldExplosion(this).cableExplosion()))
        }
        slowProcessList.add(IProcess { dt ->
            sinceCheck += dt
            if (sinceCheck >= CHECK_PERIOD) {
                sinceCheck = 0.0
                val fed = PowerBundle.reachesBreakerBox(this)
                if (fed != fedByBreakerBox) {
                    fedByBreakerBox = fed
                    needPublish()
                }
            }
        })
    }

    /**
     * Whether a breaker box feeds this bundle. Its breakers keep every line within what it is made for, so the bundle
     * does not heat up; used as a plain multi-core cable, it does.
     */
    var fedByBreakerBox = false
        private set
    // Spread the checks of many bundles over time.
    private var sinceCheck = Math.random() * CHECK_PERIOD

    override fun initialize() {
        for (line in bundleLoads) descriptor.applyTo(line)
        descriptor.applyTo(thermalLoad)
        // All the lines heat the one jacket, which gets rid of heat like a few cables, not sixteen.
        thermalLoad.Rp /= HEAT_SHARING
        thermalLoad.heatCapacity *= HEAT_SHARING
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int): ElectricalLoad? = bundleLoads.getOrNull(PowerBundle.lineFor(mask))

    override fun getConnectionMask(lrdu: LRDU): Int = NodeBase.maskPowerBundle

    override fun newConnectionAt(connection: NodeConnection?, isA: Boolean) {
        PowerBundle.connectOtherLines(this, connection ?: return, isA)
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        colorCare = 0
        color = 15
    }

    /** No painting: the color of the cables that join it picks the line. */
    override fun onBlockActivated(entityPlayer: Player, side: Direction, vx: Float, vy: Float, vz: Float) = false

    private fun busyLines() = bundleLoads.indices.filter { abs(bundleLoads[it].current) > 0.01 || abs(bundleLoads[it].voltage) > 0.5 }

    override fun multiMeterString(): String {
        val lines = busyLines()
        if (lines.isEmpty()) return tr("No line in use")
        return lines.joinToString(" ") { PowerBundle.colorName(it) + ": " + Utils.plotUIP(bundleLoads[it].voltage, bundleLoads[it].current).trim() }
    }

    override fun getWaila(): Map<String, String> {
        val info = LinkedHashMap<String, String>()
        for (i in busyLines()) {
            info[PowerBundle.colorName(i)] = Utils.plotVolt("", bundleLoads[i].voltage) + Utils.plotAmpere(" ", bundleLoads[i].current)
        }
        if (info.isEmpty()) info[tr("Lines")] = tr("No line in use")
        info[tr("Temperature")] = plotAmbientCelsius("", thermalLoad.temperature)
        if (fedByBreakerBox) info[tr("Protection")] = tr("Fed by a breaker box: does not overheat")
        return info
    }

    companion object {
        /** How many lines at full current the jacket can carry the heat of. */
        const val HEAT_SHARING = 6.0
        /** Seconds between looks for a breaker box. */
        const val CHECK_PERIOD = 2.0
    }
}
