package com.norwood.ahf.stance;

import com.norwood.ahf.client.StanceClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** S2C {@link StanceSync} delta for player {@code entityId}; 1.20.1 stand-in for NeoForge's attachment payload. */
public final class StanceSyncPacket {

    private final int entityId;
    private final StanceState state;
    private final boolean initialSync;
    private final StanceState.Update update;

    StanceSyncPacket(int entityId, StanceState state, boolean initialSync) {
        this.entityId = entityId;
        this.state = state;
        this.initialSync = initialSync;
        this.update = null;
    }

    private StanceSyncPacket(int entityId, StanceState.Update update) {
        this.entityId = entityId;
        this.state = null;
        this.initialSync = false;
        this.update = update;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        StanceSync.INSTANCE.write(buf, state, initialSync);
    }

    public static StanceSyncPacket decode(FriendlyByteBuf buf) {
        return new StanceSyncPacket(buf.readVarInt(), StanceState.Update.read(buf));
    }

    public void handleClient() {
        if (FMLEnvironment.dist == Dist.CLIENT && StanceClient.levelEntity(entityId) instanceof Player p) {
            StanceSync.receive(p, update);
        }
    }
}
