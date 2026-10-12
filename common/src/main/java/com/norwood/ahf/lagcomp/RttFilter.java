package com.norwood.ahf.lagcomp;

/** Min over the last {@code window} samples. Not thread-safe. */
public final class RttFilter {

    private final long[] samples;
    private int next;
    private int count;

    public RttFilter(int window) {
        samples = new long[window];
    }

    public int window() {
        return samples.length;
    }

    public void add(long rttNanos) {
        samples[next] = rttNanos;
        next = (next + 1) % samples.length;
        if (count < samples.length) {
            count++;
        }
    }

    /** -1 until the first sample. */
    public long min() {
        if (count == 0) {
            return -1L;
        }
        long m = Long.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            m = Math.min(m, samples[i]);
        }
        return m;
    }

    /** min(cap, ceil(rtt / tick) + interp + slack); unknown RTT (< 0) counts as 0. */
    public static int maxRewindTicks(long rttNanos, long nanosPerTick, int interp, int slack, int cap) {
        long rttTicks = rttNanos <= 0L ? 0L : (rttNanos + nanosPerTick - 1L) / nanosPerTick;
        return (int) Math.min(cap, rttTicks + interp + slack);
    }

    public static long boundViewTick(long reportedViewTick, long now, int maxRewind) {
        return Math.max(now - maxRewind, Math.min(now, reportedViewTick));
    }
}
