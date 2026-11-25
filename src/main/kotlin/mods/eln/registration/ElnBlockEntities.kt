package mods.eln.registration

import mods.eln.Eln
import mods.eln.lightblock.LightBlockEntity
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.node.transparent.TransparentNodeEntityWithFluid
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

object ElnBlockEntities {
    @JvmStatic
    lateinit var sixNode: BlockEntityType<SixNodeEntity>
        private set

    @JvmStatic
    lateinit var transparentNode: BlockEntityType<TransparentNodeEntity>
        private set

    @JvmStatic
    lateinit var transparentNodeWithFluid: BlockEntityType<TransparentNodeEntityWithFluid>
        private set

    @JvmStatic
    lateinit var lightBlock: BlockEntityType<LightBlockEntity>
        private set

    fun <T : BlockEntity> create(path: String, factory: (BlockPos, BlockState) -> T, vararg blocks: Block): BlockEntityType<T> {
        val type: BlockEntityType<T> = BlockEntityType.Builder.of(factory, *blocks).build(null)
        ElnRegistry.register<BlockEntityType<*>>(Registries.BLOCK_ENTITY_TYPE, path, type)
        return type
    }

    @JvmStatic
    fun register() {
        sixNode = create("six_node", ::SixNodeEntity, Eln.sixNodeBlock)
        transparentNode = create("transparent_node", ::TransparentNodeEntity, Eln.transparentNodeBlock)
        transparentNodeWithFluid = create("transparent_node_fluid", ::TransparentNodeEntityWithFluid, Eln.transparentNodeBlock)
        lightBlock = create("light_block", ::LightBlockEntity, Eln.lightBlock)
    }
}
