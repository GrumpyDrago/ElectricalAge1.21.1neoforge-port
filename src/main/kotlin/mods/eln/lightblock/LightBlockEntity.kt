package mods.eln.lightblock

import mods.eln.registration.ElnBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.INBTTReady
import mods.eln.misc.Utils
import net.minecraft.world.level.block.Blocks
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.Level

class LightBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ElnBlockEntities.lightBlock, pos, state) {

    companion object {
        @JvmField
        val observers: MutableList<LightBlockObserver> = mutableListOf()

        @JvmStatic
        fun addLight(w: Level, x: Int, y: Int, z: Int, light: Int, timeout: Int) {
            val block = w.getBlock(x, y, z)

            if (block !== Eln.lightBlock) {
                if (!isAir(block)) return
                w.setBlock(BlockPos(x, y, z), Eln.lightBlock.stateFor(light), 2)
            }

            val t = w.getTileEntity(x, y, z)

            if (t is LightBlockEntity) t.addLight(light, timeout)
            else Utils.println("Error in setting light at %d %d %d", x, y, z)
        }

        @JvmStatic
        fun addLight(coord: Coordinate, light: Int, timeout: Int) {
            addLight(coord.world(), coord.x, coord.y, coord.z, light, timeout)
        }
    }

    interface LightBlockObserver {

        fun lightBlockDestructor(coord: Coordinate)

    }

    private val lightList: MutableList<LightHandle> = mutableListOf()

    private fun addLight(light: Int, timeout: Int) {
        lightList.add(LightHandle(light, timeout))
    }

    internal class LightHandle(var value: Int = 0, var timeout: Int = 0) : INBTTReady {

        override fun readFromNBT(nbt: CompoundTag, str: String) {
            value = nbt.getInt(str + "value")
            timeout = nbt.getInt(str + "timeout")
        }

        override fun writeToNBT(nbt: CompoundTag, str: String) {
            nbt.putInt(str + "value", value)
            nbt.putInt(str + "timeout", timeout)
        }

    }

    fun updateEntity() {
        if (worldObj.isClientSide) return

        if (lightList.isEmpty()) {
            worldObj.setBlockToAir(xCoord, yCoord, zCoord)
            Utils.println("Destroy light at %d %d %d", xCoord, yCoord, zCoord)
            return
        }

        var light = 0
        val iterator: MutableIterator<LightHandle> = lightList.iterator()

        while (iterator.hasNext()) {
            val l = iterator.next()
            if (light < l.value) light = l.value
            l.timeout--
            if (l.timeout <= 0) iterator.remove()
        }

        if (light != blockState.getValue(LightBlock.LIGHT)) {
            worldObj.setBlock(blockPos, blockState.setValue(LightBlock.LIGHT, light.coerceIn(0, 15)), 2)
        }
    }

}