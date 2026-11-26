package mods.eln.sixnode.oscilloscope

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.compat.client.GL11
import mods.eln.gui.GuiButtonEln
import mods.eln.gui.GuiHelper
import mods.eln.gui.GuiScreenEln
import mods.eln.gui.IGuiObject
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Obj3D
import mods.eln.misc.Utils
import mods.eln.misc.UtilsClient
import mods.eln.node.NodeBase
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeDescriptor
import mods.eln.node.six.SixNodeElement
import mods.eln.node.six.SixNodeElementRender
import mods.eln.node.six.SixNodeEntity
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sixnode.protection.PartsDescriptor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Two channels of samples in a ring, averaged into points as wide as the time base asks for. */
class ScopeTrace(val points: Int) {
    val channels = Array(2) { DoubleArray(points) }
    var count = 0
        private set
    var head = 0
        private set
    private val sums = DoubleArray(2)
    private var samples = 0

    /** Adds a sample; every [perPoint] samples become one point. */
    fun sample(a: Double, b: Double, perPoint: Int) {
        sums[0] += a
        sums[1] += b
        if (++samples < perPoint) return
        for (c in 0..1) {
            channels[c][head] = sums[c] / samples
            sums[c] = 0.0
        }
        samples = 0
        head = (head + 1) % points
        if (count < points) count++
    }

    /** Point [i] from the oldest (0) to the newest (count - 1). */
    fun get(channel: Int, i: Int): Double = channels[channel][(head - count + i + points) % points]

    fun clear() {
        count = 0
        head = 0
        samples = 0
        sums.fill(0.0)
    }

    /** A 1-2-5 full scale for the largest value of [channel], so the trace fills between half and all of the screen. */
    fun scale(channel: Int): Double {
        var peak = 0.0
        for (i in 0 until count) peak = max(peak, abs(get(channel, i)))
        return niceScale(peak)
    }

    companion object {
        fun niceScale(peak: Double): Double {
            if (!(peak > 1e-6)) return 1.0
            val decade = 10.0.pow(Math.floor(log10(peak)))
            for (m in doubleArrayOf(1.0, 2.0, 5.0, 10.0)) if (peak <= m * decade) return m * decade
            return 10 * decade
        }
    }
}

class OscilloscopeDescriptor(name: String, obj: Obj3D) :
    PartsDescriptor(name, OscilloscopeElement::class.java, OscilloscopeRender::class.java, obj, listOf("case", "ledpower")) {

    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.addAll(tr("Plots two voltages over time on its screen.\nProbe power or signal cables; for a current,\nprobe the output of a current sensor.").split("\n"))
        list.add(tr("Front: channel 1 (yellow). Back: channel 2 (blue)."))
        list.add(tr("Samples at the rate of the simulation."))
    }

    /** Draws the traces on the screen; the screen's corners come from script/expansion_models.py. */
    fun drawTraces(values: Array<ByteArray>, count: Int) {
        if (count < 2) return
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        UtilsClient.disableLight()
        val colours = arrayOf(floatArrayOf(1f, 0.9f, 0.25f), floatArrayOf(0.35f, 0.85f, 1f))
        val mid = (SCREEN_Y0 + SCREEN_Y1) / 2
        val half = (SCREEN_Y1 - SCREEN_Y0) / 2 * 0.92f
        for (c in values.indices) {
            val col = colours[c]
            GL11.glColor4f(col[0], col[1], col[2], 1f)
            val x = SCREEN_X + 0.001f * (c + 1)
            fun z(i: Int) = SCREEN_Z1 - (SCREEN_Z1 - SCREEN_Z0) * (count - 1 - i) / (POINTS - 1)
            fun y(i: Int) = mid + values[c][i] / 127f * half
            // A quad per segment, as wide across the segment as along a flat one, so steep edges show too. The
            // corners go so that it faces out of the screen and isn't culled.
            GL11.glBegin(GL11.GL_QUADS)
            for (i in 0 until count - 1) {
                val z0 = z(i)
                val y0 = y(i)
                val z1 = z(i + 1)
                val y1 = y(i + 1)
                val len = Math.hypot((z1 - z0).toDouble(), (y1 - y0).toDouble()).toFloat().coerceAtLeast(1e-6f)
                val nz = -(y1 - y0) / len * LINE
                val ny = (z1 - z0) / len * LINE
                // Extend each segment a little along itself, so the joints have no gaps.
                val ez = (z1 - z0) / len * LINE * 0.5f
                val ey = (y1 - y0) / len * LINE * 0.5f
                GL11.glVertex3f(x, y0 - ny - ey, z0 - nz - ez)
                GL11.glVertex3f(x, y0 + ny - ey, z0 + nz - ez)
                GL11.glVertex3f(x, y1 + ny + ey, z1 + nz + ez)
                GL11.glVertex3f(x, y1 - ny + ey, z1 - nz + ez)
            }
            GL11.glEnd()
        }
        GL11.glColor4f(1f, 1f, 1f, 1f)
        UtilsClient.enableLight()
        GL11.glEnable(GL11.GL_TEXTURE_2D)
    }

    companion object {
        const val POINTS = 120
        const val SCREEN_X = 0.2605f
        const val SCREEN_Y0 = -0.2f
        const val SCREEN_Y1 = 0.3f
        const val SCREEN_Z0 = -0.4f
        const val SCREEN_Z1 = 0.2f
        const val LINE = 0.006f
        /** Seconds across the screen. */
        val TIME_BASES = doubleArrayOf(6.0, 12.0, 30.0, 60.0, 120.0, 300.0)
    }
}

class OscilloscopeElement(sixNode: SixNode, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElement(sixNode, side, descriptor) {

    val probe1 = NbtElectricalLoad("probe1")
    val probe2 = NbtElectricalLoad("probe2")
    val trace = ScopeTrace(OscilloscopeDescriptor.POINTS)
    var timeBase = 0
    var running = true
    private var publishTimer = 0.0

    init {
        electricalLoadList.add(probe1)
        electricalLoadList.add(probe2)
        electricalComponentList.add(Resistor(probe1, null).pullDown())
        electricalComponentList.add(Resistor(probe2, null).pullDown())
        electricalProcessList.add(IProcess { dt ->
            if (!running) return@IProcess
            val seconds = OscilloscopeDescriptor.TIME_BASES[timeBase]
            val perPoint = max(1, (seconds / OscilloscopeDescriptor.POINTS / dt).roundToInt())
            trace.sample(probe1.voltage, probe2.voltage, perPoint)
        })
        slowProcessList.add(IProcess { dt ->
            publishTimer += dt
            if (publishTimer >= PUBLISH_PERIOD && running) {
                publishTimer = 0.0
                needPublish()
            }
        })
    }

    override fun initialize() {
        Eln.applySmallRs(probe1)
        Eln.applySmallRs(probe2)
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int): ElectricalLoad? = when (lrdu) {
        front -> probe1
        front.inverse() -> probe2
        else -> null
    }

    override fun getConnectionMask(lrdu: LRDU): Int = when (lrdu) {
        front, front.inverse() -> NodeBase.maskElectricalAll
        else -> 0
    }

    override fun multiMeterString(): String = Utils.plotVolt("CH1:", probe1.voltage) + Utils.plotVolt("CH2:", probe2.voltage)

    override fun getWaila(): Map<String, String> = linkedMapOf(
        tr("Channel 1") to Utils.plotVolt("", probe1.voltage) + " / " + Utils.plotVolt("", trace.scale(0)),
        tr("Channel 2") to Utils.plotVolt("", probe2.voltage) + " / " + Utils.plotVolt("", trace.scale(1)),
        tr("Time across screen") to Utils.plotTime("", OscilloscopeDescriptor.TIME_BASES[timeBase]),
        tr("Running") to if (running) tr("Yes") else tr("No")
    )

    override fun hasGui() = true

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        try {
            stream.writeByte(timeBase)
            stream.writeBoolean(running)
            stream.writeShort(trace.count)
            for (c in 0..1) {
                val scale = trace.scale(c)
                stream.writeFloat(scale.toFloat())
                for (i in 0 until trace.count) {
                    stream.writeByte((trace.get(c, i) / scale * 127).roundToInt().coerceIn(-127, 127))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            when (stream.readByte().toInt()) {
                NEXT_TIME_BASE -> {
                    timeBase = (timeBase + 1) % OscilloscopeDescriptor.TIME_BASES.size
                    trace.clear()
                }
                TOGGLE_RUN -> running = !running
                CLEAR -> trace.clear()
            }
            needPublish()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        timeBase = nbt.getByte("timeBase").toInt().coerceIn(0, OscilloscopeDescriptor.TIME_BASES.size - 1)
        running = !nbt.contains("running") || nbt.getBoolean("running")
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putByte("timeBase", timeBase.toByte())
        nbt.putBoolean("running", running)
    }

    companion object {
        const val PUBLISH_PERIOD = 0.25
        const val NEXT_TIME_BASE = 1
        const val TOGGLE_RUN = 2
        const val CLEAR = 3
    }
}

class OscilloscopeRender(entity: SixNodeEntity, side: Direction, descriptor: SixNodeDescriptor) :
    SixNodeElementRender(entity, side, descriptor) {
    private val desc = descriptor as OscilloscopeDescriptor
    val values = Array(2) { ByteArray(OscilloscopeDescriptor.POINTS) }
    val scales = FloatArray(2)
    var count = 0
    var timeBase = 0
    var running = true

    override fun draw() {
        super.draw()
        front!!.glRotateOnX()
        desc.drawParts(setOf("ledpower"))
        desc.drawTraces(values, count)
    }

    override fun publishUnserialize(stream: DataInputStream) {
        super.publishUnserialize(stream)
        try {
            timeBase = stream.readByte().toInt()
            running = stream.readBoolean()
            count = stream.readShort().toInt().coerceIn(0, OscilloscopeDescriptor.POINTS)
            for (c in 0..1) {
                scales[c] = stream.readFloat()
                for (i in 0 until count) values[c][i] = stream.readByte()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun getCableRender(lrdu: LRDU): CableRenderDescriptor? = when (lrdu) {
        front, front!!.inverse() -> Eln.instance.signalCableDescriptor.render
        else -> null
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen = OscilloscopeGui(this)
}

class OscilloscopeGui(private val render: OscilloscopeRender) : GuiScreenEln() {
    private lateinit var timeBase: GuiButtonEln
    private lateinit var run: GuiButtonEln
    private lateinit var clear: GuiButtonEln

    override fun newHelper() = GuiHelper(this, 160, 76)

    override fun initGui() {
        super.initGui()
        timeBase = newGuiButton(6, 6, 148, "")
        run = newGuiButton(6, 30, 72, "")
        clear = newGuiButton(82, 30, 72, tr("Clear"))
    }

    override fun preDraw(f: Float, x: Int, y: Int) {
        super.preDraw(f, x, y)
        timeBase.displayString = tr("Time across screen: %1$", Utils.plotTime("", OscilloscopeDescriptor.TIME_BASES[render.timeBase]))
        run.displayString = if (render.running) tr("Hold") else tr("Run")
    }

    override fun postDraw(f: Float, x: Int, y: Int) {
        super.postDraw(f, x, y)
        helper.drawString(8, 56, 0xC0A020, tr("CH1: %1$ full scale", Utils.plotVolt("", render.scales[0].toDouble())))
        helper.drawString(8, 66, 0x3090C0, tr("CH2: %1$ full scale", Utils.plotVolt("", render.scales[1].toDouble())))
    }

    override fun guiObjectEvent(`object`: IGuiObject) {
        super.guiObjectEvent(`object`)
        when {
            `object` === timeBase -> render.clientSend(OscilloscopeElement.NEXT_TIME_BASE)
            `object` === run -> render.clientSend(OscilloscopeElement.TOGGLE_RUN)
            `object` === clear -> render.clientSend(OscilloscopeElement.CLEAR)
        }
    }
}
