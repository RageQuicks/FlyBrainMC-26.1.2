package com.fruitfly.brain;

/**
 * Leaky integrate-and-fire parameters. Defaults follow the connectome-constrained whole-brain model of
 * Shiu et al. 2024 (Nature, "A Drosophila computational brain model reveals sensorimotor processing"):
 * every neuron is an identical LIF unit, each synapse contributes a fixed voltage jump, sign by neurotransmitter.
 *
 * All voltages in mV, times in ms.
 */
public final class LifConfig {
    /** Integration step (ms). */
    public double dtMs = 0.1;
    /** Membrane time constant (ms). */
    public double tauMs = 20.0;
    /** Resting / leak potential (mV). */
    public double vRest = -52.0;
    /** Spike threshold (mV). */
    public double vThreshold = -45.0;
    /** Reset potential after a spike (mV). */
    public double vReset = -52.0;
    /** Absolute refractory period (ms). */
    public double refractoryMs = 2.2;
    /** Synaptic transmission delay (ms). */
    public double delayMs = 1.8;
    /**
     * Synaptic current time constant (ms). With {@code > 0} each presynaptic spike adds its weight to a synaptic
     * variable g that decays exponentially and drives dv/dt = (vRest - v + g)/tau (exactly the Shiu et al. 2024
     * formulation, tau_syn = 5 ms). With 0 the weight is added to v instantaneously (delta synapse).
     */
    public double synTauMs = 5.0;
    /** Voltage jump per single synapse (mV). Total jump of a connection = wSyn * synapseCount * sign * gain. */
    public double wSynMv = 0.275;
    /**
     * Global gain multiplier on all synaptic weights. 1.0 = Shiu's literal 0.275 mV/synapse for FlyWire. The male
     * CNS graph has ~1.5× more synapses per neuron (90 M synapses in the ≥5 graph over 176 k neurons vs 52.8 M over
     * 127 k), so the weight is recalibrated by the paper's own recipe: the smallest gain at which 100 Hz sugar-GRN
     * input drives MN9 while grooming (JO → aDN1/2) and escape (LC4/LPLC2 → GF) stay stable and Kenyon cells silent.
     */
    public double gain = 0.65;
    /** Extra multiplier applied to inhibitory connections only (1.0 = symmetric). */
    public double inhibitoryGain = 1.0;
    /** Clamp on the total jump a single presynaptic spike can cause at one target (mV); 0 disables. */
    public double maxJumpMv = 0.0;
    /**
     * Spike-frequency adaptation: each spike raises the neuron's threshold by this much (mV); the increment decays with
     * {@link #adaptTauMs}. 0 disables. Biologically ubiquitous; prevents the refractory-limited 450 Hz saturation of a
     * pure LIF and keeps population activity sparse.
     */
    public double adaptIncMv = 0.0;
    /** Decay time constant (ms) of the adaptive threshold increment. */
    public double adaptTauMs = 100.0;
    /**
     * In-degree normalisation: inputs to a neuron whose total incoming synapse count exceeds this value are scaled by
     * (this / total), approximating the lower input resistance of large neurons. 0 disables (literal synapse counts).
     */
    public double inputNormSynapses = 0.0;
    /** Neurons whose membrane potential is within this distance of rest and have no pending input go idle. */
    public double idleEpsMv = 0.02;
    /** Time constant (ms) of the per-neuron exponential moving average firing rate exposed to observers. */
    public double rateEmaMs = 200.0;
    /** Max spikes recorded per tick into the spike log for visualisation (0 disables the log). */
    public int spikeLogCapacity = 8192;
    /** Random seed for stimulation Poisson processes. */
    public long seed = 20260903L;
    /** Worker threads for the integration loop (0 = auto: min(8, cores − 2)). */
    public int threads = 0;
    /** Parallelise integration only when at least this many neurons are active. */
    public int parallelThreshold = 4000;

    public LifConfig() {}

    public LifConfig copy() {
        LifConfig c = new LifConfig();
        c.dtMs = dtMs;
        c.tauMs = tauMs;
        c.vRest = vRest;
        c.vThreshold = vThreshold;
        c.vReset = vReset;
        c.refractoryMs = refractoryMs;
        c.delayMs = delayMs;
        c.synTauMs = synTauMs;
        c.wSynMv = wSynMv;
        c.gain = gain;
        c.inhibitoryGain = inhibitoryGain;
        c.maxJumpMv = maxJumpMv;
        c.adaptIncMv = adaptIncMv;
        c.adaptTauMs = adaptTauMs;
        c.inputNormSynapses = inputNormSynapses;
        c.idleEpsMv = idleEpsMv;
        c.rateEmaMs = rateEmaMs;
        c.spikeLogCapacity = spikeLogCapacity;
        c.seed = seed;
        c.threads = threads;
        c.parallelThreshold = parallelThreshold;
        return c;
    }

    public int refractorySteps() { return Math.max(1, (int) Math.round(refractoryMs / dtMs)); }
    public int delaySteps() { return Math.max(1, (int) Math.round(delayMs / dtMs)); }

    @Override
    public String toString() {
        return "LifConfig{dt=" + dtMs + "ms, tau=" + tauMs + ", vRest=" + vRest + ", vThr=" + vThreshold + ", vReset=" + vReset
                + ", refr=" + refractoryMs + ", delay=" + delayMs + ", synTau=" + synTauMs + ", wSyn=" + wSynMv + "mV, gain=" + gain
                + ", inhGain=" + inhibitoryGain + ", maxJump=" + maxJumpMv + ", adapt=" + adaptIncMv + "mV/" + adaptTauMs
                + "ms, inNorm=" + inputNormSynapses + ", idleEps=" + idleEpsMv + "}";
    }
}
