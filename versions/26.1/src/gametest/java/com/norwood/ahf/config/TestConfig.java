package com.norwood.ahf.config;

import com.norwood.ahf.hit.HitAuthority;

public final class TestConfig {

    private TestConfig() {
    }

    public static void hitGapRejectTolerance(double v) {
        AhfConfig.setHitGapRejectTolerance(v);
    }

    public static void hitRegistrationMode(HitRegMode v) {
        AhfConfig.setHitRegistrationMode(v);
    }

    public static void hitAuthority(HitAuthority v) {
        AhfConfig.setHitAuthority(v);
    }

    public static void lagCompNonPlayerRange(double v) {
        AhfConfig.setLagCompNonPlayerRange(v);
    }

    public static void reset() {
        AhfConfigSpec.bind();
    }
}
