package com.prisset.vtools.input;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public final class AutoFarmHandler {

    private static final int OFFHAND_SCREEN_SLOT = 45;
    private static int swapCooldown = 0;
    private static boolean holdingUse = false;

    private AutoFarmHandler() {}

    public static void tick() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            releaseUse(mc);
            return;
        }
        if (mc.interactionManager == null) return;

        if (swapCooldown > 0) swapCooldown--;

        if (prefs.isAutoFarm()) tickAutoFarm(mc);

        if (prefs.isAutoEat()) {
            tickAutoEat(mc);
        } else {
            releaseUse(mc);
        }
    }

    private static void releaseUse(MinecraftClient mc) {
        if (holdingUse && mc != null && mc.options != null) {
            mc.options.useKey.setPressed(false);
            holdingUse = false;
        }
    }

    private static void tickAutoFarm(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        ClientPlayerInteractionManager im = mc.interactionManager;

        ensureSwordInHand(mc);

        float cooldown = player.getAttackCooldownProgress(0.5f);
        if (cooldown < 1.0f) return;

        HitResult target = mc.crosshairTarget;
        if (target == null || target.getType() != HitResult.Type.ENTITY) return;

        Entity entity = ((EntityHitResult) target).getEntity();
        if (!(entity instanceof MobEntity mob)) return;
        if (mob.isDead()) return;

        im.attackEntity(player, entity);
        player.swingHand(Hand.MAIN_HAND);
    }

    private static void ensureSwordInHand(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() instanceof SwordItem) return;
        if (swapCooldown > 0) return;

        PlayerInventory inv = player.getInventory();
        int bestInvSlot = -1;
        float bestDmg = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof SwordItem sword)) continue;
            float dmg = sword.getAttackDamage();
            if (dmg > bestDmg) {
                bestDmg = dmg;
                bestInvSlot = i;
            }
        }

        if (bestInvSlot < 0) return;

        if (bestInvSlot < 9) {
            inv.selectedSlot = bestInvSlot;
        } else {
            int screenSlot = invToScreen(bestInvSlot);
            int syncId = player.currentScreenHandler.syncId;
            im(mc).clickSlot(syncId, screenSlot, inv.selectedSlot, SlotActionType.SWAP, player);
        }
        swapCooldown = 5;
    }

    private static void tickAutoEat(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        ClientPlayerInteractionManager im = mc.interactionManager;

        ItemStack offhand = player.getOffHandStack();
        boolean hasFood = !offhand.isEmpty() && offhand.getItem().isFood();

        if (!hasFood) {
            if (swapCooldown > 0) return;
            int foodInvSlot = findBestFood(player);
            if (foodInvSlot < 0) return;

            int screenSlot = invToScreen(foodInvSlot);
            int syncId = player.currentScreenHandler.syncId;
            im.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, player);
            im.clickSlot(syncId, OFFHAND_SCREEN_SLOT, 0, SlotActionType.PICKUP, player);

            ItemStack cursor = player.currentScreenHandler.getCursorStack();
            if (cursor != null && !cursor.isEmpty()) {
                im.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, player);
            }

            swapCooldown = 5;
            return;
        }

        if (player.getHungerManager().getFoodLevel() >= 20) {
            releaseUse(mc);
            return;
        }

        mc.options.useKey.setPressed(true);
        holdingUse = true;
    }

    private static int findBestFood(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        int bestSlot = -1;
        int bestHunger = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (!item.isFood()) continue;
            FoodComponent food = item.getFoodComponent();
            if (food == null) continue;
            int hunger = food.getHunger();
            if (hunger > bestHunger) {
                bestHunger = hunger;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private static int invToScreen(int invSlot) {
        if (invSlot < 9) return invSlot + 36;
        return invSlot;
    }

    private static ClientPlayerInteractionManager im(MinecraftClient mc) {
        return mc.interactionManager;
    }
}
