package dev.adapt.v3.feature.module.impl.combat;

import dev.adapt.v3.Adapt;
import dev.adapt.v3.event.impl.PostTickEvent;
import dev.adapt.v3.event.impl.PreTickEvent;
import dev.adapt.v3.event.system.Subscribe;
import dev.adapt.v3.feature.helper.rotation.RotationHelper;
import dev.adapt.v3.feature.helper.rotation.RotationProperties;
import dev.adapt.v3.feature.helper.rotation.model.impl.LinearRotationModel;
import dev.adapt.v3.feature.helper.target.TargetHelper;
import dev.adapt.v3.feature.helper.target.TargetProperties;
import dev.adapt.v3.feature.helper.target.impl.PlayerTarget;
import dev.adapt.v3.feature.module.Module;
import dev.adapt.v3.feature.module.ModuleCategory;
import dev.adapt.v3.feature.module.impl.combat.killaura.KillAuraModule;
import dev.adapt.v3.feature.property.impl.BooleanProperty;
import dev.adapt.v3.feature.property.impl.NumberProperty;
import dev.adapt.v3.util.player.PlayerUtility;
import dev.adapt.v3.util.player.RaytraceUtility;
import dev.adapt.v3.util.player.RotationUtility;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

// Breaks the nearest bed like a real player, with a free camera. It tracks known beds (BedESP-style, from loaded block
// entities) and tunnels straight to the bed, breaking whatever defence is in the path with the best tool for each block
// (speed-based: shears for wool, axe for wood, pickaxe for stone, bare hand otherwise). It only digs a block its real
// server rotation is actually looking at, aborts the instant the bed leaves range / line advances off it (you ran off),
// and yields to combat - whenever KillAura is on a target or an enemy comes within range it stops and lets KillAura fight.
public final class BedBreakerModule extends Module {
    public static final int BED_COLOR = 0xFF5050;
    public final RotationProperties rotation = new RotationProperties(this, new LinearRotationModel(90.0));
    public final NumberProperty breakRange = new NumberProperty("Break range", "m", 6.0, 2.0, 16.0, 0.5);
    public final NumberProperty combatRange = new NumberProperty("Combat range", "m", 6.0, 2.0, 16.0, 0.5);
    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final NumberProperty espRange = (NumberProperty)new NumberProperty("ESP range", "m", 64.0, 16.0, 256.0, 8.0).hideIf(() -> !this.render.getValue());
    public final TargetProperties targetSettings;
    private List<BlockPos> knownBeds = List.of();
    private BlockPos destroyPos;

    public BedBreakerModule() {
        super("Bed Breaker", "Tunnels to the nearest bed through its defence with optimal tools, aborting for combat.", ModuleCategory.COMBAT);
        this.addProperties(this.breakRange, this.combatRange, this.render, this.espRange);
        // Players on, no local-player option, Hostile on, Passive off, Teammates off (never abort for a teammate walking up)
        this.targetSettings = new TargetProperties(this, true, false, false, true, false, false);
    }

    @Override
    public void onDisable() {
        this.abort();
        this.knownBeds = List.of();
    }

    @Subscribe(priority = 1)
    public void onPreTick(PreTickEvent event) {
        if (!this.isEnabled() || mc.player == null || mc.level == null) {
            this.knownBeds = List.of();
            this.abort();
            return;
        }

        // refresh the tracked beds every tick (cheap: only loaded block entities), so ESP and targeting share one scan
        this.knownBeds = this.scanBeds();
        if (mc.gameMode == null || mc.gui.screen() != null) {
            this.abort();
            return;
        }

        // combat takes priority: let KillAura run, and bail the moment an enemy is close enough to fight
        if (this.shouldYieldToCombat()) {
            this.abort();
            return;
        }

        BlockPos blockpos = this.nearestBed();
        if (blockpos == null) {
            this.abort();
            return;
        }

        // aim the server rotation at the nearest point of the bed (camera stays free), then dig whatever that line hits
        Vec3 vec3 = mc.player.getEyePosition();
        Vec3 vec31 = closestPoint(blockpos, vec3);
        Vec2 vec2 = RotationUtility.fixRotation(RotationUtility.getRotationsBetween(vec3, vec31));
        RotationHelper.getUpdater().setRotation(vec2, this.rotation.createModel());
        HitResult hitresult = RaytraceUtility.raytraceBlockFromVec(
            mc.player.blockInteractionRange(), false, mc.player.getYRot(), mc.player.getXRot(), vec3
        );
        if (hitresult instanceof BlockHitResult blockhitresult && blockhitresult.getType() == HitResult.Type.BLOCK) {
            BlockPos blockpos1 = blockhitresult.getBlockPos();
            BlockState blockstate = mc.level.getBlockState(blockpos1);
            // nothing to dig (air) or unbreakable (bedrock etc.) - either still mid-turn or a dead end
            if (blockstate.isAir() || blockstate.getDestroySpeed(mc.level, blockpos1) < 0.0F) {
                this.abort();
                return;
            }

            this.selectOptimalTool(blockstate);
            boolean flag = mc.gameMode.continueDestroyBlock(blockpos1, blockhitresult.getDirection());
            this.destroyPos = blockpos1;
            if (flag) {
                mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
            }
        } else {
            // not looking at anything in reach yet (mid-turn, or the bed is too far to dig - walk closer)
            this.abort();
        }
    }

    @Subscribe
    public void onEndTick(PostTickEvent event) {
        if (this.render.getValue() && this.isEnabled() && mc.player != null && mc.level != null) {
            int i = alpha(0.9F) | BED_COLOR;

            for (BlockPos blockpos : this.knownBeds) {
                BlockState blockstate = mc.level.getBlockState(blockpos);
                if (blockstate.getBlock() instanceof BedBlock) {
                    try {
                        Gizmos.cuboid(shapeBox(blockpos, blockstate), GizmoStyle.stroke(i, 1.5F)).setAlwaysOnTop();
                    } catch (IllegalStateException illegalstateexception) {
                    }
                }
            }
        }
    }

    // Releases the current dig (ABORT_DESTROY_BLOCK), exactly like letting go of left-click.
    private void abort() {
        if (this.destroyPos != null) {
            if (mc.gameMode != null) {
                mc.gameMode.stopDestroyBlock();
            }

            this.destroyPos = null;
        }
    }

    // Switches the hotbar to whatever mines this block fastest (shears/axe/pickaxe...), falling back to a bare hand when no
    // tool helps. Setting the real slot keeps the client's dig speed correct and continueDestroyBlock syncs it to the server.
    private void selectOptimalTool(BlockState state) {
        Inventory inventory = mc.player.getInventory();
        int i = inventory.getSelectedSlot();
        float f = inventory.getItem(i).getDestroySpeed(state);

        for (int j = 0; j < 9; j++) {
            float f1 = inventory.getItem(j).getDestroySpeed(state);
            if (f1 > f) {
                f = f1;
                i = j;
            }
        }

        if (f <= 1.0001F) {
            int k = firstEmptyHotbarSlot(inventory);
            if (k != -1) {
                i = k;
            }
        }

        if (i != inventory.getSelectedSlot()) {
            inventory.setSelectedSlot(i);
        }
    }

    private static int firstEmptyHotbarSlot(Inventory inventory) {
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private boolean shouldYieldToCombat() {
        KillAuraModule killauramodule = Adapt.getInstance().getModuleManager().getByClass(KillAuraModule.class);
        if (killauramodule != null && killauramodule.isEnabled() && killauramodule.getTargetFinder().hasAttackTarget()) {
            return true;
        }

        double d0 = this.combatRange.getValue();
        for (PlayerTarget playertarget : TargetHelper.getInstance().getTargets(this.targetSettings.getFlags(), PlayerTarget.class)) {
            if (!playertarget.isLocalPlayer() && playertarget.getEntity() instanceof LivingEntity livingentity) {
                if (livingentity.isAlive() && !livingentity.isSpectator() && PlayerUtility.getDistanceToEntityBox(livingentity) <= d0) {
                    return true;
                }
            }
        }

        return false;
    }

    private BlockPos nearestBed() {
        double d0 = this.breakRange.getValue();
        Vec3 vec3 = mc.player.getEyePosition();
        BlockPos blockpos = null;
        double d1 = d0 * d0;

        for (BlockPos blockpos1 : this.knownBeds) {
            double d2 = vec3.distanceToSqr(blockpos1.getX() + 0.5, blockpos1.getY() + 0.5, blockpos1.getZ() + 0.5);
            if (d2 <= d1) {
                d1 = d2;
                blockpos = blockpos1;
            }
        }

        return blockpos;
    }

    // Collects bed positions from loaded chunks (so beds are known even when hidden behind defence). Sections without any
    // bed are skipped by their palette, so this stays cheap enough to run every tick even at a large ESP range.
    private List<BlockPos> scanBeds() {
        double d0 = this.render.getValue() ? Math.max(this.espRange.getValue(), this.breakRange.getValue()) : this.breakRange.getValue();
        List<BlockPos> list = new ArrayList<>();
        Vec3 vec3 = mc.player.getEyePosition();
        double d1 = d0 * d0;
        int i = Math.min(mc.options.getEffectiveRenderDistance(), (int)Math.ceil(d0 / 16.0) + 1);
        int j = mc.player.chunkPosition().x();
        int k = mc.player.chunkPosition().z();

        for (int l = j - i; l <= j + i; l++) {
            for (int i1 = k - i; i1 <= k + i; i1++) {
                if (mc.level.getChunkSource().getChunk(l, i1, ChunkStatus.FULL, false) instanceof LevelChunk levelchunk) {
                    LevelChunkSection[] alevelchunksection = levelchunk.getSections();
                    int j1 = levelchunk.getPos().getMinBlockX();
                    int k1 = levelchunk.getPos().getMinBlockZ();

                    for (int l1 = 0; l1 < alevelchunksection.length; l1++) {
                        LevelChunkSection levelchunksection = alevelchunksection[l1];
                        if (levelchunksection != null
                            && !levelchunksection.hasOnlyAir()
                            && levelchunksection.maybeHas(state -> state.getBlock() instanceof BedBlock)) {
                            int i2 = mc.level.getSectionYFromSectionIndex(l1) << 4;

                            for (int j2 = 0; j2 < 16; j2++) {
                                for (int k2 = 0; k2 < 16; k2++) {
                                    for (int l2 = 0; l2 < 16; l2++) {
                                        if (levelchunksection.getBlockState(k2, j2, l2).getBlock() instanceof BedBlock
                                            && vec3.distanceToSqr(j1 + k2 + 0.5, i2 + j2 + 0.5, k1 + l2 + 0.5) <= d1) {
                                            list.add(new BlockPos(j1 + k2, i2 + j2, k1 + l2));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return list;
    }

    private Vec3 closestPoint(BlockPos pos, Vec3 eye) {
        double d0 = Math.max(pos.getX(), Math.min(eye.x, pos.getX() + 1.0));
        double d1 = Math.max(pos.getY(), Math.min(eye.y, pos.getY() + 1.0));
        double d2 = Math.max(pos.getZ(), Math.min(eye.z, pos.getZ() + 1.0));
        return new Vec3(d0, d1, d2);
    }

    private static AABB shapeBox(BlockPos pos, BlockState state) {
        VoxelShape voxelshape = state.getShape(mc.level, pos);
        return (voxelshape.isEmpty() ? new AABB(pos) : voxelshape.bounds().move(pos)).inflate(0.002);
    }

    private static int alpha(float value) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F) << 24;
    }
}
