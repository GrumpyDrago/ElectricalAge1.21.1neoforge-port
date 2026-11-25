package mods.eln.entity

import mods.eln.Eln
import mods.eln.misc.Utils
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.RandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level

class ReplicatorEntity(type: EntityType<out ReplicatorEntity>, world: Level) : Monster(type, world) {
    var isSpawnedFromWeather = false
    var hungerTime = 10.0 * 60.0
    var hungerToEnergy = 10.0 * hungerTime
    var energyToDuplicate = 10000.0
    var hungerToDuplicate = -energyToDuplicate / hungerToEnergy
    var hungerToCanibal = 0.6
    var hunger = (Math.random() - 0.5) * 0.3

    init {
        setPersistenceRequired()
    }

    override fun registerGoals() {
        var priority = 0
        goalSelector.addGoal(priority++, FloatGoal(this))
        goalSelector.addGoal(priority++, MeleeAttackGoal(this, 1.0, true))
        goalSelector.addGoal(priority++, ReplicatorCableAI(this))
        goalSelector.addGoal(priority++, MoveTowardsRestrictionGoal(this, 1.0))
        goalSelector.addGoal(priority++, RandomStrollGoal(this, 1.0, 20))
        goalSelector.addGoal(priority, LookAtPlayerGoal(this, Player::class.java, 8.0f))
        goalSelector.addGoal(priority, RandomLookAroundGoal(this))
        targetSelector.addGoal(1, HurtByTargetGoal(this).setAlertOthers())
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Villager::class.java, false))
        // Hungry replicators turn on each other.
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, ReplicatorEntity::class.java, 10, false, false) { hunger >= hungerToCanibal })
    }

    override fun doHurtTarget(entity: Entity): Boolean {
        if (entity is ReplicatorEntity) {
            hunger -= 0.4
            entity.hunger += 0.4
        }
        return super.doHurtTarget(entity)
    }

    override fun customServerAiStep() {
        super.customServerAiStep()
        hunger += 0.05 / hungerTime
        if (hunger > 1 && Math.random() < 0.05 / 5) {
            hurt(damageSources().starve(), 1.0f)
        }
        if (hunger < 0.5 && Math.random() * 10 < 0.05) {
            heal(1.0f)
        }
        if (hunger < hungerToDuplicate) {
            spawnAt(level(), x, y, z)
            hunger = 0.0
        }
    }

    fun eatElectricity(energy: Double) {
        hunger -= Math.min(0.001, energy / hungerToEnergy)
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.SILVERFISH_AMBIENT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.SILVERFISH_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.SILVERFISH_DEATH

    override fun dropCustomDeathLoot(level: ServerLevel, source: DamageSource, recentlyHit: Boolean) {
        super.dropCustomDeathLoot(level, source, recentlyHit)
        val drops = dropList
        if (drops.isNotEmpty()) spawnAtLocation(drops[random.nextInt(drops.size)].copy(), 0.5f)
        if (isSpawnedFromWeather && Math.random() < 0.33) spawnAtLocation(ItemStack(ElnEntities.replicatorEgg), 0.5f)
    }

    override fun addAdditionalSaveData(nbt: CompoundTag) {
        super.addAdditionalSaveData(nbt)
        nbt.putDouble("ElnHunger", hunger)
        nbt.putBoolean("isSpawnedFromWeather", isSpawnedFromWeather)
    }

    override fun readAdditionalSaveData(nbt: CompoundTag) {
        super.readAdditionalSaveData(nbt)
        hunger = nbt.getDouble("ElnHunger")
        isSpawnedFromWeather = nbt.getBoolean("isSpawnedFromWeather")
        Utils.println("[Replicator] $x $y $z ")
    }

    companion object {
        private val dropList: List<ItemStack> by lazy {
            listOf(
                Eln.findItemStack("Iron Dust", 1), Eln.findItemStack("Copper Dust", 1), Eln.findItemStack("Gold Dust", 1),
                ItemStack(Items.REDSTONE), ItemStack(Items.GLOWSTONE_DUST)
            )
        }

        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.FOLLOW_RANGE, 8.0)
            .add(Attributes.MAX_HEALTH, 8.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23)
            .add(Attributes.ATTACK_DAMAGE, 3.0)

        fun spawnAt(world: Level, x: Double, y: Double, z: Double): ReplicatorEntity {
            val entity = ReplicatorEntity(ElnEntities.replicator, world)
            entity.moveTo(x, y, z, 0.0f, 0.0f)
            world.addFreshEntity(entity)
            entity.playAmbientSound()
            return entity
        }
    }
}
