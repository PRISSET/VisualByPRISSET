package com.prisset.vtools.blueprint;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

/**
 * Finds the required block item in inventory and moves it to the hotbar.
 */
public final class InventoryHelper {

    private InventoryHelper() {}

    /**
     * Check if the player is currently holding the correct block in main hand.
     */
    public static boolean isHolding(ClientPlayerEntity player, String blockId) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return false;
        String heldId = Registries.BLOCK.getId(bi.getBlock()).toString();
        return heldId.equals(blockId);
    }

    /**
     * Find the block anywhere in inventory and swap to selected hotbar slot.
     * Returns true if block is now in hand (or was already).
     */
    public static boolean equipBlock(ClientPlayerEntity player, String blockId) {
        if (isHolding(player, blockId)) return true;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.interactionManager == null) return false;

        Block targetBlock = Registries.BLOCK.get(new Identifier(blockId));
        if (targetBlock == null) return false;

        int selectedSlot = player.getInventory().selectedSlot;

        // Check hotbar first (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (matchesBlock(stack, blockId)) {
                player.getInventory().selectedSlot = i;
                return true;
            }
        }

        // Check main inventory (slots 9-35) and swap with current hotbar slot
        int syncId = player.currentScreenHandler.syncId;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (matchesBlock(stack, blockId)) {
                // Click slot i to pick up, click hotbar slot to place
                mc.interactionManager.clickSlot(syncId, i, selectedSlot, SlotActionType.SWAP, player);
                return true;
            }
        }

        return false;
    }

    private static boolean matchesBlock(ItemStack stack, String blockId) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return false;
        return Registries.BLOCK.getId(bi.getBlock()).toString().equals(blockId);
    }
}
