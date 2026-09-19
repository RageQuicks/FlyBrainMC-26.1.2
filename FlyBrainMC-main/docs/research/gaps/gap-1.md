# GAP-1: Calibration and stability of the Shiu LIF on male-cns:v1.0

**Status:** all simulations were actually run locally. 300+ full-network simulations of the complete
176,422-neuron male CNS. Every number below marked `[measured]` came out of a run on this machine;
`[verified]` means it came from a primary source I fetched; `[inferred]` means I reasoned from measured
data and say so.

**Headline: the answer to the question as posed is "W_syn ~= 0.21, and you must not use it."**
The Shiu 80%-of-maximal-MN9 criterion lands at W_syn ~= 0.21 mV on this connectome, which is
*inside* the runaway regime for every non-sugar pathway. There is no single W that satisfies both
the calibration criterion and stability. The recommendation is **W_syn = 0.15 mV on the w>=1 graph
(or 0.125 on w>=5), plus a mandatory antennal-lobe fix**, and to abandon the 80% criterion.

Artefacts written next to this report:
- `gap-1-bodyids.json` — every population's bodyId list (129 keys), ready to paste into Java.
- `lif.py` — the validated simulator kernel (the reference for the Java port).
- `run_exp.py`, `build_graph.py`, `validate.py` — graph build + Brian2 equivalence harness.
- Raw results: `../sim/results/*.json` + `*.npz` (300+ runs), `../sim/data/graph_w{1,5}.npz`.

---

## 1. What was actually built and how it was validated

### 1.1 Data

| Item | Value | Confidence |
|---|---|---|
| Edge source | `gs://flyem-male-cns/v1.0/connectome-data/flat-connectome/connectome-weights-male-cns-v1.0-minconf-0.5-traced-only.feather` (508,025,642 bytes, md5 `6601d4ad0afa99fd03eb087965ef2423`) | high [measured] |
| Anonymous HTTPS works | `https://storage.googleapis.com/flyem-male-cns/...` returns 200, no auth, no gcloud needed | high [measured] |
| Feather schema | `body_pre:int64, body_post:int64, weight:int64, type_pre:string, type_post:string` | high [measured] |
| Rows (= edges, w>=1) | **25,563,197** | high [measured] |
| Total synapses | **124,025,046** | high [measured] |
| Edges w>=5 | **6,235,682** (24.4% of edges) | high [measured] |
| Synapses in w>=5 | **89,731,544** (72.3% of synapses) | high [measured] |
| Distinct bodyIds in edges | 164,587 — all present in the 176,422 neuPrint `Neuron` set (100% match) | high [measured] |
| Neuron metadata | pulled from neuPrint Cypher, 176,422 rows x 30 properties, ~18 s total | high [measured] |

Note the small discrepancy with the orchestrator's brief: the *traced-only* flat-connectome file has
124.0M synapses / 25.56M edges, whereas neuPrint's `ConnectsTo` totals were quoted as 125.02M / 25.86M.
The difference (~1M synapses, ~300k edges) is the untraced/orphan fraction the "traced-only" file
excludes. **Use the traced-only file** — it is the one that matches the annotated neuron set. Confidence: high [measured].

### 1.2 Neurotransmitter sign map

Applied exactly as specified (ACh/DA/OA/5-HT = +, GABA/Glu/histamine = -, `consensusNt` first, then
`predictedNt`, then default +):

| Source of sign | Count |
|---|---|
| consensus:acetylcholine | 104,182 |
| consensus:glutamate | 29,443 |
| consensus:gaba | 22,195 |
| consensus:histamine | 8,007 |
| consensus:dopamine / octopamine / serotonin | 396 / 101 / 48 |
| predicted:* (consensusNt was null or `unclear`) | 1,120 |
| default + (no NT at all) | 10,930 |

Result: **34.0% of neurons inhibitory, 38.3% of synapses inhibitory.** Confidence: high [measured].

Per-neuron input balance (w>=1 graph): mean 465 excitatory + 289 inhibitory input synapses; median
per-neuron I/E input ratio 0.80 (10th pct 0.35, 90th pct 1.95). Confidence: high [measured].

### 1.3 The LIF and an exact Brian2 equivalence proof

I re-implemented Shiu's Brian2 model in numpy (`lif.py`) and proved bit-level equivalence.
Parameters verified verbatim from `model.py` in the Shiu repo (fetched):

```python
'v_0': -52*mV, 'v_rst': -52*mV, 'v_th': -45*mV, 't_mbr': 20*ms, 'tau': 5*ms,
't_rfc': 2.2*ms, 't_dly': 1.8*ms, 'w_syn': .275*mV, 'r_poi': 150*Hz, 'f_poi': 250,
'eqs': 'dv/dt = (v_0 - v + g)/t_mbr : volt (unless refractory)\n dg/dt = -g/tau : volt (unless refractory)\n rfc : second',
'eq_th': 'v > v_th', 'eq_rst': 'v = v_rst; w = 0; g = 0 * mV'
NeuronGroup(..., method='linear', refractory='rfc')
Synapses(neu, neu, 'w : volt', on_pre='g += w', delay=params['t_dly'])
syn.w = df_con['Excitatory x Connectivity'].values * params['w_syn']
PoissonInput(target=neu[i], target_var='v', N=1, rate=r_poi, weight=w_syn*f_poi)  # and neu[i].rfc = 0*ms
```
Source: <https://raw.githubusercontent.com/philshiu/Drosophila_brain_model/main/model.py>. Confidence: high [verified].

**Equivalence test result** (4,392-neuron / 176,579-edge sugar subgraph, 300 ms, identical Poisson
kick trains injected via `SpikeGeneratorGroup` into both):

```
numpy sim: spikes 3796
brian2 numpy: spikes 3796
identical (step,neuron) spike pairs: 3796   only numpy: 0   only brian2: 0
MN9 10331 L: numpy 116.67 Hz, brian2 116.67 Hz     active neurons: numpy 358, brian2 358
```
Confidence: high [measured].

**Two implementation traps that silently change results — the Java port MUST reproduce both:**

1. **`(unless refractory)` makes `v` and `g` read-only during refractoriness, and this blocks
   `on_pre` writes too.** Brian2 doc: *"a variable of a neuron that is in its refractory period is
   read-only: incoming synapses or other code will have no effect on the value of `v` until it
   leaves its refractory period."* (<https://brian2.readthedocs.io/en/stable/user/refractoriness.html>).
   So `g += w` is **dropped**, not deferred, for a refractory postsynaptic neuron. Before I modelled
   this, my numpy sim produced 4,150 spikes vs Brian2's 3,796 on the same input — a 9% error that
   compounds. Confidence: high [measured — this was the actual bug I found and fixed].
2. **Refractory test is `timestep(t - lastspike, dt) >= timestep(rfc, dt)`**, i.e. integer-step
   comparison, not float. With `t_rfc = 2.2 ms, dt = 0.1 ms` that is exactly **22 steps**.
   Delay 1.8 ms = exactly **18 steps**. Confidence: high [measured, from `Gn.thresholder['spike'].abstract_code`].

Exact-integrator constants for dt = 0.1 ms (use these directly in Java; `method='linear'` is exact):
```
a = exp(-dt/t_mbr) = 0.9950124791926823      # v decay toward v_0
b = exp(-dt/tau)   = 0.9801986733067553      # g decay
c = (tau/(tau-t_mbr))*(b-a) = 0.004937935295309022   # g -> v coupling per step
v <- v_0 + (v - v_0)*a + c*g ;  g <- g*b       (skip entirely while refractory)
```
Confidence: high [measured, verified against Brian2 state traces to <1e-6 mV].

**Step order** (must match): state update -> threshold -> {push spikes to delay queue; apply Poisson
kicks to `v`; deliver spikes that are due, `g += W*weight`} -> reset. Confidence: high [measured].

**Poisson input semantics:** kick weight is `w_syn * f_poi = 0.275 * 250 = 68.75 mV` against a 7 mV
threshold gap, so **every Poisson event fires the target deterministically**, and Shiu sets those
neurons' refractory period to 0. Net effect: a stimulated neuron is a **Bernoulli spike source at
exactly the requested rate**, and `W_syn` does not scale the input drive. Measured: 17 cells at 100 Hz
for 1 s emitted 1,723 spikes = 101.4 Hz/cell. Confidence: high [measured].
*Java simplification:* skip the 68.75 mV kick entirely and just force a spike with probability
`rate*dt` on stimulated neurons. Identical behaviour, no magic constant.

---

## 2. Cell populations used (bodyIds in `gap-1-bodyids.json`)

### 2.1 The sugar GRN question — resolved against a primary source

The orchestrator specified "right labellar sugar GRNs (types LB3b + LB3c, ~34 cells)". Two corrections:

- **The count is 17 per side, not 34.** LB3b = 11 total (5 L, 6 R), LB3c = 23 total (12 L, 11 R).
  Right side = 6 + 11 = **17 cells**. Confidence: high [measured, neuPrint].
  Sidedness for GRNs is on `rootSide` (entry nerve `MxLbN`), **not** `somaSide`, which is null for
  all labellar GRNs — a real trap for the implementer. Confidence: high [measured].
- **LB3b + LB3c is nevertheless the correct sugar set**, confirmed by the male-CNS taste paper:
  Tastekin et al. 2025, *"From Sensory Detection to Motor Action: The Comprehensive Drosophila
  Taste-Feeding Connectome"*, bioRxiv 10.1101/2025.08.25.671814 v2 (2025-12-13) — same male CNS volume.
  It reports: **LB3b & LB3c match Gr64f-GAL4 (sweet/sugar)**; LB3a matches ppk28 (water); LB3d matches
  Ir47a/ppk23 (high salt); **LB1a-d match Gr33a (bitter)**; LB1e expresses Ir94e. Confidence: high [verified].

That paper's assignment is corroborated by the connectivity I measured: LB3c/LB3d project overwhelmingly
to the known Shiu-2022 second-order sugar neurons (LB3c -> GNG038 *Billiards* 1149 syn, GNG042
*Quasimodo* 890, GNG215 *Zorro* 766, GNG232 *G2N-1* 519, GNG175 *Usnea* 488, GNG132 *Rattle* 447),
while LB1a-d project to the bitter second-order neurons (LB1c -> GNG087 *Scapula* 1738 syn).
Confidence: high [measured].

### 2.2 Definitive population table

| Population | n | bodyIds |
|---|---|---|
| `sugar_LB3bc_R` (Gr64f sugar, right) | 17 | 72059, 92440, 101087, 120303, 140015, 142827, 163597, 183084, 187492, 202888, 209155, 215556, 516217, 557646, 942168, 957530, 213650853 |
| `sugar_LB3bc_L` | 17 | 71254, 78240, 85806, 159772, 180314, 190769, 261450, 262567, 272263, 512551, 531237, 933317, 158893964, 174444965, 349137284, 475202322, 766547228 |
| `bitter_LB1ad_R` (Gr33a bitter, right) | 19 | 107241, 115666, 139178, 154544, 163395, 208885, 256844, 375038, 514546, 522746, 522752, 522761, 522762, 522841, 549024, 556797, 304136793, 602736959, 911389008 |
| `bitter_LB1ad_L` | 19 | 54104, 81741, 125111, 144334, 168492, 173462, 511882, 514547, 517255, 518112, 522753, 522754, 533618, 557937, 912379, 140334446, 144295263, 324178811, 772366874 |
| **MN9** | **2** | **10331 (somaSide L), 16949 (somaSide R)** |
| MN6 / MN8 / MN11 | 2 / 2 / 5 | 519667, 924612 / 16827, 20173 / 11269, 11393, 49829, 551398, 492462351 |
| `JO_CE_windgravity` (JO-C* + JO-E*, subclass `wind_gravity`) | 320 (188 L, 132 R) | see JSON |
| LC4 R / L | 55 / 71 | see JSON |
| LPLC2 R / L | 91 / 94 | see JSON |
| ORN (class `olfactory`) | 2,639 | see JSON |
| L2 L/R, L3 L/R | 886/893, 880/892 | see JSON |
| DNp01 (Giant Fiber) | L 10010, R 10001 | |
| DNp02 | L 10197, R 10117 | |
| DNp04 | L 531898, R 11137 | |
| DNp09 | L 10783, R 11177 | |
| DNp10 / DNp11 | L 10425 R 10433 / L 10259 R 10106 | |
| DNa01 / DNa02 | L 10442 R 10760 / L 523769 R 10360 | |
| DNg62 (aDN1) | L 13624, R 15148 | |
| DNge078 (aDN2) | L 14537, R 36541 | |
| DNg100 (BDN2) | L 10045, R 10056 | |
| MDN | L 11288, 12348; R 10763, 11332 | |
| `vnc_motor` all / `cb_motor` all / `descending_neuron` all | 708 / 107 / 1,314 | see JSON |

Confidence: high [measured, neuPrint male-cns:v1.0].

---

## 3. (a) The W_syn sweep — and why the 80% criterion fails here

**Protocol:** `sugar_LB3bc_R` (17 cells) at 100 Hz for 1,000 ms, full 176,422-neuron network, dt 0.1 ms,
seed 0. MN9_L (10331) is the responder; MN9_R (16949) stays at 0 Hz throughout the safe range — the
pathway is strongly lateralised. Confidence: high [measured].

### 3.1 MN9 rate vs W_syn at 100 Hz sugar

| W_syn (mV) | MN9_L Hz (w>=1) | MN9_L Hz (w>=5) | active non-stim (w>=1) | peak active/10 ms (w>=1) | spikes/sim-s (w>=1) |
|---|---|---|---|---|---|
| 0.050 | 0 | 0 | 12 | 21 | 1,884 |
| 0.075 | 0 | 0 | 15 | 23 | 2,087 |
| 0.100 | 0 | 0 | 38 | 35 | 2,440 |
| 0.125 | 0 | 0 | 70 | 56 | 3,137 |
| **0.150** | **5** | **4** | **131** | **68** | **4,392** |
| **0.175** | **40** | **39** | **430** | **176** | **7,251** |
| **0.200** | **69** | **71** | **1,020** | **394** | **14,998** |
| 0.205 | 75 | 71 | 992 | 317 | 15,375 |
| 0.210 | 87 | 72 | 1,114 | 377 | 16,537 |
| **0.215** | 83 | 89 | **13,464** | **6,917** | **294,604** <- ignition |
| 0.220 | 77 | 70 | 13,614 | 7,168 | 541,537 |
| 0.225 | 86 | 100 | 6,107 | 1,703 | 101,616 |
| 0.250 | 76 | 109 | 6,847 | 2,088 | 141,064 |
| **0.275 (Shiu's value)** | **101** | **123** | **8,271** | **2,907** | **186,003** |
| 0.300 | 99 | 113 | 18,170 | 9,981 | 1,071,537 |
| 0.350 | 137 | 101 | 20,465 | 10,976 | 1,264,856 |
| 0.400 | 79 | 144 | 23,245 | 11,802 | 1,449,682 |

Confidence: high [measured].

### 3.2 The 80% criterion

Shiu's Methods (verified verbatim from PMC11446845): *"We chose W_syn such that activation of sugar
GRNs at 100 Hz resulted in roughly 80% of maximal MN9 firing."* To apply it I measured the full
GRN-rate/MN9 saturation curve:

| W_syn | MN9_L Hz @ GRN 10 / 25 / 50 / 100 / 150 / 200 / 300 Hz (w>=1) | saturating max | ratio at 100 Hz |
|---|---|---|---|
| 0.150 | 0 / 0 / 0 / 5 / 26 / 51 / 77 | ~77+ | 6% |
| 0.175 | 0 / 0 / 8 / 40 / 67 / 87 / 104 | ~104 | 38% |
| 0.190 | - / - / - / 58 / 80 / 101 / 112 | ~112 | 52% |
| 0.200 | 0 / 3 / 18 / 69 / 98 / 107 / 110 | ~110 | **63%** |
| 0.205 | - / - / - / 75 / 98 / 99 / 117 | ~117 | 64% |
| 0.210 | - / - / - / 87 / - / - / - | ~118 [inferred] | **~74%** |

**So the 80% criterion lands at W_syn ~= 0.21-0.22 mV** — and ignition (see §4) begins at
**W_syn = 0.215** for sugar and **W_syn = 0.155-0.16 for bitter**. The criterion point is inside the
unstable regime. Confidence: high [measured] for the numbers; medium for the exact 80% crossing since
I did not run the full rate curve at 0.21 (the network is already partly unstable there in w>=5).

**The brief's premise that "Shiu's 0.275 mV is almost certainly wrong here" is confirmed, but the
direction matters:** 0.275 on male-cns is not merely mis-scaled, it is 28% above the ignition
threshold. At W=0.275 the sugar stimulus alone activates 8,271 neurons and emits 186,003 spikes/s —
compare Shiu's published FlyWire figure of *"455 [neurons activated] at 200 Hz"* (verified from the paper).
The male CNS at Shiu's W is ~18x more active than the female brain model was. Confidence: high [measured].

### 3.3 w>=1 vs w>=5 — the contradiction in the prior reports, settled

**Shiu applies no minimum-synapse threshold.** The paper says *"All 127,400 proofread neurons from
Flywire materialization v.630 are included in the model"* and the only stated filter is *"a cleft
score cutoff of 50"* for the neurotransmitter call — there is no "at least 5 synapses" sentence
anywhere in the Methods (I searched for it explicitly). Confidence: high [verified].
So **w>=1 is the faithful reproduction**; w>=5 is purely a performance choice.

Measured agreement between the two graphs (same W, same seed, same stimulus, per-neuron firing rates):

| Condition | active w>=1 | active w>=5 | Jaccard | Pearson(rate) | Spearman | DN-subset Pearson |
|---|---|---|---|---|---|---|
| sugar W=0.15 | 148 | 126 | 0.851 | **0.997** | 0.982 | 0.996 |
| sugar W=0.175 | 447 | 581 | 0.691 | 0.942 | 0.724 | 0.977 |
| sugar W=0.20 | 1,037 | 965 | 0.789 | 0.982 | 0.914 | 0.980 |
| groom W=0.15 | 2,307 | 2,143 | 0.838 | 0.987 | 0.932 | 0.967 |
| loom W=0.15 | 2,119 | 1,725 | 0.761 | 0.982 | 0.890 | 0.984 |
| ORN+vision W=0.10 | 12,227 | 12,342 | 0.877 | 0.981 | 0.945 | 0.920 |

**Verdict: use w>=5.** It preserves the readout layer almost perfectly (DN-subset rate correlation
0.92-0.996 in every condition; MN9 40 vs 39 Hz at W=0.175, 69 vs 71 at W=0.20) while costing 4.1x
fewer edges (6.24M vs 25.56M), 4x less memory (39 MB vs 155 MB as int32 idx + int16 weight), and
running 1.3-2x faster. The identity of *which* low-rate neurons flicker on differs (Jaccard 0.69-0.88),
but no decoder should be reading a 1-spike-per-second neuron anyway. Confidence: high [measured].
Caveat: w>=5 has a *slightly lower* ignition threshold under some stimuli (bitter latches at 0.15 on
w>=5 but not on w>=1) — so re-tune W per graph, do not port the number across. Confidence: high [measured].

---

## 4. (c) + the real stability story: ignition is stimulus-dependent, and olfaction is the problem

This is the most important result in the report and it is **not** what the brief anticipated.

### 4.1 The right stability test is persistence after stimulus offset, not activity level

I stimulated for 500 ms, then ran 1,000 ms with **zero input**, and measured mean active neurons per
10 ms window over t = 1300-1500 ms. A healthy network returns to exactly 0. Confidence: high [measured].

**Residual active neurons per 10 ms at 1300-1500 ms (0 = clean shutdown):**

| Stimulus (all off at t=500 ms) | w1 0.05 | w1 0.075 | w1 0.10 | w1 0.125 | **w1 0.15** | w1 0.175 | w1 0.20 |
|---|---|---|---|---|---|---|---|
| sugar LB3b/c R 100 Hz | 0 | 0 | 0 | 0 | **0** | 0 | 0 |
| sugar 200 Hz | - | - | - | - | - | 11 | 0 |
| bitter LB1a-d R 100 Hz | 0 | 0 | 0 | 0 | **0** | **4,784** | 5,934 |
| JO wind/gravity 140 Hz | 0 | 0 | 0 | 0 | **0** | **4,706** | 5,683 |
| JO wind/gravity 220 Hz | 0 | 0 | 0 | 69 | **3,247** | 4,757 | 5,919 |
| LC4+LPLC2 R 150 Hz | 0 | 0 | 15 | 20 | **27** | 511 | 649 |
| LC4+LPLC2 bilateral | - | - | - | - | 326 | 453 | 726 |
| DNg100 bilateral 100 Hz | 0 | 0 | 0 | 0 | **0** | 0 | 736 |
| **L2+L3 both eyes 50 Hz (vision only)** | **0** | **0** | **0** | **0** | **0** | - | - |
| **L2+L3 both eyes 20 Hz** | **0** | **0** | **0** | **0** | **0** | - | - |
| **all 2,639 ORNs 50 Hz (olfaction only)** | **385** | **684** | **1,042** | **1,535** | **3,214** | - | - |
| ORN + L2/L3 (the brief's stress test) | 386 | 690 | 1,044 | 1,529 | 3,198 | 4,699 | 6,000 |
| everything at once | 386 | 688 | 1,050 | 1,701 | 3,238 | 4,844 | 6,022 |

Confidence: high [measured, 178 runs].

### 4.2 The three findings that change the design

**(1) Ignition threshold is stimulus-specific and spans a 1.4x range in W.**
Sugar ignites at W ~= 0.215. Bitter ignites at **W ~= 0.155-0.16** (at W=0.16 the bitter stimulus
alone produces 8,612 active neurons and 238,804 spikes/s, vs 420 active at W=0.15). JO wind/gravity
at 220 Hz ignites at W ~= 0.14. **You must calibrate against the worst-case pathway, not sugar.**
Calibrating on sugar as Shiu did gives a W at which taste-aversion and hearing permanently latch.
Confidence: high [measured].

**(2) Vision is safe; olfaction is not.** L2+L3 of both eyes (3,551 cells at 50 Hz — a full-field
visual drive) returns to **exactly 0** at every W from 0.05 to 0.15. All 2,639 ORNs at 50 Hz latch at
**every W >= 0.02**. Confidence: high [measured].

**(3) The olfactory latch is essentially unconditional.** I varied odour breadth, rate and duration:

| Olfactory drive | cells | residual @ w1 W=0.15 | residual @ w5 W=0.125 |
|---|---|---|---|
| 1 glomerulus (ORN_DM1) at 10 Hz | 74 | 2,980 | 1,330 |
| 1 glomerulus at 50 Hz | 74 | 3,235 | 1,315 |
| 3 glomeruli (DM1+DA1+VA2) at 10 Hz | 361 | 3,212 | 1,325 |
| all ORNs, **100 ms pulse only** | 2,639 | 3,240 | 1,322 |
| all ORNs at 50 Hz, 500 ms | 2,639 | 3,214 | 1,330 |

**74 receptor neurons firing at 10 Hz for half a second permanently lights up ~3,000 neurons.**
The residual is independent of odour identity, intensity and duration. Once lit, it never goes out.
Confidence: high [measured].

### 4.3 Mechanism: a genuinely cholinergic antennal-lobe local-neuron network

I traced which neurons stay active. At W=0.05, ORN-only, 200 ms after offset: **707 neurons**, of which

| class | n | mean Hz |
|---|---|---|
| ALPN (antennal-lobe projection neurons) | 288 | 60.4 |
| **ALLN (antennal-lobe local neurons)** | **200** | **121.0** |
| (unclassified, mostly AL/LH) | 194 | 29.7 |
| hygrosensory / ALON / ALIN / DAN / MBON | 23 | - |

Top persistent types: **`lLN1_bc` (30 cells, 211 Hz mean, 225 Hz max)**, `lLN2P_a/b/c`, `lLN2X12`,
`lLN2T_a`. The persistent set contains **1,533,626 internal synapses, 79% of them excitatory
(1,217,070 vs 316,556)** — a self-exciting recurrent loop. Confidence: high [measured].

**This is not an annotation artefact.** I checked the neurotransmitter evidence in
`body-neurotransmitters-male-cns-v1.0.feather`:

| type | n | predicted | conf | consensus | **ground_truth** |
|---|---|---|---|---|---|
| **lLN1_bc** | 30 | acetylcholine | 0.75 | acetylcholine | **acetylcholine** |
| lLN2T_a | 5 | acetylcholine | 0.80 | acetylcholine | **acetylcholine** |
| lLN2X12 | 11 | acetylcholine | 0.55 | acetylcholine | (none) |
| lLN2P_b | 12 | gaba | 0.75 | gaba | **gaba** |

`lLN1_bc` carries a **ground-truth** cholinergic label on all 30 cells. Overall the ALLN class
(420 cells) is 137 ACh / 115 GABA / 99 Glu / 67 unclear / 2 OA — roughly a third genuinely excitatory.
Confidence: high [measured].

So the latch is a real consequence of (real excitatory AL local neurons) + (a LIF with **no spike-frequency
adaptation, no synaptic depression, no GABA-B slow inhibition, no neuromodulation**). Shiu's model omits
all of the mechanisms that terminate an odour response in vivo. It did not show up in FlyWire-based work
because published Shiu simulations stimulate small GRN/JON sets, not ORNs.
Confidence: high for the mechanism [measured]; medium for the claim that it never appears in the
FlyWire literature [inferred — I did not exhaustively check FlyWire ORN simulations].

### 4.4 The fix, tested

**Treat all antennal-lobe local neurons (`class == 'ALLN'`, 420 cells) as inhibitory.** In practice
this flips 206 cells (137 ACh + 67 unclear + 2 OA); the other 214 are already GABA/Glu. That is
165,118 edges / 1,397,713 synapses out of 25.6M edges — a 0.65% edit to the graph.

| Condition | metric | base | **ALLN-inhibitory** |
|---|---|---|---|
| ORN 50 Hz, w1 W=0.15 | residual after offset | 3,214 | **0.0** |
| ORN 50 Hz, w1 W=0.15 | spikes/sim-s | 454,861 | **54,101 (8.4x cheaper)** |
| ORN 50 Hz, w1 W=0.10/0.125 | residual | 1,042 / 1,535 | **0.0 / 0.0** |
| sugar 100 Hz, w1 W=0.15 | MN9_L Hz / active | 5 / 131 | **5 / 131 (identical)** |
| bitter, w5 W=0.15 | residual | 1,775 | **0.0** |
| JO 220 Hz, w5 W=0.15 | residual | 1,759 | **0.0** |
| loom, w1 W=0.15 | DNp01_R Hz | 270 | **270 (identical)** |
| **all sensory at once, w5 W=0.15** | residual | 1,818 | **250** |
| **all sensory at once, w5 W=0.15** | spikes/sim-s | 408,284 | **203,381** |
| **all sensory at once, w5 W=0.15** | **DNg62 / DNge078 Hz** | **0 / 0** | **278 / 194** |

That last row is the decisive one: **in the unmitigated model, under full sensory load the grooming
decoder reads 0 Hz — the AL noise floor has swallowed the signal.** With the fix the same channel reads
278 Hz. The mitigation does not merely save CPU, it is what makes the decoders work at all under
combined input. Confidence: high [measured].

Document this as an explicit, deliberate deviation from Shiu (it is one), justified as standing in for
the missing adaptation/depression. An alternative I did not test but flag as the more principled fix:
add spike-frequency adaptation (an after-hyperpolarisation current) to all neurons. Confidence: high
for the tested fix; the adaptation alternative is [inferred, untested].

---

## 5. (b) Behavioural validations at the recommended W

### 5.1 Escape / looming — works, with one predicted failure

`LC4_R + LPLC2_R` (146 cells) at 150 Hz, 100 ms pulse from t=200 ms. Latency = time from stimulus
onset to first spike; rate over the 400 ms following onset.

| Readout | w1 W=0.10 | w1 W=0.15 | w1 W=0.175 | latency @ W=0.15 |
|---|---|---|---|---|
| **DNp01_R (Giant Fiber, ipsi)** | 60 Hz | 68 Hz | 73 Hz | **3.3 ms** |
| DNp01_L (contra) | 30 Hz | 33 Hz | 28 Hz | 8.6 ms |
| **DNp04_R** | 63 Hz | 70 Hz | 73 Hz | **3.2 ms** |
| DNp02_R | 45 Hz | 53 Hz | 55 Hz | 4.9 ms |
| DNp11_R | 43 Hz | 50 Hz | 50 Hz | 4.3 ms |
| **DNa02 (either side)** | **0 Hz** | **0 Hz** | **0 Hz** | **never** |

- **The brief's prediction "DNp01 > 150 Hz, DNp04/DNp02 > 100 Hz" is met** at sustained stimulation
  (during a continuous 800 ms drive DNp01_R reaches 234-297 Hz, DNp04_R 240-305 Hz, DNp02_R 171-245 Hz).
  Confidence: high [measured].
- **The predicted ~5 ms latency is confirmed: 3.2-3.3 ms to first spike, 10.7 ms to third spike.**
  Confidence: high [measured].
- **The prediction "contralateral DNa02 > 20 Hz with ~22 ms latency" is FALSE.** DNa02 gives
  **exactly 0 Hz** at every W from 0.10 to 0.20, on both graphs, ipsi and contra. The only nonzero
  reading anywhere was 8.8 Hz at W=0.275 with a 241 ms latency (i.e. inside the runaway state, not a
  response). **Do not build a looming-triggered turn on DNa02.** Confidence: high [measured, 8 runs].
- Caveat on realism: DNp01 is the Giant Fiber, which in vivo fires **one** spike to a looming stimulus.
  The model has it at 250-300 Hz. Read DNp01 as a **binary trigger** (crossed threshold at least once),
  never as a rate. Confidence: high [measured + established GF biology].

### 5.2 Grooming — works well

`JO-C* + JO-E*` subclass `wind_gravity` (320 cells) at 100-220 Hz:

| Drive | graph/W | DNg62_L (aDN1) | DNg62_R | DNge078_L (aDN2) | DNge078_R | first-spike latency (DNg62_L) |
|---|---|---|---|---|---|---|
| 140 Hz | w1 0.10 | 76 Hz | 83 Hz | 47 Hz | 48 Hz | 62.5 ms |
| 140 Hz | w1 0.15 | 115 Hz | 115 Hz | 86 Hz | 85 Hz | **28.1 ms** |
| 100 Hz | w1 0.175 | 122 Hz | 118 Hz | 89 Hz | 93 Hz | 22.1 ms |
| 140 Hz | w5 0.15 | 106 Hz | 114 Hz | 80 Hz | 87 Hz | 38.3 ms |

**The brief's prediction (JO-C/JO-E wind_gravity at 100-220 Hz -> aDN1/aDN2) is fully confirmed**, and
the response is robust across the whole safe W range and both graphs. aDN1 > aDN2 consistently, which
matches Shiu's reported ordering. Note Shiu used JO-C/E/F/m (147 JONs); I used the 320-cell
`wind_gravity` subset — both work. **Cap JO drive at 140 Hz**: 220 Hz ignites at W>=0.14.
Confidence: high [measured].

### 5.3 Bitter suppression of MN9 — confirmed, but only in a narrow window

| Condition (w1) | W=0.10 | W=0.15 | W=0.16+ |
|---|---|---|---|
| sugar alone -> MN9_L | 0 Hz | **5 Hz** | 40-69 Hz |
| **sugar + bitter -> MN9_L** | 0 Hz | **0 Hz** | 0 Hz (but network ignited) |
| bitter alone -> MN9_L | 0 Hz | 0 Hz | 0 Hz |

**The suppression is real and complete** — at W=0.15 sugar drives MN9 to 5 Hz and adding bitter takes
it to exactly 0. At W=0.175-0.275 MN9 also reads 0 with bitter, but there the bitter pathway has
ignited the whole network (10,000+ active neurons), so the "suppression" is not interpretable.
The clean demonstration window is narrow because sugar's MN9 drive is weak at stable W. **Fix by
raising the sugar GRN input rate rather than W:** at W=0.15, sugar at 200 Hz gives MN9 = 51 Hz and
at 300 Hz gives 77 Hz, both with clean shutdown. Confidence: high [measured].

Also measured: using all four LB3 subtypes (LB3a-d, 40 cells) instead of just LB3b+c roughly doubles
the MN9 drive (80 Hz vs 40 Hz at W=0.175) — but LB3a is water and LB3d is high-salt, so that is
biologically wrong. Keep LB3b+c and raise the rate. Confidence: high [measured].

### 5.4 (d) Locomotion: DN vs MN readout — decided, and it is DN

`DNg100` (BDN2) or `DNp09` bilateral at 50-200 Hz for 2,000 ms, measuring the 500 leg motor neurons
in T1/T2/T3.

| Drive | W | leg MNs active / 500 | leg MN mean Hz | max Hz | L vs R Hz | autocorr peak (20-500 ms) |
|---|---|---|---|---|---|---|
| DNg100 100 Hz | 0.10 | 40 | 0.38 | 17.0 | 0.38 / 0.38 | 0.114 |
| DNg100 100 Hz | 0.15 | 79 | 1.53 | 37.5 | 1.47 / 1.60 | 0.096 |
| DNg100 100 Hz | 0.175 | 97 | 1.99 | 38.5 | 1.94 / 2.05 | 0.168 |
| DNp09 100 Hz | 0.15 | 36 | 0.53 | 38.0 | 0.44 / 0.61 | 0.175 |
| DNp09 100 Hz | 0.175 | 98 | 0.96 | 47.5 | 0.93 / 1.00 | 0.178 |

**There is no rhythm.** Pooled leg-MN autocorrelation peaks at 0.10-0.18 in the 20-500 ms lag band —
i.e. noise. (The only values above 0.3 occur in runs that had ignited, where the "rhythm" is the
global oscillation of a runaway network, not a gait.) At safe W only **8-20% of leg motor neurons fire
at all**, at a population mean of 0.4-2.0 Hz, and **left and right are symmetric to within 6%** — so
there is no turning signal either. Confidence: high [measured, 28 runs].

**Verdict: read locomotion from the descending layer, not the motor layer. Not a 70/30 blend — closer
to 100/0.** The VNC premotor circuitry in this model does not produce leg rhythms, which is expected:
a current-based LIF with no proprioceptive feedback loop (the fly is not moving, so campaniform and
chordotonal organs are silent) and no CPG intrinsic currents cannot generate a gait. Real fly walking
CPGs depend on both. Confidence: high [measured]; the mechanistic explanation is [inferred].

Two further negative results for the decoder design:
- **DNa02 or DNa01 driven unilaterally at 100 Hz produces almost nothing downstream** — 45-113 active
  neurons total, 0 Hz in every leg MN pool. If you want DNa02-based steering, read **DNa02's own
  stimulated rate** (which you set) and use it as a direct steering command. Confidence: high [measured].
- **MDN bilateral at 100 Hz** gives 520 active at W=0.15 (safe) but 3,329 at W=0.175 (approaching
  ignition). Backward walking must be read from MDN itself, not from a distinct leg-MN pattern. Confidence: high [measured].

**The specific MN pools named in the brief are the wrong ones.** `Ti flexor MN` and `Tr extensor MN`
fire at ~0.0 Hz under DN drive. The pools that actually respond (DNg100 100 Hz, w1 W=0.175, mean Hz):

| MN type | neuromere | n | mean Hz | max Hz |
|---|---|---|---|---|
| ps1 MN | T2 | 2 | 32.3 | 32.5 |
| hg1 MN | T2 | 2 | 22.3 | 26.5 |
| **Sternotrochanter MN** | T3 | 6 | 17.8 | 38.5 |
| hg3 MN | T2 | 2 | 17.0 | 22.0 |
| **Pleural remotor/abductor MN** | T3 | 4 | 14.5 | 38.0 |
| **Fe reductor MN** | T3 | 4 | 13.6 | 29.0 |
| b3 MN | T2 | 2 | 12.3 | 12.5 |
| **Pleural remotor/abductor MN** | T1 | 4 | 12.1 | 23.0 |
| **Ti extensor MN** | T2 | 4 | 10.0 | 15.0 |
| **Tergopleural/Pleural promotor MN** | T1 | 8 | 7.3 | 22.0 |
| Ti flexor MN | T1/T2/T3 | 30 | **0.0** | 0.0 |
| Tr extensor MN | T1/T2 | 8 | **0.0-0.2** | - |

If you want *any* MN-derived signal (e.g. to modulate a DN-driven gait's amplitude), use
Pleural remotor/abductor + Sternotrochanter + Fe reductor + Tergopleural promotor + Ti extensor.
bodyIds for all of these are in `gap-1-bodyids.json` under `legMN|<type>|<neuromere>|<side>`.
Wing MNs (ps1/hg1/hg3/b3) respond better than leg MNs — a flight/wing-driven readout is more viable
than a walking one. Confidence: high [measured].

---

## 6. (e) Cost model for the Java runtime budget

### 6.1 Measured wall times (Python/numpy, single uncontended process, 32-core Windows box)

| Condition | graph | spikes/sim-s | active | **wall s per 1 s simulated** |
|---|---|---|---|---|
| sugar 100 Hz, W=0.15 | w1 | 4,392 | 131 | **4.5** |
| sugar 100 Hz, W=0.15 | w5 | 4,046 | 109 | **4.4** |
| ORN+vision, W=0.15 | w1 | 864,704 | 14,837 | **15.3** |
| ORN+vision, W=0.15 | w5 | 654,695 | 13,179 | **13.1** |

Confidence: high [measured].

### 6.2 Isolated cost terms (1 core, numpy)

| Operation | cost |
|---|---|
| Dense state update + threshold scan, N=176,422, **float64** | 4,773 us/step = **47.7 s per simulated second** |
| Same, **float32** | 442 us/step = **4.42 s per simulated second** |
| Edge scatter, w>=1, 50 spikes/step (9,552 edges) | ~500 us/step above baseline |
| Edge scatter, w>=1, 1000 spikes/step (131,154 edges) | ~12,000 us/step above baseline |
| Edge scatter, w>=5, 1000 spikes/step (39,105 edges) | ~3,600 us/step above baseline |

Confidence: high [measured].

**The dominant cost is the dense state update, not the synapses.** At dt = 0.1 ms you pay
10,000 x 176,422 = **1.76 billion neuron-updates per simulated second** regardless of activity.
That is ~7 Gflop/s just to keep 176k neurons idling in real time. Confidence: high [measured].

### 6.3 Memory

| Graph | edges | CSR (int32 idx + float32 w) | CSR (int32 idx + int16 w) |
|---|---|---|---|
| w>=1 | 25,563,197 | 206 MB | **155 MB** |
| w>=5 | 6,235,682 | 51 MB | **39 MB** |
| w>=5, optic lobe excluded | 3,472,652 | 28 MB | **21 MB** |

Weights fit in int16 (max edge weight 2,591) — store the integer synapse count and multiply by W_syn
at delivery time. Confidence: high [measured].

### 6.4 Why the obvious optimisation does not work, and what does

**Event-driven / sparse update is viable only when olfaction and vision are off.** I measured the
"non-resting" set (neurons with `|v - v_0| > 1e-3` or `g != 0`, i.e. those that actually need updating):

| Condition | non-resting neurons (median) | % of N | receiving >=1 synaptic event per 10 ms |
|---|---|---|---|
| sugar 100 Hz only | 8,076 | **4.6%** | 3,611 |
| 3 glomeruli + L2/L3 both eyes 50 Hz | 155,551 | **88.2%** | 139,046 |
| all ORN + L2/L3 50 Hz | 155,806 | **88.3%** | 140,690 |

So a sparse kernel buys ~20x on taste-only and **nothing** once the eyes are open. Confidence: high [measured].

**The optimisation that does work: exclude the optic lobe and drive visual projection neurons directly.**

| | full | OL-free (drop `ol_intrinsic` + `ol_sensory`) |
|---|---|---|
| neurons (dense update cost) | 176,422 | **80,921 (46%)** |
| edges (w>=5) | 6,235,682 | **3,472,652 (56%)** |
| memory (w>=5, int16) | 39 MB | **21 MB** |

95,501 neurons (54.1% of the network) are optic-lobe intrinsic/sensory. Instead of stimulating L2/L3
(which costs 62,000-74,000 spikes/s just to represent a static visual scene) stimulate LC4, LPLC2,
LC10, LPLC1 etc. directly from raycasts: measured cost of the looming stimulus is 26,350-31,313
spikes/s and only 1,541-1,909 active neurons — and it yields the same DNp01/DNp02/DNp04 escape
signals. This roughly halves both the per-step cost and the memory, and removes the largest source of
background spiking. Confidence: high [measured for the sizes and the two conditions' costs];
medium for "you lose nothing behaviourally" [inferred — the optic lobe would still be needed for
any behaviour driven by fine visual features rather than by the LC/LPLC feature detectors].

### 6.5 Recommended Java budget

Minecraft at 20 TPS gives 50 ms of wall time per tick. Options, from the measured numbers:

1. **Do not run the network on the tick thread.** Run it on a background thread with its own clock
   and let the mob read the most recent decoder state. [inferred, but forced by the numbers]
2. **Realistic throughput target:** a tight Java float32 kernel over 80,921 neurons (OL-free) at
   dt = 0.1 ms is ~8.1e8 neuron-updates per simulated second. At an optimistic 1e9 updates/s/core that
   is ~0.8 wall-seconds per simulated second on one core, plus synapse cost. **Budget 1:1 to 1:5
   simulated-to-wall for the quiet case and 1:10-1:20 for full sensory load** — i.e. the fly's brain
   runs in slow motion. Confidence: medium [inferred from measured numpy timings and flop counts;
   no Java implementation was benchmarked].
3. **If you need real time, raise dt.** dt = 0.5 ms cuts cost 5x; the exact integrator stays exact
   (it is exact for any dt on this linear system), but refractory becomes 4-5 steps and the delay
   4 steps, which will change firing rates and require re-running the sweep in §3. Confidence: high
   for "the integrator stays exact"; high for "rates will change" [inferred but certain — the delay
   and refractory quantisation both shift].
4. **Activity budget to size buffers:** at the recommended operating point, spikes per simulated
   second range from **1,375** (taste only) to **54,101** (olfaction, with the ALLN fix) to
   **203,381** (everything at once, w5 + ALLN fix). Peak active neurons per 10 ms window: 40 to 4,470.
   Confidence: high [measured].

---

## 7. Deliverable: the parameter table

### 7.1 Recommended parameters

| Parameter | Value | Basis |
|---|---|---|
| **W_syn** | **0.15 mV** on w>=1, **0.125 mV** on w>=5 | highest W at which every pathway shuts down cleanly (§4.1) |
| Graph | **w>=5** (6,235,682 edges) | DN-rate correlation 0.92-0.996 vs w>=1 at 4x lower cost (§3.3) |
| ALLN handling | **force `class=='ALLN'` presynaptic sign to -1** (206 of 420 cells actually change) | eliminates the olfactory latch, 8.4x cheaper, restores DN decodability (§4.4) |
| v_0, v_rst | -52 mV | Shiu [verified] |
| v_th | -45 mV | Shiu [verified] |
| t_mbr | 20 ms | Shiu [verified] |
| tau (synaptic) | 5 ms | Shiu [verified] |
| t_rfc | 2.2 ms = **22 steps** | Shiu [verified]; integer-step comparison (§1.3) |
| t_dly | 1.8 ms = **18 steps** | Shiu [verified] |
| dt | 0.1 ms | Shiu [verified] |
| a, b, c | 0.9950124791926823, 0.9801986733067553, 0.004937935295309022 | exact integrator (§1.3) |
| Poisson input | force spike with p = rate*dt; set refractory 0 on stimulated cells | equivalent to Shiu's 68.75 mV kick (§1.3) |

### 7.2 Input rate caps (exceeding these ignites the network)

| Sensory channel | population | max safe rate | measured ignition point |
|---|---|---|---|
| Sugar taste | `sugar_LB3bc_R/L` (17/side) | **300 Hz** (no ignition observed at any rate) | none up to 300 Hz |
| Bitter taste | `bitter_LB1ad_R/L` (19/side) | **100 Hz at W<=0.15** | W=0.16 at 100 Hz |
| Hearing / wind | `JO_CE_windgravity` (320) | **140 Hz** | 220 Hz ignites at W>=0.14 |
| Vision (columnar) | L2/L3 (3,551) | **50 Hz** (never ignited) | none |
| Vision (feature) | LC4/LPLC2 (146/side) | **150 Hz** | small residual (27) at W=0.15 |
| **Olfaction** | ORNs (2,639) | **requires the ALLN fix**; then 50 Hz | latches at every W>=0.02 without the fix |
| DN command drive | DNg100/DNp09/MDN | **100 Hz at W<=0.15** | DNg100 ignites at W=0.20 |

Confidence: high [measured].

### 7.3 Runaway guard

Two guards, both cheap:

1. **Activity guard (catches explosions).** Count distinct neurons that spiked in each 10 ms window
   (100 steps). Measured values at the recommended operating point:
   - quiet (taste only): 12-103
   - normal (vision + a taste + hearing): 400-1,900
   - full sensory load with ALLN fix: ~4,470
   - **ignited: 3,000-11,802**

   **Trip at 6,000 active neurons per 10 ms window (3.4% of N)** — above every legitimate measured
   state, below every ignited one except the mildest. On trip: zero all `g`, reset all `v` to v_0,
   clear the delay queue, and suppress input for 100 ms. Confidence: high [measured].

2. **Persistence guard (catches latching, which the activity guard misses).** Latched states sit at
   1,000-6,000 active — sometimes under the activity threshold. Track a slow EMA of active-per-10 ms
   over ~500 ms **while no stimulus is being applied**. In every healthy measured run this falls to
   **exactly 0** within ~50 ms of stimulus offset. **Trip if the no-input EMA exceeds 50.** Confidence: high [measured].

A hard spike-rate cap is also worth having: **spikes per simulated second > 250,000** was never
reached by a legitimate configuration with the ALLN fix (max measured 203,381 for all-sensory-at-once).
Confidence: high [measured].

### 7.4 Decoder thresholds (measured, at w5 / W=0.125-0.15 / ALLN fix)

| Behaviour | Read | Threshold | Measured range |
|---|---|---|---|
| Proboscis extension (feed) | MN9 (10331 L, 16949 R) | **> 20 Hz** | 0 Hz baseline; 5 Hz @ 100 Hz sugar, 51 @ 200 Hz, 77 @ 300 Hz; 0 when bitter co-active |
| Escape / takeoff | DNp01 (10010 L, 10001 R) | **any spike** (binary, 3.3 ms latency) | 0 baseline -> 68-270 Hz on looming |
| Escape direction | DNp04 (531898 L, 11137 R), DNp02 (10197 L, 10117 R) | **> 40 Hz**, use L/R ratio | ipsi 70/53 Hz vs contra ~0 |
| Antennal grooming | DNg62 (13624 L, 15148 R) + DNge078 (14537 L, 36541 R) | **> 40 Hz** | 0 baseline -> 106-322 Hz on JO drive |
| Forward walk | DNg100 (10045 L, 10056 R) — **read the drive you set** | n/a | leg MNs give no usable rhythm |
| Turn | DNa02 (523769 L, 10360 R) — **read the drive you set** | n/a | downstream footprint is ~60 neurons, 0 Hz in leg MNs |
| Backward walk | MDN (11288, 12348 L; 10763, 11332 R) — **read the drive you set** | n/a | 520 active downstream, no MN signature |

Confidence: high [measured]. Note the asymmetry: **sensory-driven behaviours (feed, escape, groom)
decode cleanly from named DNs/MNs; locomotor behaviours do not decode from the network at all** and
must be driven top-down.

---

## 8. Open items and honest limits

1. **I did not run 30 trials per condition as Shiu does** (they run `n_run=30` and average). I ran
   1-3 seeds. Seed-to-seed variability at W=0.175 was substantial: MN9_L = 40, 35, 47 Hz across three
   seeds; active neurons 430, 432, 526. At W=0.20: 69, 73, 67 Hz. **Budget +/-25% on any single-trial
   rate near threshold**, and average 3-5 windows in the decoder. Confidence: high [measured].
2. **Ignition near threshold is stochastic and non-monotonic.** w>=1 at W=0.215 and 0.220 exploded
   (294k and 541k spikes/s) while W=0.225 and 0.250 landed in a milder state (101k, 141k). Do not
   assume monotonicity in W; leave margin. Confidence: high [measured].
3. **The 80% criterion could be met safely if you weaken the sugar pathway's competitors rather than
   lowering W globally** — e.g. per-population W scaling. I did not test that. [not tested]
4. **Spike-frequency adaptation was not tested** as an alternative to the ALLN fix. It is the more
   principled solution and would likely raise every ignition threshold. [not tested]
5. **`somaSide` vs `rootSide`:** GRNs and JONs have null `somaSide` and use `rootSide`; central
   neurons use `somaSide`. Handle both or you will silently select zero cells. Confidence: high [measured].
6. **Which MN9 is ipsilateral is not what the naming suggests:** stimulating `rootSide == 'R'` sugar
   GRNs drives MN9 **10331, whose `somaSide` is 'L'**. Do not assume side labels match across the
   sensory/central boundary. Confidence: high [measured].
7. **No Java implementation was benchmarked.** All timings are numpy; the §6.5 Java projections are
   extrapolations from flop counts. Confidence: medium.
8. Shiu's `f_poi=250` / `r_poi=150 Hz` defaults and the "Extended Data / Supplementary Table 11"
   +/-30% W_syn robustness analysis are quoted from the paper text via Europe PMC; I could not open
   the supplementary tables themselves. The reported robustness ("the decreased Wsyn model predictions
   were consistent with 90.2% of the default model predictions") is on FlyWire, not male-cns, and my
   results show it does **not** transfer — a 30% increase from 0.15 to 0.195 crosses two ignition
   thresholds. Confidence: high [measured] for non-transfer.

## 9. Sources

- Shiu PK, Sterne GR, Spiller N, et al. **A Drosophila computational brain model reveals sensorimotor processing.** *Nature* 634, 2024. DOI 10.1038/s41586-024-07763-9, PMCID PMC11446845. Full text: <https://www.ebi.ac.uk/europepmc/webservices/rest/PMC11446845/fullTextXML>
- Shiu lab model code: <https://github.com/philshiu/Drosophila_brain_model> (`model.py` fetched verbatim)
- Tastekin I, de Haan Vicente I, Beresford RJ, et al. **From Sensory Detection to Motor Action: The Comprehensive Drosophila Taste-Feeding Connectome.** bioRxiv 10.1101/2025.08.25.671814 v2, 2025-12-13. <https://api.biorxiv.org/details/biorxiv/10.1101/2025.08.25.671814>
- Berg S, Beckett IR, Costa M, et al. **Sexual dimorphism in the complete connectome of the Drosophila male central nervous system.** bioRxiv 10.1101/2025.10.09.680999 v2, 2025-10-30 (166,691 neurons, 11,691 types). <https://api.biorxiv.org/details/biorxiv/10.1101/2025.10.09.680999>
- Eckstein N, et al. **Neurotransmitter classification from electron microscopy images at synaptic sites in Drosophila melanogaster.** *Cell*, 2024. DOI 10.1016/j.cell.2024.03.016, PMCID PMC11106717. (the NT predictions underlying `consensusNt`/`predictedNt`)
- Brian2 refractoriness semantics: <https://brian2.readthedocs.io/en/stable/user/refractoriness.html>
- neuPrint male-cns:v1.0 Cypher API: `https://neuprint.janelia.org/api/custom/custom` (anonymous POST, no token)
- Flat connectome files: `https://storage.googleapis.com/flyem-male-cns/v1.0/connectome-data/flat-connectome/`
