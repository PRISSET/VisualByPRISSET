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
    private static final float TURN_SPEED_MIN = 8.0f;
    private static final float TURN_SPEED_MAX = 16.0f;
    private static final float AIM_THRESHOLD = 3.5f;
    private static final int RETURN_DELAY_MIN = 2;
    private static final int RETURN_DELAY_MAX = 5;
    private static final float RETURN_SPEED_MULT = 0.7f;

    private static int swapCooldown = 0;

    private static State state = State.IDLE;
    private static int targetId = -1;
    private static float returnYaw;
    private static float returnPitch;
    private static int delayTicks;
    private static boolean attacked;

    private enum State {
        IDLE,
        TURNING_TO,
        HIT_DELAY,
        TURNING_BACK
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
        returnYaw = self.getYaw();
        returnPitch = self.getPitch();
        attacked = false;
        state = State.TURNING_TO;
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
            case TURNING_TO -> tickTurnTo(mc, self);
            case HIT_DELAY -> tickHitDelay(mc, self);
            case TURNING_BACK -> tickTurnBack(mc, self);
            default -> {}
        }
    }

    private static void tickTurnTo(MinecraftClient mc, ClientPlayerEntity self) {
        AbstractClientPlayerEntity target = resolveTarget(mc);
        if (target == null || target.isDead()) {
            reset();
            return;
        }

        float[] aim = calcAim(self, target);
        float targetYaw = aim[0];
        float targetPitch = aim[1];

        float remaining = Math.abs(wrapDegrees(targetYaw - self.getYaw()));
        float speed = adaptiveSpeed(remaining);
        float newYaw = smoothRotate(self.getYaw(), targetYaw, speed);
        float newPitch = smoothRotate(self.getPitch(), targetPitch, speed * 0.6f);

        newYaw += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 1.5f;
        newPitch += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.8f;

        self.setYaw(newYaw);
        self.setPitch(MathHelper.clamp(newPitch, -90f, 90f));

        float yawDiff = Math.abs(wrapDegrees(newYaw - targetYaw));
        float pitchDiff = Math.abs(newPitch - targetPitch);

        if (yawDiff < AIM_THRESHOLD && pitchDiff < AIM_THRESHOLD) {
            performAttack(mc, self, target);
            attacked = true;
            delayTicks = ThreadLocalRandom.current().nextInt(RETURN_DELAY_MIN, RETURN_DELAY_MAX + 1);
            state = State.HIT_DELAY;
        }
    }

    private static void tickHitDelay(MinecraftClient mc, ClientPlayerEntity self) {
        delayTicks--;
        if (delayTicks <= 0) {
            state = State.TURNING_BACK;
        }
    }

    private static void tickTurnBack(MinecraftClient mc, ClientPlayerEntity self) {
        float remaining = Math.abs(wrapDegrees(returnYaw - self.getYaw()));
        float speed = adaptiveSpeed(remaining) * RETURN_SPEED_MULT;
        float newYaw = smoothRotate(self.getYaw(), returnYaw, speed);
        float newPitch = smoothRotate(self.getPitch(), returnPitch, speed * 0.6f);

        newYaw += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 1.2f;
        newPitch += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.6f;

        self.setYaw(newYaw);
        self.setPitch(MathHelper.clamp(newPitch, -90f, 90f));

        float yawDiff = Math.abs(wrapDegrees(newYaw - returnYaw));
        float pitchDiff = Math.abs(newPitch - returnPitch);

        if (yawDiff < 2.0f && pitchDiff < 2.0f) {
            self.setYaw(returnYaw);
            self.setPitch(returnPitch);
            reset();
        }
    }

    private static void performAttack(MinecraftClient mc, ClientPlayerEntity self, AbstractClientPlayerEntity target) {
        mc.getNetworkHandler().sendPacket(
            PlayerInteractEntityC2SPacket.attack(target, self.isSneaking())
        );
        mc.getNetworkHandler().sendPacket(
            new HandSwingC2SPacket(Hand.MAIN_HAND)
        );
        self.swingHand(Hand.MAIN_HAND);
        self.resetLastAttackedTicks();
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

    private static float smoothRotate(float current, float target, float maxStep) {
        float diff = wrapDegrees(target - current);
        if (Math.abs(diff) <= maxStep) return target;
        return current + Math.signum(diff) * maxStep;
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    private static float adaptiveSpeed(float remainingDeg) {
        float t = MathHelper.clamp(remainingDeg / 90f, 0f, 1f);
        float base = MathHelper.lerp(t, TURN_SPEED_MIN * 0.5f, TURN_SPEED_MAX);
        float jitter = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 4f;
        return Math.max(2f, base + jitter);
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
        attacked = false;
        delayTicks = 0;
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
            int screenSlot = bestSlot;
            int syncId = player.currentScreenHandler.syncId;
            mc.interactionManager.clickSlot(syncId, screenSlot, inv.selectedSlot, SlotActionType.SWAP, player);
        }
        swapCooldown = 5;
    }
}
