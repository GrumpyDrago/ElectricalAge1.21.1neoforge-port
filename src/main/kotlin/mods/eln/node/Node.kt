@file:Suppress("NAME_SHADOWING")
package mods.eln.node

import net.minecraft.core.BlockPos
import mods.eln.compat.*

import mods.eln.misc.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.LightLayer
import java.io.DataOutputStream
import java.io.IOException

abstract class Node : NodeBase() {
    private var lastLight = 0
    var lightValue: Int
        get() = lastLight
        set(light) {
            var light = light
            if (light > 15) light = 15
            if (light < 0) light = 0
            if (lastLight != light) {
                lastLight = light
                syncLight()
                needPublish = true
            }
        }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        lastLight = nbt.getByte("lastLight").toInt()
    }

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putByte("lastLight", lastLight.toByte())
    }

    /** Writes the emitted light into the block state. */
    fun syncLight() {
        if (!coordinate.blockExist) return
        NodeBlock.setLight(coordinate.world(), BlockPos(coordinate.x, coordinate.y, coordinate.z), lastLight)
    }

    var oldSendedRedstone = false
    override fun publishSerialize(stream: DataOutputStream) {
        super.publishSerialize(stream)
        try {
            val redstone = canConnectRedstone()
            stream.writeByte(lastLight or if (redstone) 0x10 else 0x00)
            if (redstone != oldSendedRedstone) needNotify = true
            oldSendedRedstone = redstone
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    val entity: NodeBlockEntity
        get() = coordinate.world().getTileEntity(coordinate.x, coordinate.y, coordinate.z) as NodeBlockEntity

    open fun isProvidingWeakPower(side: Direction?): Int {
        return 0
    }

    open fun canConnectRedstone(): Boolean {
        return false
    }
}
