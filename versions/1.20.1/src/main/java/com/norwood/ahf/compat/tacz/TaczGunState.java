package com.norwood.ahf.compat.tacz;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class TaczGunState {

    private TaczGunState() {
    }

    public static float aimingProgress(LivingEntity entity) {
        try {
            IGunOperator operator = IGunOperator.fromLivingEntity(entity);
            if (operator == null) {
                return 0.0F;
            }
            return Mth.clamp(operator.getSynAimingProgress(), 0.0F, 1.0F);
        } catch (Throwable t) {
            return 0.0F;
        }
    }
}
