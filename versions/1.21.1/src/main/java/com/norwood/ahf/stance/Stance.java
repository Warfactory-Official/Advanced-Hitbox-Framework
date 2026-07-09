package com.norwood.ahf.stance;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.rig.HumanoidRig;
import net.minecraft.world.entity.LivingEntity;

public interface Stance {

    String id();

    default int priority() {
        return 0;
    }

    boolean appliesTo(LivingEntity entity);

    default boolean upright() {
        return false;
    }

    default double envelopeReachHorizontal() {
        return AhfConfig.stanceEnvelopeReachHorizontal();
    }

    default double envelopeReachVertical() {
        return AhfConfig.stanceEnvelopeReachVertical();
    }

    void apply(LivingEntity entity, HumanoidRig.LocalRig rig);
}
