package com.norwood.ahf.lagcomp;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellIndexTest {

    /** Diagonal whose bounding box overlaps the box but which passes its corner by ~0.57. */
    @Test
    void aDiagonalMissingTheBoxInsideItsBoundsIsNotAHit() {
        AABB box = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        Vec3 from = new Vec3(-1.0, 0.5, 0.8);
        Vec3 to = new Vec3(0.8, 0.5, 2.6);
        assertTrue(new AABB(from, to).intersects(box), "setup: segment bounds overlap the box");
        assertFalse(EntityHistory.segmentHits(box, from, to));
        assertTrue(EntityHistory.segmentHits(box, new Vec3(-1.0, 0.5, -1.0), new Vec3(2.0, 0.5, 2.0)));
        assertFalse(EntityHistory.segmentHits(box, new Vec3(-1.0, 0.5, 0.5), new Vec3(-0.1, 0.5, 0.5)),
                "segment ends before the box");
    }

    /** Superset property: every box a segment meets is returned (once), across cells, signs and long spans. */
    @Test
    void queryReturnsEveryBoxTheSegmentMeetsOnce() {
        Random rnd = new Random(11);
        CellIndex<Integer> index = new CellIndex<>();
        for (int round = 0; round < 3; round++) {
            index.reset(round);
            List<AABB> boxes = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                double x = rnd.nextDouble() * 120.0 - 60.0;
                double y = rnd.nextDouble() * 60.0 - 30.0;
                double z = rnd.nextDouble() * 120.0 - 60.0;
                AABB b = new AABB(x, y, z, x + 0.2 + rnd.nextDouble() * 2.0, y + 0.5 + rnd.nextDouble() * 2.0,
                        z + 0.2 + rnd.nextDouble() * 2.0);
                boxes.add(b);
                index.add(i, b);
            }
            int hits = 0;
            for (int k = 0; k < 3000; k++) {
                Vec3 from = new Vec3(rnd.nextDouble() * 160.0 - 80.0, rnd.nextDouble() * 80.0 - 40.0,
                        rnd.nextDouble() * 160.0 - 80.0);
                Vec3 to = k % 3 == 0 ? boxes.get(k % boxes.size()).getCenter()
                        : new Vec3(rnd.nextDouble() * 160.0 - 80.0, rnd.nextDouble() * 80.0 - 40.0,
                        rnd.nextDouble() * 160.0 - 80.0);
                if (k % 7 == 0) {
                    to = new Vec3(from.x, to.y, from.z);
                }
                List<Integer> got = new ArrayList<>();
                index.query(from, to, got);
                Set<Integer> unique = new HashSet<>(got);
                assertEquals(unique.size(), got.size(), "duplicate candidates");
                for (int i = 0; i < boxes.size(); i++) {
                    if (EntityHistory.segmentHits(boxes.get(i), from, to)) {
                        hits++;
                        assertTrue(unique.contains(i), "box " + i + " met by " + from + " -> " + to + " not returned");
                    }
                }
            }
            assertTrue(hits > 500, "setup: only " + hits + " hits");
        }
    }

    /** Box in cell x=0, segment 0.3 past it in cell x=1: found only when keyed with a pad. */
    @Test
    void aPaddedKeyReachesASegmentInTheNextCell() {
        AABB box = new AABB(7.0, 0.0, 0.0, 7.9, 2.0, 1.0);
        Vec3 from = new Vec3(8.2, 1.0, -5.0);
        Vec3 to = new Vec3(8.2, 1.0, 5.0);
        assertFalse(EntityHistory.segmentHits(box, 0.0, from, to));
        assertTrue(EntityHistory.segmentHits(box, 0.3, from, to));
        assertFalse(EntityHistory.segmentHits(box, 0.29, from, to));
        CellIndex<Integer> plain = new CellIndex<>();
        plain.reset(0);
        plain.add(1, box);
        List<Integer> got = new ArrayList<>();
        plain.query(from, to, got);
        assertTrue(got.isEmpty(), "setup: unpadded key already reaches the next cell " + got);
        CellIndex<Integer> padded = new CellIndex<>();
        padded.reset(0);
        padded.add(1, box, EntityHistory.MAX_CANDIDATE_PAD);
        padded.query(from, to, got);
        assertEquals(List.of(1), got);
    }

    @Test
    void aSegmentOutsideEveryBoxWalksNoCells() {
        CellIndex<Integer> index = new CellIndex<>();
        index.reset(0);
        index.add(1, new AABB(0.0, 0.0, 0.0, 1.0, 2.0, 1.0));
        List<Integer> got = new ArrayList<>();
        index.query(new Vec3(1.0e6, 0.0, 0.0), new Vec3(-1.0e6, 50.0, 0.0), got);
        assertTrue(got.isEmpty(), "far segment returned " + got);
        index.query(new Vec3(1.0e6, 1.0, 0.5), new Vec3(-1.0e6, 1.0, 0.5), got);
        assertEquals(List.of(1), got, "a 2e6-block segment through the box");
    }
}
