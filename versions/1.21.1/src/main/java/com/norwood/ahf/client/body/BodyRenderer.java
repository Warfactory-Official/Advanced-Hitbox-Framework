package com.norwood.ahf.client.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.ahf.Ahf;
import com.norwood.ahf.body.BodyMesh;
import com.norwood.ahf.body.BodyMesh.Joint;
import com.norwood.ahf.body.BodyMesh.Part;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Draws {@link PlayerModel} as the jointed body GLB instead of its cubes: vanilla part poses, plus PlayerAnimator
 * bends (bendy-lib semantics: lower half turned about the cube centre) and {@link AhfBody} arm overrides. Render
 * thread only.
 */
public final class BodyRenderer {

    public static final ResourceLocation WIDE = ResourceLocation.fromNamespaceAndPath(Ahf.MOD_ID, "models/player_wide.glb");
    public static final ResourceLocation SLIM = ResourceLocation.fromNamespaceAndPath(Ahf.MOD_ID, "models/player_slim.glb");
    /** Bend slots, {@code (axis, angle)} rad, PlayerAnimator {@code getBend} values. */
    public static final int WAIST = 0, RIGHT_ARM = 1, LEFT_ARM = 2, RIGHT_LEG = 3, LEFT_LEG = 4, BENDS = 5;

    /** Off => vanilla cubes. */
    public static boolean enabled = true;

    private static BodyMesh wide;
    private static BodyMesh slim;
    private static volatile BendSource bendSource = (model, out) -> false;

    private static final int J = Joint.VALUES.length;
    private static final Matrix4f[] JOINT = new Matrix4f[J];
    /** Joint frames: root part space (part pivot origin, blocks) -> posed model space. */
    static final Matrix4f[] FRAME = new Matrix4f[J];
    private static final Matrix4f[] VIEW = new Matrix4f[J];
    private static final Matrix3f[] NORMAL = new Matrix3f[J];
    private static final float[] BEND = new float[BENDS * 2];
    private static final PoseStack PART = new PoseStack();
    private static final Matrix4f M = new Matrix4f();
    private static final Matrix4f U = new Matrix4f();
    private static final Matrix4f L = new Matrix4f();
    /** Upper-body bend, model space; head + arms ride it. */
    static final Matrix4f CONJ = new Matrix4f();
    private static final Matrix4f TMP = new Matrix4f();
    private static final Quaternionf Q = new Quaternionf();
    private static final Quaternionf ID = new Quaternionf();
    private static final Vector3f P = new Vector3f();
    private static final Vector3f N = new Vector3f();

    static {
        for (int i = 0; i < J; i++) {
            JOINT[i] = new Matrix4f();
            FRAME[i] = new Matrix4f();
            VIEW[i] = new Matrix4f();
            NORMAL[i] = new Matrix3f();
        }
    }

    private BodyRenderer() {
    }

    /** {@code out[slot * 2] = axis, out[slot * 2 + 1] = angle} of the model's current draw; false => no bends. */
    @FunctionalInterface
    public interface BendSource {
        boolean bends(PlayerModel<?> model, float[] out);
    }

    public static void setBendSource(BendSource source) {
        bendSource = source;
    }

    public static final ResourceManagerReloadListener RELOAD = BodyRenderer::reload;

    private static void reload(ResourceManager rm) {
        wide = load(rm, WIDE);
        slim = load(rm, SLIM);
    }

    private static BodyMesh load(ResourceManager rm, ResourceLocation id) {
        try (InputStream in = rm.open(id)) {
            return BodyMesh.read(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(id.toString(), e);
        }
    }

    /** False => caller draws the vanilla cubes. */
    public static boolean render(PlayerModel<?> model, boolean slimArms, PoseStack pose, VertexConsumer vc, int light,
                                 int overlay, int color) {
        BodyMesh mesh = slimArms ? slim : wide;
        if (!enabled || mesh == null || model.young) {
            return false;
        }
        pose(model, model, mesh);
        Matrix4f view = pose.last().pose();
        for (int i = 0; i < J; i++) {
            view.mul(JOINT[i], VIEW[i]);
            VIEW[i].normal(NORMAL[i]);
        }
        for (Part part : Part.VALUES) {
            ModelPart mp = part(model, part);
            float[] c = mesh.corners[part.ordinal()];
            if (!mp.visible || mp.skipDraw || c == null) {
                continue;
            }
            byte[] cj = mesh.joints[part.ordinal()];
            for (int k = 0, o = 0; k < cj.length; k++, o += BodyMesh.STRIDE) {
                int j = cj[k];
                VIEW[j].transformPosition(c[o], c[o + 1], c[o + 2], P);
                NORMAL[j].transform(c[o + 3], c[o + 4], c[o + 5], N).normalize();
                vc.addVertex(P.x, P.y, P.z, color, c[o + 6], c[o + 7], overlay, light, N.x, N.y, N.z);
            }
        }
        return true;
    }

    /** {@link #pose}'s frame for {@code j}. */
    public static Matrix4f frame(Joint j) {
        return FRAME[j.ordinal()];
    }

    /** Wide-arm mesh; null before the first resource load. */
    public static BodyMesh wide() {
        return wide;
    }

    /** Mesh drawn for {@code model}; null => vanilla cubes. */
    public static BodyMesh mesh(PlayerModel<?> model) {
        BodyMesh mesh = ((SlimArms) model).ahf$slim() ? slim : wide;
        return enabled && !model.young ? mesh : null;
    }

    /**
     * The model's current joint skin matrices (rest model space -> posed model space, blocks), {@link Joint} order,
     * into {@code out}; false => vanilla cubes drawn.
     */
    public static boolean skin(PlayerModel<?> model, Matrix4f[] out) {
        BodyMesh mesh = mesh(model);
        if (mesh == null) {
            return false;
        }
        pose(model, model, mesh);
        for (int i = 0; i < J; i++) {
            out[i].set(JOINT[i]);
        }
        return true;
    }

    /** {@code PlayerModel} slim arms (mixin). */
    public interface SlimArms {
        boolean ahf$slim();
    }

    /**
     * {@link #JOINT} + {@link #FRAME} from {@code model}'s part transforms and {@code wearer}'s bends ({@code model}
     * = armour => its copied parts).
     */
    public static void pose(HumanoidModel<?> model, PlayerModel<?> wearer, BodyMesh mesh) {
        java.util.Arrays.fill(BEND, 0);
        bendSource.bends(wearer, BEND);
        // Torso: lower half rigid, upper half bent about the cube centre; head + arms follow the upper half.
        transform(model.body, M);
        bent(M, mesh, Joint.BODY, Joint.WAIST, WAIST, U, L);
        skin(U, mesh, Joint.BODY, Joint.BODY);
        skin(L, mesh, Joint.CHEST, Joint.BODY);
        half(U, L, mesh, Joint.BODY, Joint.WAIST);
        CONJ.set(M).invertAffine().mulLocal(L);
        transform(model.head, M);
        skin(M.mulLocal(CONJ), mesh, Joint.HEAD, Joint.HEAD);
        limb(model.rightArm, mesh, Joint.RIGHT_ARM, Joint.RIGHT_ELBOW, Joint.RIGHT_FOREARM, RIGHT_ARM,
                AhfBody.override(HumanoidArm.RIGHT), true);
        limb(model.leftArm, mesh, Joint.LEFT_ARM, Joint.LEFT_ELBOW, Joint.LEFT_FOREARM, LEFT_ARM,
                AhfBody.override(HumanoidArm.LEFT), true);
        limb(model.rightLeg, mesh, Joint.RIGHT_LEG, Joint.RIGHT_KNEE, Joint.RIGHT_SHIN, RIGHT_LEG, null, false);
        limb(model.leftLeg, mesh, Joint.LEFT_LEG, Joint.LEFT_KNEE, Joint.LEFT_SHIN, LEFT_LEG, null, false);
    }

    private static void limb(ModelPart part, BodyMesh mesh, Joint rigid, Joint mid, Joint moving, int slot,
                             AhfBody.Override override, boolean upper) {
        if (override != null) {
            U.set(override.upper());
            L.set(override.lower());
        } else {
            transform(part, M);
            if (upper) {
                M.mulLocal(CONJ);
            }
            bent(M, mesh, rigid, mid, slot, U, L);
        }
        skin(U, mesh, rigid, rigid);
        skin(L, mesh, moving, rigid);
        half(U, L, mesh, rigid, mid);
    }

    /** {@code upper = base}, {@code lower = base T(c) R(bend) T(-c)}, {@code c} = {@code mid} in part space. */
    private static void bent(Matrix4f base, BodyMesh mesh, Joint rigid, Joint mid, int slot, Matrix4f upper,
                             Matrix4f lower) {
        upper.set(base);
        lower.set(base);
        float angle = BEND[slot * 2 + 1];
        if (angle != 0) {
            float axis = -BEND[slot * 2];
            float cx = (mesh.pivot(mid, 0) - mesh.pivot(rigid, 0)) / 16;
            float cy = (mesh.pivot(mid, 1) - mesh.pivot(rigid, 1)) / 16;
            float cz = (mesh.pivot(mid, 2) - mesh.pivot(rigid, 2)) / 16;
            lower.translate(cx, cy, cz).rotate(Q.identity().rotateAxis(angle, (float) Math.cos(axis), 0,
                    (float) Math.sin(axis))).translate(-cx, -cy, -cz);
        }
    }

    /** Half-angle ring between {@code upper} and {@code lower} about {@code mid}. */
    private static void half(Matrix4f upper, Matrix4f lower, BodyMesh mesh, Joint rigid, Joint mid) {
        float cx = (mesh.pivot(mid, 0) - mesh.pivot(rigid, 0)) / 16;
        float cy = (mesh.pivot(mid, 1) - mesh.pivot(rigid, 1)) / 16;
        float cz = (mesh.pivot(mid, 2) - mesh.pivot(rigid, 2)) / 16;
        TMP.set(upper).invertAffine().mul(lower).getNormalizedRotation(Q);
        ID.identity().slerp(Q, 0.5F, Q);
        TMP.set(upper).translate(cx, cy, cz).rotate(Q).translate(-cx, -cy, -cz);
        skin(TMP, mesh, mid, rigid);
    }

    /** {@code JOINT[j] = frame T(-rest)}, rest = the part pivot ({@code root}) in model px. */
    private static void skin(Matrix4f frame, BodyMesh mesh, Joint j, Joint root) {
        FRAME[j.ordinal()].set(frame);
        JOINT[j.ordinal()].set(frame).translate(-mesh.pivot(root, 0) / 16, -mesh.pivot(root, 1) / 16,
                -mesh.pivot(root, 2) / 16);
    }

    private static void transform(ModelPart part, Matrix4f out) {
        PART.setIdentity();
        part.translateAndRotate(PART);
        out.set(PART.last().pose());
    }

    /**
     * {@code translateToHand} for bends: {@code pre} = upper-body bend before the arm transform, {@code post} =
     * elbow bend after it. Skipped when bendy-lib bends PlayerAnimator's own item ({@code bendyLib}).
     */
    public static void handPre(PlayerModel<?> model, boolean slimArms, PoseStack stack) {
        BodyMesh mesh = slimArms ? slim : wide;
        if (!enabled || mesh == null || AhfBody.bendyLib()) {
            return;
        }
        java.util.Arrays.fill(BEND, 0);
        if (!bendSource.bends(model, BEND) || BEND[WAIST * 2 + 1] == 0) {
            return;
        }
        transform(model.body, M);
        bent(M, mesh, Joint.BODY, Joint.WAIST, WAIST, U, L);
        stack.mulPose(CONJ.set(M).invertAffine().mulLocal(L));
    }

    public static void handPost(PlayerModel<?> model, boolean slimArms, HumanoidArm side, PoseStack stack) {
        BodyMesh mesh = slimArms ? slim : wide;
        if (!enabled || mesh == null || AhfBody.bendyLib() || AhfBody.override(side) != null) {
            return;
        }
        java.util.Arrays.fill(BEND, 0);
        int slot = side == HumanoidArm.RIGHT ? RIGHT_ARM : LEFT_ARM;
        if (!bendSource.bends(model, BEND) || BEND[slot * 2 + 1] == 0) {
            return;
        }
        Joint rigid = side == HumanoidArm.RIGHT ? Joint.RIGHT_ARM : Joint.LEFT_ARM;
        Joint mid = side == HumanoidArm.RIGHT ? Joint.RIGHT_ELBOW : Joint.LEFT_ELBOW;
        bent(M.identity(), mesh, rigid, mid, slot, U, L);
        stack.mulPose(L);
    }

    private static ModelPart part(PlayerModel<?> m, Part p) {
        return switch (p) {
            case HEAD -> m.head;
            case HAT -> m.hat;
            case BODY -> m.body;
            case JACKET -> m.jacket;
            case RIGHT_ARM -> m.rightArm;
            case RIGHT_SLEEVE -> m.rightSleeve;
            case LEFT_ARM -> m.leftArm;
            case LEFT_SLEEVE -> m.leftSleeve;
            case RIGHT_LEG -> m.rightLeg;
            case RIGHT_PANTS -> m.rightPants;
            case LEFT_LEG -> m.leftLeg;
            case LEFT_PANTS -> m.leftPants;
        };
    }
}
