package com.norwood.ahf;

import com.norwood.ahf.client.AhfKeybinds;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@Mod(Ahf.MOD_ID)
public final class AhfNeoForge {

    public AhfNeoForge(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, AhfConfigSpec.SPEC);
        modBus.addListener(AhfNeoForge::onConfig);
        AhfNetwork.register(modBus);
        AhfStance.ATTACHMENTS.register(modBus);
        AhfStances.register(PostureStance.UPRIGHT);
        AhfStances.register(PostureStance.LYING);
        AhfHooks.addGunAimProgress(0, e -> e instanceof Player p ? AhfStance.adsProgress(p) : Double.NaN);

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
