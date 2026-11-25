package mods.eln.sixnode.thermometersensor

import mods.eln.compat.*

import mods.eln.misc.BasicContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot

class ThermometerSensorContainer(player: Player, inventory: IInventory) :
    BasicContainer(player, inventory, arrayOf<Slot>())
