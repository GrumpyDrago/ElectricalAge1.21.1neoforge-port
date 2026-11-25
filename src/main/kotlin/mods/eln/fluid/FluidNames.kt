package mods.eln.fluid

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids

/**
 * Electrical Age's configuration refers to fluids by plain names such as "diesel", whichever mod provides them.
 */
object FluidNames {
    private fun normal(name: String) = name.lowercase().replace("_", "").replace(" ", "")

    private val byName: Map<String, Fluid> by lazy {
        val map = HashMap<String, Fluid>()
        for (fluid in BuiltInRegistries.FLUID) {
            if (fluid === Fluids.EMPTY || !fluid.defaultFluidState().isSource) continue
            map.putIfAbsent(normal(BuiltInRegistries.FLUID.getKey(fluid).path), fluid)
        }
        map
    }

    /** The fluid with this name, in any namespace. */
    @JvmStatic
    fun find(name: String): Fluid? {
        if (':' in name) {
            val id = ResourceLocation.tryParse(name) ?: return null
            return if (BuiltInRegistries.FLUID.containsKey(id)) BuiltInRegistries.FLUID.get(id) else null
        }
        return byName[normal(name)]
    }

    /** The name used for this fluid in the configuration, "" for none. */
    @JvmStatic
    fun nameOf(fluid: Fluid?): String =
        if (fluid == null || fluid === Fluids.EMPTY) "" else BuiltInRegistries.FLUID.getKey(fluid).path

    /** Like [nameOf], but unambiguous; for saving. */
    @JvmStatic
    fun idOf(fluid: Fluid?): String =
        if (fluid == null || fluid === Fluids.EMPTY) "" else BuiltInRegistries.FLUID.getKey(fluid).toString()
}

val Fluid.name: String
    get() = FluidNames.nameOf(this)
