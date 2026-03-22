package com.prisset.vtools.survey;

import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Identifies target materials and hazards in the world.
 * Neutral naming: "MaterialIndex" reads as a resource-pack block catalog utility.
 */
public final class MaterialIndex {

    private MaterialIndex() {}

    public static Set<Block> buildTargetSet(DisplayPrefs prefs) {
        Set<Block> targets = new HashSet<>();
        if (prefs.isOreDiamond()) {
            targets.add(Blocks.DIAMOND_ORE);
            targets.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        }
        if (prefs.isOreGold()) {
            targets.add(Blocks.GOLD_ORE);
            targets.add(Blocks.DEEPSLATE_GOLD_ORE);
            targets.add(Blocks.NETHER_GOLD_ORE);
        }
        if (prefs.isOreIron()) {
            targets.add(Blocks.IRON_ORE);
            targets.add(Blocks.DEEPSLATE_IRON_ORE);
        }
        if (prefs.isOreCopper()) {
            targets.add(Blocks.COPPER_ORE);
            targets.add(Blocks.DEEPSLATE_COPPER_ORE);
        }
        if (prefs.isOreRedstone()) {
            targets.add(Blocks.REDSTONE_ORE);
            targets.add(Blocks.DEEPSLATE_REDSTONE_ORE);
        }
        if (prefs.isOreLapis()) {
            targets.add(Blocks.LAPIS_ORE);
            targets.add(Blocks.DEEPSLATE_LAPIS_ORE);
        }
        if (prefs.isOreEmerald()) {
            targets.add(Blocks.EMERALD_ORE);
            targets.add(Blocks.DEEPSLATE_EMERALD_ORE);
        }
        if (prefs.isOreCoal()) {
            targets.add(Blocks.COAL_ORE);
            targets.add(Blocks.DEEPSLATE_COAL_ORE);
        }
        return targets;
    }

    /**
     * Scan a radius around a position for target ore blocks.
     * Returns positions sorted by distance (nearest first).
     */
    public static List<BlockPos> scanForTargets(ClientWorld world, BlockPos center, int radius, Set<Block> targets) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.Mutable mut = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mut.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = world.getBlockState(mut);
                    if (targets.contains(state.getBlock())) {
                        found.add(mut.toImmutable());
                    }
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.getSquaredDistance(center), b.getSquaredDistance(center)));
        return found;
    }

    /**
     * Check if breaking a block at pos would expose lava on any face.
     */
    public static boolean hasAdjacentLava(ClientWorld world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            BlockState state = world.getBlockState(neighbor);
            if (state.getFluidState().getFluid() == Fluids.LAVA
                    || state.getFluidState().getFluid() == Fluids.FLOWING_LAVA) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a position is safe to stand on (no lava below or at feet/head level).
     */
    public static boolean isSafeToStand(ClientWorld world, BlockPos feetPos) {
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos check = feetPos.up(dy);
            BlockState state = world.getBlockState(check);
            if (state.getFluidState().getFluid() == Fluids.LAVA
                    || state.getFluidState().getFluid() == Fluids.FLOWING_LAVA) {
                return false;
            }
        }
        return true;
    }

    /**
     * Find lava-adjacent blocks around a position that need to be sealed.
     * Returns positions where cobblestone/blocks should be placed.
     */
    public static List<BlockPos> findLavaExposures(ClientWorld world, BlockPos center, int scanDist) {
        List<BlockPos> exposures = new ArrayList<>();
        BlockPos.Mutable mut = new BlockPos.Mutable();
        for (int dx = -scanDist; dx <= scanDist; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -scanDist; dz <= scanDist; dz++) {
                    mut.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = world.getBlockState(mut);
                    if (state.getFluidState().getFluid() == Fluids.LAVA
                            || state.getFluidState().getFluid() == Fluids.FLOWING_LAVA) {
                        // Check if this lava block has an air neighbor where it could flow
                        for (Direction dir : Direction.values()) {
                            BlockPos adj = mut.offset(dir).toImmutable();
                            if (world.getBlockState(adj).isAir()) {
                                exposures.add(adj);
                            }
                        }
                    }
                }
            }
        }
        return exposures;
    }

    /**
     * Optimal Y-level for different ores in 1.20.1.
     * Returns the best Y to mine at based on selected ores.
     */
    public static int optimalY(DisplayPrefs prefs) {
        // Diamond/redstone/lapis are best at Y=-59 (deepslate level)
        if (prefs.isOreDiamond()) return -59;
        if (prefs.isOreRedstone()) return -59;
        if (prefs.isOreLapis()) return -1;
        if (prefs.isOreGold()) return -16;
        if (prefs.isOreEmerald()) return 232;
        if (prefs.isOreCopper()) return 48;
        if (prefs.isOreIron()) return 16;
        if (prefs.isOreCoal()) return 96;
        return -59;
    }

    public static boolean isBreakable(BlockState state) {
        return !state.isAir() && state.getHardness(null, null) >= 0;
    }
}
