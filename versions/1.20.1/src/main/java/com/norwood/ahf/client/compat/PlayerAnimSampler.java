package com.norwood.ahf.client.compat;

import com.norwood.ahf.pose.PoseClip;
import com.norwood.ahf.rig.HumanoidRig;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class PlayerAnimSampler implements HumanoidRig.AnimSampler {

    private PlayerAnimSampler() {
    }

    public static void register() {
        HumanoidRig.setAnimSampler(new PlayerAnimSampler());
    }

    @Override
    public double[] sample(LivingEntity entity, String bone, double[] in) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return null;
        }
        AnimationApplier anim = ((IAnimatedPlayer) player).getAnimation();
        StanceAnimLayer.sampling = true;
        try {
            if (!anim.isActive()) {
                return null;
            }
            Vec3f pos = anim.get3DTransform(bone, TransformType.POSITION,
                    new Vec3f((float) in[0], (float) in[1], (float) in[2]));
            Vec3f rot = anim.get3DTransform(bone, TransformType.ROTATION,
                    new Vec3f((float) in[3], (float) in[4], (float) in[5]));
            Vec3f bend = anim.get3DTransform(bone, TransformType.BEND,
                    new Vec3f((float) in[PoseClip.BEND_AXIS], (float) in[PoseClip.BEND], 0));
            return new double[]{pos.getX(), pos.getY(), pos.getZ(), rot.getX(), rot.getY(), rot.getZ(), bend.getX(),
                    bend.getY()};
        } finally {
            StanceAnimLayer.sampling = false;
        }
    }

    @Override
    public boolean body(LivingEntity entity, double[] out) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return false;
        }
        AnimationApplier anim = ((IAnimatedPlayer) player).getAnimation();
        StanceAnimLayer.sampling = true;
        try {
            if (!anim.isActive()) {
                return false;
            }
            Vec3f pos = anim.get3DTransform("body", TransformType.POSITION, Vec3f.ZERO);
            Vec3f rot = anim.get3DTransform("body", TransformType.ROTATION, Vec3f.ZERO);
            out[0] = pos.getX();
            out[1] = pos.getY();
            out[2] = pos.getZ();
            out[3] = rot.getX();
            out[4] = rot.getY();
            out[5] = rot.getZ();
            Vec3f bend = anim.get3DTransform("body", TransformType.BEND, Vec3f.ZERO);
            out[PoseClip.BEND_AXIS] = bend.getX();
            out[PoseClip.BEND] = bend.getY();
            return true;
        } finally {
            StanceAnimLayer.sampling = false;
        }
    }
}
