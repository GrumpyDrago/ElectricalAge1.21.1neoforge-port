@file:Suppress("FunctionName", "unused", "UNUSED_PARAMETER")

package mods.eln.compat.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import mods.eln.compat.rl
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import kotlin.math.sqrt

/** Geometry in quads; [STRIDE] floats per vertex: position, texture coordinate, normal, colour. */
class GlMesh {
    var data = FloatArray(STRIDE * 64)
        private set
    var vertexCount = 0
        private set

    /** False for vertices emitted before any glColor call inside a display list; those inherit the current colour. */
    var ownColor = BooleanArray(64)
        private set

    fun add(x: Float, y: Float, z: Float, u: Float, v: Float, nx: Float, ny: Float, nz: Float, r: Float, g: Float, b: Float, a: Float, own: Boolean) {
        if ((vertexCount + 1) * STRIDE > data.size) {
            data = data.copyOf(data.size * 2)
            ownColor = ownColor.copyOf(ownColor.size * 2)
        }
        var i = vertexCount * STRIDE
        data[i++] = x
        data[i++] = y
        data[i++] = z
        data[i++] = u
        data[i++] = v
        data[i++] = nx
        data[i++] = ny
        data[i++] = nz
        data[i++] = r
        data[i++] = g
        data[i++] = b
        data[i] = a
        ownColor[vertexCount] = own
        vertexCount++
    }

    /** Set once the mesh does not change any more. */
    var finished = false
    internal val variants = ArrayList<GlBake.Variant>(0)

    private fun sameVertex(a: Int, b: Int): Boolean {
        for (k in 0 until STRIDE) if (data[a * STRIDE + k] != data[b * STRIDE + k]) return false
        return ownColor[a] == ownColor[b]
    }

    /**
     * This mesh, with pairs of triangles that share an edge made into one quad. Triangles are quads that end with the
     * same corner twice.
     */
    fun mergeTriangles(): GlMesh {
        val quads = vertexCount / 4
        val triangles = (0 until quads).filter { sameVertex(it * 4 + 2, it * 4 + 3) }
        if (triangles.size < 2) return this

        fun hash(vertex: Int): Int {
            var h = 1
            for (k in 0 until STRIDE) h = h * 31 + (data[vertex * STRIDE + k] + 0f).hashCode()
            return h
        }

        // Directed edges; the neighbor has the same edge the other way round.
        val edges = HashMap<Long, MutableList<Int>>()
        fun key(from: Int, to: Int) = (hash(from).toLong() shl 32) or (hash(to).toLong() and 0xFFFFFFFFL)
        for (t in triangles) for (e in 0..2) edges.getOrPut(key(t * 4 + e, t * 4 + (e + 1) % 3)) { ArrayList(1) }.add(t * 4 + e)

        val out = GlMesh()
        val isTriangle = BooleanArray(quads).also { for (t in triangles) it[t] = true }
        val used = BooleanArray(quads)
        for (q in 0 until quads) {
            if (used[q]) continue
            used[q] = true
            var merged = false
            if (isTriangle[q]) {
                for (e in 0..2) {
                    val a = q * 4 + e
                    val b = q * 4 + (e + 1) % 3
                    val c = q * 4 + (e + 2) % 3
                    // Triangle b, c, a and its neighbor a, d, b across the edge a-b make up the quad b, c, a, d.
                    val other = edges[key(b, a)]?.firstOrNull { edge ->
                        !used[edge / 4] && sameVertex(edge, b) && sameVertex(edge / 4 * 4 + (edge % 4 + 1) % 3, a)
                    } ?: continue
                    val triangle = other / 4
                    used[triangle] = true
                    val d = triangle * 4 + (other % 4 + 2) % 3
                    out.copyVertex(this, b)
                    out.copyVertex(this, c)
                    out.copyVertex(this, a)
                    out.copyVertex(this, d)
                    merged = true
                    break
                }
            }
            if (!merged) for (k in 0..3) out.copyVertex(this, q * 4 + k)
        }
        return out
    }

    fun copyVertex(from: GlMesh, index: Int) {
        val i = index * STRIDE
        val d = from.data
        add(d[i], d[i + 1], d[i + 2], d[i + 3], d[i + 4], d[i + 5], d[i + 6], d[i + 7], d[i + 8], d[i + 9], d[i + 10], d[i + 11], from.ownColor[index])
    }

    fun clear() {
        vertexCount = 0
    }

    companion object {
        const val STRIDE = 12
    }
}

/**
 * A fixed-function style drawing API on top of PoseStack and MultiBufferSource.
 *
 * Electrical Age's models, cables and GUIs are drawn with translate / rotate / scale and begin / vertex / end calls.
 * Renderers open a context with [inWorld] or [inGui]; all geometry then goes through the render types in
 * [ElnRenderTypes], so it is batched and lit like any other block entity or GUI element.
 * Must only be used on the render thread.
 */
object GL11 {
    const val GL_POINTS = 0
    const val GL_LINES = 1
    const val GL_LINE_LOOP = 2
    const val GL_LINE_STRIP = 3
    const val GL_TRIANGLES = 4
    const val GL_TRIANGLE_STRIP = 5
    const val GL_TRIANGLE_FAN = 6
    const val GL_QUADS = 7
    const val GL_QUAD_STRIP = 8
    const val GL_POLYGON = 9

    const val GL_CURRENT_BIT = 0x1
    const val GL_ENABLE_BIT = 0x2000
    const val GL_CULL_FACE = 0xB44
    const val GL_LIGHTING = 0xB50
    const val GL_DEPTH_TEST = 0xB71
    const val GL_ALPHA_TEST = 0xBC0
    const val GL_BLEND = 0xBE2
    const val GL_SCISSOR_TEST = 0xC11
    const val GL_TEXTURE_2D = 0xDE1
    const val GL_LINE_SMOOTH = 0xB20
    const val GL_LINE_SMOOTH_HINT = 0xC52
    const val GL_NICEST = 0x1102
    const val GL_SRC_COLOR = 0x300
    const val GL_ONE_MINUS_SRC_COLOR = 0x301
    const val GL_SRC_ALPHA = 0x302
    const val GL_ONE_MINUS_SRC_ALPHA = 0x303
    const val GL_DST_COLOR = 0x306
    const val GL_LEQUAL = 0x203
    const val GL_GREATER = 0x204
    const val GL_BACK = 0x405
    const val GL_FLAT = 0x1D00
    const val GL_SMOOTH = 0x1D01
    const val GL_NEAREST = 0x2600
    const val GL_LINEAR = 0x2601
    const val GL_TEXTURE_MAG_FILTER = 0x2800
    const val GL_TEXTURE_MIN_FILTER = 0x2801
    const val GL_COMPILE = 0x1300

    val WHITE: ResourceLocation = ResourceLocation.withDefaultNamespace("textures/misc/white.png")

    // ------------------------------------------------------------ context

    private class Context(
        val pose: PoseStack,
        val buffers: MultiBufferSource,
        val gui: GuiGraphics?,
        val light: Int,
        val overlay: Int,
        val forceNoCull: Boolean
    ) {
        /** Blocks drawn the usual way. Shader packs and effects bring their own buffers, and need to see the vertices. */
        val keepable = gui == null && !forceNoCull && buffers.javaClass == MultiBufferSource.BufferSource::class.java
    }

    private val contexts = ArrayList<Context>()
    private val idlePose = PoseStack()
    private val ctx: Context? get() = contexts.lastOrNull()

    /** The GUI currently being drawn, if any. */
    @JvmStatic
    val guiGraphics: GuiGraphics?
        get() = ctx?.gui

    @JvmStatic
    val pose: PoseStack
        get() = ctx?.pose ?: idlePose

    @JvmStatic
    val light: Int
        get() = ctx?.light ?: LightTexture.FULL_BRIGHT

    @JvmStatic
    val buffers: MultiBufferSource?
        get() = ctx?.buffers

    private fun <T> run(context: Context, body: () -> T): T {
        contexts.add(context)
        val saved = State(state)
        state.reset(context.gui != null)
        context.pose.pushPose()
        try {
            return body()
        } finally {
            context.pose.popPose()
            contexts.removeAt(contexts.size - 1)
            state.set(saved)
            mode = -1
        }
    }

    /** Runs [body] with drawing directed at world (or item) geometry. */
    @JvmStatic
    @JvmOverloads
    fun <T> inWorld(pose: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int = OverlayTexture.NO_OVERLAY, forceNoCull: Boolean = false, body: () -> T): T =
        run(Context(pose, buffers, null, light, overlay, forceNoCull), body)

    /** Runs [body] with drawing directed at the GUI. */
    @JvmStatic
    fun <T> inGui(graphics: GuiGraphics, body: () -> T): T {
        graphics.flush()
        try {
            return run(Context(graphics.pose(), graphics.bufferSource(), graphics, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, true), body)
        } finally {
            graphics.flush()
        }
    }

    // ------------------------------------------------------------ state

    private class State() {
        var texture: ResourceLocation? = null
        var texture2d = true
        var blend = false
        var cull = true
        var lighting = true
        var depthTest = true
        var r = 1f
        var g = 1f
        var b = 1f
        var a = 1f
        var lineWidth = 1f

        constructor(o: State) : this() {
            set(o)
        }

        fun set(o: State) {
            texture = o.texture
            texture2d = o.texture2d
            blend = o.blend
            cull = o.cull
            lighting = o.lighting
            depthTest = o.depthTest
            r = o.r
            g = o.g
            b = o.b
            a = o.a
            lineWidth = o.lineWidth
        }

        fun reset(gui: Boolean) {
            set(State())
            lighting = !gui
        }
    }

    private val state = State()
    private val attribStack = ArrayList<State>()
    private val linearTextures = HashSet<ResourceLocation>()

    @JvmStatic
    fun bindTexture(texture: ResourceLocation?) = op {
        state.texture = texture
    }

    @JvmStatic
    fun glEnable(cap: Int) = setCap(cap, true)

    @JvmStatic
    fun glDisable(cap: Int) = setCap(cap, false)

    private fun setCap(cap: Int, on: Boolean) = op {
        when (cap) {
            GL_TEXTURE_2D -> state.texture2d = on
            GL_BLEND -> state.blend = on
            GL_CULL_FACE -> state.cull = on
            GL_LIGHTING -> state.lighting = on
            GL_DEPTH_TEST -> state.depthTest = on
        }
    }

    @JvmStatic
    fun glIsEnabled(cap: Int): Boolean = when (cap) {
        GL_TEXTURE_2D -> state.texture2d
        GL_BLEND -> state.blend
        GL_CULL_FACE -> state.cull
        GL_LIGHTING -> state.lighting
        GL_DEPTH_TEST -> state.depthTest
        else -> false
    }

    @JvmStatic
    fun glGetBoolean(cap: Int): Boolean = glIsEnabled(cap)

    @JvmStatic
    fun glPushAttrib(bits: Int) = op { attribStack.add(State(state)) }

    @JvmStatic
    fun glPopAttrib() = op { attribStack.removeLastOrNull()?.let { state.set(it) } }

    @JvmStatic
    fun glBlendFunc(src: Int, dst: Int) {
    }

    @JvmStatic
    fun glAlphaFunc(func: Int, ref: Float) {
    }

    @JvmStatic
    fun glShadeModel(mode: Int) {
    }

    @JvmStatic
    fun glDepthMask(on: Boolean) {
    }

    @JvmStatic
    fun glDepthFunc(func: Int) {
    }

    @JvmStatic
    fun glCullFace(face: Int) {
    }

    @JvmStatic
    fun glHint(target: Int, mode: Int) {
    }

    @JvmStatic
    fun glLineWidth(width: Float) = op { state.lineWidth = width }

    @JvmStatic
    fun glPointSize(size: Float) {
    }

    @JvmStatic
    fun glTexParameteri(target: Int, name: Int, value: Int) = op {
        if (name == GL_TEXTURE_MAG_FILTER) {
            state.texture?.let { if (value == GL_LINEAR) linearTextures.add(it) else linearTextures.remove(it) }
        }
    }

    @JvmStatic
    fun glGetTexParameteri(target: Int, name: Int): Int =
        if (state.texture != null && state.texture in linearTextures) GL_LINEAR else GL_NEAREST

    @JvmStatic
    fun glColor3f(r: Float, g: Float, b: Float) = glColor4f(r, g, b, 1f)

    @JvmStatic
    fun glColor3d(r: Double, g: Double, b: Double) = glColor4f(r.toFloat(), g.toFloat(), b.toFloat(), 1f)

    @JvmStatic
    fun glColor4d(r: Double, g: Double, b: Double, a: Double) = glColor4f(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat())

    @JvmStatic
    fun glColor4f(r: Float, g: Float, b: Float, a: Float) {
        val rr = r.coerceIn(0f, 1f)
        val gg = g.coerceIn(0f, 1f)
        val bb = b.coerceIn(0f, 1f)
        val aa = a.coerceIn(0f, 1f)
        val rec = recording
        if (rec != null) {
            recR = rr
            recG = gg
            recB = bb
            recA = aa
            recOwn = true
            rec.add { setColor(rr, gg, bb, aa) }
            return
        }
        setColor(rr, gg, bb, aa)
    }

    private fun setColor(r: Float, g: Float, b: Float, a: Float) {
        state.r = r
        state.g = g
        state.b = b
        state.a = a
    }

    // ------------------------------------------------------------ matrices

    @JvmStatic
    fun glPushMatrix() = op { pose.pushPose() }

    @JvmStatic
    fun glPopMatrix() = op { if (!pose.clear()) pose.popPose() }

    @JvmStatic
    fun glTranslatef(x: Float, y: Float, z: Float) = op { pose.translate(x, y, z) }

    @JvmStatic
    fun glTranslated(x: Double, y: Double, z: Double) = op { pose.translate(x, y, z) }

    @JvmStatic
    fun glScalef(x: Float, y: Float, z: Float) = op { pose.scale(x, y, z) }

    @JvmStatic
    fun glScaled(x: Double, y: Double, z: Double) = op { pose.scale(x.toFloat(), y.toFloat(), z.toFloat()) }

    @JvmStatic
    fun glRotated(angle: Double, x: Double, y: Double, z: Double) = glRotatef(angle.toFloat(), x.toFloat(), y.toFloat(), z.toFloat())

    @JvmStatic
    fun glRotatef(angle: Float, x: Float, y: Float, z: Float) = op {
        val len = sqrt(x * x + y * y + z * z)
        if (len > 1e-6f && angle != 0f) {
            pose.mulPose(Quaternionf().rotationAxis(Math.toRadians(angle.toDouble()).toFloat(), x / len, y / len, z / len))
        }
    }

    // ------------------------------------------------------------ immediate mode

    private var mode = -1
    private val scratch = GlMesh()
    private var u = 0f
    private var v = 0f
    private var nx = 0f
    private var ny = 1f
    private var nz = 0f

    @JvmStatic
    fun glBegin(mode: Int) {
        this.mode = mode
        scratch.clear()
    }

    @JvmStatic
    fun glTexCoord2f(u: Float, v: Float) {
        this.u = u
        this.v = v
    }

    @JvmStatic
    fun glTexCoord2d(u: Double, v: Double) = glTexCoord2f(u.toFloat(), v.toFloat())

    @JvmStatic
    fun glNormal3f(x: Float, y: Float, z: Float) {
        nx = x
        ny = y
        nz = z
    }

    @JvmStatic
    fun glVertex2f(x: Float, y: Float) = glVertex3f(x, y, 0f)

    @JvmStatic
    fun glVertex2d(x: Double, y: Double) = glVertex3f(x.toFloat(), y.toFloat(), 0f)

    @JvmStatic
    fun glVertex3d(x: Double, y: Double, z: Double) = glVertex3f(x.toFloat(), y.toFloat(), z.toFloat())

    @JvmStatic
    fun glVertex3f(x: Float, y: Float, z: Float) {
        if (recording != null) {
            scratch.add(x, y, z, u, v, nx, ny, nz, recR, recG, recB, recA, recOwn)
        } else {
            scratch.add(x, y, z, u, v, nx, ny, nz, state.r, state.g, state.b, state.a, true)
        }
    }

    @JvmStatic
    fun glEnd() {
        val m = mode
        mode = -1
        if (m < 0 || scratch.vertexCount == 0) return
        if (m == GL_LINES || m == GL_LINE_STRIP || m == GL_LINE_LOOP) {
            val lines = toLineList(scratch, m)
            val rec = recording
            if (rec != null) rec.add { emitLines(lines) } else emitLines(lines)
            return
        }
        if (m == GL_POINTS) return
        val quads = toQuads(scratch, m)
        val rec = recording
        if (rec != null) {
            quads.finished = true
            rec.add { drawMesh(quads) }
        } else {
            drawMesh(quads)
        }
    }

    private fun toQuads(src: GlMesh, mode: Int): GlMesh {
        val n = src.vertexCount
        if (mode == GL_QUADS && recording == null && n % 4 == 0) return src
        val out = GlMesh()
        fun tri(a: Int, b: Int, c: Int) {
            out.copyVertex(src, a)
            out.copyVertex(src, b)
            out.copyVertex(src, c)
            out.copyVertex(src, c)
        }
        when (mode) {
            GL_QUADS -> for (i in 0 until n - n % 4) out.copyVertex(src, i)
            GL_TRIANGLES -> for (i in 0 until n / 3) tri(i * 3, i * 3 + 1, i * 3 + 2)
            GL_TRIANGLE_STRIP -> for (i in 0 until n - 2) if (i % 2 == 0) tri(i, i + 1, i + 2) else tri(i + 1, i, i + 2)
            GL_TRIANGLE_FAN, GL_POLYGON -> for (i in 1 until n - 1) tri(0, i, i + 1)
            GL_QUAD_STRIP -> {
                var i = 0
                while (i + 3 < n) {
                    out.copyVertex(src, i)
                    out.copyVertex(src, i + 1)
                    out.copyVertex(src, i + 3)
                    out.copyVertex(src, i + 2)
                    i += 2
                }
            }
        }
        return out
    }

    private fun toLineList(src: GlMesh, mode: Int): GlMesh {
        val out = GlMesh()
        val n = src.vertexCount
        when (mode) {
            GL_LINES -> for (i in 0 until n - n % 2) out.copyVertex(src, i)
            else -> {
                for (i in 0 until n - 1) {
                    out.copyVertex(src, i)
                    out.copyVertex(src, i + 1)
                }
                if (mode == GL_LINE_LOOP && n > 2) {
                    out.copyVertex(src, n - 1)
                    out.copyVertex(src, 0)
                }
            }
        }
        return out
    }

    // ------------------------------------------------------------ emission

    private fun currentTexture(): ResourceLocation = (if (state.texture2d) state.texture else null) ?: WHITE

    /** Draws [mesh] (a list of quads) with the current matrix, texture and state. */
    @JvmStatic
    fun drawMesh(mesh: GlMesh) = drawMesh(mesh, 0f, 0f)

    @JvmStatic
    fun drawMesh(mesh: GlMesh, uOffset: Float, vOffset: Float) {
        val c = ctx ?: return
        if (mesh.vertexCount == 0) return
        val texture = currentTexture()
        val linear = linearTextures.isNotEmpty() && texture in linearTextures
        val last = c.pose.last()
        val matrix = last.pose()
        val d = mesh.data
        if (c.gui != null) {
            val vc = c.buffers.getBuffer(ElnRenderTypes.gui(texture, linear, state.depthTest))
            for (i in 0 until mesh.vertexCount) {
                val o = i * GlMesh.STRIDE
                vc.addVertex(matrix, d[o], d[o + 1], d[o + 2]).setUv(d[o + 3] + uOffset, d[o + 4] + vOffset)
                color(vc, mesh, i, o)
            }
            return
        }
        val cull = state.cull && !c.forceNoCull
        if (!state.lighting) {
            val vc = c.buffers.getBuffer(ElnRenderTypes.unlit(texture, state.blend, cull, linear, state.depthTest))
            for (i in 0 until mesh.vertexCount) {
                val o = i * GlMesh.STRIDE
                vc.addVertex(matrix, d[o], d[o + 1], d[o + 2])
                color(vc, mesh, i, o)
                vc.setUv(d[o + 3] + uOffset, d[o + 4] + vOffset).setLight(LightTexture.FULL_BRIGHT)
            }
            return
        }
        val type = ElnRenderTypes.world(texture, state.blend, cull, linear, state.depthTest)
        val color = packColor(state.r, state.g, state.b, state.a)
        if (c.keepable && !state.blend && GlBake.draw(mesh, type, last, color, c.light, c.overlay, uOffset, vOffset)) return
        val vc = c.buffers.getBuffer(type)
        // Models can have thousands of vertices, so everything is spelled out here: passing a vertex at once is several
        // times faster than one attribute after the other.
        val m00 = matrix.m00(); val m01 = matrix.m01(); val m02 = matrix.m02()
        val m10 = matrix.m10(); val m11 = matrix.m11(); val m12 = matrix.m12()
        val m20 = matrix.m20(); val m21 = matrix.m21(); val m22 = matrix.m22()
        val m30 = matrix.m30(); val m31 = matrix.m31(); val m32 = matrix.m32()
        val normals = last.normal()
        val n00 = normals.m00(); val n01 = normals.m01(); val n02 = normals.m02()
        val n10 = normals.m10(); val n11 = normals.m11(); val n12 = normals.m12()
        val n20 = normals.m20(); val n21 = normals.m21(); val n22 = normals.m22()
        val own = mesh.ownColor
        val overlay = c.overlay
        val light = c.light
        var lastNx = Float.NaN
        var lastNy = Float.NaN
        var lastNz = Float.NaN
        var nx = 0f
        var ny = 0f
        var nz = 0f
        var o = 0
        for (i in 0 until mesh.vertexCount) {
            val x = d[o]
            val y = d[o + 1]
            val z = d[o + 2]
            val inX = d[o + 5]
            val inY = d[o + 6]
            val inZ = d[o + 7]
            if (inX != lastNx || inY != lastNy || inZ != lastNz) {
                lastNx = inX
                lastNy = inY
                lastNz = inZ
                nx = n00 * inX + n10 * inY + n20 * inZ
                ny = n01 * inX + n11 * inY + n21 * inZ
                nz = n02 * inX + n12 * inY + n22 * inZ
                val length = sqrt(nx * nx + ny * ny + nz * nz)
                if (length > 1e-6f) {
                    nx /= length
                    ny /= length
                    nz /= length
                }
            }
            vc.addVertex(
                m00 * x + m10 * y + m20 * z + m30,
                m01 * x + m11 * y + m21 * z + m31,
                m02 * x + m12 * y + m22 * z + m32,
                if (own[i]) packColor(d[o + 8], d[o + 9], d[o + 10], d[o + 11]) else color,
                d[o + 3] + uOffset, d[o + 4] + vOffset, overlay, light, nx, ny, nz
            )
            o += GlMesh.STRIDE
        }
    }

    internal fun packColor(r: Float, g: Float, b: Float, a: Float): Int =
        ((a * 255f).toInt() shl 24) or ((r * 255f).toInt() shl 16) or ((g * 255f).toInt() shl 8) or (b * 255f).toInt()

    @JvmStatic
    fun nextFrame() = GlBake.nextFrame()

    /** Frees the models kept on the graphics card. */
    @JvmStatic
    fun forgetMeshes() = GlBake.clear()

    private fun color(vc: VertexConsumer, mesh: GlMesh, index: Int, o: Int) {
        if (mesh.ownColor[index]) {
            val d = mesh.data
            vc.setColor(d[o + 8], d[o + 9], d[o + 10], d[o + 11])
        } else {
            vc.setColor(state.r, state.g, state.b, state.a)
        }
    }

    private fun emitLines(lines: GlMesh) {
        val c = ctx ?: return
        val d = lines.data
        if (c.gui != null) {
            // Screen-space lines become thin quads.
            val quads = GlMesh()
            // The width is in pixels, whatever the scale the plot is drawn at.
            val m = c.pose.last().pose()
            val base = (state.lineWidth * 0.5f).coerceAtLeast(0.5f) / 2f
            val scaleX = sqrt(m.m00() * m.m00() + m.m01() * m.m01()).coerceAtLeast(1e-6f)
            val scaleY = sqrt(m.m10() * m.m10() + m.m11() * m.m11()).coerceAtLeast(1e-6f)
            for (i in 0 until lines.vertexCount / 2) {
                val a = i * 2 * GlMesh.STRIDE
                val b = a + GlMesh.STRIDE
                val dx = (d[b] - d[a]) * scaleX
                val dy = (d[b + 1] - d[a + 1]) * scaleY
                val len = sqrt(dx * dx + dy * dy)
                if (len < 1e-6f) continue
                val px = -dy / len * base / scaleX
                val py = dx / len * base / scaleY
                fun put(o: Int, idx: Int, sx: Float, sy: Float) {
                    val own = lines.ownColor[idx]
                    quads.add(d[o] + sx, d[o + 1] + sy, d[o + 2], 0f, 0f, 0f, 0f, 1f, d[o + 8], d[o + 9], d[o + 10], d[o + 11], own)
                }
                put(a, i * 2, px, py)
                put(a, i * 2, -px, -py)
                put(b, i * 2 + 1, -px, -py)
                put(b, i * 2 + 1, px, py)
            }
            val savedTexture = state.texture2d
            state.texture2d = false
            drawMesh(quads)
            state.texture2d = savedTexture
            return
        }
        val vc = c.buffers.getBuffer(RenderType.lines())
        val last = c.pose.last()
        for (i in 0 until lines.vertexCount / 2) {
            val a = i * 2 * GlMesh.STRIDE
            val b = a + GlMesh.STRIDE
            val dx = d[b] - d[a]
            val dy = d[b + 1] - d[a + 1]
            val dz = d[b + 2] - d[a + 2]
            val len = sqrt(dx * dx + dy * dy + dz * dz)
            if (len < 1e-6f) continue
            for ((k, o) in intArrayOf(a, b).withIndex()) {
                vc.addVertex(last.pose(), d[o], d[o + 1], d[o + 2])
                color(vc, lines, i * 2 + k, o)
                vc.setNormal(last, dx / len, dy / len, dz / len)
            }
        }
    }

    // ------------------------------------------------------------ display lists

    private val lists = HashMap<Int, List<() -> Unit>>()
    private var nextList = 1
    private var recording: ArrayList<() -> Unit>? = null
    private var recordingId = 0
    private var recOwn = false
    private var recR = 1f
    private var recG = 1f
    private var recB = 1f
    private var recA = 1f

    private inline fun op(crossinline body: () -> Unit) {
        val rec = recording
        if (rec != null) rec.add { body() } else body()
    }

    @JvmStatic
    fun glGenLists(count: Int): Int {
        val id = nextList
        nextList += count
        return id
    }

    @JvmStatic
    fun glNewList(id: Int, mode: Int) {
        recording = ArrayList()
        recordingId = id
        recOwn = false
    }

    @JvmStatic
    fun glEndList() {
        recording?.let { lists[recordingId] = it }
        recording = null
    }

    @JvmStatic
    fun glCallList(id: Int) = op { lists[id]?.forEach { it() } }

    @JvmStatic
    fun glDeleteLists(id: Int, count: Int) {
        for (i in id until id + count) lists.remove(i)
    }

    // ------------------------------------------------------------ text

    @JvmStatic
    fun drawString(text: String?, x: Float, y: Float, color: Int, shadow: Boolean) {
        val c = ctx ?: return
        if (text == null) return
        val font = Minecraft.getInstance().font
        val argb = if (color and -0x4000000 == 0) color or -0x1000000 else color
        if (c.gui != null) {
            c.gui.flush()
            c.gui.drawString(font, text, x, y, argb, shadow)
            c.gui.flush()
        } else {
            val light = if (state.lighting) c.light else LightTexture.FULL_BRIGHT
            font.drawInBatch(text, x, y, argb, shadow, c.pose.last().pose(), c.buffers, net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, 0, light)
        }
    }

    /** Shader colour used by vanilla blits; keep it neutral. */
    @JvmStatic
    fun resetShaderColor() = RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

    @JvmStatic
    fun texture(namespace: String, path: String): ResourceLocation = rl(namespace, path)
}

object GL12 {
    const val GL_RESCALE_NORMAL = 0x803A
}
