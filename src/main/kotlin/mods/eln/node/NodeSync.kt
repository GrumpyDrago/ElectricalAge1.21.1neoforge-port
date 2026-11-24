package mods.eln.node

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/** Carries a node's published state inside block entity update tags, so clients have it as soon as a chunk arrives. */
object NodeSync {
    private const val KEY = "elnNode"

    fun write(tag: CompoundTag, node: NodeBase?) {
        val packet = node?.publishPacket ?: return
        tag.putByteArray(KEY, packet.toByteArray())
    }

    fun read(tag: CompoundTag?, entity: INodeEntity) {
        if (tag == null || !tag.contains(KEY, Tag.TAG_BYTE_ARRAY.toInt())) return
        val stream = DataInputStream(ByteArrayInputStream(tag.getByteArray(KEY)))
        stream.readByte() // packet id
        repeat(4) { stream.readInt() } // position and dimension
        if (stream.readUTF() == entity.nodeUuid) entity.serverPublishUnserialize(stream)
    }
}
