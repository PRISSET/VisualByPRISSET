package com.prisset.vtools;

import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.config.ProfileIndex;
import com.prisset.vtools.gui.PrefsScreen;
import com.prisset.vtools.input.AutoFarmHandler;
import com.prisset.vtools.input.InstantKillHandler;
import com.prisset.vtools.input.SequenceListener;
import com.prisset.vtools.input.WTapHandler;
import com.prisset.vtools.notify.AlertDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class VToolsMod implements ClientModInitializer {

    private static DisplayPrefs prefs;
    private static final SequenceListener seq = new SequenceListener();
    private static long tickCounter = 0;

    @Override
    public void onInitializeClient() {
        prefs = DisplayPrefs.load();
        ProfileIndex.get().load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            seq.tick(tickCounter);
            WTapHandler.tick();
            AutoFarmHandler.tick();
            InstantKillHandler.tick();
            AlertDispatcher.scan(client, prefs);
            AlertDispatcher.scanAfk(client, prefs);
        });
    }

    public static void onKeyPress(int keyCode) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        if (client.currentScreen == null) {
            if (prefs != null && prefs.getInstantKillKey() >= 0 && keyCode == prefs.getInstantKillKey()) {
                InstantKillHandler.execute();
            }
        }

        if (client.currentScreen != null) return;

        if (seq.onKey(keyCode, tickCounter)) {
            seq.reset();
            client.setScreen(new PrefsScreen(prefs));
        }
    }

    public static void onMousePress(int button) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        if (client.currentScreen != null) return;

        int encoded = -(button + 1);
        if (prefs != null && prefs.getInstantKillKey() < 0 && encoded == prefs.getInstantKillKey()) {
            InstantKillHandler.execute();
        }
    }

    public static DisplayPrefs getPrefs() {
        return prefs;
    }
}
