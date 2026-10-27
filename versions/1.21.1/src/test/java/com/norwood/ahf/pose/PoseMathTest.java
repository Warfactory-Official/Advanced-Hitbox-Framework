package com.norwood.ahf.pose;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoseMathTest {

    private static Matrix3d rot(double[] v) {
        return new Matrix3d().set(new Quaterniond().rotationZYX(v[5], v[4], v[3]));
    }

    private static void assertMatrix(Matrix3d a, Matrix3d b) {
        double[] x = a.get(new double[9]);
        double[] y = b.get(new double[9]);
        for (int i = 0; i < 9; i++) {
            assertEquals(x[i], y[i], 1e-12);
        }
    }

    @Test
    void preRotateIsParentFrame() {
        double[] v = {0, 0, 0, 0.3, -0.7, 1.1, 0, 0};
        Matrix3d before = rot(v);
        PoseMath.preRotateX(v, 0.4);
        assertMatrix(new Matrix3d().rotationX(0.4).mul(before), rot(v));
        before = rot(v);
        PoseMath.preRotateY(v, -0.9);
        assertMatrix(new Matrix3d().rotationY(-0.9).mul(before), rot(v));
    }

    /** Axis 0 => angle add (TACZ clips); off-x axis => rotation vectors summed. */
    @Test
    void torsoBendAddsAboutX() {
        double[] v = new double[PoseClip.CHANNELS];
        v[PoseClip.BEND] = 0.2;
        PoseMath.addTorsoBend(v, 0.3, null);
        assertEquals(0.5, v[PoseClip.BEND], 1e-12);
        assertEquals(0.0, v[PoseClip.BEND_AXIS], 1e-12);
        v[PoseClip.BEND_AXIS] = -Math.PI / 2;
        v[PoseClip.BEND] = 0.4;
        PoseMath.addTorsoBend(v, 0.3, null);
        // (0, 0, 0.4) + (0.3, 0, 0): rendered axis (cos -a, 0, sin -a)
        double a = -v[PoseClip.BEND_AXIS];
        assertEquals(0.3, Math.cos(a) * v[PoseClip.BEND], 1e-12);
        assertEquals(0.4, Math.sin(a) * v[PoseClip.BEND], 1e-12);
    }

    /** Torso yawed psi: parent x in the torso frame = (cos psi, 0, sin psi); bent upper half turns about parent x. */
    @Test
    void torsoBendFollowsTheTorsoYaw() {
        double psi = 0.7;
        double[] torso = {0, 0, 0, 0, psi, 0, 0, 0};
        double[] v = new double[PoseClip.CHANNELS];
        PoseMath.addTorsoBend(v, 0.3, torso);
        double a = -v[PoseClip.BEND_AXIS];
        Matrix3d bend = new Matrix3d().rotation(v[PoseClip.BEND], Math.cos(a), 0, Math.sin(a));
        Matrix3d r = rot(torso);
        assertMatrix(new Matrix3d().rotationX(0.3).mul(r), new Matrix3d(r).mul(bend));
    }
}
