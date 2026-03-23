package com.prisset.vtools.mixin;

import com.prisset.vtools.survey.SampleCollector;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects AFTER KeyboardInput.tick() to override movement values.
 * This runs after MC reads physical key states, so our values won't be reset.
 */
@Mixin(KeyboardInput.class)
public abstract class MovementInputMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void afterTick(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        SampleCollector sc = SampleCollector.instance();
        if (sc.getState() == SampleCollector.State.IDLE) return;

        SampleCollector.MoveRequest req = sc.getMoveRequest();
        if (req == null) return;

        KeyboardInput self = (KeyboardInput) (Object) this;

        if (req.forward) {
            self.pressingForward = true;
            self.movementForward = 1.0f;
        }
        if (req.jump) {
            self.jumping = true;
        }
        if (req.sneak) {
            self.sneaking = true;
        }
    }
}
