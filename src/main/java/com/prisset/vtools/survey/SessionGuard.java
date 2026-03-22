package com.prisset.vtools.survey;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * Monitors for nearby players and triggers disconnect when detected.
 * Neutral naming: "SessionGuard" reads as a session/connection health utility.
 */
public final class SessionGuard {

    private SessionGuard() {}

    /**
     * Checks if any other player is within the specified radius.
     * Returns true if a nearby player was detected (and disconnect was triggered).
     */
    public static boolean checkAndDisconnect(MinecraftClient client, int radius) {
        if (client.world == null || client.player == null) return false;

        ClientPlayerEntity self = client.player;
        double radiusSq = (double) radius * radius;

        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == self) continue;
            if (!(player instanceof OtherClientPlayerEntity)) continue;

            double distSq = self.squaredDistanceTo(player);
            if (distSq <= radiusSq) {
                disconnect(client);
                return true;
            }
        }
        return false;
    }

    static void disconnect(MinecraftClient client) {
        if (client.world != null) {
            client.world.disconnect();
        }
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().getConnection().disconnect(
                    Text.literal("Connection lost")
            );
        }
    }
}
