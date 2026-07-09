package com.norwood.ahf.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.ahf.Ahf;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.config.HitRegMode;
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.hit.HitRegistration;
import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.rig.RigCache;
import com.norwood.ahf.rig.RigTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

@EventBusSubscriber(modid = Ahf.MOD_ID, value = Dist.CLIENT)
public final class HitboxDebugRenderer {

    private static final double RANGE = 32.0;
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final int[][] FACES = {
            {0, 2, 6, 4}, {1, 3, 7, 5},
            {0, 4, 5, 1}, {2, 3, 7, 6},
            {0, 1, 3, 2}, {4, 6, 7, 5}
    };
    private static final float FILL_ALPHA = 0.30F;
    public static boolean enabled = false;

    public enum Style {
        EDGES,
        FILLED
    }

    public static Style style = Style.EDGES;

    private HitboxDebugRenderer() {
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static Style cycleStyle(int dir) {
        Style[] all = Style.values();
        int next = Math.floorMod(style.ordinal() + dir, all.length);
        style = all[next];
        return style;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (!enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 cam = new Vec3(event.getLevelRenderState().cameraRenderState.pos.x,
                event.getLevelRenderState().cameraRenderState.pos.y,
                event.getLevelRenderState().cameraRenderState.pos.z);
        AABB area = new AABB(cam.x - RANGE, cam.y - RANGE, cam.z - RANGE,
                cam.x + RANGE, cam.y + RANGE, cam.z + RANGE);
        List<LivingEntity> targets = mc.level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e instanceof Player || AhfHooks.isEnvelopeTarget(e));
        if (targets.isEmpty()) {
            return;
        }

        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ps.last().pose();
        PoseStack.Pose pose = ps.last();
        boolean regActive = AhfConfig.hitRegistrationMode() != HitRegMode.OFF;

        if (style == Style.FILLED) {
            VertexConsumer fillVc = buffers.getBuffer(RenderTypes.debugFilledBox());
            for (LivingEntity e : targets) {
                float rigAlpha = HitboxApi.rigPoseSupported(e) ? 1.0F : 0.4F;
                fillRig(mat, fillVc, e, pt, rigAlpha);
            }
            buffers.endBatch(RenderTypes.debugFilledBox());
        }

        VertexConsumer vc = buffers.getBuffer(RenderTypes.lines());
        for (LivingEntity e : targets) {
            AABB tight = e.getBoundingBox();
            renderLineBox(mat, pose, vc, tight, 0.55F, 0.55F, 0.55F, 0.6F);
            if (regActive) {
                AABB env = HitRegistration.registrationBox(e);
                renderLineBox(mat, pose, vc, env, 1.0F, 1.0F, 1.0F, 0.4F);
            }
            float rigAlpha = HitboxApi.rigPoseSupported(e) ? 1.0F : 0.4F;
            renderRig(mat, pose, vc, e, pt, rigAlpha);
        }
        ps.popPose();
        buffers.endBatch(RenderTypes.lines());
    }

    private static void renderLineBox(Matrix4f mat, PoseStack.Pose pose, VertexConsumer vc,
                                      AABB box, float r, float g, float b, float a) {
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ;
        double x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        line(mat, pose, vc, x0, y0, z0, x1, y0, z0, r, g, b, a);
        line(mat, pose, vc, x0, y0, z0, x0, y1, z0, r, g, b, a);
        line(mat, pose, vc, x0, y0, z0, x0, y0, z1, r, g, b, a);
        line(mat, pose, vc, x1, y0, z0, x1, y1, z0, r, g, b, a);
        line(mat, pose, vc, x1, y0, z0, x1, y0, z1, r, g, b, a);
        line(mat, pose, vc, x0, y1, z0, x1, y1, z0, r, g, b, a);
        line(mat, pose, vc, x0, y1, z0, x0, y1, z1, r, g, b, a);
        line(mat, pose, vc, x0, y0, z1, x1, y0, z1, r, g, b, a);
        line(mat, pose, vc, x0, y0, z1, x0, y1, z1, r, g, b, a);
        line(mat, pose, vc, x1, y1, z0, x1, y1, z1, r, g, b, a);
        line(mat, pose, vc, x1, y0, z1, x1, y1, z1, r, g, b, a);
        line(mat, pose, vc, x0, y1, z1, x1, y1, z1, r, g, b, a);
    }

    private static void renderRig(Matrix4f mat, PoseStack.Pose pose, VertexConsumer vc, LivingEntity e, float pt, float alpha) {
        HumanoidRig.LocalRig rig = RigCache.get(e);
        double[] frame = frame(e, pt);
        HitboxPart hl = RigTuning.ACTIVE ? RigTuning.highlight : null;
        for (Obb obb : rig.all()) {
            float[] c = colorFor(obb.limb());
            float a = limbAlpha(obb.limb(), hl, alpha);
            Vec3[] corners = cornersOf(obb, frame);
            for (int[] edge : EDGES) {
                lineVec(mat, pose, vc, corners[edge[0]], corners[edge[1]], c[0], c[1], c[2], a);
            }
        }
    }

    private static void fillRig(Matrix4f mat, VertexConsumer fillVc, LivingEntity e, float pt, float alpha) {
        HumanoidRig.LocalRig rig = RigCache.get(e);
        double[] frame = frame(e, pt);
        HitboxPart hl = RigTuning.ACTIVE ? RigTuning.highlight : null;
        for (Obb obb : rig.all()) {
            float[] c = colorFor(obb.limb());
            float a = limbAlpha(obb.limb(), hl, alpha) * FILL_ALPHA;
            Vec3[] corners = cornersOf(obb, frame);
            for (int[] face : FACES) {
                Vec3 v0 = corners[face[0]];
                Vec3 v1 = corners[face[1]];
                Vec3 v2 = corners[face[2]];
                Vec3 v3 = corners[face[3]];
                fillTri(mat, fillVc, v0, v1, v2, c[0], c[1], c[2], a);
                fillTri(mat, fillVc, v0, v2, v3, c[0], c[1], c[2], a);
            }
        }
    }

    private static double[] frame(LivingEntity e, float pt) {
        double px = Mth.lerp(pt, e.xOld, e.getX());
        double py = Mth.lerp(pt, e.yOld, e.getY());
        double pz = Mth.lerp(pt, e.zOld, e.getZ());
        double yaw = Math.toRadians(Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot));
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        return new double[]{px, py, pz, fx, fz, -fz, fx};
    }

    private static float limbAlpha(HitboxPart limb, HitboxPart highlight, float alpha) {
        if (highlight == null) {
            return alpha;
        }
        return limb == highlight ? Math.min(1.0F, alpha + 0.4F) : alpha * 0.3F;
    }

    private static Vec3[] cornersOf(Obb obb, double[] frame) {
        double px = frame[0];
        double py = frame[1];
        double pz = frame[2];
        double fx = frame[3];
        double fz = frame[4];
        double rx = frame[5];
        double rz = frame[6];
        Vec3 c = obb.center();
        Vec3 ax = obb.axisX();
        Vec3 ay = obb.axisY();
        Vec3 az = obb.axisZ();
        double hx = obb.half().x;
        double hy = obb.half().y;
        double hz = obb.half().z;
        Vec3[] corners = new Vec3[8];
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    double lx = c.x + sx * hx * ax.x + sy * hy * ay.x + sz * hz * az.x;
                    double ly = c.y + sx * hx * ax.y + sy * hy * ay.y + sz * hz * az.y;
                    double lz = c.z + sx * hx * ax.z + sy * hy * ay.z + sz * hz * az.z;
                    double wx = px + lx * rx + lz * fx;
                    double wy = py + ly;
                    double wz = pz + lx * rz + lz * fz;
                    corners[cornerIndex(sx, sy, sz)] = new Vec3(wx, wy, wz);
                }
            }
        }
        return corners;
    }

    private static void fillTri(Matrix4f mat, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c,
                                float red, float green, float blue, float alpha) {
        vc.addVertex(mat, (float) a.x, (float) a.y, (float) a.z).setColor(red, green, blue, alpha);
        vc.addVertex(mat, (float) b.x, (float) b.y, (float) b.z).setColor(red, green, blue, alpha);
        vc.addVertex(mat, (float) c.x, (float) c.y, (float) c.z).setColor(red, green, blue, alpha);
    }

    private static int cornerIndex(int sx, int sy, int sz) {
        return (sx > 0 ? 1 : 0) | (sy > 0 ? 2 : 0) | (sz > 0 ? 4 : 0);
    }

    private static void lineVec(Matrix4f mat, PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b,
                                float red, float green, float blue, float alpha) {
        line(mat, pose, vc, a.x, a.y, a.z, b.x, b.y, b.z, red, green, blue, alpha);
    }

    private static void line(Matrix4f mat, PoseStack.Pose pose, VertexConsumer vc,
                             double ax, double ay, double az, double bx, double by, double bz,
                             float red, float green, float blue, float alpha) {
        float dx = (float) (bx - ax);
        float dy = (float) (by - ay);
        float dz = (float) (bz - az);
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0e-6f) {
            dx /= len;
            dy /= len;
            dz /= len;
        }
        vc.addVertex(mat, (float) ax, (float) ay, (float) az).setColor(red, green, blue, alpha)
                .setNormal(pose, dx, dy, dz).setLineWidth(1.0f);
        vc.addVertex(mat, (float) bx, (float) by, (float) bz).setColor(red, green, blue, alpha)
                .setNormal(pose, dx, dy, dz).setLineWidth(1.0f);
    }

    private static float[] colorFor(HitboxPart limb) {
        return switch (limb) {
            case HEAD -> new float[]{1.0F, 0.2F, 0.2F};
            case TORSO -> new float[]{0.2F, 1.0F, 0.3F};
            case LEFT_ARM -> new float[]{0.2F, 0.8F, 1.0F};
            case RIGHT_ARM -> new float[]{0.2F, 0.4F, 1.0F};
            case LEFT_LEG -> new float[]{1.0F, 0.9F, 0.2F};
            default -> new float[]{1.0F, 0.5F, 0.1F};
        };
    }
}
