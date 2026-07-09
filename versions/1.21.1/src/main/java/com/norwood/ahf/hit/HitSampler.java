package com.norwood.ahf.hit;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.part.HitboxPart;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class HitSampler {

    private HitSampler() {
    }

    public static HitboxPart pick(LivingEntity victim, DamageSource src, HitCategory cat, RandomSource rand) {
        if (victim != null && AhfConfig.geometricHitLocation()) {
            HitboxPart g = HitboxApi.classifyHit(victim, src, cat);
            if (g != null) {
                return g;
            }
            warnUntraceable(victim, src, cat);
        }
        return pickWeighted(src, cat, rand);
    }

    public static List<HitboxPart> pickPierced(LivingEntity victim, DamageSource src, HitCategory cat,
                                               RandomSource rand) {
        if (victim != null && AhfConfig.geometricHitLocation()) {
            List<HitboxPart> pierced = HitboxApi.classifyHitPierced(victim, src, cat);
            if (!pierced.isEmpty()) {
                return pierced;
            }
            warnUntraceable(victim, src, cat);
        }
        return List.of(pickWeighted(src, cat, rand));
    }

    private static void warnUntraceable(LivingEntity victim, DamageSource src, HitCategory cat) {
        if (src == null) {
            return;
        }
        Entity attacker = src.getEntity();
        Entity direct = src.getDirectEntity();
        boolean directional = (attacker != null && attacker != victim) || (direct != null && direct != victim);
        if (!directional) {
            return;
        }
        if (HitboxApi.resolveHitPoint(victim, src, cat) != null) {
            return;
        }
        Ahf.LOGGER.warn(
                "Untraceable hit direction: could not reconstruct a hit position on {} for damage '{}' (category "
                        + "{}, attacker={}, projectile={}) -- fell back to weighted limb sampling.",
                victim.getName().getString(),
                src.getMsgId(),
                cat,
                attacker != null ? attacker.getName().getString() : "none",
                direct != null && direct != attacker ? direct.getName().getString() : "none");
    }

    private static HitboxPart pickWeighted(DamageSource source, HitCategory cat, RandomSource rand) {
        HitboxPart[] limbs = HitboxPart.VALUES;
        float[] weights = new float[limbs.length];
        float total = 0.0F;
        for (int i = 0; i < limbs.length; i++) {
            float w = limbs[i].getHitWeight() * categoryBias(limbs[i], cat);
            if (w < 0.0F) {
                w = 0.0F;
            }
            weights[i] = w;
            total += w;
        }
        if (total <= 0.0F || rand == null) {
            return HitboxPart.TORSO;
        }
        float roll = rand.nextFloat() * total;
        float acc = 0.0F;
        for (int i = 0; i < limbs.length; i++) {
            acc += weights[i];
            if (roll < acc) {
                return limbs[i];
            }
        }
        return limbs[limbs.length - 1];
    }

    private static float categoryBias(HitboxPart limb, HitCategory cat) {
        if (cat == null) {
            return 1.0F;
        }
        switch (cat) {
            case BALLISTIC:
            case PIERCING:
                if (limb == HitboxPart.TORSO) {
                    return 1.35F;
                }
                if (limb == HitboxPart.HEAD) {
                    return 1.5F;
                }
                return 0.85F;
            case FALL:
                if (limb.isLeg()) {
                    return 2.5F;
                }
                if (limb == HitboxPart.TORSO) {
                    return 0.6F;
                }
                return 0.0F;
            case EXPLOSION:
                return limb.isVital() ? 0.9F : 1.2F;
            default:
                return 1.0F;
        }
    }
}
