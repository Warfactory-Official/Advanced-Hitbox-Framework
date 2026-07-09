package com.norwood.ahf.client;

import com.norwood.ahf.Ahf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Ahf.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class AhfClientEvents {

    private AhfClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
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
        double delta = event.getScrollDeltaY();
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
