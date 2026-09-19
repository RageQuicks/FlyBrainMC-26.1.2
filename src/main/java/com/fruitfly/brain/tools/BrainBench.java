package com.fruitfly.brain.tools;

import com.fruitfly.brain.Connectome;
import com.fruitfly.brain.LifConfig;
import com.fruitfly.brain.LifNetwork;
import com.fruitfly.brain.PopulationIndex;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Headless harness: load a FLYB connectome, drive some populations, print activity of others.
 *
 * <pre>
 * gradlew brainBench -Pflyb=src/main/resources/connectome/malecns-v1.0.flyb.gz -Pms=1000 \
 *     -Pstim="prefix:ORN_DM1:150,prefix:ORN_VA2:150" -Preport="DNp09,DNa02,MN9,superclass:descending_neuron"
 * </pre>
 * Stimulus entries are {@code populationSpec:hz}; population specs follow {@link PopulationIndex}.
 */
public final class BrainBench {
    public static void main(String[] args) throws Exception {
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) a.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        String flyb = a.getOrDefault("flyb", "src/main/resources/connectome/malecns-v1.0.flyb.gz");
        double ms = Double.parseDouble(a.getOrDefault("ms", "1000"));
        double tickMs = 50.0;
        LifConfig cfg = new LifConfig();
        if (a.containsKey("dt")) cfg.dtMs = Double.parseDouble(a.get("dt"));
        if (a.containsKey("gain")) cfg.gain = Double.parseDouble(a.get("gain"));
        if (a.containsKey("seed")) cfg.seed = Long.parseLong(a.get("seed"));
        if (a.containsKey("cfg")) applyOverrides(cfg, a.get("cfg"));
        String stim = a.getOrDefault("stim", "");
        String report = a.getOrDefault("report", "superclass:descending_neuron,superclass:vnc_motor,superclass:cb_motor,DNp09,DNa02,MDN,DNp01");

        long t0 = System.nanoTime();
        Connectome c;
        try (InputStream in = new FileInputStream(flyb)) {
            c = Connectome.load(in);
        }
        System.out.printf(Locale.ROOT, "loaded %s in %.1fs%n", c, (System.nanoTime() - t0) / 1e9);
        System.out.println(c.metaJson);
        System.out.println(cfg);

        PopulationIndex pi = new PopulationIndex(c);
        LifNetwork net = new LifNetwork(c, cfg);
        List<String> stimSpecs = new ArrayList<>();
        if (!stim.isEmpty()) {
            for (String entry : stim.split(";")) {
                int k = entry.lastIndexOf(':');
                String spec = entry.substring(0, k).trim();
                double hz = Double.parseDouble(entry.substring(k + 1).trim());
                int[] pop = pi.resolve(spec);
                net.setStimulusRate(pop, hz);
                stimSpecs.add(spec + " (" + pop.length + " neurons @ " + hz + " Hz)");
            }
        }
        System.out.println("stimuli: " + stimSpecs);
        if (a.containsKey("postGain")) {
            for (String entry : a.get("postGain").split(";")) {
                if (entry.isBlank()) continue;
                int k = entry.lastIndexOf(':');
                String spec = entry.substring(0, k).trim();
                double gain = Double.parseDouble(entry.substring(k + 1).trim());
                int[] pop = pi.resolve(spec);
                net.setPostsynapticGain(pop, gain);
                System.out.println("postsynaptic gain " + spec + " (" + pop.length + " neurons) x " + gain);
            }
        }
        String[] reports = report.split(";");
        int[][] popIdx = new int[reports.length][];
        for (int i = 0; i < reports.length; i++) {
            popIdx[i] = pi.resolve(reports[i].trim());
            System.out.printf(Locale.ROOT, "report %-40s %6d neurons%n", reports[i].trim(), popIdx[i].length);
        }

        int ticks = (int) Math.round(ms / tickMs);
        long simNanos = 0;
        long spikesBefore = 0;
        System.out.printf(Locale.ROOT, "%8s %8s %8s %8s", "t(ms)", "active", "spikes", "wall(ms)");
        for (String r : reports) System.out.printf(Locale.ROOT, " %14s", abbreviate(r.trim(), 14));
        System.out.println();
        for (int t = 0; t < ticks; t++) {
            long s0 = System.nanoTime();
            net.runMs(tickMs);
            long s1 = System.nanoTime();
            simNanos += s1 - s0;
            System.out.printf(Locale.ROOT, "%8.0f %8d %8d %8.1f", net.simTimeMs(), net.activeNeurons(),
                    net.totalSpikes() - spikesBefore, (s1 - s0) / 1e6);
            spikesBefore = net.totalSpikes();
            for (int[] pop : popIdx) System.out.printf(Locale.ROOT, " %11.1fHz", net.populationRateHz(pop, tickMs));
            System.out.println();
            net.endTick(tickMs);
        }
        System.out.printf(Locale.ROOT, "simulated %.0f ms in %.2f s wall (%.2fx real time), %d spikes total%n",
                ms, simNanos / 1e9, ms / (simNanos / 1e6), net.totalSpikes());
    }

    /** Apply "field=value,field=value" overrides to any public numeric field of LifConfig via reflection. */
    public static void applyOverrides(LifConfig cfg, String spec) throws Exception {
        for (String kv : spec.split(",")) {
            if (kv.isBlank()) continue;
            String[] p = kv.split("=");
            java.lang.reflect.Field f = LifConfig.class.getField(p[0].trim());
            Class<?> t = f.getType();
            String val = p[1].trim();
            if (t == double.class) f.setDouble(cfg, Double.parseDouble(val));
            else if (t == int.class) f.setInt(cfg, Integer.parseInt(val));
            else if (t == long.class) f.setLong(cfg, Long.parseLong(val));
            else if (t == boolean.class) f.setBoolean(cfg, Boolean.parseBoolean(val));
            else throw new IllegalArgumentException("unsupported field type for " + p[0]);
        }
    }

    private static String abbreviate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "~";
    }
}
