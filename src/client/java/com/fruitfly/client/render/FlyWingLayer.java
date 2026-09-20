package com.fruitfly.client.render;

import com.fruitfly.entity.FlyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.ARGB;

/**
 * Draws the two wing quads with {@code RenderType.entityTranslucent} (alpha blended, two-sided, lit) so the membrane
 * painted at alpha ~110/255 reads as glass, while the body stays in the opaque cutout pass. While the fly is flapping
 * two extra "ghost" strokes per wing are drawn at a low tint alpha as a cheap motion blur (a 200 Hz wing beat cannot
 * be displayed at 20 ticks/s; the vanilla-bee cadence plus ghosts reads as a buzz).
 *
 * <p>Note on alpha: both entity shaders discard texels whose final alpha is below 0.1, so the ghost tint alpha (80/255)
 * times the membrane alpha (110/255) must stay above that: 0.31 * 0.43 = 0.135.
 */
public final class FlyWingLayer extends RenderLayer<FlyEntity, FlyModel> {
    private static final int GHOST_TINT = FastColor.ARGB32.color(80, 255, 255, 255);
    private static final float[] GHOST_OFFSETS = {-0.65F, 0.65F};

    public FlyWingLayer(RenderLayerParent<FlyEntity, FlyModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, FlyEntity fly, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (fly.isInvisible()) return;
        FlyModel model = getParentModel();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(fly)));
        int overlay = LivingEntityRenderer.getOverlayCoords(fly, 0F);
        poseStack.pushPose();
        model.body.translateAndRotate(poseStack);              // wings are children of body
        model.leftWing.visible = true;
        model.rightWing.visible = true;
        model.leftWing.render(poseStack, vc, packedLight, overlay);
        model.rightWing.render(poseStack, vc, packedLight, overlay);
        float blur = model.wingBlur();
        if (blur > 0.4F) {
            float l = model.leftWing.zRot, r = model.rightWing.zRot;
            for (float dz : GHOST_OFFSETS) {
                model.leftWing.zRot = l + dz * blur;
                model.rightWing.zRot = r - dz * blur;
                model.leftWing.render(poseStack, vc, packedLight, overlay, GHOST_TINT);
                model.rightWing.render(poseStack, vc, packedLight, overlay, GHOST_TINT);
            }
            model.leftWing.zRot = l;
            model.rightWing.zRot = r;
        }
        model.leftWing.visible = false;                        // keep them out of the cutout pass
        model.rightWing.visible = false;
        poseStack.popPose();
    }
}
