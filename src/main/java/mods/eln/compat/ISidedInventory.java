package mods.eln.compat;

import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sided variant of {@link IInventory}; sides are the vanilla 3D data values (0 = down ... 5 = east).
 */
public interface ISidedInventory extends IInventory, WorldlyContainer {

    int[] getAccessibleSlotsFromSide(int side);

    boolean canInsertItem(int slot, ItemStack stack, int side);

    boolean canExtractItem(int slot, ItemStack stack, int side);

    @Override
    default int @NotNull [] getSlotsForFace(@NotNull Direction side) {
        return getAccessibleSlotsFromSide(side.get3DDataValue());
    }

    @Override
    default boolean canPlaceItemThroughFace(int slot, @NotNull ItemStack stack, @Nullable Direction side) {
        return canInsertItem(slot, stack, side == null ? 0 : side.get3DDataValue());
    }

    @Override
    default boolean canTakeItemThroughFace(int slot, @NotNull ItemStack stack, @NotNull Direction side) {
        return canExtractItem(slot, stack, side.get3DDataValue());
    }
}
