package com.norwood.ahf.hook;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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

    private static volatile RigPose[] rigPose = new RigPose[0];

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

    /**
     * Whole-body pose owner (physics): bones replace every animation layer, stance and tilt, rig and render alike.
     * First non-zero answer wins.
     */
    public static synchronized void addRigPose(RigPose provider) {
        rigPose = append(rigPose, provider);
    }

    /** A {@link RigPose} owns {@code e} now. */
    public static boolean rigPosed(LivingEntity e) {
        for (RigPose p : rigPose) {
            if (p.active(e)) {
                return true;
            }
        }
        return false;
    }

    /** {@link RigPose#pose} of the first provider with an opinion; 0 => none. */
    public static double rigPose(LivingEntity e, float partialTick, double x, double y, double z, double bodyYaw,
                                 double[][] out) {
        for (RigPose p : rigPose) {
            double w = p.pose(e, partialTick, x, y, z, bodyYaw, out);
            if (w > 0.0) {
                return w;
            }
        }
        return 0.0;
    }

    public interface RigPose {
        boolean active(LivingEntity e);

        /**
         * @param x       feet position at {@code partialTick}, as the renderer's
         * @param bodyYaw degrees at {@code partialTick}, as the renderer's
         * @param out     {@code [PoseClip bone][PoseClip.CHANNELS]}, {@code ModelPart} conventions, bones
         *                {@code HEAD..LEFT_LEG}; expressed for that position and yaw
         * @return weight over the normal pose, 0..1; 0 => {@code out} unread
         */
        double pose(LivingEntity e, float partialTick, double x, double y, double z, double bodyYaw, double[][] out);
    }

    private static <T> T[] append(T[] a, T v) {
        T[] next = Arrays.copyOf(a, a.length + 1);
        next[a.length] = v;
        return next;
    }

    private record AimProvider(int priority, ToDoubleFunction<LivingEntity> provider) {
    }
}
