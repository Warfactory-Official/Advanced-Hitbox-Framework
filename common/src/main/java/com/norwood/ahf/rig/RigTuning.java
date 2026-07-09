package com.norwood.ahf.rig;

import com.norwood.ahf.part.HitboxPart;

public final class RigTuning {

    public static final int FIELDS = 9;

    public enum Field {
        OX, OY, OZ,
        SX, SY, SZ,
        PX, PY, PZ;

        public static final Field[] VALUES = values();

        public static Field fromString(String s) {
            for (Field f : VALUES) {
                if (f.name().equalsIgnoreCase(s)) {
                    return f;
                }
            }
            return null;
        }

        public String lower() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum RigPose {
        STANDING, CROUCHING, PRONE;

        public static final RigPose[] VALUES = values();

        public static RigPose fromString(String s) {
            for (RigPose p : VALUES) {
                if (p.name().equalsIgnoreCase(s)) {
                    return p;
                }
            }
            return null;
        }

        public String lower() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum HandAction {
        NONE, BOW, GUN, BLOCK;

        public static final HandAction[] VALUES = values();

        public static HandAction fromString(String s) {
            for (HandAction h : VALUES) {
                if (h.name().equalsIgnoreCase(s)) {
                    return h;
                }
            }
            return null;
        }

        public String lower() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final int LIMBS = HitboxPart.VALUES.length;
    private static final int PER_POSE = LIMBS * FIELDS;
    private static final int HAND_ACTIONS = HandAction.VALUES.length;
    private static final int PER_HAND_POSE = HAND_ACTIONS * LIMBS * FIELDS;

    public static volatile boolean ACTIVE = false;

    private static volatile double[] deltas = new double[RigPose.VALUES.length * PER_POSE];

    public static volatile HitboxPart highlight = null;

    private RigTuning() {
    }

    public static double[] deltas() {
        return deltas;
    }

    public static int base(RigPose pose, HitboxPart limb) {
        return pose.ordinal() * PER_POSE + limb.ordinal() * FIELDS;
    }

    public static int index(RigPose pose, HitboxPart limb, Field f) {
        return base(pose, limb) + f.ordinal();
    }

    public static double delta(RigPose pose, HitboxPart limb, Field f) {
        return deltas[index(pose, limb, f)];
    }

    public static synchronized void set(RigPose pose, HitboxPart limb, Field f, double value) {
        double[] next = deltas.clone();
        next[index(pose, limb, f)] = value;
        deltas = next;
        highlight = limb;
    }

    public static synchronized double add(RigPose pose, HitboxPart limb, Field f, double amount) {
        double[] next = deltas.clone();
        int idx = index(pose, limb, f);
        double v = next[idx] + amount;
        next[idx] = v;
        deltas = next;
        highlight = limb;
        return v;
    }

    public static synchronized void reset() {
        deltas = new double[RigPose.VALUES.length * PER_POSE];
        highlight = null;
    }

    public static synchronized void reset(RigPose pose) {
        double[] next = deltas.clone();
        int b = pose.ordinal() * PER_POSE;
        for (int i = 0; i < PER_POSE; i++) {
            next[b + i] = 0.0;
        }
        deltas = next;
    }

    public static synchronized void reset(RigPose pose, HitboxPart limb) {
        double[] next = deltas.clone();
        int b = base(pose, limb);
        for (int i = 0; i < FIELDS; i++) {
            next[b + i] = 0.0;
        }
        deltas = next;
    }

    public static boolean hasTuning(RigPose pose, HitboxPart limb) {
        double[] d = deltas;
        int b = base(pose, limb);
        for (int i = 0; i < FIELDS; i++) {
            if (d[b + i] != 0.0) {
                return true;
            }
        }
        return false;
    }

    public static int tunedCount() {
        double[] d = deltas;
        int n = 0;
        for (double v : d) {
            if (v != 0.0) {
                n++;
            }
        }
        return n;
    }

    private static volatile double[] handDeltas = new double[RigPose.VALUES.length * PER_HAND_POSE];

    public static double[] handDeltas() {
        return handDeltas;
    }

    public static int handBase(RigPose pose, HandAction hand, HitboxPart limb) {
        return pose.ordinal() * PER_HAND_POSE + hand.ordinal() * (LIMBS * FIELDS) + limb.ordinal() * FIELDS;
    }

    public static double handDelta(RigPose pose, HandAction hand, HitboxPart limb, Field f) {
        return handDeltas[handBase(pose, hand, limb) + f.ordinal()];
    }

    public static synchronized void setHand(RigPose pose, HandAction hand, HitboxPart limb, Field f, double value) {
        double[] next = handDeltas.clone();
        next[handBase(pose, hand, limb) + f.ordinal()] = value;
        handDeltas = next;
        highlight = limb;
    }

    public static synchronized double addHand(RigPose pose, HandAction hand, HitboxPart limb, Field f, double amount) {
        double[] next = handDeltas.clone();
        int idx = handBase(pose, hand, limb) + f.ordinal();
        double v = next[idx] + amount;
        next[idx] = v;
        handDeltas = next;
        highlight = limb;
        return v;
    }

    public static synchronized void resetHand(RigPose pose, HandAction hand) {
        double[] next = handDeltas.clone();
        int b = pose.ordinal() * PER_HAND_POSE + hand.ordinal() * (LIMBS * FIELDS);
        for (int i = 0; i < LIMBS * FIELDS; i++) {
            next[b + i] = 0.0;
        }
        handDeltas = next;
    }

    public static synchronized void resetHand(RigPose pose, HandAction hand, HitboxPart limb) {
        double[] next = handDeltas.clone();
        int b = handBase(pose, hand, limb);
        for (int i = 0; i < FIELDS; i++) {
            next[b + i] = 0.0;
        }
        handDeltas = next;
    }

    public enum EnvAxis {
        HORIZONTAL, VERTICAL;

        public static final EnvAxis[] VALUES = values();

        public static EnvAxis fromString(String s) {
            if (s == null) {
                return null;
            }
            String t = s.toLowerCase(java.util.Locale.ROOT);
            if (t.equals("h") || t.equals("horizontal")) {
                return HORIZONTAL;
            }
            if (t.equals("v") || t.equals("vertical")) {
                return VERTICAL;
            }
            return null;
        }

        public String lower() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static volatile double[] envelope = new double[RigPose.VALUES.length * 2];

    public static double envReach(RigPose pose, EnvAxis axis) {
        return envelope[pose.ordinal() * 2 + axis.ordinal()];
    }

    public static synchronized void seedEnvelope(double[] hv) {
        envelope = hv.clone();
    }

    public static synchronized void setEnv(RigPose pose, EnvAxis axis, double value) {
        double[] next = envelope.clone();
        next[pose.ordinal() * 2 + axis.ordinal()] = Math.max(0.0, value);
        envelope = next;
    }

    public static synchronized double addEnv(RigPose pose, EnvAxis axis, double amount) {
        double[] next = envelope.clone();
        int i = pose.ordinal() * 2 + axis.ordinal();
        double v = Math.max(0.0, next[i] + amount);
        next[i] = v;
        envelope = next;
        return v;
    }
}
