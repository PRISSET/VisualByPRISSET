package com.prisset.vtools.input;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public final class AutoFarmHandler {

    private AutoFarmHandler() {}

    public static void tick() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        if (prefs.isAutoFarm()) tickAutoFarm(mc);
        if (prefs.isAutoEat()) tickAutoEat(mc);
    }

    private static void tickAutoFarm(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        GameOptions opt = mc.options;

        if (!opt.attackKey.isPressed()) return;

        float cooldown = player.getAttackCooldownProgress(0.5f);
        if (cooldown < 1.0f) return;

        HitResult target = mc.crosshairTarget;
        if (target == null || target.getType() != HitResult.Type.ENTITY) return;

        Entity entity = ((EntityHitResult) target).getEntity();
        if (!(entity instanceof LivingEntity)) return;
        if (entity == player) return;

        mc.interactionManager.attackEntity(player, entity);
        player.swingHand(Hand.MAIN_HAND);
    }

    private static void tickAutoEat(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;

        ItemStack offhand = player.getOffHandStack();
        if (offhand.isEmpty() || !offhand.getItem().isFood()) return;

        if (player.getHungerManager().getFoodLevel() >= 20) return;

        if (!player.isUsingItem()) {
            mc.interactionManager.interactItem(player, Hand.OFF_HAND);
        }
    }
}
