package com.prisset.vtools.blueprint;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Renders ghost preview of a loaded schematic at anchor position.
 *
 * Non-air blocks drawn as colored wireframe cubes:
 *   - Correct position (already matches world): green wireframe
 *   - Needs placement (air in world): yellow wireframe
 *   - Wrong block (different block in world): red wireframe
 *
 * Also renders the outer bounding box and anchor marker.
 */
public final class GhostRenderer {

    private GhostRenderer() {}

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        BlueprintPlacer placer = BlueprintPlacer.instance();
        if (!placer.isLoaded() || !placer.isPreviewing() || !placer.hasAnchor()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        SchematicData schem = placer.getSchematic();
        BlockPos anchor = placer.getAnchor();
        Vec3d camPos = camera.getPos();

        VertexConsumerProvider.Immediate immediate =
            mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        float pulse = (float)(Math.sin(System.currentTimeMillis() / 500.0) * 0.15 + 0.85);

        int sx = schem.getSizeX();
        int sy = schem.getSizeY();
        int sz = schem.getSizeZ();

        // Render bounding box (cyan, dim)
        renderBoundingBox(matrices, lines, camPos, placer, pulse);

        // Render anchor marker (white bright)
        renderAnchorMarker(matrices, lines, camPos, anchor, pulse);

        // Render ghost blocks (limit render distance for perf)
        double maxDistSq = 48 * 48;

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    if (schem.isAir(x, y, z)) continue;

                    BlockPos worldPos = placer.localToWorld(x, y, z);

                    // Distance cull
                    double dx = worldPos.getX() + 0.5 - camPos.x;
                    double dy = worldPos.getY() + 0.5 - camPos.y;
                    double dz = worldPos.getZ() + 0.5 - camPos.z;
                    if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;

                    // Check world state
                    String expected = schem.getBlockState(x, y, z);
                    String actual = SchematicData.encodeState(
                        mc.world.getBlockState(worldPos));

                    float r, g, b, a;

                    if (expected.equals(actual)) {
                        // Correct: green, very dim
                        r = 0.2f; g = 0.8f; b = 0.2f; a = pulse * 0.2f;
                    } else if (mc.world.getBlockState(worldPos).isAir()) {
                        // Needs placement: yellow
                        r = 1.0f; g = 0.9f; b = 0.2f; a = pulse * 0.5f;
                    } else {
                        // Wrong block: red
                        r = 1.0f; g = 0.2f; b = 0.2f; a = pulse * 0.5f;
                    }

                    matrices.push();
                    matrices.translate(
                        worldPos.getX() - camPos.x,
                        worldPos.getY() - camPos.y,
                        worldPos.getZ() - camPos.z
                    );
                    WorldRenderer.drawBox(matrices, lines,
                        new Box(0, 0, 0, 1, 1, 1), r, g, b, a);
                    matrices.pop();
                }
            }
        }

        immediate.draw();
    }

    private static void renderBoundingBox(MatrixStack matrices, VertexConsumer lines,
                                           Vec3d camPos, BlueprintPlacer placer, float pulse) {
        BlockPos anchor = placer.getAnchor();
        int wsx = placer.getWorldSizeX();
        int wsy = placer.getWorldSizeY();
        int wsz = placer.getWorldSizeZ();

        matrices.push();
        matrices.translate(
            anchor.getX() - camPos.x,
            anchor.getY() - camPos.y,
            anchor.getZ() - camPos.z
        );
        WorldRenderer.drawBox(matrices, lines,
            new Box(0, 0, 0, wsx, wsy, wsz),
            0.3f, 0.8f, 1.0f, pulse * 0.4f);
        matrices.pop();
    }

    private static void renderAnchorMarker(MatrixStack matrices, VertexConsumer lines,
                                            Vec3d camPos, BlockPos anchor, float pulse) {
        matrices.push();
        matrices.translate(
            anchor.getX() - camPos.x,
            anchor.getY() - camPos.y,
            anchor.getZ() - camPos.z
        );
        // Slightly inset box to distinguish from ghost blocks
        WorldRenderer.drawBox(matrices, lines,
            new Box(-0.02, -0.02, -0.02, 1.02, 1.02, 1.02),
            1.0f, 1.0f, 1.0f, pulse);
        matrices.pop();
    }
}
