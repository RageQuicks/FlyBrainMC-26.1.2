package com.fruitfly.brain.tools;

import com.fruitfly.brain.Connectome;
import com.fruitfly.brain.LifConfig;
import com.fruitfly.brain.LifNetwork;
import com.fruitfly.brain.MotorDecoder;
import com.fruitfly.brain.PopulationIndex;
import com.fruitfly.brain.RetinaGeometry;
import com.fruitfly.brain.SensoryEncoders;
import com.fruitfly.brain.SensoryFrame;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Headless vision harness. Renders synthetic scenes onto the retina columns, runs them through
 * {@link SensoryEncoders} and reports lamina / medulla / lobula / descending activity and the decoded motor command.
 *
 * Scenes: {@code dark} (all columns 0), {@code flash} (dark 200 ms, bright 300 ms, dark), {@code loom} (expanding
 * dark disc in the right visual field, plus the analytic object channel), {@code bar} (dark vertical bar sweeping
 * front-to-back across the right eye), {@code fly} (small fly-like object moving in the frontal field).
 */
public final class VisionBench {
    public static void main(String[] args) throws Exception {
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) a.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        String flyb = a.getOrDefault("flyb", "src/main/resources/connectome/malecns-v1.0.flyb.gz");
        String scene = a.getOrDefault("scene", "flash");
        double ms = Double.parseDouble(a.getOrDefault("ms", "800"));
        double tickMs = 50;
        LifConfig cfg = new LifConfig();
        cfg.dtMs = 0.5;
        if (a.containsKey("gain")) cfg.gain = Double.parseDouble(a.get("gain"));
        if (a.containsKey("cfg")) BrainBench.applyOverrides(cfg, a.get("cfg"));
        String report = a.getOrDefault("report",
                "L1/R;L2/R;L3/R;Mi1/R;Tm3/R;Tm1/R;Tm2/R;prefix:T4/R;prefix:T5/R;LC4/R;LPLC2/R;LC11/R;HSE/R,HSN/R,HSS/R;DNp01;DNp04;DNa02/L;DNa02/R;DNp09;class:Kenyon_Cell");

        Connectome c;
        try (InputStream in = new FileInputStream(flyb)) { c = Connectome.load(in); }
        PopulationIndex pi = new PopulationIndex(c);
        RetinaGeometry geom = new RetinaGeometry(c);
        System.out.println(c);
        System.out.println(geom);
        System.out.println(cfg);
        int withPr = 0, withL1 = 0;
        for (RetinaGeometry.Column col : geom.allColumns()) {
            if (col.photoreceptors.length > 0) withPr++;
            if (col.l1 >= 0) withL1++;
        }
        System.out.printf(Locale.ROOT, "columns: %d (with photoreceptors %d, with L1 %d)%n", geom.columnCount(), withPr, withL1);
        for (int side = 0; side < 2; side++) {
            float azMin = 999, azMax = -999, elMin = 999, elMax = -999;
            for (RetinaGeometry.Column col : geom.columns(side)) {
                azMin = Math.min(azMin, col.azimuthDeg); azMax = Math.max(azMax, col.azimuthDeg);
                elMin = Math.min(elMin, col.elevationDeg); elMax = Math.max(elMax, col.elevationDeg);
            }
            System.out.printf(Locale.ROOT, "  %s eye: %d columns, azimuth %.0f..%.0f, elevation %.0f..%.0f, %d rays%n",
                    side == 0 ? "left " : "right", geom.columnCount(side), azMin, azMax, elMin, elMax, geom.rays(side).size());
        }

        LifNetwork net = new LifNetwork(c, cfg);
        SensoryEncoders.Params ep = new SensoryEncoders.Params();
        if (a.containsKey("tonic")) ep.laminaTonicMvPerMs = Double.parseDouble(a.get("tonic"));
        if (a.containsKey("objectChannels")) ep.objectChannels = Boolean.parseBoolean(a.get("objectChannels"));
        ep.olfaction = false; ep.gustation = false; ep.mechanosensation = false; ep.thermoHygro = false;
        System.out.println("analytic object channels: " + ep.objectChannels);
        SensoryEncoders enc = new SensoryEncoders(c, pi, geom, ep);
        System.out.println("encoder populations: " + enc.populationSizes());
        if (a.containsKey("postGain")) {
            for (String entry : a.get("postGain").split(";")) {
                if (entry.isBlank()) continue;
                int k = entry.lastIndexOf(':');
                net.setPostsynapticGain(pi.resolve(entry.substring(0, k).trim()), Double.parseDouble(entry.substring(k + 1).trim()));
            }
        }
        MotorDecoder dec = new MotorDecoder(pi);
        String[] reports = report.split(";");
        int[][] pops = new int[reports.length][];
        for (int i = 0; i < reports.length; i++) pops[i] = pi.resolve(reports[i].trim());

        SensoryFrame frame = new SensoryFrame();
        frame.luminance = new float[geom.columnCount()];
        int ticks = (int) Math.round(ms / tickMs);
        System.out.printf(Locale.ROOT, "%6s %7s %7s %6s", "t", "active", "spikes", "wall");
        for (String r : reports) System.out.printf(Locale.ROOT, " %9s", r.trim().length() > 9 ? r.trim().substring(0, 9) : r.trim());
        System.out.println("  | motor");
        for (int t = 0; t < ticks; t++) {
            double time = t * tickMs;
            renderScene(scene, time, ms, geom, frame);
            long w0 = System.nanoTime();
            enc.apply(frame, net, tickMs);
            net.runMs(tickMs);
            MotorDecoder.MotorCommand cmd = dec.update(net, tickMs);
            long w1 = System.nanoTime();
            System.out.printf(Locale.ROOT, "%6.0f %7d %7d %6.1f", time + tickMs, net.activeNeurons(), spikesThisTick(net, pops, c), (w1 - w0) / 1e6);
            for (int[] pop : pops) System.out.printf(Locale.ROOT, " %7.1fHz", net.populationRateHz(pop, tickMs));
            System.out.println("  | " + cmd);
            net.endTick(tickMs);
        }
    }

    private static long lastTotal = 0;
    private static long spikesThisTick(LifNetwork net, int[][] pops, Connectome c) {
        long now = net.totalSpikes();
        long d = now - lastTotal;
        lastTotal = now;
        return d;
    }

    /** Paint the scene luminance (0..1) per column and fill the object channel. */
    static void renderScene(String scene, double timeMs, double totalMs, RetinaGeometry geom, SensoryFrame f) {
        f.objects.clear();
        f.yawRateDegPerS = 0;
        switch (scene) {
            case "dark" -> Arrays.fill(f.luminance, 0f);
            case "bright" -> Arrays.fill(f.luminance, 1f);
            case "flash" -> Arrays.fill(f.luminance, (timeMs >= 200 && timeMs < 500) ? 1f : 0f);
            case "loom" -> {
                Arrays.fill(f.luminance, 0.8f);
                // dark disc centred at az 60, el 0 grows from 5 to 90 deg diameter over 200..700 ms
                double t = Math.max(0, Math.min(1, (timeMs - 200) / 500));
                if (timeMs >= 200 && timeMs < 750) {
                    double size = 5 + 85 * t * t;
                    for (int ci : geom.columnsWithin(60, 0, size / 2)) f.luminance[ci] = 0.05f;
                    double expansion = 2 * 85 * t / 0.5; // d(size)/dt in deg/s
                    f.objects.add(new SensoryFrame.VisualObject(60, 0, (float) size, (float) expansion, 0, false));
                }
            }
            case "bar" -> {
                Arrays.fill(f.luminance, 0.8f);
                double az = 10 + 140 * (timeMs / totalMs); // sweep front to back on the right
                for (RetinaGeometry.Column col : geom.columns(RetinaGeometry.RIGHT)) {
                    if (Math.abs(col.azimuthDeg - az) < 8) f.luminance[col.index] = 0.05f;
                }
                f.objects.add(new SensoryFrame.VisualObject((float) az, 0, 16, 0, (float) (140 / (totalMs / 1000.0)), false));
            }
            case "fly" -> {
                Arrays.fill(f.luminance, 0.8f);
                double az = -30 + 60 * (0.5 + 0.5 * Math.sin(timeMs / 300.0));
                for (int ci : geom.columnsWithin(az, -5, 4)) f.luminance[ci] = 0.2f;
                f.objects.add(new SensoryFrame.VisualObject((float) az, -5, 8, 0, 60, true));
            }
            case "spin" -> {
                Arrays.fill(f.luminance, 0.6f);
                f.yawRateDegPerS = 300;
            }
            default -> throw new IllegalArgumentException("unknown scene " + scene);
        }
    }
}
