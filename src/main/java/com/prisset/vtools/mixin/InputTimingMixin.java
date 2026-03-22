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

    // ~30% chance to skip = avg ~14 actions/sec, looks human
    @Unique
    private boolean vtools$shouldSkip() {
        return ThreadLocalRandom.current().nextFloat() < 0.3f;
    }

    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void vtools$clearUseCooldown(CallbackInfo ci) {
        if (vtools$fastEnabled() && vtools$holdsCrystal() && !vtools$shouldSkip()) {
            this.itemUseCooldown = 0;
        }
    }

    @Inject(method = "doAttack", at = @At("RETURN"))
    private void vtools$clearAttackCooldown(CallbackInfoReturnable<Boolean> cir) {
        if (vtools$fastEnabled() && vtools$holdsCrystal() && !vtools$shouldSkip()) {
            this.attackCooldown = 0;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void vtools$clearCooldownsOnTick(CallbackInfo ci) {
        if (vtools$fastEnabled() && vtools$holdsCrystal() && !vtools$shouldSkip()) {
            this.itemUseCooldown = 0;
            this.attackCooldown = 0;
        }
    }
}
