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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Human-like builder FSM. Strict layer-by-layer building.
 * Places blocks only via real crosshair (aims at neighbor face, then RMB).
 *
 * Layer logic: fully completes Y=0 before moving to Y=1, etc.
 * Within a layer, picks nearest reachable block.
 * If block is out of reach, walks to it first.
 * If block material is missing, skips it (stays in same layer).
 * Placement: aims head at the solid neighbor face, waits for crosshairTarget
 * to match, then triggers use-item (same as player pressing RMB).
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

    public enum State {
        IDLE, PLANNING, WALKING, EQUIP, AIM, VERIFY, PLACE, COOLDOWN, DONE, PAUSED
    }

    private State state = State.IDLE;

    // Layer system
    private List<List<BuildQueue.PlaceEntry>> layers;
    private int currentLayerIdx;
    private int currentBlockIdx;

    private int cooldownTicks;
    private int placedCount;
    private int totalCount;
    private int blocksSinceBreak;

    // Aim interpolation
    private float targetYaw, targetPitch;
    private float startYaw, startPitch;
    private int aimTicks, aimDuration;
    private int verifyTicks;

    // Target placement info (for crosshair verification)
    private BlockPos placeTarget;      // where the new block goes
    private BlockPos placeNeighbor;    // the solid block we click on
    private Direction placeFaceDir;    // the face of neighbor we click

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
        LOG.info("Builder started");
    }

    public void stop() {
        state = State.IDLE;
        layers = null;
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
            case COOLDOWN -> tickCooldown();
        }
    }

    // -- PLANNING: generate layer-based queue --

    private void tickPlanning(ClientWorld world, ClientPlayerEntity player) {
        layers = BuildQueue.generateLayers(world, player);
        currentLayerIdx = 0;
        currentBlockIdx = 0;

        // Count total
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

    // -- EQUIP: find next block in current layer, equip it --

    private void tickEquip(MinecraftClient mc, ClientPlayerEntity player) {
        // Advance to next available block in current layer
        BuildQueue.PlaceEntry entry = nextEntry(mc);
        if (entry == null) {
            // All layers done, re-plan to catch any missed
            state = State.PLANNING;
            return;
        }

        String blockId = entry.getBlockId();

        // Try to equip
        if (!InventoryHelper.isHolding(player, blockId)) {
            boolean found = InventoryHelper.equipBlock(player, blockId);
            if (!found) {
                // No such block in inventory, skip this entry
                currentBlockIdx++;
                return;
            }
            cooldownTicks = 2 + ThreadLocalRandom.current().nextInt(3);
            state = State.COOLDOWN;
            return;
        }

        // Block in hand, check distance
        double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(entry.worldPos));
        if (dist > PLACE_REACH) {
            walkTarget = entry.worldPos;
            walkTicks = 0;
            state = State.WALKING;
            return;
        }

        // Find placement face (solid neighbor to click on)
        Direction face = findPlaceFace(mc.world, entry.worldPos);
        if (face == null) {
            // No neighbor to place against, skip for now
            currentBlockIdx++;
            return;
        }

        // Set up aim toward the neighbor face
        placeTarget = entry.worldPos;
        placeNeighbor = entry.worldPos.offset(face);
        placeFaceDir = face.getOpposite(); // the face of neighbor we look at

        // Calculate aim point: center of the face on the neighbor block
        Vec3d faceCenter = Vec3d.ofCenter(placeNeighbor)
            .add(Vec3d.of(placeFaceDir.getVector()).multiply(0.5));
        beginAim(player, faceCenter);
        state = State.AIM;
    }

    /**
     * Get the next valid entry: skip already-placed blocks.
     * Stays within current layer until it's empty, then advances.
     */
    private BuildQueue.PlaceEntry nextEntry(MinecraftClient mc) {
        while (currentLayerIdx < layers.size()) {
            List<BuildQueue.PlaceEntry> layer = layers.get(currentLayerIdx);

            while (currentBlockIdx < layer.size()) {
                BuildQueue.PlaceEntry entry = layer.get(currentBlockIdx);

                // Check if already correctly placed
                if (mc.world != null) {
                    String actual = SchematicData.encodeState(mc.world.getBlockState(entry.worldPos));
                    if (entry.blockState.equals(actual)) {
                        currentBlockIdx++;
                        continue;
                    }
                }
                return entry;
            }

            // Layer finished, move to next
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
            LOG.warn("Walk timeout, skipping block at {}", walkTarget);
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

            double eyeDist = player.getEyePos().distanceTo(targetCenter);
            if (eyeDist <= PLACE_REACH) {
                state = State.EQUIP; // re-enter equip to set up aim
            } else {
                currentBlockIdx++;
                state = State.EQUIP;
            }
            return;
        }

        // Face toward target
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.setYaw(lerpAngle(player.getYaw(), desiredYaw, 0.25f));

        // Walk forward
        mc.options.forwardKey.setPressed(true);

        // Jump if stuck
        if (shouldJump(player)) {
            mc.options.jumpKey.setPressed(true);
        } else {
            mc.options.jumpKey.setPressed(false);
        }

        // Sprint for long distances
        mc.options.sprintKey.setPressed(distXZ > 6.0);
    }

    // -- AIM: smoothly rotate head toward the face we want to click --

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

        // Tiny jitter for human-like imprecision
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

    // -- VERIFY: wait 1-2 ticks for crosshairTarget to update, then check it --

    private void tickVerify(MinecraftClient mc, ClientPlayerEntity player) {
        verifyTicks++;

        // crosshairTarget updates per-frame, give it a tick to settle
        if (verifyTicks < 2) return;

        HitResult hit = mc.crosshairTarget;

        // Check if crosshair is pointing at the correct neighbor block's face
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos hitPos = blockHit.getBlockPos();
            Direction hitSide = blockHit.getSide();

            // The block we'd place at = hitPos offset by hitSide
            BlockPos wouldPlaceAt = hitPos.offset(hitSide);

            if (wouldPlaceAt.equals(placeTarget)) {
                // Crosshair is correct, place!
                state = State.PLACE;
                return;
            }
        }

        // Crosshair not aligned yet
        if (verifyTicks > AIM_VERIFY_MAX) {
            // Gave up waiting, try to re-aim or skip
            currentBlockIdx++;
            state = State.EQUIP;
            return;
        }

        // Nudge aim slightly toward target
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

    // -- PLACE: use the real crosshairTarget to place (like pressing RMB) --

    private void tickPlace(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world) {
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            currentBlockIdx++;
            state = State.EQUIP;
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hit;

        // Place via interactBlock using the REAL crosshair hit result
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, blockHit);
        player.swingHand(Hand.MAIN_HAND);

        placedCount++;
        currentBlockIdx++;
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
            state = State.EQUIP;
        }
    }

    // -- Helpers --

    /**
     * Find a solid neighbor direction to place against.
     * Returns the direction FROM target TO the solid neighbor.
     */
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
