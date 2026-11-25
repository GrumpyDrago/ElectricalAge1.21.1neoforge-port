package mods.eln.node.six

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.ServerKeyHandler
import mods.eln.item.IConfigurable
import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromInt
import mods.eln.misc.LRDU
import mods.eln.misc.LRDUCubeMask
import mods.eln.misc.Utils.isCreative
import mods.eln.misc.Utils.newNbtTagCompund
import mods.eln.misc.Utils.println
import mods.eln.node.ISixNodeCache
import mods.eln.node.Node
import mods.eln.node.NodeConnectionEndpoint
import mods.eln.node.NodeConnection
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.ThermalConnection
import mods.eln.sim.ThermalLoad
import mods.eln.sixnode.lampsocket.LampSocketDescriptor
import mods.eln.sixnode.lampsocket.LampSocketElement
import net.minecraft.world.level.block.Block
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.items.IItemHandler
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.*

class SixNode : Node() {
    @JvmField
    var sideElementList = arrayOfNulls<SixNodeElement>(6)
    @JvmField
    var sideElementIdList = IntArray(6)
    var internalElectricalConnectionList = ArrayList<ElectricalConnection>(1)
    var internalThermalConnectionList = ArrayList<ThermalConnection>(1)
    @JvmField
    var sixNodeCacheBlock = Blocks.AIR
    @JvmField
    var lrduElementMask = LRDUCubeMask()
    fun getElement(side: Direction): SixNodeElement? {
        return sideElementList[side.int]
    }

    override fun canConnectRedstone(): Boolean {
        for (element in sideElementList) {
            if (element != null && element.canConnectRedstone()) {
                return true
            }
        }
        return false
    }

    override fun isProvidingWeakPower(side: Direction?): Int {
        var value = 0
        for (element in sideElementList) {
            if (element != null) {
                val eValue = element.isProvidingWeakPower
                if (eValue > value) value = eValue
            }
        }
        return value
    }

    fun createSubBlock(itemStack: ItemStack, direction: Direction, player: Player?): Boolean {
        val descriptor = Eln.sixNodeItem.getDescriptor(itemStack)
        if (sideElementList[direction.int] != null) return false
        try {
            sideElementIdList[direction.int] = itemStack.itemDamage //Je sais c'est moche !
            sideElementList[direction.int] = descriptor!!.ElementClass.getConstructor(SixNode::class.java, Direction::class.java, SixNodeDescriptor::class.java).newInstance(this, direction, descriptor) as SixNodeElement
            if (descriptor is LampSocketDescriptor) {
                LampSocketElement.placingPlayerIsCreative = player is ServerPlayer && isCreative(player)
            }
            sideElementIdList[direction.int] = 0
            disconnect()
            sideElementList[direction.int]!!.front = descriptor.getFrontFromPlace(direction, player!!)!!
            sideElementList[direction.int]!!.initialize()
            sideElementIdList[direction.int] = itemStack.itemDamage
            connect()
            println("createSubBlock " + sideElementIdList[direction.int] + " " + direction)
            needPublish = true
            itemSourcesChanged(sideElementList[direction.int])
            return true
        } catch (e: InstantiationException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return false
    }

    fun playerAskToBreakSubBlock(entityPlayer: ServerPlayer?, direction: Direction): Boolean {
        if (sideElementList[direction.int] == null) return deleteSubBlock(entityPlayer, direction)
        return if (sideElementList[direction.int]!!.playerAskToBreak()) {
            deleteSubBlock(entityPlayer, direction)
        } else {
            false
        }
    }

    fun deleteSubBlock(entityPlayer: ServerPlayer?, direction: Direction): Boolean {
        if (sideElementList[direction.int] == null) return false
        println("deleteSubBlock  $direction")
        disconnect()
        val e = sideElementList[direction.int]
        sideElementList[direction.int] = null
        sideElementIdList[direction.int] = 0
        e!!.destroy(entityPlayer)
        connect()
        recalculateLightValue()
        needPublish = true
        itemSourcesChanged(e)
        return true
    }

    /** For hoppers and pipes, if there is something for them to take. */
    val itemHandler: IItemHandler?
        get() = if (sideElementList.any { it is ItemSource }) ItemSourceHandler(this) else null

    private fun itemSourcesChanged(element: SixNodeElement?) {
        if (element !is ItemSource) return
        val level = coordinate.world() as? ServerLevel ?: return
        level.invalidateCapabilities(coordinate.blockPos)
        // Pipes look again for what is next to them.
        level.updateNeighborsAt(coordinate.blockPos, Eln.sixNodeBlock)
    }

    val ifSideRemain: Boolean
        get() {
            for (sideElement in sideElementList) {
                if (sideElement != null) return true
            }
            return false
        }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt.getCompound("node"))
        sixNodeCacheBlock = if (nbt.contains("cacheBlock")) BuiltInRegistries.BLOCK.get(rl(nbt.getString("cacheBlock"))) else Blocks.AIR
        var idx: Int
        idx = 0
        while (idx < 6) {
            val sideElementId = nbt.getShort("EID$idx")
            if (sideElementId.toInt() == 0) {
                sideElementList[idx] = null
                sideElementIdList[idx] = 0
            } else {
                try {
                    val descriptor = Eln.sixNodeItem.getDescriptor(sideElementId.toInt())
                    sideElementIdList[idx] = sideElementId.toInt()
                    sideElementList[idx] = descriptor!!.ElementClass.getConstructor(SixNode::class.java, Direction::class.java, SixNodeDescriptor::class.java).newInstance(this, fromInt(idx), descriptor) as SixNodeElement
                    sideElementList[idx]!!.readFromNBT(nbt.getCompound("ED$idx"))
                    sideElementList[idx]!!.initialize()
                } catch (e: InstantiationException) {
                    e.printStackTrace()
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                } catch (e: NoSuchMethodException) {
                    e.printStackTrace()
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
            idx++
        }
        initializeFromNBT()
    }

    override fun nodeAutoSave(): Boolean {
        return false
    }

    override fun writeToNBT(nbt: CompoundTag) {
        var idx = 0
        if (sixNodeCacheBlock !== Blocks.AIR) nbt.putString("cacheBlock", BuiltInRegistries.BLOCK.getKey(sixNodeCacheBlock).toString())
        for (sideElement in sideElementList) {
            if (sideElement == null) {
                nbt.putShort("EID$idx", 0.toShort())
            } else {
                nbt.putShort("EID$idx", sideElementIdList[idx].toShort())
                sideElement.writeToNBT(newNbtTagCompund(nbt, "ED$idx"))
            }
            idx++
        }
        val nodeNbt = CompoundTag()
        super.writeToNBT(nodeNbt)
        nbt.put("node", nodeNbt)
    }

    fun getSideEnable(direction: Direction): Boolean {
        return sideElementList[direction.int] != null
    }

    override fun getElectricalLoad(side: Direction, lrdu: LRDU, mask: Int): ElectricalLoad? {
        val elementSide = side.applyLRDU(lrdu)
        val element = sideElementList[elementSide.int] ?: return null
        return element.getElectricalLoad(elementSide.getLRDUGoingTo(side)!!, mask)
    }

    override fun getElectricalLoad(side: Direction, lrdu: LRDU, mask: Int, remoteEndpoint: NodeConnectionEndpoint): ElectricalLoad? {
        val elementSide = side.applyLRDU(lrdu)
        val element = sideElementList[elementSide.int] ?: return null
        return element.getElectricalLoad(elementSide.getLRDUGoingTo(side)!!, mask, remoteEndpoint)
    }

    override fun getThermalLoad(side: Direction, lrdu: LRDU, mask: Int): ThermalLoad? {
        val elementSide = side.applyLRDU(lrdu)
        val element = sideElementList[elementSide.int] ?: return null
        return element.getThermalLoad(elementSide.getLRDUGoingTo(side)!!, mask)
    }

    override fun getSideConnectionMask(side: Direction, lrdu: LRDU): Int {
        val elementSide = side.applyLRDU(lrdu)
        val element = sideElementList[elementSide.int] ?: return 0
        return element.getConnectionMask(elementSide.getLRDUGoingTo(side)!!)
    }

    override fun getConnectionEndpoint(side: Direction, lrdu: LRDU): NodeConnectionEndpoint {
        val elementSide = side.applyLRDU(lrdu)
        val elementLrdu = elementSide.getLRDUGoingTo(side)
        val element = sideElementList[elementSide.int]
        return NodeConnectionEndpoint(this, side, lrdu, element, elementSide, elementLrdu)
    }

    override fun multiMeterString(side: Direction): String {
        val element = sideElementList[side.int] ?: return ""
        return element.multiMeterString()
    }

    override fun thermoMeterString(side: Direction): String {
        val element = sideElementList[side.int] ?: return ""
        return element.thermoMeterString()
    }

    override fun readConfigTool(side: Direction?, tag: CompoundTag?, invoker: Player?): Boolean {
        val element = sideElementList[side!!.int]
        if (element is IConfigurable) {
            (element as IConfigurable).readConfigTool(tag, invoker)
            return true
        }
        return false
    }

    override fun writeConfigTool(side: Direction?, tag: CompoundTag?, invoker: Player?): Boolean {
        val element = sideElementList[side!!.int]
        if (element is IConfigurable) {
            (element as IConfigurable).writeConfigTool(tag, invoker)
            return true
        }
        return false
    }

    override fun publishSerialize(stream: DataOutputStream) {
        super.publishSerialize(stream)
        try {
            var idx = 0
            stream.writeInt(blockId(sixNodeCacheBlock))
            for (sideElement in sideElementList) {
                if (sideElement == null) {
                    stream.writeShort((0).toInt())
                } else {
                    stream.writeShort(sideElementIdList[idx])
                    sideElement.networkSerialize(stream)
                }
                idx++
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun preparePacketForClient(stream: DataOutputStream, e: SixNodeElement) {
        try {
            super.preparePacketForClient(stream)
            val side = e.side.int
            stream.writeByte(side)
            stream.writeShort(e.sixNodeElementDescriptor.parentItemDamage)
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
    }

    override fun initializeFromThat(front: Direction, entityLiving: LivingEntity?,
                                    itemStack: ItemStack?) {
        neighborBlockRead()
    }

    override fun initializeFromNBT() {
        connect()
    }

    override fun connectInit() {
        super.connectInit()
        internalElectricalConnectionList.clear()
        internalThermalConnectionList.clear()
        lrduElementMask.clear()
    }

    override fun connectJob() {
        super.connectJob()
        for (element in sideElementList) {
            element?.connectJob()
        }

        //INTERNAL
        run {
            val side = Direction.YN
            val element = sideElementList[side.int]
            if (element != null) {
                for (lrdu in LRDU.values()) {
                    val otherSide = side.applyLRDU(lrdu)
                    val otherElement = sideElementList[otherSide.int]
                    if (otherElement != null) {
                        val otherLRDU = otherSide.getLRDUGoingTo(side)!!
                        tryConnectTwoInternalElement(side, element, lrdu, otherSide, otherElement, otherLRDU)
                    }
                }
            }
        }
        run {
            val side = Direction.YP
            val element = sideElementList[side.int]
            if (element != null) {
                for (lrdu in LRDU.values()) {
                    val otherSide = side.applyLRDU(lrdu)
                    val otherElement = sideElementList[otherSide.int]
                    if (otherElement != null) {
                        val otherLRDU = otherSide.getLRDUGoingTo(side)!!
                        tryConnectTwoInternalElement(side, element, lrdu, otherSide, otherElement, otherLRDU)
                    }
                }
            }
        }
        run {
            var side = Direction.XN
            for (idx in 0..3) {
                val otherSide = side.right()
                val element = sideElementList[side.int]
                val otherElement = sideElementList[otherSide.int]
                if (element != null && otherElement != null) {
                    tryConnectTwoInternalElement(side, element, LRDU.Right, otherSide, otherElement, LRDU.Left)
                }
                side = otherSide
            }
        }
    }

    override fun disconnectJob() {
        super.disconnectJob()
        for (element in sideElementList) {
            element?.disconnectJob()
        }
        Eln.simulator.removeAllElectricalConnection(internalElectricalConnectionList)
        Eln.simulator.removeAllThermalConnection(internalThermalConnectionList)
    }

    fun tryConnectTwoInternalElement(side: Direction, element: SixNodeElement, lrdu: LRDU, otherSide: Direction, otherElement: SixNodeElement, otherLRDU: LRDU) {
        println("SixNode.tCTIE:")
        val mskThis = element.getConnectionMask(lrdu)
        val mskOther = otherElement.getConnectionMask(otherLRDU)
        if (compareConnectionMask(mskThis, mskOther)) {
            println("\tConnection OK.")
            lrduElementMask[side, lrdu] = true
            lrduElementMask[otherSide, otherLRDU] = true
            val nodeConnection = NodeConnection(this, side, lrdu, this, otherSide, otherLRDU)
            nodeConnectionList.add(nodeConnection)
            element.newConnectionAt(nodeConnection, false)
            otherElement.newConnectionAt(nodeConnection, true)
            var eLoad: ElectricalLoad?
            if (element.getElectricalLoad(lrdu, mskOther, nodeConnection.endpoint(false)).also { eLoad = it } != null) {
                val otherELoad = otherElement.getElectricalLoad(otherLRDU, mskThis, nodeConnection.endpoint(true))
                if (otherELoad != null) {
                    val eCon = ElectricalConnection(eLoad, otherELoad)
                    Eln.simulator.addElectricalComponent(eCon)
                    internalElectricalConnectionList.add(eCon)
                    nodeConnection.addConnection(eCon)
                }
            }
            var tLoad: ThermalLoad?
            if (getThermalLoad(side, lrdu, mskOther).also { tLoad = it } != null) {
                val otherTLoad = element.getThermalLoad(otherLRDU, mskThis)
                if (otherTLoad != null) {
                    val tCon = ThermalConnection(tLoad, otherTLoad)
                    Eln.simulator.addThermalConnection(tCon)
                    internalThermalConnectionList.add(tCon)
                    nodeConnection.addConnection(tCon)
                }
            }
        }
    }

    override fun newConnectionAt(connection: NodeConnection?, isA: Boolean) {
        val side = if (isA) connection!!.dir1 else connection!!.dir2
        val lrdu = if (isA) connection.lrdu1 else connection.lrdu2
        val elementSide = side.applyLRDU(lrdu)
        val element = sideElementList[elementSide.int]
        if (element == null) {
            println("sixnode newConnectionAt error")
            while (true);
        }
        lrduElementMask[elementSide, elementSide.getLRDUGoingTo(side)!!] = true
        element!!.newConnectionAt(connection, isA)
    }

    override fun externalDisconnect(side: Direction?, lrdu: LRDU?) {
        val elementSide = side!!.applyLRDU(lrdu!!)
        val element = sideElementList[elementSide.int]
        if (element == null) {
            println("sixnode newConnectionAt error")
            while (true);
        }
        lrduElementMask[elementSide, elementSide.getLRDUGoingTo(side)!!] = false
    }

    override fun onBlockActivated(entityPlayer: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        return if (sixNodeCacheBlock !== Blocks.AIR) {
            false
        } else {
            val stack = entityPlayer.currentEquippedItem
            var b = Blocks.AIR
            if (stack != null) b = Block.byItem(stack.item)
            var isWrenchReplacingBlock = false
            if (ServerKeyHandler.get(ServerKeyHandler.WRENCH) && stack != null) {
                for (a in sixNodeCacheList) {
                    if (a.accept(stack)) {
                        isWrenchReplacingBlock = true
                        changeCover(b)
                        break
                    }
                }
            }

            if (isWrenchReplacingBlock) {
                if (!isCreative((entityPlayer as ServerPlayer))) stack!!.shrink(1)
                true
            } else {
                val element = sideElementList[side.int] ?: return false
                if (element.onBlockActivated(entityPlayer, side, vx, vy, vz)) true else super.onBlockActivated(entityPlayer, side, vx, vy, vz)
            }
        }
    }

    override fun hasGui(side: Direction): Boolean {
        return if (sideElementList[side.int] == null) false else sideElementList[side.int]!!.hasGui()
    }

    fun getInventory(side: Direction): IInventory? {
        return if (sideElementList[side.int] == null) null else sideElementList[side.int]!!.inventory
    }

    fun newContainer(side: Direction, player: Player): AbstractContainerMenu? {
        return if (sideElementList[side.int] == null) null else sideElementList[side.int]!!.newContainer(side, player)
    }

    /** Replaces the block that hides the elements; air for none. */
    fun changeCover(block: Block) {
        sixNodeCacheBlock = block
        needPublish = true
        val world = coordinate.world()
        val pos = BlockPos(coordinate.x, coordinate.y, coordinate.z)
        val state = world.getBlockState(pos)
        if (state.hasProperty(SixNodeBlock.COVERED)) {
            world.setBlock(pos, state.setValue(SixNodeBlock.COVERED, block !== Blocks.AIR), 3)
        }
    }

    fun physicalSelfDestructionExplosionStrength(): Float {
        return 1.0f
    }

    fun recalculateLightValue() {
        var light = 0
        for (element in sideElementList) {
            if (element == null) continue
            val eLight = element.lightValue
            if (eLight > light) light = eLight
        }
        lightValue = light
    }

    override fun networkUnserialize(stream: DataInputStream, player: ServerPlayer?) {
        super.networkUnserialize(stream, player)
        val side: Direction?
        try {
            side = fromInt(stream.readByte().toInt())
            if (side != null && sideElementIdList[side.int] == stream.readShort().toInt()) {
                sideElementList[side.int]!!.networkUnserialize(stream, player)
            } else {
                println("sixnode unserialize miss")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun hasVolume(): Boolean {
        for (element in sideElementList) {
            if (element != null && element.sixNodeElementDescriptor.hasVolume()) return true
        }
        return false
    }

    override val nodeUuid: String
        get() = Eln.sixNodeBlock.nodeUuid

    override fun globalBoot() {
        super.globalBoot()
        for (e in sideElementList) {
            if (e == null) continue
            e.globalBoot()
        }
    }

    override fun unload() {
        super.unload()
        for (e in sideElementList) {
            if (e == null) continue
            e.unload()
        }
    }

    companion object {
        @JvmField
        val sixNodeCacheList = ArrayList<ISixNodeCache>()
    }

    init {
        for (idx in 0..5) {
            sideElementList[idx] = null
            sideElementIdList[idx] = 0
        }
        lrduElementMask.clear()
    }
}
