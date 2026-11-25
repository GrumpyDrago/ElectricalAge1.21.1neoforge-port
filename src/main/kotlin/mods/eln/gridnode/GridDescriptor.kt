package mods.eln.gridnode

import mods.eln.compat.*

import mods.eln.misc.Direction
import mods.eln.misc.Obj3D
import mods.eln.misc.preserveMatrix
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

import java.util.ArrayList

import mods.eln.compat.client.GL11

open class GridDescriptor(name: String, protected val obj: Obj3D, ElementClass: Class<*>, RenderClass: Class<*>, val cableTexture: String, val cableDescriptor: ElectricalCableDescriptor, val connectRange: Int) : TransparentNodeDescriptor(name, ElementClass, RenderClass) {
    val plus = ArrayList<Obj3D.Obj3DPart>()
    val gnd = ArrayList<Obj3D.Obj3DPart>()

    var renderOffset: Vec3 = Vec3(0.0, 0.0, 0.0)

    protected var static_parts = ArrayList<Obj3D.Obj3DPart>()
    protected var rotating_parts = ArrayList<Obj3D.Obj3DPart>()

    init {
        rotating_parts.add(obj.getPart("main"))
        var i = 0
        while (true) {
            val plus = obj.getPart("p" + i)
            val gnd = obj.getPart("g" + i)
            if (plus == null || gnd == null) break
            rotating_parts.add(plus)
            rotating_parts.add(gnd)
            this.plus.add(plus)
            this.gnd.add(gnd)
            i++
        }
    }

    fun draw(idealRenderingAngle: Float) {
        preserveMatrix {
            GL11.glTranslated(
                renderOffset.xCoord, renderOffset.yCoord, renderOffset.zCoord
            )
            preserveMatrix {
                GL11.glRotatef(idealRenderingAngle, 0f, 1f, 0f)
                for (part in rotating_parts) {
                    part.draw()
                }
            }
            for (part in static_parts) {
                part.draw()
            }
        }
    }

    open fun hasCustomIcon() = false

    override fun renderItem(type: IItemRenderer.ItemRenderType, item: ItemStack, vararg data: Any) {
        if(type == IItemRenderer.ItemRenderType.INVENTORY &&
            hasCustomIcon()) {
            super.renderItem(type, item, data)
            return
        }
        objItemScale(obj)
        Direction.ZN.glRotateXnRef()
        GL11.glPushMatrix()
        GL11.glTranslatef(0f, -1f, 0f)
        GL11.glScalef(0.6f, 0.6f, 0.6f)
        draw(0f)
        GL11.glPopMatrix()
    }

    override fun handleRenderType(item: ItemStack, type: IItemRenderer.ItemRenderType): Boolean {
        return true
    }

    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType, item: ItemStack,
                                       helper: IItemRenderer.ItemRendererHelper): Boolean {
        if(helper == IItemRenderer.ItemRendererHelper.INVENTORY_BLOCK)
            return !hasCustomIcon()
        return true
    }

    open fun rotationIsFixed(): Boolean {
        return false
    }
}
