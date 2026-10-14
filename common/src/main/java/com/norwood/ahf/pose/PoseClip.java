package com.norwood.ahf.pose;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One animation of a Bedrock (GeckoLib) player-animation file, read and evaluated exactly as PlayerAnimator 2.0.4
 * ({@code GeckoLibSerializer} -> {@code KeyframeAnimation} -> {@code KeyframeAnimationPlayer}), with no PlayerAnimator
 * class: runs on a dedicated server. Channel values in ModelPart units (pivot px, Euler rad); {@code body} = PA's
 * whole-player transform (position blocks, rotation rad); bend = PA {@code getBend} {@code (axis, angle)} rad, on
 * {@code body} = waist ({@code torso_bend} / {@code body_bend}).
 */
public final class PoseClip {

    /** PA part names; {@code body} = whole player, {@code torso} = ModelPart {@code body}; items render-only. */
    public static final String[] BONES = {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg", "body",
            "rightItem", "leftItem"};
    public static final int HEAD = 0, TORSO = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5, BODY = 6,
            RIGHT_ITEM = 7, LEFT_ITEM = 8;
    /** Channels per bone: x, y, z, pitch, yaw, roll, bend axis, bend angle. */
    public static final int CHANNELS = 8;
    public static final int BEND_AXIS = 6, BEND = 7;
    /** PA {@code AnimationBuilder} part defaults, per bone x/y/z (rotation defaults 0). */
    private static final float[][] DEFAULT_POS = {
            {0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9F, 12, 0.1F}, {1.9F, 12, 0.1F}, {0, 0, 0}, {0, 0, 0},
            {0, 0, 0}};
    private static final float DEG = 0.01745329251F;
    /** TACZ layer fade, ticks. */
    public static final int FADE_TICKS = 8;

    public final String name;
    public final int endTick;
    public final int stopTick;
    public final boolean looped;
    public final int returnTick;
    /** One-shot fade-in, ticks: {@code fade_in} (s, divergence), else {@link #FADE_TICKS}. */
    public final int fadeTicks;
    /** [bone][channel], null = channel not animated (incoming value kept). */
    final Track[][] tracks = new Track[BONES.length][CHANNELS];
    /** Rest frame (tick 0, delta 0) per bone x channel; NaN = not animated. */
    private final double[] rest = new double[BONES.length * CHANNELS];

    private PoseClip(String name, int endTick, boolean looped, int returnTick, int fadeTicks) {
        this.name = name;
        this.fadeTicks = fadeTicks;
        this.endTick = Math.max(1, endTick);
        this.stopTick = this.endTick + 3;
        this.looped = looped;
        this.returnTick = returnTick;
    }

    /** Value of a channel no clip animates: PA part default position, rotation 0. */
    public static float base(int bone, int ch) {
        return ch < 3 ? DEFAULT_POS[bone][ch] : 0.0F;
    }

    public static int bone(String paName) {
        for (int i = 0; i < BONES.length; i++) {
            if (BONES[i].equals(paName)) {
                return i;
            }
        }
        return -1;
    }

    /** Every animation of {@code root} ({@code {format_version, animations: {name: ...}}}), by lower-case name. */
    public static Map<String, PoseClip> parseFile(JsonObject root) {
        Map<String, PoseClip> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("animations").entrySet()) {
            PoseClip c = parse(e.getKey(), e.getValue().getAsJsonObject());
            out.put(c.name, c);
        }
        return out;
    }

    static PoseClip parse(String name, JsonObject node) {
        int end = 0;
        boolean looped = false;
        int ret = 0;
        boolean read = false;
        if (node.has("animation_length")) {
            end = (int) Math.ceil(node.get("animation_length").getAsFloat() * 20);
            if (node.has("loop")) {
                JsonElement loop = node.get("loop");
                looped = loop.getAsJsonPrimitive().isBoolean() && loop.getAsBoolean();
                if (!looped && loop.getAsJsonPrimitive().isString() && loop.getAsString().equals("hold_on_last_frame")) {
                    looped = true;
                    ret = end;
                } else {
                    end--;
                }
            }
            read = true;
        } else if (node.has("loop") && node.get("loop").getAsBoolean()) {
            end = 1;
            looped = true;
            read = true;
        }
        int fade = node.has("fade_in") ? Math.max(1, Math.round(node.get("fade_in").getAsFloat() * 20)) : FADE_TICKS;
        PoseClip c = new PoseClip(name.toLowerCase(Locale.ENGLISH), end, looped, ret, fade);
        if (read) {
            for (Map.Entry<String, JsonElement> b : node.getAsJsonObject("bones").entrySet()) {
                if (b.getKey().endsWith("_bend")) {
                    String part = b.getKey().replace("_bend", "");
                    boolean torso = part.equals("torso");
                    int bone = bone(torso ? "body" : snake2Camel(part));
                    if (bone < 0 || bone == HEAD || bone == TORSO || bone >= RIGHT_ITEM) {
                        throw new IllegalArgumentException(name + ": unbendable " + b.getKey());
                    }
                    c.readBend(bone, torso, b.getValue().getAsJsonObject());
                    continue;
                }
                int bone = bone(snake2Camel(b.getKey()));
                if (bone < 0) {
                    throw new IllegalArgumentException(name + ": unknown bone " + b.getKey());
                }
                c.readBone(bone, b.getValue().getAsJsonObject());
            }
        }
        c.bake();
        return c;
    }

    private void readBone(int bone, JsonObject node) {
        for (String key : node.keySet()) {
            if (!key.equals("rotation") && !key.equals("position")) {
                throw new IllegalArgumentException(name + ": unsupported channel " + key);
            }
        }
        if (node.has("rotation")) {
            readChannel(bone, true, node.get("rotation"));
        }
        if (node.has("position")) {
            readChannel(bone, false, node.get("position"));
        }
    }

    /** PA {@code readBoneBend}: rotation only, {@code [angle, axis]} deg; limb axis negated, torso kept. */
    private void readBend(int bone, boolean torso, JsonObject node) {
        for (String key : node.keySet()) {
            if (!key.equals("rotation")) {
                throw new IllegalArgumentException(name + ": unsupported bend channel " + key);
            }
        }
        readChannel(bone, torso ? Kind.TORSO_BEND : Kind.BEND, node.get("rotation"));
    }

    private void readChannel(int bone, boolean rotation, JsonElement el) {
        readChannel(bone, rotation ? Kind.ROTATION : Kind.POSITION, el);
    }

    private enum Kind {
        POSITION, ROTATION, BEND, TORSO_BEND
    }

    /** PA: bare array => tick 0 LINEAR; keyed array => CONSTANT (rotation, bend) / LINEAR (position); object => lerp_mode. */
    private void readChannel(int bone, Kind kind, JsonElement el) {
        boolean rotation = kind != Kind.POSITION;
        if (el.isJsonArray()) {
            add(bone, kind, 0, Ease.LINEAR, el.getAsJsonArray());
            return;
        }
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            if (e.getKey().equals("vector")) {
                add(bone, kind, 0, Ease.LINEAR, e.getValue().getAsJsonArray());
            } else if (!e.getKey().equals("easing")) {
                int tick = (int) (Float.parseFloat(e.getKey()) * 20);
                if (e.getValue().isJsonArray()) {
                    add(bone, kind, tick, rotation ? Ease.CONSTANT : Ease.LINEAR, e.getValue().getAsJsonArray());
                } else {
                    JsonObject k = e.getValue().getAsJsonObject();
                    Ease ease = Ease.LINEAR;
                    if (k.has("lerp_mode")) {
                        ease = Ease.of(k.get("lerp_mode").getAsString());
                    }
                    if (k.has("easing")) {
                        ease = Ease.of(k.get("easing").getAsString());
                    }
                    if (k.has("easingArgs")) {
                        throw new IllegalArgumentException(name + ": easingArgs unsupported");
                    }
                    if (k.has("pre")) {
                        add(bone, kind, tick, ease, vector(k.get("pre")));
                    }
                    if (k.has("vector")) {
                        add(bone, kind, tick, ease, k.get("vector").getAsJsonArray());
                    }
                    if (k.has("post")) {
                        add(bone, kind, tick, ease, vector(k.get("post")));
                    }
                }
            }
        }
    }

    private static JsonArray vector(JsonElement e) {
        return e.isJsonArray() ? e.getAsJsonArray() : e.getAsJsonObject().get("vector").getAsJsonArray();
    }

    private void add(int bone, Kind kind, int tick, Ease ease, JsonArray a) {
        if (kind == Kind.BEND || kind == Kind.TORSO_BEND) {
            track(bone, BEND).add(tick, a.get(0).getAsFloat() * DEG, ease);
            track(bone, BEND_AXIS).add(tick, a.get(1).getAsFloat() * (kind == Kind.TORSO_BEND ? 1 : -1) * DEG, ease);
            return;
        }
        boolean rotation = kind == Kind.ROTATION;
        for (int i = 0; i < 3; i++) {
            float v = a.get(i).getAsFloat();
            int ch;
            if (rotation) {
                if (bone == BODY && i != 2) {
                    v = -v;
                }
                v *= DEG;
                ch = 3 + i;
            } else {
                if (bone == BODY) {
                    v = v / 16f;
                    if (i == 0) {
                        v = -v;
                    }
                } else if (i == 1) {
                    v = -v;
                }
                v += DEFAULT_POS[bone][i];
                ch = i;
            }
            track(bone, ch).add(tick, v, ease);
        }
    }

    private Track track(int bone, int ch) {
        Track t = tracks[bone][ch];
        if (t == null) {
            t = tracks[bone][ch] = new Track();
        }
        return t;
    }

    private void bake() {
        for (int b = 0; b < BONES.length; b++) {
            for (int ch = 0; ch < CHANNELS; ch++) {
                rest[b * CHANNELS + ch] = tracks[b][ch] == null ? Double.NaN : value(b, ch, 0, 0, false, 0);
            }
        }
    }

    /** Rest frame (tick 0) of {@code bone}; animated channels overwrite {@code v} ({@link #CHANNELS}). */
    public void applyRest(int bone, double[] v) {
        for (int ch = 0; ch < CHANNELS; ch++) {
            double r = rest[bone * CHANNELS + ch];
            if (!Double.isNaN(r)) {
                v[ch] = r;
            }
        }
    }

    /** Animated channels of {@code bone} at clip time {@code t} ticks (after the first loop); {@code v} as {@link #applyRest}. */
    public void applyAt(int bone, double t, double[] v) {
        int tick = (int) Math.floor(t);
        float delta = (float) (t - tick);
        for (int ch = 0; ch < CHANNELS; ch++) {
            if (tracks[bone][ch] != null) {
                v[ch] = value(bone, ch, tick, delta, true, (float) v[ch]);
            }
        }
    }

    /** One channel at clip time {@code t} ticks; {@code current} when not animated. */
    public float valueAt(int bone, int ch, double t, float current) {
        int tick = (int) Math.floor(t);
        return value(bone, ch, tick, (float) (t - tick), true, current);
    }

    /** PA fade-in weight (INOUTSINE) {@code t} ticks into a {@code length}-tick fade. */
    public static float fadeIn(double t, int length) {
        return Ease.INOUTSINE.apply((float) Math.min(Math.max(t / length, 0), 1));
    }

    public boolean animates(int bone) {
        for (int ch = 0; ch < CHANNELS; ch++) {
            if (tracks[bone][ch] != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * PA {@code KeyframeAnimationPlayer} value of one channel: player at {@code tick} ({@code loopStarted} after its
     * first wrap), {@code delta} partial tick, {@code current} = incoming value (returned when not animated).
     */
    public float value(int bone, int ch, int tick, float delta, boolean loopStarted, float current) {
        Track t = tracks[bone][ch];
        if (t == null) {
            return current;
        }
        boolean angle = ch >= 3;
        float in = angle ? clampToRadian(current) : current;
        float def = angle ? 0 : DEFAULT_POS[bone][ch];
        int pos = t.findAtTick(tick);
        Key before = before(t, pos, tick, in, def);
        if (loopStarted && before.tick < returnTick) {
            before = before(t, t.findAtTick(endTick), tick, in, def);
        }
        Key after = after(t, pos, tick, in, def);
        if (looped && after.tick > endTick) {
            after = after(t, t.findAtTick(returnTick - 1), tick, in, def);
        }
        float v = interpolate(before, after, tick, delta);
        return angle ? clampToRadian(v) : v;
    }

    /** PA advances a looped player {@code currentTick > endTick => returnTick}. */
    public int tickAfter(int tick) {
        int n = tick + 1;
        return looped && n > endTick ? returnTick : n;
    }

    /** PA {@code Axis.findBefore}; {@code def} = the state's default value. */
    private Key before(Track t, int pos, int tick, float current, float def) {
        if (pos == -1) {
            return tick < 0 ? new Key(0, current, Ease.INOUTSINE)
                    : tick < endTick ? new Key(0, def, Ease.INOUTSINE) : new Key(endTick, def, Ease.INOUTSINE);
        }
        Key k = t.keys[pos];
        if (!looped && tick >= endTick && pos == t.n - 1 && k.tick < endTick) {
            return new Key(endTick, k.value, k.ease);
        }
        return k;
    }

    /** PA {@code Axis.findAfter}. */
    private Key after(Track t, int pos, int tick, float current, float def) {
        if (t.n > pos + 1) {
            return t.keys[pos + 1];
        }
        if (looped) {
            return new Key(endTick + 1, def, Ease.INOUTSINE);
        }
        if (tick < endTick && t.n > 0) {
            Key last = t.keys[t.n - 1];
            return new Key(endTick, last.value, last.ease);
        }
        return tick >= endTick ? new Key(stopTick, current, Ease.INOUTSINE) : new Key(endTick, def, Ease.INOUTSINE);
    }

    private float interpolate(Key before, Key after, int tick, float delta) {
        int tb = before.tick;
        int ta = after.tick;
        if (tb >= ta) {
            if (tick < tb) {
                tb -= endTick - returnTick + 1;
            } else {
                ta += endTick - returnTick + 1;
            }
        }
        if (tb == ta) {
            return before.value;
        }
        float f = (tick + delta - (float) tb) / (ta - tb);
        return before.value + before.ease.apply(f) * (after.value - before.value);
    }

    /** PA {@code MathHelper.clampToRadian}. */
    static float clampToRadian(float f) {
        final double a = Math.PI * 2;
        double b = (f + Math.PI) % a;
        if (b < 0) {
            b += a;
        }
        return (float) (b - Math.PI);
    }

    /** PA {@code GeckoLibSerializer.snake2Camel}. */
    static String snake2Camel(String s) {
        StringBuilder b = new StringBuilder();
        boolean up = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_') {
                up = true;
                continue;
            }
            b.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return b.toString();
    }

    /** PA easings used by player-animation files; unknown names fail the load (PA: silently linear). */
    enum Ease {
        LINEAR, CONSTANT, INOUTSINE;

        static Ease of(String s) {
            String n = s.length() >= 4 && s.substring(0, 4).equalsIgnoreCase("EASE") ? s.substring(4) : s;
            return Ease.valueOf(n.toUpperCase(Locale.ROOT));
        }

        float apply(float t) {
            return switch (this) {
                case LINEAR -> t;
                case CONSTANT -> 0f;
                case INOUTSINE -> t < 0.5F ? sine(t * 2F) / 2F : 1 - sine((1 - t) * 2F) / 2F;
            };
        }

        private static float sine(float n) {
            return 1 - (float) Math.cos(n * Math.PI / 2F);
        }
    }

    record Key(int tick, float value, Ease ease) {
    }

    /** PA {@code State}: keys by tick, equal ticks in insertion order. */
    static final class Track {
        Key[] keys = new Key[4];
        int n;

        void add(int tick, float value, Ease ease) {
            int i = findAtTick(tick) + 1;
            if (n == keys.length) {
                keys = Arrays.copyOf(keys, n * 2);
            }
            System.arraycopy(keys, i, keys, i + 1, n - i);
            keys[i] = new Key(tick, value, ease);
            n++;
        }

        /** PA {@code State.findAtTick}: last key with {@code tick <= t} (of equal ticks, the later), else -1. */
        int findAtTick(int t) {
            int lo = 0, hi = n - 1, found = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int c = Integer.compare(keys[mid].tick, t);
                if (c < 0) {
                    lo = mid + 1;
                } else if (c > 0) {
                    hi = mid - 1;
                } else {
                    found = mid;
                    break;
                }
            }
            int i = found >= 0 ? found : lo - 1;
            if (i + 1 < n && keys[i + 1].tick == t) {
                return i + 1;
            }
            return i;
        }
    }
}
