package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class InputTimingMixin {

    @Shadow private int itemUseCooldown;
    @Shadow protected int attackCooldown;

    private boolean vtools$shouldClear() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        return prefs != null && prefs.isActive() && prefs.isFastInteract();
    }

    // Reset after doItemUse sets it to 4
    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void vtools$clearUseCooldown(CallbackInfo ci) {
        if (vtools$shouldClear()) {
            this.itemUseCooldown = 0;
        }
    }

    // Reset after doAttack sets it to 10
    @Inject(method = "doAttack", at = @At("RETURN"))
    private void vtools$clearAttackCooldown(CallbackInfoReturnable<Boolean> cir) {
        if (vtools$shouldClear()) {
            this.attackCooldown = 0;
        }
    }

    // Also reset every tick so held-button path never sees cooldown > 0
    @Inject(method = "tick", at = @At("HEAD"))
    private void vtools$clearCooldownsOnTick(CallbackInfo ci) {
        if (vtools$shouldClear()) {
            this.itemUseCooldown = 0;
            this.attackCooldown = 0;
        }
    }
}
