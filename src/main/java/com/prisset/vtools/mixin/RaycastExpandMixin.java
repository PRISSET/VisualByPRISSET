package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ProjectileUtil.class)
public abstract class RaycastExpandMixin {

    @Redirect(
        method = "raycast",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/math/Box;expand(DDD)Lnet/minecraft/util/math/Box;"
        )
    )
    private static Box vtools$expandMargin(Box box, double x, double y, double z) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive()) {
            return box.expand(x, y, z);
        }

        double hScale = prefs.getHScale();
        double vScale = prefs.getVScale();

        double newX = x * hScale;
        double newY = y * vScale;
        double newZ = z * hScale;

        return box.expand(newX, newY, newZ);
    }
}
