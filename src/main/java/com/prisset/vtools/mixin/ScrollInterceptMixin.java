package com.prisset.vtools.mixin;

import com.prisset.vtools.blueprint.SelectionManager;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts mouse scroll to adjust blueprint selection range.
 * When selection mode is active, scroll up/down changes air-pick distance.
 */
@Mixin(Mouse.class)
public class ScrollInterceptMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void vtools$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        SelectionManager sel = SelectionManager.instance();
        if (!sel.isActive()) return;

        double step = vertical > 0 ? 2.0 : -2.0;
        sel.adjustRange(step);
        ci.cancel();
    }
}
