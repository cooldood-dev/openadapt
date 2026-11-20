package dev.adapt.v3.feature.module.impl.visual;

import dev.adapt.v3.event.impl.PostTickEvent;
import dev.adapt.v3.event.system.Subscribe;
import dev.adapt.v3.feature.module.Module;
import dev.adapt.v3.feature.module.ModuleCategory;
import dev.adapt.v3.feature.property.impl.BooleanProperty;
import dev.adapt.v3.feature.property.impl.NumberProperty;
import dev.adapt.v3.ui.theme.Theme;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ChestEspModule extends Module {
    public static final int CHEST_COLOR = 0xF5B942;
    public static final int ENDER_CHEST_COLOR = 0xB46CFF;
    public static final int SHULKER_COLOR = 0xFF7AC6;
    public static final int BARREL_COLOR = 0xC8915A;
    public static final float OUTLINE_WIDTH = 1.5F;
    public static final float OUTLINE_ALPHA = 0.9F;
    // boxes keep full strength up to this distance, then fade towards MIN_FADE at the range limit
    public static final double FADE_START = 16.0;
    public static final float MIN_FADE = 0.25F;
    public final BooleanProperty chests = new BooleanProperty("Chests", true);
    public final BooleanProperty enderChests = new BooleanProperty("Ender chests", true);
    public final BooleanProperty shulkers = new BooleanProperty("Shulker boxes", true);
    public final BooleanProperty barrels = new BooleanProperty("Barrels", false);
    public final BooleanProperty themeColor = new BooleanProperty("Theme color", false);
    public final BooleanProperty throughWalls = new BooleanProperty("Through walls", true);
    public final NumberProperty range = new NumberProperty("Range", 64.0, 16.0, 256.0, 8.0);
    public final NumberProperty fillOpacity = new NumberProperty("Fill opacity", "%", 8.0, 0.0, 50.0, 1.0);

    public ChestEspModule() {
        super("ChestESP", "Highlights chests and other containers, even through walls.", ModuleCategory.VISUAL);
        this.addProperties(
            this.chests, this.enderChests, this.shulkers, this.barrels, this.themeColor, this.throughWalls, this.range, this.fillOpacity
        );
    }

    @Subscribe
    public void onEndTick(PostTickEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        Vec3 vec3 = mc.player.getEyePosition();
        double d0 = this.range.getValue();
        int i = Math.min(mc.options.getEffectiveRenderDistance(), (int)Math.ceil(d0 / 16.0) + 1);
        int j = mc.player.chunkPosition().x();
        int k = mc.player.chunkPosition().z();

        for (int l = j - i; l <= j + i; l++) {
            for (int i1 = k - i; i1 <= k + i; i1++) {
                if (mc.level.getChunkSource().getChunk(l, i1, ChunkStatus.FULL, false) instanceof LevelChunk levelchunk) {
                    for (BlockEntity blockentity : levelchunk.getBlockEntities().values()) {
                        int j1 = this.getColor(blockentity);
                        if (j1 != -1) {
                            AABB aabb = this.getBox(blockentity.getBlockPos(), blockentity.getBlockState());
                            if (aabb != null) {
                                double d1 = Math.sqrt(aabb.distanceToSqr(vec3));
                                if (d1 <= d0) {
                                    this.drawBox(aabb, j1, getFade(d1, d0));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // RGB of the highlight, or -1 to skip this block entity
    public int getColor(BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity) {
            return !this.chests.getValue() ? -1 : this.themeColor.getValue() ? Theme.getAccentColor() & 0xFFFFFF : CHEST_COLOR;
        } else if (blockEntity instanceof EnderChestBlockEntity) {
            return this.enderChests.getValue() ? ENDER_CHEST_COLOR : -1;
        } else if (blockEntity instanceof ShulkerBoxBlockEntity) {
            return this.shulkers.getValue() ? SHULKER_COLOR : -1;
        } else if (blockEntity instanceof BarrelBlockEntity) {
            return this.barrels.getValue() ? BARREL_COLOR : -1;
        } else {
            return -1;
        }
    }

    // a double chest is drawn once, as one box spanning both halves (from its LEFT half)
    public AABB getBox(BlockPos pos, BlockState state) {
        AABB aabb = getShapeBox(pos, state);
        if (state.hasProperty(ChestBlock.TYPE)) {
            ChestType chesttype = state.getValue(ChestBlock.TYPE);
            if (chesttype == ChestType.RIGHT) {
                return null;
            }

            if (chesttype == ChestType.LEFT) {
                BlockPos blockpos = pos.relative(ChestBlock.getConnectedDirection(state));
                aabb = aabb.minmax(getShapeBox(blockpos, mc.level.getBlockState(blockpos)));
            }
        }

        return aabb.inflate(0.002);
    }

    public static AABB getShapeBox(BlockPos pos, BlockState state) {
        VoxelShape voxelshape = state.getShape(mc.level, pos);
        return voxelshape.isEmpty() ? new AABB(pos) : voxelshape.bounds().move(pos);
    }

    public static float getFade(double distance, double range) {
        if (distance <= FADE_START || range <= FADE_START) {
            return 1.0F;
        }

        float f = (float)((distance - FADE_START) / (range - FADE_START));
        return 1.0F - (1.0F - MIN_FADE) * Math.min(1.0F, f);
    }

    public void drawBox(AABB aabb, int color, float fade) {
        int i = alpha(OUTLINE_ALPHA * fade) | color;
        int j = alpha(this.fillOpacity.getFloatValue() / 100.0F * fade) | color;

        try {
            GizmoProperties gizmoproperties = Gizmos.cuboid(aabb, j >>> 24 == 0 ? GizmoStyle.stroke(i, OUTLINE_WIDTH) : GizmoStyle.strokeAndFill(i, OUTLINE_WIDTH, j));
            if (this.throughWalls.getValue()) {
                gizmoproperties.setAlwaysOnTop();
            }
        } catch (IllegalStateException illegalstateexception) {
        }
    }

    public static int alpha(float value) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F) << 24;
    }
}
