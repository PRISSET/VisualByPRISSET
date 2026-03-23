package com.prisset.vtools.survey;

import net.minecraft.client.network.ClientPlayerEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates realistic idle behaviors between mining actions.
 * Anti-cheat systems flag 100% action uptime.
 * This component adds human-like pauses, look-arounds, jumps.
 */
public final class IdleBehavior {

    public enum IdleType {
        LOOK_LEFT,
        LOOK_RIGHT,
        LOOK_UP,
        LOOK_DOWN,
        SCAN_WALLS,
        MICRO_PAUSE,
        LONG_PAUSE,
        JUMP,
        STEP_BACK,
        LOOK_BEHIND,
        CROUCH_PEEK
    }

    private boolean active;
    private IdleType currentType;
    private int totalTicks;
    private int currentTick;
    private boolean driftStarted;
    private boolean actionDone;

    // Sneak output for MovementInputMixin
    private boolean sneakActive;

    public IdleBehavior() {}

    public boolean isActive() { return active; }
    public boolean wantSneak() { return sneakActive; }

    /**
     * Start an idle behavior. Selects type based on context.
     */
    public void start(int blocksMined, long sessionTicks) {
        currentType = selectType(blocksMined, sessionTicks);
        currentTick = 0;
        driftStarted = false;
        actionDone = false;
        sneakActive = false;
        active = true;

        switch (currentType) {
            case LOOK_LEFT, LOOK_RIGHT -> totalTicks = HumanTiming.gaussianDelay(28, 6);
            case LOOK_UP -> totalTicks = HumanTiming.gaussianDelay(20, 4);
            case LOOK_DOWN -> totalTicks = HumanTiming.gaussianDelay(18, 4);
            case SCAN_WALLS -> totalTicks = HumanTiming.gaussianDelay(40, 10);
            case MICRO_PAUSE -> totalTicks = HumanTiming.gaussianDelay(25, 8);
            case LONG_PAUSE -> totalTicks = HumanTiming.gaussianDelay(60, 15);
            case JUMP -> totalTicks = HumanTiming.gaussianDelay(12, 3);
            case STEP_BACK -> totalTicks = HumanTiming.gaussianDelay(20, 5);
            case LOOK_BEHIND -> totalTicks = HumanTiming.gaussianDelay(45, 10);
            case CROUCH_PEEK -> totalTicks = HumanTiming.gaussianDelay(15, 4);
        }
        totalTicks = Math.max(8, totalTicks);
    }

    /**
     * Tick the idle behavior. Returns true when complete.
     * @param aim the aim helper for look-around behaviors
     * @param walk the walk helper for movement behaviors
     */
    public boolean tick(ClientPlayerEntity player, AimHelper aim, WalkHelper walk) {
        if (!active) return true;

        currentTick++;

        switch (currentType) {
            case LOOK_LEFT -> {
                if (!driftStarted) {
                    float amp = HumanTiming.gaussianDelay(20, 5);
                    aim.startDrift(-amp, amp * 0.15f, totalTicks);
                    driftStarted = true;
                }
                if (currentTick >= totalTicks) return finish();
            }

            case LOOK_RIGHT -> {
                if (!driftStarted) {
                    float amp = HumanTiming.gaussianDelay(20, 5);
                    aim.startDrift(amp, amp * 0.15f, totalTicks);
                    driftStarted = true;
                }
                if (currentTick >= totalTicks) return finish();
            }

            case LOOK_UP -> {
                if (!driftStarted) {
                    aim.startDrift(
                        (ThreadLocalRandom.current().nextFloat() - 0.5f) * 6f,
                        -15f - ThreadLocalRandom.current().nextFloat() * 10f,
                        totalTicks
                    );
                    driftStarted = true;
                }
                if (currentTick >= totalTicks) return finish();
            }

            case LOOK_DOWN -> {
                if (!driftStarted) {
                    aim.startDrift(
                        (ThreadLocalRandom.current().nextFloat() - 0.5f) * 4f,
                        15f + ThreadLocalRandom.current().nextFloat() * 10f,
                        totalTicks
                    );
                    driftStarted = true;
                }
                if (currentTick >= totalTicks) return finish();
            }

            case SCAN_WALLS -> {
                if (!driftStarted) {
                    float amp = 30f + ThreadLocalRandom.current().nextFloat() * 30f;
                    aim.startDrift(amp, 5f, totalTicks);
                    driftStarted = true;
                }
                if (currentTick >= totalTicks) return finish();
            }

            case MICRO_PAUSE, LONG_PAUSE -> {
                // Just wait. Aim jitter handles micro-movement.
                if (currentTick >= totalTicks) return finish();
            }

            case JUMP -> {
                if (!actionDone && currentTick >= 3) {
                    walk.wantJump = true;
                    actionDone = true;
                } else if (actionDone && currentTick >= 5) {
                    walk.wantJump = false;
                }
                if (currentTick >= totalTicks) return finish();
            }

            case STEP_BACK -> {
                // Brief backward movement
                if (currentTick >= 3 && currentTick <= 8) {
                    walk.wantForward = -0.3f;
                } else {
                    walk.wantForward = 0;
                }
                if (currentTick >= totalTicks) {
                    walk.wantForward = 0;
                    return finish();
                }
            }

            case LOOK_BEHIND -> {
                if (!driftStarted) {
                    float dir = ThreadLocalRandom.current().nextBoolean() ? 1f : -1f;
                    float amp = 120f + ThreadLocalRandom.current().nextFloat() * 40f;
                    aim.startDrift(amp * dir, -5f, totalTicks);
                    driftStarted = true;
                }
                if (currentTick >= totalTicks) return finish();
            }

            case CROUCH_PEEK -> {
                if (currentTick >= 2 && currentTick <= totalTicks - 3) {
                    sneakActive = true;
                } else {
                    sneakActive = false;
                }
                if (currentTick >= totalTicks) {
                    sneakActive = false;
                    return finish();
                }
            }
        }

        return false;
    }

    private boolean finish() {
        active = false;
        sneakActive = false;
        return true;
    }

    /**
     * Force stop idle behavior.
     */
    public void stop() {
        active = false;
        sneakActive = false;
    }

    /**
     * Select idle type based on context (weighted random).
     */
    private static IdleType selectType(int blocksMined, long sessionTicks) {
        float minutes = sessionTicks / 1200f;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        float roll = rng.nextFloat();

        // Long session: more long pauses and look-behinds
        if (minutes > 15 && roll < 0.12f) return IdleType.LONG_PAUSE;
        if (minutes > 10 && roll < 0.08f) return IdleType.LOOK_BEHIND;

        // Normal distribution
        if (roll < 0.22f) return IdleType.MICRO_PAUSE;
        if (roll < 0.37f) return rng.nextBoolean() ? IdleType.LOOK_LEFT : IdleType.LOOK_RIGHT;
        if (roll < 0.48f) return IdleType.LOOK_DOWN;
        if (roll < 0.58f) return IdleType.SCAN_WALLS;
        if (roll < 0.66f) return IdleType.LOOK_UP;
        if (roll < 0.75f) return IdleType.JUMP;
        if (roll < 0.82f) return IdleType.LONG_PAUSE;
        if (roll < 0.88f) return IdleType.STEP_BACK;
        if (roll < 0.94f) return IdleType.CROUCH_PEEK;
        return IdleType.LOOK_BEHIND;
    }
}
