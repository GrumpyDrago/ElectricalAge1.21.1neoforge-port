package mods.eln.generic;

import mods.eln.Eln;
import mods.eln.compat.Mc;
import mods.eln.registration.ElnRegistry;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Family of plain (non block) items.
 */
public class GenericItemUsingDamage<Descriptor extends GenericItemUsingDamageDescriptor> extends DescriptorCollection<Descriptor> implements IGenericItemUsingDamage {
    private final int maxStackSize;

    public GenericItemUsingDamage(int maxStackSize) {
        this.maxStackSize = maxStackSize;
    }

    public void addWithoutRegistry(int damage, Descriptor descriptor) {
        create(damage, descriptor);
    }

    public void addElement(int damage, Descriptor descriptor) {
        create(damage, descriptor);
        orderList.add(damage);
        ElnRegistry.registerName(descriptor.name, descriptor.newItemStack(1));
    }

    private void create(int damage, Descriptor descriptor) {
        subItemList.put(damage, descriptor);
        descriptors.add(descriptor);
        SharedDescriptorItem item = new SharedDescriptorItem(this, damage, descriptor, maxStackSize);
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
            if (tab == descriptorTab) descriptor.getSubItems(list);
        }
    }
}
