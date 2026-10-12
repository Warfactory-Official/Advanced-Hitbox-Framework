package com.norwood.ahf.network;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.stance.Posture;
import com.norwood.ahf.stance.Rest;
import com.norwood.ahf.stance.StanceRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owner's stance request; the server echoes {@code seq} in the synced state. */
public record StanceIntentPacket(int seq, StanceRequest request) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StanceIntentPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ahf.MOD_ID, "stance_intent"));

    public static final StreamCodec<FriendlyByteBuf, StanceIntentPacket> STREAM_CODEC =
            StreamCodec.of(StanceIntentPacket::write, StanceIntentPacket::read);

    private static void write(FriendlyByteBuf buf, StanceIntentPacket p) {
        StanceRequest r = p.request;
        buf.writeVarInt(p.seq);
        buf.writeEnum(r.posture());
        buf.writeByte(r.level());
        buf.writeByte(r.side());
        buf.writeFloat(r.lean());
        buf.writeEnum(r.rest());
        buf.writeBoolean(r.ads());
        buf.writeFloat(r.aimYaw());
        buf.writeFloat(r.aimPitch());
    }

    private static StanceIntentPacket read(FriendlyByteBuf buf) {
        int seq = buf.readVarInt();
        return new StanceIntentPacket(seq, new StanceRequest(buf.readEnum(Posture.class), buf.readByte(),
                buf.readByte(), buf.readFloat(), buf.readEnum(Rest.class), buf.readBoolean(), buf.readFloat(),
                buf.readFloat()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
