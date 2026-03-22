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
 * Orchestrates the automated survey (mining) process.
 * Neutral naming: "SampleCollector" reads as a data-sampling utility.
 *
 * State machine with human-like delays:
 * IDLE -> DIGGING_CORRIDOR -> DIGGING_BRANCH -> MINING_ORE -> SEALING_LAVA -> PAUSED
 */
public final class SampleCollector {

    public enum Phase {
        IDLE,
        NAVIGATING,
        DIGGING_CORRIDOR,
        DIGGING_BRANCH_LEFT,
        DIGGING_BRANCH_RIGHT,
        MINING_ORE,
        SEALING_LAVA,
        SWAPPING_TOOL,
        PAUSED
    }

    private static final SampleCollector INSTANCE = new SampleCollector();

    private Phase phase = Phase.IDLE;
    private Direction mainDir;
    private int corridorStep;
    private int branchProgress;
    private Direction currentBranchDir;

    // Block-break state
    private BlockPos breakTarget;
    private int breakTicks;
    private boolean isBreaking;

    // Ore vein queue
    private final Deque<BlockPos> oreQueue = new ArrayDeque<>();

    // Lava seal queue
    private final Deque<BlockPos> sealQueue = new ArrayDeque<>();

    // Human-like timing
    private int waitTicks;
    private int ticksSinceAction;
    private long lastActionTime;

    // Tool swap cooldown
    private int swapCooldown;

    private SampleCollector() {}

    public static SampleCollector instance() { return INSTANCE; }

    public Phase getPhase() { return phase; }

    public void start(Direction facing) {
        this.mainDir = facing;
        this.corridorStep = 0;
        this.branchProgress = 0;
        this.phase = Phase.DIGGING_CORRIDOR;
        this.oreQueue.clear();
        this.sealQueue.clear();
        this.breakTarget = null;
        this.isBreaking = false;
        this.waitTicks = randomDelay(3, 8);
        this.ticksSinceAction = 0;
        this.lastActionTime = System.currentTimeMillis();
    }

    public void stop() {
        phase = Phase.IDLE;
        cancelBreaking();
        oreQueue.clear();
        sealQueue.clear();
    }

    /**
     * Stop mining and disconnect from server if auto-leave is enabled.
     */
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
     * Called every client tick from SurveyTickMixin.
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

        // Safety: check for nearby players
        if (SessionGuard.checkAndDisconnect(client, prefs.getSurveyRadius())) {
            stop();
            return;
        }

        // Human-like wait between actions
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        ticksSinceAction++;

        // Random micro-pauses to simulate human behavior
        if (ticksSinceAction > randomDelay(40, 120)) {
            waitTicks = randomDelay(5, 20);
            ticksSinceAction = 0;
            addHeadJitter(player);
            return;
        }

        // Tool management
        if (phase == Phase.SWAPPING_TOOL) {
            if (swapCooldown > 0) { swapCooldown--; return; }
            if (SlotHelper.pullPickaxeFromInventory(player)) {
                swapCooldown = randomDelay(4, 10);
                waitTicks = randomDelay(2, 6);
                phase = Phase.DIGGING_CORRIDOR;
            } else {
                // No pickaxe anywhere -- done
                stopAndLeave(client);
            }
            return;
        }

        // Check pickaxe before any breaking
        if (phase != Phase.SEALING_LAVA) {
            if (!ensurePickaxe(player, world)) return;
        }

        switch (phase) {
            case DIGGING_CORRIDOR -> tickCorridor(client, player, world, prefs);
            case DIGGING_BRANCH_LEFT, DIGGING_BRANCH_RIGHT -> tickBranch(client, player, world, prefs);
            case MINING_ORE -> tickOre(client, player, world);
            case SEALING_LAVA -> tickSeal(client, player, world);
            default -> {}
        }
    }

    // --- Corridor digging ---

    private void tickCorridor(MinecraftClient client, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        BlockPos feet = player.getBlockPos();
        List<BlockPos> toBreak = GridLayout.corridorSegment(feet, mainDir);

        // Check for lava ahead before breaking
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

        // Break blocks in front
        BlockPos target = findFirstSolid(world, toBreak);
        if (target != null) {
            lookAt(player, target);
            breakBlock(client, player, target, world);
            return;
        }

        // Corridor clear — move forward
        moveForward(player, mainDir);
        corridorStep++;
        waitTicks = randomDelay(1, 4);

        // Scan for ores around current position
        Set<Block> targets = MaterialIndex.buildTargetSet(prefs);
        if (!targets.isEmpty()) {
            List<BlockPos> ores = MaterialIndex.scanForTargets(world, feet, 4, targets);
            for (BlockPos ore : ores) {
                if (!oreQueue.contains(ore)) oreQueue.add(ore);
            }
        }

        // Mine any found ores
        if (!oreQueue.isEmpty()) {
            phase = Phase.MINING_ORE;
            return;
        }

        // Check if we should branch
        if (GridLayout.isBranchStep(corridorStep)) {
            branchProgress = 0;
            currentBranchDir = GridLayout.leftOf(mainDir);
            phase = Phase.DIGGING_BRANCH_LEFT;
        }
    }

    // --- Branch digging ---

    private void tickBranch(MinecraftClient client, ClientPlayerEntity player, ClientWorld world, DisplayPrefs prefs) {
        BlockPos feet = player.getBlockPos();
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
            lookAt(player, target);
            breakBlock(client, player, target, world);
            return;
        }

        moveForward(player, currentBranchDir);
        branchProgress++;
        waitTicks = randomDelay(1, 4);

        // Scan for ores
        Set<Block> targets = MaterialIndex.buildTargetSet(prefs);
        if (!targets.isEmpty()) {
            List<BlockPos> ores = MaterialIndex.scanForTargets(world, feet, 4, targets);
            for (BlockPos ore : ores) {
                if (!oreQueue.contains(ore)) oreQueue.add(ore);
            }
        }

        if (!oreQueue.isEmpty()) {
            phase = Phase.MINING_ORE;
            return;
        }

        if (branchProgress >= GridLayout.getBranchLength()) {
            if (phase == Phase.DIGGING_BRANCH_LEFT) {
                // Walk back to corridor, then do right branch
                branchProgress = 0;
                currentBranchDir = GridLayout.rightOf(mainDir);
                phase = Phase.DIGGING_BRANCH_RIGHT;
                waitTicks = randomDelay(5, 15);
            } else {
                // Done with both branches, back to corridor
                phase = Phase.DIGGING_CORRIDOR;
                waitTicks = randomDelay(3, 10);
            }
        }
    }

    // --- Ore mining ---

    private void tickOre(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        if (oreQueue.isEmpty()) {
            phase = Phase.DIGGING_CORRIDOR;
            waitTicks = randomDelay(2, 6);
            return;
        }

        BlockPos orePos = oreQueue.peek();
        BlockState state = world.getBlockState(orePos);
        if (state.isAir()) {
            oreQueue.poll();
            return;
        }

        // Check lava around ore
        if (MaterialIndex.hasAdjacentLava(world, orePos)) {
            List<BlockPos> exposures = MaterialIndex.findLavaExposures(world, orePos, 1);
            if (!exposures.isEmpty()) {
                sealQueue.addAll(exposures);
                phase = Phase.SEALING_LAVA;
                return;
            }
        }

        lookAt(player, orePos);
        breakBlock(client, player, orePos, world);

        if (world.getBlockState(orePos).isAir()) {
            oreQueue.poll();
            waitTicks = randomDelay(1, 3);
        }
    }

    // --- Lava sealing ---

    private void tickSeal(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        if (sealQueue.isEmpty()) {
            phase = Phase.DIGGING_CORRIDOR;
            waitTicks = randomDelay(2, 5);
            return;
        }

        BlockPos sealPos = sealQueue.peek();
        if (!world.getBlockState(sealPos).isAir()) {
            sealQueue.poll();
            return;
        }

        if (!SlotHelper.selectSealingBlock(player)) {
            // No blocks to seal with -- skip and continue cautiously
            sealQueue.clear();
            phase = Phase.DIGGING_CORRIDOR;
            return;
        }

        lookAt(player, sealPos);
        placeBlock(client, player, sealPos);
        sealQueue.poll();
        waitTicks = randomDelay(2, 5);
    }

    // --- Tool management ---

    private boolean ensurePickaxe(ClientPlayerEntity player, ClientWorld world) {
        if (SlotHelper.isHoldingUsablePickaxe(player)) return true;

        BlockState stoneState = net.minecraft.block.Blocks.STONE.getDefaultState();
        if (SlotHelper.selectBestPickaxe(player, stoneState)) {
            waitTicks = randomDelay(2, 5);
            return true;
        }

        // No pickaxe in hotbar, try inventory
        phase = Phase.SWAPPING_TOOL;
        swapCooldown = randomDelay(5, 15);
        return false;
    }

    // --- Block interaction ---

    private void breakBlock(MinecraftClient client, ClientPlayerEntity player, BlockPos pos, ClientWorld world) {
        if (breakTarget != null && breakTarget.equals(pos) && isBreaking) {
            // Continue breaking
            client.interactionManager.updateBlockBreakingProgress(pos, getBlockFace(player, pos));
            breakTicks++;
            return;
        }

        // Start new break
        cancelBreaking();
        breakTarget = pos;
        isBreaking = true;
        breakTicks = 0;
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

    // --- Movement & look ---

    private void moveForward(ClientPlayerEntity player, Direction dir) {
        Vec3d dirVec = Vec3d.of(dir.getVector());
        // Set motion towards direction with slight randomization
        double speed = 0.15 + ThreadLocalRandom.current().nextDouble() * 0.05;
        player.setVelocity(dirVec.x * speed, player.getVelocity().y, dirVec.z * speed);
    }

    private void lookAt(ClientPlayerEntity player, BlockPos target) {
        Vec3d eyes = player.getEyePos();
        Vec3d targetCenter = Vec3d.ofCenter(target);
        double dx = targetCenter.x - eyes.x;
        double dy = targetCenter.y - eyes.y;
        double dz = targetCenter.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float)(Math.toDegrees(-Math.atan2(dy, dist)));

        // Add human-like jitter
        yaw += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 1.5f;
        pitch += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.8f;

        // Smooth interpolation (don't snap instantly)
        float lerpFactor = 0.4f + ThreadLocalRandom.current().nextFloat() * 0.3f;
        player.setYaw(lerpAngle(player.getYaw(), yaw, lerpFactor));
        player.setPitch(lerp(player.getPitch(), pitch, lerpFactor));
    }

    private void addHeadJitter(ClientPlayerEntity player) {
        player.setYaw(player.getYaw() + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 3f);
        player.setPitch(player.getPitch() + (ThreadLocalRandom.current().nextFloat() - 0.5f) * 2f);
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
        float diff = b - a;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return a + diff * t;
    }
}
