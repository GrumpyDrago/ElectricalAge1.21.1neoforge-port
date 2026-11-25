package mods.eln.node

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.misc.Utils.fatal
import mods.eln.misc.Utils.notifyNeighbor
import mods.eln.misc.Utils.println
import mods.eln.misc.UtilsClient

import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.LightLayer
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class NodeBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) : BlockEntity(type, pos, state), ITileEntitySpawnClient, INodeEntity {
    val block: NodeBlock
        get() = blockState.block as NodeBlock
    var redstone = false
    var lastLight = 0xFF
    var firstUnserialize = true
    override fun serverPublishUnserialize(stream: DataInputStream) {
        var light = 0
        try {
            if (firstUnserialize) {
                firstUnserialize = false
                notifyNeighbor(this)
            }
            val b = stream.readByte()
            light = b.toInt() and 0xF
            val newRedstone = b.toInt() and 0x10 != 0
            if (redstone != newRedstone) {
                redstone = newRedstone
                worldObj.notifyBlockChange(xCoord, yCoord, zCoord, blockState.block)
            } else {
                redstone = newRedstone
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        lastLight = light
    }

    override fun serverPacketUnserialize(stream: DataInputStream) {}

    abstract fun isProvidingWeakPower(side: Direction?): Int

    var internalNode: Node? = null

    val node: Node?
        get() {
            if (worldObj.isClientSide) {
                fatal()
            }
            if (internalNode == null) {
                val nodeFromCoordonate = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(xCoord, yCoord, zCoord, worldObj))
                if (nodeFromCoordonate is Node) {
                    internalNode = nodeFromCoordonate
                } else if (nodeFromCoordonate != null) {
                    Utils.println("WARN: getNode found non-Node ${nodeFromCoordonate.javaClass.simpleName} at ${Coordinate(xCoord, yCoord, zCoord, worldObj)}")
                }
            }
            return internalNode
        }

    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu? {
        return null
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen? {
        // Debugging tip: If the GUI isn't working, but you can see it trying to open in the client debug log,
        // check that you have the renderer (client) class set correctly in the descriptor
        return null
    }

    fun renderBox(): AABB {
        return if (cameraDrawOptimisation()) localRenderBoundingBox() else unoptimizedRenderBoundingBox()
    }

    open fun cameraDrawOptimisation(): Boolean {
        return true
    }

    protected fun localRenderBoundingBox(): AABB {
        return AABB(
            (xCoord - 1).toDouble(),
            (yCoord - 1).toDouble(),
            (zCoord - 1).toDouble(),
            (xCoord + 2).toDouble(),
            (yCoord + 2).toDouble(),
            (zCoord + 2).toDouble()
        )
    }

    open fun unoptimizedRenderBoundingBox(): AABB {
        return localRenderBoundingBox()
    }

    val lightValue: Int
        get() = if (worldObj.isClientSide) {
            if (lastLight == 0xFF) {
                0
            } else lastLight
        } else {
            node?.lightValue?: 0
        }

    override fun onLoad() {
        super.onLoad()
        if (worldObj.isClientSide) clientList.add(this)
    }

    fun onBlockAdded() {
        if (!worldObj.isClientSide && node == null) {
            worldObj.setBlockToAir(xCoord, yCoord, zCoord)
        }
    }

    fun onBreakBlock() {
        if (!worldObj.isClientSide) {
            if (node == null) return
            node!!.onBreakBlock()
        }
    }

    override fun onChunkUnloaded() {
        super.onChunkUnloaded()
        if (worldObj.isClientSide) {
            destructor()
        }
    }

    //client only
    open fun destructor() {
        clientList.remove(this)
    }

    override fun setRemoved() {
        if (level?.isClientSide == true) {
            destructor()
        }
        super.setRemoved()
    }

    fun onBlockActivated(entityPlayer: Player?, side: Direction?, vx: Float, vy: Float, vz: Float): Boolean {
        if (!worldObj.isClientSide) {
            if (node == null) return false
            node!!.onBlockActivated(entityPlayer!!, side!!, vx, vy, vz)
            return true
        }
        //if(entityPlayer.getCurrentEquippedItem().getItem() instanceof ItemBlock)
        run { return true }
        //return true;
    }

    fun onNeighborBlockChange() {
        if (!worldObj.isClientSide) {
            if (node == null) return
            node!!.onNeighborBlockChange()
        }
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = super.getUpdateTag(registries)
        NodeSync.write(tag, node)
        return tag
    }

    override fun handleUpdateTag(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.handleUpdateTag(tag, registries)
        NodeSync.read(tag, this)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? = ClientboundBlockEntityDataPacket.create(this)

    override fun onDataPacket(net: Connection, packet: ClientboundBlockEntityDataPacket, registries: HolderLookup.Provider) {
        NodeSync.read(packet.tag, this)
    }

    open fun preparePacketForServer(stream: DataOutputStream) {
        try {
            stream.writeByte(Eln.packetPublishForNode.toInt())
            stream.writeInt(xCoord)
            stream.writeInt(yCoord)
            stream.writeInt(zCoord)
            stream.writeInt(worldObj.dimensionId)
            stream.writeUTF(nodeUuid)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    open fun sendPacketToServer(bos: ByteArrayOutputStream?) {
        UtilsClient.sendPacketToServer(bos!!)
    }

    open fun getCableRender(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        return null
    }

    fun getAdjacentCableRender(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        val lookupKey = CableRenderLookupKey(worldObj.dimensionId, xCoord, yCoord, zCoord, side, lrdu)
        val activeLookups = adjacentCableRenderLookups.get()
        if (!activeLookups.add(lookupKey)) return null

        try {
            findWrappedAdjacentCableRender(side, lrdu)?.let { return it }
            return findDirectAdjacentCableRender(side, lrdu)
        } finally {
            activeLookups.remove(lookupKey)
            if (activeLookups.isEmpty()) {
                adjacentCableRenderLookups.remove()
            }
        }
    }

    private fun findWrappedAdjacentCableRender(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        val emptyBlockCoord = intArrayOf(xCoord, yCoord, zCoord)
        side.applyTo(emptyBlockCoord, 1)
        val block = worldObj.getBlock(emptyBlockCoord[0], emptyBlockCoord[1], emptyBlockCoord[2])
        if (!NodeBase.isBlockWrappable(block, worldObj, xCoord, yCoord, zCoord)) return null

        val elementSide = side.applyLRDU(lrdu)
        val otherBlockCoord = intArrayOf(emptyBlockCoord[0], emptyBlockCoord[1], emptyBlockCoord[2])
        elementSide.applyTo(otherBlockCoord, 1)

        val tileEntity = worldObj.getTileEntity(otherBlockCoord[0], otherBlockCoord[1], otherBlockCoord[2]) as? NodeBlockEntity
            ?: return null
        val otherDirection = elementSide.inverse
        val otherLRDU = otherDirection.getLRDUGoingTo(side)?.inverse() ?: return null
        return tileEntity.getCableRender(otherDirection, otherLRDU)
    }

    private fun findDirectAdjacentCableRender(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        val otherBlockCoord = intArrayOf(xCoord, yCoord, zCoord)
        side.applyTo(otherBlockCoord, 1)
        val tileEntity = worldObj.getTileEntity(otherBlockCoord[0], otherBlockCoord[1], otherBlockCoord[2]) as? NodeBlockEntity
            ?: return null
        return tileEntity.getCableRender(side.inverse, lrdu.inverseIfLR())
    }

    open fun getCableDry(side: Direction?, lrdu: LRDU?): Int {
        return 0
    }

    fun canConnectRedstone(@Suppress("UNUSED_PARAMETER") xn: Direction?): Boolean {
        return if (worldObj.isClientSide) redstone else {
            if (node == null) false else node!!.canConnectRedstone()
        }
    }

    open fun clientRefresh(deltaT: Float) {}

    private data class CableRenderLookupKey(
        val dimension: Int,
        val x: Int,
        val y: Int,
        val z: Int,
        val side: Direction,
        val lrdu: LRDU
    )

    companion object {
        private val adjacentCableRenderLookups = object : ThreadLocal<MutableSet<CableRenderLookupKey>>() {
            override fun initialValue(): MutableSet<CableRenderLookupKey> = HashSet()
        }

        @JvmField
        val clientList: MutableSet<NodeBlockEntity> = ConcurrentHashMap.newKeySet()
        fun getEntity(x: Int, y: Int, z: Int): NodeBlockEntity? = ClientHooks.level()?.getTileEntity(x, y, z) as? NodeBlockEntity
    }
}
