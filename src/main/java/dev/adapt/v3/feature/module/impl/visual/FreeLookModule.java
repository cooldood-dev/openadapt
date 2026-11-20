package dev.adapt.v3.feature.module.impl.visual;

import com.mojang.blaze3d.platform.InputConstants;
import dev.adapt.v3.Adapt;
import dev.adapt.v3.binding.Bind;
import dev.adapt.v3.binding.type.InputType;
import dev.adapt.v3.event.impl.MouseUpdateEvent;
import dev.adapt.v3.event.impl.PostTickEvent;
import dev.adapt.v3.event.system.Subscribe;
import dev.adapt.v3.feature.helper.rotation.RotationHelper;
import dev.adapt.v3.feature.module.Module;
import dev.adapt.v3.feature.module.ModuleCategory;
import dev.adapt.v3.feature.property.impl.BooleanProperty;
import dev.adapt.v3.util.MouseUtility;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

public final class FreeLookModule extends Module {
    public final BooleanProperty holdKey = new BooleanProperty("Hold key", true);
    public final BooleanProperty thirdPerson = new BooleanProperty("Third person", true);
    public float cameraYaw;
    public float cameraPitch;
    public CameraType previousCameraType;

    public FreeLookModule() {
        super("FreeLook", "Look around freely without turning your player.", ModuleCategory.VISUAL);
        this.addProperties(this.holdKey, this.thirdPerson);
    }

    public static FreeLookModule getInstance() {
        return Adapt.getInstance() == null ? null : Adapt.getInstance().getModuleManager().getByClass(FreeLookModule.class);
    }

    public static boolean isLooking() {
        FreeLookModule freelookmodule = getInstance();
        return freelookmodule != null && freelookmodule.isEnabled() && mc.player != null;
    }

    @Override
    public void onBind() {
        if (this.holdKey.getValue()) {
            this.setEnabled(true);
        } else {
            this.toggle();
        }
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            return;
        }

        this.cameraYaw = RotationHelper.getState().getCameraYaw(mc.player.getYRot());
        this.cameraPitch = RotationHelper.getState().getCameraPitch(mc.player.getXRot());
        this.previousCameraType = null;
        if (this.thirdPerson.getValue() && mc.options.getCameraType().isFirstPerson()) {
            this.previousCameraType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @Override
    public void onDisable() {
        if (this.previousCameraType != null) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(this.previousCameraType);
            }

            this.previousCameraType = null;
        }
    }

    @Subscribe
    public void onEndTick(PostTickEvent event) {
        if (mc.player == null) {
            this.setEnabled(false);
        } else if (this.holdKey.getValue()) {
            Bind bind = Adapt.getInstance().getBindingManager().getRegistry().getBind(this).orElse(null);
            if (bind != null && !isDown(bind)) {
                this.setEnabled(false);
            }
        }
    }

    public static boolean isDown(Bind bind) {
        return bind.type() == InputType.MOUSE ? MouseUtility.isButtonDown(bind.code()) : InputConstants.isKeyDown(bind.code());
    }

    // runs before RotationState and RotationUpdater: the mouse moves only the free camera, never the player
    @Subscribe(priority = 100)
    public void onMouseUpdate(MouseUpdateEvent event) {
        if (mc.player != null && !event.isUnlockedCursor()) {
            double d0 = event.getSensitivityMultiplier();
            float f = mc.options.invertMouseX().get() ? -1.0F : 1.0F;
            float f1 = mc.options.invertMouseY().get() ? -1.0F : 1.0F;
            this.cameraYaw = this.cameraYaw + (float)(event.getDeltaX() * d0) * f * 0.15F;
            this.cameraPitch = Mth.clamp(this.cameraPitch + (float)(event.getDeltaY() * d0) * f1 * 0.15F, -90.0F, 90.0F);
            event.setDeltaX(0.0);
            event.setDeltaY(0.0);
        }
    }
}
