package dev.adapt.v3.feature.module.impl.movement.noslow.mode;

import dev.adapt.v3.event.impl.PostMovementPacketEvent;
import dev.adapt.v3.event.impl.PreMovementPacketEvent;
import dev.adapt.v3.event.impl.SlowdownEvent;
import dev.adapt.v3.event.system.Subscribe;
import dev.adapt.v3.feature.module.impl.combat.BlockModule;
import dev.adapt.v3.feature.module.impl.movement.noslow.NoSlowModule;
import dev.adapt.v3.feature.module.mode.ModuleMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.ItemUseAnimation;

// Hypixel's Watchdog never sees the old 1.8 sword-slot trick on 26.3 (blocking is an offhand shield now), and a per-tick
// carried-slot swap only spams slot changes and gets you kicked. So this uses the release/reuse technique: tell the server
// you stopped blocking for the exact instant it reads your movement packet, then resume the block. Unlike NCP it stays live
// through legit autoblock instead of switching off during the fight, and it coordinates with it: NoSlow keeps you fast while
// the shield is up and only stands down on the frames autoblock is itself dropping the block to swing, or killaura is attacking.
public final class HypixelNoSlow extends ModuleMode<NoSlowModule> {
    public static final Minecraft mc = Minecraft.getInstance();
    private boolean releasedThisTick;

    public HypixelNoSlow(NoSlowModule noSlowModule) {
        super(noSlowModule);
    }

    @Subscribe
    public void onSlowdown(SlowdownEvent event) {
        event.cancel();
    }

    @Subscribe
    public void onPreMotion(PreMovementPacketEvent event) {
        this.releasedThisTick = false;
        if (this.shouldUnblock()) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
            this.releasedThisTick = true;
        }
    }

    @Subscribe
    public void onPostMotion(PostMovementPacketEvent event) {
        if (this.releasedThisTick && mc.player != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundUseItemPacket(mc.player.getUsedItemHand(), 0, mc.player.getYRot(), mc.player.getXRot()));
            this.releasedThisTick = false;
        }
    }

    public boolean shouldUnblock() {
        return this.isBlockingWithItem()
            && mc.gui.screen() == null
            && NcpNoSlow.isMovingUnobstructed()
            && NcpNoSlow.hasGroundBelow(0.42)
            // let legit autoblock own the release on the frames it swings, and never touch the use-item while killaura attacks
            && !BlockModule.isUnblockingForAttack()
            && !NcpNoSlow.isKillAuraAttacking();
    }

    private boolean isBlockingWithItem() {
        return mc.player != null
            && mc.getConnection() != null
            && mc.player.isUsingItem()
            && mc.player.getUseItem().getUseAnimation() == ItemUseAnimation.BLOCK;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.releasedThisTick = false;
    }

    @Override
    public Enum getMode() {
        return NoSlowModule.Mode.HYPIXEL;
    }
}
