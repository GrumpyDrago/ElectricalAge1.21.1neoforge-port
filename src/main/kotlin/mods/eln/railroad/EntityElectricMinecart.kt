package mods.eln.railroad

import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.node.NodeManager
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.entity.ElnEntities
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import kotlin.math.abs
import kotlin.math.sign

class EntityElectricMinecart : AbstractMinecart {
    constructor(type: EntityType<*>, world: Level) : super(type, world)

    constructor(world: Level, x: Double, y: Double, z: Double) : super(ElnEntities.electricMinecart, world, x, y, z)

    private var lastPowerElement: RailroadPowerInterface? = null
    private val locomotiveMaximumResistance = 200.0
    var energyBufferTargetJoules = 10_000.0
    var energyBufferJoules = 0.0

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        val cartCoordinate = Coordinate(blockX, blockY, blockZ, level())
        val overheadWires = getOverheadWires(cartCoordinate)
        val underTrackWires = getUnderTrackWires(cartCoordinate)

        when (val oldPowerElement = lastPowerElement) {
            overheadWires, underTrackWires, null -> {
                // references existing overhead wires or under track wires, or nothing
            }
            else -> {
                // references old overhead wires or under track wires that are not the current ones
                energyBufferJoules += PoweredMinecartSimulationSingleton.cartCollectEnergy(this)
                Eln.logger.info("Deregister cart from $oldPowerElement")
                oldPowerElement.deregisterCart(this)
            }
        }

        val currentElement: RailroadPowerInterface? = underTrackWires ?: overheadWires

        if (currentElement != null) {
            if (currentElement != lastPowerElement) {
                Eln.logger.info("Register cart to $currentElement")
                currentElement.registerCart(this)
            }

            if (energyBufferJoules < energyBufferTargetJoules) {
                val chargeRateInv = energyBufferTargetJoules / (abs(energyBufferTargetJoules - energyBufferJoules) * 2)
                PoweredMinecartSimulationSingleton.powerCart(this, chargeRateInv * locomotiveMaximumResistance, 0.1)
            } else {
                PoweredMinecartSimulationSingleton.powerCart(this, MnaConst.highImpedance, 0.1)
            }
            energyBufferJoules += PoweredMinecartSimulationSingleton.cartCollectEnergy(this)
        }

        lastPowerElement = currentElement
    }

    var pushX: Double = 0.0
    var pushZ: Double = 0.0

    override fun moveAlongTrack(pos: BlockPos, state: BlockState) {
        super.moveAlongTrack(pos, state)
        if (energyBufferJoules > 0) {
            val maxEnergy = 40.0
            var energyAvailable = maxEnergy
            if (energyBufferJoules < maxEnergy) {
                energyAvailable = energyBufferJoules
            }

            val startingThreshold = 0.0005

            if (abs(motionX) >= startingThreshold || abs(motionZ) >= startingThreshold) {
                if (abs(motionX) < 0.5 && abs(motionZ) < 0.5) {
                    pushX = motionX.sign * 0.05 * (energyAvailable / maxEnergy)
                    pushZ = motionZ.sign * 0.05 * (energyAvailable / maxEnergy)
                    energyBufferJoules -= energyAvailable
                }
            }
        }

        //Eln.logger.info("Push: ($pushX, $pushZ)")

        setDeltaMovement(motionX + pushX, motionY, motionZ + pushZ)

        //Eln.logger.info("Speed: ($motionX, $motionZ)")

        pushX = 0.0
        pushZ = 0.0
    }

    private fun getOverheadWires(coordinate: Coordinate): OverheadLinesElement? {
        // Pass coordinate of tracks and check vertically the next 3 blocks (4 up looks visually weird)
        val originalY = coordinate.y
        while (coordinate.y <= (originalY + 3)) {
            coordinate.y
            val node = NodeManager.instance!!.getTransparentNodeFromCoordinate(coordinate)
            if (node is OverheadLinesElement) {
                return node
            }
            coordinate.y++
        }
        return null
    }

    private fun getUnderTrackWires(coordinate: Coordinate): UnderTrackPowerElement? {
        coordinate.y -= 1 // check the block below the cart
        val node = NodeManager.instance!!.getTransparentNodeFromCoordinate(coordinate)
        if (node is UnderTrackPowerElement) {
            return node
        }
        return null
    }

    override fun interact(player: Player, hand: InteractionHand): InteractionResult {
        val result = super.interact(player, hand)
        if (result.consumesAction()) return result
        if (player.isSecondaryUseActive || isVehicle) return InteractionResult.PASS
        if (level().isClientSide) return InteractionResult.SUCCESS
        return if (player.startRiding(this)) InteractionResult.CONSUME else InteractionResult.PASS
    }

    override fun canBeRidden(): Boolean = true

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putDouble("EnergyBufferJ", energyBufferJoules)
        tag.putDouble("EnergyBufferTargetJ", energyBufferTargetJoules)
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        if (tag.contains("EnergyBufferJ")) {
            energyBufferJoules = tag.getDouble("EnergyBufferJ")
        }
        if (tag.contains("EnergyBufferTargetJ")) {
            energyBufferTargetJoules = tag.getDouble("EnergyBufferTargetJ")
        }
    }

    override fun getMinecartType(): Type = Type.RIDEABLE

    override fun getDefaultDisplayBlockState(): BlockState = Blocks.IRON_BLOCK.defaultBlockState()

    override fun getDropItem(): Item = Eln.findItemStack("Electric Minecart", 1)?.item ?: Items.MINECART
}
