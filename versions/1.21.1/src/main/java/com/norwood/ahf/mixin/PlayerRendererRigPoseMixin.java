package com.norwood.ahf.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.pose.PoseClip;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Full {@link AhfHooks.RigPose}: yaw only; death flip, swim tilt, PlayerAnimator {@code body} are in the pose. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererRigPoseMixin {

    @Unique
    private static final double[][] ahf$POSE = new double[PoseClip.LEFT_LEG + 1][PoseClip.CHANNELS];

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("HEAD"), cancellable = true)
    private void ahf$yawOnly(AbstractClientPlayer e, PoseStack pose, float bob, float bodyYaw, float pt, float scale,
                             CallbackInfo ci) {
        if (AhfHooks.rigPosed(e) && AhfHooks.rigPose(e, pt, Mth.lerp(pt, e.xOld, e.getX()),
                Mth.lerp(pt, e.yOld, e.getY()), Mth.lerp(pt, e.zOld, e.getZ()), bodyYaw, ahf$POSE) >= 1.0) {
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            ci.cancel();
        }
    }
}
