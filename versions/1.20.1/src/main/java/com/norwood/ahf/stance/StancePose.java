package com.norwood.ahf.stance;

import com.norwood.ahf.pose.GunPoses;
import com.norwood.ahf.pose.PoseClip;
import com.norwood.ahf.rig.HumanoidRig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Stance geometry shared by the rig ({@link HumanoidRig}) and the PlayerAnimator layer: per-bone deltas
 * ({@link StanceClips}, PA channels) plus {@link #body} added to PlayerAnimator's {@code body} transform (pivot
 * {@link #PIVOT_Y}). Vanilla SWIMMING tilt applied after, once, by vanilla render and {@link HumanoidRig#applyVanillaTilt}.
 */
public final class StancePose {

    public static final double PIVOT_Y = 0.7;
    static final double PRONE_LEG_SPREAD = 0.1;
    static final double DOWNED_ARM = 1.2;
    static final double DOWNED_LEG = 0.25;
    static final double DOWNED_JITTER = 0.28;
    /** DOWNED on its back: PA body pitch pi inside the face-down swim tilt (net: on its back), yaw, lift. */
    static final double DOWNED_FLIP = Math.PI;
    static final double DOWNED_YAW = Math.toRadians(8.0);
    static final double DOWNED_LIFT = 0.1;
    private static final int CH = PoseClip.CHANNELS;

    public final Posture posture;
    /** Body roll about the forward axis, radians; lean right => negative. */
    public final double roll;
    /** Prone roll turn about the long axis so far, rad (right => positive); 0 when not rolling. */
    public final double rollTurn;
    /** PRONE bones from the pose library (GunPoses lying clip) instead of the procedural legs. */
    public final boolean libraryLying;
    private final double[] jitter = new double[4];
    /** [PA bone * CHANNELS + channel] over the layers below: positions added, rotations premultiplied (parent frame). */
    private final double[] delta = new double[PoseClip.BONES.length * CH];

    /**
     * @param height eased level ({@link Posture#minLevel()}..{@link Posture#maxLevel()})
     * @param side   eased side, -1..1
     * @param rollT  prone roll progress 0..1, {@code rollDir} -1 / 0 / +1
     */
    public StancePose(Posture posture, double lean, double height, double side, int rollDir, double rollT, int seed,
                      boolean libraryLying) {
        this.posture = posture;
        this.libraryLying = libraryLying;
        this.roll = -lean * Math.toRadians(StanceLogic.LEAN_MAX_DEGREES);
        this.rollTurn = rollDir * 2.0 * Math.PI * rollT * rollT * (3.0 - 2.0 * rollT);
        switch (posture) {
            case STAND -> {
                levels(height, Posture.STAND.minLevel(), StanceClips.STAND_LOW, StanceClips.STAND_MID, null);
                step(side, StanceClips.STEP);
            }
            case KNEEL -> {
                levels(height, Posture.KNEEL.minLevel(), StanceClips.KNEEL_LOW, StanceClips.KNEEL,
                        StanceClips.KNEEL_HIGH);
                step(side, StanceClips.KNEEL_STEP);
            }
            case PRONE -> {
                levels(height, Posture.PRONE.minLevel(), null, StanceClips.PRONE_HIGH);
                lieOnSide(side);
                if (rollDir != 0) {
                    StanceClips.add(StanceClips.PRONE_ROLL, rollT * StanceClips.PRONE_ROLL.endTick, 1.0, rollDir < 0,
                            delta);
                }
            }
            case DOWNED -> {
                int h = seed * 0x9E3779B9;
                for (int i = 0; i < jitter.length; i++) {
                    h ^= h >>> 15;
                    h *= 0x2C1B3C6D;
                    h ^= h >>> 12;
                    jitter[i] = ((h & 0xFFFF) / 65535.0 - 0.5) * 2.0 * DOWNED_JITTER;
                }
            }
        }
    }

    /** Null => vanilla pose (no stance, suspended, or STAND without lean / level / side). */
    public static @Nullable StancePose of(LivingEntity e, float partialTick) {
        if (!(e instanceof Player p)) {
            return null;
        }
        StanceState s = AhfStance.get(p);
        if (s == null) {
            return null;
        }
        Posture eff = s.effective();
        double lean = eff.lying() ? 0.0 : s.lean(partialTick);
        double height = s.height(partialTick);
        double side = s.sideAmount(partialTick);
        if (eff == Posture.STAND && lean == 0.0 && height == 0.0 && side == 0.0 || StanceLogic.suspended(p)) {
            return null;
        }
        return new StancePose(eff, lean, height, side, s.rollDir(), s.rollProgress(partialTick), p.getId(),
                eff == Posture.PRONE && GunPoses.libraryLying(p));
    }

    /** Clip per level from {@code min}; null = the posture's own pose. Blend between neighbours eased INOUTSINE. */
    private void levels(double h, int min, PoseClip... clips) {
        double i = Mth.clamp(h - min, 0, clips.length - 1);
        int lo = (int) Math.floor(i);
        int hi = Math.min(lo + 1, clips.length - 1);
        double e = PoseClip.fadeIn(i - lo, 1);
        StanceClips.add(clips[lo], Double.NaN, 1.0 - e, false, delta);
        StanceClips.add(clips[hi], Double.NaN, e, false, delta);
    }

    /** Step clip scrubbed by eased side (0 -> 1 = step out, back = reversed). */
    private void step(double side, PoseClip right) {
        StanceClips.add(right, Math.abs(side) * right.endTick, 1.0, side < 0, delta);
    }

    private void lieOnSide(double side) {
        StanceClips.add(StanceClips.PRONE_SIDE, Double.NaN, PoseClip.fadeIn(Math.abs(side), 1), side < 0, delta);
    }

    /** Body drop, blocks (PA {@code body} y down). */
    public double drop() {
        return -delta[PoseClip.BODY * CH + 1];
    }

    /** Side step, blocks, rig local x (right +). */
    public double shift() {
        return delta[PoseClip.BODY * CH];
    }

    /** Adds this stance to a PA {@code body} transform {@code x, y, z, pitch, yaw, roll, bend axis, bend} (PA frame). */
    public void body(double[] w) {
        for (int ch = 0; ch < CH; ch++) {
            w[ch] += delta[PoseClip.BODY * CH + ch];
        }
        w[5] += roll;
        w[4] += rollTurn;
        if (posture == Posture.DOWNED) {
            w[2] += DOWNED_LIFT;
            w[3] += DOWNED_FLIP;
            w[4] += DOWNED_YAW;
        }
    }

    /** PA bone {@code bone} ({@link PoseClip#HEAD}..{@link PoseClip#LEFT_LEG}), {@link PoseClip#CHANNELS} values, in place. */
    public void apply(int bone, double[] v) {
        boolean leg = bone == PoseClip.RIGHT_LEG || bone == PoseClip.LEFT_LEG;
        boolean arm = bone == PoseClip.RIGHT_ARM || bone == PoseClip.LEFT_ARM;
        double sideSign = bone == PoseClip.RIGHT_ARM || bone == PoseClip.RIGHT_LEG ? 1.0 : -1.0;
        if (posture == Posture.PRONE && leg && !libraryLying) {
            v[3] = 0.0;
            v[4] = 0.0;
            v[5] = sideSign * PRONE_LEG_SPREAD;
        } else if (posture == Posture.DOWNED) {
            if (arm) {
                v[3] = 0.0;
                v[4] = 0.0;
                v[5] = sideSign * (DOWNED_ARM + jitter[bone == PoseClip.RIGHT_ARM ? 0 : 1]);
            } else if (leg) {
                v[3] = 0.0;
                v[4] = 0.0;
                v[5] = sideSign * (DOWNED_LEG + jitter[2] * 0.5);
            } else if (bone == PoseClip.HEAD) {
                v[4] += jitter[3];
            }
        }
        int o = bone * CH;
        v[0] += delta[o];
        v[1] += delta[o + 1];
        v[2] += delta[o + 2];
        if (delta[o + 3] != 0.0 || delta[o + 4] != 0.0 || delta[o + 5] != 0.0) {
            // Parent frame: Euler add would turn a clip's lean with the gun hold's yaw (bladed torso leans sideways).
            Quaterniond q = new Quaterniond().rotationZYX(delta[o + 5], delta[o + 4], delta[o + 3])
                    .mul(new Quaterniond().rotationZYX(v[5], v[4], v[3]));
            Vector3d e = q.getEulerAnglesZYX(new Vector3d());
            v[3] = e.x;
            v[4] = e.y;
            v[5] = e.z;
        }
        if (roll != 0.0 && leg) {
            v[5] -= roll;
        }
        v[PoseClip.BEND_AXIS] += delta[o + PoseClip.BEND_AXIS];
        v[PoseClip.BEND] += delta[o + PoseClip.BEND];
    }

    /** Eye displacement in the rig local frame (x right, y up, z front) for camera eye height {@code eyeHeight}. */
    public Vec3 eyeOffsetLocal(double eyeHeight) {
        double drop = drop();
        double r = eyeHeight + drop - PIVOT_Y;
        return new Vec3(shift() - r * Math.sin(roll), PIVOT_Y - drop + r * Math.cos(roll) - eyeHeight, 0.0);
    }

    /** Body roll seen by the camera, rad (lean right => negative; PRONE side roll right => negative). */
    public double viewRoll() {
        return posture == Posture.PRONE ? roll - delta[PoseClip.BODY * CH + 4] : roll;
    }

    /** Rig local -> world direction at body yaw {@code yawDeg}. */
    public static Vec3 localToWorld(Vec3 local, float yawDeg) {
        double yaw = Math.toRadians(yawDeg);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        return new Vec3(-cos * local.x - sin * local.z, local.y, -sin * local.x + cos * local.z);
    }
}
