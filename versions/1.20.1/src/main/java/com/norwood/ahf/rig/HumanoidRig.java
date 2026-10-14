package com.norwood.ahf.rig;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.pose.GunPoses;
import com.norwood.ahf.pose.PoseClip;
import com.norwood.ahf.pose.PoseLibrary;
import com.norwood.ahf.stance.AhfStances;
import com.norwood.ahf.stance.Stance;
import com.norwood.ahf.stance.StancePose;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector3d;

public final class HumanoidRig {

    private static final double UNIT = 1.0 / 16.0;
    private static final double MODEL_SCALE = 0.9375;
    private static final double Y_SHIFT = 1.501;

    private static final double DEG2RAD = Math.PI / 180.0;
    private static final double LIMB_FREQ = 0.6662;

    private static final double[][] BASE = base();

    private static double[][] base() {
        double[][] b = new double[HitboxPart.VALUES.length][];
        b[HitboxPart.HEAD.ordinal()] = new double[]{-4, -8, -4, 8, 8, 8, 0, 0, 0};
        b[HitboxPart.TORSO.ordinal()] = new double[]{-4, 0, -2, 8, 12, 4, 0, 0, 0};
        b[HitboxPart.LEFT_ARM.ordinal()] = new double[]{-1, -2, -2, 4, 12, 4, 5, 2, 0};
        b[HitboxPart.RIGHT_ARM.ordinal()] = new double[]{-3, -2, -2, 4, 12, 4, -5, 2, 0};
        b[HitboxPart.LEFT_LEG.ordinal()] = new double[]{-2, 0, -2, 4, 12, 4, 1.9, 12, 0};
        b[HitboxPart.RIGHT_LEG.ordinal()] = new double[]{-2, 0, -2, 4, 12, 4, -1.9, 12, 0};
        return b;
    }

    private static final double[][][] POSE_ADJUST = poseAdjust();

    private static double[][][] poseAdjust() {
        return new double[RigTuning.RigPose.VALUES.length][HitboxPart.VALUES.length][RigTuning.FIELDS];
    }

    private static final double[][][][] HAND_ADJUST = handAdjust();

    private static double[][][][] handAdjust() {
        double[][][][] a = new double[RigTuning.RigPose.VALUES.length][RigTuning.HandAction.VALUES.length]
                [HitboxPart.VALUES.length][RigTuning.FIELDS];
        return a;
    }

    private static final RigSpec DEFAULT_SPEC = new RigSpec(BASE, POSE_ADJUST, HAND_ADJUST);
    private static volatile RigSpec spec = DEFAULT_SPEC;

    public static RigSpec defaultSpec() {
        return DEFAULT_SPEC.copy();
    }

    public static void setSpec(RigSpec next) {
        spec = (next == null) ? DEFAULT_SPEC : next;
    }

    /**
     * Overrides a posed bone from an external animation system. Input and output use {@code ModelPart}
     * conventions: pivot in model units, Euler radians, PA bend {@code (axis, angle)} rad. Returns
     * {@link PoseClip#CHANNELS} values, or null to keep the vanilla pose. Bones: {@code head, torso, rightArm, leftArm,
     * rightLeg, leftLeg}.
     */
    @FunctionalInterface
    public interface AnimSampler {
        double[] sample(LivingEntity entity, String bone, double[] in);

        /**
         * PlayerAnimator {@code body} (whole-player) transform into {@code out} ({@link PoseClip#CHANNELS}: x, y, z,
         * pitch, yaw, roll, waist bend), blocks / rad, starting from zero; false => none.
         */
        default boolean body(LivingEntity entity, double[] out) {
            return false;
        }
    }

    private static volatile AnimSampler animSampler;

    public static void setAnimSampler(AnimSampler sampler) {
        animSampler = sampler;
    }

    public static double[] baseSpec(HitboxPart limb) {
        return spec.base[limb.ordinal()].clone();
    }

    public static double[] poseAdjustSpec(RigTuning.RigPose pose, HitboxPart limb) {
        return spec.poseAdjust[pose.ordinal()][limb.ordinal()].clone();
    }

    public static double[] handAdjustSpec(RigTuning.RigPose pose, RigTuning.HandAction hand, HitboxPart limb) {
        return spec.handAdjust[pose.ordinal()][hand.ordinal()][limb.ordinal()].clone();
    }

    public static RigTuning.HandAction resolveHandAction(LivingEntity victim) {
        RigTuning.HandAction main = handAction(getArmPose(victim, InteractionHand.MAIN_HAND));
        if (main != RigTuning.HandAction.NONE) {
            return main;
        }
        return handAction(getArmPose(victim, InteractionHand.OFF_HAND));
    }

    private static RigTuning.HandAction handAction(ArmPose pose) {
        return switch (pose) {
            case GUN -> RigTuning.HandAction.GUN;
            case BOW, SPEAR, CROSSBOW_CHARGE, CROSSBOW_HOLD -> RigTuning.HandAction.BOW;
            case BLOCK -> RigTuning.HandAction.BLOCK;
            default -> RigTuning.HandAction.NONE;
        };
    }

    public static RigTuning.RigPose resolvePose(LivingEntity victim) {
        if (victim.isFallFlying() || victim.isVisuallySwimming() || victim.getSwimAmount(1.0F) > 0.0F) {
            return RigTuning.RigPose.PRONE;
        }
        if (victim.isCrouching()) {
            return RigTuning.RigPose.CROUCHING;
        }
        return RigTuning.RigPose.STANDING;
    }

    private static Part part(HitboxPart limb, RigTuning.RigPose pose, RigTuning.HandAction hand) {
        int li = limb.ordinal();
        RigSpec sp = spec;
        double[] s = sp.base[li];
        double[] adj = sp.poseAdjust[pose.ordinal()][li];
        double ox = s[0] + adj[0];
        double oy = s[1] + adj[1];
        double oz = s[2] + adj[2];
        double sx = s[3] + adj[3];
        double sy = s[4] + adj[4];
        double sz = s[5] + adj[5];
        double px = s[6] + adj[6];
        double py = s[7] + adj[7];
        double pz = s[8] + adj[8];
        boolean armOverlay = limb.isArm() && hand != RigTuning.HandAction.NONE;
        if (armOverlay) {
            double[] h = sp.handAdjust[pose.ordinal()][hand.ordinal()][li];
            ox += h[0];
            oy += h[1];
            oz += h[2];
            sx += h[3];
            sy += h[4];
            sz += h[5];
            px += h[6];
            py += h[7];
            pz += h[8];
        }
        if (RigTuning.ACTIVE) {
            double[] d = RigTuning.deltas();
            int b = RigTuning.base(pose, limb);
            ox += d[b];
            oy += d[b + 1];
            oz += d[b + 2];
            sx += d[b + 3];
            sy += d[b + 4];
            sz += d[b + 5];
            px += d[b + 6];
            py += d[b + 7];
            pz += d[b + 8];
            if (armOverlay) {
                double[] hd = RigTuning.handDeltas();
                int hb = RigTuning.handBase(pose, hand, limb);
                ox += hd[hb];
                oy += hd[hb + 1];
                oz += hd[hb + 2];
                sx += hd[hb + 3];
                sy += hd[hb + 4];
                sz += hd[hb + 5];
                px += hd[hb + 6];
                py += hd[hb + 7];
                pz += hd[hb + 8];
            }
        }
        return new Part(ox, oy, oz, sx, sy, sz, px, py, pz, limb);
    }

    private HumanoidRig() {
    }

    /** Client entities: the render's animation (sampler); server entities: the pose library. */
    public static LocalRig compute(LivingEntity victim) {
        return compute(victim, victim.level().isClientSide ? animSampler : null);
    }

    /** The rig as a dedicated server computes it (pose library, no client animation sampler). */
    public static LocalRig computeServer(LivingEntity victim) {
        return compute(victim, null);
    }

    private static LocalRig compute(LivingEntity victim, AnimSampler sampler) {
        RigTuning.RigPose pose = resolvePose(victim);
        RigTuning.HandAction hand = resolveHandAction(victim);

        Part body = part(HitboxPart.TORSO, pose, hand);
        Part rightLeg = part(HitboxPart.RIGHT_LEG, pose, hand);
        Part leftLeg = part(HitboxPart.LEFT_LEG, pose, hand);
        Part head = part(HitboxPart.HEAD, pose, hand);
        Part rightArm = part(HitboxPart.RIGHT_ARM, pose, hand);
        Part leftArm = part(HitboxPart.LEFT_ARM, pose, hand);

        setupAnim(victim, head, body, rightArm, leftArm, rightLeg, leftLeg);
        // PlayerAnimator body transform, PA frame: x, y, z (blocks), pitch, yaw, roll.
        double[] whole = new double[PoseClip.CHANNELS];
        if (sampler != null) {
            overrideBone(sampler, victim, "head", head);
            overrideBone(sampler, victim, "torso", body);
            overrideBone(sampler, victim, "rightArm", rightArm);
            overrideBone(sampler, victim, "leftArm", leftArm);
            overrideBone(sampler, victim, "rightLeg", rightLeg);
            overrideBone(sampler, victim, "leftLeg", leftLeg);
            sampler.body(victim, whole);
        } else {
            libraryPose(victim, new Part[]{head, body, rightArm, leftArm, rightLeg, leftLeg}, whole);
        }
        StancePose stancePose = StancePose.of(victim, 1.0F);
        if (stancePose != null) {
            double[] v = new double[PoseClip.CHANNELS];
            stanceBone(stancePose, PoseClip.HEAD, head, v);
            stanceBone(stancePose, PoseClip.TORSO, body, v);
            stanceBone(stancePose, PoseClip.RIGHT_ARM, rightArm, v);
            stanceBone(stancePose, PoseClip.LEFT_ARM, leftArm, v);
            stanceBone(stancePose, PoseClip.RIGHT_LEG, rightLeg, v);
            stanceBone(stancePose, PoseClip.LEFT_LEG, leftLeg, v);
            stancePose.body(whole);
        }

        double pad = AhfConfig.limbBoxPadding();
        LocalRig rig = new LocalRig();
        // Waist bend = PA body + torso bends; upper torso, head and arms turn with it (render: BodyRenderer).
        body.bendAxis += whole[PoseClip.BEND_AXIS];
        body.bend += whole[PoseClip.BEND];
        Matrix4d torso = body.matrix();
        Matrix4d chest = bent(body, torso);
        Matrix4d upper = new Matrix4d(torso).invertAffine().mulLocal(chest);
        rig.torso = toObb(body, torso, true, pad);
        rig.chest = toObb(body, chest, false, pad);
        rig.head = toObb(head, head.matrix().mulLocal(upper), null, pad);
        limb(rightArm, upper, pad, rig, LocalRig.Slot.RIGHT_ARM, LocalRig.Slot.RIGHT_FOREARM);
        limb(leftArm, upper, pad, rig, LocalRig.Slot.LEFT_ARM, LocalRig.Slot.LEFT_FOREARM);
        limb(rightLeg, null, pad, rig, LocalRig.Slot.RIGHT_LEG, LocalRig.Slot.RIGHT_SHIN);
        limb(leftLeg, null, pad, rig, LocalRig.Slot.LEFT_LEG, LocalRig.Slot.LEFT_SHIN);
        applyWhole(rig, whole);

        Stance s = AhfStances.active(victim);
        if (s != null) {
            s.apply(victim, rig);
        } else {
            applyVanillaTilt(victim, rig);
        }
        if (victim instanceof Player && victim.isCrouching()) {
            lower(rig, StancePose.CROUCH_RENDER_OFFSET * victim.getScale());
        }
        return rig;
    }

    private static void lower(LocalRig rig, double dy) {
        for (LocalRig.Slot slot : LocalRig.SLOTS) {
            Obb o = slot.get(rig);
            slot.set(rig, new Obb(o.center().subtract(0.0, dy, 0.0), o.axisX(), o.axisY(), o.axisZ(), o.half(),
                    o.limb()));
        }
    }

    /**
     * Server stand-in for the render's gun layers, layer order lower -> upper -> {@code empty} -> one-shot, then the
     * rotation layer. Strides by synced walk position ({@link GunPoses#phased}); other loops at rest frame. Bones = PA
     * order {@code head, torso, rightArm, leftArm, rightLeg, leftLeg}.
     */
    private static void libraryPose(LivingEntity e, Part[] parts, double[] whole) {
        GunPoses.Selection sel = GunPoses.select(e, Math.min(GunPoses.walkSpeed(e), 1.0F));
        if (sel == null) {
            return;
        }
        double[][] v = new double[parts.length][];
        for (int b = 0; b < parts.length; b++) {
            v[b] = parts[b].channels(new double[PoseClip.CHANNELS]);
        }
        double walk = GunPoses.walkPos(e);
        layer(sel.set(), sel.lower(), v, whole, walk);
        layer(sel.set(), sel.upper(), v, whole, walk);
        layer(sel.set(), GunPoses.EMPTY, v, whole, walk);
        GunPoses.applyOnce(e, v, whole);
        double lie = GunPoses.lie(e, 1.0F);
        for (int b = 0; b < parts.length; b++) {
            GunPoses.adjust(e, 1.0F, lie, b, v[b]);
            parts[b].set(v[b]);
        }
        GunPoses.adjust(e, 1.0F, lie, PoseClip.BODY, whole);
    }

    private static void layer(ResourceLocation set, @Nullable String name, double[][] v, double[] whole, double walk) {
        PoseClip clip = name == null ? null : PoseLibrary.clip(set, name);
        if (clip == null) {
            return;
        }
        boolean phased = GunPoses.phased(name);
        double t = phased ? GunPoses.phaseTicks(clip, walk) : 0;
        for (int b = 0; b < v.length; b++) {
            if (!clip.animates(b)) {
                continue;
            }
            if (phased) {
                clip.applyAt(b, t, v[b]);
            } else {
                clip.applyRest(b, v[b]);
            }
        }
        if (phased) {
            clip.applyAt(PoseClip.BODY, t, whole);
        } else {
            clip.applyRest(PoseClip.BODY, whole);
        }
    }

    /**
     * PA {@code body} on rig OBBs: {@code p' = T + C + Rz Ry Rx (p - C)}, {@code C = (0, PIVOT_Y, 0)}, in PA's frame
     * (z = -rig z). Inside the vanilla tilt (PA applies it at {@code setupRotations} RETURN).
     */
    public static void applyWhole(LocalRig rig, double[] w) {
        if (w[0] == 0.0 && w[1] == 0.0 && w[2] == 0.0 && w[3] == 0.0 && w[4] == 0.0 && w[5] == 0.0) {
            return;
        }
        double cx = Math.cos(w[3]), sx = Math.sin(w[3]);
        double cy = Math.cos(w[4]), sy = Math.sin(w[4]);
        double cz = Math.cos(w[5]), sz = Math.sin(w[5]);
        for (LocalRig.Slot slot : LocalRig.SLOTS) {
            Obb o = slot.get(rig);
            Vec3 c = o.center();
            Vec3 q = whole(c.x, c.y - StancePose.PIVOT_Y, -c.z, cx, sx, cy, sy, cz, sz);
            Vec3 centre = new Vec3(w[0] + q.x, w[1] + StancePose.PIVOT_Y + q.y, -(w[2] + q.z));
            slot.set(rig, new Obb(centre, wholeAxis(o.axisX(), cx, sx, cy, sy, cz, sz),
                    wholeAxis(o.axisY(), cx, sx, cy, sy, cz, sz), wholeAxis(o.axisZ(), cx, sx, cy, sy, cz, sz),
                    o.half(), o.limb()));
        }
    }

    private static Vec3 wholeAxis(Vec3 a, double cx, double sx, double cy, double sy, double cz, double sz) {
        Vec3 q = whole(a.x, a.y, -a.z, cx, sx, cy, sy, cz, sz);
        return new Vec3(q.x, q.y, -q.z);
    }

    /** {@code Rz Ry Rx} on a PA-frame vector. */
    private static Vec3 whole(double x, double y, double z, double cx, double sx, double cy, double sy,
                              double cz, double sz) {
        double y1 = y * cx - z * sx;
        double z1 = y * sx + z * cx;
        double x2 = x * cy + z1 * sy;
        double z2 = -x * sy + z1 * cy;
        return new Vec3(x2 * cz - y1 * sz, x2 * sz + y1 * cz, z2);
    }

    private static void overrideBone(AnimSampler sampler, LivingEntity e, String bone, Part p) {
        double[] r = sampler.sample(e, bone, p.channels(new double[PoseClip.CHANNELS]));
        if (r != null) {
            p.set(r);
        }
    }

    private static void stanceBone(StancePose pose, int bone, Part p, double[] v) {
        pose.apply(bone, p.channels(v));
        p.set(v);
    }

    /**
     * Swim/elytra render tilt (stance-posed players: {@link StancePose#lie}, as {@code PlayerRendererTiltMixin}); a
     * {@link Stance} replacing it calls this to keep it counted once.
     */
    public static void applyVanillaTilt(LivingEntity e, LocalRig rig) {
        double angle;
        double shiftY = 0.0;
        double shiftZ = 0.0;
        float swimAmount = e.getSwimAmount(1.0F);
        double lie = e.isFallFlying() ? Double.NaN : StancePose.lieOf(e, 1.0F);
        if (!Double.isNaN(lie)) {
            angle = StancePose.tiltAngle(lie);
            shiftY = StancePose.tiltShiftY(lie);
            shiftZ = StancePose.tiltShiftZ(lie);
        } else if (e.isFallFlying()) {
            if (e.isAutoSpinAttack()) {
                return;
            }
            double ticks = e.getFallFlyingTicks();
            double t = Mth.clamp(ticks * ticks / 100.0, 0.0, 1.0);
            double tiltDeg = t * (-90.0 - e.getXRot());
            angle = -tiltDeg * DEG2RAD;
        } else if (swimAmount > 0.0F) {
            double f2 = e.isInWater() ? (-90.0 - e.getXRot()) : -90.0;
            double f3 = swimAmount * f2;
            angle = -f3 * DEG2RAD;
            if (e.isVisuallySwimming()) {
                double phi = f3 * DEG2RAD;
                double c = Math.cos(phi);
                double s = Math.sin(phi);
                shiftY = -c - 0.3 * s;
                shiftZ = s - 0.3 * c;
            }
        } else {
            return;
        }
        if (angle == 0.0 && shiftY == 0.0 && shiftZ == 0.0) {
            return;
        }
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        for (LocalRig.Slot slot : LocalRig.SLOTS) {
            slot.set(rig, tilt(slot.get(rig), cos, sin, shiftY, shiftZ));
        }
    }

    private static Obb tilt(Obb o, double cos, double sin, double shiftY, double shiftZ) {
        Vec3 c = rotX(o.center(), cos, sin);
        Vec3 centre = new Vec3(c.x, c.y + shiftY, c.z + shiftZ);
        return new Obb(centre, rotX(o.axisX(), cos, sin), rotX(o.axisY(), cos, sin), rotX(o.axisZ(), cos, sin),
                o.half(), o.limb());
    }

    private static Vec3 rotX(Vec3 v, double cos, double sin) {
        return new Vec3(v.x, v.y * cos - v.z * sin, v.y * sin + v.z * cos);
    }

    private static void setupAnim(LivingEntity e, Part head, Part body, Part rightArm, Part leftArm,
                                  Part rightLeg, Part leftLeg) {
        double limbSwing = GunPoses.walkPos(e);
        double limbSwingAmount = Math.min(GunPoses.walkSpeed(e), 1.0);
        double ageInTicks = e.tickCount;
        double netHeadYaw = Mth.wrapDegrees(e.getYHeadRot() - e.yBodyRot);
        double headPitch = e.getXRot();
        float swimAmount = e.getSwimAmount(1.0F);
        boolean fallFly = e.getFallFlyingTicks() > 4;
        boolean visualSwim = e.isVisuallySwimming();
        boolean crouching = e.isCrouching();
        boolean riding = e.isPassenger() && e.getVehicle() != null && e.getVehicle().shouldRiderSit();

        head.yRot = netHeadYaw * DEG2RAD;
        if (fallFly) {
            head.xRot = -Math.PI / 4.0;
        } else if (swimAmount > 0.0F) {
            if (visualSwim) {
                head.xRot = rotlerpRad(swimAmount, head.xRot, -Math.PI / 4.0);
            } else {
                head.xRot = rotlerpRad(swimAmount, head.xRot, headPitch * DEG2RAD);
            }
        } else {
            head.xRot = headPitch * DEG2RAD;
        }

        body.yRot = 0.0;
        rightArm.z = 0.0;
        rightArm.x = -5.0;
        leftArm.z = 0.0;
        leftArm.x = 5.0;

        double f = 1.0;
        if (fallFly) {
            f = e.getDeltaMovement().lengthSqr();
            f /= 0.2;
            f *= f * f;
        }
        if (f < 1.0) {
            f = 1.0;
        }

        rightArm.xRot = Math.cos(limbSwing * LIMB_FREQ + Math.PI) * 2.0 * limbSwingAmount * 0.5 / f;
        leftArm.xRot = Math.cos(limbSwing * LIMB_FREQ) * 2.0 * limbSwingAmount * 0.5 / f;
        rightArm.zRot = 0.0;
        leftArm.zRot = 0.0;
        rightLeg.xRot = Math.cos(limbSwing * LIMB_FREQ) * 1.4 * limbSwingAmount / f;
        leftLeg.xRot = Math.cos(limbSwing * LIMB_FREQ + Math.PI) * 1.4 * limbSwingAmount / f;
        rightLeg.yRot = 0.005;
        leftLeg.yRot = -0.005;
        rightLeg.zRot = 0.005;
        leftLeg.zRot = -0.005;

        if (riding) {
            rightArm.xRot += -Math.PI / 5.0;
            leftArm.xRot += -Math.PI / 5.0;
            rightLeg.xRot = -1.4137167;
            rightLeg.yRot = Math.PI / 10.0;
            rightLeg.zRot = 0.07853982;
            leftLeg.xRot = -1.4137167;
            leftLeg.yRot = -Math.PI / 10.0;
            leftLeg.zRot = -0.07853982;
        }

        rightArm.yRot = 0.0;
        leftArm.yRot = 0.0;

        ArmPose mainPose = getArmPose(e, InteractionHand.MAIN_HAND);
        ArmPose offPose = getArmPose(e, InteractionHand.OFF_HAND);
        if (mainPose.twoHanded) {
            offPose = e.getOffhandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
        }
        HumanoidArm mainArm = e.getMainArm();
        ArmPose rightArmPose = mainArm == HumanoidArm.RIGHT ? mainPose : offPose;
        ArmPose leftArmPose = mainArm == HumanoidArm.RIGHT ? offPose : mainPose;
        boolean holdingGun = mainPose == ArmPose.GUN;

        boolean flag2 = mainArm == HumanoidArm.RIGHT;
        if (e.isUsingItem()) {
            boolean flag3 = e.getUsedItemHand() == InteractionHand.MAIN_HAND;
            if (flag3 == flag2) {
                poseRightArm(e, rightArmPose, head, rightArm, leftArm, crouching);
            } else {
                poseLeftArm(e, leftArmPose, head, rightArm, leftArm, crouching);
            }
        } else {
            boolean flag4 = flag2 ? leftArmPose.twoHanded : rightArmPose.twoHanded;
            if (flag2 != flag4) {
                poseLeftArm(e, leftArmPose, head, rightArm, leftArm, crouching);
                poseRightArm(e, rightArmPose, head, rightArm, leftArm, crouching);
            } else {
                poseRightArm(e, rightArmPose, head, rightArm, leftArm, crouching);
                poseLeftArm(e, leftArmPose, head, rightArm, leftArm, crouching);
            }
        }

        setupAttackAnimation(e, head, body, rightArm, leftArm);

        if (crouching) {
            body.xRot = 0.5;
            rightArm.xRot += 0.4;
            leftArm.xRot += 0.4;
            rightLeg.z += 4.0;
            leftLeg.z += 4.0;
            rightLeg.y += 0.2;
            leftLeg.y += 0.2;
            head.y += 4.2;
            body.y += 3.2;
            leftArm.y += 3.2;
            rightArm.y += 3.2;
        } else {
            body.xRot = 0.0;
        }

        if (rightArmPose != ArmPose.SPYGLASS) {
            bobModelPart(rightArm, ageInTicks, 1.0);
        }
        if (leftArmPose != ArmPose.SPYGLASS) {
            bobModelPart(leftArm, ageInTicks, -1.0);
        }

        if (swimAmount > 0.0F) {
            swimAnim(e, head, rightArm, leftArm, rightLeg, leftLeg, limbSwing, swimAmount, holdingGun);
        }
    }

    private static void poseRightArm(LivingEntity e, ArmPose pose, Part head, Part rightArm, Part leftArm,
                                     boolean crouching) {
        switch (pose) {
            case EMPTY -> rightArm.yRot = 0.0;
            case BLOCK -> {
                rightArm.xRot = rightArm.xRot * 0.5 - 0.9424779;
                rightArm.yRot = -Math.PI / 6.0;
            }
            case ITEM -> {
                rightArm.xRot = rightArm.xRot * 0.5 - Math.PI / 10.0;
                rightArm.yRot = 0.0;
            }
            case SPEAR -> {
                rightArm.xRot = rightArm.xRot * 0.5 - Math.PI;
                rightArm.yRot = 0.0;
            }
            case BOW -> bowPose(head, rightArm, leftArm, true);
            case GUN -> gunPose(e, head, rightArm, leftArm);
            case CROSSBOW_CHARGE -> animateCrossbowCharge(rightArm, leftArm, e, true);
            case CROSSBOW_HOLD -> animateCrossbowHold(rightArm, leftArm, head, true);
            case BRUSH -> {
                rightArm.xRot = rightArm.xRot * 0.5 - Math.PI / 5.0;
                rightArm.yRot = 0.0;
            }
            case SPYGLASS -> {
                rightArm.xRot = Mth.clamp(head.xRot - 1.9198622 - (crouching ? 0.2617994 : 0.0), -2.4, 3.3);
                rightArm.yRot = head.yRot - 0.2617994;
            }
            case TOOT_HORN -> {
                rightArm.xRot = Mth.clamp(head.xRot, -1.2, 1.2) - 1.4835298;
                rightArm.yRot = head.yRot - Math.PI / 6.0;
            }
        }
    }

    private static void poseLeftArm(LivingEntity e, ArmPose pose, Part head, Part rightArm, Part leftArm,
                                    boolean crouching) {
        switch (pose) {
            case EMPTY -> leftArm.yRot = 0.0;
            case BLOCK -> {
                leftArm.xRot = leftArm.xRot * 0.5 - 0.9424779;
                leftArm.yRot = Math.PI / 6.0;
            }
            case ITEM -> {
                leftArm.xRot = leftArm.xRot * 0.5 - Math.PI / 10.0;
                leftArm.yRot = 0.0;
            }
            case SPEAR -> {
                leftArm.xRot = leftArm.xRot * 0.5 - Math.PI;
                leftArm.yRot = 0.0;
            }
            case BOW -> bowPose(head, rightArm, leftArm, false);
            case GUN -> gunPose(e, head, rightArm, leftArm);
            case CROSSBOW_CHARGE -> animateCrossbowCharge(rightArm, leftArm, e, false);
            case CROSSBOW_HOLD -> animateCrossbowHold(rightArm, leftArm, head, false);
            case BRUSH -> {
                leftArm.xRot = leftArm.xRot * 0.5 - Math.PI / 5.0;
                leftArm.yRot = 0.0;
            }
            case SPYGLASS -> {
                leftArm.xRot = Mth.clamp(head.xRot - 1.9198622 - (crouching ? 0.2617994 : 0.0), -2.4, 3.3);
                leftArm.yRot = head.yRot + 0.2617994;
            }
            case TOOT_HORN -> {
                leftArm.xRot = Mth.clamp(head.xRot, -1.2, 1.2) - 1.4835298;
                leftArm.yRot = head.yRot + Math.PI / 6.0;
            }
        }
    }

    private static void bowPose(Part head, Part rightArm, Part leftArm, boolean mainRight) {
        if (mainRight) {
            rightArm.yRot = -0.1 + head.yRot;
            leftArm.yRot = 0.1 + head.yRot + 0.4;
        } else {
            rightArm.yRot = -0.1 + head.yRot - 0.4;
            leftArm.yRot = 0.1 + head.yRot;
        }
        rightArm.xRot = -Math.PI / 2.0 + head.xRot;
        leftArm.xRot = -Math.PI / 2.0 + head.xRot;
    }

    private static void gunPose(LivingEntity e, Part head, Part rightArm, Part leftArm) {
        if (!AhfConfig.gunArmPose()) {
            bowPose(head, rightArm, leftArm, true);
            return;
        }
        GunArmPose.Pose p = GunArmPose.resolve(AhfHooks.gunAimProgress(e));
        rightArm.xRot = p.rightX() + head.xRot;
        rightArm.yRot = p.rightY() + head.yRot;
        rightArm.zRot = p.rightZ();
        leftArm.xRot = p.leftX() + head.xRot;
        leftArm.yRot = p.leftY() + head.yRot;
        leftArm.zRot = p.leftZ();
    }

    private static void animateCrossbowHold(Part p1, Part p2, Part head, boolean right) {
        Part a = right ? p1 : p2;
        Part b = right ? p2 : p1;
        a.yRot = (right ? -0.3 : 0.3) + head.yRot;
        b.yRot = (right ? 0.6 : -0.6) + head.yRot;
        a.xRot = -Math.PI / 2.0 + head.xRot + 0.1;
        b.xRot = -1.5 + head.xRot;
    }

    private static void animateCrossbowCharge(Part p1, Part p2, LivingEntity e, boolean right) {
        Part a = right ? p1 : p2;
        Part b = right ? p2 : p1;
        a.yRot = right ? -0.8 : 0.8;
        a.xRot = -0.97079635;
        b.xRot = a.xRot;
        double f = CrossbowItem.getChargeDuration(e.getUseItem());
        double f1 = f <= 0.0 ? 0.0 : Mth.clamp((float) e.getTicksUsingItem(), 0.0F, (float) f);
        double f2 = f <= 0.0 ? 0.0 : f1 / f;
        b.yRot = Mth.lerp(f2, 0.4, 0.85) * (right ? 1 : -1);
        b.xRot = Mth.lerp(f2, b.xRot, -Math.PI / 2.0);
    }

    private static void setupAttackAnimation(LivingEntity e, Part head, Part body, Part rightArm, Part leftArm) {
        double attackTime = e.getAttackAnim(1.0F);
        if (attackTime <= 0.0) {
            return;
        }
        HumanoidArm arm = getAttackArm(e);
        Part modelpart = arm == HumanoidArm.RIGHT ? rightArm : leftArm;
        double f = attackTime;
        body.yRot = Math.sin(Math.sqrt(f) * (Math.PI * 2.0)) * 0.2;
        if (arm == HumanoidArm.LEFT) {
            body.yRot *= -1.0;
        }
        rightArm.z = Math.sin(body.yRot) * 5.0;
        rightArm.x = -Math.cos(body.yRot) * 5.0;
        leftArm.z = -Math.sin(body.yRot) * 5.0;
        leftArm.x = Math.cos(body.yRot) * 5.0;
        rightArm.yRot += body.yRot;
        leftArm.yRot += body.yRot;
        leftArm.xRot += body.yRot;
        f = 1.0 - attackTime;
        f *= f;
        f *= f;
        f = 1.0 - f;
        double f1 = Math.sin(f * Math.PI);
        double f2 = Math.sin(attackTime * Math.PI) * -(head.xRot - 0.7) * 0.75;
        modelpart.xRot -= f1 * 1.2 + f2;
        modelpart.yRot += body.yRot * 2.0;
        modelpart.zRot += Math.sin(attackTime * Math.PI) * -0.4;
    }

    private static void swimAnim(LivingEntity e, Part head, Part rightArm, Part leftArm, Part rightLeg,
                                 Part leftLeg, double limbSwing, float swimAmount, boolean holdingGun) {
        double f5 = limbSwing % 26.0;
        HumanoidArm attackArm = getAttackArm(e);
        double attackTime = e.getAttackAnim(1.0F);
        double f1 = attackArm == HumanoidArm.RIGHT && attackTime > 0.0F ? 0.0 : swimAmount;
        double f2 = attackArm == HumanoidArm.LEFT && attackTime > 0.0F ? 0.0 : swimAmount;
        if (!e.isUsingItem() && !holdingGun) {
            if (f5 < 14.0) {
                leftArm.xRot = rotlerpRad(f2, leftArm.xRot, 0.0);
                rightArm.xRot = Mth.lerp(f1, rightArm.xRot, 0.0);
                leftArm.yRot = rotlerpRad(f2, leftArm.yRot, Math.PI);
                rightArm.yRot = Mth.lerp(f1, rightArm.yRot, Math.PI);
                leftArm.zRot = rotlerpRad(f2, leftArm.zRot,
                        Math.PI + 1.8707964 * quadraticArmUpdate(f5) / quadraticArmUpdate(14.0));
                rightArm.zRot = Mth.lerp(f1, rightArm.zRot,
                        Math.PI - 1.8707964 * quadraticArmUpdate(f5) / quadraticArmUpdate(14.0));
            } else if (f5 < 22.0) {
                double f6 = (f5 - 14.0) / 8.0;
                leftArm.xRot = rotlerpRad(f2, leftArm.xRot, (Math.PI / 2.0) * f6);
                rightArm.xRot = Mth.lerp(f1, rightArm.xRot, (Math.PI / 2.0) * f6);
                leftArm.yRot = rotlerpRad(f2, leftArm.yRot, Math.PI);
                rightArm.yRot = Mth.lerp(f1, rightArm.yRot, Math.PI);
                leftArm.zRot = rotlerpRad(f2, leftArm.zRot, 5.012389 - 1.8707964 * f6);
                rightArm.zRot = Mth.lerp(f1, rightArm.zRot, 1.2707963 + 1.8707964 * f6);
            } else if (f5 < 26.0) {
                double f3 = (f5 - 22.0) / 4.0;
                leftArm.xRot = rotlerpRad(f2, leftArm.xRot, (Math.PI / 2.0) - (Math.PI / 2.0) * f3);
                rightArm.xRot = Mth.lerp(f1, rightArm.xRot, (Math.PI / 2.0) - (Math.PI / 2.0) * f3);
                leftArm.yRot = rotlerpRad(f2, leftArm.yRot, Math.PI);
                rightArm.yRot = Mth.lerp(f1, rightArm.yRot, Math.PI);
                leftArm.zRot = rotlerpRad(f2, leftArm.zRot, Math.PI);
                rightArm.zRot = Mth.lerp(f1, rightArm.zRot, Math.PI);
            }
        }
        leftLeg.xRot = Mth.lerp(swimAmount, leftLeg.xRot, 0.3 * Math.cos(limbSwing * 0.33333334 + Math.PI));
        rightLeg.xRot = Mth.lerp(swimAmount, rightLeg.xRot, 0.3 * Math.cos(limbSwing * 0.33333334));
    }

    private static void bobModelPart(Part part, double ageInTicks, double sign) {
        part.zRot += sign * (Math.cos(ageInTicks * 0.09) * 0.05 + 0.05);
        part.xRot += sign * Math.sin(ageInTicks * 0.067) * 0.05;
    }

    private static ArmPose getArmPose(LivingEntity e, InteractionHand hand) {
        ItemStack stack = e.getItemInHand(hand);
        if (stack.isEmpty()) {
            return ArmPose.EMPTY;
        }
        if (AhfHooks.isHeldGun(stack)) {
            return hand == InteractionHand.MAIN_HAND ? ArmPose.GUN : ArmPose.EMPTY;
        }
        if (e.getUsedItemHand() == hand && e.getUseItemRemainingTicks() > 0) {
            UseAnim anim = stack.getUseAnimation();
            switch (anim) {
                case BLOCK:
                    return ArmPose.BLOCK;
                case BOW:
                    return ArmPose.BOW;
                case SPEAR:
                    return ArmPose.SPEAR;
                case CROSSBOW:
                    if (hand == e.getUsedItemHand()) {
                        return ArmPose.CROSSBOW_CHARGE;
                    }
                    break;
                case SPYGLASS:
                    return ArmPose.SPYGLASS;
                case TOOT_HORN:
                    return ArmPose.TOOT_HORN;
                case BRUSH:
                    return ArmPose.BRUSH;
                default:
                    break;
            }
        } else if (!e.swinging && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return ArmPose.CROSSBOW_HOLD;
        }
        return ArmPose.ITEM;
    }

    private static HumanoidArm getAttackArm(LivingEntity e) {
        HumanoidArm main = e.getMainArm();
        return e.swingingArm == InteractionHand.MAIN_HAND ? main : main.getOpposite();
    }

    private static double rotlerpRad(double pct, double from, double to) {
        double delta = to - from;
        while (delta >= Math.PI) {
            delta -= 2.0 * Math.PI;
        }
        while (delta < -Math.PI) {
            delta += 2.0 * Math.PI;
        }
        return from + pct * delta;
    }

    private static double quadraticArmUpdate(double x) {
        return -65.0 * x + x * x;
    }

    /** Upper / lower halves of a bendable part; {@code pre} = waist bend (arms), null = none. */
    private static void limb(Part p, Matrix4d pre, double pad, LocalRig rig, LocalRig.Slot upper,
                             LocalRig.Slot lower) {
        Matrix4d m = p.matrix();
        if (pre != null) {
            m.mulLocal(pre);
        }
        upper.set(rig, toObb(p, m, false, pad));
        lower.set(rig, toObb(p, bent(p, m), true, pad));
    }

    /** {@code m T(c) R(bend) T(-c)}, c = box centre (PA / bendy-lib bend pivot), part px. */
    private static Matrix4d bent(Part p, Matrix4d m) {
        Matrix4d out = new Matrix4d(m);
        if (p.bend != 0.0) {
            double cx = p.ox + p.sx / 2.0, cy = p.oy + p.sy / 2.0, cz = p.oz + p.sz / 2.0;
            double axis = -p.bendAxis;
            out.translate(cx, cy, cz).rotate(p.bend, Math.cos(axis), 0.0, Math.sin(axis)).translate(-cx, -cy, -cz);
        }
        return out;
    }

    /** Box of {@code p} posed by {@code m} (part px -> model px); {@code half}: null = whole, false upper, true lower. */
    private static Obb toObb(Part p, Matrix4d m, Boolean half, double pad) {
        double sy = half == null ? p.sy : p.sy / 2.0;
        double oy = half == Boolean.TRUE ? p.oy + sy : p.oy;
        double cx = p.ox + p.sx / 2.0;
        double cy = oy + sy / 2.0;
        double cz = p.oz + p.sz / 2.0;
        Vec3 centre = toLocalPoint(at(m, cx, cy, cz));
        Vec3 vx = toLocalPoint(at(m, cx + p.sx / 2.0, cy, cz)).subtract(centre);
        Vec3 vy = toLocalPoint(at(m, cx, cy + sy / 2.0, cz)).subtract(centre);
        Vec3 vz = toLocalPoint(at(m, cx, cy, cz + p.sz / 2.0)).subtract(centre);
        Vec3 halfExtent = new Vec3(vx.length() + pad, vy.length() + pad, vz.length() + pad);
        return new Obb(centre, vx.normalize(), vy.normalize(), vz.normalize(), halfExtent, p.limb);
    }

    private static Vec3 at(Matrix4d m, double x, double y, double z) {
        Vector3d v = m.transformPosition(x, y, z, new Vector3d());
        return new Vec3(v.x, v.y, v.z);
    }

    private static Vec3 toLocalPoint(Vec3 model) {
        double x = -MODEL_SCALE * model.x * UNIT;
        double y = MODEL_SCALE * (Y_SHIFT - model.y * UNIT);
        double z = -MODEL_SCALE * model.z * UNIT;
        return new Vec3(x, y, z);
    }

    private enum ArmPose {
        EMPTY(false), ITEM(false), BLOCK(false), BOW(true), SPEAR(false),
        CROSSBOW_CHARGE(true), CROSSBOW_HOLD(true), SPYGLASS(false), TOOT_HORN(false),
        BRUSH(false), GUN(true);

        final boolean twoHanded;

        ArmPose(boolean twoHanded) {
            this.twoHanded = twoHanded;
        }
    }

    /** Bendable parts split at the bend pivot: {@code chest} (top, bent) / {@code torso}, upper arm / forearm, thigh / shin. */
    public static final class LocalRig {
        public static final Slot[] SLOTS = Slot.values();
        public Obb head;
        public Obb torso;
        public Obb chest;
        public Obb leftArm;
        public Obb leftForearm;
        public Obb rightArm;
        public Obb rightForearm;
        public Obb leftLeg;
        public Obb leftShin;
        public Obb rightLeg;
        public Obb rightShin;

        private Obb[] all;

        public Obb[] all() {
            Obb[] a = all;
            if (a == null) {
                a = new Obb[SLOTS.length];
                for (Slot s : SLOTS) {
                    a[s.ordinal()] = s.get(this);
                }
                all = a;
            }
            return a;
        }

        public enum Slot {
            HEAD(HitboxPart.HEAD),
            TORSO(HitboxPart.TORSO),
            CHEST(HitboxPart.TORSO),
            LEFT_ARM(HitboxPart.LEFT_ARM),
            LEFT_FOREARM(HitboxPart.LEFT_ARM),
            RIGHT_ARM(HitboxPart.RIGHT_ARM),
            RIGHT_FOREARM(HitboxPart.RIGHT_ARM),
            LEFT_LEG(HitboxPart.LEFT_LEG),
            LEFT_SHIN(HitboxPart.LEFT_LEG),
            RIGHT_LEG(HitboxPart.RIGHT_LEG),
            RIGHT_SHIN(HitboxPart.RIGHT_LEG);

            public final HitboxPart limb;

            Slot(HitboxPart limb) {
                this.limb = limb;
            }

            public Obb get(LocalRig r) {
                return switch (this) {
                    case HEAD -> r.head;
                    case TORSO -> r.torso;
                    case CHEST -> r.chest;
                    case LEFT_ARM -> r.leftArm;
                    case LEFT_FOREARM -> r.leftForearm;
                    case RIGHT_ARM -> r.rightArm;
                    case RIGHT_FOREARM -> r.rightForearm;
                    case LEFT_LEG -> r.leftLeg;
                    case LEFT_SHIN -> r.leftShin;
                    case RIGHT_LEG -> r.rightLeg;
                    case RIGHT_SHIN -> r.rightShin;
                };
            }

            public void set(LocalRig r, Obb o) {
                switch (this) {
                    case HEAD -> r.head = o;
                    case TORSO -> r.torso = o;
                    case CHEST -> r.chest = o;
                    case LEFT_ARM -> r.leftArm = o;
                    case LEFT_FOREARM -> r.leftForearm = o;
                    case RIGHT_ARM -> r.rightArm = o;
                    case RIGHT_FOREARM -> r.rightForearm = o;
                    case LEFT_LEG -> r.leftLeg = o;
                    case LEFT_SHIN -> r.leftShin = o;
                    case RIGHT_LEG -> r.rightLeg = o;
                    case RIGHT_SHIN -> r.rightShin = o;
                }
                r.all = null;
            }
        }
    }

    private static final class Part {
        final double ox, oy, oz, sx, sy, sz;
        final HitboxPart limb;
        double x, y, z;
        double xRot, yRot, zRot;
        double bendAxis, bend;

        Part(double ox, double oy, double oz, double sx, double sy, double sz,
             double px, double py, double pz, HitboxPart limb) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.x = px;
            this.y = py;
            this.z = pz;
            this.limb = limb;
        }

        double[] channels(double[] v) {
            v[0] = x;
            v[1] = y;
            v[2] = z;
            v[3] = xRot;
            v[4] = yRot;
            v[5] = zRot;
            v[PoseClip.BEND_AXIS] = bendAxis;
            v[PoseClip.BEND] = bend;
            return v;
        }

        void set(double[] v) {
            x = v[0];
            y = v[1];
            z = v[2];
            xRot = v[3];
            yRot = v[4];
            zRot = v[5];
            bendAxis = v[PoseClip.BEND_AXIS];
            bend = v[PoseClip.BEND];
        }

        /** ModelPart transform: {@code T(x, y, z) Rz Ry Rx}, part px -> model px. */
        Matrix4d matrix() {
            return new Matrix4d().translation(x, y, z).rotateZYX(zRot, yRot, xRot);
        }
    }
}
