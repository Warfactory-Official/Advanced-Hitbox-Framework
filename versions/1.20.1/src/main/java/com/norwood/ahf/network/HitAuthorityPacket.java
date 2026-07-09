package com.norwood.ahf.network;

import com.norwood.ahf.client.PoseStreamClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public record HitAuthorityPacket(boolean streamPose) {

    public static HitAuthorityPacket decode(FriendlyByteBuf buf) {
        return new HitAuthorityPacket(buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(streamPose);
    }

    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> PoseStreamClient.setEnabled(streamPose));
    }
}
