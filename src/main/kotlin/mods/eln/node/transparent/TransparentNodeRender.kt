package mods.eln.node.transparent

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.compat.client.GL11
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.phys.AABB

class TransparentNodeRender(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<TransparentNodeEntity> {
    override fun render(entity: TransparentNodeEntity, partialTick: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int) {
        val render = entity.elementRender ?: return
        GL11.inWorld(pose, buffers, light, overlay) {
            GL11.glTranslatef(.5f, .5f, .5f)
            render.draw()
        }
    }

    override fun getViewDistance(): Int = 256

    override fun getRenderBoundingBox(entity: TransparentNodeEntity): AABB = entity.renderBox()

    override fun shouldRenderOffScreen(entity: TransparentNodeEntity): Boolean = !entity.cameraDrawOptimisation()
}
