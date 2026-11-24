package mods.eln

import mods.eln.compat.ClientHooks
import mods.eln.compat.getTileEntity
import mods.eln.item.WireSnipsContainer
import mods.eln.misc.Direction
import mods.eln.misc.Utils
import mods.eln.node.INodeEntity
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Opens GUIs. A GUI is identified by a number and the position of the node it belongs to; nodes use
 * [nodeBaseOpen] + side.
 */
object GuiHandler {
    const val genericOpen = 5977
    const val wireSnipsOpen = 5978
    const val nodeBaseOpen = 6935

    /** The menu type shared by all Electrical Age containers. */
    @JvmField
    val menuType: MenuType<AbstractContainerMenu> = IMenuTypeExtension.create(::clientMenu)

    private val nextId = ThreadLocal.withInitial { 0 }

    /** Container id for the menu being constructed on this thread. */
    @JvmStatic
    val containerId: Int
        get() = nextId.get()

    private fun nodeEntity(world: Level, x: Int, y: Int, z: Int): INodeEntity? = world.getTileEntity(x, y, z) as? INodeEntity

    private fun <T> withId(id: Int, body: () -> T): T {
        val old = nextId.get()
        nextId.set(id)
        try {
            return body()
        } finally {
            nextId.set(old)
        }
    }

    private fun createMenu(id: Int, player: Player, world: Level, x: Int, y: Int, z: Int): AbstractContainerMenu? {
        if (id == wireSnipsOpen) return WireSnipsContainer(player)
        if (id < nodeBaseOpen || id > nodeBaseOpen + 5) return null
        val entity = nodeEntity(world, x, y, z) ?: return null
        return entity.newContainer(Direction.fromInt(id - nodeBaseOpen)!!, player)
    }

    /** Opens a GUI. On the server this reaches the client through the menu system, or through a packet if it has no menu. */
    @JvmStatic
    fun openGui(player: Player, id: Int, world: Level, x: Int, y: Int, z: Int) {
        if (player !is ServerPlayer) {
            ClientHooks.openGui(id, x, y, z)
            return
        }
        // Find out whether there is a menu before allocating a container id for it.
        if (createMenu(id, player, world, x, y, z) == null) {
            if (id >= nodeBaseOpen && nodeEntity(world, x, y, z) == null) return
            val bos = ByteArrayOutputStream()
            val stream = DataOutputStream(bos)
            stream.writeByte(Eln.packetOpenLocalGui.toInt())
            stream.writeInt(id)
            stream.writeInt(x)
            stream.writeInt(y)
            stream.writeInt(z)
            Utils.sendPacketToClient(bos, player)
            return
        }
        player.openMenu(object : MenuProvider {
            override fun getDisplayName(): Component = Component.empty()
            override fun createMenu(containerId: Int, inventory: Inventory, p: Player): AbstractContainerMenu? =
                withId(containerId) { createMenu(id, p, world, x, y, z) }
        }) { buf ->
            buf.writeInt(id)
            buf.writeInt(x)
            buf.writeInt(y)
            buf.writeInt(z)
        }
    }

    private fun clientMenu(containerId: Int, inventory: Inventory, buf: FriendlyByteBuf): AbstractContainerMenu {
        val id = buf.readInt()
        val x = buf.readInt()
        val y = buf.readInt()
        val z = buf.readInt()
        return withId(containerId) { mods.eln.client.ClientGui.createMenu(id, inventory.player, x, y, z) }
    }
}
