package com.prisset.vtools.input;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.config.ProfileIndex;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class InstantKillHandler {

    private static final double MAX_RANGE = 3.5;
    private static final int MIN_TICKS = 3;
    private static final float TICKS_PER_DEGREE = 0.08f;

    private static int swapCooldown = 0;
    private static boolean active = false;
    private static int targetId = -1;
    private static float startYaw, startPitch;
    private static float goalYaw, goalPitch;
    private static int totalTicks;
    private static int elapsed;

    private InstantKillHandler() {}

    public static void execute() {
        if (active) return;

        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isInstantKill()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        ClientPlayerEntity self = mc.player;
        AbstractClientPlayerEntity target = findTarget(mc, self);
        if (target == null) return;

        equipBestSword(mc);

        targetId = target.getId();
        startYaw = self.getYaw();
        startPitch = self.getPitch();

        float[] aim = calcAim(self, target);
        goalYaw = aim[0];
        goalPitch = aim[1];

        float angleDist = Math.abs(wrapDegrees(goalYaw - startYaw))
                        + Math.abs(goalPitch - startPitch) * 0.5f;
        totalTicks = Math.max(MIN_TICKS, (int)(angleDist * TICKS_PER_DEGREE)
                     + ThreadLocalRandom.current().nextInt(0, 2));
        elapsed = 0;
        active = true;
    }

    public static void tick() {
        if (swapCooldown > 0) swapCooldown--;
        if (!active) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            reset();
            return;
        }

        ClientPlayerEntity self = mc.player;
        AbstractClientPlayerEntity target = resolveTarget(mc);
        if (target == null || target.isDead()) {
            reset();
            return;
        }

        float[] aim = calcAim(self, target);
        goalYaw = aim[0];
        goalPitch = aim[1];

        elapsed++;
        float t = MathHelper.clamp((float) elapsed / totalTicks, 0f, 1f);
        float eased = easeOut(t);

        self.setYaw(lerpAngle(startYaw, goalYaw, eased));
        self.setPitch(MathHelper.clamp(
            MathHelper.lerp(eased, startPitch, goalPitch), -90f, 90f));

        if (t >= 1f) {
            mc.getNetworkHandler().sendPacket(
                PlayerInteractEntityC2SPacket.attack(target, self.isSneaking()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            self.swingHand(Hand.MAIN_HAND);
            self.resetLastAttackedTicks();
            reset();
        }
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    private static float lerpAngle(float from, float to, float t) {
        return from + wrapDegrees(to - from) * t;
    }

    private static float[] calcAim(ClientPlayerEntity self, AbstractClientPlayerEntity target) {
        Vec3d selfEye = self.getEyePos();
        Vec3d targetBody = target.getPos().add(0, target.getHeight() * 0.65, 0);
        Vec3d diff = targetBody.subtract(selfEye);
        double dx = diff.x;
        double dz = diff.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(dx * dx + dz * dz)));
        return new float[]{yaw, pitch};
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    private static AbstractClientPlayerEntity resolveTarget(MinecraftClient mc) {
        if (targetId < 0) return null;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p.getId() == targetId) return p;
        }
        return null;
    }

    private static void reset() {
        active = false;
        targetId = -1;
        elapsed = 0;
    }

    private static AbstractClientPlayerEntity findTarget(MinecraftClient mc,
                                                          ClientPlayerEntity self) {
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
            int syncId = player.currentScreenHandler.syncId;
            mc.interactionManager.clickSlot(syncId, bestSlot, inv.selectedSlot,
                                            SlotActionType.SWAP, player);
        }
        swapCooldown = 5;
    }
}
