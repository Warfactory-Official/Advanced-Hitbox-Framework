package com.norwood.ahf.client.compat;

import com.norwood.ahf.body.BodyMesh;
import com.norwood.ahf.body.BodyMesh.Joint;
import com.norwood.ahf.client.body.BodyRenderer;
import com.wf.gemrender.direct.GemRenderArmorModel;
import com.wf.gemrender.gltf.NodeTable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GemRenderArmorRigTest {

    /** Armour vertex on joint j lands at {@code S FRAME[j] (S (r + x) - p)}: same place as the body's own point. */
    @Test
    void jointsFollowBodyFrames() throws IOException {
        BodyMesh wide;
        try (InputStream in = BodyMesh.class.getResourceAsStream("/assets/ahf/models/player_wide.glb")) {
            wide = BodyMesh.read(in.readAllBytes());
        }
        Random rnd = new Random(3);
        for (Joint j : Joint.VALUES) {
            BodyRenderer.frame(j).identity().translate(rnd.nextFloat() - 0.5F, rnd.nextFloat() - 0.5F, rnd.nextFloat())
                    .rotateXYZ(rnd.nextFloat() * 2, rnd.nextFloat() * 2, rnd.nextFloat() * 2);
        }
        Joint[][] parts = {{Joint.HEAD}, {Joint.BODY, Joint.WAIST, Joint.CHEST},
                {Joint.LEFT_ARM, Joint.LEFT_ELBOW, Joint.LEFT_FOREARM},
                {Joint.RIGHT_ARM, Joint.RIGHT_ELBOW, Joint.RIGHT_FOREARM},
                {Joint.LEFT_LEG, Joint.LEFT_KNEE, Joint.LEFT_SHIN}, {Joint.RIGHT_LEG, Joint.RIGHT_KNEE, Joint.RIGHT_SHIN}};
        List<String> names = new ArrayList<>();
        List<Integer> parents = new ArrayList<>();
        List<float[]> trs = new ArrayList<>();
        List<Joint> jointOf = new ArrayList<>();
        int[] slots = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String name = GemRenderArmorModel.PARTS.get(i);
            Joint root = parts[i][0];
            slots[i] = names.size();
            names.add(name);
            parents.add(-1);
            // rest translation = S pivot, plus an off-pivot authoring offset
            trs.add(new float[]{-wide.pivot(root, 0) / 16 + 0.01F, -wide.pivot(root, 1) / 16 - 0.02F,
                    wide.pivot(root, 2) / 16, 0, 0, 0, 1, 1, 1, 1});
            jointOf.add(root);
            for (int k = 1; k < parts[i].length; k++) {
                names.add(name + (k == 1 ? GemRenderArmorRig.RING : GemRenderArmorRig.BENT));
                parents.add(slots[i]);
                trs.add(new float[]{0, 0, 0, 0, 0, 0, 1, 1, 1, 1});
                jointOf.add(parts[i][k]);
            }
        }
        float[] flat = new float[trs.size() * NodeTable.TRS_STRIDE];
        for (int i = 0; i < trs.size(); i++) {
            System.arraycopy(trs.get(i), 0, flat, i * NodeTable.TRS_STRIDE, NodeTable.TRS_STRIDE);
        }
        NodeTable table = NodeTable.ofNodes(names.toArray(String[]::new),
                parents.stream().mapToInt(Integer::intValue).toArray(), flat);
        float[] state = table.newScratch();
        new GemRenderArmorRig().write(table, state, slots, wide);

        Vector3f x = new Vector3f(0.03F, -0.2F, 0.05F);
        for (int n = 0; n < names.size(); n++) {
            Matrix4f global = new Matrix4f();
            for (int s = n; s >= 0; s = parents.get(s)) {
                Matrix4f local = new Matrix4f();
                table.localTransform(state, s, local);
                global.mulLocal(local);
            }
            Vector3f got = global.transformPosition(new Vector3f(x)).mul(-1, -1, 1);
            int owner = parents.get(n) < 0 ? n : parents.get(n);
            Joint root = jointOf.get(owner);
            float[] r = trs.get(owner);
            Vector3f part = new Vector3f(-(r[0] + x.x), -(r[1] + x.y), r[2] + x.z)
                    .sub(wide.pivot(root, 0) / 16, wide.pivot(root, 1) / 16, wide.pivot(root, 2) / 16);
            Vector3f want = BodyRenderer.frame(jointOf.get(n)).transformPosition(part);
            assertEquals(0, got.distance(want), 1e-5, names.get(n));
        }
    }
}
