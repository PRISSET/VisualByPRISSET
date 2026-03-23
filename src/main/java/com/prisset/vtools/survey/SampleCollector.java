package com.prisset.vtools.survey;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
 * v5: Mining bot that correctly descends to ore Y-level and mines like a real player.
 *
 * Key fixes from v4:
 * - ALWAYS descends (or ascends) to target Y before horizontal mining
 * - Only breaks mineable blocks (stone, deepslate, ore, gravel) -- never trees/dirt
 * - Block queue persists across break cycles (doesn't reset after each block)
 * - Stable tunnel direction using absolute coordinates
 */
public final class SampleCollector {

    // Ore Y-level data: {minY, maxY, peakY}
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

    // Blocks the bot is allowed to break (stone-like + ores + gravel)
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
        // All ores
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

    public enum State {
        IDLE, LOOKING, BREAKING, STEPPING, SCANNING, PAUSED, DESCENDING, SWAPPING_TOOL
    }

    private static final SampleCollector INSTANCE = new SampleCollector();

    private State state = State.IDLE;
    private Direction mainDir;
    private int targetY;
    private boolean reachedTargetY;

    // Tunnel: absolute coordinates
    private BlockPos tunnelStart;
    private int tunnelStep;
    private int branchStep;
    private Direction branchDir;
    private boolean branchLeftDone, branchRightDone;

    // Block queue (persists until empty)
    private final Deque<BlockPos> blockQueue = new ArrayDeque<>();

    // Ore queue
    private final Deque<BlockPos> oreQueue = new ArrayDeque<>();
    private final Map<BlockPos, String> oreTypes = new HashMap<>();

    // Breaking
    private BlockPos breakTarget;
    private Direction breakFace;
    private boolean breakStarted;

    // Camera
    private float wantYaw, wantPitch;

    // Timing
    private int pauseTicks;

    // State to return to after breaking a block
    private State returnState;

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
        this.reachedTargetY = false;
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
        this.returnState = null;
        this.pauseTicks = rnd(5, 15);
        this.state = State.PAUSED;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            this.tunnelStart = mc.player.getBlockPos();
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
        if (!prefs.isSurveyEnabled()) { if (state != State.IDLE) stop(); return; }
        if (state == State.IDLE) return;

        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;

        if (SessionGuard.checkAndDisconnect(mc, prefs.getSurveyRadius())) { stop(); return; }
        if (allGoalsMet(prefs)) { stopAndLeave(mc); return; }

        // Always smooth camera
        lerpCamera(player);

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

    // ========== PAUSED ==========

    private void tickPaused(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        if (pauseTicks > 0) {
            pauseTicks--;
            if (pauseTicks % 8 == 0) driftHead(player);
            return;
        }

        // Check Y level first — if not at target Y, descend
        if (!reachedTargetY) {
            int curY = player.getBlockPos().getY();
            if (Math.abs(curY - targetY) > 3) {
                state = State.DESCENDING;
                return;
            }
            // Close enough to target Y
            reachedTargetY = true;
            tunnelStart = player.getBlockPos();
            tunnelStep = 0;
        }

        // If there are queued blocks to break, continue breaking them
        if (!blockQueue.isEmpty()) {
            pickNextFromQueue(player, world, prefs);
            return;
        }

        // Otherwise decide what to do
        decideNext(mc, player, world, prefs);
    }

    // ========== LOOKING ==========

    private void tickLooking(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        float yawErr = Math.abs(wrapAngle(player.getYaw() - wantYaw));
        float pitchErr = Math.abs(player.getPitch() - wantPitch);
        if (yawErr < 3f && pitchErr < 3f) {
            if (breakTarget != null) {
                state = State.BREAKING;
                breakStarted = false;
            } else {
                state = State.STEPPING;
            }
        }
    }

    // ========== BREAKING ==========

    private void tickBreaking(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        if (breakTarget == null) {
            state = State.PAUSED;
            pauseTicks = rnd(1, 3);
            return;
        }

        BlockState bs = world.getBlockState(breakTarget);
        if (bs.isAir() || bs.getHardness(world, breakTarget) < 0) {
            onBlockBroken();
            return;
        }

        if (!ensurePickaxe(player, prefs)) return;

        // Continuous breaking — EVERY tick
        if (!breakStarted) {
            mc.interactionManager.attackBlock(breakTarget, breakFace);
            breakStarted = true;
        } else {
            mc.interactionManager.updateBlockBreakingProgress(breakTarget, breakFace);
        }

        if (world.getBlockState(breakTarget).isAir()) {
            onBlockBroken();
        }
    }

    private void onBlockBroken() {
        if (breakTarget != null && oreTypes.containsKey(breakTarget)) {
            String cat = oreTypes.remove(breakTarget);
            minedCounts.merge(cat, 1, Integer::sum);
        }
        breakTarget = null;
        breakStarted = false;

        // Return to the correct state to continue queue/descending
        state = State.PAUSED;
        pauseTicks = rnd(1, 3);
    }

    // ========== STEPPING ==========

    private void tickStepping(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        BlockPos target = currentTunnelTarget();
        if (target == null) {
            advanceTunnel();
            state = State.PAUSED;
            pauseTicks = rnd(2, 4);
            return;
        }

        Vec3d dest = Vec3d.of(target).add(0.5, 0, 0.5);
        double distSq = player.getPos().squaredDistanceTo(dest.x, player.getPos().y, dest.z);

        if (distSq < 0.3) {
            state = State.SCANNING;
            pauseTicks = rnd(2, 6);
            return;
        }

        nudgeToward(player, dest);
    }

    // ========== SCANNING ==========

    private void tickScanning(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        if (pauseTicks > 0) {
            pauseTicks--;
            if (pauseTicks % 4 == 0) driftHead(player);
            return;
        }

        // Scan for exposed ore
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

        decideNext(mc, player, world, prefs);
    }

    // ========== DESCENDING ==========

    private void tickDescending(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        int curY = player.getBlockPos().getY();

        // Reached target?
        if (Math.abs(curY - targetY) <= 3) {
            reachedTargetY = true;
            tunnelStart = player.getBlockPos();
            tunnelStep = 0;
            state = State.PAUSED;
            pauseTicks = rnd(3, 8);
            return;
        }

        boolean goingDown = curY > targetY;
        BlockPos feet = player.getBlockPos();

        // Build staircase block queue
        if (blockQueue.isEmpty()) {
            if (goingDown) {
                // Staircase down: dig floor ahead, then feet ahead, then head ahead
                BlockPos ahead = feet.offset(mainDir);
                BlockPos aheadDown = ahead.down();
                BlockPos aheadUp = ahead.up();

                addMineable(world, aheadUp);   // head level ahead
                addMineable(world, ahead);     // feet level ahead
                addMineable(world, aheadDown); // one below feet ahead
            } else {
                // Staircase up: dig head+1, head, feet ahead
                BlockPos ahead = feet.offset(mainDir);
                BlockPos aheadUp = ahead.up();
                BlockPos aheadUp2 = ahead.up(2);

                addMineable(world, ahead);
                addMineable(world, aheadUp);
                addMineable(world, aheadUp2);
            }
        }

        if (!blockQueue.isEmpty()) {
            pickNextFromQueue(player, world, prefs);
            return;
        }

        // All blocks clear -> step forward+down (or up)
        Vec3d dest;
        if (goingDown) {
            dest = Vec3d.of(feet.offset(mainDir).down()).add(0.5, 0, 0.5);
        } else {
            dest = Vec3d.of(feet.offset(mainDir).up()).add(0.5, 0, 0.5);
        }
        nudgeToward(player, dest);

        // Also need to jump if going up
        if (!goingDown && player.isOnGround()) {
            player.jump();
        }
    }

    // ========== DECISION LOGIC ==========

    private void decideNext(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        // 1. Mine queued ore
        while (!oreQueue.isEmpty()) {
            BlockPos op = oreQueue.peek();
            BlockState os = world.getBlockState(op);
            if (os.isAir() || !MaterialIndex.hasExposedFace(world, op)) {
                oreQueue.poll(); oreTypes.remove(op); continue;
            }
            double d = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(op));
            if (d > 4.5 * 4.5) { oreQueue.poll(); oreTypes.remove(op); continue; }
            if (MaterialIndex.hasAdjacentLava(world, op)) { oreQueue.poll(); oreTypes.remove(op); continue; }

            if (!ensurePickaxe(player, prefs)) return;
            startBreak(player, op);
            return;
        }

        // 2. Dig tunnel forward
        BlockPos tunnelPos = currentTunnelTarget();
        if (tunnelPos != null) {
            BlockPos nextFeet = tunnelPos;
            BlockPos nextHead = tunnelPos.up();

            if (MaterialIndex.hasAdjacentLava(world, nextFeet) || MaterialIndex.hasAdjacentLava(world, nextHead)) {
                advanceTunnel();
                state = State.PAUSED;
                pauseTicks = rnd(3, 6);
                return;
            }

            blockQueue.clear();
            addMineable(world, nextFeet);
            addMineable(world, nextHead);

            if (!blockQueue.isEmpty()) {
                pickNextFromQueue(player, world, prefs);
                return;
            }

            // Path clear -> walk
            state = State.STEPPING;
            return;
        }

        // 3. Advance tunnel coordinate
        advanceTunnel();
        state = State.PAUSED;
        pauseTicks = rnd(2, 5);
    }

    // ========== BLOCK QUEUE HELPERS ==========

    private void addMineable(ClientWorld world, BlockPos pos) {
        BlockState bs = world.getBlockState(pos);
        if (!bs.isAir() && MINEABLE.contains(bs.getBlock())) {
            blockQueue.add(pos);
        }
    }

    private void pickNextFromQueue(ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        while (!blockQueue.isEmpty()) {
            BlockPos next = blockQueue.poll();
            BlockState bs = world.getBlockState(next);
            if (bs.isAir()) continue;
            if (!MINEABLE.contains(bs.getBlock())) continue;

            if (!ensurePickaxe(player, prefs)) {
                blockQueue.addFirst(next); // put it back
                return;
            }
            startBreak(player, next);
            return;
        }
        // Queue exhausted
        state = State.PAUSED;
        pauseTicks = rnd(1, 3);
    }

    private void startBreak(ClientPlayerEntity player, BlockPos pos) {
        breakTarget = pos;
        breakFace = bestFace(player, pos);
        aimAt(player, pos);
        state = State.LOOKING;
    }

    // ========== TUNNEL COORDINATES ==========

    private BlockPos currentTunnelTarget() {
        if (tunnelStart == null) return null;

        if (branchDir != null && branchStep >= 0) {
            BlockPos corridorPos = tunnelStart.offset(mainDir, tunnelStep);
            return corridorPos.offset(branchDir, branchStep + 1);
        }

        return tunnelStart.offset(mainDir, tunnelStep + 1);
    }

    private void advanceTunnel() {
        if (branchDir != null) {
            branchStep++;
            if (branchStep >= GridLayout.getBranchLength()) {
                if (branchDir == GridLayout.leftOf(mainDir)) {
                    branchLeftDone = true;
                } else {
                    branchRightDone = true;
                }
                branchDir = null;
                branchStep = -1;

                if (branchLeftDone && branchRightDone) {
                    tunnelStep++;
                    branchLeftDone = false;
                    branchRightDone = false;
                } else if (!branchRightDone) {
                    branchDir = GridLayout.rightOf(mainDir);
                    branchStep = 0;
                }
            }
        } else {
            tunnelStep++;
            if (GridLayout.isBranchStep(tunnelStep)) {
                branchDir = GridLayout.leftOf(mainDir);
                branchStep = 0;
                branchLeftDone = false;
                branchRightDone = false;
            }
        }
    }

    // ========== Y-LEVEL ==========

    private int computeBestY(DisplayPrefs prefs) {
        String bestOre = null;
        int bestWeight = -1;

        if (prefs.isOreDiamond())  { int w = 100 + prefs.getCntDiamond();  if (w > bestWeight) { bestWeight = w; bestOre = "diamond"; }}
        if (prefs.isOreEmerald())  { int w = 90  + prefs.getCntEmerald();  if (w > bestWeight) { bestWeight = w; bestOre = "emerald"; }}
        if (prefs.isOreGold())     { int w = 70  + prefs.getCntGold();     if (w > bestWeight) { bestWeight = w; bestOre = "gold"; }}
        if (prefs.isOreLapis())    { int w = 60  + prefs.getCntLapis();    if (w > bestWeight) { bestWeight = w; bestOre = "lapis"; }}
        if (prefs.isOreRedstone()) { int w = 50  + prefs.getCntRedstone(); if (w > bestWeight) { bestWeight = w; bestOre = "redstone"; }}
        if (prefs.isOreIron())     { int w = 40  + prefs.getCntIron();     if (w > bestWeight) { bestWeight = w; bestOre = "iron"; }}
        if (prefs.isOreCopper())   { int w = 30  + prefs.getCntCopper();   if (w > bestWeight) { bestWeight = w; bestOre = "copper"; }}
        if (prefs.isOreCoal())     { int w = 10  + prefs.getCntCoal();     if (w > bestWeight) { bestWeight = w; bestOre = "coal"; }}

        if (bestOre != null && ORE_Y.containsKey(bestOre)) return ORE_Y.get(bestOre)[2];
        return -59;
    }

    // ========== CAMERA ==========

    private void aimAt(ClientPlayerEntity player, BlockPos target) {
        Vec3d eyes = player.getEyePos();
        Vec3d tc = Vec3d.ofCenter(target);
        double dx = tc.x - eyes.x, dy = tc.y - eyes.y, dz = tc.z - eyes.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz))
                + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.5f;
        wantPitch = (float) Math.toDegrees(-Math.atan2(dy, h))
                + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.3f;
    }

    private void lerpCamera(ClientPlayerEntity player) {
        float s = 0.2f + ThreadLocalRandom.current().nextFloat() * 0.06f;
        player.setYaw(lerpAngle(player.getYaw(), wantYaw, s));
        player.setPitch(lerp(player.getPitch(), wantPitch, s));
    }

    private void driftHead(ClientPlayerEntity player) {
        wantYaw += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 1.5f;
        wantPitch += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.7f;
    }

    // ========== MOVEMENT ==========

    private void nudgeToward(ClientPlayerEntity player, Vec3d target) {
        Vec3d pos = player.getPos();
        double dx = target.x - pos.x, dz = target.z - pos.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) return;

        double speed = 0.15 + ThreadLocalRandom.current().nextDouble() * 0.03;
        player.setVelocity((dx / len) * speed, player.getVelocity().y, (dz / len) * speed);
        wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        wantPitch = 0;
    }

    // ========== TOOLS ==========

    private boolean ensurePickaxe(ClientPlayerEntity player, DisplayPrefs prefs) {
        if (SlotHelper.isHoldingUsablePickaxe(player)) return true;
        if (SlotHelper.selectBestPickaxe(player, Blocks.STONE.getDefaultState())) return true;
        stateBeforeSwap = state;
        state = State.SWAPPING_TOOL;
        swapWait = rnd(5, 12);
        return false;
    }

    private void tickSwapTool(MinecraftClient mc, ClientPlayerEntity player) {
        if (swapWait > 0) { swapWait--; return; }
        if (SlotHelper.pullPickaxeFromInventory(player)) {
            state = stateBeforeSwap != null ? stateBeforeSwap : State.PAUSED;
            pauseTicks = rnd(3, 6);
        } else {
            stopAndLeave(mc);
        }
    }

    // ========== BREAKING HELPERS ==========

    private void abortBreak() {
        if (breakStarted) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        }
        breakTarget = null;
        breakStarted = false;
    }

    private static Direction bestFace(ClientPlayerEntity player, BlockPos pos) {
        Vec3d diff = player.getEyePos().subtract(Vec3d.ofCenter(pos));
        Direction best = Direction.UP;
        double maxDot = Double.NEGATIVE_INFINITY;
        for (Direction d : Direction.values()) {
            double dot = diff.dotProduct(Vec3d.of(d.getVector()));
            if (dot > maxDot) { maxDot = dot; best = d; }
        }
        return best;
    }

    // ========== GOALS ==========

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

    // ========== MATH ==========

    private static int rnd(int min, int max) { return ThreadLocalRandom.current().nextInt(min, max + 1); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float lerpAngle(float a, float b, float t) { return a + wrapAngle(b - a) * t; }
    private static float wrapAngle(float a) { a %= 360f; if (a > 180f) a -= 360f; if (a < -180f) a += 360f; return a; }
}
