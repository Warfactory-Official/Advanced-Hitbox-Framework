package com.norwood.ahf.client;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.network.StanceIntentPacket;
import com.norwood.ahf.network.StanceRollPacket;
import com.norwood.ahf.stance.AhfStance;
import com.norwood.ahf.stance.Posture;
import com.norwood.ahf.stance.StanceLogic;
import com.norwood.ahf.stance.StanceRequest;
import com.norwood.ahf.stance.StanceState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Ahf.MOD_ID, value = Dist.CLIENT)
public final class StanceClient {

    private StanceClient() {
    }

    /** Local player's stance request: predicted at once, validated and seq-acked by the server. */
    public static boolean request(StanceRequest r) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return false;
        }
        int seq = StanceLogic.predict(p, r);
        if (seq < 0) {
            return false;
        }
        PacketDistributor.sendToServer(new StanceIntentPacket(seq, r));
        return true;
    }

    /** Local prone roll toward {@code dir} (+1 right): predicted, then started or refused by the server. */
    public static boolean roll(int dir) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !StanceLogic.predictRoll(p, dir)) {
            return false;
        }
        PacketDistributor.sendToServer(new StanceRollPacket(dir));
        return true;
    }

    /** Rolling: movement input dropped (the roll moves the body). */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        StanceState s = AhfStance.get(event.getEntity());
        if (s != null && s.rolling()) {
            var in = event.getInput();
            in.forwardImpulse = in.leftImpulse = 0.0F;
            in.up = in.down = in.left = in.right = in.jumping = false;
        } else if (s != null && s.effective() != Posture.STAND || event.getEntity().isCrouching()) {
            event.getInput().jumping = false;
        }
    }

    /** First person only: a third-person camera orbits the body, it does not lean with it. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!event.getCamera().isDetached() && event.getCamera().getEntity() instanceof Player p) {
            float roll = AhfStance.cameraRoll(p, (float) event.getPartialTick());
            if (roll != 0.0F) {
                event.setRoll(event.getRoll() + roll);
            }
        }
    }
}
