package com.norwood.ahf.gametest;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.config.AhfConfigSpec;
import com.norwood.ahf.config.HitRegMode;
import com.norwood.ahf.config.TestConfig;
import com.norwood.ahf.hit.HitAuthority;
import com.norwood.ahf.hit.HitCategory;
import com.norwood.ahf.hit.AhfHitSource;
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.hit.RayHit;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.rig.RigCache;
import com.norwood.ahf.rig.RigTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

public class HitApiGameTest {

    private static final double JUST_ABOVE_HEAD = 1.95;
    private static final double WELL_ABOVE_HEAD = 2.4;
    private static final double ARM_Y = 1.06;

    private static DamageSource meleeAt(GameTestHelper helper, FakePlayer victim, double eyeUp) {
        return meleeAt(helper, victim, eyeUp, 2.0);
    }

    /** Victim at yaw 0; attacker {@code dist} in front, looking back along -Z. */
    private static DamageSource meleeAt(GameTestHelper helper, FakePlayer victim, double eyeUp, double dist) {
        FakePlayer a = Bodies.player(helper, new BlockPos(1, 1, 1), 180.0F);
        Vec3 feet = victim.position();
        a.snapTo(feet.x, feet.y + eyeUp - a.getEyeHeight(), feet.z + dist, 180.0F, 0.0F);
        Bodies.face(a, 180.0F);
        return helper.getLevel().damageSources().playerAttack(a);
    }

    /**
     * Arrow as {@code AbstractArrow.tick} hands it to {@code onHit}: not yet moved ({@code xo == position}), 3/tick
     * (full bow). Chest front 2.775 ahead > segment end 1.0 + pad 0.2.
     */
    @AhfGameTest
    public void aFullDrawArrowStrikingFromAfarIsNotAGap(GameTestHelper helper) {
        try {
            TestConfig.hitRegistrationMode(HitRegMode.PRECISE);
            for (float yaw : RigYawGameTest.YAWS) {
                FakePlayer v = Bodies.player(helper, new BlockPos(1, 1, 1), yaw);
                Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
                Vec3 at = Bodies.local(v, yaw, 0.0, 1.0, 2.9);
                arrow.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
                arrow.setDeltaMovement(Bodies.forward(yaw).scale(-3.0));
                DamageSource src = helper.getLevel().damageSources().arrow(arrow, null);
                helper.assertFalse(HitboxApi.shouldRejectGap(v, src, HitCategory.PIERCING),
                        "yaw " + yaw + ": a full-draw arrow on the chest was rejected as a gap");
                HitboxPart part = HitboxApi.classifyHit(v, src, HitCategory.PIERCING);
                helper.assertTrue(part == HitboxPart.TORSO, "yaw " + yaw + ": arrow classified " + part);
                List<HitboxPart> pierced = HitboxApi.classifyHitPierced(v, src, HitCategory.PIERCING);
                helper.assertTrue(!pierced.isEmpty() && pierced.get(0) == HitboxPart.TORSO,
                        "yaw " + yaw + ": arrow pierced " + pierced);
            }
        } finally {
            TestConfig.reset();
        }
        helper.succeed();
    }

    /** Eye 3.4 / 4.5 ahead: chest + pad beyond {@code meleeReach} 3.0, envelope within it (client pick lands). */
    @AhfGameTest
    public void aSwingBeyondMeleeReachThatTheEnvelopeAcceptsIsNotAGap(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        for (double dist : new double[]{3.4, 4.5}) {
            DamageSource swing = meleeAt(helper, v, 1.0, dist);
            helper.assertFalse(HitboxApi.shouldRejectGap(v, swing, HitCategory.BLUNT),
                    "a chest swing from " + dist + " was rejected as a gap at meleeReach " + AhfConfig.meleeReach());
            HitboxPart part = HitboxApi.classifyHit(v, swing, HitCategory.BLUNT);
            helper.assertTrue(part == HitboxPart.TORSO, "swing from " + dist + " classified " + part);
        }
        helper.succeed();
    }

    @AhfGameTest
    public void gapToleranceForgivesAGrazeButNotAWhiff(GameTestHelper helper) {
        for (float yaw : RigYawGameTest.YAWS) {
            FakePlayer v = Bodies.player(helper, new BlockPos(1, 1, 1), yaw);
            DamageSource graze = meleeAt(helper, v, JUST_ABOVE_HEAD);
            DamageSource whiff = meleeAt(helper, v, WELL_ABOVE_HEAD);
            try {
                helper.assertFalse(HitboxApi.isGapShot(v, graze, HitCategory.BLUNT), "yaw " + yaw
                        + ": a swing 0.05 above the head was rejected at tolerance " + AhfConfig.hitGapRejectTolerance());
                helper.assertTrue(HitboxApi.isGapShot(v, whiff, HitCategory.BLUNT),
                        "yaw " + yaw + ": a swing 0.5 above the head landed");
                TestConfig.hitGapRejectTolerance(0.0);
                helper.assertTrue(HitboxApi.isGapShot(v, graze, HitCategory.BLUNT),
                        "yaw " + yaw + ": at tolerance 0 the graze must be a gap; the pad is not what forgave it");
            } finally {
                TestConfig.reset();
            }
        }
        helper.succeed();
    }

    @AhfGameTest
    public void aSegmentMissingEveryLimbButInsideTheEnvelopeTakesTheNearestPart(GameTestHelper helper) {
        for (float yaw : RigYawGameTest.YAWS) {
            FakePlayer v = Bodies.player(helper, new BlockPos(1, 1, 1), yaw);
            RayHit graze = HitboxApi.classifyRay(v,
                    Bodies.local(v, yaw, 0.0, JUST_ABOVE_HEAD, 2.0), Bodies.local(v, yaw, 0.0, JUST_ABOVE_HEAD, -2.0));
            helper.assertTrue(graze != null && graze.part() == HitboxPart.HEAD && !graze.limb(),
                    "yaw " + yaw + ": envelope graze over the head should be a non-limb HEAD, got " + graze);
            RayHit miss = HitboxApi.classifyRay(v,
                    Bodies.local(v, yaw, 0.0, WELL_ABOVE_HEAD, 2.0), Bodies.local(v, yaw, 0.0, WELL_ABOVE_HEAD, -2.0));
            helper.assertTrue(miss == null, "yaw " + yaw + ": a segment above the envelope should miss, got " + miss);
        }
        helper.succeed();
    }

    /** 0.6 in front of the chest: inside the 0.7 envelope, 0.475 clear of every limb (> pad 0.2). */
    @AhfGameTest
    public void preciseRejectsAnEnvelopeNearMissOnTheSegmentPath(GameTestHelper helper) {
        try {
            for (float yaw : RigYawGameTest.YAWS) {
                FakePlayer v = Bodies.player(helper, new BlockPos(1, 1, 1), yaw);
                Vec3 from = Bodies.local(v, yaw, 2.0, 1.0, 0.6);
                Vec3 to = Bodies.local(v, yaw, -2.0, 1.0, 0.6);
                TestConfig.hitRegistrationMode(HitRegMode.ENVELOPE);
                helper.assertTrue(HitboxApi.classifyRay(v, from, to) != null,
                        "yaw " + yaw + ": setup: the segment must cross the envelope");
                TestConfig.hitRegistrationMode(HitRegMode.PRECISE);
                RayHit got = HitboxApi.classifyRay(v, from, to);
                helper.assertTrue(got == null, "yaw " + yaw + ": PRECISE accepted a 0.475 near miss as " + got);
                RayHit graze = HitboxApi.classifyRay(v,
                        Bodies.local(v, yaw, 0.0, JUST_ABOVE_HEAD, 2.0), Bodies.local(v, yaw, 0.0, JUST_ABOVE_HEAD, -2.0));
                helper.assertTrue(graze != null && graze.part() == HitboxPart.HEAD,
                        "yaw " + yaw + ": PRECISE must still forgive a graze within the pad, got " + graze);
            }
        } finally {
            TestConfig.reset();
        }
        helper.succeed();
    }

    /** Segment stops 0.5 short of the chest: the limb test is on the segment, not the ray beyond it. */
    @AhfGameTest
    public void aSegmentEndingShortOfTheBodyDoesNotHitIt(GameTestHelper helper) {
        try {
            TestConfig.hitRegistrationMode(HitRegMode.PRECISE);
            for (float yaw : RigYawGameTest.YAWS) {
                FakePlayer v = Bodies.player(helper, new BlockPos(1, 1, 1), yaw);
                Vec3 from = Bodies.local(v, yaw, 0.0, 1.0, 2.0);
                Vec3 to = Bodies.local(v, yaw, 0.0, 1.0, 0.5);
                RayHit got = HitboxApi.classifyRay(v, from, to);
                helper.assertTrue(got == null, "yaw " + yaw + ": a segment ending 0.5 in front hit " + got);
                RayHit full = HitboxApi.classifyRay(v, from, Bodies.local(v, yaw, 0.0, 1.0, -2.0));
                helper.assertTrue(full != null && full.part() == HitboxPart.TORSO && full.limb()
                                && full.t() > 0.4 && full.t() < 0.5,
                        "yaw " + yaw + ": the full segment should enter the torso at t ~0.47, got " + full);
            }
        } finally {
            TestConfig.reset();
        }
        helper.succeed();
    }

    @AhfGameTest
    public void aPiercingSegmentListsPartsInEntryOrderUntilTheBudgetIsSpent(GameTestHelper helper) {
        List<HitboxPart> want = List.of(HitboxPart.RIGHT_ARM, HitboxPart.TORSO);
        for (float yaw : RigYawGameTest.YAWS) {
            FakePlayer v = Bodies.player(helper, new BlockPos(1, 1, 1), yaw);
            Vec3 from = Bodies.local(v, yaw, 2.0, ARM_Y, 0.0);
            Vec3 to = Bodies.local(v, yaw, -2.0, ARM_Y, 0.0);
            List<RayHit> hits = HitboxApi.classifyRayPierced(v, from, to);
            List<HitboxPart> got = Bodies.parts(hits);
            helper.assertTrue(got.equals(want),
                    "yaw " + yaw + ": budget 1.0 = arm 0.25 + torso 0.8 => " + want + ", got " + got);
            helper.assertTrue(hits.get(0).t() < hits.get(1).t(), "yaw " + yaw + ": entry distances out of order");
        }
        helper.succeed();
    }

    /** Round-style source: segment + tick known; the attacker's eye and look (turned away here) are never used. */
    @AhfGameTest
    public void aHitSourceIsClassifiedAndGapTestedOnItsOwnRewoundSegment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer v = Bodies.player(helper);
        level.addFreshEntity(v);
        FakePlayer shooter = Bodies.player(helper, new BlockPos(1, 1, 1), 90.0F);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    long t = level.getGameTime();
                    Vec3 was = v.position();
                    v.snapTo(was.x + 3.0, was.y, was.z, 0.0F, 0.0F);
                    Bodies.face(v, 0.0F);
                    try {
                        TestConfig.hitRegistrationMode(HitRegMode.PRECISE);
                        Source hit = new Source(level, shooter, was.add(0.0, 1.0, 2.0), was.add(0.0, 1.0, -2.0), t, null);
                        HitboxPart part = HitboxApi.classifyHit(v, hit, HitCategory.BALLISTIC);
                        helper.assertTrue(part == HitboxPart.TORSO, "rewound source torso, got " + part);
                        helper.assertFalse(HitboxApi.shouldRejectGap(v, hit, HitCategory.BALLISTIC),
                                "PRECISE rejected a hit that is on the rig at the source's tick");
                        Vec3 point = HitboxApi.resolveHitPoint(v, hit, HitCategory.BALLISTIC);
                        helper.assertTrue(point != null && Math.abs(point.z - was.z - 0.125) < 0.05
                                        && Math.abs(point.x - was.x) < 1.0e-6,
                                "hit point " + point + " not on the old chest front");
                        Source nearMiss = new Source(level, shooter, was.add(2.0, 1.0, 0.6), was.add(-2.0, 1.0, 0.6),
                                t, null);
                        helper.assertTrue(HitboxApi.shouldRejectGap(v, nearMiss, HitCategory.BALLISTIC),
                                "PRECISE forgave a 0.475 near miss at the source's tick");
                        helper.assertTrue(HitboxApi.classifyHit(v, nearMiss, HitCategory.BALLISTIC) == null,
                                "near miss classified");
                        Source given = new Source(level, shooter, was, was, t, HitboxPart.LEFT_LEG);
                        helper.assertTrue(HitboxApi.classifyHit(v, given, HitCategory.BALLISTIC) == HitboxPart.LEFT_LEG,
                                "a part the caller already classified was not used");
                        Source givenOff = new Source(level, shooter, was.add(2.0, 1.0, 0.6), was.add(-2.0, 1.0, 0.6),
                                t, HitboxPart.LEFT_LEG);
                        helper.assertFalse(HitboxApi.shouldRejectGap(v, givenOff, HitCategory.BALLISTIC),
                                "a caller-classified part was gap-rejected off its segment");
                    } finally {
                        TestConfig.reset();
                        v.discard();
                    }
                })
                .thenSucceed();
    }

    private static final class Source extends DamageSource implements AhfHitSource {
        private final Vec3 from;
        private final Vec3 to;
        private final long tick;
        private final HitboxPart part;

        Source(ServerLevel level, Entity shooter, Vec3 from, Vec3 to, long tick, HitboxPart part) {
            super(level.damageSources().generic().typeHolder(), null, shooter);
            this.from = from;
            this.to = to;
            this.tick = tick;
            this.part = part;
        }

        @Override
        public Vec3 hitFrom() {
            return from;
        }

        @Override
        public Vec3 hitTo() {
            return to;
        }

        @Override
        public long hitGameTime() {
            return tick;
        }

        @Override
        public HitboxPart hitPart() {
            return part;
        }
    }

    @AhfGameTest
    public void vanillaEntityPicksUseTheEnvelope(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        helper.getLevel().addFreshEntity(v);
        FakePlayer shooter = Bodies.player(helper, new BlockPos(1, 1, 1), 0.0F);
        Vec3 feet = v.position();
        Vec3 from = new Vec3(feet.x + 0.5, feet.y + 1.0, feet.z - 3.0);
        Vec3 to = new Vec3(feet.x + 0.5, feet.y + 1.0, feet.z + 3.0);
        AABB search = new AABB(from, to).inflate(1.0);
        try {
            helper.assertTrue(projectilePick(helper, shooter, v, from, to, search) != null,
                    "projectile pick 0.2 outside the vanilla box but inside the envelope missed");
            helper.assertTrue(ProjectileUtil.getEntityHitResult(shooter, from, to, search, e -> e == v, 100.0) != null,
                    "melee pick 0.2 outside the vanilla box but inside the envelope missed");
            Vec3 eye = new Vec3(feet.x + 0.5, feet.y + 1.5, feet.z);
            Vec3 away = eye.add(3.0, 0.0, 0.0);
            helper.assertTrue(ProjectileUtil.getEntityHitResult(shooter, eye, away, new AABB(eye, away).inflate(1.0),
                            e -> e == v, 9.0) == null,
                    "an eye inside the envelope (outside the vanilla box) looking away picked the target");
            TestConfig.hitRegistrationMode(HitRegMode.OFF);
            helper.assertTrue(projectilePick(helper, shooter, v, from, to, search) == null,
                    "with the envelope OFF the same pick must miss; the envelope is not what widened it");
        } finally {
            TestConfig.reset();
            v.discard();
        }
        helper.succeed();
    }

    private static EntityHitResult projectilePick(GameTestHelper helper, FakePlayer shooter, FakePlayer v,
                                                  Vec3 from, Vec3 to, AABB search) {
        return ProjectileUtil.getEntityHitResult(helper.getLevel(), shooter, from, to, search, e -> e == v, 0.0F);
    }

    @AhfGameTest
    public void anAnimSamplerNeverPosesAServerEntity(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        double hanging = HumanoidRig.compute(v).rightArm.center().z;
        HumanoidRig.setAnimSampler((e, bone, in) -> {
            if (!bone.equals("rightArm")) {
                return null;
            }
            double[] out = in.clone();
            out[3] = -Math.PI / 2.0;
            return out;
        });
        double sampled;
        try {
            sampled = HumanoidRig.compute(v).rightArm.center().z;
        } finally {
            HumanoidRig.setAnimSampler(null);
        }
        helper.assertTrue(Math.abs(hanging) < 0.1, "hanging arm centre z=" + hanging);
        helper.assertTrue(sampled == hanging, "a (client) sampler posed a server entity: centre z=" + sampled);
        helper.succeed();
    }

    @AhfGameTest
    public void logoutDropsTheStreamedPose(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        HumanoidRig.LocalRig hint = HumanoidRig.compute(v);
        try {
            TestConfig.hitAuthority(HitAuthority.CLIENT_HINT);
            RigCache.submitHint(v.getId(), hint, helper.getLevel().getGameTime());
            helper.assertTrue(RigCache.resolve(v) == hint, "a plausible fresh hint was not used under CLIENT_HINT");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(v));
            helper.assertTrue(RigCache.resolve(v) != hint, "the hint outlived its player's logout");
        } finally {
            RigCache.clearHint(v.getId());
            TestConfig.reset();
        }
        helper.succeed();
    }

    /** Through AHF's own mod-bus listener, as a file edit arrives: no direct {@code bind()}. */
    @AhfGameTest
    public void aConfigReloadReachesAhfConfigAndRigTuning(GameTestHelper helper) {
        helper.assertTrue(AhfConfigSpec.SPEC.isLoaded(), "ahf config was never loaded");
        ModConfigSpec.DoubleValue tol = AhfConfigSpec.SPEC.getValues().get(List.of("hitRegistration", "hitGapRejectTolerance"));
        ModConfigSpec.EnumValue<HitAuthority> auth = AhfConfigSpec.SPEC.getValues().get(List.of("authority", "hitAuthority"));
        ModConfigSpec.DoubleValue reach = AhfConfigSpec.SPEC.getValues()
                .get(List.of("hitRegistration", "envelopeReach", "standingHorizontal"));
        ModContainer ahf = ModList.get().getModContainerById(Ahf.MOD_ID).orElseThrow();
        ModConfig config = ModConfigs.getModConfigs(Ahf.MOD_ID).stream()
                .filter(c -> c.getSpec() == AhfConfigSpec.SPEC).findFirst().orElseThrow();
        double prevTol = tol.get();
        HitAuthority prevAuth = auth.get();
        double prevReach = reach.get();
        try {
            tol.set(0.7);
            auth.set(HitAuthority.CLIENT_HINT);
            reach.set(0.55);
            ahf.getEventBus().post(new ModConfigEvent.Reloading(config));
            helper.assertTrue(AhfConfig.hitGapRejectTolerance() == 0.7, "tolerance not bound");
            helper.assertTrue(AhfConfig.hitAuthority() == HitAuthority.CLIENT_HINT, "authority not bound");
            helper.assertTrue(AhfConfig.hitEnvelopeReachHorizontal(RigTuning.RigPose.STANDING) == 0.55,
                    "envelope reach not bound");
            helper.assertTrue(RigTuning.envReach(RigTuning.RigPose.STANDING, RigTuning.EnvAxis.HORIZONTAL) == 0.55,
                    "rig tuning envelope not reseeded");
        } finally {
            tol.set(prevTol);
            auth.set(prevAuth);
            reach.set(prevReach);
            ahf.getEventBus().post(new ModConfigEvent.Reloading(config));
        }
        helper.succeed();
    }
}
