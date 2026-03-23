package com.prisset.vtools.survey;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates human-like timing delays using multimodal distributions.
 * Anti-cheat systems detect uniform and pure Gaussian distributions.
 * Real human timing is multimodal: mostly fast, sometimes normal,
 * occasionally slow, rarely very slow (distracted).
 */
public final class HumanTiming {

    private HumanTiming() {}

    // --- Delay Categories ---
    // Each category has 4 modes: fast, normal, slow, verySlow
    // Format: {mean, stddev} for each mode
    // Probabilities: 55% fast, 30% normal, 12% slow, 3% verySlow

    private static final float[][] BREAK_TO_BREAK = {
        {2.5f, 0.6f}, {5.0f, 1.5f}, {12.0f, 3.0f}, {25.0f, 8.0f}
    };

    private static final float[][] AIM_SETTLE = {
        {2.0f, 0.5f}, {4.0f, 1.0f}, {8.0f, 2.0f}, {15.0f, 4.0f}
    };

    private static final float[][] WALK_START = {
        {1.5f, 0.4f}, {3.0f, 1.0f}, {7.0f, 2.0f}, {15.0f, 5.0f}
    };

    private static final float[][] WALK_PAUSE = {
        {3.0f, 1.0f}, {8.0f, 2.0f}, {15.0f, 4.0f}, {30.0f, 8.0f}
    };

    private static final float[][] TOOL_SWITCH = {
        {2.0f, 0.5f}, {4.0f, 1.0f}, {8.0f, 2.0f}, {12.0f, 3.0f}
    };

    private static final float[][] IDLE_DURATION = {
        {20.0f, 5.0f}, {40.0f, 10.0f}, {80.0f, 20.0f}, {120.0f, 30.0f}
    };

    private static final float[][] SCAN_DURATION = {
        {10.0f, 3.0f}, {20.0f, 5.0f}, {35.0f, 10.0f}, {50.0f, 15.0f}
    };

    private static final float[][] PRE_BREAK = {
        {2.0f, 0.5f}, {4.0f, 1.2f}, {7.0f, 2.0f}, {12.0f, 3.0f}
    };

    private static final float[][] POST_MINE = {
        {3.0f, 0.8f}, {6.0f, 1.5f}, {10.0f, 3.0f}, {20.0f, 6.0f}
    };

    private static final float[][] DESC_PAUSE = {
        {4.0f, 1.0f}, {8.0f, 2.5f}, {14.0f, 4.0f}, {25.0f, 7.0f}
    };

    // Mode selection weights
    private static final float MODE_FAST = 0.55f;
    private static final float MODE_NORMAL = 0.85f; // cumulative: 0.55 + 0.30
    private static final float MODE_SLOW = 0.97f;   // cumulative: 0.85 + 0.12
    // verySlow = remaining 3%

    // --- Public API ---

    public static int breakToBreak() { return multimodalDelay(BREAK_TO_BREAK); }
    public static int aimSettle() { return multimodalDelay(AIM_SETTLE); }
    public static int walkStart() { return multimodalDelay(WALK_START); }
    public static int walkPause() { return multimodalDelay(WALK_PAUSE); }
    public static int toolSwitch() { return multimodalDelay(TOOL_SWITCH); }
    public static int idleDuration() { return multimodalDelay(IDLE_DURATION); }
    public static int scanDuration() { return multimodalDelay(SCAN_DURATION); }
    public static int preBreak() { return multimodalDelay(PRE_BREAK); }
    public static int postMine() { return multimodalDelay(POST_MINE); }
    public static int descPause() { return multimodalDelay(DESC_PAUSE); }

    /**
     * Multimodal delay: selects one of 4 Gaussian modes
     * based on weighted probability, then samples from that mode.
     */
    public static int multimodalDelay(float[][] modes) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        float roll = rng.nextFloat();

        float mean, stddev;
        if (roll < MODE_FAST) {
            mean = modes[0][0]; stddev = modes[0][1];
        } else if (roll < MODE_NORMAL) {
            mean = modes[1][0]; stddev = modes[1][1];
        } else if (roll < MODE_SLOW) {
            mean = modes[2][0]; stddev = modes[2][1];
        } else {
            mean = modes[3][0]; stddev = modes[3][1];
        }

        float value = (float) (rng.nextGaussian() * stddev + mean);
        return Math.max(1, Math.round(value));
    }

    /**
     * Simple Gaussian delay with bounds.
     */
    public static int gaussianDelay(float mean, float stddev) {
        float value = (float) (ThreadLocalRandom.current().nextGaussian() * stddev + mean);
        return Math.max(1, Math.round(value));
    }

    /**
     * Apply fatigue multiplier to a delay.
     * Lower fatigue = longer delays (slower actions).
     */
    public static int fatigued(int baseTicks, float fatigueMultiplier) {
        if (fatigueMultiplier <= 0.01f) return baseTicks;
        return Math.max(1, Math.round(baseTicks / fatigueMultiplier));
    }

    // --- Idle Trigger ---

    /**
     * Should an idle behavior trigger this tick?
     * Probability increases with blocks mined and session length.
     *
     * @param ticksSinceLastIdle ticks since last idle triggered
     * @param blocksMined total blocks mined this session
     * @param sessionTicks total ticks this session
     * @return true if idle should trigger
     */
    public static boolean shouldTriggerIdle(long ticksSinceLastIdle, int blocksMined,
                                             long sessionTicks) {
        // Minimum cooldown: 20 seconds (400 ticks)
        if (ticksSinceLastIdle < 400) return false;

        // Base chance per tick: 0.15% (roughly every 30-40 seconds)
        float chance = 0.0015f;

        // More blocks = more "tired" = more idle
        chance += blocksMined * 0.00008f;

        // Longer session = more idle
        float minutes = sessionTicks / 1200f;
        chance += minutes * 0.0002f;

        // After long gap without idle, increase chance
        if (ticksSinceLastIdle > 1200) { // 60 seconds
            chance += 0.002f;
        }

        // Cap at 2% per tick
        chance = Math.min(0.02f, chance);

        return ThreadLocalRandom.current().nextFloat() < chance;
    }

    // --- Action Rate Limiting ---

    // Tracks rolling action counts for APM limiting
    private static final int WINDOW_TICKS = 1200; // 60 seconds

    /**
     * Check if an action rate is within human bounds.
     * @param actionsInWindow actions performed in last 60 seconds
     * @param maxPerMinute maximum actions per minute for a human
     * @return true if action is allowed
     */
    public static boolean isRateAllowed(int actionsInWindow, int maxPerMinute) {
        return actionsInWindow < maxPerMinute;
    }

    /**
     * Jittered boolean -- chance-based with variance.
     * Used for things like "should I swing hand this tick?"
     */
    public static boolean chance(float probability) {
        return ThreadLocalRandom.current().nextFloat() < probability;
    }

    /**
     * Random int in range using Gaussian (centered, with tails).
     */
    public static int gaussianRange(int min, int max) {
        float mean = (min + max) / 2.0f;
        float stddev = (max - min) / 4.0f; // 95% within range
        int value = Math.round((float) (ThreadLocalRandom.current().nextGaussian() * stddev + mean));
        return Math.max(min, Math.min(max, value));
    }
}
