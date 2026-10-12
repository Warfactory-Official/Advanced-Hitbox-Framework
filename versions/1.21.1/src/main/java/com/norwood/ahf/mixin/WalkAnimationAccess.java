package com.norwood.ahf.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface WalkAnimationAccess {

    @Accessor("speedOld")
    void ahf$setSpeedOld(float v);

    @Accessor("speed")
    void ahf$setSpeed(float v);

    @Accessor("position")
    void ahf$setPosition(float v);
}
