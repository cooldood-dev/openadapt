package dev.adapt.v3.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.adapt.v3.feature.module.impl.visual.ItemPhysicsModule;
import dev.adapt.v3.feature.module.impl.visual.ItemPhysicsRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void adapt$extractItemPhysics(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        ItemPhysicsModule module = ItemPhysicsModule.getInstance();
        if (module != null) {
            module.extract(entity, state);
        } else {
            ((ItemPhysicsRenderState)state).adapt$setPhysics(false, 0.0F, 0.0F);
        }
    }

    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V")
    )
    private void adapt$itemPhysicsTranslate(
        PoseStack pose, float x, float y, float z, ItemEntityRenderState state, PoseStack poseArg, SubmitNodeCollector collector, CameraRenderState camera
    ) {
        if (((ItemPhysicsRenderState)state).adapt$hasPhysics()) {
            ItemPhysicsModule.applyTransform(pose, state);
        } else {
            pose.translate(x, y, z);
        }
    }

    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lcom/mojang/math/Axis;F)V")
    )
    private void adapt$itemPhysicsRotate(
        PoseStack pose, Axis axis, float angle, ItemEntityRenderState state, PoseStack poseArg, SubmitNodeCollector collector, CameraRenderState camera
    ) {
        if (!((ItemPhysicsRenderState)state).adapt$hasPhysics()) {
            pose.rotate(axis, angle);
        }
    }
}
