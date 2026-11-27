package mods.eln.gridnode

import mods.eln.i18n.I18N.tr

import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.misc.*
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.ElectricalLoad
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import org.apache.commons.lang3.tuple.Pair

import java.io.DataOutputStream
import java.io.IOException
import java.util.HashMap
import java.util.HashSet
import java.util.UUID

abstract class GridElement(transparentNode: TransparentNode, descriptor: TransparentNodeDescriptor, internal var connectRange: Int) : TransparentNodeElement(transparentNode, descriptor) {
    var gridLinkList = HashSet<GridLink>()
    var gridLinksBooting = HashSet<GridLink>()
    private val desc: GridDescriptor
    private var idealRenderingAngle: Float = 0.toFloat()

    init {
        this.desc = descriptor as GridDescriptor
    }

    /* Connect one GridNode to another. */
    override fun onBlockActivated(player: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        // Check if user is holding an appropriate tool.
        val stack = player.currentEquippedItem
        val itemDesc = GenericItemBlockUsingDamageDescriptor.getDescriptor(stack)
        if (stack != null && itemDesc is ElectricalCableDescriptor) {
            return onTryGridConnect(player, stack, itemDesc, side)
        }
        // TODO: Scissors. Break the connection without breaking the pole.
        return false
    }

    private fun onTryGridConnect(entityPlayer: Player, stack: ItemStack, cable: ElectricalCableDescriptor, side: Direction): Boolean {
        // First node, or second node?
        val uuid = entityPlayer.uuid
        val p = pending[uuid]
        var other: GridElement? = null
        if (p != null) {
            other = GridLink.getElementFromCoordinate(p.left)
        }
        // Check if it's the *correct* cable descriptor.
        if (cable != desc.cableDescriptor) {
            Utils.addChatMessage(entityPlayer, "Wrong cable, you need " + desc.cableDescriptor.name)
            return true
        }
        if(other == this) {
            Utils.addChatMessage(entityPlayer, "Cancelled connection")
            pending.remove(uuid)
            return true
        }
        if (other == null) {
            Utils.addChatMessage(entityPlayer, tr("Linking from here: now use the cable on the other end."))
            pending.put(uuid, Pair.of(this.coordinate(), side))
        } else {
            // Which side of each end the line goes to does not depend on where they were clicked: the ends find the
            // sides whose voltages match.
            val sides = chooseSides(this, side, other, p!!.right)
            if (sides == null) {
                Utils.addChatMessage(entityPlayer, tr("These two carry different voltages (%1$ and %2$): link them through a substation.",
                    describeVoltages(this, side), describeVoltages(other, p.right)))
                pending.remove(uuid)
                return true
            }
            val distance = other.coordinate().trueDistanceTo(this.coordinate())
            val cableLength = Math.ceil(distance).toInt()
            val range = Math.min(connectRange, other.connectRange)
            val count = entityPlayer.totalItemsCarried(stack)

            if (count < distance && !Utils.isCreative(entityPlayer as ServerPlayer)) {
                Utils.addChatMessage(entityPlayer, "You need $cableLength units of cable")
            } else if (distance > range) {
                Utils.addChatMessage(entityPlayer, "Cannot connect, range " + Math.ceil(distance) + " and limit " + range + " blocks")
            } else if (!this.canConnect(other)) {
                Utils.addChatMessage(entityPlayer, "Cannot connect these two objects")
            } else if (!this.validLOS(other)) {
                Utils.addChatMessage(entityPlayer, "Cannot connect, no line of sight")
            } else {
                try {
                    GridLink.addLink(this, other, sides.first, sides.second, cable, cableLength)
                    val volts = gridSideVoltages(sides.first)[sides.first] ?: other.gridSideVoltages(sides.second)[sides.second]
                    Utils.addChatMessage(entityPlayer, if (volts != null) tr("Linked, at %1$.", Utils.plotVolt("", volts).trim())
                        else tr("Linked."))
                    entityPlayer.removeMultipleItems(stack, cableLength)
                } catch (e: UserError) {
                    Utils.addChatMessage(entityPlayer, e.message)
                }
            }
            pending.remove(uuid)
        }
        return true
    }

    override fun initialize() {
        connect()
        for (link in gridLinksBooting) {
            link.connect()
        }
        gridLinksBooting.clear()
        updateIdealRenderAngle()
    }

    override fun connectJob() {
        super.connectJob()
        for (link in gridLinkList) {
            link.connect()
        }
    }

    override fun disconnectJob() {
        super.disconnectJob()
        for (link in gridLinkList) {
            link.disconnect()
        }
    }

    override fun onBreakElement() {
        super.onBreakElement()
        val copy = HashSet(gridLinkList)
        for (link in copy) {
            node?.dropItem(link.onBreakElement())
        }
    }

    override fun selfDestroy() {
        super.selfDestroy()
        val copy = HashSet(gridLinkList)
        for (link in copy) {
            link.selfDestroy()
        }
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)

        val gridLinks = Utils.newNbtTagCompund(nbt, "gridLinks")
        for ((i, link) in gridLinkList.withIndex()) {
            link.writeToNBT(Utils.newNbtTagCompund(gridLinks, i.toString()), "")
        }
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)

        assert(gridLinkList.isEmpty())
        val gridLinks = nbt.getCompound("gridLinks")
        var i: Int? = 0
        while (true) {
            val linkTag = gridLinks.getCompound(i!!.toString())
            if (linkTag.isEmpty)
                break
            gridLinksBooting.add(GridLink(linkTag, ""))
            i++
        }
    }

    abstract fun getGridElectricalLoad(side: Direction): ElectricalLoad?

    /**
     * The sides lines can be linked to, with the voltage each carries (null for any voltage). Poles take a line on
     * whatever side was [clicked]; substations and converters have one side for each voltage.
     */
    open fun gridSideVoltages(clicked: Direction): Map<Direction, Double?> = mapOf(clicked to null)

    // TODO: This should check if the wire isn't passing through blocks.
    private fun validLOS(@Suppress("UNUSED_PARAMETER") other: GridElement): Boolean {
        return true
    }

    // Return false if connecting grid elements that can't connect.
    protected fun canConnect(@Suppress("UNUSED_PARAMETER") other: GridElement): Boolean {
        return true
    }

    // TODO: One pole turns, all connected cables should be recalculated,
    // not just the ones being rendered here.
    /* Compute a rendering angle that minimizes any straight-on cables. */
    fun updateIdealRenderAngle() {
        if (desc.rotationIsFixed()) {
            when (front) {
                Direction.XN -> idealRenderingAngle = 0f
                Direction.XP -> idealRenderingAngle = 180f
                Direction.YN -> idealRenderingAngle = 90f
                Direction.YP -> idealRenderingAngle = 90f
                Direction.ZN -> idealRenderingAngle = 270f
                Direction.ZP -> idealRenderingAngle = 90f
            }
            //System.out.println(idealRenderingAngle);
        } else if (gridLinkList.size == 0) {
            idealRenderingAngle = 0f
        } else {
            // Compute angles.
            val angles = DoubleArray(gridLinkList.size)
            var i = 0
            for (link in gridLinkList) {
                var vec = link.a.subtract(link.b)
                // Angles 180 degrees apart are equivalent.
                if (vec.z < 0)
                    vec = vec.negate()
                val h = Math.sqrt((vec.x * vec.x + vec.z * vec.z).toDouble())
                angles[i++] = Math.acos(vec.x / h)
            }
            // This could probably be optimised with a bit of math, but w.e.
            var optAngle = 0.0
            var optErr = java.lang.Double.POSITIVE_INFINITY
            i = 0
            while (i < 128) {
                // Check a half-circle.
                val angle = Math.PI * i / 128.0
                val error = angles
                    .map { Math.abs(Math.sin(angle - it)) }
                    .sumOf { it * it * it }
                if (error < optErr) {
                    optAngle = angle
                    optErr = error
                }
                i++
            }
            idealRenderingAngle = Math.toDegrees(-optAngle).toFloat()
        }
    }

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try {
            stream.writeFloat(idealRenderingAngle)
            // Each wire pair should be drawn by exactly one node.
            // Check for which ones it's this one.
            val ourLinks = gridLinkList.filter { it.a == coordinate()/* && link.connected*/ }
            // The renderer needs to know, for each catenary:
            // - Vec3 of the starting point.
            // - Vec3 of the end point.
            // There's a finite number of starting points, and a potentially unlimited number of endpoints...
            // But until we get protocol buffers or something, simple remains good.
            // So we'll just send pairs, even if there's some duplication.
            stream.writeInt(ourLinks.size)
            for (link in ourLinks) {
                val target = link.getOtherElement(this)
                val ourSide = link.getSide(this)
                val theirSide = link.getSide(target)
                // It's always the "a" side doing this.
                val offset = link.b.subtract(link.a)
                for (i in 0..1) {
                    val start = getRenderCablePoint(ourSide, i).yRot(Math.toRadians(idealRenderingAngle.toDouble()).toFloat())
                    var end = target.getRenderCablePoint(theirSide, i).yRot(Math.toRadians(target.idealRenderingAngle.toDouble()).toFloat())
                    end = end.add(offset.x.toDouble(), offset.y.toDouble(), offset.z.toDouble())
                    writeVec(stream, start)
                    writeVec(stream, end)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    protected open fun getCablePoint(side: Direction, i: Int): Vec3 {
        if (i >= 2) throw AssertionError("Invalid cable point index")
        val part = (if (i == 0) desc.plus else desc.gnd)[0]
        val bb = part.boundingBox()
        return bb.centre()
    }

    protected open fun getRenderCablePoint(side: Direction, i: Int): Vec3 =
        getCablePoint(side, i).add(
            desc.renderOffset.xCoord, desc.renderOffset.yCoord, desc.renderOffset.zCoord
        )

    @Throws(IOException::class)
    private fun writeVec(stream: DataOutputStream, sp: Vec3) {
        stream.writeFloat(sp.xCoord.toFloat())
        stream.writeFloat(sp.yCoord.toFloat())
        stream.writeFloat(sp.zCoord.toFloat())
    }

    override fun multiMeterString(side: Direction): String {
        val electricalLoad = getGridElectricalLoad(side)
        return Utils.plotUIP(electricalLoad?.voltage ?: 0.0, electricalLoad?.current ?: 0.0)
    }

    override fun thermoMeterString(side: Direction): String {
        val thermalLoad = getThermalLoad(side, LRDU.Up)
        return plotAmbientCelsius("T", thermalLoad!!.temperatureCelsius)
    }

    companion object {
        /**
         * The sides of [a] and [b] to link, for a line between them: sides with the same voltage, preferring the
         * clicked ones; null when their voltages differ.
         */
        fun chooseSides(a: GridElement, aClicked: Direction, b: GridElement, bClicked: Direction): kotlin.Pair<Direction, Direction>? {
            val candidates = ArrayList<kotlin.Pair<Direction, Direction>>()
            for ((sa, va) in a.gridSideVoltages(aClicked)) for ((sb, vb) in b.gridSideVoltages(bClicked)) {
                if (va == null || vb == null || Math.abs(va - vb) < 1) candidates.add(sa to sb)
            }
            return candidates.maxByOrNull { (sa, sb) ->
                val va = a.gridSideVoltages(aClicked)[sa]
                val vb = b.gridSideVoltages(bClicked)[sb]
                (if (va != null && vb != null) 4 else 0) + (if (sa == aClicked) 1 else 0) + (if (sb == bClicked) 1 else 0)
            }
        }

        private fun describeVoltages(e: GridElement, clicked: Direction): String =
            e.gridSideVoltages(clicked).values.filterNotNull().joinToString("/") { Utils.plotVolt("", it).trim() }.ifEmpty { "?" }

        /**
         * The last place any given player tried to link two instance nodes.
         */
        private val pending = HashMap<UUID, Pair<Coordinate, Direction>>()
    }
}
