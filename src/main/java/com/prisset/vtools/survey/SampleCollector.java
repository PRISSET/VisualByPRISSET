package com.prisset.vtools.survey;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v8: Modular tunnel miner. Thin coordinator that delegates to:
 *   - BreakHelper  (block breaking)
 *   - WalkHelper   (movement via input override)
 *   - AimHelper    (camera rotation + server packets)
 *   - MaterialIndex (ore scanning)
 *   - SlotHelper   (tool management)
 *   - SessionGuard (player detection)
 *
 * Simple loop:
 *   1. If ore in reach -> break it
 *   2. If tunnel blocks ahead -> break them
 *   3. If path clear -> walk 1 block forward
 *   4. Repeat
 */
public final class SampleCollector {

    private static final Set<Block> MINEABLE = new HashSet<>();
    static {
        MINEABLE.add(Blocks.STONE); MINEABLE.add(Blocks.DEEPSLATE);
        MINEABLE.add(Blocks.COBBLESTONE); MINEABLE.add(Blocks.COBBLED_DEEPSLATE);
        MINEABLE.add(Blocks.GRANITE); MINEABLE.add(Blocks.DIORITE); MINEABLE.add(Blocks.ANDESITE);
        MINEABLE.add(Blocks.TUFF); MINEABLE.add(Blocks.CALCITE);
        MINEABLE.add(Blocks.GRAVEL); MINEABLE.add(Blocks.SAND);
        MINEABLE.add(Blocks.DIRT); MINEABLE.add(Blocks.GRASS_BLOCK);
        MINEABLE.add(Blocks.NETHERRACK); MINEABLE.add(Blocks.BASALT);
        MINEABLE.add(Blocks.SMOOTH_BASALT);
        MINEABLE.add(Blocks.COAL_ORE); MINEABLE.add(Blocks.DEEPSLATE_COAL_ORE);
        MINEABLE.add(Blocks.IRON_ORE); MINEABLE.add(Blocks.DEEPSLATE_IRON_ORE);
        MINEABLE.add(Blocks.COPPER_ORE); MINEABLE.add(Blocks.DEEPSLATE_COPPER_ORE);
        MINEABLE.add(Blocks.GOLD_ORE); MINEABLE.add(Blocks.DEEPSLATE_GOLD_ORE);
        MINEABLE.add(Blocks.NETHER_GOLD_ORE);
        MINEABLE.add(Blocks.REDSTONE_ORE); MINEABLE.add(Blocks.DEEPSLATE_REDSTONE_ORE);
        MINEABLE.add(Blocks.LAPIS_ORE); MINEABLE.add(Blocks.DEEPSLATE_LAPIS_ORE);
        MINEABLE.add(Blocks.DIAMOND_ORE); MINEABLE.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        MINEABLE.add(Blocks.EMERALD_ORE); MINEABLE.add(Blocks.DEEPSLATE_EMERALD_ORE);
    }

    // Ore Y-level: {minY, maxY, peakY}
    private static final Map<String, int[]> ORE_Y = new LinkedHashMap<>();
    static {
        ORE_Y.put("diamond",  new int[]{-64, 16, -59});
        ORE_Y.put("emerald",  new int[]{-16, 320, 236});
        ORE_Y.put("gold",     new int[]{-64, 32, -16});
        ORE_Y.put("lapis",    new int[]{-64, 64, -1});
        ORE_Y.put("redstone", new int[]{-64, 15, -59});
        ORE_Y.put("iron",     new int[]{-64, 320, 16});
        ORE_Y.put("copper",   new int[]{-16, 112, 48});
        ORE_Y.put("coal",     new int[]{0, 320, 96});
    }

    public enum State { IDLE, BREAKING, WALKING, DESCENDING }

    private static final SampleCollector INSTANCE = new SampleCollector();

    // Helpers
    private final BreakHelper breaker = new BreakHelper();
    private final WalkHelper walker = new WalkHelper();
    private final AimHelper aim = new AimHelper();

    // State
    private State state = State.IDLE;
    private Direction mainDir;
    private int targetY;
    private boolean reachedTargetY;
    private int cooldown;

    // Block queue: tunnel blocks to break in sequence (feet, head, etc.)
    private final Deque<BlockPos> breakQueue = new ArrayDeque<>();

    // Descend phase: 0=break staircase, 1=walk into gap
    private int descPhase;

    // Counters
    private final Map<String, Integer> minedCounts = new HashMap<>();

    private SampleCollector() {}
    public static SampleCollector instance() { return INSTANCE; }
    public State getState() { return state; }
    public WalkHelper getWalkHelper() { return walker; }
    public Map<String, Integer> getMinedCounts() { return Collections.unmodifiableMap(minedCounts); }

    // ===================== START / STOP =====================

    public void start(Direction facing) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        this.mainDir = facing;
        this.targetY = computeBestY(prefs);
        this.reachedTargetY = false;
        this.cooldown = rnd(8, 16);
        this.descPhase = 0;
        this.breakQueue.clear();
        this.minedCounts.clear();
        this.breaker.reset();
        this.walker.stopWalking();
        this.aim.clear();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            aim.face(dirYaw(mainDir), 0f);
            state = State.BREAKING;
        }
    }

    public void stop() {
        breaker.reset();
        walker.stopWalking();
        aim.clear();
        breakQueue.clear();
        state = State.IDLE;
    }

    private void leave(MinecraftClient mc) {
        stop();
        DisplayPrefs p = VToolsMod.getPrefs();
        p.setSurveyEnabled(false);
        p.save();
        if (p.isSurveyAutoLeave()) SessionGuard.disconnect(mc);
    }

    // ===================== MAIN TICK =====================

    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (!prefs.isSurveyEnabled()) { if (state != State.IDLE) stop(); return; }
        if (state == State.IDLE) return;

        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;

        if (SessionGuard.checkAndDisconnect(mc, prefs.getSurveyRadius())) { stop(); return; }
        if (allGoalsMet(prefs)) { leave(mc); return; }

        // Camera smooth every tick
        aim.tick(player);

        // Cooldown (human pause between actions)
        if (cooldown > 0) {
            cooldown--;
            walker.wantForward = false;
            walker.wantJump = false;
            if (cooldown % 5 == 0) aim.drift();
            return;
        }

        switch (state) {
            case BREAKING -> tickBreaking(mc, player, world, prefs);
            case WALKING -> tickWalking(mc, player, world, prefs);
            case DESCENDING -> tickDescending(mc, player, world, prefs);
            default -> {}
        }
    }

    // ===================== BREAKING =====================

    private void tickBreaking(MinecraftClient mc, ClientPlayerEntity player,
                               ClientWorld world, DisplayPrefs prefs) {
        walker.wantForward = false;
        walker.wantJump = false;

        // Y-level check first
        if (!reachedTargetY && Math.abs(player.getBlockPos().getY() - targetY) > 3) {
            state = State.DESCENDING;
            descPhase = 0;
            return;
        }
        reachedTargetY = true;

        // If breaker is active, let it work
        if (breaker.hasTarget()) {
            aim.aimAt(player, breaker.getTarget());
            breaker.tick(player, world);

            if (breaker.isDone()) {
                trackOre(world, breaker.getTarget());
                cooldown = rnd(1, 2);
            }
            return;
        }

        // Pick next from queue
        if (pickFromQueue(player, world)) return;

        // Scan for ore in reach
        BlockPos ore = findBestOre(player, world, prefs);
        if (ore != null) {
            breaker.setTarget(ore);
            aim.aimAt(player, ore);
            return;
        }

        // Queue tunnel blocks ahead (head first, then feet — top-down for gravel)
        BlockPos feet = player.getBlockPos();
        BlockPos aFeet = feet.offset(mainDir);
        BlockPos aHead = aFeet.up();

        // Lava check
        if (MaterialIndex.hasAdjacentLava(world, aFeet)
                || MaterialIndex.hasAdjacentLava(world, aHead)) {
            leave(mc);
            return;
        }

        if (isMineable(player, world, aHead)) breakQueue.add(aHead);
        if (isMineable(player, world, aFeet)) breakQueue.add(aFeet);

        if (pickFromQueue(player, world)) return;

        // Nothing to break — path clear, start walking
        startWalking(player, world, mc);
    }

    private boolean pickFromQueue(ClientPlayerEntity player, ClientWorld world) {
        while (!breakQueue.isEmpty()) {
            BlockPos next = breakQueue.poll();
            if (isMineable(player, world, next)) {
                breaker.setTarget(next);
                aim.aimAt(player, next);
                return true;
            }
        }
        return false;
    }

    // ===================== WALKING =====================

    private void startWalking(ClientPlayerEntity player, ClientWorld world, MinecraftClient mc) {
        if (!walker.startWalk(player, world, mainDir)) {
            // Unsafe ahead (void/lava) — try descending
            if (!reachedTargetY || Math.abs(player.getBlockPos().getY() - targetY) > 3) {
                state = State.DESCENDING;
                descPhase = 0;
            } else {
                // Can't go anywhere — stop
                leave(mc);
            }
            return;
        }
        aim.face(dirYaw(mainDir), 4f);
        state = State.WALKING;
    }

    private void tickWalking(MinecraftClient mc, ClientPlayerEntity player,
                              ClientWorld world, DisplayPrefs prefs) {
        boolean done = walker.tick(player);
        if (done) {
            state = State.BREAKING;
            cooldown = rnd(1, 3);
        }
    }

    // ===================== DESCENDING =====================

    private void tickDescending(MinecraftClient mc, ClientPlayerEntity player,
                                 ClientWorld world, DisplayPrefs prefs) {
        int curY = player.getBlockPos().getY();

        if (Math.abs(curY - targetY) <= 3) {
            reachedTargetY = true;
            walker.stopWalking();
            state = State.BREAKING;
            cooldown = rnd(3, 6);
            return;
        }

        boolean down = curY > targetY;

        if (descPhase == 0) {
            // Break staircase blocks
            walker.wantForward = false;
            walker.wantJump = false;

            if (breaker.hasTarget()) {
                aim.aimAt(player, breaker.getTarget());
                breaker.tick(player, world);
                if (breaker.isDone()) {
                    trackOre(world, breaker.getTarget());
                    cooldown = rnd(0, 1);
                }
                return;
            }

            if (pickFromQueue(player, world)) return;

            // Queue staircase blocks
            BlockPos feet = player.getBlockPos();
            BlockPos ahead = feet.offset(mainDir);

            if (down) {
                queueMineable(player, world, ahead.up());
                queueMineable(player, world, ahead);
                queueMineable(player, world, ahead.down());
            } else {
                queueMineable(player, world, ahead);
                queueMineable(player, world, ahead.up());
                queueMineable(player, world, ahead.up(2));
            }

            if (pickFromQueue(player, world)) return;

            // All clear — walk into gap
            descPhase = 1;
            walker.wantForward = false;
        }

        if (descPhase == 1) {
            aim.face(dirYaw(mainDir), down ? 25f : -20f);

            walker.wantForward = true;
            walker.wantJump = !down && player.isOnGround();

            int newY = player.getBlockPos().getY();
            boolean yMoved = down ? (newY < curY) : (newY > curY);

            if (yMoved) {
                walker.wantForward = false;
                walker.wantJump = false;
                descPhase = 0;
                cooldown = rnd(1, 2);
            }
        }
    }

    private void queueMineable(ClientPlayerEntity player, ClientWorld world, BlockPos pos) {
        if (isMineable(player, world, pos)) {
            breakQueue.add(pos);
        }
    }

    // ===================== ORE SCANNING =====================

    private BlockPos findBestOre(ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        Set<Block> targets = MaterialIndex.buildTargetSet(prefs);
        if (targets.isEmpty()) return null;

        List<BlockPos> found = MaterialIndex.scanExposedTargets(world, player, targets, prefs);
        for (BlockPos pos : found) {
            if (MaterialIndex.hasAdjacentLava(world, pos)) continue;
            double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
            if (dist > 4.5) continue;

            String cat = MaterialIndex.oreCategory(world.getBlockState(pos).getBlock());
            if (goalMet(cat, prefs)) continue;

            return pos;
        }
        return null;
    }

    private void trackOre(ClientWorld world, BlockPos pos) {
        if (pos == null) return;
        BlockState bs = world.getBlockState(pos);
        String cat = MaterialIndex.oreCategory(bs.getBlock());
        if (!"unknown".equals(cat)) {
            minedCounts.merge(cat, 1, Integer::sum);
        }
    }

    // ===================== BLOCK HELPERS =====================

    private boolean isMineable(ClientPlayerEntity player, ClientWorld world, BlockPos pos) {
        BlockState bs = world.getBlockState(pos);
        if (bs.isAir()) return false;
        if (bs.getHardness(world, pos) < 0) return false;
        if (!MINEABLE.contains(bs.getBlock())) return false;
        return player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) <= 4.5;
    }

    // ===================== GOALS =====================

    private boolean goalMet(String cat, DisplayPrefs prefs) {
        int goal = switch (cat) {
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
        if (goal == 0) return false;
        return minedCounts.getOrDefault(cat, 0) >= goal;
    }

    private boolean allGoalsMet(DisplayPrefs p) {
        boolean any = false;
        if (p.isOreDiamond()  && p.getCntDiamond()  > 0) { any = true; if (minedCounts.getOrDefault("diamond",  0) < p.getCntDiamond())  return false; }
        if (p.isOreGold()     && p.getCntGold()     > 0) { any = true; if (minedCounts.getOrDefault("gold",     0) < p.getCntGold())     return false; }
        if (p.isOreIron()     && p.getCntIron()     > 0) { any = true; if (minedCounts.getOrDefault("iron",     0) < p.getCntIron())     return false; }
        if (p.isOreCopper()   && p.getCntCopper()   > 0) { any = true; if (minedCounts.getOrDefault("copper",   0) < p.getCntCopper())   return false; }
        if (p.isOreRedstone() && p.getCntRedstone() > 0) { any = true; if (minedCounts.getOrDefault("redstone", 0) < p.getCntRedstone()) return false; }
        if (p.isOreLapis()    && p.getCntLapis()    > 0) { any = true; if (minedCounts.getOrDefault("lapis",    0) < p.getCntLapis())    return false; }
        if (p.isOreEmerald()  && p.getCntEmerald()  > 0) { any = true; if (minedCounts.getOrDefault("emerald",  0) < p.getCntEmerald())  return false; }
        if (p.isOreCoal()     && p.getCntCoal()     > 0) { any = true; if (minedCounts.getOrDefault("coal",     0) < p.getCntCoal())     return false; }
        return any;
    }

    // ===================== Y-LEVEL =====================

    private int computeBestY(DisplayPrefs prefs) {
        String bestOre = null; int bestW = -1;
        if (prefs.isOreDiamond())  { int w = 100 + prefs.getCntDiamond();  if (w > bestW) { bestW = w; bestOre = "diamond"; }}
        if (prefs.isOreEmerald())  { int w = 90  + prefs.getCntEmerald();  if (w > bestW) { bestW = w; bestOre = "emerald"; }}
        if (prefs.isOreGold())     { int w = 70  + prefs.getCntGold();     if (w > bestW) { bestW = w; bestOre = "gold"; }}
        if (prefs.isOreLapis())    { int w = 60  + prefs.getCntLapis();    if (w > bestW) { bestW = w; bestOre = "lapis"; }}
        if (prefs.isOreRedstone()) { int w = 50  + prefs.getCntRedstone(); if (w > bestW) { bestW = w; bestOre = "redstone"; }}
        if (prefs.isOreIron())     { int w = 40  + prefs.getCntIron();     if (w > bestW) { bestW = w; bestOre = "iron"; }}
        if (prefs.isOreCopper())   { int w = 30  + prefs.getCntCopper();   if (w > bestW) { bestW = w; bestOre = "copper"; }}
        if (prefs.isOreCoal())     { int w = 10  + prefs.getCntCoal();     if (w > bestW) { bestW = w; bestOre = "coal"; }}
        if (bestOre != null && ORE_Y.containsKey(bestOre)) return ORE_Y.get(bestOre)[2];
        return -59;
    }

    // ===================== MATH =====================

    private static float dirYaw(Direction dir) {
        return switch (dir) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    private static int rnd(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
