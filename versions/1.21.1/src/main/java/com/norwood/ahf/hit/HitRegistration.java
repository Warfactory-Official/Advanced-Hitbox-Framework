package com.norwood.ahf.hit;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.config.HitRegMode;
import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.rig.RigTuning;
import com.norwood.ahf.stance.AhfStances;
import com.norwood.ahf.stance.Stance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

public final class HitRegistration {

    private HitRegistration() {
    }

    public static boolean isEnvelopeTarget(Entity entity) {
        return AhfHooks.isEnvelopeTarget(entity);
    }

    public static AABB registrationBox(Entity entity) {
        AABB box = entity.getBoundingBox();
        if (AhfConfig.hitRegistrationMode() == HitRegMode.OFF) {
            return box;
        }
        if (!isEnvelopeTarget(entity) || !(entity instanceof LivingEntity living)) {
            return box;
        }
        Stance activeStance = AhfStances.active(living);
        double h;
        double v;
        if (activeStance != null) {
            h = activeStance.envelopeReachHorizontal();
            v = activeStance.envelopeReachVertical();
        } else {
            RigTuning.RigPose pose = HumanoidRig.resolvePose(living);
            if (RigTuning.ACTIVE) {
                h = RigTuning.envReach(pose, RigTuning.EnvAxis.HORIZONTAL);
                v = RigTuning.envReach(pose, RigTuning.EnvAxis.VERTICAL);
            } else {
                h = AhfConfig.hitEnvelopeReachHorizontal(pose);
                v = AhfConfig.hitEnvelopeReachVertical(pose);
            }
        }
        if (h <= 0.0 && v <= 0.0) {
            return box;
        }
        return box.inflate(h, v, h);
    }
}
