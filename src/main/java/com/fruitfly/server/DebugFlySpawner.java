package com.fruitfly.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Legacy debug spawner is intentionally inert. The visible 100-fly experiment is started explicitly with
 * /fruitfly learning start so the player controls when the expensive colony exists.
 */
public final class DebugFlySpawner {
    private DebugFlySpawner() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LearningColony::tick);
    }
}
