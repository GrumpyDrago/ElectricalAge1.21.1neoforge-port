package mods.eln.integration.cc

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IDynamicPeripheral
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.PeripheralCapability
import mods.eln.registration.SingleNodeRegistration
import mods.eln.simplenode.computerprobe.ComputerProbeNode
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent

/** CC: Tweaked support. Only loaded when that mod is present. */
object ComputerCraftIntegration {
    fun register(event: RegisterCapabilitiesEvent) {
        event.registerBlockEntity(PeripheralCapability.get(), SingleNodeRegistration.computerProbe) { entity, _ ->
            (entity.node as? ComputerProbeNode)?.let(::ProbePeripheral)
        }
    }
}

private class ProbePeripheral(private val probe: ComputerProbeNode) : IDynamicPeripheral {
    override fun getType(): String = "ElnProbe"

    override fun getMethodNames(): Array<String> = probe.methodNames

    override fun callMethod(computer: IComputerAccess, context: ILuaContext, method: Int, arguments: IArguments): MethodResult {
        val args = List(arguments.count()) { arguments.get(it) }
        // The node belongs to the server thread.
        return context.executeMainThreadTask {
            val result: Array<Any?> = probe.callMethod(method, args) ?: emptyArray()
            result
        }
    }

    override fun equals(other: IPeripheral?): Boolean = other is ProbePeripheral && other.probe === probe
}
