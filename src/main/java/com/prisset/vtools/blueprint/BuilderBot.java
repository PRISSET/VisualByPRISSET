package com.prisset.vtools.blueprint;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Automatic builder FSM. Places blocks one by one from the build queue.
 *
 * States:
 *   IDLE      -- not building
 *   PLANNING  -- generate queue from schematic
 *   EQUIP     -- find + equip correct block
 *   AIM       -- rotate player to look at placement target
 *   PLACE     -- right-click to place block
 *   COOLDOWN  -- wait between placements (human-like delay)
 *   DONE      -- build complete
 *   PAUSED    -- temporarily stopped
 *
 * Delays between actions simulate human behavior:
 *   - Variable 3-8 tick delay between placements
 *   - Occasional longer pauses (every 15-30 blocks)
 *   - Equip delay 2-4 ticks when switching items
 */
public final class BuilderBot {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final BuilderBot INSTANCE = new BuilderBot();

    public enum State {
        IDLE, PLANNING, EQUIP, AIM, PLACE, COOLDOWN, DONE, PAUSED
    }

    private State state = State.IDLE;
    private List<BuildQueue.PlaceEntry> queue;
    private int queueIndex;
    private int cooldownTicks;
    private int aimTicks;
    private int placedCount;
    private int totalCount;
    private int blocksSinceBreak; // for periodic pauses

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
        LOG.info("Builder stopped ({} placed)", placedCount);
    }

    public void pause() {
        if (state != State.IDLE && state != State.DONE) {
            state = State.PAUSED;
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
        if (mc.currentScreen != null) return; // don't build while menu open

        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;

        switch (state) {
            case PLANNING -> tickPlanning(world);
            case EQUIP -> tickEquip(player);
            case AIM -> tickAim(player);
            case PLACE -> tickPlace(mc, player, world);
            case COOLDOWN -> tickCooldown();
        }
    }

    private void tickPlanning(ClientWorld world) {
        queue = BuildQueue.generate(world);
        queueIndex = 0;
        totalCount = queue.size();

        if (queue.isEmpty()) {
            state = State.DONE;
            LOG.info("Build complete! {} blocks placed", placedCount);
            return;
        }

        LOG.info("Build queue: {} blocks to place", totalCount);
        state = State.EQUIP;
    }

    private void tickEquip(ClientPlayerEntity player) {
        if (queueIndex >= queue.size()) {
            state = State.PLANNING; // re-check for missed blocks
            return;
        }

        BuildQueue.PlaceEntry entry = queue.get(queueIndex);
        String blockId = entry.getBlockId();

        if (InventoryHelper.isHolding(player, blockId)) {
            state = State.AIM;
            aimTicks = 0;
            return;
        }

        boolean found = InventoryHelper.equipBlock(player, blockId);
        if (!found) {
            // Block not in inventory, skip this entry
            LOG.warn("Missing block: {}, skipping", blockId);
            queueIndex++;
            return;
        }

        // Wait a tick for inventory to sync
        cooldownTicks = 2 + ThreadLocalRandom.current().nextInt(3);
        state = State.COOLDOWN;
    }

    private void tickAim(ClientPlayerEntity player) {
        if (queueIndex >= queue.size()) { state = State.PLANNING; return; }

        BuildQueue.PlaceEntry entry = queue.get(queueIndex);
        BlockPos target = entry.worldPos;

        // Look at block center
        Vec3d eyes = player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(target);
        double dx = blockCenter.x - eyes.x;
        double dy = blockCenter.y - eyes.y;
        double dz = blockCenter.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float)(Math.toDegrees(-Math.atan2(dy, dist)));

        player.setYaw(yaw);
        player.setPitch(pitch);

        aimTicks++;
        if (aimTicks >= 2) {
            state = State.PLACE;
        }
    }

    private void tickPlace(MinecraftClient mc, ClientPlayerEntity player, ClientWorld world) {
        if (queueIndex >= queue.size()) { state = State.PLANNING; return; }

        BuildQueue.PlaceEntry entry = queue.get(queueIndex);
        BlockPos target = entry.worldPos;

        // Check reach distance
        double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(target));
        if (dist > 5.0) {
            // Too far, skip (builder will need to be closer in session 4)
            queueIndex++;
            state = State.EQUIP;
            return;
        }

        // Find a face to place against (prefer solid neighbor)
        Direction placeFace = findPlaceFace(world, target);
        if (placeFace == null) {
            // No neighbor to place against, skip
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

        // Place block via interaction
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        player.swingHand(Hand.MAIN_HAND);

        placedCount++;
        queueIndex++;
        blocksSinceBreak++;

        // Determine cooldown
        if (blocksSinceBreak >= 15 + ThreadLocalRandom.current().nextInt(20)) {
            // Periodic longer pause (like a human resting)
            cooldownTicks = 20 + ThreadLocalRandom.current().nextInt(30);
            blocksSinceBreak = 0;
        } else {
            cooldownTicks = 3 + ThreadLocalRandom.current().nextInt(6);
        }

        state = State.COOLDOWN;
    }

    private void tickCooldown() {
        cooldownTicks--;
        if (cooldownTicks <= 0) {
            if (queueIndex >= queue.size()) {
                state = State.PLANNING; // re-generate to check for remaining
            } else {
                state = State.EQUIP;
            }
        }
    }

    /**
     * Find a solid face to place the block against.
     * Checks all 6 directions for a non-air neighbor.
     * Prefers DOWN (place on top of block below).
     */
    private Direction findPlaceFace(ClientWorld world, BlockPos target) {
        // Prefer placing on top of block below
        if (!world.getBlockState(target.down()).isAir()) return Direction.DOWN;
        // Then sides
        for (Direction dir : new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
            if (!world.getBlockState(target.offset(dir)).isAir()) return dir;
        }
        // Up last
        if (!world.getBlockState(target.up()).isAir()) return Direction.UP;
        return null;
    }
}
