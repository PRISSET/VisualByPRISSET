package com.prisset.vtools.blueprint;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates an ordered list of blocks to place from a schematic.
 * Strict layer-by-layer order: completes entire Y=0, then Y=1, etc.
 * Within each layer, blocks are sorted by distance to player (nearest first).
 */
public final class BuildQueue {

    private BuildQueue() {}

    /**
     * Generate queue split into strict Y layers.
     * Returns list of layers, each layer is a list of PlaceEntry sorted by proximity.
     */
    public static List<List<PlaceEntry>> generateLayers(ClientWorld world, ClientPlayerEntity player) {
        BlueprintPlacer placer = BlueprintPlacer.instance();
        if (!placer.isReadyToPlace()) return List.of();

        SchematicData schem = placer.getSchematic();
        int sx = schem.getSizeX();
        int sy = schem.getSizeY();
        int sz = schem.getSizeZ();

        Vec3d playerPos = player.getPos();

        List<List<PlaceEntry>> layers = new ArrayList<>();

        for (int y = 0; y < sy; y++) {
            List<PlaceEntry> layer = new ArrayList<>();

            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    if (schem.isAir(x, y, z)) continue;

                    BlockPos worldPos = placer.localToWorld(x, y, z);
                    String expected = schem.getBlockState(x, y, z);

                    String actual = SchematicData.encodeState(world.getBlockState(worldPos));
                    if (expected.equals(actual)) continue;

                    layer.add(new PlaceEntry(x, y, z, worldPos, expected));
                }
            }

            if (!layer.isEmpty()) {
                // Sort within layer: nearest to player first
                layer.sort(Comparator.comparingDouble(
                    e -> e.worldPos.getSquaredDistance(playerPos)));
                layers.add(layer);
            }
        }

        return layers;
    }

    public static class PlaceEntry {
        public final int localX, localY, localZ;
        public final BlockPos worldPos;
        public final String blockState;

        public PlaceEntry(int lx, int ly, int lz, BlockPos worldPos, String blockState) {
            this.localX = lx; this.localY = ly; this.localZ = lz;
            this.worldPos = worldPos;
            this.blockState = blockState;
        }

        public String getBlockId() {
            int bracket = blockState.indexOf('[');
            return bracket >= 0 ? blockState.substring(0, bracket) : blockState;
        }
    }
}
