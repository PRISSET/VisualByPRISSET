package com.prisset.vtools.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class DistanceCheck {

    private static final double LIMIT = 3.0;
    private static final double LIMIT_SQ = LIMIT * LIMIT;

    private DistanceCheck() {}

    public static boolean isValid(PlayerEntity player, Entity target) {
        Vec3d eye = player.getEyePos();

        double hw = target.getWidth() / 2.0;
        double h = target.getHeight();
        double tx = target.getX();
        double ty = target.getY();
        double tz = target.getZ();

        Box box = new Box(
            tx - hw, ty, tz - hw,
            tx + hw, ty + h, tz + hw
        );

        double dSq = box.squaredMagnitude(eye);
        return dSq <= LIMIT_SQ;
    }
}
