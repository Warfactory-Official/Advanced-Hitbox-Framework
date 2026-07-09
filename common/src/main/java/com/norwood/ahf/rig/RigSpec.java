package com.norwood.ahf.rig;

public final class RigSpec {

    public final double[][] base;
    public final double[][][] poseAdjust;
    public final double[][][][] handAdjust;

    public RigSpec(double[][] base, double[][][] poseAdjust, double[][][][] handAdjust) {
        this.base = base;
        this.poseAdjust = poseAdjust;
        this.handAdjust = handAdjust;
    }

    public RigSpec copy() {
        return new RigSpec(copy2(base), copy3(poseAdjust), copy4(handAdjust));
    }

    private static double[][] copy2(double[][] a) {
        double[][] out = new double[a.length][];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] == null ? null : a[i].clone();
        }
        return out;
    }

    private static double[][][] copy3(double[][][] a) {
        double[][][] out = new double[a.length][][];
        for (int i = 0; i < a.length; i++) {
            out[i] = copy2(a[i]);
        }
        return out;
    }

    private static double[][][][] copy4(double[][][][] a) {
        double[][][][] out = new double[a.length][][][];
        for (int i = 0; i < a.length; i++) {
            out[i] = copy3(a[i]);
        }
        return out;
    }
}
