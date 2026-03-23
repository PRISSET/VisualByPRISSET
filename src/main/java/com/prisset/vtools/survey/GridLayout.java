package com.prisset.vtools.survey;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plans a branch-mine grid pattern with human imperfection.
 * Tunnels are slightly irregular -- sometimes 3 blocks high,
 * sometimes floor is cleared, mining order varies.
 */
public final class GridLayout {

    private static final int BRANCH_INTERVAL = 3;
    private static final int BRANCH_LENGTH = 10;

    private GridLayout() {}

    /**
     * Generate corridor blocks with human imperfection.
     * Base: 1 wide, 2 tall. Sometimes mines extra blocks.
     */
    public static List<BlockPos> corridorSegment(BlockPos feetPos, Direction facing) {
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos ahead = feetPos.offset(facing);
        blocks.add(ahead);           // feet level
        blocks.add(ahead.up());      // head level

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 12% chance: mine ceiling (3 blocks high)
        if (rng.nextFloat() < 0.12f) {
            blocks.add(ahead.up(2));
        }

        // 7% chance: clear floor
        if (rng.nextFloat() < 0.07f) {
            blocks.add(ahead.down());
        }

        return blocks;
    }

    /**
     * Generate blocks for a side branch (perpendicular to main corridor).
     * Branches go left or right from the corridor.
     */
    public static List<BlockPos> branchBlocks(BlockPos branchStart, Direction branchDir, int length) {
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos current = branchStart;
        for (int i = 0; i < length; i++) {
            current = current.offset(branchDir);
            blocks.add(current);        // feet level
            blocks.add(current.up());   // head level
        }
        return blocks;
    }

    /**
     * Returns the left and right directions relative to the player's facing.
     */
    public static Direction leftOf(Direction facing) {
        return facing.rotateYCounterclockwise();
    }

    public static Direction rightOf(Direction facing) {
        return facing.rotateYClockwise();
    }

    /**
     * Whether a corridor step index should trigger a branch.
     * Interval varies slightly (2-4) for imperfection.
     */
    public static boolean isBranchStep(int stepIndex) {
        if (stepIndex <= 0) return false;
        // Variable interval: not always exactly 3
        int interval = BRANCH_INTERVAL + (int)(NoiseGenerator.noise1D(stepIndex * 0.7f) * 1.2f);
        interval = Math.max(2, Math.min(5, interval));
        return stepIndex % interval == 0;
    }

    public static int getBranchLength() {
        return BRANCH_LENGTH;
    }

    public static int getBranchInterval() {
        return BRANCH_INTERVAL;
    }
}
