package com.norwood.ahf.network;

import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.HumanoidRig;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PoseStreamPacketTest {

    private static PoseStreamPacket roundTrip(HumanoidRig.LocalRig rig) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PoseStreamPacket.encode(buf, new PoseStreamPacket(rig));
        PoseStreamPacket out = PoseStreamPacket.decode(buf);
        assertEquals(0, buf.readableBytes(), "decode must consume the whole payload");
        return out;
    }

    /** Dyadic components: exact as float, so identity asserts test slot order, not rounding. */
    private static Obb obb(int seed, HitboxPart limb) {
        return new Obb(new Vec3(seed, seed + 1, seed + 2),
                new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                new Vec3(0.25 + seed * 0.03125, 0.5, 0.125), limb);
    }

    /** Float wire format pinned: a switch to double is a bandwidth change. */
    @Test
    void poseIsQuantisedToFloatOnTheWire() {
        HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            slot.set(rig, new Obb(new Vec3(0.1, 0.2, 0.3),
                    new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                    new Vec3(0.1, 0.1, 0.1), slot.limb));
        }
        Vec3 c = roundTrip(rig).rig().head.center();
        assertEquals(0.1, c.x, 1.0e-6);
        assertEquals((double) 0.1F, c.x, "the wire value is the float, not the original double");
    }

    @Test
    void aStreamedPoseKeepsItsSlotOrderAndServerAssignedLimbs() {
        HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
        int i = 0;
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            slot.set(rig, obb(i++, slot.limb));
        }
        HumanoidRig.LocalRig out = roundTrip(rig).rig();
        i = 0;
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            Obb sent = obb(i++, slot.limb);
            Obb got = slot.get(out);
            assertEquals(sent.center(), got.center(), "centre for " + slot);
            assertEquals(sent.half(), got.half(), "half-extent for " + slot);
            assertEquals(slot.limb, got.limb());
        }
    }

    /** No limb on the wire: a client cannot relabel its head box as a leg. */
    @Test
    void theLimbTagIsIgnoredOnTheWire() {
        HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            slot.set(rig, obb(0, HitboxPart.LEFT_LEG));
        }
        HumanoidRig.LocalRig out = roundTrip(rig).rig();
        assertEquals(HitboxPart.HEAD, out.head.limb());
        assertEquals(HitboxPart.TORSO, out.torso.limb());
    }

    @Test
    void theCachedAllArrayIsRebuiltAfterDecode() {
        HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            slot.set(rig, obb(1, slot.limb));
        }
        Obb[] all = roundTrip(rig).rig().all();
        assertEquals(HumanoidRig.LocalRig.SLOTS.length, all.length);
        for (Obb o : all) {
            assertNotNull(o);
        }
    }
}
