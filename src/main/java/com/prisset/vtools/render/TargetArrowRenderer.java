package com.prisset.vtools.render;

import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.input.PlayerTargetHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class TargetArrowRenderer {

    private static final int ARROW_SIZE = 12;
    private static final int EDGE_PAD = 30;

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

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();
        float camYaw = camera.getYaw();
        float camPitch = camera.getPitch();

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        float td = mc.getTickDelta();
        double tx = target.prevX + (target.getX() - target.prevX) * td;
        double ty = target.prevY + (target.getY() - target.prevY) * td + target.getHeight() / 2.0;
        double tz = target.prevZ + (target.getZ() - target.prevZ) * td;

        double dx = tx - camPos.x;
        double dy = ty - camPos.y;
        double dz = tz - camPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double yawRad = Math.toRadians(camYaw);
        double pitchRad = Math.toRadians(camPitch);

        double cy = Math.cos(yawRad);
        double sy = Math.sin(yawRad);
        double cp = Math.cos(pitchRad);
        double sp = Math.sin(pitchRad);

        double rx = dx * cy - dz * sy;
        double rz = dx * sy + dz * cy;

        double ry = dy * cp + rz * sp;
        double rz2 = -dy * sp + rz * cp;

        double fov = Math.toRadians(mc.options.getFov().getValue());
        double halfTan = Math.tan(fov / 2.0);
        double aspect = (double) screenW / screenH;

        if (rz2 > 0.01) {
            double ndcX = rx / (rz2 * halfTan * aspect);
            double ndcY = -ry / (rz2 * halfTan);

            double px = (ndcX * 0.5 + 0.5) * screenW;
            double py = (ndcY * 0.5 + 0.5) * screenH;

            if (px >= -10 && px <= screenW + 10 && py >= -10 && py <= screenH + 10) {
                renderTargetInfo(ctx, mc, target, dist, (int) px, (int) py - 20);
                return;
            }
        }

        double sx, ssy;
        if (rz2 > 0.01) {
            sx = rx;
            ssy = -ry;
        } else {
            sx = -rx;
            ssy = ry;
        }

        double len = Math.sqrt(sx * sx + ssy * ssy);
        if (len < 0.001) {
            sx = 0;
            ssy = 1;
            len = 1;
        }
        double dirX = sx / len;
        double dirY = ssy / len;

        double cxs = screenW / 2.0;
        double cys = screenH / 2.0;
        double padW = cxs - EDGE_PAD;
        double padH = cys - EDGE_PAD;

        double posX, posY;
        if (Math.abs(dirX) * padH > Math.abs(dirY) * padW) {
            double scale = padW / Math.abs(dirX);
            posX = cxs + dirX * scale;
            posY = cys - dirY * scale;
        } else {
            double scale = padH / Math.abs(dirY);
            posX = cxs + dirX * scale;
            posY = cys - dirY * scale;
        }

        posX = Math.max(EDGE_PAD, Math.min(screenW - EDGE_PAD, posX));
        posY = Math.max(EDGE_PAD, Math.min(screenH - EDGE_PAD, posY));

        int arrowColor = accentColor(prefs);
        double arrowAngle = Math.atan2(-(posY - cys), posX - cxs);
        renderArrowTriangle(ctx, (int) posX, (int) posY, arrowAngle, arrowColor);
        renderTargetInfo(ctx, mc, target, dist, (int) posX, (int) posY);
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

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        int textX = Math.max(2, Math.min(screenW - tw - 2, x - tw / 2));
        int textY = y + ARROW_SIZE + 4;
        if (textY + 10 > screenH) textY = y - ARROW_SIZE - 14;

        ctx.fill(textX - 2, textY - 1, textX + tw + 2, textY + 10, 0xAA000000);
        ctx.drawTextWithShadow(tr, info, textX, textY, 0xFFFF8800);
    }

    private static void renderArrowTriangle(DrawContext ctx, int cx, int cy,
                                             double angle, int color) {
        int tipX = cx + (int) (Math.cos(angle) * ARROW_SIZE);
        int tipY = cy - (int) (Math.sin(angle) * ARROW_SIZE);

        double perpAngle = angle + Math.PI / 2;
        int halfBase = ARROW_SIZE / 3;
        int bx1 = cx + (int) (Math.cos(perpAngle) * halfBase);
        int by1 = cy - (int) (Math.sin(perpAngle) * halfBase);
        int bx2 = cx - (int) (Math.cos(perpAngle) * halfBase);
        int by2 = cy + (int) (Math.sin(perpAngle) * halfBase);

        fillTriangle(ctx, tipX, tipY, bx1, by1, bx2, by2, color);

        for (int i = 2; i >= 1; i--) {
            int glowA = 30 * i;
            int glowColor = (glowA << 24) | (color & 0x00FFFFFF);
            int gtx = cx + (int) (Math.cos(angle) * (ARROW_SIZE + i * 3));
            int gty = cy - (int) (Math.sin(angle) * (ARROW_SIZE + i * 3));
            int gbx1 = cx + (int) (Math.cos(perpAngle) * (halfBase + i));
            int gby1 = cy - (int) (Math.sin(perpAngle) * (halfBase + i));
            int gbx2 = cx - (int) (Math.cos(perpAngle) * (halfBase + i));
            int gby2 = cy + (int) (Math.sin(perpAngle) * (halfBase + i));
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
