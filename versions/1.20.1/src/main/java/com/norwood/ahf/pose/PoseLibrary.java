package com.norwood.ahf.pose;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player-animation files as rig data, both sides: posture and gun-hold poses read by {@code HumanoidRig} from the
 * same keyframe JSON the render plays through PlayerAnimator. Sets registered once (mod construction / setup).
 */
public final class PoseLibrary {

    private static final Map<ResourceLocation, Map<String, PoseClip>> SETS = new ConcurrentHashMap<>();

    private PoseLibrary() {
    }

    /** Duplicate id => error. */
    public static void register(ResourceLocation id, JsonObject file) {
        Map<String, PoseClip> clips = Map.copyOf(PoseClip.parseFile(file));
        if (SETS.putIfAbsent(id, clips) != null) {
            throw new IllegalStateException("pose set " + id + " registered twice");
        }
    }

    public static void register(ResourceLocation id, InputStream json) throws IOException {
        try (InputStreamReader r = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            register(id, JsonParser.parseReader(r).getAsJsonObject());
        }
    }

    public static boolean has(ResourceLocation id) {
        return SETS.containsKey(id);
    }

    @Nullable
    public static PoseClip clip(ResourceLocation set, String name) {
        Map<String, PoseClip> clips = SETS.get(set);
        return clips == null ? null : clips.get(name);
    }
}
