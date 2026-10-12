package com.norwood.ahf.network;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.lagcomp.RttProbe;
import com.norwood.ahf.stance.StanceLogic;
import com.norwood.ahf.stance.StanceSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AhfNetwork {

    private static final String PROTOCOL = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ahf.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private AhfNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(PoseStreamPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((p, buf) -> PoseStreamPacket.encode(buf, p))
                .decoder(PoseStreamPacket::decode)
                .consumerMainThread((p, ctx) -> p.handleServer(ctx.get().getSender()))
                .add();
        CHANNEL.messageBuilder(HitAuthorityPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p, buf) -> HitAuthorityPacket.encode(buf, p))
                .decoder(HitAuthorityPacket::decode)
                .consumerMainThread((p, ctx) -> p.handleClient())
                .add();
        CHANNEL.messageBuilder(StanceRollPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((p, buf) -> buf.writeByte(p.dir()))
                .decoder(buf -> new StanceRollPacket(buf.readByte()))
                .consumerMainThread((p, ctx) -> StanceLogic.handleRoll(ctx.get().getSender(), p.dir()))
                .add();
        CHANNEL.messageBuilder(StanceIntentPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((p, buf) -> StanceIntentPacket.encode(buf, p))
                .decoder(StanceIntentPacket::decode)
                .consumerMainThread((p, ctx) -> StanceLogic.handleIntent(ctx.get().getSender(), p.seq(), p.request()))
                .add();
        CHANNEL.messageBuilder(StanceSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StanceSyncPacket::encode)
                .decoder(StanceSyncPacket::decode)
                .consumerMainThread((p, ctx) -> p.handleClient())
                .add();
        CHANNEL.messageBuilder(RttPingPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p, buf) -> buf.writeVarInt(p.seq()))
                .decoder(buf -> new RttPingPacket(buf.readVarInt()))
                .consumerNetworkThread((p, ctx) -> {
                    CHANNEL.reply(new RttPongPacket(p.seq()), ctx.get());
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(RttPongPacket.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder((p, buf) -> buf.writeVarInt(p.seq()))
                .decoder(buf -> new RttPongPacket(buf.readVarInt()))
                .consumerNetworkThread((p, ctx) -> {
                    RttProbe.onPong(ctx.get().getSender(), p.seq(), System.nanoTime());
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

    /** Players tracking {@code entity}, not itself. */
    public static void sendToTracking(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }

    public static void sendHitAuthority(ServerPlayer player) {
        sendToPlayer(player, new HitAuthorityPacket(AhfConfig.useClientPose()));
    }
}
