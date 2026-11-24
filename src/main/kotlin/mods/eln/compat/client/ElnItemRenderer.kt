package mods.eln.compat.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.compat.IItemRenderer.ItemRenderType
import mods.eln.compat.IItemRenderer.ItemRendererHelper
import mods.eln.compat.rl
import mods.eln.generic.DescriptorItem
import mods.eln.misc.UtilsClient
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/** Lets item descriptors draw themselves, see [mods.eln.compat.IItemRenderer]. */
class ElnItemRenderer : BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher, Minecraft.getInstance().entityModels) {

    /** The pose descriptors expect to draw held items in. */
    private fun equipped(block: Boolean) {
        if (block) {
            GL11.glTranslatef(-0.5f, -0.5f, -0.5f)
        } else {
            GL11.glTranslatef(0f, -0.3f, 0f)
            GL11.glScalef(1.5f, 1.5f, 1.5f)
            GL11.glRotatef(50f, 0f, 1f, 0f)
            GL11.glRotatef(335f, 0f, 0f, 1f)
            GL11.glTranslatef(-0.9375f, -0.0625f, 0f)
        }
    }

    override fun renderByItem(stack: ItemStack, context: ItemDisplayContext, pose: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int) {
        val item = stack.item as? DescriptorItem ?: return
        val type = when (context) {
            ItemDisplayContext.GUI -> ItemRenderType.INVENTORY
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND -> ItemRenderType.EQUIPPED_FIRST_PERSON
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.HEAD -> ItemRenderType.EQUIPPED
            else -> ItemRenderType.ENTITY
        }
        GL11.inWorld(pose, buffers, light, overlay, forceNoCull = true) {
            when (type) {
                ItemRenderType.INVENTORY -> {
                    // From the unit cube to slot pixels, y pointing down.
                    GL11.glTranslatef(0f, 1f, 0f)
                    GL11.glScalef(1f / 16f, -1f / 16f, 1f / 16f)
                    if (item.shouldUseRenderHelper(type, stack, ItemRendererHelper.INVENTORY_BLOCK)) {
                        GL11.glTranslatef(-2f, 3f, 0f)
                        GL11.glScalef(10f, 10f, 10f)
                        GL11.glTranslatef(1f, 0.5f, 1f)
                        GL11.glScalef(1f, 1f, -1f)
                        GL11.glRotatef(210f, 1f, 0f, 0f)
                        GL11.glRotatef(45f, 0f, 1f, 0f)
                        GL11.glRotatef(-90f, 0f, 1f, 0f)
                    }
                }
                ItemRenderType.ENTITY -> {
                    GL11.glTranslatef(0.5f, 0.5f, 0.5f)
                    if (context == ItemDisplayContext.GROUND) {
                        val scale = if (item.shouldUseRenderHelper(type, stack, ItemRendererHelper.BLOCK_3D) && item is BlockItem) 0.25f else 0.5f
                        GL11.glScalef(scale, scale, scale)
                    }
                }
                ItemRenderType.EQUIPPED_FIRST_PERSON -> {
                    GL11.glTranslatef(0.5f, 0.5f, 0.5f)
                    GL11.glRotatef(45f, 0f, 1f, 0f)
                    GL11.glScalef(0.4f, 0.4f, 0.4f)
                    equipped(item.shouldUseRenderHelper(type, stack, ItemRendererHelper.EQUIPPED_BLOCK))
                }
                else -> {
                    GL11.glTranslatef(0.5f, 0.5f, 0.5f)
                    // Back from the hand to the arm, undoing what vanilla's ItemInHandLayer did.
                    val side = if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) -1 else 1
                    GL11.glTranslatef(-side / 16f, -0.125f, 0.625f)
                    GL11.glRotatef(-180f, 0f, 1f, 0f)
                    GL11.glRotatef(90f, 1f, 0f, 0f)
                    GL11.glTranslatef(-side * 0.0625f, 0.4375f, 0.0625f)
                    if (item.shouldUseRenderHelper(type, stack, ItemRendererHelper.BLOCK_3D)) {
                        GL11.glTranslatef(0f, 0.1875f, -0.3125f)
                        GL11.glRotatef(20f, 1f, 0f, 0f)
                        GL11.glRotatef(45f, 0f, 1f, 0f)
                        GL11.glScalef(-0.375f, -0.375f, 0.375f)
                    } else {
                        GL11.glTranslatef(0.25f, 0.1875f, -0.1875f)
                        GL11.glScalef(0.375f, 0.375f, 0.375f)
                        GL11.glRotatef(60f, 0f, 0f, 1f)
                        GL11.glRotatef(-90f, 1f, 0f, 0f)
                        GL11.glRotatef(20f, 0f, 0f, 1f)
                    }
                    equipped(item.shouldUseRenderHelper(type, stack, ItemRendererHelper.EQUIPPED_BLOCK))
                }
            }
            GL11.glColor4f(1f, 1f, 1f, 1f)
            if (item.handleRenderType(stack, type)) {
                when (type) {
                    // Whose hand it is is not known here; items only need it for what the viewer sees on their screens.
                    ItemRenderType.EQUIPPED, ItemRenderType.EQUIPPED_FIRST_PERSON -> {
                        val mc = Minecraft.getInstance()
                        item.renderItem(type, stack, null, mc.player)
                    }
                    else -> item.renderItem(type, stack)
                }
            } else {
                UtilsClient.drawIcon(type, rl("eln", "textures/" + item.iconFolder + "/" + item.iconName.substringAfter(':') + ".png"))
            }
        }
    }
}
