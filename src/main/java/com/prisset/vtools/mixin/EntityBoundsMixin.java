package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityBoundsMixin {

    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void vtools$expandBounds(CallbackInfoReturnable<Box> cir) {
        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive()) return;

        Entity self = (Entity) (Object) this;
        if (!shouldExpand(self, prefs)) return;

        Box original = cir.getReturnValue();
        if (original == null) return;

        double hScale = prefs.getHScale();
        double vScale = prefs.getVScale();
        float fixedV = prefs.getFixedV();

        // Skip if scales are default and no fixed height
        if (hScale == 1.0 && vScale == 1.0 && fixedV <= 0) return;

        double centerX = (original.minX + original.maxX) / 2.0;
        double centerZ = (original.minZ + original.maxZ) / 2.0;
        double minY = original.minY;

        double origW = original.maxX - original.minX;
        double origH = original.maxY - original.minY;
        double origD = original.maxZ - original.minZ;

        double newW = origW * hScale;
        double newH;
        if (fixedV > 0) {
            newH = fixedV;
        } else {
            newH = origH * vScale;
        }
        double newD = origD * hScale;

        Box expanded = new Box(
            centerX - newW / 2.0, minY,          centerZ - newD / 2.0,
            centerX + newW / 2.0, minY + newH,   centerZ + newD / 2.0
        );

        cir.setReturnValue(expanded);
    }

    private static boolean shouldExpand(Entity entity, DisplayPrefs prefs) {
        if (entity instanceof PlayerEntity) return prefs.isFilterPlayers();
        if (entity instanceof MobEntity)    return prefs.isFilterMobs();
        if (entity instanceof ItemEntity)   return prefs.isFilterDrops();
        return false;
    }
}
