package com.norwood.ahf.gametest;

import com.mojang.authlib.GameProfile;
import com.norwood.ahf.hit.RayHit;
import com.norwood.ahf.part.HitboxPart;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.List;
import java.util.UUID;

final class Bodies {

    private Bodies() {
    }

    static FakePlayer player(GameTestHelper helper, BlockPos relative, float yaw) {
        FakePlayer p = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ahf_test"));
        BlockPos abs = helper.absolutePos(relative);
        p.snapTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, yaw, 0.0F);
        face(p, yaw);
        p.tickCount = 0;
        p.setPose(Pose.STANDING);
        return p;
    }

    static FakePlayer player(GameTestHelper helper) {
        return player(helper, new BlockPos(1, 1, 1), 0.0F);
    }

    /** Rig poses off yBodyRot; setYRot alone leaves the body at its old yaw. */
    static void face(FakePlayer p, float yaw) {
        p.setYRot(yaw);
        p.setXRot(0.0F);
        p.setYHeadRot(yaw);
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;
        p.yHeadRot = yaw;
        p.yHeadRotO = yaw;
    }

    static Vec3 forward(float yawDeg) {
        double y = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(y), 0.0, Math.cos(y));
    }

    static Vec3 right(float yawDeg) {
        double y = Math.toRadians(yawDeg);
        return new Vec3(-Math.cos(y), 0.0, -Math.sin(y));
    }

    static HitboxPart part(RayHit hit) {
        return hit == null ? null : hit.part();
    }

    static List<HitboxPart> parts(List<RayHit> hits) {
        return hits.stream().map(RayHit::part).toList();
    }

    static Vec3 local(FakePlayer v, float yaw, double r, double up, double fwd) {
        Vec3 feet = v.position();
        Vec3 rt = right(yaw);
        Vec3 fw = forward(yaw);
        return new Vec3(feet.x + rt.x * r + fw.x * fwd, feet.y + up, feet.z + rt.z * r + fw.z * fwd);
    }
}
