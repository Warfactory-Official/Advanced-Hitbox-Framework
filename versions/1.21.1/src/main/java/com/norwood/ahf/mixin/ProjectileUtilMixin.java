package com.norwood.ahf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.norwood.ahf.hit.HitRegistration;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Envelope on vanilla picks. Box from the entity itself, not {@code original}: an older {@code @Redirect} on the
 * same call (WFMedical until it delegates to AHF) would otherwise be inflated twice.
 */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilMixin {

    @WrapOperation(
            method = {
                    "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
                    "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB ahf$envelope(Entity entity, Operation<AABB> original, @Share("candidate") LocalRef<Entity> candidate) {
        candidate.set(entity);
        return HitRegistration.registrationBox(entity, entity.getBoundingBox());
    }

    /** Eye-inside-box => distance-0 pick regardless of look; tested on the vanilla box, not the envelope. */
    @WrapOperation(
            method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;contains(Lnet/minecraft/world/phys/Vec3;)Z"))
    private static boolean ahf$eyeInsideVanillaBox(AABB box, Vec3 eye, Operation<Boolean> original,
                                                   @Share("candidate") LocalRef<Entity> candidate) {
        Entity e = candidate.get();
        return original.call(e.getBoundingBox().inflate(e.getPickRadius()), eye);
    }
}
