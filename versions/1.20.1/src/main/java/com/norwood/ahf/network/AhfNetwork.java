package com.norwood.ahf.network;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.hit.HitAuthority;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AhfNetwork {

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ahf.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static boolean registered;

    private AhfNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CHANNEL.messageBuilder(PoseStreamPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PoseStreamPacket::encode)
                .decoder(PoseStreamPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleServer(ctx.get().getSender());
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(HitAuthorityPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HitAuthorityPacket::encode)
                .decoder(HitAuthorityPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleClient();
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendHitAuthority(ServerPlayer player) {
        boolean stream = AhfConfig.hitAuthority() == HitAuthority.CLIENT_HINT;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HitAuthorityPacket(stream));
    }
}
