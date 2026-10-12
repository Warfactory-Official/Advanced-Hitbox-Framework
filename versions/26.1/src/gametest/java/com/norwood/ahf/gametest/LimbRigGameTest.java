package com.norwood.ahf.gametest;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.HumanoidRig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

public class LimbRigGameTest {


    /** Yaw-0 local frame: x = right (world -X), y = up, z = front (world +Z). */
    private static Vec3 local(LivingEntity v, double lx, double ly, double lz) {
        Vec3 feet = v.position();
        return new Vec3(feet.x - lx, feet.y + ly, feet.z + lz);
    }

    private static void expectFrontal(GameTestHelper h, LivingEntity v, double lx, double ly, HitboxPart want) {
        expectRay(h, v, local(v, lx, ly, 2.0), local(v, lx, ly, -2.0), want);
    }

    private static void expectRay(GameTestHelper h, LivingEntity v, Vec3 from, Vec3 to, HitboxPart want) {
        HitboxPart got = Bodies.part(HitboxApi.classifyRay(v, from, to));
        h.assertTrue(got == want, "expected " + want + " but got " + got);
    }

    private static ArmorStand stand(GameTestHelper helper) {
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
        stand.setNoGravity(true);
        stand.setYRot(0.0F);
        stand.yBodyRot = 0.0F;
        stand.yBodyRotO = 0.0F;
        return stand;
    }

    @AhfGameTest
    public void neutralFrontalHead(GameTestHelper helper) {
        expectFrontal(helper, Bodies.player(helper), 0.0, 1.62, HitboxPart.HEAD);
        helper.succeed();
    }

    @AhfGameTest
    public void neutralFrontalTorso(GameTestHelper helper) {
        expectFrontal(helper, Bodies.player(helper), 0.0, 0.90, HitboxPart.TORSO);
        helper.succeed();
    }

    @AhfGameTest
    public void neutralFrontalRightLeg(GameTestHelper helper) {
        expectFrontal(helper, Bodies.player(helper), 0.113, 0.35, HitboxPart.RIGHT_LEG);
        helper.succeed();
    }

    @AhfGameTest
    public void sideOnRightArm(GameTestHelper helper) {
        FakePlayer v = Bodies.player(helper);
        expectRay(helper, v, local(v, 2.0, 1.06, 0.0), local(v, -2.0, 1.06, 0.0), HitboxPart.RIGHT_ARM);
        helper.succeed();
    }

    @AhfGameTest
    public void aimingBowArmForwardOfChest(GameTestHelper helper) {
        FakePlayer neutral = Bodies.player(helper);
        FakePlayer aiming = Bodies.player(helper);
        aiming.setItemInHand(InteractionHand.MAIN_HAND, Items.BOW.getDefaultInstance());
        aiming.startUsingItem(InteractionHand.MAIN_HAND);

        HumanoidRig.LocalRig rig = HumanoidRig.compute(aiming);
        Obb mainArm = aiming.getMainArm() == HumanoidArm.RIGHT ? rig.rightArm : rig.leftArm;
        helper.assertTrue(mainArm.center().z > 0.0,
                "aiming main-hand arm OBB must be in front of the body (local z>0); got z=" + mainArm.center().z);
        helper.assertTrue(mainArm.center().z > rig.torso.center().z,
                "aiming main-hand arm OBB must sit forward of the torso OBB");

        expectFrontal(helper, neutral, 0.20, 1.29, HitboxPart.TORSO);
        expectFrontal(helper, aiming, 0.20, 1.29, HitboxPart.RIGHT_ARM);
        helper.succeed();
    }

    @AhfGameTest
    public void crouchDropsHead(GameTestHelper helper) {
        FakePlayer standing = Bodies.player(helper);
        FakePlayer crouched = Bodies.player(helper);
        crouched.setPose(Pose.CROUCHING);
        double standHeadY = HumanoidRig.compute(standing).head.center().y;
        double crouchHeadY = HumanoidRig.compute(crouched).head.center().y;
        helper.assertTrue(crouchHeadY < standHeadY,
                "crouch head OBB must drop: crouched y=" + crouchHeadY + " vs standing y=" + standHeadY);
        helper.succeed();
    }

    /**
     * Crouch head top where the renderer draws it: vanilla crouch head pivot 4.2 px, cube top 8 px above, model scale
     * 0.9375 about y 1.501, then {@code PlayerRenderer.getRenderOffset} -2/16: 0.9375 (1.501 + 3.8/16) - 0.125.
     */
    @AhfGameTest
    public void crouchHeadTopMatchesTheDrawnModel(GameTestHelper helper) {
        FakePlayer crouched = Bodies.player(helper);
        crouched.setPose(Pose.CROUCHING);
        Obb head = HumanoidRig.compute(crouched).head;
        double top = head.center().y + head.half().y - com.norwood.ahf.config.AhfConfig.limbBoxPadding();
        double drawn = 0.9375 * (1.501 + 3.8 / 16.0) - 2.0 / 16.0;
        helper.assertTrue(Math.abs(top - drawn) < 0.005, "crouch head top " + top + " != drawn " + drawn);
        helper.succeed();
    }

    @AhfGameTest
    public void bandingFallbackReturnsTorsoMidFrontal(GameTestHelper helper) {
        ArmorStand v = stand(helper);
        double midY = v.getBoundingBox().getYsize() * 0.5;
        expectRay(helper, v, local(v, 0.0, midY, 2.0), local(v, 0.0, midY, -2.0), HitboxPart.TORSO);
        helper.succeed();
    }
}
