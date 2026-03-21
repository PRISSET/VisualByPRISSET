package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public abstract class RaycastExpandMixin {

    /**
     * Intercepts the margin parameter passed to Entity.getBoundingBox().expand()
     * inside ProjectileUtil.raycast(). The margin controls how much the
     * entity bounding box is inflated for intersection testing.
     *
     * Vanilla uses entity.getTargetingMargin() which returns 0.3 for most entities.
     * We increase this based on the user's scale settings, effectively expanding
     * the "aim zone" without touching getBoundingBox() itself.
     */
    @ModifyArgs(
        method = "raycast",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/math/Box;expand(DDD)Lnet/minecraft/util/math/Box;"
        )
    )
    private static Args vtools$expandTargetMargin(Args args) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive()) return args;

        double origX = args.get(0);
        double origY = args.get(1);
        double origZ = args.get(2);

        double hExtra = origX * (prefs.getHScale() - 1.0);
        double vExtra = origY * (prefs.getVScale() - 1.0);

        if (hExtra <= 0 && vExtra <= 0) return args;

        args.set(0, origX + hExtra);
        args.set(1, origY + vExtra);
        args.set(2, origZ + hExtra);

        return args;
    }
}
