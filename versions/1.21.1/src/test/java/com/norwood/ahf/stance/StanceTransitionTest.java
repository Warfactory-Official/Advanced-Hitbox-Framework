package com.norwood.ahf.stance;

import com.norwood.ahf.pose.PoseClip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StanceTransitionTest {

    private static final double EPS = 1.0e-9;

    private static void ease(StanceState s, int ticks) {
        for (int i = 0; i < ticks; i++) {
            s.ease(StanceLogic.LEAN_RATE, StanceLogic.ADS_RATE, StanceLogic.STEP_RATE, StanceLogic.SIDE_RATE);
        }
    }

    /** STAND low -> KNEEL: the new posture starts at its own level (never through kneel_low), blend from 0. */
    @Test
    void postureChangeBlendsFromTheOldPoseAtItsHeight() {
        StanceState s = new StanceState();
        s.level = -2;
        ease(s, 40);
        s.posture = Posture.KNEEL;
        s.level = 1;
        ease(s, 1);
        assertEquals(Posture.STAND, s.from());
        assertEquals(-2.0F, s.fromHeight());
        assertEquals(1.0F, s.height(1.0F));
        assertEquals(0.0F, s.blend(0.0F));
        assertEquals(StanceLogic.UPRIGHT_RATE, s.blend(1.0F), 1e-6);
        ease(s, 10);
        assertEquals(1.0F, s.blend(1.0F));
    }

    /** Back to the posture being left mid-blend: same blend walked back, no jump. */
    @Test
    void reversalWalksTheBlendBack() {
        StanceState s = new StanceState();
        ease(s, 5);
        s.posture = Posture.KNEEL;
        ease(s, 2);
        float shown = s.blend(1.0F);
        s.posture = Posture.STAND;
        ease(s, 1);
        assertEquals(Posture.KNEEL, s.from());
        assertEquals(1.0F - shown, s.blend(0.0F), 1e-6);
    }

    /** Lying tilt eases both ways at LIE_RATE; upright pair never tilts. */
    @Test
    void lieEasesInAndOut() {
        StanceState s = new StanceState();
        s.posture = Posture.KNEEL;
        ease(s, 20);
        assertEquals(0.0F, s.lie(1.0F));
        s.posture = Posture.PRONE;
        ease(s, 6);
        assertEquals(6 * StanceLogic.LIE_RATE, s.lie(1.0F), 1e-6);
        ease(s, 20);
        assertEquals(1.0F, s.lie(1.0F));
        s.posture = Posture.STAND;
        ease(s, 1);
        assertEquals(1.0F - StanceLogic.LIE_RATE, s.lie(1.0F), 1e-6);
    }

    /** Blend 0 = the pose left (its crouch restored), 1 = the new pose. */
    @Test
    void blendEndsAreTheTwoPoses() {
        StancePose kneel = new StancePose(Posture.KNEEL, 0, 0, 0, 0, 0, false);
        double[] in = {-5, 2, 0, -0.3, 0.2, 0.1, 0, 0};
        double[] a = in.clone();
        new StancePose(Posture.STAND, 0, 0, 0, 0, 0, false)
                .leaving(new StancePose(Posture.KNEEL, 0, 0, 0, 0, 0, false), 0.0, 1.0, true)
                .apply(PoseClip.RIGHT_ARM, a);
        double[] crouched = in.clone();
        crouched[1] += 3.2;
        crouched[3] += 0.4;
        kneel.apply(PoseClip.RIGHT_ARM, crouched);
        assertArray(crouched, a, "blend 0 = kneel with vanilla crouch");
        double[] b = in.clone();
        new StancePose(Posture.STAND, 0, 0, 0, 0, 0, false)
                .leaving(new StancePose(Posture.KNEEL, 0, 0, 0, 0, 0, false), 1.0, 1.0, true)
                .apply(PoseClip.RIGHT_ARM, b);
        assertArray(in, b, "blend 1 = stand");
        double[] w = new double[PoseClip.CHANNELS];
        new StancePose(Posture.STAND, 0, 0, 0, 0, 0, false)
                .leaving(new StancePose(Posture.KNEEL, 0, 0, 0, 0, 0, false), 0.0, 1.0, true).body(w);
        assertEquals(-kneel.drop() - StancePose.CROUCH_RENDER_OFFSET, w[1], EPS, "kneel drop + render offset");
        assertTrue(kneel.drop() > 0.0);
    }

    /** Still + upright: planted inside TURN_START, then TURN_RATE steps until within TURN_STOP; moving eases. */
    @Test
    void turnInPlaceIsHysteretic() {
        StanceState s = new StanceState();
        assertEquals(0.0F, StanceLogic.stepBodyYaw(s, 30.0F, false), "planted");
        s.bodyYaw = StanceLogic.stepBodyYaw(s, 40.0F, false);
        assertEquals(StanceLogic.TURN_RATE, s.bodyYaw, 1e-4F, "step starts");
        for (int i = 0; i < 10; i++) {
            s.bodyYaw = StanceLogic.stepBodyYaw(s, 40.0F, false);
        }
        assertEquals(40.0F, s.bodyYaw, 1e-4F, "caught up");
        assertEquals(40.0F, StanceLogic.stepBodyYaw(s, 60.0F, false), "planted again");
        assertEquals(45.0F, StanceLogic.stepBodyYaw(s, 60.0F, true), 1e-4F, "moving eases");
        assertEquals(120.0F - StanceLogic.BODY_YAW_MAX_LAG, StanceLogic.stepBodyYaw(new StanceState(), 120.0F, false),
                1e-4F, "lag cap holds");
    }

    private static void assertArray(double[] want, double[] got, String what) {
        for (int i = 0; i < want.length; i++) {
            assertEquals(want[i], got[i], 1e-9, what + " ch " + i);
        }
    }
}
