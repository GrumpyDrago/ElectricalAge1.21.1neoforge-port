package mods.eln.sound

import mods.eln.compat.*

import mods.eln.misc.Coordinate

class BeepLoopedSound(
    coord: Coordinate,
    private var loudness: Float = 1f,
    private var tone: Float = 1f
) : LoopedSound("eln:beep_loop", coord) {
    override fun getVolume() = loudness
    override fun getPitch() = tone

    fun setVolume(value: Float) {
        loudness = value
    }

    fun setPitch(value: Float) {
        tone = value
    }

    fun setActiveState(value: Boolean) {
        active = value
    }

    fun isActive(): Boolean {
        return active
    }
}
