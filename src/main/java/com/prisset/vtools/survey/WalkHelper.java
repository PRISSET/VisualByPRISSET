package com.prisset.vtools.survey;

import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Handles player movement via direct Input field overrides.
 * MovementInputMixin reads the request each tick AFTER KeyboardInput.tick().
 *
 * Provides floor safety checks and anti-stuck logic.
 */
public final class WalkHelper {

    // Movement request — consumed by MovementInputMixin
    public boolean wantForward;
    public boolean wantJump;

    private boolean walking;
    private BlockPos goal;
    private int ticks;
    private Vec3d lastPos;
    private int stuckTicks;

    public WalkHelper() {}

    public boolean isWalking() { return walking; }

    /**
     * Start walking 1 block in the given direction from current position.
     * Returns false if the path ahead is unsafe (3+ block drop or lava).
     */
    public boolean startWalk(ClientPlayerEntity player, ClientWorld world, Direction dir) {
        BlockPos feet = player.getBlockPos();
        BlockPos ahead = feet.offset(dir);

        // Floor safety: check what's under the destination
        if (!isFloorSafe(world, ahead)) return false;

        // Lava in the path
        if (hasLava(world, ahead) || hasLava(world, ahead.up())) return false;

        walking = true;
        goal = ahead;
        ticks = 0;
        stuckTicks = 0;
        lastPos = player.getPos();
        return true;
    }

    /**
     * Call every tick while walking. Updates wantForward/wantJump.
     * Returns true when destination reached or movement should stop.
     */
    public boolean tick(ClientPlayerEntity player) {
        if (!walking) {
            wantForward = false;
            wantJump = false;
            return true;
        }

        ticks++;

        // Check if arrived (horizontal distance to goal center)
        double dx = (goal.getX() + 0.5) - player.getPos().x;
        double dz = (goal.getZ() + 0.5) - player.getPos().z;
        double hDistSq = dx * dx + dz * dz;

        if (hDistSq < 0.1) {
            stopWalking();
            return true;
        }

        // Check if passed goal (dot product with direction)
        Vec3d toGoal = new Vec3d(dx, 0, dz).normalize();
        Vec3d currentDir = Vec3d.fromPolar(0, player.getYaw()).normalize();
        // If we overshot, just stop
        if (ticks > 3 && hDistSq < 0.5) {
            stopWalking();
            return true;
        }

        // Timeout
        if (ticks > 30) {
            stopWalking();
            return true;
        }

        // Anti-stuck: check actual movement every 5 ticks
        if (ticks % 5 == 0 && lastPos != null) {
            double moved = player.getPos().squaredDistanceTo(lastPos);
            if (moved < 0.01) {
                stuckTicks += 5;
                if (stuckTicks >= 15) {
                    // Truly stuck — give up
                    stopWalking();
                    return true;
                }
                // Try jumping
                wantJump = true;
            } else {
                stuckTicks = 0;
                wantJump = false;
            }
            lastPos = player.getPos();
        }

        wantForward = true;
        return false;
    }

    public void stopWalking() {
        walking = false;
        wantForward = false;
        wantJump = false;
        goal = null;
    }

    /**
     * Check if destination has safe floor (max 2 block drop acceptable).
     */
    public static boolean isFloorSafe(ClientWorld world, BlockPos feetPos) {
        BlockPos floor = feetPos.down();
        BlockState floorState = world.getBlockState(floor);

        // Solid floor — safe
        if (!floorState.isAir() && !isLavaBlock(world, floor)) return true;

        // 1 block drop — check next
        BlockPos floor2 = floor.down();
        BlockState floor2State = world.getBlockState(floor2);
        if (!floor2State.isAir() && !isLavaBlock(world, floor2)) return true;

        // 2+ block void — unsafe
        return false;
    }

    private static boolean hasLava(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getFluidState().getFluid() == Fluids.LAVA
                || state.getFluidState().getFluid() == Fluids.FLOWING_LAVA;
    }

    private static boolean isLavaBlock(ClientWorld world, BlockPos pos) {
        return hasLava(world, pos);
    }
}
