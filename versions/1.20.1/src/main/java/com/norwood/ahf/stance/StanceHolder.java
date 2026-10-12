package com.norwood.ahf.stance;

import org.jetbrains.annotations.Nullable;

/** Player stance field (mixin); 1.20.1 stand-in for the NeoForge attachment. */
public interface StanceHolder {

    @Nullable StanceState ahf$stance();

    void ahf$setStance(StanceState s);
}
