package mods.eln.entity

import mods.eln.Eln
import mods.eln.railroad.EntityElectricMinecart
import mods.eln.registration.ElnRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.DeferredSpawnEggItem
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent

object ElnEntities {
    @JvmStatic
    lateinit var replicator: EntityType<ReplicatorEntity>
        private set

    @JvmStatic
    lateinit var replicatorEgg: Item
        private set

    @JvmStatic
    lateinit var electricMinecart: EntityType<EntityElectricMinecart>
        private set

    @JvmStatic
    fun register() {
        replicator = EntityType.Builder.of({ type: EntityType<ReplicatorEntity>, level: Level -> ReplicatorEntity(type, level) }, MobCategory.MONSTER)
            .sized(0.3f, 0.7f).build("eln:replicator")
        ElnRegistry.register(Registries.ENTITY_TYPE, "replicator", replicator)
        replicatorEgg = DeferredSpawnEggItem({ replicator }, 0xFF0000, 0xFFC800, Item.Properties())
        ElnRegistry.registerItem("replicator_spawn_egg", replicatorEgg)
        Eln.creativeTabOther.add { ItemStack(replicatorEgg) }

        electricMinecart = EntityType.Builder.of({ type: EntityType<EntityElectricMinecart>, level: Level -> EntityElectricMinecart(type, level) }, MobCategory.MISC)
            .sized(0.98f, 0.7f).clientTrackingRange(8).build("eln:electric_minecart")
        ElnRegistry.register(Registries.ENTITY_TYPE, "electric_minecart", electricMinecart)
    }

    fun onAttributes(event: EntityAttributeCreationEvent) {
        event.put(replicator, ReplicatorEntity.createAttributes().build())
    }
}
