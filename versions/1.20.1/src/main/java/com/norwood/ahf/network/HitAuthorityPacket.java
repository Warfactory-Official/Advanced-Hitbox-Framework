package com.norwood.ahf.network;

import com.norwood.ahf.client.PoseStreamClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

public record HitAuthorityPacket(boolean streamPose) {

    public static HitAuthorityPacket decode(FriendlyByteBuf buf) {
        return new HitAuthorityPacket(buf.readBoolean());
    }

    public static void encode(FriendlyByteBuf buf, HitAuthorityPacket packet) {
        buf.writeBoolean(packet.streamPose);
    }

    public void handleClient() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PoseStreamClient.setEnabled(streamPose);
        }
    }
}
