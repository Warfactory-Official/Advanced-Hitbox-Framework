package com.norwood.ahf.config;

import com.norwood.ahf.hit.HitAuthority;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.RigTuning;

public final class AhfConfig {

    private static volatile boolean geometricHitLocation = true;
    private static volatile boolean poseAwareArms = true;
    private static volatile double headBandBottom = 0.74;
    private static volatile double legBandTop = 0.40;
    private static volatile double armSideThreshold = 0.80;
    private static volatile double meleeReach = 3.0;
    private static volatile boolean riggedLimbBoxes = true;
    private static volatile double limbBoxPadding = 0.02;
    private static volatile boolean hitboxDebug = false;
    private static volatile boolean gunArmPose = true;
    private static volatile HitRegMode hitRegistrationMode = HitRegMode.PRECISE;

    private static volatile double envelopeReachHStanding = 0.4;
    private static volatile double envelopeReachVStanding = 0.2;
    private static volatile double envelopeReachHCrouching = 0.5;
    private static volatile double envelopeReachVCrouching = 0.1;
    private static volatile double envelopeReachHProne = 1.0;
    private static volatile double envelopeReachVProne = 0.3;

    private static volatile double stanceEnvelopeReachHorizontal = 1.0;
    private static volatile double stanceEnvelopeReachVertical = 0.3;

    private static volatile HitAuthority hitAuthority = HitAuthority.SERVER;
    private static volatile int poseStreamMinIntervalTicks = 2;
    private static volatile int poseStreamMaxIntervalTicks = 10;
    private static volatile int poseHintMaxAgeTicks = 30;
    private static volatile double poseHintMargin = 0.6;

    private static volatile boolean penetrationEnabled = false;
    private static volatile double penetrationBudget = 1.0;
    private static volatile double penetrationEnergyFalloff = 0.5;

    private static volatile double penResistHead = 0.5;
    private static volatile double penResistTorso = 0.8;
    private static volatile double penResistArm = 0.25;
    private static volatile double penResistLeg = 0.4;

    private static volatile double hitGapRejectTolerance = 0.2;
    private static volatile boolean animatedHitboxes = false;
    private static volatile double poseStreamChangeEpsilon = 2.0e-4;
    private static volatile boolean logHitDetection = false;

    private static volatile boolean lagCompensation = true;
    private static volatile int lagCompMaxRewindTicks = 10;
    private static volatile int lagCompInterpTicks = 3;
    private static volatile int lagCompSlackTicks = 2;
    private static volatile double lagCompNonPlayerRange = 128.0;
    private static volatile int rttProbeIntervalTicks = 20;
    private static volatile int rttWindowSamples = 10;


    private AhfConfig() {
    }

    public static boolean geometricHitLocation() {
        return geometricHitLocation;
    }

    static void setGeometricHitLocation(boolean v) {
        geometricHitLocation = v;
    }

    public static boolean poseAwareArms() {
        return poseAwareArms;
    }

    static void setPoseAwareArms(boolean v) {
        poseAwareArms = v;
    }

    public static double headBandBottom() {
        return headBandBottom;
    }

    static void setHeadBandBottom(double v) {
        headBandBottom = v;
    }

    public static double legBandTop() {
        return legBandTop;
    }

    static void setLegBandTop(double v) {
        legBandTop = v;
    }

    public static double armSideThreshold() {
        return armSideThreshold;
    }

    static void setArmSideThreshold(double v) {
        armSideThreshold = v;
    }

    public static double meleeReach() {
        return meleeReach;
    }

    static void setMeleeReach(double v) {
        meleeReach = v;
    }

    public static boolean riggedLimbBoxes() {
        return riggedLimbBoxes;
    }

    static void setRiggedLimbBoxes(boolean v) {
        riggedLimbBoxes = v;
    }

    public static double limbBoxPadding() {
        return limbBoxPadding;
    }

    static void setLimbBoxPadding(double v) {
        limbBoxPadding = v;
    }

    public static boolean hitboxDebug() {
        return hitboxDebug;
    }

    static void setHitboxDebug(boolean v) {
        hitboxDebug = v;
    }

    public static boolean gunArmPose() {
        return gunArmPose;
    }

    static void setGunArmPose(boolean v) {
        gunArmPose = v;
    }

    public static HitRegMode hitRegistrationMode() {
        return hitRegistrationMode;
    }

    static void setHitRegistrationMode(HitRegMode v) {
        hitRegistrationMode = v;
    }

    public static double hitEnvelopeReachHorizontal(RigTuning.RigPose pose) {
        return switch (pose) {
            case STANDING -> envelopeReachHStanding;
            case CROUCHING -> envelopeReachHCrouching;
            case PRONE -> envelopeReachHProne;
        };
    }

    static void setHitEnvelopeReachHorizontal(RigTuning.RigPose pose, double v) {
        switch (pose) {
            case STANDING -> envelopeReachHStanding = v;
            case CROUCHING -> envelopeReachHCrouching = v;
            case PRONE -> envelopeReachHProne = v;
        }
    }

    public static double hitEnvelopeReachVertical(RigTuning.RigPose pose) {
        return switch (pose) {
            case STANDING -> envelopeReachVStanding;
            case CROUCHING -> envelopeReachVCrouching;
            case PRONE -> envelopeReachVProne;
        };
    }

    static void setHitEnvelopeReachVertical(RigTuning.RigPose pose, double v) {
        switch (pose) {
            case STANDING -> envelopeReachVStanding = v;
            case CROUCHING -> envelopeReachVCrouching = v;
            case PRONE -> envelopeReachVProne = v;
        }
    }

    public static double stanceEnvelopeReachHorizontal() {
        return stanceEnvelopeReachHorizontal;
    }

    static void setStanceEnvelopeReachHorizontal(double v) {
        stanceEnvelopeReachHorizontal = v;
    }

    public static double stanceEnvelopeReachVertical() {
        return stanceEnvelopeReachVertical;
    }

    static void setStanceEnvelopeReachVertical(double v) {
        stanceEnvelopeReachVertical = v;
    }

    public static double[] envelopeReachSnapshot() {
        double[] a = new double[RigTuning.RigPose.VALUES.length * 2];
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            a[pose.ordinal() * 2] = hitEnvelopeReachHorizontal(pose);
            a[pose.ordinal() * 2 + 1] = hitEnvelopeReachVertical(pose);
        }
        return a;
    }

    public static HitAuthority hitAuthority() {
        return hitAuthority;
    }

    static void setHitAuthority(HitAuthority v) {
        hitAuthority = v;
    }

    public static int poseStreamMinIntervalTicks() {
        return poseStreamMinIntervalTicks;
    }

    static void setPoseStreamMinIntervalTicks(int v) {
        poseStreamMinIntervalTicks = v;
    }

    public static int poseStreamMaxIntervalTicks() {
        return poseStreamMaxIntervalTicks;
    }

    static void setPoseStreamMaxIntervalTicks(int v) {
        poseStreamMaxIntervalTicks = v;
    }

    public static int poseHintMaxAgeTicks() {
        return poseHintMaxAgeTicks;
    }

    static void setPoseHintMaxAgeTicks(int v) {
        poseHintMaxAgeTicks = v;
    }

    public static double poseHintMargin() {
        return poseHintMargin;
    }

    static void setPoseHintMargin(double v) {
        poseHintMargin = v;
    }

    public static boolean penetrationEnabled() {
        return penetrationEnabled;
    }

    static void setPenetrationEnabled(boolean v) {
        penetrationEnabled = v;
    }

    public static double penetrationBudget() {
        return penetrationBudget;
    }

    static void setPenetrationBudget(double v) {
        penetrationBudget = v;
    }

    public static double penetrationEnergyFalloff() {
        return penetrationEnergyFalloff;
    }

    static void setPenetrationEnergyFalloff(double v) {
        penetrationEnergyFalloff = v;
    }

    public static double penetrationResistance(HitboxPart lt) {
        if (lt == HitboxPart.HEAD) {
            return penResistHead;
        }
        if (lt == HitboxPart.TORSO) {
            return penResistTorso;
        }
        return lt.isLeg() ? penResistLeg : penResistArm;
    }

    static void setPenResistHead(double v) {
        penResistHead = v;
    }

    static void setPenResistTorso(double v) {
        penResistTorso = v;
    }

    static void setPenResistArm(double v) {
        penResistArm = v;
    }

    static void setPenResistLeg(double v) {
        penResistLeg = v;
    }

    public static double hitGapRejectTolerance() {
        return hitGapRejectTolerance;
    }

    static void setHitGapRejectTolerance(double v) {
        hitGapRejectTolerance = v;
    }

    public static boolean animatedHitboxes() {
        return animatedHitboxes;
    }

    static void setAnimatedHitboxes(boolean v) {
        animatedHitboxes = v;
    }

    public static boolean useClientPose() {
        return hitAuthority == HitAuthority.CLIENT_HINT || animatedHitboxes;
    }

    public static double poseStreamChangeEpsilon() {
        return poseStreamChangeEpsilon;
    }

    static void setPoseStreamChangeEpsilon(double v) {
        poseStreamChangeEpsilon = v;
    }

    public static boolean logHitDetection() {
        return logHitDetection;
    }

    static void setLogHitDetection(boolean v) {
        logHitDetection = v;
    }

    public static boolean lagCompensation() {
        return lagCompensation;
    }

    static void setLagCompensation(boolean v) {
        lagCompensation = v;
    }

    public static int lagCompMaxRewindTicks() {
        return lagCompMaxRewindTicks;
    }

    static void setLagCompMaxRewindTicks(int v) {
        lagCompMaxRewindTicks = v;
    }

    public static int lagCompInterpTicks() {
        return lagCompInterpTicks;
    }

    static void setLagCompInterpTicks(int v) {
        lagCompInterpTicks = v;
    }

    public static int lagCompSlackTicks() {
        return lagCompSlackTicks;
    }

    static void setLagCompSlackTicks(int v) {
        lagCompSlackTicks = v;
    }

    public static int lagCompHistoryDepth() {
        return lagCompMaxRewindTicks + lagCompInterpTicks + 1;
    }

    public static double lagCompNonPlayerRange() {
        return lagCompNonPlayerRange;
    }

    static void setLagCompNonPlayerRange(double v) {
        lagCompNonPlayerRange = v;
    }

    public static int rttProbeIntervalTicks() {
        return rttProbeIntervalTicks;
    }

    static void setRttProbeIntervalTicks(int v) {
        rttProbeIntervalTicks = v;
    }

    public static int rttWindowSamples() {
        return rttWindowSamples;
    }

    static void setRttWindowSamples(int v) {
        rttWindowSamples = v;
    }
}
