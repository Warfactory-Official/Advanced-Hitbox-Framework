package com.norwood.ahf.network;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.hit.HitAuthority;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AhfNetwork {

    private AhfNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AhfNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                PoseStreamPacket.TYPE,
                PoseStreamPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> packet.handleServer(ctx.player() instanceof ServerPlayer sp ? sp : null))
        );

        registrar.playToClient(
                HitAuthorityPacket.TYPE,
                HitAuthorityPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(packet::handleClient)
        );
    }

    public static void sendToServer(Object packet) {
        if (packet instanceof PoseStreamPacket p) {
            ClientPacketDistributor.sendToServer(p);
        }
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        if (packet instanceof HitAuthorityPacket p) {
            PacketDistributor.sendToPlayer(player, p);
        }
    }

    public static void sendHitAuthority(ServerPlayer player) {
        boolean stream = AhfConfig.hitAuthority() == HitAuthority.CLIENT_HINT;
        PacketDistributor.sendToPlayer(player, new HitAuthorityPacket(stream));
    }
}
