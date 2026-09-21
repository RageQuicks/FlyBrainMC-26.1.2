package com.fruitfly.server;

import com.fruitfly.FruitFlyMod;
import com.fruitfly.entity.FlyEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Visible embodied reinforcement-learning experiment.
 *
 * Each fly has a repeatable episode: start somewhere in its pen, locate the real Minecraft apple,
 * touch it with its proboscis, consume it, receive reward, and get reset to a new start position.
 * The brain is NOT reset between episodes. At the end of a generation, the best fly's learned
 * synaptic efficacy vector is loaded into the next generation as experimental inherited memory.
 */
public final class LearningColony {
    private static final int MAX_FLIES = 100;
    private static final int GRID = 10;
    private static final double CELL = 8.0;
    private static final double PEN = 7.0;
    private static final double HEIGHT = 6.0;
    private static final int DRAW_EVERY_TICKS = 10;
    private static final int EPISODE_LIMIT_TICKS = 600;       // 30 seconds
    private static final int EPISODES_PER_GENERATION = 10;
    private static final int SUCCESS_REWARD_TICKS = 8;
    private static final int MAX_DIFFICULTY = 4;
    private static final int SUCCESSES_TO_ADVANCE = 2;
    private static final int FAILURES_TO_RETREAT = 2;

    private static final Map<FlyEntity, AABB> BOUNDS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<FlyEntity, Episode> EPISODES = Collections.synchronizedMap(new IdentityHashMap<>());

    private static boolean active;
    private static int ticks;
    private static int generation;
    private static long totalEpisodes;
    private static long totalSuccesses;
    private static long generationEpisodes;
    private static double totalSuccessTimeTicks;
    private static double bestTimeTicks = Double.POSITIVE_INFINITY;
    private static int bestFlyNumber;
    private static ServerLevel level;
    private static double originX, originY, originZ;
    private static int configuredCount;
    private static Random random;

    private LearningColony() {}

    private static final class Episode {
        int ticks;
        int successes;
        int generationEpisodes;
        double lastDistance = Double.NaN;
        double totalDistance;
        double totalTimeTicks;
        int rewardHold;
        boolean foodSeen;
        int difficulty;
        int successStreak;
        int failureStreak;
        double foodX, foodY, foodZ;
        double startX, startY, startZ;
    }

    public static boolean isActive() { return active; }

    public static synchronized void start(ServerLevel world, double ox, double oy, double oz, int requested) {
        stop();
        level = world;
        active = true;
        ticks = 0;
        generation = 1;
        totalEpisodes = 0;
        totalSuccesses = 0;
        generationEpisodes = 0;
        totalSuccessTimeTicks = 0;
        bestTimeTicks = Double.POSITIVE_INFINITY;
        bestFlyNumber = 0;
        configuredCount = Math.max(1, Math.min(MAX_FLIES, requested));
        random = new Random(0xF17E2026L);
        originX = ox - (GRID * CELL) / 2.0;
        originY = oy;
        originZ = oz - (GRID * CELL) / 2.0;

        List<FlyEntity> existing = new ArrayList<>();
        world.getEntities(FruitFlyMod.FRUIT_FLY, e -> e.isAlive(), existing);
        for (FlyEntity old : existing) old.discard();

        for (int i = 0; i < configuredCount; i++) spawnFly(world, i);

        FruitFlyMod.LOGGER.info("Learning colony started: {} flies, generation 1, {} episodes/gen", configuredCount, EPISODES_PER_GENERATION);
    }

    private static void spawnFly(ServerLevel world, int index) {
        int gx = index % GRID;
        int gz = index / GRID;
        double minX = originX + gx * CELL + (CELL - PEN) / 2.0;
        double minZ = originZ + gz * CELL + (CELL - PEN) / 2.0;
        AABB box = new AABB(minX, originY, minZ, minX + PEN, originY + HEIGHT, minZ + PEN);

        FlyEntity fly = FruitFlyMod.FRUIT_FLY.create(world, EntitySpawnReason.COMMAND);
        if (fly == null) return;
        fly.setMale(random.nextBoolean());
        fly.setFlyScale(1.0f);
        fly.finalizeSpawn(world, world.getCurrentDifficultyAt(fly.blockPosition()), EntitySpawnReason.COMMAND, null);
        fly.setLearningSandbox(box);
        BOUNDS.put(fly, box);
        resetEpisode(fly, box, true);
        world.addFreshEntity(fly);
    }

    public static synchronized void stop() {
        List<FlyEntity> flies = new ArrayList<>(BOUNDS.keySet());
        for (FlyEntity fly : flies) if (fly != null && fly.isAlive()) fly.discard();
        BOUNDS.clear();
        EPISODES.clear();
        active = false;
        level = null;
        configuredCount = 0;
    }

    public static synchronized void tick(MinecraftServer server) {
        if (!active) return;
        ticks++;
        ServerLevel world = level;
        if (world == null) return;

        List<FlyEntity> flies = new ArrayList<>(BOUNDS.keySet());
        for (FlyEntity fly : flies) {
            if (fly == null || !fly.isAlive()) {
                BOUNDS.remove(fly);
                EPISODES.remove(fly);
                continue;
            }
            AABB box = BOUNDS.get(fly);
            Episode ep = EPISODES.get(fly);
            if (box == null || ep == null) continue;

            constrain(fly, box);

            boolean foodPresent = foodPresent(world, box);
            if (ep.foodSeen && !foodPresent) {
                completeEpisode(world, fly, box, ep);
                ep = EPISODES.get(fly);
                foodPresent = false;
            }
            ep.foodSeen = foodPresent;

            double distance = distanceToFood(fly, box);
            if (!Double.isNaN(ep.lastDistance)) {
                double progress = ep.lastDistance - distance;
                ep.totalDistance += Math.max(0.0, progress);
                // Small shaping signal: reward approach, mildly penalize aimless wandering.
                double shaped = Math.max(-0.03, Math.min(0.03, progress * 0.8 - 0.001));
                fly.setLearningReward(ep.rewardHold > 0 ? 1.0 : shaped);
            } else {
                fly.setLearningReward(ep.rewardHold > 0 ? 1.0 : -0.001);
            }
            ep.lastDistance = distance;
            if (ep.rewardHold > 0) ep.rewardHold--;

            ep.ticks++;
            if (ep.ticks >= EPISODE_LIMIT_TICKS) {
                fly.setLearningReward(-0.15);
                ep.failureStreak++;
                ep.successStreak = 0;
                if (ep.failureStreak >= FAILURES_TO_RETREAT) {
                    ep.difficulty = Math.max(0, ep.difficulty - 1);
                    ep.failureStreak = 0;
                    FruitFlyMod.LOGGER.info("Fly-{} reduced to task difficulty {} after repeated failures", fly.getFlyNumber(), ep.difficulty);
                }
                resetEpisode(fly, box, false);
            }

            if (ticks % DRAW_EVERY_TICKS == 0) {
                drawBoundary(world, box);
                maintainFood(world, box);
            }
        }

        if (ticks % DRAW_EVERY_TICKS == 0 && generationEpisodes >= configuredCount * EPISODES_PER_GENERATION) {
            advanceGeneration(world, flies);
        }
        if (BOUNDS.isEmpty()) {
            active = false;
            level = null;
        }
    }

    private static void completeEpisode(ServerLevel world, FlyEntity fly, AABB box, Episode ep) {
        totalEpisodes++;
        generationEpisodes++;
        totalSuccesses++;
        ep.successes++;
        ep.generationEpisodes++;
        ep.totalTimeTicks += ep.ticks;
        ep.successStreak++;
        ep.failureStreak = 0;
        if (ep.successStreak >= SUCCESSES_TO_ADVANCE) {
            ep.difficulty = Math.min(MAX_DIFFICULTY, ep.difficulty + 1);
            ep.successStreak = 0;
            FruitFlyMod.LOGGER.info("Fly-{} advanced to task difficulty {}", fly.getFlyNumber(), ep.difficulty);
        }
        totalSuccessTimeTicks += ep.ticks;

        if (ep.ticks < bestTimeTicks) {
            bestTimeTicks = ep.ticks;
            bestFlyNumber = fly.getFlyNumber();
        }

        ep.rewardHold = SUCCESS_REWARD_TICKS;
        fly.setLearningReward(1.0);
        fly.captureLearningMemory();

        // A visible success marker makes it obvious that an episode really completed.
        world.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                (box.minX + box.maxX) * 0.5, box.minY + 1.0, (box.minZ + box.maxZ) * 0.5,
                12, 0.5, 0.5, 0.5, 0.08);

        resetEpisode(fly, box, false);
    }

    private static void resetEpisode(FlyEntity fly, AABB box, boolean first) {
        Episode ep = EPISODES.computeIfAbsent(fly, f -> new Episode());
        ep.ticks = 0;
        ep.lastDistance = Double.NaN;
        ep.totalDistance = 0;
        ep.foodSeen = false;
        ep.rewardHold = first ? 0 : Math.max(ep.rewardHold, SUCCESS_REWARD_TICKS);

        double x = box.minX + 0.6 + random.nextDouble() * (PEN - 1.2);
        double z = box.minZ + 0.6 + random.nextDouble() * (PEN - 1.2);
        double y = box.minY + 1.0 + random.nextDouble() * 2.0;
        if (ep.difficulty >= 2) {
            // Harder stages deliberately begin farther from the food in the horizontal plane.
            x = randomEdgePosition(box.minX, box.maxX);
            z = randomEdgePosition(box.minZ, box.maxZ);
        }
        if (ep.difficulty >= 3) {
            // Advanced stages start low and place food high enough that hopping cannot solve the task.
            y = box.minY + 0.8;
        }
        ep.startX = x;
        ep.startY = y;
        ep.startZ = z;
        fly.setPos(x, y, z);
        fly.setDeltaMovement(0, 0, 0);
        fly.setLearningReward(first ? 0 : 1.0);
        maintainFood(level, box, ep);
    }

    private static void advanceGeneration(ServerLevel world, List<FlyEntity> flies) {
        FlyEntity champion = null;
        int championSuccesses = -1;
        double championAvg = Double.POSITIVE_INFINITY;
        for (FlyEntity fly : flies) {
            Episode ep = EPISODES.get(fly);
            if (ep == null) continue;
            double avg = ep.generationEpisodes == 0 ? Double.POSITIVE_INFINITY : ep.totalTimeTicks / ep.generationEpisodes;
            if (ep.generationEpisodes > 0 && (ep.successes > championSuccesses || (ep.successes == championSuccesses && avg < championAvg))) {
                champion = fly;
                championSuccesses = ep.successes;
                championAvg = avg;
            }
        }

        if (champion != null && champion.learningMemory() == null) {
            // The memory snapshot is queued onto the brain thread; wait one tick rather than silently losing it.
            champion.captureLearningMemory();
            return;
        }

        float[] inherited = champion == null ? null : champion.learningMemory();
        generation++;
        generationEpisodes = 0;

        for (FlyEntity fly : flies) {
            Episode ep = EPISODES.get(fly);
            if (ep == null) continue;
            ep.successes = 0;
            ep.generationEpisodes = 0;
            ep.totalTimeTicks = 0;
            ep.successStreak = 0;
            ep.failureStreak = 0;
            if (inherited != null) fly.applyLearningMemory(inherited);
            resetEpisode(fly, BOUNDS.get(fly), false);
        }

        world.sendParticles(ParticleTypes.END_ROD,
                (originX + GRID * CELL * 0.5), originY + 2.0, (originZ + GRID * CELL * 0.5),
                80, GRID * CELL * 0.45, 1.0, GRID * CELL * 0.45, 0.02);
        FruitFlyMod.LOGGER.info("Learning colony advanced to generation {}. Inherited memory: {}", generation, inherited != null);
    }

    public static AABB boundsFor(FlyEntity fly) { return BOUNDS.get(fly); }

    public static void constrain(FlyEntity fly) {
        AABB box = BOUNDS.get(fly);
        if (box != null) constrain(fly, box);
    }

    private static void constrain(FlyEntity fly, AABB box) {
        double margin = Math.max(0.15, fly.getBbWidth() * 0.5);
        double x = clamp(fly.getX(), box.minX + margin, box.maxX - margin);
        double y = clamp(fly.getY(), box.minY + margin, box.maxY - margin);
        double z = clamp(fly.getZ(), box.minZ + margin, box.maxZ - margin);
        if (x != fly.getX() || y != fly.getY() || z != fly.getZ()) {
            fly.setPos(x, y, z);
            fly.setDeltaMovement(0, 0, 0);
        }
    }

    private static boolean foodPresent(ServerLevel world, AABB box) {
        // The curriculum can move the apple away from the pen center, so detect it anywhere in this fly's pen.
        return !world.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && e.getItem().getItem() == Items.APPLE).isEmpty();
    }

    private static double distanceToFood(FlyEntity fly, AABB box) {
        Episode ep = EPISODES.get(fly);
        if (ep == null || Double.isNaN(ep.foodX)) return Double.POSITIVE_INFINITY;
        double dx = fly.getX() - ep.foodX, dy = fly.getY() - ep.foodY, dz = fly.getZ() - ep.foodZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void drawBoundary(ServerLevel world, AABB b) {
        double y0 = b.minY, y1 = b.maxY;
        int steps = 3;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = b.minX + (b.maxX - b.minX) * t;
            double z = b.minZ + (b.maxZ - b.minZ) * t;
            particle(world, x, y0, b.minZ);
            particle(world, x, y0, b.maxZ);
            particle(world, x, y1, b.minZ);
            particle(world, x, y1, b.maxZ);
            particle(world, b.minX, y0, z);
            particle(world, b.maxX, y0, z);
            particle(world, b.minX, y1, z);
            particle(world, b.maxX, y1, z);
        }
    }

    private static void particle(ServerLevel world, double x, double y, double z) {
        world.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
    }

    private static void maintainFood(ServerLevel world, AABB box) {
        Episode ep = null;
        for (Map.Entry<FlyEntity, AABB> e : BOUNDS.entrySet()) {
            if (e.getValue() == box) {
                ep = EPISODES.get(e.getKey());
                break;
            }
        }
        if (ep != null) maintainFood(world, box, ep);
    }

    private static void maintainFood(ServerLevel world, AABB box, Episode ep) {
        if (world == null || ep == null) return;
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        AABB foodBox = new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        boolean present = !world.getEntitiesOfClass(ItemEntity.class, foodBox,
                e -> e.isAlive() && e.getItem().getItem() == Items.APPLE).isEmpty();
        if (!present) {
            double spread = 0.5 + 0.65 * ep.difficulty;
            double x = clamp(cx + (random.nextDouble() - 0.5) * spread, box.minX + 0.5, box.maxX - 0.5);
            double z = clamp(cz + (random.nextDouble() - 0.5) * spread, box.minZ + 0.5, box.maxZ - 0.5);
            double y = box.minY + 1.0 + Math.min(MAX_DIFFICULTY, ep.difficulty) * 1.0;
            y = clamp(y, box.minY + 1.0, box.maxY - 0.5);
            ep.foodX = x;
            ep.foodY = y;
            ep.foodZ = z;
            ItemEntity apple = new ItemEntity(world, x, y, z, new ItemStack(Items.APPLE));
            apple.setNoGravity(true);
            apple.setDeltaMovement(0, 0, 0);
            world.addFreshEntity(apple);
        } else {
            ItemEntity apple = world.getEntitiesOfClass(ItemEntity.class, foodBox,
                    e -> e.isAlive() && e.getItem().getItem() == Items.APPLE).get(0);
            ep.foodX = apple.getX();
            ep.foodY = apple.getY();
            ep.foodZ = apple.getZ();
        }
    }

    private static double randomEdgePosition(double min, double max) {
        double margin = 0.7;
        return random.nextBoolean()
                ? min + margin + random.nextDouble() * 0.8
                : max - margin - random.nextDouble() * 0.8;
    }

    /** Reset the current chambers/episodes without resetting the flies' brains or learned plasticity. */
    public static synchronized void resetEpisodes() {
        if (!active || level == null) return;
        for (Map.Entry<FlyEntity, AABB> e : BOUNDS.entrySet()) {
            resetEpisode(e.getKey(), e.getValue(), false);
        }
    }

    public static String statusText() {
        if (!active) return "Learning colony: STOPPED";
        long completed = totalEpisodes;
        double successRate = completed == 0 ? 0 : 100.0 * totalSuccesses / completed;
        double avg = totalSuccesses == 0 ? 0 : totalSuccessTimeTicks / totalSuccesses / 20.0;
        double best = bestTimeTicks == Double.POSITIVE_INFINITY ? 0 : bestTimeTicks / 20.0;
        return String.format(java.util.Locale.ROOT,
                "Learning colony: RUNNING | gen %d | flies %d | episodes %d | successes %d (%.1f%%) | avg success %.2fs | best %.2fs (Fly-%d) | %d/%d gen episodes | curriculum 0-%d",
                generation, configuredCount, completed, totalSuccesses, successRate, avg, best, bestFlyNumber,
                generationEpisodes, configuredCount * EPISODES_PER_GENERATION, MAX_DIFFICULTY);
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
