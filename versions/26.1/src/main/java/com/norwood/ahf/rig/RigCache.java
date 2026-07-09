package com.norwood.ahf.rig;

import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.hit.HitAuthority;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ConcurrentHashMap;

public final class RigCache {

    private static final ConcurrentHashMap<Long, Entry> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Hint> HINTS = new ConcurrentHashMap<>();
    private static final int MAX_ENTRIES = 1024;

    private RigCache() {
    }

    public static HumanoidRig.LocalRig resolve(LivingEntity victim) {
        if (!victim.level().isClientSide() && AhfConfig.hitAuthority() == HitAuthority.CLIENT_HINT) {
            HumanoidRig.LocalRig hinted = validHint(victim);
            if (hinted != null) {
                return hinted;
            }
        }
        return get(victim);
    }

    public static HumanoidRig.LocalRig get(LivingEntity victim) {
        long gameTime = victim.level().getGameTime();
        long k = key(victim);
        Entry e = CACHE.get(k);
        if (e != null && e.tick == gameTime) {
            return e.rig;
        }
        HumanoidRig.LocalRig rig = HumanoidRig.compute(victim);
        if (CACHE.size() > MAX_ENTRIES) {
            CACHE.clear();
        }
        CACHE.put(k, new Entry(gameTime, rig));
        return rig;
    }

    public static void submitHint(int entityId, HumanoidRig.LocalRig rig, long receivedTick) {
        if (HINTS.size() > MAX_ENTRIES) {
            HINTS.clear();
        }
        HINTS.put(entityId, new Hint(rig, receivedTick));
    }

    public static void clearHint(int entityId) {
        HINTS.remove(entityId);
    }

    private static HumanoidRig.LocalRig validHint(LivingEntity victim) {
        Hint h = HINTS.get(victim.getId());
        if (h == null) {
            return null;
        }
        long age = victim.level().getGameTime() - h.receivedTick;
        if (age < 0 || age > AhfConfig.poseHintMaxAgeTicks()) {
            return null;
        }
        return plausible(victim, h.rig) ? h.rig : null;
    }

    private static boolean plausible(LivingEntity victim, HumanoidRig.LocalRig rig) {
        double margin = AhfConfig.poseHintMargin();
        double reach = Math.max(victim.getBbHeight(), 1.9) + margin;
        double reachSq = reach * reach;
        double maxHalf = 1.0 + margin;
        double minHalf = 0.01;
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            Obb o = slot.get(rig);
            if (o == null) {
                return false;
            }
            Vec3 c = o.center();
            if (c.x * c.x + c.y * c.y + c.z * c.z > reachSq) {
                return false;
            }
            Vec3 hf = o.half();
            if (hf.x < minHalf || hf.y < minHalf || hf.z < minHalf
                    || hf.x > maxHalf || hf.y > maxHalf || hf.z > maxHalf) {
                return false;
            }
        }
        return true;
    }

    private static long key(LivingEntity e) {
        long id = e.getId() & 0xFFFFFFFFL;
        long side = e.level().isClientSide() ? 1L : 0L;
        return (side << 32) | id;
    }

    private record Entry(long tick, HumanoidRig.LocalRig rig) {
    }

    private record Hint(HumanoidRig.LocalRig rig, long receivedTick) {
    }
}
