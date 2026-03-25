package com.prisset.vtools.gui;

import com.prisset.vtools.config.BuyRuleStore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class BuyRuleEditScreen extends Screen {

    private static final int W = 200;
    private static final int H = 100;

    private final Screen parent;
    private final BuyRuleStore.BuyRule rule;
    private String priceBuffer;
    private String qtyBuffer;
    private int focused = 0;
    private int wx, wy;

    public BuyRuleEditScreen(Screen parent, BuyRuleStore.BuyRule rule) {
        super(Text.literal("Edit Rule"));
        this.parent = parent;
        this.rule = rule;
        this.priceBuffer = String.valueOf(rule.getMaxPricePerUnit());
        this.qtyBuffer = String.valueOf(rule.getQuantity());
    }

    @Override
    protected void init() {
        wx = (width - W) / 2;
        wy = (height - H) / 2;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xA0000000);
        ctx.fill(wx, wy, wx + W, wy + H, 0xFF0A0A0E);
        ctx.fill(wx, wy, wx + W, wy + 1, 0xFF5050FF);
        ctx.fill(wx, wy + H - 1, wx + W, wy + H, 0xFF5050FF);
        ctx.fill(wx, wy, wx + 1, wy + H, 0xFF5050FF);
        ctx.fill(wx + W - 1, wy, wx + W, wy + H, 0xFF5050FF);

        String name = rule.getDisplayName();
        if (name.length() > 24) name = name.substring(0, 22) + "..";
        ctx.drawTextWithShadow(textRenderer, name, wx + 8, wy + 6, 0xFFD8D8E0);

        int fieldX = wx + 8;
        int fieldW = W - 16;

        ctx.drawTextWithShadow(textRenderer, "\u0426\u0435\u043d\u0430 \u0437\u0430 \u0448\u0442.:", fieldX, wy + 22, 0xFF808090);
        int pBg = focused == 0 ? 0xFF1A1A24 : 0xFF0E0E12;
        int pBorder = focused == 0 ? 0xFF5050FF : 0xFF2A2A30;
        ctx.fill(fieldX, wy + 32, fieldX + fieldW, wy + 46, pBg);
        ctx.fill(fieldX, wy + 32, fieldX + fieldW, wy + 33, pBorder);
        ctx.fill(fieldX, wy + 45, fieldX + fieldW, wy + 46, pBorder);
        String pDisplay = priceBuffer.isEmpty() ? "0" : priceBuffer;
        ctx.drawTextWithShadow(textRenderer, pDisplay, fieldX + 4, wy + 35, priceBuffer.isEmpty() ? 0xFF4A4A54 : 0xFFD8D8E0);
        if (focused == 0 && (System.currentTimeMillis() / 500) % 2 == 0) {
            int curX = fieldX + 4 + textRenderer.getWidth(priceBuffer);
            ctx.fill(curX, wy + 34, curX + 1, wy + 44, 0xFFD8D8E0);
        }

        ctx.drawTextWithShadow(textRenderer, "\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e:", fieldX, wy + 52, 0xFF808090);
        int qBg = focused == 1 ? 0xFF1A1A24 : 0xFF0E0E12;
        int qBorder = focused == 1 ? 0xFF5050FF : 0xFF2A2A30;
        ctx.fill(fieldX, wy + 62, fieldX + fieldW, wy + 76, qBg);
        ctx.fill(fieldX, wy + 62, fieldX + fieldW, wy + 63, qBorder);
        ctx.fill(fieldX, wy + 75, fieldX + fieldW, wy + 76, qBorder);
        String qDisplay = qtyBuffer.isEmpty() ? "0" : qtyBuffer;
        ctx.drawTextWithShadow(textRenderer, qDisplay, fieldX + 4, wy + 65, qtyBuffer.isEmpty() ? 0xFF4A4A54 : 0xFFD8D8E0);
        if (focused == 1 && (System.currentTimeMillis() / 500) % 2 == 0) {
            int curX = fieldX + 4 + textRenderer.getWidth(qtyBuffer);
            ctx.fill(curX, wy + 64, curX + 1, wy + 74, 0xFFD8D8E0);
        }

        boolean saveHov = mx >= wx + W / 2 - 40 && mx < wx + W / 2 + 40 && my >= wy + H - 18 && my < wy + H - 4;
        int saveBg = saveHov ? 0xFF303060 : 0xFF1A1A24;
        ctx.fill(wx + W / 2 - 40, wy + H - 18, wx + W / 2 + 40, wy + H - 4, saveBg);
        String saveTxt = "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c";
        int sw = textRenderer.getWidth(saveTxt);
        ctx.drawTextWithShadow(textRenderer, saveTxt, wx + W / 2 - sw / 2, wy + H - 15, 0xFF40FF40);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int fieldX = wx + 8;
        int fieldW = W - 16;

        if (mx >= fieldX && mx < fieldX + fieldW) {
            if (my >= wy + 32 && my < wy + 46) { focused = 0; return true; }
            if (my >= wy + 62 && my < wy + 76) { focused = 1; return true; }
        }

        if (mx >= wx + W / 2 - 40 && mx < wx + W / 2 + 40 && my >= wy + H - 18 && my < wy + H - 4) {
            saveAndClose();
            return true;
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            saveAndClose();
            return true;
        }
        if (keyCode == 259) {
            if (focused == 0 && !priceBuffer.isEmpty()) {
                priceBuffer = priceBuffer.substring(0, priceBuffer.length() - 1);
            } else if (focused == 1 && !qtyBuffer.isEmpty()) {
                qtyBuffer = qtyBuffer.substring(0, qtyBuffer.length() - 1);
            }
            return true;
        }
        if (keyCode == 258) {
            focused = (focused + 1) % 2;
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr >= '0' && chr <= '9') {
            if (focused == 0 && priceBuffer.length() < 10) priceBuffer += chr;
            if (focused == 1 && qtyBuffer.length() < 6) qtyBuffer += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void saveAndClose() {
        try {
            int price = priceBuffer.isEmpty() ? 0 : Integer.parseInt(priceBuffer);
            int qty = qtyBuffer.isEmpty() ? 1 : Integer.parseInt(qtyBuffer);
            if (price > 0) rule.setMaxPricePerUnit(price);
            if (qty > 0) rule.setQuantity(qty);
            BuyRuleStore.get().save();
        } catch (NumberFormatException ignored) {}

        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
