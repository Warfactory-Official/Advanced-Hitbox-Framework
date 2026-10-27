package com.norwood.ahf.client.compat;

import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.pose.GunPoses;
import com.norwood.ahf.pose.PoseClip;
import com.norwood.ahf.pose.PoseLibrary;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * Render half of {@link PoseLibrary} sets owned by a mod without its own PlayerAnimator layering: while
 * {@link AhfHooks#poseSet} is one of {@code sets}, lower ({@code priority}), loop upper (+1) and rotation (+2)
 * layers play {@link GunPoses#select} through {@link PoseClip} (the rig's evaluator). No one-shots. Client setup,
 * PlayerAnimator loaded.
 */
public final class PoseSetLayers {
    private final Set<ResourceLocation> sets;
    private final ResourceLocation lower;
    private final ResourceLocation upper;
    private final ResourceLocation rotation;

    private PoseSetLayers(ResourceLocation id, Set<ResourceLocation> sets) {
        this.sets = Set.copyOf(sets);
        lower = id.withSuffix("_lower");
        upper = id.withSuffix("_upper");
        rotation = id.withSuffix("_rotation");
    }

    /** {@code id}: layer ids' prefix (the owner's namespace). Main thread. */
    public static void register(ResourceLocation id, int priority, Set<ResourceLocation> sets) {
        PoseSetLayers l = new PoseSetLayers(id, sets);
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(l.lower, priority, p -> new ModifierLayer<>());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(l.upper, priority + 1,
                p -> new ModifierLayer<>());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(l.rotation, priority + 2, RotationLayer::new);
        NeoForge.EVENT_BUS.addListener(l::onRenderPlayer);
    }

    private void onRenderPlayer(RenderPlayerEvent.Pre e) {
        AbstractClientPlayer p = (AbstractClientPlayer) e.getEntity();
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        ResourceLocation set = AhfHooks.poseSet(p);
        GunPoses.Selection sel = set != null && sets.contains(set)
                ? GunPoses.select(p, Math.min(p.walkAnimation.speed(e.getPartialTick()), 1.0F)) : null;
        if (PlayerAnimationAccess.getPlayerAssociatedData(p).get(rotation) instanceof RotationLayer r) {
            r.sel = sel;
        }
        play(p, lower, sel == null ? null : sel.set(), sel == null ? null : sel.lower());
        play(p, upper, sel == null ? null : sel.set(), sel == null ? null : sel.upper());
    }

    @SuppressWarnings("unchecked")
    private static void play(AbstractClientPlayer p, ResourceLocation id, @Nullable ResourceLocation set,
                             @Nullable String name) {
        if (!(PlayerAnimationAccess.getPlayerAssociatedData(p).get(id) instanceof ModifierLayer<?> raw)) {
            return;
        }
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) raw;
        PoseClip clip = set == null || name == null ? null : PoseLibrary.clip(set, name);
        if (clip == null) {
            if (layer.isActive()) {
                layer.replaceAnimationWithFade(fade(), null);
            }
            return;
        }
        IAnimation cur = layer.getAnimation();
        if (cur instanceof PhasedClip c && c.clip() == clip || cur instanceof TimedClip t && t.clip == clip) {
            return;
        }
        layer.replaceAnimationWithFade(fade(), GunPoses.phased(name) ? new PhasedClip(p, clip) : new TimedClip(clip));
    }

    private static AbstractFadeModifier fade() {
        return AbstractFadeModifier.standardFadeIn(PoseClip.FADE_TICKS, Ease.INOUTSINE);
    }

    /** PlayerAnimator {@code KeyframeAnimationPlayer} timing over a {@link PoseClip}. */
    private static final class TimedClip implements IAnimation {
        final PoseClip clip;
        private int tick;
        private boolean loopStarted;
        private float delta;

        TimedClip(PoseClip clip) {
            this.clip = clip;
        }

        @Override
        public void tick() {
            tick++;
            if (clip.looped && tick > clip.endTick) {
                tick = clip.returnTick;
                loopStarted = true;
            }
        }

        @Override
        public boolean isActive() {
            return clip.looped || tick <= clip.stopTick;
        }

        @Override
        public void setupAnim(float tickDelta) {
            delta = tickDelta;
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value) {
            int bone = PoseClip.bone(part);
            int ch = channel(type);
            if (bone < 0 || ch < 0) {
                return value;
            }
            float x = clip.value(bone, ch, tick, delta, loopStarted, value.getX());
            float y = clip.value(bone, ch + 1, tick, delta, loopStarted, value.getY());
            return new Vec3f(x, y, ch == PoseClip.BEND_AXIS ? value.getZ()
                    : clip.value(bone, ch + 2, tick, delta, loopStarted, value.getZ()));
        }
    }

    private static int channel(TransformType type) {
        return switch (type) {
            case POSITION -> 0;
            case ROTATION -> 3;
            case BEND -> PoseClip.BEND_AXIS;
            default -> -1;
        };
    }

    /** {@link GunPoses#adjust} as the rig: body rotation = 0 + yaw adjust, waist share about the torso's parent x. */
    private static final class RotationLayer implements IAnimation {
        private final AbstractClientPlayer player;
        private final double[] v = new double[PoseClip.CHANNELS];
        /** This frame's incoming {@code torso} channels (PA asks the parts before the waist bend). */
        private final double[] torso = new double[PoseClip.CHANNELS];
        @Nullable
        GunPoses.Selection sel;
        private double lie;

        RotationLayer(AbstractClientPlayer player) {
            this.player = player;
        }

        @Override
        public boolean isActive() {
            return sel != null;
        }

        @Override
        public void setupAnim(float tickDelta) {
            lie = GunPoses.lie(player, tickDelta);
        }

        @Override
        public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value) {
            int bone = PoseClip.bone(part);
            int o = channel(type);
            if (sel == null || o < 0 || bone < 0 || bone > PoseClip.BODY) {
                return value;
            }
            Arrays.fill(v, 0);
            if (!(bone == PoseClip.BODY && type == TransformType.ROTATION)) {
                v[o] = value.getX();
                v[o + 1] = value.getY();
                if (o != PoseClip.BEND_AXIS) {
                    v[o + 2] = value.getZ();
                }
            }
            if (bone == PoseClip.TORSO && type == TransformType.ROTATION) {
                System.arraycopy(v, 3, torso, 3, 3);
            }
            GunPoses.adjust(player, tickDelta, lie, bone, v, torso);
            return new Vec3f((float) v[o], (float) v[o + 1], o == PoseClip.BEND_AXIS ? value.getZ() : (float) v[o + 2]);
        }
    }
}
