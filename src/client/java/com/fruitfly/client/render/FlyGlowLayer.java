package com.fruitfly.client.render;

import com.fruitfly.entity.FlyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Subtle "brain glow": re-draws the head (with its eyes, antennae and proboscis) full-bright through
 * {@code RenderType.entityTranslucentEmissive} tinted pink, with an alpha that follows the synched brain-activity
 * summary ({@link FlyEntity#getActivity()}, spikes per tick normalised). Flies without a brain do not glow.
 * The overlay shares the head's exact geometry, so with the LEQUAL depth test it lands on top without z-fighting.
 */
public final class FlyGlowLayer extends RenderLayer<FlyEntity, FlyModel> {
    public FlyGlowLayer(RenderLayerParent<FlyEntity, FlyModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, FlyEntity fly, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!fly.hasBrain() || fly.isInvisible()) return;
        float activity = Mth.clamp(fly.getActivity(), 0F, 1F);
        if (activity < 0.04F) return;
        // gentle 1 Hz shimmer so the glow reads as "alive" even at a steady rate; alpha floor keeps texels above the
        // shaders' 0.1 discard threshold
        float shimmer = 0.85F + 0.15F * Mth.sin(ageInTicks * 0.31F);
        int alpha = Mth.clamp((int) ((40F + 110F * activity) * shimmer), 32, 160);
        int tint = ARGB.color(alpha, 255, 140, 205);
        FlyModel model = getParentModel();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(getTextureLocation(fly)));
        poseStack.pushPose();
        model.body.translateAndRotate(poseStack);
        model.head.render(poseStack, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
        poseStack.popPose();
    }
}
