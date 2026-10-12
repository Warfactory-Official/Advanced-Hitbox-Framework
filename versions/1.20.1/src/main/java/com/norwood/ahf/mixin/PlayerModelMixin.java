package com.norwood.ahf.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.ahf.client.body.BodyRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player cubes -> jointed body ({@link BodyRenderer}); held items follow the bends. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> implements BodyRenderer.SlimArms {

    @Shadow
    @Final
    private boolean slim;

    private PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Override
    public boolean ahf$slim() {
        return slim;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b,
                               float a) {
        if (!BodyRenderer.render((PlayerModel<?>) (Object) this, slim, pose, vc, light, overlay, r, g, b, a)) {
            super.renderToBuffer(pose, vc, light, overlay, r, g, b, a);
        }
    }

    @Inject(method = "translateToHand", at = @At("HEAD"))
    private void ahf$handPre(HumanoidArm side, PoseStack stack, CallbackInfo ci) {
        BodyRenderer.handPre((PlayerModel<?>) (Object) this, slim, stack);
    }

    @Inject(method = "translateToHand", at = @At("RETURN"))
    private void ahf$handPost(HumanoidArm side, PoseStack stack, CallbackInfo ci) {
        BodyRenderer.handPost((PlayerModel<?>) (Object) this, slim, side, stack);
    }
}
