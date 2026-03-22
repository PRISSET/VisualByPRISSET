package com.prisset.vtools.survey;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans a branch-mine grid pattern.
 * Neutral naming: "GridLayout" reads as a UI/structure layout helper.
 *
 * Pattern: main corridor in the player's facing direction,
 * with side branches every 3 blocks. Each branch extends 10 blocks
 * to either side. The tunnel is 1 wide, 2 tall (feet + head).
 *
 * Mining order: dig main corridor forward, periodically check
 * side blocks for ore veins within scan radius.
 */
public final class GridLayout {

    private static final int BRANCH_INTERVAL = 3;
    private static final int BRANCH_LENGTH = 10;

    private GridLayout() {}

    /**
     * Generate the sequence of BlockPos to break for a main corridor segment.
     * Corridor is 1 wide, 2 tall. Returns feet-level and head-level blocks.
     */
    public static List<BlockPos> corridorSegment(BlockPos feetPos, Direction facing) {
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos ahead = feetPos.offset(facing);
        blocks.add(ahead);           // feet level
        blocks.add(ahead.up());      // head level
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
     */
    public static boolean isBranchStep(int stepIndex) {
        return stepIndex > 0 && stepIndex % BRANCH_INTERVAL == 0;
    }

    public static int getBranchLength() {
        return BRANCH_LENGTH;
    }

    public static int getBranchInterval() {
        return BRANCH_INTERVAL;
    }
}
