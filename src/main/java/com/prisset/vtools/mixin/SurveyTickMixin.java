package com.prisset.vtools.mixin;

import com.prisset.vtools.survey.SampleCollector;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ticks the survey (sample collection) system every client tick.
 * Neutral naming matches existing pattern.
 */
@Mixin(MinecraftClient.class)
public abstract class SurveyTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        SampleCollector.instance().tick();
    }
}
