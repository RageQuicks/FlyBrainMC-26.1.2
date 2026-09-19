# Descending Neuron → Behavior Decoding Table (male-cns:v1.0 → Minecraft fly mob)

Research report. Date: 2026-09-03. All neuPrint facts queried live against `male-cns:v1.0`
via anonymous Cypher POST to `https://neuprint.janelia.org/api/custom/custom`.

Confidence legend:
- **HIGH** = stated directly in a peer-reviewed primary source, or verified by my own neuPrint query.
- **MED** = stated in a source I read but via a summarizing fetch, or inferred from two consistent sources.
- **LOW** = plausible/indirect; do not build a hard dependency on it.
- **UNKNOWN** = explicitly *not* found. Marked so you don't guess.

---

## 0. Executive summary for the mod

1. Almost every DN you listed is **exactly one cell per hemisphere** in male-cns (verified). So
   "read a DN" = read 2 spike trains (L and R). This makes lateralized decoding trivial:
   `turn ∝ rate(R) − rate(L)`.
2. The literature supports **strong, reliable behavior claims for only ~20 of the ~60 DN types**
   you listed. The rest (most `DNg*` from Sterne 2021, DNp27/32/42/49, DNa03/06/11/13/14) have
   **no published activation phenotype**. Do not fabricate mappings for them — either leave them
   unused or bucket them by connectome-derived VNC target.
3. The single highest-value decoding fact: **DN firing leads behavior by ~150 ms** (Rayshubskiy;
   Yang 2024). That sets your rate-estimation window.
4. Naming: the connectome's own `synonyms` property is the authoritative bridge between
   `male-cns` type names and paper nicknames. I dumped all 80 of them (§6).
5. **Critical correction to the project brief**: `DNg100` is *not* a halting neuron. It is
   `Sapkal 2024: BDN2`, a **forward-walking-promotion** DN. Likewise `DNge053`=BDN1,
   `DNg55`=BDN3, `DNge050`=BDN4, `DNg97`=oDN1 — all walk-promoting. The *halting* neurons from
   Sapkal 2024 are **Bluebell = DNg60** (GABA) and **Brake = AN19A018** (an *ascending* neuron,
   12 cells). "Foxglove" is an SEZ local neuron and is **not annotated in male-cns** (0 hits for
   "oxglove" in `synonyms`).

---

## 1. Master table: DN type → behavior

Columns: type | behavior on activation | laterality / cells (male-cns verified) | direction & sign |
typical activity | confidence | citation.

`nt` = `consensusNt` from male-cns (verified by query).

### 1.1 Locomotion — forward drive

| Type | Behavior | Laterality / cells | Direction & sign | Typical activity | Conf | Citation |
|---|---|---|---|---|---|---|
| **DNp09** (P9) | Forward walking **with ipsilateral turning**; at sustained/high drive transitions to **freezing**; freeze probability *anti*-correlated with current speed | 1/hemi (L 10783, R 11177), ACh | +forward, +ipsi-turn. Bilateral = fast forward; unilateral = curved pursuit | Not measured in spikes/s anywhere I found (UNKNOWN). 15 s optogenetic pulses at 5 mW/cm² gave run→freeze cycles | HIGH (behavior), UNKNOWN (rate) | Bidaye 2020 Neuron; Zacarias 2018 Nat Commun; Cande 2018 eLife; Braun 2024 Nature |
| **DNg100** = *BDN2* | Forward walking initiation; works **even in headless flies**; activity strongly correlated with forward velocity | 1/hemi (L 10045, R 10056), ACh | +forward, graded with stimulus intensity | Graded: higher opto intensity → higher forward velocity | HIGH | Sapkal 2024 Nature; "A dedicated brain circuit controls forward walking" (bioRxiv 2026) |
| **DNge053** = *BDN1* | Forward walking (BPN/Bolt downstream) | 1/hemi (L 10844, R 11965), ACh | +forward | — | MED | Sapkal 2024 |
| **DNg55** = *BDN3* | Forward-walking module member — **but GABAergic**, so likely a modulator/gate rather than a driver | **Unpaired, midline, 1 cell total** (bodyId 15105, somaSide `M`), **GABA** | sign ambiguous | — | MED (identity HIGH, function MED) | Sapkal 2024; nt + unpaired status verified by query |
| **DNge050** = *BDN4* | Forward walking | 1/hemi (L 10301, R 256420), ACh | +forward | — | MED | Sapkal 2024 |
| **DNg97** = *oDN1* | Forward walking; "critical for forward walking initiation"; a main target of Foxglove inhibition | 1/hemi (L 13805, R 230783), ACh | +forward | — | MED-HIGH | Sapkal 2024 |
| **DNa01** | Steering; broad locomotor activation. Activity rises before turns but with **shallower** turn gain than DNa02 and more sustained dynamics | 1/hemi (L 10442, R 10760), ACh | +ipsi-turn (weak), some +forward | Not given in spikes/s (UNKNOWN). Opto-inhibition → only small steering deficits | HIGH (role), UNKNOWN (rate) | Rayshubskiy eLife 102230; Yang 2024 Cell; Cande 2018 |

### 1.2 Locomotion — steering / yaw

| Type | Behavior | Laterality / cells | Direction & sign | Typical activity | Conf | Citation |
|---|---|---|---|---|---|---|
| **DNa02** | **Yaw steering.** The best-characterized turn signal in the fly. Right-minus-left firing rate is ~**linearly** related to rotational velocity **through the entire dynamic range** | 1/hemi (L 523769, R 10360), ACh. **Ipsilateral projection** | Unilateral activation → **ipsiversive** turn bias. Ipsilateral excitation + contralateral inhibition | Absolute spikes/s **not reported** in the paper text I could read (UNKNOWN). Rotational-velocity axes span **±800 °/s**. DN activity compared to behavior **150 ms later** | HIGH | Rayshubskiy et al. eLife 102230 (2020 bioRxiv → 2025 eLife); Yang 2024 |
| **DNg13** | Steering by a **different gesture**: lengthens strides on the **outside** of the turn (both power and return stroke). Also classed by Cande as "anterior reaching movements" | 1/hemi (L 11074, R 512006), ACh. **Axon decussates** (crosses midline into VNC) | Ipsiversive turn; acts on contralateral legs | Current injection reliably drove **>100 spikes/s**; stride-locked modulation of DNa02 ≈ **10% of dynamic range**; same **150 ms** lead | HIGH | Yang, Brezovec, … Wilson, *Cell* 187:6290–6308 (2024) |
| **DNa03** | Mostly a **brain-internal** DN: most output stays in central brain, strong synapses **onto DNa02** | 1/hemi (L 519624, R 10975), ACh | Presumed upstream amplifier of DNa02 | UNKNOWN | MED | Yang 2024 / Rayshubskiy discussion |
| **DNb05** | Turn-correlated in Yang 2024's five-DN set. Anatomically unique: innervates **both optic and olfactory glomeruli** | 1/hemi (L 10118, R 10065), ACh. Synonym `Matsuo 2016: AMMC-Di7` | +turn (weak) | UNKNOWN | MED | Yang 2024; Namiki 2018 |
| **DNa11, DNa13, DNa14, DNa05, DNa06, DNa07** | Grouped as "locomotion" in Cande's behavior space; DNa05 and DNa07 land in the locomotion region. **No individual published phenotype** | 1/hemi each except **DNa13 = 2/hemi** (L 525960, 555726; R 11066, 516170) | UNKNOWN | UNKNOWN | LOW | Cande 2018 (coarse); Namiki 2018 (anatomy only) |
| **DNa04, DNa15** | Members of the **DNp03 flight-saccade network** (excitatory) | 1/hemi each, ACh | +turn during flight | UNKNOWN | MED | "DNp03 … hub within a flight saccade network", Curr Biol 2025 |

### 1.3 Backward walking

| Type | Behavior | Laterality / cells | Direction & sign | Typical activity | Conf | Citation |
|---|---|---|---|---|---|---|
| **MDN** (moonwalker; syn. `Carreira-Rosario 2018: DNp50`) | **Backward walking.** Simultaneously *activates* backward and *inhibits* forward locomotion (not by reciprocal inhibition — via separate downstream paths). Required for backing away from an impassable barrier | **2 per hemisphere, 4 total**: L 11288, 12348; R 10763, 11332. ACh | Bilateral → straight backward. **Unilateral → backward + turning** (one recent preprint calls it contraversive) | **Baseline 8.5 Hz**; rises to ~**12–16 Hz** on antennal touch; median spike amplitude 6.5 mV; resting Vm ≈ −39 mV. Response within **100 ms**. Graded: higher rate ↔ faster backing. A **300 ms** pulse suffices to trigger a full backward bout | HIGH (behavior), MED (rates) | Bidaye 2014 Science 344:97; Sen 2017; Carreira-Rosario 2018 eLife 7:e38554; Feng 2020 Nat Commun 11:6166; "Control of walking direction by descending and dopaminergic neurons" bioRxiv 2025 |

MDN's VNC effectors (useful if you want sub-behavior detail): **LBL40** gives the hindleg power
stroke during stance; **LUL130** lifts legs at end of stance to start swing (Feng 2020, HIGH).

### 1.4 Halting / stopping / freezing

| Type | Behavior | Laterality / cells | Direction & sign | Typical activity | Conf | Citation |
|---|---|---|---|---|---|---|
| **DNg60** = *bluebell / BB* (also `Sterne 2021: mesa; snail`) | **Walk-OFF halting.** Predominantly suppresses the **turning** component driven by P9/DNp09; allows leg repositioning during the halt. Weaker/shorter halts than Brake | 1/hemi (L 11374, R 188947), **GABA** | −turn (and some −forward) | Halt bouts shorter than Foxglove's; "stationary periods interspersed with slow walking" | HIGH | Sapkal et al. *Nature* 634:191–200 (2024) |
| **Foxglove (FG)** | Walk-OFF halting; **specifically overrides the forward component** of P9 and BPN; long halt bouts; deployed during feeding/foraging; stronger in starved flies | **Not annotated in male-cns** — 0 synonym hits. FG is an **SEZ local neuron** that only stochastically has a short descending axon | −forward | Slower halt onset than BRK | HIGH (biology) / HIGH (absence from dataset, verified) | Sapkal 2024 |
| **AN19A018** = *BRK / Brake* | **Active braking**: arrest of stepping with **joint angles locked**; overrides **all** walking commands (forward, backward, turning). Deployed for stability during grooming | **Ascending neuron**, 6 per side, **12 cells total**, ACh. One per leg neuromere (T1/T2/T3 × L/R) | Global −locomotion, +joint stiffness | Low-latency, long-duration halts; locks legs in stance, permits completion of an in-flight swing | HIGH | Sapkal 2024; cell count verified by query |
| **DNp09** (again) | **Freezing** at sustained drive. Silencing DNp09 disrupts freezing but **not** fleeing | see above | −locomotion after an initial +run | Freeze probability rose over the 15 s stimulus and was **negatively correlated with pre-stimulus speed** | HIGH | Zacarias 2018 Nat Commun 9:3697 |
| **DNb01** | Anterior twitch of front legs at light ON → **freeze** for most of the stimulus → twitch at light OFF. Strongly **context-dependent** (highest mutual information with pre-stimulus state of any line) | 1/hemi (L 10654, R 10759), **glutamate** | −locomotion | 15 s stimulus | HIGH | Cande 2018 (line SS02542) |
| **DNg74_a/b** = *web* | Braun 2024's "cluster 2 web DNs": four GABAergic DNs each inhibiting **41–96** walking-related neurons. A connectome-level global stop/suppress signal | 2 types × 1/hemi = **4 cells**, **GABA** | −locomotion (broad) | — | MED | Braun 2024 Nature 630:686; Sterne 2021 |
| **DNg93** = *oval* | Sterne SEZ DN, **GABA**. No published activation phenotype | 1/hemi, GABA | UNKNOWN | UNKNOWN | LOW | Sterne 2021 |
| **DNd02 / DNp02** | Cande places these in the "slow movements" region | DNd02 = `Busch 2009: OA-VL1` (octopaminergic lineage) | −speed | — | MED | Cande 2018 |

### 1.5 Escape / takeoff / jump

| Type | Behavior | Laterality / cells | Direction & sign | Typical activity | Conf | Citation |
|---|---|---|---|---|---|---|
| **DNp01** = **Giant Fiber** (syn. `Kennedy and Broadie 2018: GF`) | **Short-mode escape takeoff**: jump *without* prior wing raise. **A single GF spike** is enough; relative spike timing vs. the parallel (non-GF) circuit selects short vs. long mode. Electrically coupled (gap junctions) to **TTMn** and to the **PSI** which drives **DLMn** | 1/hemi (**L 10010, R 10001**), ACh | +jump, escape away from loom | Short mode: only **5.6%** of flies raised wings >45°. Long mode: **94.4%** did. Whole jump completes in **~30 ms** — faster than a 100 fps camera's Nyquist | HIGH | von Reyn et al. Nat Neurosci 17:962 (2014); Ache 2019 Curr Biol 29:1073; Namiki 2018 |
| **DNp02** (`Matsuo 2016: P3b`) | With DNp04: **slower, controlled backward-directed escape**; promotes backward leaning before takeoff | 1/hemi (L 10197, R 10117), ACh | **backward** takeoff | — | HIGH | Dombrovski 2023 Nature 613:534; Ache 2019 Nat Neurosci 22:1132 |
| **DNp04** | Co-activated with DNp02 → backward escape. Innervates the **entire** LC4 glomerulus | 1/hemi (L 531898, R 11137), ACh | backward takeoff | — | HIGH | Dombrovski 2023; Namiki 2018 |
| **DNp11** (`Matsuo 2016: AMMC-Db3`) | **Forward-directed escape takeoff** | 1/hemi (L 10259, R 10106), ACh | **forward** takeoff | Loom responses attenuated when not flying | HIGH | Dombrovski 2023 |
| **DNp06** (`Matsuo 2016: AMMC-Di5`) | Contributes to looming-evoked takeoff. **Not** unilaterally tuned — responds equally to ipsi- and contralateral looms. Responds robustly even when not flying | 1/hemi (L 10228, R 10584), ACh | +takeoff, non-directional | — | MED | Ache 2019 Nat Neurosci |
| **DNp05** | Projects to leg neuropils; in the LC4 escape group. No dedicated phenotype paper | 1/hemi (L 524001, R 11020) | UNKNOWN | UNKNOWN | LOW | Namiki 2018 |
| **DNp03** | **Flight-saccade / collision-avoidance hub.** Unilateral activation → **contralateral** turn (right DNp03 raises right wing amplitude, lowers left → fly steers left). Only LC4-recipient DN that targets **wing** neuropil rather than lower tectulum | 1/hemi (L 10752, R 10989), ACh | **contra**-turn in flight | UNKNOWN (spikes/s) | HIGH | "Drosophila DNp03 descending neurons serve as a hub within a flight saccade network", Curr Biol 2025; Namiki 2018 |

**Escape-direction rule (usable directly in game code)**: LC4 looming neurons form *antiparallel*
synaptic gradients — **anterior** visual field → ipsilateral **DNp02** → **backward** takeoff;
**posterior** field → ipsilateral **DNp11** → **forward** takeoff. Individual LC4 cells make 1–75
synapses onto a given DN, anticorrelated between DNp02 and DNp11. (Dombrovski 2023, HIGH.)

### 1.6 Landing

| Type | Behavior | Laterality / cells | Direction & sign | Typical activity | Conf | Citation |
|---|---|---|---|---|---|---|
| **DNp07** | **Landing**: simultaneous extension of all six legs, forelegs reaching beyond the head. Spike rate **sets extension amplitude** | 1/hemi (**L 11704, R 11513**), ACh | +leg extension, graded | **92 Hz during flight → 27 Hz when not flying** (70% attenuation). Latency **26 ± 5 ms**. Largest extensions at **>150 Hz** | HIGH | Ache, Namiki, Lee, Branson, Card, Nat Neurosci 22:1132–1139 (2019) |
| **DNp10** | Landing, with a different middle/hind-leg posture than DNp07. In Cande's walking assay also produced anterior reaching + **wing flicking** with matched timing | 1/hemi (L 10425, R 10433), ACh | +leg extension | Supra-threshold visual response **completely eliminated** without flight. Latency **21 ± 4 ms** | HIGH | Ache 2019; Cande 2018 (line SS01049) |

**Flight gating is real and important**: both landing DNs are *decoupled* from vision when the fly
is not flying. Replicate this in the mod with a `flying` gate on the DNp07/DNp10 sensory drive,
not on the motor readout.

### 1.7 Flight power / gaze / neck

| Type | Behavior | Laterality / cells | Direction & sign | Typical activity | Conf | Citation |
|---|---|---|---|---|---|---|
| **DNg02** (subtypes `_a`…`_g`) | **Graded, continuous control of wingbeat amplitude / flight power** — a *population* controller, not a command neuron. Activating more cells → linearly larger amplitude change (**r² = 0.84**) | **29 cells total** in male-cns (a 10, b 5, c 4, d 2, e 2, f 2, g 4); "at least 15 pairs" in the paper | Right DNg02 correlates **positively with contralateral (left) wing amplitude**, negatively with ipsilateral → net yaw | Max activation → mechanical power ≈ **200 W/kg** | HIGH | Namiki, Ros, Morrow, Rowell, Card, Dickinson, eLife 2022; cell counts verified by query |
| **DNp15** = *DNHS1* (`Suver 2016`) | Horizontal-system-driven; **yaw** optic-flow → neck motor → gaze stabilization | 1/hemi (L 12069, R 11215), ACh | +head yaw | Baseline in flight **13.6 ± 5.0 Hz** | HIGH | Suver et al. J Neurosci 36:11768 (2016) |
| **DNp20** = *DNOVS1* | Ocelli + VS cells; **roll/pitch** optic flow → fast head movements for gaze stabilization | 1/hemi (L 10162, R 10059), ACh | +head roll/pitch | — | HIGH | Haag & Borst; Dorkenwald 2024 (synonym in dataset) |
| **DNp22** = *DNOVS2* | Ocelli + a different VS subset; nonlinear binocular optic-flow integration; neck motor + haltere/flight stabilization | 1/hemi (L 12306, R 11872), ACh | +head roll/pitch | Baseline in flight **24.5 ± 13.9 Hz** | HIGH | Haag et al. J Neurosci 28:3131 (2008); Suver 2016 |

### 1.8 Grooming

| Type | Behavior | Laterality / cells | Direction & sign | Typical activity | Conf | Citation |
|---|---|---|---|---|---|---|
| **DNg62** = *aDN1* | **Antennal grooming** (head sweeps by front legs) only. Bouts often **terminate before** the stimulus ends (~50% fewer sustained bouts than aDN2) | 1/hemi (L 13624, R 15148), ACh | +antennal groom | — | HIGH | Hampel, Franconville, Simpson, Seeds, eLife 4:e08758 (2015) |
| **DNge078** = *aDN2* | **Antennal grooming**, sustained for the whole stimulus. Braun's canonical "grooming comDN" | 1/hemi (L 14537, R 36541), ACh | +antennal groom | Alone (headless) drives only an incomplete "front leg approach"; needs its ~15–23-DN network for full grooming | HIGH | Hampel 2015; Braun 2024 Nature |
| **aDN1/aDN2 laterality** | **Unilateral** activation of aDN1 → **single-legged** head sweep | — | ipsilateral leg | — | HIGH | Guo, Zhang, Simpson, Curr Biol 2022 |
| **DNg12** (`_a`…`_h`) | **Head grooming**: front-leg rubbing alternating with head sweeps to the ventral head. Dendrites in GNG, axon to **ipsilateral T1**. Unilateral → single-legged head sweep. Also supplies BRK for head-grooming stability | **42 cells** in male-cns across 8 subtypes | +head groom, ipsilateral | — | HIGH | Guo 2022; Cande 2018; Sapkal 2024; counts verified |
| **DNg11** | **Front-leg rubbing only** (no head sweeps). Unilateral activation recruits **both** legs | 3/hemi, **6 cells**, **GABA** | +leg rub, bilateral effect | — | HIGH | Guo 2022; counts/nt verified |
| **DNg07 / DNg08** | **Head grooming**, sustained through the full 15 s window (Cande's exemplar of a sustained phenotype, line SS02635) | DNg07 1/hemi (syn. `Vaughan 2014: aPN(desc)`) | +head groom | — | HIGH | Cande 2018 |
| **DNp29** (`Scheffer 2020: NPF1`) | **Abdomen grooming** | 1/hemi (L 10552, R 10195), nt `unclear` | +abdomen groom | — | MED | Cande 2018 |
| **DNg10** | "Anterior reaching movements" (distinct from DNg13's) | **6/hemi, 12 cells**, **GABA** | +foreleg reach | — | MED | Cande 2018; counts verified |

### 1.9 Feeding / proboscis

| Type | Behavior | Laterality / cells | Direction & sign | Typical activity | Conf | Citation |
|---|---|---|---|---|---|---|
| **DNg67** = *Fudog* | Feeding-circuit **second/third-order** neuron; calcium responses to proboscis stimulation in **food-deprived** flies | 1/hemi (L 516215, R 22758), ACh | +PER (hunger-gated) | — | MED | Shiu, Sterne, Engert, Dickson, Scott, eLife 11:e79887 (2022) |
| **DNge080** = *Rounddown* | **Premotor** feeding neuron; target of bitter suppression (Scapula → Roundup/Rounddown) | 1/hemi (L 12752, R 12364), ACh | +PER, suppressed by bitter | — | MED | Shiu 2022 |
| **DNge173 / DNge174** = *Bract 2 / Bract 1* | Third-order feeding DNs projecting to the VNC | 1/hemi each, ACh | +feeding | — | MED | Shiu 2022 |
| **aSP22** = *DNa12* (`McKellar 2019: DNa12`) | Triggers a **sequence** of close-range courtship acts: **foreleg extension → proboscis extension → abdominal bending**. Sexually dimorphic (fru). Threshold-ordered: different acts appear at different activation levels | 1/hemi (**L 10090, R 10104**), ACh | +PER, +abdomen bend | Threshold-graded sequence | HIGH | McKellar et al. Curr Biol 2019; Stürner/Jefferis comparative DN connectomics, Nature 2025 |

**Motor-level feeding readout is better than the DN-level one.** `MN9` is **necessary and
sufficient** for rostrum extension — the single best proboscis-extension readout. Sugar GRN
stimulation in Shiu's LIF model activated **MN6, MN8, MN9 and MN11**; MN9 and MN11 are known to
respond to sugar in vivo. MN2 extends the haustellum, MN6 extends the labella, MN8 spreads the
labella, MN11 passes fluid from cibarium to esophagus, MN12 empties the cibarium (MN11/MN12 mutual
inhibition → pumping rhythm). (HIGH; Gordon & Scott 2009; Manzo 2012 PNAS; McKellar 2020 eLife
54978; Shiu 2024 Nature.) In male-cns, `MN9` = **2 cells**, subclass `pm`; there are **67 `pm`
(proboscis motor) neurons** total.

### 1.10 Courtship / song

| Type | Behavior | Laterality / cells | Direction & sign | Typical activity | Conf | Citation |
|---|---|---|---|---|---|---|
| **P1 / pC1** | Central courtship **command / arousal state**. Activation in a solitary male recapitulates the whole stillness → waggling → singing progression; produces a **minutes-long persistent** arousal state; drives **unilateral** wing extension | **49 subtypes, 156 cells** in male-cns (`pC1_1a`…`pC1_19`, `pC1x_a-d`), all ACh | +courtship gain (a *state*, not a movement) | Persists for minutes after stimulus offset | HIGH | von Philipsborn 2011 Neuron; Hoopfer 2015 eLife; Sequencing of wing behaviors, Curr Biol 2026; counts verified |
| **pIP10** | The courtship-song **DN**. Drives wing extension + song. Active during **both** pulse and sine, weak preference for **pulse**. Inputs to both dPR1 and TN1A-2 | 1/hemi (L 523998, R 11116), ACh. Synonyms `Kimura 2008/Kohatsu 2010: P2b; Cachero 2010: pIP-a; Yu 2010: pIP1` | +song, unilateral wing | — | HIGH | von Philipsborn 2011; Lillvis 2024 Curr Biol; Roemschied/Murthy 2024 Nat Neurosci |
| **pMP2** | **Pulse-song selective** DN. Calcium **increases** during pulse and **decreases** during sine. Primary driver of dPR1 | 1/hemi (L 10930, R 10765), ACh, male-specific | +pulse, −sine | Song-type-selective sign flip — an unusually clean binary readout | HIGH | Lillvis 2024; Roemschied 2024 |
| **pIP1** | Male-specific fru DN, courtship | 1/hemi (L 10030, R 10038), ACh | +courtship | UNKNOWN (specific act) | LOW | von Philipsborn 2011 |
| **vPR6** | VNC song CPG element (not a DN) | **8 cells** (4/side), ACh, `vnc_intrinsic` | +pulse patterning | — | HIGH | von Philipsborn 2011 |
| **dPR1** | VNC, **pulse-selective**; calcium drops sharply at pulse→sine transitions. Silencing reduces pulse song | **2 cells** (1/side), ACh, `vnc_intrinsic` | +pulse | — | HIGH | von Philipsborn 2011; Lillvis 2024 |
| **TN1a** (a–i) | VNC, **sine**-preferring; silencing TN1A reduces sine but not pulse | **22 cells** across 9 subtypes, ACh | +sine | — | HIGH | von Philipsborn 2011; Lillvis 2024 |
| **TN1c** (a–d) | VNC song circuit | **13 cells**, ACh | song patterning | — | MED | Lillvis 2024 |
| **vMS11** | VNC song CPG element | **14 cells**, **glutamate** | +pulse | — | MED | von Philipsborn 2011 |
| **vPR9** (a–c) | VNC, **GABA** — inhibitory song element | **9 cells**, GABA | −song / patterning | — | MED | Lillvis 2024 |
| **DNp13** (`Kimura 2015: pMN1; Ruta 2010: DN1`) | In **females**: command-type for **ovipositor extrusion**, responds to male song. In **males**: co-activation with pIP10 **increased sine and reduced pulse** | 1/hemi (L 10458, R 10388), ACh, dsx | song-mode bias | — | MED (male role), HIGH (female role) | Wang 2020; Lillvis 2024; Stürner 2025 |
| **DNa08** | Listed among the sexually dimorphic DN types; **no published activation phenotype** | 1/hemi (L 13509, R 11392), ACh | UNKNOWN | UNKNOWN | LOW | Stürner 2025 |

**Song acoustics (for the sound you emit in-game)**: pulse song = trains of **2–50 pulses**, each
1–3 cycles, carrier **150–300 Hz** (commonly quoted ~220–250 Hz), **inter-pulse interval ~35 ms**;
sine song = continuous hum at **~150–160 Hz**. Males extend and vibrate a **single** wing. (HIGH;
von Philipsborn 2011; Lillvis 2024.)

### 1.11 DNs with NO published activation phenotype

I searched specifically and found nothing behavioral for these. **Say "unknown", don't invent.**
All are 1 cell/hemisphere unless noted. Sterne 2021 explicitly gave these SEZ DNs *no* behavioral
attribution — they are morphological names only.

| Type | Nickname | nt | Sterne 2021 group | Status |
|---|---|---|---|---|
| DNg57 | bobber | ACh | Group 4 (inferior GNG → leg neuropil) | UNKNOWN |
| DNg61 | sink | ACh | Group 4 | UNKNOWN |
| DNg72 | genie | **glutamate**, 2/hemi (4 cells) | Group 5 | UNKNOWN |
| DNg77 | snake | ACh | Group 5 | UNKNOWN |
| DNg80 | gumdrop | **glutamate** | Group 5 | UNKNOWN |
| DNg93 | oval | **GABA** | Group 5 | UNKNOWN |
| DNge172 | mute | ACh, **asymmetric: L 1, R 3** | Group 5 | Tested in Braun 2024's DN set; no phenotype reported |
| DNg15 | nagini | ACh (also `Matsuo 2016: AMMC-Db5`) | Group 5 | UNKNOWN |
| DNg05_a/b/c | knees | ACh, 8 cells total | Group 6 — innervates **neck + wing** neuropil | UNKNOWN (anatomy suggests neck/wing) |
| DNp27, DNp32, DNp42, DNp49 | — | ACh / `unclear` / ACh / **glutamate** | — | DNp42 tested in Braun 2024 (no phenotype reported). DNp27/DNp32 have very large arbors with both in- and outputs across the cerebral ganglia (Namiki 2018) |
| DNp18 | `Matsuo 2016: AMMC-Di3` | ACh | — | Auditory input; no motor phenotype |
| DNb02 | — | **glutamate**, 2/hemi | — | Tested in Braun 2024; no phenotype reported |

---

## 2. Motor-neuron-level decoding (verified type names in male-cns:v1.0)

If you prefer to decode below the DN layer, these are the exact `type` strings present in the
dataset. Subclass codes: `wm`=wing motor, `hm`=haltere, `nm`=neck, `pm`=proboscis, `ad`=abdominal,
`fl`/`ml`/`hl`=front/middle/hind leg.

### 2.1 Wing motor neurons (`superclass=vnc_motor, subclass=wm`, 67 cells, 26 types)

**Power muscles — asynchronous, stretch-activated; drive BOTH flight and song:**

| Type | Neuromere | Cells | Muscle |
|---|---|---|---|
| `DLMn a, b` | T2 | 2 | dorsal longitudinal (wing downstroke) |
| `DLMn c-f` | T1 | 8 | dorsal longitudinal |
| `DVMn 1a-c` | T2 | 6 | dorsoventral (upstroke) |
| `DVMn 2a, b` | T2 | 4 | dorsoventral |
| `DVMn 3a, b` | T1 | 4 | dorsoventral |

→ **24 power MNs total.** These alone do **not** disambiguate flight vs. song (both use them).

**Steering muscles — phase-locked to the wingstroke, set wing orientation:**
`b1 MN`, `b2 MN`, `b3 MN` (basalar); `i1 MN`, `i2 MN` (first axillary); `iii1 MN`, `iii3 MN`
(third axillary); `hg1 MN`, `hg2 MN`, `hg3 MN`, `hg4 MN` (fourth axillary) — **2 cells each**.

**Indirect / thoracic-stiffness:** `ps1 MN`, `ps2 MN` (pleurosternal), `tp1 MN`, `tp2 MN`,
`tpn MN` (tergopleural) — 2 each. Plus `MNwm35`, `MNwm36` (2 each).

**Jump:** `TTMn` (tergotrochanteral, **2 cells, T2, classed `wm`**) — depresses the middle legs and
"jump-starts" the power muscles during escape takeoff; receives **direct gap-junctional input from
DNp01/GF**. `STTMm` (4 cells).

### 2.2 Flight vs. song vs. jump discrimination rule

This is the practical answer to your question:

- **JUMP** ⇔ `TTMn` fires (and DNp01 fired ~1–2 ms earlier). TTMn is essentially silent otherwise.
  Cleanest binary signal in the whole VNC. **HIGH confidence.**
- **FLIGHT** ⇔ power MNs (`DLMn*` + `DVMn*`) sustained **AND** haltere MNs (`hDVM MN`, `hi1 MN`,
  `hi2 MN`, `hiii2 MN`, `MNhm03/42/43`; 16 cells, T3) engaged **AND** steering MNs firing
  **bilaterally**. **MED-HIGH.**
- **SONG** ⇔ power MNs active **AND** the wing MN pattern is **strongly unilateral** (one wing
  only) **AND** haltere MNs quiet. Lillvis/Roemschied: "most of the control muscles are active
  during **pulse** song, whereas a **subset becomes inactive** during **sine** song" — so
  *pulse ⇔ more steering MNs recruited; sine ⇔ fewer*. Combine with `pMP2` sign (pulse ↑, sine ↓)
  for a robust two-source vote. **MED.**
- **LANDING** ⇔ all six legs' extensor MNs (`Ti extensor MN`, `Tr extensor MN`) co-activate while
  power MNs are still running. **MED.**

### 2.3 Leg motor neurons (functional names verified; front leg `fl` = 135 cells, 20 types)

`Acc. ti flexor MN` (19), `Acc. tr flexor MN` (6), `Fe reductor MN` (10),
`Pleural remotor/abductor MN` (4), `Sternal adductor MN` (2), `Sternal anterior rotator MN` (4),
`Sternal posterior rotator MN` (6), `Sternotrochanter MN` (4), `Ta depressor MN` (9),
`Ta levator MN` (5), `Tergopleural/Pleural promotor MN` (8), `Tergotr. MN` (8),
`Ti extensor MN` (4), `Ti flexor MN` (10), `Tr extensor MN` (4), `Tr flexor MN` (15),
`ltm MN` (8), `ltm1-tibia MN` (3), `ltm2-femur MN` (4).
Middle leg `ml` = 116 cells; hind leg `hl` = 130 cells; abdominal `ad` = 214; neck `nm` = 44;
haltere `hm` = 16; proboscis `pm` = 67; `xm` = 6.

Promotor/remotor MNs (`Tergopleural/Pleural promotor`, `Pleural remotor/abductor`) swing the leg
fore/aft at the thorax-coxa joint — **these are your best leg-level walking-direction signal**;
`Ti flexor`/`Ti extensor` give stance/swing phase.

---

## 3. Direct DN→MN connectivity caveat (important for the mod)

From the MANC analysis (Cheong et al., eLife 13:RP96084, 2024–2026): **1,328 DNs synapse onto
733 MNs, but direct DN→MN connections are infrequent** — direct DN input is only **~7%** of a
typical MN's input; the exception is **neck and abdominal MNs, which get up to 60%** direct.
Motor control runs through intervening premotor layers. **Implication**: if your LIF simulation
includes the full VNC, read MNs. If you simulate the brain only (or a reduced VNC), read DNs — a
naive direct DN→MN readout will underestimate leg/wing MN drive by more than an order of magnitude.

DNp01→TTMn and DNp01→DLMn (via PSI) are among the few strong direct pathways. (HIGH.)

---

## 4. Recommended minimal, robust decoding scheme

### 4.1 Rate estimation

- **Window: 100 ms exponential (not boxcar), τ = 100 ms, updated every Minecraft tick (50 ms).**
  Justification: DN firing precedes behavior by **~150 ms** for both DNa02 and DNg13 (Yang 2024;
  Rayshubskiy). A τ ≈ 100 ms filter with a 50 ms tick gives you ~2–3 samples per behavioral lag —
  responsive but not jittery. MDN's behavioral latency is **~100 ms**; landing DNs are
  **21–26 ms**. So:
  - **Fast/reflex channel (no smoothing, 1-tick):** DNp01 (jump), DNp07/DNp10 (landing).
    Use a **spike-count threshold**, not a rate: DNp01 needs **1 spike**.
  - **Slow/postural channel (τ = 100 ms):** everything else.
- **Normalization**: divide each DN's smoothed rate by a per-type running 95th percentile
  (or a fixed `r_max` per type). Absolute Hz values are unpublished for most DNs, so a
  self-normalizing readout is the honest choice. Published anchors you *can* hard-code:
  MDN baseline **8.5 Hz** / active **12–16 Hz**; DNp22 **24.5 Hz**; DNp15 **13.6 Hz**;
  DNp07 **27 Hz** (grounded) → **92 Hz** (flying) → **>150 Hz** (max extension);
  DNg13 **>100 Hz** achievable.

### 4.2 Populations to read

```
forward_drive  = mean_norm( DNp09, DNg100/BDN2, DNge053/BDN1, DNge050/BDN4, DNg97/oDN1 )     # 5 types, 10 cells
yaw_rate       = norm(DNa02_R) - norm(DNa02_L)                                                # primary
                 + 0.5*( norm(DNg13_R) - norm(DNg13_L) )                                      # secondary gesture
                 + 0.25*( norm(DNa01_R) - norm(DNa01_L) )                                     # low-gain, sustained
backward_drive = mean_norm( MDN_L(2), MDN_R(2) )         ; turn_bias = norm(MDN_R)-norm(MDN_L)
halt           = max( norm(DNg60/bluebell), norm(AN19A018/BRK), mean_norm(DNg74/web) )
freeze         = norm(DNp09) sustained > T for > 1 s  AND  forward_speed already low
takeoff_jump   = spike(DNp01_L) OR spike(DNp01_R)                     # 1 spike, no smoothing
takeoff_dir    = sign( norm(DNp11) - norm(DNp02 + DNp04) )            # + = forward, - = backward
landing        = max( norm(DNp07), norm(DNp10) )  gated by  is_flying
flight_power   = mean_norm( all 29 DNg02 cells )                      # graded wingbeat amplitude
flight_yaw     = norm(DNg02_L) - norm(DNg02_R)     # NB: contralateral wing -> sign flip
                 + w * ( norm(DNp03_L) - norm(DNp03_R) )   # DNp03 is CONTRAversive: negate
head_gaze      = DNp15 (yaw), DNp20/DNp22 (roll/pitch)
groom_antenna  = max( norm(DNg62/aDN1), norm(DNge078/aDN2) )   ; leg side = side of higher cell
groom_head     = mean_norm( DNg12 (42 cells), DNg07, DNg08 )
groom_legrub   = mean_norm( DNg11 (6 cells) )
groom_abdomen  = norm(DNp29)
feed_PER       = 0.6*norm(MN9) + 0.4*mean_norm( DNg67/Fudog, DNge080/Rounddown, DNge173/174/Bract )
courtship_gain = mean_norm( pC1_* (156 cells) )        # a persistent STATE, τ = 30-60 s
song_on        = norm(pIP10)  gated by courtship_gain
song_mode      = pulse if norm(pMP2) > baseline else sine        # pMP2 sign flips between modes
song_wing_side = side with higher wing steering-MN activity (unilateral)
```

### 4.3 Suggested time windows per channel

| Channel | Window | Why |
|---|---|---|
| DNp01 jump | **single tick, spike count ≥ 1** | one GF spike suffices; whole jump is ~30 ms |
| DNp07/DNp10 landing | **20–50 ms**, τ=25 ms | latencies 21±4 / 26±5 ms |
| Yaw (DNa02/DNg13/DNa01) | **τ = 100 ms**, apply output with **~150 ms** lead already consumed | measured DN→behavior lag |
| Forward / backward | **τ = 150–200 ms** | MDN needs a 300 ms pulse for a full bout |
| Halt / freeze | **τ = 250 ms** + 1 s hysteresis | Sapkal's halts are long-duration; DNp09 freeze builds over seconds |
| Grooming | **τ = 300 ms**, min bout 0.5 s | grooming is a sustained, bout-structured behavior |
| Song | **τ = 50 ms** for mode, τ = 500 ms for on/off | IPI is 35 ms; mode switches are fast, singing state is slow |
| Courtship state (pC1) | **τ = 30–60 s** | P1 arousal persists for minutes |

### 4.4 Conflict resolution (priority stack)

The biology gives you the arbitration for free: **DN clusters mutually inhibit** (Braun 2024), and
Sapkal established an explicit dominance order. Implement as a **winner-take-all with a fixed
priority ladder plus hysteresis**, not as a vector sum:

```
0. ESCAPE     DNp01 spike            -> jump, overrides everything, 200 ms refractory lockout
1. LANDING    DNp07/DNp10, if flying -> extend legs, cancel flight_power over ~150 ms
2. BRAKE      AN19A018/BRK           -> "overrode ALL walking commands (fwd, bwd, turning)" [Sapkal]
3. WALK-OFF   DNg60/bluebell         -> subtract from yaw first, then forward  [bluebell hits turning]
              Foxglove (absent)      -> if you model it, subtract from forward only
4. BACKWARD   MDN                    -> MDN actively inhibits forward; do NOT sum fwd and bwd,
                                        let MDN gate forward_drive to 0
5. FORWARD    DNp09 + BDN1-4 + oDN1  -> speed
6. YAW        DNa02/DNg13/DNa01      -> applied on top of 4 or 5
7. GROOM      aDN1/aDN2/DNg12/DNg11  -> only when speed < threshold; grooming and walking are in
                                        mutually inhibitory clusters
8. SONG/COURT pIP10/pMP2, gated by pC1 -> only when speed low and a target is near
```

Rules that come straight from papers, not from taste:
- **Never sum forward and backward.** MDN *inhibits* forward locomotion through a dedicated
  pathway (Carreira-Rosario 2018). Gate, don't add.
- **Brake beats everything walking-related** (Sapkal 2024: "overrode all walking commands").
- **Bluebell kills turning preferentially; Foxglove kills forward preferentially.** Two different
  stop flavors — worth exposing as two different mob animations.
- **Grooming ⊥ walking**: Braun 2024 found behavior-specific DN clusters with *inhibitory*
  cross-cluster connections; cluster 2 (take-off/landing) inhibits cluster 3 (walking), and four
  `web` DNs each inhibit 41–96 walking neurons. Encode as cross-inhibition in your decoder if you
  don't already have it in the simulated network.
- **Add hysteresis on every mode switch** (min dwell ~200–300 ms) — real DN clusters have
  persistent dynamics, and without it the mob will flicker between behaviors at 20 tps.

### 4.5 Reality-check constants for tuning the mob

| Quantity | Value | Source | Conf |
|---|---|---|---|
| Forward walking speed | −1.3 to **30.4 mm/s** (2.5–97.5 pct); bimodal peaks at **0** and **~17.5 mm/s** | DeAngelis 2019 eLife 46409 | HIGH |
| Rotational velocity axis | **±800 °/s** | Rayshubskiy | HIGH |
| DN→behavior lag | **150 ms** | Yang 2024; Rayshubskiy | HIGH |
| Wingbeat frequency | ~200 Hz | standard | HIGH |
| Escape jump duration | **~30 ms** | Cande 2018 | HIGH |
| Pulse-song IPI | **~35 ms** | von Philipsborn 2011 | HIGH |
| Sine-song carrier | **~150–160 Hz** | ibid | HIGH |
| MDN minimum drive for a bout | **300 ms** pulse | Carreira-Rosario 2018 | HIGH |
| Optogenetic protocol that produced the Cande atlas | 617 nm, **5 mW/cm²** (rescreen 9 mW/cm²), **15 s** ON / 45 s OFF, 30 trials, 100 fps | Cande 2018 | HIGH |

---

## 5. What Braun 2024 means for you (network vs. command)

Braun et al. (*Nature* 630:686–694, 2024) is the most consequential paper for a *simulation*:

- comDNs recruit **networks** of other DNs. DNp09 has the largest downstream DN network
  (~23–32 partners depending on the count used), aDN2 ~15–23, MDN ~9–14; the **median DN has ~4**
  downstream DN partners. DN–DN connections are **42% of DN output in the brain** (Stürner 2025).
- Sparse (headless) activation degrades behaviors to fragments: **DNp09 alone → only an abdomen
  contraction**; **aDN2 alone → only a front-leg approach**; **MDN alone → backward walking
  survives intact**.
- **Therefore**: MDN is a genuine standalone command neuron and can be decoded 1:1 to "walk
  backward". DNp09 and aDN2 are *broadcasters* — decoding them alone will look wrong. Either
  (a) decode the recruited population (average DNp09 with BDN1–4/oDN1 as above), or (b) accept
  that in a full-connectome LIF sim the network recruitment happens for you and read the
  population anyway. Option (b) is what your architecture gives you for free — read populations,
  not single cells, everywhere except DNp01/MDN.

---

## 6. Verified `synonyms` table from male-cns:v1.0 (paper name ↔ type)

Queried live; 80 DN types carry synonyms. The decision-relevant subset:

```
DNp01   GF (Kennedy and Broadie 2018)                 DNg100  BDN2 (Sapkal 2024)
DNp13   pMN1 (Kimura 2015); DN1 (Ruta 2010)           DNge053 BDN1 (Sapkal 2024)
DNp15   DNHS1 (Suver 2016)                            DNg55   BDN3 (Sapkal 2024)  [GABA, unpaired]
DNp20   DNOVS1 (Dorkenwald 2024)                      DNge050 BDN4 (Sapkal 2024)
DNp22   DNOVS2 (Dorkenwald 2024)                      DNg97   oDN1 (Sapkal 2024)
MDN     DNp50 (Carreira-Rosario 2018)                 DNg75   cDN1 (Sapkal 2024)
aSP22   DNa12 (McKellar 2019)                         DNg60   bluebell/BB (Sapkal 2024);
DNp02   P3b (Matsuo 2016)                                     mesa, snail (Sterne 2021) [GABA]
DNp05   AMMC-Db2      DNp06  AMMC-Di5                 AN19A018 BRK (Sapkal 2024) [ASCENDING, 12 cells]
DNp11   AMMC-Db3      DNp18  AMMC-Di3                 DNg62   aDN1 (Hampel 2015)
DNb05   AMMC-Di7      DNg15  AMMC-Db5 / nagini        DNge078 aDN2 (Hampel 2015)
DNp29   NPF1 (Scheffer 2020)                          DNg67   Fudog (Shiu 2022)
DNd02   OA-VL1 (Busch 2009)                           DNge080 Rounddown (Shiu 2022)
DNd03   OA-VL2 (Busch 2009)                           DNge173 Bract 2 / DNge174 Bract 1
DNg30   SPN (Scheunemann 2018) [SEROTONIN]            DNg05_a/b/c knees (Sterne 2021)
DNg07   aPN(desc) (Vaughan 2014)                      DNg57 bobber, DNg61 sink, DNg72 genie,
DNg70/DNg98  DSOG1 (Pool 2014) [GABA]                 DNg77 snake, DNg80 gumdrop, DNg93 oval,
pIP10 / pIP1  P2b, pIP-a, pIP1                        DNge172 mute, DNg74_a/b web  (Sterne 2021)
```

**Not present in male-cns** (verified, 0 hits): `DNa12` as a type name (use `aSP22`),
`DNg02`/`DNg12` as bare names (use the `_a`…`_h` subtypes), `DNg25`, `DNp37`/`vpoDN`, `oviDN`,
`Foxglove`, `Bolt`/`BPN`.

---

## 7. Explicit non-findings

- **No published absolute firing rates** for DNa01, DNa02, DNp09, DNg100/BDN2, DNg97/oDN1, DNg13
  (except the >100 spikes/s current-injection ceiling), pIP10, pMP2, aSP22, or any Sterne SEZ DN.
  The only DN firing rates I could source: MDN (8.5 / 12–16 Hz), DNp07 (27 / 92 / >150 Hz),
  DNp22 (24.5 ± 13.9 Hz), DNp15 (13.6 ± 5.0 Hz), DNg13 (>100 Hz achievable).
- **No gain coefficient** (°/s per spike/s) is published for DNa02 → yaw. Rayshubskiy reports the
  relation is linear across the range and that DNa02's slope is steeper than DNa01's, but gives no
  number. Any gain you use is a free parameter — tune it, don't cite it.
- **Cande 2018's full line→DN→behavior table** lives in "Figure 2—source data 1", a supplementary
  file I could not retrieve (eLife figures page and the ScienceOpen PDF both refused/were
  paywalled or CAPTCHA-gated). The category counts (26 locomotion, 10 anterior foreleg, 3
  grooming, 7 wing/abdomen, 4 still/slow; 53 of 58 DNs in Fig. 6; 90% of 130 lines significant;
  80% of DN types phenotyped) are solid, but per-DN assignments beyond the ones tabulated above
  came from the paper's prose, not the supplement.
- **"Foxglove"** has no male-cns annotation, so you cannot instantiate it from the connectome by
  name. If you want a forward-specific stop, use DNg60/bluebell (turning-biased) or drive
  DNg97/oDN1 + DNg100/BDN2 down directly.
- **DNp42, DNp49, DNp27, DNp32, DNa06, DNa11, DNa13, DNa14, DNb02, DNg05/knees, DNg15/nagini,
  DNg57/bobber, DNg61/sink, DNg72/genie, DNg77/snake, DNg80/gumdrop, DNg93/oval,
  DNge172/mute** — no behavior known. Leave them in the simulation (they matter for dynamics) but
  do not wire them to a game action.

---

## 8. Sources

Primary, in rough order of usefulness for this task:

1. Cande, Namiki, Qiu, Korff, Card, Shaevitz, Stern, Berman (2018) "Optogenetic dissection of descending behavioral control in Drosophila", *eLife* 7:e34275. https://elifesciences.org/articles/34275 · https://pmc.ncbi.nlm.nih.gov/articles/PMC6031430/
2. Namiki, Dickinson, Wong, Korff, Card (2018) "The functional organization of descending sensory-motor pathways in Drosophila", *eLife* 7:e34272. https://elifesciences.org/articles/34272
3. Sapkal, Mancini, Kumar, … Bidaye (2024) "Neural circuit mechanisms underlying context-specific halting in Drosophila", *Nature* 634:191–200. https://www.nature.com/articles/s41586-024-07854-7 · https://pmc.ncbi.nlm.nih.gov/articles/PMC11446846/ · preprint https://www.biorxiv.org/content/10.1101/2023.09.25.559438v1
4. Braun, Hurtak, Wang-Chen, Ramdya (2024) "Descending networks transform command signals into population motor control", *Nature* 630:686–694. https://pmc.ncbi.nlm.nih.gov/articles/PMC11186778/
5. Yang, Brezovec, Serratosa Capdevila, Vanderbeck, Adachi, Mann, Wilson (2024) "Fine-grained descending control of steering in walking Drosophila", *Cell* 187:6290–6308. https://www.cell.com/cell/fulltext/S0092-8674(24)00962-0 · https://pmc.ncbi.nlm.nih.gov/articles/PMC12778575/
6. Rayshubskiy et al. "Neural circuit mechanisms for steering control in walking Drosophila", *eLife* 102230. https://elifesciences.org/articles/102230
7. Bidaye et al. (2020) "Two brain pathways initiate distinct forward walking programs in Drosophila", *Neuron*. https://www.cell.com/neuron/fulltext/S0896-6273(20)30576-6
8. Bidaye, Machacek, Wu, Dickson (2014) "Neuronal control of Drosophila walking direction", *Science* 344:97–101.
9. Carreira-Rosario et al. (2018) "MDN brain descending neurons coordinately activate backward and inhibit forward locomotion", *eLife* 7:e38554. https://elifesciences.org/articles/38554
10. Feng, Sen, Minegishi, … Dickson (2020) "Distributed control of motor circuits for backward walking in Drosophila", *Nat Commun* 11:6166.
11. Zacarias, Namiki, Card, Vasconcelos, Moita (2018) "Speed dependent descending control of freezing behavior in Drosophila melanogaster", *Nat Commun* 9:3697.
12. von Reyn, Breads, Peek, … Card (2014) "A spike-timing mechanism for action selection", *Nat Neurosci* 17:962–970. https://www.nature.com/articles/nn.3741
13. Ache, Namiki, Lee, Branson, Card (2019) "State-dependent decoupling of sensory and motor circuits underlies behavioral flexibility in Drosophila", *Nat Neurosci* 22:1132–1139. https://pmc.ncbi.nlm.nih.gov/articles/PMC7444277/
14. Ache et al. (2019) "Neural basis for looming size and velocity encoding in the Drosophila giant fiber escape pathway", *Curr Biol* 29:1073.
15. Dombrovski et al. (2023) "Synaptic gradients transform object location to action", *Nature* 613:534. https://pmc.ncbi.nlm.nih.gov/articles/PMC9849133/
16. "Drosophila DNp03 descending neurons serve as a hub within a flight saccade network", *Curr Biol* 2025. https://pmc.ncbi.nlm.nih.gov/articles/PMC12977095/
17. Cheong et al. "Organization of circuits linking descending input to motor output in the Drosophila MANC connectome", *eLife* 13:RP96084. https://elifesciences.org/articles/96084
18. Stürner et al. (2025) "Comparative connectomics of Drosophila descending and ascending neurons", *Nature*. https://pmc.ncbi.nlm.nih.gov/articles/PMC12222017/
19. Namiki, Ros, Morrow, Rowell, Card, Dickinson (2022) "A population of descending neurons that regulate the flight motor of Drosophila", *eLife*. https://pmc.ncbi.nlm.nih.gov/articles/PMC9206711/
20. Suver et al. (2016) "An array of descending visual interneurons encoding self-motion in Drosophila", *J Neurosci* 36:11768.
21. Hampel, Franconville, Simpson, Seeds (2015) "A neural command circuit for grooming movement control", *eLife* 4:e08758. https://elifesciences.org/articles/08758
22. Guo, Zhang, Simpson (2022) "Descending neurons coordinate anterior grooming behavior in Drosophila", *Curr Biol*. https://www.cell.com/current-biology/fulltext/S0960-9822(21)01742-5
23. Aymanns, Chen, Ramdya (2022) "Descending neuron population dynamics during odor-evoked and spontaneous limb-dependent behaviors", *eLife* 11:e81527. https://elifesciences.org/articles/81527
24. Sterne, Otsuna, Dickson, Scott (2021) "Classification and genetic targeting of cell types in the primary taste and premotor center of the adult Drosophila brain", *eLife* 10:e71679. https://pmc.ncbi.nlm.nih.gov/articles/PMC8445619/
25. Shiu, Sterne, Engert, Dickson, Scott (2022) "Taste quality and hunger interactions in a feeding sensorimotor circuit", *eLife* 11:e79887. https://elifesciences.org/articles/79887
26. Shiu et al. (2024) "A Drosophila computational brain model reveals sensorimotor processing", *Nature* 634:210–219. Code: https://github.com/philshiu/Drosophila_brain_model
27. von Philipsborn, Liu, Yu, Masser, Bidaye, Dickson (2011) "Neuronal control of Drosophila courtship song", *Neuron*. https://www.cell.com/neuron/fulltext/S0896-6273(11)00057-2
28. Lillvis et al. (2024) "Nested neural circuits generate distinct acoustic signals during Drosophila courtship", *Curr Biol*. https://www.cell.com/current-biology/fulltext/S0960-9822(24)00015-0
29. Roemschied et al. (2024) "Activity of nested neural circuits drives different courtship songs in Drosophila", *Nat Neurosci*. https://pmc.ncbi.nlm.nih.gov/articles/PMC11452343/
30. McKellar et al. (2019) "Threshold-based ordering of sequential actions during Drosophila courtship", *Curr Biol*.
31. Ehrhardt et al. "Single-cell type analysis of wing premotor circuits in the VNC of Drosophila melanogaster". https://pmc.ncbi.nlm.nih.gov/articles/PMC10312520/
32. DeAngelis, Zavatone-Veth, Clark (2019) "The manifold structure of limb coordination in walking Drosophila", *eLife* 8:e46409.
33. "A dedicated brain circuit controls forward walking in Drosophila" (bioRxiv 2026) — BDN1–4 / oDN1.
34. "Control of walking direction by descending and dopaminergic neurons in Drosophila" (bioRxiv 2025) — MDN electrophysiology.

Local artifacts produced by this research (all under the scratchpad `research/` dir):
`dn_counts.json`, `dn_bodyids.txt`, `dn_synonyms.txt`, `mn_types.txt`.
