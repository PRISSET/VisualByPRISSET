package com.prisset.vtools.survey;

/**
 * Simulates player fatigue over a mining session.
 * Real players start fast, slow down, get bursts of energy, slow again.
 * Anti-cheat flags constant action rate over long periods.
 */
public final class FatigueModel {

    private long sessionStartTick;

    public FatigueModel() {}

    public void reset(long currentTick) {
        sessionStartTick = currentTick;
    }

    /**
     * Speed multiplier based on session duration.
     * 1.0 = fresh, <1.0 = fatigued (delays increase).
     * Fluctuates with wave pattern (energy bursts).
     *
     * @param currentTick current world tick
     * @return multiplier 0.5 - 1.15
     */
    public float getSpeedMultiplier(long currentTick) {
        float sessionTicks = currentTick - sessionStartTick;
        float minutes = sessionTicks / 1200f;

        // Base fatigue curve: slowly decreasing
        float baseFatigue = 1.0f - (minutes / 60f) * 0.3f;
        baseFatigue = Math.max(0.6f, baseFatigue);

        // Wave overlay: energy bursts every ~7 minutes
        float wave = (float) Math.sin(minutes * 0.9) * 0.08f;

        // Perlin spike: random energy fluctuation
        float spike = NoiseGenerator.noise1D(minutes * 0.3f) * 0.06f;

        float result = baseFatigue + wave + spike;
        return Math.max(0.5f, Math.min(1.15f, result));
    }

    /**
     * Idle chance multiplier. Increases with session length.
     * More idle = more natural for longer sessions.
     */
    public float getIdleChanceMultiplier(long currentTick) {
        float minutes = (currentTick - sessionStartTick) / 1200f;
        return 1.0f + (minutes / 30f) * 0.5f;
    }

    /**
     * Accuracy penalty. Aiming jitter increases with session length.
     * Fresh player: precise. Tired player: slightly shakier.
     */
    public float getAccuracyMultiplier(long currentTick) {
        float minutes = (currentTick - sessionStartTick) / 1200f;
        return 1.0f + (minutes / 40f) * 0.3f;
    }

    /**
     * Session duration in minutes.
     */
    public float getSessionMinutes(long currentTick) {
        return (currentTick - sessionStartTick) / 1200f;
    }
}
