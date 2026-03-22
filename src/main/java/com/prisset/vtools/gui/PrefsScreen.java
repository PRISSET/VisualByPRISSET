package com.prisset.vtools.gui;

import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class PrefsScreen extends Screen {

    private static final int ACCENT = 0xFF00E5FF;
    private static final int ACCENT_DIM = 0xFF007A8A;
    private static final int BG_PANEL = 0xD0101018;
    private static final int BG_SECTION = 0x80181824;
    private static final int BG_WIDGET = 0xFF1A1A28;
    private static final int BG_HOVER = 0xFF242438;
    private static final int TOGGLE_ON = 0xFF00E676;
    private static final int TOGGLE_OFF = 0xFF5C5C6E;
    private static final int SLIDER_TRACK = 0xFF2A2A3E;
    private static final int TEXT_PRIMARY = 0xFFE0E0E0;
    private static final int TEXT_DIM = 0xFF8888A0;
    private static final int SHADOW = 0x60000000;

    private final DisplayPrefs prefs;
    private final List<Widget> widgets = new ArrayList<>();

    private int panelX, panelY, panelW, panelH;
    private Widget dragWidget;

    public PrefsScreen(DisplayPrefs prefs) {
        super(Text.literal("PRISSET Visual Tools"));
        this.prefs = prefs;
    }

    @Override
    protected void init() {
        widgets.clear();
        panelW = 260;
        panelH = 340;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int x = panelX + 12;
        int w = panelW - 24;
        int y = panelY + 36;

        // --- Main ---
        y = sectionLabel(y, "\u041e\u0441\u043d\u043e\u0432\u043d\u043e\u0435");
        addToggle(x, y, w, "\u041e\u0432\u0435\u0440\u043b\u0435\u0439", prefs.isActive(), v -> prefs.setActive(v));
        y += 20;
        addToggle(x, y, w, "\u0422\u043e\u043b\u044c\u043a\u043e F3+B", prefs.isDebugOnly(), v -> prefs.setDebugOnly(v));
        y += 24;

        // --- Filters ---
        y = sectionLabel(y, "\u0424\u0438\u043b\u044c\u0442\u0440\u044b");
        int fw = (w - 9) / 4;
        addToggle(x, y, fw, "\u0418\u0433\u0440\u043e\u043a\u0438", prefs.isFilterPlayers(), v -> prefs.setFilterPlayers(v));
        addToggle(x + fw + 3, y, fw, "\u041c\u043e\u0431\u044b", prefs.isFilterMobs(), v -> prefs.setFilterMobs(v));
        addToggle(x + (fw + 3) * 2, y, fw, "\u0414\u0440\u043e\u043f", prefs.isFilterDrops(), v -> prefs.setFilterDrops(v));
        addToggle(x + (fw + 3) * 3, y, fw, "\u0421\u043d\u0430\u0440\u044f\u0434", prefs.isFilterProjectiles(), v -> prefs.setFilterProjectiles(v));
        y += 24;

        // --- Color ---
        y = sectionLabel(y, "\u0426\u0432\u0435\u0442");
        int hw = (w - 4) / 2;
        addSlider(x, y, hw, "\u041a\u0440\u0430\u0441\u043d\u044b\u0439", 0, 255, prefs.getTintR(), v -> prefs.setTintR((int) v), 0xFFFF4444);
        addSlider(x + hw + 4, y, hw, "\u0417\u0435\u043b\u0451\u043d\u044b\u0439", 0, 255, prefs.getTintG(), v -> prefs.setTintG((int) v), 0xFF44FF44);
        y += 20;
        addSlider(x, y, hw, "\u0421\u0438\u043d\u0438\u0439", 0, 255, prefs.getTintB(), v -> prefs.setTintB((int) v), 0xFF4488FF);
        addSlider(x + hw + 4, y, hw, "\u041f\u0440\u043e\u0437\u0440.", 0, 255, prefs.getTintA(), v -> prefs.setTintA((int) v), 0xFF888888);
        y += 24;

        // --- RGB ---
        y = sectionLabel(y, "RGB");
        addToggle(x, y, hw, "RGB \u0440\u0435\u0436\u0438\u043c", prefs.isRgbMode(), v -> prefs.setRgbMode(v));
        addSlider(x + hw + 4, y, hw, "\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c", 0.1f, 5.0f, prefs.getRgbSpeed(), v -> prefs.setRgbSpeed((float) v), ACCENT);
        y += 24;

        // --- Features ---
        y = sectionLabel(y, "\u0424\u0443\u043d\u043a\u0446\u0438\u0438");
        addToggle(x, y, hw, "\u2764 \u0417\u0434\u043e\u0440\u043e\u0432\u044c\u0435", prefs.isHealthBars(), v -> prefs.setHealthBars(v));
        addSlider(x + hw + 4, y, hw, "\u0417\u0443\u043c (C)", 1.5f, 10.0f, prefs.getZoomStrength(), v -> prefs.setZoomStrength((float) v), 0xFF44DDFF);
        y += 20;
        addToggle(x, y, hw, "\u0421\u043b\u0435\u0434", prefs.isTrailEnabled(), v -> prefs.setTrailEnabled(v));
        addSlider(x + hw + 4, y, hw, "\u0414\u043b\u0438\u043d\u0430", 5, 40, prefs.getTrailLength(), v -> prefs.setTrailLength((int) v), 0xFFFFAA44);
    }

    private int sectionLabel(int y, String label) {
        widgets.add(new SectionLabel(panelX + 12, y, label));
        return y + 14;
    }

    private void addToggle(int x, int y, int w, String label, boolean value, ToggleCallback cb) {
        widgets.add(new Toggle(x, y, w, 16, label, value, cb));
    }

    private void addSlider(int x, int y, int w, String label, float min, float max, float value, SliderCallback cb, int color) {
        widgets.add(new Slider(x, y, w, 16, label, min, max, value, cb, color));
    }

    // --- Render ---

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);

        // Shadow
        ctx.fill(panelX + 3, panelY + 3, panelX + panelW + 3, panelY + panelH + 3, SHADOW);

        // Panel background
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_PANEL);

        // Top accent line
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 2, ACCENT);

        // Title — RGB animated
        String title = "PRISSET";
        int titleW = textRenderer.getWidth(title);
        int titleX = panelX + (panelW - titleW) / 2;
        int titleY = panelY + 10;

        // Glow behind title
        ctx.fill(titleX - 4, titleY - 2, titleX + titleW + 4, titleY + 11, 0x30008888);

        // Draw title char by char with RGB shift
        float hueBase = (System.currentTimeMillis() % 3000L) / 3000f;
        for (int i = 0; i < title.length(); i++) {
            float hue = (hueBase + i * 0.08f) % 1.0f;
            int rgb = Color.HSBtoRGB(hue, 0.7f, 1.0f);
            int charColor = 0xFF000000 | (rgb & 0x00FFFFFF);
            String ch = String.valueOf(title.charAt(i));
            ctx.drawTextWithShadow(textRenderer, ch, titleX, titleY, charColor);
            titleX += textRenderer.getWidth(ch);
        }

        // Subtitle
        String sub = "Visual Tools";
        int subW = textRenderer.getWidth(sub);
        ctx.drawTextWithShadow(textRenderer, sub, panelX + (panelW - subW) / 2, titleY + 12, TEXT_DIM);

        // Widgets
        for (Widget w : widgets) {
            w.render(ctx, textRenderer, mx, my, delta);
        }

        // Color preview swatch
        int swX = panelX + panelW - 30;
        int swY = panelY + 10;
        int col = (prefs.getTintA() << 24) | (prefs.getTintR() << 16) | (prefs.getTintG() << 8) | prefs.getTintB();
        ctx.fill(swX + 1, swY + 1, swX + 17, swY + 17, SHADOW);
        ctx.fill(swX, swY, swX + 16, swY + 16, col);
        drawBorder(ctx, swX - 1, swY - 1, 18, 18, 0xFF444466);
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        for (Widget w : widgets) {
            if (w.contains((int) mx, (int) my)) {
                w.onClick((int) mx, (int) my);
                if (w instanceof Slider) {
                    dragWidget = w;
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragWidget != null) {
            dragWidget.onDrag((int) mx, (int) my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragWidget = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void close() {
        prefs.save();
        if (client != null) client.setScreen(null);
    }

    // --- Utility ---

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void fillRounded(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    // === Widget system ===

    @FunctionalInterface
    interface ToggleCallback { void set(boolean v); }

    @FunctionalInterface
    interface SliderCallback { void set(double v); }

    static abstract class Widget {
        int x, y, w, h;
        void render(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int mx, int my, float delta) {}
        void onClick(int mx, int my) {}
        void onDrag(int mx, int my) {}
        boolean contains(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    // --- Section label ---

    static class SectionLabel extends Widget {
        final String label;

        SectionLabel(int x, int y, String label) {
            this.x = x; this.y = y; this.w = 0; this.h = 12;
            this.label = label;
        }

        @Override
        void render(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int mx, int my, float delta) {
            ctx.drawTextWithShadow(tr, label, x, y + 1, ACCENT);
            int tw = tr.getWidth(label);
            ctx.fill(x + tw + 4, y + 5, x + 236, y + 6, 0xFF2A2A3E);
        }

        @Override
        boolean contains(int mx, int my) { return false; }
    }

    // --- Toggle ---

    class Toggle extends Widget {
        final String label;
        boolean value;
        final ToggleCallback callback;
        float anim;

        Toggle(int x, int y, int w, int h, String label, boolean value, ToggleCallback cb) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.label = label;
            this.value = value;
            this.callback = cb;
            this.anim = value ? 1.0f : 0.0f;
        }

        @Override
        void render(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int mx, int my, float delta) {
            boolean hover = contains(mx, my);

            // Background
            fillRounded(ctx, x, y, w, h, hover ? BG_HOVER : BG_WIDGET);

            // Animate toggle
            float target = value ? 1.0f : 0.0f;
            anim += (target - anim) * 0.25f;

            // Toggle track
            int trkW = 18;
            int trkH = 8;
            int trkX = x + w - trkW - 4;
            int trkY = y + (h - trkH) / 2;

            int trackColor = blendColor(TOGGLE_OFF, TOGGLE_ON, anim);
            fillRounded(ctx, trkX, trkY, trkW, trkH, trackColor);

            // Toggle knob
            int knobR = 5;
            int knobRange = trkW - knobR * 2;
            int knobCx = trkX + knobR + (int)(anim * knobRange);
            int knobCy = trkY + trkH / 2;

            // Knob shadow
            ctx.fill(knobCx - knobR + 1, knobCy - knobR + 1, knobCx + knobR + 1, knobCy + knobR + 1, 0x40000000);
            // Knob
            ctx.fill(knobCx - knobR, knobCy - knobR, knobCx + knobR, knobCy + knobR, 0xFFE0E0E0);

            // Label
            int labelW = w - trkW - 10;
            String trimmed = label;
            while (tr.getWidth(trimmed) > labelW && trimmed.length() > 1) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            ctx.drawTextWithShadow(tr, trimmed, x + 4, y + (h - 8) / 2, value ? TEXT_PRIMARY : TEXT_DIM);
        }

        @Override
        void onClick(int mx, int my) {
            value = !value;
            callback.set(value);
        }
    }

    // --- Slider ---

    class Slider extends Widget {
        final String label;
        final float min, max;
        float value;
        final SliderCallback callback;
        final int color;

        Slider(int x, int y, int w, int h, String label, float min, float max, float value, SliderCallback cb, int color) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.label = label;
            this.min = min; this.max = max;
            this.value = value;
            this.callback = cb;
            this.color = color;
        }

        float fraction() {
            if (max <= min) return 0;
            return (value - min) / (max - min);
        }

        void setFromMouse(int mx) {
            int trackX = x + 2;
            int trackW = w - 4;
            float frac = Math.max(0, Math.min(1, (mx - trackX) / (float) trackW));
            value = min + frac * (max - min);
            if (max - min > 1 && max <= 255) {
                value = Math.round(value);
            }
            callback.set(value);
        }

        @Override
        void render(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int mx, int my, float delta) {
            boolean hover = contains(mx, my);

            fillRounded(ctx, x, y, w, h, hover ? BG_HOVER : BG_WIDGET);

            // Track
            int trkX = x + 2;
            int trkY = y + h - 5;
            int trkW = w - 4;
            int trkH = 3;

            ctx.fill(trkX, trkY, trkX + trkW, trkY + trkH, SLIDER_TRACK);

            // Fill
            int fillW = (int)(fraction() * trkW);
            if (fillW > 0) {
                ctx.fill(trkX, trkY, trkX + fillW, trkY + trkH, color);
            }

            // Knob
            int knobX = trkX + fillW;
            int knobR = 4;
            ctx.fill(knobX - knobR + 1, trkY - 2 + 1, knobX + knobR + 1, trkY + trkH + 2 + 1, 0x40000000);
            ctx.fill(knobX - knobR, trkY - 2, knobX + knobR, trkY + trkH + 2, 0xFFD0D0D0);

            // Label + value
            String valStr;
            if (max <= 255 && max - min >= 1) {
                valStr = String.valueOf((int) value);
            } else {
                valStr = String.format("%.1f", value);
            }
            ctx.drawTextWithShadow(tr, label, x + 4, y + 1, TEXT_DIM);
            int valW = tr.getWidth(valStr);
            ctx.drawTextWithShadow(tr, valStr, x + w - valW - 4, y + 1, TEXT_PRIMARY);
        }

        @Override
        void onClick(int mx, int my) {
            setFromMouse(mx);
        }

        @Override
        void onDrag(int mx, int my) {
            setFromMouse(mx);
        }
    }

    // --- Color blend utility ---

    private static int blendColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int)(a1 + (a2 - a1) * t);
        int r = (int)(r1 + (r2 - r1) * t);
        int g = (int)(g1 + (g2 - g1) * t);
        int b = (int)(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
