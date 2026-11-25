package mods.eln.transparentnode.floodlight

import mods.eln.compat.*

import mods.eln.item.LampSlot
import mods.eln.misc.BasicContainer
import mods.eln.sixnode.lampsocket.LampSocketType
import net.minecraft.world.entity.player.Player

class FloodlightContainer(player: Player, inventory: IInventory) : BasicContainer(player, inventory, arrayOf(
    LampSlot(inventory, LAMP_SLOT_1_ID, 70+1, 61+1, 1, LAMP_SOCKET_TYPE),
    LampSlot(inventory, LAMP_SLOT_2_ID, 88+1, 61+1, 1, LAMP_SOCKET_TYPE))) {

    companion object {
        const val LAMP_SLOT_1_ID: Int = 0
        const val LAMP_SLOT_2_ID: Int = 1

        val LAMP_SOCKET_TYPE = LampSocketType.Tube
    }

}