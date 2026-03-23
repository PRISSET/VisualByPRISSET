package com.prisset.vtools.blueprint;

import net.minecraft.util.math.BlockPos;

/**
 * Manages two-point selection for schematic scanning.
 *
 * Usage:
 *   - Blueprint mode active: left-click = pos1, right-click = pos2
 *   - Both set = selection complete, ready to scan
 *   - Reset clears both points
 */
public final class SelectionManager {

    private static final SelectionManager INSTANCE = new SelectionManager();

    private BlockPos pos1;
    private BlockPos pos2;
    private boolean active; // blueprint selection mode on/off

    private SelectionManager() {}
    public static SelectionManager instance() { return INSTANCE; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) {
        this.active = active;
        if (!active) reset();
    }

    public void setPos1(BlockPos pos) { this.pos1 = pos.toImmutable(); }
    public void setPos2(BlockPos pos) { this.pos2 = pos.toImmutable(); }
    public BlockPos getPos1() { return pos1; }
    public BlockPos getPos2() { return pos2; }

    public boolean hasPos1() { return pos1 != null; }
    public boolean hasPos2() { return pos2 != null; }
    public boolean isComplete() { return pos1 != null && pos2 != null; }

    public void reset() {
        pos1 = null;
        pos2 = null;
    }

    // Min corner of selection box
    public BlockPos getMin() {
        if (!isComplete()) return null;
        return new BlockPos(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ())
        );
    }

    // Max corner of selection box
    public BlockPos getMax() {
        if (!isComplete()) return null;
        return new BlockPos(
            Math.max(pos1.getX(), pos2.getX()),
            Math.max(pos1.getY(), pos2.getY()),
            Math.max(pos1.getZ(), pos2.getZ())
        );
    }

    public int getSizeX() { return isComplete() ? getMax().getX() - getMin().getX() + 1 : 0; }
    public int getSizeY() { return isComplete() ? getMax().getY() - getMin().getY() + 1 : 0; }
    public int getSizeZ() { return isComplete() ? getMax().getZ() - getMin().getZ() + 1 : 0; }
    public int getTotalBlocks() { return getSizeX() * getSizeY() * getSizeZ(); }
}
