package com.norwood.ahf.stance;

import com.mojang.authlib.GameProfile;
import com.norwood.ahf.Ahf;
import net.minecraft.core.BlockPos;
import com.norwood.ahf.gametest.AhfGameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

public class StanceSyncGameTest {

    @AhfGameTest
    public void aYawOnlyDeltaSkipsTheOwnerOnly(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 1));
        FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ahf_owner"));
        FakePlayer tracker = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ahf_tracker"));
        owner.snapTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        StanceState s = owner.getData(AhfStance.TYPE);
        s.dirty = StanceState.YAW;
        helper.assertFalse(StanceSync.sends(owner, owner), "yaw-only delta sent to its owner");
        helper.assertTrue(StanceSync.sends(owner, tracker), "yaw-only delta withheld from a tracker");
        s.dirty = StanceState.YAW | StanceState.TARGETS;
        helper.assertTrue(StanceSync.sends(owner, owner), "target delta withheld from its owner (seq ack lost)");
        s.dirty = 0;
        helper.succeed();
    }
}
