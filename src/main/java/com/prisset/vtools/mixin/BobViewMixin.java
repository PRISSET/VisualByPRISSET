package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels view bobbing (screen shake when walking/sprinting).
 * Neutral name: reads as a rendering utility for bob-view adjustments.
 */
@Mixin(GameRenderer.class)
public abstract class BobViewMixin {

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void vtools$cancelBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs != null && prefs.isNoBobbing()) {
            ci.cancel();
        }
    }
}
