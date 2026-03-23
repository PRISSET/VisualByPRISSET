package com.prisset.vtools.blueprint;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Human-like builder bot. Places blocks only where the player is looking.
 *
 * Key behaviors:
 * - Layer-by-layer (Y=0 fully complete before Y=1)
 * - Picks nearest unplaced block in current layer
 * - Walks to block, aims at the neighbor face, waits for crosshairTarget to match
 * - Places using real crosshairTarget (what the player actually sees)
 * - Always sneaks during placement (prevents opening containers)
 * - Aware of existing world state (skips already-correct blocks, detects wrong blocks)
 * - Post-placement adjustment for repeater delay, comparator mode
 */
public final class BuilderBot {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final BuilderBot INSTANCE = new BuilderBot();

    private static final double PLACE_REACH = 4.5;
    private static final double COMFORTABLE_DIST = 3.0;
    private static final int WALK_TIMEOUT = 200;
    private static final int AIM_TIMEOUT = 40; // max ticks to aim before giving up

    public enum State {
        IDLE, PLANNING, PICK, EQUIP, WALKING, AIM, PLACE, ADJUST, COOLDOWN, DONE, PAUSED
    }

    private State state = State.IDLE;

    private List<List<BuildQueue.PlaceEntry>> layers;
    private int layerIdx;

    private Set<BlockPos> skippedThisPass;
    private int passRetryTicks;

    private int placedCount;
    private int totalCount;
    private int cooldownTicks;
    private int blocksSinceBreak;

    private BuildQueue.PlaceEntry currentEntry;
    private Direction currentFace;
    private int aimTicks;
    private int walkTicks;
    private int adjustClicks;
    private int adjustCooldown;
    private int stallCounter;

    private BuilderBot() {}
    public static BuilderBot instance() { return INSTANCE; }

    public State getState() { return state; }
    public int getPlacedCount() { return placedCount; }
    public int getTotalCount() { return totalCount; }
    public float getProgress() { return totalCount > 0 ? (float) placedCount / totalCount : 0; }

    // -- Control --

    public void start() {
        if (!BlueprintPlacer.instance().isReadyToPlace()) return;
        state = State.PLANNING;
        placedCount = 0;
        blocksSinceBreak = 0;
        layerIdx = 0;
        stallCounter = 0;
        passRetryTicks = 0;
        skippedThisPass = new HashSet<>();
        LOG.info("Builder started");
    }

    public void stop() {
        state = State.IDLE;
        layers = null;
        currentEntry = null;
        currentFace = null;
        skippedThisPass = null;
        releaseKeys();
        LOG.info("Builder stopped ({} placed)", placedCount);
    }

    public void pause() {
        if (state != State.IDLE && state != State.DONE) {
            state = State.PAUSED;
            releaseKeys();
        }
    }

    public void resume() {
        if (state == State.PAUSED) state = State.PICK;
    }

    public boolean isRunning() {
        return state != State.IDLE && state != State.DONE && state != State.PAUSED;
    }

    // -- Tick --

    public void tick() {
        if (state == State.IDLE || state == State.DONE || state == State.PAUSED) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        switch (state) {
            case PLANNING -> tickPlanning(mc);
            case PICK     -> tickPick(mc);
            case EQUIP    -> tickEquip(mc);
            case WALKING  -> tickWalking(mc);
            case AIM      -> tickAim(mc);
            case PLACE    -> tickPlace(mc);
            case ADJUST   -> tickAdjust(mc);
            case COOLDOWN -> tickCooldown();
        }
    }

    // ==========================================================
    // PLANNING
    // ==========================================================

    private void tickPlanning(MinecraftClient mc) {
        layers = BuildQueue.generateLayers(mc.world, mc.player);
        layerIdx = 0;
        stallCounter = 0;
        passRetryTicks = 0;
        if (skippedThisPass == null) skippedThisPass = new HashSet<>();
        skippedThisPass.clear();

        totalCount = 0;
        for (var layer : layers) totalCount += layer.size();

        if (layers.isEmpty()) {
            state = State.DONE;
            LOG.info("Build complete! {} placed", placedCount);
            return;
        }

        LOG.info("Queue: {} blocks, {} layers", totalCount, layers.size());
        state = State.PICK;
    }

    // ==========================================================
    // PICK: nearest unplaced block in current layer
    // ==========================================================

    private void tickPick(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;

        while (layerIdx < layers.size()) {
            List<BuildQueue.PlaceEntry> layer = layers.get(layerIdx);

            // Remove blocks that are correctly placed in world
            layer.removeIf(e -> {
                String actual = SchematicData.encodeState(world.getBlockState(e.worldPos));
                return e.blockState.equals(actual);
            });

            if (layer.isEmpty()) {
                layerIdx++;
                stallCounter = 0;
                skippedThisPass.clear();
                continue;
            }

            // Find nearest unskipped block
            BuildQueue.PlaceEntry best = null;
            double bestDist = Double.MAX_VALUE;
            Vec3d playerPos = player.getPos();

            for (BuildQueue.PlaceEntry entry : layer) {
                if (skippedThisPass.contains(entry.worldPos)) continue;

                double dist = playerPos.squaredDistanceTo(Vec3d.ofCenter(entry.worldPos));
                if (dist < bestDist) {
                    bestDist = dist;
                    best = entry;
                }
            }

            if (best != null) {
                currentEntry = best;
                currentFace = null;
                stallCounter = 0;
                state = State.EQUIP;
                return;
            }

            // All blocks skipped this pass — retry
            stallCounter++;
            if (stallCounter > 3) {
                skippedThisPass.clear();
                stallCounter = 0;
                passRetryTicks++;
                if (passRetryTicks > 30) {
                    // Stuck too long, full re-plan
                    state = State.PLANNING;
                    return;
                }
            }
            return; // stay in PICK
        }

        state = State.PLANNING;
    }

    // ==========================================================
    // EQUIP: validate target, get block in hand, check distance
    // ==========================================================

    private void tickEquip(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        BlockPos target = currentEntry.worldPos;

        // Already correctly placed?
        String actual = SchematicData.encodeState(world.getBlockState(target));
        if (currentEntry.blockState.equals(actual)) {
            state = State.PICK;
            return;
        }

        // Target has a WRONG block (not air, not correct) — skip it
        // (we don't break blocks, only place)
        BlockState worldState = world.getBlockState(target);
        if (!worldState.isAir() && !currentEntry.blockState.equals(SchematicData.encodeState(worldState))) {
            skipCurrent();
            return;
        }

        // Player occupying this position
        if (isPlayerOccupying(player, target)) {
            skipCurrent();
            return;
        }

        // No support
        if (!hasSupport(world, target)) {
            skipCurrent();
            return;
        }

        // Equip the right block
        String blockId = currentEntry.getBlockId();
        if (!InventoryHelper.isHolding(player, blockId)) {
            boolean found = InventoryHelper.equipBlock(player, blockId);
            if (!found) {
                skipCurrent();
                return;
            }
            cooldownTicks = 1;
            state = State.COOLDOWN;
            return;
        }

        // Distance check
        double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(target));
        if (dist > PLACE_REACH) {
            walkTicks = 0;
            state = State.WALKING;
            return;
        }

        // Find a face to click against
        currentFace = findBestPlaceFace(world, player, target);
        if (currentFace == null) {
            skipCurrent();
            return;
        }

        aimTicks = 0;
        state = State.AIM;
    }

    private void skipCurrent() {
        skippedThisPass.add(currentEntry.worldPos);
        state = State.PICK;
    }

    // ==========================================================
    // WALKING
    // ==========================================================

    private void tickWalking(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        BlockPos target = currentEntry.worldPos;
        walkTicks++;

        if (walkTicks > WALK_TIMEOUT) {
            releaseKeys();
            skipCurrent();
            return;
        }

        double distToTarget = player.getEyePos().distanceTo(Vec3d.ofCenter(target));
        if (distToTarget <= COMFORTABLE_DIST) {
            releaseKeys();
            state = State.EQUIP;
            return;
        }

        Vec3d walkGoal = findStandPosition(player, target);
        Vec3d pPos = player.getPos();
        double dx = walkGoal.x - pPos.x;
        double dz = walkGoal.z - pPos.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        if (distXZ <= 0.8) {
            releaseKeys();
            state = State.EQUIP;
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.setYaw(lerpAngle(player.getYaw(), yaw, 0.35f));

        mc.options.forwardKey.setPressed(true);
        mc.options.sprintKey.setPressed(distXZ > 6.0);

        if (player.isOnGround() && walkTicks > 4) {
            Vec3d vel = player.getVelocity();
            if (vel.x * vel.x + vel.z * vel.z < 0.001) {
                mc.options.jumpKey.setPressed(true);
            } else {
                mc.options.jumpKey.setPressed(false);
            }
        }
    }

    // ==========================================================
    // AIM: look at the neighbor face, wait for crosshairTarget to match
    // ==========================================================

    private void tickAim(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        BlockPos target = currentEntry.worldPos;

        // Re-verify face is still valid
        Direction face = findBestPlaceFace(mc.world, player, target);
        if (face == null) {
            skipCurrent();
            return;
        }
        currentFace = face;

        aimTicks++;
        if (aimTicks > AIM_TIMEOUT) {
            skipCurrent();
            return;
        }

        // Calculate where to look: the center of the face on the neighbor block
        BlockPos neighbor = target.offset(face);
        Vec3d hitPoint = getFaceHitPoint(target, face);
        Vec3d eyes = player.getEyePos();

        // For directional blocks, we need specific player yaw so the block
        // gets the right facing. But we still need to look at the hitPoint.
        float targetYaw;
        Map<String, String> props = parseProperties(currentEntry.blockState);
        String facingProp = props.get("facing");
        boolean needSpecificYaw = facingProp != null && isDirectionalPlacement(currentEntry.blockState);

        if (needSpecificYaw) {
            Direction desired = directionFromName(facingProp);
            if (desired != null) {
                targetYaw = directionToYaw(desired.getOpposite());
            } else {
                targetYaw = aimYawToward(player, hitPoint);
            }
        } else {
            targetYaw = aimYawToward(player, hitPoint);
        }

        double dy = hitPoint.y - eyes.y;
        double dxH = hitPoint.x - eyes.x;
        double dzH = hitPoint.z - eyes.z;
        double distXZ = Math.sqrt(dxH * dxH + dzH * dzH);
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));

        // Smoothly rotate toward target
        player.setYaw(lerpAngle(player.getYaw(), targetYaw, 0.4f));
        player.setPitch(MathHelper.clamp(
            player.getPitch() + (targetPitch - player.getPitch()) * 0.4f, -90f, 90f));

        // Snap on tick 3+
        if (aimTicks >= 3) {
            player.setYaw(targetYaw);
            player.setPitch(MathHelper.clamp(targetPitch, -90f, 90f));
        }

        // Check if crosshairTarget is looking at the correct neighbor block
        if (aimTicks >= 2 && mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult crosshairHit = (BlockHitResult) mc.crosshairTarget;
            BlockPos lookingAt = crosshairHit.getBlockPos();

            // We need to be looking at the NEIGHBOR block (the solid block we're clicking)
            if (lookingAt.equals(neighbor)) {
                state = State.PLACE;
                return;
            }
        }

        // For directional blocks, if we reached aim timeout / 2 and still haven't matched,
        // accept any close crosshair hit and use synthetic fallback
        if (aimTicks >= AIM_TIMEOUT / 2) {
            state = State.PLACE;
        }
    }

    // ==========================================================
    // PLACE: use real crosshairTarget when possible
    // ==========================================================

    private void tickPlace(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        BlockPos target = currentEntry.worldPos;

        // Final safety checks
        if (isPlayerOccupying(player, target)) {
            skipCurrent();
            return;
        }

        // Already placed?
        String actual = SchematicData.encodeState(world.getBlockState(target));
        if (currentEntry.blockState.equals(actual)) {
            state = State.PICK;
            return;
        }

        // Target has wrong block
        if (!world.getBlockState(target).isAir()) {
            skipCurrent();
            return;
        }

        // Try to use real crosshairTarget
        BlockHitResult hit = null;

        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult crosshairHit = (BlockHitResult) mc.crosshairTarget;
            BlockPos lookingAt = crosshairHit.getBlockPos();

            // Verify we're looking at a valid neighbor of target
            Direction placeFace = getPlaceFace(target, lookingAt);
            if (placeFace != null) {
                // Verify the resulting placement position is our target
                // (clicking this face of this neighbor should place at target)
                BlockPos placementPos = lookingAt.offset(crosshairHit.getSide());
                if (placementPos.equals(target)) {
                    hit = crosshairHit;
                }
            }
        }

        // Fallback: synthetic hit if crosshair didn't match perfectly
        if (hit == null) {
            Direction face = currentFace;
            if (face == null) face = findBestPlaceFace(world, player, target);
            if (face == null) {
                skipCurrent();
                return;
            }

            BlockPos neighbor = target.offset(face);
            Direction clickFace = face.getOpposite();
            Vec3d hitPos = getFaceHitPoint(target, face);

            double reachDist = player.getEyePos().distanceTo(hitPos);
            if (reachDist > PLACE_REACH) {
                walkTicks = 0;
                state = State.WALKING;
                return;
            }

            hit = new BlockHitResult(hitPos, clickFace, neighbor, false);
        }

        // Place with sneak
        sneakInteract(mc, player, hit);
        player.swingHand(Hand.MAIN_HAND);

        // Verify placement
        boolean placed = !world.getBlockState(target).isAir();
        if (placed) {
            placedCount++;
            passRetryTicks = 0;
        } else {
            // Failed to place — skip to avoid spam-clicking
            skipCurrent();
            return;
        }

        blocksSinceBreak++;

        // Post-placement adjustment
        int clicks = getRequiredAdjustClicks(currentEntry.blockState);
        if (clicks > 0) {
            adjustClicks = clicks;
            adjustCooldown = 2;
            state = State.ADJUST;
            return;
        }

        enterCooldown();
    }

    /**
     * Returns the direction from target to lookingAt if they are adjacent, null otherwise.
     */
    private Direction getPlaceFace(BlockPos target, BlockPos lookingAt) {
        for (Direction d : Direction.values()) {
            if (target.offset(d).equals(lookingAt)) return d;
        }
        return null;
    }

    // ==========================================================
    // ADJUST
    // ==========================================================

    private void tickAdjust(MinecraftClient mc) {
        if (adjustCooldown > 0) {
            adjustCooldown--;
            return;
        }

        if (adjustClicks <= 0) {
            enterCooldown();
            return;
        }

        ClientPlayerEntity player = mc.player;
        BlockPos target = currentEntry.worldPos;

        Vec3d hitPos = Vec3d.ofCenter(target).add(0, 0.25, 0);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, target, false);
        sneakInteract(mc, player, hit);

        adjustClicks--;
        adjustCooldown = 2;
    }

    // ==========================================================
    // COOLDOWN
    // ==========================================================

    private void tickCooldown() {
        cooldownTicks--;
        if (cooldownTicks <= 0) state = State.PICK;
    }

    private void enterCooldown() {
        if (blocksSinceBreak >= 15 + ThreadLocalRandom.current().nextInt(15)) {
            cooldownTicks = 15 + ThreadLocalRandom.current().nextInt(20);
            blocksSinceBreak = 0;
        } else {
            cooldownTicks = 2 + ThreadLocalRandom.current().nextInt(4);
        }
        state = State.COOLDOWN;
    }

    // ==========================================================
    // SNEAK INTERACT
    // ==========================================================

    private void sneakInteract(MinecraftClient mc, ClientPlayerEntity player, BlockHitResult hit) {
        player.networkHandler.sendPacket(
            new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));

        boolean wasSneaking = player.input.sneaking;
        player.input.sneaking = true;

        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);

        player.input.sneaking = wasSneaking;

        player.networkHandler.sendPacket(
            new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
    }

    // ==========================================================
    // BLOCK PROPERTY HELPERS
    // ==========================================================

    private static Map<String, String> parseProperties(String blockState) {
        Map<String, String> props = new HashMap<>();
        int open = blockState.indexOf('[');
        int close = blockState.indexOf(']');
        if (open < 0 || close < 0 || close <= open + 1) return props;

        String inner = blockState.substring(open + 1, close);
        for (String pair : inner.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) props.put(kv[0].trim(), kv[1].trim());
        }
        return props;
    }

    private static int getRequiredAdjustClicks(String blockState) {
        String blockId = extractBlockId(blockState);
        Map<String, String> props = parseProperties(blockState);

        if (blockId.equals("minecraft:repeater")) {
            String delayStr = props.get("delay");
            if (delayStr != null) {
                int delay = Integer.parseInt(delayStr);
                return delay - 1;
            }
        }

        if (blockId.equals("minecraft:comparator")) {
            String mode = props.get("mode");
            if ("subtract".equals(mode)) return 1;
        }

        return 0;
    }

    private static boolean isDirectionalPlacement(String blockState) {
        String blockId = extractBlockId(blockState);
        return blockId.equals("minecraft:repeater")
            || blockId.equals("minecraft:comparator")
            || blockId.contains("piston")
            || blockId.contains("observer");
    }

    private static String extractBlockId(String blockState) {
        int bracket = blockState.indexOf('[');
        return bracket >= 0 ? blockState.substring(0, bracket) : blockState;
    }

    private static Direction directionFromName(String name) {
        return switch (name.toLowerCase()) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east"  -> Direction.EAST;
            case "west"  -> Direction.WEST;
            case "up"    -> Direction.UP;
            case "down"  -> Direction.DOWN;
            default -> null;
        };
    }

    private static float directionToYaw(Direction dir) {
        return switch (dir) {
            case SOUTH -> 0f;
            case WEST  -> 90f;
            case NORTH -> 180f;
            case EAST  -> -90f;
            default -> 0f;
        };
    }

    private static float aimYawToward(ClientPlayerEntity player, Vec3d point) {
        Vec3d eyes = player.getEyePos();
        double dx = point.x - eyes.x;
        double dz = point.z - eyes.z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // ==========================================================
    // SPATIAL AWARENESS
    // ==========================================================

    private boolean isPlayerOccupying(ClientPlayerEntity player, BlockPos pos) {
        Box playerBox = player.getBoundingBox();
        Box blockBox = new Box(pos);
        return playerBox.intersects(blockBox);
    }

    /**
     * Find the best neighboring solid block to click against for placing at target.
     * Only considers faces within PLACE_REACH.
     */
    private Direction findBestPlaceFace(ClientWorld world, ClientPlayerEntity player, BlockPos target) {
        Vec3d eyes = player.getEyePos();
        Direction best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = target.offset(dir);
            if (world.getBlockState(neighbor).isAir()) continue;

            Vec3d facePoint = getFaceHitPoint(target, dir);
            double dist = eyes.distanceTo(facePoint);
            if (dist > PLACE_REACH) continue;

            if (dist < bestDist) {
                bestDist = dist;
                best = dir;
            }
        }

        return best;
    }

    private Vec3d getFaceHitPoint(BlockPos target, Direction faceDir) {
        BlockPos neighbor = target.offset(faceDir);
        Direction clickFace = faceDir.getOpposite();
        return Vec3d.ofCenter(neighbor)
            .add(Vec3d.of(clickFace.getVector()).multiply(0.5));
    }

    private Vec3d findStandPosition(ClientPlayerEntity player, BlockPos target) {
        Vec3d playerPos = player.getPos();
        Vec3d targetCenter = Vec3d.ofCenter(target);

        double dx = playerPos.x - targetCenter.x;
        double dz = playerPos.z - targetCenter.z;
        double len = Math.sqrt(dx * dx + dz * dz);

        if (len < 0.1) {
            dx = 1;
            dz = 0;
            len = 1;
        }

        double nx = dx / len;
        double nz = dz / len;

        return new Vec3d(
            targetCenter.x + nx * 2.5,
            target.getY(),
            targetCenter.z + nz * 2.5
        );
    }

    private boolean hasSupport(ClientWorld world, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (!world.getBlockState(pos.offset(d)).isAir()) return true;
        }
        return false;
    }

    private void releaseKeys() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }

    private static float lerpAngle(float from, float to, float t) {
        return from + MathHelper.wrapDegrees(to - from) * t;
    }
}
