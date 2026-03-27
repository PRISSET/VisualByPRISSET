package com.prisset.vtools.render;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.*;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChestSearchOverlay {

    private static boolean sorting = false;
    private static final List<int[]> pendingSwaps = new ArrayList<>();
    private static int swapDelay = 0;
    private static int lastPickedFrom = -1;

    private ChestSearchOverlay() {}

    public static void onScreenOpen() {
        sorting = false;
        pendingSwaps.clear();
        swapDelay = 0;
        lastPickedFrom = -1;
    }

    public static void onScreenClose() {
        if (sorting) {
            dropCursor();
        }
        sorting = false;
        pendingSwaps.clear();
        swapDelay = 0;
        lastPickedFrom = -1;
    }

    public static boolean isSorting() {
        return sorting;
    }

    public static boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isChestSearchEnabled()) return false;

        if (keyCode == 82) {
            if (!sorting) triggerSort();
            return true;
        }

        return false;
    }

    public static void tick() {
        if (!sorting || pendingSwaps.isEmpty()) return;

        if (swapDelay > 0) {
            swapDelay--;
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) {
            cancelSort();
            return;
        }

        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler)) {
            cancelSort();
            return;
        }

        int[] swap = pendingSwaps.remove(0);
        executeSwap(mc, swap[0], swap[1]);
        swapDelay = 2;

        if (pendingSwaps.isEmpty()) {
            sorting = false;
            lastPickedFrom = -1;
        }
    }

    private static void cancelSort() {
        dropCursor();
        pendingSwaps.clear();
        sorting = false;
        lastPickedFrom = -1;
    }

    private static void dropCursor() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        ItemStack cursor = handler.getCursorStack();
        if (cursor != null && !cursor.isEmpty() && lastPickedFrom >= 0) {
            mc.interactionManager.clickSlot(
                handler.syncId, lastPickedFrom, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private static void triggerSort() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!(handler instanceof GenericContainerScreenHandler container)) return;

        int containerSlots = container.getRows() * 9;

        List<SlotEntry> entries = new ArrayList<>();
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            entries.add(new SlotEntry(i, stack));
        }

        entries.sort(Comparator.comparingInt((SlotEntry e) -> category(e.stack))
                .thenComparing(e -> e.stack.getName().getString())
                .thenComparing(e -> -e.stack.getCount()));

        pendingSwaps.clear();

        int targetIdx = 0;
        boolean[] placed = new boolean[containerSlots];
        int[] mapping = new int[containerSlots];
        for (int i = 0; i < containerSlots; i++) mapping[i] = i;

        for (SlotEntry entry : entries) {
            while (targetIdx < containerSlots && placed[targetIdx]) targetIdx++;
            if (targetIdx >= containerSlots) break;

            int currentSlot = findCurrent(mapping, entry.originalSlot);
            if (currentSlot != targetIdx) {
                pendingSwaps.add(new int[]{currentSlot, targetIdx});
                int tmp = mapping[currentSlot];
                mapping[currentSlot] = mapping[targetIdx];
                mapping[targetIdx] = tmp;
            }
            placed[targetIdx] = true;
            targetIdx++;
        }

        sorting = !pendingSwaps.isEmpty();
        swapDelay = 1;
    }

    private static int findCurrent(int[] mapping, int originalSlot) {
        for (int i = 0; i < mapping.length; i++) {
            if (mapping[i] == originalSlot) return i;
        }
        return originalSlot;
    }

    private static void executeSwap(MinecraftClient mc, int from, int to) {
        ClientPlayerEntity player = mc.player;
        ClientPlayerInteractionManager im = mc.interactionManager;
        int syncId = player.currentScreenHandler.syncId;

        lastPickedFrom = from;
        im.clickSlot(syncId, from, 0, SlotActionType.PICKUP, player);
        im.clickSlot(syncId, to, 0, SlotActionType.PICKUP, player);

        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        if (cursor != null && !cursor.isEmpty()) {
            im.clickSlot(syncId, from, 0, SlotActionType.PICKUP, player);
        }
        lastPickedFrom = -1;
    }

    private static int category(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof SwordItem) return 0;
        if (item instanceof AxeItem) return 1;
        if (item instanceof PickaxeItem) return 2;
        if (item instanceof ShovelItem) return 3;
        if (item instanceof HoeItem) return 4;
        if (item instanceof BowItem || item instanceof CrossbowItem) return 5;
        if (item instanceof ArmorItem armor) {
            return 10 + armor.getSlotType().ordinal();
        }
        if (item instanceof ShieldItem) return 15;
        if (item.isFood()) return 20;
        if (item instanceof BlockItem) return 30;
        if (item instanceof ToolItem) return 6;
        return 50;
    }

    private static class SlotEntry {
        final int originalSlot;
        final ItemStack stack;

        SlotEntry(int slot, ItemStack stack) {
            this.originalSlot = slot;
            this.stack = stack.copy();
        }
    }
}
