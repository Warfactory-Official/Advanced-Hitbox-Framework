package com.norwood.ahf.mixin;

import com.norwood.ahf.lagcomp.EntityHistory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/** Non-player teleports. {@code ServerPlayer} overrides both without super => {@link PlayerTeleportMixin}. */
@Mixin(Entity.class)
public abstract class EntityTeleportMixin {

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"))
    private void ahf$dropHistory(double x, double y, double z, CallbackInfo ci) {
        EntityHistory.onTeleport((Entity) (Object) this, null, x, y, z);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"))
    private void ahf$dropHistoryLevel(ServerLevel level, double x, double y, double z, Set<RelativeMovement> rel,
                                      float yRot, float xRot, CallbackInfoReturnable<Boolean> cir) {
        EntityHistory.onTeleport((Entity) (Object) this, level, x, y, z);
    }
}
