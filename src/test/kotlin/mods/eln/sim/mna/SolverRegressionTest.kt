package mods.eln.sim.mna

import mods.eln.sim.mna.component.Capacitor
import mods.eln.sim.mna.component.CurrentSource
import mods.eln.sim.mna.component.Inductor
import mods.eln.sim.mna.component.InterSystem
import mods.eln.sim.mna.component.PowerSource
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.State
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.mna.state.VoltageStateLineReady
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolverRegressionTest {
    private fun resistor(a: State?, b: State?, ohms: Double): Resistor = Resistor(a, b).setResistance(ohms)

    @Test
    fun parallelCurrentSourcesAddUp() {
        val root = RootSystem(0.1, 1)
        val n = VoltageState()
        root.addState(n)
        root.addComponent(CurrentSource("a", n, null).setCurrent(2.0))
        root.addComponent(CurrentSource("b", n, null).setCurrent(3.0))
        root.addComponent(resistor(n, null, 10.0))
        root.step()
        assertEquals(50.0, n.state, 1e-6)
    }

    @Test
    fun seriesCurrentSourcesShareANode() {
        // 1 A up into m, 1 A on from m to n: nothing is left for m, and n gets 1 A.
        repeat(20) {
            val root = RootSystem(0.1, 1)
            val m = VoltageState()
            val n = VoltageState()
            root.addState(m)
            root.addState(n)
            root.addComponent(CurrentSource("a", m, null).setCurrent(1.0))
            root.addComponent(CurrentSource("b", n, m).setCurrent(1.0))
            root.addComponent(resistor(m, null, 10.0))
            root.addComponent(resistor(n, null, 10.0))
            root.step()
            assertEquals(0.0, m.state, 1e-6)
            assertEquals(10.0, n.state, 1e-6)
        }
    }

    @Test
    fun capacitorsSharingANodeBehaveLikeTheirSum() {
        val dt = 0.1
        val root = RootSystem(dt, 1)
        val n = VoltageState()
        root.addState(n)
        root.addComponent(Capacitor(n, null).also { it.setCoulombs(0.5) })
        root.addComponent(Capacitor(n, null).also { it.setCoulombs(0.5) })
        root.addComponent(resistor(n, null, 1.0))
        n.state = 100.0
        repeat(10) { root.step() }
        // Implicit Euler discharge of 1 F through 1 ohm.
        assertEquals(100.0 / (1.0 + dt).pow(10.0), n.state, 1e-6)
    }

    @Test
    fun aRemovedCurrentSourceStopsFeeding() {
        val root = RootSystem(0.1, 1)
        val n = VoltageState()
        root.addState(n)
        val source = CurrentSource("a", n, null).setCurrent(2.0)
        root.addComponent(source)
        root.addComponent(resistor(n, null, 10.0))
        root.step()
        assertEquals(20.0, n.state, 1e-6)

        source.breakConnection()
        root.removeComponent(source)
        root.step()
        assertEquals(0.0, n.state, 1e-6)

        // Still gone after something else in the circuit changes.
        root.addComponent(resistor(n, null, 10.0))
        root.step()
        assertEquals(0.0, n.state, 1e-6)
        assertEquals(1, root.systems.size)
        assertTrue(source !in root.systems[0].component)
    }

    @Test
    fun currentSourcesSurviveACircuitRebuild() {
        val root = RootSystem(0.1, 1)
        val n = VoltageState()
        root.addState(n)
        root.addComponent(CurrentSource("a", n, null).setCurrent(2.0))
        root.addComponent(resistor(n, null, 10.0))
        root.step()
        repeat(3) {
            root.addComponent(resistor(n, null, 1e12))
            root.step()
        }
        assertEquals(20.0, n.state, 1e-4)
    }

    @Test
    fun powerSourceDeliversItsPower() {
        val root = RootSystem(0.1, 1)
        val n = VoltageState()
        root.addState(n)
        val source = PowerSource("p", n)
        source.setPower(1000.0)
        source.setMaximumVoltage(1000.0)
        source.setMaximumCurrent(1000.0)
        root.addComponent(source)
        root.addComponent(resistor(n, null, 10.0))
        repeat(5) { root.step() }
        assertEquals(100.0, n.state, 1e-3)
    }

    @Test
    fun lineEndingAtGround() {
        val root = RootSystem(0.1, 1)
        val a = VoltageState()
        val cables = List(4) { VoltageStateLineReady().apply { setCanBeSimplifiedByLine(true) } }
        root.addState(a)
        cables.forEach(root::addState)
        root.addComponent(VoltageSource("u", a, null).setVoltage(50.0))
        root.addComponent(resistor(a, cables[0], 1.0))
        for ((x, y) in cables.zipWithNext()) root.addComponent(resistor(x, y, 1.0))
        root.addComponent(resistor(cables.last(), null, 1.0))
        repeat(2) { root.step() }
        cables.forEachIndexed { i, s -> assertEquals(40.0 - 10.0 * i, s.state, 1e-6) }
    }

    @Test
    fun lineReversedEndingAtGround() {
        val root = RootSystem(0.1, 1)
        val a = VoltageState()
        val cables = List(4) { VoltageStateLineReady().apply { setCanBeSimplifiedByLine(true) } }
        cables.reversed().forEach(root::addState)
        root.addState(a)
        root.addComponent(resistor(null, cables.last(), 1.0))
        for ((x, y) in cables.zipWithNext()) root.addComponent(resistor(y, x, 1.0))
        root.addComponent(resistor(cables[0], a, 1.0))
        root.addComponent(VoltageSource("u", a, null).setVoltage(50.0))
        repeat(2) { root.step() }
        cables.forEachIndexed { i, s -> assertEquals(40.0 - 10.0 * i, s.state, 1e-6) }
    }

    @Test
    fun changingALineResistorTakesEffect() {
        val root = RootSystem(0.1, 1)
        val a = VoltageState()
        val b = VoltageState()
        val cables = List(3) { VoltageStateLineReady().apply { setCanBeSimplifiedByLine(true) } }
        (listOf(a, b) + cables).forEach(root::addState)
        root.addComponent(VoltageSource("u", a, null).setVoltage(50.0))
        root.addComponent(resistor(a, cables[0], 1.0))
        val changing = resistor(cables[0], cables[1], 1.0)
        root.addComponent(changing)
        root.addComponent(resistor(cables[1], cables[2], 1.0))
        root.addComponent(resistor(cables[2], b, 1.0))
        root.addComponent(resistor(b, null, 1.0))
        root.step()
        assertEquals(10.0, b.state, 1e-6)
        changing.setResistance(6.0)
        root.step()
        assertEquals(5.0, b.state, 1e-6)
    }

    @Test
    fun splitCircuitsAgreeWithWholeOnes() {
        // A ladder big enough to be cut into several subsystems.
        fun run(link: (State, State) -> Resistor): Pair<Double, Int> {
            val root = RootSystem(0.05, 50)
            val nodes = List(260) { VoltageState() }
            nodes.forEach(root::addState)
            root.addComponent(VoltageSource("u", nodes[0], null).setVoltage(200.0))
            for ((x, y) in nodes.zipWithNext()) root.addComponent(link(x, y).setResistance(0.05))
            nodes.forEachIndexed { i, n -> if (i % 20 == 19) root.addComponent(resistor(n, null, 400.0)) }
            repeat(40) { root.step() }
            return nodes.last().state to root.systems.size
        }

        val (whole, one) = run { a, b -> Resistor(a, b) }
        val (split, several) = run { a, b -> InterSystem().also { it.connectTo(a, b) } }
        assertEquals(1, one)
        assertTrue(several > 1, "circuit was not split")
        assertTrue(abs(whole - split) < 0.01 * whole, "whole $whole, split $split")
    }

    @Test
    fun longChainsOfSubsystemsCanBeBroken() {
        val root = RootSystem(0.05, 1)
        val nodes = List(60000) { VoltageState() }
        nodes.forEach(root::addState)
        for ((x, y) in nodes.zipWithNext()) root.addComponent(InterSystem().also { it.connectTo(x, y) }.setResistance(0.05))
        root.generate()
        val before = root.systems.size
        assertTrue(before > 100)
        root.addComponent(resistor(nodes[0], null, 10.0))
        root.generate()
        assertTrue(root.systems.size > 100)
    }

    @Test
    fun theveninOfADividerAndItsSideEffects() {
        val root = RootSystem(0.1, 1)
        val a = VoltageState()
        val b = VoltageState()
        root.addState(a)
        root.addState(b)
        root.addComponent(VoltageSource("u", a, null).setVoltage(100.0))
        root.addComponent(resistor(a, b, 10.0))
        root.addComponent(resistor(b, null, 10.0))
        val probe = VoltageSource("probe", b, null).setVoltage(0.0)
        root.addComponent(probe)
        root.addComponent(Inductor("l", b, null).also { it.setInductance(1e9) })
        root.step()
        val th = b.subSystem.getTh(b, probe)
        assertEquals(5.0, th.resistance, 1e-6)
        assertEquals(50.0, th.voltage, 1e-4)
        assertEquals(b.state, probe.voltage, 1e-9)
    }
}
