package com.norwood.ahf.mixin;

import com.norwood.ahf.lagcomp.EntityHistory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Every server-side player reposition: commands, pearls, same-level respawn/teleportTo, dismount, and move
 * corrections (kept: short). {@code x, y, z} absolute; {@code rel} only shapes the packet.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerTeleportMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void ahf$dropHistory(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> rel,
                                 CallbackInfo ci) {
        EntityHistory.onTeleport(player, null, x, y, z);
    }
}
