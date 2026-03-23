package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * W-tap / sprint-reset for maximum knockback on every hit.
 *
 * Cycle per attack:
 *   1. IDLE: waiting for attack
 *   2. Attack detected: ensure sprinting, let the hit land
 *   3. RELEASE (1 tick): release W to break sprint
 *   4. PUSH (2-3 ticks): press W + sprint again to re-enter sprint
 *   5. Back to IDLE
 *
 * This ensures every hit is a sprint-hit with full knockback bonus.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class WTapMixin {

    @Unique private static int wtapPhase = 0;  // 0=idle, 1=release, 2=push
    @Unique private static int wtapTicks = 0;

    /**
     * On attack: force sprint so the hit gets knockback bonus,
     * then start the sprint-reset cycle.
     */
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void vtools$onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isWTap()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;

        // Ensure this hit lands as a sprint-hit
        mc.options.forwardKey.setPressed(true);
        mc.options.sprintKey.setPressed(true);
        mc.player.setSprinting(true);

        // Start sprint-reset cycle after the hit
        wtapPhase = 1;
        wtapTicks = 0;
    }

    /**
     * Tick the sprint-reset state machine.
     * Called from ClientPlayerEntity.tick() mixin to run every client tick.
     */
    @Unique
    public static void tickWTap() {
        if (wtapPhase == 0) return;

        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isWTap()) {
            wtapPhase = 0;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) {
            wtapPhase = 0;
            return;
        }

        wtapTicks++;

        if (wtapPhase == 1) {
            // RELEASE: drop W + sprint for 1 tick to reset sprint state
            mc.options.forwardKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false);

            if (wtapTicks >= 1) {
                wtapPhase = 2;
                wtapTicks = 0;
            }
        } else if (wtapPhase == 2) {
            // PUSH: re-engage W + sprint
            mc.options.forwardKey.setPressed(true);
            mc.options.sprintKey.setPressed(true);
            mc.player.setSprinting(true);

            if (wtapTicks >= 2) {
                wtapPhase = 0;
                wtapTicks = 0;
            }
        }
    }
}
