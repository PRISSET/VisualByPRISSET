package com.prisset.vtools.survey;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v4: Proper sequential mining bot that behaves like a real player.
 *
 * Core loop (each tick):
 *   1. If actively BREAKING a block -> updateBlockBreakingProgress (no interruptions)
 *   2. If block broke -> short pause, pick next block
 *   3. If no block to break -> walk forward one step
 *   4. After stepping -> scan walls for exposed ore
 *
 * Movement uses velocity nudging toward calculated absolute positions.
 * Camera smoothly lerps every tick (never snaps).
 * Tunnel position tracked absolutely (not relative to player).
 */
public final class SampleCollector {

    // --- Ore Y-level knowledge (MC 1.20.1 world gen) ---
    // Format: {minY, maxY, peakY}
    private static final Map<String, int[]> ORE_Y_DATA = new LinkedHashMap<>();
    static {
        ORE_Y_DATA.put("diamond",   new int[]{-64, 16, -59});
        ORE_Y_DATA.put("emerald",   new int[]{-16, 320, 236});
        ORE_Y_DATA.put("gold",      new int[]{-64, 32, -16});
        ORE_Y_DATA.put("lapis",     new int[]{-64, 64, -1});
        ORE_Y_DATA.put("redstone",  new int[]{-64, 15, -59});
        ORE_Y_DATA.put("iron",      new int[]{-64, 320, 16});
        ORE_Y_DATA.put("copper",    new int[]{-16, 112, 48});
        ORE_Y_DATA.put("coal",      new int[]{0, 320, 96});
    }

    public enum State {
        IDLE,
        LOOKING,      // smoothly turning camera toward target
        BREAKING,     // holding left click on a block every tick
        STEPPING,     // walking to next tunnel position
        SCANNING,     // looking around walls for ore (brief pause)
        PAUSED,       // human-like micro-pause between actions
        DESCENDING,   // digging staircase down to target Y
        SWAPPING_TOOL
    }

    private static final SampleCollector INSTANCE = new SampleCollector();

    // --- State ---
    private State state = State.IDLE;
    private Direction mainDir;
    private int targetY;

    // Tunnel tracking: absolute world positions
    private BlockPos tunnelStart;
    private int tunnelStep;        // how far along the main corridor
    private int branchStep;        // how far along current branch (-1 = no branch)
    private Direction branchDir;   // current branch direction (null = main corridor)
    private boolean branchLeftDone;
    private boolean branchRightDone;

    // Block queue: blocks to break at current position
    private final Deque<BlockPos> blockQueue = new ArrayDeque<>();

    // Ore queue: exposed ores to mine
    private final Deque<BlockPos> oreQueue = new ArrayDeque<>();
    private final Map<BlockPos, String> oreTypes = new HashMap<>();

    // Active breaking
    private BlockPos breakTarget;
    private Direction breakFace;
    private boolean breakStarted;

    // Camera
    private float wantYaw, wantPitch;

    // Timing
    private int pauseTicks;

    // Counters
    private final Map<String, Integer> minedCounts = new HashMap<>();

    // Tool swap
    private int swapWait;
    private State stateBeforeSwap;

    private SampleCollector() {}
    public static SampleCollector instance() { return INSTANCE; }
    public State getState() { return state; }
    public Map<String, Integer> getMinedCounts() { return Collections.unmodifiableMap(minedCounts); }

    // ========== START / STOP ==========

    public void start(Direction facing) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        this.mainDir = facing;
        this.targetY = computeBestY(prefs);
        this.tunnelStep = 0;
        this.branchStep = -1;
        this.branchDir = null;
        this.branchLeftDone = false;
        this.branchRightDone = false;
        this.blockQueue.clear();
        this.oreQueue.clear();
        this.oreTypes.clear();
        this.minedCounts.clear();
        this.breakTarget = null;
        this.breakStarted = false;
        this.pauseTicks = rnd(10, 25);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            this.tunnelStart = mc.player.getBlockPos();
            int curY = tunnelStart.getY();
            this.state = (curY > targetY + 3) ? State.DESCENDING : State.PAUSED;
        }
    }

    public void stop() {
        state = State.IDLE;
        abortBreak();
        blockQueue.clear();
        oreQueue.clear();
        oreTypes.clear();
    }

    private void stopAndLeave(MinecraftClient mc) {
        stop();
        DisplayPrefs p = VToolsMod.getPrefs();
        p.setSurveyEnabled(false);
        p.save();
        if (p.isSurveyAutoLeave()) SessionGuard.disconnect(mc);
    }

    // ========== MAIN TICK ==========

    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (!prefs.isSurveyEnabled()) {
            if (state != State.IDLE) stop();
            return;
        }
        if (state == State.IDLE) return;

        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;

        // Safety: nearby players -> disconnect
        if (SessionGuard.checkAndDisconnect(mc, prefs.getSurveyRadius())) {
            stop();
            return;
        }

        // Check goals
        if (allGoalsMet(prefs)) {
            stopAndLeave(mc);
            return;
        }

        // --- ALWAYS smooth-lerp camera every tick ---
        lerpCamera(player);

        // --- State machine ---
        switch (state) {
            case PAUSED -> tickPaused(mc, player, world, prefs);
            case LOOKING -> tickLooking(mc, player, world, prefs);
            case BREAKING -> tickBreaking(mc, player, world, prefs);
            case STEPPING -> tickStepping(mc, player, world, prefs);
            case SCANNING -> tickScanning(mc, player, world, prefs);
            case DESCENDING -> tickDescending(mc, player, world, prefs);
            case SWAPPING_TOOL -> tickSwapTool(mc, player);
            default -> {}
        }
    }

    // ========== STATES ==========

    private void tickPaused(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        if (pauseTicks > 0) {
            pauseTicks--;
            // Occasionally drift head slightly during pause
            if (pauseTicks % 7 == 0) driftHead(player);
            return;
        }
        // Pause done -> decide what to do next
        decideNext(mc, player, world, prefs);
    }

    private void tickLooking(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        // Wait until camera is roughly aimed
        float yawErr = Math.abs(wrapAngle(player.getYaw() - wantYaw));
        float pitchErr = Math.abs(player.getPitch() - wantPitch);
        if (yawErr < 3f && pitchErr < 3f) {
            // Camera aimed -- start breaking or stepping
            if (breakTarget != null) {
                state = State.BREAKING;
                breakStarted = false;
            } else {
                state = State.STEPPING;
            }
        }
        // lerpCamera runs every tick anyway
    }

    private void tickBreaking(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        if (breakTarget == null) {
            state = State.PAUSED;
            pauseTicks = rnd(2, 5);
            return;
        }

        BlockState bs = world.getBlockState(breakTarget);

        // Block already gone?
        if (bs.isAir() || bs.getHardness(world, breakTarget) < 0) {
            onBlockBroken(world);
            return;
        }

        // Ensure pickaxe
        if (!ensurePickaxe(player, prefs)) return;

        // Start or continue breaking -- EVERY TICK, no pauses
        if (!breakStarted) {
            mc.interactionManager.attackBlock(breakTarget, breakFace);
            breakStarted = true;
        } else {
            mc.interactionManager.updateBlockBreakingProgress(breakTarget, breakFace);
        }

        // Check if broken after this tick's progress
        if (world.getBlockState(breakTarget).isAir()) {
            onBlockBroken(world);
        }
    }

    private void onBlockBroken(ClientWorld world) {
        // Count if it was ore
        if (breakTarget != null && oreTypes.containsKey(breakTarget)) {
            String cat = oreTypes.remove(breakTarget);
            minedCounts.merge(cat, 1, Integer::sum);
        }
        breakTarget = null;
        breakStarted = false;

        // Short human pause between blocks
        state = State.PAUSED;
        pauseTicks = rnd(1, 4);
    }

    private void tickStepping(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        BlockPos target = currentTunnelTarget();
        if (target == null) {
            state = State.PAUSED;
            pauseTicks = rnd(2, 5);
            return;
        }

        Vec3d dest = Vec3d.of(target).add(0.5, 0, 0.5);
        double distSq = player.getPos().squaredDistanceTo(dest.x, player.getPos().y, dest.z);

        if (distSq < 0.25) {
            // Arrived at next position
            advanceTunnel();
            state = State.SCANNING;
            pauseTicks = rnd(3, 8);
            return;
        }

        // Walk toward target
        nudgeToward(player, dest);
    }

    private void tickScanning(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        // Brief pause while "looking around" for ore
        if (pauseTicks > 0) {
            pauseTicks--;
            if (pauseTicks % 5 == 0) driftHead(player);
            return;
        }

        // Scan for exposed ore in reach
        Set<Block> targets = MaterialIndex.buildTargetSet(prefs);
        if (!targets.isEmpty()) {
            List<BlockPos> found = MaterialIndex.scanExposedTargets(world, player, targets, prefs);
            for (BlockPos p : found) {
                if (!oreQueue.contains(p) && !MaterialIndex.hasAdjacentLava(world, p)) {
                    oreQueue.add(p);
                    oreTypes.put(p, MaterialIndex.oreCategory(world.getBlockState(p).getBlock()));
                }
            }
        }

        // Decide next action
        decideNext(mc, player, world, prefs);
    }

    private void tickDescending(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        int curY = player.getBlockPos().getY();
        if (curY <= targetY + 2) {
            tunnelStart = player.getBlockPos();
            tunnelStep = 0;
            state = State.PAUSED;
            pauseTicks = rnd(5, 15);
            return;
        }

        // Dig staircase: 3 blocks per step (ahead at feet-1, feet, head)
        BlockPos feet = player.getBlockPos();
        BlockPos stepDown = feet.offset(mainDir).down();
        BlockPos stepFeet = feet.offset(mainDir);
        BlockPos stepHead = feet.offset(mainDir).up();

        // Lava check
        if (MaterialIndex.hasAdjacentLava(world, stepDown)
                || MaterialIndex.hasAdjacentLava(world, stepFeet)) {
            stop(); // Too dangerous, stop
            return;
        }

        // Queue blocks to break
        if (blockQueue.isEmpty()) {
            if (MaterialIndex.isBreakable(world.getBlockState(stepDown)))
                blockQueue.add(stepDown);
            if (MaterialIndex.isBreakable(world.getBlockState(stepFeet)))
                blockQueue.add(stepFeet);
            if (MaterialIndex.isBreakable(world.getBlockState(stepHead)))
                blockQueue.add(stepHead);
        }

        if (!blockQueue.isEmpty()) {
            BlockPos next = blockQueue.peek();
            if (world.getBlockState(next).isAir()) {
                blockQueue.poll();
                return;
            }
            if (!ensurePickaxe(player, prefs)) return;
            aimAt(player, next);
            breakTarget = next;
            breakFace = bestFace(player, next);
            state = State.LOOKING;
            return;
        }

        // All clear -> step down
        nudgeToward(player, Vec3d.of(stepDown).add(0.5, 1, 0.5));
    }

    private void tickSwapTool(MinecraftClient mc, ClientPlayerEntity player) {
        if (swapWait > 0) { swapWait--; return; }
        if (SlotHelper.pullPickaxeFromInventory(player)) {
            state = stateBeforeSwap != null ? stateBeforeSwap : State.PAUSED;
            pauseTicks = rnd(3, 8);
        } else {
            stopAndLeave(mc);
        }
    }

    // ========== DECISION LOGIC ==========

    private void decideNext(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        // Priority 1: mine queued ore
        while (!oreQueue.isEmpty()) {
            BlockPos op = oreQueue.peek();
            if (world.getBlockState(op).isAir() || !MaterialIndex.hasExposedFace(world, op)) {
                oreQueue.poll();
                oreTypes.remove(op);
                continue;
            }
            double d = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(op));
            if (d > 4.5 * 4.5) { oreQueue.poll(); oreTypes.remove(op); continue; }
            if (MaterialIndex.hasAdjacentLava(world, op)) { oreQueue.poll(); oreTypes.remove(op); continue; }

            // Mine this ore
            if (!ensurePickaxe(player, prefs)) return;
            aimAt(player, op);
            breakTarget = op;
            breakFace = bestFace(player, op);
            state = State.LOOKING;
            return;
        }

        // Priority 2: break blocks ahead in tunnel
        BlockPos tunnelPos = currentTunnelTarget();
        if (tunnelPos != null) {
            // Queue feet + head blocks at next position
            blockQueue.clear();
            Direction digDir = (branchDir != null) ? branchDir : mainDir;
            BlockPos nextFeet = tunnelPos;
            BlockPos nextHead = tunnelPos.up();

            // Lava check before digging
            if (MaterialIndex.hasAdjacentLava(world, nextFeet)
                    || MaterialIndex.hasAdjacentLava(world, nextHead)) {
                // Skip this position, try to go around
                advanceTunnel();
                state = State.PAUSED;
                pauseTicks = rnd(3, 8);
                return;
            }

            if (MaterialIndex.isBreakable(world.getBlockState(nextFeet)))
                blockQueue.add(nextFeet);
            if (MaterialIndex.isBreakable(world.getBlockState(nextHead)))
                blockQueue.add(nextHead);

            if (!blockQueue.isEmpty()) {
                BlockPos next = blockQueue.poll();
                if (!ensurePickaxe(player, prefs)) return;
                aimAt(player, next);
                breakTarget = next;
                breakFace = bestFace(player, next);
                state = State.LOOKING;
                return;
            }

            // Path clear -> walk to it
            state = State.STEPPING;
            return;
        }

        // Nothing to do at current branch -> advance tunnel logic
        advanceTunnel();
        state = State.PAUSED;
        pauseTicks = rnd(2, 6);
    }

    // ========== TUNNEL COORDINATE SYSTEM ==========

    /**
     * Returns the feet-level BlockPos where the bot should dig/walk to next.
     * Calculated absolutely from tunnelStart + direction + step count.
     */
    private BlockPos currentTunnelTarget() {
        if (tunnelStart == null) return null;

        if (branchDir != null && branchStep >= 0) {
            // We're in a branch: corridor position + branch offset
            BlockPos corridorPos = tunnelStart.offset(mainDir, tunnelStep);
            return corridorPos.offset(branchDir, branchStep + 1);
        }

        // Main corridor
        return tunnelStart.offset(mainDir, tunnelStep + 1);
    }

    private void advanceTunnel() {
        if (branchDir != null) {
            // In a branch
            branchStep++;
            if (branchStep >= GridLayout.getBranchLength()) {
                // Branch done
                if (branchDir == GridLayout.leftOf(mainDir)) {
                    branchLeftDone = true;
                } else {
                    branchRightDone = true;
                }
                branchDir = null;
                branchStep = -1;

                // If both branches done, advance corridor
                if (branchLeftDone && branchRightDone) {
                    tunnelStep++;
                    branchLeftDone = false;
                    branchRightDone = false;
                } else if (!branchRightDone) {
                    // Start right branch
                    branchDir = GridLayout.rightOf(mainDir);
                    branchStep = 0;
                }
            }
        } else {
            // Main corridor
            tunnelStep++;

            // Start branch every N steps
            if (GridLayout.isBranchStep(tunnelStep)) {
                branchDir = GridLayout.leftOf(mainDir);
                branchStep = 0;
                branchLeftDone = false;
                branchRightDone = false;
            }
        }
    }

    // ========== Y-LEVEL KNOWLEDGE ==========

    /**
     * Compute the best Y level based on enabled ores and their requested counts.
     * Weighs by: base ore value + requested count.
     * If multiple ores selected, picks Y that covers the most valuable ones.
     */
    private int computeBestY(DisplayPrefs prefs) {
        String bestOre = null;
        int bestWeight = -1;

        if (prefs.isOreDiamond()) {
            int w = 100 + prefs.getCntDiamond();
            if (w > bestWeight) { bestWeight = w; bestOre = "diamond"; }
        }
        if (prefs.isOreEmerald()) {
            int w = 90 + prefs.getCntEmerald();
            if (w > bestWeight) { bestWeight = w; bestOre = "emerald"; }
        }
        if (prefs.isOreGold()) {
            int w = 70 + prefs.getCntGold();
            if (w > bestWeight) { bestWeight = w; bestOre = "gold"; }
        }
        if (prefs.isOreLapis()) {
            int w = 60 + prefs.getCntLapis();
            if (w > bestWeight) { bestWeight = w; bestOre = "lapis"; }
        }
        if (prefs.isOreRedstone()) {
            int w = 50 + prefs.getCntRedstone();
            if (w > bestWeight) { bestWeight = w; bestOre = "redstone"; }
        }
        if (prefs.isOreIron()) {
            int w = 40 + prefs.getCntIron();
            if (w > bestWeight) { bestWeight = w; bestOre = "iron"; }
        }
        if (prefs.isOreCopper()) {
            int w = 30 + prefs.getCntCopper();
            if (w > bestWeight) { bestWeight = w; bestOre = "copper"; }
        }
        if (prefs.isOreCoal()) {
            int w = 10 + prefs.getCntCoal();
            if (w > bestWeight) { bestWeight = w; bestOre = "coal"; }
        }

        if (bestOre != null && ORE_Y_DATA.containsKey(bestOre)) {
            return ORE_Y_DATA.get(bestOre)[2]; // peak Y
        }
        return -59; // default: diamond level
    }

    // ========== CAMERA ==========

    private void aimAt(ClientPlayerEntity player, BlockPos target) {
        Vec3d eyes = player.getEyePos();
        Vec3d tc = Vec3d.ofCenter(target);
        double dx = tc.x - eyes.x;
        double dy = tc.y - eyes.y;
        double dz = tc.z - eyes.z;
        double h = Math.sqrt(dx * dx + dz * dz);

        wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        wantPitch = (float) Math.toDegrees(-Math.atan2(dy, h));

        // Small jitter
        wantYaw += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.6f;
        wantPitch += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.4f;
    }

    private void lerpCamera(ClientPlayerEntity player) {
        float speed = 0.18f + ThreadLocalRandom.current().nextFloat() * 0.07f;
        player.setYaw(lerpAngle(player.getYaw(), wantYaw, speed));
        player.setPitch(lerp(player.getPitch(), wantPitch, speed));
    }

    private void driftHead(ClientPlayerEntity player) {
        wantYaw += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 2.0f;
        wantPitch += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 1.0f;
    }

    // ========== MOVEMENT ==========

    private void nudgeToward(ClientPlayerEntity player, Vec3d target) {
        Vec3d pos = player.getPos();
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) return;

        double speed = 0.14 + ThreadLocalRandom.current().nextDouble() * 0.03;
        player.setVelocity(
                (dx / len) * speed,
                player.getVelocity().y,
                (dz / len) * speed
        );

        // Also aim forward while walking
        wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        wantPitch = 0; // look straight ahead while walking
    }

    // ========== TOOL MANAGEMENT ==========

    private boolean ensurePickaxe(ClientPlayerEntity player, DisplayPrefs prefs) {
        if (SlotHelper.isHoldingUsablePickaxe(player)) return true;

        BlockState stone = net.minecraft.block.Blocks.STONE.getDefaultState();
        if (SlotHelper.selectBestPickaxe(player, stone)) return true;

        // Need to pull from inventory
        stateBeforeSwap = state;
        state = State.SWAPPING_TOOL;
        swapWait = rnd(5, 12);
        return false;
    }

    // ========== BLOCK BREAKING HELPERS ==========

    private void abortBreak() {
        if (breakStarted) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        }
        breakTarget = null;
        breakStarted = false;
    }

    private static Direction bestFace(ClientPlayerEntity player, BlockPos pos) {
        Vec3d eyes = player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d diff = eyes.subtract(center);
        Direction best = Direction.UP;
        double maxDot = Double.NEGATIVE_INFINITY;
        for (Direction d : Direction.values()) {
            Vec3d n = Vec3d.of(d.getVector());
            double dot = diff.dotProduct(n);
            if (dot > maxDot) { maxDot = dot; best = d; }
        }
        return best;
    }

    // ========== GOAL TRACKING ==========

    private boolean allGoalsMet(DisplayPrefs p) {
        boolean any = false;
        if (p.isOreDiamond() && p.getCntDiamond() > 0) {
            any = true;
            if (minedCounts.getOrDefault("diamond", 0) < p.getCntDiamond()) return false;
        }
        if (p.isOreGold() && p.getCntGold() > 0) {
            any = true;
            if (minedCounts.getOrDefault("gold", 0) < p.getCntGold()) return false;
        }
        if (p.isOreIron() && p.getCntIron() > 0) {
            any = true;
            if (minedCounts.getOrDefault("iron", 0) < p.getCntIron()) return false;
        }
        if (p.isOreCopper() && p.getCntCopper() > 0) {
            any = true;
            if (minedCounts.getOrDefault("copper", 0) < p.getCntCopper()) return false;
        }
        if (p.isOreRedstone() && p.getCntRedstone() > 0) {
            any = true;
            if (minedCounts.getOrDefault("redstone", 0) < p.getCntRedstone()) return false;
        }
        if (p.isOreLapis() && p.getCntLapis() > 0) {
            any = true;
            if (minedCounts.getOrDefault("lapis", 0) < p.getCntLapis()) return false;
        }
        if (p.isOreEmerald() && p.getCntEmerald() > 0) {
            any = true;
            if (minedCounts.getOrDefault("emerald", 0) < p.getCntEmerald()) return false;
        }
        if (p.isOreCoal() && p.getCntCoal() > 0) {
            any = true;
            if (minedCounts.getOrDefault("coal", 0) < p.getCntCoal()) return false;
        }
        return any; // true only if at least one goal was set AND all met
    }

    // ========== MATH ==========

    private static int rnd(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        return a + wrapAngle(b - a) * t;
    }

    private static float wrapAngle(float a) {
        a %= 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }
}
