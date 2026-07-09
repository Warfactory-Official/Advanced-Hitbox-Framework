package com.norwood.ahf.stance;

import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AhfStances {

    private static final CopyOnWriteArrayList<Stance> STANCES = new CopyOnWriteArrayList<>();

    private AhfStances() {
    }

    public static void register(Stance s) {
        STANCES.add(s);
    }

    @Nullable
    public static Stance active(LivingEntity e) {
        Stance best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (Stance s : STANCES) {
            if (s.appliesTo(e) && s.priority() > bestPriority) {
                best = s;
                bestPriority = s.priority();
            }
        }
        return best;
    }

    public static boolean isActive(LivingEntity e) {
        return active(e) != null;
    }

    public static List<Stance> all() {
        return Collections.unmodifiableList(new ArrayList<>(STANCES));
    }
}
