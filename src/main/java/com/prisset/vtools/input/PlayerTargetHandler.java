package com.prisset.vtools.input;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.config.ProfileIndex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public final class PlayerTargetHandler {

    private static String targetName = null;

    private PlayerTargetHandler() {}

    public static void onMiddleClick() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isTargetEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) {
            targetName = null;
            return;
        }

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof PlayerEntity player)) {
            targetName = null;
            return;
        }

        String name = player.getGameProfile().getName();

        if (ProfileIndex.get().isTeammate(name)) return;

        if (name.equals(targetName)) {
            targetName = null;
        } else {
            targetName = name;
        }
    }

    public static String getTargetName() {
        return targetName;
    }

    public static PlayerEntity findTargetEntity() {
        if (targetName == null) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player
                    && player.getGameProfile().getName().equals(targetName)
                    && player.isAlive()) {
                return player;
            }
        }
        return null;
    }

    public static void clearTarget() {
        targetName = null;
    }

    public static boolean hasTarget() {
        return targetName != null;
    }
}
