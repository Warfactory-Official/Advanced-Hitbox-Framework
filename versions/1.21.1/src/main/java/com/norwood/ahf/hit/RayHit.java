package com.norwood.ahf.hit;

import com.norwood.ahf.part.HitboxPart;
import net.minecraft.world.phys.Vec3;

/**
 * Part struck by a segment.
 *
 * @param t    entry along {@code from -> to}, in [0, 1]
 * @param limb true => entered a limb box; false => envelope / bounding-box fallback
 */
public record RayHit(HitboxPart part, double t, boolean limb) {

    public Vec3 point(Vec3 from, Vec3 to) {
        return from.lerp(to, t);
    }
}
