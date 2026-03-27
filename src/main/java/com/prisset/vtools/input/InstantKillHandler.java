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
    private static final float MIN_DURATION_TICKS = 6f;
    private static final float TICKS_PER_DEGREE = 0.18f;
    private static final int HOLD_TICKS_MIN = 2;
    private static final int HOLD_TICKS_MAX = 4;

    private static int swapCooldown = 0;

    private static State state = State.IDLE;
    private static int targetId = -1;

    private static float originYaw, originPitch;
    private static float startYaw, startPitch;
    private static float goalYaw, goalPitch;
    private static int totalTicks;
    private static int elapsed;

    private enum State {
        IDLE,
        AIMING,
        HOLDING,
        RETURNING
    }

    private InstantKillHandler() {}

    public static void execute() {
        if (state != State.IDLE) return;

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
        originYaw = self.getYaw();
        originPitch = self.getPitch();
        startYaw = originYaw;
        startPitch = originPitch;

        float[] aim = calcAim(self, target);
        goalYaw = aim[0];
        goalPitch = aim[1];

        float angleDist = Math.abs(wrapDegrees(goalYaw - startYaw))
                        + Math.abs(goalPitch - startPitch);
        totalTicks = Math.max((int) MIN_DURATION_TICKS,
                              (int)(angleDist * TICKS_PER_DEGREE));
        totalTicks += ThreadLocalRandom.current().nextInt(0, 3);
        elapsed = 0;

        state = State.AIMING;
    }

    public static void tick() {
        if (swapCooldown > 0) swapCooldown--;
        if (state == State.IDLE) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            reset();
            return;
        }

        ClientPlayerEntity self = mc.player;

        switch (state) {
            case AIMING -> tickAiming(mc, self);
            case HOLDING -> tickHolding(mc, self);
            case RETURNING -> tickReturning(mc, self);
            default -> {}
        }
    }

    private static void tickAiming(MinecraftClient mc, ClientPlayerEntity self) {
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
        float eased = easeInOut(t);

        float yaw = lerpAngle(startYaw, goalYaw, eased);
        float pitch = MathHelper.lerp(eased, startPitch, goalPitch);

        self.setYaw(yaw);
        self.setPitch(MathHelper.clamp(pitch, -90f, 90f));

        if (t >= 1f) {
            performAttack(mc, self, target);

            startYaw = self.getYaw();
            startPitch = self.getPitch();
            goalYaw = startYaw;
            goalPitch = startPitch;
            totalTicks = ThreadLocalRandom.current().nextInt(HOLD_TICKS_MIN, HOLD_TICKS_MAX + 1);
            elapsed = 0;
            state = State.HOLDING;
        }
    }

    private static void tickHolding(MinecraftClient mc, ClientPlayerEntity self) {
        elapsed++;
        if (elapsed >= totalTicks) {
            startYaw = self.getYaw();
            startPitch = self.getPitch();
            goalYaw = originYaw;
            goalPitch = originPitch;

            float angleDist = Math.abs(wrapDegrees(goalYaw - startYaw))
                            + Math.abs(goalPitch - startPitch);
            totalTicks = Math.max((int) MIN_DURATION_TICKS,
                                  (int)(angleDist * TICKS_PER_DEGREE * 1.3f));
            totalTicks += ThreadLocalRandom.current().nextInt(0, 4);
            elapsed = 0;

            state = State.RETURNING;
        }
    }

    private static void tickReturning(MinecraftClient mc, ClientPlayerEntity self) {
        elapsed++;
        float t = MathHelper.clamp((float) elapsed / totalTicks, 0f, 1f);
        float eased = easeInOut(t);

        float yaw = lerpAngle(startYaw, goalYaw, eased);
        float pitch = MathHelper.lerp(eased, startPitch, goalPitch);

        self.setYaw(yaw);
        self.setPitch(MathHelper.clamp(pitch, -90f, 90f));

        if (t >= 1f) {
            self.setYaw(goalYaw);
            self.setPitch(goalPitch);
            reset();
        }
    }

    private static void performAttack(MinecraftClient mc, ClientPlayerEntity self,
                                       AbstractClientPlayerEntity target) {
        mc.getNetworkHandler().sendPacket(
            PlayerInteractEntityC2SPacket.attack(target, self.isSneaking())
        );
        mc.getNetworkHandler().sendPacket(
            new HandSwingC2SPacket(Hand.MAIN_HAND)
        );
        self.swingHand(Hand.MAIN_HAND);
        self.resetLastAttackedTicks();
    }

    private static float easeInOut(float t) {
        return t < 0.5f
            ? 4f * t * t * t
            : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    private static float lerpAngle(float from, float to, float t) {
        float diff = wrapDegrees(to - from);
        return from + diff * t;
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
        state = State.IDLE;
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
            int screenSlot = bestSlot;
            int syncId = player.currentScreenHandler.syncId;
            mc.interactionManager.clickSlot(syncId, screenSlot, inv.selectedSlot,
                                            SlotActionType.SWAP, player);
        }
        swapCooldown = 5;
    }
}
