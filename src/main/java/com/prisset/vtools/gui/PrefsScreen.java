package com.prisset.vtools.gui;

import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.config.ProfileIndex;
import com.prisset.vtools.input.PlayerTargetHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PrefsScreen extends Screen {

    private static final int W = 240;
    private static final int PAD = 8;
    private static final int ROW_H = 16;
    private static final int LABEL_H = 18;
    private static final int SLIDER_LABEL_W = 80;
    private static final int SCROLL_SPEED = 12;

    private static final Map<String, String> KEY_NAMES = new HashMap<>();

    static {
        KEY_NAMES.put("32", "SPACE");
        KEY_NAMES.put("256", "ESC");
        KEY_NAMES.put("257", "ENTER");
        KEY_NAMES.put("258", "TAB");
        KEY_NAMES.put("259", "BACKSPACE");
        KEY_NAMES.put("340", "L_SHIFT");
        KEY_NAMES.put("341", "L_CTRL");
        KEY_NAMES.put("342", "L_ALT");
        KEY_NAMES.put("344", "R_SHIFT");
        KEY_NAMES.put("345", "R_CTRL");
        KEY_NAMES.put("346", "R_ALT");
        for (int i = GLFW.GLFW_KEY_F1; i <= GLFW.GLFW_KEY_F25; i++) {
            KEY_NAMES.put(String.valueOf(i), "F" + (i - GLFW.GLFW_KEY_F1 + 1));
        }
    }

    private final DisplayPrefs prefs;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Boolean> collapsed = new HashMap<>();
    private int wx, wy, totalH;
    private int scrollOffset;
    private int maxScroll;
    private Row drag;
    private TextInputRow focusedInput = null;
    private KeyBindRow activeKeyBind = null;

    public PrefsScreen(DisplayPrefs prefs) {
        super(Text.literal("PRISSET"));
        this.prefs = prefs;
    }

    @Override
    protected void init() {
        drag = null;
        scrollOffset = 0;
        rows.clear();

        buildOverlay();
        buildColor();
        buildEffects();
        buildCombat();
        buildTarget();
        buildSearch();
        buildTeammates();
        buildTelegram();
        buildMenu();

        recalcLayout();
    }

    private void buildOverlay() {
        String key = "overlay";
        rows.add(new SectionLabel("\u041e\u0412\u0415\u0420\u041b\u0415\u0419", key));
        if (!isCollapsed(key)) {
            rows.add(new Toggle("\u041e\u0432\u0435\u0440\u043b\u0435\u0439", prefs::isOverlayEnabled, v -> prefs.setOverlayEnabled(v)));
            rows.add(new Toggle("\u0425\u0438\u0442\u0431\u043e\u043a\u0441\u044b", prefs::isHitboxEnabled, v -> prefs.setHitboxEnabled(v)));
            rows.add(new Toggle("\u041f\u041a\u041c", prefs::isEspEnabled, v -> prefs.setEspEnabled(v)));
            rows.add(new Toggle("\u041f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c", prefs::isActive, v -> prefs.setActive(v)));
            rows.add(new Toggle("\u0418\u0433\u0440\u043e\u043a\u0438", prefs::isFilterPlayers, v -> prefs.setFilterPlayers(v)));
            rows.add(new Toggle("\u041c\u043e\u0431\u044b", prefs::isFilterMobs, v -> prefs.setFilterMobs(v)));
            rows.add(new Toggle("\u041f\u0440\u0435\u0434\u043c\u0435\u0442\u044b", prefs::isFilterDrops, v -> prefs.setFilterDrops(v)));
            rows.add(new Toggle("\u0421\u043d\u0430\u0440\u044f\u0434\u044b", prefs::isFilterProjectiles, v -> prefs.setFilterProjectiles(v)));
        }
    }

    private void buildColor() {
        String key = "color";
        rows.add(new SectionLabel("\u0426\u0412\u0415\u0422", key));
        if (!isCollapsed(key)) {
            rows.add(new Slider("\u041a\u0440\u0430\u0441\u043d\u044b\u0439", 0, 255, prefs.getTintR(), v -> prefs.setTintR(v.intValue())));
            rows.add(new Slider("\u0417\u0435\u043b\u0451\u043d\u044b\u0439", 0, 255, prefs.getTintG(), v -> prefs.setTintG(v.intValue())));
            rows.add(new Slider("\u0421\u0438\u043d\u0438\u0439", 0, 255, prefs.getTintB(), v -> prefs.setTintB(v.intValue())));
            rows.add(new Slider("\u041d\u0435\u043f\u0440\u043e\u0437\u0440\u0430\u0447\u043d.", 0, 255, prefs.getTintA(), v -> prefs.setTintA(v.intValue())));
        }
    }

    private void buildEffects() {
        String key = "effects";
        rows.add(new SectionLabel("\u042d\u0424\u0424\u0415\u041a\u0422\u042b", key));
        if (!isCollapsed(key)) {
            rows.add(new Toggle("RGB", prefs::isRgbMode, v -> prefs.setRgbMode(v)));
            rows.add(new Slider("RGB \u0441\u043a\u043e\u0440.", 0.1f, 5f, prefs.getRgbSpeed(), v -> prefs.setRgbSpeed(v.floatValue())));
            rows.add(new Toggle("\u0417\u0434\u043e\u0440\u043e\u0432\u044c\u0435", prefs::isHealthBars, v -> prefs.setHealthBars(v)));
            rows.add(new Slider("\u0417\u0443\u043c (C)", 1.5f, 10f, prefs.getZoomStrength(), v -> prefs.setZoomStrength(v.floatValue())));
            rows.add(new Toggle("\u0421\u043b\u0435\u0434", prefs::isTrailEnabled, v -> prefs.setTrailEnabled(v)));
            rows.add(new Slider("\u0414\u043b\u0438\u043d\u0430", 5, 40, prefs.getTrailLength(), v -> prefs.setTrailLength(v.intValue())));
            rows.add(new Toggle("\u0411\u0435\u0437 \u0442\u0440\u044f\u0441\u043a\u0438", prefs::isNoBobbing, v -> prefs.setNoBobbing(v)));
        }
    }

    private void buildCombat() {
        String key = "combat";
        rows.add(new SectionLabel("\u0411\u041e\u0419", key));
        if (!isCollapsed(key)) {
            rows.add(new Toggle("\u0410\u0432\u0442\u043e\u0444\u0430\u0440\u043c", prefs::isAutoFarm, v -> prefs.setAutoFarm(v)));
            rows.add(new Toggle("\u0410\u0432\u0442\u043e\u0435\u0434\u0430", prefs::isAutoEat, v -> prefs.setAutoEat(v)));
            rows.add(new Toggle("W \u0430\u0432\u0442\u043e\u043c\u0430\u0442", prefs::isWTap, v -> prefs.setWTap(v)));
            rows.add(new Toggle("AFK \u0437\u0430\u0449\u0438\u0442\u0430", prefs::isAfkGuard, v -> prefs.setAfkGuard(v)));
        }
    }

    private void buildTarget() {
        String key = "target";
        rows.add(new SectionLabel("\u0426\u0415\u041b\u042c", key));
        if (!isCollapsed(key)) {
            rows.add(new Toggle("\u041e\u0442\u0441\u043b\u0435\u0436\u0438\u0432\u0430\u043d\u0438\u0435", prefs::isTargetEnabled, v -> prefs.setTargetEnabled(v)));

            String targetName = PlayerTargetHandler.getTargetName();
            if (targetName != null) {
                rows.add(new Toggle("\u0421\u0431\u0440\u043e\u0441: " + targetName, () -> true, v -> {
                    PlayerTargetHandler.clearTarget();
                    init();
                }));
            }
        }
    }

    private void buildSearch() {
        String key = "search";
        rows.add(new SectionLabel("\u0421\u0423\u041d\u0414\u0423\u041a\u0418", key));
        if (!isCollapsed(key)) {
            rows.add(new Toggle("\u0421\u043e\u0440\u0442\u0438\u0440\u043e\u0432\u043a\u0430 (R)", prefs::isChestSearchEnabled, v -> prefs.setChestSearchEnabled(v)));
        }
    }

    private void buildTeammates() {
        String key = "teammates";
        rows.add(new SectionLabel("\u0422\u0418\u041c\u041c\u0415\u0419\u0422\u042b", key));
        if (!isCollapsed(key)) {
            rows.add(new TextInputRow("\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u043d\u0438\u043a", n -> {
                ProfileIndex.get().add(n);
                init();
            }, 16, true));
            for (String name : ProfileIndex.get().all()) {
                rows.add(new TeammateRow(name, this));
            }
        }
    }

    private void buildTelegram() {
        String key = "telegram";
        rows.add(new SectionLabel("TELEGRAM", key));
        if (!isCollapsed(key)) {
            rows.add(new Toggle("\u0423\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u044f", prefs::isTgEnabled, v -> prefs.setTgEnabled(v)));
            rows.add(new TextInputRow("Bot Token", v -> prefs.setTgBotToken(v), 64, false, prefs.getTgBotToken()));
            rows.add(new TextInputRow("Chat ID", v -> prefs.setTgChatId(v), 32, false, prefs.getTgChatId()));
        }
    }

    private void buildMenu() {
        String key = "menu";
        rows.add(new SectionLabel("\u041c\u0415\u041d\u042e", key));
        if (!isCollapsed(key)) {
            rows.add(new Toggle("RGB \u043c\u0435\u043d\u044e", prefs::isMenuRgbMode, v -> prefs.setMenuRgbMode(v)));
            rows.add(new Slider("RGB \u0441\u043a\u043e\u0440.", 0.1f, 5f, prefs.getMenuRgbSpeed(), v -> prefs.setMenuRgbSpeed(v.floatValue())));
            rows.add(new Slider("\u041a\u0440\u0430\u0441\u043d\u044b\u0439", 0, 255, prefs.getMenuR(), v -> prefs.setMenuR(v.intValue())));
            rows.add(new Slider("\u0417\u0435\u043b\u0451\u043d\u044b\u0439", 0, 255, prefs.getMenuG(), v -> prefs.setMenuG(v.intValue())));
            rows.add(new Slider("\u0421\u0438\u043d\u0438\u0439", 0, 255, prefs.getMenuB(), v -> prefs.setMenuB(v.intValue())));
        }
    }

    private boolean isCollapsed(String key) {
        return collapsed.getOrDefault(key, false);
    }

    private void toggleSection(String key) {
        collapsed.put(key, !isCollapsed(key));
        init();
    }

    private void recalcLayout() {
        totalH = PAD + 14;
        for (Row r : rows) {
            totalH += r instanceof SectionLabel ? LABEL_H : ROW_H;
        }
        totalH += PAD;

        wx = (width - W) / 2;
        int visibleH = Math.min(totalH, height - 20);
        wy = (height - visibleH) / 2;
        maxScroll = Math.max(0, totalH - visibleH);
    }

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

    static String keyName(int keyCode) {
        if (keyCode < 0) {
            int btn = -(keyCode + 1);
            return switch (btn) {
                case 0 -> "LMB";
                case 1 -> "RMB";
                case 2 -> "MMB";
                default -> "MOUSE" + (btn + 1);
            };
        }
        String mapped = KEY_NAMES.get(String.valueOf(keyCode));
        if (mapped != null) return mapped;
        if (keyCode >= 65 && keyCode <= 90) return String.valueOf((char) keyCode);
        if (keyCode >= 48 && keyCode <= 57) return String.valueOf((char) keyCode);
        String glfwName = GLFW.glfwGetKeyName(keyCode, 0);
        if (glfwName != null) return glfwName.toUpperCase();
        return "KEY_" + keyCode;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        int nc = neon();
        int nR = nr(nc), nG = ng(nc), nB = nb(nc);
        float pulse = (float)(Math.sin(now / 500.0) * 0.12 + 0.88);

        ctx.fill(0, 0, width, height, 0xC0000000);
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            int inset = i * 8;
            int alpha = (int)(60.0 * (1.0 - (double) i / steps));
            if (alpha <= 0) break;
            ctx.fill(inset, inset, width - inset, height - inset, rgba(0, 0, 0, alpha));
        }
        ctx.fill(0, 0, width, height, rgba(nR / 8, nG / 8, nB / 8, 18));

        int visibleH = Math.min(totalH, height - 20);

        for (int i = 6; i >= 1; i--) {
            int a = (int)(pulse * (6 + (6 - i) * 4));
            ctx.fill(wx - i, wy - i, wx + W + i, wy + visibleH + i, rgba(nR, nG, nB, a));
        }

        ctx.fill(wx, wy, wx + W, wy + visibleH, 0xFF000000);

        int ba = (int)(pulse * 180);
        int bc = rgba(nR, nG, nB, ba);
        ctx.fill(wx, wy, wx + W, wy + 1, bc);
        ctx.fill(wx, wy + visibleH - 1, wx + W, wy + visibleH, bc);
        ctx.fill(wx, wy + 1, wx + 1, wy + visibleH - 1, bc);
        ctx.fill(wx + W - 1, wy + 1, wx + W, wy + visibleH - 1, bc);

        ctx.fill(wx + 1, wy, wx + W - 1, wy + 1, rgba(nR, nG, nB, (int)(pulse * 255)));

        String title = "PRISSET";
        int tw = textRenderer.getWidth(title);
        ctx.drawTextWithShadow(textRenderer, title, wx + (W - tw) / 2, wy + 3, rgba(nR, nG, nB, 255));

        int sc = (prefs.getTintA() << 24) | (prefs.getTintR() << 16) | (prefs.getTintG() << 8) | prefs.getTintB();
        ctx.fill(wx + W - 14, wy + 3, wx + W - 4, wy + 11, sc);

        ctx.enableScissor(wx, wy + 14, wx + W, wy + visibleH);

        int y = wy + PAD + 14 - scrollOffset;
        for (Row r : rows) {
            int rh = r instanceof SectionLabel ? LABEL_H : ROW_H;
            r.rx = wx + PAD;
            r.ry = y;
            r.rw = W - PAD * 2;
            r.rh = rh;

            if (y + rh > wy && y < wy + visibleH) {
                if (!(r instanceof SectionLabel)) {
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

        if (maxScroll > 0) {
            float scrollFrac = (float) scrollOffset / maxScroll;
            int barH = Math.max(8, visibleH * visibleH / totalH);
            int barY = wy + 14 + (int)(scrollFrac * (visibleH - 14 - barH));
            ctx.fill(wx + W - 2, barY, wx + W - 1, barY + barH, rgba(nR, nG, nB, 60));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (activeKeyBind != null) {
            int encoded = -(btn + 1);
            activeKeyBind.set.accept(encoded);
            prefs.save();
            activeKeyBind = null;
            return true;
        }
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        boolean clickedInput = false;
        int visibleH = Math.min(totalH, height - 20);
        for (Row r : rows) {
            if (r.contains((int) mx, (int) my) && my >= wy && my < wy + visibleH) {
                r.onClick((int) mx);
                if (r instanceof Slider) drag = r;
                if (r instanceof TextInputRow) clickedInput = true;
                return true;
            }
        }
        if (!clickedInput) focusedInput = null;
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

    @Override
    public void close() {
        prefs.save();
        if (client != null) client.setScreen(null);
    }

    @Override
    public boolean shouldPause() { return false; }

    // === ROW TYPES ===

    static abstract class Row {
        int rx, ry, rw, rh;
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {}
        void onClick(int mx) {}
        void onDrag(int mx) {}
        boolean contains(int mx, int my) { return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh; }
    }

    class SectionLabel extends Row {
        final String text;
        final String sectionKey;

        SectionLabel(String t, String key) { text = t; sectionKey = key; }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {
            boolean hov = contains(mx, my);
            int ar = nr(nc) / 2, ag = ng(nc) / 2, ab = nb(nc) / 2;
            int textColor = hov ? rgba(nr(nc), ng(nc), nb(nc), 255) : rgba(ar, ag, ab, 255);

            String arrow = isCollapsed(sectionKey) ? "\u25b6 " : "\u25bc ";
            ctx.drawTextWithShadow(tr, arrow + text, rx, ry + 8, textColor);

            int lw = tr.getWidth(arrow + text);
            ctx.fill(rx + lw + 6, ry + 12, rx + rw, ry + 13, 0xFF111114);
        }

        @Override
        boolean contains(int mx, int my) { return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh; }

        @Override
        void onClick(int mx) { toggleSection(sectionKey); }
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

    class KeyBindRow extends Row {
        final String label;
        final Supplier<Integer> get;
        final Consumer<Integer> set;

        KeyBindRow(String l, Supplier<Integer> g, Consumer<Integer> s) { label = l; get = g; set = s; }

        boolean isListening() { return activeKeyBind == this; }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {
            ctx.drawTextWithShadow(tr, label, rx + 2, ry + 4, 0xFF808090);

            int btnX = rx + rw - 60;
            int btnW = 58;
            boolean hov = mx >= btnX && mx < btnX + btnW && my >= ry && my < ry + rh;
            boolean listening = isListening();

            int bg = listening ? rgba(nr(nc), ng(nc), nb(nc), 60) : (hov ? 0xFF1A1A24 : 0xFF0E0E12);
            int border = listening ? rgba(nr(nc), ng(nc), nb(nc), 200) : (hov ? rgba(nr(nc), ng(nc), nb(nc), 120) : 0xFF2A2A30);

            ctx.fill(btnX, ry + 1, btnX + btnW, ry + rh - 1, bg);
            ctx.fill(btnX, ry + 1, btnX + btnW, ry + 2, border);
            ctx.fill(btnX, ry + rh - 2, btnX + btnW, ry + rh - 1, border);
            ctx.fill(btnX, ry + 1, btnX + 1, ry + rh - 1, border);
            ctx.fill(btnX + btnW - 1, ry + 1, btnX + btnW, ry + rh - 1, border);

            String display = listening ? "..." : keyName(get.get());
            int textColor = listening ? rgba(nr(nc), ng(nc), nb(nc), 255) : 0xFFD8D8E0;
            int dw = tr.getWidth(display);
            ctx.drawTextWithShadow(tr, display, btnX + (btnW - dw) / 2, ry + 4, textColor);
        }

        @Override
        void onClick(int mx) {
            int btnX = rx + rw - 60;
            if (mx >= btnX) {
                activeKeyBind = isListening() ? null : this;
            }
        }
    }

    class TextInputRow extends Row {
        final String hint;
        final Consumer<String> onSubmit;
        final int maxLen;
        String buffer;
        boolean hasButton;

        TextInputRow(String h, Consumer<String> sub, int max, boolean btn) {
            hint = h; onSubmit = sub; maxLen = max; buffer = ""; hasButton = btn;
        }

        TextInputRow(String h, Consumer<String> sub, int max, boolean btn, String initial) {
            hint = h; onSubmit = sub; maxLen = max; buffer = initial != null ? initial : ""; hasButton = btn;
        }

        boolean isFocused() { return focusedInput == this; }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {
            boolean focused = isFocused();
            int rightPad = hasButton ? 46 : 0;
            int bg = focused ? 0xFF1A1A24 : 0xFF0E0E12;
            ctx.fill(rx, ry + 1, rx + rw - rightPad, ry + rh - 1, bg);

            int border = focused ? rgba(nr(nc), ng(nc), nb(nc), 120) : 0xFF2A2A30;
            ctx.fill(rx, ry + 1, rx + rw - rightPad, ry + 2, border);
            ctx.fill(rx, ry + rh - 2, rx + rw - rightPad, ry + rh - 1, border);
            ctx.fill(rx, ry + 1, rx + 1, ry + rh - 1, border);
            ctx.fill(rx + rw - rightPad - 1, ry + 1, rx + rw - rightPad, ry + rh - 1, border);

            String display = buffer.isEmpty() ? hint : buffer;
            int textColor = buffer.isEmpty() ? 0xFF4A4A54 : 0xFFD8D8E0;
            ctx.drawTextWithShadow(tr, display, rx + 4, ry + 4, textColor);

            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int curX = rx + 4 + tr.getWidth(buffer);
                ctx.fill(curX, ry + 3, curX + 1, ry + rh - 3, 0xFFD8D8E0);
            }

            if (hasButton) {
                boolean btnHov = mx >= rx + rw - 44 && mx < rx + rw && my >= ry && my < ry + rh;
                int btnBg = btnHov ? rgba(nr(nc), ng(nc), nb(nc), 40) : 0xFF1A1A24;
                ctx.fill(rx + rw - 44, ry + 1, rx + rw, ry + rh - 1, btnBg);
                ctx.fill(rx + rw - 44, ry + 1, rx + rw, ry + 2, border);
                ctx.fill(rx + rw - 44, ry + rh - 2, rx + rw, ry + rh - 1, border);

                String plus = "+";
                int pw = tr.getWidth(plus);
                ctx.drawTextWithShadow(tr, plus, rx + rw - 22 - pw / 2, ry + 4, 0xFF40FF40);
            }
        }

        @Override
        void onClick(int mx) {
            if (hasButton && mx >= rx + rw - 44) {
                submit();
            } else {
                focusedInput = this;
            }
        }

        void submit() {
            if (!buffer.isBlank()) {
                onSubmit.accept(buffer.trim());
                buffer = "";
                focusedInput = null;
            }
        }

        void typeChar(char chr) {
            if (chr >= 32 && buffer.length() < maxLen) {
                buffer += chr;
                if (!hasButton) onSubmit.accept(buffer);
            }
        }

        void backspace() {
            if (!buffer.isEmpty()) {
                buffer = buffer.substring(0, buffer.length() - 1);
                if (!hasButton) onSubmit.accept(buffer);
            }
        }

        void paste(String text) {
            String clean = text.replaceAll("[\\r\\n\\t]", "");
            int space = maxLen - buffer.length();
            if (space <= 0) return;
            if (clean.length() > space) clean = clean.substring(0, space);
            buffer += clean;
            if (!hasButton) onSubmit.accept(buffer);
        }
    }

    class TeammateRow extends Row {
        final String name;
        final PrefsScreen screen;

        TeammateRow(String n, PrefsScreen s) { name = n; screen = s; }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int mx, int my, int nc) {
            ctx.drawTextWithShadow(tr, name, rx + 4, ry + 4, 0xFF40FF40);

            boolean btnHov = mx >= rx + rw - 24 && mx < rx + rw && my >= ry && my < ry + rh;
            int btnBg = btnHov ? rgba(200, 40, 40, 60) : 0xFF1A1A24;
            ctx.fill(rx + rw - 24, ry + 1, rx + rw, ry + rh - 1, btnBg);

            String x = "x";
            int xw = tr.getWidth(x);
            ctx.drawTextWithShadow(tr, x, rx + rw - 12 - xw / 2, ry + 4, 0xFFFF4040);
        }

        @Override
        void onClick(int mx) {
            if (mx >= rx + rw - 24) {
                ProfileIndex.get().remove(name);
                screen.init();
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeKeyBind != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                activeKeyBind = null;
                return true;
            }
            activeKeyBind.set.accept(keyCode);
            prefs.save();
            activeKeyBind = null;
            return true;
        }

        if (focusedInput != null) {
            if (keyCode == 86 && (modifiers & 0x2) != 0 || keyCode == 86 && (modifiers & 0x8) != 0) {
                String clip = net.minecraft.client.MinecraftClient.getInstance().keyboard.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    focusedInput.paste(clip);
                }
                return true;
            }
            if (keyCode == 259) {
                focusedInput.backspace();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                if (focusedInput.hasButton) {
                    focusedInput.submit();
                } else {
                    focusedInput = null;
                }
                return true;
            }
            if (keyCode == 256) {
                focusedInput = null;
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (activeKeyBind != null) return true;
        if (focusedInput != null) {
            focusedInput.typeChar(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }
}
