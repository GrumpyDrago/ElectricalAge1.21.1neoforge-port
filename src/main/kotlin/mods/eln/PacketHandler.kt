package mods.eln

import mods.eln.compat.*

import net.neoforged.bus.api.SubscribeEvent
import mods.eln.client.ClientKeyHandler
import mods.eln.item.FalstadImportPacketHandler
import mods.eln.misc.Coordinate
import mods.eln.misc.Utils.println
import mods.eln.misc.Utils.sendPacketToClient
import mods.eln.node.INodeEntity
import mods.eln.node.NodeManager
import mods.eln.sound.SoundClient
import mods.eln.sound.SoundCommand
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class PacketHandler {
    /** Packets a client may send. */
    fun packetRxServer(stream: DataInputStream, player: ServerPlayer) {
        try {
            when (stream.readByte()) {
                Eln.packetPlayerKey -> packetPlayerKey(stream)
                Eln.packetPublishForNode -> packetForNode(stream, player)
                Eln.packetClientToServerConnection -> packetNewClient(player)
                Eln.packetFalstadImport -> packetFalstadImport(stream, player)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /** Packets the server sends. */
    fun packetRxClient(stream: DataInputStream, player: Player) {
        try {
            when (stream.readByte()) {
                Eln.packetNodeSingleSerialized -> packetNodeSingleSerialized(stream, player)
                Eln.packetForClientNode -> packetForClientNode(stream, player)
                Eln.packetOpenLocalGui -> packetOpenLocalGui(stream, player)
                Eln.packetPlaySound -> packetPlaySound(stream, player)
                Eln.packetDestroyUuid -> packetDestroyUuid(stream)
                Eln.packetServerToClientInfo -> packetServerInfo(stream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetNewClient(player: Player) {
        val bos = ByteArrayOutputStream(64)
        val stream = DataOutputStream(bos)
        try {
            stream.writeByte(Eln.packetServerToClientInfo.toInt())
            for (c in Eln.instance.configShared) {
                c.serializeConfig(stream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        sendPacketToClient(bos, (player as ServerPlayer))
    }

    private fun packetServerInfo(stream: DataInputStream) {
        for (c in Eln.instance.configShared) {
            try {
                c.deserialize(stream)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun packetDestroyUuid(stream: DataInputStream) {
        try {
            ClientHooks.killSound(stream.readInt())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetPlaySound(stream: DataInputStream, player: Player) {
        try {
            if (stream.readInt() != player.dimension) return
            SoundClient.play(SoundCommand.fromStream(stream, player.worldObj))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetOpenLocalGui(stream: DataInputStream, player: Player) {
        try {
            player.openGui(Eln.instance, stream.readInt(),
                player.worldObj, stream.readInt(), stream.readInt(),
                stream.readInt())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetForNode(stream: DataInputStream, player: Player?) {
        try {
            val coordinate = Coordinate(stream.readInt(), stream.readInt(), stream.readInt(), stream.readInt())
            val node = NodeManager.instance!!.getNodeFromCoordonate(coordinate)
            if (node != null && node.nodeUuid == stream.readUTF()) {
                node.networkUnserialize(stream, player as ServerPlayer?)
            } else {
                println("packetForNode node found")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetForClientNode(stream: DataInputStream, player: Player) {
        try {
            val x = stream.readInt()
            val y = stream.readInt()
            val z = stream.readInt()
            val dimension = stream.readInt()
            if (player.dimension == dimension) {
                val entity = player.worldObj.getTileEntity(x, y, z)
                if (entity != null && entity is INodeEntity) {
                    val node = entity as INodeEntity
                    if (node.nodeUuid == stream.readUTF()) {
                        node.serverPacketUnserialize(stream)
                        if (0 != stream.available()) {
                            println("0 != stream.available()")
                        }
                    } else {
                        println("Wrong node UUID warning")
                        val dataSkipLength = stream.readByte().toInt()
                        for (idx in 0 until dataSkipLength) {
                            stream.readByte()
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetNodeSingleSerialized(stream: DataInputStream, player: Player) {
        try {
            val x: Int = stream.readInt()
            val y: Int = stream.readInt()
            val z: Int = stream.readInt()
            val dimension: Int = stream.readInt()
            if (player.dimension == dimension) {
                val entity = player.worldObj.getTileEntity(x, y, z)
                if (entity != null && entity is INodeEntity) {
                    val node = entity as INodeEntity
                    if (node.nodeUuid == stream.readUTF()) {
                        node.serverPublishUnserialize(stream)
                        if (0 != stream.available()) {
                            println("0 != stream.available()")
                        }
                    } else {
                        println("Wrong node UUID warning")
                        val dataSkipLength = stream.readByte().toInt()
                        for (idx in 0 until dataSkipLength) {
                            stream.readByte()
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetPlayerKey(stream: DataInputStream) {
        try {
            val name = stream.readUTF()
            val state = stream.readBoolean()
            ServerKeyHandler.set(name, state)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun packetFalstadImport(stream: DataInputStream, player: Player) {
        try {
            val length = stream.readInt()
            val bytes = ByteArray(length)
            stream.readFully(bytes)
            FalstadImportPacketHandler.handle(player as ServerPlayer, bytes)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
