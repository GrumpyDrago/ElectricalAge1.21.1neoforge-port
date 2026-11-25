package mods.eln.node.transparent

import mods.eln.registration.ElnBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.FakeSideInventory.Companion.instance
import mods.eln.misc.LRDU
import mods.eln.node.NodeBlockEntity
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.Level
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException

open class TransparentNodeEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) : NodeBlockEntity(type, pos, state), ISidedInventory {
    constructor(pos: BlockPos, state: BlockState) : this(ElnBlockEntities.transparentNode, pos, state)

    var elementRender: TransparentNodeElementRender? = null
    var elementRenderId: Short = 0

    override fun getCableRender(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        return if (elementRender == null) null else elementRender!!.getCableRenderSide(side, lrdu)
    }

    override fun serverPublishUnserialize(stream: DataInputStream) {
        super.serverPublishUnserialize(stream)
        try {
            val id = stream.readShort()
            if (id.toInt() == 0) {
                elementRenderId = 0.toShort()
                elementRender = null
            } else {
                if (id != elementRenderId) {
                    elementRenderId = id
                    val descriptor = Eln.transparentNodeItem.getDescriptor(id.toInt())
                    elementRender = descriptor!!.RenderClass.getConstructor(TransparentNodeEntity::class.java, TransparentNodeDescriptor::class.java).newInstance(this, descriptor) as TransparentNodeElementRender
                }
                elementRender!!.networkUnserialize(stream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
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

    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu? {
        val n = node as TransparentNode? ?: return null
        return n.newContainer(side, player)
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen? {
        return elementRender!!.newGuiDraw(side, player)
    }

    override fun preparePacketForServer(stream: DataOutputStream) {
        try {
            super.preparePacketForServer(stream)
            stream.writeShort(elementRenderId.toInt())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun sendPacketToServer(bos: ByteArrayOutputStream?) {
        super.sendPacketToServer(bos)
    }

    override fun cameraDrawOptimisation(): Boolean {
        return if (elementRender == null) super.cameraDrawOptimisation() else elementRender!!.cameraDrawOptimisation()
    }

    override fun unoptimizedRenderBoundingBox(): AABB {
        return if (elementRender == null) super.unoptimizedRenderBoundingBox() else elementRender!!.unoptimizedRenderBoundingBox()
    }

    override fun tileEntityNeighborSpawn() {
        if (elementRender != null) elementRender!!.notifyNeighborSpawn()
    }

    fun addCollisionBoxesToList(par5AxisAlignedBB: AABB, list: MutableList<AABB?>, blockCoord: Coordinate?) {
        val desc = if (worldObj.isClientSide) {
            if (elementRender == null) null else elementRender!!.transparentNodeDescriptor
        } else {
            val node = node as TransparentNode?
            node?.element?.transparentNodeDescriptor
        }
        val x: Int
        val y: Int
        val z: Int
        if (blockCoord != null) {
            x = blockCoord.x
            y = blockCoord.y
            z = blockCoord.z
        } else {
            x = xCoord
            y = yCoord
            z = zCoord
        }
        if (desc == null) {
            val bb = AABB(BlockPos(x, y, z))
            if (par5AxisAlignedBB.intersects(bb)) list.add(bb)
        } else {
            desc.addCollisionBoxesToList(par5AxisAlignedBB, list, worldObj, x, y, z)
        }
    }

    override fun serverPacketUnserialize(stream: DataInputStream) {
        super.serverPacketUnserialize(stream)
        if (elementRender != null) elementRender!!.serverPacketUnserialize(stream)
    }

    override val nodeUuid: String
        get() = Eln.transparentNodeBlock.nodeUuid

    override fun destructor() {
        if (elementRender != null) elementRender!!.destructor()
        super.destructor()
    }

    override fun clientRefresh(deltaT: Float) {
        if (elementRender != null) {
            elementRender!!.refresh(deltaT)
        }
    }

    override fun isProvidingWeakPower(side: Direction?): Int {
        return 0
    }

    open val sidedInventory: ISidedInventory
        get() {
            if (worldObj.isClientSide) {
                if (elementRender == null) return instance
                val i = elementRender!!.inventory
                if (i != null && i is ISidedInventory) {
                    return i
                }
            } else {
                val node = node
                if (node != null && node is TransparentNode) {
                    val i = node.getInventory(null)
                    if (i != null && i is ISidedInventory) {
                        return i
                    }
                }
            }
            return instance
        }

    override fun markDirty() {
        super<NodeBlockEntity>.setChanged()
    }

    override fun setChanged() {
        super<NodeBlockEntity>.setChanged()
    }

    override fun getSizeInventory(): Int {
        return sidedInventory.sizeInventory
    }

    override fun getStackInSlot(var1: Int): ItemStack? {
        return sidedInventory.getStackInSlot(var1)
    }

    override fun decrStackSize(var1: Int, var2: Int): ItemStack? {
        return sidedInventory.decrStackSize(var1, var2)
    }

    override fun getStackInSlotOnClosing(var1: Int): ItemStack? {
        return sidedInventory.getStackInSlotOnClosing(var1)
    }

    override fun setInventorySlotContents(var1: Int, var2: ItemStack?) {
        sidedInventory.setInventorySlotContents(var1, var2)
    }

    override fun getInventoryName(): String {
        return sidedInventory.inventoryName
    }

    override fun hasCustomInventoryName(): Boolean {
        return sidedInventory.hasCustomInventoryName()
    }

    override fun getInventoryStackLimit(): Int {
        return sidedInventory.inventoryStackLimit
    }

    override fun isUseableByPlayer(var1: Player): Boolean {
        return sidedInventory.isUseableByPlayer(var1)
    }

    override fun openInventory() {
        sidedInventory.openInventory()
    }

    override fun closeInventory() {
        sidedInventory.closeInventory()
    }

    override fun isItemValidForSlot(var1: Int, var2: ItemStack): Boolean {
        return sidedInventory.isItemValidForSlot(var1, var2)
    }

    override fun getAccessibleSlotsFromSide(var1: Int): IntArray {
        return sidedInventory.getAccessibleSlotsFromSide(var1)
    }

    override fun canInsertItem(var1: Int, var2: ItemStack, var3: Int): Boolean {
        return sidedInventory.canInsertItem(var1, var2, var3)
    }

    override fun canExtractItem(var1: Int, var2: ItemStack, var3: Int): Boolean {
        return sidedInventory.canExtractItem(var1, var2, var3)
    }
}
