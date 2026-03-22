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

    private static final int W = 320, H = 220;
    private static final int ROW_H = 18;

    private final DisplayPrefs prefs;
    private final List<Row> rows = new ArrayList<>();
    private int wx, wy;
    private Row drag;
    private long openTime;

    public PrefsScreen(DisplayPrefs prefs) {
        super(Text.literal("PRISSET"));
        this.prefs = prefs;
    }

    @Override
    protected void init() {
        wx = (width - W) / 2;
        wy = (height - H) / 2;
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
        rows.add(new Slider("\u041d\u0435\u043f\u0440\u043e\u0437\u0440.", 0, 255, prefs.getTintA(), v -> prefs.setTintA(v.intValue())));

        rows.add(new Label("\u042d\u0424\u0424\u0415\u041a\u0422\u042b"));
        rows.add(new Toggle("RGB", prefs::isRgbMode, v -> prefs.setRgbMode(v)));
        rows.add(new Slider("RGB \u0441\u043a\u043e\u0440\u043e\u0441\u0442\u044c", 0.1f, 5f, prefs.getRgbSpeed(), v -> prefs.setRgbSpeed(v.floatValue())));
        rows.add(new Toggle("\u0417\u0434\u043e\u0440\u043e\u0432\u044c\u0435", prefs::isHealthBars, v -> prefs.setHealthBars(v)));
        rows.add(new Slider("\u0417\u0443\u043c (C)", 1.5f, 10f, prefs.getZoomStrength(), v -> prefs.setZoomStrength(v.floatValue())));
        rows.add(new Toggle("\u0421\u043b\u0435\u0434", prefs::isTrailEnabled, v -> prefs.setTrailEnabled(v)));
        rows.add(new Slider("\u0414\u043b\u0438\u043d\u0430 \u0441\u043b\u0435\u0434\u0430", 5, 40, prefs.getTrailLength(), v -> prefs.setTrailLength(v.intValue())));

        // Recalculate height
        int totalH = 8;
        for (Row r : rows) totalH += r instanceof Label ? 14 : ROW_H;
        totalH += 8;
        wy = (height - totalH) / 2;
    }

    private int glowColor() {
        if (prefs.isRgbMode()) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000f * prefs.getRgbSpeed() % 1f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.8f, 1f);
            return rgb | 0xFF000000;
        }
        return 0xFF000000 | (prefs.getTintR() << 16) | (prefs.getTintG() << 8) | prefs.getTintB();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        float open = Math.min(1f, (now - openTime) / 200f);

        // Dim
        ctx.fill(0, 0, width, height, (int)(open * 120) << 24);

        int totalH = 8;
        for (Row r : rows) totalH += r instanceof Label ? 14 : ROW_H;
        totalH += 8;

        int gc = glowColor();
        int gr = (gc >> 16) & 0xFF, gg = (gc >> 8) & 0xFF, gb = gc & 0xFF;

        // Neon glow layers (outer to inner)
        float pulse = (float)(Math.sin(now / 500.0) * 0.15 + 0.85);
        for (int i = 4; i >= 1; i--) {
            int a = (int)(pulse * (12 + (4 - i) * 6));
            int c = (a << 24) | (gr << 16) | (gg << 8) | gb;
            ctx.fill(wx - i, wy - i, wx + W + i, wy + totalH + i, c);
        }

        // Solid black fill
        ctx.fill(wx, wy, wx + W, wy + totalH, 0xFF000000);

        // Border (1px, neon color)
        int borderA = (int)(pulse * 200);
        int borderC = (borderA << 24) | (gr << 16) | (gg << 8) | gb;
        ctx.fill(wx, wy, wx + W, wy + 1, borderC);
        ctx.fill(wx, wy + totalH - 1, wx + W, wy + totalH, borderC);
        ctx.fill(wx, wy, wx + 1, wy + totalH, borderC);
        ctx.fill(wx + W - 1, wy, wx + W, wy + totalH, borderC);

        // Top neon line (brighter)
        int topA = (int)(pulse * 255);
        ctx.fill(wx + 1, wy, wx + W - 1, wy + 1, (topA << 24) | (gr << 16) | (gg << 8) | gb);

        // Title
        String title = "PRISSET";
        int tw = textRenderer.getWidth(title);
        // Title with glow color
        ctx.drawTextWithShadow(textRenderer, title, wx + W / 2 - tw / 2, wy + 2, borderC | 0xFF000000);

        // Rows
        int y = wy + 8;
        for (Row r : rows) {
            int rh = r instanceof Label ? 14 : ROW_H;
            if (!(r instanceof Label)) {
                r.rx = wx + 6;
                r.ry = y;
                r.rw = W - 12;
                r.rh = rh;

                boolean hov = mx >= r.rx && mx < r.rx + r.rw && my >= r.ry && my < r.ry + r.rh;
                if (hov) {
                    int ha = 20;
                    ctx.fill(r.rx, r.ry, r.rx + r.rw, r.ry + r.rh, (ha << 24) | (gr << 16) | (gg << 8) | gb);
                }
            }
            r.render(ctx, textRenderer, mx, my, gc);
            y += rh;
        }

        // Color preview
        int pc = (prefs.getTintA() << 24) | (prefs.getTintR() << 16) | (prefs.getTintG() << 8) | prefs.getTintB();
        ctx.fill(wx + W - 16, wy + 2, wx + W - 4, wy + 10, pc);
    }

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

    @Override
    public void close() { prefs.save(); if (client != null) client.setScreen(null); }
    @Override
    public boolean shouldPause() { return false; }

    // === ROW TYPES ===

    static abstract class Row {
        int rx, ry, rw, rh;
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int accent) {}
        void onClick(int mx) {}
        void onDrag(int mx) {}
        boolean contains(int mx, int my) { return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh; }
    }

    static class Label extends Row {
        final String text;
        Label(String t) { text = t; }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int accent) {
            int ar = (accent >> 16) & 0xFF, ag = (accent >> 8) & 0xFF, ab = accent & 0xFF;
            int dim = 0xFF000000 | ((ar / 2) << 16) | ((ag / 2) << 8) | (ab / 2);
            ctx.drawTextWithShadow(tr, text, rx + 6, ry + 4, dim);
            // thin line
            int lw = tr.getWidth(text);
            ctx.fill(rx + lw + 10, ry + 8, rx + rw, ry + 9, 0xFF1A1A1A);
        }
        @Override boolean contains(int mx, int my) { return false; }
    }

    static class Toggle extends Row {
        final String label;
        final Supplier<Boolean> get;
        final Consumer<Boolean> set;

        Toggle(String l, Supplier<Boolean> g, Consumer<Boolean> s) { label = l; get = g; set = s; }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int accent) {
            boolean on = get.get();
            ctx.drawTextWithShadow(tr, label, rx + 4, ry + 5, on ? 0xFFE0E0E0 : 0xFF606068);

            // Dot indicator
            int dx = rx + rw - 10, dy = ry + 6;
            if (on) {
                int ar = (accent >> 16) & 0xFF, ag = (accent >> 8) & 0xFF, ab = accent & 0xFF;
                // glow
                ctx.fill(dx - 2, dy - 2, dx + 8, dy + 8, (40 << 24) | (ar << 16) | (ag << 8) | ab);
                ctx.fill(dx, dy, dx + 6, dy + 6, accent | 0xFF000000);
            } else {
                ctx.fill(dx, dy, dx + 6, dy + 6, 0xFF333338);
            }
        }

        @Override void onClick(int mx) { set.accept(!get.get()); }
    }

    static class Slider extends Row {
        final String label;
        final float min, max;
        float val;
        final Consumer<Double> cb;

        Slider(String l, float mn, float mx, float v, Consumer<Double> c) {
            label = l; min = mn; max = mx; val = v; cb = c;
        }

        float frac() { return max <= min ? 0 : Math.max(0, Math.min(1, (val - min) / (max - min))); }

        void apply(int mx) {
            int trackX = rx + rw / 2;
            int trackW = rw / 2 - 14;
            float f = Math.max(0, Math.min(1, (mx - trackX) / (float) trackW));
            val = min + f * (max - min);
            if (max - min >= 1 && max <= 255) val = Math.round(val);
            cb.accept((double) val);
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int accent) {
            String vs = (max <= 255 && max - min >= 1) ? "" + (int) val : String.format("%.1f", val);
            ctx.drawTextWithShadow(tr, label, rx + 4, ry + 5, 0xFF909098);

            int trackX = rx + rw / 2;
            int trackW = rw / 2 - 14;
            int trackY = ry + 8;

            // Track bg
            ctx.fill(trackX, trackY, trackX + trackW, trackY + 2, 0xFF222228);

            // Fill
            int fw = (int)(frac() * trackW);
            if (fw > 0) {
                int ar = (accent >> 16) & 0xFF, ag = (accent >> 8) & 0xFF, ab = accent & 0xFF;
                ctx.fill(trackX, trackY, trackX + fw, trackY + 2, accent | 0xFF000000);
                // glow on fill
                ctx.fill(trackX, trackY - 1, trackX + fw, trackY + 3, (30 << 24) | (ar << 16) | (ag << 8) | ab);
            }

            // Knob
            int kx = trackX + fw;
            ctx.fill(kx - 2, trackY - 3, kx + 2, trackY + 5, 0xFFD0D0D4);

            // Value
            int vw = tr.getWidth(vs);
            ctx.drawTextWithShadow(tr, vs, rx + rw - vw - 2, ry + 5, 0xFFD0D0D4);
        }

        @Override void onClick(int mx) { apply(mx); }
        @Override void onDrag(int mx) { apply(mx); }
    }
}
