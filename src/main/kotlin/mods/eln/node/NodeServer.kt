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
            for (node in NodeManager.instance!!.nodeList) {
                if (node.needPublish) {
                    node.publishToAllPlayer()
                }
            }
            for (player in server.players) {
                var openContainerNode: NodeBase? = null
                var container: INodeContainer? = null
                if (player.containerMenu is INodeContainer) {
                    container = player.containerMenu as INodeContainer
                    openContainerNode = container.node
                }
                for (node in NodeManager.instance!!.nodeList) {
                    if (node === openContainerNode) {
                        if (counter % (1 + container!!.refreshRateDivider) == 0) node.publishToPlayer(player)
                    }
                }
            }
            counter++
        }
    }

    init {
        FMLCommonHandler.instance().bus().register(this)
    }
}
