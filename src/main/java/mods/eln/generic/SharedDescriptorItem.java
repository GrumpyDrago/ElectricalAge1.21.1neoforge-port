package mods.eln.generic;

import mods.eln.compat.IItemRenderer;
import mods.eln.misc.RealisticEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Item behind a {@link GenericItemUsingDamageDescriptor}; forwards everything to it.
 */
public class SharedDescriptorItem extends DescriptorItem {
    private final GenericItemUsingDamageDescriptor descriptor;

    public SharedDescriptorItem(DescriptorCollection<?> collection, int elnId, GenericItemUsingDamageDescriptor descriptor, int maxStackSize) {
        super(collection, elnId, new Properties().stacksTo(maxStackSize));
        this.descriptor = descriptor;
    }

    @Override
    public GenericItemUsingDamageDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public String getDescriptorName() {
        return descriptor.name;
    }

    @Override
    public String getIconName() {
        return descriptor.getIconName();
    }

    @Override
    public String getIconFolder() {
        return "item";
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
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        ItemStack result = descriptor.onItemRightClick(stack, level, player);
        return InteractionResultHolder.pass(result);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos();
        Vec3 hit = context.getClickLocation();
        boolean done = descriptor.onItemUse(context.getItemInHand(), player, context.getLevel(), pos.getX(), pos.getY(), pos.getZ(),
            context.getClickedFace().get3DDataValue(), (float) (hit.x - pos.getX()), (float) (hit.y - pos.getY()), (float) (hit.z - pos.getZ()));
        return done ? InteractionResult.sidedSuccess(context.getLevel().isClientSide) : InteractionResult.PASS;
    }

    @Override
    public boolean onEntitySwing(@NotNull ItemStack stack, @NotNull LivingEntity entity, @NotNull InteractionHand hand) {
        return descriptor.onEntitySwing(entity, stack);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, Level level, @NotNull Entity entity, int slot, boolean selected) {
        if (level.isClientSide) return;
        descriptor.onUpdate(stack, level, entity, slot, selected);
    }

    @Override
    public float getDestroySpeed(@NotNull ItemStack stack, BlockState state) {
        return Math.max(1f, descriptor.getStrVsBlock(stack, state.getBlock()));
    }

    @Override
    public boolean isCorrectToolForDrops(@NotNull ItemStack stack, BlockState state) {
        return descriptor.getStrVsBlock(stack, state.getBlock()) > 1f;
    }

    @Override
    public boolean mineBlock(@NotNull ItemStack stack, Level level, @NotNull BlockState state, @NotNull BlockPos pos, @NotNull LivingEntity entity) {
        if (level.isClientSide) return false;
        return descriptor.onBlockDestroyed(stack, level, state.getBlock(), pos.getX(), pos.getY(), pos.getZ(), entity);
    }

    @Override
    public boolean onDroppedByPlayer(@NotNull ItemStack item, @NotNull Player player) {
        return descriptor.onDroppedByPlayer(item, player);
    }

    /** Energy and wear live in the stack's data; do not replay the equip animation each time they change. */
    @Override
    public boolean shouldCauseReequipAnimation(@NotNull ItemStack oldStack, @NotNull ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean handleRenderType(ItemStack item, IItemRenderer.ItemRenderType type) {
        return descriptor.handleRenderType(item, type);
    }

    @Override
    public boolean shouldUseRenderHelper(IItemRenderer.ItemRenderType type, ItemStack item, IItemRenderer.ItemRendererHelper helper) {
        return descriptor.shouldUseRenderHelper(type, item, helper);
    }

    @Override
    public void renderItem(IItemRenderer.ItemRenderType type, ItemStack item, Object... data) {
        descriptor.renderItem(type, item, data);
    }
}
