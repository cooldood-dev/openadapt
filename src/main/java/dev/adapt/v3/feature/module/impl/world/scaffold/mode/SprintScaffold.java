package dev.adapt.v3.feature.module.impl.world.scaffold.mode;

import dev.adapt.v3.event.impl.JumpEvent;
import dev.adapt.v3.event.impl.PreMovementPacketEvent;
import dev.adapt.v3.event.impl.SprintEvent;
import dev.adapt.v3.event.system.Subscribe;
import dev.adapt.v3.feature.module.impl.world.scaffold.ScaffoldModule;
import dev.adapt.v3.feature.module.impl.world.scaffold.ScaffoldSettings;
import dev.adapt.v3.feature.module.mode.ModuleMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class SprintScaffold extends ModuleMode<ScaffoldModule> {
    public static final Minecraft MC = Minecraft.getInstance();
    public static final float VISUAL_PITCH = 80.0F;

    public SprintScaffold(ScaffoldModule scaffoldModule) {
        super(scaffoldModule);
    }

    // low priority so this runs after Movement Fix, which blocks sprint while rotated away from movement
    @Subscribe(priority = -100)
    public void onSprint(SprintEvent event) {
        if (canSprint()) {
            MC.player.setSprinting(true);
            event.setSprinting(true);
        }
    }

    @Subscribe(priority = -100)
    public void onJump(JumpEvent event) {
        if (MC.player != null && MC.player.isSprinting()) {
            event.setSprinting(true);
        }
    }

    @Subscribe(priority = -100)
    public void onPreMotion(PreMovementPacketEvent event) {
        if (MC.player != null && MC.player.isSprinting()) {
            event.setSprinting(true);
        }
    }

    public static boolean canSprint() {
        LocalPlayer player = MC.player;
        return player != null
            && player.input.getMoveVector().lengthSquared() > 0.0F
            && !player.isShiftKeyDown()
            && (player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly);
    }

    @Override
    public Enum getMode() {
        return ScaffoldSettings.Mode.SPRINT;
    }
}
