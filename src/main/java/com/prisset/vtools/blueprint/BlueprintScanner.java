package com.prisset.vtools.blueprint;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Scans a selected region and captures all blocks into a SchematicData.
 * Preserves full BlockState including properties (facing, half, type, etc.)
 */
public final class BlueprintScanner {

    private BlueprintScanner() {}

    /**
     * Scan the current selection and return a SchematicData.
     * Returns null if selection is incomplete or world is unavailable.
     */
    public static SchematicData scan(ClientWorld world) {
        SelectionManager sel = SelectionManager.instance();
        if (!sel.isComplete() || world == null) return null;

        BlockPos min = sel.getMin();
        BlockPos max = sel.getMax();

        int sx = sel.getSizeX();
        int sy = sel.getSizeY();
        int sz = sel.getSizeZ();

        SchematicData data = new SchematicData(sx, sy, sz);

        BlockPos.Mutable mut = new BlockPos.Mutable();

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    mut.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                    BlockState state = world.getBlockState(mut);
                    data.setBlock(x, y, z, state);
                }
            }
        }

        return data;
    }
}
