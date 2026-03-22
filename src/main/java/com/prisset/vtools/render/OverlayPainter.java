package com.prisset.vtools.render;

import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.awt.Color;

public final class OverlayPainter {

    private OverlayPainter() {}

    public static void paintAll(MinecraftClient client, MatrixStack matrices,
                                 float tickDelta, Camera camera, DisplayPrefs prefs) {
        if (client.world == null) return;

        Vec3d camPos = camera.getPos();

        for (Entity entity : client.world.getEntities()) {
            if (!matchesFilter(entity, prefs)) continue;
            if (entity == camera.getFocusedEntity() && !camera.isThirdPerson()) continue;

            paintEntity(entity, tickDelta, matrices, camPos, prefs);
        }
    }

    private static void paintEntity(Entity entity, float tickDelta,
                                     MatrixStack matrices, Vec3d camPos,
                                     DisplayPrefs prefs) {

        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - camPos.x;
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - camPos.y;
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - camPos.z;

        double origW = entity.getWidth();
        double origH = entity.getHeight();

        double drawW = origW * prefs.getHScale();
        double drawH = (prefs.getFixedV() > 0) ? prefs.getFixedV() : origH * prefs.getVScale();

        float r, g, b;
        float a = prefs.getTintA() / 255f;

        if (prefs.isRgbMode()) {
            float entityOffset = (entity.getId() * 0.12f) % 1.0f;
            float hue = ((System.currentTimeMillis() % 10000L) / 10000f * prefs.getRgbSpeed() + entityOffset) % 1.0f;
            int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
            r = ((rgb >> 16) & 0xFF) / 255f;
            g = ((rgb >> 8) & 0xFF) / 255f;
            b = (rgb & 0xFF) / 255f;
        } else {
            r = prefs.getTintR() / 255f;
            g = prefs.getTintG() / 255f;
            b = prefs.getTintB() / 255f;
        }

        matrices.push();
        matrices.translate(x, y, z);

        Box box = new Box(
            -drawW / 2.0, 0, -drawW / 2.0,
             drawW / 2.0, drawH, drawW / 2.0
        );

        VertexConsumerProvider.Immediate immediate =
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lineBuffer = immediate.getBuffer(RenderLayer.getLines());

        WorldRenderer.drawBox(matrices, lineBuffer, box, r, g, b, a);

        // Eye line for living entities
        if (entity instanceof LivingEntity living) {
            float eyeY = living.getStandingEyeHeight();
            if (eyeY > 0 && eyeY < drawH) {
                drawLine(matrices, lineBuffer,
                    (float)(-drawW / 2.0), eyeY, 0,
                    (float)(drawW / 2.0), eyeY, 0,
                    1.0f, 0.0f, 0.0f, 0.8f);
            }
        }

        immediate.draw();
        matrices.pop();
    }

    private static boolean matchesFilter(Entity entity, DisplayPrefs prefs) {
        if (entity instanceof PlayerEntity) return prefs.isFilterPlayers();
        if (entity instanceof MobEntity)    return prefs.isFilterMobs();
        if (entity instanceof ItemEntity)   return prefs.isFilterDrops();
        return false;
    }

    private static void drawLine(MatrixStack matrices, VertexConsumer buffer,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float r, float g, float b, float a) {
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f norm = matrices.peek().getNormalMatrix();
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001f) return;
        dx /= len; dy /= len; dz /= len;

        buffer.vertex(pos, x1, y1, z1).color(r, g, b, a).normal(norm, dx, dy, dz).next();
        buffer.vertex(pos, x2, y2, z2).color(r, g, b, a).normal(norm, dx, dy, dz).next();
    }
}
