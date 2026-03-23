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
 * Orchestrates legitimate-looking automated mining.
 *
 * Rules for anti-cheat safety:
 * - Only break blocks the player can SEE (exposed face, within 4.5 reach)
 * - Never mine through walls (no x-ray behavior)
 * - Smooth camera movement (slow lerp, never snap)
 * - Human-like timing: random pauses, irregular intervals
 * - Only collect ore when no lava threat nearby
 * - Track ore counts, stop when goals reached
 * - Descend to optimal Y before starting horizontal mining
 */
public final class SampleCollector {

    public enum Phase {
        IDLE,
        DESCENDING,
        DIGGING_CORRIDOR,
        DIGGING_BRANCH_LEFT,
        DIGGING_BRANCH_RIGHT,
        MINING_ORE,
        SEALING_LAVA,
        SWAPPING_TOOL,
        RETURNING_TO_CORRIDOR
    }

    private static final SampleCollector INSTANCE = new SampleCollector();
    private static final float CAM_LERP = 0.12f;
    private static final float CAM_LERP_JITTER = 0.06f;

    private Phase phase = Phase.IDLE;
    private Direction mainDir;
    private BlockPos corridorOrigin;
    private int corridorStep;
    private int branchProgress;
    private Direction currentBranchDir;
    private int targetY;

    // Block-break state
    private BlockPos breakTarget;
    private boolean isBreaking;

    // Ore queue (only exposed, reachable ores)
    private final Deque<BlockPos> oreQueue = new ArrayDeque<>();

    // Lava seal queue
    private final Deque<BlockPos> sealQueue = new ArrayDeque<>();

    // Ore counters (mined this session)
    private final Map<String, Integer> minedCounts = new HashMap<>();

    // Human-like timing
    private int waitTicks;
    private int ticksSinceAction;

    // Camera target (we lerp towards this smoothly)
    private float targetYaw, targetPitch;
    private boolean cameraLocked;

    // Tool swap cooldown
    private int swapCooldown;

    // Saved corridor position for returning after branch
    private BlockPos savedCorridorPos;

    private SampleCollector() {}

    public static SampleCollector instance() { return INSTANCE; }

    public Phase getPhase() { return phase; }

    public Map<String, Integer> getMinedCounts() { return Collections.unmodifiableMap(minedCounts); }

    public void start(Direction facing) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        this.mainDir = facing;
        this.corridorStep = 0;
        this.branchProgress = 0;
        this.targetY = MaterialIndex.optimalY(prefs);
        this.oreQueue.clear();
        this.sealQueue.clear();
        this.minedCounts.clear();
        this.breakTarget = null;
        this.isBreaking = false;
        this.cameraLocked = false;
        this.waitTicks = randomDelay(5, 15);
        this.ticksSinceAction = 0;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            int currentY = client.player.getBlockPos().getY();
            this.corridorOrigin = client.player.getBlockPos();
            this.phase = (currentY > targetY + 2) ? Phase.DESCENDING : Phase.DIGGING_CORRIDOR;
        } else {
            this.phase = Phase.DIGGING_CORRIDOR;
        }
    }

    public void stop() {
        phase = Phase.IDLE;
        cancelBreaking();
        oreQueue.clear();
        sealQueue.clear();
        queuedOreTypes.clear();
        cameraLocked = false;
    }

    private void stopAndLeave(MinecraftClient client) {
        stop();
        DisplayPrefs prefs = VToolsMod.getPrefs();
        prefs.setSurveyEnabled(false);
        prefs.save();
        if (prefs.isSurveyAutoLeave()) {
            SessionGuard.disconnect(client);
        }
    }

    /**
     * Called every client tick.
     */
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (!prefs.isSurveyEnabled()) {
            if (phase != Phase.IDLE) stop();
            return;
        }

        if (phase == Phase.IDLE) return;

        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        // Safety: check for nearby players -> disconnect
        if (SessionGuard.checkAndDisconnect(client, prefs.getSurveyRadius())) {
            stop();
            return;
        }

        // Check if all ore goals are met
        if (allGoalsMet(prefs)) {
            stopAndLeave(client);
            return;
        }

        // Smooth camera update (always runs, gradually moves head)
        if (cameraLocked) {
            smoothLook(player);
        }

        // Human-like wait
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        ticksSinceAction++;

        // Random micro-pause every 30-80 ticks (human behavior)
        if (ticksSinceAction > randomDelay(30, 80)) {
            waitTicks = randomDelay(3, 12);
            ticksSinceAction = 0;
            addSubtleHeadDrift(player);
            return;
        }

        // Tool swap phase
        if (phase == Phase.SWAPPING_TOOL) {
            if (swapCooldown > 0) { swapCooldown--; return; }
            if (SlotHelper.pullPickaxeFromInventory(player)) {
                swapCooldown = randomDelay(4, 10);
                waitTicks = randomDelay(3, 8);
                phase = Phase.DIGGING_CORRIDOR;
            } else {
                stopAndLeave(client);
            }
            return;
        }

        // Ensure pickaxe before any breaking (except lava sealing)
        if (phase != Phase.SEALING_LAVA) {
            if (!ensurePickaxe(player)) return;
        }

        switch (phase) {
            case DESCENDING -> tickDescend(client, player, world);
            case DIGGING_CORRIDOR -> tickCorridor(client, player, world, prefs);
            case DIGGING_BRANCH_LEFT, DIGGING_BRANCH_RIGHT -> tickBranch(client, player, world, prefs);
            case MINING_ORE -> tickOre(client, player, world, prefs);
            case SEALING_LAVA -> tickSeal(client, player, world);
            case RETURNING_TO_CORRIDOR -> tickReturnToCorridor(client, player, world, prefs);
            default -> {}
        }
    }

    // --- Descending to target Y ---

    private void tickDescend(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        int currentY = player.getBlockPos().getY();

        if (currentY <= targetY + 2) {
            corridorOrigin = player.getBlockPos();
            phase = Phase.DIGGING_CORRIDOR;
            waitTicks = randomDelay(3, 8);
            return;
        }

        // Dig staircase down: break block at feet-1 ahead, then feet ahead
        BlockPos feet = player.getBlockPos();
        BlockPos below = feet.down().offset(mainDir);
        BlockPos belowHead = feet.offset(mainDir);

        // Check lava below before descending
        if (MaterialIndex.hasAdjacentLava(world, below) || MaterialIndex.hasAdjacentLava(world, belowHead)) {
            List<BlockPos> exposures = MaterialIndex.findLavaExposures(world, below, 1);
            if (!exposures.isEmpty()) {
                sealQueue.addAll(exposures);
                phase = Phase.SEALING_LAVA;
                return;
            }
            // Lava detected but can't seal -- stop safely
            stop();
            return;
        }

        // Break blocks for staircase
        BlockPos target = null;
        if (MaterialIndex.isBreakable(world.getBlockState(below))) {
            target = below;
        } else if (MaterialIndex.isBreakable(world.getBlockState(belowHead))) {
            target = belowHead;
        }

        if (target != null) {
            setLookTarget(player, target);
            breakBlock(client, player, target);
        } else {
            // Clear -- move down+forward
            nudgeToward(player, Vec3d.ofCenter(below));
            waitTicks = randomDelay(2, 5);
        }
    }

    // --- Corridor digging ---

    private void tickCorridor(MinecraftClient client, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        BlockPos feet = player.getBlockPos();

        // First: check for exposed ores visible in the tunnel walls
        Set<Block> targets = MaterialIndex.buildTargetSet(prefs);
        if (!targets.isEmpty() && oreQueue.isEmpty()) {
            List<BlockPos> visible = MaterialIndex.scanExposedTargets(world, player, targets, prefs);
            for (BlockPos ore : visible) {
                String cat = MaterialIndex.oreCategory(world.getBlockState(ore).getBlock());
                queueOre(ore, cat);
            }
            if (!oreQueue.isEmpty()) {
                phase = Phase.MINING_ORE;
                return;
            }
        }

        // Dig the corridor: 2 blocks ahead (feet + head level)
        List<BlockPos> toBreak = GridLayout.corridorSegment(feet, mainDir);

        // Lava safety check on blocks ahead
        for (BlockPos pos : toBreak) {
            if (MaterialIndex.hasAdjacentLava(world, pos)) {
                List<BlockPos> exposures = MaterialIndex.findLavaExposures(world, pos, 2);
                if (!exposures.isEmpty()) {
                    sealQueue.addAll(exposures);
                    phase = Phase.SEALING_LAVA;
                    return;
                }
            }
        }

        // Break first solid block ahead
        BlockPos target = findFirstSolid(world, toBreak);
        if (target != null) {
            setLookTarget(player, target);
            breakBlock(client, player, target);
            return;
        }

        // Corridor clear -- walk forward
        nudgeToward(player, Vec3d.ofCenter(feet.offset(mainDir)));
        corridorStep++;
        waitTicks = randomDelay(2, 5);

        // Branch every N steps
        if (GridLayout.isBranchStep(corridorStep)) {
            savedCorridorPos = player.getBlockPos();
            branchProgress = 0;
            currentBranchDir = GridLayout.leftOf(mainDir);
            phase = Phase.DIGGING_BRANCH_LEFT;
        }
    }

    // --- Branch digging ---

    private void tickBranch(MinecraftClient client, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        BlockPos feet = player.getBlockPos();

        // Check for exposed ores
        Set<Block> targets = MaterialIndex.buildTargetSet(prefs);
        if (!targets.isEmpty() && oreQueue.isEmpty()) {
            List<BlockPos> visible = MaterialIndex.scanExposedTargets(world, player, targets, prefs);
            for (BlockPos ore : visible) {
                String cat = MaterialIndex.oreCategory(world.getBlockState(ore).getBlock());
                queueOre(ore, cat);
            }
            if (!oreQueue.isEmpty()) {
                phase = Phase.MINING_ORE;
                return;
            }
        }

        List<BlockPos> toBreak = GridLayout.corridorSegment(feet, currentBranchDir);

        // Lava check
        for (BlockPos pos : toBreak) {
            if (MaterialIndex.hasAdjacentLava(world, pos)) {
                List<BlockPos> exposures = MaterialIndex.findLavaExposures(world, pos, 2);
                if (!exposures.isEmpty()) {
                    sealQueue.addAll(exposures);
                    phase = Phase.SEALING_LAVA;
                    return;
                }
            }
        }

        BlockPos target = findFirstSolid(world, toBreak);
        if (target != null) {
            setLookTarget(player, target);
            breakBlock(client, player, target);
            return;
        }

        nudgeToward(player, Vec3d.ofCenter(feet.offset(currentBranchDir)));
        branchProgress++;
        waitTicks = randomDelay(2, 5);

        if (branchProgress >= GridLayout.getBranchLength()) {
            if (phase == Phase.DIGGING_BRANCH_LEFT) {
                // Return to corridor, then go right
                branchProgress = 0;
                currentBranchDir = GridLayout.rightOf(mainDir);
                phase = Phase.RETURNING_TO_CORRIDOR;
                waitTicks = randomDelay(5, 15);
            } else {
                phase = Phase.RETURNING_TO_CORRIDOR;
                waitTicks = randomDelay(3, 10);
            }
        }
    }

    // --- Return to corridor after branch ---

    private void tickReturnToCorridor(MinecraftClient client, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        if (savedCorridorPos == null) {
            phase = Phase.DIGGING_CORRIDOR;
            return;
        }

        double dist = player.getPos().squaredDistanceTo(Vec3d.ofCenter(savedCorridorPos));
        if (dist < 2.0) {
            if (currentBranchDir == GridLayout.rightOf(mainDir)) {
                // Already did right branch, back to corridor
                phase = Phase.DIGGING_CORRIDOR;
            } else {
                // Switch to right branch
                currentBranchDir = GridLayout.rightOf(mainDir);
                branchProgress = 0;
                phase = Phase.DIGGING_BRANCH_RIGHT;
            }
            waitTicks = randomDelay(3, 8);
            return;
        }

        // Walk back toward saved corridor position
        nudgeToward(player, Vec3d.ofCenter(savedCorridorPos));
        setLookTarget(player, savedCorridorPos);
        waitTicks = randomDelay(1, 3);
    }

    // --- Ore mining (only exposed, visible ores) ---

    private void tickOre(MinecraftClient client, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        // Remove already-mined or now-hidden ores
        while (!oreQueue.isEmpty()) {
            BlockPos peek = oreQueue.peek();
            BlockState state = world.getBlockState(peek);
            if (state.isAir() || !MaterialIndex.hasExposedFace(world, peek)) {
                oreQueue.poll();
                continue;
            }
            break;
        }

        if (oreQueue.isEmpty()) {
            phase = Phase.DIGGING_CORRIDOR;
            waitTicks = randomDelay(2, 6);
            return;
        }

        BlockPos orePos = oreQueue.peek();

        // Double-check: within reach?
        double dist = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(orePos));
        if (dist > 4.5 * 4.5) {
            // Too far -- skip it, a real player wouldn't reach
            oreQueue.poll();
            return;
        }

        // Lava safety: skip ore if lava nearby
        if (MaterialIndex.hasAdjacentLava(world, orePos)) {
            oreQueue.poll();
            waitTicks = randomDelay(1, 3);
            return;
        }

        setLookTarget(player, orePos);
        breakBlock(client, player, orePos);

        // Check if it just broke
        if (world.getBlockState(orePos).isAir()) {
            countMinedOre(orePos);
            oreQueue.poll();
            waitTicks = randomDelay(2, 5);

            if (allGoalsMet(prefs)) {
                stopAndLeave(client);
            }
        }
    }

    // --- Lava sealing ---

    private void tickSeal(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        if (sealQueue.isEmpty()) {
            phase = Phase.DIGGING_CORRIDOR;
            waitTicks = randomDelay(3, 8);
            return;
        }

        BlockPos sealPos = sealQueue.peek();
        if (!world.getBlockState(sealPos).isAir()) {
            sealQueue.poll();
            return;
        }

        // Within reach?
        double dist = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(sealPos));
        if (dist > 4.5 * 4.5) {
            sealQueue.poll();
            return;
        }

        if (!SlotHelper.selectSealingBlock(player)) {
            sealQueue.clear();
            phase = Phase.DIGGING_CORRIDOR;
            return;
        }

        setLookTarget(player, sealPos);
        placeBlock(client, player, sealPos);
        sealQueue.poll();
        waitTicks = randomDelay(3, 7);
    }

    // --- Goal tracking ---

    private final Map<BlockPos, String> queuedOreTypes = new HashMap<>();

    /**
     * Record ore type when adding to queue, and count when mined.
     */
    public void queueOre(BlockPos pos, String category) {
        if (!oreQueue.contains(pos)) {
            oreQueue.add(pos);
            queuedOreTypes.put(pos, category);
        }
    }

    private void countMinedOre(BlockPos pos) {
        String cat = queuedOreTypes.remove(pos);
        if (cat != null) {
            minedCounts.merge(cat, 1, Integer::sum);
        }
    }

    private boolean allGoalsMet(DisplayPrefs prefs) {
        // 0 means unlimited -- that ore has no goal
        if (prefs.isOreDiamond() && prefs.getCntDiamond() > 0) {
            if (minedCounts.getOrDefault("diamond", 0) < prefs.getCntDiamond()) return false;
        }
        if (prefs.isOreGold() && prefs.getCntGold() > 0) {
            if (minedCounts.getOrDefault("gold", 0) < prefs.getCntGold()) return false;
        }
        if (prefs.isOreIron() && prefs.getCntIron() > 0) {
            if (minedCounts.getOrDefault("iron", 0) < prefs.getCntIron()) return false;
        }
        if (prefs.isOreCopper() && prefs.getCntCopper() > 0) {
            if (minedCounts.getOrDefault("copper", 0) < prefs.getCntCopper()) return false;
        }
        if (prefs.isOreRedstone() && prefs.getCntRedstone() > 0) {
            if (minedCounts.getOrDefault("redstone", 0) < prefs.getCntRedstone()) return false;
        }
        if (prefs.isOreLapis() && prefs.getCntLapis() > 0) {
            if (minedCounts.getOrDefault("lapis", 0) < prefs.getCntLapis()) return false;
        }
        if (prefs.isOreEmerald() && prefs.getCntEmerald() > 0) {
            if (minedCounts.getOrDefault("emerald", 0) < prefs.getCntEmerald()) return false;
        }
        if (prefs.isOreCoal() && prefs.getCntCoal() > 0) {
            if (minedCounts.getOrDefault("coal", 0) < prefs.getCntCoal()) return false;
        }

        // All enabled ores with count>0 have met their goals
        // If no ore has count>0 set, goals are never "met" (mine forever)
        boolean anyGoalSet = false;
        if (prefs.isOreDiamond() && prefs.getCntDiamond() > 0) anyGoalSet = true;
        if (prefs.isOreGold() && prefs.getCntGold() > 0) anyGoalSet = true;
        if (prefs.isOreIron() && prefs.getCntIron() > 0) anyGoalSet = true;
        if (prefs.isOreCopper() && prefs.getCntCopper() > 0) anyGoalSet = true;
        if (prefs.isOreRedstone() && prefs.getCntRedstone() > 0) anyGoalSet = true;
        if (prefs.isOreLapis() && prefs.getCntLapis() > 0) anyGoalSet = true;
        if (prefs.isOreEmerald() && prefs.getCntEmerald() > 0) anyGoalSet = true;
        if (prefs.isOreCoal() && prefs.getCntCoal() > 0) anyGoalSet = true;

        return anyGoalSet;
    }

    // --- Tool management ---

    private boolean ensurePickaxe(ClientPlayerEntity player) {
        if (SlotHelper.isHoldingUsablePickaxe(player)) return true;

        BlockState stoneState = net.minecraft.block.Blocks.STONE.getDefaultState();
        if (SlotHelper.selectBestPickaxe(player, stoneState)) {
            waitTicks = randomDelay(3, 7);
            return true;
        }

        phase = Phase.SWAPPING_TOOL;
        swapCooldown = randomDelay(8, 20);
        return false;
    }

    // --- Block interaction ---

    private void breakBlock(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
        if (breakTarget != null && breakTarget.equals(pos) && isBreaking) {
            client.interactionManager.updateBlockBreakingProgress(pos, getBlockFace(player, pos));
            return;
        }

        cancelBreaking();
        breakTarget = pos;
        isBreaking = true;
        client.interactionManager.attackBlock(pos, getBlockFace(player, pos));
    }

    private void cancelBreaking() {
        if (isBreaking) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.interactionManager != null) {
                client.interactionManager.cancelBlockBreaking();
            }
            isBreaking = false;
            breakTarget = null;
        }
    }

    private void placeBlock(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
        Direction face = getBlockFace(player, pos);
        BlockPos neighbor = pos.offset(face.getOpposite());
        Vec3d hitVec = Vec3d.ofCenter(pos);
        BlockHitResult hit = new BlockHitResult(hitVec, face, neighbor, false);
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
    }

    // --- Camera: smooth, human-like ---

    private void setLookTarget(ClientPlayerEntity player, BlockPos target) {
        Vec3d eyes = player.getEyePos();
        Vec3d targetCenter = Vec3d.ofCenter(target);
        double dx = targetCenter.x - eyes.x;
        double dy = targetCenter.y - eyes.y;
        double dz = targetCenter.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        targetYaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
        targetPitch = (float)(Math.toDegrees(-Math.atan2(dy, dist)));

        // Subtle jitter so it doesn't look robotic
        targetYaw += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.8f;
        targetPitch += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.5f;

        cameraLocked = true;
    }

    private void smoothLook(ClientPlayerEntity player) {
        float lf = CAM_LERP + ThreadLocalRandom.current().nextFloat() * CAM_LERP_JITTER;
        player.setYaw(lerpAngle(player.getYaw(), targetYaw, lf));
        player.setPitch(lerp(player.getPitch(), targetPitch, lf));

        // Unlock when close enough
        float yawDiff = Math.abs(wrapAngle(player.getYaw() - targetYaw));
        float pitchDiff = Math.abs(player.getPitch() - targetPitch);
        if (yawDiff < 1.5f && pitchDiff < 1.0f) {
            cameraLocked = false;
        }
    }

    private void addSubtleHeadDrift(ClientPlayerEntity player) {
        // Very small random drift, like a person glancing around
        player.setYaw(player.getYaw() + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 1.5f);
        player.setPitch(player.getPitch() + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.8f);
    }

    // --- Movement: gentle nudge, like walking ---

    private void nudgeToward(ClientPlayerEntity player, Vec3d target) {
        Vec3d pos = player.getPos();
        Vec3d diff = target.subtract(pos);
        double len = diff.horizontalLength();
        if (len < 0.1) return;

        Vec3d norm = new Vec3d(diff.x / len, 0, diff.z / len);
        double speed = 0.12 + ThreadLocalRandom.current().nextDouble() * 0.04;
        player.setVelocity(norm.x * speed, player.getVelocity().y, norm.z * speed);
    }

    // --- Utility ---

    private static Direction getBlockFace(ClientPlayerEntity player, BlockPos pos) {
        Vec3d eyes = player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d diff = eyes.subtract(center);
        Direction closest = Direction.UP;
        double maxDot = Double.NEGATIVE_INFINITY;
        for (Direction dir : Direction.values()) {
            Vec3d normal = Vec3d.of(dir.getVector());
            double dot = diff.dotProduct(normal);
            if (dot > maxDot) { maxDot = dot; closest = dir; }
        }
        return closest;
    }

    private BlockPos findFirstSolid(ClientWorld world, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (MaterialIndex.isBreakable(world.getBlockState(pos))) return pos;
        }
        return null;
    }

    private static int randomDelay(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float diff = wrapAngle(b - a);
        return a + diff * t;
    }

    private static float wrapAngle(float angle) {
        angle %= 360f;
        if (angle > 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }
}
