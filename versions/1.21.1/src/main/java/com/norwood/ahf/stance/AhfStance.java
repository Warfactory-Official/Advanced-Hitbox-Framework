package com.norwood.ahf.stance;

import com.norwood.ahf.Ahf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Stance API. Mechanism here; rules via {@link #addRule}. Server writes go through {@link #request},
 * {@link #setDowned}, {@link #setCrawl}; the owning client through {@code StanceClient.request}.
 */
public final class AhfStance {

    /** Lean clamp: eye stays this far short of the first collider. */
    public static final double LEAN_CLAMP_MARGIN = 0.1;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Ahf.MOD_ID);

    /** Not {@code copyOnDeath}; not serialized. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<StanceState>> TYPE = ATTACHMENTS.register(
            "stance", () -> AttachmentType.builder(StanceState::create).sync(new StanceSync()).build());

    private static volatile StanceRule[] rules = new StanceRule[0];

    private AhfStance() {
    }

    public static synchronized void addRule(StanceRule rule) {
        StanceRule[] next = Arrays.copyOf(rules, rules.length + 1);
        next[rules.length] = rule;
        rules = next;
    }

    static StanceRule[] rules() {
        return rules;
    }

    /** Existing state or null; never creates (creation syncs). */
    public static @Nullable StanceState get(Player p) {
        return p.getExistingDataOrNull(TYPE);
    }

    /** State, created when absent (creation syncs). */
    public static StanceState getOrCreate(Player p) {
        return p.getData(TYPE);
    }

    public static Posture posture(Player p) {
        StanceState s = get(p);
        return s == null ? Posture.STAND : s.effective();
    }

    /** Gates fire, reload, ADS, co-op, stance requests. */
    public static boolean weaponsBlocked(Player p) {
        StanceState s = get(p);
        return s != null && s.downed;
    }

    /** Server-side request; same validation as a client intent. */
    public static boolean request(ServerPlayer p, StanceRequest r) {
        return StanceLogic.serverRequest(p, r);
    }

    /** DOWNED lock (WFMedical); overrides every request until released. */
    public static void setDowned(ServerPlayer p, boolean downed) {
        StanceLogic.setLock(p, true, downed);
    }

    /** Prone roll toward {@code dir} (+1 right): server start; same rule as the owner's {@code StanceClient.roll}. */
    public static boolean roll(ServerPlayer p, int dir) {
        StanceState s = p.getData(TYPE);
        if (!StanceLogic.rollValid(p, s, dir)) {
            return false;
        }
        s.startRoll(dir);
        s.dirty |= StanceState.TARGETS;
        return true;
    }

    /** External crawl request (WFMedical leg crawl): PRONE shape below DOWNED, above requests. */
    public static void setCrawl(ServerPlayer p, boolean crawl) {
        StanceLogic.setLock(p, false, crawl);
    }

    /** World-space eye displacement from lean, clamped against colliders; ZERO without lean. */
    public static Vec3 eyeOffset(Player p, float partialTick) {
        Vec3 eye = new Vec3(Mth.lerp(partialTick, p.xo, p.getX()),
                Mth.lerp(partialTick, p.yo, p.getY()) + p.getEyeHeight(), Mth.lerp(partialTick, p.zo, p.getZ()));
        return eyeOffset(p, partialTick, eye);
    }

    /** {@link #eyeOffset(Player, float)} with the unleaned eye {@code base} already known. */
    public static Vec3 eyeOffset(Player p, float partialTick, Vec3 base) {
        StancePose pose = StancePose.of(p, partialTick);
        if (pose == null || pose.roll == 0.0 && pose.shift() == 0.0) {
            return Vec3.ZERO;
        }
        StanceState s = get(p);
        Vec3 off = StancePose.localToWorld(pose.eyeOffsetLocal(p.getEyeHeight()), s.bodyYaw(partialTick));
        return clamp(p, base, off);
    }

    private static Vec3 clamp(Player p, Vec3 base, Vec3 off) {
        double len = off.length();
        if (len < 1.0E-9) {
            return Vec3.ZERO;
        }
        Vec3 dir = off.scale(1.0 / len);
        BlockHitResult hit = p.level().clip(new ClipContext(base, base.add(dir.scale(len + LEAN_CLAMP_MARGIN)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS) {
            return off;
        }
        double allowed = Math.max(0.0, hit.getLocation().distanceTo(base) - LEAN_CLAMP_MARGIN);
        return allowed >= len ? off : dir.scale(allowed);
    }

    /** Leaned eye for the {@code getEyePosition} mixins. */
    public static Vec3 leanEye(Player p, Vec3 eye, float partialTick) {
        Vec3 off = eyeOffset(p, partialTick, eye);
        return off == Vec3.ZERO ? eye : eye.add(off);
    }

    /** First-person camera roll / body roll (Insurgency Sandstorm, Arma Reforger: camera rolls well under the body). */
    public static final float CAMERA_ROLL_FRACTION = 0.4F;

    /** First-person camera roll, degrees; right lean => positive. */
    public static float cameraRoll(Player p, float partialTick) {
        StancePose pose = StancePose.of(p, partialTick);
        return pose == null ? 0.0F
                : (float) (-Math.toDegrees(pose.viewRoll()) * CAMERA_ROLL_FRACTION + Math.toDegrees(pose.rollTurn));
    }

    /** Eased ADS in [0, 1]; NaN when no state or not aiming (no opinion for {@code AhfHooks.gunAimProgress}). */
    public static double adsProgress(Player p) {
        StanceState s = get(p);
        return s == null || s.adsProgress == 0.0F ? Double.NaN : s.adsProgress;
    }
}
