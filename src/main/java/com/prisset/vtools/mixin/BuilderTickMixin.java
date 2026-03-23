package com.prisset.vtools.mixin;

import com.prisset.vtools.blueprint.BlueprintPlacer;
import com.prisset.vtools.blueprint.BuilderBot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ticks the builder bot and updates placement preview anchor every client tick.
 */
@Mixin(MinecraftClient.class)
public abstract class BuilderTickMixin {

    @Shadow public HitResult crosshairTarget;

    @Inject(method = "tick", at = @At("TAIL"))
    private void vtools$tickBuilder(CallbackInfo ci) {
        // Update preview anchor for cursor-follow placement mode
        BlueprintPlacer placer = BlueprintPlacer.instance();
        if (placer.isPlacementMode() && placer.isLoaded()) {
            if (crosshairTarget != null && crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockHitResult hit = (BlockHitResult) crosshairTarget;
                BlockPos target = hit.getBlockPos().offset(hit.getSide());
                placer.setPreviewAnchor(target);
            }
        }

        BuilderBot.instance().tick();
    }
}
