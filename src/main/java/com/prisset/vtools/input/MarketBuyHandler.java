package com.prisset.vtools.input;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.BuyRuleStore;
import com.prisset.vtools.config.BuyRuleStore.BuyRule;
import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.input.MarketParser.MarketItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MarketBuyHandler {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final int ACTION_DELAY_TICKS = 40;
    private static final int REFRESH_SLOT = 47;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int PREV_PAGE_SLOT = 48;

    private static State state = State.IDLE;
    private static int delayCooldown = 0;
    private static int matchedSlot = -1;
    private static BuyRule matchedRule = null;
    private static int currentPage = 0;
    private static int totalPages = 0;
    private static boolean waitingForGui = false;
    private static long guiWaitStart = 0;
    private static final long GUI_WAIT_TIMEOUT = 5000L;

    private static final List<MarketItem> cachedMarketItems = new ArrayList<>();

    private enum State {
        IDLE,
        OPENING_MARKET,
        SCANNING,
        CLICKING_ITEM,
        WAITING_CONFIRM,
        CLICKING_BUY,
        CYCLE_DONE
    }

    private MarketBuyHandler() {}

    public static List<MarketItem> getCachedItems() {
        return Collections.unmodifiableList(cachedMarketItems);
    }

    public static void clearCache() {
        cachedMarketItems.clear();
    }

    public static String getStatusText() {
        switch (state) {
            case IDLE: return "\u041e\u0436\u0438\u0434\u0430\u043d\u0438\u0435";
            case OPENING_MARKET: return "\u041e\u0442\u043a\u0440\u044b\u0442\u0438\u0435 \u043c\u0430\u0440\u043a\u0435\u0442\u0430...";
            case SCANNING: return "\u0421\u043a\u0430\u043d\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435 \u0441\u0442\u0440. " + currentPage + "/" + totalPages;
            case CLICKING_ITEM: return "\u041f\u043e\u043a\u0443\u043f\u043a\u0430...";
            case WAITING_CONFIRM: return "\u041e\u0436\u0438\u0434\u0430\u043d\u0438\u0435 \u043f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0438\u044f...";
            case CLICKING_BUY: return "\u041f\u043e\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043d\u0438\u0435 \u043f\u043e\u043a\u0443\u043f\u043a\u0438...";
            case CYCLE_DONE: return "\u041e\u0431\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435...";
            default: return "";
        }
    }

    public static void stop() {
        state = State.IDLE;
        delayCooldown = 0;
        matchedSlot = -1;
        matchedRule = null;
        waitingForGui = false;
    }

    public static void tick() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isAutoBuy()) {
            if (state != State.IDLE) stop();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            stop();
            return;
        }

        List<BuyRule> active = BuyRuleStore.get().activeRules();
        if (active.isEmpty()) {
            if (state != State.IDLE) stop();
            return;
        }

        if (delayCooldown > 0) {
            delayCooldown--;
            return;
        }

        switch (state) {
            case IDLE -> tickIdle(mc);
            case OPENING_MARKET -> tickOpeningMarket(mc);
            case SCANNING -> tickScanning(mc, active);
            case CLICKING_ITEM -> tickClickingItem(mc);
            case WAITING_CONFIRM -> tickWaitingConfirm(mc);
            case CLICKING_BUY -> tickClickingBuy(mc);
            case CYCLE_DONE -> tickCycleDone(mc);
        }
    }

    private static void tickIdle(MinecraftClient mc) {
        if (mc.currentScreen != null) return;
        cachedMarketItems.clear();
        mc.player.networkHandler.sendChatCommand("ah");
        state = State.OPENING_MARKET;
        waitingForGui = true;
        guiWaitStart = System.currentTimeMillis();
        LOG.info("[AutoBuy] Opening market...");
    }

    private static void tickOpeningMarket(MinecraftClient mc) {
        if (!isMarketScreen(mc)) {
            if (System.currentTimeMillis() - guiWaitStart > GUI_WAIT_TIMEOUT) {
                LOG.warn("[AutoBuy] Market GUI timeout, retrying...");
                state = State.IDLE;
                delayCooldown = ACTION_DELAY_TICKS;
            }
            return;
        }
        waitingForGui = false;
        state = State.SCANNING;
        updatePageInfo(mc);
    }

    private static void tickScanning(MinecraftClient mc, List<BuyRule> active) {
        if (!isMarketScreen(mc)) {
            state = State.IDLE;
            delayCooldown = ACTION_DELAY_TICKS;
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        List<MarketItem> items = MarketParser.parseSlots(handler);

        cachedMarketItems.clear();
        cachedMarketItems.addAll(items);

        for (MarketItem item : items) {
            for (BuyRule rule : active) {
                if (matchesRule(item, rule)) {
                    matchedSlot = item.getSlotIndex();
                    matchedRule = rule;
                    state = State.CLICKING_ITEM;
                    delayCooldown = ACTION_DELAY_TICKS;
                    LOG.info("[AutoBuy] Match found: {} price={} slot={}",
                            item.getDisplayName(), item.getPricePerUnit(), matchedSlot);
                    return;
                }
            }
        }

        state = State.CYCLE_DONE;
        delayCooldown = ACTION_DELAY_TICKS;
    }

    private static void tickClickingItem(MinecraftClient mc) {
        if (!isMarketScreen(mc) || matchedSlot < 0) {
            state = State.IDLE;
            delayCooldown = ACTION_DELAY_TICKS;
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        mc.interactionManager.clickSlot(
                handler.syncId, matchedSlot, 0, SlotActionType.PICKUP, mc.player);

        state = State.WAITING_CONFIRM;
        waitingForGui = true;
        guiWaitStart = System.currentTimeMillis();
    }

    private static void tickWaitingConfirm(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof HandledScreen<?>)) {
            if (System.currentTimeMillis() - guiWaitStart > GUI_WAIT_TIMEOUT) {
                LOG.warn("[AutoBuy] Confirm GUI timeout");
                state = State.IDLE;
                delayCooldown = ACTION_DELAY_TICKS;
            }
            return;
        }

        String title = mc.currentScreen.getTitle().getString();
        if (title.contains("\u041c\u0430\u0440\u043a\u0435\u0442") && title.contains("/")) return;

        state = State.CLICKING_BUY;
        delayCooldown = ACTION_DELAY_TICKS;
    }

    private static void tickClickingBuy(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof HandledScreen<?>)) {
            state = State.IDLE;
            delayCooldown = ACTION_DELAY_TICKS;
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        int buySlot = findBuyButton(handler);

        if (buySlot < 0) {
            LOG.warn("[AutoBuy] Buy button not found, closing");
            mc.player.closeHandledScreen();
            state = State.IDLE;
            delayCooldown = ACTION_DELAY_TICKS;
            return;
        }

        mc.interactionManager.clickSlot(
                handler.syncId, buySlot, 0, SlotActionType.PICKUP, mc.player);

        if (matchedRule != null) {
            matchedRule.addBought(1);
            BuyRuleStore.get().save();
            LOG.info("[AutoBuy] Bought item for rule: {} ({}/{})",
                    matchedRule.getDisplayName(), matchedRule.getBought(), matchedRule.getQuantity());
        }

        matchedSlot = -1;
        matchedRule = null;

        state = State.IDLE;
        delayCooldown = ACTION_DELAY_TICKS;
    }

    private static void tickCycleDone(MinecraftClient mc) {
        if (!isMarketScreen(mc)) {
            state = State.IDLE;
            delayCooldown = ACTION_DELAY_TICKS;
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        mc.interactionManager.clickSlot(
                handler.syncId, REFRESH_SLOT, 0, SlotActionType.PICKUP, mc.player);

        LOG.info("[AutoBuy] Refreshing market...");
        state = State.SCANNING;
        delayCooldown = ACTION_DELAY_TICKS;
    }

    private static boolean isMarketScreen(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof HandledScreen<?>)) return false;
        String title = mc.currentScreen.getTitle().getString();
        return title.contains("\u041c\u0430\u0440\u043a\u0435\u0442");
    }

    private static void updatePageInfo(MinecraftClient mc) {
        String title = mc.currentScreen.getTitle().getString();
        currentPage = MarketParser.parseCurrentPage(title);
        totalPages = MarketParser.parsePageInfo(title);
        if (currentPage < 1) currentPage = 1;
        if (totalPages < 1) totalPages = 1;
    }

    private static boolean matchesRule(MarketItem item, BuyRule rule) {
        String nameLC = item.getDisplayName().toLowerCase();
        String ruleNameLC = rule.getDisplayName().toLowerCase();

        if (!nameLC.contains(ruleNameLC) && !item.getItemId().contains(ruleNameLC)) {
            return false;
        }

        int pricePerUnit = item.getPricePerUnit();
        if (pricePerUnit <= 0) return false;

        return pricePerUnit <= rule.getMaxPricePerUnit();
    }

    private static int findBuyButton(ScreenHandler handler) {
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            NbtCompound nbt = stack.getNbt();
            if (nbt == null) continue;

            if (nbt.contains("display", 10)) {
                NbtCompound display = nbt.getCompound("display");
                if (display.contains("Name")) {
                    String name = display.getString("Name");
                    try {
                        Text parsed = Text.Serializer.fromJson(name);
                        if (parsed != null && parsed.getString().contains("\u041a\u0443\u043f\u0438\u0442\u044c")) {
                            return i;
                        }
                    } catch (Exception ignored) {}
                }
            }

            String displayName = stack.getName().getString();
            if (displayName.contains("\u041a\u0443\u043f\u0438\u0442\u044c")) {
                return i;
            }
        }
        return -1;
    }
}
