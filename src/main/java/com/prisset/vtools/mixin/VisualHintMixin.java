package com.prisset.vtools.mixin;

import com.prisset.vtools.VToolsMod;
import com.prisset.vtools.config.DisplayPrefs;
import com.prisset.vtools.config.ProfileIndex;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class VisualHintMixin {

    @Inject(method = "isGlowing", at = @At("RETURN"), cancellable = true)
    private void vtools$forceGlow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof PlayerEntity)) return;
        if (self == net.minecraft.client.MinecraftClient.getInstance().player) return;

        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive() || !prefs.isEspEnabled()) return;

        cir.setReturnValue(true);
    }

    @Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true)
    private void vtools$teamColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof PlayerEntity player)) return;
        if (self == net.minecraft.client.MinecraftClient.getInstance().player) return;

        DisplayPrefs prefs = VToolsMod.getPrefs();
        if (prefs == null || !prefs.isActive() || !prefs.isEspEnabled()) return;

        String name = player.getGameProfile().getName();
        if (ProfileIndex.get().isTeammate(name)) {
            cir.setReturnValue(0x00FF00);
        } else {
            cir.setReturnValue(0xFF0000);
        }
    }
}
