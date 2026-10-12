package com.norwood.ahf.part;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitboxPartTest {

    @Test
    void everyPartHasADisplayNameAndAPositiveHitWeight() {
        for (HitboxPart p : HitboxPart.VALUES) {
            assertFalse(p.getDisplayName().isBlank(), p + " has no display name");
            assertTrue(p.getHitWeight() > 0.0F, p + " would never be picked by the weighted fallback");
        }
    }

    @Test
    void theTorsoIsTheLikeliestRandomHitAndTheHeadTheLeast() {
        for (HitboxPart p : HitboxPart.VALUES) {
            assertTrue(HitboxPart.TORSO.getHitWeight() >= p.getHitWeight(), p + " outweighs the torso");
            assertTrue(HitboxPart.HEAD.getHitWeight() <= p.getHitWeight(), p + " is rarer than the head");
        }
    }
}
