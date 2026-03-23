package com.prisset.vtools.input;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;

/**
 * Sprint-reset W-tap state machine.
 *
 * Cycle per attack:
 *   1. Attack detected: ensure sprinting, hit lands with max knockback
 *   2. RELEASE (1 tick): release W to break sprint
 *   3. PUSH (2 ticks): press W + sprint to re-enter sprint
 *   4. Back to idle, ready for next hit
 */
public final class WTapHandler {

    private static int phase = 0;  // 0=idle, 1=release, 2=push
    private static int ticks = 0;

    private WTapHandler() {}

    /** Called from WTapMixin on attackEntity */
    public static void onAttack() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isWTap()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;

        mc.options.forwardKey.setPressed(true);
        mc.options.sprintKey.setPressed(true);
        mc.player.setSprinting(true);

        phase = 1;
        ticks = 0;
    }

    /** Called every client tick from VToolsMod */
    public static void tick() {
        if (phase == 0) return;

        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isWTap()) {
            phase = 0;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) {
            phase = 0;
            return;
        }

        ticks++;

        if (phase == 1) {
            // Release W + sprint for 1 tick to reset sprint state
            mc.options.forwardKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false);

            if (ticks >= 1) {
                phase = 2;
                ticks = 0;
            }
        } else if (phase == 2) {
            // Re-engage W + sprint
            mc.options.forwardKey.setPressed(true);
            mc.options.sprintKey.setPressed(true);
            mc.player.setSprinting(true);

            if (ticks >= 2) {
                phase = 0;
                ticks = 0;
            }
        }
    }
}
