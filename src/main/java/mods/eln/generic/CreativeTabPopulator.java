package mods.eln.generic;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
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
    }
}
