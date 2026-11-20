package dev.adapt.v3.feature.module.impl.visual;

// Implemented on ItemEntityRenderState by ItemEntityRenderStateMixin.
public interface ItemPhysicsRenderState {
    boolean adapt$hasPhysics();

    float adapt$getPitch();

    float adapt$getYaw();

    void adapt$setPhysics(boolean physics, float pitch, float yaw);
}
