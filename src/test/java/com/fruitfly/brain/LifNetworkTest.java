package com.fruitfly.brain;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LifNetworkTest {

    /** Delta-synapse configuration: makes single-spike arithmetic exact for the tests below. */
    private static LifConfig fastConfig() {
        LifConfig cfg = new LifConfig();
        cfg.dtMs = 0.1;
        cfg.delayMs = 1.0;
        cfg.synTauMs = 0.0;
        cfg.idleEpsMv = 1e-3;
        cfg.gain = 1.0; // tests reason about the literal 0.275 mV/synapse weight
        return cfg;
    }

    @Test
    void exponentialSynapseFiltersInputLikeShiuModel() {
        // dv/dt=(v0-v+g)/20ms, dg/dt=-g/5ms, g+=w. Peak depolarisation for a jump w is w*(5/15)*(e^-0.462 - e^-1.848) ~ 0.157 w
        SyntheticConnectome s = new SyntheticConnectome();
        int pre = s.neuron("A", 1, "L");
        int post = s.neuron("B", 1, "L");
        s.edge(pre, post, 100); // w = 27.5 mV -> peak ~4.3 mV < 7 mV gap: no spike
        Connectome c = s.build();
        LifConfig cfg = new LifConfig();
        cfg.dtMs = 0.1;
        cfg.delayMs = 1.0;
        cfg.idleEpsMv = 1e-3;
        cfg.gain = 1.0;
        LifNetwork net = new LifNetwork(c, cfg);
        net.forceSpike(pre);
        float peak = -100f;
        for (int k = 0; k < 400; k++) { // 40 ms
            net.step();
            peak = Math.max(peak, net.membrane(post));
        }
        assertEquals(0, net.spikesThisTick(post));
        double expectedPeak = -52.0 + 27.5 * (5.0 / 15.0) * (Math.exp(-0.462) - Math.exp(-1.848));
        assertEquals(expectedPeak, peak, 0.15, "peak depolarisation should match the analytic alpha-synapse response");

        // 200 synapses -> peak ~8.6 mV > 7 mV: one spike, delayed by the synaptic rise (~9 ms)
        SyntheticConnectome s2 = new SyntheticConnectome();
        int pre2 = s2.neuron("A", 1, "L");
        int post2 = s2.neuron("B", 1, "L");
        s2.edge(pre2, post2, 200);
        LifNetwork net2 = new LifNetwork(s2.build(), cfg);
        net2.forceSpike(pre2);
        net2.runMs(3.0);
        assertEquals(0, net2.spikesThisTick(post2), "should not fire before the synaptic current has built up");
        net2.runMs(30.0);
        assertEquals(1, net2.spikesThisTick(post2));
        net2.runMs(300);
        assertEquals(0, net2.activeNeurons());
    }

    @Test
    void poissonDrivenNeuronFiresAtRequestedRate() {
        SyntheticConnectome s = new SyntheticConnectome();
        int a = s.neuron("ORN_X", 1, "L");
        Connectome c = s.build();
        LifNetwork net = new LifNetwork(c, fastConfig());
        net.setStimulusRate(a, 100.0);
        net.runMs(10_000);
        long spikes = net.totalSpikes();
        // 100 Hz for 10 s -> ~1000 spikes; Poisson std ~32
        assertTrue(spikes > 850 && spikes < 1150, "spikes=" + spikes);
    }

    @Test
    void strongExcitatoryConnectionDrivesTargetAfterDelay() {
        SyntheticConnectome s = new SyntheticConnectome();
        int pre = s.neuron("A", 1, "L");
        int post = s.neuron("B", 1, "L");
        s.edge(pre, post, 40); // 40 * 0.275 = 11 mV jump >= 7 mV threshold gap
        Connectome c = s.build();
        LifConfig cfg = fastConfig();
        LifNetwork net = new LifNetwork(c, cfg);
        net.forceSpike(pre);
        // before the delay elapses, B must not have fired
        net.runMs(0.5);
        assertEquals(0, net.spikesThisTick(post));
        net.runMs(1.0);
        assertEquals(1, net.spikesThisTick(post), "B should fire exactly once after the 1 ms delay");
        assertEquals(1, net.spikesThisTick(pre));
        // network returns to idle
        net.runMs(200);
        assertEquals(0, net.activeNeurons(), "all neurons should go idle");
    }

    @Test
    void weakConnectionDoesNotReachThresholdButIntegrates() {
        SyntheticConnectome s = new SyntheticConnectome();
        int pre = s.neuron("A", 1, "L");
        int post = s.neuron("B", 1, "L");
        s.edge(pre, post, 10); // 2.75 mV < 7 mV
        Connectome c = s.build();
        LifNetwork net = new LifNetwork(c, fastConfig());
        net.forceSpike(pre);
        net.runMs(1.2);
        assertEquals(0, net.spikesThisTick(post));
        assertTrue(net.membrane(post) > -52.0 + 2.0, "membrane should be depolarised, was " + net.membrane(post));
        // three near-simultaneous presynaptic spikes summate over threshold
        net.forceSpike(pre);
        net.forceSpike(pre);
        net.forceSpike(pre);
        net.runMs(1.2);
        assertEquals(1, net.spikesThisTick(post));
    }

    @Test
    void inhibitionPreventsFiring() {
        SyntheticConnectome s = new SyntheticConnectome();
        int exc = s.neuron("E", 1, "L");
        int inh = s.neuron("I", -1, "L");
        int post = s.neuron("B", 1, "L");
        s.edge(exc, post, 30);   // +8.25 mV
        s.edge(inh, post, 30);   // -8.25 mV
        Connectome c = s.build();
        LifNetwork net = new LifNetwork(c, fastConfig());
        net.forceSpike(exc);
        net.forceSpike(inh);
        net.runMs(3);
        assertEquals(0, net.spikesThisTick(post), "balanced E/I must cancel");
        net.forceSpike(exc);
        net.runMs(3);
        assertEquals(1, net.spikesThisTick(post));
    }

    @Test
    void refractoryPeriodLimitsRate() {
        SyntheticConnectome s = new SyntheticConnectome();
        int post = s.neuron("B", 1, "L");
        Connectome c = s.build();
        LifConfig cfg = fastConfig();
        cfg.refractoryMs = 2.0;
        LifNetwork net = new LifNetwork(c, cfg);
        net.setInjectedCurrent(post, 50.0); // huge drive: 5 mV per 0.1 ms step
        net.runMs(100);
        int spikes = net.spikesThisTick(post);
        // with 2 ms refractory + ~0.2 ms to threshold: at most ~45-50 spikes in 100 ms
        assertTrue(spikes >= 40 && spikes <= 50, "spikes=" + spikes);
    }

    @Test
    void endTickUpdatesEmaAndResetsCounts() {
        SyntheticConnectome s = new SyntheticConnectome();
        int a = s.neuron("A", 1, "L");
        Connectome c = s.build();
        LifConfig cfg = fastConfig();
        cfg.rateEmaMs = 50.0;
        LifNetwork net = new LifNetwork(c, cfg);
        net.setStimulusRate(a, 200.0);
        double ema = 0;
        for (int t = 0; t < 40; t++) {
            net.runMs(50);
            net.endTick(50);
            ema = net.rateHz(a);
        }
        assertEquals(0, net.spikesThisTick(a));
        assertTrue(ema > 120 && ema < 280, "ema=" + ema);
    }

    @Test
    void chainPropagatesThroughManyNeuronsAndActiveSetStaysSmall() {
        SyntheticConnectome s = new SyntheticConnectome();
        int len = 200;
        int[] ids = new int[len];
        for (int i = 0; i < len; i++) ids[i] = s.neuron("chain", 1, "L");
        for (int i = 0; i + 1 < len; i++) s.edge(ids[i], ids[i + 1], 40);
        // plus 5000 idle bystanders that never receive input
        for (int i = 0; i < 5000; i++) s.neuron("idle", 1, "R");
        Connectome c = s.build();
        LifNetwork net = new LifNetwork(c, fastConfig());
        net.forceSpike(ids[0]);
        int maxActive = 0;
        for (int step = 0; step < 3000; step++) {
            net.step();
            maxActive = Math.max(maxActive, net.activeNeurons());
        }
        for (int i = 0; i < len; i++) assertEquals(1, net.spikesThisTick(ids[i]), "neuron " + i);
        assertTrue(maxActive < 50, "active set should stay small, was " + maxActive);
        assertEquals(len, net.totalSpikes());
    }

    @Test
    void populationSpecsResolve() {
        SyntheticConnectome s = new SyntheticConnectome();
        int a = s.neuron("DNp09", 1, "L");
        int b = s.neuron("DNp09", 1, "R");
        int cIdx = s.neuron("DNa02", 1, "L");
        s.neuron("ORN_DM1", 1, "L");
        s.neuron("ORN_DM4", 1, "L");
        Connectome c = s.build();
        PopulationIndex pi = new PopulationIndex(c);
        assertArrayEquals(new int[]{a, b}, pi.resolve("DNp09"));
        assertArrayEquals(new int[]{a}, pi.resolve("DNp09/L"));
        assertArrayEquals(new int[]{a, cIdx}, pi.resolve("DNp09/L, DNa02"));
        assertEquals(2, pi.resolve("prefix:ORN_").length);
        assertEquals(5, pi.resolve("all").length);
        assertEquals(1, pi.resolve("body:10002").length);
    }

    @Test
    void flybRoundTrip() throws IOException {
        // Build a minimal FLYB v1 byte stream by hand and load it through Connectome.load
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        ByteBuffer b = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
        int n = 2, nEdges = 1, nRetina = 1;
        b.put("FLYB".getBytes(StandardCharsets.US_ASCII)).putInt(1).putInt(n).putInt(nEdges).putInt(nRetina);
        putStr16(b, "test:v0");
        byte[] meta = "{\"x\":1}".getBytes(StandardCharsets.UTF_8);
        b.putInt(meta.length).put(meta);
        // 10 tables: types, superclasses, classes, subclasses, nts, sides, dimorphisms, fruDsx, neuromeres, nerves
        putTable(b, "", "R1-R6", "L1");
        putTable(b, "", "ol_sensory", "ol_intrinsic");
        putTable(b, "", "visual");
        putTable(b, "");
        putTable(b, "", "histamine", "acetylcholine");
        putTable(b, "", "L", "R");
        putTable(b, "");
        putTable(b, "");
        putTable(b, "");
        putTable(b, "");
        b.putLong(10001).putLong(10002);                    // bodyId
        b.putInt(1).putInt(2);                              // typeIdx
        b.put((byte) 1).put((byte) 2);                      // superclass
        b.put((byte) 1).put((byte) 0);                      // class
        b.putShort((short) 0).putShort((short) 0);          // subclass
        b.put((byte) 1).put((byte) 2);                      // nt
        b.put((byte) -1).put((byte) 1);                     // sign
        b.put((byte) 2).put((byte) 2);                      // side R,R
        b.put((byte) -1).put((byte) 5);                     // hex1
        b.put((byte) -1).put((byte) 7);                     // hex2
        b.put((byte) 0).put((byte) 0);                      // dim
        b.put((byte) 0).put((byte) 0);                      // fruDsx
        b.put((byte) 0).put((byte) 0);                      // neuromere
        b.put((byte) 0).put((byte) 0);                      // nerve
        b.putFloat(Float.NaN).putFloat(Float.NaN).putFloat(Float.NaN).putFloat(1f).putFloat(2f).putFloat(3f);
        b.putInt(100).putInt(200);                          // pre syn
        b.putInt(5).putInt(50);                             // post syn
        b.putInt(0).putInt(1).putInt(1);                    // rowPtr
        b.putInt(1);                                        // postIdx
        b.putShort((short) 60000);                          // weight (unsigned)
        b.putInt(0).put((byte) 2).put((byte) 5).put((byte) 7).put((byte) 0); // retina entry
        b.flip();
        try (GZIPOutputStream gz = new GZIPOutputStream(raw)) {
            gz.write(b.array(), 0, b.limit());
        }
        Connectome c = Connectome.load(new ByteArrayInputStream(raw.toByteArray()));
        assertEquals("test:v0", c.dataset);
        assertEquals(2, c.n);
        assertEquals("R1-R6", c.type(0));
        assertEquals("L1", c.type(1));
        assertEquals("ol_sensory", c.superclass(0));
        assertEquals("histamine", c.nt(0));
        assertEquals(-1, c.ntSign[0]);
        assertEquals("R", c.side(1));
        assertFalse(c.hasSoma(0));
        assertTrue(c.hasSoma(1));
        assertEquals(60000, c.synapseCount(0));
        assertEquals(1, c.postIdx[0]);
        assertEquals(1, c.retinaNeuron.length);
        assertEquals(5, c.retinaHex1[0]);
        assertEquals(1, c.indexOfBodyId(10002));
        assertEquals(1, c.outDegree(0));
        assertEquals(0, c.outDegree(1));
    }

    private static void putStr16(ByteBuffer b, String s) {
        byte[] d = s.getBytes(StandardCharsets.UTF_8);
        b.putShort((short) d.length).put(d);
    }

    private static void putTable(ByteBuffer b, String... entries) {
        b.putShort((short) entries.length);
        for (String e : entries) putStr16(b, e);
    }
}
