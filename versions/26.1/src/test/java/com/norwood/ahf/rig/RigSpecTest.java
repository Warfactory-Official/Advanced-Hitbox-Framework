package com.norwood.ahf.rig;

import com.norwood.ahf.part.HitboxPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RigSpecTest {

    private static final int FIELDS = RigTuning.FIELDS;
    private static final int SX = RigTuning.Field.SX.ordinal();
    private static final int SY = RigTuning.Field.SY.ordinal();
    private static final int SZ = RigTuning.Field.SZ.ordinal();

    @AfterEach
    void resetSpec() {
        HumanoidRig.setSpec(null);
    }

    @Nested
    @DisplayName("built-in defaults")
    class Defaults {

        @Test
        void everyLimbHasACompleteBaseRow() {
            for (HitboxPart limb : HitboxPart.VALUES) {
                double[] row = HumanoidRig.baseSpec(limb);
                assertNotNull(row, limb + " has no base row");
                assertEquals(FIELDS, row.length, limb + " base row is the wrong width");
                for (double v : row) {
                    assertTrue(Double.isFinite(v), limb + " base row holds a non-finite value");
                }
            }
        }

        @Test
        void everyLimbHasAPositiveVolume() {
            for (HitboxPart limb : HitboxPart.VALUES) {
                double[] row = HumanoidRig.baseSpec(limb);
                assertTrue(row[SX] > 0.0, limb + " has non-positive SX");
                assertTrue(row[SY] > 0.0, limb + " has non-positive SY");
                assertTrue(row[SZ] > 0.0, limb + " has non-positive SZ");
            }
        }

        @Test
        void standingPoseAdjustIsAllZero() {
            for (HitboxPart limb : HitboxPart.VALUES) {
                assertArrayEquals(new double[FIELDS],
                        HumanoidRig.poseAdjustSpec(RigTuning.RigPose.STANDING, limb),
                        0.0, "STANDING adjust for " + limb + " should be all-zero");
            }
        }

        @Test
        void everyPoseAndHandCombinationIsPopulated() {
            for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
                for (HitboxPart limb : HitboxPart.VALUES) {
                    assertEquals(FIELDS, HumanoidRig.poseAdjustSpec(pose, limb).length,
                            "missing pose adjust for " + pose + "/" + limb);
                }
                for (RigTuning.HandAction hand : RigTuning.HandAction.VALUES) {
                    for (HitboxPart limb : HitboxPart.VALUES) {
                        assertEquals(FIELDS, HumanoidRig.handAdjustSpec(pose, hand, limb).length,
                                "missing hand adjust for " + pose + "/" + hand + "/" + limb);
                    }
                }
            }
        }

        @Test
        void handActionNoneNeverAdjustsAnything() {
            for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
                for (HitboxPart limb : HitboxPart.VALUES) {
                    assertArrayEquals(new double[FIELDS],
                            HumanoidRig.handAdjustSpec(pose, RigTuning.HandAction.NONE, limb), 0.0,
                            "NONE hand action adjusted " + pose + "/" + limb);
                }
            }
        }

        @Test
        void onlyArmsAreTouchedByAHandAction() {
            for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
                for (RigTuning.HandAction hand : RigTuning.HandAction.VALUES) {
                    for (HitboxPart limb : HitboxPart.VALUES) {
                        if (limb == HitboxPart.LEFT_ARM || limb == HitboxPart.RIGHT_ARM) {
                            continue;
                        }
                        assertArrayEquals(new double[FIELDS], HumanoidRig.handAdjustSpec(pose, hand, limb), 0.0,
                                hand + " while " + pose + " moved a non-arm limb (" + limb + ")");
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("copy semantics")
    class Copies {

        @Test
        void accessorsHandOutCopiesNotTheLiveArrays() {
            double[] first = HumanoidRig.baseSpec(HitboxPart.HEAD);
            double original = first[SX];
            first[SX] = 999.0;
            assertEquals(original, HumanoidRig.baseSpec(HitboxPart.HEAD)[SX], 0.0,
                    "mutating the returned base row changed the live spec");

            double[] pose = HumanoidRig.poseAdjustSpec(RigTuning.RigPose.CROUCHING, HitboxPart.HEAD);
            double poseOriginal = pose[SY];
            pose[SY] = 999.0;
            assertEquals(poseOriginal,
                    HumanoidRig.poseAdjustSpec(RigTuning.RigPose.CROUCHING, HitboxPart.HEAD)[SY], 0.0);

            double[] hand = HumanoidRig.handAdjustSpec(
                    RigTuning.RigPose.STANDING, RigTuning.HandAction.GUN, HitboxPart.RIGHT_ARM);
            double handOriginal = hand[SZ];
            hand[SZ] = 999.0;
            assertEquals(handOriginal, HumanoidRig.handAdjustSpec(
                    RigTuning.RigPose.STANDING, RigTuning.HandAction.GUN, HitboxPart.RIGHT_ARM)[SZ], 0.0);
        }

        @Test
        void specCopyIsDeepAtEveryNestingLevel() {
            RigSpec a = HumanoidRig.defaultSpec();
            RigSpec b = a.copy();

            assertNotSame(a.base[0], b.base[0]);
            assertNotSame(a.poseAdjust[1][0], b.poseAdjust[1][0]);
            assertNotSame(a.handAdjust[0][1][0], b.handAdjust[0][1][0]);

            b.base[0][SX] = 123.0;
            b.poseAdjust[1][0][SX] = 123.0;
            b.handAdjust[0][1][0][SX] = 123.0;

            assertEquals(123.0, b.base[0][SX], 0.0);
            assertTrue(a.base[0][SX] != 123.0, "copy() aliased the base rows");
            assertTrue(a.poseAdjust[1][0][SX] != 123.0, "copy() aliased the pose rows");
            assertTrue(a.handAdjust[0][1][0][SX] != 123.0, "copy() aliased the hand rows");
        }

        @Test
        void defaultSpecHandsOutAFreshCopyEachCall() {
            RigSpec one = HumanoidRig.defaultSpec();
            RigSpec two = HumanoidRig.defaultSpec();
            assertNotSame(one.base, two.base);
            one.base[0][SX] = 555.0;
            assertTrue(two.base[0][SX] != 555.0, "defaultSpec() handed out the shared defaults");
        }
    }

    @Nested
    @DisplayName("override file")
    class OverrideFile {

        @Test
        void writeThenReloadRoundTripsTheGeometry(@TempDir Path dir) throws Exception {
            RigSpec edited = HumanoidRig.defaultSpec();
            edited.base[HitboxPart.HEAD.ordinal()][SX] = 42.5;
            edited.poseAdjust[RigTuning.RigPose.PRONE.ordinal()][HitboxPart.TORSO.ordinal()][SY] = -3.25;

            RigSpecIO.write(dir, edited);
            RigSpecIO.reload(dir);

            assertEquals(42.5, HumanoidRig.baseSpec(HitboxPart.HEAD)[SX], 1.0e-9);
            assertEquals(-3.25,
                    HumanoidRig.poseAdjustSpec(RigTuning.RigPose.PRONE, HitboxPart.TORSO)[SY], 1.0e-9);
        }

        @Test
        void anAbsentFileResetsToTheBuiltInDefaults(@TempDir Path dir) throws Exception {
            RigSpec edited = HumanoidRig.defaultSpec();
            edited.base[HitboxPart.HEAD.ordinal()][SX] = 42.5;
            RigSpecIO.write(dir, edited);
            RigSpecIO.reload(dir);
            assertEquals(42.5, HumanoidRig.baseSpec(HitboxPart.HEAD)[SX], 1.0e-9);

            Files.delete(dir.resolve(RigSpecIO.FILE_NAME));
            RigSpecIO.reload(dir);

            assertEquals(HumanoidRig.defaultSpec().base[HitboxPart.HEAD.ordinal()][SX],
                    HumanoidRig.baseSpec(HitboxPart.HEAD)[SX], 1.0e-9,
                    "deleting the override should fall back to the built-in geometry");
        }

        @Test
        void aCorruptFileFallsBackInsteadOfThrowing(@TempDir Path dir) throws Exception {
            Files.writeString(dir.resolve(RigSpecIO.FILE_NAME), "{ this is not json", StandardCharsets.UTF_8);
            RigSpecIO.reload(dir);

            assertEquals(HumanoidRig.defaultSpec().base[HitboxPart.HEAD.ordinal()][SX],
                    HumanoidRig.baseSpec(HitboxPart.HEAD)[SX], 1.0e-9,
                    "a corrupt override should leave the built-in geometry active");
        }

        @Test
        void aPartialFileOnlyReplacesTheRowsItNames(@TempDir Path dir) throws Exception {
            double[] head = HumanoidRig.defaultSpec().base[HitboxPart.HEAD.ordinal()];
            head[SX] = 11.0;
            String partial = "{ \"base\": { \"HEAD\": " + java.util.Arrays.toString(head) + " } }";
            Files.writeString(dir.resolve(RigSpecIO.FILE_NAME), partial, StandardCharsets.UTF_8);
            RigSpecIO.reload(dir);

            assertEquals(11.0, HumanoidRig.baseSpec(HitboxPart.HEAD)[SX], 1.0e-9,
                    "the named row should have been overridden");

            RigSpec defaults = HumanoidRig.defaultSpec();
            assertEquals(defaults.base[HitboxPart.HEAD.ordinal()][SY],
                    HumanoidRig.baseSpec(HitboxPart.HEAD)[SY], 1.0e-9,
                    "the untouched fields of the named row should survive");
            for (HitboxPart limb : HitboxPart.VALUES) {
                if (limb == HitboxPart.HEAD) {
                    continue;
                }
                assertArrayEquals(defaults.base[limb.ordinal()], HumanoidRig.baseSpec(limb), 1.0e-9,
                        "an unnamed limb (" + limb + ") was clobbered by a partial file");
            }
        }

        @Test
        void aWrongLengthRowIsSkippedWithoutLosingTheRestOfTheFile(@TempDir Path dir) throws Exception {
            double[] torso = HumanoidRig.defaultSpec().base[HitboxPart.TORSO.ordinal()];
            torso[SX] = 7.5;
            String mixed = "{ \"base\": {"
                    + " \"HEAD\": [1.0, 2.0, 3.0],"
                    + " \"TORSO\": " + java.util.Arrays.toString(torso)
                    + " } }";
            Files.writeString(dir.resolve(RigSpecIO.FILE_NAME), mixed, StandardCharsets.UTF_8);
            RigSpecIO.reload(dir);

            RigSpec defaults = HumanoidRig.defaultSpec();
            assertArrayEquals(defaults.base[HitboxPart.HEAD.ordinal()],
                    HumanoidRig.baseSpec(HitboxPart.HEAD), 1.0e-9,
                    "the 3-value HEAD row should have been skipped, leaving the default");
            assertEquals(7.5, HumanoidRig.baseSpec(HitboxPart.TORSO)[SX], 1.0e-9,
                    "the valid TORSO row in the same file should still have been applied");
        }
    }
}
