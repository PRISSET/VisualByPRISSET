package com.prisset.vtools.input;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * W-tap: sprint-reset for maximum knockback on every hit.
 *
 * After each attack:
 *   1. Immediately stop moving (release W, kill sprint) so sprint resets
 *   2. Stay stopped for a few ticks
 *   3. Re-engage W + sprint so the next hit is a sprint-hit
 *
 * This ensures Minecraft registers a NEW sprint for each hit,
 * giving full knockback bonus every time.
 */
public final class WTapHandler {

    // 0 = idle (not doing anything)
    // 1 = STOP phase (W released, waiting)
    // 2 = SPRINT phase (W pressed, sprinting toward target)
    private static int phase = 0;
    private static int ticks = 0;

    // How long to hold W released after a hit (ticks)
    private static final int STOP_TICKS = 2;
    // How long to sprint before next hit (ticks) — enough to get sprint going
    private static final int SPRINT_TICKS = 3;

    private WTapHandler() {}

    /**
     * Called RIGHT BEFORE the attack lands (from mixin on attackEntity HEAD).
     * At this point we must be sprinting for knockback bonus.
     */
    public static void onAttack() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isWTap()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;

        // Force sprint for THIS hit
        mc.player.setSprinting(true);

        // After the hit, start the stop phase
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
        if (player == null || mc.options == null) {
            phase = 0;
            return;
        }

        ticks++;

        switch (phase) {
            case 1 -> {
                // STOP: release W, break sprint
                mc.options.forwardKey.setPressed(false);
                mc.options.sprintKey.setPressed(false);
                player.setSprinting(false);

                if (ticks >= STOP_TICKS) {
                    phase = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                // SPRINT: press W + sprint to re-enter sprint before next hit
                mc.options.forwardKey.setPressed(true);
                mc.options.sprintKey.setPressed(true);
                player.setSprinting(true);

                if (ticks >= SPRINT_TICKS) {
                    // Done — keys back to whatever the player is actually pressing
                    phase = 0;
                    ticks = 0;
                    // Don't force keys anymore, let real input take over
                }
            }
        }
    }
}
