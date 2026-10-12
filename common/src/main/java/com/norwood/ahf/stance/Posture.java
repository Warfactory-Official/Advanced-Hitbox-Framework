package com.norwood.ahf.stance;

/** Arma height steps per posture: STAND normal / mid / low, KNEEL high / normal / low, PRONE normal / high. */
public enum Posture {
    STAND(-2, 0), KNEEL(-1, 1), PRONE(0, 1), DOWNED(0, 0);

    public static final Posture[] VALUES = values();

    private final int minLevel;
    private final int maxLevel;

    Posture(int minLevel, int maxLevel) {
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
    }

    public boolean lying() {
        return this == PRONE || this == DOWNED;
    }

    public int minLevel() {
        return minLevel;
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** Side step (STAND / KNEEL) or side roll (PRONE). */
    public boolean sides() {
        return this != DOWNED;
    }
}
