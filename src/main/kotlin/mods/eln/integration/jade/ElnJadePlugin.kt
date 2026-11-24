package mods.eln.integration.jade

import mods.eln.Eln
import mods.eln.ghost.GhostBlock
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeBlock
import mods.eln.node.six.SixNodeElement
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeBlock
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import snownee.jade.api.BlockAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.IServerDataProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaCommonRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.WailaPlugin
import snownee.jade.api.config.IPluginConfig
import net.minecraft.world.level.block.Block

/** Shows what a multimeter would, in Jade's overlay. */
@WailaPlugin
class ElnJadePlugin : IWailaPlugin {
    override fun register(registration: IWailaCommonRegistration) {
        registration.registerBlockDataProvider(NodeProvider, Block::class.java)
    }

    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerBlockComponent(NodeProvider, SixNodeBlock::class.java)
        registration.registerBlockComponent(NodeProvider, TransparentNodeBlock::class.java)
        registration.registerBlockComponent(NodeProvider, GhostBlock::class.java)
    }
}

object NodeProvider : IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private const val KEY = "elnLines"
    private val UID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("eln", "node")

    override fun getUid(): ResourceLocation = UID

    private fun format(map: Map<String, String>?): List<String> = map?.map { (k, v) -> "$k: §f$v" } ?: emptyList()

    private fun linesFor(accessor: BlockAccessor): List<String> {
        val pos = accessor.position
        var coord = Coordinate(pos.x, pos.y, pos.z, accessor.level)
        var side: Direction? = (accessor.blockEntity as? SixNodeEntity)?.let { Eln.sixNodeBlock.hitSide(it, accessor.hitResult) }
        Eln.ghostManager.getGhost(coord)?.observatorCoordonate?.let { real ->
            coord = real
            side = (Eln.ghostManager.getObserver(real) as? SixNodeElement)?.side
        }
        return when (val node = NodeManager.instance!!.getNodeFromCoordonate(coord)) {
            is SixNode -> side?.let { format(node.getElement(it)?.getWaila()) } ?: emptyList()
            is TransparentNode -> node.element?.getWailaEntries()?.flatMap { entry ->
                if (entry.values.size == 1) listOf("${entry.label}: §f${entry.values.single()}")
                else listOf("${entry.label}:") + entry.values.map { "§f$it" }
            } ?: emptyList()
            else -> emptyList()
        }
    }

    override fun appendServerData(data: CompoundTag, accessor: BlockAccessor) {
        val block = accessor.block
        if (block !is SixNodeBlock && block !is TransparentNodeBlock && block !is GhostBlock) return
        val list = ListTag()
        for (line in linesFor(accessor)) list.add(StringTag.valueOf(line))
        if (!list.isEmpty()) data.put(KEY, list)
    }

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        val list = accessor.serverData.getList(KEY, Tag.TAG_STRING.toInt())
        for (i in 0 until list.size) tooltip.add(Component.literal(list.getString(i)))
    }
}
