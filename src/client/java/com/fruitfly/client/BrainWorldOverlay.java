package com.fruitfly.client;

/**
 * Brain world overlay placeholder while the 26.1 render pipeline migration is completed.
 *
 * The original overlay used the pre-26.1 immediate-mode world renderer. Minecraft 26.1
 * moved world rendering to extracted render states and submit nodes, so that renderer is
 * being reimplemented against the new API rather than carrying obsolete GL calls forward.
 */
public final class BrainWorldOverlay {
    private BrainWorldOverlay() {}

    public static void register() {
        // Re-enabled once the new 26.1 world-render submission path is implemented.
    }

    public static int pointCount() {
        return 0;
    }
}
