package mods.eln.server

import net.minecraft.nbt.NbtAccounter
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.level.LevelEvent
import mods.eln.compat.*

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import mods.eln.Eln
import mods.eln.environment.RoomThermalManager
import mods.eln.item.electricalitem.TreeCapitation.process
import mods.eln.misc.Coordinate
import mods.eln.misc.Utils
import mods.eln.mqtt.MqttManager
import mods.eln.node.NodeManager
import net.minecraft.world.entity.LightningBolt
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*

class ServerEventListener {
    private var lightningListNext = LinkedList<LightningBolt>()
    private var lightningList = LinkedList<LightningBolt>()
    @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        lightningList = lightningListNext
        lightningListNext = LinkedList()
        process(0.05)
    }

    @SubscribeEvent
    fun onNewEntity(event: EntityJoinLevelEvent) {
        val entity = event.entity
        if (entity is LightningBolt && !event.level.isClientSide) lightningListNext.add(entity)
    }

    fun clear() {
        lightningList.clear()
    }

    fun getLightningClosestTo(c: Coordinate): Double {
        var best = 10000000.0
        for (l in lightningList) {
            if (c.world() !== l.worldObj) continue
            val d = l.position().distanceTo(Vec3(c.x.toDouble(), c.y.toDouble(), c.z.toDouble()))
            if (d < best) best = d
        }
        return best
    }

    private val loadedWorlds = HashSet<Int>()
    @SubscribeEvent
    fun onWorldLoad(e: LevelEvent.Load) {
        val world = e.level as? Level ?: return
        if (world.isClientSide) return
        loadedWorlds.add(world.dimensionId)
        val fileNames = FileNames(world)
        val dimension = world.dimensionId
        try {
            if (!Files.exists(fileNames.worldSave) && !Files.exists(fileNames.backupSave)) return
            readSave(fileNames.worldSave, dimension)
        } catch (ex: Exception) {
            try {
                ex.printStackTrace()
                println("Using BACKUP Electrical Age save: " + fileNames.backupSave)
                readSave(fileNames.backupSave, dimension)
            } catch (ex2: Exception) {
                ex2.printStackTrace()
                println("Failed to read backup save!")
            }
        }
    }

    @Throws(IOException::class)
    private fun readSave(worldSave: Path, dimension: Int) {
        val inputStream = ByteArrayInputStream(Files.readAllBytes(worldSave))
        val nbt = NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap())
        readFromEaWorldNBT(nbt, dimension)
    }

    @SubscribeEvent
    fun onWorldUnload(e: LevelEvent.Unload) {
        val world = e.level as? Level ?: return
        if (world.isClientSide) return
        loadedWorlds.remove(world.dimensionId)
        try {
            NodeManager.instance!!.unload(world.dimensionId)
            Eln.ghostManager.unload(world.dimensionId)
            RoomThermalManager.unloadDimension(world.dimensionId)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @SubscribeEvent
    fun onWorldSave(e: LevelEvent.Save) {
        val world = e.level as? Level ?: return
        if (world.isClientSide) return
        if (!loadedWorlds.contains(world.dimensionId)) {
            //System.out.println("I hate you minecraft");
            return
        }
        try {
            val nbt = CompoundTag()
            writeToEaWorldNBT(nbt, world.dimensionId)
            val fileNames = FileNames(world)

            // Write a new save to a temporary file.
            val bytes = ByteArrayOutputStream(512 * 1024)
            NbtIo.writeCompressed(nbt, bytes)
            Files.write(fileNames.tempSave, bytes.toByteArray())

            // Replace backup save with old save, and old save with new one.
            if (Files.exists(fileNames.worldSave)) replaceFile(fileNames.worldSave, fileNames.backupSave)
            replaceFile(fileNames.tempSave, fileNames.worldSave)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun replaceFile(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private inner class FileNames internal constructor(world: Level) {
        val worldSave: Path
        val tempSave: Path
        val backupSave: Path
        init {
            worldSave = Utils.getMapFile("data/electricalAgeWorld" + world.dimensionId + ".dat").toPath()
            tempSave = worldSave.resolveSibling(worldSave.fileName.toString() + ".tmp")
            backupSave = worldSave.resolveSibling(worldSave.fileName.toString() + ".bak")
            Files.createDirectories(worldSave.parent)
        }
    }

    companion object {
        /** True while the nodes of a world are being read. */
        @JvmStatic
        var loading = false
            private set

        fun readFromEaWorldNBT(nbt: CompoundTag, dim: Int) {
            loading = true
            try {
                NodeManager.instance!!.loadFromNbt(nbt.getCompound("nodes"))
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loading = false
            }
            try {
                Eln.ghostManager.loadFromNBT(nbt.getCompound("ghost"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                MqttManager.readWorldData(nbt.getCompound("mqtt"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                RoomThermalManager.readFromNbt(nbt, dim)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun writeToEaWorldNBT(nbt: CompoundTag?, dim: Int) {
            try {
                NodeManager.instance!!.saveToNbt(Utils.newNbtTagCompund(nbt, "nodes"), dim)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                Eln.ghostManager.saveToNBT(Utils.newNbtTagCompund(nbt, "ghost"), dim)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val tag = Utils.newNbtTagCompund(nbt, "mqtt")
                MqttManager.writeWorldData(tag)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                RoomThermalManager.writeToNbt(nbt!!, dim)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        NeoForge.EVENT_BUS.register(this)
    }
}
