package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Final;
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
    @Shadow @Final public GameOptions options;

    @Shadow private void doItemUse() {}
    @Shadow private boolean doAttack() { return false; }

    private static final int EXTRA_ACTIONS = 3;

    private boolean vtools$shouldClear() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        return prefs != null && prefs.isActive() && prefs.isFastInteract();
    }

    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void vtools$clearUseCooldown(CallbackInfo ci) {
        if (vtools$shouldClear()) {
            this.itemUseCooldown = 0;
        }
    }

    @Inject(method = "doAttack", at = @At("RETURN"))
    private void vtools$clearAttackCooldown(CallbackInfoReturnable<Boolean> cir) {
        if (vtools$shouldClear()) {
            this.attackCooldown = 0;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void vtools$clearCooldownsOnTick(CallbackInfo ci) {
        if (vtools$shouldClear()) {
            this.itemUseCooldown = 0;
            this.attackCooldown = 0;
        }
    }

    // Send extra use/attack actions per tick when buttons are held
    @Inject(method = "handleInputEvents", at = @At("RETURN"))
    private void vtools$extraActions(CallbackInfo ci) {
        if (!vtools$shouldClear()) return;

        for (int i = 0; i < EXTRA_ACTIONS; i++) {
            if (this.options.useKey.isPressed()) {
                this.itemUseCooldown = 0;
                this.doItemUse();
            }
            if (this.options.attackKey.isPressed()) {
                this.attackCooldown = 0;
                this.doAttack();
            }
        }
    }
}
