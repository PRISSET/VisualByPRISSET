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

    // iOS Dark palette
    private static final int BG_DIM        = 0xB0000000;
    private static final int BG_PANEL      = 0xFF1C1C1E;
    private static final int BG_SECTION    = 0xFF2C2C2E;
    private static final int BG_ROW_HOVER  = 0xFF3A3A3C;
    private static final int SEPARATOR     = 0xFF38383A;
    private static final int TEXT_WHITE    = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFF8E8E93;
    private static final int TEXT_LABEL    = 0xFFEBEBF5;
    private static final int TINT_BLUE     = 0xFF0A84FF;
    private static final int TOGGLE_GREEN  = 0xFF30D158;
    private static final int TOGGLE_BG_OFF = 0xFF636366;
    private static final int TOGGLE_KNOB   = 0xFFFFFFFF;
    private static final int SLIDER_TRACK  = 0xFF636366;
    private static final int HEADER_TEXT   = 0xFF8E8E93;

    private static final int PANEL_W = 280;
    private static final int ROW_H = 28;
    private static final int SECTION_PAD = 8;
    private static final int CORNER = 6;

    private final DisplayPrefs prefs;
    private final List<Row> rows = new ArrayList<>();
    private int panelX, panelY, panelH;
    private int scrollOffset;
    private Row dragRow;

    public PrefsScreen(DisplayPrefs prefs) {
        super(Text.literal("Visual Tools"));
        this.prefs = prefs;
    }

    @Override
    protected void init() {
        rows.clear();
        scrollOffset = 0;
        dragRow = null;
        panelX = (width - PANEL_W) / 2;

        // -- Build rows --

        rows.add(new HeaderRow("\u041e\u0432\u0435\u0440\u043b\u0435\u0439"));
        rows.add(new ToggleRow("\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c", prefs::isActive, v -> prefs.setActive(v)));
        rows.add(new ToggleRow("\u0418\u0433\u0440\u043e\u043a\u0438", prefs::isFilterPlayers, v -> prefs.setFilterPlayers(v)));
        rows.add(new ToggleRow("\u041c\u043e\u0431\u044b", prefs::isFilterMobs, v -> prefs.setFilterMobs(v)));
        rows.add(new ToggleRow("\u041f\u0440\u0435\u0434\u043c\u0435\u0442\u044b", prefs::isFilterDrops, v -> prefs.setFilterDrops(v)));
        rows.add(new ToggleRow("\u0421\u043d\u0430\u0440\u044f\u0434\u044b", prefs::isFilterProjectiles, v -> prefs.setFilterProjectiles(v)));

        rows.add(new HeaderRow("\u0426\u0432\u0435\u0442"));
        rows.add(new SliderRow("\u041a\u0440\u0430\u0441\u043d\u044b\u0439", 0, 255, prefs.getTintR(), v -> prefs.setTintR(v.intValue()), 0xFFFF453A));
        rows.add(new SliderRow("\u0417\u0435\u043b\u0451\u043d\u044b\u0439", 0, 255, prefs.getTintG(), v -> prefs.setTintG(v.intValue()), 0xFF30D158));
        rows.add(new SliderRow("\u0421\u0438\u043d\u0438\u0439", 0, 255, prefs.getTintB(), v -> prefs.setTintB(v.intValue()), 0xFF0A84FF));
        rows.add(new SliderRow("\u041d\u0435\u043f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c", 0, 255, prefs.getTintA(), v -> prefs.setTintA(v.intValue()), TEXT_SECONDARY));

        rows.add(new HeaderRow("\u042d\u0444\u0444\u0435\u043a\u0442\u044b"));
        rows.add(new ToggleRow("RGB", prefs::isRgbMode, v -> prefs.setRgbMode(v)));
        rows.add(new SliderRow("\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c RGB", 0.1f, 5.0f, prefs.getRgbSpeed(), v -> prefs.setRgbSpeed(v.floatValue()), TINT_BLUE));
        rows.add(new ToggleRow("\u0417\u0434\u043e\u0440\u043e\u0432\u044c\u0435", prefs::isHealthBars, v -> prefs.setHealthBars(v)));

        rows.add(new HeaderRow("\u041a\u0430\u043c\u0435\u0440\u0430"));
        rows.add(new SliderRow("\u041f\u0440\u0438\u0431\u043b\u0438\u0436\u0435\u043d\u0438\u0435 (C)", 1.5f, 10.0f, prefs.getZoomStrength(), v -> prefs.setZoomStrength(v.floatValue()), TINT_BLUE));

        rows.add(new HeaderRow("\u0421\u043b\u0435\u0434"));
        rows.add(new ToggleRow("\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c", prefs::isTrailEnabled, v -> prefs.setTrailEnabled(v)));
        rows.add(new SliderRow("\u0414\u043b\u0438\u043d\u0430", 5, 40, prefs.getTrailLength(), v -> prefs.setTrailLength(v.intValue()), 0xFFFF9F0A));

        // Calculate panel height
        panelH = 20; // top padding
        for (Row r : rows) {
            panelH += r instanceof HeaderRow ? 32 : ROW_H;
        }
        panelH += 12; // bottom padding
        panelH = Math.min(panelH, height - 40);
        panelY = (height - panelH) / 2;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Dim
        ctx.fill(0, 0, width, height, BG_DIM);

        // Panel
        fillRounded(ctx, panelX, panelY, PANEL_W, panelH, BG_PANEL);

        // Title bar
        String title = "Visual Tools";
        int tw = textRenderer.getWidth(title);
        ctx.drawTextWithShadow(textRenderer, title, panelX + (PANEL_W - tw) / 2, panelY + 6, TEXT_WHITE);

        // Clipping via manual skip
        int contentTop = panelY + 20;
        int contentBot = panelY + panelH - 6;

        int y = contentTop - scrollOffset;
        int sectionStart = -1;
        int sectionEnd = -1;
        int nextSectionY = -1;

        // Pre-pass to find section groups for rounded bg
        List<int[]> sectionBounds = new ArrayList<>();
        int sy = contentTop - scrollOffset;
        int secStartY = -1;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int rh = r instanceof HeaderRow ? 32 : ROW_H;
            if (r instanceof HeaderRow) {
                if (secStartY >= 0) {
                    sectionBounds.add(new int[]{secStartY, sy});
                }
                secStartY = sy + rh;
            }
            sy += rh;
        }
        if (secStartY >= 0) {
            sectionBounds.add(new int[]{secStartY, sy});
        }

        // Draw section backgrounds
        for (int[] bounds : sectionBounds) {
            int top = Math.max(bounds[0], contentTop);
            int bot = Math.min(bounds[1], contentBot);
            if (top < bot) {
                fillRounded(ctx, panelX + 10, top, PANEL_W - 20, bot - top, BG_SECTION);
            }
        }

        // Draw rows
        y = contentTop - scrollOffset;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int rh = r instanceof HeaderRow ? 32 : ROW_H;

            if (y + rh > contentTop && y < contentBot) {
                r.rx = panelX + 14;
                r.ry = y;
                r.rw = PANEL_W - 28;
                r.rh = rh;

                r.render(ctx, textRenderer, mx, my, panelX + 10, PANEL_W - 20);

                // Separator between non-header rows
                if (!(r instanceof HeaderRow) && i + 1 < rows.size() && !(rows.get(i + 1) instanceof HeaderRow)) {
                    int sepY = y + rh - 1;
                    if (sepY > contentTop && sepY < contentBot) {
                        ctx.fill(panelX + 24, sepY, panelX + PANEL_W - 24, sepY + 1, SEPARATOR);
                    }
                }
            } else {
                r.ry = -999;
            }

            y += rh;
        }

        // Fade edges
        ctx.fill(panelX, panelY + 18, panelX + PANEL_W, panelY + 22, BG_PANEL);
        ctx.fill(panelX, panelY + panelH - 8, panelX + PANEL_W, panelY + panelH, BG_PANEL);
    }

    // === Input ===

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int imx = (int) mx, imy = (int) my;
        int contentTop = panelY + 20;
        int contentBot = panelY + panelH - 6;

        for (Row r : rows) {
            if (r.ry < contentTop || r.ry > contentBot) continue;
            if (r.contains(imx, imy)) {
                r.onClick(imx, imy);
                if (r instanceof SliderRow) dragRow = r;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragRow != null) {
            dragRow.onDrag((int) mx, (int) my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragRow = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int totalH = 20;
        for (Row r : rows) totalH += r instanceof HeaderRow ? 32 : ROW_H;
        totalH += 12;
        int maxScroll = Math.max(0, totalH - panelH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(amount * 16)));
        return true;
    }

    @Override
    public void close() {
        prefs.save();
        if (client != null) client.setScreen(null);
    }

    @Override
    public boolean shouldPause() { return false; }

    // === Drawing helpers ===

    private static void fillRounded(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + CORNER, y, x + w - CORNER, y + h, color);
        ctx.fill(x, y + CORNER, x + CORNER, y + h - CORNER, color);
        ctx.fill(x + w - CORNER, y + CORNER, x + w, y + h - CORNER, color);
        // corners (2px inset approximation)
        ctx.fill(x + 2, y + 1, x + CORNER, y + CORNER, color);
        ctx.fill(x + 1, y + 2, x + 2, y + CORNER, color);
        ctx.fill(x + w - CORNER, y + 1, x + w - 2, y + CORNER, color);
        ctx.fill(x + w - 2, y + 2, x + w - 1, y + CORNER, color);
        ctx.fill(x + 2, y + h - CORNER, x + CORNER, y + h - 1, color);
        ctx.fill(x + 1, y + h - CORNER, x + 2, y + h - 2, color);
        ctx.fill(x + w - CORNER, y + h - CORNER, x + w - 2, y + h - 1, color);
        ctx.fill(x + w - 2, y + h - CORNER, x + w - 1, y + h - 2, color);
    }

    // === Row types ===

    static abstract class Row {
        int rx, ry, rw, rh;
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int sectionX, int sectionW) {}
        void onClick(int mx, int my) {}
        void onDrag(int mx, int my) {}
        boolean contains(int mx, int my) { return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh; }
    }

    static class HeaderRow extends Row {
        final String text;
        HeaderRow(String text) { this.text = text; }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int sectionX, int sectionW) {
            ctx.drawTextWithShadow(tr, text.toUpperCase(), rx + 2, ry + 20, HEADER_TEXT);
        }

        @Override boolean contains(int mx, int my) { return false; }
    }

    static class ToggleRow extends Row {
        final String label;
        final Supplier<Boolean> getter;
        final Consumer<Boolean> setter;
        float anim;

        ToggleRow(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.label = label;
            this.getter = getter;
            this.setter = setter;
            this.anim = getter.get() ? 1f : 0f;
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int sectionX, int sectionW) {
            boolean hover = contains(mx, my);
            if (hover) {
                ctx.fill(sectionX + 1, ry, sectionX + sectionW - 1, ry + rh, BG_ROW_HOVER);
            }

            boolean on = getter.get();
            float target = on ? 1f : 0f;
            anim += (target - anim) * 0.2f;

            ctx.drawTextWithShadow(tr, label, rx + 4, ry + (rh - 8) / 2, TEXT_LABEL);

            // iOS toggle
            int trkW = 30;
            int trkH = 16;
            int trkX = rx + rw - trkW - 2;
            int trkY = ry + (rh - trkH) / 2;

            int bgColor = blendColor(TOGGLE_BG_OFF, TOGGLE_GREEN, anim);
            fillToggleTrack(ctx, trkX, trkY, trkW, trkH, bgColor);

            // Knob
            int knobD = trkH - 4;
            int knobRange = trkW - knobD - 4;
            int knobX = trkX + 2 + (int)(anim * knobRange);
            int knobY = trkY + 2;
            fillToggleTrack(ctx, knobX, knobY, knobD, knobD, TOGGLE_KNOB);
        }

        @Override
        void onClick(int mx, int my) { setter.accept(!getter.get()); }
    }

    static class SliderRow extends Row {
        final String label;
        final float min, max;
        float value;
        final Consumer<Double> callback;
        final int color;

        SliderRow(String label, float min, float max, float value, Consumer<Double> cb, int color) {
            this.label = label;
            this.min = min; this.max = max;
            this.value = value;
            this.callback = cb;
            this.color = color;
        }

        float frac() { return max <= min ? 0 : Math.max(0, Math.min(1, (value - min) / (max - min))); }

        void applyMouse(int mx) {
            int trackX = rx + 4;
            int trackW = rw - 8;
            float f = Math.max(0, Math.min(1, (mx - trackX) / (float) trackW));
            value = min + f * (max - min);
            if (max - min >= 1 && max <= 255) value = Math.round(value);
            callback.accept((double) value);
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int sectionX, int sectionW) {
            boolean hover = contains(mx, my);
            if (hover) {
                ctx.fill(sectionX + 1, ry, sectionX + sectionW - 1, ry + rh, BG_ROW_HOVER);
            }

            // Value text
            String vs = (max <= 255 && max - min >= 1) ? String.valueOf((int) value) : String.format("%.1f", value);

            ctx.drawTextWithShadow(tr, label, rx + 4, ry + 3, TEXT_LABEL);
            int vw = tr.getWidth(vs);
            ctx.drawTextWithShadow(tr, vs, rx + rw - vw - 4, ry + 3, color);

            // Track
            int trackX = rx + 4;
            int trackW = rw - 8;
            int trackY = ry + rh - 7;
            int trackH = 4;

            // Track bg (rounded-ish)
            ctx.fill(trackX + 2, trackY, trackX + trackW - 2, trackY + trackH, SLIDER_TRACK);
            ctx.fill(trackX, trackY + 1, trackX + 2, trackY + trackH - 1, SLIDER_TRACK);
            ctx.fill(trackX + trackW - 2, trackY + 1, trackX + trackW, trackY + trackH - 1, SLIDER_TRACK);

            // Fill
            int fillW = (int)(frac() * trackW);
            if (fillW > 2) {
                ctx.fill(trackX + 2, trackY, trackX + fillW - 2, trackY + trackH, color);
                ctx.fill(trackX, trackY + 1, trackX + 2, trackY + trackH - 1, color);
            }

            // Knob
            int knobX = trackX + fillW;
            int knobR = 5;
            // shadow
            ctx.fill(knobX - knobR + 1, trackY - 3 + 1, knobX + knobR + 1, trackY + trackH + 3 + 1, 0x30000000);
            // knob
            ctx.fill(knobX - knobR, trackY - 3, knobX + knobR, trackY + trackH + 3, TOGGLE_KNOB);
        }

        @Override void onClick(int mx, int my) { applyMouse(mx); }
        @Override void onDrag(int mx, int my) { applyMouse(mx); }
    }

    // === Utility ===

    private static int blendColor(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = blend1((c1 >> 24) & 0xFF, (c2 >> 24) & 0xFF, t);
        int r = blend1((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, t);
        int g = blend1((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, t);
        int b = blend1(c1 & 0xFF, c2 & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blend1(int a, int b, float t) { return (int)(a + (b - a) * t); }

    private static void fillToggleTrack(DrawContext ctx, int x, int y, int w, int h, int color) {
        int r = h / 2;
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + 2, x + r, y + h - 2, color);
        ctx.fill(x + 1, y + 1, x + r, y + 2, color);
        ctx.fill(x + 1, y + h - 2, x + r, y + h - 1, color);
        ctx.fill(x + w - r, y + 2, x + w, y + h - 2, color);
        ctx.fill(x + w - r, y + 1, x + w - 1, y + 2, color);
        ctx.fill(x + w - r, y + h - 2, x + w - 1, y + h - 1, color);
    }
}
