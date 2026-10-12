package com.norwood.ahf.client.body;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Jointed player body API, render thread. Arm override = the arm's upper and lower (elbow-down) frames for the next
 * {@code PlayerModel} draws, part space (arm pivot origin, ModelPart axes, blocks) -> model space; replaces the posed
 * arm + bend. Set before the draw, {@link #clear} after.
 */
public final class AhfBody {

    public record Override(Matrix4f upper, Matrix4f lower) {
    }

    private static final Override[] ARMS = {new Override(new Matrix4f(), new Matrix4f()),
            new Override(new Matrix4f(), new Matrix4f())};
    private static final boolean[] SET = new boolean[2];
    private static Boolean bendyLib;

    private AhfBody() {
    }

    public static void overrideArm(HumanoidArm side, Matrix4fc upper, Matrix4fc lower) {
        int i = side.ordinal();
        ARMS[i].upper().set(upper);
        ARMS[i].lower().set(lower);
        SET[i] = true;
    }

    public static void clear() {
        SET[0] = false;
        SET[1] = false;
    }

    public static @Nullable Override override(HumanoidArm side) {
        return SET[side.ordinal()] ? ARMS[side.ordinal()] : null;
    }

    /** bendy-lib present => PlayerAnimator bends held items itself. */
    static boolean bendyLib() {
        if (bendyLib == null) {
            bendyLib = ModList.get().isLoaded("bendylib");
        }
        return bendyLib;
    }
}
