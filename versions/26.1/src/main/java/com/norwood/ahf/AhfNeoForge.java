package com.norwood.ahf;

import com.norwood.ahf.network.AhfNetwork;
import com.norwood.ahf.rig.RigSpecIO;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import com.norwood.ahf.client.AhfKeybinds;

@Mod("ahf")
public final class AhfNeoForge {

    public AhfNeoForge(IEventBus modBus, ModContainer modContainer) {
        AhfNetwork.register(modBus);

        modBus.addListener(this::onCommonSetup);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener(AhfKeybinds::onRegisterKeyMappings);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> RigSpecIO.reload(FMLPaths.CONFIGDIR.get()));
    }
}
