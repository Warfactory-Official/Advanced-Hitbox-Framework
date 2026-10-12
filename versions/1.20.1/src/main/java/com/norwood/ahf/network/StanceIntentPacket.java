package com.norwood.ahf.network;

import com.norwood.ahf.stance.Posture;
import com.norwood.ahf.stance.Rest;
import com.norwood.ahf.stance.StanceRequest;
import net.minecraft.network.FriendlyByteBuf;

/** Owner's stance request; the server echoes {@code seq} in the synced state. */
public record StanceIntentPacket(int seq, StanceRequest request) {

    public static void encode(FriendlyByteBuf buf, StanceIntentPacket p) {
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

    public static StanceIntentPacket decode(FriendlyByteBuf buf) {
        int seq = buf.readVarInt();
        return new StanceIntentPacket(seq, new StanceRequest(buf.readEnum(Posture.class), buf.readByte(),
                buf.readByte(), buf.readFloat(), buf.readEnum(Rest.class), buf.readBoolean(), buf.readFloat(),
                buf.readFloat()));
    }
}
