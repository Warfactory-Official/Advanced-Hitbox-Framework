package com.norwood.ahf.client;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.client.compat.PlayerAnimSampler;
import com.norwood.ahf.client.compat.StanceAnimLayer;
import com.norwood.ahf.client.body.BodyRenderer;
import com.norwood.ahf.client.compat.PlayerAnimBends;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

@Mod.EventBusSubscriber(modid = Ahf.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
