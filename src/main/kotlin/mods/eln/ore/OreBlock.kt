package mods.eln.ore

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour

class OreBlock : Block(BlockBehaviour.Properties.of().strength(3.0f, 5.0f).sound(SoundType.STONE).requiresCorrectToolForDrops())
