package com.norwood.ahf.body;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Jointed player body GLB ({@code tools/ahf.py body}): one skin, rigid single-joint vertices, quads as triangle
 * pairs {@code (a,b,c)(a,c,d)}. Converted to PlayerModel space: blocks, y down, front -z.
 */
public final class BodyMesh {

    /** Skin joints. Per limb: rigid upper, half-angle ring, moving lower. */
    public enum Joint {
        BODY, WAIST, CHEST, HEAD, RIGHT_ARM, RIGHT_ELBOW, RIGHT_FOREARM, LEFT_ARM, LEFT_ELBOW, LEFT_FOREARM,
        RIGHT_LEG, RIGHT_KNEE, RIGHT_SHIN, LEFT_LEG, LEFT_KNEE, LEFT_SHIN;

        public static final Joint[] VALUES = values();
    }

    /** PlayerModel parts, primitive {@code extras.part}. */
    public enum Part {
        HEAD, HAT, BODY, JACKET, RIGHT_ARM, RIGHT_SLEEVE, LEFT_ARM, LEFT_SLEEVE, RIGHT_LEG, RIGHT_PANTS, LEFT_LEG,
        LEFT_PANTS;

        public static final Part[] VALUES = values();
    }

    public static final int STRIDE = 8;

    /** Joint rest positions, model px. */
    public final float[] pivot = new float[Joint.VALUES.length * 3];
    /** Per part: quad corners {@code x, y, z} (blocks), {@code nx, ny, nz}, {@code u, v}; null = absent. */
    public final float[][] corners = new float[Part.VALUES.length][];
    /** Per part, per corner: {@link Joint} ordinal. */
    public final byte[][] joints = new byte[Part.VALUES.length][];

    private BodyMesh() {
    }

    public float pivot(Joint j, int axis) {
        return pivot[j.ordinal() * 3 + axis];
    }

    public static BodyMesh read(byte[] glb) {
        ByteBuffer b = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt(0) != 0x46546C67) {
            throw new IllegalArgumentException("not a GLB");
        }
        int jsonLength = b.getInt(12);
        JsonObject doc = JsonParser.parseString(new String(glb, 20, jsonLength, StandardCharsets.UTF_8))
                .getAsJsonObject();
        int binStart = 20 + jsonLength + 8;
        BodyMesh m = new BodyMesh();
        JsonArray nodes = doc.getAsJsonArray("nodes");
        int[] parent = new int[nodes.size()];
        java.util.Arrays.fill(parent, -1);
        for (int i = 0; i < nodes.size(); i++) {
            JsonObject n = nodes.get(i).getAsJsonObject();
            if (n.has("rotation") || n.has("scale") || n.has("matrix")) {
                throw new IllegalArgumentException("node " + i + ": rest rotation/scale unsupported");
            }
            if (n.has("children")) {
                for (JsonElement c : n.getAsJsonArray("children")) {
                    parent[c.getAsInt()] = i;
                }
            }
        }
        JsonObject skin = doc.getAsJsonArray("skins").get(0).getAsJsonObject();
        JsonArray skinJoints = skin.getAsJsonArray("joints");
        Joint[] byIndex = new Joint[skinJoints.size()];
        boolean[] seen = new boolean[Joint.VALUES.length];
        for (int i = 0; i < skinJoints.size(); i++) {
            int node = skinJoints.get(i).getAsInt();
            Joint j = Joint.valueOf(nodes.get(node).getAsJsonObject().get("name").getAsString()
                    .toUpperCase(Locale.ROOT));
            byIndex[i] = j;
            seen[j.ordinal()] = true;
            double x = 0, y = 0, z = 0;
            for (int n = node; n >= 0; n = parent[n]) {
                JsonObject o = nodes.get(n).getAsJsonObject();
                if (o.has("translation")) {
                    JsonArray t = o.getAsJsonArray("translation");
                    x += t.get(0).getAsDouble();
                    y += t.get(1).getAsDouble();
                    z += t.get(2).getAsDouble();
                }
            }
            m.pivot[j.ordinal() * 3] = (float) (x * 16);
            m.pivot[j.ordinal() * 3 + 1] = (float) (24 - y * 16);
            m.pivot[j.ordinal() * 3 + 2] = (float) (-z * 16);
        }
        for (Joint j : Joint.VALUES) {
            if (!seen[j.ordinal()]) {
                throw new IllegalArgumentException("missing joint " + j);
            }
        }
        JsonArray accessors = doc.getAsJsonArray("accessors");
        JsonArray views = doc.getAsJsonArray("bufferViews");
        for (JsonElement pe : doc.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives")) {
            JsonObject p = pe.getAsJsonObject();
            Part part = Part.valueOf(p.getAsJsonObject("extras").get("part").getAsString().toUpperCase(Locale.ROOT));
            JsonObject at = p.getAsJsonObject("attributes");
            float[] pos = floats(b, binStart, accessors, views, at.get("POSITION").getAsInt(), 3);
            float[] nrm = floats(b, binStart, accessors, views, at.get("NORMAL").getAsInt(), 3);
            float[] uv = floats(b, binStart, accessors, views, at.get("TEXCOORD_0").getAsInt(), 2);
            float[] w = floats(b, binStart, accessors, views, at.get("WEIGHTS_0").getAsInt(), 4);
            int[] jn = ints(b, binStart, accessors, views, at.get("JOINTS_0").getAsInt(), 4);
            int[] idx = ints(b, binStart, accessors, views, p.get("indices").getAsInt(), 1);
            if (idx.length % 6 != 0) {
                throw new IllegalArgumentException(part + ": index count not quads");
            }
            List<Integer> order = new ArrayList<>();
            for (int t = 0; t < idx.length; t += 6) {
                if (idx[t] != idx[t + 3] || idx[t + 2] != idx[t + 4]) {
                    throw new IllegalArgumentException(part + ": triangles " + t / 3 + " not a quad pair");
                }
                order.add(idx[t]);
                order.add(idx[t + 1]);
                order.add(idx[t + 2]);
                order.add(idx[t + 5]);
            }
            float[] c = new float[order.size() * STRIDE];
            byte[] cj = new byte[order.size()];
            for (int k = 0; k < order.size(); k++) {
                int v = order.get(k);
                if (Math.abs(w[v * 4] - 1) > 1e-4F) {
                    throw new IllegalArgumentException(part + ": vertex " + v + " not single-joint");
                }
                int o = k * STRIDE;
                c[o] = pos[v * 3];
                c[o + 1] = 1.5F - pos[v * 3 + 1];
                c[o + 2] = -pos[v * 3 + 2];
                c[o + 3] = nrm[v * 3];
                c[o + 4] = -nrm[v * 3 + 1];
                c[o + 5] = -nrm[v * 3 + 2];
                c[o + 6] = uv[v * 2];
                c[o + 7] = uv[v * 2 + 1];
                cj[k] = (byte) byIndex[jn[v * 4]].ordinal();
            }
            m.corners[part.ordinal()] = c;
            m.joints[part.ordinal()] = cj;
        }
        return m;
    }

    private static int offset(int binStart, JsonArray accessors, JsonArray views, int a) {
        JsonObject acc = accessors.get(a).getAsJsonObject();
        JsonObject view = views.get(acc.get("bufferView").getAsInt()).getAsJsonObject();
        if (view.has("byteStride")) {
            throw new IllegalArgumentException("interleaved accessor " + a);
        }
        return binStart + (view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0)
                + (acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0);
    }

    private static float[] floats(ByteBuffer b, int bin, JsonArray accessors, JsonArray views, int a, int n) {
        JsonObject acc = accessors.get(a).getAsJsonObject();
        if (acc.get("componentType").getAsInt() != 5126) {
            throw new IllegalArgumentException("accessor " + a + ": float expected");
        }
        int base = offset(bin, accessors, views, a);
        float[] out = new float[acc.get("count").getAsInt() * n];
        for (int i = 0; i < out.length; i++) {
            out[i] = b.getFloat(base + i * 4);
        }
        return out;
    }

    private static int[] ints(ByteBuffer b, int bin, JsonArray accessors, JsonArray views, int a, int n) {
        JsonObject acc = accessors.get(a).getAsJsonObject();
        int type = acc.get("componentType").getAsInt();
        int base = offset(bin, accessors, views, a);
        int[] out = new int[acc.get("count").getAsInt() * n];
        for (int i = 0; i < out.length; i++) {
            out[i] = switch (type) {
                case 5121 -> b.get(base + i) & 0xFF;
                case 5123 -> b.getShort(base + i * 2) & 0xFFFF;
                case 5125 -> b.getInt(base + i * 4);
                default -> throw new IllegalArgumentException("accessor " + a + ": component " + type);
            };
        }
        return out;
    }
}
