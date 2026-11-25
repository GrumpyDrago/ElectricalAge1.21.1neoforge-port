package mods.eln.sound

import mods.eln.compat.rl
import mods.eln.misc.Coordinate
import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.resources.sounds.TickableSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource

abstract class LoopedSound(
    val sample: String, val coord: Coordinate,
    attentuationType: SoundInstance.Attenuation = SoundInstance.Attenuation.LINEAR
) : AbstractSoundInstance(rl(sample), SoundSource.BLOCKS, RandomSource.create()), TickableSoundInstance {
    var active = true

    init {
        x = coord.x + 0.5
        y = coord.y + 0.5
        z = coord.z + 0.5
        looping = true
        delay = 0
        attenuation = attentuationType
    }

    override fun getPitch() = 1f
    override fun getVolume() = 1f
    override fun isStopped() = !active
    override fun canStartSilent() = true
    override fun tick() {}
}
