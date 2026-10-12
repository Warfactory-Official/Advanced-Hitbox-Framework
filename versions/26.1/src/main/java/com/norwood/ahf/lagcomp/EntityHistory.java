package com.norwood.ahf.lagcomp;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.hit.HitRegistration;
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.mixin.ServerLevelAccessor;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.rig.RigCache;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lag-comp history ring per living entity. Server thread only.
 * Key = {@link #broadcastTick}, captured at {@code LevelTickEvent.Pre} => {@code snapshot(e, level.getGameTime())}
 * = state broadcast this tick, before entity ticking. Teleport beyond {@link #TELEPORT_KEEP_DISTANCE}, dimension
 * change, respawn, removal drop the ring => no lookup crosses them.
 */
public final class EntityHistory {

    /** Same-level repositions up to this far keep the ring: move corrections, rotation-only teleports, short hops. */
    public static final double TELEPORT_KEEP_DISTANCE = 8.0;
    /** Largest {@link #candidates} pad: the index keys boxes grown by this. */
    public static final double MAX_CANDIDATE_PAD = 0.5;

    private static final Map<ServerLevel, LevelHistory> LEVELS = new IdentityHashMap<>();
    private static final List<ServerPlayer> BY_X = new ArrayList<>();
    private static final Comparator<ServerPlayer> X_ORDER = Comparator.comparingDouble(Entity::getX);
    private static final List<Ring> QUERY = new ArrayList<>();
    private static double[] px = new double[16];
    private static double[] py = new double[16];
    private static double[] pz = new double[16];
    private static int playerCount;
    private static long tickKey;
    private static boolean inTick;

    private EntityHistory() {
    }

    /**
     * Tick whose broadcast carries state written now: inside a server tick (from the first {@code LevelTickEvent.Pre}
     * to {@code ServerTickEvent.Post}) that tick's key, every level alike; between ticks the next tick's.
     */
    public static long broadcastTick(ServerLevel level) {
        if (inTick) {
            return tickKey;
        }
        return level.getGameTime() + (level.tickRateManager().runsNormally() ? 1L : 0L);
    }

    /** First {@code LevelTickEvent.Pre} of a server tick: time not yet advanced iff the level ticks time. */
    public static void beginTick(ServerLevel level) {
        if (inTick) {
            return;
        }
        boolean advances = ((ServerLevelAccessor) level).ahf$tickTime() && level.tickRateManager().runsNormally();
        tickKey = level.getGameTime() + (advances ? 1L : 0L);
        inTick = true;
    }

    public static void endTick() {
        inTick = false;
    }

    public static void capture(ServerLevel level) {
        if (!AhfConfig.lagCompensation()) {
            LEVELS.clear();
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            LEVELS.remove(level);
            return;
        }
        long key = broadcastTick(level);
        int depth = AhfConfig.lagCompHistoryDepth();
        double range = AhfConfig.lagCompNonPlayerRange();
        sortPlayers(players);
        LevelHistory h = LEVELS.computeIfAbsent(level, l -> new LevelHistory());
        CellIndex<Ring> index = h.index(key, depth);
        index.reset(key);
        for (Entity e : level.getAllEntities()) {
            if (!(e instanceof LivingEntity living) || e.isRemoved()) {
                continue;
            }
            if (!(e instanceof Player) && !nearAnyPlayer(e, range)) {
                continue;
            }
            Ring ring = h.rings.get(e.getId());
            if (ring == null || ring.entity != living || ring.ticks.length != depth) {
                ring = new Ring(living, depth);
                h.rings.put(e.getId(), ring);
            }
            ring.push(key, living);
            index.add(ring, ring.env[ring.head], MAX_CANDIDATE_PAD);
        }
        if (key % depth == 0) {
            long stale = key - depth;
            h.rings.int2ObjectEntrySet().removeIf(en -> en.getValue().count == 0
                    || en.getValue().ticks[en.getValue().head] < stale);
        }
    }

    private static void sortPlayers(List<ServerPlayer> players) {
        BY_X.clear();
        BY_X.addAll(players);
        BY_X.sort(X_ORDER);
        int n = BY_X.size();
        if (px.length < n) {
            px = new double[n * 2];
            py = new double[n * 2];
            pz = new double[n * 2];
        }
        for (int i = 0; i < n; i++) {
            ServerPlayer p = BY_X.get(i);
            px[i] = p.getX();
            py[i] = p.getY();
            pz[i] = p.getZ();
        }
        playerCount = n;
        BY_X.clear();
    }

    /** Against {@link #sortPlayers}: binary search to the x slab, then distance. */
    private static boolean nearAnyPlayer(Entity e, double range) {
        double x = e.getX();
        double y = e.getY();
        double z = e.getZ();
        int n = playerCount;
        int lo = 0;
        int hi = n;
        double from = x - range;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (px[mid] < from) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        double rangeSq = range * range;
        for (int i = lo; i < n && px[i] <= x + range; i++) {
            double dx = px[i] - x;
            double dy = py[i] - y;
            double dz = pz[i] - z;
            if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable Snapshot snapshot(Entity entity, long gameTime) {
        Ring ring = ring(entity);
        if (ring == null) {
            return null;
        }
        int i = ring.find(gameTime);
        return i < 0 ? null : ring.snapshot(i);
    }

    public static boolean has(Entity entity, long gameTime) {
        Ring ring = ring(entity);
        return ring != null && ring.find(gameTime) >= 0;
    }

    /** Registration box at {@code gameTime}; null when no snapshot (caller uses live). */
    public static @Nullable AABB bounds(Entity entity, long gameTime) {
        Ring ring = ring(entity);
        if (ring == null) {
            return null;
        }
        int i = ring.find(gameTime);
        return i < 0 ? null : ring.env[i];
    }

    /**
     * Appends to {@code out} the entities whose registration box at {@code gameTime} meets segment
     * {@code from -> to}. Entities without a snapshot at {@code gameTime} are not listed; query them live, filtered
     * by {@code !has(e, gameTime)}.
     */
    public static void candidates(ServerLevel level, long gameTime, Vec3 from, Vec3 to,
                                  List<? super LivingEntity> out) {
        candidates(level, gameTime, from, to, 0.0, out);
    }

    /** {@link #candidates} against registration boxes grown by {@code pad} in {@code [0, MAX_CANDIDATE_PAD]}. */
    public static void candidates(ServerLevel level, long gameTime, Vec3 from, Vec3 to, double pad,
                                  List<? super LivingEntity> out) {
        if (!(pad >= 0.0 && pad <= MAX_CANDIDATE_PAD)) {
            throw new IllegalArgumentException("pad " + pad);
        }
        LevelHistory h = LEVELS.get(level);
        if (h == null) {
            return;
        }
        CellIndex<Ring> index = h.indexAt(gameTime);
        if (index == null) {
            return;
        }
        QUERY.clear();
        index.query(from, to, QUERY);
        for (int k = 0, n = QUERY.size(); k < n; k++) {
            Ring ring = QUERY.get(k);
            int i = ring.find(gameTime);
            if (i >= 0 && segmentHits(ring.env[i], pad, from, to)) {
                out.add(ring.entity);
            }
        }
        QUERY.clear();
    }

    static boolean segmentHits(AABB b, Vec3 from, Vec3 to) {
        return segmentHits(b, 0.0, from, to);
    }

    /** Segment meets {@code b} grown by {@code pad}. */
    public static boolean segmentHits(AABB b, double pad, Vec3 from, Vec3 to) {
        double[] span = {0.0, 1.0};
        return slab(from.x, to.x - from.x, b.minX - pad, b.maxX + pad, span)
                && slab(from.y, to.y - from.y, b.minY - pad, b.maxY + pad, span)
                && slab(from.z, to.z - from.z, b.minZ - pad, b.maxZ + pad, span);
    }

    private static boolean slab(double o, double d, double lo, double hi, double[] span) {
        if (Math.abs(d) < 1.0e-12) {
            return o >= lo && o <= hi;
        }
        double inv = 1.0 / d;
        double ta = (lo - o) * inv;
        double tb = (hi - o) * inv;
        if (ta > tb) {
            double s = ta;
            ta = tb;
            tb = s;
        }
        span[0] = Math.max(span[0], ta);
        span[1] = Math.min(span[1], tb);
        return span[0] <= span[1];
    }

    /**
     * Drops {@code entity}'s history and its passengers' when it lands more than {@link #TELEPORT_KEEP_DISTANCE}
     * from where it is, or in another level. No-op client-side.
     */
    public static void onTeleport(Entity entity, @Nullable Level to, double x, double y, double z) {
        if (to != null && to != entity.level()
                || entity.distanceToSqr(x, y, z) > TELEPORT_KEEP_DISTANCE * TELEPORT_KEEP_DISTANCE) {
            invalidate(entity);
        }
    }

    /** Drops {@code entity}'s history and its passengers'. No-op client-side. */
    public static void invalidate(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        LevelHistory h = LEVELS.get(level);
        if (h == null) {
            return;
        }
        for (Entity e : entity.getSelfAndPassengers().toList()) {
            Ring ring = h.rings.get(e.getId());
            if (ring != null) {
                ring.count = 0;
            }
        }
    }

    public static void onLeave(Entity entity, Level level) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        LevelHistory h = LEVELS.get(server);
        if (h == null) {
            return;
        }
        Ring ring = h.rings.get(entity.getId());
        if (ring != null && ring.entity == entity) {
            ring.count = 0;
            h.rings.remove(entity.getId());
        }
    }

    public static void clearAll() {
        LEVELS.clear();
        inTick = false;
    }

    private static @Nullable Ring ring(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return null;
        }
        LevelHistory h = LEVELS.get(level);
        if (h == null) {
            return null;
        }
        Ring ring = h.rings.get(entity.getId());
        return ring == null || ring.entity != entity ? null : ring;
    }

    static int trackedCount(ServerLevel level) {
        LevelHistory h = LEVELS.get(level);
        return h == null ? 0 : h.rings.size();
    }

    private static final class LevelHistory {
        final Int2ObjectOpenHashMap<Ring> rings = new Int2ObjectOpenHashMap<>();
        @SuppressWarnings("unchecked")
        CellIndex<Ring>[] indices = new CellIndex[0];

        @SuppressWarnings("unchecked")
        CellIndex<Ring> index(long key, int depth) {
            if (indices.length != depth) {
                indices = new CellIndex[depth];
                for (int i = 0; i < depth; i++) {
                    indices[i] = new CellIndex<>();
                }
            }
            return indices[(int) Math.floorMod(key, depth)];
        }

        @Nullable CellIndex<Ring> indexAt(long key) {
            if (indices.length == 0) {
                return null;
            }
            CellIndex<Ring> index = indices[(int) Math.floorMod(key, indices.length)];
            return index.tick == key ? index : null;
        }
    }

    private static final class Ring {
        final LivingEntity entity;
        final long[] ticks;
        final Vec3[] pos;
        final AABB[] box;
        final AABB[] env;
        final float[] yaw;
        final boolean[] upright;
        final boolean[] aiming;
        final HumanoidRig.LocalRig[] rig;
        int head = -1;
        int count;

        Ring(LivingEntity entity, int depth) {
            this.entity = entity;
            ticks = new long[depth];
            pos = new Vec3[depth];
            box = new AABB[depth];
            env = new AABB[depth];
            yaw = new float[depth];
            upright = new boolean[depth];
            aiming = new boolean[depth];
            rig = new HumanoidRig.LocalRig[depth];
        }

        void push(long key, LivingEntity e) {
            if (count == 0 || ticks[head] != key) {
                head = (head + 1) % ticks.length;
                count = Math.min(count + 1, ticks.length);
            }
            AABB b = e.getBoundingBox();
            ticks[head] = key;
            pos[head] = e.position();
            box[head] = b;
            env[head] = HitRegistration.registrationBox(e, b);
            yaw[head] = e.yBodyRot;
            upright[head] = HitboxApi.isUprightHumanoid(e);
            aiming[head] = HitboxApi.isAimingWeapon(e);
            rig[head] = e instanceof Player && AhfConfig.riggedLimbBoxes() && HitboxApi.rigUsable(e)
                    ? RigCache.capture(e, key) : null;
        }

        int find(long key) {
            if (count == 0) {
                return -1;
            }
            int n = ticks.length;
            long off = ticks[head] - key;
            if (off < 0) {
                return -1;
            }
            if (off < count) {
                int i = (int) ((head - off + n) % n);
                if (ticks[i] == key) {
                    return i;
                }
            }
            for (int k = 0; k < count; k++) {
                int i = (head - k + n) % n;
                if (ticks[i] == key) {
                    return i;
                }
            }
            return -1;
        }

        Snapshot snapshot(int i) {
            return new Snapshot(ticks[i], pos[i], box[i], env[i], yaw[i], upright[i], aiming[i], rig[i]);
        }
    }
}
