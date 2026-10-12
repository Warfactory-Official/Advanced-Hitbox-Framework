package com.norwood.ahf.lagcomp;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

/**
 * Uniform-grid broadphase: items keyed by every {@link #CELL} cube their box overlaps; a segment query walks the cells
 * it crosses (3D DDA, clipped to the union of item boxes). Reused across ticks; not thread-safe.
 */
final class CellIndex<T> {

    static final double CELL = 8.0;
    /** Boxes grow by this before keying => a segment through a shared cell corner still meets them. */
    private static final double KEY_PAD = 1.0e-6;

    long tick = Long.MIN_VALUE;
    private final Long2IntOpenHashMap head = new Long2IntOpenHashMap();
    private Object[] items = new Object[16];
    private int[] seen = new int[16];
    private int itemCount;
    private int[] nodeItem = new int[32];
    private int[] nodeNext = new int[32];
    private int nodeCount;
    private int query;
    private double minX;
    private double minY;
    private double minZ;
    private double maxX;
    private double maxY;
    private double maxZ;

    CellIndex() {
        head.defaultReturnValue(-1);
        reset(Long.MIN_VALUE);
    }

    void reset(long tick) {
        this.tick = tick;
        head.clear();
        Arrays.fill(items, 0, itemCount, null);
        itemCount = 0;
        nodeCount = 0;
        minX = minY = minZ = Double.POSITIVE_INFINITY;
        maxX = maxY = maxZ = Double.NEGATIVE_INFINITY;
    }

    int size() {
        return itemCount;
    }

    void add(T item, AABB box) {
        add(item, box, 0.0);
    }

    /** Keyed and bounded as {@code box} grown by {@code pad}. */
    void add(T item, AABB box, double pad) {
        if (itemCount == items.length) {
            items = Arrays.copyOf(items, itemCount * 2);
            seen = Arrays.copyOf(seen, itemCount * 2);
        }
        int id = itemCount++;
        items[id] = item;
        seen[id] = query;
        minX = Math.min(minX, box.minX - pad);
        minY = Math.min(minY, box.minY - pad);
        minZ = Math.min(minZ, box.minZ - pad);
        maxX = Math.max(maxX, box.maxX + pad);
        maxY = Math.max(maxY, box.maxY + pad);
        maxZ = Math.max(maxZ, box.maxZ + pad);
        double key = pad + KEY_PAD;
        int x0 = cell(box.minX - key);
        int x1 = cell(box.maxX + key);
        int y0 = cell(box.minY - key);
        int y1 = cell(box.maxY + key);
        int z0 = cell(box.minZ - key);
        int z1 = cell(box.maxZ + key);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    link(key(x, y, z), id);
                }
            }
        }
    }

    private void link(long k, int id) {
        if (nodeCount == nodeItem.length) {
            nodeItem = Arrays.copyOf(nodeItem, nodeCount * 2);
            nodeNext = Arrays.copyOf(nodeNext, nodeCount * 2);
        }
        int n = nodeCount++;
        nodeItem[n] = id;
        nodeNext[n] = head.put(k, n);
    }

    /** Each item keyed in a cell {@code from -> to} crosses, once, appended to {@code out}. Superset; no box test. */
    void query(Vec3 from, Vec3 to, List<? super T> out) {
        if (itemCount == 0) {
            return;
        }
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double[] span = {0.0, 1.0};
        if (!clip(from.x, dx, minX, maxX, span) || !clip(from.y, dy, minY, maxY, span)
                || !clip(from.z, dz, minZ, maxZ, span)) {
            return;
        }
        if (++query == 0) {
            Arrays.fill(seen, 0, itemCount, -1);
            query = 1;
        }
        double sx = from.x + dx * span[0];
        double sy = from.y + dy * span[0];
        double sz = from.z + dz * span[0];
        int cx = cell(sx);
        int cy = cell(sy);
        int cz = cell(sz);
        int ex = cell(from.x + dx * span[1]);
        int ey = cell(from.y + dy * span[1]);
        int ez = cell(from.z + dz * span[1]);
        int stepX = Integer.signum(ex - cx);
        int stepY = Integer.signum(ey - cy);
        int stepZ = Integer.signum(ez - cz);
        double tMaxX = boundary(sx, dx, cx, stepX);
        double tMaxY = boundary(sy, dy, cy, stepY);
        double tMaxZ = boundary(sz, dz, cz, stepZ);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : CELL / Math.abs(dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : CELL / Math.abs(dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : CELL / Math.abs(dz);
        visit(key(cx, cy, cz), out);
        for (int steps = Math.abs(ex - cx) + Math.abs(ey - cy) + Math.abs(ez - cz); steps > 0; steps--) {
            double ax = cx != ex ? tMaxX : Double.POSITIVE_INFINITY;
            double ay = cy != ey ? tMaxY : Double.POSITIVE_INFINITY;
            double az = cz != ez ? tMaxZ : Double.POSITIVE_INFINITY;
            if (cx != ex && ax <= ay && ax <= az) {
                cx += stepX;
                tMaxX += tDeltaX;
            } else if (cy != ey && ay <= az) {
                cy += stepY;
                tMaxY += tDeltaY;
            } else {
                cz += stepZ;
                tMaxZ += tDeltaZ;
            }
            visit(key(cx, cy, cz), out);
        }
    }

    @SuppressWarnings("unchecked")
    private void visit(long k, List<? super T> out) {
        for (int n = head.get(k); n >= 0; n = nodeNext[n]) {
            int id = nodeItem[n];
            if (seen[id] != query) {
                seen[id] = query;
                out.add((T) items[id]);
            }
        }
    }

    /** Segment-parameter distance to the first cell boundary on this axis; +inf when the axis never steps. */
    private static double boundary(double s, double d, int c, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double edge = (step > 0 ? c + 1 : c) * CELL;
        return (edge - s) / d;
    }

    /** Slab clip of {@code o + d*t, t in span} to {@code [lo, hi]}; narrows {@code span}. */
    private static boolean clip(double o, double d, double lo, double hi, double[] span) {
        if (Math.abs(d) < 1.0e-12) {
            return o >= lo && o <= hi;
        }
        double ta = (lo - o) / d;
        double tb = (hi - o) / d;
        if (ta > tb) {
            double s = ta;
            ta = tb;
            tb = s;
        }
        span[0] = Math.max(span[0], ta);
        span[1] = Math.min(span[1], tb);
        return span[0] <= span[1];
    }

    static int cell(double v) {
        return Mth.floor(v / CELL);
    }

    /** Collisions only add candidates. */
    static long key(int x, int y, int z) {
        return (x & 0x1FFFFFL) | (y & 0x1FFFFFL) << 21 | (z & 0x3FFFFFL) << 42;
    }
}
