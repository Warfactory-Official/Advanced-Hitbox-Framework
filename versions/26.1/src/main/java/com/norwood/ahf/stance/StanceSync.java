package com.norwood.ahf.stance;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/** Delta sync: {@code dirty} fields + body yaw + stamp; the client merges instead of replacing. */
final class StanceSync implements AttachmentSyncHandler<StanceState> {

    /** 26.1: FakePlayer connections have no channel => NeoForge's {@code hasChannel} NPEs; never sent. */
    @Override
    public boolean sendToPlayer(IAttachmentHolder holder, ServerPlayer to) {
        return !(to instanceof FakePlayer) && sends(holder, to);
    }

    /** Owner predicts its own body yaw and walks its own legs: a yaw/walk-only delta skips it. Initial syncs never run mid-flush. */
    static boolean sends(IAttachmentHolder holder, ServerPlayer to) {
        if (holder != to) {
            return true;
        }
        StanceState s = AhfStance.get(to);
        return s == null || (s.dirty & ~(StanceState.YAW | StanceState.WALK)) != 0;
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf, StanceState s, boolean initialSync) {
        s.write(buf, initialSync ? StanceState.ALL : s.dirty);
    }

    @Override
    public StanceState read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable StanceState previous) {
        StanceState.Update u = StanceState.Update.read(buf);
        if (previous == null) {
            StanceState s = new StanceState();
            s.apply(u);
            s.bodyYawO = s.bodyYaw;
            s.lean = s.leanO = s.effective().lying() ? 0.0F : s.leanTarget;
            s.height = s.heightO = s.effectiveLevel();
            s.sideAmount = s.sideAmountO = s.effectiveSide();
            return s;
        }
        if (holder instanceof Player p && p.isLocalPlayer()) {
            previous.mergeOwner(u);
        } else {
            previous.enqueue(u);
        }
        return previous;
    }
}
