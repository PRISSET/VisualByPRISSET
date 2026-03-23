package com.prisset.vtools.blueprint;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adaptive builder bot that walks to each block and places it.
 *
 * Core behavior:
 * - Strict layer-by-layer (Y=0 complete before Y=1)
 * - Within each layer, always picks the NEAREST reachable block to the player
 * - Walks to blocks it can't reach, stops at comfortable placement distance
 * - Sneaks when placing against interactive blocks (chests, furnaces, etc.)
 * - Defers blocks that can't be placed yet (no support, player in the way)
 * - Post-placement adjustment for repeater delay, comparator mode
 */
public final class BuilderBot {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final BuilderBot INSTANCE = new BuilderBot();

    private static final double PLACE_REACH = 4.5;
    private static final double COMFORTABLE_DIST = 3.0; // stop walking at this distance
    private static final int WALK_TIMEOUT = 200;
    private static final int MAX_DEFERRED_ROUNDS = 8;

    public enum State {
        IDLE, PLANNING, PICK, EQUIP, WALKING, AIM, PLACE, ADJUST, COOLDOWN, DONE, PAUSED
    }

    private State state = State.IDLE;

    // Layer data: each layer is a mutable list, blocks are removed when placed
    private List<List<BuildQueue.PlaceEntry>> layers;
    private int layerIdx;
    private List<BuildQueue.PlaceEntry> deferred;
    private int deferredRounds;

    private int placedCount;
    private int totalCount;
    private int cooldownTicks;
    private int blocksSinceBreak;

    private BuildQueue.PlaceEntry currentEntry;
    private Direction currentFace; // cached face for current placement
    private int aimTicks;
    private int walkTicks;
    private int adjustClicks;
    private int adjustCooldown;

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
        deferredRounds = 0;
        deferred = new ArrayList<>();
        LOG.info("Builder started");
    }

    public void stop() {
        state = State.IDLE;
        layers = null;
        deferred = null;
        currentEntry = null;
        currentFace = null;
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
    // PLANNING: generate all layers
    // ==========================================================

    private void tickPlanning(MinecraftClient mc) {
        layers = BuildQueue.generateLayers(mc.world, mc.player);
        layerIdx = 0;
        if (deferred == null) deferred = new ArrayList<>();
        deferred.clear();
        deferredRounds = 0;

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
    // PICK: find the nearest placeable block in current layer
    // ==========================================================

    private void tickPick(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;

        // Advance through layers
        while (layerIdx < layers.size()) {
            List<BuildQueue.PlaceEntry> layer = layers.get(layerIdx);

            // Remove already-placed blocks from layer
            layer.removeIf(e -> {
                String actual = SchematicData.encodeState(world.getBlockState(e.worldPos));
                return e.blockState.equals(actual);
            });

            if (layer.isEmpty()) {
                layerIdx++;
                continue;
            }

            // Find nearest block that we can attempt to place
            BuildQueue.PlaceEntry best = null;
            double bestDist = Double.MAX_VALUE;
            Vec3d playerPos = player.getPos();

            for (BuildQueue.PlaceEntry entry : layer) {
                // Skip blocks player is occupying
                if (isPlayerOccupying(player, entry.worldPos)) continue;

                // Skip blocks with no support
                if (!hasSupport(world, entry.worldPos)) continue;

                double dist = playerPos.squaredDistanceTo(Vec3d.ofCenter(entry.worldPos));
                if (dist < bestDist) {
                    bestDist = dist;
                    best = entry;
                }
            }

            if (best != null) {
                currentEntry = best;
                layer.remove(best);
                state = State.EQUIP;
                return;
            }

            // All remaining blocks in this layer are deferred (no support / player blocking)
            // Move them to deferred list and try next layer or retry
            deferred.addAll(layer);
            layer.clear();
            layerIdx++;
        }

        // All layers exhausted — check deferred
        if (!deferred.isEmpty() && deferredRounds < MAX_DEFERRED_ROUNDS) {
            deferredRounds++;
            layers.add(new ArrayList<>(deferred));
            deferred.clear();
            // Don't increment layerIdx — we just added a new layer at the end
            return;
        }

        // Truly done or stuck
        state = State.PLANNING;
    }

    // ==========================================================
    // EQUIP: get the right block in hand, then check distance
    // ==========================================================

    private void tickEquip(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        BlockPos target = currentEntry.worldPos;

        // Re-check: already placed?
        String actual = SchematicData.encodeState(mc.world.getBlockState(target));
        if (currentEntry.blockState.equals(actual)) {
            state = State.PICK;
            return;
        }

        // Equip the right block
        String blockId = currentEntry.getBlockId();
        if (!InventoryHelper.isHolding(player, blockId)) {
            boolean found = InventoryHelper.equipBlock(player, blockId);
            if (!found) {
                // Material not in inventory, skip this block
                state = State.PICK;
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

        // Close enough — find a face to place against
        currentFace = findBestPlaceFace(mc.world, player, target);
        if (currentFace == null) {
            // Can't find a face even though we're close — defer
            deferred.add(currentEntry);
            state = State.PICK;
            return;
        }

        aimTicks = 0;
        state = State.AIM;
    }

    // ==========================================================
    // WALKING: move toward the target block
    // ==========================================================

    private void tickWalking(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        BlockPos target = currentEntry.worldPos;
        walkTicks++;

        if (walkTicks > WALK_TIMEOUT) {
            releaseKeys();
            deferred.add(currentEntry);
            state = State.PICK;
            return;
        }

        // Check if we're now close enough to place
        double distToTarget = player.getEyePos().distanceTo(Vec3d.ofCenter(target));
        if (distToTarget <= COMFORTABLE_DIST) {
            releaseKeys();
            // Re-enter EQUIP to find face now that we're close
            state = State.EQUIP;
            return;
        }

        // Walk toward a position beside the target
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

        // Face the walk direction
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.setYaw(lerpAngle(player.getYaw(), yaw, 0.35f));

        mc.options.forwardKey.setPressed(true);
        mc.options.sprintKey.setPressed(distXZ > 6.0);

        // Jump over obstacles
        if (player.isOnGround() && walkTicks > 4) {
            Vec3d vel = player.getVelocity();
            double speed = vel.x * vel.x + vel.z * vel.z;
            if (speed < 0.001) {
                mc.options.jumpKey.setPressed(true);
            } else {
                mc.options.jumpKey.setPressed(false);
            }
        }
    }

    // ==========================================================
    // AIM: look at the placement face
    // ==========================================================

    private void tickAim(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        BlockPos target = currentEntry.worldPos;

        // Re-find face (player may have moved slightly)
        Direction face = findBestPlaceFace(mc.world, player, target);
        if (face == null) {
            deferred.add(currentEntry);
            state = State.PICK;
            return;
        }
        currentFace = face;

        // For directional blocks, orient player correctly
        float targetYaw;
        Map<String, String> props = parseProperties(currentEntry.blockState);
        String facingProp = props.get("facing");
        if (facingProp != null && isDirectionalPlacement(currentEntry.blockState)) {
            Direction desired = directionFromName(facingProp);
            if (desired != null) {
                targetYaw = directionToYaw(desired.getOpposite());
            } else {
                targetYaw = aimYawToward(player, getFaceHitPoint(target, face));
            }
        } else {
            targetYaw = aimYawToward(player, getFaceHitPoint(target, face));
        }

        Vec3d hitPoint = getFaceHitPoint(target, face);
        Vec3d eyes = player.getEyePos();
        double dy = hitPoint.y - eyes.y;
        double dxH = hitPoint.x - eyes.x;
        double dzH = hitPoint.z - eyes.z;
        double distXZ = Math.sqrt(dxH * dxH + dzH * dzH);
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));

        player.setYaw(lerpAngle(player.getYaw(), targetYaw, 0.5f));
        player.setPitch(MathHelper.clamp(
            player.getPitch() + (targetPitch - player.getPitch()) * 0.5f, -90f, 90f));

        aimTicks++;
        if (aimTicks >= 2) {
            player.setYaw(targetYaw);
            player.setPitch(MathHelper.clamp(targetPitch, -90f, 90f));
            state = State.PLACE;
        }
    }

    // ==========================================================
    // PLACE: actually place the block
    // ==========================================================

    private void tickPlace(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        BlockPos target = currentEntry.worldPos;

        // Final safety
        if (isPlayerOccupying(player, target)) {
            deferred.add(currentEntry);
            state = State.PICK;
            return;
        }

        Direction face = currentFace;
        if (face == null) {
            face = findBestPlaceFace(world, player, target);
        }
        if (face == null) {
            deferred.add(currentEntry);
            state = State.PICK;
            return;
        }

        BlockPos neighbor = target.offset(face);
        Direction clickFace = face.getOpposite();
        Vec3d hitPos = getFaceHitPoint(target, face);

        // Final reach check
        double reachDist = player.getEyePos().distanceTo(hitPos);
        if (reachDist > PLACE_REACH) {
            // Got too far somehow, walk again
            walkTicks = 0;
            state = State.WALKING;
            return;
        }

        BlockHitResult hit = new BlockHitResult(hitPos, clickFace, neighbor, false);

        // Always sneak during placement to prevent opening containers
        boolean wasSneaking = player.input.sneaking;
        player.input.sneaking = true;
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);
        player.input.sneaking = wasSneaking;

        boolean placed = !world.getBlockState(target).isAir();
        if (placed) {
            placedCount++;
        }

        blocksSinceBreak++;

        // Post-placement adjustment (repeater delay, comparator mode)
        int clicks = getRequiredAdjustClicks(currentEntry.blockState);
        if (placed && clicks > 0) {
            adjustClicks = clicks;
            adjustCooldown = 2;
            state = State.ADJUST;
            return;
        }

        enterCooldown();
    }

    // ==========================================================
    // ADJUST: post-placement right-clicks (repeater delay, etc.)
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

        boolean wasSneaking = player.input.sneaking;
        player.input.sneaking = true;
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.input.sneaking = wasSneaking;

        adjustClicks--;
        adjustCooldown = 2;
    }

    // ==========================================================
    // COOLDOWN: anti-detection pause between placements
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
    // SPATIAL AWARENESS HELPERS
    // ==========================================================

    private boolean isPlayerOccupying(ClientPlayerEntity player, BlockPos pos) {
        Box playerBox = player.getBoundingBox();
        Box blockBox = new Box(pos);
        return playerBox.intersects(blockBox);
    }

    /**
     * Find the best face to place against.
     * Only considers faces within PLACE_REACH from player's eyes.
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

    /**
     * Find a position beside the target block for the player to stand.
     * Picks the side closest to the player's current position, 2 blocks out.
     */
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
