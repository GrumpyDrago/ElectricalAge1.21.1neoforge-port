package mods.eln.generic;

import mods.eln.misc.RealisticEnum;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Item behind a {@link GenericItemBlockUsingDamageDescriptor}.
 */
public abstract class BlockDescriptorItem<Descriptor extends GenericItemBlockUsingDamageDescriptor> extends DescriptorItem {
    protected final Descriptor descriptor;

    protected BlockDescriptorItem(DescriptorCollection<?> collection, int elnId, Descriptor descriptor) {
        super(collection, elnId, new Properties());
        this.descriptor = descriptor;
    }

    @Override
    public Descriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public String getDescriptorName() {
        return descriptor.name;
    }

    @Override
    public String getIconName() {
        return "eln:" + descriptor.iconName;
    }

    @Override
    public String getIconFolder() {
        return "block";
    }

    @Override
    protected void describe(ItemStack stack, Player player, List<String> list, boolean advanced) {
        descriptor.addInformation(stack, player, list, advanced);
    }

    @Override
    protected RealisticEnum describeRealism(List<String> list) {
        return descriptor.addRealismContext(list);
    }

    @Override
    public int getMaxStackSize(@NotNull ItemStack stack) {
        return descriptor.getItemStackLimit(stack);
    }

    @Override
    public boolean onEntityItemUpdate(@NotNull ItemStack stack, @NotNull ItemEntity entity) {
        return descriptor.onEntityItemUpdate(entity);
    }

    @Override
    public @NotNull InteractionResult onItemUseFirst(@NotNull ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && descriptor.onItemUseFirst(stack, player)) return InteractionResult.SUCCESS;
        return InteractionResult.PASS;
    }
}
