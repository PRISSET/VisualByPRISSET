package com.prisset.vtools.survey;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v6: Position-relative tunnel miner with simulated keyboard movement.
 *
 * Core loop: MINE blocks ahead -> WALK forward -> scan walls -> repeat.
 * All coordinates relative to player's current position, never absolute offsets.
 * Movement via simulated W-key (movementForward), not setVelocity.
 * Fast camera: instant for forward blocks, quick lerp for side ore.
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

    public enum State { IDLE, MINING, WALKING, DESCENDING }

    private static final SampleCollector INSTANCE = new SampleCollector();

    private State state = State.IDLE;
    private Direction mainDir;
    private int targetY;
    private boolean reachedTargetY;

    // Breaking
    private BlockPos breakTarget;
    private Direction breakFace;
    private boolean breakStarted;

    // After current break finishes, break these next (e.g. head block after feet block)
    private final Deque<BlockPos> pendingBreaks = new ArrayDeque<>();

    // Ore detour: after mining ore, resume tunnel from this state
    private BlockPos oreTarget;

    // Camera
    private float wantYaw, wantPitch;
    private boolean fastAim; // true = instant snap, false = smooth lerp

    // Walking
    private BlockPos walkGoal;
    private int walkTicks;
    private int walkMaxTicks;

    // Pause between actions (human-like)
    private int cooldown;

    // Descending: blocks mined in current staircase step
    private int descStepPhase;

    // Counters
    private final Map<String, Integer> minedCounts = new HashMap<>();

    // Scan throttle: only scan every N blocks mined in tunnel
    private int blocksSinceLastScan;
    private static final int SCAN_INTERVAL = 2;

    // Movement key simulation state
    private boolean wasPressingForward;
    private boolean wasPressingJump;

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
        this.breakTarget = null;
        this.breakStarted = false;
        this.oreTarget = null;
        this.pendingBreaks.clear();
        this.minedCounts.clear();
        this.cooldown = rnd(10, 25);
        this.blocksSinceLastScan = 0;
        this.descStepPhase = 0;
        this.walkGoal = null;
        this.wasPressingForward = false;
        this.wasPressingJump = false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            this.wantYaw = directionYaw(mainDir);
            this.wantPitch = 0f;
            this.fastAim = true;
            this.state = State.MINING;
        }
    }

    public void stop() {
        releaseKeys();
        abortBreak();
        state = State.IDLE;
        breakTarget = null;
        oreTarget = null;
        pendingBreaks.clear();
        walkGoal = null;
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

        // Camera every tick
        applyCameraStep(player);

        // Cooldown between actions
        if (cooldown > 0) {
            cooldown--;
            // Idle head sway while waiting
            if (cooldown % 7 == 0) driftHead();
            return;
        }

        switch (state) {
            case MINING -> tickMining(mc, player, world, prefs);
            case WALKING -> tickWalking(mc, player, world, prefs);
            case DESCENDING -> tickDescending(mc, player, world, prefs);
            default -> {}
        }
    }

    // ========== MINING ==========

    private void tickMining(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        releaseKeys();

        // Check Y-level first
        if (!reachedTargetY) {
            int curY = player.getBlockPos().getY();
            if (Math.abs(curY - targetY) > 3) {
                state = State.DESCENDING;
                descStepPhase = 0;
                return;
            }
            reachedTargetY = true;
        }

        // If we have an active break target, keep breaking it
        if (breakTarget != null) {
            if (!continueBreaking(mc, player, world, prefs)) return;
            // Block broken or invalid — fall through to pick next
        }

        // Pick next break target
        if (!pickNextTarget(mc, player, world, prefs)) {
            // Nothing to break ahead — time to walk forward
            transitionToWalking(player);
        }
    }

    /**
     * Continue breaking current target. Returns true when done (block broken or invalid).
     */
    private boolean continueBreaking(MinecraftClient mc, ClientPlayerEntity player,
                                      ClientWorld world, DisplayPrefs prefs) {
        BlockState bs = world.getBlockState(breakTarget);

        // Block already gone
        if (bs.isAir() || bs.getHardness(world, breakTarget) < 0) {
            onBlockBroken(world);
            return true;
        }

        // Reach check every tick
        double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(breakTarget));
        if (dist > 4.5) {
            abortBreak();
            return true;
        }

        // Wait for camera to be close enough before starting to break
        float yawErr = Math.abs(wrapAngle(player.getYaw() - wantYaw));
        float pitchErr = Math.abs(player.getPitch() - wantPitch);
        if (yawErr > 4f || pitchErr > 4f) {
            // Still turning — don't start breaking yet
            return false;
        }

        // Ensure pickaxe
        if (!ensurePickaxe(player)) return false;

        if (!breakStarted) {
            mc.interactionManager.attackBlock(breakTarget, breakFace);
            breakStarted = true;
        } else {
            mc.interactionManager.updateBlockBreakingProgress(breakTarget, breakFace);
        }

        // Check if broken after this tick's progress
        if (world.getBlockState(breakTarget).isAir()) {
            onBlockBroken(world);
            return true;
        }

        return false;
    }

    private void onBlockBroken(ClientWorld world) {
        if (breakTarget != null) {
            String cat = MaterialIndex.oreCategory(world.getBlockState(breakTarget).getBlock());
            if (!"unknown".equals(cat)) {
                minedCounts.merge(cat, 1, Integer::sum);
            }
        }
        breakTarget = null;
        breakStarted = false;
        // Tiny human pause between blocks
        cooldown = rnd(1, 2);
    }

    /**
     * Find next block to break. Returns true if a target was set.
     * Priority: pending queue -> ore detour -> tunnel blocks ahead.
     */
    private boolean pickNextTarget(MinecraftClient mc, ClientPlayerEntity player,
                                    ClientWorld world, DisplayPrefs prefs) {
        // 1. Pending breaks from multi-block sequences (feet+head)
        while (!pendingBreaks.isEmpty()) {
            BlockPos next = pendingBreaks.poll();
            if (canBreak(player, world, next)) {
                startBreak(player, next, false);
                return true;
            }
        }

        // 2. Check for ore detour
        BlockPos ore = findBestOre(player, world, prefs);
        if (ore != null) {
            startBreak(player, ore, false);
            return true;
        }

        // 3. Tunnel: break blocks directly ahead (feet + head level)
        BlockPos feet = player.getBlockPos();
        BlockPos aheadFeet = feet.offset(mainDir);
        BlockPos aheadHead = aheadFeet.up();

        boolean anyQueued = false;

        // Lava safety
        if (MaterialIndex.hasAdjacentLava(world, aheadFeet)
                || MaterialIndex.hasAdjacentLava(world, aheadHead)) {
            // Skip this column, walk sideways or just walk forward into air
            return false;
        }

        // Queue feet and head if solid
        if (canBreak(player, world, aheadHead)) {
            startBreak(player, aheadHead, true);
            if (canBreak(player, world, aheadFeet)) {
                pendingBreaks.add(aheadFeet);
            }
            anyQueued = true;
        } else if (canBreak(player, world, aheadFeet)) {
            startBreak(player, aheadFeet, true);
            anyQueued = true;
        }

        // Also check floor: if block below ahead is air, we'd fall. Check and handle.
        BlockPos floorAhead = aheadFeet.down();
        BlockState floorState = world.getBlockState(floorAhead);
        if (floorState.isAir() && !anyQueued) {
            // Gap ahead — can still walk (will fall 1 block, acceptable)
        }

        return anyQueued;
    }

    private boolean canBreak(ClientPlayerEntity player, ClientWorld world, BlockPos pos) {
        BlockState bs = world.getBlockState(pos);
        if (bs.isAir()) return false;
        if (bs.getHardness(world, pos) < 0) return false;
        if (!MINEABLE.contains(bs.getBlock())) return false;
        double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
        return dist <= 4.5;
    }

    private void startBreak(ClientPlayerEntity player, BlockPos pos, boolean isTunnel) {
        breakTarget = pos;
        breakFace = bestFace(player, pos);
        breakStarted = false;

        // Camera: if block is directly ahead in tunnel direction, barely need to turn
        Vec3d eyes = player.getEyePos();
        Vec3d tc = Vec3d.ofCenter(pos);
        double dx = tc.x - eyes.x, dy = tc.y - eyes.y, dz = tc.z - eyes.z;
        double h = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dy, h));

        // Add tiny human imprecision
        targetYaw += jitter(0.4f);
        targetPitch += jitter(0.25f);

        wantYaw = targetYaw;
        wantPitch = targetPitch;

        // Fast aim for tunnel blocks (already mostly facing right way)
        float yawDelta = Math.abs(wrapAngle(player.getYaw() - targetYaw));
        fastAim = isTunnel && yawDelta < 30f;
    }

    // ========== WALKING ==========

    private void transitionToWalking(ClientPlayerEntity player) {
        state = State.WALKING;
        walkGoal = player.getBlockPos().offset(mainDir);
        walkTicks = 0;
        walkMaxTicks = 15; // safety: if not arrived in 15 ticks, re-evaluate

        // Face tunnel direction
        wantYaw = directionYaw(mainDir);
        wantPitch = 5f + jitter(3f); // slightly looking down, natural
        fastAim = true;

        blocksSinceLastScan++;
    }

    private void tickWalking(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        BlockPos currentPos = player.getBlockPos();
        walkTicks++;

        // Arrived at goal or passed it?
        boolean arrived = false;
        if (walkGoal != null) {
            double distSq = player.getPos().squaredDistanceTo(
                    walkGoal.getX() + 0.5, player.getPos().y, walkGoal.getZ() + 0.5);
            arrived = distSq < 0.15;

            // Also check if we've PASSED the goal (moved beyond it in mainDir)
            if (!arrived) {
                Vec3d toGoal = new Vec3d(
                        walkGoal.getX() + 0.5 - player.getPos().x,
                        0,
                        walkGoal.getZ() + 0.5 - player.getPos().z);
                Vec3d dirVec = Vec3d.of(mainDir.getVector());
                double dot = toGoal.dotProduct(dirVec);
                if (dot < -0.3) arrived = true; // passed it
            }
        }

        if (arrived || walkTicks > walkMaxTicks) {
            releaseKeys();

            // Scan for ore in walls
            if (blocksSinceLastScan >= SCAN_INTERVAL) {
                blocksSinceLastScan = 0;
                // Brief pause to "look around" like a real player
                cooldown = rnd(2, 5);
            } else {
                cooldown = rnd(0, 1);
            }

            state = State.MINING;
            return;
        }

        // Simulate W key
        pressForward(mc, true);

        // Check if stuck (no horizontal movement)
        if (walkTicks > 5) {
            Vec3d vel = player.getVelocity();
            double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (horizSpeed < 0.01 && player.isOnGround()) {
                // Stuck — maybe need to jump over something
                pressJump(mc, true);
            } else {
                pressJump(mc, false);
            }
        }
    }

    // ========== DESCENDING ==========

    private void tickDescending(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        int curY = player.getBlockPos().getY();

        // Reached target?
        if (Math.abs(curY - targetY) <= 3) {
            reachedTargetY = true;
            releaseKeys();
            state = State.MINING;
            cooldown = rnd(3, 8);
            descStepPhase = 0;
            return;
        }

        boolean goingDown = curY > targetY;
        BlockPos feet = player.getBlockPos();

        // Staircase: each step = break blocks, then walk into the gap
        // Phase 0: break blocks for this step
        // Phase 1: walk into position

        if (descStepPhase == 0) {
            releaseKeys();

            // If we have an active break target, keep going
            if (breakTarget != null) {
                if (!continueBreaking(mc, player, world, prefs)) return;
            }

            // Pick next block from pending
            while (!pendingBreaks.isEmpty()) {
                BlockPos next = pendingBreaks.poll();
                if (canBreak(player, world, next)) {
                    startBreak(player, next, false);
                    return;
                }
            }

            // Queue staircase blocks for this step
            if (goingDown) {
                BlockPos ahead = feet.offset(mainDir);
                // Clear: head level, feet level, one below
                queueIfBreakable(player, world, ahead.up());
                queueIfBreakable(player, world, ahead);
                queueIfBreakable(player, world, ahead.down());
            } else {
                BlockPos ahead = feet.offset(mainDir);
                // Clear: feet, head, one above head
                queueIfBreakable(player, world, ahead);
                queueIfBreakable(player, world, ahead.up());
                queueIfBreakable(player, world, ahead.up(2));
            }

            if (!pendingBreaks.isEmpty()) {
                BlockPos next = pendingBreaks.poll();
                startBreak(player, next, false);
                return;
            }

            // All blocks cleared for this step — transition to walking phase
            descStepPhase = 1;
            walkTicks = 0;
        }

        if (descStepPhase == 1) {
            // Walk forward into the gap
            wantYaw = directionYaw(mainDir);
            wantPitch = goingDown ? 25f : -20f;
            fastAim = true;

            pressForward(mc, true);
            walkTicks++;

            // Jump if going up
            if (!goingDown && player.isOnGround()) {
                pressJump(mc, true);
            } else if (goingDown) {
                pressJump(mc, false);
            }

            // Check if Y changed (stepped down/up)
            int newY = player.getBlockPos().getY();
            boolean yChanged = goingDown ? (newY < curY) : (newY > curY);

            // Also check if we've moved horizontally at all
            if (yChanged || walkTicks > 20) {
                releaseKeys();
                descStepPhase = 0;
                cooldown = rnd(1, 3);
            }

            // Anti-stuck: if not moving for too long, try jumping
            if (walkTicks > 8) {
                Vec3d vel = player.getVelocity();
                double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                if (horizSpeed < 0.01 && player.isOnGround()) {
                    pressJump(mc, true);
                }
            }
        }
    }

    private void queueIfBreakable(ClientPlayerEntity player, ClientWorld world, BlockPos pos) {
        if (canBreak(player, world, pos)) {
            pendingBreaks.add(pos);
        }
    }

    // ========== ORE SCANNING ==========

    private BlockPos findBestOre(ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        Set<Block> targets = MaterialIndex.buildTargetSet(prefs);
        if (targets.isEmpty()) return null;

        List<BlockPos> found = MaterialIndex.scanExposedTargets(world, player, targets, prefs);
        for (BlockPos pos : found) {
            if (MaterialIndex.hasAdjacentLava(world, pos)) continue;
            double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
            if (dist > 4.5) continue;

            // Check if we already met the goal for this ore type
            String cat = MaterialIndex.oreCategory(world.getBlockState(pos).getBlock());
            if (isOreGoalMet(cat, prefs)) continue;

            return pos;
        }
        return null;
    }

    private boolean isOreGoalMet(String cat, DisplayPrefs prefs) {
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
        if (goal == 0) return false; // 0 = unlimited
        return minedCounts.getOrDefault(cat, 0) >= goal;
    }

    // ========== KEYBOARD SIMULATION ==========

    private void pressForward(MinecraftClient mc, boolean press) {
        KeyBinding fwd = mc.options.forwardKey;
        if (press && !wasPressingForward) {
            KeyBinding.setKeyPressed(fwd.getDefaultKey(), true);
            wasPressingForward = true;
        } else if (!press && wasPressingForward) {
            KeyBinding.setKeyPressed(fwd.getDefaultKey(), false);
            wasPressingForward = false;
        }
    }

    private void pressJump(MinecraftClient mc, boolean press) {
        KeyBinding jump = mc.options.jumpKey;
        if (press && !wasPressingJump) {
            KeyBinding.setKeyPressed(jump.getDefaultKey(), true);
            wasPressingJump = true;
        } else if (!press && wasPressingJump) {
            KeyBinding.setKeyPressed(jump.getDefaultKey(), false);
            wasPressingJump = false;
        }
    }

    private void releaseKeys() {
        if (wasPressingForward || wasPressingJump) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (wasPressingForward) {
                KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
                wasPressingForward = false;
            }
            if (wasPressingJump) {
                KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
                wasPressingJump = false;
            }
        }
    }

    // ========== CAMERA ==========

    private void applyCameraStep(ClientPlayerEntity player) {
        if (fastAim) {
            // Instant snap (± tiny jitter for human feel)
            float yawErr = Math.abs(wrapAngle(player.getYaw() - wantYaw));
            float pitchErr = Math.abs(player.getPitch() - wantPitch);
            if (yawErr > 0.5f || pitchErr > 0.5f) {
                // Fast but not literally instant — 2 ticks max
                float speed = 0.65f + ThreadLocalRandom.current().nextFloat() * 0.15f;
                player.setYaw(lerpAngle(player.getYaw(), wantYaw, speed));
                player.setPitch(lerp(player.getPitch(), wantPitch, speed));
            }
        } else {
            // Smooth lerp for side ore
            float speed = 0.40f + ThreadLocalRandom.current().nextFloat() * 0.10f;
            player.setYaw(lerpAngle(player.getYaw(), wantYaw, speed));
            player.setPitch(lerp(player.getPitch(), wantPitch, speed));
        }
    }

    private void driftHead() {
        wantYaw += jitter(1.2f);
        wantPitch += jitter(0.5f);
    }

    // ========== TOOLS ==========

    private boolean ensurePickaxe(ClientPlayerEntity player) {
        if (SlotHelper.isHoldingUsablePickaxe(player)) return true;
        if (SlotHelper.selectBestPickaxe(player, Blocks.STONE.getDefaultState())) return true;
        if (SlotHelper.pullPickaxeFromInventory(player)) {
            cooldown = rnd(3, 6);
            return false;
        }
        // No pickaxe at all — stop
        MinecraftClient mc = MinecraftClient.getInstance();
        stopAndLeave(mc);
        return false;
    }

    // ========== BREAK HELPERS ==========

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

    // ========== MATH ==========

    private static float directionYaw(Direction dir) {
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
