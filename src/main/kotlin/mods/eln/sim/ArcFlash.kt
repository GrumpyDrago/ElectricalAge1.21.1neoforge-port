package mods.eln.sim

import mods.eln.Eln
import mods.eln.compat.currentServer
import mods.eln.misc.Coordinate
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * What happens when a contact opens under load. The current keeps flowing for a moment through the air between the
 * contacts, as an arc. Within its rating a switch puts that out at once: a spark. Breaking a fault current, many
 * times what the contacts are made for, gives an arc flash: a blast of light, heat and molten metal that burns and
 * blinds whoever stands close.
 */
object ArcFlash {
    /** Below this power in the arc there is nothing to see. */
    const val SPARK_POWER = 300.0
    /** From this power, when it is also a fault, the arc flashes. */
    const val FLASH_POWER = 5000.0
    /** A current this many times the rating is a fault, which the contacts cannot quench at once. */
    const val FAULT_RATIO = 2.0
    /** Arcs need some voltage to strike across opening contacts. */
    const val MIN_VOLTAGE = 20.0

    enum class Kind { NONE, SPARK, FLASH }

    class Arc(val kind: Kind, val power: Double) {
        /** How much bigger than the smallest flash it is, in powers of ten. */
        val severity get() = if (kind == Kind.FLASH) log10(power / FLASH_POWER).coerceAtLeast(0.0) else 0.0
        /** The distance it hurts within, in blocks. */
        val radius get() = (1.5 + 1.2 * severity).coerceAtMost(5.0)
        /** Damage at the contacts, in half hearts; it falls off to nothing at [radius]. */
        val damage get() = (3.0 + 5.0 * severity).coerceAtMost(18.0)
        val fireSeconds get() = (2.0 + 3.0 * severity).coerceAtMost(8.0)
        val blindTicks get() = if (severity >= 0.5) (40 + 60 * severity).toInt().coerceAtMost(160) else 0
    }

    /** What opening a contact that carries [current] at [voltage] does, for contacts rated [ratedCurrent]. */
    fun classify(voltage: Double, current: Double, ratedCurrent: Double): Arc {
        val v = abs(voltage)
        val i = abs(current)
        val power = v * i
        if (v < MIN_VOLTAGE || power < SPARK_POWER) return Arc(Kind.NONE, power)
        val fault = ratedCurrent <= 0 || i > FAULT_RATIO * ratedCurrent
        return Arc(if (fault && power >= FLASH_POWER) Kind.FLASH else Kind.SPARK, power)
    }

    /** A contact at [at] opened while carrying [current] at [voltage]; shows the arc and hurts those near it. */
    fun interrupt(at: Coordinate?, voltage: Double, current: Double, ratedCurrent: Double): Arc {
        val arc = classify(voltage, current, ratedCurrent)
        if (arc.kind == Kind.NONE || at == null || !Eln.arcFlashEnable) return arc
        val server = currentServer ?: return arc
        // The simulation may run off the main thread; the world is only touched on it.
        server.execute { show(at, arc) }
        return arc
    }

    private fun show(at: Coordinate, arc: Arc) {
        if (!at.worldExist) return
        val level = at.world() as? ServerLevel ?: return
        val x = at.x + 0.5
        val y = at.y + 0.5
        val z = at.z + 0.5
        if (arc.kind == Kind.SPARK) {
            val count = (4 + log10(arc.power / SPARK_POWER) * 6).toInt().coerceIn(4, 16)
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, count, 0.15, 0.15, 0.15, 0.2)
            level.playSound(null, x, y, z, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.3f, 1.8f)
            return
        }
        val s = arc.severity
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1 + s.toInt(), 0.0, 0.0, 0.0, 0.0)
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, (30 + 40 * s).toInt(), 0.3, 0.3, 0.3, 0.6)
        level.sendParticles(ParticleTypes.LAVA, x, y, z, (3 + 4 * s).toInt(), 0.2, 0.2, 0.2, 0.0)
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, (6 + 8 * s).toInt(), 0.3, 0.3, 0.3, 0.02)
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
            (0.6 + 0.3 * s).toFloat().coerceAtMost(2f), 1.7f)
        if (s >= 1) {
            level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.8f, 1.4f)
        }

        val r = arc.radius
        for (e in level.getEntitiesOfClass(LivingEntity::class.java, AABB(x - r, y - r, z - r, x + r, y + r, z + r))) {
            val d = sqrt(e.distanceToSqr(x, e.y + e.bbHeight / 2, z))
            if (d > r) continue
            val near = 1 - d / r
            e.hurt(level.damageSources().lightningBolt(), (arc.damage * near).toFloat().coerceAtLeast(1f))
            e.igniteForSeconds((arc.fireSeconds * near).toFloat().coerceAtLeast(1f))
            if (arc.blindTicks > 0) e.addEffect(MobEffectInstance(MobEffects.BLINDNESS, (arc.blindTicks * near).toInt().coerceAtLeast(20)))
        }
    }
}
