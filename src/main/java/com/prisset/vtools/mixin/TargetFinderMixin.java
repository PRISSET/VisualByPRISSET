package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.util.ExpandContext;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class TargetFinderMixin {

    @Inject(method = "updateTargetedEntity", at = @At("HEAD"))
    private void vtools$beforeTargetUpdate(float tickDelta, CallbackInfo ci) {
        if (VToolsMod.getPrefs() != null && VToolsMod.getPrefs().isActive()) {
            ExpandContext.beginRaycast();
        }
    }

    @Inject(method = "updateTargetedEntity", at = @At("RETURN"))
    private void vtools$afterTargetUpdate(float tickDelta, CallbackInfo ci) {
        ExpandContext.endRaycast();
    }
}
