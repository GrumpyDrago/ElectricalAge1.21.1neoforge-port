package mods.eln.compat.client

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

/** Render types used by [GL11]; one per texture and state combination. */
object ElnRenderTypes {
    private data class WorldKey(val texture: ResourceLocation, val blend: Boolean, val cull: Boolean, val linear: Boolean, val depth: Boolean, val write: Boolean)
    private data class GuiKey(val texture: ResourceLocation, val linear: Boolean, val depth: Boolean)

    private val worldTypes = HashMap<WorldKey, RenderType>()
    private val guiTypes = HashMap<GuiKey, RenderType>()

    @JvmStatic
    fun world(texture: ResourceLocation, blend: Boolean, cull: Boolean, linear: Boolean, depth: Boolean, write: Boolean): RenderType =
        worldTypes.getOrPut(WorldKey(texture, blend, cull, linear, depth, write)) { Shards.world(texture, blend, cull, linear, depth, write) }

    @JvmStatic
    fun gui(texture: ResourceLocation, linear: Boolean, depth: Boolean): RenderType =
        guiTypes.getOrPut(GuiKey(texture, linear, depth)) { Shards.gui(texture, linear, depth) }

    private val unlitTypes = HashMap<WorldKey, RenderType>()

    /** Not affected by diffuse lighting. Vertices carry position, colour, texture coordinate and light. */
    @JvmStatic
    fun unlit(texture: ResourceLocation, blend: Boolean, cull: Boolean, linear: Boolean, depth: Boolean, write: Boolean): RenderType =
        unlitTypes.getOrPut(WorldKey(texture, blend, cull, linear, depth, write)) { Shards.unlit(texture, blend, cull, linear, depth, write) }

    // RenderStateShard's constants are protected, hence the subclass.
    private object Shards : RenderStateShard("eln_shards", {}, {}) {
        fun world(texture: ResourceLocation, blend: Boolean, cull: Boolean, linear: Boolean, depth: Boolean, write: Boolean): RenderType {
            val state = RenderType.CompositeState.builder()
                .setShaderState(
                    when {
                        blend -> RENDERTYPE_ENTITY_TRANSLUCENT_SHADER
                        cull -> RENDERTYPE_ENTITY_CUTOUT_SHADER
                        else -> RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER
                    }
                )
                .setTextureState(TextureStateShard(texture, linear, false))
                .setTransparencyState(if (blend) TRANSLUCENT_TRANSPARENCY else NO_TRANSPARENCY)
                .setCullState(if (cull) CULL else NO_CULL)
                .setDepthTestState(if (depth) LEQUAL_DEPTH_TEST else NO_DEPTH_TEST)
                .setWriteMaskState(if (write) COLOR_DEPTH_WRITE else COLOR_WRITE)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true)
            return RenderType.create("eln_world", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false, state)
        }

        fun unlit(texture: ResourceLocation, blend: Boolean, cull: Boolean, linear: Boolean, depth: Boolean, write: Boolean): RenderType {
            val state = RenderType.CompositeState.builder()
                .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                .setTextureState(TextureStateShard(texture, linear, false))
                .setTransparencyState(if (blend) TRANSLUCENT_TRANSPARENCY else NO_TRANSPARENCY)
                .setCullState(if (cull) CULL else NO_CULL)
                .setDepthTestState(if (depth) LEQUAL_DEPTH_TEST else NO_DEPTH_TEST)
                .setWriteMaskState(if (write) COLOR_DEPTH_WRITE else COLOR_WRITE)
                .setLightmapState(LIGHTMAP)
                .createCompositeState(false)
            return RenderType.create("eln_unlit", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 1536, false, false, state)
        }

        fun gui(texture: ResourceLocation, linear: Boolean, depth: Boolean): RenderType {
            val state = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(TextureStateShard(texture, linear, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(if (depth) LEQUAL_DEPTH_TEST else NO_DEPTH_TEST)
                .createCompositeState(false)
            return RenderType.create("eln_gui", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 786, false, false, state)
        }
    }
}
