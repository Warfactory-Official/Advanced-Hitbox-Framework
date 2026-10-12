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

/**
 * Evaluator == PlayerAnimator 1.0.2 (1.20.1 render) on TACZ's rifle set, every clip x tick x sub-tick x channel.
 * Divergence: 1.0.2 float order != 2.0.4 => within 1e-6, not bitwise (measured max 9.5e-7 px, 5732 / 245952 values).
 */
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

    private static void matches(JsonObject root) {
        Map<String, PoseClip> mine = PoseClip.parseFile(root);
        List<KeyframeAnimation> theirs = GeckoLibSerializer.serialize(root);
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
                            assertEquals(want[ch], got, 1.0E-6F,
                                    c.name + " " + bone + " ch" + ch + " tick " + player.getTick() + " d" + d);
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
}
