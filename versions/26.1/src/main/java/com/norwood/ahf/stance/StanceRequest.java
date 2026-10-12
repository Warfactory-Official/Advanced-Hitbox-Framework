package com.norwood.ahf.stance;

/**
 * Owner-requested stance targets.
 *
 * @param level    height step within {@code posture} ({@link Posture#minLevel()}..{@link Posture#maxLevel()}); 0 =
 *                 the posture's own height
 * @param side     -1 / 0 / +1: STAND / KNEEL step left / right, PRONE rolled onto the left / right side
 * @param lean     lean target in [-1, 1]; +1 = full right
 * @param aimYaw   weapon offset from the camera, degrees
 * @param aimPitch weapon offset from the camera, degrees
 */
public record StanceRequest(Posture posture, int level, int side, float lean, Rest rest, boolean ads, float aimYaw,
                            float aimPitch) {

    public static final StanceRequest STAND = new StanceRequest(Posture.STAND, 0, 0, 0.0F, Rest.NONE, false, 0.0F,
            0.0F);

    /** Current targets of {@code s}. */
    public static StanceRequest of(StanceState s) {
        return new StanceRequest(s.posture, s.level, s.side, s.leanTarget, s.rest, s.ads, s.aimYaw, s.aimPitch);
    }

    /** Another posture resets level + side (per-posture steps). */
    public StanceRequest withPosture(Posture p) {
        return p == posture ? this : new StanceRequest(p, 0, 0, lean, rest, ads, aimYaw, aimPitch);
    }

    public StanceRequest withLevel(int l) {
        return new StanceRequest(posture, l, side, lean, rest, ads, aimYaw, aimPitch);
    }

    public StanceRequest withSide(int s) {
        return new StanceRequest(posture, level, s, lean, rest, ads, aimYaw, aimPitch);
    }

    public StanceRequest withLean(float l) {
        return new StanceRequest(posture, level, side, l, rest, ads, aimYaw, aimPitch);
    }

    public StanceRequest withRest(Rest r) {
        return new StanceRequest(posture, level, side, lean, r, ads, aimYaw, aimPitch);
    }

    public StanceRequest withAds(boolean a, float yaw, float pitch) {
        return new StanceRequest(posture, level, side, lean, rest, a, yaw, pitch);
    }
}
