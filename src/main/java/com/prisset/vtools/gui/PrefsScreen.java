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

    // --- Palette ---
    private static final int COL_BG         = 0xF0141420;
    private static final int COL_SIDEBAR     = 0xF01A1A2E;
    private static final int COL_CONTENT     = 0xF0101020;
    private static final int COL_HEADER      = 0xF0181830;
    private static final int COL_ACCENT      = 0xFFE040A0;
    private static final int COL_ACCENT_DIM  = 0xFF802060;
    private static final int COL_CARD_OFF    = 0xD0202038;
    private static final int COL_CARD_ON     = 0xD0302848;
    private static final int COL_CARD_HOVER  = 0xD02A2A44;
    private static final int COL_TEXT        = 0xFFE0E0E0;
    private static final int COL_TEXT_DIM    = 0xFF7878A0;
    private static final int COL_TEXT_DESC   = 0xFF9090A8;
    private static final int COL_TOGGLE_ON   = 0xFF00E676;
    private static final int COL_TOGGLE_OFF  = 0xFF505068;
    private static final int COL_SLIDER_TRK  = 0xFF2A2A40;
    private static final int COL_SHADOW      = 0x60000000;
    private static final int COL_CAT_HOVER   = 0x40FFFFFF;
    private static final int COL_CAT_ACTIVE  = 0x30E040A0;
    private static final int COL_BORDER      = 0xFF2A2A44;
    private static final int COL_SETTINGS_BG = 0xF0181830;

    // --- Layout ---
    private static final int WIN_W = 420;
    private static final int WIN_H = 280;
    private static final int SIDEBAR_W = 100;
    private static final int HEADER_H = 36;
    private static final int CARD_W = 145;
    private static final int CARD_H = 52;
    private static final int CARD_GAP = 8;

    private final DisplayPrefs prefs;
    private int winX, winY;

    private final List<Category> categories = new ArrayList<>();
    private Category activeCategory;
    private ModuleCard settingsOpen;
    private final List<SettingsWidget> settingsWidgets = new ArrayList<>();
    private SettingsWidget dragWidget;
    private int scrollOffset;

    public PrefsScreen(DisplayPrefs prefs) {
        super(Text.literal("PRISSET"));
        this.prefs = prefs;
    }

    @Override
    protected void init() {
        winX = (width - WIN_W) / 2;
        winY = (height - WIN_H) / 2;
        settingsOpen = null;
        scrollOffset = 0;
        dragWidget = null;
        buildCategories();
        activeCategory = categories.get(0);
    }

    private void buildCategories() {
        categories.clear();

        // --- Render ---
        Category render = new Category("\u0420\u0435\u043d\u0434\u0435\u0440", "\u25c8");
        render.add(new ModuleCard("\u041e\u0432\u0435\u0440\u043b\u0435\u0439",
            "\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442 \u0433\u0440\u0430\u043d\u0438\u0446\u044b \u043c\u043e\u0434\u0435\u043b\u0435\u0439 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0435\u0439",
            prefs::isActive, v -> prefs.setActive(v), this::buildOverlaySettings));

        render.add(new ModuleCard("RGB \u0420\u0435\u0436\u0438\u043c",
            "\u0420\u0430\u0434\u0443\u0436\u043d\u0430\u044f \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u044f \u0446\u0432\u0435\u0442\u0430",
            prefs::isRgbMode, v -> prefs.setRgbMode(v), this::buildRgbSettings));

        render.add(new ModuleCard("\u0417\u0434\u043e\u0440\u043e\u0432\u044c\u0435",
            "\u0421\u0435\u0440\u0434\u0435\u0447\u043a\u0438 \u043d\u0430\u0434 \u0433\u043e\u043b\u043e\u0432\u043e\u0439 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0435\u0439",
            prefs::isHealthBars, v -> prefs.setHealthBars(v), null));

        render.add(new ModuleCard("\u0421\u043b\u0435\u0434",
            "\u041f\u043e\u043b\u0443\u043f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u044b\u0439 \u0441\u043b\u0435\u0434 \u0437\u0430 \u0438\u0433\u0440\u043e\u043a\u0430\u043c\u0438",
            prefs::isTrailEnabled, v -> prefs.setTrailEnabled(v), this::buildTrailSettings));

        // --- Combat ---
        Category combat = new Category("\u0411\u043e\u0439", "\u2694");
        combat.add(new ModuleCard("\u0411\u044b\u0441\u0442\u0440\u044b\u0435 \u041a\u0440\u0438\u0441\u0442\u0430\u043b\u043b\u044b",
            "\u0423\u043c\u0435\u043d\u044c\u0448\u0430\u0435\u0442 \u0437\u0430\u0434\u0435\u0440\u0436\u043a\u0443 \u043a\u0440\u0438\u0441\u0442\u0430\u043b\u043b\u043e\u0432 \u043a\u0440\u0430\u044f",
            prefs::isFastInteract, v -> {}, null));

        // --- Visual ---
        Category visual = new Category("\u0412\u0438\u0437\u0443\u0430\u043b", "\u25ce");
        visual.add(new ModuleCard("\u0417\u0443\u043c",
            "\u041f\u0440\u0438\u0431\u043b\u0438\u0436\u0435\u043d\u0438\u0435 \u043d\u0430 \u043a\u043b\u0430\u0432\u0438\u0448\u0443 C",
            () -> true, v -> {}, this::buildZoomSettings));

        // --- Settings ---
        Category settings = new Category("\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438", "\u2699");
        settings.add(new ModuleCard("\u0426\u0432\u0435\u0442",
            "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430 \u0446\u0432\u0435\u0442\u0430 \u043e\u0432\u0435\u0440\u043b\u0435\u044f",
            () -> true, v -> {}, this::buildColorSettings));

        settings.add(new ModuleCard("\u0424\u0438\u043b\u044c\u0442\u0440\u044b",
            "\u0412\u044b\u0431\u043e\u0440 \u0442\u0438\u043f\u043e\u0432 \u0441\u0443\u0449\u043d\u043e\u0441\u0442\u0435\u0439",
            () -> true, v -> {}, this::buildFilterSettings));

        settings.add(new ModuleCard("F3+B \u0420\u0435\u0436\u0438\u043c",
            "\u041e\u0432\u0435\u0440\u043b\u0435\u0439 \u0442\u043e\u043b\u044c\u043a\u043e \u0441 F3+B",
            prefs::isDebugOnly, v -> prefs.setDebugOnly(v), null));

        categories.add(render);
        categories.add(combat);
        categories.add(visual);
        categories.add(settings);
    }

    // === Settings builders ===

    private void buildOverlaySettings() {
        settingsWidgets.clear();
        settingsWidgets.add(new SettingsSlider("\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c", 0, 255, prefs.getTintA(), v -> prefs.setTintA(v.intValue()), COL_TEXT_DIM));
    }

    private void buildRgbSettings() {
        settingsWidgets.clear();
        settingsWidgets.add(new SettingsSlider("\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c", 0.1f, 5.0f, prefs.getRgbSpeed(), v -> prefs.setRgbSpeed(v.floatValue()), COL_ACCENT));
    }

    private void buildTrailSettings() {
        settingsWidgets.clear();
        settingsWidgets.add(new SettingsSlider("\u0414\u043b\u0438\u043d\u0430", 5, 40, prefs.getTrailLength(), v -> prefs.setTrailLength(v.intValue()), 0xFFFFAA44));
    }

    private void buildZoomSettings() {
        settingsWidgets.clear();
        settingsWidgets.add(new SettingsSlider("\u0421\u0438\u043b\u0430", 1.5f, 10.0f, prefs.getZoomStrength(), v -> prefs.setZoomStrength(v.floatValue()), 0xFF44DDFF));
    }

    private void buildColorSettings() {
        settingsWidgets.clear();
        settingsWidgets.add(new SettingsSlider("\u041a\u0440\u0430\u0441\u043d\u044b\u0439", 0, 255, prefs.getTintR(), v -> prefs.setTintR(v.intValue()), 0xFFFF4444));
        settingsWidgets.add(new SettingsSlider("\u0417\u0435\u043b\u0451\u043d\u044b\u0439", 0, 255, prefs.getTintG(), v -> prefs.setTintG(v.intValue()), 0xFF44FF44));
        settingsWidgets.add(new SettingsSlider("\u0421\u0438\u043d\u0438\u0439", 0, 255, prefs.getTintB(), v -> prefs.setTintB(v.intValue()), 0xFF4488FF));
        settingsWidgets.add(new SettingsSlider("\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c", 0, 255, prefs.getTintA(), v -> prefs.setTintA(v.intValue()), 0xFF888888));
    }

    private void buildFilterSettings() {
        settingsWidgets.clear();
        settingsWidgets.add(new SettingsToggle("\u0418\u0433\u0440\u043e\u043a\u0438", prefs::isFilterPlayers, v -> prefs.setFilterPlayers(v)));
        settingsWidgets.add(new SettingsToggle("\u041c\u043e\u0431\u044b", prefs::isFilterMobs, v -> prefs.setFilterMobs(v)));
        settingsWidgets.add(new SettingsToggle("\u0414\u0440\u043e\u043f", prefs::isFilterDrops, v -> prefs.setFilterDrops(v)));
        settingsWidgets.add(new SettingsToggle("\u0421\u043d\u0430\u0440\u044f\u0434\u044b", prefs::isFilterProjectiles, v -> prefs.setFilterProjectiles(v)));
    }

    // === Render ===

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Dim background
        ctx.fill(0, 0, width, height, 0x80000000);

        // Window shadow
        ctx.fill(winX + 4, winY + 4, winX + WIN_W + 4, winY + WIN_H + 4, COL_SHADOW);

        // Window bg
        ctx.fill(winX, winY, winX + WIN_W, winY + WIN_H, COL_BG);

        renderSidebar(ctx, mx, my, delta);
        renderHeader(ctx, mx, my, delta);
        renderContent(ctx, mx, my, delta);

        if (settingsOpen != null) {
            renderSettingsPanel(ctx, mx, my, delta);
        }

        // Window border
        drawBorder(ctx, winX, winY, WIN_W, WIN_H, COL_BORDER);
    }

    private void renderSidebar(DrawContext ctx, int mx, int my, float delta) {
        int sx = winX;
        int sy = winY;
        int sw = SIDEBAR_W;
        int sh = WIN_H;

        ctx.fill(sx, sy, sx + sw, sy + sh, COL_SIDEBAR);
        ctx.fill(sx + sw - 1, sy, sx + sw, sy + sh, COL_BORDER);

        // Logo
        int logoY = sy + 8;
        float hueBase = (System.currentTimeMillis() % 4000L) / 4000f;
        String logo = "PRISSET";
        int logoW = textRenderer.getWidth(logo);
        int logoX = sx + (sw - logoW) / 2;

        for (int i = 0; i < logo.length(); i++) {
            float hue = (hueBase + i * 0.07f) % 1.0f;
            int rgb = Color.HSBtoRGB(hue, 0.6f, 1.0f);
            int c = 0xFF000000 | (rgb & 0x00FFFFFF);
            String ch = String.valueOf(logo.charAt(i));
            ctx.drawTextWithShadow(textRenderer, ch, logoX, logoY, c);
            logoX += textRenderer.getWidth(ch);
        }

        // Category buttons
        int cy = sy + 30;
        for (Category cat : categories) {
            boolean active = cat == activeCategory;
            boolean hover = mx >= sx && mx < sx + sw && my >= cy && my < cy + 22;

            if (active) {
                ctx.fill(sx + 2, cy, sx + sw - 1, cy + 22, COL_CAT_ACTIVE);
                ctx.fill(sx, cy, sx + 3, cy + 22, COL_ACCENT);
            } else if (hover) {
                ctx.fill(sx + 2, cy, sx + sw - 1, cy + 22, COL_CAT_HOVER);
            }

            ctx.drawTextWithShadow(textRenderer, cat.icon + " " + cat.name,
                sx + 10, cy + 7, active ? COL_TEXT : COL_TEXT_DIM);

            cat.renderY = cy;
            cat.renderH = 22;
            cy += 24;
        }

        // Bottom label
        ctx.drawTextWithShadow(textRenderer, "\u00a78v1.0.0", sx + 8, sy + sh - 14, COL_TEXT_DIM);
    }

    private void renderHeader(DrawContext ctx, int mx, int my, float delta) {
        int hx = winX + SIDEBAR_W;
        int hy = winY;
        int hw = WIN_W - SIDEBAR_W;

        ctx.fill(hx, hy, hx + hw, hy + HEADER_H, COL_HEADER);
        ctx.fill(hx, hy + HEADER_H - 1, hx + hw, hy + HEADER_H, COL_BORDER);

        // Category title
        if (activeCategory != null) {
            ctx.drawTextWithShadow(textRenderer,
                activeCategory.icon + "  " + activeCategory.name,
                hx + 12, hy + 13, COL_TEXT);
        }

        // Module count
        if (activeCategory != null) {
            String count = activeCategory.modules.size() + " \u043c\u043e\u0434.";
            int cw = textRenderer.getWidth(count);
            ctx.drawTextWithShadow(textRenderer, count, hx + hw - cw - 12, hy + 13, COL_TEXT_DIM);
        }
    }

    private void renderContent(DrawContext ctx, int mx, int my, float delta) {
        int cx = winX + SIDEBAR_W + 1;
        int cy = winY + HEADER_H;
        int cw = WIN_W - SIDEBAR_W - 1;
        int ch = WIN_H - HEADER_H;

        ctx.fill(cx, cy, cx + cw, cy + ch, COL_CONTENT);

        if (activeCategory == null) return;

        int pad = 10;
        int cols = 2;
        int cardX = cx + pad;
        int cardY = cy + pad - scrollOffset;

        for (int i = 0; i < activeCategory.modules.size(); i++) {
            ModuleCard card = activeCategory.modules.get(i);
            int col = i % cols;
            int row = i / cols;

            int x = cardX + col * (CARD_W + CARD_GAP);
            int y = cardY + row * (CARD_H + CARD_GAP);

            // Skip if outside visible area
            if (y + CARD_H < cy || y > cy + ch) {
                card.lastX = -1;
                continue;
            }

            card.lastX = x;
            card.lastY = y;

            boolean on = card.getter.get();
            boolean hover = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H
                         && my >= cy && my < cy + ch;

            // Card shadow
            ctx.fill(x + 2, y + 2, x + CARD_W + 2, y + CARD_H + 2, 0x30000000);

            // Card bg
            int bg = on ? COL_CARD_ON : COL_CARD_OFF;
            if (hover) bg = lighten(bg, 15);
            ctx.fill(x, y, x + CARD_W, y + CARD_H, bg);

            // Left accent strip if on
            if (on) {
                ctx.fill(x, y, x + 3, y + CARD_H, COL_ACCENT);
            }

            // Card border
            drawBorder(ctx, x, y, CARD_W, CARD_H, on ? COL_ACCENT_DIM : COL_BORDER);

            // Module name
            ctx.drawTextWithShadow(textRenderer, card.name, x + 8, y + 6, on ? COL_TEXT : COL_TEXT_DIM);

            // Status indicator dot
            int dotX = x + CARD_W - 14;
            int dotY = y + 8;
            ctx.fill(dotX, dotY, dotX + 6, dotY + 6, on ? COL_TOGGLE_ON : COL_TOGGLE_OFF);

            // Description
            String desc = card.desc;
            int maxDescW = CARD_W - 16;
            if (textRenderer.getWidth(desc) > maxDescW) {
                while (desc.length() > 3 && textRenderer.getWidth(desc + "..") > maxDescW) {
                    desc = desc.substring(0, desc.length() - 1);
                }
                desc += "..";
            }
            ctx.drawTextWithShadow(textRenderer, desc, x + 8, y + 20, COL_TEXT_DESC);

            // Settings gear hint
            if (card.settingsBuilder != null && hover) {
                ctx.drawTextWithShadow(textRenderer, "\u00a78[\u041f\u041a\u041c]", x + 8, y + CARD_H - 14, COL_TEXT_DIM);
            }
        }
    }

    private void renderSettingsPanel(DrawContext ctx, int mx, int my, float delta) {
        // Overlay dim
        int ox = winX + SIDEBAR_W;
        int oy = winY + HEADER_H;
        int ow = WIN_W - SIDEBAR_W;
        int oh = WIN_H - HEADER_H;
        ctx.fill(ox, oy, ox + ow, oy + oh, 0xA0000000);

        // Settings panel
        int pw = 200;
        int itemH = 24;
        int ph = 36 + settingsWidgets.size() * itemH + 10;
        int px = ox + (ow - pw) / 2;
        int py = oy + (oh - ph) / 2;

        ctx.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, COL_SHADOW);
        ctx.fill(px, py, px + pw, py + ph, COL_SETTINGS_BG);
        ctx.fill(px, py, px + pw, py + 2, COL_ACCENT);
        drawBorder(ctx, px, py, pw, ph, COL_BORDER);

        // Title
        ctx.drawTextWithShadow(textRenderer, "\u2699 " + settingsOpen.name, px + 8, py + 10, COL_TEXT);

        // Close hint
        String closeHint = "[ESC]";
        int chw = textRenderer.getWidth(closeHint);
        ctx.drawTextWithShadow(textRenderer, closeHint, px + pw - chw - 8, py + 10, COL_TEXT_DIM);

        // Widgets
        int wy = py + 30;
        for (SettingsWidget sw : settingsWidgets) {
            sw.x = px + 10;
            sw.y = wy;
            sw.w = pw - 20;
            sw.h = 20;
            sw.render(ctx, textRenderer, mx, my);
            wy += itemH;
        }

        // Color preview for color settings
        if (settingsOpen.name.equals("\u0426\u0432\u0435\u0442")) {
            int col = (prefs.getTintA() << 24) | (prefs.getTintR() << 16) | (prefs.getTintG() << 8) | prefs.getTintB();
            ctx.fill(px + pw - 26, py + 6, px + pw - 10, py + 22, col);
            drawBorder(ctx, px + pw - 27, py + 5, 18, 18, COL_BORDER);
        }
    }

    // === Input ===

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int imx = (int) mx, imy = (int) my;

        // Settings panel interaction
        if (settingsOpen != null) {
            for (SettingsWidget sw : settingsWidgets) {
                if (sw.contains(imx, imy)) {
                    sw.onClick(imx, imy);
                    if (sw instanceof SettingsSlider) dragWidget = sw;
                    return true;
                }
            }
            // Click outside settings = close
            settingsOpen = null;
            settingsWidgets.clear();
            return true;
        }

        // Sidebar click
        for (Category cat : categories) {
            if (imx >= winX && imx < winX + SIDEBAR_W
                && imy >= cat.renderY && imy < cat.renderY + cat.renderH) {
                activeCategory = cat;
                scrollOffset = 0;
                return true;
            }
        }

        // Content cards
        if (activeCategory != null) {
            int contentTop = winY + HEADER_H;
            int contentBot = winY + WIN_H;

            for (ModuleCard card : activeCategory.modules) {
                if (card.lastX < 0) continue;
                if (imx >= card.lastX && imx < card.lastX + CARD_W
                    && imy >= card.lastY && imy < card.lastY + CARD_H
                    && imy >= contentTop && imy < contentBot) {

                    if (button == 1 && card.settingsBuilder != null) {
                        // Right click = open settings
                        settingsOpen = card;
                        card.settingsBuilder.run();
                        return true;
                    }

                    // Left click = toggle
                    boolean newVal = !card.getter.get();
                    card.setter.accept(newVal);
                    return true;
                }
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
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (settingsOpen == null) {
            scrollOffset = Math.max(0, scrollOffset - (int)(amount * 20));
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (settingsOpen != null && keyCode == 256) {
            settingsOpen = null;
            settingsWidgets.clear();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        prefs.save();
        if (client != null) client.setScreen(null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // === Helpers ===

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x, y, x + w, y + 1, c);
        ctx.fill(x, y + h - 1, x + w, y + h, c);
        ctx.fill(x, y, x + 1, y + h, c);
        ctx.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static int lighten(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blendColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int)(a1 + (a2 - a1) * t) << 24)
             | ((int)(r1 + (r2 - r1) * t) << 16)
             | ((int)(g1 + (g2 - g1) * t) << 8)
             |  (int)(b1 + (b2 - b1) * t);
    }

    // === Data structures ===

    static class Category {
        final String name, icon;
        final List<ModuleCard> modules = new ArrayList<>();
        int renderY, renderH;

        Category(String name, String icon) { this.name = name; this.icon = icon; }
        void add(ModuleCard m) { modules.add(m); }
    }

    static class ModuleCard {
        final String name, desc;
        final Supplier<Boolean> getter;
        final Consumer<Boolean> setter;
        final Runnable settingsBuilder;
        int lastX = -1, lastY = -1;

        ModuleCard(String name, String desc, Supplier<Boolean> getter, Consumer<Boolean> setter, Runnable settingsBuilder) {
            this.name = name;
            this.desc = desc;
            this.getter = getter;
            this.setter = setter;
            this.settingsBuilder = settingsBuilder;
        }
    }

    // === Settings widgets ===

    static abstract class SettingsWidget {
        int x, y, w, h;
        abstract void render(DrawContext ctx, TextRenderer tr, int mx, int my);
        void onClick(int mx, int my) {}
        void onDrag(int mx, int my) {}
        boolean contains(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    static class SettingsSlider extends SettingsWidget {
        final String label;
        final float min, max;
        float value;
        final Consumer<Double> callback;
        final int color;

        SettingsSlider(String label, float min, float max, float value, Consumer<Double> cb, int color) {
            this.label = label; this.min = min; this.max = max;
            this.value = value; this.callback = cb; this.color = color;
        }

        float frac() { return max <= min ? 0 : (value - min) / (max - min); }

        void setFromMouse(int mx) {
            float f = Math.max(0, Math.min(1, (mx - x - 2) / (float)(w - 4)));
            value = min + f * (max - min);
            if (max - min >= 1 && max <= 255) value = Math.round(value);
            callback.accept((double) value);
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my) {
            boolean hover = contains(mx, my);
            ctx.fill(x, y, x + w, y + h, hover ? 0xFF242440 : 0xFF1E1E38);

            // Track
            int ty = y + h - 6;
            ctx.fill(x + 2, ty, x + w - 2, ty + 3, COL_SLIDER_TRK);
            int fw = (int)(frac() * (w - 4));
            if (fw > 0) ctx.fill(x + 2, ty, x + 2 + fw, ty + 3, color);

            // Knob
            int kx = x + 2 + fw;
            ctx.fill(kx - 3, ty - 2, kx + 3, ty + 5, 0xFFD0D0D0);

            // Label + value
            ctx.drawTextWithShadow(tr, label, x + 4, y + 2, COL_TEXT_DIM);
            String vs = (max <= 255 && max - min >= 1) ? String.valueOf((int) value) : String.format("%.1f", value);
            int vw = tr.getWidth(vs);
            ctx.drawTextWithShadow(tr, vs, x + w - vw - 4, y + 2, COL_TEXT);
        }

        @Override void onClick(int mx, int my) { setFromMouse(mx); }
        @Override void onDrag(int mx, int my) { setFromMouse(mx); }
    }

    static class SettingsToggle extends SettingsWidget {
        final String label;
        final Supplier<Boolean> getter;
        final Consumer<Boolean> setter;

        SettingsToggle(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.label = label; this.getter = getter; this.setter = setter;
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my) {
            boolean on = getter.get();
            boolean hover = contains(mx, my);
            ctx.fill(x, y, x + w, y + h, hover ? 0xFF242440 : 0xFF1E1E38);

            ctx.drawTextWithShadow(tr, label, x + 4, y + 6, on ? COL_TEXT : COL_TEXT_DIM);

            // Toggle indicator
            int trkW = 20;
            int trkX = x + w - trkW - 6;
            int trkY = y + 5;
            ctx.fill(trkX, trkY, trkX + trkW, trkY + 10, on ? COL_TOGGLE_ON : COL_TOGGLE_OFF);

            int knobX = on ? trkX + trkW - 8 : trkX + 2;
            ctx.fill(knobX, trkY + 2, knobX + 6, trkY + 8, 0xFFE0E0E0);
        }

        @Override
        void onClick(int mx, int my) {
            setter.accept(!getter.get());
        }
    }
}
