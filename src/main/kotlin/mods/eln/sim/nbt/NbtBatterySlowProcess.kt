package mods.eln.sim.nbt

import mods.eln.node.NodeBase
import mods.eln.sim.BatteryProcess
import mods.eln.sim.BatterySlowProcess
import mods.eln.sim.ThermalLoad

class NbtBatterySlowProcess(
    var node: NodeBase,
    batteryProcess: BatteryProcess,
    thermalLoad: ThermalLoad
) : BatterySlowProcess(batteryProcess, thermalLoad) {

    private var explosionRadius = 2f

    /** What happens instead of an explosion, if anything: batteries vent. */
    var onDestroy: (() -> Unit)? = null

    override fun destroy() {
        val instead = onDestroy
        if (instead != null) instead() else node.physicalSelfDestruction(explosionRadius)
    }
}
