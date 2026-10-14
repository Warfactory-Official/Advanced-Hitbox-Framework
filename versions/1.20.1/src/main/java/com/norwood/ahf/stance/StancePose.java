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
import org.joml.Matrix3d;

/**
 * Stance geometry shared by the rig ({@link HumanoidRig}) and the PlayerAnimator layer: per-bone deltas
 * ({@link StanceClips}, PA channels) plus {@link #body} added to PlayerAnimator's {@code body} transform (pivot
 * {@link #PIVOT_Y}). Lying tilt ({@link #lie}) applied after, once, by {@code PlayerRendererTiltMixin} and
 * {@link HumanoidRig#applyVanillaTilt}.
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
    /** Vanilla {@code HumanoidModel} crouch minus upright, [bone * CHANNELS + ch], ModelPart units. */
    private static final double[] CROUCH = new double[PoseClip.BONES.length * CH];
    /** {@code PlayerRenderer.getRenderOffset} while crouching, blocks (x scale). */
    public static final double CROUCH_RENDER_OFFSET = 2.0 / 16.0;

    static {
        CROUCH[PoseClip.HEAD * CH + 1] = 4.2;
        CROUCH[PoseClip.TORSO * CH + 1] = 3.2;
        CROUCH[PoseClip.TORSO * CH + 3] = 0.5;
        for (int arm : new int[]{PoseClip.RIGHT_ARM, PoseClip.LEFT_ARM}) {
            CROUCH[arm * CH + 1] = 3.2;
            CROUCH[arm * CH + 3] = 0.4;
        }
        for (int leg : new int[]{PoseClip.RIGHT_LEG, PoseClip.LEFT_LEG}) {
            CROUCH[leg * CH + 1] = 0.2;
            CROUCH[leg * CH + 2] = 4.0;
        }
    }

    public final Posture posture;
    /** Body roll about the forward axis, radians; lean right => negative. */
    public final double roll;
    /** Prone roll turn about the long axis so far, rad (right => positive); 0 when not rolling. */
    public final double rollTurn;
    /** PRONE bones from the pose library (GunPoses lying clip) instead of the procedural legs. */
    public final boolean libraryLying;
    /** Lying tilt 0..1, eased + smoothstepped (replaces vanilla's swim tilt): {@link #lieOf}. */
    public double lie;
    /** Transition: pose left behind, weight of this pose over it (0..1); null = settled. */
    private @Nullable StancePose from;
    private double blend = 1.0;
    /** Crouch {@code from} stood in minus the layers' crouch now; see {@link #leaving}. */
    private double fromCrouch;
    private boolean crouchBones;
    private double scale = 1.0;
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
        double blend = s.blend(partialTick);
        double lie = smooth(s.lie(partialTick));
        if (eff == Posture.STAND && lean == 0.0 && height == 0.0 && side == 0.0 && blend >= 1.0 && lie == 0.0
                || StanceLogic.suspended(p)) {
            return null;
        }
        StancePose pose = new StancePose(eff, lean, height, side, s.rollDir(), s.rollProgress(partialTick), p.getId(),
                eff == Posture.PRONE && GunPoses.libraryLying(p));
        pose.lie = lie;
        pose.scale = p.getScale();
        if (blend < 1.0) {
            Posture from = s.from();
            // Own player: LocalPlayer.isCrouching = sneak key => no vanilla crouch in KNEEL to undo.
            double crouch = p.isLocalPlayer() ? 0.0
                    : (from == Posture.KNEEL ? 1.0 : 0.0) - (p.isCrouching() ? 1.0 : 0.0);
            pose.leaving(new StancePose(from, from.lying() ? 0.0 : lean, s.fromHeight(), s.fromSide(), 0, 0.0,
                    p.getId(), from == Posture.PRONE && GunPoses.hasLying(p)), smooth(blend), crouch,
                    GunPoses.select(p, Math.min(GunPoses.walkSpeed(p), 1.0F)) == null);
        }
        return pose;
    }

    /**
     * This pose reached from {@code from} by {@code blend} (0 = {@code from}, 1 = this); {@code fromCrouch} = vanilla
     * crouch {@code from} stood in minus the crouch the layers below carry now; {@code crouchBones} false => gun
     * clips own the bones (upper overwritten, lower faded by the consumer), render offset only.
     */
    StancePose leaving(StancePose from, double blend, double fromCrouch, boolean crouchBones) {
        this.from = from;
        this.blend = blend;
        this.fromCrouch = fromCrouch;
        this.crouchBones = crouchBones;
        return this;
    }

    /** {@link #lie} without building the pose; NaN => vanilla tilt (no stance, or upright + settled: vanilla crawl). */
    public static double lieOf(LivingEntity e, float partialTick) {
        StanceState s = e instanceof Player p && !StanceLogic.suspended(p) ? AhfStance.get(p) : null;
        if (s == null) {
            return Double.NaN;
        }
        float lie = s.lie(partialTick);
        return lie == 0.0F && !s.effective().lying() ? Double.NaN : smooth(lie);
    }

    static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
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
        double d = -delta[PoseClip.BODY * CH + 1];
        return from == null ? d : from.drop() + (d - from.drop()) * blend;
    }

    /** Side step, blocks, rig local x (right +). */
    public double shift() {
        double d = delta[PoseClip.BODY * CH];
        return from == null ? d : from.shift() + (d - from.shift()) * blend;
    }

    /** Adds this stance to a PA {@code body} transform {@code x, y, z, pitch, yaw, roll, bend axis, bend} (PA frame). */
    public void body(double[] w) {
        if (from == null) {
            ownBody(w);
            return;
        }
        double[] b = w.clone();
        from.ownBody(b);
        b[1] -= fromCrouch * CROUCH_RENDER_OFFSET * scale;
        ownBody(w);
        mix(b, w, blend);
    }

    private void ownBody(double[] w) {
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
        if (from == null) {
            own(bone, v);
            return;
        }
        double[] b = v.clone();
        if (crouchBones) {
            for (int ch = 0; ch < CH; ch++) {
                b[ch] += fromCrouch * CROUCH[bone * CH + ch];
            }
        }
        from.own(bone, b);
        own(bone, v);
        mix(b, v, blend);
    }

    /** {@code to = from + (to - from) * t}: positions + bend linear, ZYX rotation slerped. */
    private static void mix(double[] from, double[] to, double t) {
        for (int ch : new int[]{0, 1, 2, PoseClip.BEND_AXIS, PoseClip.BEND}) {
            to[ch] = from[ch] + (to[ch] - from[ch]) * t;
        }
        eulerZYX(new Quaterniond().rotationZYX(from[5], from[4], from[3])
                .slerp(new Quaterniond().rotationZYX(to[5], to[4], to[3]), t), to);
    }

    /**
     * {@code v[3..5] = (x, y, z)} with {@code q = Rz Ry Rx}. JOML 1.10.5 {@code getEulerAnglesZYX} FORBIDDEN: x off
     * (0.1 -> 0.0966 round trip).
     */
    static void eulerZYX(Quaterniond q, double[] v) {
        Matrix3d m = new Matrix3d().set(q);
        v[3] = Math.atan2(m.m12, m.m22);
        v[4] = Math.asin(Mth.clamp(-m.m02, -1.0, 1.0));
        v[5] = Math.atan2(m.m01, m.m00);
    }

    private void own(int bone, double[] v) {
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
            eulerZYX(new Quaterniond().rotationZYX(delta[o + 5], delta[o + 4], delta[o + 3])
                    .mul(new Quaterniond().rotationZYX(v[5], v[4], v[3])), v);
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
        double r = posture == Posture.PRONE ? roll - delta[PoseClip.BODY * CH + 4] : roll;
        return from == null ? r : from.viewRoll() + (r - from.viewRoll()) * blend;
    }

    /** Lying tilt at {@code lie} ({@link #lieOf}): about rig x, radians (vanilla swim tilt sign). */
    public static double tiltAngle(double lie) {
        return lie * Math.PI / 2.0;
    }

    /**
     * Lying tilt shift, rig frame {@code (y, z)} blocks: vanilla's flat end {@code (0.3, -1)} reached linearly, pivot
     * at the feet (vanilla's rotated-frame shift sinks the body 1 block at the start of the tilt).
     */
    public static double tiltShiftY(double lie) {
        return 0.3 * lie;
    }

    public static double tiltShiftZ(double lie) {
        return -lie;
    }

    /** Rig local -> world direction at body yaw {@code yawDeg}. */
    public static Vec3 localToWorld(Vec3 local, float yawDeg) {
        double yaw = Math.toRadians(yawDeg);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        return new Vec3(-cos * local.x - sin * local.z, local.y, -sin * local.x + cos * local.z);
    }
}
