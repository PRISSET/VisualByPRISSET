package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class InputTimingMixin {

    @Shadow private int itemUseCooldown;
    @Shadow protected int attackCooldown;
    @Shadow @Final public GameOptions options;
    @Shadow public ClientPlayerEntity player;

    @Shadow private void doItemUse() {}
    @Shadow private boolean doAttack() { return false; }

    // ~16ms average (3 actions per 50ms tick)
    @Unique private static final int EXTRA_PER_TICK = 2;

    @Unique
    private boolean vtools$fastEnabled() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        return prefs != null && prefs.isActive() && prefs.isFastInteract();
    }

    @Unique
    private boolean vtools$holdsCrystal() {
        if (this.player == null) return false;
        return this.player.getStackInHand(Hand.MAIN_HAND).isOf(Items.END_CRYSTAL)
            || this.player.getStackInHand(Hand.OFF_HAND).isOf(Items.END_CRYSTAL);
    }

    // Only clear cooldown when holding crystal
    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void vtools$clearUseCooldown(CallbackInfo ci) {
        if (vtools$fastEnabled() && vtools$holdsCrystal()) {
            this.itemUseCooldown = 0;
        }
    }

    @Inject(method = "doAttack", at = @At("RETURN"))
    private void vtools$clearAttackCooldown(CallbackInfoReturnable<Boolean> cir) {
        if (vtools$fastEnabled() && vtools$holdsCrystal()) {
            this.attackCooldown = 0;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void vtools$clearCooldownsOnTick(CallbackInfo ci) {
        if (vtools$fastEnabled() && vtools$holdsCrystal()) {
            this.itemUseCooldown = 0;
            this.attackCooldown = 0;
        }
    }

    // Extra crystal actions per tick for ~16ms average delay
    @Inject(method = "handleInputEvents", at = @At("RETURN"))
    private void vtools$extraActions(CallbackInfo ci) {
        if (!vtools$fastEnabled() || !vtools$holdsCrystal()) return;

        for (int i = 0; i < EXTRA_PER_TICK; i++) {
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
