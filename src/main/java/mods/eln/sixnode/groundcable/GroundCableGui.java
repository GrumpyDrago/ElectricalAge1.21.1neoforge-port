package mods.eln.sixnode.groundcable;

import mods.eln.gui.GuiContainerEln;
import mods.eln.gui.GuiHelperContainer;
import mods.eln.gui.GuiTextFieldEln;
import mods.eln.compat.client.GuiButton;
import net.minecraft.world.entity.player.Player;
import mods.eln.compat.IInventory;

public class GroundCableGui extends GuiContainerEln {

    GuiButton toogleSwitch;
    GuiTextFieldEln setUmin, setUmax;
    GroundCableRender render;

    enum SelectedType {none, min, max}

    public GroundCableGui(Player player, IInventory inventory, GroundCableRender render) {
        super(new GroundCableContainer(player, inventory));
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
