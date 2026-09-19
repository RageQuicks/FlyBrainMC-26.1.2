package com.fruitfly.brain;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Event-driven leaky integrate-and-fire simulation over a {@link Connectome}.
 *
 * <p>Each neuron is a point LIF unit (see {@link LifConfig}). A presynaptic spike adds
 * {@code sign * synapseCount * wSyn * gain} mV to every postsynaptic target after a fixed delay.
 * Only neurons that are away from rest, refractory, or have pending input are integrated each step ("active set"),
 * so a quiet brain is cheap and cost scales with actual activity rather than network size.</p>
 *
 * <p>Sensory input: {@link #setStimulusRate(int, double)} makes a neuron fire as a Poisson process at the given rate
 * (the neuron becomes a spike generator, as in Shiu et al. 2024); {@link #setInjectedCurrent(int, double)} adds a
 * constant depolarising drive (mV/ms) for graded inputs (e.g. lamina cells in the dark).</p>
 *
 * <p>Not thread-safe: one owner thread steps the network; observers read snapshots produced by the owner.</p>
 */
public final class LifNetwork {
    public final Connectome c;
    public final LifConfig cfg;
    public final int n;

    private final float[] v;
    private final int[] refUntil;
    private final int[] pending;          // pending delayed inputs per neuron (count)
    private final float[][] delayBuf;     // [delaySteps+1][n]
    private final int slots;
    private final int delaySteps;
    private final int refSteps;
    private final float decay;            // exp(-dt/tau)
    private final float vRest, vThr, vReset, idleEps;
    private final float[] preScale;       // per presynaptic neuron: sign * wSyn * gain (* inhGain)
    private final float maxJump;
    private final float[] g;              // synaptic drive variable (mV), null in delta-synapse mode
    private final boolean expSyn;
    private final float synDecay;         // exp(-dt/tauSyn)
    private final float synCoupling;      // exact contribution of g to v over one step
    private float[] inScale;              // per postsynaptic neuron input scaling (null = off)
    private final float[] thrAdd;         // adaptive threshold increment (mV)
    private final float adaptInc, adaptDecay;
    private final boolean adapt;

    private final boolean[] active;
    private int[] activeList;
    private int activeCount;

    private final int nWorkers;
    private final int parallelThreshold;
    private final int[][] spikeBuf;       // per worker spike collection buffers
    private final int[] spikeCount;

    private final float[] stimProb;       // per-step spike probability for Poisson-driven neurons (0 = off)
    private int[] stimList = new int[256];
    private int stimCount;
    private final boolean[] inStim;

    private final float[] inject;         // mV per step
    private int[] injectList = new int[64];
    private int injectCount;
    private final boolean[] inInject;

    private final int[] spikeCountTick;   // spikes since the last tick boundary
    private final float[] rateEma;        // Hz
    private final int[] spikeLogNeuron;
    private final int[] spikeLogStep;
    private int spikeLogCount;
    private long totalSpikes;
    private long tickSpikeBase;            // totalSpikes at the last tick boundary (for reservoir sampling)
    private long stepIndex;
    private long lastTickStep;
    private double simTimeMs;

    private final SplittableRandom rng;

    public LifNetwork(Connectome c, LifConfig cfg) {
        this.c = c;
        this.cfg = cfg.copy();
        this.n = c.n;
        this.v = new float[n];
        this.refUntil = new int[n];
        this.pending = new int[n];
        this.delaySteps = this.cfg.delaySteps();
        this.slots = delaySteps + 1;
        this.delayBuf = new float[slots][n];
        this.refSteps = this.cfg.refractorySteps();
        this.decay = (float) Math.exp(-this.cfg.dtMs / this.cfg.tauMs);
        this.vRest = (float) this.cfg.vRest;
        this.vThr = (float) this.cfg.vThreshold;
        this.vReset = (float) this.cfg.vReset;
        this.idleEps = (float) this.cfg.idleEpsMv;
        this.maxJump = (float) this.cfg.maxJumpMv;
        this.preScale = new float[n];
        for (int i = 0; i < n; i++) {
            int s = c.ntSign[i];
            double scale = s * this.cfg.wSynMv * this.cfg.gain;
            if (s < 0) scale *= this.cfg.inhibitoryGain;
            preScale[i] = (float) scale;
        }
        if (this.cfg.inputNormSynapses > 0) {
            long[] totalIn = new long[n];
            for (int i = 0; i < n; i++) {
                for (int k = c.rowPtr[i]; k < c.rowPtr[i + 1]; k++) totalIn[c.postIdx[k]] += c.weight[k] & 0xFFFF;
            }
            inScale = new float[n];
            double K = this.cfg.inputNormSynapses;
            for (int j = 0; j < n; j++) inScale[j] = (float) (totalIn[j] > K ? K / totalIn[j] : 1.0);
        } else {
            inScale = null;
        }
        this.expSyn = this.cfg.synTauMs > 0;
        this.g = expSyn ? new float[n] : null;
        if (expSyn) {
            double tau = this.cfg.synTauMs, tm = this.cfg.tauMs, dt = this.cfg.dtMs;
            this.synDecay = (float) Math.exp(-dt / tau);
            // exact solution of dv/dt=(v0-v+g)/tm, dg/dt=-g/tau over one step: v += g * coupling
            if (Math.abs(tm - tau) < 1e-9) this.synCoupling = (float) ((dt / tm) * Math.exp(-dt / tm));
            else this.synCoupling = (float) ((tau / (tm - tau)) * (Math.exp(-dt / tm) - Math.exp(-dt / tau)));
        } else {
            this.synDecay = 0f;
            this.synCoupling = 0f;
        }
        this.adapt = this.cfg.adaptIncMv > 0;
        this.thrAdd = adapt ? new float[n] : null;
        this.adaptInc = (float) this.cfg.adaptIncMv;
        this.adaptDecay = (float) Math.exp(-this.cfg.dtMs / Math.max(1e-3, this.cfg.adaptTauMs));
        Arrays.fill(v, vRest);
        this.active = new boolean[n];
        this.activeList = new int[Math.max(1024, n / 16)];
        int threads = this.cfg.threads > 0 ? this.cfg.threads : Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 2));
        this.nWorkers = Math.max(1, threads);
        this.parallelThreshold = Math.max(64, this.cfg.parallelThreshold);
        this.spikeBuf = new int[nWorkers][];
        for (int t = 0; t < nWorkers; t++) spikeBuf[t] = new int[1024];
        this.spikeCount = new int[nWorkers];
        this.stimProb = new float[n];
        this.inStim = new boolean[n];
        this.inject = new float[n];
        this.inInject = new boolean[n];
        this.spikeCountTick = new int[n];
        this.rateEma = new float[n];
        int cap = Math.max(0, this.cfg.spikeLogCapacity);
        this.spikeLogNeuron = new int[cap];
        this.spikeLogStep = new int[cap];
        this.rng = new SplittableRandom(this.cfg.seed);
    }

    // ------------------------------------------------------------------ stimulation

    /** Drive neuron i as a Poisson spike generator at {@code hz} spikes/s (0 removes the drive). */
    public void setStimulusRate(int i, double hz) {
        float p = (float) Math.min(1.0, Math.max(0.0, hz) * cfg.dtMs / 1000.0);
        if (p > 0f) {
            stimProb[i] = p;
            if (!inStim[i]) {
                inStim[i] = true;
                if (stimCount == stimList.length) stimList = Arrays.copyOf(stimList, stimCount * 2);
                stimList[stimCount++] = i;
            }
        } else if (inStim[i]) {
            stimProb[i] = 0f;
            // lazily removed at next compaction
        }
    }

    /** Set the same Poisson rate on every neuron of a population. */
    public void setStimulusRate(int[] neurons, double hz) {
        for (int i : neurons) setStimulusRate(i, hz);
    }

    /** Remove all Poisson drives. */
    public void clearStimuli() {
        for (int k = 0; k < stimCount; k++) {
            int i = stimList[k];
            stimProb[i] = 0f;
            inStim[i] = false;
        }
        stimCount = 0;
    }

    /** Constant depolarising drive in mV per ms (negative allowed = hyperpolarising). 0 removes it. */
    public void setInjectedCurrent(int i, double mvPerMs) {
        float perStep = (float) (mvPerMs * cfg.dtMs);
        if (perStep != 0f) {
            inject[i] = perStep;
            if (!inInject[i]) {
                inInject[i] = true;
                if (injectCount == injectList.length) injectList = Arrays.copyOf(injectList, injectCount * 2);
                injectList[injectCount++] = i;
            }
            activate(i);
        } else if (inInject[i]) {
            inject[i] = 0f;
        }
    }

    public void clearInjectedCurrents() {
        for (int k = 0; k < injectCount; k++) {
            int i = injectList[k];
            inject[i] = 0f;
            inInject[i] = false;
        }
        injectCount = 0;
    }

    /** Force a spike now (e.g. a mechanosensory tap). */
    public void forceSpike(int i) {
        emitSpike(i);
    }

    /**
     * Multiply all synaptic input to the given neurons by {@code gain} (composes with in-degree normalisation).
     * Used for documented per-cell-type corrections such as the high spike threshold of Kenyon cells.
     */
    public void setPostsynapticGain(int[] ids, double gain) {
        if (inScale == null) {
            inScale = new float[n];
            Arrays.fill(inScale, 1f);
        }
        for (int i : ids) inScale[i] *= (float) gain;
    }

    /** Current postsynaptic input scaling of neuron i (1 = literal). */
    public float postsynapticGain(int i) { return inScale == null ? 1f : inScale[i]; }

    // ------------------------------------------------------------------ stepping

    /** Advance one integration step (cfg.dtMs). */
    public void step() {
        final int step = (int) stepIndex;
        final int readSlot = (int) (stepIndex % slots);
        final float[] in = delayBuf[readSlot];

        // Poisson-driven neurons: spike generators
        if (stimCount > 0) {
            int w = 0;
            for (int k = 0; k < stimCount; k++) {
                int i = stimList[k];
                float p = stimProb[i];
                if (p <= 0f) { inStim[i] = false; continue; }
                stimList[w++] = i;
                if (rng.nextFloat() < p) {
                    emitSpike(i);
                    v[i] = vReset;
                    refUntil[i] = step + refSteps;
                    activate(i);
                }
            }
            stimCount = w;
        }
        // injected currents
        if (injectCount > 0) {
            int w = 0;
            for (int k = 0; k < injectCount; k++) {
                int i = injectList[k];
                if (inject[i] == 0f) { inInject[i] = false; continue; }
                injectList[w++] = i;
                if (!active[i]) activate(i);
            }
            injectCount = w;
        }

        // Integrate active neurons. Each neuron's state is touched only by the worker owning its segment of the
        // active list; spikes are collected per worker and delivered afterwards on the calling thread (delivery
        // appends newly activated neurons and may reallocate activeList).
        final int count = activeCount;
        int w;
        if (count >= parallelThreshold && nWorkers > 1) {
            final int T = nWorkers;
            final int chunk = (count + T - 1) / T;
            final int[] newCounts = new int[T];
            java.util.stream.IntStream.range(0, T).parallel().forEach(t -> {
                int s = t * chunk, e = Math.min(count, s + chunk);
                newCounts[t] = e > s ? integrateSegment(s, e, step, in, t) - s : 0;
            });
            w = 0;
            for (int t = 0; t < T; t++) {
                int s = t * chunk, c = newCounts[t];
                if (c > 0 && s != w) System.arraycopy(activeList, s, activeList, w, c);
                w += c;
            }
            activeCount = w;
            for (int t = 0; t < T; t++) {
                int[] sb = spikeBuf[t];
                for (int k = 0, n = spikeCount[t]; k < n; k++) emitSpike(sb[k]);
                spikeCount[t] = 0;
            }
        } else {
            w = integrateSegment(0, count, step, in, 0);
            activeCount = w;
            int[] sb = spikeBuf[0];
            for (int k = 0, n = spikeCount[0]; k < n; k++) emitSpike(sb[k]);
            spikeCount[0] = 0;
        }
        stepIndex++;
        simTimeMs += cfg.dtMs;
    }

    /**
     * Integrate neurons activeList[s..e), compacting survivors in place from index s; spikes are appended to
     * spikeBuf[worker]. Returns the index one past the last survivor.
     */
    private int integrateSegment(final int s, final int e, final int step, final float[] in, final int worker) {
        int[] sb = spikeBuf[worker];
        int sc = 0;
        int w = s;
        final int[] list = activeList;
        for (int k = s; k < e; k++) {
            final int i = list[k];
            float input = in[i];
            if (input != 0f) {
                in[i] = 0f;
                pending[i]--;
            }
            float vi = v[i];
            boolean refractory = step < refUntil[i];
            float ta = 0f;
            if (adapt) {
                ta = thrAdd[i] * adaptDecay;
                thrAdd[i] = ta;
            }
            float gi = 0f;
            boolean spiked = false;
            if (expSyn) {
                // Shiu et al. 2024 formulation: inputs add to g; g drives v and decays; both frozen while refractory
                gi = g[i] + input;
                if (refractory) {
                    vi = vReset;
                } else {
                    vi = vRest + (vi - vRest) * decay + gi * synCoupling + inject[i];
                    gi *= synDecay;
                    if (vi >= vThr + ta) {
                        spiked = true;
                        vi = vReset;
                        gi = 0f;
                    }
                }
                g[i] = gi;
            } else if (refractory) {
                vi = vReset; // clamp during refractory period; inputs arriving now are dropped
            } else {
                vi = vRest + (vi - vRest) * decay + input + inject[i];
                if (vi >= vThr + ta) {
                    spiked = true;
                    vi = vReset;
                }
            }
            if (spiked) {
                refUntil[i] = step + refSteps;
                refractory = true;
                if (adapt) thrAdd[i] = ta + adaptInc;
                if (sc == sb.length) sb = spikeBuf[worker] = Arrays.copyOf(sb, sc * 2);
                sb[sc++] = i;
            }
            v[i] = vi;
            boolean keep = refractory || pending[i] > 0 || inject[i] != 0f || Math.abs(vi - vRest) > idleEps
                    || Math.abs(gi) > idleEps || (adapt && thrAdd[i] > idleEps);
            if (keep) {
                list[w++] = i;
            } else {
                v[i] = vRest;
                active[i] = false;
            }
        }
        spikeCount[worker] = sc;
        return w;
    }

    /** Run for approximately {@code ms} milliseconds of simulated time. */
    public void runMs(double ms) {
        int steps = (int) Math.round(ms / cfg.dtMs);
        for (int s = 0; s < steps; s++) step();
    }

    private void activate(int i) {
        if (!active[i]) {
            active[i] = true;
            if (activeCount == activeList.length) activeList = Arrays.copyOf(activeList, activeCount * 2);
            activeList[activeCount++] = i;
        }
    }

    private void emitSpike(int i) {
        totalSpikes++;
        spikeCountTick[i]++;
        // reservoir sample of this tick's spikes (uniform over the whole tick, not just the first spikes)
        final int logCap = spikeLogNeuron.length;
        if (logCap > 0) {
            if (spikeLogCount < logCap) {
                spikeLogNeuron[spikeLogCount] = i;
                spikeLogStep[spikeLogCount] = (int) (stepIndex - lastTickStep);
                spikeLogCount++;
            } else {
                long seen = totalSpikes - tickSpikeBase; // spikes so far this tick, including this one
                long j = rng.nextLong(seen);
                if (j < logCap) {
                    spikeLogNeuron[(int) j] = i;
                    spikeLogStep[(int) j] = (int) (stepIndex - lastTickStep);
                }
            }
        }
        final float scale = preScale[i];
        if (scale == 0f) return; // neuromodulatory / unknown transmitter: no fast synaptic effect
        final int writeSlot = (int) ((stepIndex + delaySteps) % slots);
        final float[] out = delayBuf[writeSlot];
        final int[] post = c.postIdx;
        final short[] wgt = c.weight;
        final int a = c.rowPtr[i], b = c.rowPtr[i + 1];
        final float cap = maxJump;
        final float[] norm = inScale;
        for (int k = a; k < b; k++) {
            final int j = post[k];
            float jump = scale * (wgt[k] & 0xFFFF);
            if (norm != null) jump *= norm[j];
            if (cap > 0f) {
                if (jump > cap) jump = cap; else if (jump < -cap) jump = -cap;
            }
            if (out[j] == 0f) pending[j]++;
            out[j] += jump;
            if (out[j] == 0f) pending[j]--; // exact cancellation: keep counter consistent
            if (!active[j]) activate(j);
        }
    }

    // ------------------------------------------------------------------ observation

    /** Number of spikes of neuron i since the last {@link #endTick(double)}. */
    public int spikesThisTick(int i) { return spikeCountTick[i]; }

    /** Smoothed firing rate (Hz) of neuron i, updated at tick boundaries. */
    public float rateHz(int i) { return rateEma[i]; }

    public float membrane(int i) { return v[i]; }
    public int activeNeurons() { return activeCount; }
    public long totalSpikes() { return totalSpikes; }
    public long stepIndex() { return stepIndex; }
    public double simTimeMs() { return simTimeMs; }
    public int spikeLogCount() { return spikeLogCount; }
    public int spikeLogNeuron(int k) { return spikeLogNeuron[k]; }
    /** Step offset (from tick start) of the k-th logged spike. */
    public int spikeLogStep(int k) { return spikeLogStep[k]; }

    /** Sum of spikes this tick over a population. */
    public int spikesThisTick(int[] pop) {
        int s = 0;
        for (int i : pop) s += spikeCountTick[i];
        return s;
    }

    /** Mean firing rate (Hz) of a population over the elapsed tick window of {@code windowMs}. */
    public double populationRateHz(int[] pop, double windowMs) {
        if (pop.length == 0 || windowMs <= 0) return 0;
        return spikesThisTick(pop) * 1000.0 / (pop.length * windowMs);
    }

    /** Mean smoothed rate (Hz) of a population. */
    public double populationEmaHz(int[] pop) {
        if (pop.length == 0) return 0;
        double s = 0;
        for (int i : pop) s += rateEma[i];
        return s / pop.length;
    }

    /**
     * Close the current observation window: update EMA rates from the spike counts of the elapsed {@code tickMs},
     * then reset the per-tick counters and the spike log. Call once per game tick after stepping.
     */
    public void endTick(double tickMs) {
        double alpha = 1.0 - Math.exp(-tickMs / cfg.rateEmaMs);
        float a = (float) alpha, b = 1f - a;
        float toHz = (float) (1000.0 / tickMs);
        // only neurons that spiked have non-zero counts; but EMA must decay for all -> vectorised loop
        for (int i = 0; i < n; i++) {
            float r = rateEma[i] * b;
            int s = spikeCountTick[i];
            if (s != 0) {
                r += a * s * toHz;
                spikeCountTick[i] = 0;
            }
            rateEma[i] = r;
        }
        spikeLogCount = 0;
        lastTickStep = stepIndex;
        tickSpikeBase = totalSpikes;
    }

    /** Reset all state (membranes, pending input, counters), keeping stimuli configuration cleared. */
    public void reset() {
        Arrays.fill(v, vRest);
        Arrays.fill(refUntil, 0);
        Arrays.fill(pending, 0);
        for (float[] s : delayBuf) Arrays.fill(s, 0f);
        Arrays.fill(active, false);
        if (thrAdd != null) Arrays.fill(thrAdd, 0f);
        if (g != null) Arrays.fill(g, 0f);
        activeCount = 0;
        clearStimuli();
        clearInjectedCurrents();
        Arrays.fill(spikeCountTick, 0);
        Arrays.fill(rateEma, 0f);
        spikeLogCount = 0;
        totalSpikes = 0;
        tickSpikeBase = 0;
        stepIndex = 0;
        lastTickStep = 0;
        simTimeMs = 0;
    }
}
