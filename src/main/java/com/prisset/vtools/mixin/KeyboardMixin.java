package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void vtools$onKey(long window, int key, int scancode,
                               int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) {
            VToolsMod.onKeyPress(key);
        }
    }
}
