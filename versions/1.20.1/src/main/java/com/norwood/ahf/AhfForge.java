package com.norwood.ahf;

import com.norwood.ahf.client.AhfKeybinds;
import com.norwood.ahf.compat.tacz.TaczCompat;
import com.norwood.ahf.compat.tacz.TaczGunState;
import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.network.AhfNetwork;
import com.norwood.ahf.rig.RigSpecIO;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod("ahf")
public final class AhfForge {

    public AhfForge(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        AhfNetwork.register();

        AhfHooks.setHeldGunPredicate(TaczCompat::isHeldGun);
        AhfHooks.setGunAimProgress(TaczGunState::aimingProgress);
        AhfHooks.setBulletHitPos(TaczCompat::bulletHitPos);

        modBus.addListener(this::onCommonSetup);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                modBus.addListener(AhfKeybinds::onRegisterKeyMappings)
        );
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> RigSpecIO.reload(FMLPaths.CONFIGDIR.get()));
    }
}
