package com.norwood.ahf.gametest;

import com.mojang.authlib.GameProfile;
import com.norwood.ahf.Ahf;
import com.norwood.ahf.config.TestConfig;
import com.norwood.ahf.hit.HitRegistration;
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.lagcomp.EntityHistory;
import com.norwood.ahf.lagcomp.Snapshot;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.RigCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class LagCompGameTest {

    private static final double SHIFT = 3.0;
    private static final double TORSO_Y = 1.0;
    private static final double ARM_Y = 1.06;
    private static final List<HitboxPart> ARM_THEN_TORSO = List.of(HitboxPart.RIGHT_ARM, HitboxPart.TORSO);

    private static Vec3[] through(Vec3 feet) {
        return new Vec3[]{feet.add(0.0, TORSO_Y, 2.0), feet.add(0.0, TORSO_Y, -2.0)};
    }

    /** From the victim's right to its left at arm height, for a body at {@code feet} facing {@code yaw}. */
    private static Vec3[] sideOn(Vec3 feet, float yaw) {
        Vec3 r = Bodies.right(yaw).scale(2.0);
        return new Vec3[]{feet.add(r.x, ARM_Y, r.z), feet.add(-r.x, ARM_Y, -r.z)};
    }

    private static List<LivingEntity> candidates(ServerLevel level, long t, Vec3 from, Vec3 to) {
        List<LivingEntity> out = new ArrayList<>();
        EntityHistory.candidates(level, t, from, to, out);
        return out;
    }

    @AhfGameTest
    public void aRewoundRayHitsWhereTheTargetWas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer v = Bodies.player(helper);
        level.addFreshEntity(v);
        long[] then = new long[1];
        Vec3[] was = new Vec3[1];
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    then[0] = level.getGameTime();
                    was[0] = v.position();
                    Snapshot s = EntityHistory.snapshot(v, then[0]);
                    helper.assertTrue(s != null && s.rig() != null, "no player snapshot with a rig this tick");
                    helper.assertTrue(RigCache.get(v) == s.rig(),
                            "capture's rig not cached for the tick => the first live query recomputes it");
                    v.snapTo(was[0].x + SHIFT, was[0].y, was[0].z, 0.0F, 0.0F);
                    Bodies.face(v, 0.0F);
                    helper.assertTrue(s.pos().equals(was[0]),
                            "snapshot(now) must be the broadcast state, captured before this tick's moves; got "
                                    + s.pos() + " want " + was[0]);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    try {
                        Vec3[] old = through(was[0]);
                        Vec3[] now = through(v.position());
                        long t = then[0];
                        helper.assertTrue(HitboxApi.classifyRay(v, old[0], old[1]) == null,
                                "live ray through the old spot hit a target " + SHIFT + " blocks away");
                        HitboxPart rewound = Bodies.part(HitboxApi.classifyRay(v, old[0], old[1], t));
                        helper.assertTrue(rewound == HitboxPart.TORSO,
                                "rewound ray through the old torso should be TORSO, got " + rewound);
                        helper.assertTrue(HitboxApi.classifyRay(v, now[0], now[1], t) == null,
                                "rewound ray through the live spot must miss");
                        helper.assertTrue(Bodies.part(HitboxApi.classifyRay(v, now[0], now[1])) == HitboxPart.TORSO,
                                "live ray through the live torso missed");
                        Vec3[] side = sideOn(was[0], 0.0F);
                        List<HitboxPart> pierced = Bodies.parts(HitboxApi.classifyRayPierced(v, side[0], side[1], t));
                        helper.assertTrue(pierced.equals(ARM_THEN_TORSO),
                                "rewound pierce through the old spot: want " + ARM_THEN_TORSO + ", got " + pierced);

                        AABB expect = HitRegistration.registrationBox(v, v.getType().getDimensions()
                                .makeBoundingBox(was[0]));
                        AABB bounds = EntityHistory.bounds(v, t);
                        helper.assertTrue(bounds != null && bounds.equals(expect),
                                "rewound bounds " + bounds + " != envelope at the old spot " + expect);

                        long cur = level.getGameTime();
                        helper.assertTrue(candidates(level, t, old[0], old[1]).contains(v),
                                "broadphase at the old tick missed the target");
                        helper.assertFalse(candidates(level, cur, old[0], old[1]).contains(v),
                                "broadphase at the current tick found it at the old spot");
                        helper.assertTrue(candidates(level, cur, now[0], now[1]).contains(v),
                                "broadphase at the current tick missed the live spot");
                    } finally {
                        v.discard();
                    }
                })
                .thenSucceed();
    }

    /** Captured facing 90, queried facing 0: the same world ray is side-on then, front-to-back now. */
    @AhfGameTest
    public void aRewoundRayUsesTheBodyYawOfThatTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer v = Bodies.player(helper, new BlockPos(1, 1, 1), 90.0F);
        level.addFreshEntity(v);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    long t = level.getGameTime();
                    try {
                        Snapshot s = EntityHistory.snapshot(v, t);
                        helper.assertTrue(s != null && s.bodyYaw() == 90.0F,
                                "setup: captured body yaw " + (s == null ? null : s.bodyYaw()) + " != 90");
                        Bodies.face(v, 0.0F);
                        Vec3[] side = sideOn(v.position(), 90.0F);
                        HitboxPart live = Bodies.part(HitboxApi.classifyRay(v, side[0], side[1]));
                        HitboxPart rewound = Bodies.part(HitboxApi.classifyRay(v, side[0], side[1], t));
                        helper.assertTrue(rewound == HitboxPart.RIGHT_ARM,
                                "rewound side-on ray at the captured yaw should be RIGHT_ARM, got " + rewound);
                        helper.assertTrue(live == HitboxPart.TORSO,
                                "setup: the same ray against the live yaw should be TORSO, got " + live);
                        List<HitboxPart> pierced = Bodies.parts(HitboxApi.classifyRayPierced(v, side[0], side[1], t));
                        helper.assertTrue(pierced.equals(ARM_THEN_TORSO),
                                "rewound pierce at the captured yaw: want " + ARM_THEN_TORSO + ", got " + pierced);
                    } finally {
                        v.discard();
                    }
                })
                .thenSucceed();
    }

    /** No rig => banded fallback on the snapshot box, yaw and flags; live: moved, turned 180, lying. */
    @AhfGameTest
    public void aRewoundRayHitsWhereAMobWas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer anchor = Bodies.player(helper);
        level.addFreshEntity(anchor);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(3, 1, 1));
        stand.setYRot(0.0F);
        stand.setYBodyRot(0.0F);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    long t = level.getGameTime();
                    try {
                        Snapshot s = EntityHistory.snapshot(stand, t);
                        helper.assertTrue(s != null && s.bodyYaw() == 0.0F && s.rig() == null,
                                "setup: mob snapshot facing 0 without a rig; yaw " + (s == null ? null : s.bodyYaw()));
                        Vec3 feet = stand.position();
                        Vec3[] old = through(feet);
                        Vec3[] offCentre = {feet.add(0.22, TORSO_Y, 2.0), feet.add(0.22, TORSO_Y, -2.0)};
                        stand.setPos(stand.getX() + SHIFT, stand.getY(), stand.getZ());
                        stand.setYBodyRot(180.0F);
                        stand.setPose(Pose.SLEEPING);
                        helper.assertTrue(HitboxApi.classifyRay(stand, old[0], old[1]) == null,
                                "live ray through the mob's old spot hit it " + SHIFT + " blocks away");
                        HitboxPart rewound = Bodies.part(HitboxApi.classifyRay(stand, old[0], old[1], t));
                        helper.assertTrue(rewound == HitboxPart.TORSO,
                                "rewound ray through the mob's old torso band should be TORSO, got " + rewound);
                        HitboxPart side = Bodies.part(HitboxApi.classifyRay(stand, offCentre[0], offCentre[1], t));
                        helper.assertTrue(side == HitboxPart.LEFT_ARM,
                                "0.22 off-centre (world +x) at the captured yaw 0 is the mob's left, got " + side);
                        helper.assertTrue(candidates(level, t, old[0], old[1]).contains(stand),
                                "broadphase at the old tick missed the mob");
                    } finally {
                        stand.discard();
                        anchor.discard();
                    }
                })
                .thenSucceed();
    }

    /** Exact against brute force: diagonal segments over mobs spread across many index cells. */
    @AhfGameTest
    public void broadphaseMatchesBruteForce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer anchor = Bodies.player(helper);
        level.addFreshEntity(anchor);
        Vec3 base = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(1, 40, 1)));
        List<ChunkPos> forced = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(base.x - 32); cx <= SectionPos.blockToSectionCoord(base.x + 32); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(base.z - 32); cz <= SectionPos.blockToSectionCoord(base.z + 32); cz++) {
                if (level.setChunkForced(cx, cz, true)) {
                    forced.add(new ChunkPos(cx, cz));
                }
            }
        }
        Random rnd = new Random(7);
        List<ArmorStand> crowd = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            ArmorStand s = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.LOAD);
            s.setNoGravity(true);
            s.snapTo(base.x + rnd.nextDouble() * 48.0 - 24.0, base.y + rnd.nextDouble() * 16.0,
                    base.z + rnd.nextDouble() * 48.0 - 24.0, rnd.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(s);
            crowd.add(s);
        }
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    long t = level.getGameTime();
                    try {
                        int hits = 0;
                        for (int k = 0; k < 400; k++) {
                            Vec3 from = base.add(rnd.nextDouble() * 64.0 - 32.0, rnd.nextDouble() * 24.0 - 4.0,
                                    rnd.nextDouble() * 64.0 - 32.0);
                            Vec3 to = k % 4 == 0
                                    ? crowd.get(k % crowd.size()).position().add(0.0, 1.0, 0.0)
                                    : base.add(rnd.nextDouble() * 64.0 - 32.0, rnd.nextDouble() * 24.0 - 4.0,
                                    rnd.nextDouble() * 64.0 - 32.0);
                            Set<LivingEntity> want = new HashSet<>();
                            for (ArmorStand s : crowd) {
                                AABB b = EntityHistory.bounds(s, t);
                                helper.assertTrue(b != null, "setup: stand without history");
                                if (b.contains(from) || b.clip(from, to).isPresent()) {
                                    want.add(s);
                                }
                            }
                            Set<LivingEntity> got = new HashSet<>(candidates(level, t, from, to));
                            got.retainAll(crowd);
                            helper.assertTrue(got.equals(want), "segment " + from + " -> " + to + ": broadphase "
                                    + got.size() + " != brute force " + want.size());
                            hits += want.size();
                        }
                        helper.assertTrue(hits > 50, "setup: only " + hits + " segment/mob hits");
                    } finally {
                        crowd.forEach(ArmorStand::discard);
                        anchor.discard();
                        forced.forEach(c -> level.setChunkForced(c.x(), c.z(), false));
                    }
                })
                .thenSucceed();
    }

    @AhfGameTest
    public void inATickTheBroadcastKeyIsThatTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(EntityHistory.broadcastTick(level) == level.getGameTime(),
                "broadcastTick " + EntityHistory.broadcastTick(level) + " != this tick " + level.getGameTime());
        helper.succeed();
    }

    @AhfGameTest
    public void teleportDropsHistory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer anchor = Bodies.player(helper);
        level.addFreshEntity(anchor);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
        long[] then = new long[1];
        double jump = EntityHistory.TELEPORT_KEEP_DISTANCE + 2.0;
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    long t = level.getGameTime();
                    then[0] = t;
                    helper.assertTrue(EntityHistory.has(stand, t) && EntityHistory.has(stand, t - 1),
                            "a mob near a player has no history");
                    stand.teleportTo(stand.getX() + 1.0, stand.getY(), stand.getZ());
                    helper.assertTrue(EntityHistory.has(stand, t), "a 1-block teleport dropped the history");
                    stand.teleportTo(stand.getX(), stand.getY() + jump, stand.getZ());
                    helper.assertFalse(EntityHistory.has(stand, t), "teleport kept the current entry");
                    helper.assertTrue(EntityHistory.snapshot(stand, t - 1) == null, "teleport kept an older entry");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(EntityHistory.has(stand, level.getGameTime()), "capture did not resume");
                        helper.assertFalse(EntityHistory.has(stand, then[0]), "pre-teleport entry came back");
                    } finally {
                        stand.discard();
                        anchor.discard();
                    }
                })
                .thenSucceed();
    }

    /**
     * FakePlayer's own listener overrides teleport as a no-op; a real one over an unconnected Connection queues.
     * Move corrections and rotation-only teleports reuse this call => a victim could clear its own history.
     */
    @AhfGameTest
    public void playerTeleportDropsHistoryButCorrectionsDoNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer v = Bodies.player(helper);
        level.addFreshEntity(v);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    long t = level.getGameTime();
                    try {
                        helper.assertTrue(EntityHistory.has(v, t), "no history for a live player");
                        ServerGamePacketListenerImpl real = new ServerGamePacketListenerImpl(level.getServer(),
                                new Connection(PacketFlow.SERVERBOUND), v,
                                CommonListenerCookie.createInitial(v.getGameProfile(), false));
                        real.teleport(v.getX() + 0.3, v.getY(), v.getZ(), 0.0F, 0.0F);
                        real.teleport(new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, 15.0F, 0.0F),
                                EnumSet.of(Relative.X, Relative.Y, Relative.Z));
                        helper.assertTrue(EntityHistory.has(v, t), "a correction or rotation-only teleport dropped it");
                        real.teleport(v.getX(), v.getY() + EntityHistory.TELEPORT_KEEP_DISTANCE + 2.0, v.getZ(),
                                0.0F, 0.0F);
                        helper.assertFalse(EntityHistory.has(v, t), "player teleport kept the history");
                    } finally {
                        v.discard();
                    }
                })
                .thenSucceed();
    }

    @AhfGameTest
    public void aNewEntityWithTheSameIdAndRemovalGetNoHistory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer v = Bodies.player(helper);
        level.addFreshEntity(v);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    long t = level.getGameTime();
                    try {
                        helper.assertTrue(EntityHistory.has(v, t), "no history for a live player");
                        FakePlayer respawned = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ahf_respawn"));
                        respawned.setId(v.getId());
                        helper.assertTrue(EntityHistory.snapshot(respawned, t) == null,
                                "a respawned player object inherited its predecessor's history");
                    } finally {
                        v.discard();
                    }
                    helper.assertFalse(EntityHistory.has(v, t), "removal kept the history");
                })
                .thenSucceed();
    }

    /** Own batch: the range is global config and spans ticks. */
    @AhfGameTest(batch = "ahf_lagcomp_range")
    public void mobsOutsideTheRangeOfEveryPlayerGetNoHistory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestConfig.lagCompNonPlayerRange(2.0);
        FakePlayer a = Bodies.player(helper, new BlockPos(1, 1, 1), 0.0F);
        FakePlayer b = Bodies.player(helper, new BlockPos(1, 1, 9), 0.0F);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        ArmorStand nearB = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 9));
        ArmorStand far = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(5, 1, 5));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    long t = level.getGameTime();
                    try {
                        helper.assertTrue(EntityHistory.has(nearB, t), "a mob 1 block from the second player");
                        helper.assertFalse(EntityHistory.has(far, t), "a mob 4+ blocks from every player");
                    } finally {
                        TestConfig.reset();
                        List.of(a, b, nearB, far).forEach(e -> e.discard());
                    }
                })
                .thenSucceed();
    }
}
