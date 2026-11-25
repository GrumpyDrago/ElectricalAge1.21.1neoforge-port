package mods.eln.compat;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Inventory contract used by all Electrical Age code: an empty slot is represented by {@code null} rather than by
 * {@link ItemStack#EMPTY}. The vanilla {@link Container} methods are bridged here, so implementations automatically
 * work with menus, hoppers and anything else that speaks {@link Container}.
 */
public interface IInventory extends Container {

    int getSizeInventory();

    @Nullable
    ItemStack getStackInSlot(int slot);

    @Nullable
    ItemStack decrStackSize(int slot, int amount);

    @Nullable
    default ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    void setInventorySlotContents(int slot, @Nullable ItemStack stack);

    default String getInventoryName() {
        return "";
    }

    default boolean hasCustomInventoryName() {
        return false;
    }

    int getInventoryStackLimit();

    void markDirty();

    boolean isUseableByPlayer(Player player);

    default void openInventory() {
    }

    default void closeInventory() {
    }

    boolean isItemValidForSlot(int slot, ItemStack stack);

    // ---- Container bridge ----

    @Override
    default int getContainerSize() {
        return getSizeInventory();
    }

    @Override
    default boolean isEmpty() {
        for (int i = 0; i < getSizeInventory(); i++) {
            if (getStackInSlot(i) != null) return false;
        }
        return true;
    }

    @Override
    @NotNull
    default ItemStack getItem(int slot) {
        return Mc.orEmpty(getStackInSlot(slot));
    }

    @Override
    @NotNull
    default ItemStack removeItem(int slot, int amount) {
        return Mc.orEmpty(decrStackSize(slot, amount));
    }

    @Override
    @NotNull
    default ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        if (!stack.isEmpty()) setInventorySlotContents(slot, null);
        return stack;
    }

    @Override
    default void setItem(int slot, @NotNull ItemStack stack) {
        setInventorySlotContents(slot, stack.isEmpty() ? null : stack);
    }

    @Override
    default int getMaxStackSize() {
        return getInventoryStackLimit();
    }

    @Override
    default void setChanged() {
        markDirty();
    }

    @Override
    default boolean stillValid(@NotNull Player player) {
        return isUseableByPlayer(player);
    }

    @Override
    default boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        return isItemValidForSlot(slot, stack);
    }

    @Override
    default void clearContent() {
        for (int i = 0; i < getSizeInventory(); i++) setInventorySlotContents(i, null);
    }
}
