package com.norwood.ahf;

import com.norwood.ahf.lagcomp.EntityHistory;
import com.norwood.ahf.lagcomp.RttProbe;
import com.norwood.ahf.network.AhfNetwork;
import com.norwood.ahf.rig.RigCache;
import com.norwood.ahf.stance.StanceLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Ahf.MOD_ID)
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
    public static void onLevelTickBegin(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.level instanceof ServerLevel level) {
            EntityHistory.beginTick(level);
        }
    }

    /** LOWEST: after other Pre handlers' moves => capture == broadcast. Stance first: capture sees its rig. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLevelTickPre(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.level instanceof ServerLevel level) {
            for (ServerPlayer p : level.players()) {
                StanceLogic.serverTick(p);
            }
            EntityHistory.capture(level);
        }
    }

    @SubscribeEvent
    public static void onServerTickPost(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            EntityHistory.endTick();
            RttProbe.tick(event.getServer());
        }
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
