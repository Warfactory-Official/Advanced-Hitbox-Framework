package com.norwood.ahf.rig;

public final class GunArmPose {

    private static final Pose HOLD = new Pose(
            -1.400, -0.300, 0.000,
            -1.400, 0.800, 0.000);
    private static final Pose AIM = new Pose(
            -1.600, -0.350, 0.000,
            -1.600, 0.800, 0.000);

    private GunArmPose() {
    }

    public static Pose resolve(float aimProgress) {
        double t = aimProgress < 0.0F ? 0.0 : (aimProgress > 1.0F ? 1.0 : aimProgress);
        return new Pose(
                lerp(t, HOLD.rightX, AIM.rightX),
                lerp(t, HOLD.rightY, AIM.rightY),
                lerp(t, HOLD.rightZ, AIM.rightZ),
                lerp(t, HOLD.leftX, AIM.leftX),
                lerp(t, HOLD.leftY, AIM.leftY),
                lerp(t, HOLD.leftZ, AIM.leftZ));
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    public record Pose(double rightX, double rightY, double rightZ,
                       double leftX, double leftY, double leftZ) {
    }
}
