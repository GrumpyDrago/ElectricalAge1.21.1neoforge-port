package mods.eln.generic;

import mods.eln.Eln;
import mods.eln.compat.Mc;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Keeps track of the descriptor families so they can be listed in the creative tabs.
 */
public final class CreativeTabPopulator {

    private static final List<DescriptorCollection<?>> COLLECTIONS = new ArrayList<>();

    private CreativeTabPopulator() {
    }

    public static void register(DescriptorCollection<?> collection) {
        if (!COLLECTIONS.contains(collection)) COLLECTIONS.add(collection);
    }

    public static List<DescriptorCollection<?>> getCollections() {
        return COLLECTIONS;
    }

    public static void addEntries(GenericCreativeTab tab, List<ItemStack> list) {
        for (DescriptorCollection<?> collection : COLLECTIONS) {
            collection.addToTab(tab, list);
        }
        if (tab == Eln.creativeTabPowerElectronics) {
            moveRegulatorChipsAfterDcDcConverters(list);
        }
    }

    private static void moveRegulatorChipsAfterDcDcConverters(List<ItemStack> list) {
        List<ItemStack> regulators = new ArrayList<>();

        for (Iterator<ItemStack> iterator = list.iterator(); iterator.hasNext(); ) {
            ItemStack stack = iterator.next();
            if (stack == null) continue;

            if (isRegulatorChip(stack)) {
                regulators.add(stack);
                iterator.remove();
            }
        }

        if (regulators.isEmpty()) return;
        int insertAfter = -1;
        for (int index = 0; index < list.size(); index++) {
            if (isDcDcConverter(list.get(index))) {
                insertAfter = index;
            }
        }
        int insertAt = insertAfter >= 0 ? insertAfter + 1 : list.size();
        list.addAll(insertAt, regulators);
    }

    private static boolean isRegulatorChip(ItemStack stack) {
        return Eln.sixNodeItem.owns(stack.getItem()) && (Mc.getItemDamage(stack) >> 6) == 5;
    }

    private static boolean isDcDcConverter(ItemStack stack) {
        if (!Eln.transparentNodeItem.owns(stack.getItem())) return false;
        int damage = Mc.getItemDamage(stack);
        int group = damage >> 6;
        int subId = damage & 63;
        return group == 2 && subId >= 1 && subId <= 7;
    }
}
