package com.prisset.vtools.survey;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Human-like movement controller with acceleration curves.
 *
 * Anti-cheat principles:
 * - Float-based forward speed (not binary 0/1)
 * - Acceleration curve on start (ease-in)
 * - Deceleration curve on stop (ease-out)
 * - Sprint toggle with realistic timing
 * - Strafe drift (humans never walk perfectly straight)
 * - Multi-block walking (not stop-start every block)
 * - Speed micro-variation via Perlin noise
 */
public final class WalkHelper {

    public enum WalkPhase {
        STOPPED,
        ACCELERATING,
        CRUISING,
        DECELERATING
    }

    // Movement output -- consumed by MovementInputMixin
    public float wantForward;   // 0.0 - 1.0
    public float wantStrafe;    // -1.0 to 1.0
    public boolean wantJump;
    public boolean wantSprint;
    public boolean wantSneak;

    private WalkPhase phase = WalkPhase.STOPPED;
    private Direction walkDir;
    private int targetBlocks;
    private Vec3d startPos;

    // Acceleration
    private int accelTick;
    private int accelTicks;
    private int sprintDelay;

    // Deceleration
    private int decelTick;
    private int decelTicks;

    // Tracking
    private int totalTicks;
    private Vec3d lastPos;
    private int stuckTicks;
    private int jumpCooldown;

    public WalkHelper() {}

    public WalkPhase getPhase() { return phase; }
    public boolean isWalking() { return phase != WalkPhase.STOPPED; }

    /**
     * Start walking N blocks in the given direction.
     * Returns false if the path is unsafe.
     */
    public boolean startWalk(ClientPlayerEntity player, ClientWorld world,
                              Direction dir, int blocks) {
        BlockPos feet = player.getBlockPos();

        // Safety check for entire path
        for (int i = 1; i <= blocks; i++) {
            BlockPos ahead = feet.offset(dir, i);
            if (!isFloorSafe(world, ahead)) {
                blocks = i - 1;
                break;
            }
            if (hasDangerousFluid(world, ahead) || hasDangerousFluid(world, ahead.up())) {
                blocks = i - 1;
                break;
            }
        }

        if (blocks <= 0) return false;

        walkDir = dir;
        targetBlocks = blocks;
        startPos = player.getPos();
        lastPos = player.getPos();
        totalTicks = 0;
        stuckTicks = 0;
        jumpCooldown = 0;

        // Acceleration parameters
        accelTick = 0;
        accelTicks = HumanTiming.gaussianDelay(4, 1.0f);
        accelTicks = Math.max(2, Math.min(6, accelTicks));

        // Sprint: enable after halfway through acceleration
        sprintDelay = accelTicks / 2 + 1;

        // Deceleration (pre-calculated)
        decelTick = 0;
        decelTicks = HumanTiming.gaussianDelay(3, 0.8f);
        decelTicks = Math.max(2, Math.min(5, decelTicks));

        phase = WalkPhase.ACCELERATING;
        wantJump = false;
        wantSneak = false;

        return true;
    }

    /**
     * Start a single-block walk (convenience).
     */
    public boolean startWalk(ClientPlayerEntity player, ClientWorld world, Direction dir) {
        return startWalk(player, world, dir, 1);
    }

    /**
     * Call every tick while walking.
     * Returns true when destination reached or movement should stop.
     */
    public boolean tick(ClientPlayerEntity player, long worldTick) {
        if (phase == WalkPhase.STOPPED) {
            clearOutputs();
            return true;
        }

        totalTicks++;

        double traveled = horizontalDistance(startPos, player.getPos());
        double remaining = targetBlocks - traveled;

        switch (phase) {
            case ACCELERATING -> {
                accelTick++;
                float t = Math.min(1.0f, (float) accelTick / accelTicks);
                wantForward = MotionProfile.easeInOut(t);

                // Sprint after delay (only for multi-block walks)
                if (accelTick >= sprintDelay && targetBlocks > 1) {
                    wantSprint = true;
                    player.setSprinting(true);
                }

                if (t >= 1.0f) {
                    phase = WalkPhase.CRUISING;
                }
            }

            case CRUISING -> {
                // Not perfect 1.0 -- micro variation
                float variation = NoiseGenerator.speedVariation(worldTick);
                wantForward = Math.max(0.88f, Math.min(1.0f, 1.0f + variation));

                // Start decelerating when close
                float decelDist = wantSprint ? 1.4f : 0.7f;
                if (remaining <= decelDist) {
                    phase = WalkPhase.DECELERATING;
                    decelTick = 0;
                    wantSprint = false;
                    player.setSprinting(false);
                }
            }

            case DECELERATING -> {
                decelTick++;
                float t = Math.min(1.0f, (float) decelTick / decelTicks);
                wantForward = 1.0f - MotionProfile.easeInOut(t);

                if (t >= 1.0f || remaining <= 0.08) {
                    stopWalking();
                    return true;
                }
            }
        }

        // Strafe drift (Perlin-based, very subtle)
        wantStrafe = NoiseGenerator.pathDrift(worldTick) * 0.12f;

        // Jump cooldown (prevent infinite jumping)
        if (jumpCooldown > 0) jumpCooldown--;

        // Anti-stuck: only jump after genuinely stuck, single-tick pulse
        if (totalTicks % 8 == 0 && lastPos != null) {
            double moved = player.getPos().squaredDistanceTo(lastPos);
            if (moved < 0.01) {
                stuckTicks += 8;
                if (stuckTicks >= 32 && jumpCooldown <= 0 && player.isOnGround()) {
                    wantJump = true;
                    jumpCooldown = 20; // no more jumps for 1 second
                }
                if (stuckTicks >= 60) {
                    stopWalking();
                    return true;
                }
            } else {
                stuckTicks = 0;
                wantJump = false;
            }
            lastPos = player.getPos();
        }

        // Single-tick jump pulse: reset after one tick of jumping
        if (wantJump && !player.isOnGround()) {
            wantJump = false;
        }

        // Timeout
        if (totalTicks > 120) {
            stopWalking();
            return true;
        }

        return false;
    }

    public void stopWalking() {
        phase = WalkPhase.STOPPED;
        clearOutputs();
    }

    private void clearOutputs() {
        wantForward = 0;
        wantStrafe = 0;
        wantJump = false;
        wantSprint = false;
        wantSneak = false;
    }

    // --- Floor & Fluid Safety ---

    /**
     * Floor is safe if there's solid ground within 2 blocks below feet,
     * and no dangerous fluids (lava or water) at that position.
     */
    public static boolean isFloorSafe(ClientWorld world, BlockPos feetPos) {
        // Dangerous fluid at feet level = not safe
        if (hasDangerousFluid(world, feetPos)) return false;

        BlockPos floor = feetPos.down();
        BlockState floorState = world.getBlockState(floor);

        if (isDangerousFluid(floorState)) return false;
        if (!floorState.isAir()) return true;

        // 1 block gap: check 2 blocks down
        BlockPos floor2 = floor.down();
        BlockState floor2State = world.getBlockState(floor2);
        if (isDangerousFluid(floor2State)) return false;
        if (!floor2State.isAir()) return true;

        // 2+ block drop with nothing solid = not safe
        return false;
    }

    /**
     * Any dangerous fluid: lava (burns) or water (drowns, pushes).
     */
    public static boolean hasDangerousFluid(ClientWorld world, BlockPos pos) {
        return isDangerousFluid(world.getBlockState(pos));
    }

    private static boolean isDangerousFluid(BlockState state) {
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) return false;
        return fluid.getFluid() == Fluids.LAVA
                || fluid.getFluid() == Fluids.FLOWING_LAVA
                || fluid.getFluid() == Fluids.WATER
                || fluid.getFluid() == Fluids.FLOWING_WATER;
    }

    /**
     * Lava specifically (for situations where water is ok but lava isn't).
     */
    public static boolean hasLava(ClientWorld world, BlockPos pos) {
        FluidState fluid = world.getBlockState(pos).getFluidState();
        return fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA;
    }

    private static double horizontalDistance(Vec3d a, Vec3d b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
