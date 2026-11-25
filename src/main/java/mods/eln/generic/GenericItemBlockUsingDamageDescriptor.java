package mods.eln.generic;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import mods.eln.compat.Mc;

import mods.eln.Eln;
import mods.eln.misc.ElnAssets;
import mods.eln.misc.RealisticEnum;
import mods.eln.generic.GenericCreativeTab;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import mods.eln.compat.IIcon;

import java.util.HashMap;
import java.util.List;

public class GenericItemBlockUsingDamageDescriptor {

    String iconName;
    private IIcon iconIndex;
    public String name;

    public static String INVALID_NAME = "$NO_DESCRIPTOR";

    public static HashMap<String, GenericItemBlockUsingDamageDescriptor> byName = new HashMap<>();

    public static GenericItemBlockUsingDamageDescriptor getByName(String name) {
        return byName.get(name);
    }

    public Item parentItem;
    public int parentItemDamage;

    public GenericItemBlockUsingDamageDescriptor(String name) {
        this(name, name);
    }

    public GenericItemBlockUsingDamageDescriptor(String name, String iconName) {
        setDefaultIcon(iconName);
        this.name = name;
        byName.put(name, this);
    }

    public void setDefaultIcon(String name) {
        String iconName = name.replaceAll(" ", "").toLowerCase();
        //Utils.println("Icon Name: " + iconName);
        if (Eln.noSymbols &&
            ElnAssets.exists("textures/block/" + iconName + "-ni.png")) {
            this.iconName = iconName + "-ni";
        } else {
            this.iconName = iconName;
        }
        iconIndex = new IIcon("eln:" + this.iconName);
    }

    public CompoundTag getDefaultNBT() {
        return null;
    }

    public void addInformation(ItemStack itemStack, Player entityPlayer, List<String> list, boolean par4) {
    }

    public RealisticEnum addRealismContext(List<String> list) {
        return null;
    }

    public IIcon getIcon() {
        return iconIndex;
    }

    public String getName(ItemStack stack) {
        return name;
    }

    private boolean hidden = false;

    public void setParent(Item item, int damage) {
        this.parentItem = item;
        this.parentItemDamage = damage;
    }

    public ItemStack newItemStack(int size) {
        return new ItemStack(parentItem, size);
    }

    public ItemStack newItemStack() {
        return new ItemStack(parentItem);
    }

    public ItemStack newCreativeTabStack() {
        ItemStack stack = new ItemStack(parentItem);
        Mc.setTagCompound(stack, getDefaultNBT());
        return stack;
    }

    public int getItemStackLimit(ItemStack stack) {
        return 64;
    }

    public boolean checkSameItemStack(ItemStack stack) {
        if(stack == null) return false;
        return stack.getItem() == parentItem;
    }

    public static GenericItemBlockUsingDamageDescriptor getDescriptor(ItemStack stack) {
        if (stack == null) return null;
        Item item = stack.getItem();
        if (item instanceof BlockDescriptorItem<?> d) return d.getDescriptor();
        return null;
    }

    public static GenericItemBlockUsingDamageDescriptor getDescriptor(ItemStack stack, Class<?> extendClass) {
        GenericItemBlockUsingDamageDescriptor desc = getDescriptor(stack);
        if (desc == null) return null;
        if (!extendClass.isAssignableFrom(desc.getClass())) return null;
        return desc;
    }

    public boolean onEntityItemUpdate(ItemEntity entityItem) {
        return false;
    }

    public boolean onItemUseFirst(ItemStack stack, Player player) {
        return false;
    }

    private GenericCreativeTab creativeTab;

    public GenericItemBlockUsingDamageDescriptor setCreativeTab(GenericCreativeTab creativeTab) {
        this.creativeTab = creativeTab;
        return this;
    }

    public GenericCreativeTab getCreativeTab() {
        return creativeTab;
    }

    public GenericItemBlockUsingDamageDescriptor hideFromCreative() {
        this.hidden = true;
        return this;
    }

    public boolean isHidden() {
        return hidden;
    }
}
