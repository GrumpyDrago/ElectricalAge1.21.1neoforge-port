package mods.eln.entity

import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.resources.ResourceLocation

class ReplicatorRender(context: EntityRendererProvider.Context) :
    MobRenderer<ReplicatorEntity, ReplicatorModel>(context, ReplicatorModel(context.bakeLayer(ReplicatorModel.LAYER)), 0.3f) {
    override fun getTextureLocation(entity: ReplicatorEntity): ResourceLocation = TEXTURE

    companion object {
        private val TEXTURE = ResourceLocation.fromNamespaceAndPath("eln", "textures/entity/replicator.png")
    }
}
