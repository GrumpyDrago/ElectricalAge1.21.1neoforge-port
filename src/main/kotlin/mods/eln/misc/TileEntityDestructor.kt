package mods.eln.misc

import mods.eln.compat.*

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.*

class TileEntityDestructor {
    var destroyList = ArrayList<BlockEntity>()
    fun clear() {
        destroyList.clear()
    }

    fun add(tile: BlockEntity) {
        destroyList.add(tile)
    }

    @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        for (t in destroyList) {
            if (t.worldObj != null && t.worldObj.getTileEntity(t.xCoord, t.yCoord, t.zCoord) === t) {
                t.worldObj.setBlockToAir(t.xCoord, t.yCoord, t.zCoord)
                Utils.println("destroy light at " + t.xCoord + " " + t.yCoord + " " + t.zCoord)
            }
        }
        destroyList.clear()
    }

    init {
        FMLCommonHandler.instance().bus().register(this)
    }
}
