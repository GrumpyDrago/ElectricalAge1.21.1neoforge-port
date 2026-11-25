package mods.eln.eventhandlers

import mods.eln.environment.RoomThermalManager
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ExplosionEvent

class RoomThermalBlockEventsHandler {
    private fun changed(level: LevelAccessor, pos: BlockPos) {
        if (level is Level && !level.isClientSide) RoomThermalManager.onBlockChanged(level, pos.x, pos.y, pos.z)
    }

    @SubscribeEvent
    fun onBlockBreak(event: BlockEvent.BreakEvent) = changed(event.level, event.pos)

    @SubscribeEvent
    fun onBlockPlace(event: BlockEvent.EntityPlaceEvent) = changed(event.level, event.pos)

    @SubscribeEvent
    fun onExplosionDetonate(event: ExplosionEvent.Detonate) {
        for (pos in event.affectedBlocks) changed(event.level, pos)
    }
}
