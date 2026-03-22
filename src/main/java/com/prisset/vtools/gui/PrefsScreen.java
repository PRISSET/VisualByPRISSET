package com.prisset.vtools.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PrefsScreen extends Screen {

    // Textures
    private static final Identifier TEX_WINDOW   = new Identifier("prisset-vtools", "textures/gui/window_bg.png");
    private static final Identifier TEX_SIDEBAR   = new Identifier("prisset-vtools", "textures/gui/sidebar_bg.png");
    private static final Identifier TEX_CARD_OFF  = new Identifier("prisset-vtools", "textures/gui/card_off.png");
    private static final Identifier TEX_CARD_ON   = new Identifier("prisset-vtools", "textures/gui/card_on.png");
    private static final Identifier TEX_CARD_HOV  = new Identifier("prisset-vtools", "textures/gui/card_hover.png");
    private static final Identifier TEX_PILL      = new Identifier("prisset-vtools", "textures/gui/pill.png");
    private static final Identifier TEX_GLOW      = new Identifier("prisset-vtools", "textures/gui/glow.png");
    private static final Identifier TEX_SEARCH    = new Identifier("prisset-vtools", "textures/gui/search_bar.png");
    private static final Identifier TEX_TOG_ON    = new Identifier("prisset-vtools", "textures/gui/toggle_on.png");
    private static final Identifier TEX_TOG_OFF   = new Identifier("prisset-vtools", "textures/gui/toggle_off.png");
    private static final Identifier TEX_SLD_TRK   = new Identifier("prisset-vtools", "textures/gui/slider_track.png");
    private static final Identifier TEX_SLD_FILL  = new Identifier("prisset-vtools", "textures/gui/slider_fill.png");
    private static final Identifier TEX_SLD_KNOB  = new Identifier("prisset-vtools", "textures/gui/slider_knob.png");
    private static final Identifier TEX_DOT_ON    = new Identifier("prisset-vtools", "textures/gui/dot_on.png");
    private static final Identifier TEX_DOT_OFF   = new Identifier("prisset-vtools", "textures/gui/dot_off.png");
    private static final Identifier TEX_ICON_R    = new Identifier("prisset-vtools", "textures/gui/icon_render.png");
    private static final Identifier TEX_ICON_V    = new Identifier("prisset-vtools", "textures/gui/icon_visual.png");
    private static final Identifier TEX_ICON_S    = new Identifier("prisset-vtools", "textures/gui/icon_settings.png");
    private static final Identifier TEX_ICON_LOGO = new Identifier("prisset-vtools", "textures/gui/icon_logo.png");
    private static final Identifier TEX_SETTINGS  = new Identifier("prisset-vtools", "textures/gui/settings_panel.png");
    private static final Identifier TEX_HLINE     = new Identifier("prisset-vtools", "textures/gui/header_line.png");

    // Font
    private static final Identifier FONT_ID = new Identifier("prisset-vtools", "vtools");
    private static Style fontStyle() { return Style.EMPTY.withFont(FONT_ID); }

    // Layout
    private static final int W = 460, H = 310;
    private static final int SB_W = 120;
    private static final int HDR_H = 38;
    private static final int CARD_W = 152, CARD_H = 62, CARD_GAP = 8;

    private final DisplayPrefs prefs;
    private int wx, wy;
    private final List<Cat> cats = new ArrayList<>();
    private Cat activeCat;
    private long openTime;
    private Mod settingsMod;
    private final List<SWidget> sWidgets = new ArrayList<>();
    private SWidget sDrag;

    public PrefsScreen(DisplayPrefs prefs) {
        super(Text.literal("PRISSET"));
        this.prefs = prefs;
    }

    @Override
    protected void init() {
        wx = (width - W) / 2;
        wy = (height - H) / 2;
        openTime = System.currentTimeMillis();
        settingsMod = null;
        sDrag = null;
        buildCats();
        activeCat = cats.get(0);
    }

    private void buildCats() {
        cats.clear();

        Cat r = new Cat("\u0420\u0435\u043d\u0434\u0435\u0440", TEX_ICON_R);
        r.add(new Mod("\u041e\u0432\u0435\u0440\u043b\u0435\u0439", "\u041f\u043e\u043a\u0430\u0437 \u0433\u0440\u0430\u043d\u0438\u0446 \u043c\u043e\u0434\u0435\u043b\u0435\u0439 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0435\u0439", prefs::isActive, v -> prefs.setActive(v), this::bOverlay));
        r.add(new Mod("RGB", "\u0420\u0430\u0434\u0443\u0436\u043d\u0430\u044f \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u044f \u0446\u0432\u0435\u0442\u043e\u0432\u043e\u0433\u043e \u043e\u0432\u0435\u0440\u043b\u0435\u044f", prefs::isRgbMode, v -> prefs.setRgbMode(v), this::bRgb));
        r.add(new Mod("\u0417\u0434\u043e\u0440\u043e\u0432\u044c\u0435", "\u041e\u0442\u043e\u0431\u0440\u0430\u0436\u0435\u043d\u0438\u0435 HP \u043d\u0430\u0434 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u044f\u043c\u0438", prefs::isHealthBars, v -> prefs.setHealthBars(v), null));
        r.add(new Mod("\u0421\u043b\u0435\u0434", "\u041f\u043e\u043b\u0443\u043f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u0430\u044f \u043b\u0438\u043d\u0438\u044f \u0437\u0430 \u0438\u0433\u0440\u043e\u043a\u0430\u043c\u0438", prefs::isTrailEnabled, v -> prefs.setTrailEnabled(v), this::bTrail));

        Cat v = new Cat("\u0412\u0438\u0437\u0443\u0430\u043b", TEX_ICON_V);
        v.add(new Mod("\u0417\u0443\u043c", "\u041f\u0440\u0438\u0431\u043b\u0438\u0436\u0435\u043d\u0438\u0435 \u043d\u0430 \u043a\u043b\u0430\u0432\u0438\u0448\u0443 C", () -> true, x -> {}, this::bZoom));

        Cat s = new Cat("\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438", TEX_ICON_S);
        s.add(new Mod("\u0426\u0432\u0435\u0442", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 RGBA \u043e\u0432\u0435\u0440\u043b\u0435\u044f", () -> true, x -> {}, this::bColor));
        s.add(new Mod("\u0424\u0438\u043b\u044c\u0442\u0440\u044b", "\u0412\u044b\u0431\u043e\u0440 \u0442\u0438\u043f\u043e\u0432 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0435\u0439 \u0434\u043b\u044f \u043e\u0432\u0435\u0440\u043b\u0435\u044f", () -> true, x -> {}, this::bFilter));

        cats.add(r); cats.add(v); cats.add(s);
    }

    // Settings builders
    private void bOverlay() { sWidgets.clear(); sWidgets.add(new SSlider("\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c", 0,255, prefs.getTintA(), x -> prefs.setTintA(x.intValue()))); }
    private void bRgb() { sWidgets.clear(); sWidgets.add(new SSlider("\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c", 0.1f,5f, prefs.getRgbSpeed(), x -> prefs.setRgbSpeed(x.floatValue()))); }
    private void bTrail() { sWidgets.clear(); sWidgets.add(new SSlider("\u0414\u043b\u0438\u043d\u0430", 5,40, prefs.getTrailLength(), x -> prefs.setTrailLength(x.intValue()))); }
    private void bZoom() { sWidgets.clear(); sWidgets.add(new SSlider("\u0421\u0438\u043b\u0430", 1.5f,10f, prefs.getZoomStrength(), x -> prefs.setZoomStrength(x.floatValue()))); }
    private void bColor() {
        sWidgets.clear();
        sWidgets.add(new SSlider("\u041a\u0440\u0430\u0441\u043d\u044b\u0439", 0,255, prefs.getTintR(), x -> prefs.setTintR(x.intValue())));
        sWidgets.add(new SSlider("\u0417\u0435\u043b\u0451\u043d\u044b\u0439", 0,255, prefs.getTintG(), x -> prefs.setTintG(x.intValue())));
        sWidgets.add(new SSlider("\u0421\u0438\u043d\u0438\u0439", 0,255, prefs.getTintB(), x -> prefs.setTintB(x.intValue())));
        sWidgets.add(new SSlider("\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c", 0,255, prefs.getTintA(), x -> prefs.setTintA(x.intValue())));
    }
    private void bFilter() {
        sWidgets.clear();
        sWidgets.add(new SToggle("\u0418\u0433\u0440\u043e\u043a\u0438", prefs::isFilterPlayers, x -> prefs.setFilterPlayers(x)));
        sWidgets.add(new SToggle("\u041c\u043e\u0431\u044b", prefs::isFilterMobs, x -> prefs.setFilterMobs(x)));
        sWidgets.add(new SToggle("\u041f\u0440\u0435\u0434\u043c\u0435\u0442\u044b", prefs::isFilterDrops, x -> prefs.setFilterDrops(x)));
        sWidgets.add(new SToggle("\u0421\u043d\u0430\u0440\u044f\u0434\u044b", prefs::isFilterProjectiles, x -> prefs.setFilterProjectiles(x)));
    }

    // === RENDER ===

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        float openA = Math.min(1f, (now - openTime) / 250f);
        float ease = easeOut(openA);
        int dimA = (int)(ease * 160);
        ctx.fill(0, 0, width, height, dimA << 24);

        // Scale animation
        if (openA < 1f) {
            float sc = 0.88f + 0.12f * ease;
            ctx.getMatrices().push();
            float cx = wx + W / 2f, cy = wy + H / 2f;
            ctx.getMatrices().translate(cx, cy, 0);
            ctx.getMatrices().scale(sc, sc, 1f);
            ctx.getMatrices().translate(-cx, -cy, 0);
        }

        // Window bg texture (stretched)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        tex(ctx, TEX_WINDOW, wx, wy, W, H);

        // Sidebar
        tex(ctx, TEX_SIDEBAR, wx, wy, SB_W, H);

        // Logo
        tex(ctx, TEX_ICON_LOGO, wx + 8, wy + 6, 24, 24);
        drawFont(ctx, "PRISSET", wx + 36, wy + 12, 0xFFE8E8F0);

        // Sidebar divider line
        ctx.fill(wx + SB_W - 1, wy, wx + SB_W, wy + H, 0x30FFFFFF);

        // Category list
        int cy = wy + 42;
        for (int i = 0; i < cats.size(); i++) {
            Cat c = cats.get(i);
            boolean act = c == activeCat;
            boolean hov = mx >= wx && mx < wx + SB_W && my >= cy && my < cy + 28;
            c.ry = cy;

            if (act) {
                tex(ctx, TEX_PILL, wx + 4, cy, SB_W - 8, 28);
            } else if (hov) {
                ctx.fill(wx + 4, cy, wx + SB_W - 4, cy + 28, 0x18FFFFFF);
            }

            // Category icon
            tex(ctx, c.icon, wx + 12, cy + 4, 20, 20);
            // Category label
            drawFont(ctx, c.name, wx + 36, cy + 10, act ? 0xFFFFFFFF : 0xFFA0A0B8);
            cy += 32;
        }

        // Bottom version
        drawFont(ctx, "v1.0.0", wx + 14, wy + H - 16, 0xFF505068);

        // Header
        int hx = wx + SB_W;
        ctx.fill(hx, wy, wx + W, wy + HDR_H, 0xE0161628);

        // Search bar texture
        tex(ctx, TEX_SEARCH, hx + 10, wy + 7, W - SB_W - 20, 24);
        drawFont(ctx, "\u2315  " + (activeCat != null ? activeCat.name : ""), hx + 20, wy + 14, 0xFF6E6E88);

        // Header line
        tex(ctx, TEX_HLINE, hx, wy + HDR_H - 1, W - SB_W, 2);

        // Content area
        drawContent(ctx, mx, my, now);

        // Settings overlay
        if (settingsMod != null) drawSettings(ctx, mx, my);

        if (openA < 1f) ctx.getMatrices().pop();
        RenderSystem.disableBlend();
    }

    private void drawContent(DrawContext ctx, int mx, int my, long now) {
        if (activeCat == null) return;
        int cx = wx + SB_W;
        int cy = wy + HDR_H;
        int cw = W - SB_W;
        int ch = H - HDR_H;
        int pad = 10;
        int cols = 2;

        for (int i = 0; i < activeCat.mods.size(); i++) {
            Mod m = activeCat.mods.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = cx + pad + col * (CARD_W + CARD_GAP);
            int y = cy + pad + row * (CARD_H + CARD_GAP);

            if (y + CARD_H < cy || y > cy + ch) { m.lx = -1; continue; }
            m.lx = x; m.ly = y;

            boolean on = m.get.get();
            boolean hov = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H && my >= cy;

            // Entrance animation
            float ent = Math.min(1f, Math.max(0f, (now - openTime - i * 50L) / 200f));
            ent = easeOut(ent);

            if (ent < 1f) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x + CARD_W / 2f, y + CARD_H / 2f, 0);
                ctx.getMatrices().scale(ent, ent, 1f);
                ctx.getMatrices().translate(-(x + CARD_W / 2f), -(y + CARD_H / 2f), 0);
            }

            // Glow behind active cards
            if (on) {
                float pulse = (float)(Math.sin(now / 600.0 + i * 0.7) * 0.15 + 0.85);
                RenderSystem.setShaderColor(1f, 1f, 1f, pulse * 0.6f);
                tex(ctx, TEX_GLOW, x - 14, y - 9, CARD_W + 28, CARD_H + 18);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }

            // Card texture
            Identifier cardTex = on ? TEX_CARD_ON : (hov ? TEX_CARD_HOV : TEX_CARD_OFF);
            tex(ctx, cardTex, x, y, CARD_W, CARD_H);

            // Status dot
            tex(ctx, on ? TEX_DOT_ON : TEX_DOT_OFF, x + CARD_W - 16, y + 8, 8, 8);

            // Module name (bold via custom font)
            drawFont(ctx, m.name, x + 10, y + 10, on ? 0xFFFFFFFF : 0xFFCCCCDD);

            // Settings gear icon
            if (m.sBuild != null) {
                drawFont(ctx, "\u2699", x + textRenderer.getWidth(Text.literal(m.name).setStyle(fontStyle())) + 14, y + 10, 0xFF8888A0);
            }

            // Description
            String desc = m.desc;
            int maxDW = CARD_W - 18;
            List<String> lines = wrapText(desc, maxDW);
            int dy = y + 26;
            for (int li = 0; li < Math.min(lines.size(), 2); li++) {
                drawFont(ctx, lines.get(li), x + 10, dy, 0xFF9090A8);
                dy += 12;
            }

            if (ent < 1f) ctx.getMatrices().pop();
        }
    }

    private void drawSettings(DrawContext ctx, int mx, int my) {
        int ox = wx + SB_W, oy = wy + HDR_H;
        int ow = W - SB_W, oh = H - HDR_H;
        ctx.fill(ox, oy, ox + ow, oy + oh, 0xC0080810);

        int pw = 200, ih = 28;
        int ph = 38 + sWidgets.size() * ih + 10;
        int px = ox + (ow - pw) / 2;
        int py = oy + (oh - ph) / 2;

        tex(ctx, TEX_SETTINGS, px, py, pw, ph);

        drawFont(ctx, settingsMod.name, px + 12, py + 12, 0xFFE8E8F0);
        drawFont(ctx, "\u2715", px + pw - 18, py + 12, 0xFF6E6E88);

        int wy2 = py + 34;
        for (SWidget sw : sWidgets) {
            sw.x = px + 12; sw.y = wy2; sw.w = pw - 24; sw.h = 22;
            sw.render(ctx, this, mx, my);
            wy2 += ih;
        }

        // Color preview
        if (settingsMod.name.equals("\u0426\u0432\u0435\u0442")) {
            int c = (prefs.getTintA() << 24) | (prefs.getTintR() << 16) | (prefs.getTintG() << 8) | prefs.getTintB();
            ctx.fill(px + pw - 30, py + 8, px + pw - 14, py + 24, c);
        }
    }

    // Font helper
    private void drawFont(DrawContext ctx, String text, int x, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(text).setStyle(fontStyle()), x, y, color);
    }

    // Text wrapping
    private List<String> wrapText(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (textRenderer.getWidth(Text.literal(test).setStyle(fontStyle())) > maxW && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(w);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(w);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    // Texture draw helper
    private static void tex(DrawContext ctx, Identifier id, int x, int y, int w, int h) {
        ctx.drawTexture(id, x, y, 0, 0, w, h, w, h);
    }

    // === INPUT ===

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int imx = (int) mx, imy = (int) my;
        if (settingsMod != null) {
            for (SWidget sw : sWidgets) {
                if (sw.contains(imx, imy)) { sw.onClick(imx); if (sw instanceof SSlider) sDrag = sw; return true; }
            }
            settingsMod = null; sWidgets.clear(); return true;
        }
        for (Cat c : cats) {
            if (imx >= wx && imx < wx + SB_W && imy >= c.ry && imy < c.ry + 28) { activeCat = c; return true; }
        }
        if (activeCat != null) {
            for (Mod m : activeCat.mods) {
                if (m.lx < 0) continue;
                if (imx >= m.lx && imx < m.lx + CARD_W && imy >= m.ly && imy < m.ly + CARD_H) {
                    if (btn == 1 && m.sBuild != null) { settingsMod = m; m.sBuild.run(); return true; }
                    m.set.accept(!m.get.get()); return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (sDrag != null) { sDrag.onDrag((int) mx); return true; }
        return super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) { sDrag = null; return super.mouseReleased(mx, my, b); }
    @Override public boolean keyPressed(int k, int s, int m) {
        if (settingsMod != null && k == 256) { settingsMod = null; sWidgets.clear(); return true; }
        return super.keyPressed(k, s, m);
    }
    @Override public void close() { prefs.save(); if (client != null) client.setScreen(null); }
    @Override public boolean shouldPause() { return false; }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t); }

    // === DATA ===
    static class Cat { String name; Identifier icon; List<Mod> mods = new ArrayList<>(); int ry;
        Cat(String n, Identifier i) { name = n; icon = i; } void add(Mod m) { mods.add(m); } }
    static class Mod { String name, desc; Supplier<Boolean> get; Consumer<Boolean> set; Runnable sBuild; int lx=-1, ly;
        Mod(String n, String d, Supplier<Boolean> g, Consumer<Boolean> s, Runnable sb) { name=n; desc=d; get=g; set=s; sBuild=sb; } }

    // === SETTINGS WIDGETS ===
    static abstract class SWidget { int x,y,w,h;
        abstract void render(DrawContext ctx, PrefsScreen scr, int mx, int my);
        void onClick(int mx) {} void onDrag(int mx) {}
        boolean contains(int mx, int my) { return mx>=x && mx<x+w && my>=y && my<y+h; }
    }

    static class SSlider extends SWidget {
        String label; float min,max,val; Consumer<Double> cb;
        SSlider(String l, float mn, float mx, float v, Consumer<Double> c) { label=l; min=mn; max=mx; val=v; cb=c; }
        float frac() { return max<=min?0:Math.max(0,Math.min(1,(val-min)/(max-min))); }
        void apply(int mx) { float f=Math.max(0,Math.min(1,(mx-x)/(float)w)); val=min+f*(max-min);
            if(max-min>=1&&max<=255) val=Math.round(val); cb.accept((double)val); }
        @Override void render(DrawContext ctx, PrefsScreen scr, int mx, int my) {
            String vs = (max<=255&&max-min>=1) ? ""+(int)val : String.format("%.1f",val);
            scr.drawFont(ctx, label, x, y+1, 0xFF9090A8);
            int vw = scr.textRenderer.getWidth(Text.literal(vs).setStyle(fontStyle()));
            scr.drawFont(ctx, vs, x+w-vw, y+1, 0xFFE8E8F0);
            int ty=y+14, tw=w;
            tex(ctx, TEX_SLD_TRK, x, ty, tw, 6);
            int fw=(int)(frac()*tw);
            if(fw>0) tex(ctx, TEX_SLD_FILL, x, ty, fw, 6);
            tex(ctx, TEX_SLD_KNOB, x+fw-6, ty-5, 12, 16);
        }
        @Override void onClick(int mx) { apply(mx); }
        @Override void onDrag(int mx) { apply(mx); }
    }

    static class SToggle extends SWidget {
        String label; Supplier<Boolean> get; Consumer<Boolean> set;
        SToggle(String l, Supplier<Boolean> g, Consumer<Boolean> s) { label=l; get=g; set=s; }
        @Override void render(DrawContext ctx, PrefsScreen scr, int mx, int my) {
            boolean on = get.get();
            scr.drawFont(ctx, label, x, y+5, on ? 0xFFE8E8F0 : 0xFF9090A8);
            tex(ctx, on ? TEX_TOG_ON : TEX_TOG_OFF, x+w-36, y+1, 36, 20);
        }
        @Override void onClick(int mx) { set.accept(!get.get()); }
    }
}
