package mods.eln.generic;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * An Electrical Age creative tab. Filled from the descriptor families and from items added with {@link #add}.
 */
public class GenericCreativeTab {
    private final String label;
    private ItemStack iconStack;
    private final List<Supplier<ItemStack>> extra = new ArrayList<>();

    public GenericCreativeTab(String label, Item item) {
        this.label = label;
        setIcon(new ItemStack(item));
    }

    public String getLabel() {
        return label;
    }

    public void setIcon(ItemStack stack) {
        this.iconStack = stack == null ? null : stack.copy();
    }

    public void add(Supplier<ItemStack> stack) {
        extra.add(stack);
    }

    public CreativeModeTab build() {
        return CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + label))
            .icon(() -> iconStack != null ? iconStack : new ItemStack(Items.REDSTONE))
            .displayItems((parameters, output) -> {
                List<ItemStack> list = new ArrayList<>();
                for (Supplier<ItemStack> s : extra) list.add(s.get());
                CreativeTabPopulator.addEntries(this, list);
                Set<Integer> seen = new HashSet<>();
                for (ItemStack stack : list) {
                    if (stack == null || stack.isEmpty()) continue;
                    ItemStack one = stack.copyWithCount(1);
                    // Tabs reject duplicates outright.
                    if (seen.add(ItemStack.hashItemAndComponents(one))) output.accept(one);
                }
            })
            .build();
    }
}
