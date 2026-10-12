package com.norwood.ahf.mixin;

import com.norwood.ahf.stance.StanceHolder;
import com.norwood.ahf.stance.StanceState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
public abstract class PlayerStanceMixin implements StanceHolder {

    @Unique
    private StanceState ahf$stance;

    @Override
    public StanceState ahf$stance() {
        return ahf$stance;
    }

    @Override
    public void ahf$setStance(StanceState s) {
        ahf$stance = s;
    }
}
