package com.norwood.ahf.lagcomp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RttFilterTest {

    private static final long MS = 1_000_000L;
    private static final long TICK = 50 * MS;

    @Test
    void minOverWindowForgetsOldSpikesAndOldLows() {
        RttFilter f = new RttFilter(3);
        assertEquals(-1L, f.min(), "no sample yet");
        f.add(80 * MS);
        f.add(300 * MS);
        assertEquals(80 * MS, f.min(), "a spike must not raise the estimate");
        f.add(90 * MS);
        f.add(95 * MS);
        assertEquals(90 * MS, f.min(), "80 ms left the 3-sample window");
        f.add(200 * MS);
        f.add(210 * MS);
        f.add(220 * MS);
        assertEquals(200 * MS, f.min(), "a sustained rise must be followed once the window turns over");
    }

    @Test
    void maxRewindIsCeilRttPlusInterpPlusSlackCapped() {
        assertEquals(5, RttFilter.maxRewindTicks(-1L, TICK, 3, 2, 10), "unknown RTT counts as 0");
        assertEquals(5, RttFilter.maxRewindTicks(0L, TICK, 3, 2, 10));
        assertEquals(6, RttFilter.maxRewindTicks(1L, TICK, 3, 2, 10), "any RTT rounds up to a tick");
        assertEquals(6, RttFilter.maxRewindTicks(50 * MS, TICK, 3, 2, 10));
        assertEquals(8, RttFilter.maxRewindTicks(120 * MS, TICK, 3, 2, 10));
        assertEquals(9, RttFilter.maxRewindTicks(180 * MS, TICK, 3, 2, 10));
        assertEquals(10, RttFilter.maxRewindTicks(300 * MS, TICK, 3, 2, 10), "cap");
        assertEquals(7, RttFilter.maxRewindTicks(120 * MS, 2 * TICK, 3, 2, 10), "10 TPS: same RTT, fewer ticks");
    }

    @Test
    void viewTickIsClampedIntoTheWindow() {
        assertEquals(995L, RttFilter.boundViewTick(990L, 1000L, 5), "too old => oldest allowed");
        assertEquals(997L, RttFilter.boundViewTick(997L, 1000L, 5));
        assertEquals(1000L, RttFilter.boundViewTick(1010L, 1000L, 5), "future => now");
        assertEquals(1000L, RttFilter.boundViewTick(900L, 1000L, 0));
    }
}
