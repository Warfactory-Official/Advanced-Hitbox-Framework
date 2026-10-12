package com.norwood.ahf.hit;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.config.HitRegMode;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.lagcomp.EntityHistory;
import com.norwood.ahf.lagcomp.Snapshot;
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
import net.minecraft.world.item.ItemUseAnimation;
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
    /** Ray-cast limit, segment lengths. Derived segments (vanilla projectile, eye + reach) => unbounded: end is a guess. */
    private static final double SEGMENT = 1.0;
    /** != +inf: {@link Obb#rayEntry} miss = +inf. */
    private static final double UNBOUNDED = Double.MAX_VALUE;

    private HitboxApi() {
    }

    public static @Nullable HitboxPart classifyHit(LivingEntity victim, DamageSource src, HitCategory cat) {
        if (src instanceof AhfHitSource h) {
            if (h.hitPart() != null) {
                return h.hitPart();
            }
            RayHit r = classifyRay(victim, h.hitFrom(), h.hitTo(), h.hitGameTime());
            return r == null ? null : r.part();
        }
        if (victim instanceof Player && AhfConfig.riggedLimbBoxes() && rigUsable(victim)) {
            HitboxPart rigLimb = classifyRig(victim, src, cat);
            if (rigLimb != null) {
                if (AhfConfig.logHitDetection()) {
                    HitDetectionDebug.logClassifyHit(victim, src, cat, "RIG", rigLimb);
                }
                return rigLimb;
            }
        }
        Vec3 hit = resolveHitPoint(victim, src, cat);
        HitboxPart result = hit == null ? null : classifyLocal(victim, hit);
        if (AhfConfig.logHitDetection()) {
            HitDetectionDebug.logClassifyHit(victim, src, cat, hit == null ? "NO_HIT_POINT" : "BANDED_FALLBACK",
                    result);
        }
        return result;
    }

    public static List<HitboxPart> classifyHitPierced(LivingEntity victim, DamageSource src, HitCategory cat) {
        if (src instanceof AhfHitSource h) {
            if (h.hitPart() != null) {
                return List.of(h.hitPart());
            }
            return parts(classifyRayPierced(victim, h.hitFrom(), h.hitTo(), h.hitGameTime()));
        }
        if (victim instanceof Player && AhfConfig.riggedLimbBoxes() && rigUsable(victim)) {
            Vec3[] seg = attackSegment(victim, src, cat);
            if (seg != null) {
                List<RayHit> pierced = rigRayPierce(victim.position(), victim.yBodyRot, RigCache.resolve(victim),
                        seg[0], seg[1], UNBOUNDED);
                if (!pierced.isEmpty()) {
                    return parts(pierced);
                }
            }
        }
        HitboxPart single = classifyHit(victim, src, cat);
        return single == null ? List.of() : List.of(single);
    }

    private static List<HitboxPart> parts(List<RayHit> hits) {
        List<HitboxPart> out = new ArrayList<>(hits.size());
        for (RayHit h : hits) {
            out.add(h.part());
        }
        return out;
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
                return rigPointPick(victim, nearestPointOnBox(box, to), src, "PROJECTILE_DEGENERATE");
            }
            Vec3 d = dir.normalize();
            return part(rigRayPick(victim.position(), victim.yBodyRot, RigCache.resolve(victim),
                    from.subtract(d.scale(TRACE_MARGIN)), to.add(d.scale(TRACE_MARGIN)), UNBOUNDED));
        }

        if (attacker instanceof LivingEntity shooter && attacker != victim) {
            boolean ballistic = (cat == HitCategory.BALLISTIC);
            boolean melee = (direct == attacker);
            if (ballistic || melee) {
                Vec3 eye = shooter.getEyePosition();
                Vec3 look = shooter.getViewVector(1.0F);
                double range = ballistic ? HITSCAN_RANGE : AhfConfig.meleeReach();
                return part(rigRayPick(victim.position(), victim.yBodyRot, RigCache.resolve(victim), eye,
                        eye.add(look.scale(range)), UNBOUNDED));
            }
        }

        Vec3 srcPos = src.getSourcePosition();
        if (srcPos != null) {
            Vec3 centre = box.getCenter();
            Vec3 entry = box.clip(srcPos, centre).orElse(centre);
            return rigPointPick(victim, entry, src, "SRC_POS_FALLBACK");
        }

        return null;
    }

    private static @Nullable HitboxPart part(@Nullable RayHit hit) {
        return hit == null ? null : hit.part();
    }

    public static boolean isGapShot(LivingEntity victim, DamageSource src, HitCategory cat) {
        if (src == null || !(victim instanceof Player) && !HitRegistration.isEnvelopeTarget(victim)) {
            return false;
        }
        double pad = AhfConfig.hitGapRejectTolerance();
        if (src instanceof AhfHitSource h) {
            return h.hitPart() == null && !rayHitsWithin(victim, h.hitFrom(), h.hitTo(), h.hitGameTime(), pad);
        }
        if (!rigPoseSupported(victim)) {
            return false;
        }
        Vec3[] seg = attackSegment(victim, src, cat);
        if (seg == null) {
            return false;
        }
        return !rigRayHitsWithin(victim.position(), victim.yBodyRot, RigCache.resolve(victim), seg[0], seg[1], pad,
                UNBOUNDED);
    }

    /**
     * Segment passes within {@code pad} of a limb box of {@code victim} as broadcast in tick {@code gameTime}
     * (snapshot rig; live without snapshot). No usable rig => true (cannot tell a gap).
     */
    public static boolean rayHitsWithin(LivingEntity victim, Vec3 from, Vec3 to, long gameTime, double pad) {
        Snapshot s = EntityHistory.snapshot(victim, gameTime);
        if (s == null) {
            return !rigPoseSupported(victim)
                    || rigRayHitsWithin(victim.position(), victim.yBodyRot, RigCache.resolve(victim), from, to, pad,
                    SEGMENT);
        }
        return s.rig() == null || rigRayHitsWithin(s.pos(), s.bodyYaw(), s.rig(), from, to, pad, SEGMENT);
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
        if (src instanceof AhfHitSource h) {
            RayHit r = classifyRay(victim, h.hitFrom(), h.hitTo(), h.hitGameTime());
            return r != null ? r.point(h.hitFrom(), h.hitTo()) : nearestPointOnBox(box, h.hitTo());
        }
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

    /**
     * Part struck by the segment {@code from -> to} (world space). Rig: first limb box entered, else nearest part to
     * the segment's entry into the registration box; PRECISE rejects that fallback unless a limb box lies within
     * {@code hitGapRejectTolerance}. No rig: banded part at the bounding-box entry. Null on miss.
     */
    public static @Nullable RayHit classifyRay(LivingEntity victim, Vec3 from, Vec3 to) {
        if (victim instanceof Player && AhfConfig.riggedLimbBoxes() && rigUsable(victim)) {
            return rigSegment(victim, victim.position(), victim.yBodyRot, RigCache.resolve(victim),
                    HitRegistration.registrationBox(victim), from, to);
        }
        return bandedSegment(victim, victim.getBoundingBox(), victim.yBodyRot, isUprightHumanoid(victim),
                isAimingWeapon(victim), from, to);
    }

    /**
     * {@link #classifyRay} against {@code victim} as broadcast in tick {@code gameTime}
     * ({@link EntityHistory#snapshot}): rig, position, body yaw, boxes and banded flags from the snapshot.
     * No snapshot => live.
     */
    public static @Nullable RayHit classifyRay(LivingEntity victim, Vec3 from, Vec3 to, long gameTime) {
        Snapshot s = EntityHistory.snapshot(victim, gameTime);
        return s == null ? classifyRay(victim, from, to) : classifyRay(victim, from, to, s);
    }

    /** {@link #classifyRay(LivingEntity, Vec3, Vec3, long)} against a snapshot already looked up. */
    public static @Nullable RayHit classifyRay(LivingEntity victim, Vec3 from, Vec3 to, Snapshot s) {
        if (s.rig() != null) {
            return rigSegment(victim, s.pos(), s.bodyYaw(), s.rig(), s.envelope(), from, to);
        }
        return bandedSegment(victim, s.box(), s.bodyYaw(), s.upright(), s.aiming(), from, to);
    }

    private static @Nullable RayHit rigSegment(LivingEntity victim, Vec3 pos, float yaw, HumanoidRig.LocalRig rig,
                                               AABB envelope, Vec3 from, Vec3 to) {
        RayHit limb = rigRayPick(pos, yaw, rig, from, to, SEGMENT);
        if (limb != null) {
            return limb;
        }
        Vec3 entry = envelope.contains(from) ? from : envelope.clip(from, to).orElse(null);
        if (entry == null) {
            return null;
        }
        if (AhfConfig.hitRegistrationMode() == HitRegMode.PRECISE
                && !rigRayHitsWithin(pos, yaw, rig, from, to, AhfConfig.hitGapRejectTolerance(), SEGMENT)) {
            return null;
        }
        return new RayHit(rigPointPick(victim, pos, yaw, rig, entry, null, "SEGMENT_ENVELOPE"),
                fraction(from, to, entry), false);
    }

    private static @Nullable RayHit bandedSegment(LivingEntity victim, AABB box, float yaw, boolean upright,
                                                  boolean aiming, Vec3 from, Vec3 to) {
        Vec3 hit = box.contains(from) ? from : box.clip(from, to).orElse(null);
        if (hit == null) {
            return null;
        }
        HitboxPart part = classifyLocal(victim, box, yaw, upright, aiming, hit);
        return part == null ? null : new RayHit(part, fraction(from, to, hit), false);
    }

    private static double fraction(Vec3 from, Vec3 to, Vec3 p) {
        double len2 = to.distanceToSqr(from);
        return len2 == 0.0 ? 0.0 : Math.sqrt(p.distanceToSqr(from) / len2);
    }

    /**
     * Parts along {@code from -> to} in entry order, cut once {@link AhfConfig#penetrationBudget} is spent.
     * Falls back to the single {@link #classifyRay} hit; empty on miss. Callers gate on
     * {@link AhfConfig#penetrationEnabled}.
     */
    public static List<RayHit> classifyRayPierced(LivingEntity victim, Vec3 from, Vec3 to) {
        if (victim instanceof Player && AhfConfig.riggedLimbBoxes() && rigUsable(victim)) {
            List<RayHit> pierced = rigRayPierce(victim.position(), victim.yBodyRot, RigCache.resolve(victim),
                    from, to, SEGMENT);
            if (!pierced.isEmpty()) {
                return pierced;
            }
        }
        RayHit single = classifyRay(victim, from, to);
        return single == null ? List.of() : List.of(single);
    }

    /** {@link #classifyRayPierced} at tick {@code gameTime}; see {@link #classifyRay(LivingEntity, Vec3, Vec3, long)}. */
    public static List<RayHit> classifyRayPierced(LivingEntity victim, Vec3 from, Vec3 to, long gameTime) {
        Snapshot s = EntityHistory.snapshot(victim, gameTime);
        return s == null ? classifyRayPierced(victim, from, to) : classifyRayPierced(victim, from, to, s);
    }

    /** {@link #classifyRayPierced(LivingEntity, Vec3, Vec3, long)} against a snapshot already looked up. */
    public static List<RayHit> classifyRayPierced(LivingEntity victim, Vec3 from, Vec3 to, Snapshot s) {
        if (s.rig() != null) {
            List<RayHit> pierced = rigRayPierce(s.pos(), s.bodyYaw(), s.rig(), from, to, SEGMENT);
            if (!pierced.isEmpty()) {
                return pierced;
            }
        }
        RayHit single = classifyRay(victim, from, to, s);
        return single == null ? List.of() : List.of(single);
    }

    public static boolean rigUsable(LivingEntity victim) {
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

    /** Entry at {@code t > tMax} (segment lengths) is a miss. */
    private static @Nullable RayHit rigRayPick(Vec3 pos, float yaw, HumanoidRig.LocalRig rig, Vec3 from, Vec3 to,
                                               double tMax) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0e-12) {
            return null;
        }
        Vec3 origin = worldToLocalPoint(pos, yaw, from);
        Vec3 localDir = worldToLocalDir(yaw, dir);
        double best = Double.POSITIVE_INFINITY;
        HitboxPart limb = null;
        for (Obb obb : rig.all()) {
            double t = obb.rayEntry(origin, localDir);
            if (t < best && t <= tMax) {
                best = t;
                limb = obb.limb();
            }
        }
        return limb == null ? null : new RayHit(limb, best, true);
    }

    private static boolean rigRayHitsWithin(Vec3 pos, float yaw, HumanoidRig.LocalRig rig, Vec3 from, Vec3 to,
                                            double pad, double tMax) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0e-12) {
            return false;
        }
        Vec3 origin = worldToLocalPoint(pos, yaw, from);
        Vec3 localDir = worldToLocalDir(yaw, dir);
        for (Obb obb : rig.all()) {
            if (obb.rayEntry(origin, localDir, pad) <= tMax) {
                return true;
            }
        }
        return false;
    }

    private static List<RayHit> rigRayPierce(Vec3 pos, float yaw, HumanoidRig.LocalRig rig, Vec3 from, Vec3 to,
                                             double tMax) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0e-12) {
            return List.of();
        }
        Vec3 origin = worldToLocalPoint(pos, yaw, from);
        Vec3 localDir = worldToLocalDir(yaw, dir);
        Obb[] all = rig.all();
        double[] ts = new double[all.length];
        HitboxPart[] limbs = new HitboxPart[all.length];
        int n = 0;
        for (Obb obb : all) {
            double t = obb.rayEntry(origin, localDir);
            if (t <= tMax) {
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
        List<RayHit> out = new ArrayList<>(n);
        double budget = AhfConfig.penetrationBudget();
        for (int i = 0; i < n; i++) {
            // Halves of one bent part (upper arm / forearm, ...) entered back to back = one part.
            if (i > 0 && limbs[i] == limbs[i - 1]) {
                continue;
            }
            out.add(new RayHit(limbs[i], ts[i], true));
            budget -= AhfConfig.penetrationResistance(limbs[i]);
            if (budget <= 0.0) {
                break;
            }
        }
        return out;
    }

    private static HitboxPart rigPointPick(LivingEntity victim, Vec3 worldPoint, @Nullable DamageSource src,
                                           String branch) {
        return rigPointPick(victim, victim.position(), victim.yBodyRot, RigCache.resolve(victim), worldPoint, src,
                branch);
    }

    private static HitboxPart rigPointPick(LivingEntity victim, Vec3 pos, float yaw, HumanoidRig.LocalRig rig,
                                           Vec3 worldPoint, @Nullable DamageSource src, String branch) {
        Vec3 local = worldToLocalPoint(pos, yaw, worldPoint);
        double best = Double.POSITIVE_INFINITY;
        HitboxPart limb = HitboxPart.TORSO;
        for (Obb obb : rig.all()) {
            double d = obb.distanceSq(local);
            if (d < best) {
                best = d;
                limb = obb.limb();
            }
        }
        if (AhfConfig.logHitDetection()) {
            HitDetectionDebug.logRigPointPick(victim, src, branch, rig, local, limb);
        }
        return limb;
    }

    private static Vec3 worldToLocalPoint(Vec3 pos, float yawDeg, Vec3 world) {
        double yaw = Math.toRadians(yawDeg);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-front.z, 0.0, front.x);
        Vec3 off = world.subtract(pos);
        return new Vec3(off.dot(right), off.y, off.dot(front));
    }

    private static Vec3 worldToLocalDir(float yawDeg, Vec3 dir) {
        double yaw = Math.toRadians(yawDeg);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-front.z, 0.0, front.x);
        return new Vec3(dir.dot(right), dir.y, dir.dot(front));
    }

    public static @Nullable HitboxPart classifyLocal(LivingEntity victim, Vec3 worldHit) {
        return classifyLocal(victim, victim.getBoundingBox(), victim.yBodyRot, isUprightHumanoid(victim),
                isAimingWeapon(victim), worldHit);
    }

    private static @Nullable HitboxPart classifyLocal(LivingEntity victim, AABB box, float bodyYaw, boolean upright,
                                                      boolean aiming, Vec3 worldHit) {
        if (!upright || box.getYsize() < box.getXsize()) {
            return null;
        }

        Vec3 centre = box.getCenter();
        double relY = (worldHit.y - box.minY) / box.getYsize();

        double yaw = Math.toRadians(bodyYaw);
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw)).normalize();
        Vec3 right = new Vec3(-front.z, 0.0, front.x);

        Vec3 off = worldHit.subtract(centre);
        double side = off.dot(right);
        double along = off.dot(front);
        double nx = side / (box.getXsize() * 0.5);

        if (AhfConfig.poseAwareArms() && aiming
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

    public static boolean isAimingWeapon(LivingEntity victim) {
        if (victim.isUsingItem()) {
            ItemStack using = victim.getUseItem();
            if (using.getItem() instanceof BowItem || using.getItem() instanceof CrossbowItem) {
                return true;
            }
            ItemUseAnimation anim = using.getUseAnimation();
            if (anim == ItemUseAnimation.BOW || anim == ItemUseAnimation.TRIDENT || anim == ItemUseAnimation.CROSSBOW) {
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
