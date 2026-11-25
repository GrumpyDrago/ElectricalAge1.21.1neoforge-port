package mods.eln.integration.jade

import mods.eln.Eln
import mods.eln.ghost.GhostBlock
import mods.eln.integration.NodeInfo
import mods.eln.node.six.SixNodeBlock
import mods.eln.node.transparent.TransparentNodeBlock
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import snownee.jade.api.BlockAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.IServerDataProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaCommonRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.JadeIds
import snownee.jade.api.WailaPlugin
import snownee.jade.api.config.IPluginConfig
import snownee.jade.api.theme.IThemeHelper
import snownee.jade.api.ui.IElement
import snownee.jade.api.ui.IElementHelper

/** Shows what something is, and what a multimeter would, in Jade's overlay. */
@WailaPlugin
class ElnJadePlugin : IWailaPlugin {
    private val blocks = listOf(SixNodeBlock::class.java, TransparentNodeBlock::class.java, GhostBlock::class.java)

    override fun register(registration: IWailaCommonRegistration) {
        for (block in blocks) registration.registerBlockDataProvider(NodeProvider, block)
    }

    override fun registerClient(registration: IWailaClientRegistration) {
        for (block in blocks) {
            registration.registerBlockComponent(NodeProvider, block)
            registration.registerBlockComponent(NodeNameProvider, block)
            registration.registerBlockIcon(NodeNameProvider, block)
        }
    }
}

private const val LINES = "elnLines"
private const val ITEM = "elnItem"

object NodeProvider : IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private val UID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Eln.MODID, "node")

    override fun getUid(): ResourceLocation = UID

    override fun appendServerData(data: CompoundTag, accessor: BlockAccessor) {
        val info = NodeInfo.of(accessor.level, accessor.hitResult)
        if (!info.stack.isEmpty) data.putString(ITEM, BuiltInRegistries.ITEM.getKey(info.stack.item).toString())
        if (info.lines.isNotEmpty()) data.put(LINES, info.lines.mapTo(ListTag()) { StringTag.valueOf(it) })
    }

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        val list = accessor.serverData.getList(LINES, Tag.TAG_STRING.toInt())
        for (i in 0 until list.size) tooltip.add(Component.literal(list.getString(i)))
    }
}

/** All components are the same block to the game, and so are all machines; only what is in them has a name. */
object NodeNameProvider : IBlockComponentProvider {
    private val UID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Eln.MODID, "node_name")

    override fun getUid(): ResourceLocation = UID

    override fun isRequired() = true

    // Soon after Jade's own name.
    override fun getDefaultPriority() = -10000

    /** Clients know what is in a block by themselves, but not what ghost blocks are a part of. */
    private fun stack(accessor: BlockAccessor): ItemStack {
        val picked = accessor.pickedResult
        // What was picked can be from the player's inventory, with a count and a name of its own.
        if (!picked.isEmpty) return ItemStack(picked.item)
        if (!accessor.serverData.contains(ITEM)) return ItemStack.EMPTY
        val id = ResourceLocation.tryParse(accessor.serverData.getString(ITEM)) ?: return ItemStack.EMPTY
        return ItemStack(BuiltInRegistries.ITEM.get(id))
    }

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        val stack = stack(accessor)
        if (!stack.isEmpty) tooltip.replace(JadeIds.CORE_OBJECT_NAME, IThemeHelper.get().title(stack.hoverName))
    }

    override fun getIcon(accessor: BlockAccessor, config: IPluginConfig, currentIcon: IElement?): IElement? {
        val stack = stack(accessor)
        return if (stack.isEmpty) currentIcon else IElementHelper.get().item(stack)
    }
}
