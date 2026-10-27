package com.norwood.ahf.stance;

import com.norwood.ahf.network.AhfNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Delta sync: {@code dirty} fields + body yaw + stamp; the client merges instead of replacing. 1.20.1: own
 * {@link StanceSyncPacket} in place of NeoForge attachment sync; initial syncs from {@link StanceEvents}.
 */
final class StanceSync {

    static final StanceSync INSTANCE = new StanceSync();

    /** Owner predicts its own body yaw and walks its own legs: a yaw/walk-only delta skips it. Initial syncs never run mid-flush. */
    boolean sendToPlayer(Player holder, ServerPlayer to) {
        if (holder != to) {
            return true;
        }
        StanceState s = AhfStance.get(to);
        return s == null || (s.dirty & ~(StanceState.YAW | StanceState.WALK)) != 0;
    }

    void write(FriendlyByteBuf buf, StanceState s, boolean initialSync) {
        s.write(buf, initialSync ? StanceState.ALL : s.dirty);
    }

    StanceState read(Player holder, StanceState.Update u, @Nullable StanceState previous) {
        if (previous == null) {
            StanceState s = new StanceState();
            s.apply(u);
            s.bodyYawO = s.bodyYaw;
            s.lean = s.leanO = s.effective().leans() ? s.leanTarget : 0.0F;
            s.height = s.heightO = s.effectiveLevel();
            s.sideAmount = s.sideAmountO = s.effectiveSide();
            return s;
        }
        if (holder.isLocalPlayer()) {
            previous.mergeOwner(u);
        } else {
            previous.enqueue(u);
        }
        return previous;
    }

    /** {@code syncData}: holder (filtered by {@link #sendToPlayer}) and its trackers. */
    static void sync(ServerPlayer holder, StanceState s) {
        StanceSyncPacket packet = new StanceSyncPacket(holder.getId(), s, false);
        if (INSTANCE.sendToPlayer(holder, holder)) {
            AhfNetwork.sendToPlayer(holder, packet);
        }
        AhfNetwork.sendToTracking(holder, packet);
    }

    /** Initial sync of {@code holder}'s existing state to {@code to}. */
    static void syncInitial(Player holder, ServerPlayer to) {
        StanceState s = AhfStance.get(holder);
        if (s != null) {
            AhfNetwork.sendToPlayer(to, new StanceSyncPacket(holder.getId(), s, true));
        }
    }

    static void receive(Player holder, StanceState.Update u) {
        ((StanceHolder) holder).ahf$setStance(INSTANCE.read(holder, u, AhfStance.get(holder)));
    }
}
