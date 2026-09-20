package com.fruitfly.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Brain-activity glow layer using the 26.1 render-state submit pipeline. */
public final class FlyGlowLayer extends RenderLayer<FlyRenderState, FlyModel> {
    public FlyGlowLayer(RenderLayerParent<FlyRenderState, FlyModel> parent) { super(parent); }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, FlyRenderState state, float yRot, float xRot) {
        if (!state.hasBrain || state.isInvisible) return;
        float activity = Mth.clamp(state.activity, 0F, 1F);
        if (activity < 0.04F) return;
        int alpha = Mth.clamp((int)((40F + 110F * activity) * (0.85F + 0.15F * Mth.sin(state.ageInTicks * 0.31F))), 32, 160);
        FlyModel model = getParentModel();
        model.setupAnim(state);
        Identifier texture = state.male ? FlyRendererTexture.MALE : FlyRendererTexture.FEMALE;
        collector.submitModel(model, state, poseStack, RenderType.entityTranslucentEmissive(texture), LightTexture.FULL_BRIGHT, 0, (alpha << 24) | 0x00FF8CCD, null);
    }
}
