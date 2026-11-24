package mods.eln.sixnode.mqttsignal

import mods.eln.compat.*

import mods.eln.misc.BasicContainer
import net.minecraft.world.entity.player.Player

class MqttSignalControllerContainer(player: Player, inventory: IInventory) :
    BasicContainer(player, inventory, emptyArray())
