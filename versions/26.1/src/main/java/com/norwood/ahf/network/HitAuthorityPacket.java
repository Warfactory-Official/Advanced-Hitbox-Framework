package com.norwood.ahf.network;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.client.PoseStreamClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public record HitAuthorityPacket(boolean streamPose) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HitAuthorityPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Ahf.MOD_ID, "hit_authority"));

    public static final StreamCodec<FriendlyByteBuf, HitAuthorityPacket> STREAM_CODEC =
            StreamCodec.of(HitAuthorityPacket::encode, HitAuthorityPacket::decode);

    public static HitAuthorityPacket decode(FriendlyByteBuf buf) {
        return new HitAuthorityPacket(buf.readBoolean());
    }

    public static void encode(FriendlyByteBuf buf, HitAuthorityPacket packet) {
        buf.writeBoolean(packet.streamPose);
    }

    public void handleClient() {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            PoseStreamClient.setEnabled(streamPose);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
