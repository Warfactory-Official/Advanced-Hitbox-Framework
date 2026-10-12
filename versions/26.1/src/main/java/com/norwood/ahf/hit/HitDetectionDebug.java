package com.norwood.ahf.hit;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.HumanoidRig;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

final class HitDetectionDebug {

    private static final double AMBIGUOUS_MARGIN = 0.15;

    private HitDetectionDebug() {
    }

    static void logClassifyHit(LivingEntity victim, DamageSource src, HitCategory cat, String branch,
                               @Nullable HitboxPart result) {
        Entity attacker = src.getEntity();
        Ahf.LOGGER.info("[{}] classify: attacker={} victim={} cat={} branch={} result={} pose={} dist={}",
                Ahf.MOD_ID, name(attacker), victim.getName().getString(), cat, branch, result,
                HumanoidRig.resolvePose(victim),
                attacker == null ? "?" : String.format("%.2f", attacker.position().distanceTo(victim.position())));
    }

    static void logRigPointPick(LivingEntity victim, @Nullable DamageSource src, String branch,
                                HumanoidRig.LocalRig rig, Vec3 local, HitboxPart winner) {
        Obb[] all = rig.all();
        double[] dist = new double[all.length];
        Integer[] order = new Integer[all.length];
        for (int i = 0; i < all.length; i++) {
            dist[i] = Math.sqrt(all[i].distanceSq(local));
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(dist[a], dist[b]));
        StringBuilder ranked = new StringBuilder();
        for (int i = 0; i < order.length; i++) {
            if (i > 0) {
                ranked.append(", ");
            }
            ranked.append(all[order[i]].limb()).append('=').append(String.format("%.3f", dist[order[i]]));
        }
        double margin = order.length > 1 ? dist[order[1]] - dist[order[0]] : Double.POSITIVE_INFINITY;
        String attacker = src == null ? "?" : name(src.getEntity());
        if (margin < AMBIGUOUS_MARGIN) {
            Ahf.LOGGER.warn("[{}] AMBIGUOUS POINT-PICK ({}): {} on {} -> {}, runner-up +{}m (< {}m): [{}]",
                    Ahf.MOD_ID, branch, attacker, victim.getName().getString(), winner,
                    String.format("%.3f", margin), AMBIGUOUS_MARGIN, ranked);
        } else {
            Ahf.LOGGER.info("[{}] point-pick ({}): {} on {} -> {}: [{}]",
                    Ahf.MOD_ID, branch, attacker, victim.getName().getString(), winner, ranked);
        }
    }

    static void logRandomFallback(LivingEntity victim, @Nullable DamageSource src, HitCategory cat,
                                  HitboxPart chosen) {
        Ahf.LOGGER.warn("[{}] RANDOM FALLBACK: no geometric hit location for attacker={} victim={} cat={} -> {}",
                Ahf.MOD_ID, src == null ? "?" : name(src.getEntity()), victim.getName().getString(), cat, chosen);
    }

    private static String name(@Nullable Entity e) {
        return e == null ? "?" : e.getName().getString();
    }
}
