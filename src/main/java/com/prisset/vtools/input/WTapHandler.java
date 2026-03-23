package com.prisset.vtools.input;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Sprint-reset for maximum knockback on every hit.
 *
 * Minecraft gives extra knockback only on the FIRST hit while sprinting.
 * To get max KB on every hit, we reset sprinting between attacks.
 *
 * We don't touch W key at all (player controls movement).
 * We only manipulate sprint state:
 *   1. On attack: force sprinting = true (hit gets KB bonus)
 *   2. After attack: force sprinting = false for a few ticks (resets sprint)
 *   3. Then force sprinting = true again (ready for next hit)
 */
public final class WTapHandler {

    private static int phase = 0;  // 0=idle, 1=sprint-off, 2=sprint-on
    private static int ticks = 0;
    private static final int OFF_TICKS = 1;  // how long sprint stays off
    private static final int ON_TICKS = 2;   // how long to force sprint back on

    private WTapHandler() {}

    /** Called from WTapMixin right before attackEntity executes */
    public static void onAttack() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isWTap()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // This hit must land while sprinting
        mc.player.setSprinting(true);

        // Start sprint-reset cycle
        phase = 1;
        ticks = 0;
    }

    /** Called every client tick */
    public static void tick() {
        if (phase == 0) return;

        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isWTap()) {
            phase = 0;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            phase = 0;
            return;
        }

        ticks++;

        switch (phase) {
            case 1 -> {
                // Kill sprint so Minecraft resets the "already sprinted" flag
                player.setSprinting(false);
                if (ticks >= OFF_TICKS) {
                    phase = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                // Re-engage sprint so the next hit gets knockback
                player.setSprinting(true);
                if (ticks >= ON_TICKS) {
                    phase = 0;
                    ticks = 0;
                }
            }
        }
    }
}
