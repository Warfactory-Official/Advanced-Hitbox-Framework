package com.norwood.ahf.stance;

import com.google.gson.JsonParser;
import com.norwood.ahf.pose.PoseClip;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@code assets/ahf/player_animator/stance.json}, both sides. Divergence from PA layers: clips ADD their delta from
 * {@link PoseClip#base} onto the layers below (gun hold, vanilla crouch) instead of overwriting. {@code *_right}
 * clips mirror for left.
 */
final class StanceClips {

    static final String PATH = "/assets/ahf/player_animator/stance.json";
    static final PoseClip STAND_LOW, STAND_MID, STEP, KNEEL_LOW, KNEEL, KNEEL_HIGH, KNEEL_STEP, PRONE_HIGH, PRONE_SIDE,
            PRONE_ROLL;
    private static final int[] MIRROR = {PoseClip.HEAD, PoseClip.TORSO, PoseClip.LEFT_ARM, PoseClip.RIGHT_ARM,
            PoseClip.LEFT_LEG, PoseClip.RIGHT_LEG, PoseClip.BODY, PoseClip.LEFT_ITEM, PoseClip.RIGHT_ITEM};
    /** Mirror sign per channel x, y, z, pitch, yaw, roll, bend axis, bend angle. */
    private static final double[] FLIP = {-1, 1, 1, 1, -1, -1, -1, 1};

    static {
        Map<String, PoseClip> m;
        try (InputStream in = StanceClips.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException("missing " + PATH);
            }
            m = PoseClip.parseFile(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        STAND_LOW = need(m, "stand_low");
        STAND_MID = need(m, "stand_mid");
        STEP = need(m, "step_right");
        KNEEL_LOW = need(m, "kneel_low");
        KNEEL = need(m, "kneel");
        KNEEL_HIGH = need(m, "kneel_high");
        KNEEL_STEP = need(m, "kneel_step_right");
        PRONE_HIGH = need(m, "prone_high");
        PRONE_SIDE = need(m, "prone_side_right");
        PRONE_ROLL = need(m, "prone_roll_right");
    }

    private StanceClips() {
    }

    private static PoseClip need(Map<String, PoseClip> m, String name) {
        PoseClip c = m.get(name);
        if (c == null) {
            throw new IllegalStateException(PATH + ": missing clip " + name);
        }
        return c;
    }

    /** {@code out[bone * CHANNELS + ch] += w * delta} of {@code c} at {@code t} ticks; NaN {@code t} => rest frame. */
    static void add(PoseClip c, double t, double w, boolean mirror, double[] out) {
        if (c == null || w == 0.0) {
            return;
        }
        double[] v = new double[PoseClip.CHANNELS];
        for (int b = 0; b < PoseClip.BONES.length; b++) {
            if (!c.animates(b)) {
                continue;
            }
            for (int ch = 0; ch < PoseClip.CHANNELS; ch++) {
                v[ch] = PoseClip.base(b, ch);
            }
            if (Double.isNaN(t)) {
                c.applyRest(b, v);
            } else {
                c.applyAt(b, t, v);
            }
            int to = mirror ? MIRROR[b] : b;
            for (int ch = 0; ch < PoseClip.CHANNELS; ch++) {
                out[to * PoseClip.CHANNELS + ch] += w * (mirror ? FLIP[ch] : 1.0) * (v[ch] - PoseClip.base(b, ch));
            }
        }
    }
}
