package com.norwood.ahf.stance;

import com.mojang.authlib.GameProfile;
import com.norwood.ahf.Ahf;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(Ahf.MOD_ID)
@PrefixGameTestTemplate(false)
public class StanceSyncGameTest {

    @GameTest(templateNamespace = Ahf.MOD_ID, template = "empty")
    public void aYawOnlyDeltaSkipsTheOwnerOnly(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 1));
        FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ahf_owner"));
        FakePlayer tracker = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ahf_tracker"));
        owner.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        StanceState s = AhfStance.getOrCreate(owner);
        StanceSync sync = new StanceSync();
        s.dirty = StanceState.YAW;
        helper.assertFalse(sync.sendToPlayer(owner, owner), "yaw-only delta sent to its owner");
        helper.assertTrue(sync.sendToPlayer(owner, tracker), "yaw-only delta withheld from a tracker");
        s.dirty = StanceState.YAW | StanceState.TARGETS;
        helper.assertTrue(sync.sendToPlayer(owner, owner), "target delta withheld from its owner (seq ack lost)");
        s.dirty = 0;
        helper.succeed();
    }
}
