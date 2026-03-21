package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class InteractionGuardMixin {

    private static final double LIMIT_SQ = 3.0 * 3.0;

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void vtools$validateDistance(PlayerEntity player, Entity target, CallbackInfo ci) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive()) return;

        // Compute distance from player eyes to the ORIGINAL (non-expanded) bounding box
        Vec3d eye = player.getEyePos();

        double hw = target.getWidth() / 2.0;
        double h = target.getHeight();
        double tx = target.getX();
        double ty = target.getY();
        double tz = target.getZ();

        Box realBox = new Box(
            tx - hw, ty, tz - hw,
            tx + hw, ty + h, tz + hw
        );

        double dSq = realBox.squaredMagnitude(eye);

        // If the real distance exceeds vanilla limit, silently drop the attack
        // so no suspicious packet ever reaches the server
        if (dSq > LIMIT_SQ) {
            ci.cancel();
        }
    }
}
