package com.norwood.ahf.pose;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.GeckoLibSerializer;
import dev.kosmx.playerAnim.core.util.Vec3f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Evaluator == PlayerAnimator 2.0.4 on TACZ's rifle set + bend clips, every clip x tick x sub-tick x channel, bitwise. */
class PoseClipTest {

    private static final float[] DELTAS = {0F, 0.25F, 0.5F, 0.999F};
    private static final Vec3f POS_IN = new Vec3f(1.5F, -2.25F, 3.75F);
    private static final Vec3f ROT_IN = new Vec3f(0.3F, -2.9F, 4.0F);
    private static final Vec3f BEND_IN = new Vec3f(0.7F, -0.4F, 0F);

    private static JsonObject rifle() throws Exception {
        try (var in = PoseClipTest.class.getResourceAsStream("/pose/rifle_default.json")) {
            assertNotNull(in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void matchesPlayerAnimator() throws Exception {
        matches(rifle());
    }

    /** Bend channels: keyed + bare, limb (negated) + {@code torso_bend} / {@code body_bend} (both PA {@code body}). */
    @Test
    void bendsMatchPlayerAnimator() {
        matches(JsonParser.parseString("""
                {"format_version": "1.8.0", "animations": {"bent": {"animation_length": 1.0, "bones": {
                  "right_arm_bend": {"rotation": {"0.0": {"vector": [0, 0, 0], "lerp_mode": "INOUTSINE"},
                    "0.5": {"vector": [30, -80, 0], "lerp_mode": "INOUTSINE"}, "0.75": [10, -20, 0]}},
                  "left_leg_bend": {"rotation": [0, -95, 0]},
                  "right_item": {"rotation": [-29, 0, 0], "position": [0, 0.25, -0.97]},
                  "torso_bend": {"rotation": {"0.25": {"vector": [-15, 25, 0], "lerp_mode": "LINEAR"}}}}},
                  "bent_body": {"loop": "hold_on_last_frame", "animation_length": 0.5, "bones": {
                  "body_bend": {"rotation": {"0.0": [0, 0, 0], "0.5": {"vector": [5, 40, 0]}}},
                  "right_leg_bend": {"rotation": {"vector": [0, -60, 0]}}}}}}""").getAsJsonObject());
    }

    private static void matches(JsonObject root) {
        Map<String, PoseClip> mine = PoseClip.parseFile(root);
        List<KeyframeAnimation> theirs = GeckoLibSerializer.deserialize(root);
        assertEquals(theirs.size(), mine.size());
        int compared = 0;
        for (KeyframeAnimation a : theirs) {
            PoseClip c = mine.get(((String) a.extraData.get("name")).toLowerCase(Locale.ENGLISH));
            assertNotNull(c, (String) a.extraData.get("name"));
            assertEquals(a.endTick, c.endTick, c.name);
            assertEquals(a.stopTick, c.stopTick, c.name);
            assertEquals(a.isInfinite, c.looped, c.name);
            assertEquals(a.returnToTick, c.returnTick, c.name);
            KeyframeAnimationPlayer player = new KeyframeAnimationPlayer(a);
            int ticks = a.isInfinite ? 3 * (a.endTick + 1) : a.stopTick + 2;
            for (int i = 0; i < ticks && player.isActive(); i++) {
                for (float d : DELTAS) {
                    player.setupAnim(d);
                    for (int b = 0; b < PoseClip.BONES.length; b++) {
                        String bone = PoseClip.BONES[b];
                        Vec3f pos = player.get3DTransform(bone, TransformType.POSITION, d, POS_IN);
                        Vec3f rot = player.get3DTransform(bone, TransformType.ROTATION, d, ROT_IN);
                        Vec3f bend = player.get3DTransform(bone, TransformType.BEND, d, BEND_IN);
                        float[] want = {pos.getX(), pos.getY(), pos.getZ(), rot.getX(), rot.getY(), rot.getZ(),
                                bend.getX(), bend.getY()};
                        float[] in = {POS_IN.getX(), POS_IN.getY(), POS_IN.getZ(), ROT_IN.getX(), ROT_IN.getY(),
                                ROT_IN.getZ(), BEND_IN.getX(), BEND_IN.getY()};
                        for (int ch = 0; ch < PoseClip.CHANNELS; ch++) {
                            float got = c.tracks[b][ch] == null ? (ch >= 3 ? PoseClip.clampToRadian(PoseClip
                                    .clampToRadian(in[ch])) : in[ch])
                                    : c.value(b, ch, player.getTick(), d, player.isLoopStarted(), in[ch]);
                            assertEquals(Float.floatToIntBits(want[ch]), Float.floatToIntBits(got),
                                    c.name + " " + bone + " ch" + ch + " tick " + player.getTick() + " d" + d
                                            + ": " + want[ch] + " != " + got);
                            compared++;
                        }
                    }
                }
                int next = c.tickAfter(player.getTick());
                player.tick();
                if (player.isActive()) {
                    assertEquals(player.getTick(), next, c.name + " tick advance");
                }
            }
        }
        assertTrue(compared > 1_000, "compared " + compared);
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
