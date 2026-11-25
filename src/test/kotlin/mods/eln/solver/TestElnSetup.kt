package mods.eln.solver

import mods.eln.Eln
import mods.eln.misc.FunctionTable

internal fun ensureBatteryVoltageTable(table: FunctionTable) {
    Eln.instance.batteryVoltageFunctionTable = table
}
