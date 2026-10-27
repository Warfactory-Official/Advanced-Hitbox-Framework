package com.norwood.ahf.gametest;

import com.norwood.ahf.Ahf;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.rig.HumanoidRig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3f;

@GameTestHolder(Ahf.MOD_ID)
@PrefixGameTestTemplate(false)
public class HandTargetsGameTest {

    private static final String TEMPLATE = "empty";
    /** Right wrist target, model space blocks (+Y down, -Z forward): ahead of and below the shoulder, in reach. */
    private static final Vector3f TARGET = new Vector3f(-0.3125F, 0.4F, -0.4F);
    private static volatile FakePlayer aimed;
    private static boolean registered;

    /** {@link AhfHooks#addHandTargets}: the right forearm's wrist end lands on the target. */
    @GameTest(templateNamespace = Ahf.MOD_ID, template = TEMPLATE)
    public void handTargetPlacesTheWrist(GameTestHelper helper) {
        synchronized (HandTargetsGameTest.class) {
            if (!registered) {
                registered = true;
                AhfHooks.addHandTargets((e, frame, tick, right, left) -> {
                    if (e != aimed) {
                        return 0;
                    }
                    right.set(TARGET);
                    return 1 << HumanoidArm.RIGHT.ordinal();
                });
            }
        }
        FakePlayer p = Bodies.player(helper);
        aimed = p;
        try {
            Obb fore = HumanoidRig.compute(p).rightForearm;
            double pad = AhfConfig.limbBoxPadding();
            Vec3 axis = fore.axisY().scale(fore.half().y - pad);
            // Model blocks -> rig local (HumanoidRig.toLocalPoint): 0.9375 (-x, 1.501 - y, -z).
            Vec3 want = new Vec3(-TARGET.x, 1.501 - TARGET.y, -TARGET.z).scale(0.9375);
            double d = Math.min(fore.center().add(axis).distanceTo(want), fore.center().subtract(axis).distanceTo(want));
            helper.assertTrue(d < 0.02, "wrist " + d + " from the target " + want + " (forearm " + fore.center() + ")");
        } finally {
            aimed = null;
        }
        helper.succeed();
    }
}
