package mods.eln.entity

import net.minecraft.client.model.HierarchicalModel
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.resources.ResourceLocation

class ReplicatorModel(private val rootPart: ModelPart) : HierarchicalModel<ReplicatorEntity>() {
    override fun root(): ModelPart = rootPart

    override fun setupAnim(entity: ReplicatorEntity, limbSwing: Float, limbSwingAmount: Float, age: Float, headYaw: Float, headPitch: Float) {}

    companion object {
        @JvmField
        val LAYER = ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("eln", "replicator"), "main")

        @JvmStatic
        fun createLayer(): LayerDefinition {
            val mesh = MeshDefinition()
            fun part(name: String, u: Int, v: Int, x: Float, y: Float, z: Float, dx: Float, dy: Float, dz: Float, pose: PartPose) {
                mesh.root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).mirror().addBox(x, y, z, dx, dy, dz), pose)
            }
            part("head", 32, 4, -4.0f, -4.0f, -8.0f, 8.0f, 8.0f, 8.0f, PartPose.offsetAndRotation(0.0f, 20.0f, -3.0f, 0.0f, 0.0f, 0.0f))
            part("body", 0, 0, -3.0f, -3.0f, -3.0f, 6.0f, 6.0f, 6.0f, PartPose.offsetAndRotation(0.0f, 20.0f, 0.0f, 0.0f, 0.0f, 0.0f))
            part("rearEnd", 0, 12, -5.0f, -4.0f, -6.0f, 10.0f, 8.0f, 12.0f, PartPose.offsetAndRotation(0.0f, 20.0f, 9.0f, 0.0f, 0.0f, 0.0f))
            part("leg8", 18, 0, -1.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f, PartPose.offsetAndRotation(4.0f, 20.0f, -1.0f, 0.0f, 0.5759587f, 0.1919862f))
            part("leg6", 18, 0, -1.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f, PartPose.offsetAndRotation(4.0f, 20.0f, 0.0f, 0.0f, 0.2792527f, 0.1919862f))
            part("leg4", 18, 0, -1.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f, PartPose.offsetAndRotation(4.0f, 20.0f, 1.0f, 0.0f, -0.2792527f, 0.1919862f))
            part("leg2", 18, 0, -1.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f, PartPose.offsetAndRotation(4.0f, 20.0f, 2.0f, 0.0f, -0.5759587f, 0.1919862f))
            part("leg7", 18, 0, -15.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f, PartPose.offsetAndRotation(-4.0f, 20.0f, -1.0f, 0.0f, -0.5759587f, -0.1919862f))
            part("leg5", 18, 0, -15.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f, PartPose.offsetAndRotation(-4.0f, 20.0f, 0.0f, 0.0f, -0.2792527f, -0.1919862f))
            part("leg3", 18, 0, -15.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f, PartPose.offsetAndRotation(-4.0f, 20.0f, 1.0f, 0.0f, 0.2792527f, -0.1919862f))
            part("leg1", 18, 0, -15.0f, -1.0f, -1.0f, 16.0f, 2.0f, 2.0f, PartPose.offsetAndRotation(-4.0f, 20.0f, 2.0f, 0.0f, 0.5759587f, -0.1919862f))
            return LayerDefinition.create(mesh, 64, 64)
        }
    }
}
