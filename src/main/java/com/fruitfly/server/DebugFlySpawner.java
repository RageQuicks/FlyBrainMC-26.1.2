package com.fruitfly.server;

import com.fruitfly.FruitFlyMod;
import com.fruitfly.entity.FlyEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Temporary debug population and hard player leash. */
public final class DebugFlySpawner {
    private static boolean spawnedThisServer;

    private DebugFlySpawner() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerPlayer anchor = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            if (anchor == null) return;

            if (!spawnedThisServer) {
                spawnedThisServer = true;
                spawnAround(anchor, FruitFlyMod.CONFIG.debugStartFlyCount);
            }

            leashAll(server.getAllLevels(), anchor);
        });
    }

    private static void spawnAround(ServerPlayer anchor, int count) {
        if (count <= 0) return;
        ServerLevel world = anchor.serverLevel();
        List<FlyEntity> existing = new ArrayList<>();
        world.getEntities(FruitFlyMod.FRUIT_FLY,
                e -> e.isAlive() && e.distanceToSqr(anchor) <= 32.0 * 32.0, existing);

        int needed = Math.max(0, count - existing.size());
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < needed; i++) {
            double angle = rng.nextDouble(0, Math.PI * 2.0);
            double radius = rng.nextDouble(4.0, 14.0);
            double x = anchor.getX() + Math.cos(angle) * radius;
            double z = anchor.getZ() + Math.sin(angle) * radius;
            double y = anchor.getY() + rng.nextDouble(1.0, 4.0);

            FlyEntity fly = FruitFlyMod.FRUIT_FLY.create(world, EntitySpawnReason.NATURAL);
            if (fly == null) continue;
            fly.setPos(x, y, z);
            fly.setMale(rng.nextBoolean());
            world.addFreshEntity(fly);
        }
        FruitFlyMod.LOGGER.info("Debug population: spawned {} fruit flies around {}", needed, anchor.getGameProfile().name());
    }

    private static void leashAll(Iterable<ServerLevel> levels, ServerPlayer preferredAnchor) {
        for (ServerLevel level : levels) {
            List<FlyEntity> flies = new ArrayList<>();
            level.getEntities(FruitFlyMod.FRUIT_FLY, e -> e.isAlive(), flies);
            for (FlyEntity fly : flies) {
                ServerPlayer nearest = nearestPlayer(fly, preferredAnchor);
                if (nearest == null) continue;

                double max = Math.max(4.0, FruitFlyMod.CONFIG.debugLeashRadius);
                double d2 = fly.distanceToSqr(nearest);
                if (d2 <= max * max) continue;

                double d = Math.sqrt(d2);
                double nx = (fly.getX() - nearest.getX()) / d;
                double ny = (fly.getY() - nearest.getY()) / d;
                double nz = (fly.getZ() - nearest.getZ()) / d;
                double target = Math.max(1.0, max - 1.0);

                fly.setPos(nearest.getX() + nx * target,
                        nearest.getY() + ny * target,
                        nearest.getZ() + nz * target);
                fly.setDeltaMovement(0, 0, 0);
            }
        }
    }

    private static ServerPlayer nearestPlayer(FlyEntity fly, ServerPlayer preferred) {
        ServerPlayer best = preferred;
        double bestD2 = preferred == null ? Double.MAX_VALUE : fly.distanceToSqr(preferred);
        for (ServerPlayer p : fly.level().players()) {
            double d2 = fly.distanceToSqr(p);
            if (d2 < bestD2) {
                best = p;
                bestD2 = d2;
            }
        }
        return best;
    }
}
