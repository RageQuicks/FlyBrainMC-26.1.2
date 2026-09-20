package com.fruitfly.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public final class FlyRenderState extends LivingEntityRenderState {
    public int entityId;
    public boolean male;
    public float flyScale = 1.0F;
    public float proboscis;
    public boolean flapping;
    public boolean flyingState;
    public byte groomState;
    public byte wingExtension;
    public float verticalVelocity;
    public float bodyYaw;
    public float oldBodyYaw;
    public float headYaw;
    public float headPitch;
    public float activity;
    public boolean hasBrain;
}
