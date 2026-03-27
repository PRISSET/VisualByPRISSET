package com.prisset.vtools.mixin;

import com.prisset.vtools.render.ChestSearchOverlay;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class ContainerSearchMixin {

    @Inject(method = "init()V", at = @At("RETURN"))
    private void vtools$onInit(CallbackInfo ci) {
        ChestSearchOverlay.onScreenOpen();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void vtools$onClose(CallbackInfo ci) {
        ChestSearchOverlay.onScreenClose();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void vtools$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (ChestSearchOverlay.onKeyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }
}
