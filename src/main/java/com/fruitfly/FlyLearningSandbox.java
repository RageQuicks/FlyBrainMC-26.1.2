package com.fruitfly;

import com.fruitfly.brain.BrainRunner;
import com.fruitfly.brain.Connectome;
import com.fruitfly.brain.LifConfig;
import com.fruitfly.brain.MotorDecoder;
import com.fruitfly.brain.PopulationIndex;
import com.fruitfly.brain.RetinaGeometry;
import com.fruitfly.brain.SensoryEncoders;
import com.fruitfly.brain.SensoryFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Engine-independent learning laboratory. Minecraft is deliberately absent from this loop: each agent owns a real
 * connectome brain, a tiny embodied world, sensors, motor decoder and slowly changing synaptic efficacy. The purpose
 * of this first experiment is to determine whether useful fly-like activity can emerge before we put the agents back
 * into the expensive Minecraft world.
 */
public final class FlyLearningSandbox implements AutoCloseable {
    private static final double ARENA = 64.0;
    private static final double FOOD_REWARD = 1.0;
    private static final double STEP_SECONDS = 0.05;

    private final FruitFlyConfig config;
    private final Connectome connectome;
    private final PopulationIndex populations;
    private final RetinaGeometry geometry;
    private final List<Agent> agents = new ArrayList<>();
    private final List<Food> food = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final SplittableRandom rng = new SplittableRandom(0xF17E20260920L);
    private Thread thread;
    private long cycles;
    private double rewardSum;
    private double distanceSum;
    private long lastLogCycle;

    public FlyLearningSandbox(FruitFlyConfig config, Connectome connectome, PopulationIndex populations, RetinaGeometry geometry) {
        this.config = config;
        this.connectome = connectome;
        this.populations = populations;
        this.geometry = geometry;
    }

    public synchronized void start() {
        if (!config.learningSandboxEnabled || running.get()) return;
        int count = Math.max(1, Math.min(100, config.learningSandboxFlies));
        food.clear();
        for (int i = 0; i < 16; i++) food.add(new Food(4 + rng.nextDouble(ARENA - 8), 4 + rng.nextDouble(ARENA - 8)));
        agents.clear();
        for (int i = 0; i < count; i++) agents.add(new Agent(i));
        running.set(true);
        thread = new Thread(this::loop, "fruitfly-learning-sandbox");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 2);
        thread.start();
        FruitFlyMod.LOGGER.info("Learning sandbox started: {} parallel connectome flies", count);
    }

    private void loop() {
        long next = System.nanoTime();
        while (running.get()) {
            long t0 = System.nanoTime();
            step();
            long wall = System.nanoTime() - t0;
            long period = 50_000_000L;
            next += period;
            long sleep = next - System.nanoTime();
            if (sleep > 0) java.util.concurrent.locks.LockSupport.parkNanos(sleep);
            else if (-sleep > period * 4) next = System.nanoTime();
            if (wall > 45_000_000L && cycles % 20 == 0) {
                FruitFlyMod.LOGGER.warn("Learning sandbox is behind real time: cycle {} took {} ms", cycles, wall / 1_000_000.0);
            }
        }
    }

    private void step() {
        double ms = Math.max(0.5, config.learningSandboxMsPerCycle);
        for (Agent a : agents) {
            a.step(ms);
            rewardSum += a.lastReward;
            distanceSum += a.lastDistance;
        }
        cycles++;
        double logEvery = Math.max(1.0, config.learningSandboxLogSeconds);
        long logCycles = Math.max(1L, Math.round(logEvery / STEP_SECONDS));
        if (cycles - lastLogCycle >= logCycles) {
            lastLogCycle = cycles;
            double n = Math.max(1, agents.size());
            FruitFlyMod.LOGGER.info("Sandbox t={}s flies={} avgReward={}/cycle avgMove={} blocks/cycle hungry={}",
                    String.format("%.1f", cycles * STEP_SECONDS), agents.size(),
                    String.format("%.5f", rewardSum / (n * logCycles)),
                    String.format("%.4f", distanceSum / (n * logCycles)),
                    agents.stream().filter(a -> a.hunger < 0.15).count());
            rewardSum = 0;
            distanceSum = 0;
        }
    }

    @Override public synchronized void close() {
        running.set(false);
        if (thread != null) thread.interrupt();
        for (Agent a : agents) a.close();
        agents.clear();
        FruitFlyMod.LOGGER.info("Learning sandbox stopped");
    }

    private final class Agent implements AutoCloseable {
        final BrainRunner brain;
        final SensoryEncoders encoders;
        final MotorDecoder decoder;
        final SensoryFrame frame = new SensoryFrame();
        final float[] lum;
        final float[] red;
        final float[] green;
        final float[] blue;
        final float[] uv;
        final SplittableRandom random;
        double x, z, heading;
        double hunger = 0.65;
        double lastReward;
        double lastDistance;
        double previousX, previousZ;

        Agent(int id) {
            LifConfig lc = FruitFlyMod.BRAIN.lifConfig();
            lc.threads = 1;                 // 100 headless brains share the host; do not create 100 worker pools
            lc.spikeLogCapacity = Math.min(lc.spikeLogCapacity, 1024);
            lc.seed ^= 0x9E3779B97F4A7C15L * (id + 1L);
            brain = new BrainRunner(connectome, lc, config.learningSandboxMsPerCycle, "sandbox-" + id);
            SensoryEncoders.Params ep = new SensoryEncoders.Params();
            ep.vision = config.vision;
            ep.colorVision = config.colorVision;
            ep.olfaction = config.olfaction;
            ep.ornRMax = config.maxOrnRateHz;
            ep.ornSpont = config.spontOrnRateHz;
            ep.joWindSpont = config.spontJoWindRateHz;
            ep.joAuditorySpont = config.spontJoAuditoryRateHz;
            ep.laminaTonicMvPerMs = config.laminaTonicMvPerMs;
            PopulationIndex pi = brain.populations;
            encoders = new SensoryEncoders(connectome, pi, geometry, ep);
            decoder = new MotorDecoder(pi);
            lum = new float[geometry.columnCount()];
            red = new float[geometry.columnCount()];
            green = new float[geometry.columnCount()];
            blue = new float[geometry.columnCount()];
            uv = new float[geometry.columnCount()];
            frame.luminance = lum;
            frame.red = red;
            frame.green = green;
            frame.blue = blue;
            frame.uv = uv;
            random = new SplittableRandom(0xA5A5A5A5L + id * 7919L);
            x = 8 + random.nextDouble(ARENA - 16);
            z = 8 + random.nextDouble(ARENA - 16);
            heading = random.nextDouble(Math.PI * 2);
            previousX = x;
            previousZ = z;
            brain.setPostStepHook(net -> {
                decoder.update(net, brain.tickMs());
                net.applyNeuromodulatedPlasticity(lastReward, config.learningRate,
                        config.learningHomeostaticRate, config.learningMaxDeviation);
            });
        }

        void step(double ms) {
            buildSense(ms);
            brain.submit(net -> encoders.apply(frame, net, ms));
            brain.tickNow();
            MotorDecoder.MotorCommand cmd = decoder.latest();
            previousX = x;
            previousZ = z;
            move(cmd, ms / 1000.0);
            lastDistance = Math.hypot(x - previousX, z - previousZ);
            lastReward = computeReward();
        }

        private void buildSense(double ms) {
            frame.clear();
            float wallLight = (float) Math.max(0.05, Math.min(1.0, nearestWallDistance() / 12.0));
            for (int i = 0; i < lum.length; i++) {
                lum[i] = 0.5f + 0.35f * wallLight;
                red[i] = green[i] = blue[i] = lum[i];
                uv[i] = lum[i] * 0.8f;
            }
            Food target = nearestFood();
            if (target != null) {
                double dx = target.x - x, dz = target.z - z;
                double d = Math.hypot(dx, dz);
                double bearing = wrapDeg(Math.toDegrees(Math.atan2(dz, dx) - heading));
                double drive = (float) Math.max(0, Math.min(1, 1.0 - d / 28.0));
                frame.addOdor("DM1", (float) drive);
                frame.addOdor("VA2", (float) (drive * 0.8));
                frame.odorBearingDeg = (float) bearing;
                SensoryFrame.VisualObject o = new SensoryFrame.VisualObject((float) bearing, 0,
                        (float) Math.max(2, 12.0 / Math.max(1, d)), 0, 0, false);
                frame.objects.add(o);
                if (d < 2.0) {
                    frame.addTaste("LB3b", 1.0f);
                    frame.addTaste("LgLG3", 0.8f);
                }
            }
            double edge = nearestWallDistance();
            frame.touchNotum = edge < 0.5 ? 1f : 0f;
            frame.touchLegs = edge < 1.0 ? 0.6f : 0f;
            frame.windLeft = frame.windRight = (float) Math.min(1, lastDistance * 2.0);
            frame.hunger = (float) hunger;
            frame.thirst = 0.15f;
            frame.proteinNeed = (float) Math.max(0, hunger - 0.5);
            frame.sleepPressure = 0;
            frame.reproductiveDrive = 0;
            frame.groomingNeed = 0.05f;
            frame.stress = (float) Math.max(0, 1.0 - edge / 3.0);
            frame.health = 1f;
            frame.legsOnGround = true;
            frame.airborne = false;
            frame.wingbeat = 0;
            frame.yawRateDegPerS = 0;
        }

        private void move(MotorDecoder.MotorCommand cmd, double dt) {
            double yaw = Math.max(-1, Math.min(1, cmd.yaw));
            heading += yaw * Math.toRadians(config.turnRateDegPerS) * dt;
            double drive = Math.max(-1, Math.min(1, cmd.forward - cmd.backward));
            double speed = drive * config.walkSpeedBlocksPerS;
            x += Math.cos(heading) * speed * dt;
            z += Math.sin(heading) * speed * dt;
            if (x < 0) { x = 0; heading = Math.PI - heading; }
            if (x > ARENA) { x = ARENA; heading = Math.PI - heading; }
            if (z < 0) { z = 0; heading = -heading; }
            if (z > ARENA) { z = ARENA; heading = -heading; }
        }

        private double computeReward() {
            double reward = -0.0002 * lastDistance;
            hunger = Math.max(0, hunger - 0.0004);
            Food target = nearestFood();
            if (target != null && Math.hypot(x - target.x, z - target.z) < 2.0) {
                reward += FOOD_REWARD * (0.5 + 0.5 * hunger);
                hunger = 1.0;
                target.respawn(random);
            }
            if (hunger < 0.1) reward -= 0.002;
            if (nearestWallDistance() < 0.2) reward -= 0.01;
            return Math.max(-1, Math.min(1, reward));
        }

        private Food nearestFood() {
            Food best = null; double bd = Double.MAX_VALUE;
            for (Food f : food) { double d = (x - f.x) * (x - f.x) + (z - f.z) * (z - f.z); if (d < bd) { bd = d; best = f; } }
            return best;
        }

        private double nearestWallDistance() { return Math.min(Math.min(x, ARENA - x), Math.min(z, ARENA - z)); }
        @Override public void close() { brain.close(); }
    }

    private static final class Food {
        double x, z;
        Food(double x, double z) { this.x = x; this.z = z; }
        void respawn(SplittableRandom r) { x = 4 + r.nextDouble(ARENA - 8); z = 4 + r.nextDouble(ARENA - 8); }
    }

    private static double wrapDeg(double d) {
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }
}
