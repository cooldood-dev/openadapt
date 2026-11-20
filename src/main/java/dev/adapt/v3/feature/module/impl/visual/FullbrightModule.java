package dev.adapt.v3.feature.module.impl.visual;

import dev.adapt.v3.Adapt;
import dev.adapt.v3.feature.module.Module;
import dev.adapt.v3.feature.module.ModuleCategory;
import dev.adapt.v3.feature.property.impl.NumberProperty;
import dev.adapt.v3.util.animation.Spring;

public final class FullbrightModule extends Module {
    public final NumberProperty strength = new NumberProperty("Strength", "%", 100.0, 10.0, 100.0, 5.0);
    public final Spring brightnessSpring = Spring.gentle(0.0F);

    public FullbrightModule() {
        super("Fullbright", "Lights up caves and nights as if everything were in full daylight.", ModuleCategory.VISUAL);
        this.addProperties(this.strength);
    }

    public static FullbrightModule getInstance() {
        return Adapt.getInstance() == null ? null : Adapt.getInstance().getModuleManager().getByClass(FullbrightModule.class);
    }

    public float getBrightnessFactor() {
        this.brightnessSpring.target(this.isEnabled() ? this.strength.getFloatValue() / 100.0F : 0.0F);
        return Math.max(0.0F, this.brightnessSpring.update());
    }
}
