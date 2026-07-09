package com.norwood.ahf.hook;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class AhfHooks {

    private static Predicate<ItemStack> heldGunPredicate = stack -> false;
    private static Function<DamageSource, Optional<Vec3>> bulletHitPosFunc = src -> Optional.empty();
    private static Predicate<Entity> envelopeTargetPredicate = e -> e instanceof Player;
    private static Function<LivingEntity, Float> gunAimProgressFunc = e -> 0.0f;

    private AhfHooks() {
    }

    public static boolean isHeldGun(ItemStack stack) {
        return heldGunPredicate.test(stack);
    }

    public static Optional<Vec3> bulletHitPos(DamageSource src) {
        return bulletHitPosFunc.apply(src);
    }

    public static boolean isEnvelopeTarget(Entity e) {
        return envelopeTargetPredicate.test(e);
    }

    public static float gunAimProgress(LivingEntity e) {
        return gunAimProgressFunc.apply(e);
    }

    public static void setHeldGunPredicate(Predicate<ItemStack> predicate) {
        heldGunPredicate = predicate;
    }

    public static void setBulletHitPos(Function<DamageSource, Optional<Vec3>> func) {
        bulletHitPosFunc = func;
    }

    public static void setEnvelopeTargetPredicate(Predicate<Entity> predicate) {
        envelopeTargetPredicate = predicate;
    }

    public static void setGunAimProgress(Function<LivingEntity, Float> func) {
        gunAimProgressFunc = func;
    }
}
