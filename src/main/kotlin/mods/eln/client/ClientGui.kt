package mods.eln.client

import mods.eln.GuiHandler
import mods.eln.compat.client.GuiContainer
import mods.eln.compat.getTileEntity
import mods.eln.item.WireSnipsGui
import mods.eln.misc.Direction
import mods.eln.misc.UtilsClient
import mods.eln.node.INodeEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.network.chat.Component

/** Client half of [GuiHandler]. */
object ClientGui {
    private var pending: GuiContainer? = null

    private fun create(id: Int, player: Player, x: Int, y: Int, z: Int): Screen? {
        if (id == GuiHandler.genericOpen) return UtilsClient.guiLastOpen
        if (id == GuiHandler.wireSnipsOpen) return WireSnipsGui(player)
        if (id >= GuiHandler.nodeBaseOpen && id <= GuiHandler.nodeBaseOpen + 5) {
            val entity = player.level().getTileEntity(x, y, z) as? INodeEntity ?: return null
            return entity.newGuiDraw(Direction.fromInt(id - GuiHandler.nodeBaseOpen)!!, player)
        }
        return null
    }

    /** Opens a GUI without a menu. */
    fun open(id: Int, x: Int, y: Int, z: Int) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val screen = create(id, player, x, y, z) ?: return
        mc.setScreen(screen)
    }

    /** The screens construct their own menu; build the screen first and hand out its menu. */
    fun createMenu(id: Int, player: Player, x: Int, y: Int, z: Int): AbstractContainerMenu {
        val screen = create(id, player, x, y, z)
        val container = screen as? GuiContainer ?: throw IllegalStateException("No container GUI for $id at $x, $y, $z")
        pending = container
        return container.inventorySlots
    }

    @Suppress("UNUSED_PARAMETER")
    fun screenFor(menu: AbstractContainerMenu, inventory: Inventory, title: Component): GuiContainer {
        val screen = pending
        pending = null
        return screen ?: throw IllegalStateException("No GUI was prepared")
    }
}
