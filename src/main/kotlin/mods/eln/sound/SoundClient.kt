package mods.eln.sound

import mods.eln.compat.*

import mods.eln.client.ClientProxy
import mods.eln.client.SoundLoader
import mods.eln.misc.Utils.TraceRayWeightOpaque
import mods.eln.misc.Utils.println
import mods.eln.misc.Utils.traceRay
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.player.Player
import kotlin.math.pow
import kotlin.math.sqrt

object SoundClient {
    private fun playAt(p: SoundCommand, x: Double, y: Double, z: Double, track: String, volume: Float, pitch: Float) {
        val manager = Minecraft.getInstance().soundManager
        val sound = SimpleSoundInstance(soundEvent(track), SoundSource.BLOCKS, volume, pitch, RandomSource.create(), x, y, z)
        manager.play(sound)
        if (p.uuid.isNotEmpty()) ClientProxy.uuidManager.add(p.uuid, SoundClientEntity(manager, sound))
    }

    fun play(p: SoundCommand) {

        val player: Player = Minecraft.getInstance().player!!
        if (p.world!!.dimensionId != player.dimension) return
        val distance = 0.01 + sqrt((p.x - player.x).pow(2.0) + (p.y - player.y).pow(2.0) + (p.z - player.z).pow(2.0))
        if (distance >= p.rangeMax) return
        var distanceFactor = 1f
        if (distance > p.rangeNominal) {
            distanceFactor = ((p.rangeMax - distance) / (p.rangeMax - p.rangeNominal)).toFloat()
        }

        val blockFactor = traceRay(
            p.world!!,
            player.x,
            player.y,
            player.z,
            p.x,
            p.y,
            p.z,
            TraceRayWeightOpaque()
        ) * p.blockFactor

        val trackCount = SoundLoader.getTrackCount(p.track)

        if (trackCount == 1) {
            val temp = 1.0f / (1 + blockFactor)
            p.volume *= temp.pow(2.0f)
            p.volume *= distanceFactor
            if (p.volume <= 0) return

            playAt(p,
                player.x + 2 * (p.x - player.x) / distance,
                player.y + 2 * (p.y - player.y) / distance,
                player.z + 2 * (p.z - player.z) / distance,
                p.track!!,
                p.volume,
                p.pitch
            )
        } else {
            for (idx in 0 until trackCount) {
                var bandVolume = p.volume
                bandVolume *= distanceFactor

                bandVolume -= (((trackCount - 1 - idx) / (trackCount - 1f) + 0.2) * blockFactor).toFloat()
                println(bandVolume)
                playAt(p,
                    player.x + 2 * (p.x - player.x) / distance,
                    player.y + 2 * (p.y - player.y) / distance,
                    player.z + 2 * (p.z - player.z) / distance,
                    p.track + "_" + idx + "x",
                    bandVolume,
                    p.pitch
                )
            }
        }

    }
}
