package mods.eln.gametest

import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * Measures what a large base costs the server. Does nothing unless started with `-Deln.stress=<number of circuits>`,
 * for example through `JAVA_TOOL_OPTIONS`.
 */
@GameTestHolder(Eln.MODID)
@PrefixGameTestTemplate(false)
object ElnStressTest {
    private val CIRCUITS = Integer.getInteger("eln.stress", 0)
    private const val SPINE = 24
    private const val TOOTH = 4
    private const val WARM_UP = 200
    private const val MEASURED = 400

    /** Where to record what the server does while it is measured, for Java Mission Control or `jfr print`. */
    private val RECORDING: String? = System.getProperty("eln.stressJfr")
    private var recording: jdk.jfr.Recording? = null

    private val began = LongArray(WARM_UP + MEASURED)
    private val took = LongArray(WARM_UP + MEASURED)
    private var tick = -1

    @GameTest(template = "empty", timeoutTicks = 100000, batch = "stress")
    @JvmStatic
    fun stress(helper: GameTestHelper) {
        if (CIRCUITS == 0) {
            helper.succeed()
            return
        }
        @Suppress("DEPRECATION")
        val player = helper.makeMockServerPlayerInLevel()
        val level = helper.level
        val origin = helper.absolutePos(BlockPos(64, 2, 64))

        // Those of earlier runs are still in the world.
        NodeManager.instance!!.unload(mods.eln.compat.dimensionId(level.dimension()))
        val start = System.nanoTime()
        for (n in 0 until CIRCUITS) circuit(level, player, origin.offset(n % 10 * (SPINE + 4), 0, n / 10 * (2 * TOOTH + 4)), n)
        everything(level, player, origin.offset(0, 0, (CIRCUITS / 10 + 2) * (2 * TOOTH + 4)))
        Eln.logger.info("STRESS {} nodes placed in {} ms", NodeManager.instance!!.nodeList.size, (System.nanoTime() - start) / 1000000)

        tick = 0
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ServerTickEvent.Pre::class.java) {
            if (tick in 0 until took.size) began[tick] = System.nanoTime()
            // The simulator tells what takes how long, every second.
            if (tick == WARM_UP && System.getProperty("eln.stressDebug") != null) Eln.debugEnabled = true
            if (tick == WARM_UP && RECORDING != null && recording == null) {
                recording = jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile")).apply { start() }
            }
        }
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerTickEvent.Post::class.java) {
            if (tick in 0 until took.size && began[tick] != 0L) {
                took[tick] = System.nanoTime() - began[tick]
                tick++
            }
        }
        helper.runAfterDelay((WARM_UP + MEASURED + 20).toLong()) {
            recording?.let {
                it.stop()
                it.dump(java.nio.file.Path.of(RECORDING!!))
                it.close()
                recording = null
            }
            val sorted = took.copyOfRange(WARM_UP, took.size).sorted()
            Eln.logger.info(
                "STRESS first tick {} ms; then average {} us, median {} us, 99% {} us, {} subsystems",
                took[0] / 1000000, sorted.average().toLong() / 1000, sorted[sorted.size / 2] / 1000,
                sorted[sorted.size * 99 / 100] / 1000, Eln.simulator.mna.subSystemCount
            )
            // The cost of taking a cable out of a circuit and putting it back.
            val cable = origin.offset(SPINE / 2, 0, 0)
            val before = System.nanoTime()
            repeat(50) {
                level.removeBlock(cable, false)
                Eln.simulator.mna.step()
                place(level, player, "Low Voltage Cable", cable)
                Eln.simulator.mna.step()
            }
            Eln.logger.info("STRESS rebuilding a circuit takes {} us", (System.nanoTime() - before) / 100000)
            repeat(3) {
                val t0 = System.nanoTime()
                val nbt = net.minecraft.nbt.CompoundTag()
                mods.eln.server.ServerEventListener.writeToEaWorldNBT(nbt, 0)
                val t1 = System.nanoTime()
                val bytes = java.io.ByteArrayOutputStream()
                net.minecraft.nbt.NbtIo.writeCompressed(nbt, bytes)
                val t2 = System.nanoTime()
                Eln.logger.info("STRESS saving: collecting {} ms, compressing {} ms, {} KiB", (t1 - t0) / 1000000, (t2 - t1) / 1000000, bytes.size() / 1024)
            }
            helper.succeed()
        }
    }

    private fun place(level: ServerLevel, player: ServerPlayer, name: String, pos: BlockPos) {
        if (level.getBlockState(pos.below()).isAir) level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2)
        Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(name, 1), player, level, pos.x, pos.y, pos.z, 1)
    }

    /** A comb: a source, a spine of cables, and teeth with a load at the end. Some loads change all the time. */
    private fun circuit(level: ServerLevel, player: ServerPlayer, origin: BlockPos, n: Int) {
        for (x in -1..SPINE) for (z in -TOOTH - 1..TOOTH + 1) level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2)
        place(level, player, "Electrical Source", origin.west())
        for (x in 0 until SPINE) {
            place(level, player, "Low Voltage Cable", origin.offset(x, 0, 0))
            if (x % 4 != 1) continue
            val direction = if (x % 8 == 1) 1 else -1
            for (z in 1 until TOOTH) place(level, player, "Low Voltage Cable", origin.offset(x, 0, z * direction))
            val load = when {
                n % 4 == 0 && x % 8 == 1 -> "10A Diode"
                n % 4 == 1 -> "Power Capacitor"
                else -> "Creative Power Resistor"
            }
            place(level, player, load, origin.offset(x, 0, TOOTH * direction))
            place(level, player, "Ground Cable", origin.offset(x, 0, (TOOTH + 1) * direction))
        }
        val node = NodeManager.instance!!.nodeList.filterIsInstance<SixNode>().firstOrNull {
            it.coordinate.x == origin.x - 1 && it.coordinate.y == origin.y && it.coordinate.z == origin.z
        }
        (node?.getElement(Direction.YN) as? ElectricalSourceElement)?.voltageSource?.setVoltage(50.0)
    }

    private fun everything(level: ServerLevel, player: ServerPlayer, origin: BlockPos) {
        var n = 0
        for ((_, descriptor) in Eln.sixNodeItem.subItemList.toSortedMap()) {
            val pos = origin.offset(n % 40 * 2, 0, n / 40 * 3)
            n++
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2)
            Eln.sixNodeItem.placeBlockAt(descriptor.newItemStack(1), player, level, pos.x, pos.y, pos.z, 1)
        }
        n = 0
        for ((_, descriptor) in Eln.transparentNodeItem.subItemList.toSortedMap()) {
            val pos = origin.offset(n % 12 * 9, 0, 40 + n / 12 * 14)
            n++
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2)
            Eln.transparentNodeItem.placeBlockAt(descriptor.newItemStack(1), player, level, pos.x, pos.y, pos.z, 1)
        }
    }
}
