package com.norwood.ahf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.ahf.client.body.ArmorRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Armour pieces -> {@link ArmorRenderer} at the layer's draw calls: PlayerAnimator replaces
 * {@code HumanoidModel.renderToBuffer} while animating.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    private static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void ahf$begin(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float a,
                           float b, float c, float d, float e, float f, CallbackInfo ci) {
        ArmorRenderer.begin(((RenderLayer<?, ?>) (Object) this).getParentModel());
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void ahf$end(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float a,
                         float b, float c, float d, float e, float f, CallbackInfo ci) {
        ArmorRenderer.end();
    }

    @WrapOperation(method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/Model;ZFFFLnet/minecraft/resources/ResourceLocation;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void ahf$layer(Model model, PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g,
                           float b, float a, Operation<Void> original) {
        if (!ArmorRenderer.render(model, pose, vc, light, overlay, r, g, b, a)) {
            original.call(model, pose, vc, light, overlay, r, g, b, a);
        }
    }

    @WrapOperation(method = {
            "renderTrim(Lnet/minecraft/world/item/ArmorMaterial;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/Model;Z)V",
            "renderGlint(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void ahf$overlay(Model model, PoseStack pose, VertexConsumer vc, int light, int overlay, float r,
                             float g, float b, float a, Operation<Void> original) {
        if (!ArmorRenderer.render(model, pose, vc, light, overlay, r, g, b, a)) {
            original.call(model, pose, vc, light, overlay, r, g, b, a);
        }
    }
}
