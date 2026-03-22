package com.prisset.vtools.gui;

import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PrefsScreen extends Screen {

    // Celestial-like palette
    private static final int C_WIN_BG       = 0xF0161622;
    private static final int C_SIDEBAR_BG   = 0xF01C1C30;
    private static final int C_CONTENT_BG   = 0xF0121220;
    private static final int C_HEADER_BG    = 0xF0181828;
    private static final int C_ACCENT       = 0xFFE8489C;
    private static final int C_ACCENT2      = 0xFFA855F7;
    private static final int C_CARD_BG      = 0xE81E1E32;
    private static final int C_CARD_ON1     = 0xE8E8489C;
    private static final int C_CARD_ON2     = 0xE8A855F7;
    private static final int C_CARD_HOVER   = 0xE8282840;
    private static final int C_TEXT         = 0xFFE8E8F0;
    private static final int C_TEXT_DIM     = 0xFF6E6E88;
    private static final int C_TEXT_DESC    = 0xFFB0B0C8;
    private static final int C_CAT_ACTIVE   = 0xFF3D2D58;
    private static final int C_DOT_ON       = 0xFF4ADE80;
    private static final int C_DOT_OFF      = 0xFF52525E;
    private static final int C_DIVIDER      = 0xFF2A2A40;
    private static final int C_SEARCH_BG    = 0xFF22222E;
    private static final int C_TOGGLE_ON    = 0xFF4ADE80;
    private static final int C_TOGGLE_OFF   = 0xFF52525E;
    private static final int C_SLIDER_TRK   = 0xFF2A2A40;
    private static final int C_KNOB         = 0xFFE0E0E8;

    // Layout
    private static final int W = 440, H = 300;
    private static final int SB_W = 110;
    private static final int HDR_H = 34;
    private static final int CARD_W = 148, CARD_H = 56, CARD_GAP = 6;

    private final DisplayPrefs prefs;
    private int wx, wy;
    private final List<Category> cats = new ArrayList<>();
    private Category activeCat;
    private long openTime;

    // Settings overlay
    private Module settingsModule;
    private final List<SettingsWidget> sWidgets = new ArrayList<>();
    private SettingsWidget sDrag;

    // Per-card hover animation
    private final float[] cardHover = new float[32];
    // Per-category hover
    private final float[] catHover = new float[16];

    public PrefsScreen(DisplayPrefs prefs) {
        super(Text.literal("PRISSET"));
        this.prefs = prefs;
    }

    @Override
    protected void init() {
        wx = (width - W) / 2;
        wy = (height - H) / 2;
        openTime = System.currentTimeMillis();
        settingsModule = null;
        sDrag = null;
        java.util.Arrays.fill(cardHover, 0f);
        java.util.Arrays.fill(catHover, 0f);
        buildCategories();
        activeCat = cats.get(0);
    }

    private void buildCategories() {
        cats.clear();

        Category rndr = cat("\u25C8  \u0420\u0435\u043d\u0434\u0435\u0440");
        rndr.add(mod("\u041e\u0432\u0435\u0440\u043b\u0435\u0439", "\u041f\u043e\u043a\u0430\u0437 \u0433\u0440\u0430\u043d\u0438\u0446 \u043c\u043e\u0434\u0435\u043b\u0435\u0439", prefs::isActive, v -> prefs.setActive(v), this::bldOverlay));
        rndr.add(mod("RGB", "\u0420\u0430\u0434\u0443\u0436\u043d\u0430\u044f \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u044f", prefs::isRgbMode, v -> prefs.setRgbMode(v), this::bldRgb));
        rndr.add(mod("\u0417\u0434\u043e\u0440\u043e\u0432\u044c\u0435", "\u0425\u041f \u043d\u0430\u0434 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u044f\u043c\u0438", prefs::isHealthBars, v -> prefs.setHealthBars(v), null));
        rndr.add(mod("\u0421\u043b\u0435\u0434", "\u041b\u0438\u043d\u0438\u044f \u0437\u0430 \u0438\u0433\u0440\u043e\u043a\u0430\u043c\u0438", prefs::isTrailEnabled, v -> prefs.setTrailEnabled(v), this::bldTrail));

        Category vis = cat("\u25CE  \u0412\u0438\u0437\u0443\u0430\u043b");
        vis.add(mod("\u0417\u0443\u043c", "\u041f\u0440\u0438\u0431\u043b\u0438\u0436\u0435\u043d\u0438\u0435 \u043d\u0430 \u043a\u043b\u0430\u0432\u0438\u0448\u0443 C", () -> true, v -> {}, this::bldZoom));

        Category cfg = cat("\u2699  \u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438");
        cfg.add(mod("\u0426\u0432\u0435\u0442", "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 RGBA", () -> true, v -> {}, this::bldColor));
        cfg.add(mod("\u0424\u0438\u043b\u044c\u0442\u0440\u044b", "\u0422\u0438\u043f\u044b \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0435\u0439", () -> true, v -> {}, this::bldFilter));

        cats.add(rndr);
        cats.add(vis);
        cats.add(cfg);
    }

    private Category cat(String n) { return new Category(n); }
    private Module mod(String n, String d, Supplier<Boolean> g, Consumer<Boolean> s, Runnable sb) {
        return new Module(n, d, g, s, sb);
    }

    // Settings builders
    private void bldOverlay() {
        sWidgets.clear();
        sWidgets.add(new SSlider("\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c", 0, 255, prefs.getTintA(), v -> prefs.setTintA(v.intValue()), C_TEXT_DIM));
    }
    private void bldRgb() {
        sWidgets.clear();
        sWidgets.add(new SSlider("\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c", 0.1f, 5f, prefs.getRgbSpeed(), v -> prefs.setRgbSpeed(v.floatValue()), C_ACCENT));
    }
    private void bldTrail() {
        sWidgets.clear();
        sWidgets.add(new SSlider("\u0414\u043b\u0438\u043d\u0430", 5, 40, prefs.getTrailLength(), v -> prefs.setTrailLength(v.intValue()), 0xFFFF9F0A));
    }
    private void bldZoom() {
        sWidgets.clear();
        sWidgets.add(new SSlider("\u0421\u0438\u043b\u0430", 1.5f, 10f, prefs.getZoomStrength(), v -> prefs.setZoomStrength(v.floatValue()), 0xFF38BDF8));
    }
    private void bldColor() {
        sWidgets.clear();
        sWidgets.add(new SSlider("\u041a\u0440\u0430\u0441\u043d\u044b\u0439", 0, 255, prefs.getTintR(), v -> prefs.setTintR(v.intValue()), 0xFFFF453A));
        sWidgets.add(new SSlider("\u0417\u0435\u043b\u0451\u043d\u044b\u0439", 0, 255, prefs.getTintG(), v -> prefs.setTintG(v.intValue()), 0xFF4ADE80));
        sWidgets.add(new SSlider("\u0421\u0438\u043d\u0438\u0439", 0, 255, prefs.getTintB(), v -> prefs.setTintB(v.intValue()), 0xFF38BDF8));
        sWidgets.add(new SSlider("\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c", 0, 255, prefs.getTintA(), v -> prefs.setTintA(v.intValue()), 0xFF8E8E93));
    }
    private void bldFilter() {
        sWidgets.clear();
        sWidgets.add(new SToggle("\u0418\u0433\u0440\u043e\u043a\u0438", prefs::isFilterPlayers, v -> prefs.setFilterPlayers(v)));
        sWidgets.add(new SToggle("\u041c\u043e\u0431\u044b", prefs::isFilterMobs, v -> prefs.setFilterMobs(v)));
        sWidgets.add(new SToggle("\u041f\u0440\u0435\u0434\u043c\u0435\u0442\u044b", prefs::isFilterDrops, v -> prefs.setFilterDrops(v)));
        sWidgets.add(new SToggle("\u0421\u043d\u0430\u0440\u044f\u0434\u044b", prefs::isFilterProjectiles, v -> prefs.setFilterProjectiles(v)));
    }

    // === RENDER ===

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        float openAnim = Math.min(1f, (System.currentTimeMillis() - openTime) / 200f);
        float scale = 0.85f + 0.15f * easeOut(openAnim);
        int alpha = (int)(openAnim * 180);

        // Dim bg
        ctx.fill(0, 0, width, height, (alpha << 24));

        if (openAnim < 1f) {
            ctx.getMatrices().push();
            float cx = wx + W / 2f, cy = wy + H / 2f;
            ctx.getMatrices().translate(cx, cy, 0);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.getMatrices().translate(-cx, -cy, 0);
        }

        // Shadow
        ctx.fill(wx + 5, wy + 5, wx + W + 5, wy + H + 5, 0x50000000);

        // Window
        ctx.fill(wx, wy, wx + W, wy + H, C_WIN_BG);

        drawSidebar(ctx, mx, my, delta);
        drawHeader(ctx, mx, my);
        drawContent(ctx, mx, my, delta);

        if (settingsModule != null) drawSettings(ctx, mx, my);

        // Window border glow
        drawBdr(ctx, wx, wy, W, H, C_DIVIDER);

        if (openAnim < 1f) {
            ctx.getMatrices().pop();
        }
    }

    private void drawSidebar(DrawContext ctx, int mx, int my, float delta) {
        int sx = wx, sy = wy, sh = H;
        ctx.fill(sx, sy, sx + SB_W, sy + sh, C_SIDEBAR_BG);
        ctx.fill(sx + SB_W - 1, sy, sx + SB_W, sy + sh, C_DIVIDER);

        // Logo animated
        long t = System.currentTimeMillis();
        float hBase = (t % 5000L) / 5000f;
        String logo = "PRISSET";
        int lw = textRenderer.getWidth(logo);
        int lx = sx + (SB_W - lw) / 2;
        int ly = sy + 10;

        // Glow pulse
        float pulse = (float)(Math.sin(t / 400.0) * 0.3 + 0.7);
        int ga = (int)(pulse * 40);
        ctx.fill(lx - 6, ly - 3, lx + lw + 6, ly + 12, (ga << 24) | (0xE8489C & 0x00FFFFFF));

        for (int i = 0; i < logo.length(); i++) {
            float hue = (hBase + i * 0.06f) % 1f;
            int rgb = Color.HSBtoRGB(hue, 0.55f, 1f);
            ctx.drawTextWithShadow(textRenderer, String.valueOf(logo.charAt(i)), lx, ly, 0xFF000000 | (rgb & 0xFFFFFF));
            lx += textRenderer.getWidth(String.valueOf(logo.charAt(i)));
        }

        // Categories
        int cy = sy + 32;
        for (int ci = 0; ci < cats.size(); ci++) {
            Category c = cats.get(ci);
            boolean act = c == activeCat;
            boolean hov = mx >= sx && mx < sx + SB_W && my >= cy && my < cy + 26;

            // Hover animation
            float tgt = (act || hov) ? 1f : 0f;
            if (ci < catHover.length) {
                catHover[ci] += (tgt - catHover[ci]) * 0.18f;
            }
            float anim = ci < catHover.length ? catHover[ci] : 0f;

            if (act) {
                // Active: accent bg + left bar
                int abg = blendAlpha(C_CAT_ACTIVE, anim);
                ctx.fill(sx + 2, cy, sx + SB_W - 1, cy + 26, abg);
                ctx.fill(sx, cy + 2, sx + 3, cy + 24, C_ACCENT);
            } else if (anim > 0.01f) {
                ctx.fill(sx + 2, cy, sx + SB_W - 1, cy + 26, blendAlpha(0x20FFFFFF, anim));
            }

            ctx.drawTextWithShadow(textRenderer, c.name, sx + 10, cy + 9, act ? C_TEXT : C_TEXT_DIM);
            c.ry = cy;
            cy += 28;
        }

        // Bottom
        ctx.drawTextWithShadow(textRenderer, "\u00a78v1.0.0", sx + 8, sy + sh - 14, C_TEXT_DIM);
    }

    private void drawHeader(DrawContext ctx, int mx, int my) {
        int hx = wx + SB_W, hy = wy;
        int hw = W - SB_W;
        ctx.fill(hx, hy, hx + hw, hy + HDR_H, C_HEADER_BG);
        ctx.fill(hx, hy + HDR_H - 1, hx + hw, hy + HDR_H, C_DIVIDER);

        // Search bar decoration
        int srchX = hx + 10, srchY = hy + 8, srchW = hw - 20, srchH = 18;
        fillRound(ctx, srchX, srchY, srchW, srchH, C_SEARCH_BG);
        ctx.drawTextWithShadow(textRenderer, "\u00a78\u2315  " + (activeCat != null ? activeCat.name : ""), srchX + 6, srchY + 5, C_TEXT_DIM);

        // Module count
        if (activeCat != null) {
            String cnt = activeCat.mods.size() + "";
            int cw = textRenderer.getWidth(cnt);
            ctx.fill(hx + hw - cw - 22, srchY + 2, hx + hw - 18, srchY + srchH - 2, C_DIVIDER);
            ctx.drawTextWithShadow(textRenderer, cnt, hx + hw - cw - 20, srchY + 5, C_TEXT_DIM);
        }
    }

    private void drawContent(DrawContext ctx, int mx, int my, float delta) {
        if (activeCat == null) return;
        int cx = wx + SB_W + 1;
        int cy = wy + HDR_H;
        int cw = W - SB_W - 1;
        int ch = H - HDR_H;
        ctx.fill(cx, cy, cx + cw, cy + ch, C_CONTENT_BG);

        int pad = 10;
        int cols = 2;
        long now = System.currentTimeMillis();

        for (int i = 0; i < activeCat.mods.size(); i++) {
            Module m = activeCat.mods.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = cx + pad + col * (CARD_W + CARD_GAP);
            int y = cy + pad + row * (CARD_H + CARD_GAP);

            if (y + CARD_H < cy || y > cy + ch) { m.lx = -1; continue; }
            m.lx = x; m.ly = y;

            boolean on = m.getter.get();
            boolean hov = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H && my >= cy;

            // Hover anim
            float htgt = hov ? 1f : 0f;
            if (i < cardHover.length) {
                cardHover[i] += (htgt - cardHover[i]) * 0.15f;
            }
            float ha = i < cardHover.length ? cardHover[i] : 0f;

            // Card entrance animation
            float entrance = Math.min(1f, (now - openTime - i * 40L) / 250f);
            entrance = Math.max(0f, entrance);
            entrance = easeOut(entrance);
            int cardAlpha = (int)(entrance * 232);

            // Card background
            if (on) {
                // Gradient: accent1 -> accent2 with pulse
                float p = (float)(Math.sin(now / 800.0 + i * 0.5) * 0.08 + 0.92);
                int c1 = applyAlpha(lerpColor(C_CARD_ON1, C_CARD_ON2, (float)(i % 3) / 2f), (int)(cardAlpha * p));
                ctx.fill(x, y, x + CARD_W, y + CARD_H, c1);
                // Inner glow top
                ctx.fill(x, y, x + CARD_W, y + 2, applyAlpha(0xFFFFFFFF, (int)(30 * p)));
            } else {
                int bg = ha > 0.01f ? lerpColor(C_CARD_BG, C_CARD_HOVER, ha) : C_CARD_BG;
                ctx.fill(x, y, x + CARD_W, y + CARD_H, applyAlpha(bg, cardAlpha));
            }

            // Left accent strip (on)
            if (on) {
                float glow = (float)(Math.sin(now / 600.0 + i) * 0.3 + 0.7);
                ctx.fill(x, y + 2, x + 3, y + CARD_H - 2, applyAlpha(C_ACCENT, (int)(255 * glow)));
            }

            // Border
            drawBdr(ctx, x, y, CARD_W, CARD_H, on ? applyAlpha(C_ACCENT, 80) : C_DIVIDER);

            // Status dot
            int dotX = x + CARD_W - 14, dotY = y + 7;
            int dotC = on ? C_DOT_ON : C_DOT_OFF;
            ctx.fill(dotX, dotY, dotX + 6, dotY + 6, dotC);
            if (on) {
                // Dot glow
                ctx.fill(dotX - 1, dotY - 1, dotX + 7, dotY + 7, applyAlpha(C_DOT_ON, 40));
            }

            // Name
            ctx.drawTextWithShadow(textRenderer, m.name, x + 8, y + 7, on ? C_TEXT : C_TEXT_DIM);

            // Description (up to 2 lines)
            String d = m.desc;
            int maxW = CARD_W - 16;
            if (textRenderer.getWidth(d) > maxW) {
                // Line 1
                String l1 = trimToWidth(d, maxW);
                ctx.drawTextWithShadow(textRenderer, l1, x + 8, y + 22, C_TEXT_DESC);
                String l2 = d.substring(l1.length()).trim();
                if (textRenderer.getWidth(l2) > maxW) l2 = trimToWidth(l2, maxW - 8) + "..";
                if (!l2.isEmpty()) ctx.drawTextWithShadow(textRenderer, l2, x + 8, y + 33, C_TEXT_DESC);
            } else {
                ctx.drawTextWithShadow(textRenderer, d, x + 8, y + 22, C_TEXT_DESC);
            }

            // Settings icon hint
            if (m.sBuild != null && ha > 0.3f) {
                int ia = (int)((ha - 0.3f) / 0.7f * 200);
                ctx.drawTextWithShadow(textRenderer, "\u2699", x + CARD_W - 14, y + CARD_H - 14, applyAlpha(C_TEXT_DIM, ia));
            }
        }
    }

    private void drawSettings(DrawContext ctx, int mx, int my) {
        int ox = wx + SB_W, oy = wy + HDR_H;
        int ow = W - SB_W, oh = H - HDR_H;
        ctx.fill(ox, oy, ox + ow, oy + oh, 0xC0101020);

        int pw = 190;
        int ih = 26;
        int ph = 32 + sWidgets.size() * ih + 8;
        int px = ox + (ow - pw) / 2;
        int py = oy + (oh - ph) / 2;

        // Panel shadow
        ctx.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, 0x40000000);
        // Panel
        fillRound(ctx, px, py, pw, ph, C_SIDEBAR_BG);
        ctx.fill(px, py, px + pw, py + 2, C_ACCENT);
        drawBdr(ctx, px, py, pw, ph, C_DIVIDER);

        // Title
        ctx.drawTextWithShadow(textRenderer, settingsModule.name, px + 10, py + 10, C_TEXT);
        ctx.drawTextWithShadow(textRenderer, "\u00a78\u2715", px + pw - 16, py + 10, C_TEXT_DIM);

        // Widgets
        int wy2 = py + 28;
        for (SettingsWidget sw : sWidgets) {
            sw.x = px + 8; sw.y = wy2; sw.w = pw - 16; sw.h = 22;
            sw.render(ctx, textRenderer, mx, my);
            wy2 += ih;
        }

        // Color preview
        if (settingsModule.name.equals("\u0426\u0432\u0435\u0442")) {
            int c = (prefs.getTintA() << 24) | (prefs.getTintR() << 16) | (prefs.getTintG() << 8) | prefs.getTintB();
            ctx.fill(px + pw - 28, py + 6, px + pw - 14, py + 20, c);
            drawBdr(ctx, px + pw - 29, py + 5, 16, 16, C_DIVIDER);
        }
    }

    // === INPUT ===

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int imx = (int) mx, imy = (int) my;

        if (settingsModule != null) {
            for (SettingsWidget sw : sWidgets) {
                if (sw.contains(imx, imy)) {
                    sw.onClick(imx, imy);
                    if (sw instanceof SSlider) sDrag = sw;
                    return true;
                }
            }
            settingsModule = null; sWidgets.clear();
            return true;
        }

        // Sidebar
        for (Category c : cats) {
            if (imx >= wx && imx < wx + SB_W && imy >= c.ry && imy < c.ry + 26) {
                activeCat = c;
                java.util.Arrays.fill(cardHover, 0f);
                return true;
            }
        }

        // Cards
        if (activeCat != null) {
            for (Module m : activeCat.mods) {
                if (m.lx < 0) continue;
                if (imx >= m.lx && imx < m.lx + CARD_W && imy >= m.ly && imy < m.ly + CARD_H) {
                    if (button == 1 && m.sBuild != null) {
                        settingsModule = m; m.sBuild.run(); return true;
                    }
                    boolean nv = !m.getter.get();
                    m.setter.accept(nv);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (sDrag != null) { sDrag.onDrag((int) mx, (int) my); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        sDrag = null; return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (settingsModule != null && key == 256) { settingsModule = null; sWidgets.clear(); return true; }
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public void close() { prefs.save(); if (client != null) client.setScreen(null); }

    @Override
    public boolean shouldPause() { return false; }

    // === HELPERS ===

    private String trimToWidth(String s, int maxW) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += textRenderer.getWidth(String.valueOf(s.charAt(i)));
            if (w > maxW) return s.substring(0, Math.max(1, i));
        }
        return s;
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t); }

    private static void drawBdr(DrawContext ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x, y, x + w, y + 1, c); ctx.fill(x, y + h - 1, x + w, y + h, c);
        ctx.fill(x, y, x + 1, y + h, c); ctx.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static void fillRound(DrawContext ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x + 3, y, x + w - 3, y + h, c);
        ctx.fill(x, y + 3, x + 3, y + h - 3, c);
        ctx.fill(x + w - 3, y + 3, x + w, y + h - 3, c);
        ctx.fill(x + 1, y + 1, x + 3, y + 3, c); ctx.fill(x + w - 3, y + 1, x + w - 1, y + 3, c);
        ctx.fill(x + 1, y + h - 3, x + 3, y + h - 1, c); ctx.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, c);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        return ((int)(ch(a, 24) + (ch(b, 24) - ch(a, 24)) * t) << 24) |
               ((int)(ch(a, 16) + (ch(b, 16) - ch(a, 16)) * t) << 16) |
               ((int)(ch(a, 8) + (ch(b, 8) - ch(a, 8)) * t) << 8) |
                (int)(ch(a, 0) + (ch(b, 0) - ch(a, 0)) * t);
    }
    private static int ch(int c, int sh) { return (c >> sh) & 0xFF; }

    private static int applyAlpha(int color, int a) {
        return (Math.min(255, Math.max(0, a)) << 24) | (color & 0x00FFFFFF);
    }

    private static int blendAlpha(int color, float t) {
        int a = (int)(((color >> 24) & 0xFF) * t);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    // === DATA ===

    static class Category {
        final String name;
        final List<Module> mods = new ArrayList<>();
        int ry;
        Category(String n) { this.name = n; }
        void add(Module m) { mods.add(m); }
    }

    static class Module {
        final String name, desc;
        final Supplier<Boolean> getter;
        final Consumer<Boolean> setter;
        final Runnable sBuild;
        int lx = -1, ly;
        Module(String n, String d, Supplier<Boolean> g, Consumer<Boolean> s, Runnable sb) {
            name = n; desc = d; getter = g; setter = s; sBuild = sb;
        }
    }

    // === SETTINGS WIDGETS ===

    static abstract class SettingsWidget {
        int x, y, w, h;
        abstract void render(DrawContext ctx, TextRenderer tr, int mx, int my);
        void onClick(int mx, int my) {}
        void onDrag(int mx, int my) {}
        boolean contains(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    static class SSlider extends SettingsWidget {
        final String label; final float min, max; float val;
        final Consumer<Double> cb; final int color;
        SSlider(String l, float mn, float mx, float v, Consumer<Double> c, int cl) {
            label = l; min = mn; max = mx; val = v; cb = c; color = cl;
        }
        float frac() { return max <= min ? 0 : Math.max(0, Math.min(1, (val - min) / (max - min))); }
        void apply(int mx) {
            float f = Math.max(0, Math.min(1, (mx - x - 2) / (float)(w - 4)));
            val = min + f * (max - min);
            if (max - min >= 1 && max <= 255) val = Math.round(val);
            cb.accept((double) val);
        }
        @Override void render(DrawContext ctx, TextRenderer tr, int mx, int my) {
            boolean hov = contains(mx, my);
            ctx.fill(x, y, x + w, y + h, hov ? 0xFF282840 : 0xFF202038);
            String vs = (max <= 255 && max - min >= 1) ? "" + (int) val : String.format("%.1f", val);
            ctx.drawTextWithShadow(tr, label, x + 4, y + 2, C_TEXT_DIM);
            int vw = tr.getWidth(vs);
            ctx.drawTextWithShadow(tr, vs, x + w - vw - 4, y + 2, C_TEXT);
            int ty = y + h - 6, tw = w - 4;
            ctx.fill(x + 2, ty, x + 2 + tw, ty + 3, C_SLIDER_TRK);
            int fw = (int)(frac() * tw);
            if (fw > 0) ctx.fill(x + 2, ty, x + 2 + fw, ty + 3, color);
            ctx.fill(x + 2 + fw - 3, ty - 2, x + 2 + fw + 3, ty + 5, C_KNOB);
        }
        @Override void onClick(int mx, int my) { apply(mx); }
        @Override void onDrag(int mx, int my) { apply(mx); }
    }

    static class SToggle extends SettingsWidget {
        final String label; final Supplier<Boolean> get; final Consumer<Boolean> set; float anim;
        SToggle(String l, Supplier<Boolean> g, Consumer<Boolean> s) {
            label = l; get = g; set = s; anim = g.get() ? 1f : 0f;
        }
        @Override void render(DrawContext ctx, TextRenderer tr, int mx, int my) {
            boolean on = get.get();
            float tgt = on ? 1f : 0f;
            anim += (tgt - anim) * 0.2f;
            boolean hov = contains(mx, my);
            ctx.fill(x, y, x + w, y + h, hov ? 0xFF282840 : 0xFF202038);
            ctx.drawTextWithShadow(tr, label, x + 4, y + 7, on ? C_TEXT : C_TEXT_DIM);
            int tw2 = 24, th = 12, tx = x + w - tw2 - 4, ty = y + 5;
            ctx.fill(tx, ty, tx + tw2, ty + th, lerpColor(C_TOGGLE_OFF, C_TOGGLE_ON, anim));
            int kd = th - 4, kr = tw2 - kd - 4, kx = tx + 2 + (int)(anim * kr);
            ctx.fill(kx, ty + 2, kx + kd, ty + 2 + kd, C_KNOB);
        }
        @Override void onClick(int mx, int my) { set.accept(!get.get()); }
    }
}
