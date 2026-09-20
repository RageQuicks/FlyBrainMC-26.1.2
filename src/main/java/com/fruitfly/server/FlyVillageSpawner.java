package com.fruitfly.server;

import com.fruitfly.FruitFlyConfig;
import com.fruitfly.FruitFlyMod;
import com.fruitfly.entity.FlyEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ecological population seeding only. Once spawned, flies receive no Minecraft AI goals:
 * their subsequent actions are selected by their nervous system.
 */
public final class FlyVillageSpawner {
    private FlyVillageSpawner() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> { for (ServerLevel level : server.getAllLevels()) tick(level); });
    }

    private static void tick(ServerLevel world) {
        FruitFlyConfig cfg = FruitFlyMod.CONFIG;
        if (cfg.villageFlyTarget <= 0 || world.getGameTime() % Math.max(20, cfg.villageSpawnCheckTicks) != 0) return;

        List<net.minecraft.world.entity.Entity> villagers = new ArrayList<>();
        world.getEntities(net.minecraft.world.entity.EntityType.VILLAGER, e -> e.isAlive(), villagers);

        Set<Integer> clustered = new HashSet<>();
        double radius = cfg.villageClusterRadius;
        double radius2 = radius * radius;

        for (net.minecraft.world.entity.Entity seed : villagers) {
            if (!clustered.add(seed.getId())) continue;

            List<net.minecraft.world.entity.Entity> cluster = new ArrayList<>();
            cluster.add(seed);
            for (net.minecraft.world.entity.Entity other : villagers) {
                if (other == seed || clustered.contains(other.getId())) continue;
                if (other.distanceToSqr(seed) <= radius2) {
                    cluster.add(other);
                    clustered.add(other.getId());
                }
            }

            if (cluster.size() < 1) continue;
            int existing = 0;
            List<FlyEntity> nearby = new ArrayList<>();
            world.getEntities(FruitFlyMod.FRUIT_FLY, e -> e.isAlive() && e.distanceToSqr(seed) <= radius2, nearby);
            existing = nearby.size();
            int needed = Math.max(0, cfg.villageFlyTarget - existing);
            for (int i = 0; i < needed; i++) {
                spawnOne(world, seed);
            }
        }
    }

    private static void spawnOne(ServerLevel world, net.minecraft.world.entity.Entity anchor) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int x = anchor.blockPosition().getX() + rng.nextInt(-8, 9);
        int z = anchor.blockPosition().getZ() + rng.nextInt(-8, 9);
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);

        FlyEntity fly = FruitFlyMod.FRUIT_FLY.create(world, EntitySpawnReason.NATURAL);
        if (fly == null) return;
        fly.setPos(x + 0.5, y + 0.15, z + 0.5);
        fly.setMale(rng.nextBoolean());
        world.addFreshEntity(fly);
    }
}
