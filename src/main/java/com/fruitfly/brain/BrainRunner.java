package com.fruitfly.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Owns one {@link LifNetwork} on a dedicated daemon thread and advances it in fixed "brain ticks" that mirror the
 * game tick (50 ms by default). Sensory input is applied through queued {@link Consumer}s executed on the brain thread
 * at tick boundaries; outputs are published as an immutable {@link BrainSnapshot} that the game thread reads.
 *
 * <p>If the simulation cannot keep up with real time, the runner keeps stepping full ticks but the published
 * snapshots fall behind wall-clock time; {@link BrainSnapshot#realTimeFactor} reports the ratio so the body can
 * slow itself down ("bullet time") rather than skip neural time.</p>
 */
public final class BrainRunner implements AutoCloseable {
    public final LifNetwork net;
    public final PopulationIndex populations;
    private final double tickMs;
    private final Thread thread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<Consumer<LifNetwork>> inbox = new ConcurrentLinkedQueue<>();
    private final List<String> watched = new ArrayList<>();
    private int[][] watchedIdx = new int[0][];
    private volatile BrainSnapshot snapshot;
    private volatile Consumer<LifNetwork> postStepHook;
    private volatile Consumer<Throwable> actionErrorHandler;
    private volatile long ticksCompleted;
    private volatile double lastTickWallMs;
    private volatile double realTimeFactor = 1.0;
    private volatile Throwable failure;

    public BrainRunner(Connectome c, LifConfig cfg, double tickMs, String name) {
        this.net = new LifNetwork(c, cfg);
        this.populations = new PopulationIndex(c);
        this.tickMs = tickMs;
        this.snapshot = BrainSnapshot.empty();
        this.thread = new Thread(this::loop, "fruitfly-brain-" + name);
        this.thread.setDaemon(true);
        this.thread.setPriority(Thread.NORM_PRIORITY - 1);
    }

    public void start() { thread.start(); }

    public double tickMs() { return tickMs; }

    /**
     * Register a population whose mean rate appears in every snapshot (thread-safe, applied next tick). An invalid
     * spec throws inside the queued action, which {@link #drainInbox()} isolates; the watch list is left untouched.
     */
    public void watch(String spec) {
        inbox.add(n -> {
            synchronized (watched) {
                if (watched.contains(spec)) return;
                int[] ids = populations.resolve(spec); // resolve BEFORE mutating so a bad spec never lingers
                watched.add(spec);
                int[][] nw = Arrays.copyOf(watchedIdx, watched.size());
                nw[watched.size() - 1] = ids;
                watchedIdx = nw;
            }
        });
    }

    /**
     * Handler invoked on the brain thread when a queued action throws (default: a line on stderr). One bad action
     * (e.g. an unknown population spec) must not kill the whole simulation.
     */
    public void setActionErrorHandler(Consumer<Throwable> handler) { this.actionErrorHandler = handler; }

    /** Queue a mutation (stimulus update etc.) to run on the brain thread before the next tick. */
    public void submit(Consumer<LifNetwork> action) { inbox.add(action); }

    public BrainSnapshot snapshot() { return snapshot; }
    public long ticksCompleted() { return ticksCompleted; }
    public double lastTickWallMs() { return lastTickWallMs; }
    public double realTimeFactor() { return realTimeFactor; }
    public Throwable failure() { return failure; }
    public boolean isAlive() { return thread.isAlive() && failure == null; }

    public void setPaused(boolean p) { paused.set(p); }
    public boolean isPaused() { return paused.get(); }

    /** Advance exactly one tick synchronously on the caller thread (for tests/headless use; do not mix with start()). */
    public void tickNow() { doTick(); }

    private void loop() {
        long nextDeadline = System.nanoTime();
        final long tickNanos = (long) (tickMs * 1_000_000L);
        try {
            while (running.get()) {
                if (paused.get()) {
                    drainInbox();
                    LockSupport.parkNanos(5_000_000L);
                    nextDeadline = System.nanoTime();
                    continue;
                }
                long t0 = System.nanoTime();
                doTick();
                long t1 = System.nanoTime();
                double wall = (t1 - t0) / 1e6;
                lastTickWallMs = wall;
                realTimeFactor = 0.9 * realTimeFactor + 0.1 * Math.min(1.0, tickMs / Math.max(1e-3, wall));
                nextDeadline += tickNanos;
                long now = System.nanoTime();
                if (nextDeadline - now > 0) {
                    LockSupport.parkNanos(nextDeadline - now);
                } else if (now - nextDeadline > 10 * tickNanos) {
                    nextDeadline = now; // too far behind: drop the backlog instead of spinning
                }
            }
        } catch (Throwable t) {
            failure = t;
        }
    }

    private void drainInbox() {
        Consumer<LifNetwork> a;
        while ((a = inbox.poll()) != null) {
            try {
                a.accept(net);
            } catch (RuntimeException e) {
                Consumer<Throwable> h = actionErrorHandler;
                if (h != null) h.accept(e);
                else System.err.println("[" + thread.getName() + "] queued brain action failed: " + e);
            }
        }
    }

    /** Hook run on the brain thread after each tick's integration and before counters reset (e.g. motor decoding). */
    public void setPostStepHook(Consumer<LifNetwork> hook) { this.postStepHook = hook; }

    private void doTick() {
        drainInbox();
        net.runMs(tickMs);
        Consumer<LifNetwork> hook = postStepHook;
        if (hook != null) hook.accept(net);
        int[][] w = watchedIdx;
        String[] names;
        synchronized (watched) { names = watched.toArray(new String[0]); }
        double[] rates = new double[w.length];
        int[] counts = new int[w.length];
        for (int i = 0; i < w.length; i++) {
            counts[i] = net.spikesThisTick(w[i]);
            rates[i] = net.populationRateHz(w[i], tickMs);
        }
        int logN = net.spikeLogCount();
        int[] logNeurons = new int[logN];
        short[] logSteps = new short[logN];
        for (int k = 0; k < logN; k++) {
            logNeurons[k] = net.spikeLogNeuron(k);
            logSteps[k] = (short) Math.min(Short.MAX_VALUE, net.spikeLogStep(k));
        }
        long spikesTotal = net.totalSpikes();
        BrainSnapshot prev = snapshot;
        long spikesThisTick = spikesTotal - prev.totalSpikes;
        net.endTick(tickMs);
        snapshot = new BrainSnapshot(ticksCompleted + 1, net.simTimeMs(), spikesTotal, spikesThisTick, net.activeNeurons(),
                names, rates, counts, logNeurons, logSteps, realTimeFactor);
        ticksCompleted++;
    }

    @Override
    public void close() {
        running.set(false);
        thread.interrupt();
    }

    /** Immutable per-tick observation published by the brain thread. */
    public static final class BrainSnapshot {
        public final long tick;
        public final double simTimeMs;
        public final long totalSpikes;
        public final long spikesThisTick;
        public final int activeNeurons;
        public final String[] watchedNames;
        public final double[] watchedRatesHz;
        public final int[] watchedCounts;
        public final int[] spikeLogNeurons;
        public final short[] spikeLogSteps;
        public final double realTimeFactor;

        BrainSnapshot(long tick, double simTimeMs, long totalSpikes, long spikesThisTick, int activeNeurons,
                      String[] watchedNames, double[] watchedRatesHz, int[] watchedCounts,
                      int[] spikeLogNeurons, short[] spikeLogSteps, double realTimeFactor) {
            this.tick = tick;
            this.simTimeMs = simTimeMs;
            this.totalSpikes = totalSpikes;
            this.spikesThisTick = spikesThisTick;
            this.activeNeurons = activeNeurons;
            this.watchedNames = watchedNames;
            this.watchedRatesHz = watchedRatesHz;
            this.watchedCounts = watchedCounts;
            this.spikeLogNeurons = spikeLogNeurons;
            this.spikeLogSteps = spikeLogSteps;
            this.realTimeFactor = realTimeFactor;
        }

        static BrainSnapshot empty() {
            return new BrainSnapshot(0, 0, 0, 0, 0, new String[0], new double[0], new int[0], new int[0], new short[0], 1.0);
        }

        /** Rate of a watched population by spec, or 0 if not watched. */
        public double rate(String spec) {
            for (int i = 0; i < watchedNames.length; i++) if (watchedNames[i].equals(spec)) return watchedRatesHz[i];
            return 0;
        }
    }
}
