package mods.eln.sixnode.electricalfiredetector

import mods.eln.compat.*

import mods.eln.gui.GuiContainerEln
import mods.eln.gui.GuiHelperContainer
import net.minecraft.world.entity.player.Player

class ElectricalFireDetectorGui(player: Player, inventory: IInventory, var render: ElectricalFireDetectorRender)
    : GuiContainerEln(ElectricalFireDetectorContainer(player, inventory)) {
    override fun newHelper(): GuiHelperContainer = GuiHelperContainer(this, 176, 166 - 52, 8, 84 - 52)
}
