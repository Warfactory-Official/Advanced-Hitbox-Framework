package com.norwood.ahf.stance;

import com.norwood.ahf.Ahf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Ahf.MOD_ID)
public final class StanceEvents {

    private StanceEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player p = event.player;
        if (p.level().isClientSide) {
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
        StanceState s = AhfStance.get(p);
        Pose forced = s == null ? null : switch (s.appliedShape) {
            case STAND -> Pose.STANDING;
            case KNEEL -> Pose.CROUCHING;
            case PRONE, DOWNED -> Pose.SWIMMING;
        };
        if (forced == event.getPose()) {
            EntityDimensions shaped = StanceLogic.shape(p, s.appliedShape, s.appliedLevel);
            if (shaped != null) {
                event.setNewSize(shaped);
                event.setNewEyeHeight(StanceLogic.shapeEye(p, s.appliedShape, s.appliedLevel));
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

    /** Initial sync to a new tracker (NeoForge: attachments ride the pairing packets). */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player holder && event.getEntity() instanceof ServerPlayer to) {
            StanceSync.syncInitial(holder, to);
        }
    }

    /** The client's new player entity starts without state. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            StanceSync.syncInitial(p, p);
        }
    }
}
