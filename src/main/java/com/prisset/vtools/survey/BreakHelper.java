package com.prisset.vtools.survey;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Handles block breaking in a clean, Meteor-like pattern.
 * One call to tick() per game tick. Tracks its own state.
 *
 * Usage:
 *   breakHelper.setTarget(pos);
 *   // each tick:
 *   breakHelper.tick(player, world);
 *   if (breakHelper.isDone()) { ... next block ... }
 */
public final class BreakHelper {

    private BlockPos target;
    private boolean started;
    private boolean done;

    public BreakHelper() {}

    public void setTarget(BlockPos pos) {
        // If switching target, abort previous
        if (target != null && !target.equals(pos)) {
            abort();
        }
        target = pos;
        started = false;
        done = false;
    }

    public BlockPos getTarget() { return target; }
    public boolean isDone() { return done; }
    public boolean hasTarget() { return target != null && !done; }

    /**
     * Call every tick. Handles attackBlock -> updateBlockBreakingProgress -> detect broken.
     * Returns true if actively breaking (caller should not do other actions).
     */
    public boolean tick(ClientPlayerEntity player, ClientWorld world) {
        if (target == null || done) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerInteractionManager im = mc.interactionManager;
        if (im == null) return false;

        BlockState state = world.getBlockState(target);

        // Already broken
        if (state.isAir()) {
            done = true;
            target = null;
            return false;
        }

        // Unbreakable (bedrock etc)
        if (state.getHardness(world, target) < 0) {
            done = true;
            target = null;
            return false;
        }

        // Reach check
        double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(target));
        if (dist > 4.5) {
            done = true;
            target = null;
            return false;
        }

        // Ensure holding pickaxe
        if (!SlotHelper.isHoldingUsablePickaxe(player)) {
            if (!SlotHelper.selectBestPickaxe(player, Blocks.STONE.getDefaultState())) {
                SlotHelper.pullPickaxeFromInventory(player);
                return true; // wait a tick for swap to take effect
            }
        }

        Direction face = getDirection(player, target);

        if (!started) {
            im.attackBlock(target, face);
            started = true;
        } else {
            im.updateBlockBreakingProgress(target, face);
        }

        player.swingHand(Hand.MAIN_HAND);

        // Check if broken after progress update
        if (world.getBlockState(target).isAir()) {
            done = true;
            target = null;
        }

        return true;
    }

    public void abort() {
        if (started) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.interactionManager != null) {
                mc.interactionManager.cancelBlockBreaking();
            }
        }
        target = null;
        started = false;
        done = false;
    }

    public void reset() {
        abort();
    }

    /**
     * Pick best face to break from, same as Meteor's getDirection pattern.
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
