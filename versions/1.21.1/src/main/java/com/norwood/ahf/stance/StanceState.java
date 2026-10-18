package com.norwood.ahf.stance;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

import java.util.ArrayDeque;

/**
 * Per-player stance attachment. Synced: targets + seq (owner-predicted), locks, body yaw, walk animation, stamp
 * (server-only). Eased {@code lean}/{@code adsProgress} and their previous values are local to each side.
 */
public final class StanceState {

    static final int TARGETS = 1;
    static final int LOCKS = 2;
    static final int YAW = 4;
    static final int WALK = 8;
    static final int ALL = TARGETS | LOCKS;
    /** Server walk speed: displacement averaged over this many ticks (move packets bunch under jitter). */
    static final int WALK_WINDOW = 3;
    /** Resync when the tracker's dead reckoning ({@code pos += speed} per tick) drifts past these. */
    static final float WALK_SPEED_EPSILON = 0.01F;
    static final float WALK_POS_EPSILON = 0.05F;
    private static final int MAX_PENDING = 64;

    Posture posture = Posture.STAND;
    int level;
    int side;
    /** Prone roll: started rolls (synced; a newer one starts here), direction, ticks into it (local, both sides). */
    int rollSeq;
    int rollDir;
    int rollTicks;
    float leanTarget;
    Rest rest = Rest.NONE;
    boolean ads;
    float aimYaw;
    float aimPitch;
    int seq;

    boolean downed;
    boolean crawl;
    /** Client, owner: the stream carries yaw / walk deltas for it (a recording made with the owner fully synced). */
    boolean fullFeed;
    float bodyYaw;
    long stamp;
    /** Vanilla {@code WalkAnimationState} recurrence: server-computed, remote trackers adopt it. */
    float walkPos;
    float walkSpeed;
    boolean walkSynced;
    /** Remote: a {@code WALK} update applied this tick (else dead reckoning). */
    boolean walkApplied;

    float lean;
    float leanO;
    /** Eased {@link #effectiveLevel()} / {@link #effectiveSide()}. */
    float height;
    float heightO;
    float sideAmount;
    float sideAmountO;
    float adsProgress;
    float adsProgressO;
    float bodyYawO;
    /** Turn in place under way ({@link StanceLogic#stepBodyYaw(StanceState, float, boolean)}); local, both sides. */
    boolean turning;
    /** Posture transition: eased pose leaves {@code from} (frozen at {@code fromHeight} / {@code fromSide}). */
    Posture shown = Posture.STAND;
    Posture from = Posture.STAND;
    float fromHeight;
    float fromSide;
    float blend = 1.0F;
    float blendO = 1.0F;
    /** Lying amount (render + rig tilt), 0 upright .. 1 flat. */
    float lie;
    float lieO;

    int dirty;
    int intents;
    float sentBodyYaw;
    float sentWalkPos;
    float sentWalkSpeed;
    /** Server ticks since the last {@code WALK} went out. */
    int walkSentAge;
    /** Server: last {@link #WALK_WINDOW} + 1 horizontal positions, ring. */
    final double[] walkX = new double[WALK_WINDOW + 1];
    final double[] walkZ = new double[WALK_WINDOW + 1];
    int walkSamples;
    boolean ownsForcedPose;
    Posture appliedShape = Posture.STAND;
    int appliedLevel;
    final ArrayDeque<Update> pending = new ArrayDeque<>();

    StanceState() {
    }

    static StanceState create(IAttachmentHolder holder) {
        StanceState s = new StanceState();
        if (holder instanceof LivingEntity e) {
            s.bodyYaw = e.yBodyRot;
            s.bodyYawO = e.yBodyRot;
        }
        s.dirty = ALL;
        return s;
    }

    /** DOWNED > crawl (PRONE) > requested. */
    public Posture effective() {
        return downed ? Posture.DOWNED : crawl ? Posture.PRONE : posture;
    }

    public Posture requested() {
        return posture;
    }

    public int level() {
        return level;
    }

    public int side() {
        return side;
    }

    /** Requested level while the requested posture is the effective one; else 0. */
    public int effectiveLevel() {
        return effective() == posture ? level : 0;
    }

    public int effectiveSide() {
        return effective() == posture ? side : 0;
    }

    public float height(float partialTick) {
        return Mth.lerp(partialTick, heightO, height);
    }

    public float sideAmount(float partialTick) {
        return Mth.lerp(partialTick, sideAmountO, sideAmount);
    }

    public Posture from() {
        return from;
    }

    public float fromHeight() {
        return fromHeight;
    }

    public float fromSide() {
        return fromSide;
    }

    /** Transition progress from {@link #from()} to the effective posture, 0..1 (1 = settled). */
    public float blend(float partialTick) {
        return Mth.lerp(partialTick, blendO, blend);
    }

    public float lie(float partialTick) {
        return Mth.lerp(partialTick, lieO, lie);
    }

    public boolean rolling() {
        return rollDir != 0;
    }

    public int rollDir() {
        return rollDir;
    }

    /** Roll progress [0, 1]; 0 when not rolling. */
    public float rollProgress(float partialTick) {
        return rollDir == 0 ? 0.0F : Math.min((rollTicks + partialTick) / StanceLogic.ROLL_TICKS, 1.0F);
    }

    void startRoll(int dir) {
        rollSeq++;
        rollDir = dir;
        rollTicks = 0;
    }

    void stepRoll() {
        if (rollDir != 0 && ++rollTicks >= StanceLogic.ROLL_TICKS) {
            rollDir = 0;
        }
    }

    public float leanTarget() {
        return leanTarget;
    }

    public float lean(float partialTick) {
        return Mth.lerp(partialTick, leanO, lean);
    }

    public Rest rest() {
        return rest;
    }

    public boolean ads() {
        return ads;
    }

    public float adsProgress(float partialTick) {
        return Mth.lerp(partialTick, adsProgressO, adsProgress);
    }

    public float aimYaw() {
        return aimYaw;
    }

    public float aimPitch() {
        return aimPitch;
    }

    public float bodyYaw(float partialTick) {
        return Mth.rotLerp(partialTick, bodyYawO, bodyYaw);
    }

    public boolean downed() {
        return downed;
    }

    public boolean crawl() {
        return crawl;
    }

    public int seq() {
        return seq;
    }

    public float walkPos() {
        return walkPos;
    }

    public float walkSpeed() {
        return walkSpeed;
    }

    /** Server tick: vanilla's walk recurrence over the windowed horizontal displacement. */
    void stepWalk(double x, double z) {
        int k = walkSamples % walkX.length;
        walkX[k] = x;
        walkZ[k] = z;
        int n = Math.min(walkSamples, WALK_WINDOW);
        walkSamples++;
        float d = 0.0F;
        if (n > 0) {
            int o = (walkSamples - 1 - n) % walkX.length;
            d = (float) (Math.sqrt((x - walkX[o]) * (x - walkX[o]) + (z - walkZ[o]) * (z - walkZ[o])) / n);
        }
        walkSpeed += (Math.min(d * 4.0F, 1.0F) - walkSpeed) * 0.4F;
        walkPos += walkSpeed;
        walkSentAge++;
        if (Math.abs(walkSpeed - sentWalkSpeed) > WALK_SPEED_EPSILON
                || Math.abs(walkPos - (sentWalkPos + sentWalkSpeed * walkSentAge)) > WALK_POS_EPSILON) {
            dirty |= WALK;
        }
    }

    /** Server flush carrying {@code WALK}: dead reckoning restarts from it. */
    void walkSent() {
        sentWalkPos = walkPos;
        sentWalkSpeed = walkSpeed;
        walkSentAge = 0;
    }

    /** Remote tick without a {@code WALK} update: the server's recurrence at the last synced speed. */
    void reckonWalk() {
        if (walkSynced && !walkApplied) {
            walkPos += walkSpeed;
        }
        walkApplied = false;
    }

    /** Server tick whose broadcast first carried this state. */
    public long stamp() {
        return stamp;
    }

    boolean hasTargets() {
        return posture != Posture.STAND || level != 0 || side != 0 || leanTarget != 0.0F || rest != Rest.NONE;
    }

    void applyTargets(StanceRequest r) {
        posture = r.posture();
        level = r.level();
        side = r.side();
        leanTarget = r.lean();
        rest = r.rest();
        ads = r.ads();
        aimYaw = r.aimYaw();
        aimPitch = r.aimPitch();
        dirty |= TARGETS;
    }

    void clearTargets() {
        posture = Posture.STAND;
        level = 0;
        side = 0;
        leanTarget = 0.0F;
        rest = Rest.NONE;
        dirty |= TARGETS;
    }

    void reset() {
        clearTargets();
        ads = false;
        aimYaw = 0.0F;
        aimPitch = 0.0F;
        downed = false;
        crawl = false;
        lean = leanO = 0.0F;
        height = heightO = sideAmount = sideAmountO = 0.0F;
        shown = from = Posture.STAND;
        blend = blendO = 1.0F;
        lie = lieO = 0.0F;
        rollDir = 0;
        adsProgress = adsProgressO = 0.0F;
        dirty |= ALL;
    }

    void ease(float leanRate, float adsRate, float stepRate, float sideRate) {
        Posture eff = effective();
        float leanTo = eff.lying() ? 0.0F : leanTarget;
        leanO = lean;
        lean = approach(lean, leanTo, leanRate);
        heightO = height;
        sideAmountO = sideAmount;
        blendO = blend;
        if (eff != shown) {
            if (eff == from && blend < 1.0F) {
                // Reversed mid-way: back along the same blend, no jump.
                float h = fromHeight, sd = fromSide;
                fromHeight = height;
                fromSide = sideAmount;
                height = heightO = h;
                sideAmount = sideAmountO = sd;
                blend = blendO = 1.0F - blend;
            } else {
                fromHeight = height;
                fromSide = sideAmount;
                height = heightO = effectiveLevel();
                sideAmount = sideAmountO = 0.0F;
                blend = blendO = 0.0F;
            }
            from = shown;
            shown = eff;
        }
        blend = approach(blend, 1.0F, StanceLogic.transitionRate(from, eff));
        height = approach(height, effectiveLevel(), stepRate);
        sideAmount = approach(sideAmount, effectiveSide(), sideRate);
        lieO = lie;
        lie = approach(lie, eff.lying() ? 1.0F : 0.0F, StanceLogic.LIE_RATE);
        adsProgressO = adsProgress;
        adsProgress = approach(adsProgress, ads && !downed ? 1.0F : 0.0F, adsRate);
    }

    static float approach(float from, float to, float step) {
        return from < to ? Math.min(from + step, to) : Math.max(from - step, to);
    }

    /** Owner: locks always adopted; targets only once the server has processed every request sent. */
    void mergeOwner(Update u) {
        stamp = u.stamp;
        if ((u.mask & LOCKS) != 0) {
            downed = u.downed;
            crawl = u.crawl;
        }
        if ((u.mask & TARGETS) != 0 && u.seq - seq >= 0) {
            adoptTargets(u);
        }
    }

    /** Remote tracker: held until the view tick reaches the stamp. */
    void enqueue(Update u) {
        if (pending.size() >= MAX_PENDING) {
            apply(pending.poll());
        }
        pending.add(u);
    }

    void applyDue(long viewTick) {
        Update u;
        while ((u = pending.peek()) != null && u.stamp <= viewTick) {
            apply(pending.poll());
        }
    }

    void apply(Update u) {
        stamp = u.stamp;
        bodyYaw = u.bodyYaw;
        if ((u.mask & WALK) != 0) {
            walkPos = u.walkPos;
            walkSpeed = u.walkSpeed;
            walkSynced = walkApplied = true;
        }
        if ((u.mask & LOCKS) != 0) {
            downed = u.downed;
            crawl = u.crawl;
        }
        if ((u.mask & TARGETS) != 0) {
            adoptTargets(u);
        }
    }

    private void adoptTargets(Update u) {
        if (u.rollSeq - rollSeq > 0) {
            rollDir = u.rollDir;
            rollTicks = 0;
        } else if (u.rollSeq != rollSeq) {
            rollDir = 0;
        }
        rollSeq = u.rollSeq;
        posture = u.posture;
        level = u.level;
        side = u.side;
        leanTarget = u.leanTarget;
        rest = u.rest;
        ads = u.ads;
        aimYaw = u.aimYaw;
        aimPitch = u.aimPitch;
        seq = u.seq;
    }

    void write(FriendlyByteBuf buf, int mask) {
        buf.writeByte(mask);
        if ((mask & TARGETS) != 0) {
            buf.writeEnum(posture);
            buf.writeByte(level);
            buf.writeByte(side);
            buf.writeVarInt(rollSeq);
            buf.writeByte(rollDir);
            buf.writeFloat(leanTarget);
            buf.writeEnum(rest);
            buf.writeBoolean(ads);
            buf.writeFloat(aimYaw);
            buf.writeFloat(aimPitch);
            buf.writeVarInt(seq);
        }
        if ((mask & LOCKS) != 0) {
            buf.writeBoolean(downed);
            buf.writeBoolean(crawl);
        }
        if ((mask & WALK) != 0) {
            buf.writeFloat(walkPos);
            buf.writeFloat(walkSpeed);
        }
        buf.writeFloat(bodyYaw);
        buf.writeVarLong(stamp);
    }

    /** One sync payload; fields outside {@code mask} are unset. */
    static final class Update {
        int mask;
        Posture posture;
        int level;
        int side;
        int rollSeq;
        int rollDir;
        float leanTarget;
        Rest rest;
        boolean ads;
        float aimYaw;
        float aimPitch;
        int seq;
        boolean downed;
        boolean crawl;
        float bodyYaw;
        long stamp;
        float walkPos;
        float walkSpeed;

        static Update read(FriendlyByteBuf buf) {
            Update u = new Update();
            u.mask = buf.readByte();
            if ((u.mask & TARGETS) != 0) {
                u.posture = buf.readEnum(Posture.class);
                u.level = buf.readByte();
                u.side = buf.readByte();
                u.rollSeq = buf.readVarInt();
                u.rollDir = buf.readByte();
                u.leanTarget = buf.readFloat();
                u.rest = buf.readEnum(Rest.class);
                u.ads = buf.readBoolean();
                u.aimYaw = buf.readFloat();
                u.aimPitch = buf.readFloat();
                u.seq = buf.readVarInt();
            }
            if ((u.mask & LOCKS) != 0) {
                u.downed = buf.readBoolean();
                u.crawl = buf.readBoolean();
            }
            if ((u.mask & WALK) != 0) {
                u.walkPos = buf.readFloat();
                u.walkSpeed = buf.readFloat();
            }
            u.bodyYaw = buf.readFloat();
            u.stamp = buf.readVarLong();
            return u;
        }
    }
}
