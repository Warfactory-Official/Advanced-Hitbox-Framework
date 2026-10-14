package com.norwood.ahf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.norwood.ahf.stance.StancePose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Stance-owned players: swim tilt = {@link StancePose#lieOf}, shift {@link StancePose#tiltShiftY} / Z (rig frame). */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererTiltMixin {

    @Unique
    private static final String SETUP = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V";

    /** This draw's lie, from the {@code getSwimAmount} read to the translate (NaN = vanilla); render thread. */
    @Unique
    private double ahf$lie = Double.NaN;

    @WrapOperation(method = SETUP,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSwimAmount(F)F"))
    private float ahf$swimAmount(AbstractClientPlayer p, float partialTick, Operation<Float> original) {
        ahf$lie = p.isFallFlying() ? Double.NaN : StancePose.lieOf(p, partialTick);
        return Double.isNaN(ahf$lie) ? original.call(p, partialTick) : (float) ahf$lie;
    }

    @WrapOperation(method = SETUP,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;isVisuallySwimming()Z"))
    private boolean ahf$shifted(AbstractClientPlayer p, Operation<Boolean> original) {
        return !Double.isNaN(ahf$lie) || original.call(p);
    }

    @WrapOperation(method = SETUP,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void ahf$shift(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        double lie = ahf$lie;
        if (Double.isNaN(lie)) {
            original.call(pose, x, y, z);
            return;
        }
        // Rig (y, z) -> the tilted frame vanilla translates in: [[c, -s], [-s, -c]], phi = -tilt (its own inverse).
        double c = Math.cos(-StancePose.tiltAngle(lie));
        double s = Math.sin(-StancePose.tiltAngle(lie));
        double sy = StancePose.tiltShiftY(lie);
        double sz = StancePose.tiltShiftZ(lie);
        original.call(pose, 0.0F, (float) (c * sy - s * sz), (float) (-s * sy - c * sz));
    }
}
