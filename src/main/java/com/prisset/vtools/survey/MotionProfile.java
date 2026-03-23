package com.prisset.vtools.survey;

/**
 * Mathematical motion profiles for natural-looking movement.
 * Bezier curves, easing functions, acceleration/deceleration profiles.
 */
public final class MotionProfile {

    private MotionProfile() {}

    // --- Cubic Bezier ---

    /**
     * Cubic Bezier interpolation between p0 and p3 with control points p1, p2.
     * @param t progress 0.0 - 1.0
     */
    public static float cubicBezier(float t, float p0, float p1, float p2, float p3) {
        float u = 1.0f - t;
        float tt = t * t;
        float uu = u * u;
        return uu * u * p0 + 3 * uu * t * p1 + 3 * u * tt * p2 + tt * t * p3;
    }

    // --- Easing Functions ---

    /**
     * Smooth S-curve (ease-in-out). Standard for natural motion.
     * Slow start, fast middle, slow end.
     */
    public static float easeInOut(float t) {
        t = clamp01(t);
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Smoother S-curve (quintic). Even more natural.
     */
    public static float easeInOutSmooth(float t) {
        t = clamp01(t);
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    /**
     * Ease-in-out with overshoot at the end (back easing).
     * Goes slightly past target, then settles. Perfect for head rotation.
     * Overshoot is ~10% of the range.
     */
    public static float easeInOutBack(float t) {
        t = clamp01(t);
        float s = 1.70158f * 1.1f; // overshoot amount
        if (t < 0.5f) {
            float t2 = t * 2.0f;
            return 0.5f * (t2 * t2 * ((s + 1) * t2 - s));
        } else {
            float t2 = t * 2.0f - 2.0f;
            return 0.5f * (t2 * t2 * ((s + 1) * t2 + s) + 2.0f);
        }
    }

    /**
     * Ease-out with elastic bounce. Subtle oscillation at end.
     * Good for micro-correction after rotation overshoot.
     */
    public static float easeOutElastic(float t) {
        t = clamp01(t);
        if (t == 0 || t == 1) return t;
        float p = 0.3f;
        float s = p / 4.0f;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t - s) * (2 * Math.PI) / p) + 1.0f);
    }

    /**
     * Simple ease-in (accelerating).
     */
    public static float easeIn(float t) {
        t = clamp01(t);
        return t * t;
    }

    /**
     * Simple ease-out (decelerating).
     */
    public static float easeOut(float t) {
        t = clamp01(t);
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    // --- Acceleration Profiles ---

    /**
     * Generate a natural acceleration curve over N ticks.
     * Returns array of speed values [0] to [ticks-1], range 0.0 to 1.0.
     */
    public static float[] accelerationCurve(int ticks) {
        if (ticks <= 0) return new float[]{1.0f};
        float[] curve = new float[ticks];
        for (int i = 0; i < ticks; i++) {
            float t = (float) (i + 1) / ticks;
            curve[i] = easeInOutSmooth(t);
        }
        return curve;
    }

    /**
     * Generate a natural deceleration curve over N ticks.
     * Returns array of speed values [0] to [ticks-1], range 1.0 to 0.0.
     */
    public static float[] decelerationCurve(int ticks) {
        if (ticks <= 0) return new float[]{0.0f};
        float[] curve = new float[ticks];
        for (int i = 0; i < ticks; i++) {
            float t = (float) (i + 1) / ticks;
            curve[i] = 1.0f - easeInOutSmooth(t);
        }
        return curve;
    }

    // --- Overshoot Path ---

    /**
     * Generate intermediate angle values for rotation with overshoot.
     * Path: start -> (end + overshoot) -> end
     *
     * @param start starting angle
     * @param end target angle
     * @param overshootDeg how far past the target (degrees)
     * @param mainTicks ticks for main rotation
     * @param correctionTicks ticks for overshoot correction
     * @return array of angle values per tick
     */
    public static float[] overshootPath(float start, float end, float overshootDeg,
                                         int mainTicks, int correctionTicks) {
        int total = mainTicks + correctionTicks;
        float[] path = new float[total];

        float overshootTarget = end + overshootDeg;

        // Main rotation: start -> overshootTarget
        for (int i = 0; i < mainTicks; i++) {
            float t = (float) (i + 1) / mainTicks;
            float eased = easeInOutSmooth(t);
            path[i] = start + (overshootTarget - start) * eased;
        }

        // Correction: overshootTarget -> end
        for (int i = 0; i < correctionTicks; i++) {
            float t = (float) (i + 1) / correctionTicks;
            float eased = easeOutElastic(t);
            path[mainTicks + i] = overshootTarget + (end - overshootTarget) * eased;
        }

        return path;
    }

    /**
     * Lerp with clamped t.
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    /**
     * Angle lerp (handles 360 wrapping).
     */
    public static float lerpAngle(float a, float b, float t) {
        float delta = wrapDegrees(b - a);
        return a + delta * clamp01(t);
    }

    /**
     * Wrap angle to [-180, 180].
     */
    public static float wrapDegrees(float angle) {
        angle %= 360f;
        if (angle > 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
