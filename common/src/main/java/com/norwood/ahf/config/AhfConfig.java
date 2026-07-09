package com.norwood.ahf.config;

import com.norwood.ahf.hit.HitAuthority;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.RigTuning;

public final class AhfConfig {

    private static boolean geometricHitLocation = true;
    private static boolean poseAwareArms = true;
    private static double headBandBottom = 0.74;
    private static double legBandTop = 0.40;
    private static double armSideThreshold = 0.80;
    private static double meleeReach = 3.0;
    private static boolean riggedLimbBoxes = true;
    private static double limbBoxPadding = 0.02;
    private static boolean hitboxDebug = false;
    private static boolean gunArmPose = true;
    private static HitRegMode hitRegistrationMode = HitRegMode.ENVELOPE;

    private static double envelopeReachHStanding = 0.4;
    private static double envelopeReachVStanding = 0.2;
    private static double envelopeReachHCrouching = 0.5;
    private static double envelopeReachVCrouching = 0.1;
    private static double envelopeReachHProne = 1.0;
    private static double envelopeReachVProne = 0.3;

    private static double stanceEnvelopeReachHorizontal = 1.0;
    private static double stanceEnvelopeReachVertical = 0.3;

    private static HitAuthority hitAuthority = HitAuthority.SERVER;
    private static int poseStreamMinIntervalTicks = 2;
    private static int poseStreamMaxIntervalTicks = 10;
    private static int poseHintMaxAgeTicks = 30;
    private static double poseHintMargin = 0.6;

    private static boolean penetrationEnabled = false;
    private static double penetrationBudget = 1.0;
    private static double penetrationEnergyFalloff = 0.5;

    private static double penResistHead = 0.5;
    private static double penResistTorso = 0.8;
    private static double penResistArm = 0.25;
    private static double penResistLeg = 0.4;

    private AhfConfig() {
    }

    public static boolean geometricHitLocation() {
        return geometricHitLocation;
    }

    public static void setGeometricHitLocation(boolean v) {
        geometricHitLocation = v;
    }

    public static boolean poseAwareArms() {
        return poseAwareArms;
    }

    public static void setPoseAwareArms(boolean v) {
        poseAwareArms = v;
    }

    public static double headBandBottom() {
        return headBandBottom;
    }

    public static void setHeadBandBottom(double v) {
        headBandBottom = v;
    }

    public static double legBandTop() {
        return legBandTop;
    }

    public static void setLegBandTop(double v) {
        legBandTop = v;
    }

    public static double armSideThreshold() {
        return armSideThreshold;
    }

    public static void setArmSideThreshold(double v) {
        armSideThreshold = v;
    }

    public static double meleeReach() {
        return meleeReach;
    }

    public static void setMeleeReach(double v) {
        meleeReach = v;
    }

    public static boolean riggedLimbBoxes() {
        return riggedLimbBoxes;
    }

    public static void setRiggedLimbBoxes(boolean v) {
        riggedLimbBoxes = v;
    }

    public static double limbBoxPadding() {
        return limbBoxPadding;
    }

    public static void setLimbBoxPadding(double v) {
        limbBoxPadding = v;
    }

    public static boolean hitboxDebug() {
        return hitboxDebug;
    }

    public static void setHitboxDebug(boolean v) {
        hitboxDebug = v;
    }

    public static boolean gunArmPose() {
        return gunArmPose;
    }

    public static void setGunArmPose(boolean v) {
        gunArmPose = v;
    }

    public static HitRegMode hitRegistrationMode() {
        return hitRegistrationMode;
    }

    public static void setHitRegistrationMode(HitRegMode v) {
        hitRegistrationMode = v;
    }

    public static double hitEnvelopeReachHorizontal(RigTuning.RigPose pose) {
        return switch (pose) {
            case STANDING -> envelopeReachHStanding;
            case CROUCHING -> envelopeReachHCrouching;
            case PRONE -> envelopeReachHProne;
        };
    }

    public static void setHitEnvelopeReachHorizontal(RigTuning.RigPose pose, double v) {
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

    public static void setHitEnvelopeReachVertical(RigTuning.RigPose pose, double v) {
        switch (pose) {
            case STANDING -> envelopeReachVStanding = v;
            case CROUCHING -> envelopeReachVCrouching = v;
            case PRONE -> envelopeReachVProne = v;
        }
    }

    public static double stanceEnvelopeReachHorizontal() {
        return stanceEnvelopeReachHorizontal;
    }

    public static void setStanceEnvelopeReachHorizontal(double v) {
        stanceEnvelopeReachHorizontal = v;
    }

    public static double stanceEnvelopeReachVertical() {
        return stanceEnvelopeReachVertical;
    }

    public static void setStanceEnvelopeReachVertical(double v) {
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

    public static void setHitAuthority(HitAuthority v) {
        hitAuthority = v;
    }

    public static int poseStreamMinIntervalTicks() {
        return poseStreamMinIntervalTicks;
    }

    public static void setPoseStreamMinIntervalTicks(int v) {
        poseStreamMinIntervalTicks = v;
    }

    public static int poseStreamMaxIntervalTicks() {
        return poseStreamMaxIntervalTicks;
    }

    public static void setPoseStreamMaxIntervalTicks(int v) {
        poseStreamMaxIntervalTicks = v;
    }

    public static int poseHintMaxAgeTicks() {
        return poseHintMaxAgeTicks;
    }

    public static void setPoseHintMaxAgeTicks(int v) {
        poseHintMaxAgeTicks = v;
    }

    public static double poseHintMargin() {
        return poseHintMargin;
    }

    public static void setPoseHintMargin(double v) {
        poseHintMargin = v;
    }

    public static boolean penetrationEnabled() {
        return penetrationEnabled;
    }

    public static void setPenetrationEnabled(boolean v) {
        penetrationEnabled = v;
    }

    public static double penetrationBudget() {
        return penetrationBudget;
    }

    public static void setPenetrationBudget(double v) {
        penetrationBudget = v;
    }

    public static double penetrationEnergyFalloff() {
        return penetrationEnergyFalloff;
    }

    public static void setPenetrationEnergyFalloff(double v) {
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

    public static void setPenResistHead(double v) {
        penResistHead = v;
    }

    public static void setPenResistTorso(double v) {
        penResistTorso = v;
    }

    public static void setPenResistArm(double v) {
        penResistArm = v;
    }

    public static void setPenResistLeg(double v) {
        penResistLeg = v;
    }
}
