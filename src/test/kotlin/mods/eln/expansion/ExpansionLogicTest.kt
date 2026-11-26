package mods.eln.expansion

import mods.eln.sim.renewable.PvCurve
import mods.eln.sim.renewable.RotorAerodynamics
import mods.eln.sixnode.mppt.MpptLogic
import mods.eln.sixnode.oscilloscope.ScopeTrace
import mods.eln.sixnode.protection.BatteryManagementLogic
import mods.eln.sixnode.protection.OvercurrentRelayLogic
import mods.eln.sixnode.protection.TransferSwitchLogic
import mods.eln.sixnode.protection.TripCurve
import mods.eln.transparentnode.battery.BatteryChemistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The physics and control logic of the parts added on the expansion-testing branch, without a world. */
class ExpansionLogicTest {
    private val dt = 0.05

    @Test
    fun tripCurvesMatchIec60255() {
        // Published values at ten times pickup with a time multiplier of 1.
        assertEquals(2.97, TripCurve.STANDARD_INVERSE.tripTime(10.0, 1.0), 0.01)
        assertEquals(1.5, TripCurve.VERY_INVERSE.tripTime(10.0, 1.0), 0.01)
        assertEquals(0.808, TripCurve.EXTREMELY_INVERSE.tripTime(10.0, 1.0), 0.01)
        assertEquals(0.4, TripCurve.DEFINITE_TIME.tripTime(3.0, 0.4), 1e-9)
        assertTrue(TripCurve.STANDARD_INVERSE.tripTime(0.9, 1.0).isInfinite())
        // More current, sooner.
        assertTrue(TripCurve.VERY_INVERSE.tripTime(2.0, 1.0) > TripCurve.VERY_INVERSE.tripTime(5.0, 1.0))
    }

    private fun relay() = OvercurrentRelayLogic().apply {
        pickup = 10.0
        tms = 0.1
        curve = TripCurve.STANDARD_INVERSE
        instantaneous = 8.0
        close()
    }

    /** Seconds until it is no longer closed, at [current]. */
    private fun timeToOpen(r: OvercurrentRelayLogic, current: Double, limit: Double = 100.0): Double {
        var t = 0.0
        while (r.closed && t < limit) {
            r.step(dt, current, false)
            t += dt
        }
        return t
    }

    @Test
    fun relayTripsAfterTheCurveTime() {
        val r = relay()
        // Twice the pickup: 0.1 * 0.14 / (2^0.02 - 1) = 1.003 s.
        assertEquals(1.0, timeToOpen(r, 20.0), 0.1)
        assertEquals(OvercurrentRelayLogic.State.LOCKED_OUT, r.state)
        assertEquals(20.0, r.lastTripCurrent, 1e-9)
    }

    @Test
    fun relayHoldsBelowPickupAndRunsBack() {
        val r = relay()
        assertTrue(timeToOpen(r, 9.0, 30.0) >= 30.0)
        // Half way to a trip, then the current falls: the disc runs back.
        repeat(10) { r.step(dt, 20.0, false) }
        assertTrue(r.progress > 0.4)
        repeat(100) { r.step(dt, 5.0, false) }
        assertEquals(0.0, r.progress, 1e-9)
        assertTrue(r.closed)
    }

    @Test
    fun relayTripsAtOnceOnAShortCircuit() {
        val r = relay()
        r.step(dt, 500.0, false)
        assertEquals(OvercurrentRelayLogic.State.LOCKED_OUT, r.state)
    }

    @Test
    fun recloserRetriesThenLocksOut() {
        val r = relay()
        r.recloseShots = 2
        r.recloseDelay = 1.0
        r.step(dt, 500.0, false)
        assertEquals(OvercurrentRelayLogic.State.RECLOSING, r.state)
        repeat(25) { r.step(dt, 0.0, false) }
        assertEquals(OvercurrentRelayLogic.State.CLOSED, r.state)
        assertEquals(1, r.shotsUsed)
        // The fault is still there.
        r.step(dt, 500.0, false)
        repeat(25) { r.step(dt, 0.0, false) }
        assertEquals(2, r.shotsUsed)
        r.step(dt, 500.0, false)
        assertEquals(OvercurrentRelayLogic.State.LOCKED_OUT, r.state)
        r.close()
        assertTrue(r.closed)
    }

    @Test
    fun recloserForgetsOldTripsAfterTheReclaimTime() {
        val r = relay()
        r.recloseShots = 1
        r.recloseDelay = 1.0
        r.reclaimTime = 5.0
        r.step(dt, 500.0, false)
        repeat(25) { r.step(dt, 0.0, false) }
        assertEquals(1, r.shotsUsed)
        repeat(120) { r.step(dt, 0.0, false) }
        assertEquals(0, r.shotsUsed)
        r.step(dt, 500.0, false)
        assertEquals(OvercurrentRelayLogic.State.RECLOSING, r.state)
    }

    @Test
    fun remoteTripOpensWithoutReclosing() {
        val r = relay()
        r.recloseShots = 3
        r.step(dt, 0.0, true)
        assertEquals(OvercurrentRelayLogic.State.OPEN, r.state)
    }

    @Test
    fun batteryManagementCutsOffAndLetsAChargerThrough() {
        val b = BatteryManagementLogic().apply { nominalVoltage = 50.0; defaultCurrentLimit = 100.0 }
        b.step(dt, 50.0, 50.0, 10.0)
        assertTrue(b.closed)
        // Flat battery: the load is cut off.
        b.step(dt, 44.0, 44.0, 10.0)
        assertEquals(BatteryManagementLogic.State.LOW, b.state)
        b.step(dt, 44.0, 0.0, 0.0)
        assertTrue(!b.closed)
        // A charger on the load side: it closes and stays closed while charging.
        b.step(dt, 44.0, 52.0, 0.0)
        assertTrue(b.closed)
        b.step(dt, 46.0, 46.2, -5.0)
        assertTrue(b.closed)
        // The charger goes and the load draws again: open.
        b.step(dt, 45.5, 45.0, 3.0)
        assertTrue(!b.closed)
        // Charged back up.
        b.step(dt, 50.5, 0.0, 0.0)
        assertEquals(BatteryManagementLogic.State.NORMAL, b.state)
    }

    @Test
    fun batteryManagementStopsOverchargeAndOvercurrent() {
        val b = BatteryManagementLogic().apply { nominalVoltage = 50.0; defaultCurrentLimit = 20.0 }
        b.step(dt, 55.0, 55.0, -5.0)
        assertEquals(BatteryManagementLogic.State.HIGH, b.state)
        b.step(dt, 55.0, 56.0, -5.0)
        assertTrue(!b.closed)
        // A load may still draw.
        b.step(dt, 54.5, 53.0, 0.0)
        assertTrue(b.closed)
        val c = BatteryManagementLogic().apply { nominalVoltage = 50.0; defaultCurrentLimit = 20.0 }
        c.step(dt, 50.0, 50.0, 30.0)
        assertEquals(BatteryManagementLogic.State.FAULT, c.state)
        assertTrue(!c.closed)
        repeat((BatteryManagementLogic.FAULT_RETRY / dt).toInt() + 2) { c.step(dt, 50.0, 0.0, 0.0) }
        assertTrue(c.closed)
    }

    @Test
    fun transferSwitchGoesToBackupAndBack() {
        val t = TransferSwitchLogic().apply { transferDelay = 0.5; retransferDelay = 2.0 }
        repeat(20) { t.step(dt, 50.0, 50.0) }
        assertEquals(TransferSwitchLogic.Source.PRIMARY, t.source)
        // A short dip is ridden through.
        repeat(5) { t.step(dt, 0.0, 50.0) }
        assertEquals(TransferSwitchLogic.Source.PRIMARY, t.source)
        repeat(10) { t.step(dt, 0.0, 50.0) }
        assertEquals(TransferSwitchLogic.Source.BACKUP, t.source)
        // Mains back, but not for long enough.
        repeat(20) { t.step(dt, 50.0, 50.0) }
        assertEquals(TransferSwitchLogic.Source.BACKUP, t.source)
        repeat(30) { t.step(dt, 50.0, 50.0) }
        assertEquals(TransferSwitchLogic.Source.PRIMARY, t.source)
        // Neither: off.
        repeat(20) { t.step(dt, 0.0, 0.0) }
        assertEquals(TransferSwitchLogic.Source.NONE, t.source)
    }

    @Test
    fun pvCurveHasItsPowerAtTheKnee() {
        val pv = PvCurve(118.0, 1000.0)
        val (v, p) = pv.maximumPowerPoint(1.0)
        assertEquals(1000.0, p, 5.0)
        // Silicon panels have their maximum power near 0.8 of the open-circuit voltage.
        assertTrue(v / 118.0 in 0.75..0.88, "Vmpp/Voc = ${v / 118.0}")
        assertEquals(pv.shortCircuitCurrent, pv.current(0.0, 1.0), 1e-6)
        assertEquals(0.0, pv.current(118.0, 1.0), 1e-6)
        val fill = p / (118.0 * pv.shortCircuitCurrent)
        assertTrue(fill in 0.7..0.82, "fill factor $fill")
        // Half the light: about half the power, a little less voltage.
        val (vHalf, pHalf) = pv.maximumPowerPoint(0.5)
        assertTrue(pHalf in 430.0..500.0, "half light $pHalf")
        assertTrue(vHalf < v)
    }

    @Test
    fun rotorPeaksNearTheBetzLimit() {
        val best = (10..150).maxOf { RotorAerodynamics.powerCoefficient(it / 10.0, 0.0) }
        assertTrue(best in 0.44..0.5, "Cp max $best")
        assertTrue(best < 16.0 / 27)
        val atOptimum = RotorAerodynamics.powerCoefficient(RotorAerodynamics.OPTIMAL_TIP_SPEED_RATIO, 0.0)
        assertTrue(atOptimum > 0.95 * best)
        // Pitching the blades sheds power.
        assertTrue(RotorAerodynamics.powerCoefficient(8.1, 10.0) < atOptimum * 0.7)
        assertTrue(RotorAerodynamics.powerCoefficient(8.1, 90.0) < 0.01)
    }

    /** The voltage of [pv] loaded by [resistance], where its current equals V / R. */
    private fun operatingVoltage(pv: PvCurve, light: Double, resistance: Double): Double {
        var lo = 0.0
        var hi = pv.openCircuitVoltage(light)
        repeat(60) {
            val mid = (lo + hi) / 2
            if (pv.current(mid, light) > mid / resistance) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    @Test
    fun mpptFindsTheMaximumPowerPoint() {
        val pv = PvCurve(118.0, 1000.0)
        val mppt = MpptLogic(200.0, 40.0)
        var light = 1.0
        var r = mppt.inputResistance
        var power = 0.0
        repeat(600) {
            val v = operatingVoltage(pv, light, r)
            mppt.step(dt, v, false)
            r = mppt.inputResistance
            power = v * v / r
        }
        val best = pv.maximumPowerPoint(light).second
        assertTrue(power > 0.95 * best, "tracked $power of $best")
        // A cloud: it follows.
        light = 0.4
        repeat(400) {
            val v = operatingVoltage(pv, light, r)
            mppt.step(dt, v, false)
            r = mppt.inputResistance
            power = v * v / r
        }
        val cloudy = pv.maximumPowerPoint(light).second
        assertTrue(power > 0.93 * cloudy, "tracked $power of $cloudy under a cloud")
        assertEquals(MpptLogic.Mode.TRACKING, mppt.mode)
    }

    @Test
    fun mpptBacksOffWhenTheBatteryIsFull() {
        val mppt = MpptLogic(200.0, 40.0)
        repeat(20) { mppt.step(dt, 90.0, false) }
        val tracking = mppt.inputResistance
        repeat(20) { mppt.step(dt, 95.0, true) }
        assertEquals(MpptLogic.Mode.LIMITED, mppt.mode)
        assertTrue(mppt.inputResistance > tracking)
        repeat(3) { mppt.step(dt, 250.0, false) }
        assertEquals(MpptLogic.Mode.OVERVOLTAGE, mppt.mode)
    }

    @Test
    fun scopeAveragesAndScales() {
        val trace = ScopeTrace(4)
        for (i in 0 until 12) trace.sample(i.toDouble(), -1.0, 2)
        assertEquals(4, trace.count)
        // The last four points are the averages of pairs 4-5, 6-7, 8-9 and 10-11.
        assertEquals(4.5, trace.get(0, 0), 1e-9)
        assertEquals(10.5, trace.get(0, 3), 1e-9)
        assertEquals(20.0, trace.scale(0), 1e-9)
        assertEquals(1.0, trace.scale(1), 1e-9)
        assertEquals(50.0, ScopeTrace.niceScale(48.0), 1e-9)
        assertEquals(0.2, ScopeTrace.niceScale(0.13), 1e-9)
    }

    @Test
    fun chemistriesBehaveAsTheirCellsDo() {
        for (c in BatteryChemistry.entries) {
            // The voltage rises with the charge.
            var last = -1.0
            for (i in 0..12) {
                val v = c.voltageFunction.getValue(i / 10.0)
                assertTrue(v > last, "$c at ${i / 10.0}")
                last = v
            }
            assertEquals(1.0, c.resistanceFactor(25.0), 1e-9)
            assertTrue(c.resistanceFactor(-20.0) > 1.0)
            assertEquals(0.0, c.extraWear(20.0, 0.5, false), 1e-9)
            assertTrue(c.extraWear(45.0, 0.5, false) > 2.9)
        }
        // LiFePO4 is the flattest between 20 % and 90 %.
        fun swing(c: BatteryChemistry) = c.voltageFunction.getValue(0.9) - c.voltageFunction.getValue(0.2)
        assertTrue(BatteryChemistry.entries.all { swing(BatteryChemistry.LITHIUM_IRON_PHOSPHATE) <= swing(it) })
        // Only lithium-ion runs away, and only when hot.
        assertEquals(0.0, BatteryChemistry.LITHIUM_ION.runawayPower(60.0, 1000.0), 1e-9)
        assertTrue(BatteryChemistry.LITHIUM_ION.runawayPower(100.0, 1000.0) > 0)
        assertEquals(0.0, BatteryChemistry.LITHIUM_IRON_PHOSPHATE.runawayPower(150.0, 1000.0), 1e-9)
        // Lead-acid sulphates when left flat; lithium plates when charged in the cold.
        assertTrue(BatteryChemistry.LEAD_ACID.extraWear(20.0, 0.05, false) > 2)
        assertTrue(BatteryChemistry.LITHIUM_ION.extraWear(-5.0, 0.5, true) > 3)
        assertEquals(0.0, BatteryChemistry.NICKEL_CADMIUM.extraWear(-5.0, 0.5, true), 1e-9)
    }
}
