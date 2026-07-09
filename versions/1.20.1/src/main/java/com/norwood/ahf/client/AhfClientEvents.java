package com.norwood.ahf.client;

import com.norwood.ahf.Ahf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Ahf.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AhfClientEvents {

    private AhfClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            drain(AhfKeybinds.TOGGLE_HITBOX);
            return;
        }
        while (AhfKeybinds.TOGGLE_HITBOX.consumeClick()) {
            HitboxDebugRenderer.toggle();
            mc.player.displayClientMessage(Component.literal("Hitbox overlay: "
                    + (HitboxDebugRenderer.enabled ? "on (" + HitboxDebugRenderer.style + ", scroll to change)" : "off")), true);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!HitboxDebugRenderer.enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        double delta = event.getScrollDelta();
        if (delta == 0.0) {
            return;
        }
        HitboxDebugRenderer.Style now = HitboxDebugRenderer.cycleStyle(delta > 0.0 ? 1 : -1);
        mc.player.displayClientMessage(Component.literal("Hitbox overlay: " + now), true);
        event.setCanceled(true);
    }

    private static void drain(net.minecraft.client.KeyMapping key) {
        while (key.consumeClick()) {
        }
    }
}
