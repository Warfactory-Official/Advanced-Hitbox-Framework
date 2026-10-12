package com.norwood.ahf.client.compat;

import com.norwood.ahf.client.body.BodyRenderer;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.core.util.Pair;
import dev.kosmx.playerAnim.impl.IMutableModel;
import net.minecraft.client.model.PlayerModel;

/** PlayerAnimator bends of the model's current animation ({@code torso} + {@code body} -> waist, as PA's torso cube). */
public final class PlayerAnimBends implements BodyRenderer.BendSource {

    private static final String[] PARTS = {"body", "rightArm", "leftArm", "rightLeg", "leftLeg"};

    private PlayerAnimBends() {
    }

    public static void register() {
        BodyRenderer.setBendSource(new PlayerAnimBends());
    }

    @Override
    public boolean bends(PlayerModel<?> model, float[] out) {
        AnimationProcessor a = ((IMutableModel) model).getEmoteSupplier().get();
        if (a == null || !a.isActive()) {
            return false;
        }
        for (int i = 0; i < PARTS.length; i++) {
            Pair<Float, Float> b = a.getBend(PARTS[i]);
            out[i * 2] = b.getLeft();
            out[i * 2 + 1] = b.getRight();
        }
        Pair<Float, Float> torso = a.getBend("torso");
        out[0] += torso.getLeft();
        out[1] += torso.getRight();
        return true;
    }
}
