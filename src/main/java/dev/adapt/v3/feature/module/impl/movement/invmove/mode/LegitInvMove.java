package dev.adapt.v3.feature.module.impl.movement.invmove.mode;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.adapt.v3.Adapt;
import dev.adapt.v3.event.impl.PreTickEvent;
import dev.adapt.v3.feature.module.impl.utility.InvManagerModule;
import dev.adapt.v3.event.system.Subscribe;
import dev.adapt.v3.feature.module.impl.movement.invmove.InvMoveModule;
import dev.adapt.v3.feature.module.impl.utility.SprintModule;
import dev.adapt.v3.feature.module.mode.ModuleMode;
import dev.adapt.v3.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

// openadjust's Legit Inv Move kept your inventory open client-side but closed server-side, reopening it with the
// 1.8 "open inventory" status packet whenever you clicked a slot. 26.3 dropped that packet, so the reopen trick is
// gone: this closes the player inventory server-side while you move, so the server never sees inventory-open
// movement. Scoped to your own inventory, and slot clicks while it is closed server-side will not register.
public final class LegitInvMove extends ModuleMode<InvMoveModule> {
    public static final Minecraft mc = Minecraft.getInstance();
    private boolean closedServerSide;

    public LegitInvMove(InvMoveModule invMoveModule) {
        super(invMoveModule);
    }

    @Subscribe
    public void onPreTick(PreTickEvent event) {
        if (mc.player == null) {
            return;
        }

        boolean ownInventory = mc.gui.screen() instanceof InventoryScreen;
        if (!VanillaInvMove.isAllowedScreen(mc.gui.screen()) || !ownInventory) {
            this.restore();
            return;
        }

        boolean moving = false;
        for (KeyMapping keymapping : new KeyMapping[]{
            mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight, mc.options.keyJump, mc.options.keySprint
        }) {
            Key key = ((KeyMappingAccessor)keymapping).adapt$getKey();
            if (!keymapping.isUnbound() && key.getType() != Type.MOUSE) {
                boolean down = keymapping == mc.options.keySprint && SprintModule.isSprintEnabled() || InputConstants.isKeyDown(key.getValue());
                keymapping.setDown(down);
                if (down && keymapping != mc.options.keySprint) {
                    moving = true;
                }
            }
        }

        // InvManager runs its own silent open/close on this same container; closing underneath it desyncs the server and kicks
        if (moving && !this.closedServerSide && mc.getConnection() != null && !isInvManagerBusy()) {
            mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            this.closedServerSide = true;
        }
    }

    private static boolean isInvManagerBusy() {
        Adapt adapt = Adapt.getInstance();
        InvManagerModule invmanager = adapt == null ? null : adapt.getModuleManager().getByClass(InvManagerModule.class);
        return invmanager != null && invmanager.silentInventoryOpen;
    }

    private void restore() {
        this.closedServerSide = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.restore();
    }

    @Override
    public Enum getMode() {
        return InvMoveModule.Mode.LEGIT;
    }
}
