package com.norwood.ahf;

import com.norwood.ahf.lagcomp.EntityHistory;
import com.norwood.ahf.lagcomp.RttProbe;
import com.norwood.ahf.network.AhfNetwork;
import com.norwood.ahf.rig.RigCache;
import com.norwood.ahf.stance.StanceLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Ahf.MOD_ID)
public final class AhfServerEvents {

    private AhfServerEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AhfNetwork.sendHitAuthority(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RigCache.clearHint(player.getId());
            RttProbe.forget(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelTickBegin(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EntityHistory.beginTick(level);
        }
    }

    /** LOWEST: after other Pre handlers' moves => capture == broadcast. Stance first: capture sees its rig. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (ServerPlayer p : level.players()) {
                StanceLogic.serverTick(p);
            }
            EntityHistory.capture(level);
        }
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        EntityHistory.endTick();
        RttProbe.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        EntityHistory.onLeave(event.getEntity(), event.getLevel());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        EntityHistory.clearAll();
        RttProbe.clearAll();
    }
}
