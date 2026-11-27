package mods.eln.integration.cc

import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IDynamicPeripheral
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.PeripheralCapability
import mods.eln.misc.Direction
import mods.eln.node.ComputerControllable
import mods.eln.node.NodeBlockEntity
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.registration.ElnBlockEntities
import mods.eln.registration.SingleNodeRegistration
import mods.eln.simplenode.computerprobe.ComputerProbeNode
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent

/** CC: Tweaked support. Only loaded when that mod is present. */
object ComputerCraftIntegration {
    fun register(event: RegisterCapabilitiesEvent) {
        event.registerBlockEntity(PeripheralCapability.get(), SingleNodeRegistration.computerProbe) { entity, _ ->
            (entity.node as? ComputerProbeNode)?.let(::ProbePeripheral)
        }
        // Every part of the mod, on a wall or a whole block, as the "eln" peripheral.
        event.registerBlockEntity(PeripheralCapability.get(), ElnBlockEntities.sixNode) { entity, _ -> NodePeripheral(entity) }
        event.registerBlockEntity(PeripheralCapability.get(), ElnBlockEntities.transparentNode) { entity, _ -> NodePeripheral(entity) }
        event.registerBlockEntity(PeripheralCapability.get(), ElnBlockEntities.transparentNodeWithFluid) { entity, _ -> NodePeripheral(entity) }
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

/**
 * Any block of the mod. A block can hold one part (a machine) or up to six (parts on its faces): those are named by
 * the face they are on, "down", "up", "north", "south", "west" or "east".
 *
 * - getParts(): the parts, as {face = name} (a machine is on face "block").
 * - getInfo([face]): what Jade shows, as text.
 * - getValues([face]): numbers and flags, for parts that give them.
 * - getActions([face]): what call can do with the part.
 * - call([face,] action, ...): does an action, such as switching a breaker.
 */
private class NodePeripheral(private val entity: NodeBlockEntity) : IDynamicPeripheral {
    override fun getType(): String = "eln"

    override fun getMethodNames(): Array<String> = METHODS

    override fun callMethod(computer: IComputerAccess, context: ILuaContext, method: Int, arguments: IArguments): MethodResult {
        val args = List(arguments.count()) { arguments.get(it) }
        return context.executeMainThreadTask {
            try {
                arrayOf(run(METHODS[method], args))
            } catch (e: IllegalArgumentException) {
                throw LuaException(e.message)
            }
        }
    }

    private fun parts(): Map<String, Any> {
        val node = entity.node
        val parts = LinkedHashMap<String, Any>()
        when (node) {
            is SixNode -> for ((name, dir) in FACES) node.getElement(dir)?.let { parts[name] = it }
            is TransparentNode -> node.element?.let { parts["block"] = it }
        }
        return parts
    }

    /** The part named by the first argument, if it is a face; otherwise the only part. Returns it and the rest. */
    private fun part(args: List<Any?>): Pair<Any, List<Any?>> {
        val parts = parts()
        val face = args.firstOrNull() as? String
        if (face != null && (face in FACES || face == "block")) {
            return (parts[face] ?: throw IllegalArgumentException("nothing on face $face")) to args.drop(1)
        }
        if (parts.size == 1) return parts.values.first() to args
        throw IllegalArgumentException("say which face: " + parts.keys.joinToString(", "))
    }

    private fun name(element: Any): String = when (element) {
        is mods.eln.node.six.SixNodeElement -> element.sixNodeElementDescriptor.name
        is mods.eln.node.transparent.TransparentNodeElement -> element.descriptor?.name ?: "?"
        else -> element.javaClass.simpleName
    }

    private fun info(element: Any): Map<String, String> = when (element) {
        is mods.eln.node.six.SixNodeElement -> element.getWaila() ?: emptyMap()
        is mods.eln.node.transparent.TransparentNodeElement -> element.getWaila()
        else -> emptyMap()
    }.mapValues { stripFormatting(it.value) }

    private fun run(method: String, args: List<Any?>): Any? = when (method) {
        "getParts" -> parts().mapValues { name(it.value) }
        "getInfo" -> info(part(args).first)
        "getValues" -> (part(args).first as? ComputerControllable)?.computerValues() ?: emptyMap<String, Any>()
        "getActions" -> (part(args).first as? ComputerControllable)?.computerActions() ?: emptyList<String>()
        "call" -> {
            val (element, rest) = part(args)
            val action = rest.firstOrNull() as? String ?: throw IllegalArgumentException("say which action")
            val controllable = element as? ComputerControllable ?: throw IllegalArgumentException("${name(element)} takes no actions")
            controllable.computerCall(action, rest.drop(1))
        }
        else -> null
    }

    override fun equals(other: IPeripheral?): Boolean = other is NodePeripheral && other.entity === entity

    companion object {
        val METHODS = arrayOf("getParts", "getInfo", "getValues", "getActions", "call")
        val FACES = linkedMapOf(
            "down" to Direction.YN, "up" to Direction.YP, "north" to Direction.ZN,
            "south" to Direction.ZP, "west" to Direction.XN, "east" to Direction.XP
        )

        /** Minecraft color codes, which Jade text has, mean nothing to a computer. */
        fun stripFormatting(s: String) = s.replace(Regex("§."), "").trim()
    }
}
