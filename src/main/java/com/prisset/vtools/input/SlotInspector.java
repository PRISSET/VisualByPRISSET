package com.prisset.vtools.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class SlotInspector {

    private static final Logger LOG = LoggerFactory.getLogger("vtools-inspector");

    private SlotInspector() {}

    public static void dump() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            LOG.info("[INSPECTOR] No GUI open");
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        String title = mc.currentScreen.getTitle().getString();

        LOG.info("========== SLOT DUMP ==========");
        LOG.info("Title: {}", title);
        LOG.info("Title raw: {}", Text.Serializer.toJson(mc.currentScreen.getTitle()));
        LOG.info("SyncId: {}, Slots: {}", handler.syncId, handler.slots.size());
        LOG.info("-------------------------------");

        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            String displayName = stack.getName().getString();
            String displayNameRaw = Text.Serializer.toJson(stack.getName());
            int count = stack.getCount();
            int damage = stack.getDamage();
            int maxDamage = stack.getMaxDamage();

            LOG.info("--- Slot {} ---", i);
            LOG.info("  ID: {}", itemId);
            LOG.info("  Name: {}", displayName);
            LOG.info("  NameRaw: {}", displayNameRaw);
            LOG.info("  Count: {}, Dmg: {}/{}", count, damage, maxDamage);

            NbtCompound nbt = stack.getNbt();
            if (nbt != null) {
                if (nbt.contains("display", 10)) {
                    NbtCompound display = nbt.getCompound("display");
                    if (display.contains("Name")) {
                        LOG.info("  CustomName: {}", display.getString("Name"));
                    }
                    if (display.contains("Lore", 9)) {
                        NbtList lore = display.getList("Lore", 8);
                        for (int j = 0; j < lore.size(); j++) {
                            String loreLine = lore.getString(j);
                            LOG.info("  Lore[{}]: {}", j, loreLine);
                            try {
                                Text parsed = Text.Serializer.fromJson(loreLine);
                                if (parsed != null) {
                                    LOG.info("  Lore[{}] text: {}", j, parsed.getString());
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                if (nbt.contains("Enchantments", 9)) {
                    NbtList enchants = nbt.getList("Enchantments", 10);
                    for (int j = 0; j < enchants.size(); j++) {
                        NbtCompound ench = enchants.getCompound(j);
                        LOG.info("  Enchant: {} lvl {}", ench.getString("id"), ench.getShort("lvl"));
                    }
                }

                LOG.info("  NBT: {}", nbt);
            }

            List<Text> tooltip = stack.getTooltip(mc.player, net.minecraft.client.item.TooltipContext.BASIC);
            if (tooltip != null) {
                for (int j = 0; j < tooltip.size(); j++) {
                    LOG.info("  Tooltip[{}]: {}", j, tooltip.get(j).getString());
                }
            }
        }

        LOG.info("========== END DUMP ==========");
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("\u00a7a[VTools] \u0414\u0430\u043c\u043f \u0441\u043b\u043e\u0442\u043e\u0432 \u0437\u0430\u043f\u0438\u0441\u0430\u043d \u0432 \u043b\u043e\u0433!"), false);
        }
    }
}
