package com.prisset.vtools.blueprint;

import net.minecraft.util.math.BlockPos;

/**
 * Manages loaded schematic placement: anchor position, rotation, preview state.
 *
 * Rotation is in 90-degree steps (0, 1, 2, 3 = 0, 90, 180, 270 degrees CW).
 * Rotation transforms schematic coordinates around Y axis relative to anchor.
 */
public final class BlueprintPlacer {

    private static final BlueprintPlacer INSTANCE = new BlueprintPlacer();

    private SchematicData schematic;
    private BlockPos anchor;       // world position of schematic origin
    private int rotation;          // 0-3 (0=none, 1=90CW, 2=180, 3=270CW)
    private boolean previewing;    // ghost render active
    private String loadedName;

    private BlueprintPlacer() {}
    public static BlueprintPlacer instance() { return INSTANCE; }

    // -- Load/unload --

    public boolean load(String name) {
        SchematicData data = BlueprintStorage.load(name);
        if (data == null) return false;
        this.schematic = data;
        this.loadedName = name;
        this.rotation = 0;
        this.anchor = null;
        this.previewing = false;
        return true;
    }

    public void unload() {
        this.schematic = null;
        this.loadedName = null;
        this.anchor = null;
        this.rotation = 0;
        this.previewing = false;
    }

    public boolean isLoaded() { return schematic != null; }
    public SchematicData getSchematic() { return schematic; }
    public String getLoadedName() { return loadedName; }

    // -- Anchor --

    public void setAnchor(BlockPos pos) { this.anchor = pos.toImmutable(); }
    public BlockPos getAnchor() { return anchor; }
    public boolean hasAnchor() { return anchor != null; }

    // -- Rotation --

    public int getRotation() { return rotation; }

    public void rotateCW() {
        rotation = (rotation + 1) % 4;
    }

    public void rotateCCW() {
        rotation = (rotation + 3) % 4;
    }

    // -- Preview --

    public boolean isPreviewing() { return previewing; }
    public void setPreviewing(boolean val) { this.previewing = val; }

    public boolean isReadyToPlace() {
        return schematic != null && anchor != null;
    }

    // -- Coordinate transformation --

    /**
     * Transform local schematic coord (x, y, z) to world pos,
     * applying rotation around Y axis and anchor offset.
     */
    public BlockPos localToWorld(int lx, int ly, int lz) {
        if (anchor == null) return new BlockPos(lx, ly, lz);

        int sx = schematic.getSizeX();
        int sz = schematic.getSizeZ();
        int wx, wz;

        switch (rotation) {
            case 1 -> { // 90 CW: (x,z) -> (sz-1-z, x)
                wx = sz - 1 - lz;
                wz = lx;
            }
            case 2 -> { // 180: (x,z) -> (sx-1-x, sz-1-z)
                wx = sx - 1 - lx;
                wz = sz - 1 - lz;
            }
            case 3 -> { // 270 CW: (x,z) -> (z, sx-1-x)
                wx = lz;
                wz = sx - 1 - lx;
            }
            default -> { // 0: no rotation
                wx = lx;
                wz = lz;
            }
        }

        return new BlockPos(
            anchor.getX() + wx,
            anchor.getY() + ly,
            anchor.getZ() + wz
        );
    }

    /**
     * Get rotated dimensions (X and Z swap on 90/270 rotation).
     */
    public int getWorldSizeX() {
        if (schematic == null) return 0;
        return (rotation == 1 || rotation == 3) ? schematic.getSizeZ() : schematic.getSizeX();
    }

    public int getWorldSizeZ() {
        if (schematic == null) return 0;
        return (rotation == 1 || rotation == 3) ? schematic.getSizeX() : schematic.getSizeZ();
    }

    public int getWorldSizeY() {
        return schematic == null ? 0 : schematic.getSizeY();
    }
}
