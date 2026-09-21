package com.fruitfly;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** User-facing configuration, stored as JSON in the Fabric config directory (config/fruitfly.json). */
public final class FruitFlyConfig {
    // --- brain ---
    /** Integration step (ms); 0.5 is real time on one core, 0.1 matches Brian2. */
    public double brainDtMs = 0.5;
    /** Milliseconds of neural time simulated per game tick (50 = real time; 25 = half-speed "bullet time"). */
    public double brainMsPerTick = 50;
    /** Global synaptic gain (see LifConfig.gain). */
    public double synapticGain = 0.65;
    /** Worker threads per brain (0 = auto). */
    public int brainThreads = 0;
    /** Maximum simultaneously simulated flies. Flies without a brain are inert until a brain becomes available. */
    public int maxBrains = 100;

    // --- headless learning sandbox ---
    /** Run the connectome learning laboratory alongside Minecraft. */
    public boolean learningSandboxEnabled = false;
    /** Initial parallel fly simulations in the sandbox. */
    public int learningSandboxFlies = 100;
    /** Simulated milliseconds advanced by each sandbox cycle. */
    public double learningSandboxMsPerCycle = 5.0;
    /** Seconds between sandbox telemetry log lines. */
    public double learningSandboxLogSeconds = 10.0;
    /** Three-factor plasticity rate; deliberately tiny to keep learning slow and stable. */
    public double learningRate = 0.002;
    /** Pull learned synaptic efficacy back toward the connectome baseline each cycle. */
    public double learningHomeostaticRate = 0.0005;
    /** Maximum learned deviation from the connectome-derived presynaptic efficacy. */
    public double learningMaxDeviation = 0.50;
    /** Postsynaptic input gain for Kenyon cells (sparse coding correction; 1 = literal). */
    public double kenyonCellInputGain = 0.25;
    /** Postsynaptic input gain for antennal-lobe projection neurons (1 = literal). */
    public double projectionNeuronInputGain = 1.0;
    /** Optional path to an alternative FLYB file (empty = bundled male-cns v1.0). */
    public String connectomeFile = "";

    // --- senses ---
    public boolean vision = true;
    public boolean colorVision = true;
    public boolean objectVision = true;
    public boolean olfaction = true;
    public int raysPerEye = 300;
    public double visionRayLength = 24;
    public double odorRadius = 16;
    public double odorFalloffBlocks = 6;
    public double maxOrnRateHz = 120;
    public double spontOrnRateHz = 8;
    public double spontJoWindRateHz = 8;
    public double spontJoAuditoryRateHz = 2;
    public double laminaTonicMvPerMs = 0.5;

    // --- body ---
    /** Visual/hitbox scale (1.0 = 0.5-block body, ~170x a real fly). */
    public double flyScale = 1.0;
    public double walkSpeedBlocksPerS = 3.0;
    public double flightSpeedBlocksPerS = 8.0;
    public double turnRateDegPerS = 300;
    public double flightTurnRateDegPerS = 600;
    /** Deprecated compatibility setting. Behavior is brain-only; this flag no longer drives movement. */
    public boolean reflexLayer = false;
    public boolean flightEnabled = true;
    public boolean spawnEggInCreativeTab = true;

    // --- debug ecology ---
    /** Number of flies spawned around the first player when the server/world begins. */
    public int debugStartFlyCount = 0;
    /** Maximum distance from the nearest player; beyond this the debug leash returns the fly inward. */
    public double debugLeashRadius = 0;

    // --- telemetry / debug ---
    /**
     * Telemetry cadence in game ticks. Every payload carries {@link #spikeSampleSize} neuron indices (~3 KB at 1024)
     * plus ~0.8 KB of names/rates/retina to every tracking player within {@link #telemetryRangeBlocks}: 2 (10 Hz) is
     * ~0.3 Mbit/s per fly per player, 1 (20 Hz, the smoothest brain-view animation) doubles that. The brain view's
     * heat map fades over ~300 ms, so 10 Hz still animates.
     */
    public int telemetryEveryTicks = 2;
    public double telemetryRangeBlocks = 64;
    /** Spiking neurons sampled per payload for the brain view / brain cloud (~3 KB of VarInts at 1024). */
    public int spikeSampleSize = 1024;
    /** Populations shown on the neuroscope HUD (PopulationIndex specs). */
    public String[] hudPopulations = {
            "DNp09", "DNg100", "DNa02/L", "DNa02/R", "MDN", "DNg60", "DNp01", "DNp07", "DNp10", "DNg62", "DNge078",
            "MN9", "GNG232", "pIP10", "prefix:pC1_", "LC4", "LPLC2", "LC10a", "class:Kenyon_Cell", "class:ALPN",
            "superclass:descending_neuron", "superclass:vnc_motor"
    };

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static FruitFlyConfig load(Path file) {
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                FruitFlyConfig c = GSON.fromJson(r, FruitFlyConfig.class);
                if (c != null) {
                    c.save(file); // write back any new defaults
                    return c;
                }
            } catch (Exception e) {
                FruitFlyMod.LOGGER.warn("Could not read {}: {} (using defaults)", file, e.toString());
            }
        }
        FruitFlyConfig c = new FruitFlyConfig();
        c.save(file);
        return c;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            FruitFlyMod.LOGGER.warn("Could not write {}: {}", file, e.toString());
        }
    }
}
