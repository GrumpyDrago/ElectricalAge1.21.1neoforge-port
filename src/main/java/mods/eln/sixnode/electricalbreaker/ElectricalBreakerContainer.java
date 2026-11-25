package mods.eln.sixnode.electricalbreaker;

import mods.eln.misc.BasicContainer;
import net.minecraft.world.entity.player.Player;
import mods.eln.compat.IInventory;

public class ElectricalBreakerContainer extends BasicContainer {

    public ElectricalBreakerContainer(Player player, IInventory inventory) {
        super(player, inventory, new net.minecraft.world.inventory.Slot[]{});
    }
}
