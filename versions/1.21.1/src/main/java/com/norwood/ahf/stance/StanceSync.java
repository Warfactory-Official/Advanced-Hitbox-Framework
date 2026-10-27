package com.norwood.ahf.stance;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.Nullable;

/** Delta sync: {@code dirty} fields + body yaw + stamp; the client merges instead of replacing. */
final class StanceSync implements AttachmentSyncHandler<StanceState> {

    // Every delta goes to the owner too, yaw and walk included. Its live client ignores them (it predicts its own body
    // yaw and walks its own legs), but a Flashback recording is made from the owner's packets: without them a replay
    // has no body yaw to show, and the recorded player turns, crouches and peeks relative to a guess.

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
            s.lean = s.leanO = s.effective().leans() ? s.leanTarget : 0.0F;
            s.height = s.heightO = s.effectiveLevel();
            s.sideAmount = s.sideAmountO = s.effectiveSide();
            return s;
        }
        if (holder instanceof Player p && p.isLocalPlayer()) {
            // A delta of yaw / walk alone is only ever sent to an owner by a server that syncs it fully.
            previous.fullFeed |= (u.mask & (StanceState.TARGETS | StanceState.LOCKS)) == 0;
        }
        if (holder instanceof Player p && Replay.ownerPredicts(p, previous)) {
            previous.mergeOwner(u);
        } else {
            previous.enqueue(u);
        }
        return previous;
    }
}
