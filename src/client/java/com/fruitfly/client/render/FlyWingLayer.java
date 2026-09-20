package com.fruitfly.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;

/** Translucent wing layer using the 26.1 submit/render-state pipeline. */
public final class FlyWingLayer extends RenderLayer<FlyRenderState, FlyModel> {
    private static final float[] GHOST_OFFSETS = {-0.65F, 0.65F};

    public FlyWingLayer(RenderLayerParent<FlyRenderState, FlyModel> parent) { super(parent); }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, FlyRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        FlyModel model = getParentModel();
        Identifier texture = state.male ? FlyRendererTexture.MALE : FlyRendererTexture.FEMALE;
        model.leftWing.visible = true;
        model.rightWing.visible = true;
        model.setupAnim(state);
        collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(texture), packedLight, 0, 0xFFFFFFFF, null, state.outlineColor, null);
        float blur = model.wingBlur();
        if (blur > 0.4F) {
            float l = model.leftWing.zRot, r = model.rightWing.zRot;
            for (float dz : GHOST_OFFSETS) {
                model.leftWing.zRot = l + dz * blur;
                model.rightWing.zRot = r - dz * blur;
                collector.submitModel(model, state, poseStack, RenderType.entityTranslucent(texture), packedLight, 0, 0x50FFFFFF, null, state.outlineColor, null);
            }
            model.leftWing.zRot = l;
            model.rightWing.zRot = r;
        }
        model.leftWing.visible = false;
        model.rightWing.visible = false;
    }
}
