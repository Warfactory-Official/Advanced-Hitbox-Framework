package com.norwood.ahf.hook;

import net.minecraft.resources.Identifier;
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
    private static volatile Function<LivingEntity, Identifier>[] poseSet = new Function[0];

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
    public static synchronized void addPoseSet(Function<LivingEntity, Identifier> provider) {
        poseSet = append(poseSet, provider);
    }

    public static Identifier poseSet(LivingEntity e) {
        for (Function<LivingEntity, Identifier> f : poseSet) {
            Identifier id = f.apply(e);
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

    private static <T> T[] append(T[] a, T v) {
        T[] next = Arrays.copyOf(a, a.length + 1);
        next[a.length] = v;
        return next;
    }

    private record AimProvider(int priority, ToDoubleFunction<LivingEntity> provider) {
    }
}
