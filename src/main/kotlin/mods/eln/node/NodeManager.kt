package mods.eln.node

import mods.eln.compat.*

import mods.eln.misc.Coordinate
import mods.eln.misc.Utils.getTags
import mods.eln.misc.Utils.println
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeElement
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import java.util.*

class NodeManager {
    val nodeArray: HashMap<Coordinate, NodeBase>
    val nodes: ArrayList<NodeBase>
    val nodeList: Collection<NodeBase>
        get() = nodeArray.values

    fun addNode(node: NodeBase) {
        // nodeArray.add(node);
        val old = nodeArray.put(node.coordinate, node)
        if (old != null) {
            nodes.remove(old)
        }
        nodes.add(node)
        if (node.needPublish) publishLater(node)
        // println("NodeManager has " + nodeArray.size + "node")
        // nodeArray.put(new NodeIdentifier(node), node);
    }

    fun removeNode(node: NodeBase?) {
        if (node == null) return
        nodeArray.remove(node.coordinate)
        nodes.remove(node)
        println("NodeManager has " + nodeArray.size + "node")
    }

    fun removeCoordonate(c: Coordinate?) {
        // nodeArray.remove(node);
        val n = nodeArray.remove(c)
        if (n != null) nodes.remove(n)
        println("NodeManager has " + nodeArray.size + "node")
    }

    fun getNodeFromCoordonate(nodeCoordinate: Coordinate?): NodeBase? {
        return nodeArray[nodeCoordinate]
    }

    fun getTransparentNodeFromCoordinate(coord: Coordinate?): TransparentNodeElement? {
        val base = getNodeFromCoordonate(coord)
        if (base is TransparentNode) {
            return base.element
        }
        return null
    }

    var rand = Random()
    val randomNode: NodeBase?
        get() = if (nodes.isEmpty()) null else nodes[rand.nextInt(nodes.size)]

    fun loadFromNbt(nbt: CompoundTag?) {
        val addedNode: MutableList<NodeBase> = ArrayList()
        for (o in getTags(nbt!!)) {
            val tag = o
            val nodeClass = UUIDToClass[tag.getString("tag")]
            try {
                val node = nodeClass!!.getConstructor().newInstance() as NodeBase
                node.readFromNBT(tag)
                addNode(node)
                addedNode.add(node)
                node.initializeFromNBT()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        for (n in addedNode) {
            n.globalBoot()
        }
    }

    fun saveToNbt(nbt: CompoundTag, dim: Int) {
        var nodeCounter = 0
        val nodesCopy: MutableList<NodeBase> = ArrayList()
        nodesCopy.addAll(nodes)
        for (node in nodesCopy) {
            try {
                if (node.mustBeSaved() == false) continue
                if (dim != Int.MIN_VALUE && node.coordinate.dimension != dim) continue
                val nbtNode = CompoundTag()
                nbtNode.putString("tag", node.nodeUuid)
                node.writeToNBT(nbtNode)
                nbt.put("n" + nodeCounter++, nbtNode)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var publishQueue = ArrayList<NodeBase>()
    private var publishing = ArrayList<NodeBase>()

    internal fun publishLater(node: NodeBase) {
        publishQueue.add(node)
    }

    /** Sends the nodes that have changed to the players that see them. */
    fun publishChanges() {
        val batch = publishQueue
        publishQueue = publishing
        publishing = batch
        for (node in batch) {
            // Nodes that are not registered (yet) get queued when they are.
            if (node.needPublish && nodeArray[node.coordinate] === node) node.publishToAllPlayer()
        }
        batch.clear()
    }

    fun clear() {
        publishQueue.clear()
        nodes.clear()
        nodeArray.clear()
    }

    fun unload(dimensionId: Int) {
        val unloaded = nodes.filter { it.coordinate.dimension == dimensionId }
        for (n in unloaded) {
            n.unload()
            nodeArray.remove(n.coordinate)
        }
        nodes.removeAll { it.coordinate.dimension == dimensionId }
    }

    companion object {
        @JvmField
        var instance: NodeManager? = null
        val UUIDToClass = HashMap<String, Class<*>>()
        @JvmStatic
        fun registerUuid(uuid: String, classType: Class<*>) {
            UUIDToClass[uuid] = classType
        }
    }

    // private ArrayList<Node> nodeArray = new ArrayList<Node>();
    init {
        nodeArray = HashMap()
        nodes = ArrayList()
        instance = this
    }
}
