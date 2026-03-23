package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Texture-independent attack cooldown indicator rendered below crosshair.
 * Drawn with primitives so texture packs cannot hide it.
 * Shows cooldown progress bar + changes color when ready (white -> green).
 */
@Mixin(InGameHud.class)
public abstract class AttackIndicatorMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void vtools$renderAttackIndicator(DrawContext ctx, float tickDelta, CallbackInfo ci) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isAttackIndicator()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null) return;

        float progress = mc.player.getAttackCooldownProgress(0.5f);
        boolean hasTarget = mc.targetedEntity != null;
        boolean ready = progress >= 1.0f;

        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;

        int barW = 11;
        int barH = 1;
        int barX = cx - barW / 2;
        int barY = cy + 4;

        // Background always visible
        ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0x80000000);

        int fillW = (int) (progress * barW);

        if (ready && hasTarget) {
            // Full damage + target: green
            ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF40FF40);
        } else if (ready) {
            // Full damage, no target: white
            ctx.fill(barX, barY, barX + barW, barY + barH, 0xFFFFFFFF);
        } else {
            // Charging: white fill on gray
            ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
            ctx.fill(barX, barY, barX + fillW, barY + barH, 0xFFFFFFFF);
        }
    }
}
