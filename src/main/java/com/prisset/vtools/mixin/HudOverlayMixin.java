package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.render.TargetArrowRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class HudOverlayMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void vtools$afterHudRender(DrawContext ctx, float tickDelta, CallbackInfo ci) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive()) return;

        TargetArrowRenderer.renderHud(ctx, prefs);
    }
}
