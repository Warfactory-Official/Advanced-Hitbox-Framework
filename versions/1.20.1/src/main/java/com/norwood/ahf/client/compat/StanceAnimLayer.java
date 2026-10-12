package com.norwood.ahf.client.compat;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.pose.PoseClip;
import com.norwood.ahf.stance.StancePose;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Stance render layer: the same {@link StancePose} the rig applies. Top priority => applied after every other
 * layer, as the rig applies it after the sampled pose. Suppressed while {@link PlayerAnimSampler} samples, else the
 * rig would apply the stance twice.
 */
public final class StanceAnimLayer implements IAnimation {

    public static final ResourceLocation ID = new ResourceLocation(Ahf.MOD_ID, "stance");
    private static final int PRIORITY = 10_000;

    static boolean sampling;

    private final AbstractClientPlayer player;
    private final double[] scratch = new double[PoseClip.CHANNELS];
    private StancePose pose;
    private final double[] body = new double[PoseClip.CHANNELS];
    private float poseDelta = Float.NaN;
    private int poseTick = Integer.MIN_VALUE;
    private boolean active;
    private int activeTick = Integer.MIN_VALUE;

    private StanceAnimLayer(AbstractClientPlayer player) {
        this.player = player;
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(ID, PRIORITY, StanceAnimLayer::new);
    }

    /** PA asks per part x transform type: one {@link StancePose#of} per tick. */
    @Override
    public boolean isActive() {
        if (sampling) {
            return false;
        }
        if (player.tickCount != activeTick) {
            active = StancePose.of(player, 1.0F) != null;
            activeTick = player.tickCount;
        }
        return active;
    }

    @Override
    public void setupAnim(float tickDelta) {
        pose(tickDelta);
    }

    private StancePose pose(float tickDelta) {
        if (tickDelta != poseDelta || player.tickCount != poseTick) {
            pose = StancePose.of(player, tickDelta);
            poseDelta = tickDelta;
            poseTick = player.tickCount;
        }
        return pose;
    }

    @Override
    public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value) {
        if (sampling) {
            return value;
        }
        StancePose p = pose(tickDelta);
        if (p == null) {
            return value;
        }
        int o = switch (type) {
            case POSITION -> 0;
            case ROTATION -> 3;
            case BEND -> PoseClip.BEND_AXIS;
            default -> -1;
        };
        if (o < 0) {
            return value;
        }
        if (part.equals("body")) {
            java.util.Arrays.fill(body, 0.0);
            p.body(body);
            return new Vec3f(value.getX() + (float) body[o], value.getY() + (float) body[o + 1],
                    o == PoseClip.BEND_AXIS ? value.getZ() : value.getZ() + (float) body[o + 2]);
        }
        int bone = PoseClip.bone(part);
        if (bone < 0) {
            return value;
        }
        java.util.Arrays.fill(scratch, 0.0);
        scratch[o] = value.getX();
        scratch[o + 1] = value.getY();
        if (o != PoseClip.BEND_AXIS) {
            scratch[o + 2] = value.getZ();
        }
        p.apply(bone, scratch);
        return new Vec3f((float) scratch[o], (float) scratch[o + 1],
                o == PoseClip.BEND_AXIS ? value.getZ() : (float) scratch[o + 2]);
    }
}
