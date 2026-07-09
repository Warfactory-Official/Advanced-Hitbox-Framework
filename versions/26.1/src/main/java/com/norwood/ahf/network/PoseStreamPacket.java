package com.norwood.ahf.network;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.hit.HitAuthority;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.rig.RigCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public record PoseStreamPacket(HumanoidRig.LocalRig rig) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PoseStreamPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Ahf.MOD_ID, "pose_stream"));

    public static final StreamCodec<FriendlyByteBuf, PoseStreamPacket> STREAM_CODEC =
            StreamCodec.of(PoseStreamPacket::encode, PoseStreamPacket::decode);

    public static PoseStreamPacket decode(FriendlyByteBuf buf) {
        HumanoidRig.LocalRig rig = new HumanoidRig.LocalRig();
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            slot.set(rig, readObb(buf, slot.limb));
        }
        return new PoseStreamPacket(rig);
    }

    public static void encode(FriendlyByteBuf buf, PoseStreamPacket packet) {
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            writeObb(buf, slot.get(packet.rig));
        }
    }

    private static void writeObb(FriendlyByteBuf buf, Obb o) {
        writeVec(buf, o.center());
        writeVec(buf, o.axisX());
        writeVec(buf, o.axisY());
        writeVec(buf, o.axisZ());
        writeVec(buf, o.half());
    }

    private static Obb readObb(FriendlyByteBuf buf, HitboxPart limb) {
        Vec3 center = readVec(buf);
        Vec3 axisX = readVec(buf);
        Vec3 axisY = readVec(buf);
        Vec3 axisZ = readVec(buf);
        Vec3 half = readVec(buf);
        return new Obb(center, axisX, axisY, axisZ, half, limb);
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeFloat((float) v.x);
        buf.writeFloat((float) v.y);
        buf.writeFloat((float) v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void handleServer(ServerPlayer sender) {
        if (sender == null || AhfConfig.hitAuthority() != HitAuthority.CLIENT_HINT) {
            return;
        }
        RigCache.submitHint(sender.getId(), rig, sender.level().getGameTime());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
