package com.prisset.vtools.render;

import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.input.PlayerTargetHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class TargetArrowRenderer {

    private static final int ARROW_SIZE = 12;
    private static final int EDGE_PAD = 30;
    private static final String ARROW_CHAR = "\u25b6";

    private TargetArrowRenderer() {}

    public static void renderHud(DrawContext ctx, DisplayPrefs prefs) {
        if (!prefs.isTargetEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        PlayerEntity target = PlayerTargetHandler.findTargetEntity();
        if (target == null) {
            renderNoTargetHud(ctx, mc);
            return;
        }

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = target.getPos();
        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float playerYaw = mc.player.getYaw();
        double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));
        double relAngle = normalizeAngle(angleToTarget - playerYaw);

        float pitch = mc.player.getPitch();
        double dy = targetPos.y - playerPos.y;
        double vertAngle = Math.toDegrees(Math.atan2(dy, Math.max(dist, 0.1)));
        double relVertAngle = normalizeAngle(vertAngle + pitch);

        double screenAngle = Math.toRadians(relAngle);
        double dirX = Math.sin(screenAngle);
        double dirY = -Math.sin(Math.toRadians(relVertAngle));

        double len = Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 0.001) {
            renderTargetInfo(ctx, mc, target, dist, screenW / 2, screenH / 2 - 40);
            return;
        }
        dirX /= len;
        dirY /= len;

        double cx = screenW / 2.0;
        double cy = screenH / 2.0;
        double halfW = cx - EDGE_PAD;
        double halfH = cy - EDGE_PAD;

        double tX, tY;
        if (Math.abs(dirX) * halfH > Math.abs(dirY) * halfW) {
            double scale = halfW / Math.abs(dirX);
            tX = cx + dirX * scale;
            tY = cy + dirY * scale;
        } else {
            double scale = halfH / Math.abs(dirY);
            tX = cx + dirX * scale;
            tY = cy + dirY * scale;
        }

        tY = Math.max(EDGE_PAD, Math.min(screenH - EDGE_PAD, tY));
        tX = Math.max(EDGE_PAD, Math.min(screenW - EDGE_PAD, tX));

        int arrowColor = accentColor(prefs);
        double arrowAngle = Math.atan2(dirY, dirX);
        renderArrowTriangle(ctx, (int) tX, (int) tY, arrowAngle, arrowColor);
        renderTargetInfo(ctx, mc, target, dist, (int) tX, (int) tY);
    }

    private static void renderNoTargetHud(DrawContext ctx, MinecraftClient mc) {
        String targetName = PlayerTargetHandler.getTargetName();
        if (targetName == null) return;

        TextRenderer tr = mc.textRenderer;
        int screenW = mc.getWindow().getScaledWidth();
        String text = targetName + " [?]";
        int tw = tr.getWidth(text);
        ctx.drawTextWithShadow(tr, text, screenW / 2 - tw / 2, 4, 0xFF888888);
    }

    private static void renderTargetInfo(DrawContext ctx, MinecraftClient mc,
                                          PlayerEntity target, double dist,
                                          int x, int y) {
        TextRenderer tr = mc.textRenderer;
        String name = target.getGameProfile().getName();
        float hp = target.getHealth();
        String info = name + " \u00a7c" + String.format("%.0f", hp) + "\u2764 \u00a7f" + (int) dist + "m";
        int tw = tr.getWidth(info);

        int textX = Math.max(2, Math.min(mc.getWindow().getScaledWidth() - tw - 2, x - tw / 2));
        int textY = y + ARROW_SIZE + 4;
        int screenH = mc.getWindow().getScaledHeight();
        if (textY + 10 > screenH) textY = y - ARROW_SIZE - 14;

        ctx.fill(textX - 2, textY - 1, textX + tw + 2, textY + 10, 0xAA000000);
        ctx.drawTextWithShadow(tr, info, textX, textY, 0xFFFF8800);
    }

    private static void renderArrowTriangle(DrawContext ctx, int cx, int cy,
                                             double angle, int color) {
        int tipX = cx + (int) (Math.cos(angle) * ARROW_SIZE);
        int tipY = cy + (int) (Math.sin(angle) * ARROW_SIZE);

        double perpAngle = angle + Math.PI / 2;
        int halfBase = ARROW_SIZE / 3;
        int bx1 = cx + (int) (Math.cos(perpAngle) * halfBase);
        int by1 = cy + (int) (Math.sin(perpAngle) * halfBase);
        int bx2 = cx - (int) (Math.cos(perpAngle) * halfBase);
        int by2 = cy - (int) (Math.sin(perpAngle) * halfBase);

        fillTriangle(ctx, tipX, tipY, bx1, by1, bx2, by2, color);

        for (int i = 2; i >= 1; i--) {
            int glowA = 30 * i;
            int glowColor = (glowA << 24) | (color & 0x00FFFFFF);
            int gtx = cx + (int) (Math.cos(angle) * (ARROW_SIZE + i * 3));
            int gty = cy + (int) (Math.sin(angle) * (ARROW_SIZE + i * 3));
            int gbx1 = cx + (int) (Math.cos(perpAngle) * (halfBase + i));
            int gby1 = cy + (int) (Math.sin(perpAngle) * (halfBase + i));
            int gbx2 = cx - (int) (Math.cos(perpAngle) * (halfBase + i));
            int gby2 = cy - (int) (Math.sin(perpAngle) * (halfBase + i));
            fillTriangle(ctx, gtx, gty, gbx1, gby1, gbx2, gby2, glowColor);
        }
    }

    private static void fillTriangle(DrawContext ctx,
                                      int x1, int y1, int x2, int y2, int x3, int y3,
                                      int color) {
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));

        for (int y = minY; y <= maxY; y++) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;

            int ex = edgeX(x1, y1, x2, y2, y);
            if (ex != Integer.MIN_VALUE) { minX = Math.min(minX, ex); maxX = Math.max(maxX, ex); }
            ex = edgeX(x2, y2, x3, y3, y);
            if (ex != Integer.MIN_VALUE) { minX = Math.min(minX, ex); maxX = Math.max(maxX, ex); }
            ex = edgeX(x3, y3, x1, y1, y);
            if (ex != Integer.MIN_VALUE) { minX = Math.min(minX, ex); maxX = Math.max(maxX, ex); }

            if (minX <= maxX) {
                ctx.fill(minX, y, maxX + 1, y + 1, color);
            }
        }
    }

    private static int edgeX(int x1, int y1, int x2, int y2, int y) {
        if ((y < Math.min(y1, y2)) || (y > Math.max(y1, y2))) return Integer.MIN_VALUE;
        if (y1 == y2) return Math.min(x1, x2);
        return x1 + (x2 - x1) * (y - y1) / (y2 - y1);
    }

    private static double normalizeAngle(double angle) {
        angle = angle % 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }

    private static int accentColor(DisplayPrefs prefs) {
        if (prefs.isMenuRgbMode()) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000f * prefs.getMenuRgbSpeed() % 1f;
            return java.awt.Color.HSBtoRGB(hue, 0.8f, 1f) | 0xFF000000;
        }
        int r = prefs.getMenuR(), g = prefs.getMenuG(), b = prefs.getMenuB();
        if (r + g + b < 60) { r = 80; g = 80; b = 255; }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
