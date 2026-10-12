package com.norwood.ahf.network;

import com.norwood.ahf.Ahf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RttPingPacket(int seq) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RttPingPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Ahf.MOD_ID, "rtt_ping"));

    public static final StreamCodec<FriendlyByteBuf, RttPingPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeVarInt(p.seq), buf -> new RttPingPacket(buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
