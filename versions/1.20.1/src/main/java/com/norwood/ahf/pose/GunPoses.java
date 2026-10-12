package com.norwood.ahf.pose;

import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.lagcomp.EntityHistory;
import com.norwood.ahf.stance.AhfStance;
import com.norwood.ahf.stance.Posture;
import com.norwood.ahf.stance.StanceState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Gun-hold clip selection (TACZ {@code AnimationManager} + {@code AdjustmentYRotModifier}), shared by the rig
 * ({@code HumanoidRig}: loop clips at rest frame) and the render layers of the mod owning the pose set (full clips).
 * Divergences from TACZ: KNEEL => no lower clip (AHF kneel legs); lying = AHF PRONE only; a clip missing from the set
 * falls back along {@link #fallback} instead of leaving the previous one playing.
 */
public final class GunPoses {

    public static final String EMPTY = "empty";
    private static final float MOVING = 0.05F;
    private static final float YAW_CLAMP = 85.0F;
    /** Vanilla limb cycle, walk-position units ({@code cos(pos * 0.6662)}). */
    public static final double STRIDE = 2.0 * Math.PI / 0.6662;
    /** TACZ layer fade, ticks. */
    public static final int FADE_TICKS = 8;

    /** One-shot upper-body clips (TACZ {@code playOnceAnimation}). */
    public enum Once {
        FIRE, RELOAD
    }

    /** Server: one-shot clip per entity, started at a history key ({@code EntityHistory.broadcastTick}). Server thread. */
    private static final Map<LivingEntity, Playing> ONCE = new WeakHashMap<>();

    private record Playing(ResourceLocation set, String clip, long start) {
    }

    private GunPoses() {
    }

    /** Clips of one moment; {@code lower} null => none (lying, KNEEL, or absent from the set). */
    public record Selection(ResourceLocation set, @Nullable String lower, @Nullable String upper, boolean lying) {
    }

    /** Null => no gun pose: no set for the held item, or sleeping / climbing / swimming / elytra. */
    @Nullable
    public static Selection select(LivingEntity e, float limbSwingAmount) {
        ResourceLocation set = AhfHooks.poseSet(e);
        if (set == null || e.getPose() == Pose.SLEEPING || e.onClimbable() || e.isSwimming()
                || e.getPose() == Pose.FALL_FLYING) {
            return null;
        }
        boolean flying = e instanceof Player p && !p.onGround() && p.getAbilities().flying;
        boolean aiming = AhfHooks.gunAimProgress(e) > 0.0F;
        boolean moving = !flying && limbSwingAmount > MOVING;
        boolean sprint = !flying && e.isSprinting();
        Posture posture = e instanceof Player p ? AhfStance.posture(p) : Posture.STAND;
        boolean lying = posture == Posture.PRONE && e.getPose() == Pose.SWIMMING;
        if (lying) {
            String upper = aiming ? moving ? "lie_move" : "lie_aim" : sprint || moving ? "lie_move" : "lie";
            upper = resolve(set, upper);
            return upper == null ? null : new Selection(set, null, upper, true);
        }
        boolean crouch = e.getPose() == Pose.CROUCHING;
        String lower;
        if (posture == Posture.KNEEL) {
            lower = null;
        } else if (e.getVehicle() != null) {
            lower = "ride_lower";
        } else if (flying) {
            lower = "hold_lower";
        } else if (sprint || moving) {
            lower = crouch ? "crouch_walk_lower" : sprint ? "run_lower" : "walk_lower";
        } else {
            lower = crouch ? "crouch_lower" : "hold_lower";
        }
        String upper = aiming ? "aim_upper"
                : sprint || moving ? crouch ? "crouch_walk_upper" : sprint ? "run_upper" : "walk_upper"
                : "hold_upper";
        return new Selection(set, lower == null ? null : resolve(set, lower), resolve(set, upper), false);
    }

    /** {@code name} or its first present fallback; null => none. */
    @Nullable
    public static String resolve(ResourceLocation set, String name) {
        for (String n = name; n != null; n = fallback(n)) {
            if (PoseLibrary.clip(set, n) != null) {
                return n;
            }
        }
        return null;
    }

    @Nullable
    static String fallback(String name) {
        return switch (name) {
            case "crouch_walk_upper", "run_upper" -> "walk_upper";
            case "walk_upper", "aim_upper" -> "hold_upper";
            case "crouch_walk_lower" -> "crouch_lower";
            case "run_lower" -> "walk_lower";
            case "walk_lower", "crouch_lower", "ride_lower" -> "hold_lower";
            case "lie_move", "lie_aim" -> "lie";
            default -> null;
        };
    }

    /**
     * Moving loops (strides) play by walk position on both sides, one loop per vanilla limb cycle: the synced walk
     * position keeps the drawn stride and the server rig in phase. Other loops play by time on the render, rest frame
     * on the rig.
     */
    public static boolean phased(String clip) {
        return clip.contains("walk") || clip.startsWith("run") || clip.equals("lie_move");
    }

    /** Clip time, ticks, of a phased clip at walk position {@code walkPos}. */
    public static double phaseTicks(PoseClip clip, double walkPos) {
        double f = walkPos / STRIDE;
        f -= Math.floor(f);
        int from = clip.looped ? clip.returnTick : 0;
        return from + f * (clip.endTick - from + 1);
    }

    /** TACZ one-shot clip name for {@code kind} in {@code sel}; null => none. */
    @Nullable
    public static String onceClip(Selection sel, boolean aiming, Once kind) {
        return switch (kind) {
            case FIRE -> sel.lying() ? aiming ? "lie_aim_fire" : "lie_normal_fire"
                    : aiming ? "aim_fire_upper" : "normal_fire_upper";
            case RELOAD -> sel.lying() ? "lie_reload" : "reload_upper";
        };
    }

    /**
     * Server: {@code kind} starts on {@code e}'s rig now; the same clip still playing => ignored (TACZ), another =>
     * replaced (reload over a fire kick). The render does the same at view time.
     */
    public static void playOnce(LivingEntity e, Once kind) {
        if (!(e.level() instanceof ServerLevel level)) {
            return;
        }
        long now = EntityHistory.broadcastTick(level);
        Selection sel = select(e, Math.min(walkSpeed(e), 1.0F));
        String name = sel == null ? null : onceClip(sel, AhfHooks.gunAimProgress(e) > 0.0F, kind);
        PoseClip clip = name == null ? null : PoseLibrary.clip(sel.set(), name);
        if (clip == null) {
            return;
        }
        Playing cur = ONCE.get(e);
        if (cur != null && cur.set.equals(sel.set()) && cur.clip.equals(name) && now - cur.start <= clip.stopTick) {
            return;
        }
        ONCE.put(e, new Playing(sel.set(), name, now));
    }

    /** Server rig: the playing one-shot over {@code parts} + {@code whole}, faded in as the render. */
    public static void applyOnce(LivingEntity e, double[][] parts, double[] whole) {
        Playing cur = e.level() instanceof ServerLevel ? ONCE.get(e) : null;
        if (cur == null) {
            return;
        }
        PoseClip c = PoseLibrary.clip(cur.set, cur.clip);
        long t = EntityHistory.broadcastTick((ServerLevel) e.level()) - cur.start;
        if (c == null || t > c.stopTick) {
            ONCE.remove(e);
            return;
        }
        float w = PoseClip.fadeIn(t, FADE_TICKS);
        double[] v = new double[PoseClip.CHANNELS];
        for (int b = 0; b <= PoseClip.BODY; b++) {
            double[] in = b == PoseClip.BODY ? whole : parts[b];
            System.arraycopy(in, 0, v, 0, PoseClip.CHANNELS);
            c.applyAt(b, t, v);
            for (int ch = 0; ch < PoseClip.CHANNELS; ch++) {
                in[ch] += (v[ch] - in[ch]) * w;
            }
        }
    }

    /**
     * Walk speed the rig uses: server players AHF's synced walk (vanilla's is ~0 for a player on a server: the
     * connection resets {@code xo} before the player ticks); else the entity's (remote players follow the synced one).
     */
    public static float walkSpeed(LivingEntity e) {
        if (!e.level().isClientSide && e instanceof Player p) {
            StanceState s = AhfStance.get(p);
            if (s != null) {
                return s.walkSpeed();
            }
        }
        return e.walkAnimation.speed();
    }

    public static float walkPos(LivingEntity e) {
        if (!e.level().isClientSide && e instanceof Player p) {
            StanceState s = AhfStance.get(p);
            if (s != null) {
                return s.walkPos();
            }
        }
        return e.walkAnimation.position();
    }

    /** Head-minus-body yaw, degrees, wrapped and clamped as TACZ. */
    public static float headYaw(LivingEntity e, float pt) {
        float body = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        float head = Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot);
        return Mth.clamp(Mth.wrapDegrees(head - body), -YAW_CLAMP, YAW_CLAMP);
    }

    public static float pitch(LivingEntity e, float pt) {
        return Mth.wrapDegrees(Mth.lerp(pt, e.xRotO, e.getXRot()));
    }

    /**
     * Rotation layer (added after every clip): {@code body} +(0, -yaw, 0) standing, +(0, 0, -yaw) lying, none while
     * riding; {@code head} and both arms +pitch on x. {@code rot} = radians, rewritten in place.
     */
    public static void adjust(LivingEntity e, float pt, boolean lying, int bone, double[] rot) {
        switch (bone) {
            case PoseClip.BODY -> {
                if (e.getVehicle() == null) {
                    double yaw = -headYaw(e, pt) * (Math.PI / 180.0);
                    if (lying) {
                        rot[2] += (float) yaw;
                    } else {
                        rot[1] += (float) yaw;
                    }
                }
            }
            case PoseClip.HEAD, PoseClip.RIGHT_ARM, PoseClip.LEFT_ARM -> rot[0] += (float) (pitch(e, pt) * (Math.PI / 180.0));
            default -> {
            }
        }
    }

    /** Library prone (clip replaces AHF's procedural PRONE legs). */
    public static boolean libraryLying(LivingEntity e) {
        if (!(e instanceof Player p)) {
            return false;
        }
        StanceState s = AhfStance.get(p);
        if (s == null || s.effective() != Posture.PRONE) {
            return false;
        }
        Selection sel = select(e, Math.min(walkSpeed(e), 1.0F));
        return sel != null && sel.lying();
    }
}
