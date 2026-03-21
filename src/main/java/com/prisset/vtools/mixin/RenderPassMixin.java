package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.render.OverlayPainter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class RenderPassMixin<T extends Entity> {

    @Inject(method = "render", at = @At("TAIL"))
    private void vtools$afterRender(T entity, float yaw, float tickDelta,
                                     MatrixStack matrices,
                                     VertexConsumerProvider vertexConsumers,
                                     int light, CallbackInfo ci) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (prefs.isDebugOnly()) {
            // Check vanilla debug overlay state via dispatcher
            boolean debugActive = client.getEntityRenderDispatcher()
                .shouldRenderHitboxes();
            if (!debugActive) return;
        }

        OverlayPainter.paint(entity, tickDelta, matrices, vertexConsumers, prefs);
    }
}
