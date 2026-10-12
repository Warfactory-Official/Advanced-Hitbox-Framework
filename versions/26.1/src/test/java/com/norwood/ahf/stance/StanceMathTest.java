package com.norwood.ahf.stance;

import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.pose.PoseClip;
import com.norwood.ahf.rig.HumanoidRig;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StanceMathTest {

    private static final double EPS = 1.0e-9;
    private static final double STAND_EYE = 1.62;
    /** Vanilla: eye 1.62 - neck pivot 1.5. */
    private static final double NECK_TO_EYE = 0.12;

    private static HumanoidRig.LocalRig pointRig(Vec3 at) {
        HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            slot.set(rig, new Obb(at, new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                    new Vec3(0.1, 0.1, 0.1), HitboxPart.HEAD));
        }
        return rig;
    }

    private static void assertVec(Vec3 want, Vec3 got, String what) {
        assertEquals(want.x, got.x, EPS, what + " x");
        assertEquals(want.y, got.y, EPS, what + " y");
        assertEquals(want.z, got.z, EPS, what + " z");
    }

    @Test
    void eyeOffsetIsTheBodyTransformOfTheEye() {
        for (Posture posture : new Posture[]{Posture.STAND, Posture.KNEEL}) {
            double eye = posture == Posture.KNEEL ? StanceLogic.KNEEL_EYE : STAND_EYE;
            for (double lean : new double[]{-1.0, -0.4, 0.0, 0.7, 1.0}) {
                StancePose pose = new StancePose(posture, lean, 0, 0, 0, 0, 0, false);
                HumanoidRig.LocalRig rig = pointRig(new Vec3(0.0, eye + pose.drop(), 0.0));
                HumanoidRig.applyWhole(rig, new double[]{0, -pose.drop(), 0, 0, 0, pose.roll, 0, 0});
                assertVec(new Vec3(0.0, eye, 0.0).add(pose.eyeOffsetLocal(eye)), rig.head.center(),
                        posture + " lean " + lean + ": eye offset != rig transform of the eye point");
            }
        }
    }

    @Test
    void fullRightLeanMovesTheEyeRightByTheLeverArm() {
        StancePose pose = new StancePose(Posture.STAND, 1.0, 0, 0, 0, 0, 0, false);
        Vec3 off = pose.eyeOffsetLocal(STAND_EYE);
        double arm = STAND_EYE - StancePose.PIVOT_Y;
        double a = Math.toRadians(StanceLogic.LEAN_MAX_DEGREES);
        assertEquals(arm * Math.sin(a), off.x, EPS, "lateral");
        assertEquals(arm * (Math.cos(a) - 1.0), off.y, EPS, "drop");
        assertEquals(0.0, off.z, EPS);
        assertEquals(-1.0, Math.signum(pose.roll), "right lean = negative roll (PlayerAnimator body frame)");
    }

    @Test
    void kneelDropsTheBodyWithoutMovingTheEye() {
        StancePose pose = new StancePose(Posture.KNEEL, 0.0, 0, 0, 0, 0, 0, false);
        assertVec(Vec3.ZERO, pose.eyeOffsetLocal(StanceLogic.KNEEL_EYE), "kneel eye");
        HumanoidRig.LocalRig rig = pointRig(new Vec3(0.2, 1.5, -0.1));
        HumanoidRig.applyWhole(rig, new double[]{0, -pose.drop(), 0, 0, 0, pose.roll, 0, 0});
        assertTrue(pose.drop() > 0.0, "kneel drop " + pose.drop());
        assertVec(new Vec3(0.2, 1.5 - pose.drop(), -0.1), rig.torso.center(), "kneel drop");
    }

    @Test
    void legsCounterRollSoTheyStayVertical() {
        StancePose pose = new StancePose(Posture.STAND, 1.0, 0, 0, 0, 0, 0, false);
        double[] leg = new double[PoseClip.CHANNELS];
        pose.apply(PoseClip.RIGHT_LEG, leg);
        assertEquals(-pose.roll, leg[5], EPS, "leg zRot must cancel the body roll");
        double[] arm = {0, 0, 0, 0.1, 0.2, 0.3, 0, 0};
        pose.apply(PoseClip.RIGHT_ARM, arm);
        assertEquals(0.3, arm[5], EPS, "arms roll with the body");
    }

    /** Lowest point of a pitch-bent leg (thigh, shin: 6 px each, half width 2 px), blocks; roll ignored. */
    private static double lowest(double hip, double[] v) {
        double thigh = v[3];
        double shin = thigh + v[PoseClip.BEND];
        double knee = hip - 0.375 * Math.cos(thigh);
        double end = knee - 0.375 * Math.cos(shin);
        double w = 0.125;
        return Math.min(knee - w * Math.abs(Math.sin(thigh)), Math.min(knee, end) - w * Math.abs(Math.sin(shin)));
    }

    /**
     * Level steps drawn from the clips: eye (neck + {@link #NECK_TO_EYE}) == the level's box eye; each leg's lowest
     * point on the ground. KNEEL over vanilla crouch (head y 4.2 px, legs y 12.2 px, render offset 2 px).
     */
    @Test
    void levelBodyMatchesItsBoxAndTheGround() {
        for (Posture posture : new Posture[]{Posture.STAND, Posture.KNEEL}) {
            boolean crouch = posture == Posture.KNEEL;
            double offset = crouch ? 2.0 / 16.0 : 0.0;
            for (int level = posture.minLevel(); level <= posture.maxLevel(); level++) {
                StancePose pose = new StancePose(posture, 0, level, 0, 0, 0, 0, false);
                double[] head = new double[PoseClip.CHANNELS];
                head[1] = crouch ? 4.2 : 0.0;
                pose.apply(PoseClip.HEAD, head);
                double eye = 1.5 - head[1] / 16.0 - offset - pose.drop() + NECK_TO_EYE;
                double want = posture == Posture.STAND
                        ? (level == 0 ? STAND_EYE : StanceLogic.STAND_EYE[level + 2])
                        : StanceLogic.KNEEL_EYES[level + 1];
                assertEquals(want, eye, 0.01, posture + " level " + level + " eye");
                for (int leg : new int[]{PoseClip.RIGHT_LEG, PoseClip.LEFT_LEG}) {
                    double[] v = new double[PoseClip.CHANNELS];
                    v[1] = crouch ? 12.2 : 12.0;
                    pose.apply(leg, v);
                    double hip = 1.5 - v[1] / 16.0 - offset - pose.drop();
                    assertEquals(0.0, lowest(hip, v), 0.02, posture + " level " + level + " leg " + leg + " lowest");
                }
            }
        }
    }

    @Test
    void sideStepMovesBodyAndEyeTogether() {
        for (double side : new double[]{-1, 0.4, 1}) {
            StancePose pose = new StancePose(Posture.KNEEL, 0.6, 0, side, 0, 0, 0, false);
            double eye = StanceLogic.KNEEL_EYE;
            HumanoidRig.LocalRig rig = pointRig(new Vec3(0.0, eye + pose.drop(), 0.0));
            double[] w = new double[PoseClip.CHANNELS];
            pose.body(w);
            HumanoidRig.applyWhole(rig, w);
            assertVec(new Vec3(0.0, eye, 0.0).add(pose.eyeOffsetLocal(eye)), rig.head.center(), "side " + side);
        }
        for (int side : new int[]{-1, 1}) {
            assertEquals(side, Math.signum(new StancePose(Posture.KNEEL, 0, 0, side, 0, 0, 0, false).shift()), "direction");
        }
        assertEquals(new StancePose(Posture.STAND, 0, 0, 1, 0, 0, 0, false).shift(),
                -new StancePose(Posture.STAND, 0, 0, -1, 0, 0, 0, false).shift(), EPS, "left mirrors right");
        assertEquals(0.0, new StancePose(Posture.PRONE, 0, 0, 1, 0, 0, 0, false).shift(), EPS, "prone side = roll");
    }

    /**
     * Step right (model px, player right +): left foot planted the whole step; right foot planted once the weight
     * moves ({@code side >= 4/7}). Foot = hip + 12 sin(zRot).
     */
    @Test
    void sideStepKeepsTheFeetPlanted() {
        double rightEnd = Double.NaN;
        for (int i = 7; i >= 0; i--) {
            StancePose pose = new StancePose(Posture.STAND, 0, 0, i / 7.0, 0, 0, 0, false);
            double body = pose.shift() * 16.0;
            double left = foot(pose, PoseClip.LEFT_LEG, body - 1.9);
            double right = foot(pose, PoseClip.RIGHT_LEG, body + 1.9);
            assertEquals(-1.9, left, 0.6, "left foot at " + i + "/7");
            if (i == 7) {
                rightEnd = right;
            } else if (i >= 4) {
                assertEquals(rightEnd, right, 0.6, "right foot at " + i + "/7");
            }
        }
        assertTrue(rightEnd > 1.9 + 4.0, "right foot stepped out: " + rightEnd);
    }

    private static double foot(StancePose pose, int leg, double hip) {
        double[] v = new double[PoseClip.CHANNELS];
        pose.apply(leg, v);
        return hip + 12.0 * Math.sin(v[5]);
    }

    @Test
    void proneRollEndsAfterItsTicksAndOnlyOnce() {
        StanceState s = new StanceState();
        s.posture = Posture.PRONE;
        s.startRoll(1);
        float last = -1;
        for (int i = 0; i < StanceLogic.ROLL_TICKS; i++) {
            assertTrue(s.rolling());
            float t = s.rollProgress(0);
            assertTrue(t > last, "progress " + t + " after " + last);
            last = t;
            s.stepRoll();
        }
        assertFalse(s.rolling(), "roll outlived its ticks");
        StanceState server = new StanceState();
        server.posture = Posture.PRONE;
        server.startRoll(1);
        s.mergeOwner(roundTrip(server, StanceState.ALL));
        assertFalse(s.rolling(), "echo of the predicted roll restarted it");
        StanceState remote = new StanceState();
        remote.apply(roundTrip(server, StanceState.ALL));
        assertTrue(remote.rolling() && remote.rollDir() == 1, "remote did not start the synced roll");
    }

    @Test
    void levelAndSideSurviveTheWire() {
        StanceState server = new StanceState();
        server.posture = Posture.STAND;
        server.level = -2;
        server.side = 1;
        StanceState remote = new StanceState();
        remote.apply(roundTrip(server, StanceState.ALL));
        assertEquals(-2, remote.level());
        assertEquals(1, remote.side());
    }

    @Test
    void downedSprawlIsSeededByEntityIdOnly() {
        double[] a = new double[PoseClip.CHANNELS];
        double[] b = new double[PoseClip.CHANNELS];
        double[] c = new double[PoseClip.CHANNELS];
        new StancePose(Posture.DOWNED, 0.0, 0, 0, 0, 0, 42, false).apply(PoseClip.RIGHT_ARM, a);
        new StancePose(Posture.DOWNED, 0.0, 0, 0, 0, 0, 42, false).apply(PoseClip.RIGHT_ARM, b);
        new StancePose(Posture.DOWNED, 0.0, 0, 0, 0, 0, 43, false).apply(PoseClip.RIGHT_ARM, c);
        assertEquals(a[5], b[5], "same id, same sprawl on every side");
        assertTrue(a[5] != c[5], "ids should differ");
        assertTrue(Math.abs(a[5] - StancePose.DOWNED_ARM) <= StancePose.DOWNED_JITTER + EPS);
    }

    @Test
    void localToWorldMatchesTheRigFrame() {
        for (float yaw : new float[]{0.0F, 90.0F, 180.0F, -45.0F, 200.0F}) {
            double y = Math.toRadians(yaw);
            assertVec(new Vec3(-Math.cos(y), 0, -Math.sin(y)), StancePose.localToWorld(new Vec3(1, 0, 0), yaw),
                    "right at " + yaw);
            assertVec(new Vec3(-Math.sin(y), 0, Math.cos(y)), StancePose.localToWorld(new Vec3(0, 0, 1), yaw),
                    "front at " + yaw);
        }
    }

    @Test
    void bodyYawEasesAndNeverLagsPastTheLimit() {
        assertEquals(45.0F, StanceLogic.stepBodyYaw(0.0F, 90.0F, Posture.STAND), 1e-4F, "clamped to max lag");
        assertEquals(5.0F, StanceLogic.stepBodyYaw(0.0F, 20.0F, Posture.STAND), 1e-4F, "eased inside the lag");
        assertEquals(70.0F, StanceLogic.stepBodyYaw(0.0F, 90.0F, Posture.PRONE), 1e-4F, "lying lag is tighter");
        assertEquals(12.0F, StanceLogic.stepBodyYaw(12.0F, 170.0F, Posture.DOWNED), "downed body never turns");
        float wrapped = StanceLogic.stepBodyYaw(170.0F, -170.0F, Posture.STAND);
        assertEquals(175.0F, wrapped, 1e-4F, "short way across +-180");
    }

    @Test
    void ownerKeepsItsPredictionUntilTheServerHasSeenIt() {
        StanceState owner = new StanceState();
        owner.seq = 6;
        owner.posture = Posture.KNEEL;
        StanceState server = new StanceState();
        server.seq = 5;
        server.posture = Posture.STAND;
        server.downed = true;
        owner.mergeOwner(roundTrip(server, StanceState.ALL));
        assertEquals(Posture.KNEEL, owner.posture, "stale echo overwrote the prediction");
        assertTrue(owner.downed, "locks are server-only and always adopted");
        server.seq = 6;
        server.downed = false;
        owner.mergeOwner(roundTrip(server, StanceState.ALL));
        assertEquals(Posture.STAND, owner.posture, "echo of the latest seq is the correction");
        assertFalse(owner.downed);
    }

    @Test
    void remoteAppliesAtViewTimeInOrder() {
        StanceState remote = new StanceState();
        StanceState server = new StanceState();
        server.posture = Posture.PRONE;
        server.stamp = 100;
        remote.enqueue(roundTrip(server, StanceState.TARGETS));
        server.posture = Posture.KNEEL;
        server.bodyYaw = 30.0F;
        server.stamp = 102;
        remote.enqueue(roundTrip(server, StanceState.YAW));
        remote.applyDue(99);
        assertEquals(Posture.STAND, remote.posture, "applied before its view tick");
        remote.applyDue(101);
        assertEquals(Posture.PRONE, remote.posture);
        assertEquals(0.0F, remote.bodyYaw);
        remote.applyDue(102);
        assertEquals(Posture.PRONE, remote.posture, "a yaw-only update must not touch targets");
        assertEquals(30.0F, remote.bodyYaw);
    }

    private static StanceState.Update roundTrip(StanceState s, int mask) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        s.write(buf, mask);
        StanceState.Update u = StanceState.Update.read(buf);
        assertEquals(0, buf.readableBytes(), "payload not fully consumed");
        return u;
    }

    /**
     * Server walk: vanilla's recurrence over the windowed displacement (0.25 b/t => target speed 1). A tracker gets
     * {@code WALK} only when its dead reckoning would drift: steady walking and standing still send almost nothing,
     * yet the tracker's walk stays on the server's.
     */
    @Test
    void walkIsDeadReckonedByTheTracker() {
        StanceState s = new StanceState();
        StanceState remote = new StanceState();
        int sent = 0;
        int sentLate = 0;
        double worst = 0;
        for (int t = 0; t <= 80; t++) {
            s.dirty = 0;
            s.stepWalk(0.0, t <= 50 ? 0.25 * t : 12.5);
            if ((s.dirty & StanceState.WALK) != 0) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                s.write(buf, StanceState.WALK);
                s.walkSent();
                remote.apply(StanceState.Update.read(buf));
                sent++;
                if (t > 30 && t <= 50 || t > 70) {
                    sentLate++;
                }
            }
            remote.reckonWalk();
            worst = Math.max(worst, Math.abs(remote.walkPos - s.walkPos));
        }
        assertTrue(s.walkSpeed < 0.01F, "stopped: speed " + s.walkSpeed);
        assertTrue(remote.walkSynced);
        assertTrue(worst <= StanceState.WALK_POS_EPSILON + 1.0e-6, "tracker drifted " + worst);
        assertTrue(sentLate <= 2, "steady states resent " + sentLate + " times");
        assertTrue(sent < 40, "sent " + sent + " of 81 ticks");
    }
}
