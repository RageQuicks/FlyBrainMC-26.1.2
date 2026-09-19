package com.fruitfly.brain.tools;

import com.fruitfly.brain.Connectome;
import com.fruitfly.brain.LifConfig;
import com.fruitfly.brain.LifNetwork;
import com.fruitfly.brain.MotorDecoder;
import com.fruitfly.brain.MotorMap;
import com.fruitfly.brain.PopulationIndex;
import com.fruitfly.brain.RetinaGeometry;
import com.fruitfly.brain.SensoryEncoders;
import com.fruitfly.brain.SensoryFrame;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Headless embodiment harness: scripted {@link SensoryFrame} scenarios are pushed through the whole brain loop
 * (encoders -> LIF connectome -> motor decoder) at 50 ms ticks, exactly in the order FlyEntity/BrainRunner use them
 * (apply, runMs, decode, endTick), and the resulting behaviour is compared with the expectation from the literature.
 *
 * <pre>
 * gradlew embodiedBench                      # all scenarios
 * gradlew embodiedBench -Pscenario=apple     # one scenario
 *   [-Pms=4000] [-Pgain=0.65] [-Pcfg=dtMs=0.5,threads=8] [-Preport="DNp09;MN9"] [-Pflyb=path]
 * </pre>
 *
 * Scenarios: {@code apple} (odor ramp, tarsal then labellar sugar contact -> FEED / MN9), {@code loom} (dark disc
 * expanding at azimuth 60 -> ESCAPE via DNp01), {@code rain} (debris on the head -> GROOM via aDN1/aDN2),
 * {@code wind} (left then right antennal deflection -> ipsilateral JO wind/gravity drive), {@code song}
 * (conspecific song -> JO-A/B auditory drive). Brain parameters mirror the in-game defaults: dt 0.5 ms, gain 0.65,
 * Kenyon-cell postsynaptic gain 0.25, ORN rMax 120 Hz. Exit status is always 0; the summary table reports whether
 * each expected behaviour was reached.
 *
 * <p>Ambient luminance: the retina is always on (as in game). By default every column sees dim daylight
 * ({@code --lum 0.6}); append {@code /dark} (or {@code /lum=0.2}) to a scenario name, e.g. {@code -Pscenario=all/dark}
 * or {@code -Pscenario=apple/dark,apple}, to run it in darkness and isolate the modality under test. The loom scene
 * paints its own bright background.</p>
 */
public final class EmbodiedBench {
    static final double TICK_MS = 50;

    /** Fills the frame for brain time {@code tMs} (frame already cleared, luminance array sized to the retina). */
    interface Script {
        void fill(SensoryFrame f, double tMs, RetinaGeometry geom);
    }

    /** Decides whether the expected behaviour was observed and describes what was seen. */
    interface Judge {
        String[] judge(Run run); // {achieved "yes"/"no"/"partial", observed description}
    }

    static final class Scenario {
        final String name, expected;
        final double durationMs;
        final String[] populations;
        final Script script;
        final Judge judge;

        Scenario(String name, String expected, double durationMs, String[] populations, Script script, Judge judge) {
            this.name = name;
            this.expected = expected;
            this.durationMs = durationMs;
            this.populations = populations;
            this.script = script;
            this.judge = judge;
        }
    }

    /** A scenario plus the ambient luminance it runs under. */
    record Selection(Scenario scenario, double ambient, String label) {}

    /** Everything recorded while running one scenario. */
    static final class Run {
        final Scenario scenario;
        String label;
        final String[] popSpecs;
        final List<double[]> popRates = new ArrayList<>();      // per tick
        final List<MotorDecoder.MotorCommand> commands = new ArrayList<>();
        final List<Double> times = new ArrayList<>();
        final Map<String, Double> maxChannel = new LinkedHashMap<>();
        final Map<String, Double> maxRate = new LinkedHashMap<>();
        final EnumMap<MotorDecoder.Mode, Integer> modeTicks = new EnumMap<>(MotorDecoder.Mode.class);
        long totalSpikes;
        int maxActive;
        double wallMs;
        boolean jumped;

        Run(Scenario s, String label, String[] popSpecs) {
            this.scenario = s;
            this.label = label;
            this.popSpecs = popSpecs;
        }

        double maxRate(String spec) { return maxRate.getOrDefault(spec, 0.0); }
        double maxChannel(String name) { return maxChannel.getOrDefault(name, 0.0); }
        int ticksIn(MotorDecoder.Mode m) { return modeTicks.getOrDefault(m, 0); }

        /** Mean rate of a reported population over ticks whose time lies in [fromMs, toMs). */
        double meanRate(String spec, double fromMs, double toMs) {
            int k = Arrays.asList(popSpecs).indexOf(spec);
            if (k < 0) return 0;
            double s = 0;
            int n = 0;
            for (int t = 0; t < times.size(); t++) {
                double time = times.get(t);
                if (time >= fromMs && time < toMs) { s += popRates.get(t)[k]; n++; }
            }
            return n == 0 ? 0 : s / n;
        }

        String topChannel() {
            String best = "-";
            double bv = -1;
            for (Map.Entry<String, Double> e : maxChannel.entrySet()) {
                if (e.getKey().equals(MotorMap.JUMP)) continue;
                if (Math.abs(e.getValue()) > bv) { bv = Math.abs(e.getValue()); best = e.getKey() + "=" + fmt(e.getValue()); }
            }
            return best + (jumped ? " +JUMP" : "");
        }

        String modesSeen() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<MotorDecoder.Mode, Integer> e : modeTicks.entrySet()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(e.getKey()).append('x').append(e.getValue());
            }
            return sb.toString();
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) a.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        String flyb = a.getOrDefault("flyb", "src/main/resources/connectome/malecns-v1.0.flyb.gz");
        String which = a.getOrDefault("scenario", "all");
        double msOverride = a.containsKey("ms") ? Double.parseDouble(a.get("ms")) : -1;
        LifConfig cfg = new LifConfig();
        cfg.dtMs = 0.5;
        if (a.containsKey("gain")) cfg.gain = Double.parseDouble(a.get("gain"));
        if (a.containsKey("cfg")) BrainBench.applyOverrides(cfg, a.get("cfg"));
        double kcGain = Double.parseDouble(a.getOrDefault("kcGain", "0.25"));
        double ornRMax = Double.parseDouble(a.getOrDefault("ornRMax", "120"));
        double ambientDefault = Double.parseDouble(a.getOrDefault("lum", "0.6"));
        String[] extraReport = a.containsKey("report") ? a.get("report").split(";") : new String[0];

        long t0 = System.nanoTime();
        Connectome c;
        try (InputStream in = new FileInputStream(flyb)) { c = Connectome.load(in); }
        System.out.printf(Locale.ROOT, "loaded %s in %.1fs%n", c, (System.nanoTime() - t0) / 1e9);
        System.out.println(cfg);
        PopulationIndex pi = new PopulationIndex(c);
        RetinaGeometry geom = new RetinaGeometry(c);
        System.out.println(geom);
        System.out.printf(Locale.ROOT, "Kenyon-cell input gain %.2f, ORN rMax %.0f Hz, tick %.0f ms%n", kcGain, ornRMax, TICK_MS);

        List<Scenario> all = scenarios();
        List<Selection> selected = select(which, all, ambientDefault);
        if (selected.isEmpty()) {
            System.err.println("unknown scenario '" + which + "'; known: " + all.stream().map(s -> s.name).toList()
                    + " (optionally suffixed /dark or /lum=<0..1>), or all");
            System.exit(2);
        }

        List<Run> runs = new ArrayList<>();
        for (Selection sel : selected) {
            double duration = msOverride > 0 ? msOverride : sel.scenario().durationMs;
            runs.add(run(sel, duration, c, pi, geom, cfg, kcGain, ornRMax, extraReport));
        }

        System.out.println();
        System.out.println("SUMMARY");
        String fmtRow = "%-14s | %-58s | %-70s | %s%n";
        System.out.printf(Locale.ROOT, fmtRow, "scenario", "expected", "observed (max channel; modes; key rates)", "achieved");
        System.out.println("-".repeat(170));
        for (Run r : runs) {
            String[] verdict = r.scenario.judge.judge(r);
            System.out.printf(Locale.ROOT, fmtRow, r.label, r.scenario.expected, verdict[1], verdict[0]);
        }
        System.out.println("-".repeat(170));
        for (Run r : runs) {
            System.out.printf(Locale.ROOT, "%-14s %d ticks, %d spikes, max active %d, %.1f s wall (%.2fx real time)%n", r.label,
                    r.times.size(), r.totalSpikes, r.maxActive, r.wallMs / 1000, r.times.size() * TICK_MS / Math.max(1, r.wallMs));
        }
        System.exit(0);
    }

    /** Parse "apple,loom/dark,all/lum=0.2" into selections; unknown names are skipped with a warning. */
    static List<Selection> select(String which, List<Scenario> all, double ambientDefault) {
        List<Selection> out = new ArrayList<>();
        for (String entry : which.split(",")) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            String[] parts = e.split("/");
            double ambient = ambientDefault;
            for (int i = 1; i < parts.length; i++) {
                String mod = parts[i].trim();
                if (mod.equals("dark")) ambient = 0;
                else if (mod.equals("light")) ambient = 1;
                else if (mod.startsWith("lum=")) ambient = Double.parseDouble(mod.substring(4));
                else System.err.println("ignoring unknown scenario modifier '" + mod + "'");
            }
            String suffix = ambient == 0 ? "/dark" : ambient == ambientDefault ? "" : "/lum=" + fmt(ambient);
            String base = parts[0].trim();
            boolean matched = false;
            for (Scenario s : all) {
                if (base.equals("all") || base.equals(s.name)) {
                    out.add(new Selection(s, ambient, s.name + suffix));
                    matched = true;
                }
            }
            if (!matched) System.err.println("unknown scenario '" + base + "'");
        }
        return out;
    }

    // ------------------------------------------------------------------ running

    static Run run(Selection sel, double durationMs, Connectome c, PopulationIndex pi, RetinaGeometry geom, LifConfig cfg,
                   double kcGain, double ornRMax, String[] extraReport) {
        Scenario s = sel.scenario();
        float ambient = (float) sel.ambient();
        List<String> specs = new ArrayList<>(Arrays.asList(s.populations));
        for (String e : extraReport) if (!e.isBlank() && !specs.contains(e.trim())) specs.add(e.trim());
        String[] popSpecs = specs.toArray(new String[0]);
        int[][] pops = new int[popSpecs.length][];
        for (int i = 0; i < popSpecs.length; i++) pops[i] = pi.resolve(popSpecs[i]);

        // fresh brain state per scenario (same seed -> each scenario is reproducible on its own)
        LifNetwork net = new LifNetwork(c, cfg);
        if (kcGain != 1.0) net.setPostsynapticGain(pi.resolve("prefix:KC"), kcGain);
        SensoryEncoders.Params ep = new SensoryEncoders.Params();
        ep.ornRMax = ornRMax;
        SensoryEncoders enc = new SensoryEncoders(c, pi, geom, ep);
        MotorDecoder dec = new MotorDecoder(pi);

        System.out.println();
        System.out.println("=== scenario '" + sel.label() + "': " + s.expected + " (" + (int) durationMs + " ms, ambient luminance "
                + fmt(ambient) + (s.name.equals("loom") ? " overridden by the scene" : "") + ")");
        StringBuilder head = new StringBuilder(String.format(Locale.ROOT, "%6s %7s %7s %6s", "t", "active", "spikes", "wall"));
        for (int i = 0; i < popSpecs.length; i++) head.append(String.format(Locale.ROOT, " %10s", abbreviate(popSpecs[i], 10)));
        head.append("  | motor");
        System.out.println(head);

        Run run = new Run(s, sel.label(), popSpecs);
        SensoryFrame frame = new SensoryFrame();
        int ticks = (int) Math.round(durationMs / TICK_MS);
        long spikesBefore = 0;
        for (int t = 0; t < ticks; t++) {
            double time = t * TICK_MS;
            frame.clear();
            if (frame.luminance.length != geom.columnCount()) frame.luminance = new float[geom.columnCount()];
            Arrays.fill(frame.luminance, ambient); // scene scripts may override
            s.script.fill(frame, time, geom);

            long w0 = System.nanoTime();
            enc.apply(frame, net, TICK_MS);
            net.runMs(TICK_MS);
            MotorDecoder.MotorCommand cmd = dec.update(net, TICK_MS);
            long w1 = System.nanoTime();
            double wall = (w1 - w0) / 1e6;
            run.wallMs += wall;

            double[] rates = new double[pops.length];
            for (int i = 0; i < pops.length; i++) {
                rates[i] = net.populationRateHz(pops[i], TICK_MS);
                run.maxRate.merge(popSpecs[i], rates[i], Math::max);
            }
            long spikes = net.totalSpikes() - spikesBefore;
            spikesBefore = net.totalSpikes();
            run.totalSpikes += spikes;
            run.maxActive = Math.max(run.maxActive, net.activeNeurons());
            run.times.add(time + TICK_MS);
            run.popRates.add(rates);
            run.commands.add(cmd);
            run.modeTicks.merge(cmd.mode, 1, Integer::sum);
            if (cmd.jump) run.jumped = true;
            for (Map.Entry<String, Double> e : cmd.channels.entrySet()) {
                run.maxChannel.merge(e.getKey(), e.getValue(), (x, y) -> Math.abs(y) > Math.abs(x) ? y : x);
            }

            StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "%6.0f %7d %7d %6.1f", time + TICK_MS, net.activeNeurons(), spikes, wall));
            for (double r : rates) row.append(String.format(Locale.ROOT, " %8.1fHz", r));
            row.append("  | ").append(cmd);
            System.out.println(row);
            net.endTick(TICK_MS);
        }
        String[] verdict = s.judge.judge(run);
        System.out.println("--- " + sel.label() + ": achieved=" + verdict[0] + "; " + verdict[1]);
        return run;
    }

    // ------------------------------------------------------------------ scenarios

    static List<Scenario> scenarios() {
        List<Scenario> list = new ArrayList<>();

        list.add(new Scenario("quiet",
                "no stimulus (control): only the retina at the ambient luminance -> expect IDLE, no wing motor drive", 1500,
                new String[]{"L1/R", "L2/R", "L3/R", "Mi1/R", "prefix:T4/R", "subclass:wm", "prefix:DNg02", "DNp07,DNp10", "DNp01", "DNp09",
                        "superclass:descending_neuron", "superclass:vnc_motor"},
                (f, t, geom) -> { },
                run -> {
                    boolean idle = run.ticksIn(MotorDecoder.Mode.IDLE) == run.times.size();
                    String obs = run.topChannel() + "; modes " + run.modesSeen() + "; wingMotor max " + fmt(run.maxChannel(MotorMap.WING_MOTOR))
                            + "; wm MNs max " + fmt(run.maxRate("subclass:wm")) + " Hz, L1/R " + fmt(run.maxRate("L1/R")) + ", Mi1/R " + fmt(run.maxRate("Mi1/R"))
                            + ", DN mean max " + fmt(run.maxRate("superclass:descending_neuron")) + ", VNC MN mean max " + fmt(run.maxRate("superclass:vnc_motor"));
                    return new String[]{idle ? "yes" : "no", obs};
                }));

        list.add(new Scenario("apple",
                "odor approach then sugar contact -> FEED (MN9 30-90 Hz, G2N-1/Fudog/Rounddown active)", 5000,
                new String[]{"ORN_DM1", "class:ALPN", "class:Kenyon_Cell", "LgLG3", "LB3b,LB3c", "GNG232", "DNg67", "DNge080", "MN9",
                        "GNG087", "DNp09", "DNa02/L", "DNa02/R", "superclass:descending_neuron"},
                (f, t, geom) -> {
                    // apple odor vector (docs/research/sensory-mapping.md a.3) ramping 0 -> 1 over 2 s from 40 deg right
                    double k = Math.min(1, t / 2000);
                    f.addOdor("DM1", (float) (1.0 * k));
                    f.addOdor("DM2", (float) (0.9 * k));
                    f.addOdor("VA2", (float) (0.6 * k));
                    f.odorBearingDeg = t < 3000 ? 40 : 0;
                    if (t >= 3000) f.addTaste("LgLG3", 1f);              // tarsal sugar contact
                    if (t >= 3500) {                                     // labellar + pharyngeal sugar
                        f.addTaste("LB3b", 1f);
                        f.addTaste("LB3c", 1f);
                        f.addTaste("PhG1a", 0.9f);
                        f.addTaste("PhG1b", 0.9f);
                        f.addTaste("PhG1c", 0.9f);
                    }
                },
                run -> {
                    boolean feed = run.ticksIn(MotorDecoder.Mode.FEED) > 0;
                    double mn9 = run.maxRate("MN9");
                    String obs = run.topChannel() + "; feed=" + fmt(run.maxChannel(MotorMap.FEED)) + "; modes " + run.modesSeen()
                            + "; MN9 max " + fmt(mn9) + " Hz (mean after 3.5 s " + fmt(run.meanRate("MN9", 3500, 1e9)) + ")"
                            + "; G2N-1 " + fmt(run.maxRate("GNG232")) + ", Fudog " + fmt(run.maxRate("DNg67")) + ", Rounddown " + fmt(run.maxRate("DNge080"))
                            + "; fwd max " + fmt(run.maxChannel(MotorMap.FORWARD)) + " yaw peak " + fmt(run.maxChannel(MotorMap.YAW));
                    return new String[]{feed ? "yes" : (mn9 >= 30 ? "partial (MN9 fired, no FEED mode)" : "no"), obs};
                }));

        list.add(new Scenario("loom",
                "dark disc expanding 5->90 deg at azimuth 60 -> ESCAPE (DNp01 giant fibre spike, TTMn)", 1200,
                new String[]{"L1/R", "LC4/R", "LPLC2/R", "LC4/L", "DNp01", "DNp04", "DNp02", "DNp03", "TTMn", "DNa02/L", "DNa02/R",
                        "superclass:descending_neuron"},
                (f, t, geom) -> {
                    Arrays.fill(f.luminance, 0.8f);
                    if (t >= 200 && t < 750) {
                        double s = Math.max(0, Math.min(1, (t - 200) / 500));
                        double size = 5 + 85 * s * s;                    // deg diameter
                        for (int ci : geom.columnsWithin(60, 0, size / 2)) f.luminance[ci] = 0.05f;
                        double expansion = 2 * 85 * s / 0.5;            // d(size)/dt in deg/s
                        f.objects.add(new SensoryFrame.VisualObject(60, 0, (float) size, (float) expansion, 0, false));
                    }
                },
                run -> {
                    boolean escape = run.jumped || run.ticksIn(MotorDecoder.Mode.ESCAPE) > 0;
                    String obs = run.topChannel() + "; modes " + run.modesSeen() + "; DNp01 max " + fmt(run.maxRate("DNp01")) + " Hz, DNp04 "
                            + fmt(run.maxRate("DNp04")) + ", TTMn " + fmt(run.maxRate("TTMn")) + ", LC4/R " + fmt(run.maxRate("LC4/R")) + ", LPLC2/R "
                            + fmt(run.maxRate("LPLC2/R")) + "; takeoff max " + fmt(run.maxChannel(MotorMap.TAKEOFF));
                    return new String[]{escape ? "yes" : "no", obs};
                }));

        list.add(new Scenario("rain",
                "debris on head/antennae (groomDust 0.6) -> GROOM (aDN1 DNg62 ~200 Hz, aDN2 DNge078 ~150 Hz)", 2000,
                new String[]{"subclass:grooming", "BM_InOm", "BM_Taste", "DNg62", "DNge078", "prefix:DNg12", "DNg15", "DNg11", "DNp09",
                        "superclass:descending_neuron"},
                (f, t, geom) -> {
                    f.groomDust = 0.6f;
                    f.moist = 0.5f;
                    f.addOdor("VP5", 0.5f);
                },
                run -> {
                    boolean groom = run.ticksIn(MotorDecoder.Mode.GROOM) > 0;
                    String obs = run.topChannel() + "; groomAntenna=" + fmt(run.maxChannel(MotorMap.GROOM_ANTENNA)) + " groomHead=" + fmt(run.maxChannel(MotorMap.GROOM_HEAD))
                            + "; modes " + run.modesSeen() + "; aDN1 max " + fmt(run.maxRate("DNg62")) + " Hz, aDN2 " + fmt(run.maxRate("DNge078"))
                            + ", DNg12 " + fmt(run.maxRate("prefix:DNg12")) + ", nagini " + fmt(run.maxRate("DNg15"));
                    return new String[]{groom ? "yes" : (run.maxRate("DNg62") > 50 ? "partial (aDN1 active, no GROOM mode)" : "no"), obs};
                }));

        list.add(new Scenario("wind",
                "airflow on the left antenna (0-1 s) then the right (1-2 s) -> ipsilateral JO wind/gravity drive; watch DNs", 2000,
                new String[]{"subclass:wind_gravity/L", "subclass:wind_gravity/R", "AMMC026", "DNg106", "DNa02/L", "DNa02/R", "DNg60", "DNp09",
                        "prefix:DNg02", "superclass:descending_neuron"},
                (f, t, geom) -> {
                    if (t < 1000) f.windLeft = 1f; else f.windRight = 1f;
                },
                run -> {
                    double l1 = run.meanRate("subclass:wind_gravity/L", 0, 1000), r1 = run.meanRate("subclass:wind_gravity/R", 0, 1000);
                    double l2 = run.meanRate("subclass:wind_gravity/L", 1000, 2000), r2 = run.meanRate("subclass:wind_gravity/R", 1000, 2000);
                    boolean ok = l1 > 2 * Math.max(1, r1) && r2 > 2 * Math.max(1, l2);
                    String obs = run.topChannel() + "; modes " + run.modesSeen() + "; JO wind L/R " + fmt(l1) + "/" + fmt(r1) + " Hz then " + fmt(l2) + "/" + fmt(r2)
                            + "; DNg106 max " + fmt(run.maxRate("DNg106")) + ", AMMC026 " + fmt(run.maxRate("AMMC026")) + ", yaw peak " + fmt(run.maxChannel(MotorMap.YAW))
                            + ", halt " + fmt(run.maxChannel(MotorMap.HALT));
                    return new String[]{ok ? "yes" : "no", obs};
                }));

        list.add(new Scenario("song",
                "conspecific courtship song (song 1.0) -> JO-A/B auditory drive (~275 Hz); watch GF/pIP10/pC1", 2000,
                new String[]{"subclass:auditory", "GNG301", "DNp01", "DNg29", "pIP10", "prefix:pC1_", "pMP2", "DNa02/L", "DNa02/R", "DNp09",
                        "superclass:descending_neuron"},
                (f, t, geom) -> {
                    f.song = 1f;
                    f.soundHigh = 0.5f;
                },
                run -> {
                    double aud = run.meanRate("subclass:auditory", 200, 1e9);
                    boolean ok = aud > 100;
                    String obs = run.topChannel() + "; modes " + run.modesSeen() + "; JO auditory mean " + fmt(aud) + " Hz; DNp01 max " + fmt(run.maxRate("DNp01"))
                            + ", GNG301 " + fmt(run.maxRate("GNG301")) + ", pIP10 " + fmt(run.maxRate("pIP10")) + ", pC1 " + fmt(run.maxRate("prefix:pC1_"))
                            + "; song ch " + fmt(run.maxChannel(MotorMap.SONG)) + " courtship " + fmt(run.maxChannel(MotorMap.COURTSHIP));
                    return new String[]{ok ? "yes" : "no", obs};
                }));
        return list;
    }

    // ------------------------------------------------------------------ helpers

    static String fmt(double v) { return String.format(Locale.ROOT, Math.abs(v) < 10 ? "%.2f" : "%.0f", v); }

    private static String abbreviate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "~";
    }
}
