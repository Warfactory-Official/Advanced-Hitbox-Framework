package com.norwood.ahf.stance;

import com.mojang.authlib.GameProfile;
import com.norwood.ahf.Ahf;
import com.norwood.ahf.rig.HumanoidRig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Measurement, not a gate: logs per-tick stance cost and the lean eye/rig overhead. */
@GameTestHolder(Ahf.MOD_ID)
@PrefixGameTestTemplate(false)
public class StanceBench {

    private static final int[] PLAYERS = {1, 20, 100};
    private static final int WARMUP = 200;
    private static final int RUNS = 400;

    @GameTest(templateNamespace = Ahf.MOD_ID, template = "empty", required = false, batch = "ahf_bench")
    public void stanceCost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        for (int n : PLAYERS) {
            List<FakePlayer> crowd = new ArrayList<>();
            try {
                for (int i = 0; i < n; i++) {
                    FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ahf_sbench" + i));
                    p.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, i * 37.0F, 0.0F);
                    level.addFreshEntity(p);
                    crowd.add(p);
                    StanceLogic.serverTick(p);
                }
                long tickNs = time(() -> {
                    for (FakePlayer p : crowd) {
                        StanceLogic.serverTick(p);
                    }
                });
                Ahf.LOGGER.info("[ahf-bench] stance serverTick players={} total={} us", n, tickNs / 1000.0);
            } finally {
                crowd.forEach(FakePlayer::discard);
            }
        }

        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ahf_sbench_lean"));
        p.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 180.0F, 0.0F);
        p.yBodyRot = 180.0F;
        level.addFreshEntity(p);
        try {
            StanceLogic.serverTick(p);
            long eyeStand = time(p::getEyePosition);
            long rigStand = time(() -> HumanoidRig.compute(p));
            AhfStance.request(p, StanceRequest.STAND.withLean(1.0F));
            for (int i = 0; i < 6; i++) {
                StanceLogic.serverTick(p);
            }
            long eyeOpen = time(p::getEyePosition);
            long rigLean = time(() -> HumanoidRig.compute(p));
            helper.setBlock(new BlockPos(2, 3, 1), Blocks.STONE);
            long eyeWall = time(p::getEyePosition);
            Ahf.LOGGER.info("[ahf-bench] getEyePosition stand={} us lean(open)={} us lean(wall)={} us;"
                            + " rigCompute stand={} us lean={} us", eyeStand / 1000.0, eyeOpen / 1000.0,
                    eyeWall / 1000.0, rigStand / 1000.0, rigLean / 1000.0);
        } finally {
            p.discard();
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
