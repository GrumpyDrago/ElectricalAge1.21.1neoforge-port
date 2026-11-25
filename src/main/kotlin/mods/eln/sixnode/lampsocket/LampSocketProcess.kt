package mods.eln.sixnode.lampsocket

import mods.eln.compat.*

import mods.eln.Eln
import mods.eln.item.LampDescriptor
import mods.eln.lightblock.LightBlockEntity
import mods.eln.misc.Coordinate
import mods.eln.misc.INBTTReady
import mods.eln.misc.MutVec3
import mods.eln.misc.Utils
import mods.eln.server.SaveConfig
import mods.eln.sim.IProcess
import mods.eln.sixnode.lampsupply.LampSupplyElement
import mods.eln.sixnode.lampsupply.LampSupplyElement.PowerSupplyChannelHandle
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

class LampSocketProcess(var lamp: LampSocketElement) : IProcess, INBTTReady /*,LightBlockObserver*/ {
    @JvmField
    var light = 0 // 0..15
    @JvmField
    var alphaZ = 0.0
    var stableProb = 0.0
    var lampStackLast: ItemStack? = null
    var boot = true
    private var ageTime = 0.0
    var lbCoord: Coordinate

    var bestChannelHandle: Pair<Double, PowerSupplyChannelHandle>? = null

    private fun findBestSupply(here: Coordinate, forceUpdate: Boolean = false): Pair<Double, PowerSupplyChannelHandle>? {
        val chanMap = LampSupplyElement.channelMap[lamp.channel] ?: return null
        val bestChanHand = bestChannelHandle
        // Here's our cached value. We just check if it's null and if it's still a thing.
        if (bestChanHand != null && !forceUpdate && chanMap.contains(bestChanHand.second)) {
            return bestChanHand // we good!
        }
        val list = LampSupplyElement.channelMap[lamp.channel]?.filterNotNull() ?: return null
        val chanHand = list
            .map { Pair(it.element.sixNode?.coordinate?.trueDistanceTo(here)?: Double.MAX_VALUE, it) }
            .filter { it.first < it.second.element.range }
            .minByOrNull { it.first }
        bestChannelHandle = chanHand
        return bestChannelHandle
    }

    private fun updateNearbyBlocks(growRate: Double, nominalLight: Double, actualLight: Int, deltaT: Double) {
        val randTarget = 1.0 / growRate * deltaT * (1.0 * actualLight / nominalLight / 15.0)
        if (randTarget > Math.random()) {
            val direction = MutVec3(1.0, 0.0, 0.0)
            direction.rotateAroundZ((alphaZ * Math.PI / 180.0).toFloat())
            direction.rotateAroundY(((Math.random() - 0.5) * 2 * Math.PI / 4).toFloat())
            direction.rotateAroundZ(((Math.random() - 0.5) * 2 * Math.PI / 4).toFloat())
            val c = raytrace(direction, lamp.socketDescriptor.range + actualLight) ?: return
            val world = c.first.world() as? ServerLevel ?: return
            if (!c.first.state.isAir) c.first.state.randomTick(world, c.first.blockPos, world.random)
        }
    }

    /** Where the light gets to and the vector there, or null if that is not loaded. Turns [direction] as the socket is. */
    private fun raytrace(direction: MutVec3, range: Int): Pair<Coordinate, MutVec3>? {
        direction.set(lamp.front.rotateOnXnLeft(direction.toVec3()))
        direction.set(lamp.side.rotateFromXN(direction.toVec3()))
        val position = MutVec3(Utils.getVec05(lamp.sixNode!!.coordinate))
        val coord = Coordinate(lamp.sixNode!!.coordinate)
        for (idx in 0 until range) {
            move(position, direction, 1)
            coord.setPosition(position.toVec3())
            if (!coord.blockExist) return null
            if (isOpaque(coord)) {
                move(position, direction, -1)
                coord.setPosition(position.toVec3())
                break
            }
        }
        return Pair(coord, position)
    }

    private fun move(position: MutVec3, direction: MutVec3, sign: Int) {
        position.xCoord += sign * direction.xCoord
        position.yCoord += sign * direction.yCoord
        position.zCoord += sign * direction.zCoord
    }

    override fun process(time: Double) {
        var newLight: Int
        val lampStack = lamp.inventory!!.getStackInSlot(0)
        val cableStack = lamp.inventory!!.getStackInSlot(LampSocketContainer.cableSlotId)
        // LampDescriptor? is *important* here. Otherwise, NPE.
        val lampDescriptor = Utils.getItemObject(lampStack) as? LampDescriptor
        if (cableStack == null || lampStack == null) {
            /*
            Cable slot and lamp slot are empty. This means no light, and disconnect from lamp supply.
             */
            stableProb = 0.0
            lamp.setIsConnectedToLampSupply(false)
        } else {
            /*
            Cable slot and lamp slot have stuff. This means there can be light.
             */
            if (lamp.poweredByLampSupply) {
                // Powered by a lamp supply.
                val lampSupplyList = findBestSupply(lamp.sixNode!!.coordinate)
                val bestLampSupply = lampSupplyList?.second
                if (bestLampSupply != null && lampDescriptor != null && bestLampSupply.element.getChannelState(bestLampSupply.id)) {
                    bestLampSupply.element.addToRp(lampDescriptor.r)
                    lamp.positiveLoad.state = bestLampSupply.element.powerLoad.state
                } else {
                    lamp.positiveLoad.state = 0.0
                }

                lamp.setIsConnectedToLampSupply(bestLampSupply != null)
            }
            else
            {
                // Not powered by a lamp supply.
                lamp.setIsConnectedToLampSupply(false)
            }
        }

        lamp.computeElectricalLoad()
        if (lampStack != lampStackLast) {
            stableProb = 0.0
        }

        if (stableProb < 0) stableProb = 0.0
        var lightDouble = 0.0

        if (lampDescriptor != null) {
            // This code makes the ECO lights blink, and the other lights are just "stable"
            when (lampDescriptor.type) {
                LampDescriptor.Type.INCANDESCENT, LampDescriptor.Type.LED, LampDescriptor.Type.HALOGEN -> {
                    if (lamp.lampResistor.voltage < lampDescriptor.minimalU) {
                        lightDouble = 0.0
                    } else {
                        lightDouble = lampDescriptor.nominalLight * ((Math.abs(lamp.lampResistor.voltage) - lampDescriptor.minimalU) / (lampDescriptor.nominalU - lampDescriptor.minimalU))
                    }
                    lightDouble *= 15
                }
                LampDescriptor.Type.ECO -> {
                    val U = Math.abs(lamp.lampResistor.voltage)
                    if (U < lampDescriptor.minimalU) {
                        stableProb = 0.0
                        lightDouble = 0.0
                    } else {
                        val powerFactor = U / lampDescriptor.nominalU
                        stableProb += U / lampDescriptor.stableU * time / lampDescriptor.stableTime * lampDescriptor.stableUNormalised
                        if (stableProb > U / lampDescriptor.stableU) stableProb = U / lampDescriptor.stableU
                        if (Math.random() > stableProb) {
                            lightDouble = 0.0
                        } else {
                            lightDouble = lampDescriptor.nominalLight * powerFactor
                            lightDouble *= 16
                        }
                    }
                }
            }
        } else {
            // there is no light bulb. Light = 0
            lightDouble = 0.0
        }

        newLight = lightDouble.toInt()
        //light.coerceIn(0, 15) // IT F'ING LIES
        if (newLight > 15) newLight = 15
        if (newLight < 0) newLight = 0

        if (lampDescriptor != null) {
            val bulbCanAge = when(lampDescriptor.type) {
                LampDescriptor.Type.INCANDESCENT -> !Eln.incandescentLampInfiniteLife && !SaveConfig.instance!!.infiniteIncandescentLife
                LampDescriptor.Type.ECO -> !Eln.ecoLampInfiniteLife && !SaveConfig.instance!!.infiniteEcoBulbLife
                LampDescriptor.Type.LED -> !Eln.ledLampInfiniteLife && !SaveConfig.instance!!.infiniteLedBulbLife
                LampDescriptor.Type.HALOGEN -> !Eln.halogenLampInfiniteLife && !SaveConfig.instance!!.infiniteHalogenBulbLife
            }

            if (bulbCanAge) {
                ageTime += time
                // A changed stack has to be sent to all who see it, so not every tick.
                val currentLife = if (ageTime < 1.0) lampDescriptor.getLifeInTag(lampStack!!) else {
                    val life = lampDescriptor.ageLamp(lampStack!!, lamp.lampResistor.voltage, ageTime)
                    ageTime = 0.0
                    life
                }

                if (currentLife <= 0.0) {
                    lamp.inventory!!.setInventorySlotContents(0, null)
                    lamp.inventory!!.markDirty()
                    newLight = 0
                }
            }
        } else {
            newLight = 0
        }

        if (lamp.coordinate!!.blockExist && lampDescriptor != null && lampDescriptor.vegetableGrowRate != 0.0) {
            updateNearbyBlocks(lampDescriptor.vegetableGrowRate, lampDescriptor.nominalLight, newLight, time)
        }

        boot = false
        lampStackLast = lampStack
        // A lamp that stays dark has no light to put anywhere.
        if (newLight != 0 || light != 0) placeSpot(newLight)
        if (light != newLight)
            lamp.needPublish()
    }

    fun placeSpot(newLight: Int) {
        if (!lbCoord.blockExist) return
        val direction = MutVec3(1.0, 0.0, 0.0)
        direction.rotateAroundZ((alphaZ * Math.PI / 180.0).toFloat())
        val (newCoord, position) = raytrace(direction, lamp.socketDescriptor.range) ?: return
        var count = 0
        while (newCoord != lamp.sixNode!!.coordinate) {
            val block = newCoord.block
            if (newCoord.state.isAir || block === Eln.lightBlock) {
                count++
                if (count == 2) break
            }
            move(position, direction, -1)
            newCoord.setPosition(position.toVec3())
        }
        setLightAt(newCoord, newLight)
    }

    private fun isOpaque(coord: Coordinate): Boolean {
        val block = coord.block
        return block.isOpaqueCube && block !== Blocks.FARMLAND
    }

    fun setLightAt(coord: Coordinate, value: Int) {
        val oldLbCoord = lbCoord
        lbCoord = Coordinate(coord)
        val oldLight = light
        val same = coord == oldLbCoord
        light = value
        if (!same && oldLbCoord == lamp.sixNode!!.coordinate) {
            lamp.sixNode!!.recalculateLightValue()
        }
        if (lbCoord == lamp.sixNode!!.coordinate) {
            if (light != oldLight || !same) lamp.sixNode!!.recalculateLightValue()
        } else if (light > 0) {
            LightBlockEntity.addLight(lbCoord, light, 5)
        }
        if (light != oldLight) {
            val bos = ByteArrayOutputStream(64)
            val packet = DataOutputStream(bos)
            lamp.preparePacketForClient(packet)
            try {
                packet.writeByte(light)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            lamp.sendPacketToAllClient(bos)
        }
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        stableProb = nbt.getDouble(str + "LSP" + "stableProb")
        lbCoord.readFromNBT(nbt, str + "lbCoordInst")
        alphaZ = nbt.getFloat(str + "alphaZ").toDouble()
        light = nbt.getInt(str + "light")
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        nbt.putDouble(str + "LSP" + "stableProb", stableProb)
        lbCoord.writeToNBT(nbt, str + "lbCoordInst")
        nbt.putFloat(str + "alphaZ", alphaZ.toFloat())
        nbt.putInt(str + "light", light)
    }

    val blockLight: Int
        get() = if (lbCoord == lamp.sixNode!!.coordinate) {
            light
        } else {
            0
        }

    init {
        lbCoord = Coordinate(lamp.sixNode!!.coordinate)
    }
}
