package mods.eln.client

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.item.ThermalCamera
import mods.eln.misc.UtilsClient
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** Asks for and draws the picture of a held thermal camera. */
class ThermalCameraOverlay {
    private var ticks = 0

    private fun active(): Boolean {
        val mc = Minecraft.getInstance()
        return mc.player != null && ThermalCamera.isHolding(mc.player)
    }

    private fun fresh() = System.currentTimeMillis() - ThermalCamera.imageTime < STALE_MS

    @SubscribeEvent
    fun onTick(@Suppress("UNUSED_PARAMETER") event: ClientTickEvent.Post) {
        if (!active()) {
            ticks = 0
            return
        }
        if (ticks++ % REQUEST_TICKS != 0) return
        val bos = ByteArrayOutputStream(1)
        DataOutputStream(bos).writeByte(Eln.packetThermalScanRequest.toInt())
        UtilsClient.sendPacketToServer(bos)
    }

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !active() || !fresh()) return
        val image = ThermalCamera.image
        if (image.isEmpty()) return
        val (cold, hot) = ThermalCamera.scale()
        val camera = event.camera.position
        val pose = event.poseStack
        val buffers = Minecraft.getInstance().renderBuffers().bufferSource()
        val fill = buffers.getBuffer(RenderType.debugFilledBox())
        pose.pushPose()
        pose.translate(-camera.x, -camera.y, -camera.z)
        for (spot in image) {
            val t = (spot.celsius - cold) / (hot - cold)
            val c = ThermalCamera.colour(t)
            val grow = 0.01
            LevelRenderer.addChainedFilledBoxVertices(
                pose, fill,
                spot.x - grow, spot.y - grow, spot.z - grow, spot.x + 1 + grow, spot.y + 1 + grow, spot.z + 1 + grow,
                c[0], c[1], c[2], 0.25f + 0.35f * t.coerceIn(0f, 1f)
            )
        }
        buffers.endBatch(RenderType.debugFilledBox())
        // Outline the hottest part.
        image.maxByOrNull { it.celsius }?.let { h ->
            val lines = buffers.getBuffer(RenderType.lines())
            LevelRenderer.renderLineBox(pose, lines, h.x - 0.02, h.y - 0.02, h.z - 0.02, h.x + 1.02, h.y + 1.02, h.z + 1.02, 1f, 1f, 1f, 1f)
            buffers.endBatch(RenderType.lines())
        }
        pose.popPose()
    }

    @SubscribeEvent
    fun onRenderGui(event: RenderGuiEvent.Post) {
        if (!active()) return
        val mc = Minecraft.getInstance()
        val g = event.guiGraphics
        val font = mc.font
        val x = g.guiWidth() - 22
        val top = 30
        val height = 100
        if (!fresh()) {
            g.drawString(font, tr("Thermal camera: no signal"), x - 100, top, 0xFFFFFF)
            return
        }
        val (cold, hot) = ThermalCamera.scale()
        for (i in 0 until height) {
            val c = ThermalCamera.colour(1f - i / (height - 1f))
            val argb = (0xFF shl 24) or ((c[0] * 255).toInt() shl 16) or ((c[1] * 255).toInt() shl 8) or (c[2] * 255).toInt()
            g.fill(x, top + i, x + 10, top + i + 1, argb)
        }
        g.drawString(font, "%.0f°C".format(hot), x - font.width("%.0f°C".format(hot)) - 3, top, 0xFFFFFF)
        g.drawString(font, "%.0f°C".format(cold), x - font.width("%.0f°C".format(cold)) - 3, top + height - 8, 0xFFFFFF)
        ThermalCamera.image.maxByOrNull { it.celsius }?.let {
            val text = tr("Hottest: %1$ °C", "%.0f".format(it.celsius))
            g.drawString(font, text, x + 10 - font.width(text), top + height + 6, 0xFFFFFF)
        }
    }

    companion object {
        const val REQUEST_TICKS = 20
        const val STALE_MS = 3000L
    }
}
