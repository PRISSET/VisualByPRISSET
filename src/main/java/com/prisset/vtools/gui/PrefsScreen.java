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
    private boolean stealth;
    private float vScale;
    private float hScale;
    private float fixedV;
    private int tintR;
    private int tintG;
    private int tintB;
    private int tintA;
    private boolean filterPlayers;
    private boolean filterMobs;
    private boolean filterDrops;

    public PrefsScreen(DisplayPrefs prefs) {
        super(Text.literal("PRISSET Visual Tools"));
        this.prefs = prefs;
        this.active = prefs.isActive();
        this.debugOnly = prefs.isDebugOnly();
        this.stealth = prefs.isStealth();
        this.vScale = prefs.getVScale();
        this.hScale = prefs.getHScale();
        this.fixedV = prefs.getFixedV();
        this.tintR = prefs.getTintR();
        this.tintG = prefs.getTintG();
        this.tintB = prefs.getTintB();
        this.tintA = prefs.getTintA();
        this.filterPlayers = prefs.isFilterPlayers();
        this.filterMobs = prefs.isFilterMobs();
        this.filterDrops = prefs.isFilterDrops();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 200;
        int left = cx - w / 2;
        int y = 24;

        // === MAIN TOGGLES ===

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("ON"), Text.literal("OFF"))
            .initially(active)
            .build(left, y, w, 20,
                Text.literal("Overlay Active"),
                (btn, val) -> active = val));
        y += 22;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("ON"), Text.literal("OFF"))
            .initially(debugOnly)
            .build(left, y, w, 20,
                Text.literal("Only with F3+B"),
                (btn, val) -> debugOnly = val));
        y += 22;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("ON"), Text.literal("OFF"))
            .initially(stealth)
            .build(left, y, w, 20,
                Text.literal("Stealth Mode"),
                (btn, val) -> stealth = val));
        y += 26;

        // === SIZE CONTROLS ===

        addDrawableChild(new ValueSlider(left, y, w, 20,
            0.1, 5.0, vScale,
            val -> Text.literal("Height Scale: " + String.format("%.1fx", val)),
            val -> vScale = val.floatValue()));
        y += 22;

        addDrawableChild(new ValueSlider(left, y, w, 20,
            0.1, 5.0, hScale,
            val -> Text.literal("Width Scale: " + String.format("%.1fx", val)),
            val -> hScale = val.floatValue()));
        y += 22;

        addDrawableChild(new ValueSlider(left, y, w, 20,
            0.0, 10.0, fixedV < 0 ? 0.0 : fixedV,
            val -> Text.literal("Fixed Height: " + (val < 0.05 ? "Auto" : String.format("%.1f", val))),
            val -> fixedV = val < 0.05f ? -1.0f : val.floatValue()));
        y += 26;

        // === COLOR ===

        addDrawableChild(new ValueSlider(left, y, w, 20,
            0, 255, tintR,
            val -> Text.literal("Red: " + val.intValue()),
            val -> tintR = val.intValue()));
        y += 22;

        addDrawableChild(new ValueSlider(left, y, w, 20,
            0, 255, tintG,
            val -> Text.literal("Green: " + val.intValue()),
            val -> tintG = val.intValue()));
        y += 22;

        addDrawableChild(new ValueSlider(left, y, w, 20,
            0, 255, tintB,
            val -> Text.literal("Blue: " + val.intValue()),
            val -> tintB = val.intValue()));
        y += 22;

        addDrawableChild(new ValueSlider(left, y, w, 20,
            0, 255, tintA,
            val -> Text.literal("Alpha: " + val.intValue()),
            val -> tintA = val.intValue()));
        y += 26;

        // === ENTITY FILTERS ===

        int btnW = 64;
        int gap = 4;
        int totalW = btnW * 3 + gap * 2;
        int fl = cx - totalW / 2;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("ON"), Text.literal("OFF"))
            .initially(filterPlayers)
            .build(fl, y, btnW, 20,
                Text.literal("Players"),
                (btn, val) -> filterPlayers = val));

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("ON"), Text.literal("OFF"))
            .initially(filterMobs)
            .build(fl + btnW + gap, y, btnW, 20,
                Text.literal("Mobs"),
                (btn, val) -> filterMobs = val));

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.literal("ON"), Text.literal("OFF"))
            .initially(filterDrops)
            .build(fl + (btnW + gap) * 2, y, btnW, 20,
                Text.literal("Drops"),
                (btn, val) -> filterDrops = val));
        y += 28;

        // === APPLY BUTTON ===

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Apply & Close"),
                btn -> applyAndClose())
            .dimensions(cx - 60, y, 120, 20)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
            this.width / 2, 8, 0x00FF00);

        // Color preview swatch
        int swatchSize = 20;
        int swatchX = this.width / 2 + 110;
        int swatchY = 24 + 7 * 22 + 26;
        int color = (tintA << 24) | (tintR << 16) | (tintG << 8) | tintB;
        context.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, color);
        context.drawBorder(swatchX - 1, swatchY - 1, swatchSize + 2, swatchSize + 2, 0xFFAAAAAA);

        // Stealth mode hint
        if (stealth) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Stealth: expanded box hidden visually"),
                this.width / 2, this.height - 14, 0xFF5555);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        applyAndClose();
    }

    private void applyAndClose() {
        prefs.setActive(active);
        prefs.setDebugOnly(debugOnly);
        prefs.setStealth(stealth);
        prefs.setVScale(vScale);
        prefs.setHScale(hScale);
        prefs.setFixedV(fixedV);
        prefs.setTintR(tintR);
        prefs.setTintG(tintG);
        prefs.setTintB(tintB);
        prefs.setTintA(tintA);
        prefs.setFilterPlayers(filterPlayers);
        prefs.setFilterMobs(filterMobs);
        prefs.setFilterDrops(filterDrops);
        prefs.save();

        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
