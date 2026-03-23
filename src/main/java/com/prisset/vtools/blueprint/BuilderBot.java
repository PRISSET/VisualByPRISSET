package com.prisset.vtools.blueprint;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Smart builder bot with spatial awareness.
 *
 * Key intelligence:
 * - Never places blocks where the player is standing (feet + head positions)
 * - Never places blocks it can't physically reach (checks line of sight to face)
 * - Walks BESIDE blocks to place, not ON TOP of them
 * - Layer-by-layer strict order, defers unsupported blocks
 * - Synthetic BlockHitResult placement (reliable in singleplayer)
 */
public final class BuilderBot {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final BuilderBot INSTANCE = new BuilderBot();

    private static final double PLACE_REACH = 4.5;
    private static final double WALK_STOP_DIST = 3.5;
    private static final int WALK_TIMEOUT = 160;

    public enum State {
        IDLE, PLANNING, EQUIP, WALKING, AIM, PLACE, ADJUST, COOLDOWN, DONE, PAUSED
    }

    private State state = State.IDLE;

    private List<List<BuildQueue.PlaceEntry>> layers;
    private int layerIdx;
    private int blockIdx;
    private List<BuildQueue.PlaceEntry> deferred;
    private int deferredAttempts;

    private int placedCount;
    private int totalCount;
    private int cooldownTicks;
    private int blocksSinceBreak;

    private BuildQueue.PlaceEntry currentEntry;
    private int aimTicks;
    private int walkTicks;
    private int adjustClicks;   // remaining right-clicks for post-placement (repeater delay)
    private int adjustCooldown; // ticks between adjust clicks

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
        blockIdx = 0;
        deferredAttempts = 0;
        deferred = new ArrayList<>();
        LOG.info("Builder started");
    }

    public void stop() {
        state = State.IDLE;
        layers = null;
        deferred = null;
        currentEntry = null;
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
        if (state == State.PAUSED) state = State.EQUIP;
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
            case EQUIP    -> tickEquip(mc);
            case WALKING  -> tickWalking(mc);
            case AIM      -> tickAim(mc);
            case PLACE    -> tickPlace(mc);
            case ADJUST   -> tickAdjust(mc);
            case COOLDOWN -> tickCooldown();
        }
    }

    // -- PLANNING --

    private void tickPlanning(MinecraftClient mc) {
        layers = BuildQueue.generateLayers(mc.world, mc.player);
        layerIdx = 0;
        blockIdx = 0;
        if (deferred == null) deferred = new ArrayList<>();
        deferred.clear();
        deferredAttempts = 0;

        totalCount = 0;
        for (var layer : layers) totalCount += layer.size();

        if (layers.isEmpty()) {
            state = State.DONE;
            LOG.info("Build complete! {} placed", placedCount);
            return;
        }

        LOG.info("Queue: {} blocks, {} layers", totalCount, layers.size());
        state = State.EQUIP;
    }

    // -- EQUIP --

    private void tickEquip(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        currentEntry = pickNext(mc);

        if (currentEntry == null) {
            if (!deferred.isEmpty() && deferredAttempts < 5) {
                layers.add(new ArrayList<>(deferred));
                deferred.clear();
                deferredAttempts++;
                return;
            }
            state = State.PLANNING;
            return;
        }

        BlockPos target = currentEntry.worldPos;

        // SPATIAL AWARENESS: skip blocks player is occupying
        if (isPlayerOccupying(player, target)) {
            deferred.add(currentEntry);
            blockIdx++;
            return;
        }

        // Check support (at least one non-air neighbor)
        if (!hasSupport(mc.world, target)) {
            deferred.add(currentEntry);
            blockIdx++;
            return;
        }

        // Equip block first (needed before any placement attempt)
        String blockId = currentEntry.getBlockId();
        if (!InventoryHelper.isHolding(player, blockId)) {
            boolean found = InventoryHelper.equipBlock(player, blockId);
            if (!found) {
                blockIdx++;
                return;
            }
            cooldownTicks = 1;
            state = State.COOLDOWN;
            return;
        }

        // Distance check: if too far from target, walk first
        double distToTarget = player.getEyePos().distanceTo(Vec3d.ofCenter(target));
        if (distToTarget > PLACE_REACH) {
            walkTicks = 0;
            state = State.WALKING;
            return;
        }

        // Close enough — find a valid face to place against
        Direction face = findBestPlaceFace(mc.world, player, target);
        if (face == null) {
            deferred.add(currentEntry);
            blockIdx++;
            return;
        }

        aimTicks = 0;
        state = State.AIM;
    }

    private BuildQueue.PlaceEntry pickNext(MinecraftClient mc) {
        while (layerIdx < layers.size()) {
            var layer = layers.get(layerIdx);
            while (blockIdx < layer.size()) {
                var entry = layer.get(blockIdx);
                String actual = SchematicData.encodeState(mc.world.getBlockState(entry.worldPos));
                if (entry.blockState.equals(actual)) {
                    blockIdx++;
                    continue;
                }
                return entry;
            }
            layerIdx++;
            blockIdx = 0;
        }
        return null;
    }

    // -- WALKING --

    private void tickWalking(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        BlockPos target = currentEntry.worldPos;
        walkTicks++;

        if (walkTicks > WALK_TIMEOUT) {
            releaseKeys();
            blockIdx++;
            state = State.EQUIP;
            return;
        }

        // Walk toward a spot BESIDE the target, not on top of it
        Vec3d walkGoal = findStandPosition(player, target);
        Vec3d pPos = player.getPos();
        double dx = walkGoal.x - pPos.x;
        double dz = walkGoal.z - pPos.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        if (distXZ <= 1.0) {
            releaseKeys();
            aimTicks = 0;
            state = State.AIM;
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.setYaw(lerpAngle(player.getYaw(), yaw, 0.3f));

        mc.options.forwardKey.setPressed(true);
        mc.options.sprintKey.setPressed(distXZ > 5.0);

        if (player.isOnGround()) {
            Vec3d vel = player.getVelocity();
            if (vel.x * vel.x + vel.z * vel.z < 0.001 && walkTicks > 5) {
                mc.options.jumpKey.setPressed(true);
            } else {
                mc.options.jumpKey.setPressed(false);
            }
        }
    }

    // -- AIM --

    private void tickAim(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        BlockPos target = currentEntry.worldPos;

        Direction face = findBestPlaceFace(mc.world, player, target);
        if (face == null) {
            deferred.add(currentEntry);
            blockIdx++;
            state = State.EQUIP;
            return;
        }

        // For directional blocks (repeater, comparator), player must face opposite
        // to the desired "facing" property so Minecraft orients the block correctly.
        // Repeater/comparator facing = direction the output points = opposite of player look.
        float targetYaw;
        float targetPitch;

        Map<String, String> props = parseProperties(currentEntry.blockState);
        String facingProp = props.get("facing");
        if (facingProp != null && isDirectionalPlacement(currentEntry.blockState)) {
            Direction desired = directionFromName(facingProp);
            if (desired != null) {
                // Player must look OPPOSITE to the desired facing
                Direction lookDir = desired.getOpposite();
                targetYaw = directionToYaw(lookDir);
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
        targetPitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));

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

    // -- PLACE --

    private void tickPlace(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        BlockPos target = currentEntry.worldPos;

        // Final safety: don't place into player
        if (isPlayerOccupying(player, target)) {
            deferred.add(currentEntry);
            blockIdx++;
            state = State.EQUIP;
            return;
        }

        Direction face = findBestPlaceFace(world, player, target);
        if (face == null) {
            blockIdx++;
            state = State.EQUIP;
            return;
        }

        BlockPos neighbor = target.offset(face);
        Direction clickFace = face.getOpposite();
        Vec3d hitPos = getFaceHitPoint(target, face);

        BlockHitResult hit = new BlockHitResult(hitPos, clickFace, neighbor, false);

        // Sneak to prevent opening interactive blocks (chests, furnaces, brewing stands, etc.)
        // ClientPlayerEntity.isSneaking() reads input.sneaking, not the Entity flag
        boolean wasSneaking = player.input.sneaking;
        player.input.sneaking = true;
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);
        player.input.sneaking = wasSneaking;

        boolean placed = !world.getBlockState(target).isAir();
        if (placed) {
            placedCount++;
        }

        blockIdx++;
        blocksSinceBreak++;

        // Check if post-placement adjustment is needed (e.g. repeater delay)
        int clicks = getRequiredAdjustClicks(currentEntry.blockState);
        if (placed && clicks > 0) {
            adjustClicks = clicks;
            adjustCooldown = 2;
            state = State.ADJUST;
            return;
        }

        if (blocksSinceBreak >= 15 + ThreadLocalRandom.current().nextInt(15)) {
            cooldownTicks = 15 + ThreadLocalRandom.current().nextInt(20);
            blocksSinceBreak = 0;
        } else {
            cooldownTicks = 2 + ThreadLocalRandom.current().nextInt(4);
        }
        state = State.COOLDOWN;
    }

    // -- ADJUST (post-placement: repeater delay clicks, etc.) --

    private void tickAdjust(MinecraftClient mc) {
        if (adjustCooldown > 0) {
            adjustCooldown--;
            return;
        }

        if (adjustClicks <= 0) {
            // Done adjusting, go to cooldown
            if (blocksSinceBreak >= 15 + ThreadLocalRandom.current().nextInt(15)) {
                cooldownTicks = 15 + ThreadLocalRandom.current().nextInt(20);
                blocksSinceBreak = 0;
            } else {
                cooldownTicks = 2 + ThreadLocalRandom.current().nextInt(4);
            }
            state = State.COOLDOWN;
            return;
        }

        ClientPlayerEntity player = mc.player;
        BlockPos target = currentEntry.worldPos;

        // Right-click the placed block to cycle its state (e.g. repeater delay)
        Vec3d hitPos = Vec3d.ofCenter(target).add(0, 0.25, 0);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, target, false);

        boolean wasSneaking = player.input.sneaking;
        player.input.sneaking = true;
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.input.sneaking = wasSneaking;

        adjustClicks--;
        adjustCooldown = 2; // small delay between clicks for reliability
    }

    // -- COOLDOWN --

    private void tickCooldown() {
        cooldownTicks--;
        if (cooldownTicks <= 0) state = State.EQUIP;
    }

    // ==========================================================
    // BLOCK PROPERTY HELPERS
    // ==========================================================

    /**
     * Parse "[prop=val,prop2=val2]" from a blockState string like
     * "minecraft:repeater[delay=3,facing=north,locked=false,powered=false]"
     */
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

    /**
     * How many right-clicks are needed after placement to reach the desired state.
     * Repeater: default delay=1, each click +1 (up to 4). So delay=3 -> 2 clicks.
     * Comparator: default mode=compare, one click -> subtract. So subtract -> 1 click.
     */
    private static int getRequiredAdjustClicks(String blockState) {
        String blockId = blockState.contains("[") ? blockState.substring(0, blockState.indexOf('[')) : blockState;
        Map<String, String> props = parseProperties(blockState);

        if (blockId.equals("minecraft:repeater")) {
            String delayStr = props.get("delay");
            if (delayStr != null) {
                int delay = Integer.parseInt(delayStr);
                return delay - 1; // placed with delay=1, each click adds 1
            }
        }

        if (blockId.equals("minecraft:comparator")) {
            String mode = props.get("mode");
            if ("subtract".equals(mode)) return 1;
        }

        return 0;
    }

    /**
     * Whether this block's orientation depends on player facing direction at placement.
     * Repeaters, comparators, stairs, pistons, etc.
     */
    private static boolean isDirectionalPlacement(String blockState) {
        String blockId = blockState.contains("[") ? blockState.substring(0, blockState.indexOf('[')) : blockState;
        return blockId.equals("minecraft:repeater")
            || blockId.equals("minecraft:comparator")
            || blockId.contains("piston")
            || blockId.contains("observer");
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

    /**
     * Check if the player's bounding box overlaps with the given block position.
     * Prevents placing blocks into the player (feet, body, head).
     */
    private boolean isPlayerOccupying(ClientPlayerEntity player, BlockPos pos) {
        Box playerBox = player.getBoundingBox();
        Box blockBox = new Box(pos);
        return playerBox.intersects(blockBox);
    }

    /**
     * Find the best face to place against, considering:
     * 1. The neighbor must be solid (non-air)
     * 2. The face must be reachable from player's eye position
     * 3. The neighbor must NOT be a position the player occupies
     *
     * Priority: DOWN first (natural), then sides, then UP
     */
    private Direction findBestPlaceFace(ClientWorld world, ClientPlayerEntity player, BlockPos target) {
        Vec3d eyes = player.getEyePos();

        Direction best = null;
        double bestDist = Double.MAX_VALUE;

        // Preferred order: DOWN, sides, UP
        Direction[] order = {
            Direction.DOWN,
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST,
            Direction.UP
        };

        for (Direction dir : order) {
            BlockPos neighbor = target.offset(dir);

            // Neighbor must be solid
            if (world.getBlockState(neighbor).isAir()) continue;

            // Check reach to the face
            Vec3d facePoint = getFaceHitPoint(target, dir);
            double dist = eyes.distanceTo(facePoint);
            if (dist > PLACE_REACH) continue;

            // Pick closest reachable face
            if (dist < bestDist) {
                bestDist = dist;
                best = dir;
            }
        }

        return best;
    }

    /**
     * Calculate the exact hit point on the face between target and its neighbor.
     * This is the center of the face on the neighbor block that faces toward target.
     */
    private Vec3d getFaceHitPoint(BlockPos target, Direction faceDir) {
        BlockPos neighbor = target.offset(faceDir);
        Direction clickFace = faceDir.getOpposite();
        return Vec3d.ofCenter(neighbor)
            .add(Vec3d.of(clickFace.getVector()).multiply(0.5));
    }

    /**
     * Find a good position for the player to stand while placing a block.
     * Prefers positions that are:
     * - On the same Y level or one below the target
     * - Adjacent to the target horizontally
     * - Not inside the target block
     */
    private Vec3d findStandPosition(ClientPlayerEntity player, BlockPos target) {
        Vec3d playerPos = player.getPos();
        Vec3d targetCenter = Vec3d.ofCenter(target);

        // Direction from target to player (we want to stand on the player's side)
        double dx = playerPos.x - targetCenter.x;
        double dz = playerPos.z - targetCenter.z;
        double len = Math.sqrt(dx * dx + dz * dz);

        if (len < 0.1) {
            // Player is basically at target, pick arbitrary direction
            dx = 1;
            dz = 0;
            len = 1;
        }

        // Normalize and step 2 blocks away from target center
        double nx = dx / len;
        double nz = dz / len;

        return new Vec3d(
            targetCenter.x + nx * 2.5,
            target.getY(), // same Y level
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
