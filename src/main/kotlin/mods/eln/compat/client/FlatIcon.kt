package mods.eln.compat.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.compat.IItemRenderer.ItemRenderType
import mods.eln.compat.IItemRenderer.ItemRendererHelper
import mods.eln.generic.DescriptorItem
import mods.eln.misc.ElnAssets
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.client.IItemDecorator
import org.joml.Matrix4f

/**
 * The icon of an item that starts with pictures on top of each other, as most do. Those are an ordinary item model, which
 * every mod that deals with the drawing of items knows how to handle. Anything on top of them, like the charge of a
 * battery, is drawn as a decoration.
 */
class FlatIcon private constructor(
    /** From back to front. */
    val layers: List<ResourceLocation>,
    val hasMore: Boolean
) : IItemDecorator {

    override fun render(graphics: GuiGraphics, font: Font, stack: ItemStack, x: Int, y: Int): Boolean {
        val item = stack.item as? DescriptorItem ?: return false
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y.toFloat(), DECORATION_DEPTH)
        GL11.intercepting({ texture, mesh, _ -> texture in layers && mesh != null && fillsSlot(mesh) }) {
            GL11.inGui(graphics) { item.renderItem(ItemRenderType.INVENTORY, stack) }
        }
        pose.popPose()
        // The state of the renderer may have been changed.
        return true
    }

    companion object {
        private const val MAX_LAYERS = 5
        /** Where vanilla puts the number of items. */
        private const val DECORATION_DEPTH = 200f
        private val identity = Matrix4f()

        /** The icon of [item], or null if it is something else. */
        fun of(item: DescriptorItem): FlatIcon? {
            val stack = item.defaultInstance
            if (!item.handleRenderType(stack, ItemRenderType.INVENTORY)) return null
            if (item.shouldUseRenderHelper(ItemRenderType.INVENTORY, stack, ItemRendererHelper.INVENTORY_BLOCK)) return null
            val layers = ArrayList<ResourceLocation>()
            var more = false
            try {
                GL11.intercepting({ texture, mesh, pose ->
                    if (!more && texture != null && mesh != null && pose.equals(identity, 1e-5f) && fillsSlot(mesh) && usable(texture)) {
                        layers.add(texture)
                    } else {
                        more = true
                    }
                    true
                }) {
                    GL11.inWorld(PoseStack(), MultiBufferSource { throw IllegalStateException("not while recording") }, 0) {
                        item.renderItem(ItemRenderType.INVENTORY, stack)
                    }
                }
            } catch (_: Exception) {
                // Where models are generated there is no font, for example. What has been drawn so far still counts.
                more = true
            }
            return if (layers.isEmpty() || layers.size > MAX_LAYERS) null else FlatIcon(layers, more)
        }

        /** Whether the texture is one that item models can use. */
        private fun usable(texture: ResourceLocation) =
            (texture.path.startsWith("textures/block/") || texture.path.startsWith("textures/item/") || isBackground(texture)) &&
                ElnAssets.exists(texture.path)

        fun isBackground(texture: ResourceLocation) = texture.path.startsWith("textures/voltages/")

        /** A white square from 0, 0 to 16, 16 with the whole texture on it. */
        private fun fillsSlot(mesh: GlMesh): Boolean {
            if (mesh.vertexCount != 4) return false
            val d = mesh.data
            for (i in 0 until 4) {
                val o = i * GlMesh.STRIDE
                if (d[o] != d[o + 3] * 16f || d[o + 1] != d[o + 4] * 16f || d[o + 2] != 0f) return false
                for (c in 8..11) if (d[o + c] != 1f) return false
            }
            return true
        }
    }
}
