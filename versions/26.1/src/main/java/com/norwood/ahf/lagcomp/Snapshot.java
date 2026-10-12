package com.norwood.ahf.lagcomp;

import com.norwood.ahf.rig.HumanoidRig;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Entity state broadcast to clients in tick {@code gameTime}.
 *
 * @param envelope registration box at capture ({@code box} when not an envelope target)
 * @param upright  {@code HitboxApi.isUprightHumanoid} at capture (banded fallback)
 * @param aiming   {@code HitboxApi.isAimingWeapon} at capture (banded fallback)
 * @param rig      player rig at capture; null => rig unusable then (banded fallback) or not a player
 */
public record Snapshot(long gameTime, Vec3 pos, AABB box, AABB envelope, float bodyYaw, boolean upright,
                       boolean aiming, @Nullable HumanoidRig.LocalRig rig) {
}
