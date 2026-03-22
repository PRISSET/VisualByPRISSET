package com.prisset.vtools.gui;

import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PrefsScreen extends Screen {

    private static final int W = 240;
    private static final int PAD = 8;
    private static final int ROW_H = 16;
    private static final int LABEL_H = 18;
    private static final int SLIDER_LABEL_W = 80;

    private final DisplayPrefs prefs;
    private final List<Row> rows = new ArrayList<>();
    private int wx, wy, totalH;
    private Row drag;
    private long openTime;

    public PrefsScreen(DisplayPrefs prefs) {
        super(Text.literal("PRISSET"));
        this.prefs = prefs;
    }

    @Override
    protected void init() {
        openTime = System.currentTimeMillis();
        drag = null;
        rows.clear();

        rows.add(new Label("\u041e\u0412\u0415\u0420\u041b\u0415\u0419"));
        rows.add(new Toggle("\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c", prefs::isActive, v -> prefs.setActive(v)));
        rows.add(new Toggle("\u0418\u0433\u0440\u043e\u043a\u0438", prefs::isFilterPlayers, v -> prefs.setFilterPlayers(v)));
        rows.add(new Toggle("\u041c\u043e\u0431\u044b", prefs::isFilterMobs, v -> prefs.setFilterMobs(v)));
        rows.add(new Toggle("\u041f\u0440\u0435\u0434\u043c\u0435\u0442\u044b", prefs::isFilterDrops, v -> prefs.setFilterDrops(v)));
        rows.add(new Toggle("\u0421\u043d\u0430\u0440\u044f\u0434\u044b", prefs::isFilterProjectiles, v -> prefs.setFilterProjectiles(v)));

        rows.add(new Label("\u0426\u0412\u0415\u0422"));
        rows.add(new Slider("\u041a\u0440\u0430\u0441\u043d\u044b\u0439", 0, 255, prefs.getTintR(), v -> prefs.setTintR(v.intValue())));
        rows.add(new Slider("\u0417\u0435\u043b\u0451\u043d\u044b\u0439", 0, 255, prefs.getTintG(), v -> prefs.setTintG(v.intValue())));
        rows.add(new Slider("\u0421\u0438\u043d\u0438\u0439", 0, 255, prefs.getTintB(), v -> prefs.setTintB(v.intValue())));
        rows.add(new Slider("\u041d\u0435\u043f\u0440\u043e\u0437\u0440\u0430\u0447\u043d.", 0, 255, prefs.getTintA(), v -> prefs.setTintA(v.intValue())));

        rows.add(new Label("\u042d\u0424\u0424\u0415\u041a\u0422\u042b"));
        rows.add(new Toggle("RGB", prefs::isRgbMode, v -> prefs.setRgbMode(v)));
        rows.add(new Slider("RGB \u0441\u043a\u043e\u0440.", 0.1f, 5f, prefs.getRgbSpeed(), v -> prefs.setRgbSpeed(v.floatValue())));
        rows.add(new Toggle("\u0417\u0434\u043e\u0440\u043e\u0432\u044c\u0435", prefs::isHealthBars, v -> prefs.setHealthBars(v)));
        rows.add(new Slider("\u0417\u0443\u043c (C)", 1.5f, 10f, prefs.getZoomStrength(), v -> prefs.setZoomStrength(v.floatValue())));
        rows.add(new Toggle("\u0421\u043b\u0435\u0434", prefs::isTrailEnabled, v -> prefs.setTrailEnabled(v)));
        rows.add(new Slider("\u0414\u043b\u0438\u043d\u0430", 5, 40, prefs.getTrailLength(), v -> prefs.setTrailLength(v.intValue())));

        totalH = PAD + 14;
        for (Row r : rows) totalH += r instanceof Label ? LABEL_H : ROW_H;
        totalH += PAD;

        wx = (width - W) / 2;
        wy = (height - totalH) / 2;
    }

    private int neon() {
        if (prefs.isRgbMode()) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000f * prefs.getRgbSpeed() % 1f;
            return java.awt.Color.HSBtoRGB(hue, 0.8f, 1f) | 0xFF000000;
        }
        int r = prefs.getTintR(), g = prefs.getTintG(), b = prefs.getTintB();
        if (r + g + b < 60) { r = 80; g = 80; b = 255; }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int nr(int c) { return (c >> 16) & 0xFF; }
    private static int ng(int c) { return (c >> 8) & 0xFF; }
    private static int nb(int c) { return c & 0xFF; }
    private static int rgba(int r, int g, int b, int a) { return (a << 24) | (r << 16) | (g << 8) | b; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        int nc = neon();
        int nR = nr(nc), nG = ng(nc), nB = nb(nc);
        float pulse = (float)(Math.sin(now / 500.0) * 0.12 + 0.88);

        // Blur-like background: multiple semi-transparent dark layers
        ctx.fill(0, 0, width, height, 0x90000000);
        ctx.fill(0, 0, width, height, 0x40000000);

        // Outer glow (6 layers, fading outward)
        for (int i = 6; i >= 1; i--) {
            int a = (int)(pulse * (6 + (6 - i) * 4));
            ctx.fill(wx - i, wy - i, wx + W + i, wy + totalH + i, rgba(nR, nG, nB, a));
        }

        // Black panel
        ctx.fill(wx, wy, wx + W, wy + totalH, 0xFF000000);

        // Border 1px neon
        int ba = (int)(pulse * 180);
        int bc = rgba(nR, nG, nB, ba);
        ctx.fill(wx, wy, wx + W, wy + 1, bc);
        ctx.fill(wx, wy + totalH - 1, wx + W, wy + totalH, bc);
        ctx.fill(wx, wy + 1, wx + 1, wy + totalH - 1, bc);
        ctx.fill(wx + W - 1, wy + 1, wx + W, wy + totalH - 1, bc);

        // Top line brighter
        ctx.fill(wx + 1, wy, wx + W - 1, wy + 1, rgba(nR, nG, nB, (int)(pulse * 255)));

        // Title centered
        String title = "PRISSET";
        int tw = textRenderer.getWidth(title);
        ctx.drawTextWithShadow(textRenderer, title, wx + (W - tw) / 2, wy + 3, rgba(nR, nG, nB, 255));

        // Color swatch next to title
        int sc = (prefs.getTintA() << 24) | (prefs.getTintR() << 16) | (prefs.getTintG() << 8) | prefs.getTintB();
        ctx.fill(wx + W - 14, wy + 3, wx + W - 4, wy + 11, sc);

        // Rows
        int y = wy + PAD + 14;
        for (Row r : rows) {
            int rh = r instanceof Label ? LABEL_H : ROW_H;
            r.rx = wx + PAD;
            r.ry = y;
            r.rw = W - PAD * 2;
            r.rh = rh;

            if (!(r instanceof Label)) {
                boolean hov = r.contains(mx, my);
                if (hov) {
                    ctx.fill(r.rx, r.ry, r.rx + r.rw, r.ry + r.rh, rgba(nR, nG, nB, 14));
                }
                // Bottom separator
                ctx.fill(r.rx, r.ry + r.rh - 1, r.rx + r.rw, r.ry + r.rh, 0xFF0E0E10);
            }

            r.render(ctx, textRenderer, mx, my, nc);
            y += rh;
        }
    }

    // === INPUT ===

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        for (Row r : rows) {
            if (!(r instanceof Label) && r.contains((int) mx, (int) my)) {
                r.onClick((int) mx);
                if (r instanceof Slider) drag = r;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (drag != null) { drag.onDrag((int) mx); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        drag = null;
        return super.mouseReleased(mx, my, btn);
    }

    @Override public void close() { prefs.save(); if (client != null) client.setScreen(null); }
    @Override public boolean shouldPause() { return false; }

    // === ROW TYPES ===

    static abstract class Row {
        int rx, ry, rw, rh;
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {}
        void onClick(int mx) {}
        void onDrag(int mx) {}
        boolean contains(int mx, int my) { return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh; }
    }

    // Section header
    static class Label extends Row {
        final String text;
        Label(String t) { text = t; }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {
            int ar = nr(nc) / 2, ag = ng(nc) / 2, ab = nb(nc) / 2;
            ctx.drawTextWithShadow(tr, text, rx, ry + 8, rgba(ar, ag, ab, 255));
            int lw = tr.getWidth(text);
            ctx.fill(rx + lw + 6, ry + 12, rx + rw, ry + 13, 0xFF111114);
        }

        @Override boolean contains(int mx, int my) { return false; }
    }

    // Toggle with circle indicator
    static class Toggle extends Row {
        final String label;
        final Supplier<Boolean> get;
        final Consumer<Boolean> set;

        Toggle(String l, Supplier<Boolean> g, Consumer<Boolean> s) { label = l; get = g; set = s; }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {
            boolean on = get.get();

            // Label left-aligned
            ctx.drawTextWithShadow(tr, label, rx + 2, ry + 4, on ? 0xFFD8D8E0 : 0xFF4A4A54);

            // Circle toggle (right side)
            int circR = 5;
            int cx = rx + rw - circR - 4;
            int cy = ry + rh / 2;

            if (on) {
                int nR = nr(nc), nG = ng(nc), nB = nb(nc);
                // Outer glow
                ctx.fill(cx - circR - 2, cy - circR - 2, cx + circR + 2, cy + circR + 2, rgba(nR, nG, nB, 30));
                // Filled circle (approximation with rects)
                ctx.fill(cx - circR + 1, cy - circR, cx + circR - 1, cy + circR, rgba(nR, nG, nB, 220));
                ctx.fill(cx - circR, cy - circR + 1, cx + circR, cy + circR - 1, rgba(nR, nG, nB, 220));
                // Inner bright dot
                ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFFFFFFF);
            } else {
                // Ring only
                ctx.fill(cx - circR + 1, cy - circR, cx + circR - 1, cy - circR + 1, 0xFF3A3A42);
                ctx.fill(cx - circR + 1, cy + circR - 1, cx + circR - 1, cy + circR, 0xFF3A3A42);
                ctx.fill(cx - circR, cy - circR + 1, cx - circR + 1, cy + circR - 1, 0xFF3A3A42);
                ctx.fill(cx + circR - 1, cy - circR + 1, cx + circR, cy + circR - 1, 0xFF3A3A42);
            }
        }

        @Override void onClick(int mx) { set.accept(!get.get()); }
    }

    // Slider: label left, [track + knob] right, value far right
    static class Slider extends Row {
        final String label;
        final float min, max;
        float val;
        final Consumer<Double> cb;

        Slider(String l, float mn, float mx, float v, Consumer<Double> c) {
            label = l; min = mn; max = mx; val = v; cb = c;
        }

        float frac() { return max <= min ? 0 : Math.max(0, Math.min(1, (val - min) / (max - min))); }

        int trackX() { return rx + SLIDER_LABEL_W; }
        int trackW() { return rw - SLIDER_LABEL_W - 30; }

        void apply(int mx) {
            int tx = trackX(), tw = trackW();
            float f = Math.max(0, Math.min(1, (mx - tx) / (float) tw));
            val = min + f * (max - min);
            if (max - min >= 1 && max <= 255) val = Math.round(val);
            cb.accept((double) val);
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {
            // Label
            ctx.drawTextWithShadow(tr, label, rx + 2, ry + 4, 0xFF808090);

            int tx = trackX(), tw = trackW();
            int ty = ry + rh / 2;

            // Track background
            ctx.fill(tx, ty, tx + tw, ty + 2, 0xFF1C1C22);

            // Fill with neon
            int fw = (int)(frac() * tw);
            int nR = nr(nc), nG = ng(nc), nB = nb(nc);
            if (fw > 0) {
                ctx.fill(tx, ty, tx + fw, ty + 2, rgba(nR, nG, nB, 200));
                ctx.fill(tx, ty - 1, tx + fw, ty + 3, rgba(nR, nG, nB, 25));
            }

            // Knob (small square)
            int kx = tx + fw;
            ctx.fill(kx - 2, ty - 3, kx + 2, ty + 5, 0xFFCCCCD0);
            ctx.fill(kx - 1, ty - 2, kx + 1, ty + 4, 0xFFFFFFFF);

            // Value (right-aligned, separate from track)
            String vs = (max <= 255 && max - min >= 1) ? "" + (int) val : String.format("%.1f", val);
            int vw = tr.getWidth(vs);
            ctx.drawTextWithShadow(tr, vs, rx + rw - vw, ry + 4, 0xFFB0B0B8);
        }

        @Override void onClick(int mx) { apply(mx); }
        @Override void onDrag(int mx) { apply(mx); }
    }
}
