package mods.eln.gametest

import mods.eln.Eln
import mods.eln.item.ThermalCamera
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeElement
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sim.renewable.PmgWindSource
import mods.eln.sim.renewable.PvCurveSource
import mods.eln.sixnode.electricalbreaker.ElectricalBreakerContainer
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.sixnode.mppt.MpptControllerElement
import mods.eln.sixnode.oscilloscope.OscilloscopeElement
import mods.eln.sixnode.protection.BatteryManagementElement
import mods.eln.sixnode.protection.BatteryManagementLogic
import mods.eln.sixnode.protection.OvercurrentRelayElement
import mods.eln.sixnode.protection.OvercurrentRelayLogic
import mods.eln.sixnode.protection.SwitchgearElement
import mods.eln.sixnode.protection.TransferSwitchElement
import mods.eln.sixnode.protection.TransferSwitchLogic
import mods.eln.transparentnode.battery.BatteryElement
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs
import net.minecraft.core.Direction as McDirection

/** The parts added on the expansion-testing branch, wired up in a world. */
@GameTestHolder(Eln.MODID)
@PrefixGameTestTemplate(false)
object ElnExpansionTests {
    private const val EMPTY = "empty"

    @Suppress("DEPRECATION")
    private fun player(helper: GameTestHelper): ServerPlayer = helper.makeMockServerPlayerInLevel()

    private fun floor(helper: GameTestHelper) {
        for (x in 0..10) for (z in 0..10) helper.setBlock(BlockPos(x, 0, z), Blocks.STONE)
    }

    private fun placeSix(helper: GameTestHelper, player: ServerPlayer, name: String, pos: BlockPos): SixNode {
        val p = helper.absolutePos(pos)
        val placed = Eln.sixNodeItem.placeBlockAt(Eln.findItemStack(name, 1), player, helper.level, p.x, p.y, p.z, 1)
        helper.assertTrue(placed, "$name was not placed")
        return NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x, p.y, p.z, helper.level)) as SixNode
    }

    private fun placeTransparent(helper: GameTestHelper, player: ServerPlayer, name: String, pos: BlockPos): TransparentNode {
        val p = helper.absolutePos(pos)
        val placed = Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(name, 1), player, helper.level, p.x, p.y, p.z, 1)
        helper.assertTrue(placed, "$name was not placed")
        return NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x, p.y, p.z, helper.level)) as TransparentNode
    }

    /** A part on the floor at [pos]; its front points to [front] in the world. */
    private inline fun <reified T : SixNodeElement> part(
        helper: GameTestHelper, player: ServerPlayer, name: String, pos: BlockPos, front: McDirection
    ): T {
        val element = placeSix(helper, player, name, pos).getElement(Direction.YN) as T
        element.front = Direction.YN.getLRDUGoingTo(Direction.from(front))!!
        return element
    }

    /** Where the [lrdu] side of a part on the floor at [pos] leads. */
    private fun beside(pos: BlockPos, lrdu: LRDU): BlockPos = pos.relative(Direction.YN.applyLRDU(lrdu).toForge())

    private fun source(helper: GameTestHelper, player: ServerPlayer, pos: BlockPos, voltage: Double): ElectricalSourceElement {
        val s = placeSix(helper, player, "Electrical Source", pos).getElement(Direction.YN) as ElectricalSourceElement
        s.voltageSource.setVoltage(voltage)
        return s
    }

    private fun withCable(element: SwitchgearElement) {
        element.inventory.setInventorySlotContents(ElectricalBreakerContainer.cableSlotId, Eln.findItemStack("Low Voltage Cable", 1))
        element.inventory.markDirty()
        element.inventoryChanged()
        element.sixNode!!.reconnect()
    }

    /** A short circuit trips the relay at once and it locks out. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun relayTripsOnAShortCircuit(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val pos = BlockPos(5, 1, 5)
        val relay = part<OvercurrentRelayElement>(helper, player, "Overcurrent Protection Relay", pos, McDirection.EAST)
        source(helper, player, beside(pos, relay.front), 50.0)
        placeSix(helper, player, "Ground Cable", beside(pos, relay.front.inverse()))
        withCable(relay)
        relay.logic.close()
        helper.succeedWhen {
            helper.assertTrue(relay.logic.state == OvercurrentRelayLogic.State.LOCKED_OUT,
                "still ${relay.logic.state} at ${relay.contact.current} A")
            helper.assertTrue(relay.logic.lastTripCurrent > relay.logic.effectivePickup * 8,
                "tripped at ${relay.logic.lastTripCurrent} A")
            helper.assertTrue(relay.contact.resistance >= MnaConst.highImpedance, "the contact is still closed")
        }
    }

    /** Below its pickup the relay stays closed and passes the current. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun relayCarriesNormalLoad(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val pos = BlockPos(5, 1, 5)
        val relay = part<OvercurrentRelayElement>(helper, player, "Overcurrent Protection Relay", pos, McDirection.EAST)
        source(helper, player, beside(pos, relay.front), 50.0)
        source(helper, player, beside(pos, relay.front.inverse()), 49.0)
        withCable(relay)
        relay.logic.close()
        helper.runAfterDelay(60) {
            helper.assertTrue(relay.logic.closed, "it tripped: ${relay.logic.state} at ${relay.logic.lastTripCurrent} A")
            helper.assertTrue(abs(relay.contact.current) > 0.1, "no current through it")
            helper.succeed()
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    @JvmStatic
    fun transferSwitchFallsBackAndReturns(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val pos = BlockPos(5, 1, 5)
        val ats = part<TransferSwitchElement>(helper, player, "Automatic Transfer Switch", pos, McDirection.SOUTH)
        val mains = source(helper, player, beside(pos, ats.front.inverse()), 50.0)
        source(helper, player, beside(pos, ats.front.left()), 50.0)
        withCable(ats)
        ats.logic.retransferDelay = 1.0
        helper.startSequence()
            .thenWaitUntil { helper.assertTrue(ats.logic.source == TransferSwitchLogic.Source.PRIMARY, "not on the mains: ${ats.logic.source}") }
            .thenExecute { mains.voltageSource.setVoltage(0.0) }
            .thenWaitUntil {
                helper.assertTrue(ats.logic.source == TransferSwitchLogic.Source.BACKUP, "not on the backup: ${ats.logic.source}")
                helper.assertTrue(abs(ats.outLoad.voltage - 50) < 2, "output at ${ats.outLoad.voltage} V on the backup")
            }
            .thenExecute { mains.voltageSource.setVoltage(50.0) }
            .thenWaitUntil { helper.assertTrue(ats.logic.source == TransferSwitchLogic.Source.PRIMARY, "did not return: ${ats.logic.source}") }
            .thenSucceed()
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun batteryManagementCutsOffAFlatBattery(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val pos = BlockPos(5, 1, 5)
        val bms = part<BatteryManagementElement>(helper, player, "Battery Management System", pos, McDirection.EAST)
        val battery = source(helper, player, beside(pos, bms.front), 40.0)
        withCable(bms)
        helper.startSequence()
            .thenWaitUntil {
                helper.assertTrue(bms.logic.state == BatteryManagementLogic.State.LOW, "not cut off at ${bms.batteryLoad.voltage} V")
                helper.assertTrue(!bms.logic.closed, "still closed")
            }
            .thenExecute { battery.voltageSource.setVoltage(51.0) }
            .thenWaitUntil {
                helper.assertTrue(bms.logic.state == BatteryManagementLogic.State.NORMAL, "still ${bms.logic.state}")
                helper.assertTrue(abs(bms.loadLoad.voltage - 51) < 1, "load side at ${bms.loadLoad.voltage} V")
            }
            .thenSucceed()
    }

    /** From a 100 V source into a 50 V "battery", across the voltages, as a buck converter does. */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    @JvmStatic
    fun mpptChargesABatteryFromAHigherVoltage(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val pos = BlockPos(5, 1, 5)
        val mppt = part<MpptControllerElement>(helper, player, "MPPT Charge Controller", pos, McDirection.EAST)
        source(helper, player, beside(pos, mppt.front), 100.0)
        source(helper, player, beside(pos, mppt.front.inverse()), 50.0)
        mppt.sixNode!!.reconnect()
        helper.succeedWhen {
            helper.assertTrue(mppt.outputPower > 500, "gives ${mppt.outputPower} W from ${mppt.inputPower} W, ${mppt.logic.mode}")
            helper.assertTrue(mppt.outputPower <= mppt.inputPower, "makes energy: ${mppt.outputPower} W from ${mppt.inputPower} W")
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun oscilloscopeRecordsAVoltage(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val pos = BlockPos(5, 1, 5)
        val scope = part<OscilloscopeElement>(helper, player, "Oscilloscope", pos, McDirection.EAST)
        source(helper, player, beside(pos, scope.front), 42.0)
        scope.sixNode!!.reconnect()
        helper.succeedWhen {
            helper.assertTrue(scope.trace.count >= 3, "only ${scope.trace.count} points")
            val last = scope.trace.get(0, scope.trace.count - 1)
            helper.assertTrue(abs(last - 42) < 1, "channel 1 reads $last V")
            helper.assertTrue(scope.trace.scale(0) == 50.0, "scale ${scope.trace.scale(0)}")
        }
    }

    /**
     * Into a real battery, which floats: its + on the back of the controller and its - on ground, which is the
     * controller's common negative. It finds the battery's voltage by itself.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    @JvmStatic
    fun mpptChargesARealBattery(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val batteryPos = BlockPos(5, 1, 5)
        val battery = placeTransparent(helper, player, "Lithium-Ion Battery", batteryPos).element as BatteryElement
        val plus = battery.front.left().toForge()
        val mpptPos = batteryPos.relative(plus)
        val mppt = part<MpptControllerElement>(helper, player, "MPPT Charge Controller", mpptPos, plus)
        source(helper, player, beside(mpptPos, mppt.front), 100.0)
        placeSix(helper, player, "Ground Cable", batteryPos.relative(battery.front.right().toForge()))
        mppt.sixNode!!.reconnect()
        battery.batteryProcess.charge = 0.5
        val start = 0.5
        helper.startSequence()
            .thenWaitUntil {
                helper.assertTrue(mppt.bankSize == 1, "found ${mppt.bankSize} batteries at ${mppt.outSource.restVoltage} V")
                helper.assertTrue(mppt.outputPower > 200, "gives ${mppt.outputPower} W at ${mppt.outLoad.voltage} V, ${mppt.logic.mode}")
                helper.assertTrue(battery.batteryProcess.charge > start, "the battery is not charging: ${battery.batteryProcess.charge}")
                helper.assertTrue(mppt.outLoad.voltage <= mppt.effectiveChargeVoltage + 0.5, "charges at ${mppt.outLoad.voltage} V")
            }
            .thenSucceed()
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    @JvmStatic
    fun chemistryBatteriesFollowTheirCurves(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val names = listOf("Lead-Acid Battery", "Nickel-Cadmium Battery", "Lithium-Ion Battery", "LiFePO4 Battery")
        val batteries = names.mapIndexed { i, n -> placeTransparent(helper, player, n, BlockPos(1 + 2 * i, 1, 5)).element as BatteryElement }
        helper.runAfterDelay(20) {
            for ((i, b) in batteries.withIndex()) {
                val chemistry = b.descriptor.chemistry
                helper.assertTrue(chemistry != null && b.chemistryProcess != null, "${names[i]} has no chemistry")
                val expected = chemistry!!.voltageFunction.getValue(b.batteryProcess.charge) * b.descriptor.electricalU
                helper.assertTrue(abs(b.batteryProcess.u - expected) < 0.5, "${names[i]}: ${b.batteryProcess.u} V, expected $expected")
                helper.assertTrue(b.chemistryProcess!!.celsius in -40.0..60.0, "${names[i]} at ${b.chemistryProcess!!.celsius} °C")
                // Unused, it does not age, whatever its temperature.
                helper.assertTrue(b.batteryProcess.life == 1.0, "${names[i]} aged to ${b.batteryProcess.life} while idle")
            }
            helper.succeed()
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    @JvmStatic
    fun thermalCameraSeesNearbyParts(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val node = placeTransparent(helper, player, "Lead-Acid Battery", BlockPos(5, 1, 5))
        val p = helper.absolutePos(BlockPos(5, 2, 7))
        player.moveTo(p.x + 0.5, p.y.toDouble(), p.z + 0.5)
        helper.runAfterDelay(5) {
            val t = ThermalCamera.hottest(node)
            helper.assertTrue(t != null && t in -40.0..60.0, "the battery reads $t °C")
            val c = node.coordinate
            helper.assertTrue(ThermalCamera.scan(player).any { it.x == c.x && it.y == c.y && it.z == c.z }, "the camera does not see it")
            helper.succeed()
        }
    }

    private fun field(o: Any, name: String): Any? {
        var c: Class<*>? = o.javaClass
        while (c != null) {
            try {
                return c.getDeclaredField(name).apply { isAccessible = true }.get(o)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    @JvmStatic
    fun newGeneratorsUseTheirPhysics(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val turbine = placeTransparent(helper, player, "Permanent-Magnet Wind Turbine", BlockPos(2, 1, 5)).element!!
        helper.assertTrue(field(turbine, "powerSource") is PmgWindSource, "the wind turbine has no rotor")
        val panel = placeTransparent(helper, player, "Monocrystalline Solar Panel", BlockPos(7, 1, 5)).element!!
        helper.assertTrue(field(panel, "powerSource") is PvCurveSource, "the solar panel has no curve")
        helper.succeed()
    }
}
