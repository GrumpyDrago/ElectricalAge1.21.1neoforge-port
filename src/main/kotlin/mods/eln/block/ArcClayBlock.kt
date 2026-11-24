package mods.eln.block

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour

/** Block of aluminium, as it comes out of the arc furnace. */
class ArcClayBlock : Block(BlockBehaviour.Properties.of().strength(1.5f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops())

/** Block of steel, as it comes out of the arc furnace. */
class ArcMetalBlock : Block(BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops())
