package dev.adapt.v3.feature.module.impl.visual;

import dev.adapt.v3.Adapt;
import dev.adapt.v3.feature.module.Module;
import dev.adapt.v3.feature.module.ModuleCategory;
import dev.adapt.v3.feature.property.impl.BooleanProperty;
import java.util.Set;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public final class NoSwingSoundModule extends Module {
    // 1.8.9 had none of these attack sounds; hits only played the target's hurt sound
    public static final Set<Identifier> MODERN_ATTACK_SOUNDS = Set.of(
        SoundEvents.PLAYER_ATTACK_SWEEP.location(),
        SoundEvents.PLAYER_ATTACK_STRONG.location(),
        SoundEvents.PLAYER_ATTACK_CRIT.location(),
        SoundEvents.PLAYER_ATTACK_KNOCKBACK.location(),
        SoundEvents.PLAYER_ATTACK_WEAK.location(),
        SoundEvents.PLAYER_ATTACK_NODAMAGE.location()
    );
    public final BooleanProperty oldHitSounds = new BooleanProperty("1.8 hit sounds", true);

    public NoSwingSoundModule() {
        super("No Swing Sound", "Removes the sweep sound, or every modern attack sound for 1.8.9-style hits.", ModuleCategory.UTILITY);
        this.addProperties(this.oldHitSounds);
    }

    public static boolean shouldCancelSound(SoundInstance soundInstance) {
        Adapt adapt = Adapt.getInstance();
        if (adapt != null && soundInstance != null) {
            NoSwingSoundModule noswingsoundmodule = adapt.getModuleManager().getByClass(NoSwingSoundModule.class);
            if (noswingsoundmodule == null || !noswingsoundmodule.isEnabled()) {
                return false;
            }

            Identifier identifier = soundInstance.getIdentifier();
            return noswingsoundmodule.oldHitSounds.getValue()
                ? MODERN_ATTACK_SOUNDS.contains(identifier)
                : SoundEvents.PLAYER_ATTACK_SWEEP.location().equals(identifier);
        } else {
            return false;
        }
    }
}
