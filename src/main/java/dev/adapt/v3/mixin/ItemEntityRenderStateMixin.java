package dev.adapt.v3.mixin;

import dev.adapt.v3.feature.module.impl.visual.ItemPhysicsRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsRenderState {
    @Unique
    private boolean adapt$physics;
    @Unique
    private float adapt$pitch;
    @Unique
    private float adapt$yaw;

    @Override
    public boolean adapt$hasPhysics() {
        return this.adapt$physics;
    }

    @Override
    public float adapt$getPitch() {
        return this.adapt$pitch;
    }

    @Override
    public float adapt$getYaw() {
        return this.adapt$yaw;
    }

    @Override
    public void adapt$setPhysics(boolean physics, float pitch, float yaw) {
        this.adapt$physics = physics;
        this.adapt$pitch = pitch;
        this.adapt$yaw = yaw;
    }
}
