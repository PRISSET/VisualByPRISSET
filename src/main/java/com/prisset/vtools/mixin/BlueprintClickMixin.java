package com.prisset.vtools.mixin;

import com.prisset.vtools.blueprint.BlueprintPlacer;
import com.prisset.vtools.blueprint.SelectionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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
 * Placement mode: LMB = confirm schematic position at crosshair
 */
@Mixin(MinecraftClient.class)
public abstract class BlueprintClickMixin {

    @Shadow public ClientPlayerEntity player;
    @Shadow public HitResult crosshairTarget;

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
        if (crosshairTarget == null || crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult hit = (BlockHitResult) crosshairTarget;
        sel.setPos1(hit.getBlockPos());
        cir.setReturnValue(false);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void vtools$blueprintUse(CallbackInfo ci) {
        // Block RMB during placement mode to prevent accidental block placement
        BlueprintPlacer placer = BlueprintPlacer.instance();
        if (placer.isPlacementMode() && placer.isLoaded()) {
            ci.cancel();
            return;
        }

        // Selection mode: RMB = pos2
        SelectionManager sel = SelectionManager.instance();
        if (!sel.isActive()) return;
        if (crosshairTarget == null || crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult hit = (BlockHitResult) crosshairTarget;
        sel.setPos2(hit.getBlockPos());
        ci.cancel();
    }
}
