package mods.eln.packets

import io.netty.buffer.ByteBuf
import mods.eln.Eln
import mods.eln.compat.ClientHooks
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/** The one packet of the mod; its content starts with one of the Eln.packet* ids. */
class ElnPayload(val bytes: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ElnPayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("eln", "main"))

        @JvmField
        val CODEC: StreamCodec<ByteBuf, ElnPayload> = ByteBufCodecs.BYTE_ARRAY.map(::ElnPayload) { it.bytes }

        fun register(event: RegisterPayloadHandlersEvent) {
            event.registrar("1").playBidirectional(
                TYPE, CODEC,
                DirectionalPayloadHandler(
                    { payload, _ -> ClientHooks.player()?.let { Eln.packetHandler.packetRxClient(payload.stream(), it) } },
                    { payload, context -> Eln.packetHandler.packetRxServer(payload.stream(), context.player() as net.minecraft.server.level.ServerPlayer) }
                )
            )
        }
    }

    private fun stream() = DataInputStream(ByteArrayInputStream(bytes))
}
