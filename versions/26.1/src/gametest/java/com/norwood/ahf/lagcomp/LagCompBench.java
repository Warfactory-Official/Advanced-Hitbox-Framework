package com.norwood.ahf.lagcomp;

import com.mojang.authlib.GameProfile;
import com.norwood.ahf.Ahf;
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.rig.HumanoidRig;
import net.minecraft.core.BlockPos;
import com.norwood.ahf.gametest.AhfGameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Measurement, not a gate: logs capture/lookup cost. One callback => the automatic capture never sees the crowd.
 * Own batch: capture writes every entity in the level.
 */
public class LagCompBench {

    private static final int[][] CASES = {{1, 0}, {20, 0}, {100, 0}, {20, 500}, {100, 2000}};
    private static final int WARMUP = 200;
    private static final int RUNS = 400;

    @AhfGameTest(required = false, batch = "ahf_bench")
    public void captureCost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int[] c : CASES) {
            List<Entity> crowd = new ArrayList<>();
            try {
                for (int i = 0; i < c[0]; i++) {
                    FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ahf_bench" + i));
                    p.snapTo(base.getX() + 0.5 + (i % 10) * 0.7, base.getY(), base.getZ() + 0.5 + (i / 10) * 0.7,
                            i * 37.0F, 0.0F);
                    p.yBodyRot = i * 37.0F;
                    level.addFreshEntity(p);
                    crowd.add(p);
                }
                for (int i = 0; i < c[1]; i++) {
                    ArmorStand s = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.LOAD);
                    s.snapTo(base.getX() + (i % 40) * 0.5, base.getY(), base.getZ() + (i / 40) * 0.5, 0.0F, 0.0F);
                    level.addFreshEntity(s);
                    crowd.add(s);
                }
                long captureNs = time(() -> EntityHistory.capture(level));
                int tracked = EntityHistory.trackedCount(level);
                long t = EntityHistory.broadcastTick(level);
                Vec3 from = new Vec3(base.getX() - 10.0, base.getY() + 1.0, base.getZ() + 2.0);
                Vec3 to = new Vec3(base.getX() + 30.0, base.getY() + 1.0, base.getZ() + 2.0);
                List<LivingEntity> out = new ArrayList<>();
                long candNs = time(() -> {
                    out.clear();
                    EntityHistory.candidates(level, t, from, to, out);
                });
                Entity target = crowd.get(0);
                Vec3 f2 = target.position().add(0.0, 1.0, 2.0);
                Vec3 t2 = target.position().add(0.0, 1.0, -2.0);
                long rayNs = time(() -> HitboxApi.classifyRay((FakePlayer) target, f2, t2, t));
                long rigNs = time(() -> HumanoidRig.compute((FakePlayer) target));
                out.clear();
                EntityHistory.candidates(level, t, from, to, out);
                int hits = out.size();
                Ahf.LOGGER.info("[ahf-bench] players={} stands={} tracked={} capture={} us candidates={} us ({} hit)"
                                + " rewoundRay={} us rigCompute={} us", c[0], c[1], tracked, captureNs / 1000.0,
                        candNs / 1000.0, hits, rayNs / 1000.0, rigNs / 1000.0);
            } finally {
                crowd.forEach(Entity::discard);
            }
        }
        helper.succeed();
    }

    /** Median ns over RUNS after WARMUP. */
    private static long time(Runnable r) {
        for (int i = 0; i < WARMUP; i++) {
            r.run();
        }
        long[] ns = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long s = System.nanoTime();
            r.run();
            ns[i] = System.nanoTime() - s;
        }
        java.util.Arrays.sort(ns);
        return ns[RUNS / 2];
    }
}
