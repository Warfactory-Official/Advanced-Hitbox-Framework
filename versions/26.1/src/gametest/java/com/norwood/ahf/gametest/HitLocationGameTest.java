package com.norwood.ahf.gametest;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.hit.HitCategory;
import com.norwood.ahf.hit.HitSampler;
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.part.HitboxPart;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Banded AABB classification (no rig: armor stand). */
public class HitLocationGameTest {


    private static ArmorStand victim(GameTestHelper helper) {
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
        stand.setNoGravity(true);
        stand.setYRot(0.0F);
        stand.yBodyRot = 0.0F;
        stand.yBodyRotO = 0.0F;
        return stand;
    }

    private static double yAt(AABB box, double relY) {
        return box.minY + relY * box.getYsize();
    }

    private static void expect(GameTestHelper helper, ArmorStand v, Vec3 from, Vec3 to, HitboxPart want) {
        HitboxPart got = Bodies.part(HitboxApi.classifyRay(v, from, to));
        helper.assertTrue(got == want, "expected " + want + " but got " + got);
    }

    private static void frontal(GameTestHelper helper, double relY, HitboxPart want) {
        ArmorStand v = victim(helper);
        AABB box = v.getBoundingBox();
        double cx = box.getCenter().x;
        double y = yAt(box, relY);
        expect(helper, v, new Vec3(cx, y, box.maxZ + 1.0), new Vec3(cx, y, box.minZ - 1.0), want);
        helper.succeed();
    }

    private static void side(GameTestHelper helper, boolean fromMinX, HitboxPart want) {
        ArmorStand v = victim(helper);
        AABB box = v.getBoundingBox();
        double cz = box.getCenter().z;
        double y = yAt(box, 0.50);
        Vec3 minSide = new Vec3(box.minX - 1.0, y, cz);
        Vec3 maxSide = new Vec3(box.maxX + 1.0, y, cz);
        expect(helper, v, fromMinX ? minSide : maxSide, fromMinX ? maxSide : minSide, want);
        helper.succeed();
    }

    @AhfGameTest
    public void frontalHeadIsHead(GameTestHelper helper) {
        frontal(helper, 0.90, HitboxPart.HEAD);
    }

    @AhfGameTest
    public void frontalTorsoIsTorso(GameTestHelper helper) {
        frontal(helper, 0.50, HitboxPart.TORSO);
    }

    @AhfGameTest
    public void lowFrontalIsLeg(GameTestHelper helper) {
        frontal(helper, 0.20, HitboxPart.RIGHT_LEG);
    }

    @AhfGameTest
    public void sideOnIsArm(GameTestHelper helper) {
        side(helper, true, HitboxPart.RIGHT_ARM);
    }

    @AhfGameTest
    public void blastFromLeftIsLeftSide(GameTestHelper helper) {
        side(helper, false, HitboxPart.LEFT_ARM);
    }

    @AhfGameTest
    public void geometrylessPickFallsBackToWeighted(GameTestHelper helper) {
        ArmorStand v = victim(helper);
        DamageSource generic = helper.getLevel().damageSources().generic();
        helper.assertTrue(HitboxApi.classifyHit(v, generic, HitCategory.GENERIC) == null,
                "generic damage must have no reconstructable hit point");
        HitboxPart part = HitSampler.pick(v, generic, HitCategory.GENERIC, RandomSource.create(1234L));
        helper.assertTrue(part != null, "geometry-less pick must return a part via the sampler");
        helper.succeed();
    }
}
