package com.norwood.ahf.pose;

/** Channel arithmetic on {@link PoseClip#CHANNELS} values; rotation {@code v[3..5]} = {@code Rz Ry Rx}. */
public final class PoseMath {

    private PoseMath() {
    }

    /** {@code R(v) = Rx(a) R(v)}: turn about the parent frame's x (Euler add turns about the part's own x). */
    public static void preRotateX(double[] v, double a) {
        if (a == 0.0) {
            return;
        }
        double[] m = matrix(v);
        double c = Math.cos(a), s = Math.sin(a);
        for (int col = 0; col < 3; col++) {
            double y = m[3 + col], z = m[6 + col];
            m[3 + col] = c * y - s * z;
            m[6 + col] = s * y + c * z;
        }
        euler(m, v);
    }

    /** {@code R(v) = Ry(a) R(v)}. */
    public static void preRotateY(double[] v, double a) {
        if (a == 0.0) {
            return;
        }
        double[] m = matrix(v);
        double c = Math.cos(a), s = Math.sin(a);
        for (int col = 0; col < 3; col++) {
            double x = m[col], z = m[6 + col];
            m[col] = c * x + s * z;
            m[6 + col] = -s * x + c * z;
        }
        euler(m, v);
    }

    /**
     * Waist bend ({@code v[BEND_AXIS]}, {@code v[BEND]}, rendered about {@code (cos -axis, 0, sin -axis)} in the torso
     * part's frame) plus {@code a} about the parent x, as rotation vectors summed: the parent x seen from {@code torso}
     * (its channels; null = unrotated), flattened onto the bend plane (bends cannot twist). Unrotated torso + axis 0
     * (TACZ clips) => angle add.
     */
    public static void addTorsoBend(double[] v, double a, double[] torso) {
        double ux = 1.0, uz = 0.0;
        if (torso != null) {
            double[] m = matrix(torso);
            double n = Math.hypot(m[0], m[2]);
            if (n > 1e-9) {
                ux = m[0] / n;
                uz = m[2] / n;
            }
        }
        double axis = -v[PoseClip.BEND_AXIS];
        double x = Math.cos(axis) * v[PoseClip.BEND] + a * ux;
        double z = Math.sin(axis) * v[PoseClip.BEND] + a * uz;
        if (z == 0.0) {
            v[PoseClip.BEND] = x;
            v[PoseClip.BEND_AXIS] = 0.0;
            return;
        }
        double angle = Math.hypot(x, z);
        double ax = Math.atan2(z, x);
        // keep the incoming axis side: (angle, axis) == (-angle, axis + pi)
        if (Math.cos(ax - axis) < 0.0) {
            angle = -angle;
            ax += Math.PI;
        }
        v[PoseClip.BEND] = angle;
        v[PoseClip.BEND_AXIS] = -(axis + Math.IEEEremainder(ax - axis, 2.0 * Math.PI));
    }

    /** Row-major 3x3 of {@code Rz(v5) Ry(v4) Rx(v3)}. */
    static double[] matrix(double[] v) {
        double cx = Math.cos(v[3]), sx = Math.sin(v[3]);
        double cy = Math.cos(v[4]), sy = Math.sin(v[4]);
        double cz = Math.cos(v[5]), sz = Math.sin(v[5]);
        return new double[]{
                cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
                sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
                -sy, cy * sx, cy * cx};
    }

    static void euler(double[] m, double[] v) {
        v[3] = Math.atan2(m[7], m[8]);
        v[4] = Math.asin(Math.max(-1.0, Math.min(1.0, -m[6])));
        v[5] = Math.atan2(m[3], m[0]);
    }
}
