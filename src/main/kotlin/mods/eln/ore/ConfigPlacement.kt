package mods.eln.ore

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mods.eln.Eln
import mods.eln.registration.ElnRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.util.RandomSource
import net.minecraft.world.level.levelgen.placement.PlacementContext
import net.minecraft.world.level.levelgen.placement.PlacementFilter
import net.minecraft.world.level.levelgen.placement.PlacementModifierType

/** Placement modifier that lets a feature through if a boolean setting in the Electrical Age configuration is on. */
class ConfigPlacement(val path: String, val default: Boolean) : PlacementFilter() {
    override fun shouldPlace(context: PlacementContext, random: RandomSource, pos: BlockPos): Boolean =
        Eln.config.getBooleanOrElse(path, default)

    override fun type(): PlacementModifierType<*> = TYPE

    companion object {
        val CODEC: MapCodec<ConfigPlacement> = RecordCodecBuilder.mapCodec { builder ->
            builder.group(
                Codec.STRING.fieldOf("path").forGetter { it.path },
                Codec.BOOL.fieldOf("default").forGetter { it.default }
            ).apply(builder, ::ConfigPlacement)
        }
        val TYPE: PlacementModifierType<ConfigPlacement> = PlacementModifierType { CODEC }

        fun register() {
            ElnRegistry.register(Registries.PLACEMENT_MODIFIER_TYPE, "config", TYPE)
        }
    }
}
