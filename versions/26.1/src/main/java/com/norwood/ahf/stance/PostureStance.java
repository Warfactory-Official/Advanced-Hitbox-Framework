package com.norwood.ahf.stance;

import com.norwood.ahf.rig.HumanoidRig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** AHF posture as a {@link Stance}: UPRIGHT = KNEEL, DOWNED or leaning / stepped STAND; LYING = PRONE, crawl. */
public final class PostureStance implements Stance {

    public static final PostureStance UPRIGHT = new PostureStance("ahf:upright", true);
    public static final PostureStance LYING = new PostureStance("ahf:lying", false);

    private final String id;
    private final boolean upright;

    private PostureStance(String id, boolean upright) {
        this.id = id;
        this.upright = upright;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean upright() {
        return upright;
    }

    @Override
    public boolean appliesTo(LivingEntity entity) {
        if (!(entity instanceof Player p)) {
            return false;
        }
        StanceState s = AhfStance.get(p);
        if (s == null) {
            return false;
        }
        Posture eff = s.effective();
        boolean posed = upright ? eff == Posture.KNEEL || eff == Posture.DOWNED || eff == Posture.STAND
                && (s.lean != 0.0F || s.height != 0.0F || s.sideAmount != 0.0F) : eff.lying();
        return posed && !StanceLogic.suspended(p);
    }

    @Override
    /** Bones + drop/roll already in {@link HumanoidRig#compute}. */
    public void apply(LivingEntity entity, HumanoidRig.LocalRig rig) {
        apply(entity, rig, 1.0F);
    }

    @Override
    public void apply(LivingEntity entity, HumanoidRig.LocalRig rig, float partialTick) {
        HumanoidRig.applyVanillaTilt(entity, rig, partialTick);
    }
}
