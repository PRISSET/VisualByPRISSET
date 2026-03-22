package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class ZoomMixin {

    @Unique private float vtools$currentZoom = 1.0f;
    @Unique private static final float SMOOTH_SPEED = 0.15f;

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void vtools$applyZoom(Camera camera, float tickDelta, boolean changingFov,
                                   CallbackInfoReturnable<Double> cir) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) return;

        long window = client.getWindow().getHandle();
        boolean keyDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;

        float target = keyDown ? (1.0f / prefs.getZoomStrength()) : 1.0f;
        vtools$currentZoom += (target - vtools$currentZoom) * SMOOTH_SPEED;

        if (Math.abs(vtools$currentZoom - 1.0f) > 0.001f) {
            cir.setReturnValue(cir.getReturnValue() * vtools$currentZoom);
        }
    }
}
