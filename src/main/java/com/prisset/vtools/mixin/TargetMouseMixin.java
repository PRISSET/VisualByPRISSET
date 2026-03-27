package com.prisset.vtools.mixin;

import com.prisset.vtools.input.PlayerTargetHandler;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class TargetMouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void vtools$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && action == GLFW.GLFW_PRESS) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen == null) {
                PlayerTargetHandler.onMiddleClick();
            }
        }
    }
}
