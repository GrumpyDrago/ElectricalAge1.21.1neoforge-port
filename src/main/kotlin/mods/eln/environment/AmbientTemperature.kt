package mods.eln.environment

import mods.eln.compat.blockExists
import mods.eln.compat.currentServer
import mods.eln.misc.Coordinate

/**
 * The ambient temperature of one node element.
 *
 * Watchdogs want it on every tick, while it only follows the time of day and the weather. Looking the biome up is the
 * expensive part, above all where the chunk is not loaded, so that is only done again where the chunk is there anyway.
 */
class AmbientTemperature {
    private var temperature = 0.0
    private var validUntil = Int.MIN_VALUE
    private var sampledAt = Int.MAX_VALUE
    private var profile: BiomeClimateProfile? = null
    private var x = 0
    private var y = 0
    private var z = 0
    private var dimension = 0

    fun get(coordinate: Coordinate): Double {
        val now = currentServer?.tickCount ?: 0
        if (now < validUntil && now >= sampledAt && coordinate.x == x && coordinate.y == y && coordinate.z == z && coordinate.dimension == dimension) {
            return temperature
        }

        check(coordinate.worldExist) { "World not loaded for coordinate $coordinate while sampling ambient temperature." }
        val world = coordinate.world()
        if (coordinate.x != x || coordinate.y != y || coordinate.z != z || coordinate.dimension != dimension) {
            profile = null
            x = coordinate.x
            y = coordinate.y
            z = coordinate.z
            dimension = coordinate.dimension
        }
        val climate = profile.takeIf { !world.blockExists(x, y, z) }
            ?: BiomeClimateService.profileAt(world, x, y, z).also { profile = it }
        temperature = BiomeClimateService.temperatureCelsius(world, y, climate)
        sampledAt = now
        // Not everything in the same tick.
        validUntil = now + INTERVAL + Math.floorMod(coordinate.hashCode(), SPREAD)
        return temperature
    }

    private companion object {
        const val INTERVAL = 20
        const val SPREAD = 20
    }
}
