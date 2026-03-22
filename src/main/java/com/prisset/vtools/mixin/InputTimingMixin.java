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

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void vtools$clearUseCooldown(CallbackInfo ci) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs != null && prefs.isActive() && prefs.isFastInteract()) {
            this.itemUseCooldown = 0;
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void vtools$clearAttackCooldown(CallbackInfoReturnable<Boolean> cir) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs != null && prefs.isActive() && prefs.isFastInteract()) {
            this.attackCooldown = 0;
        }
    }
}
