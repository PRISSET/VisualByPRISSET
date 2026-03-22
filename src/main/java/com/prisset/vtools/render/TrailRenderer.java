package com.prisset.vtools.render;

import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.*;

public final class TrailRenderer {

    private static final Map<Integer, List<Vec3d>> TRAILS = new HashMap<>();
    private static final double MIN_DIST_SQ = 0.01;

    private TrailRenderer() {}

    public static void tick(MinecraftClient client, DisplayPrefs prefs) {
        if (client.world == null || !prefs.isTrailEnabled()) {
            if (!TRAILS.isEmpty()) TRAILS.clear();
            return;
        }

        int maxPoints = prefs.getTrailLength();
        Set<Integer> alive = new HashSet<>();

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof PlayerEntity)) continue;
            if (entity == client.player) continue;

            int id = entity.getId();
            alive.add(id);

            List<Vec3d> trail = TRAILS.computeIfAbsent(id, k -> new ArrayList<>());
            Vec3d pos = entity.getPos();

            if (trail.isEmpty() || trail.get(trail.size() - 1).squaredDistanceTo(pos) > MIN_DIST_SQ) {
                trail.add(pos);
                while (trail.size() > maxPoints) {
                    trail.remove(0);
                }
            }
        }

        TRAILS.keySet().removeIf(id -> !alive.contains(id));
    }

    public static void render(MinecraftClient client, MatrixStack matrices,
                               float tickDelta, Camera camera, DisplayPrefs prefs) {
        if (client.world == null || !prefs.isTrailEnabled()) return;

        Vec3d camPos = camera.getPos();

        VertexConsumerProvider.Immediate immediate =
            client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer buffer = immediate.getBuffer(RenderLayer.getLines());

        for (Map.Entry<Integer, List<Vec3d>> entry : TRAILS.entrySet()) {
            List<Vec3d> trail = entry.getValue();
            if (trail.size() < 2) continue;

            int entityId = entry.getKey();
            renderTrail(trail, entityId, matrices, camPos, buffer, prefs);
        }

        immediate.draw();
    }

    private static void renderTrail(List<Vec3d> trail, int entityId,
                                     MatrixStack matrices, Vec3d camPos,
                                     VertexConsumer buffer, DisplayPrefs prefs) {
        matrices.push();
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f norm = matrices.peek().getNormalMatrix();

        int size = trail.size();

        for (int i = 0; i < size - 1; i++) {
            Vec3d a = trail.get(i);
            Vec3d b = trail.get(i + 1);

            float progress = (float) i / (size - 1);
            float alpha = progress * 0.9f + 0.1f;

            float r, g, bl;
            if (prefs.isRgbMode()) {
                float offset = (entityId * 0.12f) % 1.0f;
                float hue = ((System.currentTimeMillis() % 10000L) / 10000f * prefs.getRgbSpeed()
                    + offset + progress * 0.3f) % 1.0f;
                int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
                r = ((rgb >> 16) & 0xFF) / 255f;
                g = ((rgb >> 8) & 0xFF) / 255f;
                bl = (rgb & 0xFF) / 255f;
            } else {
                r = prefs.getTintR() / 255f;
                g = prefs.getTintG() / 255f;
                bl = prefs.getTintB() / 255f;
            }

            float x1 = (float)(a.x - camPos.x);
            float y1 = (float)(a.y - camPos.y) + 0.1f;
            float z1 = (float)(a.z - camPos.z);
            float x2 = (float)(b.x - camPos.x);
            float y2 = (float)(b.y - camPos.y) + 0.1f;
            float z2 = (float)(b.z - camPos.z);

            float dx = x2 - x1;
            float dy = y2 - y1;
            float dz = z2 - z1;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.001f) continue;
            dx /= len; dy /= len; dz /= len;

            buffer.vertex(pos, x1, y1, z1).color(r, g, bl, alpha).normal(norm, dx, dy, dz).next();
            buffer.vertex(pos, x2, y2, z2).color(r, g, bl, alpha).normal(norm, dx, dy, dz).next();
        }

        matrices.pop();
    }

    public static void clear() {
        TRAILS.clear();
    }
}
