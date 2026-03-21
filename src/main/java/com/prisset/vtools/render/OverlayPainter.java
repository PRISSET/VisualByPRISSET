package com.prisset.vtools.render;

import com.mojang.blaze3d.systems.RenderSystem;
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

public final class OverlayPainter {

    private OverlayPainter() {}

    public static void paintAll(MinecraftClient client, MatrixStack matrices,
                                 float tickDelta, Camera camera, DisplayPrefs prefs) {
        if (client.world == null) return;

        Vec3d camPos = camera.getPos();

        // Setup render state for lines
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        RenderSystem.lineWidth(2.0f);

        for (Entity entity : client.world.getEntities()) {
            if (!matchesFilter(entity, prefs)) continue;
            if (entity == camera.getFocusedEntity() && !camera.isThirdPerson()) continue;

            paintEntity(entity, tickDelta, matrices, camPos, prefs);
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    private static void paintEntity(Entity entity, float tickDelta,
                                     MatrixStack matrices, Vec3d camPos,
                                     DisplayPrefs prefs) {

        // Interpolated position
        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - camPos.x;
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - camPos.y;
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - camPos.z;

        Box bounds = entity.getBoundingBox();
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

        // Stealth mode: use original dimensions for visual, but the actual box is expanded
        // (server-side expansion is separate; this just controls visual)
        boolean stealth = prefs.isStealth();
        double drawW = stealth ? origW : w;
        double drawH = stealth ? origH : h;
        double drawD = stealth ? origD : d;

        float r = prefs.getTintR() / 255f;
        float g = prefs.getTintG() / 255f;
        float b = prefs.getTintB() / 255f;
        float a = prefs.getTintA() / 255f;

        matrices.push();
        matrices.translate(x, y, z);

        Box box = new Box(
            -drawW / 2.0, 0, -drawD / 2.0,
             drawW / 2.0, drawH, drawD / 2.0
        );

        // Draw the main box
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

        // View direction line for living entities
        if (entity instanceof LivingEntity living) {
            float eyeY = living.getStandingEyeHeight();
            float yaw = living.getYaw(tickDelta);
            float pitch = living.getPitch(tickDelta);
            float radYaw = -yaw * ((float) Math.PI / 180f);
            float radPitch = pitch * ((float) Math.PI / 180f);
            float lookX = (float)(Math.sin(radYaw) * Math.cos(radPitch)) * 2f;
            float lookY = (float)(-Math.sin(radPitch)) * 2f;
            float lookZ = (float)(Math.cos(radYaw) * Math.cos(radPitch)) * 2f;
            drawLine(matrices, lineBuffer,
                0, eyeY, 0,
                lookX, eyeY + lookY, lookZ,
                0.0f, 0.0f, 1.0f, 0.8f);
        }

        immediate.draw();
        matrices.pop();
    }

    private static boolean matchesFilter(Entity entity, DisplayPrefs prefs) {
        if (entity instanceof PlayerEntity) return prefs.isFilterPlayers();
        if (entity instanceof MobEntity)    return prefs.isFilterMobs();
        if (entity instanceof ItemEntity)   return prefs.isFilterDrops();
        return prefs.isFilterMobs();
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
