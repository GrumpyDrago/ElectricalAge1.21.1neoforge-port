package mods.eln.fluid

import mods.eln.Eln
import mods.eln.registration.ElnRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.FlowingFluid
import net.neoforged.neoforge.fluids.BaseFlowingFluid
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.registries.NeoForgeRegistries

/** One of the mod's own fluids with everything that belongs to it. */
class ElnFluid(val definition: ElnFluidRegistry) {
    val name: String = definition.name
    val type = FluidType(
        FluidType.Properties.create()
            .density(definition.density)
            .viscosity(definition.viscosity)
            .lightLevel(definition.luminosity)
            .temperature(definition.temperature)
            .canConvertToSource(false)
            .supportsBoating(true)
            .canHydrate(true)
            .descriptionId("fluid_type.eln.$name")
    )
    lateinit var source: FlowingFluid
        private set
    lateinit var flowing: FlowingFluid
        private set
    lateinit var block: LiquidBlock
        private set
    var bucket: BucketItem? = null
        private set

    fun register() {
        val properties = BaseFlowingFluid.Properties({ type }, { source }, { flowing }).block { block }
        if (definition.isBucketable) properties.bucket { bucket!! }
        source = BaseFlowingFluid.Source(properties)
        flowing = BaseFlowingFluid.Flowing(properties)
        block = LiquidBlock(source, BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable())
        ElnRegistry.register(NeoForgeRegistries.Keys.FLUID_TYPES, name, type)
        ElnRegistry.register(Registries.FLUID, name, source)
        ElnRegistry.register(Registries.FLUID, "flowing_$name", flowing)
        ElnRegistry.registerBlock(name, block)
        if (definition.isBucketable) {
            val item = BucketItem(source, Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1))
            bucket = item
            ElnRegistry.registerItem("${name}_bucket", item)
            Eln.creativeTabOther.add { ItemStack(item) }
        }
    }
}

object ElnFluids {
    val all = ArrayList<ElnFluid>()
}

fun registerElnFluids() {
    for (definition in ElnFluidRegistry.entries) {
        val fluid = ElnFluid(definition)
        fluid.register()
        ElnFluids.all.add(fluid)
    }
}
