package com.norwood.ahf.lagcomp;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.network.RttPingPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RttProbe {

    private static final int OUTSTANDING = 4;
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private RttProbe() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % AhfConfig.rttProbeIntervalTicks() != 0) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State st = STATES.computeIfAbsent(p.getUUID(), u -> new State());
            int seq;
            synchronized (st) {
                seq = st.nextSeq++;
                int slot = Math.floorMod(seq, OUTSTANDING);
                st.sentSeq[slot] = seq;
                st.sentNanos[slot] = System.nanoTime();
            }
            PacketDistributor.sendToPlayer(p, new RttPingPacket(seq));
        }
    }

    /** Network thread. Unknown or already-answered seq => ignored. */
    public static void onPong(ServerPlayer player, int seq, long nowNanos) {
        State st = STATES.get(player.getUUID());
        if (st == null) {
            return;
        }
        synchronized (st) {
            int slot = Math.floorMod(seq, OUTSTANDING);
            if (st.sentNanos[slot] == 0L || st.sentSeq[slot] != seq) {
                return;
            }
            st.sample(nowNanos - st.sentNanos[slot]);
            st.sentNanos[slot] = 0L;
        }
    }

    /** Min-filtered RTT; -1 before the first sample. */
    public static long rttNanos(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        if (st == null) {
            return -1L;
        }
        synchronized (st) {
            return st.filter.min();
        }
    }

    public static int maxRewindTicks(ServerPlayer player) {
        return RttFilter.maxRewindTicks(rttNanos(player), player.level().getServer().tickRateManager().nanosecondsPerTick(),
                AhfConfig.lagCompInterpTicks(), AhfConfig.lagCompSlackTicks(), AhfConfig.lagCompMaxRewindTicks());
    }

    /** Client-reported view tick clamped into {@code [now - maxRewindTicks(player), now]}. */
    public static long boundViewTick(ServerPlayer player, long reportedViewTick, long now) {
        return RttFilter.boundViewTick(reportedViewTick, now, maxRewindTicks(player));
    }

    public static void forget(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    public static void clearAll() {
        STATES.clear();
    }

    private static final class State {
        final int[] sentSeq = new int[OUTSTANDING];
        final long[] sentNanos = new long[OUTSTANDING];
        int nextSeq;
        RttFilter filter = new RttFilter(AhfConfig.rttWindowSamples());

        void sample(long rtt) {
            if (filter.window() != AhfConfig.rttWindowSamples()) {
                filter = new RttFilter(AhfConfig.rttWindowSamples());
            }
            filter.add(rtt);
        }
    }
}
