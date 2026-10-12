package com.norwood.ahf;

import com.norwood.ahf.client.AhfKeybinds;
import com.norwood.ahf.compat.tacz.TaczCompat;
import com.norwood.ahf.compat.tacz.TaczGunState;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.config.AhfConfigSpec;
import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.network.AhfNetwork;
import com.norwood.ahf.rig.RigSpecIO;
import com.norwood.ahf.rig.RigTuning;
import com.norwood.ahf.stance.AhfStance;
import com.norwood.ahf.stance.AhfStances;
import com.norwood.ahf.stance.PostureStance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod(Ahf.MOD_ID)
public final class AhfForge {

    public AhfForge(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.COMMON, AhfConfigSpec.SPEC);
        modBus.addListener(AhfForge::onConfig);
        AhfNetwork.register();
        AhfStances.register(PostureStance.UPRIGHT);
        AhfStances.register(PostureStance.LYING);
        AhfHooks.addGunAimProgress(0, e -> e instanceof Player p ? AhfStance.adsProgress(p) : Double.NaN);
        if (TaczCompat.isLoaded()) {
            AhfHooks.addHeldGun(TaczCompat::isHeldGun);
            AhfHooks.addGunAimProgress(-1, TaczGunState::aimingProgress);
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(AhfKeybinds::onRegisterKeyMappings);
        }
    }

    private static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != AhfConfigSpec.SPEC || event instanceof ModConfigEvent.Unloading) {
            return;
        }
        AhfConfigSpec.bind();
        RigTuning.ACTIVE = AhfConfig.hitboxDebug();
        RigTuning.seedEnvelope(AhfConfig.envelopeReachSnapshot());
        RigSpecIO.reload(FMLPaths.CONFIGDIR.get());
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> server.getPlayerList().getPlayers().forEach(AhfNetwork::sendHitAuthority));
        }
    }
}
