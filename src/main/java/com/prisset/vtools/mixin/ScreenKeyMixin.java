package com.prisset.vtools.mixin;

import com.prisset.vtools.input.SlotInspector;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public class ScreenKeyMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void vtools$onKeyInGui(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode == GLFW.GLFW_KEY_F6) {
            SlotInspector.dump();
        }
    }
}
