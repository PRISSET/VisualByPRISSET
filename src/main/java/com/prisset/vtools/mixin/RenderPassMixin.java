package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.blueprint.GhostRenderer;
import com.prisset.vtools.blueprint.SelectionRenderer;
import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.render.OverlayPainter;
import com.prisset.vtools.render.TrailRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class RenderPassMixin {

    @Inject(
        method = "render",
        at = @At("RETURN")
    )
    private void vtools$afterWorldRender(
            MatrixStack matrices,
            float tickDelta,
            long limitTime,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightmapTextureManager lightmapTextureManager,
            Matrix4f projectionMatrix,
            CallbackInfo ci
    ) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || prefs == null) return;

        // Blueprint rendering (independent of overlay toggle)
        SelectionRenderer.render(matrices, camera, tickDelta);
        GhostRenderer.render(matrices, camera, tickDelta);

        if (!prefs.isActive()) return;

        if (prefs.isDebugOnly()) {
            boolean debugActive = client.getEntityRenderDispatcher()
                .shouldRenderHitboxes();
            if (!debugActive) return;
        }

        OverlayPainter.paintAll(client, matrices, tickDelta, camera, prefs);

        TrailRenderer.tick(client, prefs);
        TrailRenderer.render(client, matrices, tickDelta, camera, prefs);
    }
}
