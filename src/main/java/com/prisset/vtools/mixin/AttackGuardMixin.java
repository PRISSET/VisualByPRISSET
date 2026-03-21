package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.util.DistanceCheck;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class AttackGuardMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void vtools$guardAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive()) return;

        if (!DistanceCheck.isValid(player, target)) {
            ci.cancel();
        }
    }
}
