package mods.eln.item;

import mods.eln.compat.Mc;

import mods.eln.Eln;
import mods.eln.generic.GenericItemBlockUsingDamageDescriptor;
import mods.eln.generic.GenericItemUsingDamageDescriptor;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.node.NodeBase;
import mods.eln.node.NodeBlock;
import mods.eln.node.NodeManager;
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import mods.eln.compat.IInventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public class ConfigCopyToolDescriptor extends GenericItemUsingDamageDescriptor {
    public ConfigCopyToolDescriptor(String name) { super(name); }

    @Override
    public boolean onItemUse(ItemStack stack, Player player, Level world, int x, int y, int z, int side, float vx, float vy, float vz) {
        if(world.isClientSide) return false;

        Block block = Mc.getBlock(world, x, y, z);

        if(block instanceof NodeBlock) {
            NodeBase node = NodeManager.instance.getNodeFromCoordonate(new Coordinate(x, y, z, world));
            if(node != null) {
                node.onBlockActivated(player, Direction.fromIntMinecraftSide(side), vx, vy, vz);
            }
            return true;
        }
        return false;
    }

    public static boolean readCableType(CompoundTag compound, IInventory inv, int slot, Player invoker) {
        return readCableType(compound, "cable", inv, slot, invoker);
    }

    public static boolean readCableType(CompoundTag compound, String name, IInventory inv, int slot, Player invoker) {
        if(compound.contains(name + "Type")) {
            int amt = 1;
            if(compound.contains(name + "Amt")) {
                amt = compound.getInt(name + "Amt");
            }
            int type = compound.getInt(name + "Type");
            ItemStack stackInSlot = inv.getStackInSlot(slot);
            if (stackInSlot != null) {
                GenericItemBlockUsingDamageDescriptor thisCableDesc = GenericItemBlockUsingDamageDescriptor.getDescriptor(stackInSlot, ElectricalCableDescriptor.class);
                if(thisCableDesc != null) {
                    (new ItemMovingHelper() {
                        @Override
                        public boolean acceptsStack(ItemStack stack) {
                            return thisCableDesc.checkSameItemStack(stack);
                        }

                        @Override
                        public ItemStack newStackOfSize(int items) {
                            return thisCableDesc.newItemStack(items);
                        }
                    }).move(invoker.getInventory(), inv, slot, 0);
                }
            }
            if(type != -1) {
                GenericItemBlockUsingDamageDescriptor cableDesc = Eln.sixNodeItem.getDescriptor(type);
                (new ItemMovingHelper() {
                    @Override
                    public boolean acceptsStack(ItemStack stack) {
                        return cableDesc.checkSameItemStack(stack);
                    }

                    @Override
                    public ItemStack newStackOfSize(int items) {
                        return cableDesc.newItemStack(items);
                    }
                }).move(invoker.getInventory(), inv, slot, amt);
            }
            return true;
        }
        return false;
    }

    public static void writeCableType(CompoundTag compound, ItemStack stack) {
        writeCableType(compound, "cable", stack);
    }

    public static void writeCableType(CompoundTag compound, String name, ItemStack stack) {
        if(stack != null) {
            Eln.logger.info("CCT Copy: " + name + "Amt: " + stack.getCount());
            compound.putInt(name + "Amt", stack.getCount());
        }
        GenericItemBlockUsingDamageDescriptor desc = GenericItemBlockUsingDamageDescriptor.getDescriptor(stack);
        if(desc != null) {
            Eln.logger.info("CCT Copy: " + name + "Type: " + desc.parentItemDamage);
            compound.putInt(name + "Type", desc.parentItemDamage);
        } else {
            Eln.logger.info("CCT Copy: " + name + "Type: -1");
            compound.putInt(name + "Type", -1);
        }
    }

    public static boolean readGenDescriptor(CompoundTag compound, String name, IInventory inv, int slot, Player invoker) {
        if(compound.contains(name)) {
            int amt = 1;
            if(compound.contains(name + "Amt")) {
                amt = compound.getInt(name + "Amt");
            }
            String type = compound.getString(name);
            GenericItemUsingDamageDescriptor desc = GenericItemUsingDamageDescriptor.getDescriptor(inv.getStackInSlot(slot));
            if(desc != null) {
                (new ItemMovingHelper() {
                    @Override
                    public boolean acceptsStack(ItemStack stack) {
                        return desc.checkSameItemStack(stack);
                    }

                    @Override
                    public ItemStack newStackOfSize(int items) {
                        return desc.newItemStack(items);
                    }
                }).move(invoker.getInventory(), inv, slot, 0);
            }
            if(!type.equals(GenericItemUsingDamageDescriptor.INVALID_NAME)) {
                GenericItemUsingDamageDescriptor newDesc = GenericItemUsingDamageDescriptor.getByName(type);
                (new ItemMovingHelper() {
                    @Override
                    public boolean acceptsStack(ItemStack stack) {
                        return newDesc.checkSameItemStack(stack);
                    }

                    @Override
                    public ItemStack newStackOfSize(int items) {
                        return newDesc.newItemStack(items);
                    }
                }).move(invoker.getInventory(), inv, slot, amt);
            }
            return true;
        }
        return false;
    }

    public static void writeGenDescriptor(CompoundTag compound, String name, ItemStack stack) {
        if(stack != null) {
            Eln.logger.info("CCT Copy: " + name + "Amt: " + stack.getCount());
            compound.putInt(name + "Amt", stack.getCount());
        }
        GenericItemUsingDamageDescriptor desc = GenericItemUsingDamageDescriptor.getDescriptor(stack);
        if(desc != null) {
            Eln.logger.info("CCT Copy: " + name + " " + desc.name);
            compound.putString(name, desc.name);
        } else {
            Eln.logger.info("CCT Copy: " + name + " Invalid Descriptor");
            compound.putString(name, GenericItemUsingDamageDescriptor.INVALID_NAME);
        }
    }

    public static boolean readVanillaStack(CompoundTag compound, String name, IInventory inv, int slot, Player invoker) {
        if(compound.contains(name)) {
            int amt = 1;
            if(compound.contains(name + "Amt")) {
                amt = compound.getInt(name + "Amt");
            }
            int itemId = compound.getInt(name);
            ItemStack current = inv.getStackInSlot(slot);
            if(current != null) {
                (new ItemMovingHelper() {
                    @Override
                    public boolean acceptsStack(ItemStack stack) {
                        return current.getItem() == stack.getItem();
                    }

                    @Override
                    public ItemStack newStackOfSize(int items) {
                        return new ItemStack(current.getItem(), items);
                    }
                }).move(invoker.getInventory(), inv, slot, 0);
            }
            if(itemId >= 0) {
                (new ItemMovingHelper() {
                    @Override
                    public boolean acceptsStack(ItemStack stack) {
                        return Mc.itemId(stack.getItem()) == itemId;
                    }

                    @Override
                    public ItemStack newStackOfSize(int items) {
                        return new ItemStack(Mc.itemById(itemId), items);
                    }
                }).move(invoker.getInventory(), inv, slot, amt);
            }
            return true;
        }
        return false;
    }

    public static void writeVanillaStack(CompoundTag compound, String name, ItemStack stack) {
        if(stack == null) {
            Eln.logger.info("CCT Copy: " + name + "Amt: 0");
            compound.putInt(name, -1);
            compound.putInt(name + "Amt", 0);
        } else {
            Eln.logger.info("CCT Copy: " + name + " " + Mc.itemId(stack.getItem()));
            Eln.logger.info("CCT Copy: " + name + "Amt: " + stack.getCount());
            compound.putInt(name, Mc.itemId(stack.getItem()));
            compound.putInt(name + "Amt", stack.getCount());
        }
    }
}
