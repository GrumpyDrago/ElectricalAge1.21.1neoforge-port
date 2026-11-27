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
import mods.eln.transparentnode.battery.BatteryDescriptor
import mods.eln.transparentnode.battery.BatteryElement
import mods.eln.sixnode.breakerbox.BreakerBoxElement
import mods.eln.sixnode.breakerbox.BreakerBoxLogic
import mods.eln.sixnode.electricalcable.ElectricalCableElement
import mods.eln.sixnode.electricalcable.PowerBundle
import mods.eln.sixnode.electricalcable.PowerBundleCableElement
import net.minecraft.world.entity.EntityType
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

    /** A power cable on the floor at [pos], painted [color] (a brush color; 0, black, is unpainted). */
    private fun cable(helper: GameTestHelper, player: ServerPlayer, name: String, pos: BlockPos, color: Int): ElectricalCableElement {
        val element = placeSix(helper, player, name, pos).getElement(Direction.YN) as ElectricalCableElement
        ElectricalCableElement::class.java.getDeclaredField("color").apply { isAccessible = true }.setInt(element, color)
        element.sixNode!!.reconnect()
        return element
    }

    /**
     * Supply, breaker box, two lengths of bundle and a red cable out of it to a slightly lower source: the current
     * flows on the red line only. An unpainted cable on the bundle is on the black line, which is dead once its
     * breaker is off, as the source right beside the bundle does not connect to it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun bundleCarriesEachColorOnItsOwnLine(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val boxPos = BlockPos(2, 1, 5)
        source(helper, player, BlockPos(1, 1, 5), 50.0)
        val box = part<BreakerBoxElement>(helper, player, "200V Breaker Box", boxPos, McDirection.WEST)
        val first = placeSix(helper, player, "200V Power Bundle Cable", BlockPos(3, 1, 5)).getElement(Direction.YN) as PowerBundleCableElement
        val second = placeSix(helper, player, "200V Power Bundle Cable", BlockPos(4, 1, 5)).getElement(Direction.YN) as PowerBundleCableElement
        val red = cable(helper, player, "Medium Voltage Cable", BlockPos(5, 1, 5), RED)
        // A bundle used as a plain multi-core cable, away from the box.
        val lone = placeSix(helper, player, "200V Power Bundle Cable", BlockPos(8, 1, 8)).getElement(Direction.YN) as PowerBundleCableElement
        placeSix(helper, player, "200V Power Bundle Cable", BlockPos(8, 1, 9))
        source(helper, player, BlockPos(6, 1, 5), 49.0)
        val black = cable(helper, player, "Medium Voltage Cable", BlockPos(4, 1, 6), 0)
        source(helper, player, BlockPos(3, 1, 4), 30.0)
        box.sixNode!!.reconnect()
        helper.startSequence()
            .thenWaitUntil {
                // Every breaker is on: each line is live from the supply, but only the red one carries current.
                helper.assertTrue(abs(red.electricalLoad.current) > 1, "no current on the red line: ${red.electricalLoad.current} A")
                helper.assertTrue(abs(first.bundleLoads[RED].voltage - 49.5) < 1, "red line at ${first.bundleLoads[RED].voltage} V")
                helper.assertTrue(abs(black.electricalLoad.voltage - 50) < 0.5, "the black cable is at ${black.electricalLoad.voltage} V")
                helper.assertTrue(abs(black.electricalLoad.current) < 0.01, "current on the black line: ${black.electricalLoad.current} A")
                // Fed by the box, both lengths are kept from overheating; the lone one is not.
                helper.assertTrue(first.fedByBreakerBox && second.fedByBreakerBox, "the bundle does not see the box")
                helper.assertTrue(!PowerBundle.reachesBreakerBox(lone), "the lone bundle thinks a box feeds it")
            }
            .thenExecute { box.toggle(0) }
            .thenWaitUntil {
                helper.assertTrue(abs(black.electricalLoad.voltage) < 0.5, "the black cable is at ${black.electricalLoad.voltage} V")
                helper.assertTrue(abs(first.bundleLoads[RED].voltage - 49.5) < 1, "red line at ${first.bundleLoads[RED].voltage} V")
                helper.assertTrue(box.logic.state.withIndex().all { it.index == 0 || it.value == BreakerBoxLogic.State.ON },
                    "a breaker tripped: ${box.logic.state.toList()}")
            }
            .thenSucceed()
    }

    /** A short circuit on one line trips its breaker at once, the others stay on, and the arc burns a pig nearby. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun breakerBoxTripsOnAShortCircuitWithAnArcFlash(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val boxPos = BlockPos(2, 1, 5)
        source(helper, player, BlockPos(1, 1, 5), 200.0)
        val box = part<BreakerBoxElement>(helper, player, "200V Breaker Box", boxPos, McDirection.WEST)
        placeSix(helper, player, "200V Power Bundle Cable", BlockPos(3, 1, 5))
        cable(helper, player, "Medium Voltage Cable", BlockPos(4, 1, 5), RED)
        placeSix(helper, player, "Ground Cable", BlockPos(5, 1, 5))
        box.sixNode!!.reconnect()
        val pig = helper.spawn(EntityType.PIG, BlockPos(2, 1, 7))
        val health = pig.health
        helper.startSequence()
            .thenWaitUntil {
                helper.assertTrue(box.logic.state[RED] == BreakerBoxLogic.State.TRIPPED, "the red breaker is ${box.logic.state[RED]}")
                helper.assertTrue(box.logic.tripCurrent[RED] > BreakerBoxLogic.MAGNETIC_TRIP * box.logic.ratedCurrent(RED),
                    "tripped at ${box.logic.tripCurrent[RED]} A")
                helper.assertTrue(box.logic.state.withIndex().all { it.index == RED || it.value == BreakerBoxLogic.State.ON },
                    "others tripped: ${box.logic.state.toList()}")
            }
            .thenWaitUntil { helper.assertTrue(pig.health < health || pig.isOnFire, "the pig was not hurt: ${pig.health}") }
            .thenSucceed()
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    @JvmStatic
    fun batteriesStayCoolAtRatedPower(helper: GameTestHelper) {
        for (name in listOf("Cost Oriented Battery", "Capacity Oriented Battery", "Lead-Acid Battery", "Lithium-Ion Battery")) {
            val d = Eln.transparentNodeItem.getDescriptor(Eln.findItemStack(name, 1)) as BatteryDescriptor
            val current = d.electricalStdP / d.electricalU
            val rise = current * current * d.electricalRs * 2 * d.thermalRp
            // Steady state at rated power, in a hot place: under the limit with room to spare.
            helper.assertTrue(BatteryDescriptor.DESIGN_AMBIENT + rise < d.thermalWarmLimit - 5,
                "$name rises $rise °C at its rated power")
        }
        helper.succeed()
    }

    /** The 800 V box feeds an 800 V circuit through the 800 V bundle, and nothing burns. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun highVoltageBreakerBoxCarries800Volts(helper: GameTestHelper) =
        breakerBoxCircuit(helper, "800V Breaker Box", "800V Power Bundle Cable", "High Voltage Cable", 800.0, 799.0)

    /** The 50 V box and bundle with a 50 V circuit. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun lowVoltageBreakerBoxCarries50Volts(helper: GameTestHelper) =
        breakerBoxCircuit(helper, "50V Breaker Box", "50V Power Bundle Cable", "Low Voltage Cable", 50.0, 49.5)

    private fun breakerBoxCircuit(helper: GameTestHelper, boxName: String, bundleName: String, cableName: String, voltage: Double, end: Double) {
        val player = player(helper)
        floor(helper)
        val boxPos = BlockPos(2, 1, 5)
        source(helper, player, BlockPos(1, 1, 5), voltage)
        val box = part<BreakerBoxElement>(helper, player, boxName, boxPos, McDirection.WEST)
        val bundle = placeSix(helper, player, bundleName, BlockPos(3, 1, 5)).getElement(Direction.YN) as PowerBundleCableElement
        val red = cable(helper, player, cableName, BlockPos(4, 1, 5), RED)
        // A little below the supply, so a few amps flow, within the rating of the breakers.
        source(helper, player, BlockPos(5, 1, 5), end)
        box.sixNode!!.reconnect()
        helper.runAfterDelay(60) {
            helper.assertTrue(abs(red.electricalLoad.current) > 1, "no current: ${red.electricalLoad.current} A")
            helper.assertTrue(bundle.bundleLoads[RED].voltage > voltage * 0.985, "red line at ${bundle.bundleLoads[RED].voltage} V")
            helper.assertTrue(box.logic.state.all { it == BreakerBoxLogic.State.ON }, "a breaker tripped: ${box.logic.state.toList()}")
            helper.assertTrue(NodeManager.instance!!.getNodeFromCoordonate(box.sixNode!!.coordinate) != null, "the box burned out")
            helper.succeed()
        }
    }

    /** A 50 V box fed 200 V burns out. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun lowVoltageBreakerBoxBurnsOutOnTooHighAVoltage(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val boxPos = BlockPos(2, 1, 5)
        source(helper, player, BlockPos(1, 1, 5), 200.0)
        val box = part<BreakerBoxElement>(helper, player, "50V Breaker Box", boxPos, McDirection.WEST)
        val coordinate = box.sixNode!!.coordinate
        box.sixNode!!.reconnect()
        helper.succeedWhen {
            helper.assertTrue(NodeManager.instance!!.getNodeFromCoordonate(coordinate)?.let { (it as SixNode).getElement(Direction.YN) } !is BreakerBoxElement,
                "the 50 V box takes 200 V")
        }
    }

    /**
     * An overheated battery vents instead of exploding: it is still there, cut off and with less life, and a bucket of
     * water brings it back.
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    @JvmStatic
    fun overheatedBatteryVentsAndIsCooledWithWater(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val node = placeTransparent(helper, player, "Cost Oriented Battery", BlockPos(5, 1, 5))
        val battery = node.element as BatteryElement
        val life = battery.batteryProcess.life
        helper.startSequence()
            .thenExecute { battery.thermalLoad.temperatureCelsius = 150.0 }
            .thenWaitUntil { helper.assertTrue(battery.vented, "not vented at ${battery.thermalLoad.temperatureCelsius} degrees above ambient") }
            .thenExecute {
                helper.assertTrue(NodeManager.instance!!.getNodeFromCoordonate(node.coordinate) === node, "the battery is gone")
                helper.assertTrue(battery.positiveLoad.serialResistance >= MnaConst.highImpedance, "still connected")
                helper.assertTrue(battery.batteryProcess.life < life, "lost no life")
                // It stays off however long it waits, until it is cooled.
                battery.thermalLoad.temperatureCelsius = 0.0
            }
            .thenIdle(40)
            .thenExecute { helper.assertTrue(battery.vented, "came back by itself") }
            .thenExecute { battery.coolWithWater() }
            .thenWaitUntil {
                helper.assertTrue(!battery.vented, "still vented")
                helper.assertTrue(battery.positiveLoad.serialResistance < 1, "not reconnected")
            }
            .thenSucceed()
    }

    /**
     * A padlock locks a breaker box off and an electrical breaker open; nobody can switch them on until the lock is
     * taken off, and then they work again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun lockoutKeepsSwitchesOpen(helper: GameTestHelper) {
        val player = player(helper)
        val other = player(helper)
        floor(helper)
        source(helper, player, BlockPos(1, 1, 5), 50.0)
        val box = part<BreakerBoxElement>(helper, player, "200V Breaker Box", BlockPos(2, 1, 5), McDirection.WEST)
        val bundle = placeSix(helper, player, "200V Power Bundle Cable", BlockPos(3, 1, 5)).getElement(Direction.YN) as PowerBundleCableElement
        box.sixNode!!.reconnect()
        val breaker = placeSix(helper, player, "Electrical Breaker", BlockPos(7, 1, 7)).getElement(Direction.YN)
            as mods.eln.sixnode.electricalbreaker.ElectricalBreakerElement
        helper.startSequence()
            .thenWaitUntil { helper.assertTrue(bundle.bundleLoads[RED].voltage > 49, "the line is dead before the lock") }
            .thenExecute {
                box.lockout.use(box, player)
                breaker.lockout.use(breaker, player)
                // Someone else's lock goes on too, and switching on does nothing.
                box.lockout.use(box, other)
                box.toggle(RED)
                box.toggle(RED)
                breaker.setSwitchState(true)
            }
            .thenWaitUntil {
                helper.assertTrue(abs(bundle.bundleLoads[RED].voltage) < 0.5, "the line is live while locked: ${bundle.bundleLoads[RED].voltage} V")
                helper.assertTrue(!breaker.switchState, "the breaker closed while locked")
            }
            .thenExecute {
                // Taking one lock off is not enough.
                box.lockout.use(box, player)
                helper.assertTrue(box.lockout.isLocked, "one lock opened it")
                box.lockout.use(box, other)
                breaker.lockout.use(breaker, player)
                breaker.setSwitchState(true)
            }
            .thenWaitUntil {
                helper.assertTrue(bundle.bundleLoads[RED].voltage > 49, "the line is dead after the locks came off")
                helper.assertTrue(breaker.switchState, "the breaker does not close after the lock came off")
            }
            .thenSucceed()
    }

    /** What a computer sees and does through the eln peripheral: a breaker box switched from afar, and a battery read. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    @JvmStatic
    fun computersReadAndSwitchParts(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        source(helper, player, BlockPos(1, 1, 5), 50.0)
        val box = part<BreakerBoxElement>(helper, player, "200V Breaker Box", BlockPos(2, 1, 5), McDirection.WEST)
        val bundle = placeSix(helper, player, "200V Power Bundle Cable", BlockPos(3, 1, 5)).getElement(Direction.YN) as PowerBundleCableElement
        box.sixNode!!.reconnect()
        val battery = placeTransparent(helper, player, "Cost Oriented Battery", BlockPos(7, 1, 7)).element as BatteryElement
        val line = RED + 1
        helper.startSequence()
            .thenWaitUntil { helper.assertTrue(bundle.bundleLoads[RED].voltage > 49, "the line is dead") }
            .thenExecute {
                helper.assertTrue(box.computerCall("setBreaker", listOf(line.toDouble(), false)) == true, "setBreaker failed")
                helper.assertTrue(box.computerCall("setRating", listOf(line.toDouble(), 9.5)) == 10.0, "setRating did not round to 10 A")
            }
            .thenWaitUntil { helper.assertTrue(abs(bundle.bundleLoads[RED].voltage) < 0.5, "the line is live after switching off") }
            .thenExecute {
                @Suppress("UNCHECKED_CAST")
                val breakers = box.computerValues()["breakers"] as List<Map<String, Any?>>
                helper.assertTrue(breakers[RED]["state"] == "off" && breakers[RED]["rating"] == 10.0, "reads ${breakers[RED]}")
                // Locked out, a computer cannot switch it on.
                box.lockout.use(box, player)
                helper.assertTrue(box.computerCall("setBreaker", listOf(line.toDouble(), true)) == false, "switched on while locked")
                val values = battery.computerValues()
                helper.assertTrue(values["charge"] is Double && values["vented"] == false, "battery reads $values")
            }
            .thenSucceed()
    }

    /**
     * The three tiers: 3.2 kV into a step-up substation, 51.2 kV across to a distribution substation, 12.8 kV out to a
     * utility pole. Then the pole is shorted to the ground: the distribution substation's low voltage breaker trips at
     * once, and nothing burns.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    @JvmStatic
    fun substationsStepUpAndDown(helper: GameTestHelper) {
        val player = player(helper)
        floor(helper)
        val stepUp = placeTransparent(helper, player, "Step-Up Substation", BlockPos(2, 1, 2)).element
            as mods.eln.gridnode.substation.SubstationElement
        val distribution = placeTransparent(helper, player, "Distribution Substation", BlockPos(2, 1, 8)).element
            as mods.eln.gridnode.substation.SubstationElement
        val pole = placeTransparent(helper, player, "Direct Utility Pole", BlockPos(8, 1, 8)).element
            as mods.eln.gridnode.GridElement
        // The 3.2 kV supply in front of the step-up substation.
        val front = BlockPos(2, 1, 2).relative(stepUp.front.toForge())
        source(helper, player, front, 3200.0)
        stepUp.node!!.reconnect()
        val cable = Eln.instance.highVoltageCableDescriptor
        mods.eln.gridnode.GridLink.addLink(stepUp, distribution, stepUp.front.left(), distribution.front.left(), cable, 8)
        mods.eln.gridnode.GridLink.addLink(distribution, pole, distribution.front.right(), Direction.YP, cable, 8)
        helper.startSequence()
            .thenWaitUntil {
                helper.assertTrue(abs(stepUp.highLoad.voltage - 51200) < 1000, "step-up gives ${stepUp.highLoad.voltage} V")
                helper.assertTrue(abs(distribution.lowLoad.voltage - 12800) < 300, "distribution gives ${distribution.lowLoad.voltage} V")
            }
            .thenExecute {
                // Short the pole to the ground.
                val polePos = BlockPos(8, 1, 8)
                placeSix(helper, player, "Ground Cable", polePos.relative(McDirection.NORTH))
                placeSix(helper, player, "Ground Cable", polePos.relative(McDirection.SOUTH))
            }
            .thenWaitUntil {
                helper.assertTrue(distribution.lowRelay.state != mods.eln.sixnode.protection.OvercurrentRelayLogic.State.CLOSED,
                    "the low voltage breaker did not trip: ${distribution.lowRelay.state}")
            }
            .thenExecute {
                helper.assertTrue(distribution.node!!.coordinate.let { NodeManager.instance!!.getNodeFromCoordonate(it) } != null,
                    "the substation burned")
                // Locked out, it will not close.
                distribution.lockout.use(distribution, player)
                helper.assertTrue(!distribution.closeBreaker(false), "closed while locked out")
            }
            .thenSucceed()
    }

    private const val RED = 1
}
