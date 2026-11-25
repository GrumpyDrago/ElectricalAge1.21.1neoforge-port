package mods.eln.integration

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeElement
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.TransparentNode
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult

/** What a player looks at, for overlays. Only known to servers. */
class NodeInfo(
    /** As an item, or nothing if the block is not a part of any node. */
    val stack: ItemStack,
    val lines: List<String>
) {
    companion object {
        private val NONE = NodeInfo(ItemStack.EMPTY, emptyList())

        fun of(level: Level, hit: BlockHitResult): NodeInfo {
            val pos = hit.blockPos
            var coordinate = Coordinate(pos.x, pos.y, pos.z, level)
            var side: Direction? = (level.getBlockEntity(pos) as? SixNodeEntity)?.let { Eln.sixNodeBlock.hitSide(it, hit) }
            Eln.ghostManager.getGhost(coordinate)?.observatorCoordonate?.let { real ->
                coordinate = real
                side = (Eln.ghostManager.getObserver(real) as? SixNodeElement)?.side
            }
            return when (val node = NodeManager.instance!!.getNodeFromCoordonate(coordinate)) {
                is SixNode -> {
                    val element = side?.let { node.getElement(it) } ?: return NONE
                    val lines = element.getWaila()?.flatMap { (label, value) -> format(label, value.split('\n')) }
                    NodeInfo(element.sixNodeElementDescriptor.newItemStack(1), lines ?: emptyList())
                }
                is TransparentNode -> {
                    val element = node.element ?: return NONE
                    NodeInfo(element.transparentNodeDescriptor.newItemStack(1), element.getWailaEntries().flatMap { format(it.label, it.values) })
                }
                else -> NONE
            }
        }

        /** Some things have nothing to say, and some labels come with a colon. */
        fun format(label: String, values: List<String>): List<String> {
            val some = values.filter { it.isNotBlank() }
            val title = label.trim().removeSuffix(":")
            return when (some.size) {
                0 -> emptyList()
                1 -> listOf("$title: §f${some[0].trim()}")
                else -> listOf("$title:") + some.map { "§f" + it.trimEnd() }
            }
        }
    }
}
