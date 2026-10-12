package com.norwood.ahf.hit;

import com.norwood.ahf.part.HitboxPart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by a {@code DamageSource} that already knows its strike segment (rounds). {@link HitboxApi} damage
 * entry points then classify and gap-test that segment at {@link #hitGameTime}, never the attacker's eye and look.
 */
public interface AhfHitSource {

    Vec3 hitFrom();

    Vec3 hitTo();

    /** Broadcast tick the victim is rewound to ({@code EntityHistory.snapshot}); no snapshot => live. */
    long hitGameTime();

    /** Part the caller already classified for this victim; null => classified from the segment. Non-null => never a gap. */
    @Nullable HitboxPart hitPart();
}
