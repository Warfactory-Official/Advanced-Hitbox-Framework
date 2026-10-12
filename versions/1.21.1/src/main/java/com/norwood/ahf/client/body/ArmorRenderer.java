package com.norwood.ahf.client.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.ahf.body.BodyMesh;
import com.norwood.ahf.body.BodyMesh.Joint;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Cube armour on the jointed body: each root part drawn by vanilla into root-part space (recorded), quads cut at
 * the body's joint plane, halves + cut ring skinned with the wearer's joint frames. Render thread only.
 */
public final class ArmorRenderer {

    private static final int J = Joint.VALUES.length;
    /** Corner: x, y, z (root part space, blocks), u, v, nx, ny, nz, joint. */
    private static final int W = 9;
    private static final float[] IN = new float[4 * W];
    private static final float[] UP = new float[5 * W];
    private static final float[] DOWN = new float[5 * W];
    private static final Matrix4f[] VIEW = new Matrix4f[J];
    private static final Matrix3f[] NORMAL = new Matrix3f[J];
    private static final PoseStack LOCAL = new PoseStack();
    private static final Matrix4f INV = new Matrix4f();
    private static final Vector3f P = new Vector3f();
    private static final Vector3f N = new Vector3f();
    private static final Recorder RECORDER = new Recorder();
    private static final Map<Class<?>, Boolean> CUBES = new IdentityHashMap<>();

    private static PlayerModel<?> wearer;
    private static VertexConsumer out;
    private static int light, overlay, color;
    private static float cut;
    private static int above, ring, below;

    static {
        for (int i = 0; i < J; i++) {
            VIEW[i] = new Matrix4f();
            NORMAL[i] = new Matrix3f();
        }
    }

    private ArmorRenderer() {
    }

    /** Armour layer start: {@code parent} = the layer's parent model. */
    public static void begin(EntityModel<?> parent) {
        wearer = parent instanceof PlayerModel<?> p && BodyRenderer.mesh(p) != null ? p : null;
    }

    public static void end() {
        wearer = null;
    }

    /** Jointed wearer of the current armour layer; null outside one or for vanilla cubes. */
    public static PlayerModel<?> wearer() {
        return wearer;
    }

    /** False => caller draws {@code m} itself. */
    public static boolean render(Model m, PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
        if (wearer == null || !(m instanceof HumanoidModel<?> model) || model.young || !cubes(m.getClass())) {
            return false;
        }
        BodyMesh mesh = BodyRenderer.mesh(wearer);
        BodyRenderer.pose(model, wearer, mesh);
        Matrix4f view = pose.last().pose();
        for (int i = 0; i < J; i++) {
            view.mul(BodyRenderer.FRAME[i], VIEW[i]);
            VIEW[i].normal(NORMAL[i]);
        }
        out = vc;
        ArmorRenderer.light = light;
        ArmorRenderer.overlay = overlay;
        ArmorRenderer.color = color;
        part(model.head, mesh, Joint.HEAD, null, null, null);
        part(model.hat, mesh, Joint.HEAD, null, null, null);
        part(model.body, mesh, Joint.CHEST, Joint.WAIST, Joint.BODY, Joint.BODY);
        part(model.rightArm, mesh, Joint.RIGHT_ARM, Joint.RIGHT_ELBOW, Joint.RIGHT_FOREARM, Joint.RIGHT_ARM);
        part(model.leftArm, mesh, Joint.LEFT_ARM, Joint.LEFT_ELBOW, Joint.LEFT_FOREARM, Joint.LEFT_ARM);
        part(model.rightLeg, mesh, Joint.RIGHT_LEG, Joint.RIGHT_KNEE, Joint.RIGHT_SHIN, Joint.RIGHT_LEG);
        part(model.leftLeg, mesh, Joint.LEFT_LEG, Joint.LEFT_KNEE, Joint.LEFT_SHIN, Joint.LEFT_LEG);
        out = null;
        return true;
    }

    /** Class draws its parts' cubes ({@code renderToBuffer} not overridden below {@code HumanoidModel}). */
    private static boolean cubes(Class<?> c) {
        return CUBES.computeIfAbsent(c, k -> {
            try {
                Class<?> owner = k.getMethod("renderToBuffer", PoseStack.class, VertexConsumer.class, int.class,
                        int.class, int.class).getDeclaringClass();
                return owner == HumanoidModel.class || owner == AgeableListModel.class;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * {@code up} = joint for y < cut (y down), {@code low} for y > cut, cut = {@code mid} rest height in
     * {@code pivot}'s part; {@code mid} null => all {@code up}.
     */
    private static void part(ModelPart root, BodyMesh mesh, Joint up, Joint mid, Joint low, Joint pivot) {
        if (!root.visible) {
            return;
        }
        above = up.ordinal();
        ring = mid == null ? -1 : mid.ordinal();
        below = low == null ? -1 : low.ordinal();
        cut = mid == null ? 0 : (mesh.pivot(mid, 1) - mesh.pivot(pivot, 1)) / 16;
        // root's own transform cancelled: vanilla draws into root part space
        LOCAL.setIdentity();
        root.translateAndRotate(LOCAL);
        INV.set(LOCAL.last().pose()).invertAffine();
        LOCAL.last().pose().set(INV);
        INV.normal(LOCAL.last().normal());
        RECORDER.n = 0;
        root.render(LOCAL, RECORDER, 0, 0);
    }

    private static void polygon() {
        if (ring < 0) {
            for (int k = 0; k < 4; k++) {
                IN[k * W + 8] = above;
            }
            emit(IN, 4);
            return;
        }
        int up = 0, down = 0, on = 0;
        for (int k = 0; k < 4; k++) {
            int a = k * W, b = (k + 1) % 4 * W;
            float da = IN[a + 1] - cut, db = IN[b + 1] - cut;
            if (da == 0) {
                on++;
            }
            if (da <= 0) {
                up = put(UP, up, a, da == 0 ? ring : above);
            }
            if (da >= 0) {
                down = put(DOWN, down, a, da == 0 ? ring : below);
            }
            if (da < 0 && db > 0 || da > 0 && db < 0) {
                float t = da / (da - db);
                int o = up * W;
                for (int c = 0; c < 8; c++) {
                    UP[o + c] = IN[a + c] + (IN[b + c] - IN[a + c]) * t;
                }
                UP[o + 8] = ring;
                up++;
                System.arraycopy(UP, o, DOWN, down * W, W);
                down++;
            }
        }
        if (up >= 3) {
            emit(UP, up);
        }
        if (down >= 3 && on < 4) {
            emit(DOWN, down);
        }
    }

    private static int put(float[] dst, int n, int at, int joint) {
        System.arraycopy(IN, at, dst, n * W, 8);
        dst[n * W + 8] = joint;
        return n + 1;
    }

    /** Polygon as quads: 3 -> (0,1,2,2), 5 -> (0,1,2,3)(0,3,4,4). */
    private static void emit(float[] c, int n) {
        vertex(c, 0);
        vertex(c, 1);
        vertex(c, 2);
        vertex(c, n == 3 ? 2 : 3);
        if (n == 5) {
            vertex(c, 0);
            vertex(c, 3);
            vertex(c, 4);
            vertex(c, 4);
        }
    }

    private static void vertex(float[] c, int k) {
        int o = k * W;
        int j = (int) c[o + 8];
        VIEW[j].transformPosition(c[o], c[o + 1], c[o + 2], P);
        NORMAL[j].transform(c[o + 5], c[o + 6], c[o + 7], N).normalize();
        out.addVertex(P.x, P.y, P.z, color, c[o + 3], c[o + 4], overlay, light, N.x, N.y, N.z);
    }

    /** Collects vanilla's quads (4 corners each) into {@link #IN}. */
    private static final class Recorder implements VertexConsumer {
        int n;

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            addVertex(x, y, z);
            setUv(u, v);
            setNormal(nx, ny, nz);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            int o = n * W;
            IN[o] = x;
            IN[o + 1] = y;
            IN[o + 2] = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            IN[n * W + 3] = u;
            IN[n * W + 4] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            int o = n * W;
            IN[o + 5] = x;
            IN[o + 6] = y;
            IN[o + 7] = z;
            if (++n == 4) {
                n = 0;
                polygon();
            }
            return this;
        }
    }
}
