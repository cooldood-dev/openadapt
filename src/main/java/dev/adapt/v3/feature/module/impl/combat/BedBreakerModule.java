package dev.adapt.v3.feature.module.impl.combat;

import dev.adapt.v3.Adapt;
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
import dev.adapt.v3.feature.module.impl.world.scaffold.ScaffoldModule;
import dev.adapt.v3.feature.property.impl.NumberProperty;
import dev.adapt.v3.util.player.PlayerUtility;
import dev.adapt.v3.util.player.RaytraceUtility;
import dev.adapt.v3.util.player.RaytracedRotation;
import dev.adapt.v3.util.player.RotationUtility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

// Breaks the nearest reachable bed the way a real player does: it turns the *server* rotation onto an exposed bed face with
// a human rotation model and only mines once that rotation actually raycasts onto the bed, while the camera stays free (the
// user can look anywhere). It never mines through walls, aborts the instant the bed leaves reach/line of sight (you ran off),
// and yields to combat - whenever KillAura is on a target, or any enemy comes within range, it stops and lets KillAura fight.
public final class BedBreakerModule extends Module {
    public final RotationProperties rotation = new RotationProperties(this, new LinearRotationModel(90.0));
    public final NumberProperty combatRange = new NumberProperty("Combat range", "m", 6.0, 2.0, 16.0, 0.5);
    public final TargetProperties targetSettings;
    private BlockPos destroyPos;

    public BedBreakerModule() {
        super("Bed Breaker", "Breaks the nearest bed legitimately with a free camera, aborting for combat.", ModuleCategory.COMBAT);
        this.addProperties(this.combatRange);
        // Players on, no local-player option, Hostile on, Passive off, Teammates off (never abort for a teammate walking up)
        this.targetSettings = new TargetProperties(this, true, false, false, true, false, false);
    }

    @Override
    public void onDisable() {
        this.abort();
    }

    @Subscribe(priority = 1)
    public void onPreTick(PreTickEvent event) {
        if (!this.isEnabled() || mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) {
            this.abort();
            return;
        }

        // combat takes priority: let KillAura run, and bail out the moment an enemy is close enough to fight
        if (this.shouldYieldToCombat()) {
            this.abort();
            return;
        }

        BedBreakerModule.BedAim bedaim = this.findNearestBedAim();
        if (bedaim == null) {
            // no exposed bed in reach - either you ran off, it is walled, or it is already gone
            this.abort();
            return;
        }

        // turn the server rotation onto the bed (camera stays free), then check what that rotation actually hits this tick
        RotationHelper.getUpdater().setRotation(bedaim.rotation(), this.rotation.createModel());
        HitResult hitresult = RaytraceUtility.raytraceBlockFromVec(
            mc.player.blockInteractionRange(), false, mc.player.getYRot(), mc.player.getXRot(), mc.player.getEyePosition()
        );
        if (hitresult instanceof BlockHitResult blockhitresult
            && blockhitresult.getType() == HitResult.Type.BLOCK
            && mc.level.getBlockState(blockhitresult.getBlockPos()).getBlock() instanceof BedBlock) {
            boolean flag = mc.gameMode.continueDestroyBlock(blockhitresult.getBlockPos(), blockhitresult.getDirection());
            this.destroyPos = blockhitresult.getBlockPos();
            if (flag) {
                mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
            }
        } else {
            // still mid-turn, or lost line of sight - don't send a dig while not actually looking at the bed
            this.abort();
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

    private BedBreakerModule.BedAim findNearestBedAim() {
        double d0 = mc.player.blockInteractionRange();
        Vec3 vec3 = mc.player.getEyePosition();
        Vec2 vec2 = new Vec2(mc.player.getYRot(), mc.player.getXRot());
        int i = (int)Math.ceil(d0) + 1;
        BlockPos blockpos = mc.player.blockPosition();
        List<BlockPos> arraylist = new ArrayList<>();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int j = -i; j <= i; j++) {
            for (int k = -i; k <= i; k++) {
                for (int l = -i; l <= i; l++) {
                    blockpos$mutableblockpos.set(blockpos.getX() + j, blockpos.getY() + k, blockpos.getZ() + l);
                    if (mc.level.getBlockState(blockpos$mutableblockpos).getBlock() instanceof BedBlock
                        && vec3.distanceToSqr(
                                blockpos$mutableblockpos.getX() + 0.5, blockpos$mutableblockpos.getY() + 0.5, blockpos$mutableblockpos.getZ() + 0.5
                            )
                            <= (d0 + 1.0) * (d0 + 1.0)) {
                        arraylist.add(blockpos$mutableblockpos.immutable());
                    }
                }
            }
        }

        if (arraylist.isEmpty()) {
            return null;
        }

        arraylist.sort(Comparator.comparingDouble(pos -> vec3.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));

        for (BlockPos blockpos1 : arraylist) {
            Vec2 vec21 = this.aimAtBed(blockpos1, vec2, vec3);
            if (vec21 != null) {
                return new BedBreakerModule.BedAim(blockpos1, vec21);
            }
        }

        return null;
    }

    // Finds the rotation that most cheaply looks at an exposed, reachable face of this bed (null if every face is hidden).
    private Vec2 aimAtBed(BlockPos pos, Vec2 current, Vec3 eye) {
        Vec2 vec2 = null;
        double d0 = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            double d1 = pos.getX() + 0.5 + direction.getStepX() * 0.5;
            double d2 = pos.getY() + 0.5 + direction.getStepY() * 0.5;
            double d3 = pos.getZ() + 0.5 + direction.getStepZ() * 0.5;
            // skip faces pointing away from the eye; they can never be raycast-hit
            if ((eye.x - d1) * direction.getStepX() + (eye.y - d2) * direction.getStepY() + (eye.z - d3) * direction.getStepZ() <= 0.0) {
                continue;
            }

            RaytracedRotation raytracedrotation = ScaffoldModule.raytraceFace(pos, direction, current, eye);
            if (raytracedrotation != null) {
                double d4 = RotationUtility.getAngleDifference(raytracedrotation.rotation(), current);
                if (d4 < d0) {
                    d0 = d4;
                    vec2 = raytracedrotation.rotation();
                }
            }
        }

        return vec2;
    }

    private record BedAim(BlockPos pos, Vec2 rotation) {
    }
}
