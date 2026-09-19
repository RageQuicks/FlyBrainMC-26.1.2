package com.fruitfly.brain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives {@link SensoryEncoders} with hand-made {@link SensoryFrame}s on a small annotated connectome and measures
 * the resulting Poisson spike counts. No synapses: every neuron is an isolated spike generator, so counts over a
 * window are direct read-outs of the encoder's rates (Bernoulli per 0.5 ms step, fixed seed -> reproducible).
 */
class SensoryEncodersTest {

    /** Small sensory periphery with every annotation the encoders key on. */
    private static final class Periphery {
        final Connectome c;
        final PopulationIndex pi;
        final RetinaGeometry geom;
        final int[] dm1L, dm1R, dm4L, dm4R, lb3b, lb1a, lc4L, lc4R, lplc2L, lplc2R, joAudL, joAudR, joWindL, joWindR, joGroom, hsL, hsR;

        Periphery(int ornPerSide) {
            AnnotatedConnectome s = new AnnotatedConnectome();
            dm1L = s.neurons(ornPerSide, "ORN_DM1", 1, "L", "olfactory", "");
            dm1R = s.neurons(ornPerSide, "ORN_DM1", 1, "R", "olfactory", "");
            dm4L = s.neurons(2, "ORN_DM4", 1, "L", "olfactory", "");
            dm4R = s.neurons(2, "ORN_DM4", 1, "R", "olfactory", "");
            lb3b = s.neurons(4, "LB3b", 1, "", "gustatory", "");
            lb1a = s.neurons(2, "LB1a", 1, "", "gustatory", "");
            lc4L = s.neurons(3, "LC4", 1, "L", "visual", "");
            lc4R = s.neurons(3, "LC4", 1, "R", "visual", "");
            lplc2L = s.neurons(2, "LPLC2", 1, "L", "visual", "");
            lplc2R = s.neurons(2, "LPLC2", 1, "R", "visual", "");
            joAudL = s.neurons(2, "JO-A1", 1, "L", "mechanosensory", "auditory");
            joAudR = s.neurons(2, "JO-A1", 1, "R", "mechanosensory", "auditory");
            joWindL = s.neurons(2, "JO-CL", 1, "L", "mechanosensory", "wind_gravity");
            joWindR = s.neurons(2, "JO-CL", 1, "R", "mechanosensory", "wind_gravity");
            joGroom = s.neurons(2, "JO-FV", 1, "L", "mechanosensory", "grooming");
            hsL = new int[]{s.neuron("HSE", 1, "L")};
            hsR = new int[]{s.neuron("HSE", 1, "R")};
            c = s.build();
            pi = new PopulationIndex(c);
            geom = new RetinaGeometry(c); // no retina table -> zero columns
        }
    }

    private static LifConfig config() {
        LifConfig cfg = new LifConfig();
        cfg.dtMs = 0.5;
        cfg.threads = 1;
        return cfg;
    }

    private static SensoryEncoders encoders(Periphery p, SensoryEncoders.Params ep) {
        return new SensoryEncoders(p.c, p.pi, p.geom, ep);
    }

    /** Mean rate (Hz) of a population from the spikes counted since the last endTick over {@code ms}. */
    private static double rateHz(LifNetwork net, int[] ids, double ms) {
        return net.spikesThisTick(ids) * 1000.0 / (ids.length * ms);
    }

    private static double hill(SensoryEncoders.Params p, double s) {
        double sn = Math.pow(s, p.hillN);
        return sn / (Math.pow(p.hillK, p.hillN) + sn);
    }

    @Test
    void odorSourceOnTheRightDrivesTheRightAntennaHarder() {
        Periphery p = new Periphery(10);
        LifNetwork net = new LifNetwork(p.c, config());
        SensoryEncoders.Params ep = new SensoryEncoders.Params();
        SensoryEncoders enc = encoders(p, ep);

        SensoryFrame f = new SensoryFrame();
        f.addOdor("DM1", 1f);
        f.odorBearingDeg = 90; // head frame: positive azimuth = the fly's right (WorldSenses.toHead convention)
        enc.apply(f, net, 50);
        net.runMs(1000);

        double left = rateHz(net, p.dm1L, 1000), right = rateHz(net, p.dm1R, 1000);
        double mean = (left + right) / 2;
        // Hill(1 - 0.6 * adaptation) with one 50 ms adaptation step ~ 0.916 -> ~229 Hz at rMax 250
        double expectedMean = ep.ornRMax * hill(ep, 1 - ep.ornAdaptFraction * (1 - Math.exp(-50 / ep.ornAdaptTauMs)));
        assertEquals(expectedMean, mean, expectedMean * 0.15, "mean ORN_DM1 rate");
        // bilateral gain 1 +/- 0.3: the antenna ipsilateral to the source (right) gets 1.3x, the other 0.7x
        assertTrue(right > left * 1.3,
                "right ORN_DM1 (ipsilateral to a +90 deg source) should out-fire the left: right=" + right + " Hz, left=" + left + " Hz");
        // an undriven glomerulus stays silent
        assertEquals(0, net.spikesThisTick(p.dm4L) + net.spikesThisTick(p.dm4R), "ORN_DM4 has no drive");
        net.endTick(1000);

        // mirror image: source on the left favours the left antenna
        f.odorBearingDeg = -90;
        enc.apply(f, net, 50);
        net.runMs(1000);
        left = rateHz(net, p.dm1L, 1000);
        right = rateHz(net, p.dm1R, 1000);
        assertTrue(left > right * 1.3, "left ORN_DM1 (ipsilateral to a -90 deg source) should out-fire the right: left=" + left + " Hz, right=" + right + " Hz");
    }

    @Test
    void odorWithoutABearingDrivesBothAntennaeEqually() {
        Periphery p = new Periphery(10);
        LifNetwork net = new LifNetwork(p.c, config());
        SensoryEncoders enc = encoders(p, new SensoryEncoders.Params());

        SensoryFrame f = new SensoryFrame();
        f.addOdor("DM1", 1f);
        assertTrue(Float.isNaN(f.odorBearingDeg), "a fresh frame has no dominant source direction");
        enc.apply(f, net, 50);
        net.runMs(1000);
        double l = rateHz(net, p.dm1L, 1000), r = rateHz(net, p.dm1R, 1000);
        assertEquals(l, r, Math.max(l, r) * 0.15, "no bearing -> symmetric drive: L=" + l + " R=" + r);
        assertTrue(l > 150, "both antennae are driven: L=" + l);
    }

    @Test
    void sugarGrnContactFiresNearItsMaximumRateAndStopsOnRelease() {
        Periphery p = new Periphery(2);
        LifNetwork net = new LifNetwork(p.c, config());
        SensoryEncoders.Params ep = new SensoryEncoders.Params();
        SensoryEncoders enc = encoders(p, ep);

        SensoryFrame f = new SensoryFrame();
        f.addTaste("LB3b", 1f);
        enc.apply(f, net, 50);
        net.runMs(1000);
        double lb3b = rateHz(net, p.lb3b, 1000);
        assertEquals(120, lb3b, 30, "LB3b ~120 Hz +/- 25% (Hill(1) * grnRMax = " + ep.grnRMax * hill(ep, 1) + ")");
        assertEquals(0, net.spikesThisTick(p.lb1a), "bitter LB1a not contacted");
        assertEquals(0, net.spikesThisTick(p.dm1L) + net.spikesThisTick(p.dm1R), "no odor -> ORNs silent");
        net.endTick(1000);

        f.clear();
        enc.apply(f, net, 50);
        net.runMs(1000);
        assertEquals(0, net.spikesThisTick(p.lb3b), "GRNs fire only while in contact");
    }

    @Test
    void loomingObjectDrivesLc4AndLplc2OnTheEyeThatSeesIt() {
        Periphery p = new Periphery(2);
        LifNetwork net = new LifNetwork(p.c, config());
        SensoryEncoders.Params ep = new SensoryEncoders.Params();
        SensoryEncoders enc = encoders(p, ep);

        SensoryFrame f = new SensoryFrame();
        f.objects.add(new SensoryFrame.VisualObject(60, 0, 30, 400, 0, false)); // 30 deg disc expanding at 400 deg/s
        enc.apply(f, net, 50);
        net.runMs(500);
        double lc4R = rateHz(net, p.lc4R, 500), lplc2R = rateHz(net, p.lplc2R, 500);
        double expectedLc4 = ep.lcRMax * hill(ep, 400.0 / (400 + ep.loomExpansionHalfDegPerS));
        assertEquals(expectedLc4, lc4R, expectedLc4 * 0.25, "LC4/R encodes expansion velocity");
        assertTrue(lplc2R > 50, "LPLC2/R encodes angular size, was " + lplc2R);
        assertEquals(0, net.spikesThisTick(p.lc4L), "LC4/L sees nothing");
        assertEquals(0, net.spikesThisTick(p.lplc2L), "LPLC2/L sees nothing");
        net.endTick(500);

        // mirror: same object on the left
        f.objects.clear();
        f.objects.add(new SensoryFrame.VisualObject(-60, 0, 30, 400, 0, false));
        enc.apply(f, net, 50);
        net.runMs(500);
        assertTrue(rateHz(net, p.lc4L, 500) > 50);
        assertEquals(0, net.spikesThisTick(p.lc4R));
        net.endTick(500);

        // a static (non-expanding) object does not drive the loom detectors
        f.objects.clear();
        f.objects.add(new SensoryFrame.VisualObject(60, 0, 30, 0, 0, false));
        enc.apply(f, net, 50);
        net.runMs(500);
        assertEquals(0, net.spikesThisTick(p.lc4R) + net.spikesThisTick(p.lplc2R) + net.spikesThisTick(p.lc4L));
    }

    @Test
    void ornAdaptationReducesTheRateOverFiveSecondsOfConstantOdor() {
        Periphery p = new Periphery(10);
        LifNetwork net = new LifNetwork(p.c, config());
        SensoryEncoders.Params ep = new SensoryEncoders.Params();
        SensoryEncoders enc = encoders(p, ep);
        int[] all = concat(p.dm1L, p.dm1R);

        SensoryFrame f = new SensoryFrame();
        f.addOdor("DM1", 1f);
        long first = 0, last = 0;
        int ticks = 100; // 5 s at 50 ms
        for (int t = 0; t < ticks; t++) {
            enc.apply(f, net, 50);
            net.runMs(50);
            int s = net.spikesThisTick(all);
            if (t < 10) first += s;
            if (t >= ticks - 10) last += s;
            net.endTick(50);
        }
        double firstHz = first * 1000.0 / (all.length * 500), lastHz = last * 1000.0 / (all.length * 500);
        // adaptation state -> 1 - e^-2.5 = 0.92; effective drive 1 - 0.6 * 0.92 = 0.45 -> Hill 0.77 vs ~0.91 initially
        double expectedRatio = hill(ep, 1 - ep.ornAdaptFraction * (1 - Math.exp(-5000 / ep.ornAdaptTauMs))) / hill(ep, 0.99);
        assertTrue(lastHz < firstHz * 0.93, "adapted rate " + lastHz + " Hz should be well below the initial " + firstHz + " Hz");
        assertEquals(expectedRatio, lastHz / firstHz, 0.08, "adaptation depth");

        // removing the odor silences the ORNs even though the adaptation state is still high
        f.odor.clear();
        enc.apply(f, net, 50);
        net.runMs(500);
        assertEquals(0, net.spikesThisTick(all));
    }

    @Test
    void mechanosensoryChannelsMapToTheJohnstonsOrganSubclasses() {
        Periphery p = new Periphery(2);
        LifNetwork net = new LifNetwork(p.c, config());
        SensoryEncoders.Params ep = new SensoryEncoders.Params();
        SensoryEncoders enc = encoders(p, ep);

        SensoryFrame f = new SensoryFrame();
        f.song = 1f;
        f.windLeft = 1f;
        f.groomDust = 0.6f;
        enc.apply(f, net, 50);
        net.runMs(1000);
        double aud = ep.joAuditoryRMax * hill(ep, ep.songWeight); // song is weighted down relative to a startling noise
        assertEquals(aud, rateHz(net, p.joAudL, 1000), aud * 0.2, "song -> JO auditory (left)");
        assertEquals(aud, rateHz(net, p.joAudR, 1000), aud * 0.2, "song -> JO auditory (right)");
        double wind = ep.joWindRMax * hill(ep, 1);
        assertEquals(wind, rateHz(net, p.joWindL, 1000), wind * 0.2, "left antennal deflection -> left wind/gravity JO");
        assertEquals(0, net.spikesThisTick(p.joWindR), "no wind on the right antenna");
        double groom = ep.joGroomRMax * hill(ep, 0.6);
        assertEquals(groom, rateHz(net, p.joGroom, 1000), groom * 0.2, "dust -> JO-F grooming neurons");
        net.endTick(1000);

        f.clear();
        f.windRight = 1f;
        f.tilt = 0.1f; // gravity tilt drives both sides (Hill(0.1) ~ 0.26 -> ~47 Hz)
        enc.apply(f, net, 50);
        net.runMs(1000);
        assertTrue(rateHz(net, p.joWindR, 1000) > rateHz(net, p.joWindL, 1000) * 1.5, "right wind + tilt beats tilt alone");
        assertTrue(rateHz(net, p.joWindL, 1000) > 20, "tilt alone still deflects the left antenna");
        assertEquals(0, net.spikesThisTick(p.joAudL) + net.spikesThisTick(p.joAudR), "silence");
        assertEquals(0, net.spikesThisTick(p.joGroom));
    }

    @Test
    void selfRotationDrivesTheHorizontalSystemCellsOfTheEyeSeeingProgressiveFlow() {
        Periphery p = new Periphery(2);
        LifNetwork net = new LifNetwork(p.c, config());
        SensoryEncoders.Params ep = new SensoryEncoders.Params();
        SensoryEncoders enc = encoders(p, ep);
        // "HSE,HSN,HSS" per side must resolve to that side's cells only (one HSE per hemisphere here)
        assertEquals(2, enc.populationSizes().get("HS"), "HS cells resolved per eye: HSE/L + HSE/R");

        SensoryFrame f = new SensoryFrame();
        f.yawRateDegPerS = 300; // turning right -> left eye sees front-to-back flow -> left HS
        enc.apply(f, net, 50);
        net.runMs(1000);
        double expected = ep.lcRMax * 300 / (300 + ep.opticFlowHalfDegPerS);
        assertEquals(expected, rateHz(net, p.hsL, 1000), expected * 0.25);
        assertEquals(0, net.spikesThisTick(p.hsR));
        net.endTick(1000);

        f.yawRateDegPerS = -300;
        enc.apply(f, net, 50);
        net.runMs(1000);
        assertEquals(0, net.spikesThisTick(p.hsL));
        assertEquals(expected, rateHz(net, p.hsR, 1000), expected * 0.25);
    }

    @Test
    void laminaCellsFireTonicallyInDarknessAndAreSilencedByLight() {
        // two right-eye columns: one with reconstructed photoreceptors + L1, one with only lamina cells
        AnnotatedConnectome s = new AnnotatedConnectome();
        int prA = s.neuron("R1-R6", -1, "R"), prB = s.neuron("R1-R6", -1, "R");
        int l1A = s.neuron("L1", -1, "R");
        int l1B = s.neuron("L1", -1, "R"), l2B = s.neuron("L2", -1, "R");
        s.retina(prA, "R", 10, 10, Connectome.RETINA_R1R6);
        s.retina(prB, "R", 10, 10, Connectome.RETINA_R1R6);
        s.retina(l1A, "R", 10, 10, Connectome.RETINA_L1);
        s.retina(l1B, "R", 30, 30, Connectome.RETINA_L1);
        s.retina(l2B, "R", 30, 30, Connectome.RETINA_L2);
        Connectome c = s.build();
        PopulationIndex pi = new PopulationIndex(c);
        RetinaGeometry geom = new RetinaGeometry(c);
        assertEquals(2, geom.columnCount());
        LifNetwork net = new LifNetwork(c, config());
        SensoryEncoders.Params ep = new SensoryEncoders.Params();
        SensoryEncoders enc = new SensoryEncoders(c, pi, geom, ep);

        SensoryFrame f = new SensoryFrame();
        f.luminance = new float[geom.columnCount()];
        Arrays.fill(f.luminance, 0f); // darkness
        enc.apply(f, net, 50);
        enc.apply(f, net, 50); // second identical frame: the one-tick ON/OFF transient channels return to 0 (as in-game)
        net.runMs(1000);
        // tonic 0.5 mV/ms against a 20 ms leak -> 10 mV asymptote, 7 mV threshold: ~24 ms + 2.2 ms refractory -> ~38 Hz
        int darkB = net.spikesThisTick(l1B), darkB2 = net.spikesThisTick(l2B), darkA = net.spikesThisTick(l1A);
        assertTrue(darkB >= 25 && darkB <= 55, "L1 dark rate " + darkB + " Hz");
        assertEquals(darkB, darkB2, "L1 and L2 of the same column get the same tonic drive");
        assertEquals(darkB, darkA, "tonic drive does not depend on photoreceptor reconstruction in the dark");
        assertEquals(0, net.spikesThisTick(prA) + net.spikesThisTick(prB), "photoreceptors are silent in the dark");
        net.endTick(1000);

        Arrays.fill(f.luminance, 1f); // full light
        enc.apply(f, net, 50);
        enc.apply(f, net, 50); // settle the transient channels
        net.runMs(1000);
        assertTrue(net.spikesThisTick(l1B) <= 1, "light removes the tonic drive of a column without photoreceptors, got " + net.spikesThisTick(l1B));
        assertTrue(net.spikesThisTick(prA) > 50 && net.spikesThisTick(prB) > 50, "photoreceptors fire in the light");
        // the column with photoreceptors keeps its tonic current (the photoreceptors inhibit it synaptically in the real graph)
        assertTrue(net.spikesThisTick(l1A) >= 25, "L1 with photoreceptors keeps its tonic drive: " + net.spikesThisTick(l1A));
        net.endTick(1000);

        // NaN luminance = not sampled this tick: keeps the previous value (still light)
        Arrays.fill(f.luminance, Float.NaN);
        enc.apply(f, net, 50);
        net.runMs(1000);
        assertTrue(net.spikesThisTick(prA) > 50, "unsampled columns keep their last luminance");
    }

    @Test
    void disabledModalitiesAreNotApplied() {
        Periphery p = new Periphery(2);
        LifNetwork net = new LifNetwork(p.c, config());
        SensoryEncoders.Params ep = new SensoryEncoders.Params();
        ep.olfaction = false;
        ep.gustation = false;
        ep.mechanosensation = false;
        SensoryEncoders enc = encoders(p, ep);
        SensoryFrame f = new SensoryFrame();
        f.addOdor("DM1", 1f);
        f.addTaste("LB3b", 1f);
        f.song = 1f;
        f.objects.add(new SensoryFrame.VisualObject(60, 0, 30, 400, 0, false));
        enc.apply(f, net, 50);
        net.runMs(500);
        assertEquals(0, net.spikesThisTick(concat(p.dm1L, p.dm1R)));
        assertEquals(0, net.spikesThisTick(p.lb3b));
        assertEquals(0, net.spikesThisTick(concat(p.joAudL, p.joAudR)));
        assertTrue(net.spikesThisTick(p.lc4R) > 0, "vision stays enabled");
    }

    @Test
    void populationSizesReportTheEncoderTargets() {
        Periphery p = new Periphery(10);
        SensoryEncoders enc = encoders(p, new SensoryEncoders.Params());
        Map<String, Integer> sizes = enc.populationSizes();
        assertEquals(24, sizes.get("ORN+VP"), "10+10 DM1 and 2+2 DM4");
        assertEquals(6, sizes.get("GRN"), "4 LB3b + 2 LB1a");
        assertEquals(4, sizes.get("JO auditory"));
        assertEquals(4, sizes.get("JO wind"));
        assertEquals(2, sizes.get("JO groom"));
        assertEquals(6, sizes.get("LC4"));
        assertEquals(4, sizes.get("LPLC2"));
        assertEquals(0, sizes.get("retina columns"));
        assertEquals(2, enc.glomeruli().length);
        assertTrue(Arrays.asList(enc.glomeruli()).containsAll(Arrays.asList("DM1", "DM4")));
        assertSame(p.geom, enc.geometry());
    }

    private static int[] concat(int[] a, int[] b) {
        int[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
