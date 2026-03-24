package com.prisset.vtools.input;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class WTapHandler {

    private static int phase = 0;
    private static int ticks = 0;
    private static final int OFF_TICKS = 1;
    private static final int ON_TICKS = 2;

    private WTapHandler() {}

    public static void onAttack() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isWTap()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        mc.player.setSprinting(true);
        phase = 1;
        ticks = 0;
    }

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
                player.setSprinting(false);
                if (ticks >= OFF_TICKS) {
                    phase = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                player.setSprinting(true);
                if (ticks >= ON_TICKS) {
                    phase = 0;
                    ticks = 0;
                }
            }
        }
    }
}
