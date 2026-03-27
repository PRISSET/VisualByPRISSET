package com.prisset.vtools.input;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.config.ProfileIndex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class InstantKillHandler {

    private static final double MAX_RANGE = 6.0;
    private static int swapCooldown = 0;

    private InstantKillHandler() {}

    public static void tick() {
        if (swapCooldown > 0) swapCooldown--;
    }

    public static void execute() {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isInstantKill()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (mc.interactionManager == null) return;

        ClientPlayerEntity self = mc.player;

        AbstractClientPlayerEntity target = findTarget(mc, self);
        if (target == null) return;

        float savedYaw = self.getYaw();
        float savedPitch = self.getPitch();

        equipBestSword(mc);

        Vec3d eyePos = target.getEyePos();
        Vec3d selfEye = self.getEyePos();
        Vec3d diff = eyePos.subtract(selfEye);
        double dist = diff.length();
        double dx = diff.x;
        double dz = diff.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(diff.y, Math.sqrt(dx * dx + dz * dz))));

        self.setYaw(yaw);
        self.setPitch(pitch);
        self.prevYaw = yaw;
        self.prevPitch = pitch;

        mc.interactionManager.attackEntity(self, target);
        self.swingHand(Hand.MAIN_HAND);

        self.setYaw(savedYaw);
        self.setPitch(savedPitch);
        self.prevYaw = savedYaw;
        self.prevPitch = savedPitch;
    }

    private static AbstractClientPlayerEntity findTarget(MinecraftClient mc, ClientPlayerEntity self) {
        List<AbstractClientPlayerEntity> players = mc.world.getPlayers();
        AbstractClientPlayerEntity best = null;
        double bestDist = MAX_RANGE;

        for (AbstractClientPlayerEntity player : players) {
            if (player == self) continue;
            if (player.isDead()) continue;

            String name = player.getGameProfile().getName();
            if (ProfileIndex.get().isTeammate(name)) continue;

            double dist = self.distanceTo(player);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }

        return best;
    }

    private static void equipBestSword(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() instanceof SwordItem) return;
        if (swapCooldown > 0) return;

        PlayerInventory inv = player.getInventory();
        int bestSlot = -1;
        float bestDmg = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof SwordItem sword)) continue;
            float dmg = sword.getAttackDamage();
            if (dmg > bestDmg) {
                bestDmg = dmg;
                bestSlot = i;
            }
        }

        if (bestSlot < 0) return;

        if (bestSlot < 9) {
            inv.selectedSlot = bestSlot;
        } else {
            int screenSlot = bestSlot < 9 ? bestSlot + 36 : bestSlot;
            int syncId = player.currentScreenHandler.syncId;
            mc.interactionManager.clickSlot(syncId, screenSlot, inv.selectedSlot, SlotActionType.SWAP, player);
        }
        swapCooldown = 5;
    }
}
