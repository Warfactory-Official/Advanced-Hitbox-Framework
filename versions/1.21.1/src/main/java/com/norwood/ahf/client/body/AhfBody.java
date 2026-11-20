package com.norwood.ahf.client.body;

import java.util.Arrays;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Jointed player body API, render thread. Limb override = the limb's upper and lower (elbow / knee down) frames for
 * the next {@code PlayerModel} draws, part space (limb pivot origin, ModelPart axes, blocks) -> model space; replaces
 * the posed limb + bend. Set before the draw, {@link #clear} after.
 */
public final class AhfBody {

    public record Override(Matrix4f upper, Matrix4f lower) {
    }

    /** Right arm, left arm, right leg, left leg. */
    private static final Override[] LIMBS = {new Override(new Matrix4f(), new Matrix4f()),
            new Override(new Matrix4f(), new Matrix4f()), new Override(new Matrix4f(), new Matrix4f()),
            new Override(new Matrix4f(), new Matrix4f())};
    private static final boolean[] SET = new boolean[4];
    private static Boolean bendyLib;

    private AhfBody() {
    }

    public static void overrideArm(HumanoidArm side, Matrix4fc upper, Matrix4fc lower) {
        set(side.ordinal(), upper, lower);
    }

    /** Frames may scale along the leg (stretched reach). */
    public static void overrideLeg(HumanoidArm side, Matrix4fc upper, Matrix4fc lower) {
        set(2 + side.ordinal(), upper, lower);
    }

    private static void set(int i, Matrix4fc upper, Matrix4fc lower) {
        LIMBS[i].upper().set(upper);
        LIMBS[i].lower().set(lower);
        SET[i] = true;
    }

    public static void clear() {
        Arrays.fill(SET, false);
    }

    public static @Nullable Override override(HumanoidArm side) {
        return SET[side.ordinal()] ? LIMBS[side.ordinal()] : null;
    }

    public static @Nullable Override legOverride(HumanoidArm side) {
        int i = 2 + side.ordinal();
        return SET[i] ? LIMBS[i] : null;
    }

    /** bendy-lib present => PlayerAnimator bends held items itself. */
    static boolean bendyLib() {
        if (bendyLib == null) {
            bendyLib = ModList.get().isLoaded("bendylib");
        }
        return bendyLib;
    }
}
