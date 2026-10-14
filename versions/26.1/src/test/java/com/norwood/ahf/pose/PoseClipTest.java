package com.norwood.ahf.pose;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 26.1: PlayerAnimator bitwise comparison omitted (no 26.x PlayerAnimator). */
class PoseClipTest {

    private static JsonObject rifle() throws Exception {
        try (var in = PoseClipTest.class.getResourceAsStream("/pose/rifle_default.json")) {
            assertNotNull(in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void restFrameIsTickZero() throws Exception {
        PoseClip hold = PoseClip.parseFile(rifle()).get("hold_upper");
        double[] v = {9, 9, 9, 9, 9, 9, 9, 9};
        hold.applyRest(PoseClip.RIGHT_ARM, v);
        // right_arm tick 0: rotation [-90.5, 0, 2.5] deg, position [2.275, -2, 1] + default (-5, 2, 0), y negated.
        assertEquals(-90.5F * 0.01745329251F, (float) v[3]);
        assertEquals(2.275F - 5F, (float) v[0]);
        assertEquals(4F, (float) v[1]);
        double[] legs = {9, 9, 9, 9, 9, 9, 9, 9};
        hold.applyRest(PoseClip.RIGHT_LEG, legs);
        assertEquals(9, legs[0], "hold_upper must not touch legs");
    }

    @Test
    void unknownEaseFails() {
        JsonObject bad = JsonParser.parseString("""
                {"animations": {"x": {"loop": true, "animation_length": 1, "bones": {"head": {"rotation":
                {"0.0": {"post": [1, 2, 3], "lerp_mode": "catmullrom"}}}}}}}""").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> PoseClip.parseFile(bad));
    }

    @Test
    void fadeInSecondsElseDefault() {
        Map<String, PoseClip> c = PoseClip.parseFile(JsonParser.parseString("""
                {"animations": {"quick": {"animation_length": 0.5, "fade_in": 0.1, "bones": {}},
                  "plain": {"animation_length": 0.5, "bones": {}}}}""").getAsJsonObject());
        assertEquals(2, c.get("quick").fadeTicks);
        assertEquals(PoseClip.FADE_TICKS, c.get("plain").fadeTicks);
    }
}
