package com.norwood.ahf.stance;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.lagcomp.EntityHistory;
import com.norwood.ahf.mixin.PlayerInvoker;
import com.norwood.ahf.mixin.WalkAnimationAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Shared stance rules and ticking. Constants, not config: owner prediction and remote easing must match the server
 * bit for bit, and COMMON config is not synced.
 */
public final class StanceLogic {

    public static final double LEAN_MAX_DEGREES = 30.0;
    public static final float LEAN_RATE = 0.2F;
    public static final float ADS_RATE = 0.25F;
    public static final float BODY_YAW_MAX_LAG = 45.0F;
    public static final float BODY_YAW_MAX_LAG_LYING = 20.0F;
    public static final float BODY_YAW_EASE = 0.25F;
    public static final float BODY_YAW_SYNC_EPSILON = 1.0F;
    /** Turn in place (upright, still): planted until gap > START, then RATE deg/tick until gap < STOP. */
    public static final float TURN_START = 35.0F, TURN_RATE = 9.0F, TURN_STOP = 2.0F;
    /** Walk speed above which the body eases toward the camera every tick. */
    static final float MOVING_SPEED = 0.05F;
    public static final float KNEEL_HEIGHT = 1.2F;
    public static final float KNEEL_EYE = 1.0F;
    /** Prone roll: length, ticks; lateral travel, blocks. */
    public static final int ROLL_TICKS = 14;
    public static final double ROLL_DISTANCE = 1.0;
    /** Height / side easing, steps per tick. */
    public static final float STEP_RATE = 0.15F, SIDE_RATE = 0.2F;
    /** Posture transition, per tick: upright pair, into / out of lying ({@link #LIE_RATE} = tilt). */
    public static final float UPRIGHT_RATE = 1.0F / 5.0F, LIE_RATE = 1.0F / 12.0F;
    /** Box height, eye height by level - minLevel: STAND low / mid / normal, KNEEL low / normal / high, PRONE. */
    static final float[] STAND_HEIGHT = {1.4F, 1.6F}, STAND_EYE = {1.22F, 1.42F};
    static final float[] KNEEL_HEIGHTS = {1.0F, KNEEL_HEIGHT, 1.4F}, KNEEL_EYES = {0.8F, KNEEL_EYE, 1.2F};
    static final float PRONE_HIGH_HEIGHT = 0.8F, PRONE_HIGH_EYE = 0.6F;
    static final float AIM_LIMIT = 90.0F;
    /** Further intents in the same tick are refused (seq still echoed). */
    public static final int MAX_INTENTS_PER_TICK = 4;

    private StanceLogic() {
    }

    /** Vanilla owns the pose: stance targets cleared, nothing forced. DOWNED lock holds anywhere alive + non-spectator. */
    public static boolean suspended(Player p) {
        if (!p.isAlive() || p.isSpectator()) {
            return true;
        }
        StanceState s = AhfStance.get(p);
        if (s != null && s.downed) {
            return false;
        }
        return p.isPassenger() || p.isSleeping() || p.isFallFlying() || p.isInWater() || p.getAbilities().flying;
    }

    public static EntityDimensions dimensions(Player p, Posture eff, int level) {
        EntityDimensions shaped = shape(p, eff, level);
        if (shaped != null) {
            return shaped;
        }
        return p.getDimensions(eff.lying() ? Pose.SWIMMING : Pose.STANDING);
    }

    /** Non-vanilla box of {@code eff} at {@code level}; null => vanilla STANDING / SWIMMING. */
    static @Nullable EntityDimensions shape(Player p, Posture eff, int level) {
        return switch (eff) {
            case STAND -> level == 0 ? null : sized(p.getDimensions(Pose.STANDING), STAND_HEIGHT[level + 2],
                    STAND_EYE[level + 2], p.getScale());
            case KNEEL -> sized(p.getDimensions(Pose.CROUCHING), KNEEL_HEIGHTS[level + 1], KNEEL_EYES[level + 1],
                    p.getScale());
            case PRONE -> level == 0 ? null : sized(p.getDimensions(Pose.SWIMMING), PRONE_HIGH_HEIGHT,
                    PRONE_HIGH_EYE, p.getScale());
            case DOWNED -> null;
        };
    }

    static EntityDimensions sized(EntityDimensions base, float height, float eye, float scale) {
        return new EntityDimensions(base.width(), height * scale, eye * scale, base.attachments(), base.fixed());
    }

    public static boolean fits(Player p, Posture eff, int level) {
        return p.level().noCollision(p, dimensions(p, eff, level).makeBoundingBox(p.position()).deflate(1.0E-7));
    }

    /** Owner prediction and server validation. Space check before rules. */
    public static boolean validate(Player p, StanceState s, @Nullable StanceRequest r) {
        if (r == null || r.posture() == null || r.rest() == null || r.posture() == Posture.DOWNED) {
            return false;
        }
        if (r.level() < r.posture().minLevel() || r.level() > r.posture().maxLevel() || Math.abs(r.side()) > 1) {
            return false;
        }
        if (!Float.isFinite(r.lean()) || Math.abs(r.lean()) > 1.0F
                || !Float.isFinite(r.aimYaw()) || Math.abs(r.aimYaw()) > AIM_LIMIT
                || !Float.isFinite(r.aimPitch()) || Math.abs(r.aimPitch()) > AIM_LIMIT) {
            return false;
        }
        if (s.downed) {
            return false;
        }
        boolean targets = r.posture() != Posture.STAND || r.level() != 0 || r.side() != 0 || r.lean() != 0.0F
                || r.rest() != Rest.NONE;
        if (targets && suspended(p)) {
            return false;
        }
        Posture eff = s.crawl ? Posture.PRONE : r.posture();
        int level = eff == r.posture() ? r.level() : 0;
        if (eff.lying() && r.lean() != 0.0F) {
            return false;
        }
        if ((eff != s.effective() || level != s.effectiveLevel()) && !fits(p, eff, level)) {
            return false;
        }
        for (StanceRule rule : AhfStance.rules()) {
            if (!rule.allow(p, s, r)) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code r} if valid; else {@code r}'s ADS + aim offset over the current posture/lean/rest if that is valid
     * (ADS toggles independently of a refused body change); else null.
     */
    static @Nullable StanceRequest accept(Player p, StanceState s, StanceRequest r) {
        if (validate(p, s, r)) {
            return r;
        }
        if (r == null) {
            return null;
        }
        StanceRequest adsOnly = StanceRequest.of(s).withAds(r.ads(), r.aimYaw(), r.aimPitch());
        return !adsOnly.equals(r) && validate(p, s, adsOnly) ? adsOnly : null;
    }

    static float transitionRate(Posture from, Posture to) {
        return from.lying() == to.lying() && from != Posture.DOWNED && to != Posture.DOWNED ? UPRIGHT_RATE : LIE_RATE;
    }

    /** Upright and still: {@link #TURN_START} hysteresis; else {@link #stepBodyYaw(float, float, Posture)}. */
    static float stepBodyYaw(StanceState s, float cameraYaw, boolean moving) {
        Posture eff = s.effective();
        if (moving || eff.lying()) {
            s.turning = false;
            return stepBodyYaw(s.bodyYaw, cameraYaw, eff);
        }
        float gap = Mth.wrapDegrees(cameraYaw - s.bodyYaw);
        s.turning |= Math.abs(gap) > TURN_START;
        if (!s.turning) {
            return s.bodyYaw;
        }
        float next = s.bodyYaw + Mth.clamp(gap, -TURN_RATE, TURN_RATE);
        gap = Mth.wrapDegrees(cameraYaw - next);
        if (Math.abs(gap) < TURN_STOP) {
            s.turning = false;
        }
        return next + Math.signum(gap) * Math.max(Math.abs(gap) - BODY_YAW_MAX_LAG, 0.0F);
    }

    static float stepBodyYaw(float prev, float cameraYaw, Posture eff) {
        if (eff == Posture.DOWNED) {
            return prev;
        }
        float lag = eff.lying() ? BODY_YAW_MAX_LAG_LYING : BODY_YAW_MAX_LAG;
        float next = prev + Mth.wrapDegrees(cameraYaw - prev) * BODY_YAW_EASE;
        float gap = Mth.wrapDegrees(cameraYaw - next);
        if (gap > lag) {
            next += gap - lag;
        } else if (gap < -lag) {
            next += gap + lag;
        }
        return next;
    }

    /** Forced pose + dimensions from state; both sides. */
    static void resolvePose(Player p, StanceState s) {
        boolean suspended = suspended(p);
        Posture eff = suspended ? Posture.STAND : s.effective();
        int level = suspended ? 0 : s.effectiveLevel();
        Pose want = switch (eff) {
            case STAND -> level == 0 ? null : Pose.STANDING;
            case KNEEL -> Pose.CROUCHING;
            case PRONE, DOWNED -> Pose.SWIMMING;
        };
        boolean reshape = eff != s.appliedShape || level != s.appliedLevel;
        s.appliedShape = eff;
        s.appliedLevel = level;
        if (want != null) {
            s.ownsForcedPose = true;
            p.setForcedPose(want);
            if (p.getPose() != want) {
                p.setPose(want);
                reshape = false;
            }
        } else if (s.ownsForcedPose) {
            s.ownsForcedPose = false;
            p.setForcedPose(null);
            if (p.level().isClientSide && !p.isLocalPlayer()) {
                // RemotePlayer.updatePlayerPose is a no-op; the synced server pose was overwritten above.
                p.setPose(remotePose(p));
            } else {
                ((PlayerInvoker) p).ahf$updatePlayerPose();
            }
        }
        if (reshape) {
            p.refreshDimensions();
        }
    }

    /** {@code Player.updatePlayerPose} choice from synced flags, fit checks skipped (server-validated). */
    static Pose remotePose(Player p) {
        if (p.isFallFlying()) {
            return Pose.FALL_FLYING;
        }
        if (p.isSleeping()) {
            return Pose.SLEEPING;
        }
        if (p.isSwimming()) {
            return Pose.SWIMMING;
        }
        if (p.isAutoSpinAttack()) {
            return Pose.SPIN_ATTACK;
        }
        return p.isShiftKeyDown() && !p.getAbilities().flying ? Pose.CROUCHING : Pose.STANDING;
    }

    static void writeYaw(Player p, StanceState s) {
        p.yBodyRotO = s.bodyYawO;
        p.yBodyRot = s.bodyYaw;
    }

    /**
     * Authoritative tick at {@code LevelTickEvent.Pre}, before history capture. Only sync point: every server-side
     * change since the last tick goes out once, stamped with this tick.
     */
    public static void serverTick(ServerPlayer p) {
        StanceState s = AhfStance.get(p);
        if (s == null) {
            s = p.getData(AhfStance.TYPE);
            s.dirty = 0;
        }
        s.intents = 0;
        boolean suspended = suspended(p);
        if (suspended && s.hasTargets()) {
            s.clearTargets();
        }
        s.ease(LEAN_RATE, ADS_RATE, STEP_RATE, SIDE_RATE);
        s.stepRoll();
        s.bodyYawO = s.bodyYaw;
        if (suspended) {
            s.bodyYaw = p.yBodyRot;
        } else {
            s.bodyYaw = stepBodyYaw(s, p.getYRot(), s.walkSpeed > MOVING_SPEED);
            writeYaw(p, s);
        }
        resolvePose(p, s);
        if (!suspended && Math.abs(Mth.wrapDegrees(s.bodyYaw - s.sentBodyYaw)) > BODY_YAW_SYNC_EPSILON) {
            s.dirty |= StanceState.YAW;
        }
        s.stepWalk(p.getX(), p.getZ());
        flush(p, s);
    }

    /**
     * Client tick at {@code PlayerTickEvent.Post}: owner predicts, remote trackers apply at view time. Suspended =>
     * vanilla (or the vehicle) owns {@code yBodyRot}; followed, not written.
     */
    public static void clientTick(Player p) {
        StanceState s = AhfStance.get(p);
        if (s == null) {
            return;
        }
        boolean suspended = suspended(p);
        s.bodyYawO = s.bodyYaw;
        if (p.isLocalPlayer()) {
            s.bodyYaw = stepBodyYaw(s, p.getYRot(), p.walkAnimation.speed() > MOVING_SPEED);
        } else {
            float speedO = s.walkSpeed;
            s.applyDue(p.level().getGameTime() - AhfConfig.lagCompInterpTicks());
            s.reckonWalk();
            if (s.walkSynced) {
                // Vanilla's own update (from the lerped position) ran in this tick: replaced by the server's walk.
                WalkAnimationAccess w = (WalkAnimationAccess) p.walkAnimation;
                w.ahf$setSpeedOld(speedO);
                w.ahf$setSpeed(s.walkSpeed);
                w.ahf$setPosition(s.walkPos);
            }
        }
        s.ease(LEAN_RATE, ADS_RATE, STEP_RATE, SIDE_RATE);
        s.stepRoll();
        if (suspended) {
            s.bodyYaw = p.yBodyRot;
        } else {
            writeYaw(p, s);
        }
        if (p.isLocalPlayer() && s.rolling()) {
            rollMotion(p, s);
        }
        resolvePose(p, s);
    }

    /** Roll start: PRONE (not crawl / DOWNED), not rolling, dir +-1. */
    static boolean rollValid(Player p, StanceState s, int dir) {
        return Math.abs(dir) == 1 && !s.rolling() && s.effective() == Posture.PRONE && s.posture == Posture.PRONE
                && !suspended(p);
    }

    /** Owner: next tick's lateral velocity, body right ({@code dir} +1) of the body yaw; vertical kept. */
    static void rollMotion(Player p, StanceState s) {
        double yaw = Math.toRadians(s.bodyYaw);
        double v = ROLL_DISTANCE / ROLL_TICKS * s.rollDir;
        p.setDeltaMovement(-Math.cos(yaw) * v, p.getDeltaMovement().y, -Math.sin(yaw) * v);
    }

    /** C2S roll: started or refused; synced at the next {@link #serverTick}. */
    public static void handleRoll(ServerPlayer p, int dir) {
        StanceState s = p.getData(AhfStance.TYPE);
        if (++s.intents <= MAX_INTENTS_PER_TICK && rollValid(p, s, dir)) {
            s.startRoll(dir);
        }
        s.dirty |= StanceState.TARGETS;
    }

    /** Owner prediction; false => refused locally. */
    public static boolean predictRoll(Player owner, int dir) {
        StanceState s = owner.getData(AhfStance.TYPE);
        if (!rollValid(owner, s, dir)) {
            return false;
        }
        s.startRoll(dir);
        return true;
    }

    /** Server, after vanilla's {@code tickHeadTurn} rewrote the body yaw. */
    public static void afterPlayerTick(Player p) {
        StanceState s = AhfStance.get(p);
        if (s != null && !suspended(p)) {
            writeYaw(p, s);
        }
    }

    static void flush(ServerPlayer p, StanceState s) {
        if (s.dirty == 0) {
            return;
        }
        s.stamp = EntityHistory.broadcastTick((ServerLevel) p.level());
        s.sentBodyYaw = s.bodyYaw;
        if ((s.dirty & StanceState.WALK) != 0) {
            s.walkSent();
        }
        p.syncData(AhfStance.TYPE);
        s.dirty = 0;
    }

    /** C2S intent: validate, apply or reject, always echo {@code seq}; synced at the next {@link #serverTick}. */
    public static void handleIntent(ServerPlayer p, int seq, StanceRequest r) {
        StanceState s = p.getData(AhfStance.TYPE);
        StanceRequest ok = ++s.intents > MAX_INTENTS_PER_TICK ? null : accept(p, s, r);
        if (ok != null) {
            s.applyTargets(ok);
        }
        s.seq = seq;
        s.dirty |= StanceState.TARGETS;
        resolvePose(p, s);
    }

    /** Owner prediction: applied locally at once; returns the seq to send, or -1 when refused locally. */
    public static int predict(Player owner, StanceRequest r) {
        StanceState s = owner.getData(AhfStance.TYPE);
        StanceRequest ok = accept(owner, s, r);
        if (ok == null) {
            return -1;
        }
        s.seq++;
        s.applyTargets(ok);
        s.dirty = 0;
        resolvePose(owner, s);
        return s.seq;
    }

    static boolean serverRequest(ServerPlayer p, StanceRequest r) {
        StanceState s = p.getData(AhfStance.TYPE);
        if (!validate(p, s, r)) {
            return false;
        }
        s.applyTargets(r);
        resolvePose(p, s);
        return true;
    }

    static void setLock(ServerPlayer p, boolean downed, boolean value) {
        StanceState s = p.getData(AhfStance.TYPE);
        if (downed) {
            s.downed = value;
            if (value && s.ads) {
                s.ads = false;
                s.dirty |= StanceState.TARGETS;
            }
        } else {
            s.crawl = value;
        }
        s.dirty |= StanceState.LOCKS;
        if (!value && !fits(p, s.effective(), s.effectiveLevel())) {
            s.posture = Posture.PRONE;
            s.level = 0;
            s.side = 0;
            s.dirty |= StanceState.TARGETS;
        }
        if (s.effective().lying() && s.leanTarget != 0.0F) {
            s.leanTarget = 0.0F;
            s.dirty |= StanceState.TARGETS;
        }
        resolvePose(p, s);
    }

    static void onDeath(ServerPlayer p) {
        StanceState s = AhfStance.get(p);
        if (s == null) {
            return;
        }
        s.reset();
        resolvePose(p, s);
    }
}
