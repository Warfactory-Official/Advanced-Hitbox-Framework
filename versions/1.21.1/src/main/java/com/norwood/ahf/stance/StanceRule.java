package com.norwood.ahf.stance;

import net.minecraft.world.entity.player.Player;

/** Consumer stance rule; runs on the owner (prediction) and the server (validation) with the same inputs. */
@FunctionalInterface
public interface StanceRule {
    boolean allow(Player player, StanceState current, StanceRequest request);
}
