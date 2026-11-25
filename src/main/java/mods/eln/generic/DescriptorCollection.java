package mods.eln.generic;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * A numbered family of descriptors. Each descriptor is backed by its own registered item; the number
 * ((group << 6) + subId) is how nodes are saved and synchronised, so existing numbers must not be reused.
 */
public abstract class DescriptorCollection<Descriptor> {
    public Hashtable<Integer, Descriptor> subItemList = new Hashtable<>();
    public ArrayList<Integer> orderList = new ArrayList<>();
    public ArrayList<Descriptor> descriptors = new ArrayList<>();
    protected final Map<Integer, GenericCreativeTab> creativeTabByGroup = new HashMap<>();
    protected Descriptor defaultElement = null;

    protected DescriptorCollection() {
        CreativeTabPopulator.register(this);
    }

    public void setDefaultElement(Descriptor descriptor) {
        defaultElement = descriptor;
    }

    public Descriptor getDescriptor(int damage) {
        return subItemList.get(damage);
    }

    /** The descriptor behind this stack, or the default element (normally null) if it is not part of this family. */
    @SuppressWarnings("unchecked")
    public Descriptor getDescriptor(ItemStack itemStack) {
        if (itemStack == null || !owns(itemStack.getItem())) return defaultElement;
        return (Descriptor) ((DescriptorItem) itemStack.getItem()).getDescriptor();
    }

    public boolean owns(Item item) {
        return item instanceof DescriptorItem d && d.getCollection() == this;
    }

    public void setCreativeTabForGroup(int group, GenericCreativeTab tab) {
        creativeTabByGroup.put(group, tab);
    }

    /** The stack for a descriptor number, or null. */
    public abstract ItemStack stackOf(int damage);

    /** Stacks of this family that belong in this tab. */
    public abstract void addToTab(GenericCreativeTab tab, List<ItemStack> list);
}
