package com.norwood.ahf.rig;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Two-bone arm solve to a hand target: the drawn third-person arm and the server rig run this one solve. Model space,
 * blocks ({@code +Y} down, {@code -Z} forward). Instance = scratch: one thread.
 */
public final class ArmSolver {
    private static final float REACH_EPSILON = 1e-4F;
    private static final float MAX_REACH_FRACTION = 0.97F;
    /** Default-pole length added to the authored elbow offset (blocks): straight authored arm => default side. */
    private static final float POLE_DEFAULT_SHARE = 0.02F;

    /** Arm cube landmarks, part px: centre x / z, shoulder top, elbow = cube centre (body joint), wrist = fist end. */
    public record Landmarks(float centerX, float centerZ, float shoulderY, float elbowY, float wristY) {
        /** Vanilla player arm cube ({@code PlayerModel.createMesh}). */
        public static Landmarks vanilla(boolean left, boolean slim) {
            float w = slim ? 3 : 4;
            return new Landmarks((left ? -1 : 1 - w) + w / 2, 0, -2, 4, 10);
        }
    }

    private final Matrix4f arm = new Matrix4f();
    private final Matrix4f bent = new Matrix4f();
    private final Quaternionf bendQ = new Quaternionf();
    private final Vector3f shoulder = new Vector3f();
    private final Vector3f elbow = new Vector3f();
    private final Vector3f restHand = new Vector3f();
    private final Vector3f authored = new Vector3f();
    private final Vector3f line = new Vector3f();
    private final Vector3f pole = new Vector3f();
    private final Vector3f poleDefault = new Vector3f();
    private final Vector3f pu = new Vector3f();
    private final Vector3f pl = new Vector3f();
    private final Vector3f d1 = new Vector3f();
    private final Vector3f d2 = new Vector3f();
    private final Vector3f dir = new Vector3f();
    private final Vector3f perp = new Vector3f();
    private final Vector3f e = new Vector3f();
    private final Vector3f solvedHand = new Vector3f();
    private final Vector3f t = new Vector3f();
    private final Quaternionf q = new Quaternionf();
    private final Matrix3f r = new Matrix3f();
    private final Matrix3f qm = new Matrix3f();

    /**
     * Arm part {@code T(x, y, z) Rz Ry Rx} (px, rad) with the clip's elbow bend {@code (bendAxis, bend)} (forearm turned
     * about {@code (cos -axis, 0, sin -axis)} at the elbow) -> joint frames {@code upper} (origin shoulder top),
     * {@code lower} (origin elbow) reaching {@code target}. Pole = authored elbow off the shoulder -> authored hand line
     * (+ a little of the default: straight arms) => the solve keeps the clip's elbow.
     */
    public void solve(float x, float y, float z, float xRot, float yRot, float zRot, Landmarks lm, boolean left,
                      Vector3fc target, float bendAxis, float bend, Matrix4f upper, Matrix4f lower) {
        arm.translation(x / 16F, y / 16F, z / 16F).rotateZYX(zRot, yRot, xRot);
        arm.transformPosition(lm.centerX() / 16F, lm.shoulderY() / 16F, lm.centerZ() / 16F, shoulder);
        arm.transformPosition(lm.centerX() / 16F, lm.elbowY() / 16F, lm.centerZ() / 16F, elbow);
        arm.transformPosition(lm.centerX() / 16F, lm.wristY() / 16F, lm.centerZ() / 16F, restHand);
        upper.set(arm).setTranslation(shoulder);
        lower.set(arm).setTranslation(elbow);
        float axis = -bendAxis;
        bent.set(arm).translate(lm.centerX() / 16F, lm.elbowY() / 16F, lm.centerZ() / 16F)
                .rotate(bendQ.identity().rotateAxis(bend, (float) Math.cos(axis), 0, (float) Math.sin(axis)))
                .transformPosition(0, (lm.wristY() - lm.elbowY()) / 16F, 0, authored);
        // Model space: +Y down, -Z forward, right arm at -X => default elbows down and outward.
        pole.set(left ? 0.6F : -0.6F, 1, -0.3F).normalize(poleDefault);
        authored.sub(shoulder, line);
        elbow.sub(shoulder, pole);
        if (line.lengthSquared() > 1e-10F) {
            line.normalize();
            pole.fma(-pole.dot(line), line);
        }
        pole.fma(POLE_DEFAULT_SHARE, poleDefault);
        twoBone(upper, lower, restHand, target, shoulder, pole);
    }

    /** Joint frames of {@link #solve} -> arm part space (part px -> model blocks). */
    public static void partFrames(Landmarks lm, Matrix4fc upper, Matrix4fc lower, Matrix4f upperPart,
                                  Matrix4f lowerPart) {
        upperPart.set(upper).translate(-lm.centerX() / 16F, -lm.shoulderY() / 16F, -lm.centerZ() / 16F);
        lowerPart.set(lower).translate(-lm.centerX() / 16F, -lm.elbowY() / 16F, -lm.centerZ() / 16F);
    }

    /**
     * {@code upper}, {@code lower}: joint globals, rewritten; rest hand fixes segment lengths. Returns the reached wrist
     * (scratch).
     */
    public Vector3f twoBone(Matrix4f upper, Matrix4f lower, Vector3fc rest, Vector3fc target, Vector3fc root,
                            Vector3fc poleDir) {
        upper.getTranslation(pu);
        lower.getTranslation(pl);
        pl.sub(pu, d1);
        rest.sub(pl, d2);
        float l1 = d1.length(), l2 = d2.length();
        target.sub(root, dir);
        float d = dir.length();
        if (l1 < 1e-6F || l2 < 1e-6F || d < 1e-6F) {
            return solvedHand.set(target);
        }
        dir.div(d);
        float minimumReach = Math.abs(l1 - l2) + REACH_EPSILON;
        float maximumReach = Math.max(minimumReach, (l1 + l2) * MAX_REACH_FRACTION);
        d = Math.clamp(d, minimumReach, maximumReach);
        solvedHand.set(root).fma(d, dir);
        float a = (l1 * l1 - l2 * l2 + d * d) / (2 * d);
        float h = (float) Math.sqrt(Math.max(l1 * l1 - a * a, 0));
        perp.set(poleDir).fma(-poleDir.dot(dir), dir);
        if (perp.lengthSquared() < 1e-12F) {
            perp.set(dir.y, -dir.x, 0);
        }
        perp.normalize();
        e.set(root).fma(a, dir).fma(h, perp);

        upper.get3x3(r);
        q.rotationTo(d1.div(l1), t.set(e).sub(root).normalize());
        r.mulLocal(qm.set(q));
        upper.set3x3(r).setTranslation(root);

        // Forearm inherits the upper bone's swing (twist continuous across the elbow), then bends minimally.
        lower.get3x3(r);
        r.mulLocal(qm);
        q.transform(d2.div(l2));
        q.rotationTo(d2, t.set(solvedHand).sub(e).normalize());
        r.mulLocal(qm.set(q));
        lower.set3x3(r).setTranslation(e);
        return solvedHand;
    }
}
