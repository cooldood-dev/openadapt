package dev.adapt.v3.feature.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.adapt.v3.Adapt;
import dev.adapt.v3.feature.module.Module;
import dev.adapt.v3.feature.module.ModuleCategory;
import dev.adapt.v3.feature.property.impl.BooleanProperty;
import dev.adapt.v3.feature.property.impl.NumberProperty;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ItemPhysicsModule extends Module {
    public static final float HALF_PI = Mth.PI / 2.0F;
    // same threshold vanilla uses to tell flat (sprite) item models from 3D ones
    public static final float FLAT_DEPTH = 0.0625F;
    public static final float GROUND_GAP = 0.01F;
    public static final float BUNDLE_SPREAD = 0.15F;
    public static final float MAX_SPIN_PER_TICK = 0.5F;
    public final NumberProperty spinSpeed = new NumberProperty("Spin Speed", "x", 1.0, 0.0, 3.0, 0.1);
    public final BooleanProperty randomYaw = new BooleanProperty("Random Yaw", true);
    // per item: {tumble angle (rad), ageInTicks at last update}; weak keys so despawned items drop out
    public final Map<ItemEntity, float[]> tumble = new WeakHashMap<>();

    public ItemPhysicsModule() {
        super("Item Physics", "Dropped items tumble through the air and land flat on the ground.", ModuleCategory.VISUAL);
        this.addProperties(this.spinSpeed, this.randomYaw);
    }

    public static ItemPhysicsModule getInstance() {
        return Adapt.getInstance() == null ? null : Adapt.getInstance().getModuleManager().getByClass(ItemPhysicsModule.class);
    }

    @Override
    public void onDisable() {
        this.tumble.clear();
    }

    public void extract(ItemEntity entity, ItemEntityRenderState state) {
        ItemPhysicsRenderState physics = (ItemPhysicsRenderState)state;
        if (!this.isEnabled() || state.item.isEmpty()) {
            physics.adapt$setPhysics(false, 0.0F, 0.0F);
            return;
        }

        boolean flat = state.item.getModelBoundingBox().getZsize() <= FLAT_DEPTH;
        // flat items rest on their back (+Z up, so stacked copies pile upward); 3D items rest on any face
        float rest = flat ? -HALF_PI : 0.0F;
        float period = flat ? Mth.PI : HALF_PI;
        float[] angle = this.tumble.computeIfAbsent(entity, key -> new float[]{0.0F, state.ageInTicks});
        float dt = Mth.clamp(state.ageInTicks - angle[1], 0.0F, 3.0F);
        angle[1] = state.ageInTicks;
        if (entity.onGround()) {
            float target = Math.round(angle[0] / period) * period;
            angle[0] += (target - angle[0]) * (1.0F - (float)Math.exp(-dt * 0.6F));
        } else {
            float speed = (float)entity.getDeltaMovement().length();
            angle[0] += Math.min(speed * 4.0F, MAX_SPIN_PER_TICK) * dt * this.spinSpeed.getFloatValue();
        }

        angle[0] %= Mth.TWO_PI;
        physics.adapt$setPhysics(true, rest + angle[0], this.randomYaw.getValue() ? state.bobOffset : 0.0F);
    }

    // replaces vanilla's hover/bob translate and spin rotate in ItemEntityRenderer.submit
    public static void applyTransform(PoseStack pose, ItemEntityRenderState state) {
        ItemPhysicsRenderState physics = (ItemPhysicsRenderState)state;
        AABB box = state.item.getModelBoundingBox();
        Vec3 center = box.getCenter();
        float pitch = physics.adapt$getPitch();
        float cos = Math.abs(Mth.cos(pitch));
        float sin = Math.abs(Mth.sin(pitch));
        // height of the pitched model box's lowest corner below its centre (yaw spins about Y, so it can't change this)
        float lift = (float)box.getYsize() / 2.0F * cos + (float)box.getZsize() / 2.0F * sin + GROUND_GAP;
        if (state.count > 1 && box.getZsize() > FLAT_DEPTH) {
            lift += BUNDLE_SPREAD * (cos + sin);
        }

        pose.translate(0.0F, lift, 0.0F);
        pose.rotate(Axis.YP, physics.adapt$getYaw());
        pose.rotate(Axis.XP, pitch);
        pose.translate((float)-center.x, (float)-center.y, (float)-center.z);
    }
}
