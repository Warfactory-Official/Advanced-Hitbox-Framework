package com.norwood.ahf.geometry;

import com.norwood.ahf.part.HitboxPart;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObbRayTest {

    private static final double EPS = 1.0e-9;

    private static Obb unitBox() {
        return box(Vec3.ZERO, 1.0, 1.0, 1.0);
    }

    private static Obb box(Vec3 center, double hx, double hy, double hz) {
        return new Obb(center,
                new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                new Vec3(hx, hy, hz), HitboxPart.TORSO);
    }

    private static boolean hit(double t) {
        return t != Double.POSITIVE_INFINITY;
    }

    private static Vec3 rot(Vec3 v, double yaw, double pitch, double roll) {
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double x1 = v.x * cy - v.z * sy, y1 = v.y, z1 = v.x * sy + v.z * cy;
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double x2 = x1, y2 = y1 * cp - z1 * sp, z2 = y1 * sp + z1 * cp;
        double cr = Math.cos(roll), sr = Math.sin(roll);
        return new Vec3(x2 * cr - y2 * sr, x2 * sr + y2 * cr, z2);
    }

    private static Obb rotated(Obb o, double yaw, double pitch, double roll) {
        return new Obb(rot(o.center(), yaw, pitch, roll),
                rot(o.axisX(), yaw, pitch, roll),
                rot(o.axisY(), yaw, pitch, roll),
                rot(o.axisZ(), yaw, pitch, roll),
                o.half(), o.limb());
    }

    @Nested
    @DisplayName("axis-aligned basics")
    class Basics {

        @Test
        void frontalHitEntersAtTheNearFace() {
            Obb b = unitBox();
            double t = b.rayEntry(new Vec3(-10.0, 0.0, 0.0), new Vec3(1.0, 0.0, 0.0));
            assertEquals(9.0, t, EPS);
        }

        @Test
        void tIsInUnitsOfTheDirectionVectorNotWorldDistance() {
            Obb b = unitBox();
            Vec3 from = new Vec3(-10.0, 0.0, 0.0);
            double unit = b.rayEntry(from, new Vec3(1.0, 0.0, 0.0));
            double segment = b.rayEntry(from, new Vec3(20.0, 0.0, 0.0));
            assertEquals(9.0, unit, EPS);
            assertEquals(9.0 / 20.0, segment, EPS);
        }

        @Test
        void originInsideReturnsZero() {
            assertEquals(0.0, unitBox().rayEntry(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0)), EPS);
        }

        @Test
        void rayPointingAwayMisses() {
            assertFalse(hit(unitBox().rayEntry(new Vec3(-10.0, 0.0, 0.0), new Vec3(-1.0, 0.0, 0.0))));
        }

        @Test
        void parallelRayOutsideTheSlabMisses() {
            assertFalse(hit(unitBox().rayEntry(new Vec3(-10.0, 5.0, 0.0), new Vec3(1.0, 0.0, 0.0))));
        }

        @Test
        void cornerGrazeIsAHitAndJustBeyondItIsNot() {
            Obb b = unitBox();
            assertTrue(hit(b.rayEntry(new Vec3(-10.0, 1.0, 0.0), new Vec3(1.0, 0.0, 0.0))));
            assertFalse(hit(b.rayEntry(new Vec3(-10.0, 1.0 + 1.0e-6, 0.0), new Vec3(1.0, 0.0, 0.0))));
        }

        @Test
        void offCentreBoxShiftsTheEntryDistance() {
            Obb b = box(new Vec3(5.0, 0.0, 0.0), 1.0, 1.0, 1.0);
            assertEquals(4.0, b.rayEntry(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0)), EPS);
        }
    }

    @Nested
    @DisplayName("degenerate direction components")
    class Degenerate {

        @Test
        void exactlyParallelInsideTwoSlabsHits() {
            Obb b = unitBox();
            assertTrue(hit(b.rayEntry(new Vec3(0.5, -0.5, -10.0), new Vec3(0.0, 0.0, 1.0))));
        }

        @Test
        void exactlyParallelOutsideOneSlabMisses() {
            Obb b = unitBox();
            assertFalse(hit(b.rayEntry(new Vec3(2.5, 0.0, -10.0), new Vec3(0.0, 0.0, 1.0))));
        }

        @Test
        void justUnderTheEpsilonBehavesLikeExactlyParallel() {
            Obb b = unitBox();
            assertFalse(hit(b.rayEntry(new Vec3(2.5, 0.0, -10.0), new Vec3(1.0e-12, 0.0, 1.0))));
        }

        @Test
        void zeroLengthDirectionDoesNotHitFromOutside() {
            Obb b = unitBox();
            assertFalse(hit(b.rayEntry(new Vec3(10.0, 10.0, 10.0), Vec3.ZERO)));
            assertTrue(hit(b.rayEntry(new Vec3(0.2, 0.2, 0.2), Vec3.ZERO)));
        }
    }

    @Nested
    @DisplayName("padding (gap rejection)")
    class Padding {

        @Test
        void padOnlyEverMakesHittingEasier() {
            Obb b = unitBox();
            Vec3 from = new Vec3(-10.0, 1.4, 0.0);
            Vec3 dir = new Vec3(1.0, 0.0, 0.0);
            assertFalse(hit(b.rayEntry(from, dir)), "1.4 is outside the un-padded half-extent of 1.0");
            assertTrue(hit(b.rayEntry(from, dir, 0.5)), "with 0.5 of pad the same ray should graze");
        }

        @Test
        void padNeverTurnsAHitIntoAMiss() {
            Obb b = unitBox();
            Random rng = new Random(20260905L);
            for (int i = 0; i < 2000; i++) {
                Vec3 from = new Vec3(rng.nextDouble() * 8 - 4, rng.nextDouble() * 8 - 4, rng.nextDouble() * 8 - 4);
                Vec3 dir = new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1);
                double bare = b.rayEntry(from, dir);
                double padded = b.rayEntry(from, dir, 0.25);
                if (hit(bare)) {
                    assertTrue(hit(padded), "pad lost a hit at from=" + from + " dir=" + dir);
                    assertTrue(padded <= bare + EPS,
                            "pad should enter no later than bare: " + padded + " > " + bare);
                }
            }
        }

        @Test
        void zeroPadIsIdenticalToTheBareTest() {
            Obb b = unitBox();
            Random rng = new Random(7L);
            for (int i = 0; i < 500; i++) {
                Vec3 from = new Vec3(rng.nextDouble() * 6 - 3, rng.nextDouble() * 6 - 3, rng.nextDouble() * 6 - 3);
                Vec3 dir = new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1);
                assertEquals(b.rayEntry(from, dir), b.rayEntry(from, dir, 0.0), EPS);
            }
        }
    }

    @Nested
    @DisplayName("rotation invariance")
    class Rotation {

        @Test
        void rotatingBoxAndRayTogetherPreservesEntryDistance() {
            // Transposed/swapped axis passes every axis-aligned test; fails here.
            Random rng = new Random(4242L);
            for (int i = 0; i < 3000; i++) {
                double hx = 0.1 + rng.nextDouble() * 2.0;
                double hy = 0.1 + rng.nextDouble() * 2.0;
                double hz = 0.1 + rng.nextDouble() * 2.0;
                Obb b = box(new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1),
                        hx, hy, hz);
                Vec3 from = new Vec3(rng.nextDouble() * 12 - 6, rng.nextDouble() * 12 - 6, rng.nextDouble() * 12 - 6);
                Vec3 dir = new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1);

                double yaw = rng.nextDouble() * Math.PI * 2;
                double pitch = rng.nextDouble() * Math.PI * 2;
                double roll = rng.nextDouble() * Math.PI * 2;

                double before = b.rayEntry(from, dir);
                double after = rotated(b, yaw, pitch, roll)
                        .rayEntry(rot(from, yaw, pitch, roll), rot(dir, yaw, pitch, roll));

                if (hit(before) || hit(after)) {
                    assertEquals(before, after, 1.0e-6,
                            "rotation changed the answer: box=" + b.center() + " from=" + from + " dir=" + dir);
                }
            }
        }

        @Test
        void aRotatedBoxIsActuallyRotated() {
            // 45 deg still covers the ray along the diagonal => vacuous; quarter turn required.
            Obb along = box(Vec3.ZERO, 3.0, 0.2, 0.2);
            Obb across = rotated(along, Math.PI / 2, 0.0, 0.0);
            Vec3 from = new Vec3(2.9, 0.0, -10.0);
            Vec3 dir = new Vec3(0.0, 0.0, 1.0);
            assertTrue(hit(along.rayEntry(from, dir)), "x=2.9 is within the 3.0 half-extent");
            assertFalse(hit(across.rayEntry(from, dir)), "after a quarter turn the X half-extent is 0.2");
        }
    }

    @Nested
    @DisplayName("contains / distanceSq agreement")
    class PointQueries {

        @Test
        void containsAgreesWithDistanceSqBeingZero() {
            Obb b = box(new Vec3(0.5, -0.25, 1.0), 1.0, 2.0, 0.5);
            Random rng = new Random(99L);
            for (int i = 0; i < 3000; i++) {
                Vec3 p = new Vec3(rng.nextDouble() * 8 - 4, rng.nextDouble() * 8 - 4, rng.nextDouble() * 8 - 4);
                assertEquals(b.contains(p), b.distanceSq(p) == 0.0,
                        "contains/distanceSq disagree at " + p);
            }
        }

        @Test
        void aContainedOriginAlwaysEntersAtZero() {
            Obb b = box(new Vec3(0.5, -0.25, 1.0), 1.0, 2.0, 0.5);
            Random rng = new Random(1234L);
            for (int i = 0; i < 1000; i++) {
                Vec3 p = new Vec3(rng.nextDouble() * 8 - 4, rng.nextDouble() * 8 - 4, rng.nextDouble() * 8 - 4);
                if (!b.contains(p)) {
                    continue;
                }
                Vec3 dir = new Vec3(rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1, rng.nextDouble() * 2 - 1);
                assertEquals(0.0, b.rayEntry(p, dir), EPS, "inside-origin should enter at 0");
            }
        }

        @Test
        void distanceSqIsTheSquaredEuclideanGapOnAnAxis() {
            Obb b = unitBox();
            assertEquals(9.0, b.distanceSq(new Vec3(4.0, 0.0, 0.0)), EPS);
            assertEquals(0.0, b.distanceSq(new Vec3(1.0, 1.0, 1.0)), EPS, "surface counts as inside");
        }
    }

    @Test
    @DisplayName("repeated calls are deterministic (no hidden state)")
    void deterministic() {
        Obb b = box(new Vec3(0.1, 0.2, 0.3), 0.7, 1.3, 0.4);
        Vec3 from = new Vec3(-3.0, 0.5, 0.25);
        Vec3 dir = new Vec3(1.0, 0.1, -0.2);
        double first = b.rayEntry(from, dir);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, b.rayEntry(from, dir), 0.0);
        }
    }
}
