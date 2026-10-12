package com.norwood.ahf.network;

import com.norwood.ahf.Ahf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RttPongPacket(int seq) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RttPongPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ahf.MOD_ID, "rtt_pong"));

    public static final StreamCodec<FriendlyByteBuf, RttPongPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeVarInt(p.seq), buf -> new RttPongPacket(buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
