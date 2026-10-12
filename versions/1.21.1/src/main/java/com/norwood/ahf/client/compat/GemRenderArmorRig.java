package com.norwood.ahf.client.compat;

import com.norwood.ahf.body.BodyMesh;
import com.norwood.ahf.body.BodyMesh.Joint;
import com.norwood.ahf.client.body.ArmorRenderer;
import com.norwood.ahf.client.body.BodyRenderer;
import com.wf.gemrender.direct.ArmorRig;
import com.wf.gemrender.direct.GemRenderArmorModel;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.RigPatch;
import com.wf.gemrender.vendor.jgltf.model.NodeModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * GemRender armour on the jointed body. Import: each unskinned limb/body node gains {@code <node>.ahf_ring} +
 * {@code <node>.ahf_bent} joints, vertices weighted across the body's cut. Draw: root nodes + joints set from
 * {@link BodyRenderer} frames. Space: glTF = {@code S} model, {@code S = diag(-1, -1, 1)}; node rest translation
 * {@code r} = bone origin; part space = {@code S r - p}, {@code p} = wide-body rest pivot.
 */
public final class GemRenderArmorRig implements ArmorRig {

    public static final String RING = ".ahf_ring", BENT = ".ahf_bent";
    /** Weight band half-width, px: rigid -> ring -> bent across the cut. */
    private static final float BAND = 2;
    /** Per {@link GemRenderArmorModel#PARTS}: rigid, ring, bent; ring null = unsplit. */
    private static final Joint[][] JOINTS = {
            {Joint.HEAD, null, null},
            {Joint.BODY, Joint.WAIST, Joint.CHEST},
            {Joint.LEFT_ARM, Joint.LEFT_ELBOW, Joint.LEFT_FOREARM},
            {Joint.RIGHT_ARM, Joint.RIGHT_ELBOW, Joint.RIGHT_FOREARM},
            {Joint.LEFT_LEG, Joint.LEFT_KNEE, Joint.LEFT_SHIN},
            {Joint.RIGHT_LEG, Joint.RIGHT_KNEE, Joint.RIGHT_SHIN}};

    private final Map<NodeTable, int[]> joints = new WeakHashMap<>();
    private final Matrix4f g = new Matrix4f();
    private final Matrix4f gc = new Matrix4f();
    private final Matrix4f rest = new Matrix4f();
    private final Quaternionf q = new Quaternionf();
    private final Vector3f t = new Vector3f();

    public static void install() {
        GemRenderArmorModel.setRig(new GemRenderArmorRig());
    }

    @Override
    public void patch(RigPatch.Rig rig, Map<String, String> bones) {
        BodyMesh mesh = BodyRenderer.wide();
        if (mesh == null) {
            return;
        }
        for (int i = 0; i < JOINTS.length; i++) {
            Joint[] j = JOINTS[i];
            String name = bones.get(GemRenderArmorModel.PARTS.get(i));
            NodeModel owner = name == null || j[1] == null ? null : rig.node(name);
            if (owner == null || owner.getSkinModel() != null) {
                continue;
            }
            NodeModel ring = rig.addJoint(owner, name + RING);
            NodeModel bent = rig.addJoint(owner, name + BENT);
            float[] r = owner.getTranslation() == null ? new float[3] : owner.getTranslation();
            float[] rr = owner.getRotation() == null ? new float[]{0, 0, 0, 1} : owner.getRotation();
            Quaternionf restRot = new Quaternionf(rr[0], rr[1], rr[2], rr[3]);
            // part y = -(r.y + (R x).y) - p.y; d > 0 => bent side
            float cut = (mesh.pivot(j[1], 1) - mesh.pivot(j[0], 1)) / 16;
            float py = mesh.pivot(j[0], 1) / 16;
            float sign = j[0] == Joint.BODY ? -1 : 1;
            float band = BAND / 16;
            Vector3f v = new Vector3f();
            rig.weigh(owner, (x, y, z, out, w) -> {
                restRot.transform(v.set(x, y, z));
                float d = sign * (-(r[1] + v.y) - py - cut);
                float s = (d + band) / (2 * band);
                if (s <= 0) {
                    out[0] = owner;
                    w[0] = 1;
                    return 1;
                }
                if (s >= 1) {
                    out[0] = bent;
                    w[0] = 1;
                    return 1;
                }
                out[0] = ring;
                out[1] = s < 0.5F ? owner : bent;
                w[1] = s < 0.5F ? 1 - 2 * s : 2 * s - 1;
                w[0] = 1 - w[1];
                return 2;
            });
        }
    }

    @Override
    public void pose(HumanoidModel<?> parts, NodeTable table, float[] state, int[] slots) {
        PlayerModel<?> wearer = ArmorRenderer.wearer();
        BodyMesh wide = BodyRenderer.wide();
        if (wearer == null || wide == null) {
            return;
        }
        BodyRenderer.pose(parts, wearer, BodyRenderer.mesh(wearer));
        write(table, state, slots, wide);
    }

    /** Root nodes + split joints from {@link BodyRenderer#frame}. */
    void write(NodeTable table, float[] state, int[] slots, BodyMesh wide) {
        int[] split = joints.computeIfAbsent(table, k -> lookup(k, slots));
        int[] parents = table.parentSlots();
        for (int i = 0; i < slots.length; i++) {
            int slot = slots[i];
            if (slot < 0 || parents[slot] >= 0) {
                continue;
            }
            Joint root = JOINTS[i][0];
            global(table, slot, root, wide, BodyRenderer.frame(root), g);
            table.setTranslation(state, slot, g.m30(), g.m31(), g.m32());
            table.setRotation(state, slot, g.getNormalizedRotation(q));
            for (int k = 1; k <= 2; k++) {
                int joint = split[i * 2 + k - 1];
                if (joint >= 0) {
                    global(table, slot, root, wide, BodyRenderer.frame(JOINTS[i][k]), gc);
                    gc.mulLocal(rest.set(g).invertAffine());
                    table.setTranslation(state, joint, gc.m30(), gc.m31(), gc.m32());
                    table.setRotation(state, joint, gc.getNormalizedRotation(q));
                }
            }
        }
    }

    /** {@code out = S F T(S r - p) S R_rest}: node space -> glTF model space under part frame {@code frame}. */
    private void global(NodeTable table, int slot, Joint root, BodyMesh wide, Matrix4f frame, Matrix4f out) {
        t.set(-table.restTranslation(slot, 0) - wide.pivot(root, 0) / 16,
                -table.restTranslation(slot, 1) - wide.pivot(root, 1) / 16,
                table.restTranslation(slot, 2) - wide.pivot(root, 2) / 16);
        table.restRotation(slot, q);
        out.scaling(-1, -1, 1).mul(frame).translate(t).scale(-1, -1, 1).rotate(q);
    }

    private static int[] lookup(NodeTable table, int[] slots) {
        int[] out = new int[slots.length * 2];
        for (int i = 0; i < slots.length; i++) {
            String name = slots[i] < 0 ? null : table.nodeName(slots[i]);
            out[i * 2] = name == null ? -1 : table.slotOfName(name + RING);
            out[i * 2 + 1] = name == null ? -1 : table.slotOfName(name + BENT);
        }
        return out;
    }
}
