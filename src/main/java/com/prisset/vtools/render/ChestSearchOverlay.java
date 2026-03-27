package com.prisset.vtools.render;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class ChestSearchOverlay {

    private static String query = "";
    private static boolean active = false;

    private ChestSearchOverlay() {}

    public static void onScreenOpen() {
        query = "";
        active = false;
    }

    public static void onScreenClose() {
        query = "";
        active = false;
    }

    public static boolean onCharTyped(char chr) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isChestSearchEnabled()) return false;

        if (chr >= 32) {
            query += chr;
            active = true;
            return true;
        }
        return false;
    }

    public static boolean onKeyPressed(int keyCode) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isChestSearchEnabled()) return false;

        if (keyCode == 259 && active) {
            if (!query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
            }
            if (query.isEmpty()) active = false;
            return true;
        }

        if (keyCode == 256 && active) {
            query = "";
            active = false;
            return true;
        }

        return false;
    }

    public static boolean isActive() {
        return active && !query.isEmpty();
    }

    public static void render(DrawContext ctx, HandledScreen<?> screen,
                               int guiLeft, int guiTop) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isChestSearchEnabled()) return;
        if (!isActive()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;

        String lowerQuery = query.toLowerCase();

        ScreenHandler handler = screen.getScreenHandler();
        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            String itemName = stack.getName().getString().toLowerCase();
            boolean matches = itemName.contains(lowerQuery);

            int slotX = guiLeft + slot.x;
            int slotY = guiTop + slot.y;

            if (matches) {
                ctx.fill(slotX, slotY, slotX + 16, slotY + 1, 0xFFFF8800);
                ctx.fill(slotX, slotY + 15, slotX + 16, slotY + 16, 0xFFFF8800);
                ctx.fill(slotX, slotY, slotX + 1, slotY + 16, 0xFFFF8800);
                ctx.fill(slotX + 15, slotY, slotX + 16, slotY + 16, 0xFFFF8800);
            } else {
                ctx.fill(slotX, slotY, slotX + 16, slotY + 16, 0xC0000000);
            }
        }

        int screenW = mc.getWindow().getScaledWidth();
        String display = "> " + query;
        if ((System.currentTimeMillis() / 500) % 2 == 0) display += "_";
        int tw = tr.getWidth(display);
        int bx = screenW / 2 - tw / 2 - 4;
        int by = guiTop - 16;
        ctx.fill(bx, by, bx + tw + 8, by + 12, 0xCC000000);
        ctx.fill(bx, by, bx + tw + 8, by + 1, 0xFFFF8800);
        ctx.drawTextWithShadow(tr, display, bx + 4, by + 2, 0xFFFFAA00);
    }

    public static String getQuery() {
        return query;
    }
}
