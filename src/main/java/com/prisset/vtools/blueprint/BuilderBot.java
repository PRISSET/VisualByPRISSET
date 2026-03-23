package com.prisset.vtools.blueprint;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Human-like automatic builder FSM. Walks to blocks, smoothly rotates head,
 * equips correct items, and places blocks layer by layer.
 *
 * States:
 *   IDLE     -- not building
 *   PLANNING -- generate/refresh queue from schematic
 *   WALKING  -- moving toward next block target
 *   EQUIP    -- find + equip correct block in hotbar
 *   AIM      -- smoothly rotate head toward placement target
 *   PLACE    -- interact to place block
 *   COOLDOWN -- variable delay between placements
 *   DONE     -- build complete
 *   PAUSED   -- temporarily stopped by user
 */
public final class BuilderBot {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final BuilderBot INSTANCE = new BuilderBot();

    private static final double PLACE_REACH = 4.5;
    private static final double WALK_CLOSE_ENOUGH = 3.0;
    private static final int AIM_TICKS_MIN = 3;
    private static final int AIM_TICKS_MAX = 6;
    private static final int WALK_TIMEOUT = 200;

    public enum State {
        IDLE, PLANNING, WALKING, EQUIP, AIM, PLACE, COOLDOWN, DONE, PAUSED
    }

    private State state = State.IDLE;
    private List<BuildQueue.PlaceEntry> queue;
    private int queueIndex;
    private int cooldownTicks;
    private int placedCount;
    private int totalCount;
    private int blocksSinceBreak;

    // Aim interpolation
    private float targetYaw, targetPitch;
    private float startYaw, startPitch;
    private int aimTicks, aimDuration;

    // Walking
    private BlockPos walkTarget;
    private int walkTicks;

    private BuilderBot() {}
    public static BuilderBot instance() { return INSTANCE; }

    public State getState() { return state; }
    public int getPlacedCount() { return placedCount; }
    public int getTotalCount() { return totalCount; }

    public float getProgress() {
        return totalCount > 0 ? (float) placedCount / totalCount : 0;
    }

    // -- Control --

    public void start() {
        if (!BlueprintPlacer.instance().isReadyToPlace()) return;
        state = State.PLANNING;
        placedCount = 0;
        blocksSinceBreak = 0;
        LOG.info("Builder started");
    }

    public void stop() {
        state = State.IDLE;
        queue = null;
        queueIndex = 0;
        stopMovement();
        LOG.info("Builder stopped ({} placed)", placedCount);
    }

    public void pause() {
        if (state != State.IDLE && state != State.DONE) {
            state = State.PAUSED;
            stopMovement();
        }
    }

    public void resume() {
        if (state == State.PAUSED) {
            state = State.EQUIP;
        }
    }

    public boolean isRunning() {
        return state != State.IDLE && state != State.DONE && state != State.PAUSED;
    }

    // -- Tick (called from mixin every client tick) --

    public void tick() {
        if (state == State.IDLE || state == State.DONE || state == State.PAUSED) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;

        switch (state) {
            case PLANNING -> tickPlanning(world, player);
            case WALKING  -> tickWalking(player);
            case EQUIP    -> tickEquip(player);
            case AIM      -> tickAim(player);
            case PLACE    -> tickPlace(mc, player, world);
            case COOLDOWN -> tickCooldown();
        }
    }

    // -- PLANNING: generate queue sorted by proximity --

    private void tickPlanning(ClientWorld world, ClientPlayerEntity player) {
        queue = BuildQueue.generate(world);
        queueIndex = 0;
        totalCount = queue.size();

        if (queue.isEmpty()) {
            state = State.DONE;
            LOG.info("Build complete! {} blocks placed", placedCount);
            return;
        }

        // Sort by distance to player within each Y layer for human-like order
        Vec3d playerPos = player.getPos();
        queue.sort(Comparator
            .comparingInt((BuildQueue.PlaceEntry e) -> e.localY)
            .thenComparingDouble(e -> e.worldPos.getSquaredDistance(playerPos)));

        LOG.info("Build queue: {} blocks to place", totalCount);
        state = State.EQUIP;
    }

    // -- EQUIP: find and switch to correct block --

    private void tickEquip(ClientPlayerEntity player) {
        if (queueIndex >= queue.size()) {
            state = State.PLANNING;
            return;
        }

        BuildQueue.PlaceEntry entry = queue.get(queueIndex);
        String blockId = entry.getBlockId();

        // Check if block already placed correctly
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            String actual = SchematicData.encodeState(mc.world.getBlockState(entry.worldPos));
            if (entry.blockState.equals(actual)) {
                queueIndex++;
                return;
            }
        }

        if (InventoryHelper.isHolding(player, blockId)) {
            // Check distance: do we need to walk?
            double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(entry.worldPos));
            if (dist > PLACE_REACH) {
                walkTarget = entry.worldPos;
                walkTicks = 0;
                state = State.WALKING;
            } else {
                beginAim(player, entry.worldPos);
                state = State.AIM;
            }
            return;
        }

        boolean found = InventoryHelper.equipBlock(player, blockId);
        if (!found) {
            LOG.warn("Missing block: {}, skipping", blockId);
            queueIndex++;
            return;
        }

        // Small delay after switching items
        cooldownTicks = 2 + ThreadLocalRandom.current().nextInt(3);
        state = State.COOLDOWN;
    }

    // -- WALKING: navigate toward target block --

    private void tickWalking(ClientPlayerEntity player) {
        if (walkTarget == null) {
            state = State.EQUIP;
            return;
        }

        walkTicks++;

        // Timeout: if stuck for too long, skip this block
        if (walkTicks > WALK_TIMEOUT) {
            LOG.warn("Walk timeout, skipping block at {}", walkTarget);
            stopMovement();
            queueIndex++;
            state = State.EQUIP;
            return;
        }

        Vec3d playerPos = player.getPos();
        Vec3d targetCenter = Vec3d.ofCenter(walkTarget);
        double dx = targetCenter.x - playerPos.x;
        double dz = targetCenter.z - playerPos.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        // Close enough to place
        if (distXZ <= WALK_CLOSE_ENOUGH) {
            stopMovement();

            // Verify still in reach
            double eyeDist = player.getEyePos().distanceTo(targetCenter);
            if (eyeDist <= PLACE_REACH) {
                beginAim(player, walkTarget);
                state = State.AIM;
            } else {
                // Still too far vertically, skip
                queueIndex++;
                state = State.EQUIP;
            }
            return;
        }

        // Face toward target (smooth over walking)
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.setYaw(lerpAngle(player.getYaw(), desiredYaw, 0.15f));

        // Simple forward movement
        player.input.movementForward = 1.0f;
        player.input.movementSideways = 0.0f;
        player.input.pressingForward = true;

        // Jump if blocked (simple obstacle detection)
        if (shouldJump(player)) {
            player.input.jumping = true;
        }

        // Sprint for longer distances
        player.setSprinting(distXZ > 8.0);
    }

    // -- AIM: smooth head rotation toward target --

    private void beginAim(ClientPlayerEntity player, BlockPos target) {
        Vec3d eyes = player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(target);
        double dx = blockCenter.x - eyes.x;
        double dy = blockCenter.y - eyes.y;
        double dz = blockCenter.z - eyes.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        startYaw = player.getYaw();
        startPitch = player.getPitch();
        targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        targetPitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));

        // Bigger angle difference = more ticks to rotate (human-like)
        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - startYaw));
        float pitchDiff = Math.abs(targetPitch - startPitch);
        float totalDiff = yawDiff + pitchDiff;

        if (totalDiff < 10) {
            aimDuration = AIM_TICKS_MIN;
        } else if (totalDiff < 45) {
            aimDuration = AIM_TICKS_MIN + 1;
        } else {
            aimDuration = AIM_TICKS_MIN + ThreadLocalRandom.current().nextInt(
                AIM_TICKS_MAX - AIM_TICKS_MIN + 1);
        }
        aimTicks = 0;
    }

    private void tickAim(ClientPlayerEntity player) {
        if (queueIndex >= queue.size()) { state = State.PLANNING; return; }

        aimTicks++;
        float progress = Math.min(1.0f, (float) aimTicks / aimDuration);

        // Smooth ease-in-out interpolation
        float t = smoothStep(progress);

        float newYaw = lerpAngle(startYaw, targetYaw, t);
        float newPitch = startPitch + (targetPitch - startPitch) * t;

        // Add tiny random jitter for human-like imprecision
        if (aimTicks < aimDuration) {
            newYaw += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.3f;
            newPitch += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.2f;
        }

        player.setYaw(newYaw);
        player.setPitch(MathHelper.clamp(newPitch, -90f, 90f));

        if (aimTicks >= aimDuration) {
            state = State.PLACE;
        }
    }

    // -- PLACE: interact to place block --

    private void tickPlace(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world) {
        if (queueIndex >= queue.size()) { state = State.PLANNING; return; }

        BuildQueue.PlaceEntry entry = queue.get(queueIndex);
        BlockPos target = entry.worldPos;

        // Final reach check
        double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(target));
        if (dist > PLACE_REACH + 0.5) {
            // Need to walk closer
            walkTarget = target;
            walkTicks = 0;
            state = State.WALKING;
            return;
        }

        // Find a face to place against
        Direction placeFace = findPlaceFace(world, target);
        if (placeFace == null) {
            queueIndex++;
            state = State.EQUIP;
            return;
        }

        BlockPos neighbor = target.offset(placeFace);
        Vec3d hitPos = Vec3d.ofCenter(neighbor)
            .add(Vec3d.of(placeFace.getOpposite().getVector()).multiply(0.5));

        BlockHitResult hitResult = new BlockHitResult(
            hitPos, placeFace.getOpposite(), neighbor, false
        );

        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        player.swingHand(Hand.MAIN_HAND);

        placedCount++;
        queueIndex++;
        blocksSinceBreak++;

        // Human-like variable cooldown
        if (blocksSinceBreak >= 15 + ThreadLocalRandom.current().nextInt(20)) {
            cooldownTicks = 20 + ThreadLocalRandom.current().nextInt(30);
            blocksSinceBreak = 0;
        } else {
            cooldownTicks = 3 + ThreadLocalRandom.current().nextInt(6);
        }

        state = State.COOLDOWN;
    }

    // -- COOLDOWN --

    private void tickCooldown() {
        cooldownTicks--;
        if (cooldownTicks <= 0) {
            if (queueIndex >= queue.size()) {
                state = State.PLANNING;
            } else {
                state = State.EQUIP;
            }
        }
    }

    // -- Helpers --

    private Direction findPlaceFace(ClientWorld world, BlockPos target) {
        if (!world.getBlockState(target.down()).isAir()) return Direction.DOWN;
        for (Direction dir : new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
            if (!world.getBlockState(target.offset(dir)).isAir()) return dir;
        }
        if (!world.getBlockState(target.up()).isAir()) return Direction.UP;
        return null;
    }

    private void stopMovement() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.input.movementForward = 0;
            mc.player.input.movementSideways = 0;
            mc.player.input.pressingForward = false;
            mc.player.input.jumping = false;
            mc.player.setSprinting(false);
        }
    }

    private boolean shouldJump(ClientPlayerEntity player) {
        // Jump if on ground and horizontal velocity is near zero (stuck against wall)
        if (!player.isOnGround()) return false;

        Vec3d vel = player.getVelocity();
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        return hSpeed < 0.01 && walkTicks > 5;
    }

    private static float lerpAngle(float from, float to, float t) {
        float diff = MathHelper.wrapDegrees(to - from);
        return from + diff * t;
    }

    private static float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }
}
