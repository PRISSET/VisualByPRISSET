package com.prisset.vtools.mixin;

import com.prisset.vtools.survey.IdleBehavior;
import com.prisset.vtools.survey.SampleCollector;
import com.prisset.vtools.survey.WalkHelper;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects AFTER KeyboardInput.tick() RETURN to override movement values.
 * Supports float-based forward/strafe, sprint, and sneak.
 */
@Mixin(KeyboardInput.class)
public abstract class MovementInputMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void afterTick(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        SampleCollector sc = SampleCollector.instance();
        if (sc.getState() == SampleCollector.State.IDLE) return;

        WalkHelper walk = sc.getWalkHelper();
        if (walk == null) return;

        KeyboardInput self = (KeyboardInput) (Object) this;

        // Float-based forward movement (acceleration curve)
        if (walk.wantForward > 0.01f) {
            self.pressingForward = true;
            self.movementForward = walk.wantForward;
        } else if (walk.wantForward < -0.01f) {
            // Backward movement (for idle step-back)
            self.pressingBack = true;
            self.movementForward = walk.wantForward;
        }

        // Strafe drift (continuous Perlin-based)
        if (walk.wantStrafe > 0.05f) {
            self.pressingRight = true;
            self.movementSideways = -walk.wantStrafe;
        } else if (walk.wantStrafe < -0.05f) {
            self.pressingLeft = true;
            self.movementSideways = -walk.wantStrafe;
        }

        // Jump
        if (walk.wantJump) {
            self.jumping = true;
        }

        // Sneak (from idle behaviors like crouch-peek)
        if (walk.wantSneak) {
            self.sneaking = true;
        }

        // Also check idle behavior sneak
        IdleBehavior idle = sc.getIdleBehavior();
        if (idle != null && idle.wantSneak()) {
            self.sneaking = true;
        }
    }
}
