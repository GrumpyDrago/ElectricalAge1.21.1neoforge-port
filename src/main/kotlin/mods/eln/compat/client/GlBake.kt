package mods.eln.compat.client

import com.mojang.blaze3d.shaders.FogShape
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderType
import org.joml.Matrix4f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Keeps big models that stand still on the graphics card, instead of sending thousands of vertices every frame.
 *
 * What is kept is the model as it is oriented, coloured and lit; only where it is relative to the camera changes from
 * frame to frame. Something that turns never looks the same for long enough to be kept.
 */
internal object GlBake {
    private const val MIN_VERTICES = 256
    private const val STEADY_FRAMES = 4
    private const val MAX_VARIANTS = 24
    private const val MAX_UNUSED_FRAMES = 600

    class Variant(val rotation: FloatArray, val color: Int, val light: Int, val overlay: Int, val u: Float, val v: Float) {
        var buffer: VertexBuffer? = null
        var lastFrame = frame
        var steady = 0
    }

    var frame = 0
        private set
    private val meshes = LinkedHashSet<GlMesh>()

    /** Forgets what has not been drawn for a while. */
    fun nextFrame() {
        frame++
        if (frame % MAX_UNUSED_FRAMES != 0 || meshes.isEmpty()) return
        meshes.removeIf { mesh ->
            mesh.variants.removeIf { variant ->
                val unused = frame - variant.lastFrame > MAX_UNUSED_FRAMES
                if (unused) variant.buffer?.close()
                unused
            }
            mesh.variants.isEmpty()
        }
    }

    fun clear() {
        for (mesh in meshes) {
            for (variant in mesh.variants) variant.buffer?.close()
            mesh.variants.clear()
        }
        meshes.clear()
    }

    /** Draws the mesh if it is being kept; otherwise the caller has to. */
    fun draw(mesh: GlMesh, type: RenderType, pose: PoseStack.Pose, color: Int, light: Int, overlay: Int, u: Float, v: Float): Boolean {
        if (mesh.vertexCount < MIN_VERTICES || !mesh.finished) return false
        val m = pose.pose()
        val x = m.m30()
        val y = m.m31()
        val z = m.m32()
        val variant = find(mesh, m, color, light, overlay, u, v) ?: return false
        if (variant.lastFrame != frame) {
            variant.steady = if (variant.lastFrame == frame - 1) variant.steady + 1 else 0
            variant.lastFrame = frame
        }
        val buffer = variant.buffer ?: run {
            if (variant.steady < STEADY_FRAMES) return false
            bake(mesh, variant, pose).also { variant.buffer = it }
        }

        type.setupRenderState()
        val shader = RenderSystem.getShader()
        if (shader == null) {
            type.clearRenderState()
            return false
        }
        // The shader computes the fog from the vertices alone, so it has to be moved instead of the model.
        val fogStart = RenderSystem.getShaderFogStart()
        val fogEnd = RenderSystem.getShaderFogEnd()
        val distance = if (RenderSystem.getShaderFogShape() == FogShape.CYLINDER) max(sqrt(x * x + z * z), abs(y)) else sqrt(x * x + y * y + z * z)
        RenderSystem.setShaderFogStart(fogStart - distance)
        RenderSystem.setShaderFogEnd(fogEnd - distance)
        buffer.bind()
        buffer.drawWithShader(Matrix4f(RenderSystem.getModelViewMatrix()).translate(x, y, z), RenderSystem.getProjectionMatrix(), shader)
        RenderSystem.setShaderFogStart(fogStart)
        RenderSystem.setShaderFogEnd(fogEnd)
        VertexBuffer.unbind()
        type.clearRenderState()
        return true
    }

    private fun find(mesh: GlMesh, m: Matrix4f, color: Int, light: Int, overlay: Int, u: Float, v: Float): Variant? {
        val variants = mesh.variants
        for (i in variants.indices) {
            val variant = variants[i]
            val r = variant.rotation
            if (variant.color == color && variant.light == light && variant.overlay == overlay && variant.u == u && variant.v == v &&
                r[0] == m.m00() && r[1] == m.m01() && r[2] == m.m02() &&
                r[3] == m.m10() && r[4] == m.m11() && r[5] == m.m12() &&
                r[6] == m.m20() && r[7] == m.m21() && r[8] == m.m22()
            ) return variant
        }
        if (variants.size >= MAX_VARIANTS) {
            val stale = variants.indexOfFirst { it.lastFrame < frame - 2 }
            if (stale < 0) return null
            variants.removeAt(stale).buffer?.close()
        }
        val rotation = floatArrayOf(m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(), m.m22())
        return Variant(rotation, color, light, overlay, u, v).also {
            variants.add(it)
            meshes.add(mesh)
        }
    }

    private fun bake(mesh: GlMesh, variant: Variant, pose: PoseStack.Pose): VertexBuffer {
        val r = variant.rotation
        val n = pose.normal()
        val d = mesh.data
        ByteBufferBuilder(mesh.vertexCount * DefaultVertexFormat.NEW_ENTITY.vertexSize).use { bytes ->
            val builder = BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY)
            for (i in 0 until mesh.vertexCount) {
                val o = i * GlMesh.STRIDE
                val x = d[o]
                val y = d[o + 1]
                val z = d[o + 2]
                var nx = n.m00() * d[o + 5] + n.m10() * d[o + 6] + n.m20() * d[o + 7]
                var ny = n.m01() * d[o + 5] + n.m11() * d[o + 6] + n.m21() * d[o + 7]
                var nz = n.m02() * d[o + 5] + n.m12() * d[o + 6] + n.m22() * d[o + 7]
                val length = sqrt(nx * nx + ny * ny + nz * nz)
                if (length > 1e-6f) {
                    nx /= length
                    ny /= length
                    nz /= length
                }
                builder.addVertex(
                    r[0] * x + r[3] * y + r[6] * z,
                    r[1] * x + r[4] * y + r[7] * z,
                    r[2] * x + r[5] * y + r[8] * z,
                    if (mesh.ownColor[i]) GL11.packColor(d[o + 8], d[o + 9], d[o + 10], d[o + 11]) else variant.color,
                    d[o + 3] + variant.u, d[o + 4] + variant.v, variant.overlay, variant.light, nx, ny, nz
                )
            }
            val buffer = VertexBuffer(VertexBuffer.Usage.STATIC)
            buffer.bind()
            buffer.upload(builder.buildOrThrow())
            VertexBuffer.unbind()
            return buffer
        }
    }
}
