package com.prisset.vtools.blueprint;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Human-like builder FSM. Strict layer-by-layer building.
 * Places blocks via real crosshair, verifies placement succeeded,
 * checks support blocks exist before attempting placement.
 *
 * States:
 *   IDLE     - not building
 *   PLANNING - generate layer queue
 *   WALKING  - moving toward target block
 *   EQUIP    - find + equip correct block
 *   AIM      - smoothly rotate head toward neighbor face
 *   VERIFY   - wait for crosshairTarget to align
 *   PLACE    - interact to place block
 *   CONFIRM  - verify block was actually placed, retry or defer if failed
 *   COOLDOWN - human-like delay between placements
 *   DONE     - build complete
 *   PAUSED   - stopped by user
 */
public final class BuilderBot {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final BuilderBot INSTANCE = new BuilderBot();

    private static final double PLACE_REACH = 3.0;
    private static final double WALK_CLOSE_ENOUGH = 2.5;
    private static final int AIM_TICKS_MIN = 3;
    private static final int AIM_TICKS_MAX = 6;
    private static final int WALK_TIMEOUT = 200;
    private static final int AIM_VERIFY_MAX = 10;
    private static final int CONFIRM_WAIT = 3;
    private static final int MAX_RETRIES = 2;

    public enum State {
        IDLE, PLANNING, WALKING, EQUIP, AIM, VERIFY, PLACE, CONFIRM, COOLDOWN, DONE, PAUSED
    }

    private State state = State.IDLE;

    // Layer system
    private List<List<BuildQueue.PlaceEntry>> layers;
    private int currentLayerIdx;
    private int currentBlockIdx;
    private List<BuildQueue.PlaceEntry> deferred; // blocks deferred due to no support

    private int cooldownTicks;
    private int placedCount;
    private int totalCount;
    private int blocksSinceBreak;

    // Aim interpolation
    private float targetYaw, targetPitch;
    private float startYaw, startPitch;
    private int aimTicks, aimDuration;
    private int verifyTicks;
    private int confirmTicks;
    private int retryCount;

    // Target placement info
    private BlockPos placeTarget;
    private BlockPos placeNeighbor;
    private Direction placeFaceDir;
    private String placeExpectedState;

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
        currentLayerIdx = 0;
        currentBlockIdx = 0;
        deferred = new ArrayList<>();
        LOG.info("Builder started");
    }

    public void stop() {
        state = State.IDLE;
        layers = null;
        deferred = null;
        currentLayerIdx = 0;
        currentBlockIdx = 0;
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
        if (state == State.PAUSED) {
            state = State.EQUIP;
        }
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

        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;

        switch (state) {
            case PLANNING -> tickPlanning(world, player);
            case WALKING  -> tickWalking(mc, player);
            case EQUIP    -> tickEquip(mc, player);
            case AIM      -> tickAim(player);
            case VERIFY   -> tickVerify(mc, player);
            case PLACE    -> tickPlace(mc, player, world);
            case CONFIRM  -> tickConfirm(mc, world);
            case COOLDOWN -> tickCooldown();
        }
    }

    // -- PLANNING --

    private void tickPlanning(ClientWorld world, ClientPlayerEntity player) {
        layers = BuildQueue.generateLayers(world, player);
        currentLayerIdx = 0;
        currentBlockIdx = 0;
        if (deferred == null) deferred = new ArrayList<>();
        deferred.clear();

        totalCount = 0;
        for (List<BuildQueue.PlaceEntry> layer : layers) {
            totalCount += layer.size();
        }

        if (layers.isEmpty()) {
            state = State.DONE;
            LOG.info("Build complete! {} blocks placed", placedCount);
            return;
        }

        LOG.info("Build queue: {} blocks in {} layers", totalCount, layers.size());
        state = State.EQUIP;
    }

    // -- EQUIP --

    private void tickEquip(MinecraftClient mc, ClientPlayerEntity player) {
        BuildQueue.PlaceEntry entry = nextEntry(mc);
        if (entry == null) {
            // Try deferred blocks (ones that had no support earlier)
            if (!deferred.isEmpty()) {
                List<BuildQueue.PlaceEntry> retry = new ArrayList<>(deferred);
                deferred.clear();
                // Add as extra layer
                layers.add(retry);
                state = State.EQUIP;
                return;
            }
            state = State.PLANNING;
            return;
        }

        String blockId = entry.getBlockId();

        // Check if there's a support block (at least one non-air neighbor)
        if (!hasSupport(mc.world, entry.worldPos)) {
            // No support: defer this block for later
            deferred.add(entry);
            currentBlockIdx++;
            return;
        }

        // Try to equip
        if (!InventoryHelper.isHolding(player, blockId)) {
            boolean found = InventoryHelper.equipBlock(player, blockId);
            if (!found) {
                currentBlockIdx++;
                return;
            }
            cooldownTicks = 2 + ThreadLocalRandom.current().nextInt(3);
            state = State.COOLDOWN;
            return;
        }

        // Check distance
        double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(entry.worldPos));
        if (dist > PLACE_REACH) {
            walkTarget = entry.worldPos;
            walkTicks = 0;
            state = State.WALKING;
            return;
        }

        // Find placement face
        Direction face = findPlaceFace(mc.world, entry.worldPos);
        if (face == null) {
            deferred.add(entry);
            currentBlockIdx++;
            return;
        }

        // Set up aim
        placeTarget = entry.worldPos;
        placeExpectedState = entry.blockState;
        placeNeighbor = entry.worldPos.offset(face);
        placeFaceDir = face.getOpposite();
        retryCount = 0;

        Vec3d faceCenter = Vec3d.ofCenter(placeNeighbor)
            .add(Vec3d.of(placeFaceDir.getVector()).multiply(0.5));
        beginAim(player, faceCenter);
        state = State.AIM;
    }

    private BuildQueue.PlaceEntry nextEntry(MinecraftClient mc) {
        while (currentLayerIdx < layers.size()) {
            List<BuildQueue.PlaceEntry> layer = layers.get(currentLayerIdx);

            while (currentBlockIdx < layer.size()) {
                BuildQueue.PlaceEntry entry = layer.get(currentBlockIdx);

                if (mc.world != null) {
                    String actual = SchematicData.encodeState(mc.world.getBlockState(entry.worldPos));
                    if (entry.blockState.equals(actual)) {
                        currentBlockIdx++;
                        continue;
                    }
                }
                return entry;
            }

            currentLayerIdx++;
            currentBlockIdx = 0;
        }

        return null;
    }

    // -- WALKING --

    private void tickWalking(MinecraftClient mc, ClientPlayerEntity player) {
        if (walkTarget == null) {
            releaseKeys();
            state = State.EQUIP;
            return;
        }

        walkTicks++;

        if (walkTicks > WALK_TIMEOUT) {
            LOG.warn("Walk timeout at {}", walkTarget);
            releaseKeys();
            currentBlockIdx++;
            state = State.EQUIP;
            return;
        }

        Vec3d playerPos = player.getPos();
        Vec3d targetCenter = Vec3d.ofCenter(walkTarget);
        double dx = targetCenter.x - playerPos.x;
        double dz = targetCenter.z - playerPos.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        if (distXZ <= WALK_CLOSE_ENOUGH) {
            releaseKeys();
            state = State.EQUIP;
            return;
        }

        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.setYaw(lerpAngle(player.getYaw(), desiredYaw, 0.25f));

        mc.options.forwardKey.setPressed(true);

        if (shouldJump(player)) {
            mc.options.jumpKey.setPressed(true);
        } else {
            mc.options.jumpKey.setPressed(false);
        }

        mc.options.sprintKey.setPressed(distXZ > 6.0);
    }

    // -- AIM --

    private void beginAim(ClientPlayerEntity player, Vec3d aimPoint) {
        Vec3d eyes = player.getEyePos();
        double dx = aimPoint.x - eyes.x;
        double dy = aimPoint.y - eyes.y;
        double dz = aimPoint.z - eyes.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        startYaw = player.getYaw();
        startPitch = player.getPitch();
        targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        targetPitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));

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
        aimTicks++;
        float progress = Math.min(1.0f, (float) aimTicks / aimDuration);
        float t = smoothStep(progress);

        float newYaw = lerpAngle(startYaw, targetYaw, t);
        float newPitch = startPitch + (targetPitch - startPitch) * t;

        if (aimTicks < aimDuration) {
            newYaw += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.3f;
            newPitch += (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.2f;
        }

        player.setYaw(newYaw);
        player.setPitch(MathHelper.clamp(newPitch, -90f, 90f));

        if (aimTicks >= aimDuration) {
            verifyTicks = 0;
            state = State.VERIFY;
        }
    }

    // -- VERIFY --

    private void tickVerify(MinecraftClient mc, ClientPlayerEntity player) {
        verifyTicks++;
        if (verifyTicks < 2) return;

        HitResult hit = mc.crosshairTarget;

        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos wouldPlaceAt = blockHit.getBlockPos().offset(blockHit.getSide());

            if (wouldPlaceAt.equals(placeTarget)) {
                state = State.PLACE;
                return;
            }
        }

        if (verifyTicks > AIM_VERIFY_MAX) {
            currentBlockIdx++;
            state = State.EQUIP;
            return;
        }

        // Nudge aim
        Vec3d faceCenter = Vec3d.ofCenter(placeNeighbor)
            .add(Vec3d.of(placeFaceDir.getVector()).multiply(0.5));
        Vec3d eyes = player.getEyePos();
        double dx = faceCenter.x - eyes.x;
        double dy = faceCenter.y - eyes.y;
        double dz = faceCenter.z - eyes.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float wantPitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));

        player.setYaw(lerpAngle(player.getYaw(), wantYaw, 0.5f));
        player.setPitch(MathHelper.clamp(
            player.getPitch() + (wantPitch - player.getPitch()) * 0.5f, -90f, 90f));
    }

    // -- PLACE --

    private void tickPlace(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world) {
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            currentBlockIdx++;
            state = State.EQUIP;
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hit;

        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, blockHit);
        player.swingHand(Hand.MAIN_HAND);

        // Don't increment yet, wait for CONFIRM
        confirmTicks = 0;
        state = State.CONFIRM;
    }

    // -- CONFIRM: verify block actually placed --

    private void tickConfirm(MinecraftClient mc, ClientWorld world) {
        confirmTicks++;
        if (confirmTicks < CONFIRM_WAIT) return;

        // Check if block is now at target position
        String actual = SchematicData.encodeState(world.getBlockState(placeTarget));
        boolean placed = !world.getBlockState(placeTarget).isAir();

        if (placed) {
            // Block placed
            placedCount++;
            currentBlockIdx++;
            blocksSinceBreak++;
            retryCount = 0;

            // Human-like variable cooldown
            if (blocksSinceBreak >= 15 + ThreadLocalRandom.current().nextInt(20)) {
                cooldownTicks = 20 + ThreadLocalRandom.current().nextInt(30);
                blocksSinceBreak = 0;
            } else {
                cooldownTicks = 3 + ThreadLocalRandom.current().nextInt(6);
            }
            state = State.COOLDOWN;
            return;
        }

        // Block NOT placed
        retryCount++;

        if (retryCount > MAX_RETRIES) {
            // Check support: if no neighbor support, defer
            if (!hasSupport(world, placeTarget)) {
                LOG.warn("No support at {}, deferring", placeTarget);
                // Find the entry and defer it
                if (currentBlockIdx < layers.get(currentLayerIdx).size()) {
                    deferred.add(layers.get(currentLayerIdx).get(currentBlockIdx));
                }
            } else {
                LOG.warn("Failed to place at {} after {} retries, skipping", placeTarget, retryCount);
            }
            currentBlockIdx++;
            retryCount = 0;
            state = State.EQUIP;
            return;
        }

        // Retry: go back to equip (re-aim + re-place)
        LOG.debug("Block not placed at {}, retry {}", placeTarget, retryCount);
        cooldownTicks = 2;
        state = State.COOLDOWN;
    }

    // -- COOLDOWN --

    private void tickCooldown() {
        cooldownTicks--;
        if (cooldownTicks <= 0) {
            state = State.EQUIP;
        }
    }

    // -- Helpers --

    /**
     * Check if target position has at least one non-air neighbor (support to place against).
     */
    private boolean hasSupport(ClientWorld world, BlockPos target) {
        for (Direction dir : Direction.values()) {
            if (!world.getBlockState(target.offset(dir)).isAir()) {
                return true;
            }
        }
        return false;
    }

    private Direction findPlaceFace(ClientWorld world, BlockPos target) {
        if (!world.getBlockState(target.down()).isAir()) return Direction.DOWN;
        for (Direction dir : new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
            if (!world.getBlockState(target.offset(dir)).isAir()) return dir;
        }
        if (!world.getBlockState(target.up()).isAir()) return Direction.UP;
        return null;
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

    private boolean shouldJump(ClientPlayerEntity player) {
        if (!player.isOnGround()) return false;
        Vec3d vel = player.getVelocity();
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        return hSpeed < 0.03 && walkTicks > 5;
    }

    private static float lerpAngle(float from, float to, float t) {
        float diff = MathHelper.wrapDegrees(to - from);
        return from + diff * t;
    }

    private static float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }
}
