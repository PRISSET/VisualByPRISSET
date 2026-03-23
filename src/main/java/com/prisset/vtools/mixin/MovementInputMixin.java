package com.prisset.vtools.mixin;

import com.prisset.vtools.survey.SampleCollector;
import com.prisset.vtools.survey.WalkHelper;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects AFTER KeyboardInput.tick() RETURN to override movement values.
 * Runs after MC reads physical keys, so our values are final.
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

        if (walk.wantForward) {
            self.pressingForward = true;
            self.movementForward = 1.0f;
        }
        if (walk.wantJump) {
            self.jumping = true;
        }
    }
}
