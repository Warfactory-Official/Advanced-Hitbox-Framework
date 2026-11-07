package com.norwood.ahf.mixin;

import com.norwood.ahf.client.body.BodyRenderer;
import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.pose.PoseClip;
import com.norwood.ahf.rig.HumanoidRig;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@link AhfHooks.RigPose} over {@code setupAnim}. Priority > PlayerAnimator's: runs after its RETURN. */
@Mixin(value = PlayerModel.class, priority = 1100)
public abstract class PlayerModelRigPoseMixin<T extends LivingEntity> extends HumanoidModel<T> {

    @Unique
    private static final double[][] ahf$POSE = new double[PoseClip.LEFT_LEG + 1][PoseClip.CHANNELS];
    @Unique
    private static final float[] ahf$BEND = new float[BodyRenderer.BENDS * 2];

    @Shadow
    @Final
    public ModelPart leftSleeve;
    @Shadow
    @Final
    public ModelPart rightSleeve;
    @Shadow
    @Final
    public ModelPart leftPants;
    @Shadow
    @Final
    public ModelPart rightPants;
    @Shadow
    @Final
    public ModelPart jacket;

    private PlayerModelRigPoseMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void ahf$rigPose(T e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                             float headPitch, CallbackInfo ci) {
        PlayerModel<?> self = (PlayerModel<?>) (Object) this;
        float pt = ageInTicks - e.tickCount;
        double w = !AhfHooks.rigPosed(e) ? 0.0 : AhfHooks.rigPose(e, pt, Mth.lerp(pt, e.xOld, e.getX()),
                Mth.lerp(pt, e.yOld, e.getY()), Mth.lerp(pt, e.zOld, e.getZ()),
                Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot), ahf$POSE);
        if (w <= 0.0) {
            BodyRenderer.setPoseBends(null, ahf$BEND);
            return;
        }
        BodyRenderer.sourceBends(self, ahf$BEND);
        ahf$bone(this.head, ahf$POSE[PoseClip.HEAD], w, -1);
        ahf$bone(this.body, ahf$POSE[PoseClip.TORSO], w, BodyRenderer.WAIST);
        ahf$bone(this.rightArm, ahf$POSE[PoseClip.RIGHT_ARM], w, BodyRenderer.RIGHT_ARM);
        ahf$bone(this.leftArm, ahf$POSE[PoseClip.LEFT_ARM], w, BodyRenderer.LEFT_ARM);
        ahf$bone(this.rightLeg, ahf$POSE[PoseClip.RIGHT_LEG], w, BodyRenderer.RIGHT_LEG);
        ahf$bone(this.leftLeg, ahf$POSE[PoseClip.LEFT_LEG], w, BodyRenderer.LEFT_LEG);
        BodyRenderer.setPoseBends(self, ahf$BEND);
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
    }

    @Unique
    private static void ahf$bone(ModelPart p, double[] to, double w, int slot) {
        if (w >= 1.0) {
            p.x = (float) to[0];
            p.y = (float) to[1];
            p.z = (float) to[2];
            p.xRot = (float) to[3];
            p.yRot = (float) to[4];
            p.zRot = (float) to[5];
            if (slot >= 0) {
                ahf$BEND[slot * 2] = (float) to[PoseClip.BEND_AXIS];
                ahf$BEND[slot * 2 + 1] = (float) to[PoseClip.BEND];
            }
            return;
        }
        float t = (float) w;
        p.x += ((float) to[0] - p.x) * t;
        p.y += ((float) to[1] - p.y) * t;
        p.z += ((float) to[2] - p.z) * t;
        double[] angles = HumanoidRig.zyx(new Matrix3d().set(new Quaterniond().rotationZYX(p.zRot, p.yRot, p.xRot)
                .slerp(new Quaterniond().rotationZYX(to[5], to[4], to[3]), w)));
        p.xRot = (float) angles[0];
        p.yRot = (float) angles[1];
        p.zRot = (float) angles[2];
        if (slot >= 0) {
            double axis = ahf$BEND[slot * 2], bend = ahf$BEND[slot * 2 + 1];
            double fromX = Math.cos(axis) * bend, fromZ = Math.sin(axis) * bend;
            double toX = Math.cos(to[PoseClip.BEND_AXIS]) * to[PoseClip.BEND];
            double toZ = Math.sin(to[PoseClip.BEND_AXIS]) * to[PoseClip.BEND];
            double x = fromX + (toX - fromX) * w, z = fromZ + (toZ - fromZ) * w;
            ahf$BEND[slot * 2] = (float) Math.atan2(z, x);
            ahf$BEND[slot * 2 + 1] = (float) Math.hypot(x, z);
        }
    }
}
