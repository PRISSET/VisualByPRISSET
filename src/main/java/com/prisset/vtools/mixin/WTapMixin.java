package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * W-tap automation for crystal PvP.
 * On each attack: forces W + Sprint so the hit applies maximum knockback.
 * Sprint-hitting gives 1.0 extra knockback vs standing still.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class WTapMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void vtools$wTapOnAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isWTap()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;

        // Force sprint + forward on attack for max knockback
        mc.options.forwardKey.setPressed(true);
        mc.player.setSprinting(true);
    }
}
