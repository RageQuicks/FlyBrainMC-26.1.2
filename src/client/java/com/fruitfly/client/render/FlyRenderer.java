package com.fruitfly.client.render;

import com.fruitfly.FruitFlyMod;
import com.fruitfly.client.FruitFlyClient;
import com.fruitfly.entity.FlyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renders {@link FlyEntity} with {@link FlyModel}. The model is built at bee scale, so it is shrunk by
 * {@code 0.6 * flyScale} here (fly scale 1.0 = the 0.5 x 0.3 block hitbox). Body opaque via the model's default
 * {@code entityCutoutNoCull}; wings translucent via {@link FlyWingLayer}; activity glow via {@link FlyGlowLayer}.
 */
public class FlyRenderer extends MobRenderer<FlyEntity, FlyModel> {
    private static final Identifier TEXTURE_MALE = FruitFlyMod.id("textures/entity/fruit_fly.png");
    private static final Identifier TEXTURE_FEMALE = FruitFlyMod.id("textures/entity/fruit_fly_female.png");
    private static final float BASE_SCALE = 0.6F;
    private static final float SHADOW_RADIUS = 0.15F;

    public FlyRenderer(EntityRendererProvider.Context context) {
        super(context, new FlyModel(context.bakeLayer(FruitFlyClient.FLY_LAYER)), SHADOW_RADIUS);
        addLayer(new FlyWingLayer(this));
        addLayer(new FlyGlowLayer(this));
    }

    @Override
    protected void scale(FlyEntity fly, PoseStack poseStack, float partialTick) {
        float s = BASE_SCALE * fly.getFlyScale();
        poseStack.scale(s, s, s);
    }

    @Override
    protected float getShadowRadius(FlyEntity fly) {
        return SHADOW_RADIUS * fly.getFlyScale();
    }

    @Override
    public ResourceLocation getTextureLocation(FlyEntity fly) {
        return fly.isMale() ? TEXTURE_MALE : TEXTURE_FEMALE;
    }
}
