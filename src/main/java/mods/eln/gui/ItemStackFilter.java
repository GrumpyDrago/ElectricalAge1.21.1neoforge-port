package mods.eln.gui;

import mods.eln.compat.OreDict;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.function.Predicate;

public class ItemStackFilter implements IItemStackFilter {

    private final Predicate<ItemStack> predicate;

    private ItemStackFilter(Predicate<ItemStack> predicate) {
        this.predicate = predicate;
    }

    public ItemStackFilter(Item item) {
        this(stack -> stack.getItem() == item);
    }

    public ItemStackFilter(Block block) {
        this(block.asItem());
    }

    public static ItemStackFilter[] OreDict(String name) {
        return new ItemStackFilter[]{new ItemStackFilter(stack -> OreDict.matches(stack, name))};
    }

    @Override
    public boolean tryItemStack(ItemStack itemStack) {
        return itemStack != null && predicate.test(itemStack);
    }
}
