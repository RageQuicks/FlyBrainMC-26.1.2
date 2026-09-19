package com.fruitfly.brain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BrainRunnerTest {
    private static final double TICK = 50;

    /** Delta synapses, 1 ms delay, literal weights: A -> B (40 synapses = 11 mV) makes B fire exactly once per A spike. */
    private static LifConfig fastConfig() {
        LifConfig cfg = new LifConfig();
        cfg.dtMs = 0.1;
        cfg.delayMs = 1.0;
        cfg.synTauMs = 0.0;
        cfg.idleEpsMv = 1e-3;
        cfg.gain = 1.0;
        cfg.threads = 1;
        return cfg;
    }

    private static final class Pair {
        final Connectome c;
        final int a, b;

        Pair() {
            SyntheticConnectome s = new SyntheticConnectome();
            a = s.neuron("A", 1, "L");
            b = s.neuron("B", 1, "R");
            s.edge(a, b, 40);
            for (int i = 0; i < 100; i++) s.neuron("idle", 1, "L");
            c = s.build();
        }
    }

    @Test
    void tickNowAdvancesSimulationTimeByExactlyOneTick() {
        Pair p = new Pair();
        try (BrainRunner r = new BrainRunner(p.c, fastConfig(), TICK, "test")) {
            assertEquals(TICK, r.tickMs());
            assertEquals(0, r.ticksCompleted());
            assertEquals(0, r.snapshot().tick);
            assertEquals(0, r.snapshot().simTimeMs, 0.0);

            r.tickNow();
            assertEquals(TICK, r.net.simTimeMs(), 1e-6);
            assertEquals(1, r.ticksCompleted());
            BrainRunner.BrainSnapshot s1 = r.snapshot();
            assertEquals(1, s1.tick);
            assertEquals(TICK, s1.simTimeMs, 1e-6);
            assertEquals(0, s1.totalSpikes);
            assertEquals(0, s1.spikesThisTick);
            assertEquals(0, s1.watchedNames.length);

            r.tickNow();
            r.tickNow();
            assertEquals(3 * TICK, r.net.simTimeMs(), 1e-6);
            assertEquals(3, r.snapshot().tick);
            assertEquals(3 * TICK, r.snapshot().simTimeMs, 1e-6);
            assertNotSame(s1, r.snapshot(), "each tick publishes a new immutable snapshot");
            assertNull(r.failure());
        }
    }

    @Test
    void submittedActionsRunBeforeTheTickIntegrates() {
        Pair p = new Pair();
        try (BrainRunner r = new BrainRunner(p.c, fastConfig(), TICK, "test")) {
            AtomicReference<Double> simTimeWhenRun = new AtomicReference<>(-1.0);
            List<String> order = new ArrayList<>();
            r.submit(net -> {
                simTimeWhenRun.set(net.simTimeMs());
                order.add("submit");
                net.forceSpike(p.a);
            });
            r.setPostStepHook(net -> order.add("hook"));
            assertTrue(order.isEmpty(), "nothing runs until a tick");

            r.tickNow();
            assertEquals(0.0, simTimeWhenRun.get(), 0.0, "the action ran before runMs(tickMs)");
            assertEquals(List.of("submit", "hook"), order);
            BrainRunner.BrainSnapshot s = r.snapshot();
            assertEquals(2, s.spikesThisTick, "A (forced) and B (synaptically driven) both fired inside the tick");
            assertEquals(2, s.totalSpikes);
            assertEquals(2, s.spikeLogNeurons.length);
            assertEquals(p.a, s.spikeLogNeurons[0]);
            assertEquals(p.b, s.spikeLogNeurons[1]);
            assertEquals(0, s.spikeLogSteps[0], "A spiked at the start of the tick");
            assertEquals(fastConfig().delaySteps(), s.spikeLogSteps[1], "B followed one synaptic delay later");

            // a second tick with nothing queued: no new spikes, counters were reset by endTick
            r.tickNow();
            assertEquals(0, r.snapshot().spikesThisTick);
            assertEquals(2, r.snapshot().totalSpikes);
            assertEquals(1, order.stream().filter("submit"::equals).count(), "a submitted action runs once");
        }
    }

    @Test
    void watchedPopulationsAppearInSnapshotsWithRatesAndCounts() {
        Pair p = new Pair();
        try (BrainRunner r = new BrainRunner(p.c, fastConfig(), TICK, "test")) {
            r.watch("A");
            r.watch("B");
            r.watch("A"); // duplicate is ignored
            r.watch("idle");
            r.submit(net -> net.forceSpike(p.a));
            r.tickNow();
            BrainRunner.BrainSnapshot s = r.snapshot();
            assertArrayEquals(new String[]{"A", "B", "idle"}, s.watchedNames);
            assertArrayEquals(new int[]{1, 1, 0}, s.watchedCounts);
            assertEquals(1000.0 / TICK, s.rate("A"), 1e-9, "one spike per 50 ms = 20 Hz");
            assertEquals(1000.0 / TICK, s.rate("B"), 1e-9);
            assertEquals(0, s.rate("idle"), 0.0, "100 idle cells, no spikes");
            assertEquals(0, s.rate("not-watched"), 0.0);
            assertEquals(s.watchedRatesHz[0], s.rate("A"), 0.0);

            // the watch list can grow later; rates fall back to zero without input
            r.watch("A, B");
            r.tickNow();
            s = r.snapshot();
            assertArrayEquals(new String[]{"A", "B", "idle", "A, B"}, s.watchedNames);
            assertEquals(0, s.rate("A"), 0.0);
            assertEquals(0, s.rate("A, B"), 0.0);
            assertEquals(4, s.watchedRatesHz.length);
        }
    }

    @Test
    void postStepHookSeesTheTicksSpikesBeforeCountersReset() {
        Pair p = new Pair();
        try (BrainRunner r = new BrainRunner(p.c, fastConfig(), TICK, "test")) {
            AtomicInteger calls = new AtomicInteger();
            AtomicInteger spikesA = new AtomicInteger(-1), spikesB = new AtomicInteger(-1);
            AtomicReference<Double> simTime = new AtomicReference<>(-1.0);
            r.setPostStepHook(net -> {
                calls.incrementAndGet();
                spikesA.set(net.spikesThisTick(p.a));
                spikesB.set(net.spikesThisTick(p.b));
                simTime.set(net.simTimeMs());
            });
            r.submit(net -> net.forceSpike(p.a));
            r.tickNow();
            assertEquals(1, calls.get());
            assertEquals(1, spikesA.get(), "hook runs after integration (A's forced spike is counted)");
            assertEquals(1, spikesB.get(), "and B has fired by the end of the tick");
            assertEquals(TICK, simTime.get(), 1e-6, "hook runs after the tick's integration");
            assertEquals(0, r.net.spikesThisTick(p.a), "endTick reset the per-tick counters after the hook");
            assertEquals(0, r.net.spikesThisTick(p.b));
            assertTrue(r.net.rateHz(p.a) > 0, "endTick folded the spikes into the EMA rate");

            // a MotorDecoder in the hook (as FlyEntity does) sees the same window
            MotorDecoder dec = new MotorDecoder(r.populations);
            AtomicReference<MotorDecoder.MotorCommand> cmd = new AtomicReference<>();
            r.setPostStepHook(net -> cmd.set(dec.update(net, TICK)));
            r.tickNow();
            assertEquals(1, calls.get(), "the replaced hook no longer runs");
            assertNotNull(cmd.get());
            assertEquals(MotorDecoder.Mode.IDLE, cmd.get().mode);

            r.setPostStepHook(null);
            r.tickNow();
            assertEquals(1, calls.get(), "no hook installed");
            assertEquals(3, r.ticksCompleted());
        }
    }

    @Test
    void backgroundThreadTicksPausesAndStops() throws InterruptedException {
        Pair p = new Pair();
        BrainRunner r = new BrainRunner(p.c, fastConfig(), 5, "bg"); // 5 ms ticks keep the test short
        try {
            r.watch("A");
            r.start();
            long deadline = System.currentTimeMillis() + 5000;
            while (r.ticksCompleted() < 5 && System.currentTimeMillis() < deadline) Thread.sleep(5);
            assertTrue(r.ticksCompleted() >= 5, "background thread should tick; completed " + r.ticksCompleted());
            assertTrue(r.isAlive());
            assertNull(r.failure());
            assertTrue(r.realTimeFactor() > 0 && r.realTimeFactor() <= 1.0, "rtf=" + r.realTimeFactor());
            assertTrue(r.lastTickWallMs() >= 0);
            BrainRunner.BrainSnapshot s = r.snapshot();
            assertEquals(s.tick * 5.0, s.simTimeMs, 1e-6, "simulated time tracks the tick count");
            assertArrayEquals(new String[]{"A"}, s.watchedNames);

            r.setPaused(true);
            assertTrue(r.isPaused());
            Thread.sleep(50);
            long paused = r.ticksCompleted();
            Thread.sleep(100);
            assertTrue(r.ticksCompleted() <= paused + 1, "no ticks while paused");
            r.setPaused(false);
            deadline = System.currentTimeMillis() + 5000;
            while (r.ticksCompleted() < paused + 3 && System.currentTimeMillis() < deadline) Thread.sleep(5);
            assertTrue(r.ticksCompleted() >= paused + 3, "ticking resumes");
        } finally {
            r.close();
        }
        long deadline = System.currentTimeMillis() + 3000;
        while (r.isAlive() && System.currentTimeMillis() < deadline) Thread.sleep(5);
        assertFalse(r.isAlive(), "close() stops the brain thread");
        assertNull(r.failure());
    }
}
