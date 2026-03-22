package com.prisset.vtools.gui;

import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

public class PrefsScreen extends Screen {

    private final DisplayPrefs prefs;

    private boolean active;
    private boolean debugOnly;
    private int tintR;
    private int tintG;
    private int tintB;
    private int tintA;
    private boolean rgbMode;
    private float rgbSpeed;
    private boolean healthBars;
    private float zoomStrength;
    private boolean trailEnabled;
    private int trailLength;
    private boolean filterPlayers;
    private boolean filterMobs;
    private boolean filterDrops;

    public PrefsScreen(DisplayPrefs prefs) {
        super(Text.literal("\u00a7aPRISSET \u00a7fVisual Tools"));
        this.prefs = prefs;
        this.active = prefs.isActive();
        this.debugOnly = prefs.isDebugOnly();
        this.tintR = prefs.getTintR();
        this.tintG = prefs.getTintG();
        this.tintB = prefs.getTintB();
        this.tintA = prefs.getTintA();
        this.rgbMode = prefs.isRgbMode();
        this.rgbSpeed = prefs.getRgbSpeed();
        this.healthBars = prefs.isHealthBars();
        this.zoomStrength = prefs.getZoomStrength();
        this.trailEnabled = prefs.isTrailEnabled();
        this.trailLength = prefs.getTrailLength();
        this.filterPlayers = prefs.isFilterPlayers();
        this.filterMobs = prefs.isFilterMobs();
        this.filterDrops = prefs.isFilterDrops();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 210;
        int left = cx - w / 2;
        int y = 28;

        // === MAIN ===

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("\u00a7a\u0412\u041a\u041b"), Text.literal("\u00a7c\u0412\u042b\u041a\u041b"))
            .initially(active)
            .build(left, y, w, 20,
                Text.literal("\u041e\u0432\u0435\u0440\u043b\u0435\u0439"),
                (btn, val) -> active = val));
        y += 22;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("\u00a7a\u0412\u041a\u041b"), Text.literal("\u00a7c\u0412\u042b\u041a\u041b"))
            .initially(debugOnly)
            .build(left, y, w, 20,
                Text.literal("\u0422\u043e\u043b\u044c\u043a\u043e \u0441 F3+B"),
                (btn, val) -> debugOnly = val));
        y += 26;

        // === COLOR ===

        addDrawableChild(new ValueSlider(left, y, w, 20,
            0, 255, tintR,
            val -> Text.literal("\u00a7c\u041a\u0440\u0430\u0441\u043d\u044b\u0439: " + val.intValue()),
            val -> tintR = val.intValue()));
        y += 22;

        addDrawableChild(new ValueSlider(left, y, w, 20,
            0, 255, tintG,
            val -> Text.literal("\u00a7a\u0417\u0435\u043b\u0451\u043d\u044b\u0439: " + val.intValue()),
            val -> tintG = val.intValue()));
        y += 22;

        addDrawableChild(new ValueSlider(left, y, w, 20,
            0, 255, tintB,
            val -> Text.literal("\u00a79\u0421\u0438\u043d\u0438\u0439: " + val.intValue()),
            val -> tintB = val.intValue()));
        y += 22;

        addDrawableChild(new ValueSlider(left, y, w, 20,
            0, 255, tintA,
            val -> Text.literal("\u00a77\u041f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0441\u0442\u044c: " + val.intValue()),
            val -> tintA = val.intValue()));
        y += 24;

        // === RGB ===

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("\u00a7a\u0412\u041a\u041b"), Text.literal("\u00a7c\u0412\u042b\u041a\u041b"))
            .initially(rgbMode)
            .build(left, y, w / 2 - 2, 20,
                Text.literal("\u00a7dRGB"),
                (btn, val) -> rgbMode = val));

        addDrawableChild(new ValueSlider(left + w / 2 + 2, y, w / 2 - 2, 20,
            0.1, 5.0, rgbSpeed,
            val -> Text.literal("\u00a7d\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c: " + String.format("%.1fx", val)),
            val -> rgbSpeed = val.floatValue()));
        y += 24;

        // === HEALTH ===

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("\u00a7a\u0412\u041a\u041b"), Text.literal("\u00a7c\u0412\u042b\u041a\u041b"))
            .initially(healthBars)
            .build(left, y, w, 20,
                Text.literal("\u00a7c\u2764 \u00a7f\u0417\u0434\u043e\u0440\u043e\u0432\u044c\u0435"),
                (btn, val) -> healthBars = val));
        y += 24;

        // === ZOOM ===

        addDrawableChild(new ValueSlider(left, y, w, 20,
            1.5, 10.0, zoomStrength,
            val -> Text.literal("\u00a7b\u0417\u0443\u043c (C): " + String.format("%.1fx", val)),
            val -> zoomStrength = val.floatValue()));
        y += 24;

        // === TRAIL ===

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("\u00a7a\u0412\u041a\u041b"), Text.literal("\u00a7c\u0412\u042b\u041a\u041b"))
            .initially(trailEnabled)
            .build(left, y, w / 2 - 2, 20,
                Text.literal("\u00a7e\u0421\u043b\u0435\u0434"),
                (btn, val) -> trailEnabled = val));

        addDrawableChild(new ValueSlider(left + w / 2 + 2, y, w / 2 - 2, 20,
            10, 200, trailLength,
            val -> Text.literal("\u00a7e\u0414\u043b\u0438\u043d\u0430: " + val.intValue()),
            val -> trailLength = val.intValue()));
        y += 24;

        // === FILTERS ===

        int btnW = 66;
        int gap = 4;
        int totalW = btnW * 3 + gap * 2;
        int fl = cx - totalW / 2;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("\u00a7a\u0414\u0430"), Text.literal("\u00a7c\u041d\u0435\u0442"))
            .initially(filterPlayers)
            .build(fl, y, btnW, 20,
                Text.literal("\u0418\u0433\u0440\u043e\u043a\u0438"),
                (btn, val) -> filterPlayers = val));

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("\u00a7a\u0414\u0430"), Text.literal("\u00a7c\u041d\u0435\u0442"))
            .initially(filterMobs)
            .build(fl + btnW + gap, y, btnW, 20,
                Text.literal("\u041c\u043e\u0431\u044b"),
                (btn, val) -> filterMobs = val));

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("\u00a7a\u0414\u0430"), Text.literal("\u00a7c\u041d\u0435\u0442"))
            .initially(filterDrops)
            .build(fl + (btnW + gap) * 2, y, btnW, 20,
                Text.literal("\u0414\u0440\u043e\u043f"),
                (btn, val) -> filterDrops = val));
        y += 28;

        // === APPLY ===

        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00a7a\u041f\u0440\u0438\u043c\u0435\u043d\u0438\u0442\u044c"),
                btn -> applyAndClose())
            .dimensions(cx - 60, y, 120, 20)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
            this.width / 2, 10, 0xFFFFFF);

        // Color swatch
        int sz = 20;
        int sx = this.width / 2 + 115;
        int sy = 28 + 2 * 22 + 26;
        int color = (tintA << 24) | (tintR << 16) | (tintG << 8) | tintB;
        context.fill(sx, sy, sx + sz, sy + sz, color);
        context.drawBorder(sx - 1, sy - 1, sz + 2, sz + 2, 0xFFAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        applyAndClose();
    }

    private void applyAndClose() {
        prefs.setActive(active);
        prefs.setDebugOnly(debugOnly);
        prefs.setTintR(tintR);
        prefs.setTintG(tintG);
        prefs.setTintB(tintB);
        prefs.setTintA(tintA);
        prefs.setRgbMode(rgbMode);
        prefs.setRgbSpeed(rgbSpeed);
        prefs.setHealthBars(healthBars);
        prefs.setZoomStrength(zoomStrength);
        prefs.setTrailEnabled(trailEnabled);
        prefs.setTrailLength(trailLength);
        prefs.setFilterPlayers(filterPlayers);
        prefs.setFilterMobs(filterMobs);
        prefs.setFilterDrops(filterDrops);
        prefs.save();

        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
