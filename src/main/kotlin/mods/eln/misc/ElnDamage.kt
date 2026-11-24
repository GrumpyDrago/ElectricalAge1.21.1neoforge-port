package mods.eln.misc

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.Entity

/** Damage types, defined in data/eln/damage_type. */
object ElnDamage {
    private fun key(name: String): ResourceKey<DamageType> =
        ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("eln", name))

    @JvmField
    val FLYWHEEL: ResourceKey<DamageType> = key("flywheel")

    @JvmField
    val TURRET: ResourceKey<DamageType> = key("turret")

    @JvmField
    val ELECTROCUTION: ResourceKey<DamageType> = key("electrocution")

    @JvmStatic
    fun source(entity: Entity, type: ResourceKey<DamageType>): DamageSource = entity.damageSources().source(type)

    @JvmStatic
    fun flywheel(entity: Entity): DamageSource = source(entity, FLYWHEEL)
}
