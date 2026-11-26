package mods.eln.item

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.environment.AmbientTemperature
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Shows how hot the mod's parts are around the player, through the camera's eyes: while it is held, the server
 * sends the temperatures of nearby parts once a second, and the client tints them from blue to white.
 */
class ThermalCameraDescriptor(name: String) : GenericItemUsingDamageDescriptor(name) {
    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.addAll(tr("Hold it to see how hot cables and machines are.\nHot spots show long before anything burns.").split("\n"))
        list.add(tr("Range: %1$ blocks", ThermalCamera.RANGE))
    }
}

/** One part the camera sees. */
data class ThermalSpot(val x: Int, val y: Int, val z: Int, val celsius: Float)

object ThermalCamera {
    const val RANGE = 24
    const val MAX_SPOTS = 1024

    fun isHolding(player: Player?): Boolean {
        val camera = Eln.thermalCameraElement ?: return false
        return player != null && camera.checkSameItemStack(player.mainHandItem)
    }

    /** The temperature of the hottest thermal load of [node], in °C, or null if it has none. */
    fun hottest(node: NodeBase): Double? {
        var best: Double? = null
        fun consider(delta: Double, ambient: () -> Double) {
            val t = delta + ambient()
            if (best == null || t > best!!) best = t
        }
        when (node) {
            is SixNode -> for (element in node.sideElementList) {
                if (element == null || element.thermalLoadList.isEmpty()) continue
                val ambient by lazy { safeAmbient { element.getAmbientTemperatureCelsius() } }
                for (load in element.thermalLoadList) consider(load.temperatureCelsius) { ambient }
            }
            is TransparentNode -> {
                val element = node.element ?: return null
                if (element.thermalLoadList.isEmpty()) return null
                val ambient by lazy { safeAmbient { element.getAmbientTemperatureCelsius() } }
                for (load in element.thermalLoadList) consider(load.temperatureCelsius) { ambient }
            }
        }
        return best
    }

    private inline fun safeAmbient(f: () -> Double): Double = try {
        f()
    } catch (_: IllegalStateException) {
        20.0
    }

    /** What [player] sees: the hottest load of each part within range, nearest first. */
    fun scan(player: ServerPlayer): List<ThermalSpot> {
        val manager = NodeManager.instance ?: return emptyList()
        val level = player.level()
        val spots = ArrayList<Pair<Double, ThermalSpot>>()
        for (node in manager.nodeList) {
            val c = node.coordinate
            if (c.world() !== level) continue
            val dx = c.x + 0.5 - player.x
            val dy = c.y + 0.5 - player.y
            val dz = c.z + 0.5 - player.z
            val d2 = dx * dx + dy * dy + dz * dz
            if (d2 > RANGE * RANGE) continue
            val t = hottest(node) ?: continue
            spots.add(d2 to ThermalSpot(c.x, c.y, c.z, t.toFloat()))
        }
        spots.sortBy { it.first }
        return spots.take(MAX_SPOTS).map { it.second }
    }

    /** Answers a client that holds the camera. */
    fun answer(player: ServerPlayer) {
        if (!isHolding(player)) return
        val spots = scan(player)
        val ambient = try {
            AmbientTemperature().get(mods.eln.misc.Coordinate(player.blockPosition().x, player.blockPosition().y, player.blockPosition().z, player.level()))
        } catch (_: Exception) {
            20.0
        }
        val bos = ByteArrayOutputStream(16 + spots.size * 16)
        val stream = DataOutputStream(bos)
        stream.writeByte(Eln.packetThermalImage.toInt())
        stream.writeFloat(ambient.toFloat())
        stream.writeShort(spots.size)
        for (s in spots) {
            stream.writeInt(s.x)
            stream.writeInt(s.y)
            stream.writeInt(s.z)
            stream.writeFloat(s.celsius)
        }
        Utils.sendPacketToClient(bos, player)
    }

    /** Client side: the last picture, and when it came. */
    @Volatile
    var image: List<ThermalSpot> = emptyList()
        private set
    @Volatile
    var ambient = 20f
        private set
    @Volatile
    var imageTime = 0L
        private set

    fun receive(stream: DataInputStream) {
        ambient = stream.readFloat()
        val n = stream.readShort().toInt()
        val list = ArrayList<ThermalSpot>(n)
        repeat(n) { list.add(ThermalSpot(stream.readInt(), stream.readInt(), stream.readInt(), stream.readFloat())) }
        image = list
        imageTime = System.currentTimeMillis()
    }

    /** The span of the colour scale: from ambient to at least 40 °C above it. */
    fun scale(): Pair<Float, Float> {
        val hottest = image.maxOfOrNull { it.celsius } ?: ambient
        return ambient to maxOf(hottest, ambient + 40f)
    }

    /** The "ironbow" palette of thermal cameras, for [t] from 0 (cold) to 1 (hot): black-blue, purple, red, yellow, white. */
    fun colour(t: Float): FloatArray {
        val stops = arrayOf(
            floatArrayOf(0.05f, 0.05f, 0.35f), floatArrayOf(0.45f, 0.05f, 0.6f), floatArrayOf(0.85f, 0.15f, 0.2f),
            floatArrayOf(1f, 0.6f, 0.05f), floatArrayOf(1f, 0.95f, 0.5f), floatArrayOf(1f, 1f, 1f)
        )
        val x = t.coerceIn(0f, 1f) * (stops.size - 1)
        val i = x.toInt().coerceAtMost(stops.size - 2)
        val f = x - i
        return FloatArray(3) { stops[i][it] + (stops[i + 1][it] - stops[i][it]) * f }
    }
}
