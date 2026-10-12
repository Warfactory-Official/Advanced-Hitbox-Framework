package com.norwood.ahf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.norwood.ahf.stance.AhfStance;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Camera position does not read {@code getEyePosition}: lean offset applied here too. */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void ahf$leanCamera(Camera camera, double x, double y, double z, Operation<Void> original,
                                float partialTick) {
        if (camera.entity() instanceof Player p) {
            Vec3 off = AhfStance.eyeOffset(p, partialTick, new Vec3(x, y, z));
            original.call(camera, x + off.x, y + off.y, z + off.z);
            return;
        }
        original.call(camera, x, y, z);
    }
}
