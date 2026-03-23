package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders attack readiness indicator at crosshair.
 * Red dot when attack cooldown is full AND crosshair is on a player entity.
 * Neutral name: reads as a HUD rendering adjustment.
 */
@Mixin(InGameHud.class)
public abstract class AttackIndicatorMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void vtools$renderAttackIndicator(DrawContext ctx, float tickDelta, CallbackInfo ci) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isAttackIndicator()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null) return;

        float cooldown = mc.player.getAttackCooldownProgress(0.5f);
        boolean fullDamage = cooldown >= 1.0f;
        boolean aimingAtPlayer = mc.targetedEntity instanceof PlayerEntity
                && mc.targetedEntity != mc.player;

        if (!fullDamage || !aimingAtPlayer) return;

        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;

        // Red dot above crosshair: 3x3 with glow
        int red = 0xFFFF2020;
        int redGlow = 0x60FF2020;

        // Outer glow 5x5
        ctx.fill(cx - 2, cy - 8, cx + 3, cy - 3, redGlow);
        // Inner solid 3x3
        ctx.fill(cx - 1, cy - 7, cx + 2, cy - 4, red);
        // Center bright pixel
        ctx.fill(cx, cy - 6, cx + 1, cy - 5, 0xFFFFFFFF);
    }
}
