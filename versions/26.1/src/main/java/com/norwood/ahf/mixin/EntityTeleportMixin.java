package com.norwood.ahf.mixin;

import com.norwood.ahf.lagcomp.EntityHistory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Non-player teleports. {@code ServerPlayer} overrides both without super => {@link PlayerTeleportMixin}. */
@Mixin(Entity.class)
public abstract class EntityTeleportMixin {

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"))
    private void ahf$dropHistory(double x, double y, double z, CallbackInfo ci) {
        EntityHistory.onTeleport((Entity) (Object) this, null, x, y, z);
    }

    /** 26.1: {@code teleportTo(ServerLevel, ...)} and portals funnel here; position may be relative. */
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"))
    private void ahf$dropHistoryTransition(TeleportTransition transition, CallbackInfoReturnable<Entity> cir) {
        Entity self = (Entity) (Object) this;
        Vec3 to = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(self),
                PositionMoveRotation.of(transition), transition.relatives()).position();
        EntityHistory.onTeleport(self, transition.newLevel(), to.x, to.y, to.z);
    }
}
