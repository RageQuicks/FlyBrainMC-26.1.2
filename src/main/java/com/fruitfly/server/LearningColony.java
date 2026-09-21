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
 * Manages the visible 100-fly reinforcement-learning colony.
 *
 * <p>Each fly gets its own open sandbox: the walls are rendered with particles rather than solid blocks, so the
 * player can walk/fly through them while the fly's body is hard-clamped to its own AABB. A cake at the center of
 * each pen is a real Minecraft food stimulus sensed through the existing vision/olfaction/gustation pipeline.</p>
 */
public final class LearningColony {
    private static final int MAX_FLYES = 100;
    private static final int GRID = 10;
    private static final double CELL = 8.0;
    private static final double PEN = 7.0;
    private static final double HEIGHT = 6.0;
    private static final double FLOOR_MARGIN = 0.6;
    private static final int DRAW_EVERY_TICKS = 10;

    private static final Map<FlyEntity, AABB> BOUNDS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<FlyEntity, Integer> FOOD_X = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<FlyEntity, Integer> FOOD_Z = Collections.synchronizedMap(new IdentityHashMap<>());
    private static boolean active;
    private static int ticks;
    private static ServerLevel level;
    private static double originX, originY, originZ;

    private LearningColony() {}

    public static boolean isActive() { return active; }

    public static synchronized void start(ServerLevel world, double ox, double oy, double oz, int requested) {
        stop();
        level = world;
        active = true;
        ticks = 0;
        originX = ox - (GRID * CELL) / 2.0;
        originY = oy;
        originZ = oz - (GRID * CELL) / 2.0;

        int count = Math.max(1, Math.min(MAX_FLYES, requested));
        Random random = new Random(0xF17E2026L);
        // Starting a visual experiment is authoritative: remove the old debug population first.
        List<FlyEntity> existing = new ArrayList<>();
        world.getEntities(FruitFlyMod.FRUIT_FLY, e -> e.isAlive(), existing);
        for (FlyEntity old : existing) old.discard();

        for (int i = 0; i < count; i++) {
            int gx = i % GRID;
            int gz = i / GRID;
            double minX = originX + gx * CELL + (CELL - PEN) / 2.0;
            double minZ = originZ + gz * CELL + (CELL - PEN) / 2.0;
            AABB box = new AABB(minX, originY, minZ, minX + PEN, originY + HEIGHT, minZ + PEN);
            FlyEntity fly = FruitFlyMod.FRUIT_FLY.create(world, EntitySpawnReason.COMMAND);
            if (fly == null) continue;
            double x = box.minX + 0.5 + random.nextDouble() * (PEN - 1.0);
            double z = box.minZ + 0.5 + random.nextDouble() * (PEN - 1.0);
            double y = box.minY + 1.2 + random.nextDouble() * 1.8;
            fly.setPos(x, y, z);
            fly.setYRot(random.nextFloat() * 360f);
            fly.setXRot(0f);
            fly.setMale(random.nextBoolean());
            fly.setLearningSandbox(box);
            fly.setFlyScale(1.0f);
            fly.finalizeSpawn(world, world.getCurrentDifficultyAt(fly.blockPosition()), EntitySpawnReason.COMMAND, null);
            world.addFreshEntity(fly);
            BOUNDS.put(fly, box);
            FOOD_X.put(fly, (int) Math.floor((box.minX + box.maxX) * 0.5));
            FOOD_Z.put(fly, (int) Math.floor((box.minZ + box.maxZ) * 0.5));
        }
        FruitFlyMod.LOGGER.info("Visual learning colony started: {} flies, {}x{} pens at {},{},{}", count, GRID, GRID, ox, oy, oz);
    }

    public static synchronized void stop() {
        List<FlyEntity> flies = new ArrayList<>(BOUNDS.keySet());
        for (FlyEntity fly : flies) {
            if (fly != null && fly.isAlive()) fly.discard();
        }
        BOUNDS.clear();
        FOOD_X.clear();
        FOOD_Z.clear();
        active = false;
        level = null;
    }

    public static synchronized void tick(MinecraftServer server) {
        if (!active) return;
        ticks++;
        if (ticks % DRAW_EVERY_TICKS != 0) return;
        ServerLevel world = level;
        if (world == null) return;

        List<FlyEntity> flies = new ArrayList<>(BOUNDS.keySet());
        for (FlyEntity fly : flies) {
            if (fly == null || !fly.isAlive()) {
                BOUNDS.remove(fly);
                FOOD_X.remove(fly);
                FOOD_Z.remove(fly);
                continue;
            }
            AABB box = BOUNDS.get(fly);
            if (box == null) continue;
            constrain(fly, box);
            drawBoundary(world, box, fly.getFlyColor());
            maintainFood(world, box);
        }
        if (BOUNDS.isEmpty()) {
            active = false;
            level = null;
        }
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

    private static void drawBoundary(ServerLevel world, AABB b, int rgb) {
        // Particle color is intentionally neutral; the fly's colored name identifies its pen.
        double y0 = b.minY, y1 = b.maxY;
        double[] xs = {b.minX, b.maxX};
        double[] zs = {b.minZ, b.maxZ};
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
        for (double x : xs) for (double z : zs) {
            for (int i = 0; i <= steps; i++) particle(world, x, y0 + (y1-y0)*i/steps, z);
        }
    }

    private static void particle(ServerLevel world, double x, double y, double z) {
        world.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
    }

    private static void maintainFood(ServerLevel world, AABB box) {
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        AABB foodBox = new AABB(cx - 0.6, box.minY, cz - 0.6, cx + 0.6, box.maxY, cz + 0.6);
        boolean present = !world.getEntitiesOfClass(ItemEntity.class, foodBox,
                e -> e.isAlive() && e.getItem().getItem() == Items.APPLE).isEmpty();
        if (!present) {
            ItemEntity apple = new ItemEntity(world, cx, box.minY + 1.0, cz, new ItemStack(Items.APPLE));
            apple.setNoGravity(true);
            apple.setDeltaMovement(0, 0, 0);
            world.addFreshEntity(apple);
        }
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
