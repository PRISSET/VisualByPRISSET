package com.prisset.vtools.blueprint;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders selection box and individual point markers in world space.
 *
 * - Pos1: cyan wireframe cube
 * - Pos2: magenta wireframe cube
 * - Complete selection: green wireframe box around entire zone
 * - Pulsating alpha for visibility
 */
public final class SelectionRenderer {

    private SelectionRenderer() {}

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        SelectionManager sel = SelectionManager.instance();
        if (!sel.isActive()) return;

        Vec3d camPos = camera.getPos();
        VertexConsumerProvider.Immediate immediate =
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        float pulse = (float)(Math.sin(System.currentTimeMillis() / 400.0) * 0.2 + 0.8);

        // Pos1 marker: cyan
        if (sel.hasPos1()) {
            drawBlockOutline(matrices, lines, camPos, sel.getPos1(),
                0.0f, 0.9f, 1.0f, pulse);
        }

        // Pos2 marker: magenta
        if (sel.hasPos2()) {
            drawBlockOutline(matrices, lines, camPos, sel.getPos2(),
                1.0f, 0.2f, 0.8f, pulse);
        }

        // Full selection box: green
        if (sel.isComplete()) {
            BlockPos min = sel.getMin();
            BlockPos max = sel.getMax();

            matrices.push();
            matrices.translate(
                min.getX() - camPos.x,
                min.getY() - camPos.y,
                min.getZ() - camPos.z
            );

            Box box = new Box(
                0, 0, 0,
                max.getX() - min.getX() + 1,
                max.getY() - min.getY() + 1,
                max.getZ() - min.getZ() + 1
            );

            WorldRenderer.drawBox(matrices, lines, box,
                0.2f, 1.0f, 0.3f, pulse * 0.7f);

            matrices.pop();
        }

        immediate.draw();
    }

    private static void drawBlockOutline(MatrixStack matrices, VertexConsumer lines,
                                          Vec3d camPos, BlockPos pos,
                                          float r, float g, float b, float a) {
        matrices.push();
        matrices.translate(
            pos.getX() - camPos.x,
            pos.getY() - camPos.y,
            pos.getZ() - camPos.z
        );

        Box box = new Box(0, 0, 0, 1, 1, 1);
        WorldRenderer.drawBox(matrices, lines, box, r, g, b, a);

        matrices.pop();
    }
}
