package mods.eln.sixnode.electricalwatch;

import mods.eln.gui.GuiContainerEln;
import mods.eln.gui.GuiHelperContainer;
import net.minecraft.world.entity.player.Player;
import mods.eln.compat.IInventory;

public class ElectricalWatchGui extends GuiContainerEln {

    ElectricalWatchRender render;

    public ElectricalWatchGui(Player player, IInventory inventory, ElectricalWatchRender render) {
        super(new ElectricalWatchContainer(player, inventory));
        this.render = render;
    }

    @Override
    public void initGui() {
        super.initGui();
    }

    @Override
    protected GuiHelperContainer newHelper() {
        return new GuiHelperContainer(this, 176, 166 - 52, 8, 84 - 52);
    }
}
