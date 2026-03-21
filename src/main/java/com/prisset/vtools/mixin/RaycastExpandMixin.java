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
            target = "Lnet/minecraft/util/math/Box;expand(D)Lnet/minecraft/util/math/Box;"
        )
    )
    private static Box vtools$expandMargin(Box box, double margin) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive()) {
            return box.expand(margin);
        }

        double hScale = prefs.getHScale();
        double vScale = prefs.getVScale();

        double halfW = (box.maxX - box.minX) / 2.0;
        double halfH = (box.maxY - box.minY) / 2.0;
        double extraH = halfW * (hScale - 1.0);
        double extraV = halfH * (vScale - 1.0);

        return box.expand(margin + extraH, margin + extraV, margin + extraH);
    }
}
