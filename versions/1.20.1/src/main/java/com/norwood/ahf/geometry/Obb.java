package com.norwood.ahf.geometry;

import com.norwood.ahf.part.HitboxPart;
import net.minecraft.world.phys.Vec3;

public record Obb(Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ, Vec3 half, HitboxPart limb) {

    private static double excess(double d, double h) {
        double a = Math.abs(d) - h;
        return Math.max(a, 0.0);
    }

    public double rayEntry(Vec3 origin, Vec3 dir) {
        double px = origin.x - center.x;
        double py = origin.y - center.y;
        double pz = origin.z - center.z;
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        double po = px * axisX.x + py * axisX.y + pz * axisX.z;
        double d = dir.x * axisX.x + dir.y * axisX.y + dir.z * axisX.z;
        if (Math.abs(d) < 1.0e-9) {
            if (po < -half.x || po > half.x) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double t1 = (-half.x - po) / d;
            double t2 = (half.x - po) / d;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            if (t1 > tMin) {
                tMin = t1;
            }
            if (t2 < tMax) {
                tMax = t2;
            }
            if (tMin > tMax) {
                return Double.POSITIVE_INFINITY;
            }
        }
        po = px * axisY.x + py * axisY.y + pz * axisY.z;
        d = dir.x * axisY.x + dir.y * axisY.y + dir.z * axisY.z;
        if (Math.abs(d) < 1.0e-9) {
            if (po < -half.y || po > half.y) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double t1 = (-half.y - po) / d;
            double t2 = (half.y - po) / d;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            if (t1 > tMin) {
                tMin = t1;
            }
            if (t2 < tMax) {
                tMax = t2;
            }
            if (tMin > tMax) {
                return Double.POSITIVE_INFINITY;
            }
        }
        po = px * axisZ.x + py * axisZ.y + pz * axisZ.z;
        d = dir.x * axisZ.x + dir.y * axisZ.y + dir.z * axisZ.z;
        if (Math.abs(d) < 1.0e-9) {
            if (po < -half.z || po > half.z) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double t1 = (-half.z - po) / d;
            double t2 = (half.z - po) / d;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            if (t1 > tMin) {
                tMin = t1;
            }
            if (t2 < tMax) {
                tMax = t2;
            }
            if (tMin > tMax) {
                return Double.POSITIVE_INFINITY;
            }
        }
        if (tMax < 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(tMin, 0.0);
    }

    public boolean contains(Vec3 p) {
        double qx = p.x - center.x;
        double qy = p.y - center.y;
        double qz = p.z - center.z;
        return Math.abs(qx * axisX.x + qy * axisX.y + qz * axisX.z) <= half.x
                && Math.abs(qx * axisY.x + qy * axisY.y + qz * axisY.z) <= half.y
                && Math.abs(qx * axisZ.x + qy * axisZ.y + qz * axisZ.z) <= half.z;
    }

    public double distanceSq(Vec3 p) {
        double qx = p.x - center.x;
        double qy = p.y - center.y;
        double qz = p.z - center.z;
        double ex = excess(qx * axisX.x + qy * axisX.y + qz * axisX.z, half.x);
        double ey = excess(qx * axisY.x + qy * axisY.y + qz * axisY.z, half.y);
        double ez = excess(qx * axisZ.x + qy * axisZ.y + qz * axisZ.z, half.z);
        return ex * ex + ey * ey + ez * ez;
    }
}
