package com.norwood.ahf.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Player.class)
public interface PlayerInvoker {

    @Invoker("updatePlayerPose")
    void ahf$updatePlayerPose();
}
