package com.norwood.ahf.hit;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.config.HitRegMode;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.rig.RigCache;
import com.norwood.ahf.stance.AhfStances;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HitboxApi {

    private static final double HITSCAN_RANGE = 64.0;
    private static final double TRACE_MARGIN = 1.0;
    private static final double UPPER_ARM_LOW = 0.55;

    private HitboxApi() {
    }

    public static @Nullable HitboxPart classifyHit(LivingEntity victim, DamageSource src, HitCategory cat) {
        if (victim instanceof Player && AhfConfig.riggedLimbBoxes() && rigUsable(victim)) {
            HitboxPart rigLimb = classifyRig(victim, src, cat);
            if (rigLimb != null) {
                return rigLimb;
            }
        }
        Vec3 hit = resolveHitPoint(victim, src, cat);
        return hit == null ? null : classifyLocal(victim, hit);
    }

    public static List<HitboxPart> classifyHitPierced(LivingEntity victim, DamageSource src, HitCategory cat) {
        if (victim instanceof Player && AhfConfig.riggedLimbBoxes() && rigUsable(victim)) {
            Vec3[] seg = attackSegment(victim, src, cat);
            if (seg != null) {
                List<HitboxPart> pierced = rigRayPierce(victim, seg[0], seg[1]);
                if (!pierced.isEmpty()) {
                    return pierced;
                }
            }
        }
        HitboxPart single = classifyHit(victim, src, cat);
        return single == null ? List.of() : List.of(single);
    }

    private static @Nullable HitboxPart classifyRig(LivingEntity victim, DamageSource src, HitCategory cat) {
        AABB box = victim.getBoundingBox();
        var direct = src.getDirectEntity();
        var attacker = src.getEntity();

        if (direct instanceof Projectile && direct != attacker) {
            Vec3 to = direct.position();
            Vec3 from = new Vec3(direct.xo, direct.yo, direct.zo);
            Vec3 dir = to.subtract(from);
            if (dir.lengthSqr() < 1.0e-8) {
                dir = direct.getDeltaMovement();
            }
            if (dir.lengthSqr() < 1.0e-8) {
                dir = victim.position().subtract(to);
            }
            if (dir.lengthSqr() < 1.0e-8) {
                return rigPointPick(victim, nearestPointOnBox(box, to));
            }
            Vec3 d = dir.normalize();
            return rigRayPick(victim, from.subtract(d.scale(TRACE_MARGIN)), to.add(d.scale(TRACE_MARGIN)));
        }

        if (attacker instanceof LivingEntity shooter && attacker != victim) {
            boolean ballistic = (cat == HitCategory.BALLISTIC);
            boolean melee = (direct == attacker);
            if (ballistic || melee) {
                if (ballistic) {
                    Optional<Vec3> tacz = AhfHooks.bulletHitPos(src);
                    if (tacz.isPresent()) {
                        return rigPointPick(victim, tacz.get());
                    }
                }
                Vec3 eye = shooter.getEyePosition();
                Vec3 look = shooter.getViewVector(1.0F);
                double range = ballistic ? HITSCAN_RANGE : AhfConfig.meleeReach();
                return rigRayPick(victim, eye, eye.add(look.scale(range)));
            }
        }

        Vec3 srcPos = src.getSourcePosition();
        if (srcPos != null) {
            Vec3 centre = box.getCenter();
            Vec3 entry = box.clip(srcPos, centre).orElse(centre);
            return rigPointPick(victim, entry);
        }

        return null;
    }

    public static boolean isGapShot(LivingEntity victim, DamageSource src, HitCategory cat) {
        if (src == null || !(victim instanceof Player) && !HitRegistration.isEnvelopeTarget(victim)) {
            return false;
        }
        if (!rigPoseSupported(victim)) {
            return false;
        }
        Vec3[] seg = attackSegment(victim, src, cat);
        if (seg == null) {
            return false;
        }

        return rigRayPick(victim, seg[0], seg[1]) == null;
    }

    public static boolean shouldRejectGap(LivingEntity victim, DamageSource src, HitCategory cat) {
        HitRegMode mode = AhfConfig.hitRegistrationMode();
        if (mode == HitRegMode.OFF) {
            return false;
        }
        if (isDirectMelee(src)) {
            if (!(src.getEntity() instanceof Player)) {
                return false;
            }
            return isGapShot(victim, src, cat);
        }
        if (mode != HitRegMode.PRECISE) {
            return false;
        }
        return isGapShot(victim, src, cat);
    }

    public static boolean isDirectMelee(DamageSource src) {
        if (src == null) {
            return false;
        }
        var attacker = src.getEntity();
        return attacker instanceof LivingEntity && src.getDirectEntity() == attacker;
    }

    private static @Nullable Vec3[] attackSegment(LivingEntity victim, DamageSource src, HitCategory cat) {
        var direct = src.getDirectEntity();
        var attacker = src.getEntity();
        if (direct instanceof Projectile && direct != attacker) {
            Vec3 to = direct.position();
            Vec3 from = new Vec3(direct.xo, direct.yo, direct.zo);
            Vec3 dir = to.subtract(from);
            if (dir.lengthSqr() < 1.0e-8) {
                dir = direct.getDeltaMovement();
            }
            if (dir.lengthSqr() < 1.0e-8) {
                dir = victim.position().subtract(to);
            }
            if (dir.lengthSqr() < 1.0e-8) {
                return null;
            }
            Vec3 d = dir.normalize();
            return new Vec3[]{from.subtract(d.scale(TRACE_MARGIN)), to.add(d.scale(TRACE_MARGIN))};
        }
        if (attacker instanceof LivingEntity shooter && attacker != victim) {
            boolean ballistic = (cat == HitCategory.BALLISTIC);
            boolean melee = (direct == attacker);
            if (ballistic || melee) {
                if (ballistic && AhfHooks.bulletHitPos(src).isPresent()) {
                    return null;
                }
                Vec3 eye = shooter.getEyePosition();
                Vec3 look = shooter.getViewVector(1.0F);
                double range = ballistic ? HITSCAN_RANGE : AhfConfig.meleeReach();
                return new Vec3[]{eye, eye.add(look.scale(range))};
            }
        }
        return null;
    }

    public static @Nullable Vec3 resolveHitPoint(LivingEntity victim, DamageSource src, HitCategory cat) {
        AABB box = victim.getBoundingBox();
        var direct = src.getDirectEntity();
        var attacker = src.getEntity();

        if (direct instanceof Projectile && direct != attacker) {
            Vec3 to = direct.position();
            Vec3 from = new Vec3(direct.xo, direct.yo, direct.zo);
            Vec3 dir = to.subtract(from);
            if (dir.lengthSqr() < 1.0e-8) {
                dir = direct.getDeltaMovement();
            }
            if (dir.lengthSqr() < 1.0e-8) {
                dir = victim.position().subtract(to);
            }
            if (dir.lengthSqr() < 1.0e-8) {
                return nearestPointOnBox(box, to);
            }
            Vec3 d = dir.normalize();
            Optional<Vec3> hit = box.clip(from.subtract(d.scale(TRACE_MARGIN)), to.add(d.scale(TRACE_MARGIN)));
            final Vec3 target = to;
            return hit.orElseGet(() -> nearestPointOnBox(box, target));
        }

        if (attacker instanceof LivingEntity shooter && attacker != victim) {
            boolean ballistic = (cat == HitCategory.BALLISTIC);
            boolean melee = (direct == attacker);
            if (ballistic || melee) {
                if (ballistic) {
                    Optional<Vec3> tacz = AhfHooks.bulletHitPos(src);
                    if (tacz.isPresent()) {
                        return tacz.get();
                    }
                }
                Vec3 eye = shooter.getEyePosition();
                Vec3 look = shooter.getViewVector(1.0F);
                double range = ballistic ? HITSCAN_RANGE : AhfConfig.meleeReach();
                Vec3 end = eye.add(look.scale(range));
                Optional<Vec3> hit = box.clip(eye, end);
                return hit.orElseGet(() -> nearestPointOnBox(box, end));
            }
        }

        Vec3 srcPos = src.getSourcePosition();
        if (srcPos != null) {
            Vec3 centre = box.getCenter();
            return box.clip(srcPos, centre).orElse(centre);
        }

        return null;
    }

    public static @Nullable HitboxPart classifyRay(LivingEntity victim, Vec3 from, Vec3 to) {
        if (victim instanceof Player && AhfConfig.riggedLimbBoxes() && rigUsable(victim)) {
            HitboxPart rigLimb = rigRayPick(victim, from, to);
            if (rigLimb != null) {
                return rigLimb;
            }
        }
        Optional<Vec3> hit = victim.getBoundingBox().clip(from, to);
        return hit.map(v -> classifyLocal(victim, v)).orElse(null);
    }

    private static boolean rigUsable(LivingEntity victim) {
        if (!rigPoseSupported(victim)) {
            return false;
        }
        if (isUprightHumanoid(victim)) {
            AABB box = victim.getBoundingBox();
            return box.getYsize() >= box.getXsize();
        }
        return true;
    }

    public static boolean rigPoseSupported(LivingEntity victim) {
        if (isUprightHumanoid(victim)) {
            return true;
        }
        if (AhfStances.isActive(victim)) {
            return true;
        }
        if (victim.isAutoSpinAttack()) {
            return false;
        }
        Pose pose = victim.getPose();
        if (pose == Pose.SLEEPING || pose == Pose.DYING) {
            return false;
        }
        return victim.isFallFlying() || victim.isVisuallySwimming() || victim.getSwimAmount(1.0F) > 0.0F;
    }

    public static boolean isUprightHumanoid(LivingEntity victim) {
        com.norwood.ahf.stance.Stance active = AhfStances.active(victim);
        if (active != null && !active.upright()) {
            return false;
        }
        if (victim.isVisuallySwimming() || victim.isFallFlying() || victim.isAutoSpinAttack()) {
            return false;
        }
        if (victim.getSwimAmount(1.0F) > 0.0F) {
            return false;
        }
        Pose pose = victim.getPose();
        return pose != Pose.SWIMMING && pose != Pose.FALL_FLYING && pose != Pose.SLEEPING
                && pose != Pose.DYING && pose != Pose.SPIN_ATTACK;
    }

    private static @Nullable HitboxPart rigRayPick(LivingEntity victim, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0e-12) {
            return null;
        }
        Vec3 origin = worldToLocalPoint(victim, from);
        Vec3 localDir = worldToLocalDir(victim, dir);
        HumanoidRig.LocalRig rig = RigCache.resolve(victim);
        double best = Double.POSITIVE_INFINITY;
        HitboxPart limb = null;
        for (Obb obb : rig.all()) {
            double t = obb.rayEntry(origin, localDir);
            if (t < best) {
                best = t;
                limb = obb.limb();
            }
        }
        return best == Double.POSITIVE_INFINITY ? null : limb;
    }

    private static List<HitboxPart> rigRayPierce(LivingEntity victim, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0e-12) {
            return List.of();
        }
        Vec3 origin = worldToLocalPoint(victim, from);
        Vec3 localDir = worldToLocalDir(victim, dir);
        HumanoidRig.LocalRig rig = RigCache.resolve(victim);
        Obb[] all = rig.all();
        double[] ts = new double[all.length];
        HitboxPart[] limbs = new HitboxPart[all.length];
        int n = 0;
        for (Obb obb : all) {
            double t = obb.rayEntry(origin, localDir);
            if (t != Double.POSITIVE_INFINITY) {
                ts[n] = t;
                limbs[n] = obb.limb();
                n++;
            }
        }
        if (n == 0) {
            return List.of();
        }
        for (int i = 1; i < n; i++) {
            double tk = ts[i];
            HitboxPart lk = limbs[i];
            int j = i - 1;
            while (j >= 0 && ts[j] > tk) {
                ts[j + 1] = ts[j];
                limbs[j + 1] = limbs[j];
                j--;
            }
            ts[j + 1] = tk;
            limbs[j + 1] = lk;
        }
        List<HitboxPart> out = new ArrayList<>(n);
        double budget = AhfConfig.penetrationBudget();
        for (int i = 0; i < n; i++) {
            out.add(limbs[i]);
            budget -= AhfConfig.penetrationResistance(limbs[i]);
            if (budget <= 0.0) {
                break;
            }
        }
        return out;
    }

    private static HitboxPart rigPointPick(LivingEntity victim, Vec3 worldPoint) {
        Vec3 local = worldToLocalPoint(victim, worldPoint);
        HumanoidRig.LocalRig rig = RigCache.resolve(victim);
        double best = Double.POSITIVE_INFINITY;
        HitboxPart limb = HitboxPart.TORSO;
        for (Obb obb : rig.all()) {
            double d = obb.distanceSq(local);
            if (d < best) {
                best = d;
                limb = obb.limb();
            }
        }
        return limb;
    }

    private static Vec3 worldToLocalPoint(LivingEntity victim, Vec3 world) {
        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-front.z, 0.0, front.x);
        Vec3 off = world.subtract(victim.position());
        return new Vec3(off.dot(right), off.y, off.dot(front));
    }

    private static Vec3 worldToLocalDir(LivingEntity victim, Vec3 dir) {
        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-front.z, 0.0, front.x);
        return new Vec3(dir.dot(right), dir.y, dir.dot(front));
    }

    public static @Nullable HitboxPart classifyLocal(LivingEntity victim, Vec3 worldHit) {
        AABB box = victim.getBoundingBox();

        com.norwood.ahf.stance.Stance active = AhfStances.active(victim);
        if (active != null && !active.upright()) {
            return null;
        }
        if (box.getYsize() < box.getXsize()) {
            return null;
        }
        if (!isUprightHumanoid(victim)) {
            return null;
        }

        Vec3 centre = box.getCenter();
        double relY = (worldHit.y - box.minY) / box.getYsize();

        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw)).normalize();
        Vec3 right = new Vec3(-front.z, 0.0, front.x);

        Vec3 off = worldHit.subtract(centre);
        double side = off.dot(right);
        double along = off.dot(front);
        double nx = side / (box.getXsize() * 0.5);

        if (AhfConfig.poseAwareArms() && isAimingWeapon(victim)
                && along > 0.0
                && relY >= UPPER_ARM_LOW && relY < AhfConfig.headBandBottom()) {
            return armForAimPose(victim, nx);
        }

        return classifyStanding(relY, nx);
    }

    private static HitboxPart classifyStanding(double relY, double nx) {
        if (relY >= AhfConfig.headBandBottom()) {
            return HitboxPart.HEAD;
        }
        if (relY <= AhfConfig.legBandTop()) {
            return (nx >= 0.0) ? HitboxPart.RIGHT_LEG : HitboxPart.LEFT_LEG;
        }
        if (Math.abs(nx) >= AhfConfig.armSideThreshold()) {
            return (nx >= 0.0) ? HitboxPart.RIGHT_ARM : HitboxPart.LEFT_ARM;
        }
        return HitboxPart.TORSO;
    }

    private static Vec3 nearestPointOnBox(AABB box, Vec3 target) {
        double x = clamp(target.x, box.minX, box.maxX);
        double y = clamp(target.y, box.minY, box.maxY);
        double z = clamp(target.z, box.minZ, box.maxZ);
        return new Vec3(x, y, z);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (Math.min(v, hi));
    }

    private static boolean isAimingWeapon(LivingEntity victim) {
        if (victim.isUsingItem()) {
            ItemStack using = victim.getUseItem();
            if (using.getItem() instanceof BowItem || using.getItem() instanceof CrossbowItem) {
                return true;
            }
            UseAnim anim = using.getUseAnimation();
            if (anim == UseAnim.BOW || anim == UseAnim.SPEAR || anim == UseAnim.CROSSBOW) {
                return true;
            }
        }
        return AhfHooks.isHeldGun(victim.getMainHandItem());
    }

    private static HitboxPart armForAimPose(LivingEntity victim, double nx) {
        HumanoidArm main = (victim instanceof Player player) ? player.getMainArm() : HumanoidArm.RIGHT;
        boolean rightMain = (main == HumanoidArm.RIGHT);
        if (nx > 0.0) {
            return HitboxPart.RIGHT_ARM;
        }
        if (nx < 0.0) {
            return HitboxPart.LEFT_ARM;
        }
        return rightMain ? HitboxPart.RIGHT_ARM : HitboxPart.LEFT_ARM;
    }
}
