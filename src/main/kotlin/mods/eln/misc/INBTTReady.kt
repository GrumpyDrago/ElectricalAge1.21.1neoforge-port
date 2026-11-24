package mods.eln.misc

import mods.eln.compat.*

import net.minecraft.nbt.CompoundTag

interface INBTTReady {
    fun readFromNBT(nbt: CompoundTag, str: String)
    fun writeToNBT(nbt: CompoundTag, str: String)
}
