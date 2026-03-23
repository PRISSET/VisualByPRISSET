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
    private BlockPos anchor;          // confirmed world position of schematic origin
    private BlockPos previewAnchor;   // follows crosshair while placement mode is on
    private int rotation;             // 0-3 (0=none, 1=90CW, 2=180, 3=270CW)
    private boolean previewing;       // ghost render active
    private boolean placementMode;    // cursor-follow mode (preview moves with crosshair)
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
        this.previewAnchor = null;
        this.previewing = false;
        this.placementMode = false;
        return true;
    }

    public void unload() {
        this.schematic = null;
        this.loadedName = null;
        this.anchor = null;
        this.previewAnchor = null;
        this.rotation = 0;
        this.previewing = false;
        this.placementMode = false;
    }

    public boolean isLoaded() { return schematic != null; }
    public SchematicData getSchematic() { return schematic; }
    public String getLoadedName() { return loadedName; }

    // -- Anchor --

    public void setAnchor(BlockPos pos) { this.anchor = pos.toImmutable(); }
    public BlockPos getAnchor() { return anchor; }
    public boolean hasAnchor() { return anchor != null; }

    // -- Placement mode (cursor-follow) --

    public boolean isPlacementMode() { return placementMode; }

    public void setPlacementMode(boolean val) {
        this.placementMode = val;
        if (val && schematic != null) {
            this.previewing = true;
        }
        if (!val) {
            this.previewAnchor = null;
        }
    }

    public void setPreviewAnchor(BlockPos pos) {
        this.previewAnchor = pos != null ? pos.toImmutable() : null;
    }

    public BlockPos getPreviewAnchor() { return previewAnchor; }

    /**
     * Confirm placement at current preview position.
     * Copies previewAnchor to anchor and exits placement mode.
     */
    public void confirmPlacement() {
        if (previewAnchor != null) {
            this.anchor = previewAnchor;
            this.placementMode = false;
        }
    }

    /**
     * Returns the effective anchor for rendering: previewAnchor while placing, anchor when confirmed.
     */
    public BlockPos getEffectiveAnchor() {
        if (placementMode && previewAnchor != null) return previewAnchor;
        return anchor;
    }

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

    /**
     * Ready for rendering (either confirmed anchor or live preview anchor).
     */
    public boolean isReadyToRender() {
        return schematic != null && getEffectiveAnchor() != null;
    }

    // -- Coordinate transformation --

    /**
     * Transform local schematic coord (x, y, z) to world pos,
     * applying rotation around Y axis and anchor offset.
     */
    public BlockPos localToWorld(int lx, int ly, int lz) {
        return localToWorld(lx, ly, lz, anchor);
    }

    /**
     * Transform using a specific anchor (used for preview rendering).
     */
    public BlockPos localToWorld(int lx, int ly, int lz, BlockPos anch) {
        if (anch == null) return new BlockPos(lx, ly, lz);

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
            anch.getX() + wx,
            anch.getY() + ly,
            anch.getZ() + wz
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
