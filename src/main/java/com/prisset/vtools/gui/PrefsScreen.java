package com.prisset.vtools.gui;

import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

public class PrefsScreen extends Screen {

    private final DisplayPrefs prefs;

    // mutable copies — applied on save
    private boolean active;
    private boolean debugOnly;
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
        super(Text.translatable("prisset-vtools.screen.title"));
        this.prefs = prefs;
        this.active = prefs.isActive();
        this.debugOnly = prefs.isDebugOnly();
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
        int centerX = this.width / 2;
        int sliderW = 200;
        int left = centerX - sliderW / 2;
        int y = 30;
        int rowH = 24;

        // Active toggle
        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.translatable("prisset-vtools.val.on"),
                Text.translatable("prisset-vtools.val.off"))
            .initially(active)
            .build(left, y, sliderW, 20,
                Text.translatable("prisset-vtools.opt.active"),
                (button, value) -> active = value));
        y += rowH;

        // Debug only toggle
        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.translatable("prisset-vtools.val.on"),
                Text.translatable("prisset-vtools.val.off"))
            .initially(debugOnly)
            .build(left, y, sliderW, 20,
                Text.translatable("prisset-vtools.opt.dbg"),
                (button, value) -> debugOnly = value));
        y += rowH + 4;

        // Vertical Scale slider (0.1 - 5.0)
        addDrawableChild(new ValueSlider(left, y, sliderW, 20,
            0.1, 5.0, vScale,
            val -> Text.translatable("prisset-vtools.opt.vs")
                .append(": " + String.format("%.1fx", val)),
            val -> vScale = val.floatValue()));
        y += rowH;

        // Horizontal Scale slider (0.1 - 5.0)
        addDrawableChild(new ValueSlider(left, y, sliderW, 20,
            0.1, 5.0, hScale,
            val -> Text.translatable("prisset-vtools.opt.hs")
                .append(": " + String.format("%.1fx", val)),
            val -> hScale = val.floatValue()));
        y += rowH;

        // Fixed Vertical slider (0.0 - 10.0, where 0 = auto)
        addDrawableChild(new ValueSlider(left, y, sliderW, 20,
            0.0, 10.0, fixedV < 0 ? 0.0 : fixedV,
            val -> {
                String display = val < 0.05 ? "Auto" : String.format("%.1f", val);
                return Text.translatable("prisset-vtools.opt.fv").append(": " + display);
            },
            val -> fixedV = val < 0.05f ? -1.0f : val.floatValue()));
        y += rowH + 4;

        // Tint R
        addDrawableChild(new ValueSlider(left, y, sliderW, 20,
            0, 255, tintR,
            val -> Text.translatable("prisset-vtools.opt.tr")
                .append(": " + val.intValue()),
            val -> tintR = val.intValue()));
        y += rowH;

        // Tint G
        addDrawableChild(new ValueSlider(left, y, sliderW, 20,
            0, 255, tintG,
            val -> Text.translatable("prisset-vtools.opt.tg")
                .append(": " + val.intValue()),
            val -> tintG = val.intValue()));
        y += rowH;

        // Tint B
        addDrawableChild(new ValueSlider(left, y, sliderW, 20,
            0, 255, tintB,
            val -> Text.translatable("prisset-vtools.opt.tb")
                .append(": " + val.intValue()),
            val -> tintB = val.intValue()));
        y += rowH;

        // Tint A
        addDrawableChild(new ValueSlider(left, y, sliderW, 20,
            0, 255, tintA,
            val -> Text.translatable("prisset-vtools.opt.ta")
                .append(": " + val.intValue()),
            val -> tintA = val.intValue()));
        y += rowH + 8;

        // Color preview will be drawn in render() at this Y
        int previewY = y;
        y += 24;

        // Entity filter toggles — 3 in a row
        int btnW = 64;
        int gap = 4;
        int totalW = btnW * 3 + gap * 2;
        int filterLeft = centerX - totalW / 2;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.translatable("prisset-vtools.val.on"),
                Text.translatable("prisset-vtools.val.off"))
            .initially(filterPlayers)
            .build(filterLeft, y, btnW, 20,
                Text.translatable("prisset-vtools.opt.fp"),
                (button, value) -> filterPlayers = value));

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.translatable("prisset-vtools.val.on"),
                Text.translatable("prisset-vtools.val.off"))
            .initially(filterMobs)
            .build(filterLeft + btnW + gap, y, btnW, 20,
                Text.translatable("prisset-vtools.opt.fm"),
                (button, value) -> filterMobs = value));

        addDrawableChild(CyclingButtonWidget.onOffBuilder(
                Text.translatable("prisset-vtools.val.on"),
                Text.translatable("prisset-vtools.val.off"))
            .initially(filterDrops)
            .build(filterLeft + (btnW + gap) * 2, y, btnW, 20,
                Text.translatable("prisset-vtools.opt.fd"),
                (button, value) -> filterDrops = value));
        y += rowH + 8;

        // Apply & Close button
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("prisset-vtools.opt.apply"),
                button -> applyAndClose())
            .dimensions(centerX - 50, y, 100, 20)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        // Title
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            this.title,
            this.width / 2,
            12,
            0xFFFFFF
        );

        // Color preview swatch
        int previewSize = 16;
        int previewX = this.width / 2 - previewSize / 2;
        // positioned after tint A slider (row 8 from top, each 24px, offset 30 start + 8*24 + 8 gap)
        int previewY = 30 + 8 * 24 + 12;
        int color = (tintA << 24) | (tintR << 16) | (tintG << 8) | tintB;
        context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, color);
        // border
        context.drawBorder(previewX - 1, previewY - 1, previewSize + 2, previewSize + 2, 0xFFAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        applyAndClose();
    }

    private void applyAndClose() {
        prefs.setActive(active);
        prefs.setDebugOnly(debugOnly);
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
