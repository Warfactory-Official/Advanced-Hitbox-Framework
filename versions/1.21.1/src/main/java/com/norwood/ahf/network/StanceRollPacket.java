package com.norwood.ahf.network;

import com.norwood.ahf.Ahf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owner's prone roll ({@code dir} +1 right); the server starts or refuses it, synced with the targets. */
public record StanceRollPacket(int dir) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StanceRollPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ahf.MOD_ID, "stance_roll"));

    public static final StreamCodec<FriendlyByteBuf, StanceRollPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeByte(p.dir), buf -> new StanceRollPacket(buf.readByte()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
