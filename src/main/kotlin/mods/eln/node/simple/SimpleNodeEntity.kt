package mods.eln.node.simple

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromInt
import mods.eln.misc.Utils.fatal
import mods.eln.misc.Utils.println
import mods.eln.node.INodeEntity
import mods.eln.node.NodeEntityClientSender
import mods.eln.node.NodeManager
import mods.eln.node.simple.DescriptorManager.get
import mods.eln.server.DelayedBlockRemove.Companion.add
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import mods.eln.node.NodeSync
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import java.io.DataInputStream
import java.io.IOException

abstract class SimpleNodeEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState, override val nodeUuid: String) : BlockEntity(type, pos, state), INodeEntity {
    open var node: SimpleNode? = null
        get() {
            if (worldObj.isClientSide) {
                fatal()
                return null
            }
            if (field == null) {
                field = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(xCoord, yCoord, zCoord, worldObj)) as SimpleNode?
                if (field == null) {
                    add(Coordinate(xCoord, yCoord, zCoord, worldObj))
                    return null
                }
            }
            return field
        }

    //***************** Wrapping **************************
    /*
	public void onBlockPlacedBy(Direction front, EntityLivingBase entityLiving, int metadata) {
	
	}
*/
    fun onBlockAdded() {
        /*if (!worldObj.isClientSide){
			if (getNode() == null) {
				worldObj.setBlockToAir(xCoord, yCoord, zCoord);
			}
		}*/
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

    // client only
    fun destructor() {}
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
        return true
    }

    fun onNeighborBlockChange() {
        if (!worldObj.isClientSide) {
            if (node == null) return
            node!!.onNeighborBlockChange()
        }
    }

    //***************** Descriptor **************************
    val descriptor: Any?
        get() {
            val b = blockState.block as SimpleNodeBlock
            return get<Any>(b.descriptorKey)
        }

    //***************** Network **************************
    var front: Direction? = null
    override fun serverPublishUnserialize(stream: DataInputStream) {
        try {
            if (front !== fromInt(stream.readByte().toInt()).also { front = it }) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun serverPacketUnserialize(stream: DataInputStream) {}
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

    open lateinit var sender: NodeEntityClientSender

    init {
        println("NodeUUID: $nodeUuid")
        sender = NodeEntityClientSender(this, nodeUuid)
    }

    //*********************** GUI ***************************
    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu? {
        return null
    }

    @OnlyIn(Dist.CLIENT)
    override fun newGuiDraw(side: Direction, player: Player): Screen? {
        return null
    }
}
