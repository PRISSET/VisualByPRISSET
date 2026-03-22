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

    private static final Map<Integer, List<float[]>> TRAILS = new HashMap<>();
    private static final double MIN_DIST_SQ = 0.02;

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

            int id = entity.getId();
            alive.add(id);

            List<float[]> trail = TRAILS.computeIfAbsent(id, k -> new ArrayList<>());
            Vec3d pos = entity.getPos();
            float height = entity.getHeight();

            boolean shouldAdd = trail.isEmpty();
            if (!shouldAdd) {
                float[] last = trail.get(trail.size() - 1);
                double dx = pos.x - last[0];
                double dy = pos.y - last[1];
                double dz = pos.z - last[2];
                shouldAdd = (dx * dx + dy * dy + dz * dz) > MIN_DIST_SQ;
            }

            if (shouldAdd) {
                trail.add(new float[]{ (float) pos.x, (float) pos.y, (float) pos.z, height });
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

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableDepthTest();

        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        boolean hasQuads = false;

        for (Map.Entry<Integer, List<float[]>> entry : TRAILS.entrySet()) {
            List<float[]> trail = entry.getValue();
            if (trail.size() < 2) continue;

            int entityId = entry.getKey();
            hasQuads |= buildQuads(trail, entityId, builder, camPos, prefs, posMatrix);
        }

        if (hasQuads) {
            BufferRenderer.drawWithGlobalProgram(builder.end());
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean buildQuads(List<float[]> trail, int entityId,
                                       BufferBuilder builder, Vec3d camPos,
                                       DisplayPrefs prefs, Matrix4f posMatrix) {
        int size = trail.size();
        boolean drew = false;

        for (int i = 0; i < size - 1; i++) {
            float[] a = trail.get(i);
            float[] b = trail.get(i + 1);

            float progress = (float) i / (size - 1);
            float alpha = progress * 0.45f + 0.05f;

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

            float ax = a[0] - (float) camPos.x;
            float ay = a[1] - (float) camPos.y;
            float az = a[2] - (float) camPos.z;
            float ah = a[3];

            float bx = b[0] - (float) camPos.x;
            float by = b[1] - (float) camPos.y;
            float bz = b[2] - (float) camPos.z;
            float bh = b[3];

            // Quad: bottom-a -> bottom-b -> top-b -> top-a
            builder.vertex(posMatrix, ax, ay, az).color(r, g, bl, alpha).next();
            builder.vertex(posMatrix, bx, by, bz).color(r, g, bl, alpha).next();
            builder.vertex(posMatrix, bx, by + bh, bz).color(r, g, bl, alpha).next();
            builder.vertex(posMatrix, ax, ay + ah, az).color(r, g, bl, alpha).next();

            drew = true;
        }

        return drew;
    }

    public static void clear() {
        TRAILS.clear();
    }
}
