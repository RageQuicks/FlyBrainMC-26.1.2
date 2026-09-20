package com.fruitfly.server;

import com.fruitfly.FruitFlyConfig;
import com.fruitfly.FruitFlyMod;
import com.fruitfly.entity.FlyEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Heightmap;
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
        ServerTickEvents.END_WORLD_TICK.register(FlyVillageSpawner::tick);
    }

    private static void tick(ServerLevel world) {
        FruitFlyConfig cfg = FruitFlyMod.CONFIG;
        if (cfg.villageFlyTarget <= 0 || world.getGameTime() % Math.max(20, cfg.villageSpawnCheckTicks) != 0) return;

        List<Villager> villagers = new ArrayList<>();
        for (var entity : world.getEntities().getAll()) {
            if (entity instanceof Villager villager && villager.isAlive()) villagers.add(villager);
        }

        Set<Integer> clustered = new HashSet<>();
        double radius = cfg.villageClusterRadius;
        double radius2 = radius * radius;

        for (Villager seed : villagers) {
            if (!clustered.add(seed.getId())) continue;

            List<Villager> cluster = new ArrayList<>();
            cluster.add(seed);
            for (Villager other : villagers) {
                if (other == seed || clustered.contains(other.getId())) continue;
                if (other.distanceToSqr(seed) <= radius2) {
                    cluster.add(other);
                    clustered.add(other.getId());
                }
            }

            if (cluster.size() < 1) continue;
            int existing = 0;
            for (var entity : world.getEntities().getAll()) {
                if (entity instanceof FlyEntity fly && fly.isAlive() && fly.distanceToSqr(seed) <= radius2) existing++;
            }
            int needed = Math.max(0, cfg.villageFlyTarget - existing);
            for (int i = 0; i < needed; i++) {
                spawnOne(world, seed);
            }
        }
    }

    private static void spawnOne(ServerLevel world, Villager anchor) {
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
