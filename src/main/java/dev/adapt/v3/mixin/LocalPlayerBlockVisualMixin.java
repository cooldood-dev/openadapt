package dev.adapt.v3.mixin;

import dev.adapt.v3.feature.module.impl.combat.BlockModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerBlockVisualMixin {
    @Inject(method = "isUsingItem", at = @At("HEAD"), cancellable = true)
    private void adapt$visualUsingItem(CallbackInfoReturnable<Boolean> cir) {
        if (BlockModule.getVisualBlockHand((LocalPlayer)(Object)this) != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getUsedItemHand", at = @At("HEAD"), cancellable = true)
    private void adapt$visualUsedHand(CallbackInfoReturnable<InteractionHand> cir) {
        InteractionHand hand = BlockModule.getVisualBlockHand((LocalPlayer)(Object)this);
        if (hand != null) {
            cir.setReturnValue(hand);
        }
    }
}
