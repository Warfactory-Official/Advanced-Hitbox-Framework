package com.norwood.ahf.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.norwood.ahf.stance.AhfStance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lean eye offset on both {@code getEyePosition} overloads; {@code getEyeY} untouched. */
@Mixin(Entity.class)
public abstract class EntityEyeMixin {

    @ModifyReturnValue(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
    private Vec3 ahf$leanEye(Vec3 eye) {
        return (Object) this instanceof Player p ? AhfStance.leanEye(p, eye, 1.0F) : eye;
    }

    @ModifyReturnValue(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
    private Vec3 ahf$leanEyePartial(Vec3 eye, float partialTick) {
        return (Object) this instanceof Player p ? AhfStance.leanEye(p, eye, partialTick) : eye;
    }

    /** Suffocation box (0.8 width around the eye) on the unleaned eye: margin 0.1 < 0.24 => wall damage. */
    @WrapOperation(method = "isInWall", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 ahf$unleanedWallEye(Entity self, Operation<Vec3> original) {
        return self instanceof Player ? new Vec3(self.getX(), self.getEyeY(), self.getZ()) : original.call(self);
    }
}
