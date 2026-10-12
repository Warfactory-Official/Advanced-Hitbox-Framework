package com.norwood.ahf.mixin;

import com.norwood.ahf.lagcomp.EntityHistory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Every server-side player reposition: commands, pearls, same-level respawn/teleportTo, dismount, and move
 * corrections (kept: short). 26.1: {@code rel} makes {@code destination} relative.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerTeleportMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"))
    private void ahf$dropHistory(PositionMoveRotation destination, Set<Relative> rel, CallbackInfo ci) {
        Vec3 to = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(player), destination, rel).position();
        EntityHistory.onTeleport(player, null, to.x, to.y, to.z);
    }
}
