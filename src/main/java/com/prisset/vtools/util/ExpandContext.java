package com.prisset.vtools.util;

/**
 * Thread-local flag that controls when bounding box expansion is active.
 * Only enabled during client-side target selection (raycast).
 * Disabled during all other operations (physics, networking, etc).
 */
public final class ExpandContext {

    private static boolean raycastActive = false;

    private ExpandContext() {}

    public static void beginRaycast() {
        raycastActive = true;
    }

    public static void endRaycast() {
        raycastActive = false;
    }

    public static boolean isRaycastActive() {
        return raycastActive;
    }
}
