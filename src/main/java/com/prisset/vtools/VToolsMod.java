package com.prisset.vtools;

import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.config.ProfileIndex;
import com.prisset.vtools.gui.PrefsScreen;
import com.prisset.vtools.input.AutoFarmHandler;
import com.prisset.vtools.input.SequenceListener;
import com.prisset.vtools.input.WTapHandler;
import com.prisset.vtools.render.ChestSearchOverlay;
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
            ChestSearchOverlay.tick();
            AlertDispatcher.scan(client, prefs);
            AlertDispatcher.scanAfk(client, prefs);
        });
    }

    public static void onKeyPress(int keyCode) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        if (client.currentScreen != null) return;

        if (seq.onKey(keyCode, tickCounter)) {
            seq.reset();
            client.setScreen(new PrefsScreen(prefs));
        }
    }

    public static DisplayPrefs getPrefs() {
        return prefs;
    }
}
