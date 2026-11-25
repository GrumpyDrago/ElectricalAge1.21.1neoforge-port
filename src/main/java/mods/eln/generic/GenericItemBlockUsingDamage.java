package mods.eln.generic;

import mods.eln.Eln;
import mods.eln.compat.Mc;
import mods.eln.registration.ElnRegistry;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Family of items that place something in the world.
 */
public abstract class GenericItemBlockUsingDamage<Descriptor extends GenericItemBlockUsingDamageDescriptor> extends DescriptorCollection<Descriptor> {

    /** Creates the item standing for this descriptor. */
    protected abstract DescriptorItem newItem(int damage, Descriptor descriptor);

    public void doubleEntry(int src, int dst) {
        subItemList.put(dst, subItemList.get(src));
    }

    public void addDescriptor(int damage, Descriptor descriptor) {
        create(damage, descriptor);
        orderList.add(damage);
        descriptors.add(descriptor);
        ElnRegistry.registerName(descriptor.name, descriptor.newItemStack(1));
    }

    public void addWithoutRegistry(int damage, Descriptor descriptor) {
        create(damage, descriptor);
    }

    private void create(int damage, Descriptor descriptor) {
        subItemList.put(damage, descriptor);
        DescriptorItem item = newItem(damage, descriptor);
        ElnRegistry.registerItem(ElnRegistry.uniquePath(Mc.registryPath(descriptor.name), damage), item);
        descriptor.setParent(item, damage);
        if (descriptor.getCreativeTab() == null) {
            GenericCreativeTab tab = creativeTabByGroup.get(damage >> 6);
            if (tab != null) descriptor.setCreativeTab(tab);
        }
    }

    @Override
    public ItemStack stackOf(int damage) {
        Descriptor d = getDescriptor(damage);
        return d == null ? null : d.newItemStack(1);
    }

    @Override
    public void addToTab(GenericCreativeTab tab, List<ItemStack> list) {
        for (int id : orderList) {
            Descriptor descriptor = subItemList.get(id);
            if (descriptor == null || descriptor.isHidden()) continue;
            GenericCreativeTab descriptorTab = descriptor.getCreativeTab();
            if (descriptorTab == null) descriptorTab = Eln.creativeTabOther;
            if (tab == descriptorTab) list.add(descriptor.newCreativeTabStack());
        }
    }
}
