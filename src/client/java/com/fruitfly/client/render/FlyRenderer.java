package com.fruitfly.client.render;

import com.fruitfly.FruitFlyMod;
import com.fruitfly.client.FruitFlyClient;
import com.fruitfly.entity.FlyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Renders a fruit fly from an extracted render state. */
public class FlyRenderer extends MobRenderer<FlyEntity, FlyRenderState, FlyModel> {
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
    public FlyRenderState createRenderState() {
        return new FlyRenderState();
    }

    @Override
    public void extractRenderState(FlyEntity fly, FlyRenderState state, float partialTick) {
        super.extractRenderState(fly, state, partialTick);
        state.entityId = fly.getId();
        state.male = fly.isMale();
        state.flyScale = fly.getFlyScale();
        state.proboscis = fly.getProboscis();
        state.flapping = fly.isFlapping();
        state.flyingState = fly.isFlyingState();
        state.groomState = fly.getGroomState();
        state.wingExtension = fly.getWingExtension();
        state.verticalVelocity = (float) fly.getDeltaMovement().y;
        state.bodyYaw = fly.yBodyRot;
        state.oldBodyYaw = fly.yBodyRotO;
        state.headYaw = fly.getYRot();
        state.headPitch = fly.getXRot();
        state.activity = fly.getActivity();
        state.hasBrain = fly.hasBrain();
        state.flyColor = fly.getFlyColor();
        state.flyName = fly.flyName();
    }

    @Override
    protected void scale(FlyRenderState state, PoseStack poseStack) {
        float s = BASE_SCALE * state.flyScale;
        poseStack.scale(s, s, s);
    }

    @Override
    protected float getShadowRadius(FlyRenderState state) {
        return SHADOW_RADIUS * state.flyScale;
    }

    @Override
    public Identifier getTextureLocation(FlyRenderState state) {
        return state.male ? TEXTURE_MALE : TEXTURE_FEMALE;
    }
}
