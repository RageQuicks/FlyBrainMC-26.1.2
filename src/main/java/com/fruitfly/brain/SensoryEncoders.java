package com.fruitfly.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts a {@link SensoryFrame} into Poisson firing rates / injected currents on the real sensory neuron types of
 * the male CNS connectome. All transfer functions use the same saturating Hill law
 * {@code r = rMax · S^n / (K^n + S^n)} (n = 1.5, K = 0.2 by default) with slow adaptation so that encounters rather than
 * steady levels drive the network (docs/research/sensory-mapping.md §f).
 *
 * <p>Vision: the histaminergic photoreceptors inhibit the lamina, so lamina monopolar cells (L1/L2/L3) receive a
 * tonic depolarising current that light-driven photoreceptor spikes suppress. Columns without reconstructed
 * photoreceptors modulate the lamina current directly. Salient objects are additionally painted onto the visual
 * projection neurons that encode them (LC4 expansion velocity, LPLC2 angular size, LC11/LC18 small objects,
 * LC10a fly-like targets) on the eye that sees them, and self-rotation drives the HS/VS optic-flow cells.</p>
 *
 * <p>Must be called on the brain thread (it mutates the network).</p>
 */
public final class SensoryEncoders {
    public static final class Params {
        public double hillN = 1.5, hillK = 0.2;
        public double ornRMax = 250, ornSpont = 0, ornAdaptTauMs = 2000, ornAdaptFraction = 0.6, ornLateralGain = 0.3;
        public double grnRMax = 120, grnSpont = 0;
        public double photoreceptorRMax = 80, photoreceptorGamma = 0.8;
        /** Tonic depolarising drive for lamina cells in mV/ms (0.5 → ~40 Hz in darkness). */
        public double laminaTonicMvPerMs = 0.5;
        /**
         * Transient (ON/OFF) channels: a luminance decrement in a column drives its L2/L3 (ACh → Tm1/Tm2 → T5, the OFF
         * pathway) and an increment drives its Mi1 (ACh → T4, the ON pathway — L1 is glutamatergic, so it cannot switch
         * the ON pathway on in a silent LIF). Rate = onOffRMax · hill(|ΔL| / derivativeScale) for one tick.
         */
        public double onOffRMax = 180, derivativeScale = 0.3;
        /** Fraction of the tonic drive removed by full light in columns lacking photoreceptors. */
        public double laminaLightSuppression = 1.0;
        public double luminanceAdaptTauMs = 1000, luminanceAdaptFraction = 0.5;
        public double lcRMax = 200;
        public double loomExpansionHalfDegPerS = 200, loomSizeMeanDeg = 60, loomSizeSigmaDeg = 25;
        public double smallObjectMaxDeg = 15, opticFlowHalfDegPerS = 300;
        public double joAuditoryRMax = 150, joAuditorySpont = 0, joWindRMax = 180, joWindSpont = 0, joGroomRMax = 150;
        /** A conspecific's courtship song is quiet: its JO drive is songWeight × the full-amplitude sound drive. */
        public double songWeight = 0.3;
        public double bristleRMax = 200, thermoRMax = 150, hygroRMax = 120, proprioRMax = 150;
        public boolean vision = true, colorVision = true, olfaction = true, gustation = true, mechanosensation = true, thermoHygro = true;
        /**
         * Analytic feature channels (LC4/LPLC2 looming, LC11/LC18 small objects, LC10a fly targets, HS/VS optic flow)
         * painted from the object list. Turn off to test what the retinotopic lamina pathway produces on its own.
         */
        public boolean objectChannels = true;
    }

    private final Connectome c;
    private final PopulationIndex pi;
    private final RetinaGeometry geom;
    private final Params p;

    // olfaction: glomerulus -> ORN indices per side
    private final Map<String, int[][]> ornBySide = new HashMap<>();
    private final Map<String, double[]> ornAdapt = new HashMap<>();
    // gustation: GRN type -> indices
    private final Map<String, int[]> grnByType = new HashMap<>();
    // mechanosensation
    private final int[] joAuditoryL, joAuditoryR, joWindL, joWindR, joGroom, bmHead, bmEye, bmTaste;
    private final int[] tactileLegs, tactileWing, tactileNotum, tactileAbdomen, proprioHaltere, proprioLegHairPlates;
    private final int[] npf, snpf, dilp;
    // thermo / hygro
    private final int[] trnHot, trnCold, hrnDry, hrnMoist;
    // visual projection neurons per side
    private final int[][] lc4, lplc2, lc11, lc18, lc10a, lc15, hs, vs;
    // vision state
    private final float[] lumAdapt;
    private final float[] lastLum;
    private final int[] lastLaminaInjected;
    private final int[] mi1ByColumn;      // ON-pathway entry cell per column (−1 if none)

    private final List<int[]> touchedPopulations = new ArrayList<>();

    public SensoryEncoders(Connectome c, PopulationIndex pi, RetinaGeometry geom, Params p) {
        this.c = c;
        this.pi = pi;
        this.geom = geom;
        this.p = p;
        // ORNs by glomerulus (type = ORN_<glom>)
        for (int i : pi.resolve("class:olfactory")) {
            String t = c.type(i);
            if (!t.startsWith("ORN_")) continue;
            String glom = t.substring(4);
            ornBySide.computeIfAbsent(glom, k -> new int[][]{new int[0], new int[0]});
        }
        for (Map.Entry<String, int[][]> e : ornBySide.entrySet()) {
            String type = "ORN_" + e.getKey();
            e.getValue()[RetinaGeometry.LEFT] = pi.resolve(type + "/L");
            e.getValue()[RetinaGeometry.RIGHT] = pi.resolve(type + "/R");
            // ORNs without a side annotation: split them between the sides deterministically
            int[] all = pi.resolve(type);
            if (e.getValue()[0].length + e.getValue()[1].length < all.length) {
                List<Integer> l = new ArrayList<>(), r = new ArrayList<>();
                for (int k = 0; k < all.length; k++) (k % 2 == 0 ? l : r).add(all[k]);
                e.getValue()[0] = l.stream().mapToInt(Integer::intValue).toArray();
                e.getValue()[1] = r.stream().mapToInt(Integer::intValue).toArray();
            }
            ornAdapt.put(e.getKey(), new double[]{0});
        }
        // hygro/thermo receptor neurons are driven through the same odor map keys ("VP2", "VP4" ...)
        for (String vp : new String[]{"VP1d", "VP1l", "VP1m", "VP2", "VP3a", "VP3b", "VP4", "VP5"}) {
            int[] ids = pi.resolve("HRN_" + vp + ",TRN_" + vp);
            if (ids.length > 0) {
                int[] l = Arrays.stream(ids).filter(i -> "L".equals(c.side(i))).toArray();
                int[] r = Arrays.stream(ids).filter(i -> "R".equals(c.side(i))).toArray();
                if (l.length + r.length < ids.length) { l = ids; r = new int[0]; }
                ornBySide.put(vp, new int[][]{l, r});
                ornAdapt.put(vp, new double[]{0});
            }
        }
        for (int i : pi.resolve("class:gustatory")) {
            String t = c.type(i);
            if (t.isEmpty()) continue;
            grnByType.computeIfAbsent(t, k -> pi.resolve(k));
        }
        joAuditoryL = pi.resolve("subclass:auditory/L");
        joAuditoryR = pi.resolve("subclass:auditory/R");
        joWindL = pi.resolve("subclass:wind_gravity/L");
        joWindR = pi.resolve("subclass:wind_gravity/R");
        joGroom = pi.resolve("subclass:grooming,prefix:JO-F");
        bmHead = pi.resolve("prefix:BM_");
        bmEye = pi.resolve("BM_InOm");
        bmTaste = pi.resolve("BM_Taste");
        tactileLegs = pi.resolve("class:mechanosensory_tactile&nerve:ProLN,class:mechanosensory_tactile&nerve:MesoLN,class:mechanosensory_tactile&nerve:MetaLN");
        tactileWing = pi.resolve("class:mechanosensory_tactile&nerve:ADMN");
        tactileNotum = pi.resolve("class:mechanosensory_tactile&nerve:PDMN,class:mechanosensory_tactile&nerve:DMetaN");
        tactileAbdomen = pi.resolve("class:mechanosensory_tactile&nerve:AbN3,class:mechanosensory_tactile&nerve:AbN4");
        proprioHaltere = pi.resolve("class:mechanosensory_proprioceptive&subclass:haltere");
        proprioLegHairPlates = pi.resolve("class:mechanosensory_proprioceptive&subclass:hair plate");
        npf = optional(pi, "NPF");
        snpf = optional(pi, "sNPF");
        dilp = optional(pi, "DILP,ILP");
        trnHot = pi.resolve("TRN_VP2");
        trnCold = pi.resolve("TRN_VP3a,TRN_VP3b");
        hrnDry = pi.resolve("HRN_VP4");
        hrnMoist = pi.resolve("HRN_VP5,HRN_VP1d");
        lc4 = sides("LC4");
        lplc2 = sides("LPLC2");
        lc11 = sides("LC11");
        lc18 = sides("LC18");
        lc10a = sides("LC10a");
        lc15 = sides("LC15");
        hs = sides("HSE,HSN,HSS");
        vs = sides("prefix:VS");
        int nCols = geom.columnCount();
        lumAdapt = new float[nCols];
        lastLum = new float[nCols];
        Arrays.fill(lastLum, 0.5f);
        Arrays.fill(lumAdapt, 0.5f);
        lastLaminaInjected = new int[nCols];
        mi1ByColumn = new int[nCols];
        Arrays.fill(mi1ByColumn, -1);
        for (int i : pi.resolve("Mi1")) {
            int side = RetinaGeometry.sideOf(c.side(i));
            int h1 = c.hex1[i], h2 = c.hex2[i];
            if (side < 0 || h1 <= 0 || h2 <= 0) continue;
            int ci = geom.columnIndex(side, h1, h2);
            if (ci >= 0) mi1ByColumn[ci] = i;
        }
    }

    /** Resolve a (possibly comma-separated) spec once per hemisphere; the side suffix is applied to every term. */
    private int[][] sides(String spec) {
        return new int[][]{pi.resolve(withSide(spec, "L")), pi.resolve(withSide(spec, "R"))};
    }

    private static String withSide(String spec, String side) {
        return Arrays.stream(spec.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s + "/" + side).collect(Collectors.joining(","));
    }

    public RetinaGeometry geometry() { return geom; }
    public Params params() { return p; }

    /** Glomeruli that can be driven through {@link SensoryFrame#odor}. */
    public String[] glomeruli() { return ornBySide.keySet().toArray(new String[0]); }

    private double hill(double s) {
        if (s <= 0) return 0;
        double sn = Math.pow(s, p.hillN);
        return sn / (Math.pow(p.hillK, p.hillN) + sn);
    }

    /** Apply one tick of sensory input. {@code dtMs} = elapsed brain time since the previous call. */
    public void apply(SensoryFrame f, LifNetwork net, double dtMs) {
        applyInternalState(f, net);
        if (p.olfaction) applyOlfaction(f, net, dtMs);
        if (p.gustation) applyGustation(f, net);
        if (p.mechanosensation) applyMechanosensation(f, net);
        if (p.thermoHygro) applyThermoHygro(f, net);
        if (p.vision) applyVision(f, net, dtMs);
    }

    // ------------------------------------------------------------------ olfaction

    private void applyOlfaction(SensoryFrame f, LifNetwork net, double dtMs) {
        double aAdapt = 1 - Math.exp(-dtMs / p.ornAdaptTauMs);
        double bearing = Float.isNaN(f.odorBearingDeg) ? 0 : Math.toRadians(f.odorBearingDeg);
        // WorldSenses.toHead: positive azimuth = the fly's right, so a right-hand source favours the right antenna
        double gainL = 1 - p.ornLateralGain * Math.sin(bearing);
        double gainR = 1 + p.ornLateralGain * Math.sin(bearing);
        for (Map.Entry<String, int[][]> e : ornBySide.entrySet()) {
            String glom = e.getKey();
            boolean isVp = glom.startsWith("VP");
            if (isVp) continue; // hygro/thermo handled separately
            double drive = f.odor.getOrDefault(glom, 0f);
            double[] ad = ornAdapt.get(glom);
            // Starvation biologically sensitizes appetitive ORNs, especially DM1/VA2,
            // while aversive DM5 signalling is comparatively suppressed.
            double stateGain = 1.0;
            if (glom.equals("DM1") || glom.equals("VA2")) stateGain += 1.8 * f.hunger;
            if (glom.equals("DM5")) stateGain *= (1.0 - 0.55 * f.hunger);
            drive *= stateGain;
            ad[0] += aAdapt * (drive - ad[0]);
            double eff = Math.max(0, drive - p.ornAdaptFraction * ad[0]);
            double rate = p.ornSpont + (p.ornRMax - p.ornSpont) * hill(eff);
            int[][] sides = e.getValue();
            setRates(net, sides[RetinaGeometry.LEFT], rate * gainL);
            setRates(net, sides[RetinaGeometry.RIGHT], rate * gainR);
        }
    }

    // ------------------------------------------------------------------ internal homeostasis

    private void applyInternalState(SensoryFrame f, LifNetwork net) {
        // These are homeostatic/neuromodulatory drives, not behavioural commands.
        // They stand in for the endocrine state reaching NPF/sNPF/insulin systems.
        setRates(net, npf, 35.0 * f.hunger);
        setRates(net, snpf, 28.0 * f.hunger);
        setRates(net, dilp, 20.0 * f.satiety());
    }

    private static int[] optional(PopulationIndex pi, String spec) {
        try {
            return pi.resolve(spec);
        } catch (IllegalArgumentException ignored) {
            return new int[0];
        }
    }

    // ------------------------------------------------------------------ gustation

    private void applyGustation(SensoryFrame f, LifNetwork net) {
        for (Map.Entry<String, int[]> e : grnByType.entrySet()) {
            double drive = f.taste.getOrDefault(e.getKey(), 0f);
            double stateGain = e.getKey().contains("LB3") || e.getKey().contains("LgLG3") || e.getKey().contains("PhG1")
                    ? 1.0 + 1.5 * f.hunger
                    : 1.0 - 0.35 * f.hunger;
            drive *= Math.max(0.1, stateGain);
            double rate = drive <= 0 ? p.grnSpont : p.grnSpont + (p.grnRMax - p.grnSpont) * hill(drive);
            setRates(net, e.getValue(), rate);
        }
    }

    // ------------------------------------------------------------------ mechanosensation

    private void applyMechanosensation(SensoryFrame f, LifNetwork net) {
        // a conspecific's song is quiet compared with a startling noise: weight it down so it informs (JO-A/B -> GNG301,
        // pC1) without driving the giant fibre every time
        double sound = Math.max(f.soundHigh, p.songWeight * f.song);
        double audRate = p.joAuditorySpont + (p.joAuditoryRMax - p.joAuditorySpont) * hill(sound);
        setRates(net, joAuditoryL, audRate);
        setRates(net, joAuditoryR, audRate);
        // JO-B (<100 Hz) shares the auditory pool in this dataset's annotation; low band adds to both
        if (f.soundLow > 0) {
            double lowRate = p.joAuditorySpont + (p.joAuditoryRMax - p.joAuditorySpont) * hill(Math.max(sound, f.soundLow * 0.7));
            setRates(net, joAuditoryL, lowRate);
            setRates(net, joAuditoryR, lowRate);
        }
        setRates(net, joWindL, p.joWindSpont + (p.joWindRMax - p.joWindSpont) * hill(Math.max(f.windLeft, f.tilt)));
        setRates(net, joWindR, p.joWindSpont + (p.joWindRMax - p.joWindSpont) * hill(Math.max(f.windRight, f.tilt)));
        setRates(net, joGroom, p.joGroomRMax * hill(f.groomDust));
        double head = Math.max(f.touchHead, f.damage);
        setRates(net, bmHead, p.bristleRMax * hill(head));
        setRates(net, bmEye, p.bristleRMax * hill(Math.max(head, f.groomDust)));
        setRates(net, bmTaste, p.bristleRMax * hill(Math.max(head, f.groomDust * 0.7)));
        setRates(net, tactileLegs, p.bristleRMax * hill(Math.max(f.touchLegs, f.damage)));
        setRates(net, tactileWing, p.bristleRMax * hill(Math.max(f.touchWing, f.damage)));
        setRates(net, tactileNotum, p.bristleRMax * hill(Math.max(f.touchNotum, f.damage)));
        setRates(net, tactileAbdomen, p.bristleRMax * hill(Math.max(f.touchAbdomen, f.damage)));
        setRates(net, proprioHaltere, p.proprioRMax * hill(f.wingbeat));
        setRates(net, proprioLegHairPlates, f.legsOnGround ? p.proprioRMax * 0.3 : 0);
    }

    // ------------------------------------------------------------------ thermo / hygro

    private void applyThermoHygro(SensoryFrame f, LifNetwork net) {
        setRates(net, trnHot, p.thermoRMax * hill(Math.max(f.hot, f.odor.getOrDefault("VP2", 0f))));
        setRates(net, trnCold, p.thermoRMax * hill(Math.max(f.cold, Math.max(f.odor.getOrDefault("VP3a", 0f), f.odor.getOrDefault("VP3b", 0f)))));
        setRates(net, hrnDry, p.hygroRMax * hill(Math.max(f.dry, f.odor.getOrDefault("VP4", 0f))));
        setRates(net, hrnMoist, p.hygroRMax * hill(Math.max(f.moist, f.odor.getOrDefault("VP5", 0f))));
    }

    // ------------------------------------------------------------------ vision

    private void applyVision(SensoryFrame f, LifNetwork net, double dtMs) {
        int n = geom.columnCount();
        double aAdapt = 1 - Math.exp(-dtMs / p.luminanceAdaptTauMs);
        boolean haveLum = f.luminance != null && f.luminance.length >= n;
        for (int ci = 0; ci < n; ci++) {
            float prev = lastLum[ci];
            float L = haveLum ? f.luminance[ci] : Float.NaN;
            if (Float.isNaN(L)) L = prev; else lastLum[ci] = L;
            float dL = L - prev;
            lumAdapt[ci] += (float) (aAdapt * (L - lumAdapt[ci]));
            // Weber-like adaptation: response to luminance relative to the adapted level
            double eff = Math.max(0, L - p.luminanceAdaptFraction * lumAdapt[ci]) / (1 - p.luminanceAdaptFraction * 0.5);
            eff = Math.min(1, eff);
            RetinaGeometry.Column col = geom.column(ci);
            double tonic = p.laminaTonicMvPerMs;
            if (col.photoreceptors.length > 0) {
                if (p.colorVision && (col.r7.length > 0 || col.r8.length > 0)) {
                    // Broadband R1-R6: motion/luminance channel
                    if (col.r1r6.length > 0) {
                        double rateL = p.photoreceptorRMax * Math.pow(eff, p.photoreceptorGamma);
                        setRates(net, col.r1r6, rateL);
                    }
                    // R7: UV channel (Rh3 in pale/DRA 345 nm, Rh4 in yellow 375 nm)
                    if (col.r7.length > 0) {
                        float uvVal = (f.uv != null && f.uv.length > ci && !Float.isNaN(f.uv[ci])) ? f.uv[ci] : (float) eff;
                        double rateUv = p.photoreceptorRMax * Math.pow(Math.max(0, Math.min(1, uvVal)), p.photoreceptorGamma);
                        setRates(net, col.r7, rateUv);
                    }
                    // R8: Blue/Green channel (Rh5 in pale 437 nm -> blue; Rh6 in yellow 508 nm -> green)
                    if (col.r8.length > 0) {
                        // High hex (u >= 46) is dorsal rim area (UV polarized); otherwise check green vs blue
                        float colVal;
                        if (col.u >= 46) {
                            colVal = (f.uv != null && f.uv.length > ci && !Float.isNaN(f.uv[ci])) ? f.uv[ci] : (float) eff;
                        } else {
                            // In pale/yellow mosaic (~65% yellow Rh6 green, ~35% pale Rh5 blue):
                            // deterministic hash on column hex coordinates to stably match the pale/yellow mosaic
                            boolean isYellow = ((col.hex1 * 31 + col.hex2 * 17 + col.side * 13) % 100) < 65;
                            if (isYellow) {
                                colVal = (f.green != null && f.green.length > ci && !Float.isNaN(f.green[ci])) ? f.green[ci] : (float) eff;
                            } else {
                                colVal = (f.blue != null && f.blue.length > ci && !Float.isNaN(f.blue[ci])) ? f.blue[ci] : (float) eff;
                            }
                        }
                        double rateR8 = p.photoreceptorRMax * Math.pow(Math.max(0, Math.min(1, colVal)), p.photoreceptorGamma);
                        setRates(net, col.r8, rateR8);
                    }
                } else {
                    double rate = p.photoreceptorRMax * Math.pow(eff, p.photoreceptorGamma);
                    setRates(net, col.photoreceptors, rate);
                }
            } else {
                tonic *= 1 - p.laminaLightSuppression * eff;
            }
            if (col.l1 >= 0) net.setInjectedCurrent(col.l1, tonic);
            if (col.l2 >= 0) net.setInjectedCurrent(col.l2, tonic);
            if (col.l3 >= 0) net.setInjectedCurrent(col.l3, tonic);
            // transient channels: OFF (decrement) -> L2/L3 spikes; ON (increment) -> Mi1 spikes, for this tick only
            double off = dL < 0 ? p.onOffRMax * hill(-dL / p.derivativeScale) : 0;
            double on = dL > 0 ? p.onOffRMax * hill(dL / p.derivativeScale) : 0;
            if (col.l2 >= 0) net.setStimulusRate(col.l2, off);
            if (col.l3 >= 0) net.setStimulusRate(col.l3, off);
            int mi1 = mi1ByColumn[ci];
            if (mi1 >= 0) net.setStimulusRate(mi1, on);
        }
        if (!p.objectChannels) return; // retinotopic pathway only (emergence test)
        // feature-level drive of visual projection neurons
        double[] loomV = new double[2], loomS = new double[2], small = new double[2], flyT = new double[2], bars = new double[2];
        for (SensoryFrame.VisualObject o : f.objects) {
            int side = o.azimuthDeg >= 0 ? RetinaGeometry.RIGHT : RetinaGeometry.LEFT;
            double contrast = Math.max(0, Math.min(1, o.contrast));
            if (o.expansionDegPerS > 0) {
                loomV[side] = Math.max(loomV[side], contrast * o.expansionDegPerS / (o.expansionDegPerS + p.loomExpansionHalfDegPerS));
                double z = (o.angularSizeDeg - p.loomSizeMeanDeg) / p.loomSizeSigmaDeg;
                loomS[side] = Math.max(loomS[side], contrast * Math.exp(-0.5 * z * z));
            }
            if (o.angularSizeDeg < p.smallObjectMaxDeg && o.angularSpeedDegPerS > 5) {
                double v = o.angularSpeedDegPerS / (o.angularSpeedDegPerS + 100);
                small[side] = Math.max(small[side], contrast * v);
                if (o.flyLike && Math.abs(o.azimuthDeg) < 70) flyT[side] = Math.max(flyT[side], contrast * Math.max(0.4, v));
            }
        }
        for (int s = 0; s < 2; s++) {
            setRates(net, lc4[s], p.lcRMax * hill(loomV[s]));
            setRates(net, lplc2[s], p.lcRMax * hill(loomS[s]));
            setRates(net, lc11[s], p.lcRMax * hill(small[s]));
            setRates(net, lc18[s], p.lcRMax * hill(small[s]));
            setRates(net, lc10a[s], p.lcRMax * hill(flyT[s]));
            setRates(net, lc15[s], p.lcRMax * hill(bars[s]));
        }
        // optic flow from self-rotation: turning right -> progressive (front-to-back) flow on the left eye -> left HS
        double yaw = f.yawRateDegPerS;
        double hsL = Math.max(0, yaw) / (Math.abs(yaw) + p.opticFlowHalfDegPerS);
        double hsR = Math.max(0, -yaw) / (Math.abs(yaw) + p.opticFlowHalfDegPerS);
        setRates(net, hs[RetinaGeometry.LEFT], p.lcRMax * hsL);
        setRates(net, hs[RetinaGeometry.RIGHT], p.lcRMax * hsR);
        double pitchRoll = Math.abs(f.pitchRateDegPerS) + Math.abs(f.rollRateDegPerS);
        double vsDrive = pitchRoll / (pitchRoll + p.opticFlowHalfDegPerS);
        setRates(net, vs[RetinaGeometry.LEFT], p.lcRMax * vsDrive * (f.rollRateDegPerS <= 0 ? 1 : 0.5));
        setRates(net, vs[RetinaGeometry.RIGHT], p.lcRMax * vsDrive * (f.rollRateDegPerS >= 0 ? 1 : 0.5));
    }

    private static void setRates(LifNetwork net, int[] ids, double hz) {
        if (ids == null || ids.length == 0) return;
        if (hz < 0.01) hz = 0;
        for (int i : ids) net.setStimulusRate(i, hz);
    }

    /** Diagnostics: population sizes for each encoder target. */
    public Map<String, Integer> populationSizes() {
        Map<String, Integer> m = new HashMap<>();
        int orn = 0;
        for (int[][] s : ornBySide.values()) orn += s[0].length + s[1].length;
        m.put("ORN+VP", orn);
        int grn = 0;
        for (int[] g : grnByType.values()) grn += g.length;
        m.put("GRN", grn);
        m.put("JO auditory", joAuditoryL.length + joAuditoryR.length);
        m.put("JO wind", joWindL.length + joWindR.length);
        m.put("JO groom", joGroom.length);
        m.put("BM head", bmHead.length);
        m.put("tactile legs", tactileLegs.length);
        m.put("tactile wing", tactileWing.length);
        m.put("tactile notum", tactileNotum.length);
        m.put("tactile abdomen", tactileAbdomen.length);
        m.put("haltere", proprioHaltere.length);
        m.put("hair plates", proprioLegHairPlates.length);
        m.put("LC4", lc4[0].length + lc4[1].length);
        m.put("LPLC2", lplc2[0].length + lplc2[1].length);
        m.put("LC11", lc11[0].length + lc11[1].length);
        m.put("LC10a", lc10a[0].length + lc10a[1].length);
        m.put("HS", hs[0].length + hs[1].length);
        m.put("VS", vs[0].length + vs[1].length);
        m.put("retina columns", geom.columnCount());
        return m;
    }
}
