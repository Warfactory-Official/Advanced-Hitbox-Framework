package com.norwood.ahf.stance;

import com.norwood.ahf.Ahf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Ahf.MOD_ID)
public final class StanceEvents {

    private StanceEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide()) {
            StanceLogic.clientTick(p);
        } else {
            StanceLogic.afterPlayerTick(p);
        }
    }

    /** Fires from the Entity constructor too => existing data only. */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        StanceState s = p.getExistingDataOrNull(AhfStance.TYPE);
        Pose forced = s == null ? null : switch (s.appliedShape) {
            case STAND -> Pose.STANDING;
            case KNEEL -> Pose.CROUCHING;
            case PRONE -> Pose.SWIMMING;
            case DOWNED -> Pose.STANDING;
        };
        if (forced == event.getPose()) {
            EntityDimensions shaped = StanceLogic.shape(p, s.appliedShape, s.appliedLevel);
            if (shaped != null) {
                event.setNewSize(shaped);
            }
        }
    }

    /** LOWEST, uncancelled only: a downed-instead-of-dead cancel keeps the stance. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            StanceLogic.onDeath(p);
        }
    }
}
