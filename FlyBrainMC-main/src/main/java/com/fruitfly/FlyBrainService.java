package com.fruitfly;

import com.fruitfly.brain.BrainRunner;
import com.fruitfly.brain.Connectome;
import com.fruitfly.brain.LifConfig;
import com.fruitfly.brain.PopulationIndex;
import com.fruitfly.brain.RetinaGeometry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the connectome once per JVM (shared, immutable) and hands out one {@link BrainRunner} per fly, bounded by
 * {@link FruitFlyConfig#maxBrains}. Loading runs asynchronously so world start is not blocked.
 */
public final class FlyBrainService {
    public static final String BUNDLED_RESOURCE = "/connectome/malecns-v1.0.flyb.gz";

    private final FruitFlyConfig config;
    private volatile Connectome connectome;
    private volatile PopulationIndex populations;
    private volatile RetinaGeometry geometry;
    private volatile CompletableFuture<Void> loading;
    private volatile String loadError;
    private volatile String[] validHudPopulations;
    private final Set<BrainRunner> runners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public FlyBrainService(FruitFlyConfig config) { this.config = config; }

    /** Start loading in the background (idempotent). */
    public synchronized CompletableFuture<Void> preload() {
        if (loading == null) {
            loading = CompletableFuture.runAsync(() -> {
                long t0 = System.nanoTime();
                try {
                    Connectome c;
                    Path override = config.connectomeFile == null || config.connectomeFile.isBlank() ? null : Path.of(config.connectomeFile);
                    if (override != null && Files.exists(override)) {
                        try (InputStream in = Files.newInputStream(override)) { c = Connectome.load(in); }
                        FruitFlyMod.LOGGER.info("Loaded connectome from {}", override);
                    } else {
                        InputStream in = FlyBrainService.class.getResourceAsStream(BUNDLED_RESOURCE);
                        if (in == null) throw new IOException("bundled connectome " + BUNDLED_RESOURCE + " missing from jar");
                        try (in) { c = Connectome.load(in); }
                    }
                    PopulationIndex pi = new PopulationIndex(c);
                    RetinaGeometry.Params rp = new RetinaGeometry.Params();
                    rp.raysPerEye = config.raysPerEye;
                    RetinaGeometry g = new RetinaGeometry(c, rp);
                    // warm the population caches used every tick
                    pi.resolve("class:olfactory");
                    pi.resolve("class:gustatory");
                    pi.resolve("superclass:descending_neuron");
                    this.populations = pi;
                    this.geometry = g;
                    this.connectome = c;
                    FruitFlyMod.LOGGER.info("Connectome ready: {} ({}) in {} ms", c, g, (System.nanoTime() - t0) / 1_000_000);
                } catch (Throwable t) {
                    loadError = t.toString();
                    FruitFlyMod.LOGGER.error("Failed to load connectome", t);
                }
            });
        }
        return loading;
    }

    public boolean ready() { return connectome != null; }
    public String loadError() { return loadError; }
    public Connectome connectome() { return connectome; }
    public PopulationIndex populations() { return populations; }
    public RetinaGeometry geometry() { return geometry; }
    public int activeBrains() { return runners.size(); }
    public int maxBrains() { return config.maxBrains; }

    public LifConfig lifConfig() {
        LifConfig lc = new LifConfig();
        lc.dtMs = config.brainDtMs;
        lc.gain = config.synapticGain;
        lc.threads = config.brainThreads;
        return lc;
    }

    /** Create and start a brain for a fly, or return null when not ready / at capacity. */
    public BrainRunner acquire(String name) {
        if (!ready()) {
            preload();
            return null;
        }
        if (runners.size() >= config.maxBrains) return null;
        BrainRunner r = new BrainRunner(connectome, lifConfig(), config.brainMsPerTick, name);
        if (config.kenyonCellInputGain != 1.0) r.net.setPostsynapticGain(populations.resolve("prefix:KC"), config.kenyonCellInputGain);
        if (config.projectionNeuronInputGain != 1.0) r.net.setPostsynapticGain(populations.resolve("class:ALPN"), config.projectionNeuronInputGain);
        r.setActionErrorHandler(t -> FruitFlyMod.LOGGER.warn("Brain {}: queued action failed: {}", name, t.toString()));
        for (String spec : hudPopulations()) r.watch(spec);
        runners.add(r);
        r.start();
        return r;
    }

    /**
     * {@link FruitFlyConfig#hudPopulations} with invalid specs dropped (validated once on the shared index, each bad
     * entry logged once). A typo in the config must not throw on the brain thread of every new fly.
     */
    private String[] hudPopulations() {
        String[] valid = validHudPopulations;
        if (valid == null) {
            java.util.List<String> ok = new java.util.ArrayList<>();
            for (String spec : config.hudPopulations) {
                try {
                    populations.resolve(spec);
                    ok.add(spec);
                } catch (IllegalArgumentException e) { // NumberFormatException is a subclass
                    FruitFlyMod.LOGGER.warn("Ignoring invalid hudPopulations entry '{}': {}", spec, e.getMessage());
                }
            }
            valid = ok.toArray(new String[0]);
            validHudPopulations = valid;
        }
        return valid;
    }

    public void release(BrainRunner r) {
        if (r != null && runners.remove(r)) r.close();
    }

    public void shutdown() {
        for (BrainRunner r : runners) r.close();
        runners.clear();
    }
}
