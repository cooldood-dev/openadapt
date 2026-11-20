package dev.adapt.v3.mixin;

import dev.adapt.v3.feature.module.impl.combat.BlockModule;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Render-time item-use spoof for legit autoblock; LocalPlayerBlockVisualMixin covers the methods LocalPlayer overrides.
@Mixin(LivingEntity.class)
public abstract class LivingEntityBlockVisualMixin {
    @Inject(method = "getUseItem", at = @At("HEAD"), cancellable = true)
    private void adapt$visualUseItem(CallbackInfoReturnable<ItemStack> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        InteractionHand hand = BlockModule.getVisualBlockHand(self);
        if (hand != null) {
            cir.setReturnValue(self.getItemInHand(hand));
        }
    }

    @Inject(method = "getUseItemRemainingTicks", at = @At("HEAD"), cancellable = true)
    private void adapt$visualUseRemaining(CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        InteractionHand hand = BlockModule.getVisualBlockHand(self);
        if (hand != null) {
            int i = self.getItemInHand(hand).getUseDuration(self);
            cir.setReturnValue(Math.max(1, i - BlockModule.getVisualBlockTicks()));
        }
    }

    @Inject(method = "getTicksUsingItem()I", at = @At("HEAD"), cancellable = true)
    private void adapt$visualTicksUsing(CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        InteractionHand hand = BlockModule.getVisualBlockHand(self);
        if (hand != null) {
            int i = self.getItemInHand(hand).getUseDuration(self);
            cir.setReturnValue(Math.min(i - 1, BlockModule.getVisualBlockTicks()));
        }
    }
}
