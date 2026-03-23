package com.prisset.vtools.survey;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Human-like block breaking controller.
 *
 * Anti-cheat principles:
 * - Pre-break delay (human reaction time before starting)
 * - Swing skip every ~10 ticks (hand tremor simulation)
 * - Swing timing variation (not every tick)
 * - Post-break state for caller to add delays
 * - Tool selection goes through ToolManager with delays
 */
public final class BreakHelper {

    public enum BreakPhase {
        NO_TARGET,
        PRE_BREAK,    // waiting before starting attackBlock
        BREAKING,     // actively breaking (updateBlockBreakingProgress)
        DONE          // block broken, waiting for caller to acknowledge
    }

    private BlockPos target;
    private BreakPhase phase = BreakPhase.NO_TARGET;

    // Pre-break
    private int preBreakDelay;
    private int preBreakTick;

    // Breaking rhythm
    private int breakTicks;
    private int nextSkipTick;   // tick to skip swing (tremor)
    private boolean attackSent; // first attackBlock sent

    public BreakHelper() {}

    public BlockPos getTarget() { return target; }
    public BreakPhase getPhase() { return phase; }
    public boolean isDone() { return phase == BreakPhase.DONE; }
    public boolean hasTarget() { return target != null && phase != BreakPhase.DONE && phase != BreakPhase.NO_TARGET; }
    public boolean isPreBreak() { return phase == BreakPhase.PRE_BREAK; }

    /**
     * Set a new target block with human reaction delay.
     */
    public void setTarget(BlockPos pos) {
        if (target != null && !target.equals(pos)) {
            abort();
        }
        target = pos;
        phase = BreakPhase.PRE_BREAK;
        preBreakDelay = HumanTiming.preBreak();
        preBreakTick = 0;
        breakTicks = 0;
        attackSent = false;
        nextSkipTick = 8 + ThreadLocalRandom.current().nextInt(5);
    }

    /**
     * Set target with extra delay (e.g. after tool switch).
     */
    public void setTargetWithExtraDelay(BlockPos pos, int extraTicks) {
        setTarget(pos);
        preBreakDelay += extraTicks;
    }

    /**
     * Call every tick. Handles pre-break delay, breaking, and swing variation.
     * Returns true if actively working (caller should not do other actions).
     */
    public boolean tick(ClientPlayerEntity player, ClientWorld world) {
        if (target == null || phase == BreakPhase.NO_TARGET || phase == BreakPhase.DONE) {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerInteractionManager im = mc.interactionManager;
        if (im == null) return false;

        BlockState state = world.getBlockState(target);

        // Already broken (by someone else or server)
        if (state.isAir()) {
            phase = BreakPhase.DONE;
            return false;
        }

        // Unbreakable
        if (state.getHardness(world, target) < 0) {
            phase = BreakPhase.DONE;
            target = null;
            return false;
        }

        // Reach check
        double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(target));
        if (dist > 4.5) {
            phase = BreakPhase.DONE;
            target = null;
            return false;
        }

        Direction face = getDirection(player, target);

        switch (phase) {
            case PRE_BREAK -> {
                preBreakTick++;
                if (preBreakTick >= preBreakDelay) {
                    // Ensure holding pickaxe before starting
                    if (!SlotHelper.isHoldingUsablePickaxe(player)) {
                        if (!SlotHelper.selectBestPickaxe(player, state)) {
                            SlotHelper.pullPickaxeFromInventory(player);
                            // Extra delay after tool switch
                            preBreakDelay = preBreakTick + HumanTiming.gaussianDelay(2, 0.5f);
                            return true;
                        }
                        // Just switched tool -- add small delay
                        preBreakDelay = preBreakTick + HumanTiming.gaussianDelay(1, 0.3f);
                        return true;
                    }

                    phase = BreakPhase.BREAKING;
                    im.attackBlock(target, face);
                    attackSent = true;
                    breakTicks = 1;
                    player.swingHand(Hand.MAIN_HAND);
                }
                return true;
            }

            case BREAKING -> {
                breakTicks++;

                // Swing skip: simulate hand tremor every ~10 ticks
                if (breakTicks >= nextSkipTick) {
                    nextSkipTick = breakTicks + 8 + ThreadLocalRandom.current().nextInt(5);
                    // Skip this tick's progress update
                    checkBroken(world);
                    return true;
                }

                // Tool check mid-break
                if (!SlotHelper.isHoldingUsablePickaxe(player)) {
                    if (!SlotHelper.selectBestPickaxe(player, state)) {
                        SlotHelper.pullPickaxeFromInventory(player);
                    }
                    return true;
                }

                im.updateBlockBreakingProgress(target, face);

                // Swing hand with slight variation (92% chance per tick)
                if (HumanTiming.chance(0.92f)) {
                    player.swingHand(Hand.MAIN_HAND);
                }

                checkBroken(world);
                return true;
            }
        }

        return false;
    }

    private void checkBroken(ClientWorld world) {
        if (target != null && world.getBlockState(target).isAir()) {
            phase = BreakPhase.DONE;
        }
    }

    public void abort() {
        if (attackSent) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.interactionManager != null) {
                mc.interactionManager.cancelBlockBreaking();
            }
        }
        target = null;
        phase = BreakPhase.NO_TARGET;
        attackSent = false;
    }

    public void reset() {
        abort();
    }

    /**
     * Acknowledge done state and clear. Call after post-mine delay.
     */
    public void acknowledge() {
        target = null;
        phase = BreakPhase.NO_TARGET;
        attackSent = false;
    }

    /**
     * Pick best face to break from (face closest to player).
     */
    private static Direction getDirection(ClientPlayerEntity player, BlockPos pos) {
        Vec3d diff = player.getEyePos().subtract(Vec3d.ofCenter(pos));
        Direction best = Direction.UP;
        double maxDot = Double.NEGATIVE_INFINITY;
        for (Direction d : Direction.values()) {
            double dot = diff.dotProduct(Vec3d.of(d.getVector()));
            if (dot > maxDot) {
                maxDot = dot;
                best = d;
            }
        }
        return best;
    }
}
