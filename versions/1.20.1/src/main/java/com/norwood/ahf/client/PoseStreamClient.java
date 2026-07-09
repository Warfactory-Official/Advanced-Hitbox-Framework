package com.norwood.ahf.client;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.rig.RigCache;
import com.norwood.ahf.network.AhfNetwork;
import com.norwood.ahf.network.PoseStreamPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Ahf.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PoseStreamClient {

    private static final float CHANGE_EPSILON = 1.0e-3F;
    private static volatile boolean enabled = false;
    private static long lastSentTick = Long.MIN_VALUE;
    private static float[] lastSent;

    private PoseStreamClient() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            lastSent = null;
            lastSentTick = Long.MIN_VALUE;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long now = player.level().getGameTime();
        long since = now - lastSentTick;
        if (since < AhfConfig.poseStreamMinIntervalTicks()) {
            return;
        }
        HumanoidRig.LocalRig rig = RigCache.get(player);
        float[] flat = flatten(rig);
        boolean changed = !nearlyEqual(flat, lastSent);
        if (!changed && since < AhfConfig.poseStreamMaxIntervalTicks()) {
            return;
        }
        AhfNetwork.sendToServer(new PoseStreamPacket(rig));
        lastSent = flat;
        lastSentTick = now;
    }

    private static float[] flatten(HumanoidRig.LocalRig rig) {
        float[] a = new float[HumanoidRig.LocalRig.SLOTS.length * 15];
        int i = 0;
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            Obb o = slot.get(rig);
            i = put(a, i, o.center());
            i = put(a, i, o.axisX());
            i = put(a, i, o.axisY());
            i = put(a, i, o.axisZ());
            i = put(a, i, o.half());
        }
        return a;
    }

    private static int put(float[] a, int i, Vec3 v) {
        a[i] = (float) v.x;
        a[i + 1] = (float) v.y;
        a[i + 2] = (float) v.z;
        return i + 3;
    }

    private static boolean nearlyEqual(float[] a, float[] b) {
        if (b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > CHANGE_EPSILON) {
                return false;
            }
        }
        return true;
    }
}
