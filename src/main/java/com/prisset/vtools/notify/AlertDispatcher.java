package com.prisset.vtools.notify;

import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.config.ProfileIndex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AlertDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final long COOLDOWN_MS = 5 * 60 * 1000L;
    private static final int MAX_NEARBY_PLAYERS = 15;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .executor(Runnable::run)
        .build();

    private static final Map<String, Long> lastAlerted = new HashMap<>();

    private AlertDispatcher() {}

    public static void scan(MinecraftClient client, DisplayPrefs prefs) {
        if (!prefs.isTgEnabled()) return;
        if (client.world == null || client.player == null) return;

        String token = prefs.getTgBotToken();
        String chatId = prefs.getTgChatId();
        if (token.isBlank() || chatId.isBlank()) return;

        PlayerEntity self = client.player;
        List<AbstractClientPlayerEntity> players = client.world.getPlayers();

        int otherCount = 0;
        for (AbstractClientPlayerEntity p : players) {
            if (p == self) continue;
            otherCount++;
        }

        if (otherCount > MAX_NEARBY_PLAYERS) return;

        long now = System.currentTimeMillis();
        lastAlerted.entrySet().removeIf(e -> now - e.getValue() > COOLDOWN_MS * 2);

        for (AbstractClientPlayerEntity player : players) {
            if (player == self) continue;

            String name = player.getGameProfile().getName();
            if (ProfileIndex.get().isTeammate(name)) continue;

            Long lastTime = lastAlerted.get(name.toLowerCase());
            if (lastTime != null && now - lastTime < COOLDOWN_MS) continue;

            int px = (int) player.getX();
            int py = (int) player.getY();
            int pz = (int) player.getZ();
            int dist = (int) self.distanceTo(player);

            String text = String.format(
                "[PRISSET] \u0418\u0433\u0440\u043e\u043a %s \u043e\u0431\u043d\u0430\u0440\u0443\u0436\u0435\u043d!\n" +
                "\u041a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442\u044b: X: %d, Y: %d, Z: %d\n" +
                "\u0420\u0430\u0441\u0441\u0442\u043e\u044f\u043d\u0438\u0435: %d \u0431\u043b\u043e\u043a\u043e\u0432",
                name, px, py, pz, dist
            );

            lastAlerted.put(name.toLowerCase(), now);
            sendAsync(token, chatId, text);
        }
    }

    private static void sendAsync(String token, String chatId, String text) {
        new Thread(() -> {
            try {
                String encoded = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(encoded))
                    .build();

                HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                LOG.warn("TG alert failed", e);
            }
        }, "vtools-tg").start();
    }
}
