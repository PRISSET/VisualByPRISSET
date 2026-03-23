package com.prisset.vtools.survey;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Imperfect path decision maker.
 * Real players don't mine in perfectly straight tunnels.
 * Adds randomized mining order, walk distances, Y-level drift.
 */
public final class PathPlanner {

    private float yawHint;
    private int blocksSinceLastBehindCheck;
    private final ThreadLocalRandom rng = ThreadLocalRandom.current();

    public PathPlanner() {}

    public float getYawHint() { return yawHint; }

    /**
     * Get walking yaw with slight drift from perfect direction.
     */
    public float adjustedYaw(Direction base, long worldTick) {
        float baseYaw = dirYaw(base);
        float drift = NoiseGenerator.pathDrift(worldTick) * 1.5f;
        yawHint = baseYaw + drift;
        return yawHint;
    }

    /**
     * Get adjusted Y-level (not exactly the optimal Y).
     * Drifts +-2 blocks over time.
     */
    public int adjustedY(int targetY, long worldTick) {
        float drift = NoiseGenerator.noise1D(worldTick * 0.002f) * 2.0f;
        return targetY + Math.round(drift);
    }

    /**
     * Shuffle mining order for tunnel blocks.
     * Real players don't always mine head-first.
     */
    public List<BlockPos> shuffleMineOrder(List<BlockPos> blocks) {
        if (blocks.size() <= 1) return blocks;

        List<BlockPos> result = new ArrayList<>(blocks);
        float roll = rng.nextFloat();

        if (roll < 0.30f) {
            // 30%: reverse order (feet first)
            Collections.reverse(result);
        } else if (roll < 0.40f) {
            // 10%: random order
            Collections.shuffle(result, rng);
        }
        // 60%: original order (head first)

        return result;
    }

    /**
     * How many blocks to walk before stopping.
     * Humans don't walk exactly 1 block at a time.
     */
    public int walkBlocks() {
        float roll = rng.nextFloat();
        if (roll < 0.55f) return 1;
        if (roll < 0.80f) return 2;
        if (roll < 0.93f) return 3;
        return 4;
    }

    /**
     * Should the player look behind? (paranoia check)
     * Triggers every 20-40 blocks.
     */
    public boolean shouldLookBehind() {
        blocksSinceLastBehindCheck++;
        int threshold = 20 + rng.nextInt(21); // 20-40
        if (blocksSinceLastBehindCheck >= threshold) {
            blocksSinceLastBehindCheck = 0;
            return true;
        }
        return false;
    }

    /**
     * Should we mine an extra block (floor/ceiling)?
     * Makes tunnel slightly irregular.
     */
    public boolean shouldMineExtraBlock() {
        return rng.nextFloat() < 0.12f;
    }

    /**
     * Should we mine the floor block (clearing uneven ground)?
     */
    public boolean shouldClearFloor() {
        return rng.nextFloat() < 0.08f;
    }

    /**
     * Reset counters for a new session.
     */
    public void reset() {
        blocksSinceLastBehindCheck = 0;
    }

    private static float dirYaw(Direction dir) {
        return switch (dir) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }
}
