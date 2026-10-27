package com.norwood.ahf.hook;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class AhfHooks {

    @SuppressWarnings("unchecked")
    private static volatile Predicate<ItemStack>[] heldGun = new Predicate[0];
    @SuppressWarnings("unchecked")
    private static volatile Predicate<Entity>[] envelopeTarget = new Predicate[0];
    private static volatile AimProvider[] gunAimProgress = new AimProvider[0];
    @SuppressWarnings("unchecked")
    private static volatile Function<LivingEntity, ResourceLocation>[] poseSet = new Function[0];
    private static volatile HandTargets[] handTargets = new HandTargets[0];
    private static volatile HeldOutline[] heldOutline = new HeldOutline[0];

    private AhfHooks() {
    }

    /** Any registered predicate true => gun. */
    public static synchronized void addHeldGun(Predicate<ItemStack> predicate) {
        heldGun = append(heldGun, predicate);
    }

    /** Any registered predicate true => envelope target. Players always are. */
    public static synchronized void addEnvelopeTarget(Predicate<Entity> predicate) {
        envelopeTarget = append(envelopeTarget, predicate);
    }

    /**
     * Aim progress in [0, 1] (0 = hold, 1 = ADS), or NaN for no opinion. Highest priority non-NaN answer wins;
     * all NaN => 0. Equal priorities: first registered wins.
     */
    public static synchronized void addGunAimProgress(int priority, ToDoubleFunction<LivingEntity> provider) {
        AimProvider[] next = Arrays.copyOf(gunAimProgress, gunAimProgress.length + 1);
        next[next.length - 1] = new AimProvider(priority, provider);
        Arrays.sort(next, Comparator.comparingInt(AimProvider::priority).reversed());
        gunAimProgress = next;
    }

    /**
     * {@code PoseLibrary} set posing a gun holder (rig + the owner's render layers), or null for no opinion. First
     * non-null answer wins; all null => procedural gun pose.
     */
    public static synchronized void addPoseSet(Function<LivingEntity, ResourceLocation> provider) {
        poseSet = append(poseSet, provider);
    }

    public static ResourceLocation poseSet(LivingEntity e) {
        for (Function<LivingEntity, ResourceLocation> f : poseSet) {
            ResourceLocation id = f.apply(e);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /**
     * Rig hand targets: arms solved to them ({@link com.norwood.ahf.rig.ArmSolver}) in place of the posed arms.
     * First non-zero answer wins.
     */
    public static synchronized void addHandTargets(HandTargets provider) {
        handTargets = append(handTargets, provider);
    }

    public static boolean hasHandTargets() {
        return handTargets.length > 0;
    }

    /** {@link HandTargets#targets} of the first provider with an opinion; 0 => none. */
    public static int handTargets(LivingEntity e, HandFrame frame, long tick, Vector3f right, Vector3f left) {
        for (HandTargets t : handTargets) {
            int mask = t.targets(e, frame, tick, right, left);
            if (mask != 0) {
                return mask;
            }
        }
        return 0;
    }

    /**
     * Held item outline: kept above the floor in posture transitions / prone by pitching the arms up
     * ({@code StancePose} floor contact). First non-zero answer wins.
     */
    public static synchronized void addHeldOutline(HeldOutline provider) {
        heldOutline = append(heldOutline, provider);
    }

    /** {@link HeldOutline#points} of the first provider with an opinion; 0 => none. */
    public static int heldOutline(LivingEntity e, HandFrame frame, float[] out) {
        for (HeldOutline h : heldOutline) {
            int n = h.points(e, frame, out);
            if (n != 0) {
                return n;
            }
        }
        return 0;
    }

    @FunctionalInterface
    public interface HeldOutline {
        /** Points written (x, y, z each), vanilla model space as {@link HandTargets#targets}; at most {@link #MAX}. */
        int points(LivingEntity e, HandFrame frame, float[] out);

        int MAX = 16;
    }

    @FunctionalInterface
    public interface HandTargets {
        /**
         * Wrist targets, vanilla model space (blocks, +Y down, -Z forward); bit {@code 1 << arm.ordinal()} per target
         * written, 0 = no opinion. {@code tick}: game tick the rig is for (history key on capture).
         */
        int targets(LivingEntity e, HandFrame frame, long tick, Vector3f right, Vector3f left);
    }

    /** Held item frame of the rig being computed. */
    public interface HandFrame {
        /**
         * {@code PlayerModel.translateToHand(arm)} (AHF: waist + elbow bends) x {@code ItemInHandLayer} x PlayerAnimator
         * item channel (posed, pre-solve arm): item -> model space, blocks.
         */
        Matrix4f heldItem(HumanoidArm arm, Matrix4f out);

        /** Slim (3 px) arms: skin model of the profile's textures. */
        boolean slim();
    }

    public static boolean isHeldGun(ItemStack stack) {
        for (Predicate<ItemStack> p : heldGun) {
            if (p.test(stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEnvelopeTarget(Entity e) {
        if (e instanceof Player) {
            return true;
        }
        for (Predicate<Entity> p : envelopeTarget) {
            if (p.test(e)) {
                return true;
            }
        }
        return false;
    }

    public static float gunAimProgress(LivingEntity e) {
        for (AimProvider p : gunAimProgress) {
            double v = p.provider.applyAsDouble(e);
            if (!Double.isNaN(v)) {
                return (float) v;
            }
        }
        return 0.0F;
    }

    private static <T> T[] append(T[] a, T v) {
        T[] next = Arrays.copyOf(a, a.length + 1);
        next[a.length] = v;
        return next;
    }

    private record AimProvider(int priority, ToDoubleFunction<LivingEntity> provider) {
    }
}
