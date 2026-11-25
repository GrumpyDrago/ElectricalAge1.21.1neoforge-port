package mods.eln.sound

import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.misc.Utils.sendPacketToClient
import net.minecraft.world.phys.Vec3
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

object SoundServer {
    fun play(p: SoundCommand) {
        val bos = ByteArrayOutputStream(64)
        val stream = DataOutputStream(bos)
        try {
            stream.writeByte(Eln.packetPlaySound.toInt())
            stream.writeInt(p.world!!.dimensionId)
            p.writeTo(stream)
            val server = currentServer ?: return
            for (player in server.players) {
                if (player.dimension == p.world!!.dimensionId && player.position().distanceTo(Vec3(p.x, p.y, p.z)) < p.rangeMax + 2) {
                    sendPacketToClient(bos, player)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
