package com.prisset.vtools.survey;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Manages hotbar tool selection and inventory pickaxe swaps.
 * Neutral naming: "SlotHelper" reads as a generic inventory utility.
 */
public final class SlotHelper {

    private SlotHelper() {}

    /**
     * Switches to the best pickaxe in the hotbar for breaking the given block.
     * Returns true if a usable pickaxe was found and selected.
     */
    public static boolean selectBestPickaxe(ClientPlayerEntity player, BlockState target) {
        PlayerInventory inv = player.getInventory();
        int bestSlot = -1;
        float bestSpeed = -1f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.getItem() instanceof PickaxeItem) {
                if (stack.getDamage() >= stack.getMaxDamage() - 2) continue;
                float speed = stack.getMiningSpeedMultiplier(target);
                int eff = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, stack);
                speed += eff > 0 ? (eff * eff + 1) : 0;
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        if (bestSlot >= 0) {
            inv.selectedSlot = bestSlot;
            return true;
        }
        return false;
    }

    /**
     * Checks if the currently held item is a pickaxe with durability remaining.
     */
    public static boolean isHoldingUsablePickaxe(ClientPlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        if (!(held.getItem() instanceof PickaxeItem)) return false;
        return held.getDamage() < held.getMaxDamage() - 2;
    }

    /**
     * Searches the full inventory (slots 9-35) for a pickaxe and moves it to the hotbar.
     * Returns true if a swap was performed.
     */
    public static boolean pullPickaxeFromInventory(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.interactionManager == null) return false;

        PlayerInventory inv = player.getInventory();

        // Find empty hotbar slot or worst item slot
        int targetHotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isEmpty()) {
                targetHotbarSlot = i;
                break;
            }
        }
        if (targetHotbarSlot < 0) targetHotbarSlot = 8;

        // Search inventory (slots 9-35) for best pickaxe
        int bestInvSlot = -1;
        float bestSpeed = -1f;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.getItem() instanceof PickaxeItem) {
                if (stack.getDamage() >= stack.getMaxDamage() - 2) continue;
                float speed = stack.getMiningSpeedMultiplier(net.minecraft.block.Blocks.STONE.getDefaultState());
                int eff = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, stack);
                speed += eff > 0 ? (eff * eff + 1) : 0;
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestInvSlot = i;
                }
            }
        }

        if (bestInvSlot < 0) return false;

        // Swap: click inventory slot, then click hotbar slot
        int syncId = player.currentScreenHandler.syncId;
        client.interactionManager.clickSlot(syncId, bestInvSlot, targetHotbarSlot, SlotActionType.SWAP, player);
        inv.selectedSlot = targetHotbarSlot;
        return true;
    }

    /**
     * Finds cobblestone or other solid blocks in inventory for sealing lava.
     * Selects it in hotbar. Returns true if found.
     */
    public static boolean selectSealingBlock(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.getItem() == Items.COBBLESTONE
                    || stack.getItem() == Items.COBBLED_DEEPSLATE
                    || stack.getItem() == Items.STONE
                    || stack.getItem() == Items.DIRT
                    || stack.getItem() == Items.NETHERRACK) {
                inv.selectedSlot = i;
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnyPickaxe(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.getItem() instanceof PickaxeItem && stack.getDamage() < stack.getMaxDamage() - 2) {
                return true;
            }
        }
        return false;
    }
}
