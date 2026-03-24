package com.prisset.vtools.render;

import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.config.ProfileIndex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.awt.Color;

public final class OverlayPainter {

    private static final String HEART_FULL = "\u2764";
    private static final String HEART_HALF = "\u2665";

    private OverlayPainter() {}

    public static void paintAll(MinecraftClient client, MatrixStack matrices,
                                 float tickDelta, Camera camera, DisplayPrefs prefs) {
        if (client.world == null) return;

        Vec3d camPos = camera.getPos();

        for (Entity entity : client.world.getEntities()) {
            if (!matchesFilter(entity, prefs)) continue;
            if (entity == camera.getFocusedEntity() && !camera.isThirdPerson()) continue;

            paintEntity(entity, tickDelta, matrices, camPos, prefs, camera);
        }
    }

    private static void paintEntity(Entity entity, float tickDelta,
                                     MatrixStack matrices, Vec3d camPos,
                                     DisplayPrefs prefs, Camera camera) {

        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - camPos.x;
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - camPos.y;
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - camPos.z;

        double drawW = entity.getWidth();
        double drawH = entity.getHeight();

        float r, g, b;
        float a = prefs.getTintA() / 255f;

        boolean isTeammate = (entity instanceof PlayerEntity player)
            && ProfileIndex.get().isTeammate(player.getGameProfile().getName());

        if (isTeammate) {
            r = 0f; g = 1f; b = 0f;
        } else if (prefs.isRgbMode()) {
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

        VertexConsumerProvider.Immediate immediate =
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        if (prefs.isHitboxEnabled()) {
            Box box = new Box(
                -drawW / 2.0, 0, -drawW / 2.0,
                 drawW / 2.0, drawH, drawW / 2.0
            );

            VertexConsumer lineBuffer = immediate.getBuffer(RenderLayer.getLines());

            WorldRenderer.drawBox(matrices, lineBuffer, box, r, g, b, a);

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
        }

        // Health hearts above head
        if (prefs.isHealthBars() && entity instanceof LivingEntity living) {
            renderHearts(living, matrices, immediate, camera);
        }

        matrices.pop();
    }

    private static void renderHearts(LivingEntity entity, MatrixStack matrices,
                                      VertexConsumerProvider.Immediate immediate,
                                      Camera camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();

        float health = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        if (maxHealth <= 0) return;

        // Build hearts string: each heart = 2 HP
        int totalHearts = Math.min((int) Math.ceil(maxHealth / 2.0f), 10);
        float halfHearts = health;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < totalHearts; i++) {
            if (halfHearts >= 2.0f) {
                sb.append("\u00a7c").append(HEART_FULL);
                halfHearts -= 2.0f;
            } else if (halfHearts >= 1.0f) {
                sb.append("\u00a76").append(HEART_FULL);
                halfHearts -= 1.0f;
            } else {
                sb.append("\u00a78").append(HEART_FULL);
            }
        }

        // HP number
        String hpText = String.format(" \u00a7f%.0f", health);
        String fullText = sb.toString() + hpText;
        Text text = Text.literal(fullText);

        float labelHeight = entity.getHeight() + 0.5f;

        matrices.push();
        matrices.translate(0.0f, labelHeight, 0.0f);
        matrices.multiply(dispatcher.getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);

        float textWidth = textRenderer.getWidth(text);
        float textX = -textWidth / 2.0f;

        // Background
        int bgColor = 0x40000000;
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();

        textRenderer.draw(text, textX, 0, 0xFFFFFFFF, false,
            posMatrix, immediate, TextRenderer.TextLayerType.NORMAL,
            bgColor, 0xF000F0);

        // Draw again fully bright on top (no shadow, see-through)
        textRenderer.draw(text, textX, 0, 0xFFFFFFFF, false,
            posMatrix, immediate, TextRenderer.TextLayerType.SEE_THROUGH,
            0, 0xF000F0);

        immediate.draw();
        matrices.pop();
    }

    private static boolean matchesFilter(Entity entity, DisplayPrefs prefs) {
        if (entity instanceof PlayerEntity) return prefs.isFilterPlayers();
        if (entity instanceof MobEntity)    return prefs.isFilterMobs();
        if (entity instanceof ItemEntity)   return prefs.isFilterDrops();
        if (entity instanceof ProjectileEntity) return prefs.isFilterProjectiles();
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
