# Connectome-constrained LIF brain model: exact specification for the Java port

Research report, 2026-09-03. Confidence tags: **[H]** verified against primary source (code, paper text, or my own measurement); **[M]** from a secondary summary or a single source I could not fully cross-check; **[L]** inference / recommendation.

Local evidence files (all in this directory): `model.py`, `utils.py`, `Readme.md`, `example.ipynb`, `figures.ipynb`, `environment.yml` (Shiu repo, downloaded verbatim); `connectivity_630.parquet` + `completeness_630.csv` (Shiu v630 data, 86 MB); `pmc.txt` (Shiu 2024 Nature full text from PMC); `escape_feeding.txt` (Chen & Xi 2025 preprint); `loihi.txt` (Wang et al. 2025); `malecns.txt` (Berg et al. male-CNS preprint, 85 pp); `eon_bench.csv` (fly-brain benchmark).

---

## 1. TL;DR

* The published whole-brain model (Shiu et al. 2024, Nature 634:210-219) is a **current-based LIF with an exponential ("alpha"-style two-time-constant) synapse**, one free parameter `w_syn = 0.275 mV per synapse`, and **no synapse-count threshold at all** (I checked the shipped parquet: minimum `Connectivity` = 1; 14,687,178 connections, 52,793,639 synapses among 127,400 neurons). **[H]**
* Sign rule: a neuron is inhibitory if >50 % of its presynapses (cleft score >= 50) are predicted GABA or glutamate; everything else (ACh, dopamine, octopamine, serotonin) is excitatory; whole-neuron sign (Dale's law); |E| = |I| per synapse. **[H]**
* Sensory drive = Poisson spike trains; stimulated neurons get a 68.75 mV kick per event (`w_syn*f_poi`, `f_poi=250`) and refractory period 0, so they fire exactly at the Poisson rate. Rates used: sugar GRNs 10-200 Hz, water 20-260 Hz, JONs 20-220 Hz, bitter/Ir94e 0-200 Hz. **[H]**
* Simulation: Brian2 (v2.5.1 in `environment.yml`), default `dt = 0.1 ms` (verified in Brian2 source and at runtime), exact/`linear` integrator, 30 trials x 1000 ms per condition, ~5 min per trial per CPU thread. Activity is extremely sparse (~0.3 % of neurons fire; ~400 neurons for sugar). **[H]**
* Readout: MN9 (proboscis rostrum-lifting motor neuron) firing rate; "activated" = >0 Hz; "required" = silencing drops MN9 below 80 % of control. 91 % of 164 testable predictions matched experiments (84 % excluding the optogenetic screen). Failures: Phantom (predicted inhibitory, experimentally drives PER), Usnea (neuropeptidergic), G2N-1 water silencing. **[H]**
* Follow-ups that reuse the exact same equations/parameters: Sapkal 2024 (walking/halting), Chen & Xi Dec-2025 (looming escape LC4/LPLC2 -> DNp01/DNp04/DNa02 + feeding suppression, FlyWire v783), Wang et al. 2025 (Loihi 2 port), eonsystems fly-brain (Brian2/CUDA/PyTorch/NEST/GeNN benchmarks). No published LIF simulation of the whole male CNS or BANC was found as of 2026-09-03; the closest are Ceballos 2026 (MANC, conductance-based LIF) and Pugliese 2025 (MANC/FANC rate model). **[M]**
* For the Java port: use the exact-integration recurrence (coefficients below; matches Brian2 to 3.5e-13 mV), integer signed synapse counts x one float scale, a 1.8 ms delay ring buffer, and expect sparse event-driven work. Treat histamine as inhibitory with a graded (non-spiking) photoreceptor front end plus tonic bias on lamina cells; make monoamine sign a config flag (default +1 to match Shiu). **[L/H mix, see section 8]**

---

## 2. Primary target: Shiu et al. 2024

### 2.1 Citation and provenance **[H]**

Shiu PK, Sterne GR, Spiller N, Blagburn JM, ... Scott K. *A Drosophila computational brain model reveals sensorimotor processing.* **Nature 634 (8032): 210-219 (2 Oct 2024).** doi:10.1038/s41586-024-07763-9. PMC11446845. bioRxiv preprint 2023.05.02.539144 (title: "A leaky integrate-and-fire computational model based on the connectome of the entire adult Drosophila brain reveals insights into sensorimotor processing").
Code: https://github.com/philshiu/Drosophila_brain_model (MIT). Raw outputs: doi:10.17617/3.CZODIW.

### 2.2 Model equations (verbatim from Methods and `model.py`) **[H]**

Paper (Methods, "Computational model"):

```
dv_i/dt = ( g_i - (v_i - V_resting) ) / T_mbr
dg_i/dt = - g_i / tau
g_i <- g_i + w_{j,i}        upon spike from neuron j   (after delay T_dly)
w_{j,i} = (synapse count j->i) * (+1 if j excitatory, -1 if inhibitory) * W_syn
spike when v_i > V_threshold ; then v_i = V_reset, g_i = 0, refractory for T_refractory
```

`model.py` (`default_params`), verbatim:

```python
't_run'  : 1000 * ms,   'n_run' : 30,
# Kakaria and de Bivort 2017 https://doi.org/10.3389/fnbeh.2017.00008
'v_0'    : -52 * mV,    # resting potential
'v_rst'  : -52 * mV,    # reset potential after spike
'v_th'   : -45 * mV,    # threshold for spiking
't_mbr'  :  20 * ms,    # membrane time scale (capacitance * resistance = .002 * uF * 10. * Mohm)
# Jürgensen et al https://doi.org/10.1088/2634-4386/ac3ba6
'tau'    : 5 * ms,      # time constant
# Lazar et al https://doi.org/10.7554/eLife.62362
't_rfc'  : 2.2 * ms,    # refractory period
# Paul et al 2015 doi: 10.3389/fncel.2015.00029
't_dly'  : 1.8*ms,      # delay for changes in post-synaptic neuron
# Free parameter
'w_syn'  : .275 * mV,   # weight per synapse (note: modulated by exponential decay)
'r_poi'  : 150*Hz,      # default rate of the Poisson inputs
'r_poi2' :   0*Hz,      # default rate of a 2nd class of Poisson inputs
'f_poi'  : 250,         # scaling factor for Poisson synapse; 250 is sufficient to cause spiking
'eqs'    : '''
            dv/dt = (v_0 - v + g) / t_mbr : volt (unless refractory)
            dg/dt = -g / tau               : volt (unless refractory)
            rfc                            : second
           ''',
'eq_th'  : 'v > v_th',
'eq_rst' : 'v = v_rst; w = 0; g = 0 * mV',
```

Network construction (verbatim essentials):

```python
neu = NeuronGroup(N=len(df_comp), model=eqs, method='linear', threshold='v > v_th',
                  reset=eq_rst, refractory='rfc', namespace=params)
neu.v = v_0; neu.g = 0; neu.rfc = t_rfc
syn = Synapses(neu, neu, 'w : volt', on_pre='g += w', delay=t_dly)
syn.connect(i=df_con['Presynaptic_Index'], j=df_con['Postsynaptic_Index'])
syn.w = df_con['Excitatory x Connectivity'].values * w_syn
# stimulation:
PoissonInput(target=neu[i], target_var='v', N=1, rate=r_poi, weight=w_syn*f_poi); neu[i].rfc = 0*ms
# silencing: syn.w['i == k'] = 0*mV  (all OUTPUT weights of neuron k set to 0)
```

Notes verified by running Brian2 2.6.0 locally on these exact strings **[H]**:
* `defaultclock.dt` = 100 us = **0.1 ms** (also `Clock(dt=0.1*ms, name="defaultclock")` in `brian2/devices/device.py`). The model never overrides dt.
* `method='linear'` is Brian2's exact integrator for linear ODEs (alias `'exact'`; Rotter & Diesmann 1999 matrix-exponential update).
* The reset string contains `w = 0`, which is not a neuron variable; Brian2 accepts it without error (it is effectively a no-op). Ignore it in the port.
* Note the `(unless refractory)` flag also freezes `g` during the 2.2 ms refractory period, and `g` is zeroed at every spike (so a spike discards pending synaptic drive). Reproduce both if you want spike-for-spike parity.
* Poisson-driven neurons: each event adds 68.75 mV to `v` (threshold is only 7 mV above rest), so every event produces a spike; with `rfc = 0` the neuron follows the Poisson process at exactly `r_poi`. These neurons still receive network input.

### 2.3 Parameter provenance table **[H]** (Methods text + code comments)

| Symbol | Value | Source cited by Shiu |
|---|---|---|
| V_resting | -52 mV | Kakaria & de Bivort 2017 (Front. Behav. Neurosci. 11:8), citing Rohrbough & Broadie 2002, Sheeba 2008 |
| V_reset | -52 mV | Kakaria & de Bivort 2017 |
| V_threshold | -45 mV | Kakaria & de Bivort 2017 (Sheeba 2008; Gouwens & Wilson 2009) |
| R_mbr | 10 kOhm cm^2 (code: 10 MOhm) | Kakaria & de Bivort 2017 |
| C_mbr | 2 uF cm^-2 (code: 0.002 uF) | Kakaria & de Bivort 2017 |
| T_mbr = R*C | 20 ms | derived |
| tau (synaptic decay) | 5 ms | Jürgensen et al. 2021, Neuromorph. Comput. Eng. 1:024008 (doi 10.1088/2634-4386/ac3ba6); Kakaria used 5 ms PSC half-life from Gaudry 2013 |
| T_refractory | 2.2 ms | Lazar et al. 2021 eLife 10:e62362 (FlyBrainLab; "refractory period of 2.2 ms as suggested in Kakaria & de Bivort 2017") |
| T_dly | 1.8 ms | Paul et al. 2015 Front. Cell. Neurosci. 9:29 (Bruchpilot/Synaptotagmin; NMJ EPSC delay) |
| W_syn | 0.275 mV | **free parameter**, chosen so that 100 Hz sugar-GRN activation gives ~80 % of maximal MN9 firing |
| dt | 0.1 ms | Brian2 default (not stated in paper) |
| Trials | 30 x 1000 ms | Methods |

Kakaria & de Bivort's own model differs in detail (V_min -72 mV undershoot, PSC 5 nA, 20 PSCs/spike, Euler dt 1e-4 s, 5 Hz background Poisson) **[M, from article fetch]**; Shiu took only the potentials and R, C from it.

### 2.4 Connectivity data actually used **[H]** (measured on the shipped v630 parquet)

* `2023_03_23_completeness_630_final.csv`: 127,400 rows (FlyWire root IDs, all `Completed=True`); row order = Brian index.
* `2023_03_23_connectivity_630_final.parquet` columns: `Presynaptic_ID, Postsynaptic_ID, Presynaptic_Index, Postsynaptic_Index, Connectivity, Excitatory, Excitatory x Connectivity`.
* 14,687,178 connections; `Connectivity` (synapse count) min **1**, max 2358, mean 3.6; total 52,793,639 synapses ("50 million"). **There is no synapse-count threshold.** Counts by threshold: >=2 keeps 50.3 % of connections / 86.2 % of synapses; >=3: 32.5 % / 76.3 %; **>=5: 17.8 % (2,614,028) / 62.5 %**; >=10: 7.0 % / 43.2 %. (Lin et al. 2024 report 2,613,129 connections at >=5 for v630 — consistent.)
* `Excitatory` is per presynaptic neuron: +1 for 8,800,532 rows, -1 for 5,886,646 rows (59.6 % of synapses excitatory). 64.2 % of neurons receive more excitatory than inhibitory synapses.
* In-degree (connections): median 73, p99 677, max 10,196. Incoming excitatory synapses per neuron: median 112, p99 2,322, max 65,070; inhibitory: median 93, p99 1,384, max 15,242.
* v783 files (`Connectivity_783.parquet`, `Completeness_783.csv`) are also in the repo; the README says the paper used v630. FlyWire v783 has 139,255 neurons (Lin 2024; Berkeley news); Wang et al. quote "138K" neurons for their v783 port. Integer weights in v783 span -2405..+1897 (Wang et al.).
* FlyWire predictions come from Eckstein et al. 2024; per-connection columns in the public release are `gaba_avg, ach_avg, glut_avg, oct_avg, ser_avg, da_avg` (Zenodo 10676866), and Codex `connections.csv` is thresholded at >=5 synapses (Lin 2024 rationale: no synapse-level proofreading) — Shiu did **not** use that thresholded table.

### 2.5 Neurotransmitter -> sign rule **[H]** (Methods, "Neurotransmitter predictions")

> "We assume GABAergic and glutamatergic neurons are inhibitory, and that each neuron is either exclusively inhibitory or excitatory. As in ref. 67, we used a cleft score cutoff of 50, and identified the highest neurotransmitter prediction for each presynaptic site and, if greater than half of all the presynaptic sites across the entire neuron are predicted to be inhibitory (GABA or Glut), we assigned this neuron as inhibitory. Neurons predicted to be dopaminergic, octopaminergic or serotonergic are assigned to the excitatory category."

FlyWire composition: ~55 % cholinergic, 24 % glutamatergic, 14 % GABAergic, 7 % DA/OA/5-HT. Among 613 taste-responsive neurons: 52 % ACh, 25.9 % GABA, 17 % Glu, 2.9 % 5-HT, 2.0 % DA, 0.2 % OA.
Robustness tests (Supp. Table 11): W_syn -30 %: predictions 90.2 % consistent with default, accuracy 85 % (water->MN9 notably weakened); W_syn +30 %: 95 % consistent, 88 % accurate. Inhibitory:excitatory ratio +/-50 %: 95-96 % consistent, 88-89 % accurate. **Glutamate excitatory** instead: bitter/Ir94e inhibition of MN9 disappears and optogenetic-screen false-positive rate rises from 1 % to 16 % — so keep glutamate inhibitory.

### 2.6 Stimulation protocol and readouts **[H]**

* GRN IDs (from `figures.ipynb`): right-hemisphere labellar sugar GRNs n=21; water n=18; bitter n=21; Ir94e n=18; left-hemisphere sugar n=10. (Paper uses "left hemisphere" = true biological side after the FAFB left-right inversion; notebook labels are FlyWire-frame.) 147 JONs (JO-C, JO-E, JO-F, JO-mz) for grooming.
* Rates: sugar 10-200 Hz (steps of 10), water 20-260 Hz, JON 20-220 Hz, bitter/Ir94e co-activation grid 0-200 Hz x 0-200 Hz using `r_poi2`.
* Screening loop: record all spikes over 30 x 1 s; average rate per neuron; take top-200 responders (top-300 for JON); activate each alone at 25-200 Hz and read MN9 (or aDN1/aDN2); silence each one during GRN activation (50-120 Hz) and read MN9.
* Definitions: activated neuron = firing >0 Hz; "required" = silencing gives MN9 < 80 % of unsilenced control; MN9 referenced is contralateral to activated GRNs (MN9 FlyWire ID 720575940660219265 in notebooks; aDN1 720575940616185531, aDN2 720575940629806974).
* Typical output: sugar at 200 Hz -> >400,000 spikes over 30 trials, ~400 neurons active (example notebook); Wang et al.: 0.3 % of 138k neurons active, ~30 Hz mean among active, 0.1 Hz network-wide.

### 2.7 Results reproduced and failures **[H]**

* Sugar and water GRN activation predicts known second-order (G2N-1, FMIn, Fudog, Phantom, Usnea, Zorro), pre-motor (Roundup, Scapula...) and MN9 responses; predicted MN9 contralateral bias for unilateral input.
* Optogenetic screen of 106/138 SEZ split-GAL4 cell types: at 50 Hz, 11 types predicted to activate MN9, 10/11 do; of 95 predicted negative, 4 false negatives; at 200 Hz five more false positives; overall >90 % accuracy.
* Bitter and Ir94e GRN activation predicted to inhibit MN9 -> validated (Ir94e was a new prediction). Quantitative mismatch: model needed higher bitter rates than experiment suggests.
* Water: Fudog and Zorro predicted responders confirmed by calcium imaging; 5/6 predicted water-silencing phenotypes confirmed; G2N-1 failed; Usnea had an unpredicted phenotype.
* Grooming: JON activation -> aBN1, aBN2, aDN1, aDN2; subtype-specific responses (JO-C/E vs JO-F) reproduced; silencing three predicted inhibitory neurons tested in Supp. Fig 5.
* Sapkal et al. 2024 used the model for locomotion/halting (section 4).
* Failures/limits (verbatim gist): basal firing assumed 0 Hz, so "inhibitory connections to an inactive neuron have no effect" (Phantom -> Scapula -> Roundup disinhibition cannot be expressed); neuromodulation/neuropeptides ignored (Usnea shown neuropeptidergic via Amontillado RNAi); no morphology, receptor dynamics, gap junctions; DA/5-HT neurons "modelled less well"; absolute rates not trusted — interpret differences across input-rate sweeps. Descending pathways were avoided in 2024 because DNs are truncated in FlyWire (a reason the male CNS is better for our motor decoding).
* Overall: 164 testable predictions, 91 % consistent; 84 % excluding the split-GAL4 screen.

### 2.8 Runtime **[H]**

* Paper: "approximately 5 min per 1,000 ms trial per CPU thread" (numpy/Cython Brian2, 127k neurons, 14.7M synapses). Colab: ~20 min for one 30-trial experiment.
* Wang et al. 2025 Table 1 (Brian2 reference, sugar experiment, v783): 4.42 +/- 0.24 s wall per 1 s simulated; with imposed background firing 0.5-40 Hz: 10.1-14.0 s. Loihi 2: 54 ms (0.1 ms dt) / 12 ms (1 ms dt) per simulated second at sparse activity.
* eonsystems `fly-brain` benchmark (Brian2 C++ standalone, FlyWire v783, 200 Hz sugar GRNs, hardware not stated, RTX 4070 mentioned for GPU builds): 1 trial x 1 s: sim 2.66 s (0.38x realtime), 100 s: 269 s; 32 parallel trials x 100 s: 3977 s. Brian2CUDA was *slower* (0.28x realtime) because activity is too sparse for GPU benefit; the README says NEST GPU/GeNN are faster. Active neurons 380-497.

---

## 3. Derived numbers for the port (my own computation, cross-checked against Brian2) **[H]**

Let u = v - V_rest. Single presynaptic spike through N synapses: g jumps by W = N*0.275 mV, then
`u(t) = W * tau/(T_mbr - tau) * (exp(-t/T_mbr) - exp(-t/tau))`.

* Peak PSP per synapse: **0.04331 mV at 9.24 ms** (Brian2 `linear`: 0.04331 mV at 9.2 ms). Area = W*tau = 1.375 mV*ms per synapse.
* Simultaneous synapses needed to reach the 7 mV threshold from rest: **~162**. A 20-synapse connection gives a 0.87 mV PSP; a 100-synapse connection 4.3 mV.
* Steady mean depolarisation from a presynaptic cell firing at r Hz through N synapses: `dV_mean = N * 0.275 mV * 5 ms * r` (e.g. 20 syn x 100 Hz = 2.75 mV; 50 syn x 200 Hz = 13.75 mV, i.e. suprathreshold).
* Stability envelope of the v630 graph: if every excitatory input of a neuron fired at 1 Hz, median depolarisation 0.15 mV, p99 3.2 mV, 0.2 % of neurons above threshold; at 5 Hz: p99 16 mV, 4.1 % above threshold; at 20 Hz: 19.9 % above threshold. So global background firing above a few Hz is not safe without inhibition being active; the model is only stable because baseline is 0 and inputs are localized.

Exact-integration recurrence (what Brian2 `linear` computes), per step dt:

```
a = exp(-dt/T_mbr);  b = exp(-dt/tau);  c = (tau/(T_mbr - tau)) * (a - b)
u_{n+1} = a*u_n + c*g_n        (u = v - V_rest)
g_{n+1} = b*g_n  (+ arriving weights whose delay expired)
dt = 0.1 ms: a = 0.99501248, b = 0.98019867, c = 0.00493794   (max |diff| vs Brian2 = 3.5e-13 mV)
dt = 1.0 ms: a = 0.95122942, b = 0.81873075, c = 0.04416622
```

Order of operations to mirror Brian2: (1) integrate u,g one step for non-refractory neurons; (2) threshold `u > 7 mV`; (3) reset u=0, g=0, start 2.2 ms refractory (22 steps at 0.1 ms); (4) deliver spikes to a delay ring buffer of 18 steps (1.8 ms), adding `sign*count*W_syn` into the target's g when the delay expires; (5) Poisson inputs add 68.75 mV to u of stimulated neurons (which have rfc = 0).

---

## 4. Follow-up and related models (2024-2026)

### 4.1 Sapkal et al. 2024 (walking / halting) **[H]**
Sapkal N, ..., Shiu PK, ..., Bidaye SS. *Neural circuit mechanisms underlying context-specific halting in Drosophila.* Nature 634:191-200 (2024), doi:10.1038/s41586-024-07854-7. Uses the Shiu model unchanged. Computationally activated walking DNs P9 (DNp09; turning), BPN (bolt protocerebral neuron; forward walking), MDN (backward), BDN2 (=DNg100; forward velocity), oDN1; halt neurons FG (Foxglove, GABAergic), BB (Bluebell = DNg60, GABAergic), BRK (Brake, cholinergic, recruits VNC inhibition). Readout = firing of oDN1/BDN2 and top-100 responders. Predictions: BB broadly suppresses P9 pathway; FG specifically inhibits oDN1 and BDN2 ("walk-OFF" during feeding); BRK engaged for grooming. BB's modelled effect was weaker than behaviour. Relevant to our decoder: BDN2/DNg100 and DNp09/MDN as walking readouts.

### 4.2 Chen & Xi, bioRxiv 16 Dec 2025 (looming escape + feeding suppression, whole brain) **[H]**
Chen W-Q, Xi W. *Whole-brain connectomics of Drosophila reveals a robust, distributed architecture for the suppression of feeding during escape.* doi:10.64898/2025.12.14.694122. FlyWire v783 (they still write "127,400 neurons, ~50 million connections"), Brian2, **identical parameters** (-52/-52/-45 mV, 20 ms, tau 5 ms, 2.2 ms, 1.8 ms, W_syn 0.275 mV, w = N_syn*S*W_syn). Looming = Poisson trains into LC4 and LPLC2 at up to 150 Hz (activating 70-100 % of the population); results: DNp01 (Giant Fiber) >150 Hz, DNp04 and DNp02 >100 Hz, contralateral DNa02 >20 Hz with ipsilateral DNa02 silent; DNp01/DNp04 latency ~5 ms, DNa02 ~22 ms; sugar GRNs at 150 Hz drive MN9 >80 Hz, suppressed below 30 Hz by escape input via DNge031 (descending) and CB0565 neurons inhibiting Roundup ("disfacilitation"); grooming suppressed at aDN while aBN1 stays active. Silencing = zeroing outgoing weights. Useful template for our vision -> escape decoding (LC4/LPLC2 -> DNp01 -> takeoff; DNa02 asymmetry -> turn direction).

### 4.3 Ceballos et al. 2026, iScience (MANC, axo-axonic inputs to DNs) **[M]** (from bioRxiv v2 full text; iScience page blocked)
*The Drosophila connectome reveals axo-axonic synapses on descending neurons (that modulate the Giant Fiber system).* bioRxiv 2025.09.04.674108; iScience 2026, S2589-0042(26)00999-5; PubMed 42063566. MANC v1.2.1: 23,437 neurons, 1,152,548 connections (619,684 cholinergic excitatory; 532,864 GABA/Glu inhibitory), connections with total strength <6 excluded. **Conductance-based** LIF in Brian2 (g_L, g_ACh, g_GABA, g_Glu with reversal potentials; GF given distinct membrane resistance); conductance increments J0 with exponential decay; background excitatory Poisson 30 Hz at 5 nS to all; test stimulation 10 Hz at 40 nS; predicted that the 8 cholinergic AN08B098 ascending neurons increase DNp01 excitability via axo-axonic synapses; validated optogenetically. Explicitly notes the LIF "respects synapse counts but ignores conductance dynamics and neuromodulators" and that MANC EM cannot detect gap junctions.

### 4.4 Pugliese et al., bioRxiv 12 Sep 2025 (MANC/FANC walking CPG) **[M]**
*Connectome simulations identify a central pattern generator circuit for fly walking* (Tuthill & Brunton labs). Firing-**rate** model (rectified tanh), not LIF, 4,604 MANC neurons of the front-leg neuropil (1,318 DNs, 144 leg MNs, 3,142 premotor), 3,780,908 synapses, **>=5-synapse threshold**, sign from MANC classifier (ACh +, GABA/Glu -), gain a~N(1,0.1)/size, threshold ~N(7.5,0.6)*size, r_max ~N(200,10) Hz, tau ~N(20,2) ms; DNg100 (BDN2) input produces 7-15 Hz stepping rhythm; minimal CPG E1 = IN17A001, E2 = INXXX466, I1 = IN16B036; input magnitude auto-tuned to keep 5-500 neurons active; LIF replication in supplement. Useful stability recipes: saturating nonlinearity, size-normalised gain, auto-tuned input.

### 4.5 Wang et al. 2025, arXiv 2508.16792 (Loihi 2 port) **[H]**
Wang F, Theilman BH, Rothganger F, Severa W, Vineyard CM, Aimone JB (Sandia). Same equations (they write dv/dt=(v0-v+g)/tau_m, dg/dt=-g/tau_g, tau_m 20 ms, tau_g 5 ms, refractory 2.2 ms, delay 1.8 ms, v0 = v_reset = 0, v_th = 7 mV, weights = integer counts x 0.275 mV). 140k neurons / 50M synapses on 12 Loihi 2 chips; integer weights range -2405..+1897 but most |w| < 100 and a large fraction are +/-1; quantised to 9-bit (capped at +255/-256), forward Euler, dt 0.1 ms (refractory 22 steps, delay 18 steps) and 1 ms (both rounded to 2 steps); spike rates within ~2 % of Brian2; 82-356x faster than CPU. Sugar experiment: ~20 Poisson inputs at 150 Hz, 0.3 % of neurons active, 30 Hz among active. For their background-activity scaling study they explicitly set weights negligible "to prevent potential runaway excitation" and used probabilistic self-spiking instead.

### 4.6 eonsystems `fly-brain` (GitHub eonsystemspbc/fly-brain) **[H for CSV numbers]**
Reimplements Shiu on Brian2 C++ standalone, Brian2CUDA, PyTorch, NEST GPU, GeNN with FlyWire v783 (`2025_Connectivity_783.parquet`; README claims "~5M synapses", probably thresholded — unverified), dt 0.1 ms everywhere, Brian2 CPU as ground truth. Benchmark numbers above.

### 4.7 desktop-fly (GitHub DenisSergeevitch/desktop-fly, kulikov0/desktop-vibe-fly) **[M]**
Hobby project close to ours: 668-neuron FlyWire v783 subset (LC4 104, LPLC2 210, DNp01 2, DNa01+DNa02 4, DNp09 2, DNg11 6, MDN 4, DNp02/04/11 6, plus 330 strongest partners), 1 kHz LIF, ACh+/GABA-/Glu-, added gap-junction "boost" on LC->GF and wind->GF, cursor approach -> looming -> GF spike in ~4 ms; decoding: DNp01 spike -> escape, DNp09 rate -> walking speed, DNa01/02 difference -> steering, DNg11 -> grooming, MDN burst -> backward. Parameters not published.

### 4.8 Jin et al. 2026, arXiv 2602.17997 (FlyGM) **[M]**
Graph neural controller instantiated from the FlyWire connectome trained by RL to drive a MuJoCo fly (flybody). Not a LIF model; irrelevant to dynamics but shows afferent/intrinsic/efferent partitioning and sign-preserving message passing.

### 4.9 Lappalainen et al. 2024 (flyvis) — the visual-system counterpart **[H]**
Lappalainen JK, Tschopp FD, Prakhya S, ..., Turaga SC. *Connectome-constrained networks predict neural activity across the fly visual system.* Nature 634:1132-1140 (2024), doi:10.1038/s41586-024-07939-3; code TuragaLab/flyvis. Rate/ODE ("deep mechanistic network"), not spiking: `tau_t dV_i/dt = -V_i + sum_j s_ij + V_rest_t + e_i`, ReLU output, per-type tau initialised 50 ms and clamped >= dt, **dt = 20 ms (1/50 s)**; 64 cell types, 45,669 neurons, 1,513,231 synapses over **721 hexagonal columns**; 734 free parameters (shared tau, V_rest, and a non-negative unitary strength alpha per source-type/target-type pair, init scale 0.01; weight = count x sign x alpha). Input: greyscale frames rendered onto a hexagonal photoreceptor lattice (BoxEye: extent 15 -> 721 hexals, 13x13-pixel box mean; HexEye: 5.8 deg per ommatidium), luminance normalised 0..1, injected directly into R1-R8 (photoreceptors are nodes receiving external input). Signs: histaminergic, GABAergic and glutamatergic hyperpolarising; cholinergic depolarising; R8 output hyperpolarising or depolarising depending on the target's histamine-receptor expression. Connectome source: FIB-25 (7 medulla columns) + FIB-19 optic lobe, 1,801 neurons, tiled by column. Implication for us: photoreceptors are treated as graded, luminance-driven inhibitory sources with learned rest offsets — no spikes.

### 4.10 Whole-CNS / gap junctions / neuromodulation **[M]**
No published whole-male-CNS or BANC spiking simulation was found (searches: "male CNS"/BANC/whole nervous system + LIF, 2025-2026). No connectome-scale model adds gap junctions (EM cannot resolve them; Ceballos and Shiu state this; only desktop-fly adds an ad-hoc GF gap-junction boost, consistent with known shakB coupling in the GF system). No connectome-scale spiking model adds neuromodulation; Shiu's Usnea case is the canonical demonstration that peptidergic neurons break the model. The State of Brain Emulation Report 2025 (arXiv 2510.15745) reviews these gaps (not fetched: >10 MB).

---

## 5. Neurotransmitter prediction sources

### 5.1 Eckstein et al. 2024 **[H]**
Eckstein N, Bates AS, Champion A, Du M, Yin Y, Schlegel P, Lu AK-Y, Rymer T, Finley-May S, Paterson T, ... Funke J. *Neurotransmitter classification from electron microscopy images at synaptic sites in Drosophila melanogaster.* **Cell 187(10):2574-2594.e23 (2024)**, doi:10.1016/j.cell.2024.03.016. Six classes (ACh, Glu, GABA, 5-HT, DA, OA); accuracy 87 % per synapse, 94 % per neuron (>30 presynapses, majority vote), 91 % per cell type in FAFB (78/91/91 % in hemibrain). Histamine was **not** predicted. Serotonin least reliable. Statement: ACh excitatory, GABA inhibitory, glutamate "can perform either function" but most reported brain examples are inhibitory (GluCl).

### 5.2 Male CNS (`male-cns:v1.0`) neurotransmitter fields **[H]** (Berg et al. bioRxiv Methods, verified in PDF text)
* Classifier: ResNet50 trained per Eckstein et al. on 640 nm^3 T-bar volumes, outputs scores for **7 transmitters** (histamine now included; ground-truth resource lists ACh, DA, GABA, Glu, glycine, histamine, NO, OA, 5-HT, tyramine — flyconnectome/drosophila_neurotransmitters).
* `predictedNt` = most frequent presynapse prediction of the neuron; **`unclear` if <50 presynapses or `predictedNtConfidence` < 0.5**.
* `celltypePredictedNt` = most frequent prediction pooled over all neurons of the type; unclear if <100 pooled presynapses or confidence < 0.5.
* **`consensusNt` = `celltypePredictedNt` except where experimental ground truth overrides; all octopamine and serotonin results are set to `unclear` in consensusNt** (scant validation) unless ground truth exists. "The consensusNt is the recommended property to use in most analyses."
* Neuron nodes carry: `predictedNt, predictedNtConfidence, celltypePredictedNt, celltypePredictedNtConfidence, totalNtPredictions, celltypeTotalNtPredictions, consensusNt` (no per-class probabilities on the node; per-synapse probabilities are in `tbar-neurotransmitters-male-cns-v1.0.feather`, 2.7 GB). Edges `ConnectsTo` carry `weight, weightHP, weightHR, roiInfo`.
* Measured distribution (my Cypher queries, 2026-09-03): consensusNt acetylcholine 104,182; glutamate 29,443; gaba 22,195; unclear 9,793; histamine 8,007; null 2,257; dopamine 396; octopamine 101; serotonin 48. consensus == celltypePredicted for 166,369 neurons; differs for 5,359 (largest override: celltype 'dopamine' -> consensus 'acetylcholine', 4,062 neurons; 'unclear' -> ACh 1,635; -> Glu 628). `unclear` consensus by superclass: untyped 6,794, cb_intrinsic 798, vnc_sensory 550, visual_projection 481, **vnc_motor 389**, vnc_intrinsic 188, cb_sensory 100, cb_motor 80.
* Histamine consensus types: R1-R6 (3,377), **T1 (1,777)**, R7/R8 subtypes (~2,700), HBeyelet (7), a few GNG/AN/IN types. For R1-R6, `predictedNt` is `unclear` for 2,627 and histamine for 750 — the consensus field is what makes photoreceptors usable.
* Sanity checks: DNp01 = ACh (confidence 0.50-0.56, low), DNa02 = ACh (0.95), L1 = glutamate (~0.8), MN9 = ACh (consensus), leg MNs (e.g. Ti flexor MN) = glutamate by consensus although predictedNt is unclear for 36/37 cells; DLMn 'unclear'.
* Superclass breakdown (consensus): descending: ACh 931, GABA 241, Glu 104, unclear 36, 5-HT 2; ascending: ACh 1,271, GABA 407, Glu 127; ol_sensory: histamine 6,098; cb_sensory: ACh 4,680 (100 unclear); vnc_sensory: ACh 5,757 (550 unclear); vnc_motor: unclear 389, Glu 302, ACh 14.

### 5.3 Recommended sign map for the Java port **[L, grounded in the above]**

| consensusNt | sign | rationale |
|---|---|---|
| acetylcholine | +1 | Shiu, Eckstein |
| gaba | -1 | Shiu |
| glutamate | -1 | Shiu default; switching to +1 destroyed bitter inhibition and raised false positives 1 % -> 16 % |
| histamine | -1 | photoreceptor/T1 output via histamine-gated Cl- channels (ort/HisCl); flyvis treats it as hyperpolarising |
| dopamine / octopamine / serotonin | +1 (config flag `monoamineSign` in {+1, 0}) | Shiu treated them as excitatory; only 545 consensus neurons in male CNS, so the choice is low-impact; 0 = "neuromodulator, no fast effect" is the defensible alternative |
| unclear | fall back to `predictedNt` if not unclear; else by superclass: vnc_motor/cb_motor -> -1 (glutamatergic MNs; few central targets), sensory -> +1, otherwise +1 | mirrors Shiu, where anything not majority GABA/Glu became excitatory |
| null (untyped fragments) | +1 or drop | 2,257 neurons; most are fragments with few synapses |

Also apply the FlyWire-style whole-neuron sign (one sign per presynaptic neuron), and consider the `weightHP` (high-probability) or `weight >= 5` edge filter for the male CNS only as a **performance** option: Shiu's fidelity results were obtained with all connections, and >=5 keeps only 62.5 % of synapses (v630 numbers; 25.86M -> 6.29M edges in male CNS).

---

## 6. Histaminergic photoreceptors: recommendation **[L, with H physiology]**

Physiology (H): R1-R6 (and R7/R8) are non-spiking, depolarise to light and release histamine in a graded fashion; L1-L3 receive histaminergic input via histamine-gated chloride channels (ort) and **hyperpolarise to light increments** with graded potentials, rebounding at light-off (Dau et al. 2016 Front. Neural Circuits 10:19; Hardie 1989 Nature 339:704 for the HisCl channel — Hardie cited from memory, not fetched). Male-CNS consensus: R1-R6, R7*, R8*, T1 and HBeyelet are histaminergic; `assignedOlHex1/2` column coordinates exist on L1, L2, L3, L5, C2, C3, Mi1, Mi4, Mi9, Tm*, T1 but not on R cells, so map each R cell to a column through its L1/L2 partner.

Design options:
1. **Poisson photoreceptors (Shiu-style, simplest).** Rate = f(luminance) in 0-200 Hz; sign -1. Problem: with 0 Hz baseline, pure inhibition onto silent L1/L2 does nothing (Shiu's own stated failure mode). Requires tonic excitatory bias on lamina targets.
2. **Graded photoreceptors (recommended, flyvis-like).** Do not spike R cells. Each step, for every R->target connection add `g_target -= N_syn * W_syn * lambda(L) * dt`, where lambda(L) is an equivalent release rate (e.g. 0-300 Hz mapped from log luminance, or better from a high-pass-filtered contrast signal with adaptation ~100-500 ms so responses are transient like real L1/L2). Cost is trivial (a few thousand R cells x ~1-5 targets each).
3. In both options give lamina monopolar cells (L1-L5) and C2/C3 a **tonic depolarising bias current** (e.g. resting drive equivalent to a constant g_bias of 4-6 mV, i.e. a few mV below threshold) so histaminergic inhibition can be *released*: L1 (glutamatergic, inhibitory onto Mi1/Tm3) firing less at light-ON disinhibits the ON pathway; L2 firing more at light-OFF drives the OFF pathway (Tm1/Tm2). flyvis achieves the same with learned per-type resting potentials. Expose the bias as a config parameter and sweep it together with W_syn.
4. Keep R7/R8 on the same inhibitory rule (flyvis's target-dependent R8 sign nuance is a refinement you can skip). Treat T1 (histaminergic, mostly output-poor) as inhibitory.

---

## 7. Neuromodulators, gap junctions, background activity **[L]**

* Monoamines: see table; add a global flag. Do not attempt slow modulation in v1; if needed later, implement DA/OA as multiplicative gain on postsynaptic W_syn with a ~1 s time constant rather than as spikes.
* Neuropeptides/co-transmission: ignored by every connectome model so far; expect Usnea-type failures.
* Gap junctions: not in any dataset; the only documented ad-hoc addition is a GF-system boost (desktop-fly). Optional: add a fixed electrical coupling for LC4/LPLC2 -> DNp01 and JO -> DNp01 if escape latency is too slow.
* Background activity: Shiu uses none. If you want spontaneous life-like activity, use the Wang et al. approach (independent probabilistic firing that does *not* propagate through scaled weights) or a very low rate (<=1 Hz; my envelope calculation shows 5 Hz everywhere already pushes 4 % of neurons past threshold without inhibition).

---

## 8. Numerical-stability and porting tips (with sources)

1. **Exact integration, not Euler** (Brian2 `linear`; Rotter & Diesmann 1999). Coefficients in section 3; per-neuron state is two doubles (u, g) plus a refractory counter. Wang et al. used forward Euler at 0.1 ms and matched within ~2 %, so Euler is acceptable if you keep dt = 0.1 ms; at dt = 1 ms use the exact coefficients (their 1 ms Euler variant required re-tuned fixed-point constants and showed capped-weight artefacts). **[H]**
2. **Weights as `int` signed synapse counts x one global float** (`W_syn`), exactly as Shiu/Wang; keep the raw count so W_syn sweeps and E/I ratio sweeps (Shiu tested +/-30 % and +/-50 %) are free. Cap outliers only if you need fixed point (Loihi capped at |w| <= 255 and saw distortions). **[H]**
3. **Sparse, event-driven delivery.** Only ~0.3-0.5 % of neurons fire; per-step cost is dominated by integrating 176k neurons (trivial) plus delivering spikes along out-edges (CSR by presynaptic neuron). 1.8 ms delay = ring buffer of 18 slots at 0.1 ms (2 slots at 1 ms). Java estimate: 176k neurons x 500 steps per 50 ms game tick ~ 9e7 simple updates per tick; feasible on one core with primitive arrays, and the integration is embarrassingly parallel. **[L]**
4. **Freeze g during refractory and zero g on reset** to match Brian2; if you deliberately drop these for realism, note that spike rates will rise. **[H]**
5. **Gain calibration recipe (Shiu):** fix all biophysics, sweep only W_syn so that 100 Hz sugar-GRN input yields ~80 % of maximal MN9 rate; then sweep input rates 10-200 Hz rather than trusting absolute rates. For the male CNS, recalibrate W_syn because synapse detection density differs from FlyWire (male CNS: 46M presynapses/312M PSDs detected, 125M synapses in the graph vs FlyWire 52.8M) — expect W_syn to drop roughly in proportion to the synapse-count inflation, and verify with the same sugar->MN9 and JON->aDN1 tests plus LC4/LPLC2->DNp01. **[L]**
6. **Runaway excitation guards:** (a) no global background drive; (b) clamp firing with the 2.2 ms refractory (max ~450 Hz); (c) add a per-neuron rate limiter or adaptive threshold only as a debug fallback; (d) monitor network-wide spikes per step and reduce W_syn when >1 % of neurons fire in a 10 ms window (Pugliese auto-tuned inputs to keep 5-500 neurons active). **[L]**
7. **Sign robustness:** always keep glutamate inhibitory (Shiu ablation); test monoamine sign 0 vs +1. **[H]**
8. **Threshold trade-off:** >=5-synapse filtering keeps 17.8 % of edges but 62.5 % of synapses (v630) — a 5x speed-up on delivery with moderate fidelity loss; all published fidelity numbers used unthresholded graphs. **[H]**
9. **Poisson inputs:** implement as per-step Bernoulli with p = rate*dt (0.1 ms -> p <= 0.03 for 300 Hz) adding 68.75 mV directly to u; set refractory 0 for driven neurons. **[H]**
10. **Validation targets before wiring to Minecraft:** sugar GRNs 100 Hz -> MN9 ~80 % of max and ~400 active neurons; JON 100-220 Hz -> aBN1/aBN2/aDN1/aDN2; LC4+LPLC2 150 Hz -> DNp01 >150 Hz, DNp04/DNp02 >100 Hz, contralateral DNa02 >20 Hz with ~5 ms / ~22 ms latencies; bitter + sugar -> MN9 suppressed. **[H for the targets]**

---

## 9. Copy-paste parameter table (Shiu 2024 defaults)

```
V_rest      = -52.0 mV        V_reset = -52.0 mV        V_th = -45.0 mV   (threshold 7 mV above rest)
tau_m       =  20.0 ms        tau_syn = 5.0 ms          t_ref = 2.2 ms    t_delay = 1.8 ms
W_syn       =   0.275 mV per synapse (free; sweep 0.19-0.36)
w_ji        =  sign(j) * synapseCount(j->i) * W_syn      (sign per presynaptic neuron; |E| = |I|)
sign        =  +1 ACh, DA, OA, 5-HT, (unclear);  -1 GABA, Glu, histamine
synapse threshold = none (all connections; >=5 optional for speed)
dt          =   0.1 ms (exact integration: a=0.99501248, b=0.98019867, c=0.00493794)
Poisson drive: per event u += W_syn*250 = 68.75 mV; driven neurons t_ref = 0; rates 10-260 Hz
trials      =  30 x 1000 ms; 'active' = >0 Hz; 'required' = MN9 < 80 % of control when silenced
baseline    =  0 Hz (no background); g frozen during refractory and zeroed at spike
```

---

## 10. Citations

* Shiu PK, Sterne GR, ... Scott K. Nature 634:210-219 (2024). doi:10.1038/s41586-024-07763-9.
* Dorkenwald S, Matsliah A, Sterling AR, Schlegel P, Yu S-C, McKellar CE, ... Seung HS, Murthy M; FlyWire Consortium. *Neuronal wiring diagram of an adult brain.* Nature 634(8032):124-138 (2024). doi:10.1038/s41586-024-07558-y.
* Schlegel P, Yin Y, Bates AS, Dorkenwald S, Eichler K, Brooks P, ... Jefferis GSXE. *Whole-brain annotation and multi-connectome cell typing of Drosophila.* Nature 634(8032):139-152 (2024). doi:10.1038/s41586-024-07686-5.
* Lin A, Yang R, Dorkenwald S, ... Murthy M. *Network statistics of the whole-brain connectome of Drosophila.* Nature 634:153-165 (2024). doi:10.1038/s41586-024-07968-y.
* Eckstein N, Bates AS, Champion A, ... Funke J. Cell 187(10):2574-2594.e23 (2024). doi:10.1016/j.cell.2024.03.016.
* Lappalainen JK, ... Turaga SC. Nature 634:1132-1140 (2024). doi:10.1038/s41586-024-07939-3.
* Sapkal N, ... Bidaye SS. Nature 634:191-200 (2024). doi:10.1038/s41586-024-07854-7.
* Berg S, Beckett IR, Costa M, Schlegel P, Januszewski M, Marin EC, ... *Sexual dimorphism in the complete connectome of the Drosophila male central nervous system.* bioRxiv 2025.10.09.680999 (9 Oct 2025); Cell, published 2026-09-03 (volume/pages not verified). 166,691 neurons; 25.6M edges between 166,391 neurons; neuPrint dataset `male-cns:v1.0`.
* Chen W-Q, Xi W. bioRxiv doi:10.64898/2025.12.14.694122 (16 Dec 2025).
* Ceballos C, et al. iScience (2026) S2589-0042(26)00999-5; bioRxiv 2025.09.04.674108.
* Pugliese SM, Chou GM, Abe ETT, Turcu D, Lancaster JK, Tuthill JC, Brunton BW. bioRxiv 2025.09.12.675944.
* Wang F, Theilman BH, Rothganger F, Severa W, Vineyard CM, Aimone JB. arXiv:2508.16792 (2025).
* Kakaria KS, de Bivort BL. Front. Behav. Neurosci. 11:8 (2017). doi:10.3389/fnbeh.2017.00008.
* Lazar AA, et al. eLife 10:e62362 (2021). Jürgensen A-M, et al. Neuromorph. Comput. Eng. (2021) doi:10.1088/2634-4386/ac3ba6. Paul MM, et al. Front. Cell. Neurosci. 9:29 (2015).
* Dau A, et al. Front. Neural Circuits 10:19 (2016) (photoreceptor-LMC histaminergic network).
* Rotter S, Diesmann M. Biol. Cybern. 81:381-402 (1999) (exact integration; cited via Brian2 docs).

---

## 11. Open questions

1. Which W_syn reproduces Shiu-level behaviour on the male-CNS graph (different synapse-detection density)? Needs the sugar->MN9 calibration run.
2. How to handle the 9,793 `unclear` + 2,257 untyped neurons — the fallback above is heuristic.
3. Is the FAFB left-right inversion relevant when mapping FlyWire IDs (Shiu GRN lists) to male-CNS bodyIds? Use cell-type names, not IDs.
4. No source gives a tested recipe for graded photoreceptor input into a spiking lamina; the tonic-bias approach in section 6 must be tuned empirically.
5. State of Brain Emulation Report 2025 (arXiv 2510.15745) not read (file >10 MB); may list additional 2025 whole-CNS attempts.
