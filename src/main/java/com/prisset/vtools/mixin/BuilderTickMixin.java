package com.prisset.vtools.mixin;

import com.prisset.vtools.blueprint.BuilderBot;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ticks the builder bot every client tick.
 */
@Mixin(MinecraftClient.class)
public abstract class BuilderTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void vtools$tickBuilder(CallbackInfo ci) {
        BuilderBot.instance().tick();
    }
}
