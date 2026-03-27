package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void vtools$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) {
            VToolsMod.onMousePress(button);
        }
    }
}
