package com.prisset.vtools.input;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MarketParser {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");

    private MarketParser() {}

    public static class MarketItem {
        private final int slotIndex;
        private final String itemId;
        private final String displayName;
        private final int totalPrice;
        private final int pricePerUnit;
        private final int count;
        private final String seller;

        public MarketItem(int slotIndex, String itemId, String displayName,
                          int totalPrice, int pricePerUnit, int count, String seller) {
            this.slotIndex = slotIndex;
            this.itemId = itemId;
            this.displayName = displayName;
            this.totalPrice = totalPrice;
            this.pricePerUnit = pricePerUnit;
            this.count = count;
            this.seller = seller;
        }

        public int getSlotIndex() { return slotIndex; }
        public String getItemId() { return itemId; }
        public String getDisplayName() { return displayName; }
        public int getTotalPrice() { return totalPrice; }
        public int getPricePerUnit() { return pricePerUnit; }
        public int getCount() { return count; }
        public String getSeller() { return seller; }
    }

    public static List<MarketItem> parseSlots(ScreenHandler handler) {
        if (handler == null) return Collections.emptyList();

        int slotCount = Math.min(handler.slots.size(), 45);
        List<MarketItem> items = new ArrayList<>();

        for (int i = 0; i < slotCount; i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            if (isNavButton(itemId)) continue;

            String displayName = stack.getName().getString();
            int count = stack.getCount();

            NbtCompound nbt = stack.getNbt();
            if (nbt == null || !nbt.contains("display", 10)) continue;

            NbtCompound display = nbt.getCompound("display");
            if (!display.contains("Lore", 9)) continue;

            NbtList lore = display.getList("Lore", 8);
            int totalPrice = 0;
            int unitPrice = 0;
            String seller = "";

            for (int j = 0; j < lore.size(); j++) {
                String loreLine = lore.getString(j);
                try {
                    Text parsed = Text.Serializer.fromJson(loreLine);
                    if (parsed == null) continue;
                    String text = parsed.getString();

                    if (text.contains("\u041f\u0440\u043e\u0434\u0430\u0432\u0435\u0446:")) {
                        seller = extractAfterColon(text);
                    } else if (text.contains("\u0426\u0435\u043d\u0430 \u0437\u0430 1 \u0448\u0442.:")) {
                        unitPrice = parsePrice(text);
                    } else if (text.contains("\u0426\u0435\u043d\u0430:")) {
                        totalPrice = parsePrice(text);
                    }
                } catch (Exception ignored) {}
            }

            if (unitPrice == 0 && totalPrice > 0 && count > 0) {
                unitPrice = totalPrice / count;
            }
            if (unitPrice == 0 && totalPrice > 0) {
                unitPrice = totalPrice;
            }

            items.add(new MarketItem(i, itemId, displayName, totalPrice, unitPrice, count, seller));
        }

        return items;
    }

    public static int parsePageInfo(String title) {
        if (title == null) return -1;
        int slash = title.indexOf('/');
        if (slash < 0) return -1;
        try {
            int paren = title.lastIndexOf('(', slash);
            int end = title.indexOf(')', slash);
            if (paren < 0 || end < 0) return -1;
            return Integer.parseInt(title.substring(slash + 1, end).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public static int parseCurrentPage(String title) {
        if (title == null) return -1;
        try {
            int paren = title.indexOf('(');
            int slash = title.indexOf('/');
            if (paren < 0 || slash < 0) return -1;
            return Integer.parseInt(title.substring(paren + 1, slash).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static int parsePrice(String text) {
        int colonIdx = text.indexOf(':');
        if (colonIdx < 0) return 0;
        String after = text.substring(colonIdx + 1);
        StringBuilder digits = new StringBuilder();
        for (char c : after.toCharArray()) {
            if (c >= '0' && c <= '9') digits.append(c);
        }
        if (digits.length() == 0) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String extractAfterColon(String text) {
        int colonIdx = text.indexOf(':');
        if (colonIdx < 0) return "";
        return text.substring(colonIdx + 1).trim();
    }

    private static boolean isNavButton(String itemId) {
        return itemId.contains("ender_chest")
                || itemId.contains("gray_dye")
                || itemId.contains("lime_dye")
                || itemId.contains("nether_star")
                || itemId.contains("paper")
                || itemId.contains("hopper")
                || itemId.contains("chest_minecart")
                || itemId.equals("minecraft:emerald")
                || itemId.equals("minecraft:chest");
    }
}
