package com.prisset.vtools.gui;

import com.prisset.vtools.blueprint.BlueprintScanner;
import com.prisset.vtools.blueprint.BlueprintStorage;
import com.prisset.vtools.blueprint.SchematicData;
import com.prisset.vtools.blueprint.SelectionManager;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
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
    private static final int SCROLL_SPEED = 12;

    private final DisplayPrefs prefs;
    private final List<Row> rows = new ArrayList<>();
    private int wx, wy, totalH;
    private int scrollOffset;
    private int maxScroll;
    private Row drag;

    public PrefsScreen(DisplayPrefs prefs) {
        super(Text.literal("PRISSET"));
        this.prefs = prefs;
    }

    @Override
    protected void init() {
        drag = null;
        scrollOffset = 0;
        rows.clear();

        // -- OVERLAY section --
        rows.add(new Label("\u041e\u0412\u0415\u0420\u041b\u0415\u0419"));
        rows.add(new Toggle("\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c", prefs::isActive, v -> prefs.setActive(v)));
        rows.add(new Toggle("\u0418\u0433\u0440\u043e\u043a\u0438", prefs::isFilterPlayers, v -> prefs.setFilterPlayers(v)));
        rows.add(new Toggle("\u041c\u043e\u0431\u044b", prefs::isFilterMobs, v -> prefs.setFilterMobs(v)));
        rows.add(new Toggle("\u041f\u0440\u0435\u0434\u043c\u0435\u0442\u044b", prefs::isFilterDrops, v -> prefs.setFilterDrops(v)));
        rows.add(new Toggle("\u0421\u043d\u0430\u0440\u044f\u0434\u044b", prefs::isFilterProjectiles, v -> prefs.setFilterProjectiles(v)));

        // -- COLOR section (overlay color) --
        rows.add(new Label("\u0426\u0412\u0415\u0422"));
        rows.add(new Slider("\u041a\u0440\u0430\u0441\u043d\u044b\u0439", 0, 255, prefs.getTintR(), v -> prefs.setTintR(v.intValue())));
        rows.add(new Slider("\u0417\u0435\u043b\u0451\u043d\u044b\u0439", 0, 255, prefs.getTintG(), v -> prefs.setTintG(v.intValue())));
        rows.add(new Slider("\u0421\u0438\u043d\u0438\u0439", 0, 255, prefs.getTintB(), v -> prefs.setTintB(v.intValue())));
        rows.add(new Slider("\u041d\u0435\u043f\u0440\u043e\u0437\u0440\u0430\u0447\u043d.", 0, 255, prefs.getTintA(), v -> prefs.setTintA(v.intValue())));

        // -- EFFECTS section --
        rows.add(new Label("\u042d\u0424\u0424\u0415\u041a\u0422\u042b"));
        rows.add(new Toggle("RGB", prefs::isRgbMode, v -> prefs.setRgbMode(v)));
        rows.add(new Slider("RGB \u0441\u043a\u043e\u0440.", 0.1f, 5f, prefs.getRgbSpeed(), v -> prefs.setRgbSpeed(v.floatValue())));
        rows.add(new Toggle("\u0417\u0434\u043e\u0440\u043e\u0432\u044c\u0435", prefs::isHealthBars, v -> prefs.setHealthBars(v)));
        rows.add(new Slider("\u0417\u0443\u043c (C)", 1.5f, 10f, prefs.getZoomStrength(), v -> prefs.setZoomStrength(v.floatValue())));
        rows.add(new Toggle("\u0421\u043b\u0435\u0434", prefs::isTrailEnabled, v -> prefs.setTrailEnabled(v)));
        rows.add(new Slider("\u0414\u043b\u0438\u043d\u0430", 5, 40, prefs.getTrailLength(), v -> prefs.setTrailLength(v.intValue())));
        rows.add(new Toggle("\u0411\u0435\u0437 \u0442\u0440\u044f\u0441\u043a\u0438", prefs::isNoBobbing, v -> prefs.setNoBobbing(v)));

        // -- MENU section (menu neon color, separate from overlay) --
        rows.add(new Label("\u041c\u0415\u041d\u042e"));
        rows.add(new Toggle("RGB \u043c\u0435\u043d\u044e", prefs::isMenuRgbMode, v -> prefs.setMenuRgbMode(v)));
        rows.add(new Slider("RGB \u0441\u043a\u043e\u0440.", 0.1f, 5f, prefs.getMenuRgbSpeed(), v -> prefs.setMenuRgbSpeed(v.floatValue())));
        rows.add(new Slider("\u041a\u0440\u0430\u0441\u043d\u044b\u0439", 0, 255, prefs.getMenuR(), v -> prefs.setMenuR(v.intValue())));
        rows.add(new Slider("\u0417\u0435\u043b\u0451\u043d\u044b\u0439", 0, 255, prefs.getMenuG(), v -> prefs.setMenuG(v.intValue())));
        rows.add(new Slider("\u0421\u0438\u043d\u0438\u0439", 0, 255, prefs.getMenuB(), v -> prefs.setMenuB(v.intValue())));

        // -- BLUEPRINT section --
        rows.add(new Label("\u0427\u0415\u0420\u0422\u0401\u0416"));
        rows.add(new Toggle("\u0420\u0435\u0436\u0438\u043c \u0432\u044b\u0434\u0435\u043b\u0435\u043d\u0438\u044f",
            () -> SelectionManager.instance().isActive(),
            v -> SelectionManager.instance().setActive(v)));
        rows.add(new Button("\u0421\u043a\u0430\u043d\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u0438 \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;
            SelectionManager sel = SelectionManager.instance();
            if (!sel.isComplete()) return;
            SchematicData data = BlueprintScanner.scan(mc.world);
            if (data == null) return;
            data.setName("build_" + System.currentTimeMillis() / 1000);
            BlueprintStorage.save(data);
        }));

        totalH = PAD + 14;
        for (Row r : rows) totalH += r instanceof Label ? LABEL_H : ROW_H;
        totalH += PAD;

        wx = (width - W) / 2;

        int visibleH = Math.min(totalH, height - 20);
        wy = (height - visibleH) / 2;
        maxScroll = Math.max(0, totalH - visibleH);
    }

    // Menu neon uses its own color fields
    private int neon() {
        if (prefs.isMenuRgbMode()) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000f * prefs.getMenuRgbSpeed() % 1f;
            return java.awt.Color.HSBtoRGB(hue, 0.8f, 1f) | 0xFF000000;
        }
        int r = prefs.getMenuR(), g = prefs.getMenuG(), b = prefs.getMenuB();
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

        // --- Blur-like background ---
        // Layer 1: heavy dark overlay
        ctx.fill(0, 0, width, height, 0xC0000000);
        // Layers 2-5: radial-ish vignette from edges inward
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            int inset = i * 8;
            int alpha = (int)(60.0 * (1.0 - (double) i / steps));
            if (alpha <= 0) break;
            ctx.fill(inset, inset, width - inset, height - inset, rgba(0, 0, 0, alpha));
        }
        // Layer 6: subtle neon-tinted fog
        ctx.fill(0, 0, width, height, rgba(nR / 8, nG / 8, nB / 8, 18));

        int visibleH = Math.min(totalH, height - 20);

        // Outer glow (6 layers, fading outward)
        for (int i = 6; i >= 1; i--) {
            int a = (int)(pulse * (6 + (6 - i) * 4));
            ctx.fill(wx - i, wy - i, wx + W + i, wy + visibleH + i, rgba(nR, nG, nB, a));
        }

        // Black panel
        ctx.fill(wx, wy, wx + W, wy + visibleH, 0xFF000000);

        // Border 1px neon
        int ba = (int)(pulse * 180);
        int bc = rgba(nR, nG, nB, ba);
        ctx.fill(wx, wy, wx + W, wy + 1, bc);
        ctx.fill(wx, wy + visibleH - 1, wx + W, wy + visibleH, bc);
        ctx.fill(wx, wy + 1, wx + 1, wy + visibleH - 1, bc);
        ctx.fill(wx + W - 1, wy + 1, wx + W, wy + visibleH - 1, bc);

        // Top line brighter
        ctx.fill(wx + 1, wy, wx + W - 1, wy + 1, rgba(nR, nG, nB, (int)(pulse * 255)));

        // Title centered
        String title = "PRISSET";
        int tw = textRenderer.getWidth(title);
        ctx.drawTextWithShadow(textRenderer, title, wx + (W - tw) / 2, wy + 3, rgba(nR, nG, nB, 255));

        // Overlay color swatch next to title
        int sc = (prefs.getTintA() << 24) | (prefs.getTintR() << 16) | (prefs.getTintG() << 8) | prefs.getTintB();
        ctx.fill(wx + W - 14, wy + 3, wx + W - 4, wy + 11, sc);

        // Scissor clip for scrollable content
        ctx.enableScissor(wx, wy + 14, wx + W, wy + visibleH);

        // Rows (scrollable)
        int y = wy + PAD + 14 - scrollOffset;
        for (Row r : rows) {
            int rh = r instanceof Label ? LABEL_H : ROW_H;
            r.rx = wx + PAD;
            r.ry = y;
            r.rw = W - PAD * 2;
            r.rh = rh;

            // Only render if visible
            if (y + rh > wy && y < wy + visibleH) {
                if (!(r instanceof Label)) {
                    boolean hov = r.contains(mx, my) && my >= wy && my < wy + visibleH;
                    if (hov) {
                        ctx.fill(r.rx, r.ry, r.rx + r.rw, r.ry + r.rh, rgba(nR, nG, nB, 14));
                    }
                    ctx.fill(r.rx, r.ry + r.rh - 1, r.rx + r.rw, r.ry + r.rh, 0xFF0E0E10);
                }
                r.render(ctx, textRenderer, mx, my, nc);
            }
            y += rh;
        }

        ctx.disableScissor();

        // Scroll indicator (thin bar on right side)
        if (maxScroll > 0) {
            float scrollFrac = (float) scrollOffset / maxScroll;
            int barH = Math.max(8, visibleH * visibleH / totalH);
            int barY = wy + 14 + (int)(scrollFrac * (visibleH - 14 - barH));
            ctx.fill(wx + W - 2, barY, wx + W - 1, barY + barH, rgba(nR, nG, nB, 60));
        }
    }

    // === INPUT ===

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        int visibleH = Math.min(totalH, height - 20);
        for (Row r : rows) {
            if (!(r instanceof Label) && r.contains((int) mx, (int) my)
                    && my >= wy && my < wy + visibleH) {
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
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(amount * SCROLL_SPEED)));
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
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

    static class Toggle extends Row {
        final String label;
        final Supplier<Boolean> get;
        final Consumer<Boolean> set;

        Toggle(String l, Supplier<Boolean> g, Consumer<Boolean> s) { label = l; get = g; set = s; }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {
            boolean on = get.get();
            ctx.drawTextWithShadow(tr, label, rx + 2, ry + 4, on ? 0xFFD8D8E0 : 0xFF4A4A54);

            int circR = 5;
            int cx = rx + rw - circR - 4;
            int cy = ry + rh / 2;

            if (on) {
                int cR = nr(nc), cG = ng(nc), cB = nb(nc);
                ctx.fill(cx - circR - 2, cy - circR - 2, cx + circR + 2, cy + circR + 2, rgba(cR, cG, cB, 30));
                ctx.fill(cx - circR + 1, cy - circR, cx + circR - 1, cy + circR, rgba(cR, cG, cB, 220));
                ctx.fill(cx - circR, cy - circR + 1, cx + circR, cy + circR - 1, rgba(cR, cG, cB, 220));
                ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFFFFFFF);
            } else {
                ctx.fill(cx - circR + 1, cy - circR, cx + circR - 1, cy - circR + 1, 0xFF3A3A42);
                ctx.fill(cx - circR + 1, cy + circR - 1, cx + circR - 1, cy + circR, 0xFF3A3A42);
                ctx.fill(cx - circR, cy - circR + 1, cx - circR + 1, cy + circR - 1, 0xFF3A3A42);
                ctx.fill(cx + circR - 1, cy - circR + 1, cx + circR, cy + circR - 1, 0xFF3A3A42);
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

        int trackX() { return rx + SLIDER_LABEL_W; }
        int trackW() { return rw - SLIDER_LABEL_W - 34; }

        void apply(int mx) {
            int tx = trackX(), tw = trackW();
            float f = Math.max(0, Math.min(1, (mx - tx) / (float) tw));
            val = min + f * (max - min);
            if (max - min >= 1 && max == Math.floor(max)) val = Math.round(val);
            cb.accept((double) val);
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {
            ctx.drawTextWithShadow(tr, label, rx + 2, ry + 4, 0xFF808090);

            int tx = trackX(), tw = trackW();
            int ty = ry + rh / 2;

            ctx.fill(tx, ty, tx + tw, ty + 2, 0xFF1C1C22);

            int fw = (int)(frac() * tw);
            int sR = nr(nc), sG = ng(nc), sB = nb(nc);
            if (fw > 0) {
                ctx.fill(tx, ty, tx + fw, ty + 2, rgba(sR, sG, sB, 200));
                ctx.fill(tx, ty - 1, tx + fw, ty + 3, rgba(sR, sG, sB, 25));
            }

            int kx = tx + fw;
            ctx.fill(kx - 2, ty - 3, kx + 2, ty + 5, 0xFFCCCCD0);
            ctx.fill(kx - 1, ty - 2, kx + 1, ty + 4, 0xFFFFFFFF);

            String vs = (max == Math.floor(max) && max - min >= 1) ? "" + (int) val : String.format("%.1f", val);
            int vw = tr.getWidth(vs);
            ctx.drawTextWithShadow(tr, vs, rx + rw - vw, ry + 4, 0xFFB0B0B8);
        }

        @Override void onClick(int mx) { apply(mx); }
        @Override void onDrag(int mx) { apply(mx); }
    }

    static class Button extends Row {
        final String label;
        final Runnable action;
        boolean flash;
        long flashTime;

        Button(String l, Runnable a) { label = l; action = a; }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {
            boolean hov = contains(mx, my);
            long age = System.currentTimeMillis() - flashTime;
            boolean showFlash = flash && age < 600;

            int bg = hov ? rgba(nr(nc), ng(nc), nb(nc), 30) : 0xFF0E0E12;
            if (showFlash) bg = rgba(40, 200, 60, 60);

            ctx.fill(rx, ry + 1, rx + rw, ry + rh - 1, bg);
            // Border
            int bc = hov ? rgba(nr(nc), ng(nc), nb(nc), 120) : 0xFF2A2A30;
            ctx.fill(rx, ry + 1, rx + rw, ry + 2, bc);
            ctx.fill(rx, ry + rh - 2, rx + rw, ry + rh - 1, bc);

            int textColor = showFlash ? 0xFF40FF40 : (hov ? 0xFFE0E0E8 : 0xFF808090);
            String display = showFlash ? "\u0413\u043e\u0442\u043e\u0432\u043e!" : label;
            int tw = tr.getWidth(display);
            ctx.drawTextWithShadow(tr, display, rx + (rw - tw) / 2, ry + 4, textColor);
        }

        @Override
        void onClick(int mx) {
            action.run();
            flash = true;
            flashTime = System.currentTimeMillis();
        }
    }
}
