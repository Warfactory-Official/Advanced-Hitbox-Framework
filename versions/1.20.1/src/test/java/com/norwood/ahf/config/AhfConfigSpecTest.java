package com.norwood.ahf.config;

import com.norwood.ahf.hit.HitAuthority;
import com.norwood.ahf.rig.RigTuning;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AhfConfigSpecTest {

    /** WFMedical default since 2026-08-09 (ENVELOPE let gap shots through). */
    @Test
    void hitRegistrationDefaultsToPrecise() {
        ForgeConfigSpec.ValueSpec spec = AhfConfigSpec.SPEC.getSpec().get(List.of("hitRegistration", "hitRegistrationMode"));
        assertEquals(HitRegMode.PRECISE, spec.getDefault());
    }

    /** Per pose {H, V}; WFMedical's tuned values. */
    @Test
    void everyPoseHasItsEnvelopeReach() {
        double[][] want = {{0.4, 0.2}, {0.5, 0.1}, {1.0, 0.3}};
        assertEquals(want.length, RigTuning.RigPose.VALUES.length);
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            assertEquals(want[pose.ordinal()][0], envDefault(pose.lower() + "Horizontal"), "horizontal " + pose);
            assertEquals(want[pose.ordinal()][1], envDefault(pose.lower() + "Vertical"), "vertical " + pose);
        }
        assertEquals(1.0, envDefault("stanceHorizontal"), "stance horizontal (medical DOWNED)");
        assertEquals(0.3, envDefault("stanceVertical"), "stance vertical (medical DOWNED)");
    }

    @Test
    void hitAuthorityDefaultsToServer() {
        ForgeConfigSpec.ValueSpec spec = AhfConfigSpec.SPEC.getSpec().get(List.of("authority", "hitAuthority"));
        assertEquals(HitAuthority.SERVER, spec.getDefault());
    }

    private static Object envDefault(String key) {
        ForgeConfigSpec.ValueSpec spec = AhfConfigSpec.SPEC.getSpec().get(List.of("hitRegistration", "envelopeReach", key));
        return spec.getDefault();
    }
}
