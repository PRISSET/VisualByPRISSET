package com.prisset.vtools.mixin;

import com.prisset.vtools.input.WTapHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts attacks to trigger W-tap sprint-reset.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class WTapMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void vtools$onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        WTapHandler.onAttack();
    }
}
