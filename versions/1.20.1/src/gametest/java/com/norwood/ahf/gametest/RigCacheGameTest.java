package com.norwood.ahf.gametest;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.hit.HitRegistration;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.rig.RigCache;
import com.norwood.ahf.rig.RigTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CLIENT_HINT is off by default => resolve() takes the server branch; plausible() tested directly. */
@GameTestHolder(Ahf.MOD_ID)
@PrefixGameTestTemplate(false)
public class RigCacheGameTest {

    private static final String TEMPLATE = "empty";

    private static Obb box(Vec3 center, double half) {
        return new Obb(center, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                new Vec3(half, half, half), HitboxPart.TORSO);
    }

    private static HumanoidRig.LocalRig uniformRig(Vec3 center, double half) {
        HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            slot.set(rig, box(center, half));
        }
        return rig;
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void theRigIsBuiltOncePerTickAndReused(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        HumanoidRig.LocalRig first = RigCache.get(v);
        helper.assertTrue(RigCache.get(v) == first, "the rig was rebuilt within one tick");
        helper.assertTrue(RigCache.resolve(v) == first, "resolve should return the cached rig without a hint");
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void twoVictimsDoNotShareARig(GameTestHelper helper) {
        FakePlayer a = Bodies.player(helper);
        FakePlayer b = Bodies.player(helper, new BlockPos(2, 1, 2), 0.0F);
        helper.assertTrue(a.getId() != b.getId(), "the two victims share an entity id");
        helper.assertTrue(RigCache.get(a) != RigCache.get(b), "cache key is not per-entity");
        helper.succeed();
    }

    /** Guards the identity tests: a stale/empty cached rig would pass them. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void theCachedRigIsTheSameOneComputeWouldBuild(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        HumanoidRig.LocalRig cached = RigCache.get(v);
        HumanoidRig.LocalRig fresh = HumanoidRig.compute(v);
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            Obb c = slot.get(cached);
            Obb f = slot.get(fresh);
            helper.assertTrue(c != null && f != null, "missing box for " + slot);
            helper.assertTrue(c.center().distanceTo(f.center()) <= 1.0e-6,
                    "cached " + slot + " at " + c.center() + " but a fresh build says " + f.center());
        }
        helper.succeed();
    }

    /** Guards the rejection tests below. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void aNormalPoseIsAccepted(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        helper.assertTrue(RigCache.plausible(v, HumanoidRig.compute(v)), "the server's own rig was rejected");
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void aMissingBoxIsRejected(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        HumanoidRig.LocalRig rig = uniformRig(new Vec3(0.0, 1.0, 0.0), 0.2);
        HumanoidRig.LocalRig.Slot.HEAD.set(rig, null);
        helper.assertFalse(RigCache.plausible(v, rig), "a rig without a head box was accepted");
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void aBoxFlungAwayFromTheBodyIsRejected(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        helper.assertFalse(RigCache.plausible(v, uniformRig(new Vec3(0.0, 500.0, 0.0), 0.2)),
                "a rig 500 blocks away was accepted");
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void aShrunkOrBallooningBoxIsRejected(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        helper.assertFalse(RigCache.plausible(v, uniformRig(new Vec3(0.0, 1.0, 0.0), 0.0001)),
                "a rig shrunk to nothing was accepted");
        helper.assertFalse(RigCache.plausible(v, uniformRig(new Vec3(0.0, 1.0, 0.0), 50.0)),
                "a rig ballooned to 50 blocks was accepted");
        helper.succeed();
    }

    /** Prone AABB is short; rig still spans a body length from the feet. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void aProneBodyIsStillJudgedAgainstAFullBodyLength(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        v.setPose(Pose.SWIMMING);
        helper.assertTrue(RigCache.plausible(v, HumanoidRig.compute(v)), "a prone player's own rig was rejected");
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void hintsCanBeSubmittedAndClearedWithoutDisturbingTheServerRig(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        HumanoidRig.LocalRig server = RigCache.get(v);
        RigCache.submitHint(v.getId(), uniformRig(new Vec3(0.0, 500.0, 0.0), 40.0), helper.getLevel().getGameTime());
        helper.assertTrue(RigCache.resolve(v) == server, "a hint displaced the server rig under SERVER authority");
        RigCache.clearHint(v.getId());
        helper.assertTrue(RigCache.resolve(v) == server, "clearing a hint disturbed the cached server rig");
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void theHitEnvelopeWidensAPlayersBoxButNotAnArbitraryEntitys(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        AABB raw = v.getBoundingBox();
        AABB envelope = HitRegistration.registrationBox(v);
        RigTuning.RigPose pose = HumanoidRig.resolvePose(v);
        double h = AhfConfig.hitEnvelopeReachHorizontal(pose);
        double vert = AhfConfig.hitEnvelopeReachVertical(pose);
        if (h > 0.0 || vert > 0.0) {
            helper.assertTrue(envelope.getSize() > raw.getSize(),
                    "the envelope did not widen the box although reach is h=" + h + " v=" + vert);
        } else {
            helper.assertTrue(envelope == raw, "with zero reach the envelope should be the untouched box");
        }
        helper.assertTrue(HitRegistration.isEnvelopeTarget(v), "a player must be an envelope target");
        helper.assertFalse(HitRegistration.isEnvelopeTarget(EntityType.PIG.create(helper.getLevel())),
                "a pig must not get a widened hit box by default");
        helper.succeed();
    }

    /** Lag-comp path hands in a rewound box: widen it, never swap in the live box. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void theEnvelopeCanBeAppliedToACallerSuppliedBox(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        AABB elsewhere = new AABB(100.0, 60.0, 100.0, 100.6, 61.8, 100.6);
        AABB widened = HitRegistration.registrationBox(v, elsewhere);
        helper.assertTrue(widened.getCenter().distanceTo(elsewhere.getCenter()) <= 1.0e-9,
                "the supplied box was recentred: " + widened.getCenter() + " vs " + elsewhere.getCenter());
        helper.assertTrue(widened.getSize() > elsewhere.getSize(), "the supplied box was not widened");
        helper.succeed();
    }
}
