package com.prisset.vtools.mixin;

import com.prisset.vtools.blueprint.BlueprintPlacer;
import com.prisset.vtools.blueprint.SelectionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts attack (LMB) and use (RMB) clicks for blueprint features.
 *
 * Selection mode: LMB = pos1, RMB = pos2
 *   - Works on blocks AND in air (raycast 64 blocks from eye)
 * Placement mode: LMB = confirm schematic position at crosshair
 */
@Mixin(MinecraftClient.class)
public abstract class BlueprintClickMixin {

    @Shadow public ClientPlayerEntity player;
    @Shadow public HitResult crosshairTarget;

    private static final double AIR_SELECT_RANGE = 64.0;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void vtools$blueprintAttack(CallbackInfoReturnable<Boolean> cir) {
        // Placement mode: LMB confirms schematic position
        BlueprintPlacer placer = BlueprintPlacer.instance();
        if (placer.isPlacementMode() && placer.isLoaded()) {
            if (crosshairTarget != null && crosshairTarget.getType() == HitResult.Type.BLOCK) {
                placer.confirmPlacement();
                cir.setReturnValue(false);
                return;
            }
        }

        // Selection mode: LMB = pos1
        SelectionManager sel = SelectionManager.instance();
        if (!sel.isActive()) return;

        BlockPos pos = getTargetBlockPos();
        if (pos != null) {
            sel.setPos1(pos);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void vtools$blueprintUse(CallbackInfo ci) {
        // Block RMB during placement mode
        BlueprintPlacer placer = BlueprintPlacer.instance();
        if (placer.isPlacementMode() && placer.isLoaded()) {
            ci.cancel();
            return;
        }

        // Selection mode: RMB = pos2
        SelectionManager sel = SelectionManager.instance();
        if (!sel.isActive()) return;

        BlockPos pos = getTargetBlockPos();
        if (pos != null) {
            sel.setPos2(pos);
            ci.cancel();
        }
    }

    /**
     * Get block position from crosshair.
     * If looking at a block, returns that block pos.
     * If looking at air, raycasts forward and returns the air block position.
     */
    private BlockPos getTargetBlockPos() {
        // If crosshair hits a block, use that
        if (crosshairTarget != null && crosshairTarget.getType() == HitResult.Type.BLOCK) {
            return ((BlockHitResult) crosshairTarget).getBlockPos();
        }

        // Air selection: raycast from eyes in look direction
        if (player == null) return null;
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d target = eye.add(look.multiply(AIR_SELECT_RANGE));
        return BlockPos.ofFloored(target.x, target.y, target.z);
    }
}
