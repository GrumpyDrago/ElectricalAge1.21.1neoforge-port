package mods.eln.node.six

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.compat.client.GL11
import mods.eln.misc.Direction.Companion.fromInt
import mods.eln.misc.UtilsClient.glDefaultColor
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.AABB

class SixNodeRender(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<SixNodeEntity> {
    override fun render(entity: SixNodeEntity, partialTick: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int) {
        if (entity.isCovered) {
            val state = entity.coverState()
            val renderer = Minecraft.getInstance().blockRenderer
            val type = ItemBlockRenderTypes.getRenderType(state, false)
            renderer.renderBatched(state, entity.blockPos, entity.level!!, pose, buffers.getBuffer(type), false, RandomSource.create(42L))
            return
        }
        GL11.inWorld(pose, buffers, light, overlay) {
            GL11.glTranslatef(.5f, .5f, .5f)
            for ((idx, render) in entity.elementRenderList.withIndex()) {
                if (render != null) {
                    glDefaultColor()
                    GL11.glPushMatrix()
                    fromInt(idx)!!.glRotateXnRef()
                    GL11.glTranslatef(-0.5f, 0f, 0f)
                    render.draw()
                    GL11.glPopMatrix()
                }
            }
        }
    }

    override fun getViewDistance(): Int = 256

    override fun getRenderBoundingBox(entity: SixNodeEntity): AABB = entity.renderBox()

    override fun shouldRenderOffScreen(entity: SixNodeEntity): Boolean = !entity.cameraDrawOptimisation()
}
