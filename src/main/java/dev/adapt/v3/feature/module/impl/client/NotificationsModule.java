package dev.adapt.v3.feature.module.impl.client;

import dev.adapt.v3.Adapt;
import dev.adapt.v3.feature.module.HiddenModule;
import dev.adapt.v3.feature.module.Module;
import dev.adapt.v3.feature.module.ModuleCategory;
import dev.adapt.v3.feature.property.impl.BooleanProperty;
import dev.adapt.v3.feature.property.impl.NumberProperty;

public final class NotificationsModule extends Module implements HiddenModule {
    public final BooleanProperty moduleToggles = new BooleanProperty("Module Toggles", true);
    public final NumberProperty size = new NumberProperty("Size", "%", 90.0, 50.0, 150.0, 5.0);

    public NotificationsModule() {
        super("Notifications", "Spring-animated toasts with live progress bars.", ModuleCategory.UTILITY);
        this.addProperties(this.moduleToggles, this.size);
    }

    public static float getScale() {
        Adapt adapt = Adapt.getInstance();
        NotificationsModule module = adapt == null ? null : adapt.getModuleManager().getByClass(NotificationsModule.class);
        return module == null ? 0.9F : module.size.getFloatValue() / 100.0F;
    }
}
