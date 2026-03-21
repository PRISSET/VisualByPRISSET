package com.prisset.vtools.render;

import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class OverlayPainter {

    private OverlayPainter() {}

    public static void paint(Entity entity, float tickDelta,
                              MatrixStack matrices,
                              VertexConsumerProvider vertices,
                              DisplayPrefs prefs) {

        if (!matchesFilter(entity, prefs)) return;

        Box bounds = entity.getBoundingBox()
            .offset(-entity.getX(), -entity.getY(), -entity.getZ());

        double origW = bounds.maxX - bounds.minX;
        double origH = bounds.maxY - bounds.minY;
        double origD = bounds.maxZ - bounds.minZ;

        double w = origW * prefs.getHScale();
        double h;
        if (prefs.getFixedV() > 0) {
            h = prefs.getFixedV();
        } else {
            h = origH * prefs.getVScale();
        }
        double d = origD * prefs.getHScale();

        Box scaled = new Box(-w / 2.0, 0, -d / 2.0, w / 2.0, h, d / 2.0);

        float r = prefs.getTintR() / 255f;
        float g = prefs.getTintG() / 255f;
        float b = prefs.getTintB() / 255f;
        float a = prefs.getTintA() / 255f;

        VertexConsumer buffer = vertices.getBuffer(RenderLayer.getLines());
        WorldRenderer.drawBox(matrices, buffer, scaled, r, g, b, a);

        // eye-level indicator line
        float eyeY = entity.getStandingEyeHeight();
        if (eyeY > 0 && eyeY < h) {
            drawLine(matrices, buffer,
                -w / 2.0, eyeY, 0,
                 w / 2.0, eyeY, 0,
                1.0f, 0.0f, 0.0f, 0.6f);
        }
    }

    private static boolean matchesFilter(Entity entity, DisplayPrefs prefs) {
        if (entity instanceof PlayerEntity) return prefs.isFilterPlayers();
        if (entity instanceof MobEntity)    return prefs.isFilterMobs();
        if (entity instanceof ItemEntity)   return prefs.isFilterDrops();
        return true;
    }

    private static void drawLine(MatrixStack matrices, VertexConsumer buffer,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  float r, float g, float b, float a) {
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f norm = matrices.peek().getNormalMatrix();

        buffer.vertex(pos, (float) x1, (float) y1, (float) z1)
            .color(r, g, b, a)
            .normal(norm, 0, 1, 0)
            .next();
        buffer.vertex(pos, (float) x2, (float) y2, (float) z2)
            .color(r, g, b, a)
            .normal(norm, 0, 1, 0)
            .next();
    }
}
