package mods.eln.entity

import mods.eln.Eln
import mods.eln.compat.*
import mods.eln.misc.Utils
import mods.eln.sim.IProcess
import net.minecraft.world.Difficulty
import net.minecraft.world.level.LightLayer

class ReplicatorPopProcess : IProcess {
    override fun process(time: Double) {
        val world = currentServer?.overworld() ?: return
        val maxReplicators = Eln.config.getIntOrElse("entities.replicator.maxCount", 100)
        val popPerSecondPerPlayer =
            Eln.config.getDoubleOrElse("entities.replicator.thunderSpawnPerSecondPerPlayer", 1.0 / 120.0)

        world.getEntities(ElnEntities.replicator) { true }.drop(maxReplicators).forEach { it.discard() }

        if (world.difficulty == Difficulty.PEACEFUL || !world.isThundering) return

        for (player in world.players()) {
            if (Math.random() * world.players().size >= time * popPerSecondPerPlayer) continue
            val x = (player.x + Utils.rand(-100.0, 100.0)).toInt()
            val z = (player.z + Utils.rand(-100.0, 100.0)).toInt()
            var y = world.minBuildHeight + 2
            if (!world.blockExists(x, y, z)) break
            while (y < world.maxBuildHeight && (!world.isAirBlock(x, y, z) || Utils.getLight(world, LightLayer.BLOCK, x, y, z) > 6)) {
                y++
            }
            if (y >= world.maxBuildHeight) break
            ReplicatorEntity.spawnAt(world, x + 0.5, y.toDouble(), z + 0.5).isSpawnedFromWeather = true
            Utils.println("Spawn Replicator at $x $y $z")
        }
    }
}
