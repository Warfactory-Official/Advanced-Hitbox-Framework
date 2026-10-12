package com.norwood.ahf.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hooks are global and append-only: one test, ordered steps. 1.20.1: no FML under JUnit => no {@code ItemStack}. */
class AhfHooksTest {

    @Test
    void hooksCompose() {
        assertFalse(AhfHooks.isHeldGun(null));
        AhfHooks.addHeldGun(stack -> false);
        AhfHooks.addHeldGun(stack -> stack == null);
        assertTrue(AhfHooks.isHeldGun(null), "second mod's gun predicate was shadowed");

        assertFalse(AhfHooks.isEnvelopeTarget(null));
        AhfHooks.addEnvelopeTarget(e -> e == null);
        assertTrue(AhfHooks.isEnvelopeTarget(null));

        assertEquals(0.0F, AhfHooks.gunAimProgress(null), "no provider => hold pose");
        AhfHooks.addGunAimProgress(0, e -> 0.25);
        assertEquals(0.25F, AhfHooks.gunAimProgress(null));
        AhfHooks.addGunAimProgress(10, e -> Double.NaN);
        assertEquals(0.25F, AhfHooks.gunAimProgress(null), "NaN must mean no opinion");
        AhfHooks.addGunAimProgress(5, e -> 0.75);
        assertEquals(0.75F, AhfHooks.gunAimProgress(null), "higher priority must win");
        AhfHooks.addGunAimProgress(5, e -> 0.5);
        assertEquals(0.75F, AhfHooks.gunAimProgress(null), "equal priority: first registered wins");
    }
}
