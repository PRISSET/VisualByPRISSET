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
 * v2: 12-state FSM tunnel miner with full human behavior simulation.
 *
 * Delegates to:
 *   - AimHelper       (Bezier rotation, Perlin jitter, no raw packets)
 *   - WalkHelper       (float acceleration, sprint, strafe drift)
 *   - BreakHelper      (pre-break delay, swing variation)
 *   - IdleBehavior     (look-around, pauses, jumps, crouch)
 *   - FatigueModel     (session speed variation)
 *   - PathPlanner      (imperfect pathing, Y-drift, order shuffle)
 *   - ToolManager      (delayed tool switching)
 *   - HumanTiming      (multimodal delays)
 *   - NoiseGenerator   (Perlin noise for all variation)
 *   - MaterialIndex    (ore scan with miss chance)
 *   - SessionGuard     (player detection)
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

    // 12-state FSM
    public enum State {
        IDLE,
        INITIALIZING,
        SCANNING,
        PRE_AIM,
        AIMING,
        TOOL_SWITCH,
        MINING,
        POST_MINE,
        PRE_WALK,
        WALKING,
        IDLE_PAUSE,
        DESCENDING
    }

    private static final SampleCollector INSTANCE = new SampleCollector();

    // Sub-components
    private final BreakHelper breaker = new BreakHelper();
    private final WalkHelper walker = new WalkHelper();
    private final AimHelper aim = new AimHelper();
    private final IdleBehavior idleBehavior = new IdleBehavior();
    private final FatigueModel fatigue = new FatigueModel();
    private final PathPlanner pathPlanner = new PathPlanner();
    private final ToolManager toolManager = new ToolManager();

    // State
    private State state = State.IDLE;
    private State previousState = State.SCANNING;
    private Direction mainDir;
    private int targetY;
    private boolean reachedTargetY;

    // Timing
    private long sessionTicks;
    private long ticksSinceLastIdle;
    private int cooldown;

    // Block queue
    private final Deque<BlockPos> breakQueue = new ArrayDeque<>();
    private BlockPos currentBreakTarget;

    // Descend
    private int descPhase;

    // Counters
    private final Map<String, Integer> minedCounts = new HashMap<>();
    private int totalBlocksMined;

    private SampleCollector() {}
    public static SampleCollector instance() { return INSTANCE; }
    public State getState() { return state; }
    public WalkHelper getWalkHelper() { return walker; }
    public IdleBehavior getIdleBehavior() { return idleBehavior; }
    public Map<String, Integer> getMinedCounts() { return Collections.unmodifiableMap(minedCounts); }

    // ===================== START / STOP =====================

    public void start(Direction facing) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        this.mainDir = facing;
        this.targetY = computeBestY(prefs);
        this.reachedTargetY = false;
        this.sessionTicks = 0;
        this.ticksSinceLastIdle = 0;
        this.cooldown = 0;
        this.descPhase = 0;
        this.totalBlocksMined = 0;
        this.breakQueue.clear();
        this.minedCounts.clear();
        this.currentBreakTarget = null;

        this.breaker.reset();
        this.walker.stopWalking();
        this.aim.clear();
        this.idleBehavior.stop();
        this.fatigue.reset(0);
        this.pathPlanner.reset();
        this.toolManager.reset();

        state = State.INITIALIZING;
        // Initial look-around before starting work
        cooldown = HumanTiming.scanDuration();
    }

    public void stop() {
        breaker.reset();
        walker.stopWalking();
        aim.clear();
        idleBehavior.stop();
        toolManager.reset();
        breakQueue.clear();
        currentBreakTarget = null;
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
        sessionTicks++;

        // Safety checks (always)
        if (SessionGuard.checkAndDisconnect(mc, prefs.getSurveyRadius())) { stop(); return; }
        if (allGoalsMet(prefs)) { leave(mc); return; }

        // Camera jitter ALWAYS (even during idle/cooldown)
        aim.tick(player, sessionTicks);

        // Fatigue
        float fatigueSpeed = fatigue.getSpeedMultiplier(sessionTicks);

        // Idle trigger check (not during IDLE_PAUSE or INITIALIZING)
        if (state != State.IDLE_PAUSE && state != State.INITIALIZING) {
            float idleMult = fatigue.getIdleChanceMultiplier(sessionTicks);
            if (HumanTiming.shouldTriggerIdle(ticksSinceLastIdle, totalBlocksMined, sessionTicks)) {
                previousState = state;
                state = State.IDLE_PAUSE;
                idleBehavior.start(totalBlocksMined, sessionTicks);
                ticksSinceLastIdle = 0;
                return;
            }
        }
        ticksSinceLastIdle++;

        // Cooldown (universal pause between state transitions)
        if (cooldown > 0) {
            cooldown--;
            walker.wantForward = 0;
            walker.wantStrafe = 0;
            walker.wantJump = false;
            walker.wantSprint = false;
            return;
        }

        // State machine
        switch (state) {
            case INITIALIZING -> tickInitializing(mc, player, world, prefs);
            case SCANNING -> tickScanning(mc, player, world, prefs);
            case PRE_AIM -> tickPreAim(fatigueSpeed);
            case AIMING -> tickAiming(mc, player, world, prefs);
            case TOOL_SWITCH -> tickToolSwitch(player);
            case MINING -> tickMining(mc, player, world, prefs);
            case POST_MINE -> tickPostMine(fatigueSpeed);
            case PRE_WALK -> tickPreWalk(mc, player, world, prefs, fatigueSpeed);
            case WALKING -> tickWalking(mc, player, world, prefs);
            case IDLE_PAUSE -> tickIdlePause(player);
            case DESCENDING -> tickDescending(mc, player, world, prefs, fatigueSpeed);
        }
    }

    // ===================== STATE HANDLERS =====================

    private void tickInitializing(MinecraftClient mc, ClientPlayerEntity player,
                                   ClientWorld world, DisplayPrefs prefs) {
        // Initial look-around: drift camera to get bearings
        if (aim.isIdle()) {
            float yaw = pathPlanner.adjustedYaw(mainDir, sessionTicks);
            aim.face(player, yaw, 4f);
        }

        // After initial scan period, start working
        state = State.SCANNING;
    }

    private void tickScanning(MinecraftClient mc, ClientPlayerEntity player,
                               ClientWorld world, DisplayPrefs prefs) {
        walker.wantForward = 0;
        walker.wantSprint = false;

        // Y-level check
        if (!reachedTargetY) {
            int adjustedY = pathPlanner.adjustedY(targetY, sessionTicks);
            if (Math.abs(player.getBlockPos().getY() - adjustedY) > 3) {
                state = State.DESCENDING;
                descPhase = 0;
                return;
            }
            reachedTargetY = true;
        }

        // Check break queue first
        if (pickFromQueue(player, world)) {
            currentBreakTarget = breaker.getTarget();
            state = State.PRE_AIM;
            cooldown = HumanTiming.fatigued(HumanTiming.aimSettle(), fatigue.getSpeedMultiplier(sessionTicks));
            return;
        }

        // Scan for ore
        BlockPos ore = findBestOre(player, world, prefs);
        if (ore != null) {
            currentBreakTarget = ore;
            breaker.setTarget(ore);
            aim.aimAt(player, ore);
            state = State.PRE_AIM;
            cooldown = HumanTiming.fatigued(HumanTiming.aimSettle(), fatigue.getSpeedMultiplier(sessionTicks));
            return;
        }

        // Queue tunnel blocks
        BlockPos feet = player.getBlockPos();
        BlockPos aFeet = feet.offset(mainDir);
        BlockPos aHead = aFeet.up();

        // Lava check
        if (MaterialIndex.hasAdjacentLava(world, aFeet) || MaterialIndex.hasAdjacentLava(world, aHead)) {
            leave(mc);
            return;
        }

        // Get corridor blocks with imperfection
        List<BlockPos> corridor = GridLayout.corridorSegment(feet, mainDir);
        List<BlockPos> ordered = pathPlanner.shuffleMineOrder(corridor);

        for (BlockPos pos : ordered) {
            if (isMineable(player, world, pos)) {
                breakQueue.add(pos);
            }
        }

        if (pickFromQueue(player, world)) {
            currentBreakTarget = breaker.getTarget();
            state = State.PRE_AIM;
            cooldown = HumanTiming.fatigued(HumanTiming.aimSettle(), fatigue.getSpeedMultiplier(sessionTicks));
            return;
        }

        // Nothing to break -- walk forward
        state = State.PRE_WALK;
        cooldown = HumanTiming.fatigued(HumanTiming.walkStart(), fatigue.getSpeedMultiplier(sessionTicks));
    }

    private void tickPreAim(float fatigueSpeed) {
        // Pre-aim delay already consumed by cooldown
        // Now start aiming
        state = State.AIMING;
    }

    private void tickAiming(MinecraftClient mc, ClientPlayerEntity player,
                             ClientWorld world, DisplayPrefs prefs) {
        // Aim is ticked globally in main tick()
        // Wait until aimed
        if (!aim.isAimed()) return;

        // Check if we need tool switch
        if (!SlotHelper.isHoldingUsablePickaxe(player)) {
            toolManager.requestTool(player);
            state = State.TOOL_SWITCH;
            return;
        }

        state = State.MINING;
    }

    private void tickToolSwitch(ClientPlayerEntity player) {
        if (toolManager.tick(player)) {
            state = State.MINING;
        }
    }

    private void tickMining(MinecraftClient mc, ClientPlayerEntity player,
                             ClientWorld world, DisplayPrefs prefs) {
        if (currentBreakTarget == null) {
            state = State.POST_MINE;
            cooldown = HumanTiming.fatigued(HumanTiming.postMine(), fatigue.getSpeedMultiplier(sessionTicks));
            return;
        }

        // Keep aim locked on target
        if (aim.getState() != AimHelper.AimState.LOCKED && aim.getState() != AimHelper.AimState.SETTLING) {
            aim.aimAt(player, currentBreakTarget);
        }

        breaker.tick(player, world);

        if (breaker.isDone()) {
            trackOre(world, currentBreakTarget);
            totalBlocksMined++;
            breaker.acknowledge();
            currentBreakTarget = null;

            state = State.POST_MINE;
            cooldown = HumanTiming.fatigued(HumanTiming.postMine(), fatigue.getSpeedMultiplier(sessionTicks));
        }
    }

    private void tickPostMine(float fatigueSpeed) {
        // Post-mine delay consumed by cooldown
        // Check for more work
        state = State.SCANNING;
    }

    private void tickPreWalk(MinecraftClient mc, ClientPlayerEntity player,
                              ClientWorld world, DisplayPrefs prefs, float fatigueSpeed) {
        // Determine walk distance
        int blocks = pathPlanner.walkBlocks();

        // Face walking direction with slight drift
        float walkYaw = pathPlanner.adjustedYaw(mainDir, sessionTicks);
        aim.face(player, walkYaw, 4f);

        if (!walker.startWalk(player, world, mainDir, blocks)) {
            // Unsafe ahead
            if (!reachedTargetY || Math.abs(player.getBlockPos().getY() - targetY) > 3) {
                state = State.DESCENDING;
                descPhase = 0;
            } else {
                leave(mc);
            }
            return;
        }

        state = State.WALKING;
    }

    private void tickWalking(MinecraftClient mc, ClientPlayerEntity player,
                              ClientWorld world, DisplayPrefs prefs) {
        boolean done = walker.tick(player, sessionTicks);
        if (done) {
            state = State.SCANNING;
            cooldown = HumanTiming.fatigued(HumanTiming.walkPause(), fatigue.getSpeedMultiplier(sessionTicks));
        }
    }

    private void tickIdlePause(ClientPlayerEntity player) {
        boolean done = idleBehavior.tick(player, aim, walker);
        if (done) {
            // Restore previous state or go to scanning
            state = State.SCANNING;
            cooldown = HumanTiming.gaussianDelay(3, 1);
        }
    }

    private void tickDescending(MinecraftClient mc, ClientPlayerEntity player,
                                 ClientWorld world, DisplayPrefs prefs, float fatigueSpeed) {
        int curY = player.getBlockPos().getY();
        int adjustedY = pathPlanner.adjustedY(targetY, sessionTicks);

        if (Math.abs(curY - adjustedY) <= 3) {
            reachedTargetY = true;
            walker.stopWalking();
            state = State.SCANNING;
            cooldown = HumanTiming.fatigued(HumanTiming.descPause(), fatigueSpeed);
            return;
        }

        boolean down = curY > adjustedY;

        if (descPhase == 0) {
            // Break staircase blocks
            walker.wantForward = 0;
            walker.wantJump = false;

            if (breaker.hasTarget()) {
                aim.aimAt(player, breaker.getTarget());
                breaker.tick(player, world);
                if (breaker.isDone()) {
                    trackOre(world, breaker.getTarget());
                    totalBlocksMined++;
                    breaker.acknowledge();
                    cooldown = HumanTiming.fatigued(HumanTiming.breakToBreak(), fatigueSpeed);
                }
                return;
            }

            if (pickFromQueue(player, world)) return;

            // Queue staircase blocks
            BlockPos feet = player.getBlockPos();
            BlockPos ahead = feet.offset(mainDir);

            List<BlockPos> descBlocks = new ArrayList<>();
            if (down) {
                descBlocks.add(ahead.up());
                descBlocks.add(ahead);
                descBlocks.add(ahead.down());
            } else {
                descBlocks.add(ahead);
                descBlocks.add(ahead.up());
                descBlocks.add(ahead.up(2));
            }

            // Shuffle order for imperfection
            descBlocks = pathPlanner.shuffleMineOrder(descBlocks);

            for (BlockPos pos : descBlocks) {
                if (isMineable(player, world, pos)) {
                    breakQueue.add(pos);
                }
            }

            if (pickFromQueue(player, world)) return;

            // All clear -- walk into gap
            descPhase = 1;
            walker.wantForward = 0;
        }

        if (descPhase == 1) {
            float pitch = down ? 25f : -20f;
            aim.face(player, pathPlanner.adjustedYaw(mainDir, sessionTicks), pitch);

            walker.wantForward = 0.6f; // not full speed for stairs
            walker.wantJump = !down && player.isOnGround();

            int newY = player.getBlockPos().getY();
            boolean yMoved = down ? (newY < curY) : (newY > curY);

            if (yMoved) {
                walker.wantForward = 0;
                walker.wantJump = false;
                descPhase = 0;
                cooldown = HumanTiming.fatigued(HumanTiming.descPause(), fatigueSpeed);
            }
        }
    }

    // ===================== BLOCK QUEUE =====================

    private boolean pickFromQueue(ClientPlayerEntity player, ClientWorld world) {
        while (!breakQueue.isEmpty()) {
            BlockPos next = breakQueue.poll();
            if (isMineable(player, world, next)) {
                breaker.setTarget(next);
                aim.aimAt(player, next);
                currentBreakTarget = next;
                return true;
            }
        }
        return false;
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
}
