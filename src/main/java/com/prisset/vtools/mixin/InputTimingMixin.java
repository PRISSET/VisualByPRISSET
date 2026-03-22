package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(MinecraftClient.class)
public abstract class InputTimingMixin {

    @Shadow private int itemUseCooldown;
    @Shadow protected int attackCooldown;
    @Shadow public ClientPlayerEntity player;

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

    // Random cooldown 0-1 ticks instead of vanilla 4
    // avg ~1.5 ticks (75ms) between actions = ~13/sec, irregular pattern
    @Unique
    private int vtools$randomSmallCooldown() {
        return ThreadLocalRandom.current().nextInt(0, 2);
    }

    // After doItemUse sets cooldown=4, replace with 0-1
    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void vtools$clearUseCooldown(CallbackInfo ci) {
        if (vtools$fastEnabled() && vtools$holdsCrystal()) {
            this.itemUseCooldown = vtools$randomSmallCooldown();
        }
    }

    // After doAttack sets cooldown=10, replace with 0-1
    @Inject(method = "doAttack", at = @At("RETURN"))
    private void vtools$clearAttackCooldown(CallbackInfoReturnable<Boolean> cir) {
        if (vtools$fastEnabled() && vtools$holdsCrystal()) {
            this.attackCooldown = vtools$randomSmallCooldown();
        }
    }
}
