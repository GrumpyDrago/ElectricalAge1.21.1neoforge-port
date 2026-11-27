package mods.eln.transparentnode.battery

import mods.eln.compat.*

import mods.eln.compat.client.GL11
import mods.eln.misc.Direction
import mods.eln.misc.UtilsClient
import mods.eln.misc.LRDUMask
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeElementRender
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import java.io.DataInputStream
import java.io.IOException

class BatteryRender(tileEntity: TransparentNodeEntity, descriptor: TransparentNodeDescriptor) : TransparentNodeElementRender(tileEntity, descriptor) {
    var energy = 0f
    var life = 0f
    var descriptor: BatteryDescriptor = descriptor as BatteryDescriptor
    var plus = false
    var minus = false
    var lrdu = LRDUMask()
    var power = 0f

    var vented = false
    /** 0 to 1, as the cells near their heat limit. */
    var heat = 0f

    override fun draw() {
        front?.glRotateXnRef()
        if (vented) {
            // Glowing red hot, pulsing.
            val pulse = 0.8f + 0.2f * Math.sin(System.currentTimeMillis() / 250.0).toFloat()
            UtilsClient.disableLight()
            GL11.glColor4f(1f, 0.3f * pulse, 0.15f * pulse, 1f)
            descriptor.draw(plus, minus)
            UtilsClient.enableLight()
            GL11.glColor4f(1f, 1f, 1f, 1f)
        } else if (heat > 0) {
            GL11.glColor4f(1f, 1f - 0.55f * heat, 1f - 0.65f * heat, 1f)
            descriptor.draw(plus, minus)
            GL11.glColor4f(1f, 1f, 1f, 1f)
        } else {
            descriptor.draw(plus, minus)
        }
    }

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        try {
            power = stream.readFloat()
            energy = stream.readFloat()
            life = stream.readShort() / 1000.0f
            lrdu.deserialize(stream)
            vented = stream.readBoolean()
            heat = (stream.readByte().toInt() and 0xFF) / 255f
            plus = true
            minus = true
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen {
        return BatteryGuiDraw(this)
    }
}
