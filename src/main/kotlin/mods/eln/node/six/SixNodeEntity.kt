@file:Suppress("NAME_SHADOWING")
package mods.eln.node.six

import mods.eln.registration.ElnBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromInt
import mods.eln.misc.LRDU
import mods.eln.misc.Utils.println
import mods.eln.node.NodeBlockEntity
import net.minecraft.world.level.block.Block
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.Level
import java.io.DataInputStream
import java.io.IOException

class SixNodeEntity(pos: BlockPos, state: BlockState) : NodeBlockEntity(ElnBlockEntities.sixNode, pos, state) {
    @JvmField
    var elementRenderList = arrayOfNulls<SixNodeElementRender>(6)
    @JvmField
    var elementRenderIdList = ShortArray(6)
    var sixNodeCacheBlock = Blocks.AIR

    val isCovered: Boolean
        get() = sixNodeCacheBlock !== Blocks.AIR

    fun coverState(): BlockState = sixNodeCacheBlock.defaultBlockState()

    /** Bit n is set when side n holds an element. */
    fun sideMask(): Int {
        var mask = 0
        val level = level ?: return 0
        if (level.isClientSide) {
            for (i in 0..5) if (elementRenderList[i] != null) mask = mask or (1 shl i)
        } else {
            val node = node as? SixNode ?: return 0
            for (i in 0..5) if (node.sideElementList[i] != null) mask = mask or (1 shl i)
        }
        return mask
    }

    override fun serverPublishUnserialize(stream: DataInputStream) {
        super.serverPublishUnserialize(stream)
        try {
            sixNodeCacheBlock = blockById(stream.readInt())
            var idx: Int
            idx = 0
            while (idx < 6) {
                val id = stream.readShort()
                if (id.toInt() == 0) {
                    elementRenderIdList[idx] = 0.toShort()
                    elementRenderList[idx] = null
                } else {
                    if (id != elementRenderIdList[idx]) {
                        var failed = false
                        elementRenderIdList[idx] = id
                        val descriptor = Eln.sixNodeItem.getDescriptor(id.toInt())
                        if (descriptor == null) {
                            println("ERROR: Server sent bad SixNodeDescriptor id $id")
                            failed = true
                        }
                        if (!failed) {
                            try {
                                elementRenderList[idx] = descriptor!!.RenderClass.getConstructor(SixNodeEntity::class.java, Direction::class.java, SixNodeDescriptor::class.java).newInstance(this, fromInt(idx), descriptor) as SixNodeElementRender
                            } catch (e: Exception) {
                                println("ERROR: Initialize SixNodeElementRender for id " + id + " descriptor " + descriptor + " RenderClass " + descriptor!!.RenderClass + " failed with exception " + e)
                                e.printStackTrace()
                                failed = true
                            }
                        }
                        if (failed) {
                            println("ERROR: A previous failure has desynchronized the DataInputStream for this packet. No further information can be processed. If something isn't rendering right now, please post a bug report for this version of Electrical Age.")
                            println("... " + stream.available() + " bytes remained on the stream, consuming all of them")
                            stream.skip(stream.available().toLong())
                            break
                        }
                    }
                    if (elementRenderList[idx] != null) elementRenderList[idx]!!.publishUnserialize(stream)
                }
                idx++
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun serverPacketUnserialize(stream: DataInputStream) {
        super.serverPacketUnserialize(stream)
        try {
            val side = stream.readByte().toInt()
            val id = stream.readShort().toInt()
            if (elementRenderIdList[side].toInt() == id) {
                elementRenderList[side]!!.serverPacketUnserialize(stream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getSyncronizedSideEnable(direction: Direction): Boolean {
        return elementRenderList[direction.int] != null
    }

    override fun newContainer(side: Direction, player: Player): AbstractContainerMenu? {
        val n = node as SixNode? ?: return null
        return n.newContainer(side, player)
    }

    override fun newGuiDraw(side: Direction, player: Player): Screen? {
        return elementRenderList[side.int]!!.newGuiDraw(side, player)
    }

    override fun getCableRender(side: Direction, lrdu: LRDU): CableRenderDescriptor? {
        val elementSide = side.applyLRDU(lrdu)
        val elementLrdu = elementSide.getLRDUGoingTo(side) ?: return null
        return if (elementRenderList[elementSide.int] == null) null else elementRenderList[elementSide.int]!!.getCableRender(elementLrdu)
    }

    override fun getCableDry(side: Direction?, lrdu: LRDU?): Int {
        val elementSide = side!!.applyLRDU(lrdu!!)
        val elementLrdu = elementSide.getLRDUGoingTo(side) ?: return 0
        return if (elementRenderList[elementSide.int] == null) 0 else elementRenderList[elementSide.int]!!.getCableDry(elementLrdu)
    }

    override fun cameraDrawOptimisation(): Boolean {
        for (e in elementRenderList) {
            if (e != null && !e.cameraDrawOptimisation()) return false
        }
        return true
    }

    override fun unoptimizedRenderBoundingBox(): AABB {
        var bb = localRenderBoundingBox()
        for (render in elementRenderList) {
            val custom = render?.getRenderBoundingBox(this) ?: continue
            bb = AABB(
                minOf(bb.minX, custom.minX),
                minOf(bb.minY, custom.minY),
                minOf(bb.minZ, custom.minZ),
                maxOf(bb.maxX, custom.maxX),
                maxOf(bb.maxY, custom.maxY),
                maxOf(bb.maxZ, custom.maxZ)
            )
        }
        return bb
    }

    override fun destructor() {
        for (render in elementRenderList) {
            render?.destructor()
        }
        super.destructor()
    }

    fun hasVolume(): Boolean {
        return if (worldObj.isClientSide) {
            for (e in elementRenderList) {
                if (e != null && e.sixNodeDescriptor.hasVolume()) return true
            }
            false
        } else {
            val node = node as SixNode? ?: return false
            node.hasVolume()
        }
    }

    override fun tileEntityNeighborSpawn() {
        for (e in elementRenderList) {
            e?.notifyNeighborSpawn()
        }
    }

    override val nodeUuid: String
        get() = Eln.sixNodeBlock.nodeUuid

    override fun clientRefresh(deltaT: Float) {
        for (e in elementRenderList) {
            e?.refresh(deltaT)
        }
    }

    override fun isProvidingWeakPower(side: Direction?): Int {
        return if (worldObj.isClientSide) {
            var max = 0
            for (r in elementRenderList) {
                if (r == null) continue
                if (max < r.isProvidingWeakPower(side)) max = r.isProvidingWeakPower(side)
            }
            max
        } else {
            if (node == null) 0 else node!!.isProvidingWeakPower(side)
        }
    }

    companion object {
        const val singleTargetId = 2
    }

    init {
        for (idx in 0..5) {
            elementRenderList[idx] = null
            elementRenderIdList[idx] = 0
        }
    }
}
