package com.prisset.vtools.mixin;

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
 * Intercepts attack (LMB) and use (RMB) clicks to set blueprint selection points.
 * When blueprint selection mode is active, clicks set pos1/pos2 instead of
 * performing normal game actions.
 */
@Mixin(MinecraftClient.class)
public abstract class BlueprintClickMixin {

    @Shadow public ClientPlayerEntity player;
    @Shadow public HitResult crosshairTarget;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void vtools$blueprintAttack(CallbackInfoReturnable<Boolean> cir) {
        SelectionManager sel = SelectionManager.instance();
        if (!sel.isActive()) return;
        if (crosshairTarget == null || crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult hit = (BlockHitResult) crosshairTarget;
        sel.setPos1(hit.getBlockPos());
        cir.setReturnValue(false);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void vtools$blueprintUse(CallbackInfo ci) {
        SelectionManager sel = SelectionManager.instance();
        if (!sel.isActive()) return;
        if (crosshairTarget == null || crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult hit = (BlockHitResult) crosshairTarget;
        sel.setPos2(hit.getBlockPos());
        ci.cancel();
    }
}
