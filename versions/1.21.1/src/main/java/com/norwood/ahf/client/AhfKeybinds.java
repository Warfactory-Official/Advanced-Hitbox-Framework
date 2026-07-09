package com.norwood.ahf.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class AhfKeybinds {

    public static final String CATEGORY = "key.categories.ahf";

    public static final KeyMapping TOGGLE_HITBOX = new KeyMapping(
            "key.ahf.toggle_hitbox",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY);

    private AhfKeybinds() {
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_HITBOX);
    }
}
