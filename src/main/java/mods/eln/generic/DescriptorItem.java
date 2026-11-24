package mods.eln.generic;

import mods.eln.compat.ClientHooks;
import mods.eln.compat.IElnItem;
import mods.eln.compat.IItemRenderer;
import mods.eln.misc.RealisticEnum;
import mods.eln.misc.UtilsClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * An item standing for one descriptor of a {@link DescriptorCollection}.
 */
public abstract class DescriptorItem extends Item implements IElnItem, IItemRenderer {
    private final int elnId;
    private final DescriptorCollection<?> collection;

    protected DescriptorItem(DescriptorCollection<?> collection, int elnId, Properties properties) {
        super(properties);
        this.collection = collection;
        this.elnId = elnId;
    }

    public DescriptorCollection<?> getCollection() {
        return collection;
    }

    public abstract Object getDescriptor();

    public abstract String getDescriptorName();

    /** Name of the flat icon, "eln:name" without folder or extension. */
    public abstract String getIconName();

    /** Folder below textures/ that holds the icon. */
    public abstract String getIconFolder();

    /** Whether the descriptor draws itself, as opposed to using the flat icon. */
    public boolean hasCustomRenderer() {
        for (ItemRenderType type : ItemRenderType.values()) {
            if (handleRenderType(getDefaultInstance(), type)) return true;
        }
        return false;
    }

    @Override
    public int getElnId() {
        return elnId;
    }

    @Override
    public @NotNull String getDescriptionId() {
        return getDescriptorName().replaceAll("\\s+", "_") + ".name";
    }

    protected abstract void describe(ItemStack stack, Player player, List<String> list, boolean advanced);

    protected abstract RealisticEnum describeRealism(List<String> list);

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        List<String> lines = new ArrayList<>();
        List<String> realism = new ArrayList<>();
        Player player = ClientHooks.player();
        if (player == null) return;
        describe(stack, player, lines, flag.isAdvanced());
        RealisticEnum kind = describeRealism(realism);
        List<String> result = new ArrayList<>();
        UtilsClient.showItemTooltip(lines, realism, kind, result);
        for (String line : result) tooltip.add(Component.literal(line));
    }
}
