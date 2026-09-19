# Completeness critique: gaps left by the six research reports

Date 2026-09-03. Inputs read in full: `lif-model.md`, `dn-behavior.md`, `fabric-api.md`, `data-access.md`,
`embodied-precedents.md`, `fly-model-art.md`, plus `sensory-mapping.md` (present in the directory but not
among the six summaries) and `eon_readme.md`/`readme.md`. Confidence tags: **[V]** verified by me this session
(live neuPrint Cypher, WebFetch of primary source, local command); **[R]** stated in a report and not
re-checked; **[L]** my inference.

## 0. Method

I looked for three kinds of gap: (1) decisions the implementer must make blind because the reports disagree
or stop at "tune empirically"; (2) load-bearing claims tagged [L]/[M] that everything downstream rests on;
(3) sensory/motor populations the demos need but no report maps to data. I then ran a few cheap probes to
sharpen or close candidate gaps before ranking them.

## 1. Probes run this session (closing or sharpening candidate gaps)

| Probe | Result | Effect |
|---|---|---|
| Cypher: `consensusNt` of lamina/medulla columnar types | **L1 = glutamate (1,776), L2/L3/L4/L5 = acetylcholine, C2/C3 = GABA, Mi1/Tm1/Tm2/Tm3/Tm4/Tm9/T4a/T5a/Lawf1/Lawf2 = ACh, Mi4 = GABA, Mi9 = glutamate, Dm8a = glutamate, T1 = histamine, LC4/LPLC2 = ACh** [V] | Confirms that in a silent Shiu-style LIF the ON pathway (L1 -> Mi1/Tm3) is *structurally unreachable* (inhibition onto silent cells is inert) while the OFF pathway (L2 -> Tm1/Tm2 -> T5, and L3) is drivable. Sharpens gap 2. |
| Cypher: presynaptic share of `consensusNt` unclear/null neurons over w>=5 edges | unclear presyn total ~1.40 M of 90.3 M synapses (~1.6 %): cb_intrinsic 728 k, untyped 153 k, vnc_sensory 120 k, **descending_neuron 86 k (36 DNs)**, vnc_intrinsic 77 k, visual_projection 76 k, visual_centrifugal 47 k, cb_sensory 44 k, vnc_motor 3.3 k, cb_motor 2.6 k [V] | The "how to sign 9,793 unclear neurons" open question is **low impact** (1.6 % of synapses); default `predictedNt` -> else +1 is adequate. Not a top-6 gap. Only caveat: 36 unclear DNs carry 86 k synapses; check `predictedNt` for those individually. |
| Local toolchain | Python 3.9.4, **Brian2 2.6.0 present, pyarrow 21.0.0 present** (data-access.md says pyarrow is missing - wrong), scipy 1.13.1, no numba; JDK 25.0.1 [V] | The 508 MB `connectome-weights-...-traced-only.feather` can be read locally; the calibration experiment in gap 1 is runnable now. |
| Fabric API 1.21.1 `ClientPlayNetworking.java` Javadoc | "Handles the incoming payload. **This is called on the render thread**, and can safely call client methods." [V] | Closes fabric-api.md open question on handler thread (client side). |
| Reiser lab repo tree | `results/eyemap/ME_pindata.csv` (columns `hex1_id, hex2_id, bin_depth, x, y, z, roi`; ~1,390 rows, right ME only, 8 nm optic-lobe:v1.1 frame), `LO_pindata.csv`, `LOP_pindata.csv`, `cache/eyemap/*_col_center_pins.pickle`, `docs/assets/column_coord.png/.pdf`, `docs/assets/Drosophila_eye_coordinates.svg`, `src/eyemap/*.py` [V] | No azimuth/elevation table exists there; pins are 3-D lines only. Sharpens gap 4 (directions must be derived). |
| GCS `malecns-v1.0-optic-lobe-column-pins/info` | neuroglancer precomputed **line** annotations, 8 nm units, properties `depth int16, roi uint8 (97-value enum), hex1 int8, hex2 int8, layer int8`, relationships `by_rel_ME(L)/ME(R)/LO(L)/LO(R)/LOP(L)/LOP(R)_column_segment`, `by_id/0.shard` 30 MB, gzip + murmurhash3 sharding [V] | Gives a parseable per-column pin line for **both** eyes in the male-cns frame - the input for gap 4. |
| Rubin et al. 2026 preprint (Europe PMC PPR1106358) abstract | "48 P1/pC1x cell types show varied synaptic connections... New genetic tools... reveal **distinct roles for specific cell types in acoustic signaling and male-male interactions**" [V] | Confirms that averaging all 156 pC1 cells into one `courtship_gain` (dn-behavior.md 4.2) conflates courtship- and aggression-promoting types. Sharpens gap 6. |
| bioRxiv full texts (Rubin 2026, Pugliese 2025) | HTTP 429 rate-limited this session; Europe PMC has no full text for either [V] | Gap 6 agent must use the Current Biology VOR (Crossref DOI 10.1016/j.cub.2026.08.013) or retry bioRxiv later. |

## 2. Ranked gaps (max 6)

### Gap 1 - W_syn, threshold and stability of the LIF on the male-CNS graph (blind decision, everything depends on it)
All reports agree the Shiu parameters must be re-calibrated on male-cns (125.0 M synapses vs FlyWire v630's
52.8 M; ~750 vs ~415 synapses per neuron) and none did it. The reports also **disagree** on two decisions that
only this run can settle: lif-model.md says keep all connections (w>=1, 25.9 M edges) for fidelity and offer
w>=5 only as a performance option; data-access.md recommends bundling w>=5 (6.29 M edges, 13 MB) by default.
embodied-precedents.md fears runaway excitation when ~700 ORNs plus ~3,400 R cells are driven at once and
proposes a 70/30 DN/MN decoder blend "to be raised if VNC premotor rhythms appear" - unknown. Every decoder
threshold, the runtime guard, and the Java budget (gap 3) need the activity statistics this run produces.

**Research question.** Using the local toolchain (Python 3.9, Brian2 2.6.0, pyarrow 21 - read
`gs://flyem-male-cns/v1.0/connectome-data/flat-connectome/connectome-weights-male-cns-v1.0-minconf-0.5-traced-only.feather`
or pull edges by anonymous Cypher), build Shiu's LIF (-52/-52/-45 mV, 20 ms, 5 ms, 2.2 ms, 1.8 ms, dt 0.1 ms,
exact integrator) on male-cns:v1.0 with the consensusNt sign map (ACh +, GABA/Glu/histamine -, monoamines +,
unclear -> predictedNt else +) and run: (a) a W_syn sweep 0.05-0.40 mV with right labellar sugar GRNs
(types LB3b + LB3c, ~34 cells) at 100 Hz, reporting the W_syn at which MN9 (2 cells, subclass pm) reaches ~80 %
of its maximal rate and ~400 neurons are active, separately for w>=1 and w>=5 graphs; (b) at that W_syn,
confirm JO-C/JO-E (wind_gravity) at 100-220 Hz -> DNg62 (aDN1)/DNge078 (aDN2), LC4 + LPLC2 at 150 Hz ->
DNp01 > 150 Hz, DNp04/DNp02 > 100 Hz, contralateral DNa02 > 20 Hz with ~5 ms / ~22 ms latency, and LB1a-d
(bitter) + sugar -> MN9 suppression; (c) a stress test with all 2,639 ORNs at 50 Hz plus L2 + L3 of both eyes
at 50 Hz simultaneously - fraction of neurons active per 10 ms window, and whether firing runs away;
(d) DNg100 (BDN2) and DNp09 driven bilaterally at 50-100 Hz for 2 s - report T1/T2/T3 leg-MN pool rates
('Ti flexor MN', 'Tr extensor MN', 'Tergopleural/Pleural promotor MN', 'Pleural remotor/abductor MN') and
any periodicity (autocorrelation) so the DN-vs-MN readout decision can be made; (e) for every condition the
active-neuron count, spikes per simulated second and wall time. Deliver a parameter table (W_syn per
threshold, guard thresholds) and the bodyId lists used.

### Gap 2 - How to drive vision into a silent spiking network (no tested recipe; reports conflict)
sensory-mapping.md says "inject sign-inverted luminance at L1/L2/L3"; lif-model.md says use graded
histaminergic R-cell drive plus a tonic bias on lamina cells; embodied-precedents.md says Poisson L1/L2/L3 at
100-150 Hz x luminance plus an ON/OFF derivative. My probe shows L1 is glutamatergic (inhibitory under the
Shiu sign rule) and C2/C3/Mi4 GABA, Mi9 glutamate, so with a 0 Hz baseline the ON pathway (L1 -> Mi1/Tm3 ->
T4) cannot be switched on by any L1 firing; only the OFF pathway (L2/L3 ACh -> Tm1/Tm2 -> T5, LC4/LPLC2) is
reachable. Eon's embodiment found flyvis activations "decorative". The escape demo (README scene 2) and all
optomotor/pursuit behaviour hinge on this, and nobody has tested any recipe.

**Research question.** On the male-cns subgraph consisting of both optic lobes, the LC/LPLC visual projection
neurons and DNp01/DNp02/DNp03/DNp04/DNp11/DNa02 (with the calibrated W_syn from gap 1), present a synthetic
looming stimulus (dark disc expanding at l/v = 10, 20, 40, 80 ms, centred at several azimuths, rendered onto
a per-column luminance map at 1 kHz) under three injection recipes: (i) Poisson drive to L2/L3 (OFF) and L1
(ON) columns with rate proportional to luminance decrement/increment (embodied-precedents recipe); (ii) graded
histaminergic photoreceptor drive g -= N_syn*W_syn*lambda(L)*dt onto L1-L5/C2/C3/T1 with a tonic
depolarising bias of 3-6 mV on L1-L5/C2/C3 so light is encoded as release from inhibition (lif-model recipe);
(iii) direct analytic Poisson drive to LC4 (proportional to dtheta/dt) and LPLC2 (Gaussian in theta) as Chen &
Xi did. For each, report whether DNp01 fires (latency, spike count), whether the LC4 population rate scales
with angular velocity and LPLC2 with angular size (Ache 2019 shape), whether DNp02 vs DNp11 correctly follow
anterior vs posterior stimulus position, and whether any T4/T5 direction selectivity appears for a moving
edge. Recommend one recipe with its parameters (bias mV, lambda scaling, adaptation tau, ON/OFF split) and
state which visual behaviours must be declared hand-built.

### Gap 3 - Is "50 ms of brain per 50 ms tick" actually achievable in Java? (architecture-level claim tagged [L])
lif-model.md estimates 176 k neurons x 500 steps per tick ~ 9e7 updates "feasible on one core";
embodied-precedents.md proposes dt = 0.5 ms with an active set and ~2-15 ms per tick; Wang et al. measured
Brian2 at 4.4 s wall per simulated second (0.23x real time) on CPU. Nobody has measured a Java kernel. If real
time is not reachable the whole design changes (default slow-motion, coarser dt, thresholded graph, GPU, or
multi-threaded integration), so this must be known before code is written.

**Research question.** Write and run a standalone JDK 25 micro-benchmark (no Minecraft) of the exact-integration
LIF kernel (u,g as float[], refractory counter, 18-slot / 4-slot / 2-slot delay ring buffer, CSR by
presynaptic index with int32 post and int16 signed synapse counts, one global W_syn) on the *real* male-cns
CSR at w>=5 (6,287,789 edges) and w>=1 (25,862,574 edges), at dt = 0.1, 0.5 and 1.0 ms, with imposed activity
of 0.3 %, 1 % and 3 % of neurons firing at ~30 Hz (from gap 1 if available, otherwise synthetic), single-thread
and with parallel integration (ForkJoin over neuron ranges, 4/8/16 threads), both dense-sweep and active-set
variants. Report wall-clock ms per 50 ms of simulated brain, allocation/GC pauses, heap footprint, and the
varint-CSR decode time from a gzipped resource, on this 32-core Windows machine while leaving headroom for a
Minecraft server. Conclude which (dt, threshold, threading) combination meets brainMsPerTick = 50 with margin,
or what slow-motion ratio is needed.

### Gap 4 - Per-column viewing direction for the male-cns hex columns, including the anterior/posterior sign
Both sensory-mapping.md and data-access.md give the hex axes (v ~ hex1+hex2 dorsal, h ~ hex1-hex2) but
explicitly leave the anterior/posterior polarity of h unverified and offer only a linear azimuth/elevation
approximation with "+/-20-30 % error at the periphery". Escape direction (anterior field -> DNp02 backward
takeoff vs posterior -> DNp11 forward), LC10a pursuit and any optomotor test depend on placing columns
correctly. My probes found the raw material but no direction table: Reiser lab `results/eyemap/ME_pindata.csv`
(right ME only, columns hex1_id, hex2_id, bin_depth, x, y, z) and the male-cns GCS column-pin lines for both
eyes (neuroglancer precomputed 'line' annotations with properties depth/roi/hex1/hex2/layer, 8 nm).

**Research question.** Produce a lookup table (side, hex1, hex2) -> (azimuth deg, elevation deg) in the fly's
head frame for the 892 ME_R and 880 ME_L columns of male-cns:v1.0, by decoding the column-pin line
annotations in `gs://flyem-male-cns/v1.0/malecns-v1.0-optic-lobe-column-pins/` (pin direction from medulla
toward the lamina/retina approximates the ommatidial optical axis), aligning the 8 nm volume frame to body
axes using neuropil/soma landmarks (brain midline from somaSide means, z as the neuraxis, the equator row
from R7/R8 pale-yellow or Nern 2025's equator markers), and cross-checking against Zhao et al. 2025
(Nature, PMC12488493: FOV from <10 deg contralateral in front to ~155 deg behind, +90 to -70 deg elevation,
DeltaPhi ~4.8 deg mean and smallest frontally, <20 deg binocular overlap, ~50 deg posterior blind spot) and the
Reiser `docs/assets/column_coord.png`. Verify the anterior/posterior sign independently (e.g., LC4 dendrite
positions in LO columns vs the Dombrovski 2023 anterior->DNp02 gradient, or the frontal binocular-overlap
columns being those whose optical axes cross the midline). Also tag each column pale/yellow via its R7p/R7y ->
Dm8/Tm5 partners and list DRA columns. Deliver the CSV plus the transformation used.

### Gap 5 - Internal state and context gating that the connectome does not contain
embodied-precedents.md (open Q7) and dn-behavior.md both note that hunger, courtship arousal and flight-state
gating are absent from the graph and must be supplied, but no report names the neurons or magnitudes. The
consequences are visible in every demo: without satiety the fly feeds forever (MN9 fires whenever sugar GRNs
do), without a P1 state LC10a pursuit and pIP10 song never start, and DNp07/DNp10 will fire landing while
walking unless gated at the sensory side as Ache 2019 showed. Foxglove (walk-OFF during feeding) is absent
from male-cns, so satiety-driven halting has no handle either.

**Research question.** For each required state - hunger/satiety (Shiu 2022 eLife 79887 hunger-gated Fudog
responses; sNPF/NPF/AstA/dopaminergic modulation of sugar-GRN -> SEZ -> MN9; Tastekin et al. 2026 Cell
10.1016/j.cell.2026.08.016), courtship arousal (P1/pC1 persistence for minutes: Hoopfer 2015, Inagaki/Zhang
et al.), flight vs walking state (Ache et al. 2019 Nat Neurosci: DNp07 92 -> 27 Hz and DNp10 response
abolished when not flying; Liessem/Ache 2026 Curr Biol: MDN and DopaMeander gated out during flight;
haltere/wing campaniform input), and feeding-linked walk-OFF (Sapkal 2024 Foxglove, absent; Bluebell DNg60
present) - identify the specific male-cns neuron types/bodyIds or synaptic pathways where a bias current or
gain change reproduces the state, with reported time constants and effect sizes, and specify one concrete
LIF implementation per state (tonic depolarisation in mV on named neurons, multiplicative gain on named edge
sets, or sensory-side gating rule) plus a satiety variable driven by ingested nutrition. Where the literature
gives no handle, say so and propose the least-invasive hand-built gate.

### Gap 6 - pC1/P1 subtype roles and the tapping -> song pathway in male-cns (courtship demo)
dn-behavior.md decodes `courtship_gain = mean_norm(pC1_*)` over all 156 cells in 49 types. Rubin et al.
2026 (Curr Biol 10.1016/j.cub.2026.08.013; preprint 10.1101/2025.10.21.683766, abstract verified) report
48 P1/pC1x types with "distinct roles for specific cell types in acoustic signaling and male-male
interactions" - i.e., some promote song, some aggression. sensory-mapping.md asserts LgLG5-8 -> PPN1 ->
pIP10 for the female-tapping route but gives no male-cns synapse counts, and notes pIP10 receives almost no
direct visual input (LC10a must route through P1). None of the four same-day Cell/Curr Biol papers has been
read in its published form.

**Research question.** From Rubin et al. 2026 (VOR or preprint; supplementary tables) and Tastekin et al.
2026, plus live neuPrint queries on male-cns:v1.0, produce a per-type table for pC1_1a...pC1_19, pC1x_a-d,
aIPg*, aSP10* and pIP1 giving: assigned behaviour (courtship/song promoting, aggression promoting, unknown)
with the evidence type (optogenetic/genetic tool), cell counts and bodyIds, dominant sensory inputs (LgLG5-8
/ LgLG1a-b / WG3-4 contact-pheromone GRNs via PPN1, ORN_DA1 cVA, ORN_VA1v/VA1d, LC10a), and output synapse
counts onto pIP10, pMP2, aSP22 (DNa12), vPR6 and other DNs; verify the LgLG5-8 -> PPN1 -> pIP10 path and the
LC10a -> P1 -> pIP10 path exist with weights in male-cns; and recommend which subtypes to sum with which sign
for `courtship_gain` vs an `aggression_gain`, plus the stimulation protocol (rate, duration) that should turn
song on in the LIF given gap 1's W_syn.

## 3. Lesser gaps (noted, not in the top 6)

- `Level.clip` cost per 24-32-block ray and thread-safety of off-server-thread chunk reads (embodied Q6) -
  measurable only in a running dev client; design defensively (sample on the server thread, interleave).
- ~15 Or -> odorant pairings unverified against DoOR 2.0 (sensory-mapping) - game-design impact only.
- Short vs long takeoff latencies (von Reyn 2014), inter-fly courtship distances in mm, cibarial pump rate,
  leg segment lengths - cosmetic/animation tuning.
- VP1l vs VP1m hygro/thermo labelling conflict (19 cells) - leave at baseline.
- Ceballos 2026 conductance-based parameters; Pugliese 2025 LIF-replication details (bioRxiv rate-limited
  today) - useful for gap 1(d) interpretation, not blocking.
- Cell 2026 vs bioRxiv NT-threshold wording; corresponding authors for the citation block - cosmetic.
- Cande 2018 Figure 2-source data 1 (per-DN behaviour table) - would extend the ~18 unmapped DN types.
- Whether Janelia throttles neuPrint under sustained load - use the feather files for bulk.

## 4. Corrections to the reports

- data-access.md: pyarrow **is** installed (21.0.0); the feather files can be read locally.
- fabric-api.md open question: Fabric 1.21.1 client play-payload handlers run on the render thread (Javadoc).
- sensory-mapping.md c.5 "inject at L1 with sign-inverted luminance" is not viable as stated under the Shiu
  sign rule (L1 is glutamatergic/inhibitory); see gap 2.
- dn-behavior.md 4.2 `courtship_gain = mean_norm(pC1_*)` mixes courtship- and aggression-promoting types;
  see gap 6.
- lif-model.md open question 2 (signing 9,793 unclear neurons) is low impact: ~1.6 % of w>=5 synapses.
