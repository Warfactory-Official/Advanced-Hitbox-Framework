package com.norwood.ahf.client.compat;

import com.norwood.ahf.pose.GunPoses;
import com.norwood.ahf.pose.PoseClip;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.world.entity.LivingEntity;

/** A {@link GunPoses#phased} loop as a PlayerAnimator layer: clip time from the walk position, as the server rig. */
public final class PhasedClip implements IAnimation {

    private final LivingEntity entity;
    private final PoseClip clip;
    private double t;

    public PhasedClip(LivingEntity entity, PoseClip clip) {
        this.entity = entity;
        this.clip = clip;
    }

    public PoseClip clip() {
        return clip;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void setupAnim(float tickDelta) {
        t = GunPoses.phaseTicks(clip, entity.walkAnimation.position(tickDelta));
    }

    @Override
    public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value) {
        int bone = PoseClip.bone(part);
        int ch = switch (type) {
            case POSITION -> 0;
            case ROTATION -> 3;
            case BEND -> PoseClip.BEND_AXIS;
            default -> -1;
        };
        if (bone < 0 || ch < 0) {
            return value;
        }
        if (ch == PoseClip.BEND_AXIS) {
            return new Vec3f(clip.valueAt(bone, ch, t, value.getX()), clip.valueAt(bone, ch + 1, t, value.getY()),
                    value.getZ());
        }
        return new Vec3f(clip.valueAt(bone, ch, t, value.getX()), clip.valueAt(bone, ch + 1, t, value.getY()),
                clip.valueAt(bone, ch + 2, t, value.getZ()));
    }
}
