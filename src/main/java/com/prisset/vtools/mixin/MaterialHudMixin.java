package com.prisset.vtools.mixin;

import com.prisset.vtools.blueprint.BuildMaterialHud;
import com.prisset.vtools.blueprint.BuilderBot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders material HUD and builder progress on the in-game HUD.
 */
@Mixin(InGameHud.class)
public abstract class MaterialHudMixin {

    @Shadow @Final private MinecraftClient client;

    @Shadow public abstract TextRenderer getTextRenderer();

    @Inject(method = "render", at = @At("TAIL"))
    private void vtools$renderMaterialHud(DrawContext ctx, float tickDelta, CallbackInfo ci) {
        if (client.currentScreen != null) return;

        // Material list
        BuildMaterialHud.render(ctx, getTextRenderer());

        // Builder progress bar
        BuilderBot bot = BuilderBot.instance();
        if (bot.isRunning() || bot.getState() == BuilderBot.State.PAUSED) {
            renderBuilderProgress(ctx, getTextRenderer(), bot);
        }
    }

    private void renderBuilderProgress(DrawContext ctx, TextRenderer tr, BuilderBot bot) {
        int screenW = client.getWindow().getScaledWidth();
        int x = screenW - 160;
        int y = client.getWindow().getScaledHeight() - 20;

        // Background
        ctx.fill(x - 4, y - 2, screenW - 2, y + 12, 0x90000000);

        // Progress bar
        float progress = bot.getProgress();
        int barW = 140;
        int filled = (int)(barW * progress);

        ctx.fill(x, y, x + barW, y + 8, 0xFF1A1A20);
        if (filled > 0) {
            int barColor = bot.getState() == BuilderBot.State.PAUSED ? 0xFFFFAA20 : 0xFF40CC40;
            ctx.fill(x, y, x + filled, y + 8, barColor);
        }

        // Text
        String status = bot.getState() == BuilderBot.State.PAUSED ? "\u041f\u0430\u0443\u0437\u0430" : "\u0421\u0442\u0440\u043e\u0439\u043a\u0430";
        String info = status + " " + bot.getPlacedCount() + "/" + bot.getTotalCount();
        int tw = tr.getWidth(info);
        ctx.drawTextWithShadow(tr, info, x + (barW - tw) / 2, y + 1, 0xFFFFFFFF);
    }
}
