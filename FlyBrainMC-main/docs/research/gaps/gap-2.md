# Gap 2 — Driving vision into a silent spiking network: three recipes, simulated and scored

Research report, 2026-09-03. All simulations run this session on `male-cns:v1.0` fetched live from
neuPrint. Confidence tags: **[V]** measured by me this session (Cypher or simulation, reproducible
from the scripts named below); **[P]** primary published source I read; **[S]** secondary/abstract
only; **[L]** my inference or recommendation.

Working directory with every script and result:
`C:\Users\drini\AppData\Local\Temp\claude\C--Users-drini-OneDrive-Documents-fal-dev-fruit-fly-minecraft\793798d7-139a-4671-8d90-a5256c761f25\scratchpad\gap2\`
(`np.py` neuPrint client, `fetch_neurons.py`, `fetch_edges.py`, `build2.py`, `sim.py`, `stim.py`,
`recipes.py`, `exp.py`, `grid.py`, `calib.py`, `ds.py`, `final.py`, `dombrovski.py`;
results `grid.json`, `calib.json`, `ds.json`, `final.json`, `nodes.parquet`, `edges_idx.parquet`).

---

## 1. TL;DR — the answer

**Use recipe (i), the Poisson lamina drive, but only the OFF half of it: drive L2+L3 only, never
L1.** Recommended parameters — the only combination I found that is loom-selective:

```
W_syn        = 0.275 mV          (Shiu default — keep it; do NOT lower it for this recipe)
r_max        = 200 Hz            (400 Hz produces false-positive escapes on drifting objects)
inject into  = L2 and L3 of the stimulated eye, one cell per hex column
               rate = r_max * (clip(Lf - L, 0, 1) + 0.3*(1 - L))
L1           = NOT injected (see section 3; injecting L1 is worse than useless)
tau_adapt    = 200 ms  (Lf = one-pole low-pass of column luminance L; 50 ms for motion stimuli)
sustained    = 0.3     (fraction of the rate carried by the steady dark level, rest is transient)
ON pathway   = declared dead; hand-build anything that needs it
```

Scoreboard against the five questions asked:

| Question | Recipe (i) L2/L3 Poisson | Recipe (ii) graded histamine + bias | Recipe (iii) analytic LC4/LPLC2 |
|---|---|---|---|
| DNp01 fires? | **Yes**, 28–53 Hz, first spike 72–146 ms, and **silent for static and drifting stimuli** | Yes but **212 Hz with no stimulus at all** — permanently firing | Yes, 322–427 Hz, first spike 2.7–8.0 ms; fires from **5 %** of LC4 at W_syn=0.275 |
| LC4 ∝ angular velocity? | Emergent, correct sign, but **compressed 5×** (1.58× output for 8× input range) | **Inverted** — LC4 goes to 0 Hz at light OFF | Imposed by construction; not a test |
| LPLC2 ∝ angular size? | **No — LPLC2 is silent** (0.0–0.1 Hz) | No, 0 Hz at stimulus | Imposed by construction; not a test |
| DNp02 anterior vs DNp11 posterior? | **Yes, clean crossover, fully emergent** | No signal | Present but degraded by saturation; **absent entirely** in Chen & Xi's actual flat-150 Hz protocol |
| T4/T5 direction selectivity? | **No** (T4 = 0.00 Hz) | No | Not applicable (injection is downstream of T4/T5) |

Three corrections to the existing reports, each load-bearing:

1. **`sensory-mapping.md`'s column-to-direction map has BOTH axes inverted.** Four independent
   confirmations below (section 2). Used as written it puts the sky underground and the fly's rear
   in front of it, and it silently inverts the DNp02/DNp11 escape-direction logic.
2. **`lif-model.md`'s "tonic depolarising bias of 3–6 mV" is arithmetically impossible.** Threshold
   is 7 mV above rest; in a noiseless LIF a 6.9 mV bias fires at exactly 0.0 Hz (section 4).
3. **Chen & Xi 2025 did not do what recipe (iii) attributes to them.** They injected a *flat 150 Hz*
   Poisson train into a random *fraction* of LC4/LPLC2 — no dθ/dt term, no Gaussian size term, no
   retinotopy (section 5.3, verbatim methods quoted).

---

## 2. FIRST, A CORRECTION: the hex-to-visual-direction map is inverted on both axes **[V]**

`sensory-mapping.md` section c.2 ships this snippet:

```java
double elevationDeg =  90.0 - uN * 160.0;   // +90 (up) .. -70 (down)
double azimuthDeg   = -10.0 + wN * 165.0;   // -10 (contra) .. +155 (behind)
```

Both lines are backwards. `u = hex1+hex2`, `w = hex1-hex2`, measured ranges u ∈ [7,72],
w ∈ [−19,16] **[V]**.

**Elevation.** The report's own prose says "Dorsal = **high u**" and cites the dorsal rim area at
u = 46..72, but the code maps high u to −70° (down). It contradicts itself. Correct form:

```java
double elevationDeg = -70.0 + uN * 160.0;   // low u = -70 (down), high u = +90 (up)
```

Check: with the corrected formula, R7d/R8d (dorsal rim ommatidia) sit at mean elevation **+44°**
versus **+8°** for all hex-assigned columns **[V]** — dorsal, as they must be.

**Azimuth.** High w is **anterior (frontal)**, not posterior. Three independent confirmations:

*(a) Anatomy.* I re-derived the regression myself from `somaLocation` of 550 right / 462 left
L1/L2/L3/L5 cells **[V]**:

| | right eye | left eye |
|---|---|---|
| x = | +16.8·u **+473.5·w** +10815 (R²=0.85) | −101.5·u **−426.9·w** +90412 (R²=0.84) |
| y = | **−580.0·u** +33.9·w +59116 (R²=0.996) | **−576.6·u** +84.6·w +55518 (R²=0.985) |
| z = | −51.7·u **−332.0·w** +28704 (R²=0.58) | +29.2·u **−397.3·w** +22463 (R²=0.74) |

and I fixed the volume axis convention from mean soma positions per ROI **[V]**:
AL(R) z=20 923 → PLP(R) z=30 258 → LO(R) z=33 783 → **LOP(R) z=37 652**, i.e. **z increases
posteriorly** (the lobula plate is posterior to the lobula — textbook, and it falls out of the
data). ME(R) x=16 164 → AL(R) x=41 874 → EB x=48 031, i.e. **x increases from the right lateral
edge toward the midline**. y: PB 13 322 (dorsal) → GNG 38 172 (ventral), i.e. **y increases
ventrally**.

Therefore z ≈ −332·w, so high w = low z = **anterior**. And the x coefficient flips sign between
eyes (+473 right, −427 left) — exactly what a *frontal-pointing* axis must do, since "toward the
midline" is +x on the right and −x on the left. Two axes, both eyes, all agreeing.

*(b) Function.* With the corrected azimuth the LC4→DN synaptic gradient reproduces Dombrovski
et al. 2023 exactly (section 5.4). With the uncorrected azimuth it reproduces it *backwards*.

*(c) Self-consistency.* y = −580·u at R² = 0.996 in both eyes makes u the pure dorsoventral axis,
leaving w as the anteroposterior one; the sign is then fixed by (a).

**Corrected, drop-in replacement:**

```java
// male-cns:v1.0 assignedOlHex1/2 -> visual direction.  Verified 2026-09-03.
static final int U_MIN = 7, U_MAX = 72, W_MIN = -19, W_MAX = 16;   // measured [V]
int u = hex1 + hex2, w = hex1 - hex2;
double uN = (u - U_MIN) / (double)(U_MAX - U_MIN);
double wN = (w - W_MIN) / (double)(W_MAX - W_MIN);
double elevationDeg = -70.0 + uN * 160.0;   // -70 = ventral, +90 = dorsal
double azimuthDeg   = 155.0 - wN * 165.0;   // 0 = straight ahead, +155 = ipsilateral rear,
                                            // -10 = just across the midline
// azimuthDeg is an IPSILATERAL magnitude: for the left eye, negate it to get world yaw.
```

The ±20–30 % peripheral error `sensory-mapping.md` warns about (Zhao 2025: ΔΦ varies across the
eye, hexagons are sheared) still stands **[P]** — this fixes polarity, not linearity.

### 2.1 Retinotopy for cells that carry no hex coordinate **[V]**

T4, T5, Tm3, LC4, LPLC2, Dm8 and all photoreceptors have `assignedOlHex1 = null`. I recovered a
receptive-field centroid for them by synapse-weighted label propagation on the connectome:
`pos(j) = Σ_i w_ij·pos(i) / Σ_i w_ij` over presynaptic partners on the same side, iterated 12×
from the 23 720 hex-carrying seeds; then one backward pass over *post*synaptic partners for the
sensory cells (photoreceptors have no presynaptic input). Coverage **106 302 / 106 579 neurons
(99.7 %)**, including 5 935 / 6 098 photoreceptors **[V]**. Converged after 4 iterations
(85 420 → 99 437 → 100 314 → 100 395 → 100 401). Resulting spans: T4a 1 684 cells over az −4..144°,
T5a 1 664 over −4..146°, LC4 az 25..129°, LPLC2 24..127° (LC/LPLC centroids are pulled inward
because their receptive fields are broad). Photoreceptor side is assigned from the majority side of
its targets. **This is the piece the mod needs and no published table provides**; it is validated
in section 5.4.

---

## 3. Why the ON pathway cannot be switched on — confirmed, with numbers **[V]**

The premise in the gap question is correct and I can now put synapse counts on it. Measured on my
subgraph (right eye, w ≥ 3 edges, summed over all cells of each type):

| edge | synapses | presynaptic sign |
|---|---:|---|
| L1 → Mi1 | 74 215 | **−1 (glutamate)** |
| L1 → Tm3 | 82 966 | **−1** |
| L1 → L5 | 70 917 | **−1** |
| L1 → C3 | 50 392 | **−1** |
| L1 → C2 | 27 954 | **−1** |
| L2 → Tm2 | 114 971 | +1 (ACh) |
| L2 → Tm1 | 110 011 | +1 |
| L3 → Tm9 | 25 423 | +1 |
| Mi1 → T4a | 58 887 | +1 |
| Tm3 → T4a | 24 062 | +1 |
| Mi9 → T4a | 20 360 | **−1** |
| Mi4 → T4a | 10 155 | **−1** |
| Tm1 / Tm2 / Tm9 → T5a | 20 210 / 30 225 / 30 954 | all +1 |

Every excitatory route out of L1 is absent: L1's five largest targets are all inhibited by it.
With a 0 Hz baseline, "inject sign-inverted luminance at L1" (`sensory-mapping.md` section c.5)
delivers inhibition to neurons that are already silent, i.e. nothing. Measured directly: in recipe
(i) with L1 driven at 57.1 Hz, **Mi1 = 0.0 Hz (7 of 887 cells ever spiking) and Tm3 = 0.0 Hz (0 of
1 037 cells)**, and **T4a/T4b/T4c/T4d = 0.00 Hz in every one of the 40 loom runs** **[V]**.

Two consequences that decide the rest of this report:

* **LC4 does not need T4/T5.** Its input census is T2 17.9 %, TmY3 16.0 %, Tm4 12.0 %, Tm2 7.8 %,
  Tm3 5.9 %, **T4 total 0.0 %, T5 total 0.8 %** (of 128 247 synapses) **[V]**. LC4 is reachable
  from the OFF pathway alone. This is why recipe (i) works at all.
* **LPLC2 does need them.** Its input census is **T5 21.6 %, T4 13.7 %** (of ~148 k synapses)
  **[V]**. With T4 dead and T5 near-dead, LPLC2 gets nothing — measured 0.0–0.1 Hz across all
  recipe-(i) runs. So recipe (i) delivers Ache's **velocity term only**, which is precisely the
  experimental condition Ache et al. 2019 created by silencing LPLC2 **[S, from abstract: "LPLC2
  silencing eliminates the size component of the GF looming response, leaving only the velocity
  component"]**.

---

## 4. Recipe (ii): the tonic-bias idea fails, and fails instructively **[V]**

### 4.1 A 3–6 mV bias is exactly 0 Hz

Threshold is 7 mV above rest (−45 vs −52 mV). For a constant bias `b` mV with no noise, the
interspike interval is `τ_m·ln(b/(b−7)) + t_ref`, so **[V]**:

| bias (mV) | 3.0 | 5.0 | 6.0 | 6.9 | 7.1 | 8 | 10 | 12 | 14 | 18 | 25 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| rate (Hz) | **0** | **0** | **0** | **0** | 11.4 | 22.8 | 38.1 | 50.7 | 62.3 | 83.0 | 114.0 |

`lif-model.md` section 6 item 3 proposes "a constant g_bias of 4–6 mV, i.e. a few mV below
threshold". A few mV below threshold is a few mV below *ever firing*. Confirmed in simulation: with
the bias set to exactly balance the mean histaminergic drive plus 0/2/4 mV, L1/L2/L3 all sat at
0.0 Hz.

### 4.2 The bias also has to cancel a large standing inhibition

Photoreceptor-to-lamina synapse budget per cell, right eye **[V]**: L2 133.8, **L1 103.5**, L3 27.9,
L5 11.0, T1 7.9, C3 4.5, L4 3.8, C2 3.8. Steady state is `g = N·W_syn·λ·τ_syn`, so at
W_syn = 0.275 mV and τ_syn = 5 ms, L1's standing inhibition is **2.85 mV at λ=20 Hz, 7.11 mV at
λ=50 Hz, 14.2 mV at λ=100 Hz, 21.3 mV at λ=150 Hz** **[V]**. The correct bias is therefore
`bias_i = N_hist(i)·W_syn·τ_syn·λ_background + (>7 mV)`, computed per neuron — not a flat constant.
λ_max ≈ 50 Hz is the right scale (it puts the full luminance swing across the 0–20 mV band where
the f–I curve lives); λ_max = 150–300 Hz drives L1 into ±60 mV swings and pure saturation.

### 4.3 With the bias set correctly, the OFF pathway inverts

Full-field luminance step (bright → dark at 100 ms → bright at 250 ms), λ_max = 50 Hz, bias =
auto-balance + 10 mV on the lamina and + 10 mV on nine medulla types. Population rates, Hz **[V]**:

| cell | base | OFF | Δ | expected |
|---|---:|---:|---:|---|
| L1 | 30.7 | 45.8 | **+15.1** | up — correct (histamine released) |
| L2 | 12.2 | 24.9 | **+12.7** | up — correct |
| Mi1 | 14.8 | 1.8 | −13.0 | down — correct (ON channel off) |
| Tm3 | 20.8 | 6.0 | −14.8 | down — correct |
| **Tm1** | 19.6 | 10.2 | **−9.5** | should go **up** — WRONG |
| **Tm2** | 22.8 | 13.6 | **−9.2** | should go **up** — WRONG |
| **Tm9** | 25.1 | 8.7 | **−16.4** | should go **up** — WRONG |
| **LC4** | 12.5 | **0.0** | −12.5 | should go **up** — WRONG |
| **LPLC2** | 7.6 | **0.0** | −7.6 | should go **up** — WRONG |
| **DNp01** | **212.5** | 206.7 | −5.8 | should be **0** at baseline |

The lamina half of the mechanism works — the disinhibition story is real, L1 and L2 both go up at
light OFF, and the ON channel correctly shuts down. But every OFF-channel cell goes *down*, LC4 and
LPLC2 are driven to **zero by a dark stimulus**, and the bias alone parks DNp01 at **212 Hz with no
stimulus present**. In the mod that is a fly that takes off permanently and stops taking off when
something approaches.

The reason is structural: a flat bias injects a large amount of *unstructured* excitation; the
inhibitory cells it recruits (C2/C3 GABA, Mi4 GABA, Mi9 and L1 glutamate, T1 histamine — **39 272 of
106 579 neurons in this subgraph are inhibitory, 36.8 %** **[V]**) then dominate the balance, and
the sign of everything downstream flips. There is also *no* bias setting that separates the two
requirements: lamina bias alone leaves the whole medulla at 0 Hz (Mi1 = Tm3 = LC4 = LPLC2 = DNp01 =
0.0 Hz for every extra of 8/10/12/15 mV **[V]**); adding ≥ 8 mV of medulla bias is what wakes the
network up, and it is also what lights DNp01 permanently.

**Verdict: reject.** The idea is biophysically right — this *is* how the fly does it, and it is what
flyvis implements — but flyvis gets away with it because it has a **learned per-cell-type resting
potential and a learned per-type time constant** (734 free parameters over 64 types), not one global
constant **[S, Lappalainen et al. 2024]**. Reproducing it needs that fitting step, which is out of
scope for the mod.

---

## 5. Recipe results in detail

### 5.1 What was simulated **[V]**

* **Graph.** `male-cns:v1.0`, superclasses `ol_intrinsic` (89 403) + `visual_projection` (9 201) +
  `ol_sensory` (6 098) + `visual_centrifugal` (563) + `descending_neuron` (1 314) = **106 579
  neurons**. Edges with weight ≥ 3 between them: **5 210 567 connections / 40 883 641 synapses**.
  (For reference the full unthresholded visual subgraph is 12 490 360 edges / 49 781 587 synapses;
  weight ≥ 5 gives 2 885 598 / 32 308 552 **[V]**.) Anonymous Cypher, no token; 48 chunks by
  presynaptic bodyId range, about 4 minutes total.
* **Model.** Shiu et al. 2024 exactly: V_rest = V_reset = −52 mV, V_th = −45 mV, τ_m = 20 ms,
  τ_syn = 5 ms, t_ref = 2.2 ms, delay 1.8 ms, sign = −1 for GABA/glutamate/**histamine** and +1
  otherwise, w = sign·N_syn·W_syn. dt = 0.1 ms, exact integration
  (a = 0.99501248, b = 0.98019867, c = 0.00493794), g frozen during refractory and zeroed on spike,
  18-slot delay ring. Poisson-driven cells get a fixed **68.75 mV** kick and t_ref = 0.
  *One deliberate deviation from Shiu:* he ties the kick to `W_syn·250`; I pinned it at 68.75 mV so
  that lowering W_syn in the calibration sweep does not silently stop the input neurons firing.
* **Validation — Chen & Xi replication** (LC4+LPLC2 right, 100 %, flat 150 Hz, W_syn = 0.275):

  | | mine **[V]** | Chen & Xi 2025 **[P]** |
  |---|---|---|
  | DNp01 | 426.7 Hz, first spike **3.0 ms** | > 150 Hz, ~5 ms |
  | DNp04 | 423.3 Hz, 2.7 ms | > 100 Hz, ~5 ms |
  | DNp02 | 353.3 Hz, 3.7 ms | > 100 Hz |
  | DNa02 | ipsi 36.7 Hz @ 59 ms; contra 6.7 Hz @ 168 ms | contra > 20 Hz @ ~22 ms; **ipsi silent** |

  Triggers, latencies and the trigger/postural/steering split reproduce. **DNa02 laterality does
  not**: mine is ipsi-dominant, theirs contra-dominant. Most likely because my subgraph contains no
  central-brain relays — DNa02's visual drive here is LLPC1/LT51/HSS/H2, all ipsilateral. Do not
  trust DNa02 steering sign from this subgraph; see section 7.

### 5.2 Recipe (i) — Poisson to L1 (ON) / L2+L3 (OFF)

Full grid, 4 l/v × 4 azimuths, r_max = 200 Hz, W_syn = 0.275. Cells read
*DNp01 Hz / first spike ms — DNp02 Hz / DNp11 Hz* **[V]**:

| l/v (ms) | az 30° (frontal) | az 60° | az 90° | az 120° (rear) |
|---|---|---|---|---|
| 10 | 36.9 / 57.8 — **46.1 / 0.0** | 46.1 / 64.9 — 36.9 / 0.0 | 18.4 / 78.9 — 18.4 / 0.0 | 46.1 / 58.7 — **0.0 / 36.9** |
| 20 | 50.7 / 93.1 — **50.7 / 0.0** | 46.1 / 105.7 — 32.3 / 0.0 | 32.3 / 120.9 — 27.6 / 4.6 | 36.9 / 75.7 — **9.2 / 27.6** |
| 40 | 43.8 / 170.7 — **41.5 / 0.0** | 48.4 / 163.6 — 29.9 / 0.0 | 34.6 / 249.5 — 20.7 / 4.6 | 41.5 / 87.8 — **9.2 / 20.7** |
| 80 | 29.9 / 360.8 — **27.6 / 0.0** | 53.0 / 445.6 — 23.0 / 4.6 | 27.6 / 474.9 — 15.0 / 1.2 | 21.9 / 87.8 — **4.6 / 9.2** |

* **DNp01 fires on every loom**, 18–53 Hz, and **first-spike latency scales with l/v** (58–79 ms for
  l/v = 10 ms, 361–475 ms for l/v = 80 ms). Angular size at the first spike: full angle **28.0°** for
  l/v = 10 ms but **14.4°** for l/v = 40 ms **[V]** — so this is not a constant-angular-threshold
  trigger, it fires proportionally earlier (in angle) for slow looms. Real GF spike timing is close
  to a fixed angular threshold plus a delay **[S, von Reyn 2014 via the Ache 2019 abstract]**.
  A deviation to be aware of; it does not break the demo.
* **Specificity is good** — this is the decisive table (`final.json`) **[V]**:

  | W_syn | r_max | static scene | drifting 12° object, 90°/s | loom l/v=10 az=30 | loom l/v=40 az=30 |
  |---|---|---|---|---|---|
  | 0.275 | **200** | **0.0 Hz** | **0.0 Hz** | 27.6 Hz @ 74 ms | 53.0 Hz @ 140 ms |
  | 0.275 | 400 | 0.0 Hz | **60.0 Hz — false escape** | 73.7 Hz | 101.4 Hz |
  | 0.150 | 200 | 0.0 | 0.0 | **0.0 — misses** | 0.0 |
  | 0.150 | 400 | 0.0 | 0.0 | 9.2 Hz @ 99 ms | 9.2 Hz @ 347 ms |
  | 0.080 | 200 / 400 | 0.0 | 0.0 | **0.0 — misses** | 0.0 |

  Only **W_syn = 0.275 with r_max = 200** gives escape-on-loom and no-escape-otherwise. r_max = 400
  makes a harmless drifting object trigger takeoff.
* **LC4 velocity tuning is emergent but compressed**: 4.4 / 3.5 / 3.1 / 2.8 Hz for
  l/v = 10/20/40/80 ms at az 60° — a **1.58× output range for an 8× input range** **[V]**. Correct
  sign, an order of magnitude too flat. Only 4–14 of 55 LC4 cells ever fire.
* **LPLC2 = 0.0–0.1 Hz** in every run. No size channel (section 3).
* **DNp02/DNp11 crossover is clean and emergent** — section 5.4.

### 5.3 Recipe (iii) — analytic drive to LC4/LPLC2, and what Chen & Xi actually did

**Correction to the premise.** Chen & Xi did *not* inject a dθ/dt-proportional rate into LC4 and a
Gaussian-in-θ rate into LPLC2. Verbatim from their Methods **[P]**: *"Sensory inputs (Visual looming,
Sugar) were modeled as Poisson spike trains delivered to specific receptor neurons (LC4/LPLC2, Sugar
GRNs)."* And from Results: *"we drove these inputs at high frequencies (150 Hz) to mimic the peak
synaptic drive generated during the terminal phase of a rapid loom"*, with *"virtual stress tests …
by activating random fractions of the looming-sensitive population"*. Flat rate, random subset, no
retinotopy, no time course. The Ache-shaped version described in the gap question is an *extension*
of their protocol that nobody has published.

I ran both **[V]**:

| variant | DNp01 | first spike | LC4 (Hz) l/v 10→80 | LPLC2 (Hz) l/v 10→80 | DNp02 / DNp11 at az 30 vs az 120 |
|---|---|---|---|---|---|
| **iii-flat** (Chen & Xi, 150 Hz, 100 %) | 424–427 Hz | **2.7 ms**, l/v-independent | 171→168 (flat) | 248→257 (flat) | 350/332 vs 350/332 — **no spatial signal** |
| **iii-uniform** (Ache shape, no retinotopy) | 387–399 Hz | 4.7–5.0 ms | 96.7→27.1 | 178→195 | n/a |
| **iii-retino** (Ache shape + Gaussian RF, σ=35°) | 322–382 Hz | 5.2–8.0 ms | 60.6→15.7 | 123→148 | **165.9/138.2 vs 92.1/184.3** |

* Ache's two-term structure appears **only because I injected it**: LC4 spans 3.85× for an 8× l/v
  range and LPLC2 is flat-to-slightly-rising — correct qualitative shape, but this is a tautology,
  not a test. Correlations with θ and dθ/dt (r = +0.53 to +0.97) are equally uninformative because
  θ and dθ/dt are themselves highly correlated during a loom.
* Chen & Xi's flat protocol **destroys all spatial information**: DNp02 and DNp11 fire
  indistinguishably (350 vs 332 Hz) regardless of stimulus azimuth. Copy their recipe and you cannot
  get escape direction out of the model at all.
* Everything is saturated: 424 Hz is the 2.2 ms refractory ceiling (~450 Hz). DNp01 firing here
  carries no graded information.
* **Hair-trigger.** Population stress test, DNp01 rate (Hz) vs fraction of LC4+LPLC2 activated at
  150 Hz **[V]**:

  | W_syn (mV) | 5 % | 10 % | 20 % | 30 % | 50 % | 70 % | 100 % |
  |---|---|---|---|---|---|---|---|
  | 0.275 | **253** | 340 | 380 | 393 | 407 | 417 | 427 |
  | 0.150 | 117 | 173 | 257 | 300 | 347 | 367 | 383 |
  | 0.080 | 63 | 113 | 160 | 197 | 257 | 290 | 320 |
  | 0.040 | 23 | 60 | 100 | 130 | 180 | 213 | 247 |
  | **0.020** | **0** | 17 | 53 | 77 | 117 | 147 | 177 |
  | 0.010 | 0 | 0 | 13 | 37 | 67 | 90 | 113 |

  Latency at W_syn = 0.020: 31.9 ms at 10 %, 14.5 ms at 30 %, 6.5 ms at 100 %.
  Chen & Xi report that ~30 % of the population is the threshold for a stable escape state on
  FlyWire **[P]**. Reproducing that threshold on male-cns needs **W_syn ≈ 0.02 mV — a 13.75×
  reduction from Shiu's 0.275**. `lif-model.md` section 8.5 predicted a reduction "roughly in
  proportion to the synapse-count inflation" (125 M / 52.8 M ≈ 2.4×); the measured requirement on
  this pathway is about 6× larger than that guess. Part of that is genuine reconstruction density
  (LC4→DNp01 alone is 2 580 synapses on one side **[V]**) and part is my subgraph omitting the
  central-brain inhibition that normally holds DNp01 back — I cannot separate the two here.

### 5.4 DNp02 vs DNp11 — the one result that is both emergent and correct **[V]**

**Static connectome test.** Using the propagated LC4 receptive-field centroids and the corrected
azimuth, over the 55 right-eye / 71 left-eye LC4 cells:

| | right eye | left eye |
|---|---|---|
| Spearman(azimuth, LC4→DNp02 synapses) | **−0.847** (p = 3.5e−16) | **−0.753** (p = 3.6e−14) |
| Spearman(azimuth, LC4→DNp11 synapses) | **+0.801** (p = 2.1e−13) | **+0.750** (p = 5.1e−14) |
| Spearman(DNp02 weight, DNp11 weight) | **−0.732** (p = 2.1e−10) | −0.653 (p = 6.6e−10) |
| mean synapses onto DNp02 from anterior / posterior LC4 | 46.7 / 23.9 (**1.95×**) | 41.1 / 23.4 (1.76×) |
| mean synapses onto DNp11 from anterior / posterior LC4 | 12.8 / 46.2 (**0.28×**) | 13.7 / 42.7 (0.32×) |

Dombrovski et al. 2023 Nature (PMC9849133) report exactly this: *"antiparallel synaptic number
gradients along the lobula anterior–posterior (A–P) axis for DNp02 and DNp11"*, a *"strong negative
correlation between the number of synapses a given LC4 makes with DNp11 and with DNp02"*, DNp02
biased to **anterior** LC4 and driving **backward** takeoff, DNp11 to **posterior** LC4 driving
**forward** takeoff, and DNp02 producing *"44 versus 13 spikes"* for anterior versus posterior
stimuli **[P]**. The male CNS reproduces the female-brain result quantitatively, in both eyes.
This is also the functional confirmation of the azimuth correction in section 2 — the sign only
comes out right with the corrected map.

**Dynamic test.** Recipe (i), W_syn = 0.275, r_max = 200 **[V]**:

| stimulus azimuth | DNp02 (Hz) | DNp11 (Hz) | fly should jump |
|---|---:|---:|---|
| 30° (frontal) | **46.1** | 0.0 | backward — correct |
| 60° | 36.9 | 0.0 | backward — correct |
| 90° (lateral) | 18.4 | 0.0 | — |
| 120° (rear) | 0.0 | **36.9** | forward — correct |

A complete, correctly-signed crossover, produced by the connectome and the derived retinotopy with
no hand-tuning. **This is the single strongest argument for recipe (i)**: it is the only recipe that
yields escape *direction*, not just escape *detection*.

### 5.5 T4/T5 direction selectivity — absent at every gain **[V]**

Moving OFF and ON edges swept front→back and back→front at 100 and 300 °/s. Because recipe (i)
leaves T4/T5 silent, I also ran a **best-case** drive that bypasses the lamina entirely and injects
Poisson trains straight into T4's and T5's own columnar inputs (Mi1 + Tm3 for ON; Tm1 + Tm2 + Tm9 +
Tm4 for OFF), reaching Mi1 = 59.9 Hz and Tm1 = 70.3 Hz.

DSI = (R_front→back − R_back→front) / (sum):

| drive | W_syn | pol | °/s | T4a | T4b | T4c | T4d | T5a | T5b | T5c | T5d |
|---|---|---|---|---|---|---|---|---|---|---|---|
| medulla | 0.275 | OFF | 100 | −0.072 | −0.093 | −0.115 | −0.127 | +0.004 | +0.065 | +0.077 | +0.076 |
| medulla | 0.275 | OFF | 300 | −0.046 | −0.103 | −0.109 | −0.150 | +0.021 | +0.040 | +0.057 | +0.061 |
| medulla | 0.275 | ON | 100 | +0.054 | +0.050 | +0.094 | +0.095 | −0.020 | −0.057 | −0.081 | −0.079 |
| medulla | 0.275 | ON | 300 | +0.026 | +0.020 | +0.058 | +0.060 | −0.021 | −0.029 | −0.054 | −0.053 |
| medulla | 0.080 | either | either | all ≤ 0.09 in magnitude, mostly unresolvable | | | | | | | |
| lamina (recipe i) | any | either | either | **T4 = 0.00 Hz** | | | | T5 ≤ 0.2 Hz | | | |

**Answer: no direction selectivity.** |DSI| ≤ 0.15 everywhere, and — the decisive point — **all four
T4 subtypes share the same sign in every condition, and all four T5 subtypes share the opposite
sign**. Real T4a–d have four *different* cardinal preferred directions, so a common sign across
a/b/c/d is the signature of no DS at all: what I am measuring is a shared ON/OFF-polarity and
edge-onset artefact, not a Reichardt correlation.

The mechanism is clear and it is not a gain problem: the model has **one global synaptic time
constant (5 ms) and one global delay (1.8 ms)**, so the fast arm (Mi1/Tm3) and the slow arm
(Mi9/Mi4/CT1) of the elementary motion detector have *identical* temporal filters. Without an
asymmetric delay line there is no direction selectivity to find, however the input is injected.
flyvis obtains DS on the same connectome only because it fits a **per-cell-type time constant**
(initialised at 50 ms, clamped ≥ dt) along with per-type rest potentials **[S, Lappalainen et al.
2024, Nature 634:1132]**. Consistent with this, HSE/HSS fire at 191/196 Hz under the best-case drive
but carry no flow direction, since their T4/T5 input is not DS **[V]**.

---

## 6. Recommendation

**Adopt recipe (i), OFF-only, at W_syn = 0.275 mV and r_max = 200 Hz.**

```java
// --- per column c of eye E, every tick ---
// L  = luminance 0..1 from the raycast assigned to that column's (az,el)
// Lf = one-pole low-pass of L,  tau_adapt = 200 ms   (50 ms if you want crisp motion)
Lf += (dt / tau_adapt) * (L - Lf);
double dec  = clamp(Lf - L, 0, 1);                    // luminance decrement = OFF contrast
double rate = 200.0 * (dec + 0.30 * (1.0 - L));       // Hz; 0.30 = sustained fraction

poissonDrive(L2[c], rate);   // 893 cells/eye, cholinergic
poissonDrive(L3[c], rate);   // 892 cells/eye, cholinergic
// L1: DO NOT DRIVE. It is glutamatergic/inhibitory; driving it only suppresses Mi1/Tm3.
// Poisson event -> v += 68.75 mV; driven cells have refractory 0.
```

Why this and not the others:

* It is the **only** recipe with correct specificity: silent for a static scene, silent for a
  drifting non-looming object, fires DNp01 on every loom (section 5.2).
* It is the **only** recipe that produces the **DNp02/DNp11 escape-direction crossover** emergently
  (section 5.4). Recipe (iii)-flat has no direction at all; recipe (ii) has no signal.
* DNp01 latency scales with l/v, giving free and physically sensible urgency behaviour.
* It costs 1 785 Poisson generators per eye instead of ~6 000 graded photoreceptor updates plus a
  106 579-element bias vector.

**Do not lower W_syn for this recipe.** The 0.02 mV figure from section 5.3 applies only if you
inject LC4 and LPLC2 directly at 150 Hz; under recipe (i) the LC4 population never approaches those
rates, and W_syn ≤ 0.15 makes the model miss looms entirely. If you later add a direct LC4/LPLC2
injection path (a scripted scare event, say), give **that path** its own gain — a single global
W_syn cannot serve both the escape pathway and the columnar motion pathway (T4 needs ~0.275 to fire
at all; DNp01 needs ~0.02 not to hair-trigger). Per-pathway gain is the honest fix and is what the
one published model that gets both right (flyvis) actually does.

### Optional hybrid, if the looming demo needs to look better

Keep recipe (i) as the substrate and add an **analytic LPLC2 size channel only**. LPLC2 is dead in
recipe (i) for a structural reason (it needs T4), so supplying it analytically restores Ache's
second term without faking the part that already works:

```
LPLC2 rate = 120 Hz * exp(-(theta - 45deg)^2 / (2*(25deg)^2)) * exp(-d_az^2 / (2*(35deg)^2))
```

with a **separate** gain of W_syn ≈ 0.02 mV on LPLC2's outgoing edges. Declare it as a hand-built
prosthesis in the docs. θ_pref = 45° and σ_θ = 25° are my choices, not Ache's measured values —
Ache et al. 2019 (Curr. Biol. 29:1073, doi 10.1016/j.cub.2019.01.079, PMID 30827912) is **not open
access and has no PMC record**; I could only read the abstract, which confirms the "model summing
angular velocity and angular size functions" but gives no coefficients **[S]**. Getting the real
numbers requires the paywalled figures.

---

## 7. What must be declared hand-built

| Behaviour | Status | Why |
|---|---|---|
| **Looming escape / takeoff** | **Connectome-driven** | Recipe (i) → LC4 → DNp01. Real. |
| **Escape direction (forward/backward)** | **Connectome-driven** | DNp02/DNp11 crossover, section 5.4. The best result in this report. |
| **Angular-size (Ache) term** | **Hand-built** | LPLC2 needs T4; T4 is unreachable. |
| **All optomotor / course stabilisation** | **Hand-built** | Requires T4/T5 DS, which does not exist at any gain (section 5.5). HSE/HSS/H2/VS carry no flow signal. |
| **Yaw steering from optic flow (DNa02)** | **Hand-built** | No flow input, *and* DNa02 laterality is wrong in this subgraph versus Chen & Xi (section 5.1). |
| **Object tracking / courtship pursuit (LC10a, LC11, LC15, LC18)** | **Hand-built** | These need small-object motion, i.e. T4/T5. Untested here, but the same barrier applies. |
| **Backward walking from LC16** | **Hand-built** | LC16 = 0.0 Hz in every run **[V]**. |
| **Sun/sky compass (MeTu→AOTU), polarisation (DRA)** | **Hand-built** | Not tested; needs the R7/R8 chromatic channels this recipe does not drive. |
| **Gaze stabilisation (DNp20/DNp22)** | **Hand-built** | Ocellar-dominated (`sensory-mapping.md` section c.4), and no ocellar input exists in this graph. |
| **"One spike = one takeoff"** | **Hand-built decoder rule** | The model gives DNp01 a 28–53 Hz train; the real GF fires a single spike. Latch on the first spike and impose a decoder-level refractory (~200 ms). |

So: **the escape demo is real; everything motion-based is not.** That is a defensible product
position — the looming-escape pathway is exactly the one the connectome literature has validated
end to end, and it is the one that survives here.

---

## 8. Honest limits of this study

* **Subgraph, not whole brain.** 106 579 neurons; no `cb_intrinsic` (32 k), no VNC. DNs therefore
  lack their central-brain inhibition and are more excitable than they should be. This inflates the
  DNp01 hair-trigger and is the most likely cause of the DNa02 laterality mismatch. **[V/L]**
* **Edge threshold w ≥ 3**, keeping 40.9 M of 49.8 M synapses (82 %). Shiu uses no threshold. **[V]**
* **Trials.** Most cells are 1 trial (2 for the bias sweeps), not Shiu's 30. Rates below ~5 Hz in
  small populations (LC4 n = 55, DNp01 n = 1) are noisy; the l/v and azimuth *trends* replicate
  across 16 independent grid cells, which is what I rely on. **[L]**
* **Retinotopy is derived, not published.** The propagation in section 2.1 is validated by the
  Dombrovski gradient and by DRA elevation, but LC4/LPLC2 centroids are compressed toward the eye
  centre (25–129° rather than a full hemifield) because broad receptive fields average out. The
  Reiser lab's eyemap (`github.com/reiserlab/male-drosophila-visual-system-connectome-code`, and the
  Male CNS Cell Type Explorer's advertised eyemaps) would be the authoritative source; I did not
  obtain it.
* **θ_pref and σ_θ for LPLC2 are invented** — Ache 2019 is paywalled (section 6).
* **The web-search budget was exhausted at the start of this session**, so I worked from WebFetch
  plus the primary texts already in the scratchpad (`escape_feeding.txt` = Chen & Xi full text,
  `model.py` = Shiu's actual code, `pmc.txt` = Shiu full text). Ache 2019 and Klapoetke 2017 were
  not read in full.
* **No gap junctions, no neuromodulation, no receptor dynamics** — inherited from Shiu. The GF is
  known to be electrically coupled, which the model cannot express.

---

## 9. Sources

* **Shiu PK, Sterne GR, Spiller N, Blagburn JM, … Scott K.** *A Drosophila computational brain model
  reveals sensorimotor processing.* Nature **634**:210–219 (2024). doi:10.1038/s41586-024-07763-9;
  PMC11446845. Code: github.com/philshiu/Drosophila_brain_model. Parameters read from `model.py`
  verbatim. **[P]**
* **Chen W-Q, Xi W.** *Whole-brain connectomics of Drosophila reveals a robust, distributed
  architecture for the suppression of feeding during escape.* bioRxiv, 16 Dec 2025,
  doi:10.64898/2025.12.14.694122. Methods and Results read in full from local text. **[P]**
* **Dombrovski M, Peek MY, Park JY, Vaccari A, Sumathipala M, Morrow C, Breads P, Zhao A,
  Kurmangaliyev YZ, Sanfilippo P, Rehan A, Polsky J, Alghailani S, Tenshaw E, Namiki S,
  Zipursky SL, Card GM.** *Synaptic gradients transform object location to action.* Nature (2023).
  doi:10.1038/s41586-022-05562-8, PMID 36599984, **PMC9849133**. Full text fetched. **[P]**
* **Ache JM, Namiki S, Lee A, Branson K, Card GM.** *Neural Basis for Looming Size and Velocity
  Encoding in the Drosophila Giant Fiber Escape Pathway.* Curr. Biol. **29**(6):1073–1081 (2019).
  doi:10.1016/j.cub.2019.01.079, PMID 30827912. **No PMC record, not open access — abstract only.**
  **[S]**
* **Lappalainen JK, Tschopp FD, Prakhya S, … Turaga SC.** *Connectome-constrained networks predict
  neural activity across the fly visual system.* Nature **634**:1132–1140 (2024).
  doi:10.1038/s41586-024-07939-3. Cited via `lif-model.md` section 4.9. **[S]**
* **Berg S, Beckett IR, Costa M, Schlegel P, et al.** male CNS connectome; neuPrint `male-cns:v1.0`.
  All neuron and edge numbers here measured directly from the API. **[V]**
* Local prior reports corrected or extended by this one: `lif-model.md` (section 6 bias, section 8.5
  W_syn, open question 4 — now answered), `sensory-mapping.md` (section c.2 axis polarity, section
  c.3 "drive L1" recommendation, section c.5 vision map).
* neuPrint endpoint: `POST https://neuprint.janelia.org/api/custom/custom`, body
  `{"cypher": ..., "dataset": "male-cns:v1.0"}`, **no token required**. **[V]**
