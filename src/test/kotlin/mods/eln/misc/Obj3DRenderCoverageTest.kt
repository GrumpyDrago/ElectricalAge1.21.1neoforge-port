package mods.eln.misc

import mods.eln.compat.*

import mods.eln.disableLog4jJmx
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Obj3DRenderCoverageTest {
    @Test
    fun drawingOutsideAFrameDoesNothing() {
        disableLog4jJmx()

        val obj = Obj3D()
        assertTrue(obj.loadFile("TestObj3D/TestObj3D.obj"))

        obj.bindTexture("Panel.png")

        val panel = obj.getPart("panel")
        assertNotNull(panel)
        val fg = panel.faceGroup.first()
        assertNotNull(fg.textureResource)

        assertEquals("eln:model/testobj3d/panel.png", fg.textureResource.toString())
        fg.draw()

        fg.textureResource = null
        fg.draw()

        panel.draw()
        panel.draw(0.2f, 1f, 0f, 0f)
        panel.draw(0.1f, 1f, 0f, 0f, 0.2f, 0.3f)
        panel.draw(0.1f, 1f, 0f, 0f, 0.2f, 1f, 0f, 0f)
        panel.drawNoBind()
        panel.drawNoBind(0.2f, 0f, 1f, 0f)

        obj.draw("panel")
        obj.draw("missing")

        val box = panel.boundingBox()
        assertNotNull(box)
        val cached = panel.boundingBox()
        assertTrue(cached === box)

        panel.clear()
        assertEquals(0, panel.faceGroup.size)
    }

    @Test
    fun faceGroupDrawVertexSwitchesModes() {
        val obj = Obj3D()
        val fg = Obj3D.FaceGroup()

        val a = obj.Vertex(0f, 0f, 0f)
        val b = obj.Vertex(1f, 0f, 0f)
        val c = obj.Vertex(0f, 1f, 0f)
        val d = obj.Vertex(1f, 1f, 0f)
        val normalTri = obj.Normal(a, b, c)
        val normalQuad = obj.Normal(a, b, d)

        val tri = obj.Face(
            arrayOf(a, b, c),
            arrayOf(obj.Uv(0f, 0f), obj.Uv(0f, 0f), obj.Uv(0f, 0f)),
            normalTri
        )
        val quad = obj.Face(
            arrayOf(a, b, d, c),
            arrayOf(obj.Uv(0f, 0f), obj.Uv(0f, 0f), obj.Uv(0f, 0f), obj.Uv(0f, 0f)),
            normalQuad
        )
        fg.face.add(tri)
        fg.face.add(quad)

        fg.drawNoBind()
        fg.drawNoBind()
    }

    @Test
    fun drawingWithABoundResourceOutsideAFrameDoesNothing() {
        disableLog4jJmx()

        val obj = Obj3D()
        assertTrue(obj.loadFile("StreetLight/StreetLight.obj"))

        val part = obj.getPart("socket")
        assertNotNull(part)
        val fg = part.faceGroup.first()
        fg.textureResource = rl("eln", "model/StreetLight/StreetLight_on.png")
        fg.draw()
    }
}
