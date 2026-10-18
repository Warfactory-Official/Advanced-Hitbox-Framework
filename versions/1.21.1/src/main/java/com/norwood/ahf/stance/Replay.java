package com.norwood.ahf.stance;

import net.minecraft.world.entity.player.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Flashback replay state. In a replay the "local player" is the recorded player, replayed from packets: nothing
 * drives its input, so owner prediction (body yaw from the look, own walk, own roll, input-driven crouch) has no
 * truth to follow. It is fed like any remote player instead, from the server's own state. Flashback is optional,
 * so this is reflective; absent or failing => not a replay.
 */
final class Replay {
    private static final MethodHandle IN_REPLAY = resolve();

    private Replay() {
    }

    static boolean active() {
        if (IN_REPLAY == null) {
            return false;
        }
        try {
            return (boolean) IN_REPLAY.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The client predicts this player itself: the real local player. A replayed one is fed from the server's state
     * instead, once the recording is known to carry it ({@link StanceState#fullFeed}); an older recording has no yaw
     * or walk for its owner, and predicting from the look is then the best there is.
     */
    static boolean ownerPredicts(Player p, StanceState s) {
        return p.isLocalPlayer() && !(s.fullFeed && active());
    }

    private static MethodHandle resolve() {
        try {
            return MethodHandles.publicLookup().findStatic(Class.forName("com.moulberry.flashback.Flashback"),
                    "isInReplay", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
