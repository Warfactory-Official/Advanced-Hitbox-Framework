package com.norwood.ahf.client;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.client.body.BodyRenderer;
import com.norwood.ahf.client.compat.PlayerAnimBends;
import com.norwood.ahf.client.compat.PlayerAnimSampler;
import com.norwood.ahf.client.compat.StanceAnimLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = Ahf.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AhfClientSetup {

    private AhfClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("playeranimator")) {
            event.enqueueWork(PlayerAnimSampler::register);
            event.enqueueWork(StanceAnimLayer::register);
            event.enqueueWork(PlayerAnimBends::register);
        }
        if (ModList.get().isLoaded("gemrender")) {
            com.norwood.ahf.client.compat.GemRenderArmorRig.install();
        }
    }

    @SubscribeEvent
    public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(BodyRenderer.RELOAD);
    }
}
