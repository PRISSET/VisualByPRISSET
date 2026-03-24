package com.prisset.vtools.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.*;

public final class TrailRenderer {

    private static final Map<Integer, List<TrailPoint>> TRAILS = new HashMap<>();
    private static final double MIN_DIST_SQ = 0.02;
    private static final long MAX_AGE_MS = 500L;
    private static final BufferBuilder BUILDER = new BufferBuilder(1024);

    private TrailRenderer() {}

    private static final class TrailPoint {
        final float x, y, z, h;
        final long time;

        TrailPoint(float x, float y, float z, float h, long time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.h = h;
            this.time = time;
        }
    }

    public static void tick(MinecraftClient client, DisplayPrefs prefs) {
        if (client.world == null || !prefs.isTrailEnabled()) {
            if (!TRAILS.isEmpty()) TRAILS.clear();
            return;
        }

        long now = System.currentTimeMillis();
        int maxPoints = prefs.getTrailLength();
        Set<Integer> alive = new HashSet<>();

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof PlayerEntity)) continue;

            int id = entity.getId();
            alive.add(id);

            List<TrailPoint> trail = TRAILS.computeIfAbsent(id, k -> new ArrayList<>());

            while (!trail.isEmpty() && (now - trail.get(0).time) > MAX_AGE_MS) {
                trail.remove(0);
            }

            Vec3d pos = entity.getPos();
            float height = entity.getHeight();

            boolean shouldAdd = trail.isEmpty();
            if (!shouldAdd) {
                TrailPoint last = trail.get(trail.size() - 1);
                double dx = pos.x - last.x;
                double dy = pos.y - last.y;
                double dz = pos.z - last.z;
                shouldAdd = (dx * dx + dy * dy + dz * dz) > MIN_DIST_SQ;
            }

            if (shouldAdd) {
                trail.add(new TrailPoint((float) pos.x, (float) pos.y, (float) pos.z, height, now));
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
        if (TRAILS.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        boolean firstPerson = !camera.isThirdPerson();
        int selfId = client.player != null ? client.player.getId() : -1;

        BUILDER.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        boolean hasQuads = false;
        for (Map.Entry<Integer, List<TrailPoint>> entry : TRAILS.entrySet()) {
            List<TrailPoint> trail = entry.getValue();
            if (trail.size() < 2) continue;

            int entityId = entry.getKey();
            if (firstPerson && entityId == selfId) continue;

            hasQuads |= buildQuads(trail, entityId, BUILDER, camPos, prefs, posMatrix);
        }

        if (!hasQuads) {
            BUILDER.end();
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest();

        BufferRenderer.drawWithGlobalProgram(BUILDER.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean buildQuads(List<TrailPoint> trail, int entityId,
                                       BufferBuilder builder, Vec3d camPos,
                                       DisplayPrefs prefs, Matrix4f posMatrix) {
        int size = trail.size();
        boolean drew = false;
        long now = System.currentTimeMillis();

        for (int i = 0; i < size - 1; i++) {
            TrailPoint a = trail.get(i);
            TrailPoint b = trail.get(i + 1);

            float age = (now - a.time) / (float) MAX_AGE_MS;
            float alpha = Math.max(0.0f, (1.0f - age) * 0.5f);
            if (alpha < 0.01f) continue;

            float r, g, bl;
            if (prefs.isRgbMode()) {
                float offset = (entityId * 0.12f) % 1.0f;
                float hue = ((now % 10000L) / 10000f * prefs.getRgbSpeed()
                    + offset + age * 0.3f) % 1.0f;
                int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
                r = ((rgb >> 16) & 0xFF) / 255f;
                g = ((rgb >> 8) & 0xFF) / 255f;
                bl = (rgb & 0xFF) / 255f;
            } else {
                r = prefs.getTintR() / 255f;
                g = prefs.getTintG() / 255f;
                bl = prefs.getTintB() / 255f;
            }

            float ax = a.x - (float) camPos.x;
            float ay = a.y - (float) camPos.y;
            float az = a.z - (float) camPos.z;

            float bx = b.x - (float) camPos.x;
            float by = b.y - (float) camPos.y;
            float bz = b.z - (float) camPos.z;

            builder.vertex(posMatrix, ax, ay, az).color(r, g, bl, alpha).next();
            builder.vertex(posMatrix, bx, by, bz).color(r, g, bl, alpha).next();
            builder.vertex(posMatrix, bx, by + b.h, bz).color(r, g, bl, alpha).next();
            builder.vertex(posMatrix, ax, ay + a.h, az).color(r, g, bl, alpha).next();

            drew = true;
        }

        return drew;
    }

    public static void clear() {
        TRAILS.clear();
    }
}
