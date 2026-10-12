package com.norwood.ahf.gametest;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.part.HitboxPart;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Yaw 0 = identity world->local rotation; sign/sin-cos slips only show at other yaws. */
@GameTestHolder(Ahf.MOD_ID)
@PrefixGameTestTemplate(false)
public class RigYawGameTest {

    private static final String TEMPLATE = "empty";
    static final float[] YAWS = {0.0F, 45.0F, 90.0F, 135.0F, 180.0F, -45.0F, -90.0F, 200.0F};

    private static final double HEAD_Y = 1.62;
    private static final double TORSO_Y = 0.90;
    private static final double LEG_Y = 0.35;
    private static final double ARM_Y = 1.06;

    private static void expect(GameTestHelper h, FakePlayer v, float yaw,
                               double r, double up, double fromFwd, double toFwd, HitboxPart want) {
        Vec3 from = Bodies.local(v, yaw, r, up, fromFwd);
        Vec3 to = Bodies.local(v, yaw, r, up, toFwd);
        HitboxPart got = Bodies.part(HitboxApi.classifyRay(v, from, to));
        h.assertTrue(got == want,
                "at yaw " + yaw + ", local(r=" + r + ",up=" + up + ") expected " + want + " but got " + got);
    }

    private static FakePlayer at(GameTestHelper helper, float yaw) {
        return Bodies.player(helper, new BlockPos(1, 1, 1), yaw);
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void frontalHeadAtEveryYaw(GameTestHelper helper) {
        for (float yaw : YAWS) {
            expect(helper, at(helper, yaw), yaw, 0.0, HEAD_Y, 2.0, -2.0, HitboxPart.HEAD);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void frontalTorsoAtEveryYaw(GameTestHelper helper) {
        for (float yaw : YAWS) {
            expect(helper, at(helper, yaw), yaw, 0.0, TORSO_Y, 2.0, -2.0, HitboxPart.TORSO);
        }
        helper.succeed();
    }

    /** Pairs with the frontal sweep: a 180-degree flip survives one, not both. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void shotFromBehindStillHitsTheHead(GameTestHelper helper) {
        for (float yaw : YAWS) {
            expect(helper, at(helper, yaw), yaw, 0.0, HEAD_Y, -2.0, 2.0, HitboxPart.HEAD);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void leftAndRightLegsAreNotMirroredAtAnyYaw(GameTestHelper helper) {
        for (float yaw : YAWS) {
            FakePlayer v = at(helper, yaw);
            expect(helper, v, yaw, 0.113, LEG_Y, 2.0, -2.0, HitboxPart.RIGHT_LEG);
            expect(helper, v, yaw, -0.113, LEG_Y, 2.0, -2.0, HitboxPart.LEFT_LEG);
        }
        helper.succeed();
    }

    /** Along the victim's own left-right axis: exercises the other half of the rotation. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void sideOnArmsHitTheNearArmAtEveryYaw(GameTestHelper helper) {
        for (float yaw : YAWS) {
            FakePlayer v = at(helper, yaw);
            Vec3 fromRight = Bodies.local(v, yaw, 2.0, ARM_Y, 0.0);
            Vec3 toLeft = Bodies.local(v, yaw, -2.0, ARM_Y, 0.0);
            HitboxPart got = Bodies.part(HitboxApi.classifyRay(v, fromRight, toLeft));
            helper.assertTrue(got == HitboxPart.RIGHT_ARM,
                    "at yaw " + yaw + " a ray entering from the victim's right must hit RIGHT_ARM, got " + got);
            HitboxPart mirrored = Bodies.part(HitboxApi.classifyRay(v, toLeft, fromRight));
            helper.assertTrue(mirrored == HitboxPart.LEFT_ARM,
                    "at yaw " + yaw + " the same ray reversed must hit LEFT_ARM, got " + mirrored);
        }
        helper.succeed();
    }

    /** Rig OBBs are yaw-independent (local frame); only the ray rotates => fixed world ray must change part. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void classificationIsActuallyYawSensitive(GameTestHelper helper) {
        HitboxPart from0 = worldRayFromPlusX(at(helper, 0.0F));
        HitboxPart from180 = worldRayFromPlusX(at(helper, 180.0F));
        helper.assertTrue(from0 == HitboxPart.LEFT_ARM,
                "facing +Z, a ray arriving from world +X enters the victim's left; got " + from0);
        helper.assertTrue(from180 == HitboxPart.RIGHT_ARM,
                "turned 180 degrees, the same world ray enters the victim's right; got " + from180);
        helper.succeed();
    }

    private static HitboxPart worldRayFromPlusX(FakePlayer v) {
        Vec3 feet = v.position();
        return Bodies.part(HitboxApi.classifyRay(v,
                new Vec3(feet.x + 2.0, feet.y + ARM_Y, feet.z),
                new Vec3(feet.x - 2.0, feet.y + ARM_Y, feet.z)));
    }
}
