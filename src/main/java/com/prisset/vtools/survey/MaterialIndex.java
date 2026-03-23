package com.prisset.vtools.survey;

import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Identifies target materials and hazards in the world.
 * Only reports EXPOSED ores (at least one air-adjacent face) within player reach.
 */
public final class MaterialIndex {

    private static final double REACH = 4.5;

    // Base value: higher = mine first. Used when counts are equal.
    private static final Map<Block, Integer> BASE_PRIORITY = new LinkedHashMap<>();
    static {
        BASE_PRIORITY.put(Blocks.DIAMOND_ORE, 100);
        BASE_PRIORITY.put(Blocks.DEEPSLATE_DIAMOND_ORE, 100);
        BASE_PRIORITY.put(Blocks.EMERALD_ORE, 90);
        BASE_PRIORITY.put(Blocks.DEEPSLATE_EMERALD_ORE, 90);
        BASE_PRIORITY.put(Blocks.GOLD_ORE, 70);
        BASE_PRIORITY.put(Blocks.DEEPSLATE_GOLD_ORE, 70);
        BASE_PRIORITY.put(Blocks.NETHER_GOLD_ORE, 65);
        BASE_PRIORITY.put(Blocks.LAPIS_ORE, 60);
        BASE_PRIORITY.put(Blocks.DEEPSLATE_LAPIS_ORE, 60);
        BASE_PRIORITY.put(Blocks.REDSTONE_ORE, 50);
        BASE_PRIORITY.put(Blocks.DEEPSLATE_REDSTONE_ORE, 50);
        BASE_PRIORITY.put(Blocks.IRON_ORE, 40);
        BASE_PRIORITY.put(Blocks.DEEPSLATE_IRON_ORE, 40);
        BASE_PRIORITY.put(Blocks.COPPER_ORE, 30);
        BASE_PRIORITY.put(Blocks.DEEPSLATE_COPPER_ORE, 30);
        BASE_PRIORITY.put(Blocks.COAL_ORE, 10);
        BASE_PRIORITY.put(Blocks.DEEPSLATE_COAL_ORE, 10);
    }

    // Maps each ore block to its "category" for count tracking
    public static String oreCategory(Block block) {
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return "diamond";
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) return "gold";
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return "iron";
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return "copper";
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return "redstone";
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return "lapis";
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return "emerald";
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return "coal";
        return "unknown";
    }

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
     * Scan ONLY exposed ores (at least one air/cave_air face) within player reach.
     * This is what a real player would see in the tunnel walls.
     * Sorted by priority: configured count (higher requested = higher priority),
     * then base rarity value, then distance.
     */
    public static List<BlockPos> scanExposedTargets(ClientWorld world, ClientPlayerEntity player,
                                                     Set<Block> targets, DisplayPrefs prefs) {
        Vec3d eyes = player.getEyePos();
        BlockPos center = player.getBlockPos();
        int scanR = (int) Math.ceil(REACH);
        List<BlockPos> found = new ArrayList<>();

        BlockPos.Mutable mut = new BlockPos.Mutable();
        for (int dx = -scanR; dx <= scanR; dx++) {
            for (int dy = -scanR; dy <= scanR; dy++) {
                for (int dz = -scanR; dz <= scanR; dz++) {
                    mut.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);

                    // Within reach?
                    double dist = eyes.squaredDistanceTo(Vec3d.ofCenter(mut));
                    if (dist > REACH * REACH) continue;

                    BlockState state = world.getBlockState(mut);
                    if (!targets.contains(state.getBlock())) continue;

                    // Must have at least one exposed (air) face -- visible to player
                    if (!hasExposedFace(world, mut)) continue;

                    // Must NOT have lava on any adjacent face
                    if (hasAdjacentLava(world, mut)) continue;

                    found.add(mut.toImmutable());
                }
            }
        }

        // Sort: priority first (count-weighted + base value), then distance
        found.sort((a, b) -> {
            int pa = effectivePriority(world.getBlockState(a).getBlock(), prefs);
            int pb = effectivePriority(world.getBlockState(b).getBlock(), prefs);
            if (pa != pb) return Integer.compare(pb, pa);
            return Double.compare(a.getSquaredDistance(center), b.getSquaredDistance(center));
        });

        return found;
    }

    /**
     * Priority = base_rarity + configured_count.
     * Ore with count=100 requested gets +100 priority on top of base.
     * This means: if user wants 100 coal and 10 diamonds, coal gets boosted.
     */
    private static int effectivePriority(Block block, DisplayPrefs prefs) {
        int base = BASE_PRIORITY.getOrDefault(block, 0);
        String cat = oreCategory(block);
        int countBonus = switch (cat) {
            case "diamond" -> prefs.getCntDiamond();
            case "gold" -> prefs.getCntGold();
            case "iron" -> prefs.getCntIron();
            case "copper" -> prefs.getCntCopper();
            case "redstone" -> prefs.getCntRedstone();
            case "lapis" -> prefs.getCntLapis();
            case "emerald" -> prefs.getCntEmerald();
            case "coal" -> prefs.getCntCoal();
            default -> 0;
        };
        return base + countBonus;
    }

    /**
     * Returns true if at least one face of the block is air/cave_air.
     * This means the ore is VISIBLE in a tunnel wall -- not hidden behind stone.
     */
    public static boolean hasExposedFace(ClientWorld world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockState neighbor = world.getBlockState(pos.offset(dir));
            if (neighbor.isAir()) return true;
        }
        return false;
    }

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
     * Optimal Y-level based on selected ores, weighted by priority.
     * Picks the Y for the highest-priority enabled ore.
     */
    public static int optimalY(DisplayPrefs prefs) {
        int bestY = -59;
        int bestPrio = -1;

        if (prefs.isOreDiamond()) {
            int p = 100 + prefs.getCntDiamond();
            if (p > bestPrio) { bestPrio = p; bestY = -59; }
        }
        if (prefs.isOreEmerald()) {
            int p = 90 + prefs.getCntEmerald();
            if (p > bestPrio) { bestPrio = p; bestY = 232; }
        }
        if (prefs.isOreGold()) {
            int p = 70 + prefs.getCntGold();
            if (p > bestPrio) { bestPrio = p; bestY = -16; }
        }
        if (prefs.isOreLapis()) {
            int p = 60 + prefs.getCntLapis();
            if (p > bestPrio) { bestPrio = p; bestY = -1; }
        }
        if (prefs.isOreRedstone()) {
            int p = 50 + prefs.getCntRedstone();
            if (p > bestPrio) { bestPrio = p; bestY = -59; }
        }
        if (prefs.isOreIron()) {
            int p = 40 + prefs.getCntIron();
            if (p > bestPrio) { bestPrio = p; bestY = 16; }
        }
        if (prefs.isOreCopper()) {
            int p = 30 + prefs.getCntCopper();
            if (p > bestPrio) { bestPrio = p; bestY = 48; }
        }
        if (prefs.isOreCoal()) {
            int p = 10 + prefs.getCntCoal();
            if (p > bestPrio) { bestPrio = p; bestY = 96; }
        }
        return bestY;
    }

    public static boolean isBreakable(BlockState state) {
        return !state.isAir() && state.getHardness(null, null) >= 0;
    }
}
