package com.prisset.vtools.blueprint;

import com.google.gson.*;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Compact schematic format: palette-indexed block array.
 *
 * Structure:
 *   - origin: anchor point (min corner when scanned)
 *   - sizeX/Y/Z: dimensions
 *   - palette: list of unique "block_id[prop=val,...]" strings
 *   - blocks: flat array of palette indices (Y-up, then Z, then X)
 *
 * Air blocks are stored as palette index 0 (always "minecraft:air").
 * Block properties (facing, half, type, waterlogged, etc.) are preserved.
 */
public final class SchematicData {

    private int sizeX, sizeY, sizeZ;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> paletteIndex = new HashMap<>();
    private int[] blocks; // flat array: index = y * sizeZ * sizeX + z * sizeX + x

    private String name;
    private long createdAt;

    public SchematicData() {}

    public SchematicData(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = new int[sizeX * sizeY * sizeZ];
        this.name = "unnamed";
        this.createdAt = System.currentTimeMillis();

        // Index 0 is always air
        addToPalette("minecraft:air");
    }

    // -- Palette --

    private int addToPalette(String stateStr) {
        if (paletteIndex.containsKey(stateStr)) return paletteIndex.get(stateStr);
        int idx = palette.size();
        palette.add(stateStr);
        paletteIndex.put(stateStr, idx);
        return idx;
    }

    /**
     * Encode a BlockState to a palette string: "minecraft:oak_stairs[facing=east,half=bottom]"
     */
    @SuppressWarnings("unchecked")
    public static String encodeState(BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        Collection<Property<?>> props = state.getProperties();
        if (props.isEmpty()) return id.toString();

        StringJoiner sj = new StringJoiner(",", "[", "]");
        for (Property<?> prop : props) {
            sj.add(prop.getName() + "=" + ((Property) prop).name(state.get(prop)));
        }
        return id.toString() + sj;
    }

    // -- Block access --

    private int idx(int x, int y, int z) {
        return y * sizeZ * sizeX + z * sizeX + x;
    }

    public void setBlock(int x, int y, int z, BlockState state) {
        if (state.isAir()) {
            blocks[idx(x, y, z)] = 0;
            return;
        }
        String encoded = encodeState(state);
        int paletteIdx = addToPalette(encoded);
        blocks[idx(x, y, z)] = paletteIdx;
    }

    public String getBlockState(int x, int y, int z) {
        return palette.get(blocks[idx(x, y, z)]);
    }

    public boolean isAir(int x, int y, int z) {
        return blocks[idx(x, y, z)] == 0;
    }

    // -- Getters --

    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
    public int getTotalBlocks() { return sizeX * sizeY * sizeZ; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getPalette() { return Collections.unmodifiableList(palette); }

    public int getNonAirCount() {
        int count = 0;
        for (int b : blocks) if (b != 0) count++;
        return count;
    }

    // -- Serialization --

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);
        obj.addProperty("created", createdAt);
        obj.addProperty("sx", sizeX);
        obj.addProperty("sy", sizeY);
        obj.addProperty("sz", sizeZ);

        JsonArray pal = new JsonArray();
        for (String s : palette) pal.add(s);
        obj.add("palette", pal);

        // RLE-encode blocks for compression: [value, count, value, count, ...]
        JsonArray rle = new JsonArray();
        int i = 0;
        while (i < blocks.length) {
            int val = blocks[i];
            int run = 1;
            while (i + run < blocks.length && blocks[i + run] == val) run++;
            rle.add(val);
            rle.add(run);
            i += run;
        }
        obj.add("blocks", rle);

        return obj;
    }

    public static SchematicData fromJson(JsonObject obj) {
        SchematicData data = new SchematicData();
        data.name = obj.has("name") ? obj.get("name").getAsString() : "unnamed";
        data.createdAt = obj.has("created") ? obj.get("created").getAsLong() : 0;
        data.sizeX = obj.get("sx").getAsInt();
        data.sizeY = obj.get("sy").getAsInt();
        data.sizeZ = obj.get("sz").getAsInt();

        JsonArray pal = obj.getAsJsonArray("palette");
        for (JsonElement e : pal) {
            data.addToPalette(e.getAsString());
        }

        data.blocks = new int[data.sizeX * data.sizeY * data.sizeZ];
        JsonArray rle = obj.getAsJsonArray("blocks");
        int pos = 0;
        for (int r = 0; r < rle.size(); r += 2) {
            int val = rle.get(r).getAsInt();
            int run = rle.get(r + 1).getAsInt();
            Arrays.fill(data.blocks, pos, pos + run, val);
            pos += run;
        }

        return data;
    }
}
