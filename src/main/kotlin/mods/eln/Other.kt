package mods.eln

import net.neoforged.fml.ModList

/** Other mods and the energy units they use. */
object Other {
    const val modIdCc = "computercraft"

    @JvmStatic
    var wattsToEu = 0.0

    @JvmStatic
    var wattsToOC = 0.0

    /** Also used for NeoForge energy (FE). */
    @JvmStatic
    var wattsToRf = 0.0

    @JvmStatic
    val ccLoaded: Boolean
        get() = ModList.get()?.isLoaded(modIdCc) ?: false
}
