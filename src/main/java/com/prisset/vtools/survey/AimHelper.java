package com.prisset.vtools.survey;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Human-like camera rotation controller.
 *
 * Key anti-cheat principles:
 * - NO raw packet sending (no PlayerMoveC2SPacket.LookAndOnGround)
 * - All rotation via player.setYaw()/setPitch() -- vanilla handles packets
 * - Multi-tick Bezier rotation with ease-in-out
 * - Overshoot on large rotations (human overcorrects)
 * - Perlin noise jitter ALWAYS active (simulates hand on mouse)
 * - Target offset: never aims at exact block center
 */
public final class AimHelper {

    public enum AimState {
        IDLE,       // not aiming, only jitter active
        ROTATING,   // active Bezier rotation toward target
        SETTLING,   // correcting overshoot after rotation
        LOCKED,     // on target, holding position with jitter
        DRIFTING    // idle look-around (slow drift)
    }

    private AimState state = AimState.IDLE;

    // Target
    private float targetYaw, targetPitch;

    // Rotation plan
    private float startYaw, startPitch;
    private int rotationTicks;
    private int currentRotTick;

    // Overshoot
    private float overshootYaw, overshootPitch;
    private int settlingTicks;
    private int settlingTick;

    // Drift (idle look-around)
    private float driftBaseYaw, driftBasePitch;
    private float driftAmplitudeYaw, driftAmplitudePitch;
    private int driftTotalTicks;
    private int driftTick;

    public AimHelper() {}

    public AimState getState() { return state; }

    // --- Public API ---

    /**
     * Begin rotation toward a block position.
     * Calculates Bezier path with overshoot for large angles.
     * Does NOT aim at exact center -- adds human offset.
     */
    public void aimAt(ClientPlayerEntity player, BlockPos pos) {
        Vec3d eyes = player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(pos);

        // Human imprecision: offset from exact center
        float offsetX = NoiseGenerator.aimOffset(pos.hashCode(), 0.0f);
        float offsetY = NoiseGenerator.aimOffset(pos.hashCode(), 1.0f) * 0.6f;
        float offsetZ = NoiseGenerator.aimOffset(pos.hashCode(), 2.0f);
        Vec3d aimPoint = blockCenter.add(offsetX, offsetY, offsetZ);

        float yaw = calcYaw(eyes, aimPoint);
        float pitch = calcPitch(eyes, aimPoint);

        beginRotation(player, yaw, pitch);
    }

    /**
     * Face a specific direction (for walking).
     */
    public void face(ClientPlayerEntity player, float yaw, float pitch) {
        beginRotation(player, yaw, pitch);
    }

    /**
     * Must be called every tick. Applies rotation step + Perlin jitter.
     * Only modifies player.setYaw()/setPitch() -- no raw packets.
     */
    public void tick(ClientPlayerEntity player, long worldTick) {
        // Perlin jitter ALWAYS (even in IDLE)
        float jitterYaw = NoiseGenerator.cameraJitterYaw(worldTick);
        float jitterPitch = NoiseGenerator.cameraJitterPitch(worldTick);

        switch (state) {
            case IDLE -> {
                player.setYaw(player.getYaw() + jitterYaw);
                player.setPitch(clampPitch(player.getPitch() + jitterPitch));
            }

            case ROTATING -> {
                currentRotTick++;
                float t = Math.min(1.0f, (float) currentRotTick / rotationTicks);

                // Cubic Bezier with ease-in-out-back (slight overshoot built in)
                float easedT = MotionProfile.easeInOutSmooth(t);

                float deltaYaw = MotionProfile.wrapDegrees(targetYaw - startYaw);
                float deltaPitch = targetPitch - startPitch;

                float newYaw = startYaw + deltaYaw * easedT;
                float newPitch = startPitch + deltaPitch * easedT;

                player.setYaw(newYaw + jitterYaw);
                player.setPitch(clampPitch(newPitch + jitterPitch));

                if (currentRotTick >= rotationTicks) {
                    if (Math.abs(overshootYaw) > 0.01f || Math.abs(overshootPitch) > 0.01f) {
                        state = AimState.SETTLING;
                        settlingTick = 0;
                        // Apply overshoot to current position
                        player.setYaw(targetYaw + overshootYaw + jitterYaw);
                        player.setPitch(clampPitch(targetPitch + overshootPitch + jitterPitch));
                    } else {
                        state = AimState.LOCKED;
                    }
                }
            }

            case SETTLING -> {
                settlingTick++;
                float st = Math.min(1.0f, (float) settlingTick / settlingTicks);

                // Elastic ease-out for natural correction
                float correctionT = MotionProfile.easeOutElastic(st);

                float remainOverYaw = overshootYaw * (1.0f - correctionT);
                float remainOverPitch = overshootPitch * (1.0f - correctionT);

                player.setYaw(targetYaw + remainOverYaw + jitterYaw);
                player.setPitch(clampPitch(targetPitch + remainOverPitch + jitterPitch));

                if (settlingTick >= settlingTicks) {
                    state = AimState.LOCKED;
                    overshootYaw = 0;
                    overshootPitch = 0;
                }
            }

            case LOCKED -> {
                // Stay on target with jitter
                player.setYaw(targetYaw + jitterYaw);
                player.setPitch(clampPitch(targetPitch + jitterPitch));
            }

            case DRIFTING -> {
                driftTick++;
                float dt = (float) driftTick / driftTotalTicks;
                // Sine wave drift: goes out and comes back
                float driftYaw = driftAmplitudeYaw * (float) Math.sin(dt * Math.PI);
                float driftPitch = driftAmplitudePitch * (float) Math.sin(dt * Math.PI * 0.7);

                player.setYaw(driftBaseYaw + driftYaw + jitterYaw);
                player.setPitch(clampPitch(driftBasePitch + driftPitch + jitterPitch));

                if (driftTick >= driftTotalTicks) {
                    state = AimState.IDLE;
                }
            }
        }
    }

    /**
     * Returns true when rotation is complete enough to start mining.
     * SETTLING counts -- human starts breaking before perfectly aimed.
     */
    public boolean isAimed() {
        return state == AimState.LOCKED || state == AimState.SETTLING;
    }

    /**
     * Returns true only when fully locked on target.
     */
    public boolean isLocked() {
        return state == AimState.LOCKED;
    }

    /**
     * Returns true when idle (not actively aiming at anything).
     */
    public boolean isIdle() {
        return state == AimState.IDLE;
    }

    /**
     * Start an idle drift behavior (look around).
     * @param amplitudeYaw degrees to drift horizontally
     * @param amplitudePitch degrees to drift vertically
     * @param ticks duration of the drift
     */
    public void startDrift(float amplitudeYaw, float amplitudePitch, int ticks) {
        driftBaseYaw = targetYaw;
        driftBasePitch = targetPitch;
        driftAmplitudeYaw = amplitudeYaw;
        driftAmplitudePitch = amplitudePitch;
        driftTotalTicks = Math.max(1, ticks);
        driftTick = 0;
        state = AimState.DRIFTING;
    }

    /**
     * Stop all rotation, go to IDLE.
     */
    public void clear() {
        state = AimState.IDLE;
        overshootYaw = 0;
        overshootPitch = 0;
    }

    /**
     * Interrupt current rotation and snap to IDLE.
     * Preserves current player angles.
     */
    public void reset() {
        clear();
    }

    // --- Internal ---

    private void beginRotation(ClientPlayerEntity player, float yaw, float pitch) {
        startYaw = player.getYaw();
        startPitch = player.getPitch();
        targetYaw = yaw;
        targetPitch = pitch;

        float deltaYaw = Math.abs(MotionProfile.wrapDegrees(targetYaw - startYaw));
        float deltaPitch = Math.abs(targetPitch - startPitch);
        float totalDelta = deltaYaw + deltaPitch;

        // Tick count scales with rotation magnitude
        if (totalDelta < 10f) {
            rotationTicks = HumanTiming.gaussianDelay(3, 0.8f);
        } else if (totalDelta < 25f) {
            rotationTicks = HumanTiming.gaussianDelay(5, 1.2f);
        } else if (totalDelta < 50f) {
            rotationTicks = HumanTiming.gaussianDelay(8, 2.0f);
        } else if (totalDelta < 90f) {
            rotationTicks = HumanTiming.gaussianDelay(11, 2.5f);
        } else {
            rotationTicks = HumanTiming.gaussianDelay(15, 3.0f);
        }
        rotationTicks = Math.max(2, rotationTicks);

        // Overshoot for larger rotations
        if (totalDelta > 15f) {
            float overshootScale = Math.min(totalDelta / 90f, 1.0f);
            float maxOvershoot = 1.0f + overshootScale * 2.5f;
            overshootYaw = MotionProfile.wrapDegrees(targetYaw - startYaw) * 0.03f
                    + (float) (Math.random() - 0.5) * maxOvershoot;
            overshootPitch = (targetPitch - startPitch) * 0.02f
                    + (float) (Math.random() - 0.5) * maxOvershoot * 0.6f;
            settlingTicks = HumanTiming.gaussianDelay(3, 1.0f);
            settlingTicks = Math.max(2, settlingTicks);
        } else {
            overshootYaw = 0;
            overshootPitch = 0;
            settlingTicks = 0;
        }

        currentRotTick = 0;
        state = AimState.ROTATING;
    }

    private static float calcYaw(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float calcPitch(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        return (float) Math.toDegrees(-Math.atan2(dy, horiz));
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }
}
