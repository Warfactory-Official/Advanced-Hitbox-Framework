package com.norwood.ahf.network;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.lagcomp.RttProbe;
import com.norwood.ahf.stance.StanceLogic;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AhfNetwork {

    private AhfNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AhfNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");

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

        registrar.playToServer(StanceRollPacket.TYPE, StanceRollPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> StanceLogic.handleRoll((ServerPlayer) ctx.player(), packet.dir())));
        registrar.playToServer(StanceIntentPacket.TYPE, StanceIntentPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(
                        () -> StanceLogic.handleIntent((ServerPlayer) ctx.player(), packet.seq(), packet.request())));

        PayloadRegistrar net = registrar.executesOn(HandlerThread.NETWORK);
        net.playToClient(RttPingPacket.TYPE, RttPingPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.reply(new RttPongPacket(packet.seq())));
        net.playToServer(RttPongPacket.TYPE, RttPongPacket.STREAM_CODEC,
                (packet, ctx) -> RttProbe.onPong((ServerPlayer) ctx.player(), packet.seq(), System.nanoTime()));
    }

    public static void sendToServer(Object packet) {
        if (packet instanceof PoseStreamPacket p) {
            PacketDistributor.sendToServer(p);
        }
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        if (packet instanceof HitAuthorityPacket p) {
            PacketDistributor.sendToPlayer(player, p);
        }
    }

    public static void sendHitAuthority(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new HitAuthorityPacket(AhfConfig.useClientPose()));
    }
}
