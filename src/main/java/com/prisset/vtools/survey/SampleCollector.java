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
 * v7: Reliable tunnel miner.
 *
 * Movement via MoveRequest read by MovementInputMixin (injected into KeyboardInput.tick RETURN).
 * This guarantees input is applied AFTER MC reads physical keys, so it cannot be reset.
 *
 * Floor-safe: checks ground ahead before walking. Will not walk into voids.
 * Position-relative: all targets computed from player's current position.
 * Fast camera: near-instant for forward blocks, quick lerp for ore.
 */
public final class SampleCollector {

    // Movement request — read by MovementInputMixin each tick
    public static final class MoveRequest {
        public boolean forward, jump, sneak;
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

    public enum State { IDLE, BREAKING, WALKING, DESCENDING }

    private static final SampleCollector INSTANCE = new SampleCollector();

    // State
    private State state = State.IDLE;
    private Direction mainDir;
    private int targetY;
    private boolean reachedTargetY;

    // Movement request (consumed by mixin)
    private volatile MoveRequest moveReq;

    // Breaking
    private BlockPos breakTarget;
    private Direction breakFace;
    private boolean breakStarted;
    private final Deque<BlockPos> pendingBreaks = new ArrayDeque<>();

    // Camera target
    private float wantYaw, wantPitch;

    // Walking
    private BlockPos walkGoal;
    private int walkTicks;
    private Vec3d lastWalkPos;

    // Descend stepping
    private int descPhase; // 0=break, 1=walk

    // Human-like pause
    private int cooldown;

    // Scan throttle
    private int stepsSinceScan;

    // Counters
    private final Map<String, Integer> minedCounts = new HashMap<>();

    private SampleCollector() {}
    public static SampleCollector instance() { return INSTANCE; }
    public State getState() { return state; }
    public MoveRequest getMoveRequest() { return moveReq; }
    public Map<String, Integer> getMinedCounts() { return Collections.unmodifiableMap(minedCounts); }

    // ===================== START / STOP =====================

    public void start(Direction facing) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        this.mainDir = facing;
        this.targetY = computeBestY(prefs);
        this.reachedTargetY = false;
        this.breakTarget = null;
        this.breakStarted = false;
        this.pendingBreaks.clear();
        this.minedCounts.clear();
        this.cooldown = rnd(8, 20);
        this.stepsSinceScan = 0;
        this.descPhase = 0;
        this.walkGoal = null;
        this.moveReq = null;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            this.wantYaw = dirYaw(mainDir);
            this.wantPitch = 0f;
            this.state = State.BREAKING;
        }
    }

    public void stop() {
        moveReq = null;
        abortBreak();
        pendingBreaks.clear();
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

        // Camera every tick
        smoothCamera(player);

        if (cooldown > 0) {
            cooldown--;
            moveReq = null;
            if (cooldown % 6 == 0) driftHead();
            return;
        }

        switch (state) {
            case BREAKING -> tickBreaking(mc, player, world, prefs);
            case WALKING -> tickWalking(mc, player, world, prefs);
            case DESCENDING -> tickDescending(mc, player, world, prefs);
            default -> {}
        }
    }

    // ===================== BREAKING STATE =====================
    // Stands still, breaks blocks ahead and nearby ore.
    // When nothing left to break ahead, transitions to WALKING.

    private void tickBreaking(MinecraftClient mc, ClientPlayerEntity player,
                               ClientWorld world, DisplayPrefs prefs) {
        moveReq = null; // no movement while breaking

        // Y-level check
        if (!reachedTargetY) {
            int curY = player.getBlockPos().getY();
            if (Math.abs(curY - targetY) > 3) {
                state = State.DESCENDING;
                descPhase = 0;
                return;
            }
            reachedTargetY = true;
        }

        // Active break? Continue it.
        if (breakTarget != null) {
            if (!tickContinueBreak(mc, player, world)) return; // still breaking
            // done — fall through to pick next
        }

        // Try pending queue
        if (pickFromPending(player, world)) return;

        // Scan for ore in reach
        BlockPos ore = findBestOre(player, world, prefs);
        if (ore != null) {
            beginBreak(player, ore, false);
            return;
        }

        // Queue tunnel blocks ahead (feet + head)
        BlockPos feet = player.getBlockPos();
        BlockPos aFeet = feet.offset(mainDir);
        BlockPos aHead = aFeet.up();

        // Lava safety
        if (MaterialIndex.hasAdjacentLava(world, aFeet)
                || MaterialIndex.hasAdjacentLava(world, aHead)) {
            // Cannot dig forward — stop
            leave(mc);
            return;
        }

        // Queue head first (break top-down so gravel/sand doesn't refill)
        boolean queued = false;
        if (isBreakable(player, world, aHead)) { pendingBreaks.add(aHead); queued = true; }
        if (isBreakable(player, world, aFeet)) { pendingBreaks.add(aFeet); queued = true; }

        if (queued && pickFromPending(player, world)) return;

        // Nothing to break — path is clear, walk forward
        goWalk(player);
    }

    /**
     * Continues breaking the current target. Returns true when done/aborted.
     */
    private boolean tickContinueBreak(MinecraftClient mc, ClientPlayerEntity player,
                                       ClientWorld world) {
        BlockState bs = world.getBlockState(breakTarget);
        if (bs.isAir() || bs.getHardness(world, breakTarget) < 0) {
            finishBreak(world);
            return true;
        }

        double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(breakTarget));
        if (dist > 4.5) {
            abortBreak();
            return true;
        }

        // Camera must be close before we start swinging
        float ye = Math.abs(wrapAngle(player.getYaw() - wantYaw));
        float pe = Math.abs(player.getPitch() - wantPitch);
        if (ye > 5f || pe > 5f) return false; // still turning

        if (!ensureTool(player)) return false;

        if (!breakStarted) {
            mc.interactionManager.attackBlock(breakTarget, breakFace);
            breakStarted = true;
        } else {
            mc.interactionManager.updateBlockBreakingProgress(breakTarget, breakFace);
        }

        if (world.getBlockState(breakTarget).isAir()) {
            finishBreak(world);
            return true;
        }
        return false;
    }

    private void finishBreak(ClientWorld world) {
        if (breakTarget != null) {
            String cat = MaterialIndex.oreCategory(world.getBlockState(breakTarget).getBlock());
            if (!"unknown".equals(cat)) {
                minedCounts.merge(cat, 1, Integer::sum);
            }
        }
        breakTarget = null;
        breakStarted = false;
        cooldown = rnd(1, 2);
    }

    private boolean pickFromPending(ClientPlayerEntity player, ClientWorld world) {
        while (!pendingBreaks.isEmpty()) {
            BlockPos next = pendingBreaks.poll();
            if (isBreakable(player, world, next)) {
                beginBreak(player, next, true);
                return true;
            }
        }
        return false;
    }

    private void beginBreak(ClientPlayerEntity player, BlockPos pos, boolean isTunnel) {
        breakTarget = pos;
        breakFace = facingFrom(player, pos);
        breakStarted = false;
        aimAt(player, pos, isTunnel);
    }

    // ===================== WALKING STATE =====================
    // Bot walks exactly 1 block forward, then goes back to BREAKING.

    private void goWalk(ClientPlayerEntity player) {
        BlockPos feet = player.getBlockPos();
        BlockPos ahead = feet.offset(mainDir);

        // FLOOR CHECK: is there ground to stand on?
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            BlockPos floor = ahead.down();
            BlockState floorState = mc.world.getBlockState(floor);
            // If floor is air, check one more below (2-block fall is ok in MC)
            if (floorState.isAir()) {
                BlockPos floor2 = floor.down();
                BlockState floor2State = mc.world.getBlockState(floor2);
                if (floor2State.isAir()) {
                    // 3+ block drop — too dangerous, don't walk
                    // Try to bridge: place block under
                    // For now, just stop and mine downward to descend safely
                    state = State.DESCENDING;
                    descPhase = 0;
                    return;
                }
                // 1-2 block drop is fine, MC handles it
            }

            // Lava under floor
            if (MaterialIndex.hasAdjacentLava(mc.world, ahead)
                    || MaterialIndex.hasAdjacentLava(mc.world, floor)) {
                leave(mc);
                return;
            }
        }

        state = State.WALKING;
        walkGoal = ahead;
        walkTicks = 0;
        lastWalkPos = player.getPos();
        stepsSinceScan++;

        wantYaw = dirYaw(mainDir);
        wantPitch = 4f + jitter(3f);
    }

    private void tickWalking(MinecraftClient mc, ClientPlayerEntity player,
                              ClientWorld world, DisplayPrefs prefs) {
        walkTicks++;

        // Check if arrived
        double dx = (walkGoal.getX() + 0.5) - player.getPos().x;
        double dz = (walkGoal.getZ() + 0.5) - player.getPos().z;
        double hDistSq = dx * dx + dz * dz;
        boolean arrived = hDistSq < 0.12;

        // Check if passed goal
        if (!arrived) {
            Vec3d toGoal = new Vec3d(dx, 0, dz);
            Vec3d dir = Vec3d.of(mainDir.getVector());
            if (toGoal.dotProduct(dir) < -0.2) arrived = true;
        }

        if (arrived || walkTicks > 25) {
            moveReq = null;
            state = State.BREAKING;

            if (stepsSinceScan >= 2) {
                stepsSinceScan = 0;
                cooldown = rnd(2, 5); // pause to "look around"
            } else {
                cooldown = rnd(0, 1);
            }
            return;
        }

        // Request forward movement
        MoveRequest req = new MoveRequest();
        req.forward = true;

        // Anti-stuck: if barely moved in last 4 ticks, jump
        if (walkTicks > 4 && walkTicks % 4 == 0) {
            Vec3d cur = player.getPos();
            double movedSq = cur.squaredDistanceTo(lastWalkPos);
            if (movedSq < 0.01) {
                req.jump = true;
            }
            lastWalkPos = cur;
        }

        moveReq = req;
    }

    // ===================== DESCENDING STATE =====================
    // Digs a staircase down (or up) toward targetY.
    // Phase 0: break blocks for one stair step.
    // Phase 1: walk into the gap.

    private void tickDescending(MinecraftClient mc, ClientPlayerEntity player,
                                 ClientWorld world, DisplayPrefs prefs) {
        int curY = player.getBlockPos().getY();

        if (Math.abs(curY - targetY) <= 3) {
            reachedTargetY = true;
            moveReq = null;
            state = State.BREAKING;
            cooldown = rnd(3, 8);
            return;
        }

        boolean down = curY > targetY;
        BlockPos feet = player.getBlockPos();

        if (descPhase == 0) {
            moveReq = null;

            // Continue active break
            if (breakTarget != null) {
                if (!tickContinueBreak(mc, player, world)) return;
            }

            // Pick from pending
            if (pickFromPending(player, world)) return;

            // Queue staircase blocks
            BlockPos ahead = feet.offset(mainDir);
            if (down) {
                // Going down: clear ahead head, feet, and floor-ahead
                queueBreakable(player, world, ahead.up());
                queueBreakable(player, world, ahead);
                queueBreakable(player, world, ahead.down());
            } else {
                // Going up: clear ahead feet, head, and above-head
                queueBreakable(player, world, ahead);
                queueBreakable(player, world, ahead.up());
                queueBreakable(player, world, ahead.up(2));
            }

            if (pickFromPending(player, world)) return;

            // All clear — walk into gap
            descPhase = 1;
            walkTicks = 0;
            lastWalkPos = player.getPos();
        }

        if (descPhase == 1) {
            wantYaw = dirYaw(mainDir);
            wantPitch = down ? 30f : -25f;

            MoveRequest req = new MoveRequest();
            req.forward = true;
            if (!down && player.isOnGround()) {
                req.jump = true;
            }
            moveReq = req;

            walkTicks++;
            int newY = player.getBlockPos().getY();
            boolean yMoved = down ? (newY < curY) : (newY > curY);

            if (yMoved || walkTicks > 25) {
                moveReq = null;
                descPhase = 0;
                cooldown = rnd(1, 3);
            }

            // Anti-stuck
            if (walkTicks > 6 && walkTicks % 4 == 0) {
                Vec3d cur = player.getPos();
                double movedSq = cur.squaredDistanceTo(lastWalkPos);
                if (movedSq < 0.01 && player.isOnGround()) {
                    req.jump = true;
                    moveReq = req;
                }
                lastWalkPos = cur;
            }
        }
    }

    private void queueBreakable(ClientPlayerEntity player, ClientWorld world, BlockPos pos) {
        if (isBreakable(player, world, pos)) {
            pendingBreaks.add(pos);
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

    // ===================== BLOCK HELPERS =====================

    private boolean isBreakable(ClientPlayerEntity player, ClientWorld world, BlockPos pos) {
        BlockState bs = world.getBlockState(pos);
        if (bs.isAir()) return false;
        if (bs.getHardness(world, pos) < 0) return false;
        if (!MINEABLE.contains(bs.getBlock())) return false;
        return player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) <= 4.5;
    }

    private void abortBreak() {
        if (breakStarted) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        }
        breakTarget = null;
        breakStarted = false;
    }

    private static Direction facingFrom(ClientPlayerEntity player, BlockPos pos) {
        Vec3d diff = player.getEyePos().subtract(Vec3d.ofCenter(pos));
        Direction best = Direction.UP;
        double maxDot = Double.NEGATIVE_INFINITY;
        for (Direction d : Direction.values()) {
            double dot = diff.dotProduct(Vec3d.of(d.getVector()));
            if (dot > maxDot) { maxDot = dot; best = d; }
        }
        return best;
    }

    // ===================== CAMERA =====================

    private void aimAt(ClientPlayerEntity player, BlockPos target, boolean isTunnel) {
        Vec3d eyes = player.getEyePos();
        Vec3d tc = Vec3d.ofCenter(target);
        double dx = tc.x - eyes.x, dy = tc.y - eyes.y, dz = tc.z - eyes.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + jitter(0.3f);
        wantPitch = (float) Math.toDegrees(-Math.atan2(dy, h)) + jitter(0.2f);
    }

    private void smoothCamera(ClientPlayerEntity player) {
        float ye = Math.abs(wrapAngle(player.getYaw() - wantYaw));
        float pe = Math.abs(player.getPitch() - wantPitch);

        // Speed based on angular distance: big turn = fast, small turn = precise
        float speed;
        if (ye < 10f && pe < 10f) {
            speed = 0.55f; // close — snap quickly
        } else if (ye < 30f) {
            speed = 0.45f; // medium turn
        } else {
            speed = 0.35f; // big turn — still fast but not instant
        }
        speed += jitter(0.06f);

        player.setYaw(lerpAngle(player.getYaw(), wantYaw, speed));
        player.setPitch(lerp(player.getPitch(), wantPitch, speed));
    }

    private void driftHead() {
        wantYaw += jitter(1.0f);
        wantPitch += jitter(0.4f);
    }

    // ===================== TOOLS =====================

    private boolean ensureTool(ClientPlayerEntity player) {
        if (SlotHelper.isHoldingUsablePickaxe(player)) return true;
        if (SlotHelper.selectBestPickaxe(player, Blocks.STONE.getDefaultState())) return true;
        if (SlotHelper.pullPickaxeFromInventory(player)) {
            cooldown = rnd(3, 6);
            return false;
        }
        leave(MinecraftClient.getInstance());
        return false;
    }

    // ===================== GOALS =====================

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
        String bestOre = null;
        int bestW = -1;
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

    private static float jitter(float range) {
        return (ThreadLocalRandom.current().nextFloat() - 0.5f) * range;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

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
