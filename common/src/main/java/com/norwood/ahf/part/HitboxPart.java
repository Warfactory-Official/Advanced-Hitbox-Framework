package com.norwood.ahf.part;

public enum HitboxPart {
    HEAD("Head", 0.10F, true, false, false),
    TORSO("Torso", 0.40F, true, false, false),
    LEFT_ARM("Left Arm", 0.12F, false, false, true),
    RIGHT_ARM("Right Arm", 0.12F, false, false, true),
    LEFT_LEG("Left Leg", 0.13F, false, true, false),
    RIGHT_LEG("Right Leg", 0.13F, false, true, false);

    public static final HitboxPart[] VALUES = values();
    private final String displayName;
    private final float hitWeight;
    private final boolean vital;
    private final boolean leg;
    private final boolean arm;

    HitboxPart(String displayName, float hitWeight, boolean vital, boolean leg, boolean arm) {
        this.displayName = displayName;
        this.hitWeight = hitWeight;
        this.vital = vital;
        this.leg = leg;
        this.arm = arm;
    }

    public static HitboxPart byOrdinal(int ordinal) {
        return (ordinal >= 0 && ordinal < VALUES.length) ? VALUES[ordinal] : TORSO;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getHitWeight() {
        return hitWeight;
    }

    public boolean isVital() {
        return vital;
    }

    public boolean isLeg() {
        return leg;
    }

    public boolean isArm() {
        return arm;
    }
}
