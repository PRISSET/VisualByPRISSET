package com.prisset.vtools.blueprint;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * HUD overlay showing required materials for current schematic.
 * Renders a compact list on the right side of the screen:
 *   [icon] block_name  have/need
 *
 * Colors:
 *   Green  = have enough
 *   Yellow = have some but not all
 *   Red    = have none
 */
public final class BuildMaterialHud {

    private static boolean visible = false;
    private static long lastCalcTick;
    private static List<MaterialEntry> materials = List.of();

    private BuildMaterialHud() {}

    public static boolean isVisible() { return visible; }
    public static void setVisible(boolean val) { visible = val; }

    public static void render(DrawContext ctx, TextRenderer tr) {
        if (!visible) return;

        BlueprintPlacer placer = BlueprintPlacer.instance();
        if (!placer.isLoaded()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Recalculate every 20 ticks (1 second)
        long now = mc.world != null ? mc.world.getTime() : 0;
        if (now - lastCalcTick >= 20 || materials.isEmpty()) {
            lastCalcTick = now;
            materials = calculateMaterials(placer.getSchematic(), mc.player);
        }

        if (materials.isEmpty()) return;

        int screenW = mc.getWindow().getScaledWidth();
        int x = screenW - 160;
        int y = 4;

        // Header
        ctx.fill(x - 4, y - 2, screenW - 2, y + 10, 0x90000000);
        ctx.drawTextWithShadow(tr, "\u041c\u0430\u0442\u0435\u0440\u0438\u0430\u043b\u044b", x, y, 0xFF80C0FF);
        y += 14;

        int maxRows = 20;
        int shown = 0;

        for (MaterialEntry entry : materials) {
            if (shown >= maxRows) break;

            int bgAlpha = shown % 2 == 0 ? 0x60000000 : 0x50000000;
            ctx.fill(x - 4, y - 1, screenW - 2, y + 11, bgAlpha);

            // Item icon
            Block block = Registries.BLOCK.get(new Identifier(entry.blockId));
            ItemStack iconStack = new ItemStack(block.asItem());
            if (!iconStack.isEmpty()) {
                ctx.drawItem(iconStack, x - 2, y - 2);
            }

            // Short name (strip "minecraft:")
            String name = entry.blockId;
            int colon = name.indexOf(':');
            if (colon >= 0) name = name.substring(colon + 1);
            if (name.length() > 14) name = name.substring(0, 14) + "..";

            int nameColor = 0xFFB0B0B8;
            ctx.drawTextWithShadow(tr, name, x + 18, y + 1, nameColor);

            // Count: have/need
            String count = entry.have + "/" + entry.need;
            int countColor;
            if (entry.have >= entry.need) {
                countColor = 0xFF40FF40;
            } else if (entry.have > 0) {
                countColor = 0xFFFFFF40;
            } else {
                countColor = 0xFFFF4040;
            }

            int cw = tr.getWidth(count);
            ctx.drawTextWithShadow(tr, count, screenW - 6 - cw, y + 1, countColor);

            y += 12;
            shown++;
        }

        if (materials.size() > maxRows) {
            ctx.drawTextWithShadow(tr, "... +" + (materials.size() - maxRows), x, y + 1, 0xFF606068);
        }
    }

    private static List<MaterialEntry> calculateMaterials(SchematicData schem, ClientPlayerEntity player) {
        // Count required blocks from schematic
        Map<String, Integer> needed = new LinkedHashMap<>();
        for (int y = 0; y < schem.getSizeY(); y++) {
            for (int z = 0; z < schem.getSizeZ(); z++) {
                for (int x = 0; x < schem.getSizeX(); x++) {
                    if (schem.isAir(x, y, z)) continue;
                    String state = schem.getBlockState(x, y, z);
                    String blockId = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
                    needed.merge(blockId, 1, Integer::sum);
                }
            }
        }

        // Count what player has in inventory
        Map<String, Integer> have = new HashMap<>();
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) continue;
            String id = Registries.BLOCK.getId(bi.getBlock()).toString();
            have.merge(id, stack.getCount(), Integer::sum);
        }

        // Build sorted list: missing first, then partial, then complete
        List<MaterialEntry> result = new ArrayList<>();
        for (var e : needed.entrySet()) {
            int h = have.getOrDefault(e.getKey(), 0);
            result.add(new MaterialEntry(e.getKey(), e.getValue(), h));
        }

        result.sort((a, b) -> {
            float ratioA = a.need > 0 ? (float) a.have / a.need : 1f;
            float ratioB = b.need > 0 ? (float) b.have / b.need : 1f;
            if (ratioA != ratioB) return Float.compare(ratioA, ratioB);
            return Integer.compare(b.need, a.need);
        });

        return result;
    }

    static class MaterialEntry {
        final String blockId;
        final int need;
        final int have;

        MaterialEntry(String blockId, int need, int have) {
            this.blockId = blockId;
            this.need = need;
            this.have = have;
        }
    }
}
