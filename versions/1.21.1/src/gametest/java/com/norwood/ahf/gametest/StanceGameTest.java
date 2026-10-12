package com.norwood.ahf.gametest;

import com.mojang.authlib.GameProfile;
import com.norwood.ahf.Ahf;
import com.norwood.ahf.lagcomp.EntityHistory;
import com.norwood.ahf.lagcomp.Snapshot;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.stance.AhfStance;
import com.norwood.ahf.stance.AhfStances;
import com.norwood.ahf.stance.PostureStance;
import com.norwood.ahf.stance.Posture;
import com.norwood.ahf.stance.Rest;
import com.norwood.ahf.stance.StanceLogic;
import com.norwood.ahf.stance.StancePose;
import com.norwood.ahf.stance.StanceRequest;
import com.norwood.ahf.stance.StanceState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Ahf.MOD_ID)
@PrefixGameTestTemplate(false)
public class StanceGameTest {

    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-4;
    /** Lean eases at {@link StanceLogic#LEAN_RATE} per tick; full lean after 5. */
    private static final int LEAN_TICKS = 7;
    private static final StanceRequest LEAN_RIGHT = StanceRequest.STAND.withLean(1.0F);

    /** Template layer 0 is the stone floor, lid at layer 4 => feet on layer 2, 2.0 of headroom. */
    private static FakePlayer spawn(GameTestHelper helper, float yaw) {
        FakePlayer p = Bodies.player(helper, new BlockPos(1, 2, 1), yaw);
        helper.getLevel().addFreshEntity(p);
        return p;
    }

    private static void assertDims(GameTestHelper helper, FakePlayer p, Pose pose, double height, double eye,
                                   String what) {
        helper.assertTrue(p.getPose() == pose, what + ": pose " + p.getPose() + " != " + pose);
        helper.assertTrue(Math.abs(p.getBbHeight() - height) < EPS, what + ": height " + p.getBbHeight() + " != " + height);
        helper.assertTrue(Math.abs(p.getBoundingBox().getYsize() - height) < EPS, what + ": box not refreshed");
        helper.assertTrue(Math.abs(p.getEyeHeight() - eye) < EPS, what + ": eye " + p.getEyeHeight() + " != " + eye);
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void postureSetsDimensionsAndEyeHeight(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        try {
            helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.KNEEL)), "kneel refused");
            assertDims(helper, p, Pose.CROUCHING, StanceLogic.KNEEL_HEIGHT, StanceLogic.KNEEL_EYE, "KNEEL");
            helper.assertTrue(p.getForcedPose() == Pose.CROUCHING, "kneel not forced");
            helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.PRONE)), "prone refused");
            assertDims(helper, p, Pose.SWIMMING, 0.6, 0.4, "PRONE");
            helper.assertTrue(AhfStance.request(p, StanceRequest.STAND), "stand refused");
            assertDims(helper, p, Pose.STANDING, 1.8, 1.62, "STAND");
            helper.assertTrue(p.getForcedPose() == null, "stand left a forced pose");
        } finally {
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void aTallerPostureNeedsTheSpace(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        try {
            helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.PRONE)), "prone refused");
            helper.setBlock(new BlockPos(1, 3, 1), Blocks.STONE);
            helper.assertFalse(AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.KNEEL)),
                    "kneel (1.2) accepted under a 1.0 ceiling");
            helper.assertTrue(AhfStance.posture(p) == Posture.PRONE, "refused request changed the posture");
            assertDims(helper, p, Pose.SWIMMING, 0.6, 0.4, "still PRONE");
            helper.setBlock(new BlockPos(1, 3, 1), Blocks.AIR);
            helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.KNEEL)),
                    "kneel refused with the ceiling gone");
        } finally {
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void leanOffsetsTheEyeNotEyeY(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        helper.assertTrue(AhfStance.request(p, LEAN_RIGHT), "lean refused");
        helper.runAfterDelay(LEAN_TICKS, () -> {
            try {
                Vec3 feet = p.position();
                Vec3 eye = p.getEyePosition();
                double arm = 1.62 - StancePose.PIVOT_Y;
                double a = Math.toRadians(StanceLogic.LEAN_MAX_DEGREES);
                Vec3 right = Bodies.right(0.0F);
                double lateral = eye.subtract(feet).dot(right);
                helper.assertTrue(Math.abs(lateral - arm * Math.sin(a)) < EPS,
                        "eye lateral " + lateral + " != " + arm * Math.sin(a));
                helper.assertTrue(Math.abs(eye.y - feet.y - (1.62 + arm * (Math.cos(a) - 1.0))) < EPS,
                        "eye height under lean " + (eye.y - feet.y));
                helper.assertTrue(p.getEyeY() == feet.y + p.getEyeHeight(), "getEyeY must ignore lean");
                helper.assertTrue(p.getEyePosition(1.0F).distanceTo(eye) < EPS, "partial-tick overload disagrees");
            } finally {
                p.discard();
            }
            helper.succeed();
        });
    }

    /** Player hugging cover on its right; full lean right. */
    private void leanIntoCover(GameTestHelper helper, Block cover, double faceInBlock) {
        FakePlayer p = spawn(helper, 180.0F);
        helper.setBlock(new BlockPos(2, 2, 1), cover);
        helper.setBlock(new BlockPos(2, 3, 1), cover);
        BlockPos coverAbs = helper.absolutePos(new BlockPos(2, 3, 1));
        double face = coverAbs.getX() + faceInBlock;
        p.moveTo(face - 0.3, p.getY(), coverAbs.getZ() + 0.5, 180.0F, 0.0F);
        Bodies.face(p, 180.0F);
        helper.assertTrue(AhfStance.request(p, LEAN_RIGHT), "lean refused");
        helper.runAfterDelay(LEAN_TICKS, () -> {
            try {
                Vec3 centre = new Vec3(p.getX(), p.getEyeY(), p.getZ());
                double unclamped = new StancePose(Posture.STAND, 1.0, 0, 0, 0, 0, 0, false).eyeOffsetLocal(p.getEyeHeight()).x;
                helper.assertTrue(centre.x + unclamped > face + 0.125,
                        "setup: an unclamped lean must reach past the cover (" + (centre.x + unclamped) + ")");
                Vec3 eye = p.getEyePosition();
                helper.assertTrue(eye.x < face, "eye " + eye.x + " passed the cover face " + face);
                helper.assertTrue(eye.x <= face - 0.08, "eye " + eye.x + " inside the clamp margin of " + face);
                helper.assertTrue(eye.x > centre.x + 0.05, "clamp removed the lean entirely");
                helper.assertTrue(p.getEyePosition(0.5F).x <= face - 0.08, "partial-tick eye passed the cover");
                helper.assertFalse(p.isInWall(), "leaning at cover puts the player in the wall (suffocation damage)");
            } finally {
                p.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void leanStopsShortOfAWall(GameTestHelper helper) {
        leanIntoCover(helper, Blocks.STONE, 0.0);
    }

    /** Isolated pane = centre post, x in [7/16, 9/16]; 0.43+ of lean would put the eye beyond it. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void leanStopsShortOfAThinPane(GameTestHelper helper) {
        leanIntoCover(helper, Blocks.GLASS_PANE, 7.0 / 16.0);
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void leanRotatesTheRigAndItsHistory(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        HumanoidRig.LocalRig upright = HumanoidRig.compute(p);
        helper.assertTrue(AhfStance.request(p, LEAN_RIGHT), "lean refused");
        helper.runAfterDelay(LEAN_TICKS, () -> {
            try {
                HumanoidRig.LocalRig leaned = HumanoidRig.compute(p);
                double a = Math.toRadians(StanceLogic.LEAN_MAX_DEGREES);
                double headArm = upright.head.center().y - StancePose.PIVOT_Y;
                double dx = leaned.head.center().x - upright.head.center().x;
                helper.assertTrue(Math.abs(dx - headArm * Math.sin(a)) < 1.0e-3,
                        "head moved " + dx + " right, want " + headArm * Math.sin(a));
                double torsoTilt = Math.toDegrees(Math.acos(Math.abs(leaned.torso.axisY().y)));
                helper.assertTrue(Math.abs(torsoTilt - StanceLogic.LEAN_MAX_DEGREES) < 0.5,
                        "torso tilt " + torsoTilt);
                helper.assertTrue(Math.abs(leaned.rightLeg.axisY().y) > 0.999 && Math.abs(leaned.leftLeg.axisY().y) > 0.999,
                        "legs must stay vertical under lean");
                helper.assertTrue(StancePose.of(p, 1.0F) != null && com.norwood.ahf.stance.AhfStances.active(p)
                        == com.norwood.ahf.stance.PostureStance.UPRIGHT, "leaning player not on the upright stance");
                ServerLevel level = helper.getLevel();
                Snapshot snap = EntityHistory.snapshot(p, level.getGameTime());
                helper.assertTrue(snap != null && snap.rig() != null, "no rig in history");
                helper.assertTrue(Math.abs(snap.rig().head.center().x - leaned.head.center().x) < 1.0e-6,
                        "history rig is not the leaned rig");
            } finally {
                p.discard();
            }
            helper.succeed();
        });
    }

    /** Arma height steps reshape the box per posture; a level outside the posture's range is refused. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void levelStepsReshapeTheBox(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        try {
            helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withLevel(-2)), "stand low refused");
            assertDims(helper, p, Pose.STANDING, 1.4, 1.22, "stand low");
            helper.assertFalse(AhfStance.request(p, StanceRequest.STAND.withLevel(1)), "stand above normal accepted");
            StanceRequest kneel = StanceRequest.STAND.withPosture(Posture.KNEEL);
            helper.assertTrue(AhfStance.request(p, kneel.withLevel(1)), "kneel high refused");
            assertDims(helper, p, Pose.CROUCHING, 1.4, 1.2, "kneel high");
            helper.assertTrue(AhfStance.request(p, kneel.withLevel(-1)), "kneel low refused");
            assertDims(helper, p, Pose.CROUCHING, 1.0, 0.8, "kneel low");
            helper.assertFalse(AhfStance.request(p, kneel.withLevel(-2)), "kneel below low accepted");
            helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.PRONE).withLevel(1)),
                    "prone high refused");
            assertDims(helper, p, Pose.SWIMMING, 0.8, 0.6, "prone high");
            helper.assertTrue(AhfStance.request(p, StanceRequest.STAND), "stand refused");
            assertDims(helper, p, Pose.STANDING, 1.8, 1.62, "stand");
        } finally {
            p.discard();
        }
        helper.succeed();
    }

    /** Side step right: head moved by the clip's step right and drop, torso with it; lean still allowed. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void sideStepMovesTheHitRig(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        HumanoidRig.LocalRig upright = HumanoidRig.compute(p);
        StancePose step = new StancePose(Posture.STAND, 0, 0, 1, 0, 0, 0, false);
        helper.assertTrue(step.shift() > 0.2, "step " + step.shift());
        helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withSide(1)), "side step refused");
        helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withSide(1).withLean(0.5F)), "lean refused");
        helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withSide(1)), "lean back refused");
        helper.runAfterDelay(LEAN_TICKS + 2, () -> {
            try {
                HumanoidRig.LocalRig stepped = HumanoidRig.compute(p);
                Vec3 head = stepped.head.center().subtract(upright.head.center());
                helper.assertTrue(Math.abs(head.x - step.shift()) < 1.0e-3 && Math.abs(head.y + step.drop()) < 1.0e-3,
                        "head moved " + head);
                Vec3 torso = stepped.torso.center().subtract(upright.torso.center());
                helper.assertTrue(Math.abs(torso.x - step.shift()) < 0.05, "torso moved " + torso);
            } finally {
                p.discard();
            }
            helper.succeed();
        });
    }

    /** Prone roll: refused standing and while rolling; runs {@link StanceLogic#ROLL_TICKS}, rig turned mid-roll. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void proneRollRunsOnce(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        helper.assertFalse(AhfStance.roll(p, 1), "roll accepted standing");
        helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.PRONE)), "prone refused");
        ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, p, 1.0F, "swimAmount");
        ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, p, 1.0F, "swimAmountO");
        HumanoidRig.LocalRig flat = HumanoidRig.compute(p);
        helper.assertTrue(AhfStance.roll(p, 1), "roll refused prone");
        helper.assertFalse(AhfStance.roll(p, -1), "second roll accepted mid-roll");
        helper.runAfterDelay(StanceLogic.ROLL_TICKS / 2, () -> {
            HumanoidRig.LocalRig mid = HumanoidRig.compute(p);
            double turned = Math.toDegrees(Math.acos(Math.min(1.0, Math.abs(mid.torso.axisX().dot(flat.torso.axisX())))));
            helper.assertTrue(turned > 30.0, "torso turned " + turned + " deg mid-roll");
        });
        helper.runAfterDelay(StanceLogic.ROLL_TICKS + 1, () -> {
            try {
                helper.assertFalse(AhfStance.get(p).rolling(), "roll outlived its ticks");
                helper.assertTrue(AhfStance.roll(p, -1), "next roll refused");
            } finally {
                p.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void downedLocksOutRequestsAndWeapons(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        try {
            helper.assertFalse(AhfStance.weaponsBlocked(p), "weapons blocked while standing");
            helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.KNEEL)), "kneel refused");
            AhfStance.setDowned(p, true);
            helper.assertTrue(AhfStance.weaponsBlocked(p), "downed did not block weapons");
            helper.assertTrue(AhfStance.posture(p) == Posture.DOWNED, "downed not effective");
            assertDims(helper, p, Pose.SWIMMING, 0.6, 0.4, "DOWNED");
            helper.assertFalse(AhfStance.request(p, StanceRequest.STAND), "stand accepted while downed");
            helper.assertFalse(AhfStance.request(p, LEAN_RIGHT), "lean accepted while downed");
            StanceLogic.handleIntent(p, 9, StanceRequest.STAND.withPosture(Posture.PRONE));
            StanceState s = AhfStance.get(p);
            helper.assertTrue(s.seq() == 9 && s.requested() == Posture.KNEEL,
                    "refused intent must echo seq 9 and keep KNEEL; got " + s.seq() + " " + s.requested());
            AhfStance.setDowned(p, false);
            helper.assertFalse(AhfStance.weaponsBlocked(p), "release kept weapons blocked");
            helper.assertTrue(AhfStance.posture(p) == Posture.KNEEL, "release did not return to the request");
            assertDims(helper, p, Pose.CROUCHING, StanceLogic.KNEEL_HEIGHT, StanceLogic.KNEEL_EYE, "after DOWNED");
            StanceLogic.handleIntent(p, 10, StanceRequest.STAND);
            helper.assertTrue(s.seq() == 10 && s.requested() == Posture.STAND, "accepted intent not applied");
        } finally {
            p.discard();
        }
        helper.succeed();
    }

    /** DOWNED lies on its back: head behind the hips (-90 deg about body-right), body flat; PRONE keeps it ahead. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void downedRigLiesOnItsBack(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        AhfStance.setDowned(p, true);
        // FakePlayer never ticks: swim amount (vanilla tilt progress) set to its settled value.
        ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, p, 1.0F, "swimAmount");
        ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, p, 1.0F, "swimAmountO");
        helper.runAfterDelay(1, () -> {
            try {
                HumanoidRig.LocalRig rig = HumanoidRig.compute(p);
                Vec3 head = rig.head.center();
                Vec3 torso = rig.torso.center();
                helper.assertTrue(head.z < torso.z - 0.3, "downed head " + head + " not behind torso " + torso);
                helper.assertTrue(Math.abs(head.y - torso.y) < 0.2, "downed body not flat: head " + head
                        + ", torso " + torso);
                AhfStance.setDowned(p, false);
            } finally {
                p.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void deathClearsStanceAndLocks(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        try {
            helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.KNEEL).withLean(0.5F)),
                    "kneel lean refused");
            AhfStance.setDowned(p, true);
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, helper.getLevel().damageSources().generic()));
            StanceState s = AhfStance.get(p);
            helper.assertTrue(s.effective() == Posture.STAND && s.leanTarget() == 0.0F && !s.downed(),
                    "death kept " + s.effective() + " lean " + s.leanTarget() + " downed " + s.downed());
            helper.assertTrue(p.getForcedPose() == null, "death kept a forced pose (it would override DYING)");
        } finally {
            p.discard();
        }
        helper.succeed();
    }

    /** DOWNED ignores water + mount; vanilla would stand the body up under a downed camera. Inputs emulated. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void downedHoldsInWaterAndMounted(GameTestHelper helper) {
        boolean[] env = {false};
        FakePlayer p = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ahf_downed")) {
            @Override
            public boolean isPassenger() {
                return env[0];
            }

            @Override
            public boolean isInWater() {
                return env[0];
            }
        };
        BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(p);
        helper.runAfterDelay(1, () -> {
            AhfStance.setDowned(p, true);
            env[0] = true;
            helper.runAfterDelay(1, () -> {
                try {
                    helper.assertFalse(StanceLogic.suspended(p), "downed suspended in water + mounted");
                    assertDims(helper, p, Pose.SWIMMING, 0.6, 0.4, "DOWNED in water + mounted");
                    helper.assertTrue(p.getForcedPose() == Pose.SWIMMING, "downed pose released");
                    helper.assertTrue(AhfStances.active(p) == PostureStance.LYING,
                            "rig stance " + AhfStances.active(p) + " != LYING");
                    helper.assertTrue(StancePose.of(p, 1.0F) != null, "downed rig unposed");
                    AhfStance.setDowned(p, false);
                    helper.assertTrue(StanceLogic.suspended(p), "undowned rider in water not suspended");
                } finally {
                    p.discard();
                }
                helper.succeed();
            });
        });
    }

    /** FakePlayer refuses {@code startRiding}; a rider is emulated through {@code isPassenger}, the only input read. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void mountingClearsStance(GameTestHelper helper) {
        boolean[] riding = {false};
        FakePlayer p = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ahf_rider")) {
            @Override
            public boolean isPassenger() {
                return riding[0];
            }
        };
        BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(p);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.KNEEL).withLean(0.5F)),
                    "kneel lean refused");
            riding[0] = true;
            p.yBodyRot = 77.0F;
            helper.assertFalse(AhfStance.request(p, StanceRequest.STAND.withPosture(Posture.PRONE)),
                    "prone accepted while mounted");
            helper.runAfterDelay(1, () -> {
                try {
                    StanceState s = AhfStance.get(p);
                    helper.assertTrue(p.yBodyRot == 77.0F && s.bodyYaw(1.0F) == 77.0F,
                            "mounted: the vehicle's body yaw 77 was replaced by " + p.yBodyRot + " / " + s.bodyYaw(1.0F));
                    helper.assertTrue(s.requested() == Posture.STAND && s.leanTarget() == 0.0F,
                            "mount kept " + s.requested() + " lean " + s.leanTarget());
                    helper.assertTrue(p.getForcedPose() == null, "mount kept a forced pose");
                    helper.assertTrue(StancePose.of(p, 1.0F) == null, "mounted rig still posed");
                } finally {
                    p.discard();
                }
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void bodyYawIsOwnedAndLagsTheCameraByAtMost45(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        helper.runAfterDelay(1, () -> {
            p.setYRot(90.0F);
            helper.runAfterDelay(1, () -> {
                StanceState s = AhfStance.get(p);
                helper.assertTrue(Math.abs(s.bodyYaw(1.0F) - 45.0F) < EPS, "body yaw " + s.bodyYaw(1.0F));
                helper.assertTrue(p.yBodyRot == s.bodyYaw(1.0F), "yBodyRot not written from the stance");
                helper.runAfterDelay(30, () -> {
                    try {
                        helper.assertTrue(Math.abs(p.yBodyRot - 90.0F) < 0.5F, "body never caught up: " + p.yBodyRot);
                    } finally {
                        p.discard();
                    }
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void intentsBeyondThePerTickCapAreRefusedButAcked(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        helper.runAfterDelay(1, () -> {
            for (int i = 1; i <= StanceLogic.MAX_INTENTS_PER_TICK + 1; i++) {
                StanceLogic.handleIntent(p, i, StanceRequest.STAND.withLean(i * 0.1F));
            }
            StanceState s = AhfStance.get(p);
            int cap = StanceLogic.MAX_INTENTS_PER_TICK;
            helper.assertTrue(s.seq() == cap + 1, "over-cap intent not acked: seq " + s.seq());
            helper.assertTrue(s.leanTarget() == cap * 0.1F, "over-cap intent applied: lean " + s.leanTarget());
            helper.runAfterDelay(1, () -> {
                try {
                    StanceLogic.handleIntent(p, cap + 2, StanceRequest.STAND.withLean(0.9F));
                    helper.assertTrue(s.leanTarget() == 0.9F, "the cap did not reset next tick");
                } finally {
                    p.discard();
                }
                helper.succeed();
            });
        });
    }

    /** Crawl lock + stale lean: the body change is refused, the ADS toggle in the same request is not. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void adsTogglesWhenTheBodyChangeIsRefused(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        try {
            AhfStance.setCrawl(p, true);
            StanceRequest leanAds = new StanceRequest(Posture.STAND, 0, 0, 0.5F, Rest.NONE, true, 3.0F, -2.0F);
            StanceLogic.handleIntent(p, 1, leanAds);
            StanceState s = AhfStance.get(p);
            helper.assertTrue(s.ads() && s.aimYaw() == 3.0F && s.aimPitch() == -2.0F,
                    "ADS refused with the lean: ads " + s.ads());
            helper.assertTrue(s.leanTarget() == 0.0F && s.seq() == 1, "lean applied while crawling");
            helper.assertTrue(AhfStance.request(p, leanAds.withLean(0.0F)), "a valid full request refused");
        } finally {
            p.discard();
        }
        helper.succeed();
    }

    /** DOWNED drops ADS: released later, the player does not come up already aiming. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void downedDropsAds(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        try {
            helper.assertTrue(AhfStance.request(p, new StanceRequest(Posture.STAND, 0, 0, 0.0F, Rest.NONE, true, 0.0F, 0.0F)), "ads refused");
            AhfStance.setDowned(p, true);
            AhfStance.setDowned(p, false);
            helper.assertFalse(AhfStance.get(p).ads(), "ads survived DOWNED");
        } finally {
            p.discard();
        }
        helper.succeed();
    }

    /** Server-side changes are synced once, at the next tick's Pre, stamped with that tick (== its history key). */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void aMidTickChangeIsStampedWithTheNextTick(GameTestHelper helper) {
        FakePlayer p = spawn(helper, 0.0F);
        helper.runAfterDelay(1, () -> {
            StanceState s = AhfStance.get(p);
            long before = s.stamp();
            AhfStance.setDowned(p, true);
            helper.assertTrue(s.stamp() == before, "flushed mid-tick with stamp " + s.stamp());
            helper.runAfterDelay(1, () -> {
                try {
                    long now = helper.getLevel().getGameTime();
                    helper.assertTrue(s.stamp() == now, "stamp " + s.stamp() + " != the flushing tick " + now);
                } finally {
                    p.discard();
                }
                helper.succeed();
            });
        });
    }
}
