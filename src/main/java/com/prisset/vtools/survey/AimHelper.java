package com.prisset.vtools.survey;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles camera rotation for the survey system.
 * Sends server-side rotation packets (server only checks reach, not look direction,
 * but packets keep the server aware of player facing).
 * Visual rotation lerps smoothly for human appearance.
 */
public final class AimHelper {

    private float targetYaw, targetPitch;
    private boolean hasTarget;

    public AimHelper() {}

    public void aimAt(ClientPlayerEntity player, BlockPos pos) {
        Vec3d eyes = player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        double dx = center.x - eyes.x;
        double dy = center.y - eyes.y;
        double dz = center.z - eyes.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + jitter(0.3f);
        targetPitch = (float) Math.toDegrees(-Math.atan2(dy, horiz)) + jitter(0.2f);
        hasTarget = true;
    }

    public void face(float yaw, float pitch) {
        targetYaw = yaw;
        targetPitch = pitch;
        hasTarget = true;
    }

    /**
     * Sends rotation packet to server (server needs to know we're looking at the block
     * for server validation on some servers) AND smoothly lerps visual rotation.
     */
    public void tick(ClientPlayerEntity player) {
        if (!hasTarget) return;

        // Send server packet with exact target angles
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(
                    targetYaw, targetPitch, player.isOnGround()
                )
            );
        }

        // Visual: fast lerp so it looks natural but not robotic
        float yawDelta = Math.abs(wrap(player.getYaw() - targetYaw));
        float speed = yawDelta > 30f ? 0.4f : 0.6f;
        speed += ThreadLocalRandom.current().nextFloat() * 0.08f;

        player.setYaw(lerpAngle(player.getYaw(), targetYaw, speed));
        player.setPitch(lerp(player.getPitch(), targetPitch, speed));
    }

    /**
     * Returns true when visual rotation is close enough to target.
     */
    public boolean isAimed(ClientPlayerEntity player) {
        if (!hasTarget) return true;
        float ye = Math.abs(wrap(player.getYaw() - targetYaw));
        float pe = Math.abs(player.getPitch() - targetPitch);
        return ye < 5f && pe < 5f;
    }

    public void drift() {
        targetYaw += jitter(1.0f);
        targetPitch += jitter(0.4f);
    }

    public void clear() {
        hasTarget = false;
    }

    // -- math --

    private static float jitter(float range) {
        return (ThreadLocalRandom.current().nextFloat() - 0.5f) * range;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        return a + wrap(b - a) * t;
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }
}
