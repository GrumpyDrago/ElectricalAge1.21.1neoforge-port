package mods.eln.node

import mods.eln.compat.*

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import mods.eln.environment.RoomThermalManager
import net.minecraft.server.level.ServerPlayer

class NodeServer {
    fun init() {
        //	NodeBlockEntity.nodeAddedList.clear();
    }

    fun stop() {
        //	NodeBlockEntity.nodeAddedList.clear();
        RoomThermalManager.clear()
    }

    var counter = 0
    @SubscribeEvent
    fun tick(event: ServerTickEvent.Pre) {
        val server = FMLCommonHandler.instance().minecraftServerInstance
        if (server != null) {
            RoomThermalManager.tick(server)
            val manager = NodeManager.instance!!
            manager.publishChanges()
            for (player in server.players) {
                val container = player.containerMenu as? INodeContainer ?: continue
                val node = container.node ?: continue
                if (counter % (1 + container.refreshRateDivider) == 0 && manager.getNodeFromCoordonate(node.coordinate) === node) {
                    node.publishToPlayer(player)
                }
            }
            counter++
        }
    }

    init {
        FMLCommonHandler.instance().bus().register(this)
    }
}
