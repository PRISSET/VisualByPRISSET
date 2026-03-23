package com.prisset.vtools.blueprint;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates an ordered list of blocks to place from a schematic.
 * Order: bottom to top (Y ascending), then row by row (Z), then column (X).
 * Skips air blocks and blocks already correctly placed in the world.
 */
public final class BuildQueue {

    private BuildQueue() {}

    /**
     * Build the placement queue from the current placer state.
     * Each entry: [localX, localY, localZ, paletteString, worldPos].
     */
    public static List<PlaceEntry> generate(ClientWorld world) {
        BlueprintPlacer placer = BlueprintPlacer.instance();
        if (!placer.isReadyToPlace()) return List.of();

        SchematicData schem = placer.getSchematic();
        List<PlaceEntry> queue = new ArrayList<>();

        int sx = schem.getSizeX();
        int sy = schem.getSizeY();
        int sz = schem.getSizeZ();

        // Bottom-up, layer by layer
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    if (schem.isAir(x, y, z)) continue;

                    BlockPos worldPos = placer.localToWorld(x, y, z);
                    String expected = schem.getBlockState(x, y, z);

                    // Skip already correct
                    String actual = SchematicData.encodeState(world.getBlockState(worldPos));
                    if (expected.equals(actual)) continue;

                    queue.add(new PlaceEntry(x, y, z, worldPos, expected));
                }
            }
        }

        return queue;
    }

    public static class PlaceEntry {
        public final int localX, localY, localZ;
        public final BlockPos worldPos;
        public final String blockState; // palette string: "minecraft:stone" or "minecraft:oak_stairs[facing=east,half=bottom]"

        public PlaceEntry(int lx, int ly, int lz, BlockPos worldPos, String blockState) {
            this.localX = lx; this.localY = ly; this.localZ = lz;
            this.worldPos = worldPos;
            this.blockState = blockState;
        }

        /**
         * Extract block ID without properties: "minecraft:oak_stairs[...]" -> "minecraft:oak_stairs"
         */
        public String getBlockId() {
            int bracket = blockState.indexOf('[');
            return bracket >= 0 ? blockState.substring(0, bracket) : blockState;
        }
    }
}
