# Sensory Transduction Reference: Wiring Minecraft Stimuli into male-cns:v1.0 Sensory Neuron Types

Research report for the Fabric 1.21.1 fruit-fly mod. Target: drive real `male-cns:v1.0` sensory
neuron types from Minecraft world state, in a LIF spiking network.

**Date:** 2026-09-03 · **Dataset:** `male-cns:v1.0` (Janelia FlyEM / MRC LMB / Cambridge / Google)

## Confidence legend

| Tag | Meaning |
|---|---|
| **[V]** | Verified this session by direct Cypher query against `https://neuprint.janelia.org/api/custom/custom`, or by direct quote from a primary source I fetched. Numbers are exact. |
| **[P]** | Primary literature, fetched and quoted this session, but a secondary detail (not the headline result). |
| **[L]** | Literature recall / summarized from search-result snippets. Directionally right, but **verify the exact number before shipping**. |
| **[D]** | Derived by me this session from neuPrint data (regression / set arithmetic). Method stated inline so you can re-run it. |
| **[G]** | Game-design proposal. Not a biological fact. Mine. Tune freely. |

**Nothing labelled [G] is a claim about flies.** Everything labelled [V] is re-derivable from the
queries in Appendix A.

My web-search budget was exhausted at 200 calls during this session. Items that remained
unverified when that happened are explicitly marked [L] with a note on how to close the gap.

---

## 0. Executive summary of what is actually wireable

Exact cell counts in `male-cns:v1.0` **[V]**:

| Modality | superclass / class | Cells | Types | Wireable from Minecraft? |
|---|---|---:|---:|---|
| Vision | `ol_sensory` / `visual` | 6,091 | R1-R6, R7p/y/d, R8p/y/d (+unclear) | Yes — raycasts to 892 columns/eye |
| Olfaction | `cb_sensory` / `olfactory` | 2,639 | 57 ORN_* glomerular types | Yes — nearby item/block odor sources |
| Gustation | `cb_`/`vnc_`/`ascending` / `gustatory` | 1,428 | LB, LgLG, LgAG, WG, PhG, tpGRN | Yes — contact + eating |
| Mechanosensory (head/JO) | `cb_sensory` / `mechanosensory` | 1,733 | JO-A..F, BM_*, TPMN, aPhM | Yes — sound, wind, touch |
| Mechanosensory (tactile) | `vnc_sensory` / `mechanosensory_tactile` | 2,558 | SNta01-45 | Yes — collisions, dust |
| Proprioceptive | `vnc_`/`ascending` / `..._proprioceptive` | 1,454 | SNpp*, SApp* | Synthetic (leg CPG phase) |
| Hygrosensory | `cb_sensory` / `hygrosensory` | 66 | HRN_VP1d/VP1l/VP4/VP5 | Yes — water/rain/biome humidity |
| Thermosensory | `cb_sensory` / `thermosensory` | 25 | TRN_VP1m/VP2/VP3a/VP3b | Yes — fire/lava/ice/biome temp |
| Unknown sensory | mixed | 1,712 | mostly abdominal | Leave silent |

### Three findings that change the architecture

1. **Photoreceptors are histaminergic (inhibitory) and their reconstruction is incomplete.**
   R1-R6 = 3,377 cells for 892 columns = 3.8/cartridge, not the expected 6 **[D]**. Nern et al.
   note lamina cells and R7/R8 were undercounted: *"estimated 2,777 cells from the lamina and 459
   R7 and R8 photoreceptors"* were missing **[P]**.
   **→ Inject visual drive at L1/L2/L3, not at R1-R6.** L1 has 1,767 of 1,776 cells hex-assigned:
   full 1-per-column coverage on both sides **[V]**.

2. **`assignedOlHex1`/`Hex2` are the two oblique hex lattice axes (p, q), NOT azimuth/elevation.**
   Visual-space *vertical* is the diagonal `u = hex1 + hex2`; *horizontal* is `w = hex1 - hex2`.
   Derived by regressing L1 soma position on hex coords (right eye, n=139):
   `y = -543.6*h1 - 614.6*h2 + 59046`, **R² = 1.00** — h1 and h2 load almost equally on the
   dorsoventral volume axis, which is the signature of a diagonal **[D]**.
   Dorsal = high `u`, confirmed independently: DRA columns (postsynaptic to R7d/R8d) sit at
   `u = 46..72` **[D]**. This matches Zhao et al. 2025, who describe the lattice as having *"one
   orientation being vertical (v) and the other two orientations (p and q) flanking the vertical"* **[P]**.

3. **Column ROI naming is `<ME|LO|LOP>_<side>_col_<hex1>_<hex2>`, zero-padded to 2 digits.**
   892 ME(R) column ROIs, 880 ME(L); LO 875/867; LOP 856/842 **[V]**. Verified against a Mi1 with
   `assignedOlHex1=21, assignedOlHex2=6` whose roiInfo contains `ME_L_col_21_06` **[V]**.
   You get a retinotopic-column key for free, with no external eyemap file.

---

# (a) OLFACTION

## a.1 Receptor to glomerulus table

Receptor/sensillum/co-receptor columns quoted from **Task et al. 2022, eLife 11:e72599, Table 3**
(fetched this session) **[P]**. Cell counts are `male-cns:v1.0` **[V]**. All 2,639 ORNs are
`consensusNt = acetylcholine` **[V]**, so all excitatory.

The dataset has **57 ORN types**, not 50: it splits `VM6` into `VM6l/VM6m/VM6v`, `DL2` into
`DL2d/DL2v`, and includes `DC4`.

| Glomerulus | ORN type | Cells [V] | Sensillum [P] | Tuning receptor [P] | Coreceptor [P] |
|---|---|---:|---|---|---|
| DM1 | ORN_DM1 | 74 | ab1A | Or42b | Orco |
| DM2 | ORN_DM2 | 54 | ab3A | Or22a, Or22b | Orco |
| DM3 | ORN_DM3 | 63 | ab5B | Or47a, Or33b | Orco |
| DM4 | ORN_DM4 | 32 | ab2A | Or59b | Orco |
| DM5 | ORN_DM5 | 35 | ab2B | Or85a, Or33b | Orco |
| DM6 | ORN_DM6 | 58 | ab10A | Or67a | Orco |
| D | ORN_D | 28 | ab9A | Or69aA, Or69aB | Orco |
| V | ORN_V | 55 | ab1C | **Gr21a, Gr63a** | (Gr) |
| DA1 | ORN_DA1 | **204** | at1A | **Or67d** | Orco |
| DA2 | ORN_DA2 | 48 | ab4B | **Or56a**, Or33a | Orco |
| DA3 | ORN_DA3 | 34 | ai2B | Or23a | Orco |
| DA4l | ORN_DA4l | 33 | ai3C | Or43a | Orco |
| DA4m | ORN_DA4m | 35 | ai3B | Or2a | Orco |
| DC1 | ORN_DC1 | 32 | ai3A | Or19a, Or19b | Orco |
| DC2 | ORN_DC2 | 22 | ai1A | Or13a | Orco |
| DC3 | ORN_DC3 | 35 | ai2A | Or83c | Orco |
| DC4 | ORN_DC4 | 23 | Sacculus III | **Ir64a** | Ir8a |
| DL1 | ORN_DL1 | 83 | ab1D | Or10a, Gr10a | Orco |
| DL2d | ORN_DL2d | 15 | ac3A | Ir75b | Ir8a |
| DL2v | ORN_DL2v | 23 | ac3A | Ir75c | Ir8a |
| DL3 | ORN_DL3 | 103 | at4B | **Or65a, Or65b, Or65c** | Orco |
| DL4 | ORN_DL4 | 62 | ab10B | **Or49a, Or85f** | Orco |
| DL5 | ORN_DL5 | 43 | ab4A | Or7a | Orco |
| DP1l | ORN_DP1l | 33 | ac2 | **Ir75a** | Ir8a |
| DP1m | ORN_DP1m | 31 | Sacculus III | **Ir64a** | Ir8a |
| VA1d | ORN_VA1d | 132 | at4C | **Or88a** | Orco |
| VA1v | ORN_VA1v | 130 | at4A | **Or47b** | Orco |
| VA2 | ORN_VA2 | 83 | ab1B | Or92a | Orco |
| VA3 | ORN_VA3 | 30 | ab9B | Or67b | Orco |
| VA4 | ORN_VA4 | 34 | pb3B | Or85d | Orco |
| VA5 | ORN_VA5 | 16 | ai1B | Or49b | Orco |
| VA6 | ORN_VA6 | 63 | ab5A | Or82a | Orco |
| VA7l | ORN_VA7l | 29 | pb2B | Or46a | Orco |
| VA7m | ORN_VA7m | 24 | unknown | unknown | Orco |
| VC1 | ORN_VC1 | 29 | pb2A | Or33c, Or85e | Orco |
| VC2 | ORN_VC2 | 32 | pb1B | Or71a | Orco |
| VC3 | ORN_VC3 | 34 | ac3B | Or35a | Orco + Ir76b |
| VC4 | ORN_VC4 | 38 | ab7B | Or67c | Orco |
| VC5 | ORN_VC5 | 31 | ac2 | **Ir41a** | Ir8a/25a/76b |
| VL1 | ORN_VL1 | 82 | ac1, ac2, ac4 | Ir75d | Ir25a |
| VL2a | ORN_VL2a | 98 | ac4 | **Ir84a** | Ir8a |
| VL2p | ORN_VL2p | 45 | ac1 | Ir31a | Ir8a |
| VM1 | ORN_VM1 | 33 | ac1 | **Ir92a** | Ir8a/25a/76b |
| VM2 | ORN_VM2 | 41 | ab8A | Or43b | Orco |
| VM3 | ORN_VM3 | 43 | ab8B | Or9a | Orco |
| VM4 | ORN_VM4 | 78 | ac4 | **Ir76a** | Ir8a/25a/76b |
| VM5d | ORN_VM5d | 84 | ab3B | Or85b?, Or98b? | Orco |
| VM5v | ORN_VM5v | 34 | ab7A | Or98a | Orco |
| VM6l | ORN_VM6l | 14 | Sacculus III | **Rh50, Amt** | Ir25a |
| VM6m | ORN_VM6m | 25 | Sacculus III | **Rh50, Amt** | Ir25a |
| VM6v | ORN_VM6v | 34 | ac1 | **Rh50, Amt** | Ir25a |
| VM7d | ORN_VM7d | 36 | pb1A | Or42a | Orco |
| VM7v | ORN_VM7v | 25 | pb3A | Or59c | Orco |
| (untyped) | ORN (null) | 4 | — | — | — |

### Hygro- and thermosensory (VP glomeruli)

Receptor/modality/organ from **Marin et al. 2020, Curr Biol 30:3167** (fetched) **[P]**.
Counts `male-cns:v1.0` **[V]**. All acetylcholine **[V]**.

| Glomerulus | Dataset type | Cells [V] | Class in dataset [V] | Receptor [P] | Modality [P] | Organ [P] |
|---|---|---:|---|---|---|---|
| VP1d | HRN_VP1d | 18 | hygrosensory | Ir40a | "may represent evaporative cooling" | Sacculus II |
| VP1l | HRN_VP1l | 8 | hygrosensory | Ir21a | "might represent cooling" | Sacculus I |
| VP1m | TRN_VP1m | 11 | thermosensory | Ir68a | "might represent humidity" | Sacculus I |
| VP2 | TRN_VP2 | 7 | thermosensory | Gr28b.d | **heating** | Arista |
| VP3 | TRN_VP3a / VP3b | 6 / 1 | thermosensory | Ir21a | **cooling** | Arista |
| VP4 | HRN_VP4 | 28 | hygrosensory | Ir40a | **dry air** | Sacculus I+II |
| VP5 | HRN_VP5 | 12 | hygrosensory | Ir68a | **humid air** | Sacculus II |

> **Naming conflict — flag this in the mod.** The `male-cns` class labels and Marin 2020 receptor
> logic disagree on VP1l and VP1m: the dataset calls VP1l *hygrosensory* but Marin assigns it Ir21a
> (the cooling receptor); it calls VP1m *thermosensory* but Marin assigns it Ir68a (the humidity
> receptor). Marin hedges both ("might represent"). **[V]+[P]**
> Safe to drive: **VP2 = hot, VP3 = cold, VP4 = dry, VP5 = humid.** Leave VP1l/VP1m at baseline
> (19 cells total, behaviourally unresolved).

Marin 2020 also reports the shortest known sensory-to-motor path in the fly: *"the large proportion
of dry-responsive VP4 PN input to the DNp44 descending neuron might represent the shortest known
Drosophila brain circuit from sensory periphery to descending motor control (two synapses)"* **[P]**.
A free desiccation-escape reflex.

## a.2 Ligands and valence

Verified channels first — build the game on these.

| Glomerulus | Ligand | Valence | Source | Conf |
|---|---|---|---|---|
| **DM1** (Or42b) | ethyl acetate, apple cider vinegar | **Attractive.** "absence of activity in two glomeruli, DM1 and VA2, markedly reduces attraction, while selective activation of each of these two glomeruli elicits robust attraction to vinegar" | Semmelhack & Wang 2009 Nature | [P] |
| **VA2** (Or92a) | acetoin, 2,3-butanedione, vinegar | **Attractive** (with DM1) | Semmelhack & Wang 2009 | [P] |
| **DM5** (Or85a) | high-conc vinegar | **Aversive.** "the repellant channel governed by DM5 via Or85a... sufficient to override Or42a-mediated attraction and cause higher ACV concentrations to become repellant" | Semmelhack & Wang 2009 | [P] |
| **DA2** (Or56a) | **geosmin** | **Strongly aversive, dedicated line.** "Activation of DA2 is sufficient and necessary for aversion, overrides input from other olfactory pathways, and inhibits positive chemotaxis, oviposition, and feeding." Only ab4B responds. | Stensmyr et al. 2012 Cell | [P] |
| **V** (Gr21a/Gr63a) | **CO2** | **Aversive.** "CO2 elicits avoidance over a wide range of concentrations and activates only a single glomerulus, V." Avoidance at levels "as low as 0.1%" | Suh et al. 2004 Nature | [P] |
| **DL4** (Or49a+Or85f) | **iridomyrmecin, actinidine, nepetalactol** (parasitoid wasp) | **Aversive**, dedicated wasp-avoidance circuit | Ebrahim et al. 2015 PLoS Biol | [P] |
| **DA1** (Or67d) | **cVA** (11-cis-vaccenyl acetate) | Male pheromone; male-male courtship inhibition / aggregation. 204 ORNs = largest ORN population in the dataset | Kurtovic 2007; count [V] | [L]+[V] |
| **DL3** (Or65a/b/c) | **cVA** (slower, longer-lasting) | Modulates aggression/courtship | — | [L] |
| **VA1v** (Or47b) | **methyl laurate** | **Copulation-promoting.** "fruM-positive Or47b-expressing OSNs detect ML exclusively, and Or47b-expressing OSNs are required for optimal male copulation behavior. Activation... is sufficient to provide a competitive mating advantage." | Dweck et al. 2015 PNAS | [P] |
| **VA1d** (Or88a) | methyl laurate, methyl myristate, **methyl palmitate** | **Attraction, both sexes.** "necessary and sufficient for attraction behavior in both males and females" | Dweck et al. 2015 PNAS | [P] |
| **VL2a** (Ir84a) | **phenylacetaldehyde, phenylacetic acid** | **Food odour that promotes male courtship.** "Mutation of Ir84a abolishes both odour-evoked and spontaneous electrophysiological activity in these neurons and markedly reduces male courtship behaviour." | Grosjean et al. 2011 Nature | [P] |
| **VM1** (Ir92a) | **ammonia, amines** | **Attractive.** "RNAi knockdown of IR92a... led to significantly impaired calcium responses and attraction behavior to ammonia and amines" | Min et al. 2013 PNAS | [P] |
| **VM6l/m/v** (Amt, Rh50) | **ammonia** | Second, non-canonical ammonia channel | Vulpe et al. 2021 Curr Biol | [P] |
| **VC5** (Ir41a) | **polyamines: putrescine, cadaverine, spermidine**, pyridine, pyrrolidine | Attractive by smell. Note: labellar Ir76b mediates *avoidance* of the same compounds by taste — a built-in smell/taste conflict | Hussain et al. 2016 PLoS Biol | [P] |
| **VM4** (Ir76a) | amines / phenylethylamine | Attractive | Hussain 2016 | [P] |
| **DP1l** (Ir75a) | **acetic acid, propionic acid**, 2,3-butanedione | Fermentation marker | Prieto-Godino et al. | [P] |
| **DL2d / DL2v** (Ir75b/Ir75c) | butyric acid / propionic-hexanoic acid | Carboxylic acids | — | [L] |
| **DC4 + DP1m** (Ir64a) | acids / low pH | DC4 = acid **avoidance**; DP1m = general acid | Ai et al. 2010 | [L] |

### Lower-confidence best-ligand assignments

Standard textbook Or-to-odorant pairings that I did **not** re-verify against DoOR or Hallem &
Carlson 2006 this session (search budget exhausted). Plausible defaults; check DoOR 2.0
(`http://neuro.uni.kn/DoOR`, Münch & Galizia 2016 Sci Rep 6:21841) before shipping. **[L]**

| Glomerulus | Receptor | Ligand (unverified) | Game flavour |
|---|---|---|---|
| DM2 | Or22a | ethyl hexanoate, ethyl butyrate | ripe fruit |
| DM4 | Or59b | methyl acetate, ethyl acetate | vinegar/fruit |
| DM3 | Or47a | pentyl acetate | fruit |
| DL5 | Or7a | E2-hexenal | green leaf volatile |
| DL1 | Or10a | methyl salicylate, ethyl benzoate, butyric acid (these three DoOR-confirmed strongest) | wintergreen |
| VA6 | Or82a | geranyl acetate | floral |
| VM2 | Or43b | ethyl butyrate | fruit |
| VM5d | Or85b | 2-heptanone | overripe |
| VC2 | Or71a | **4-ethylguaiacol** | **smoky/phenolic** |
| VC4 | Or67c | ethyl lactate | fermented dairy |
| DC1 | Or19a | limonene / terpenes | citrus |
| DC2 | Or13a | **1-octen-3-ol** | **mushroom** |
| DC3 | Or83c | farnesol | citrus peel |
| D | Or69aA/B | dual food + pheromone ligand | generic attractant |
| VA3 | Or67b | plant/mushroom volatiles | vegetation |

One DoOR caveat worth knowing: for Or42b, *"3-hexanone, ethyl propionate and ethyl
(S)-(+)-3-hydroxybutyrate were the three strongest ligands in this dataset"* — not ethyl acetate,
though ab1A does respond strongly to ethyl acetate **[P]**. Best-ligand rankings are dataset-dependent.

## a.3 Proposed Minecraft odour source to glomerulus map [G]

Each odour source emits a **glomerular drive vector** `g` with entries in [0,1]. Relative
intensities are my design, chosen so that the biology above produces sensible in-game behaviour.
Only glomeruli with nonzero drive are listed.

| Minecraft source | Glomerular drive vector `g` | Net valence | Rationale |
|---|---|---|---|
| **Apple / golden apple** | DM1 1.0, DM2 0.9, VA2 0.6, DM4 0.5, VM2 0.4, DM3 0.3 | **+++** | fruit esters; golden apple = same vector, source strength x1.5 |
| **Sweet berries / glow berries** | DM1 0.8, DM2 0.7, VA2 0.5, DM4 0.4, VL2a 0.3 | **++** | berry esters + trace aromatics |
| **Melon / melon slice** | DM1 0.7, DM2 0.6, VM5d 0.5, VA2 0.4 | **++** | ester + overripe note |
| **Honey bottle / honey block** | VL2a **0.9**, D 0.6, DM1 0.4, VA2 0.3 | **++** and **courtship-priming** | phenylacetaldehyde/phenylacetic acid via Ir84a — the food odour that promotes male courtship |
| **Cake / cookie / bread / sugar** | VA2 **0.8**, DP1l 0.6, DM1 0.5, VM2 0.4, DL2d 0.3 | **++** | yeast fermentation: 2,3-butanedione, acetic acid |
| **Fermented spider eye / brewing** | DP1l 0.9, VA2 0.7, DM1 0.5, **DM5 0.6** | **+ then −** | strong fermentation recruits the DM5 aversive channel |
| **Rotten flesh** | VM1 **1.0**, VM6v/m/l **0.9**, VC5 **0.9**, VM4 0.7, DP1l 0.5, **DA2 0.8** | **ambivalent** | ammonia + cadaverine/putrescine (attractive) vs geosmin-like microbial (aversive). The single best demo of concentration-dependent valence |
| **Spider eye / poisonous potato / pufferfish** | **DA2 1.0**, DL4 0.4 | **−−−** | dedicated aversive lines |
| **Mushrooms (brown/red)** | DC2 0.8, VA3 0.5, VM5d 0.3 | **+** | 1-octen-3-ol |
| **Wheat / hay / grass / leaves** | DL5 0.7, VA3 0.5, D 0.3 | **+** | green leaf volatiles (E2-hexenal) |
| **Flowers** | VL2a 0.7, D 0.6, VA6 0.5 | **++** | floral aromatics + Ir84a |
| **Water / rain / wet block** | **VP5 1.0**, VP4 → **0.0** (suppressed) | neutral-positive | humidity, not odour |
| **Dry biome (desert/nether)** | **VP4 1.0**, VP5 → 0.0 | **−** | drives DNp44 two-synapse escape |
| **Campfire / fire / torch / smoke** | **V 0.9** (CO2), VC2 **0.8** (4-ethylguaiacol, smoke), DP1l 0.4, **VP2 0.7** (heat) | **−−** | CO2 aversion + smoky phenolics + arista heat |
| **Lava / magma block** | **VP2 1.0** (heat), V 0.6, VP4 0.8 (dry) | **−−−** | heat dominates |
| **Ice / snow / powder snow** | **VP3a/b 1.0** (cold), VP5 0.5 | **−** | arista cooling |
| **Another male fly (mob)** | **DA1 1.0** (cVA), DL3 0.6 | **− for courtship** | cVA suppresses male-male courtship |
| **Another fly, volatile female cue** | **VA1v 0.9** (methyl laurate), VA1d 0.8 | **+++ courtship** | Or47b promotes copulation; Or88a promotes attraction |
| **Player / mammalian mob** | V 0.4 (exhaled CO2), VM1 0.2, DP1l 0.2 | **weak −** | see caveat below |

### Two caveats the mod must respect

**1. Female contact pheromone is GUSTATORY, not olfactory.** 7,11-heptacosadiene (7,11-HD) and
7-tricosene are long-chain cuticular hydrocarbons — effectively non-volatile. They are detected by
**contact chemoreception on the forelegs**, via ppk23/ppk25/ppk29 GRNs, not by any glomerulus.
*"In a subset of taste hairs on the legs of Drosophila, there are two ppk23-expressing,
pheromone-sensing neurons with complementary response profiles; one neuron detects female
pheromones that stimulate male courtship, the other detects male pheromones that inhibit
male-male courtship."* **[P]** In the dataset these are **LgLG1a/LgLG1b** (all legs) and
**LgLG5-8** (male foreleg-specific, contralateral). → wire 7,11-HD to *tapping contact*, section (b).
The only *volatile* female-associated cues are methyl laurate (VA1v) and methyl palmitate (VA1d).

**2. Drosophila has no dedicated human/host-seeking olfactory channel.** Unlike mosquitoes (which
have dedicated lactic-acid and 1-octen-3-ol host lines), *D. melanogaster* is a fermenting-fruit
specialist. A player should produce only a **weak** response, dominated by exhaled CO2 hitting the
V glomerulus — which is **aversive**, not attractive. Modelling players as strongly attractive
would be biologically wrong. I could not find any primary source describing Drosophila attraction
to human skin volatiles, and I believe none exists. **[L, asserted as a negative]**

---

# (b) GUSTATION

## b.1 Complete GRN inventory in male-cns:v1.0 [V]

All counts exact, with `entryNerve` from the dataset. 1,428 gustatory cells total.

### Labellar bristle GRNs (nerve: MxLbN)

| Type | Cells | Modality | Molecular ID | Sensillum class |
|---|---:|---|---|---|
| LB1a | 11 | **Bitter** | Gr33a | S- and I-type |
| LB1b | 6 | **Bitter** | Gr33a | S- and I-type |
| LB1c | 16 | **Bitter** | Gr33a | S- and I-type |
| LB1d | 5 | **Bitter** | Gr33a | S- and I-type |
| LB1e | 18 (+1) | **Amino acid** (e.g. glutamate) | Ir94e | L-type |
| LB2a | 4 | unknown | none matched | — |
| LB2b | 3 | unknown | none matched | — |
| LB2c | 6 | unknown | none matched | — |
| LB2d | 5 | unknown | none matched | — |
| LB3a | 17 | **Water** | ppk28 | — |
| LB3b | 11 | **Sugar** | Gr64f (+Ir56b low-salt attraction) | — |
| LB3c | 23 | **Sugar** | Gr64f | — |
| LB3d | 26 | **High salt (avoid)** | Ir7c, ppk23; **glutamatergic** | — |
| LB4a | 4 | novel type, unknown | none matched | — |
| LB4b | 8 (+1) | novel type, unknown | none matched | — |
| LB3 / null | 1 / 2 | unassigned | — | — |

Sensillum-class biology, quoted from the taste-feeding connectome preprint **[P]**:
- *"On the labellum's periphery closest to where the two labial palps meet, there are short(S)- and long(L)-type bristles, which house four GRNs."*
- *"Occupying a more dorsal area of the labellum's external surface, the intermediate(I)-type bristles are innervated by only two GRNs."*
- *"L-type bristles were shown not to respond to bitter stimulation or house any GRN expressing bitter-sensing receptors."*
- *"the existence of four different functional classes of bitter compound-responsive taste bristles, involving most S- and I-type bristles."*

**→ The rule to implement:** L-type = sugar + water + salt, **no bitter**. S-type = 4 GRNs incl.
bitter. I-type = 2 GRNs (sugar + bitter). So **LB1a-d (bitter) fire only for S/I contacts**, and
**LB1e (Ir94e, amino acid) only for L-type**.

### Taste peg GRNs (nerve: MxLbN)

| Type | Cells [V] | Molecular ID [P] | Function [P] |
|---|---:|---|---|
| claw_tpGRN | 50 | Ir56d, Gr64e | **carbonation, fatty acids, glycerol** |
| dorsal_tpGRN | 10 | Gr5a, Ir60d | **amino acids in protein-rich food** |

Taste pegs sit inside the labellar pseudotracheae and are only contacted once the proboscis has
already opened — i.e. they are a *second-stage, post-ingestion-onset* channel. Good for a
"continue vs abort feeding" decision in the mod. **[G]**

### Leg GRNs

**Local (LgLG, stay in VNC)** — nerves ProLN/MesoLN/MetaLN:

| Type | Cells [V] | Legs [V] | Molecular ID [P] | Function [P] |
|---|---:|---|---|---|
| LgLG1a | 136 | all | VGlut−/fru+/ppk23+/ppk25− (ACh) | **Contact pheromone → PPN1 courtship** |
| LgLG1b | 134 | all | VGlut+/fru+/ppk23+/ppk25+ (Glu) | **Contact pheromone → PPN1 courtship** |
| LgLG2 | 129 | all | fru+/Ir52a+/Ir52b+ | Pheromone |
| LgLG3 | 162 | all | Gr5a / sugar GRs | **Sugar** → Dandelion appetitive |
| LgLG4 | 42 | all | sugar GRs | **Sugar** |
| LgLG5 | 13 | **foreleg only** | VGlut+/fru+/ppk23+/ppk25+ (Glu) | **Male-specific contact pheromone**, contralateral |
| LgLG6 | 16 | **foreleg only** | VGlut−/fru+/ppk23+ (ACh) | Male-specific contact pheromone |
| LgLG7 | 21 | **foreleg only** | VGlut−/fru+/ppk23+ (ACh) | Male-specific contact pheromone |
| LgLG8 | 14 | **foreleg only** | VGlut+/fru+/ppk23+/ppk25+ (Glu) | Male-specific contact pheromone |

**LgLG5-8 are the 7,11-HD / 7-T tapping channel.** Foreleg-only, male-specific, contralateral
projection. This is what fires when a male taps a female. **[P]+[V]**

**Ascending (LgAG, project to SEZ)** — LgAG1 25, LgAG2 11, LgAG3 7, LgAG4 8, LgAG5 4, LgAG6 4,
LgAG7 5, LgAG8 9, LgAG9 3 **[V]**. LgAG1 = Gr33a bitter/aversive, also Gr32a pheromone;
*"inhibit conspecific male and interspecies courtship"*; partner TPN3 **[P]**. LgAG5-7, 9 are
foreleg-only; LgAG3, 4, 8 mid/hindleg-only **[V]**.

### Wing GRNs (nerve: ADMN)

| Type | Cells [V] | Molecular ID [P] | Function [P] |
|---|---:|---|---|
| WG1 | 96 | fru+/Ir52a+ | Courtship/pheromone |
| WG2 | 97 | Gr43a/Gr64f/Gr5a | **Sugar** → Dandelion |
| WG3 | 96 | VGlut+/fru+/ppk23+/ppk25+ | Courtship → PPN1 |
| WG4 | 96 | VGlut−/fru+/ppk23+ | Courtship → PPN1 |

40 bristles per wing margin; ~192-198 GRNs per side **[P]**.

### Pharyngeal GRNs (nerves: aPhN, PhN)

16 types, 2-4 cells each, 44 cells total **[V]**. Key IDs **[P]**: PhG1a-c = Gr64e **sugar**
(VCSO/LSO); PhG3, PhG4 = ppk28 **water**; PhG9 = Ir10a/Ir100a; PhG13 = Ir67c, **sexually
dimorphic**, male-specific partner AN05B035; PhG15 = Gr77a; PhG16 = Ir60d. PhG7-9 project via PhN
from DCSO, all contralateral.

**Pharyngeal GRNs gate swallowing, not tasting** — they only fire once food is already in the
pharynx. Use them for the ingest/reject decision after proboscis extension. **[G]**

## b.2 Engert 2022 cross-reference

Engert, Sterne, Bock & Scott 2022 (eLife 11:e78110) clustered 87 labellar GRN projections
(right hemisphere) into six groups **[P]**:

| Engert group | Modality | Marker |
|---|---|---|
| Groups 1 & 2 (~30 neurons) | **Bitter** | Gr66a — "forming a characteristic medial ringed web" |
| Group 3 | **Low salt** | Ir94e — "distinctive dorsolateral branches" |
| Group 4 | **Sugar** | Gr64f |
| Group 5 | **High salt** | ppk23 |
| Group 6 | **Water** | ppk28 |

*"79% of synapses are between neurons of the same group, while only 21% of the synapses are
between GRNs of different groups"* **[P]**, and group 4 (sugar) receives 1,468 synapses from other
group-4 neurons and 38 from group 3 **[P]**. → GRN axo-axonal coupling is real but modality-local;
model it as within-type recurrent excitation.

Mapping Engert groups onto male-CNS types: group 1/2 → LB1a-d; group 3 → LB1e (Ir94e);
group 4 → LB3b/c; group 5 → LB3d; group 6 → LB3a. **[D]**

## b.3 Proposed Minecraft taste map [G]

Fires on **contact**, not proximity. Two trigger tiers: *tarsal* (walking onto / tapping a block or
entity) and *labellar* (proboscis extension onto it).

| Minecraft contact | Tarsal (LgLG) | Labellar (LB) | Pharyngeal | Outcome |
|---|---|---|---|---|
| Sugar / honey / cake / sweet berries | LgLG3 1.0, LgLG4 0.8, WG2 0.6 | LB3b 1.0, LB3c 1.0 | PhG1a-c 0.9 | **Proboscis extension, ingest** |
| Any fruit | LgLG3 0.8, LgLG4 0.6 | LB3b 0.8, LB3c 0.8, LB3a 0.4 | PhG1a-c 0.7 | Feed |
| Water / wet block / rain puddle | — | **LB3a 1.0** | PhG3 0.8, PhG4 0.8 | Drink |
| Poisonous potato / spider eye / pufferfish | **LgAG1 1.0** | **LB1a-d 1.0** | — | **Reject, retract, avoid** |
| Suspicious stew | LgAG1 0.6 | LB1a-d 0.7, LB3b 0.3 | — | Hesitant reject |
| Salt-ish (dried kelp, sea pickle) | — | LB3d 0.9 (high-salt aversion), LB1e 0.4 | — | Mild reject |
| Rotten flesh (contact) | LgAG1 0.7 | LB1a-d 0.6, LB1e 0.8 | — | Reject; amino-acid channel says yes, bitter says no |
| Meat / protein block | — | **LB1e 0.9** (Ir94e), dorsal_tpGRN 0.9 | PhG16 0.6 | Feed slowly |
| Carbonated / bubble column | — | **claw_tpGRN 1.0** | — | Novelty response |
| **Tap another fly (female)** | **LgLG5-8 1.0**, LgLG1a/1b 0.9 | — | — | **→ PPN1 → pIP10 → courtship song** |
| **Tap another fly (male)** | LgAG1 0.9 (Gr32a), LgLG2 0.5 | — | — | **Courtship suppression / aggression** |

---

# (c) VISION

## c.1 Eye geometry

| Quantity | Value | Source | Conf |
|---|---|---|---|
| Ommatidia per eye | *"A fruit fly eye comprises around 750 columnar units called ommatidia"* | Zhao et al. 2025 Nature (s41586-025-09276-5) | [P] |
| Ommatidia per eye (alt) | 700-750, sometimes ~850 quoted | various | [L] |
| **Hex-assigned medulla columns, right** | **892** | neuPrint ME_R_col ROIs | **[V]** |
| **Hex-assigned medulla columns, left** | **880** | neuPrint ME_L_col ROIs | **[V]** |
| Lobula columns | LO(R) 875, LO(L) 867 | neuPrint | [V] |
| Lobula plate columns | LOP(R) 856, LOP(L) 842 | neuPrint | [V] |
| Inter-ommatidial angle ΔΦ | ~4.8° mean; **varies across the eye** — *"ΔΦ is smallest at the front near the equator and increases in size away from this region"* | Zhao 2025 | [P] |
| Acceptance angle Δρ | ≈ ΔΦ ≈ 4.8-5° | — | [L] |
| **Field of view, one eye** | *"from directly above to −70° in elevation, and in azimuth, from less than 10° into the opposite hemisphere in front to around 155° behind"* | Zhao 2025 | [P] |
| **Binocular overlap** | *"less than 20°"* (classical value ~5-10°) | Zhao 2025 / Heisenberg & Wolf 1984 | [P]/[L] |
| **Posterior blind spot** | *"around 50°"* (classical ~40°) | Zhao 2025 / Büchner 1974 | [P]/[L] |
| Total retinal coverage, both eyes | ~330° azimuth × 180° elevation | Heisenberg & Wolf 1984 | [L] |
| Shear angle α | *"most regular hexagons (α ≈ 90°) near the equator and the central meridian and sheared hexagons with α < 90° in the fronto-dorsal and posterior-ventral quadrants, and α > 90° in the other quadrants"* | Zhao 2025 | [P] |

> The 892 columns in the dataset exceed the ~750 ommatidia usually quoted. Both numbers are
> defensible: the medulla column count includes marginal and DRA columns, and "~750" is itself a
> round figure that varies with rearing temperature and body size. **Use 892/880 in the mod** —
> those are the columns you can actually index. **[D]**

## c.2 The hex coordinate system, decoded

This was the single biggest unknown going in. Findings:

**Axes.** `assignedOlHex1` (range 1-36) and `assignedOlHex2` (range 1-39) are the two **oblique
hexagonal lattice axes** (Zhao 2025 calls them p and q, *"flanking the vertical"*). They are
**not** azimuth and elevation. **[D]+[P]**

**Derivation.** Regressing L1 soma XYZ on (h1, h2), right eye, n=139 **[D]**:

```
x = +487.4*h1 − 457.9*h2 + 10884    R² = 0.78
y = −543.6*h1 − 614.6*h2 + 59046    R² = 1.00   <-- both coefficients nearly equal
z = −412.6*h1 + 211.9*h2 + 31691    R² = 0.47
corr(h1+h2, y) = −1.00      corr(h1−h2, x) = +0.88
```

The near-equal coefficients on `y` with R²=1.00 mean the sum `h1+h2` is the volume dorsoventral
axis. So define:

```
u = hex1 + hex2     -> DORSOVENTRAL (vertical) axis of visual space.  Range 7..72 (R), 7..71 (L)
w = hex1 - hex2     -> ANTEROPOSTERIOR (horizontal) axis.             Range -19..16 (R), -19..15 (L)
```

**Polarity.** Dorsal = **high u**. Two independent confirmations: (i) `y` decreases as `u`
increases, and `y` increases ventrally in FlyEM EM volumes; (ii) the **dorsal rim area** columns —
found by querying which hex columns are postsynaptic to R7d/R8d — occupy `u = 46..72`, i.e. the
high-u extreme: 43 DRA columns on the right, 36 on the left **[D]**.

**Lattice shape.** 892 filled columns in a 36 × 39 rhombus (1,404 sites) = 64% fill, consistent
with an elliptical eye inscribed in the lattice. Max 17-18 columns per `u` row; 66 distinct `u`
values, 36 distinct `w` values **[D]**.

**Practical column-to-direction map [G]:**

```java
// Approximate. See caveat below.
final int U_MIN = 7,  U_MAX = 72;      // measured
final int W_MIN = -19, W_MAX = 16;     // measured
int u = hex1 + hex2, w = hex1 - hex2;
double uN = (u - U_MIN) / (double)(U_MAX - U_MIN);   // 0..1
double wN = (w - W_MIN) / (double)(W_MAX - W_MIN);   // 0..1
double elevationDeg =  90.0 - uN * 160.0;            // +90 (up) .. -70 (down)
double azimuthDeg   = -10.0 + wN * 165.0;            // -10 (contra) .. +155 (behind)
// sign of azimuth flips for the left eye
```

**Caveat, stated honestly:** this linear map is an approximation. Zhao 2025 shows ΔΦ genuinely
varies across the eye and the hexagons are sheared, so a single linear scale cannot be exact
anywhere but near the centre. Expect ±20-30% angular error at the periphery. If you need the true
map, the Reiser lab ships eyemap code with the Nern 2025 paper:
`https://github.com/reiserlab/male-drosophila-visual-system-connectome-code` (I could not confirm
the exact file names — the GitHub landing page did not expose the directory tree to my fetch). The
Male CNS Cell Type Explorer also advertises *"Browse cell types, connectivity, and **eyemaps** for
the full male CNS"* **[P]**, which is the most likely source of an exact per-column direction table.

## c.3 Photoreceptors

| Type | Cells [V] | Rhodopsin [L] | Peak λ [L] | Function |
|---|---:|---|---|---|
| R1-R6 | 3,377 | Rh1 | **478 nm** + UV secondary peak (sensitizing pigment) | Broadband motion/luminance |
| R7p (pale) | 332 | Rh3 | **345 nm** | UV |
| R7y (yellow) | 482 | Rh4 | **375 nm** | UV |
| R8p (pale) | 330 | Rh5 | **437 nm** | Blue |
| R8y (yellow) | 481 | Rh6 | **508 nm** (up to 600 nm measured in situ) | Green |
| R7d (DRA) | 82 | Rh3 | 345 nm | **Polarized light** |
| R8d (DRA) | 76 | Rh3 | 345 nm | **Polarized light** |
| R7_unclear / R8_unclear / R7R8_unclear | 404 / 442 / 85 | — | — | unassigned |
| HBeyelet | 7 | Rh6 | — | Circadian entrainment (extraretinal) |

Pale:yellow ratio in the dataset = 332:482 = **41:59** among assigned R7 **[V]** (canonical is
~30:70; 404 unclear R7 make the true ratio uncertain). DRA ≈ **40 ommatidia per eye** (82 R7d / 2)
**[D]**, consistent with the 43/36 DRA columns found by connectivity **[D]**.

### Physiology — critical sign convention

- **Drosophila photoreceptors DEPOLARIZE to light** (opposite to vertebrates) and **increase**
  histamine release with increasing light **[L]**.
- Histamine opens histamine-gated chloride channels (Ort/HisCl) on L1/L2 → **L1/L2/L3 HYPERPOLARIZE
  to a light increment**. *"Light-induced depolarization promotes synaptic release of histamine,
  which opens histamine-gated chloride channels on postsynaptic L1 and L2 lamina neurons,
  triggering their hyperpolarization."* **[P]**
- → **In the LIF network, every histaminergic edge must carry a negative sign.** The 8,120
  `consensusNt = histamine` neurons in the dataset are essentially the photoreceptors. Getting this
  sign wrong inverts the entire ON/OFF pathway.
- Temporal: intracellular corner frequency falls *"approximately from 30 Hz to 10 Hz over a 4 log
  unit reduction in ambient light"* **[P]**. Behavioural/ERG flicker fusion is **>100 Hz** **[L]**;
  60-100 Hz dark-adapted in *D. hydei* **[L]**. Response latency ~10-20 ms **[L]**.
- Light adaptation: bump summation, *"smaller and faster elementary responses (bumps) whose latency
  distribution stays relatively unchanged at different mean light intensity levels"*
  (Juusola & Hardie 2001 J Gen Physiol 117:3-25) **[P]**.

### How to assign a photoreceptor to a column (they have no hex property) [V]+[G]

`R1-R6`, `R7*` and `R8*` all have `assignedOlHex1 = null` — 0 of 3,377 R1-R6 carry hex **[V]**.
But their targets do:

- **R7/R8 → assign directly.** Verified: `R8p` bodyId 166788 connects to Mi4, Tm20, L1 and Mi1 all
  at hex `(13,17)` — a single column **[V]**. Take the modal hex of its top medulla partners.
- **R1-R6 → assign by strongest lamina partner, but expect spread.** Verified: `R1-R6` bodyId
  557582 targets `T1(6,22)` w=55, `L1(6,21)` w=52, `L3(6,21)` w=15 — hex1 agrees, hex2 differs by
  one **[V]**. This is exactly the expected signature of **neural superposition**: the six R1-R6
  in one cartridge come from six *different* ommatidia. Use the top-weight L1/L2 partner column.

**Recommendation:** skip photoreceptors entirely as the injection point. Drive **L1** (1,767
hex-assigned, 1/column), **L2** (1,767) and **L3** (892 R-side) directly with the sign-inverted
luminance signal. They are complete, 1-per-column, and downstream of the incomplete R-cell
reconstruction. **[D]+[G]**

Columnar cell hex coverage, for choosing injection points **[V]**:

| Type | Total cells | With hex | Type | Total | With hex |
|---|---:|---:|---|---:|---:|
| L1 | 1,776 | 1,767 | Mi1 | 1,773 | 1,762 |
| L2 | 1,779 | 1,767 | Mi4 | 1,772 | 1,758 |
| L3 | 1,772 | 892 | Mi9 | 1,775 | 1,760 |
| L5 | 1,787 | 1,773 | Tm1 | 1,777 | 1,767 |
| T1 | 1,777 | 1,764 | Tm2 | 1,766 | 1,758 |
| C3 | 1,779 | 1,770 | Tm9 | 1,771 | 1,743 |
| C2 | 1,745 | 874 | Tm20 | 1,762 | 1,732 |
| L4 | 1,770 | **0** | Tm4 | 1,670 | 833 |
| T4a-d | ~1,690 ea | **0** | T5a-d | ~1,680 ea | **0** |
| Tm3 | 2,054 | **0** | Dm8a/b | 572/532 | **0** |

Note **T4/T5 (the direction-selective motion detectors) carry no hex coordinate** — you cannot
place them retinotopically from this property alone. Reach them through their columnar inputs
(Mi1, Tm3, Mi4, Mi9 for T4; Tm1, Tm2, Tm4, Tm9 for T5), which do carry hex. **[V]+[G]**

## c.4 Visual projection neurons: what encodes what

Counts `male-cns:v1.0` **[V]**. Synapse weights are **summed over all cells of the type**, queried
this session **[V]**. Function column is literature **[P]/[L]**.

| Type | Cells [V] | Encodes | Behaviour | Key downstream (summed synapses) [V] |
|---|---:|---|---|---|
| **LC4** | 126 | **Angular velocity of looming** (Ache 2019) | **Escape** | **DNp04 11,597; DNp01 (GF) 6,362; DNp02 4,209; DNp11 3,666; DNp03 2,507** |
| **LPLC2** | 185 | **Angular size of looming** (Ache 2019) | **Escape** | **DNp01 (GF) 4,862; DNp04 3,398** |
| **LPLC1** | 134 | Looming | Escape | DNp03 3,602; DNp11 1,041 |
| **LPLC4** | 97 | Looming | Escape | DNp03 3,740 |
| **LC6** | 124 | Looming | Avoidance (Wu 2016) | — |
| **LC16** | 182 | Looming | **Backward walking**, avoidance (Wu 2016) | — |
| **LC9** | 219 | Figure-ground | — | **DNp09 1,977; DNp11 958** |
| **LC11** | 143 | **Small object motion** (does not respond to looming) | Object tracking | — |
| **LC18** | 208 | Small moving objects | — | — |
| **LC12** | 498 | Figure-ground | — | — |
| **LC15** | 126 | **Bars / elongated objects** | — | — |
| **LC17** | 353 | Looming | — | — |
| **LC10a** | **275** | **Courtship target tracking**, gain-boosted by arousal | **Female pursuit** | DNp11 184; DNp09 65 |
| LC10b/c-1/c-2/d/e | 95/130/125/214/110 | Object tracking variants | — | LC10d → DNp10 113 |
| LC13 / LC21 / LC22 / LC26 | 177/153/73/75 | Object/looming variants | — | LC22 → DNp03 610 |
| LC31a / LC31b | 32 / 10 | — | — | DNp09 763 / 181 |
| **MeTu1** | 250 | Medulla → AOTU, sky/compass | **Navigation** | AOTU → TuBu → ER → EPG |
| MeTu2a/2b | 70/31 | ditto | Navigation | ditto |
| MeTu3a/3b/3c | 42/80/173 | ditto | Navigation | ditto |
| MeTu4a-f | 95/32/87/40/51/56 | ditto | Navigation | ditto |
| **HSE / HSN / HSS / HST** | 2 each | **Horizontal optic flow** (LPTC) | Yaw course control | HSS → DNa02 83 |
| **VS** | 18 | **Vertical optic flow** (LPTC) | Roll/pitch | **DNp20 936; DNp22 131** |
| VSm / VST1 / VST2 | 4/5/7 | Vertical flow | — | VST2 → DNp20 580; VSm → DNp22 266 |
| **H2** | 2 | Horizontal flow, contralateral | Yaw | DNa02 44 |
| Nod1-5 | 4/2/2/2/2 | Nodulus, optic flow | Course control | — |
| **OCG01a-f** | 2 each | **Ocellar** (simple eyes) | **Gaze stabilization** | **DNp20 1,481 (OCG01d); DNp22 700 (OCG01c)** |
| OCG02b/c, OCG03 | 2/4/2 | Ocellar | — | — |
| **LT51** | 22 | — | — | **MDN 353 (backward walking); DNa02 320** |
| **LLPC1** | 285 | — | — | **DNa02 523** (steering) |
| TmY14 | 477 | — | — | — |

### Findings from the connectivity queries worth building on

1. **DNp01 (Giant Fiber) input is almost purely LC4 + LPLC2**: 6,362 + 4,862 synapses, next largest
   is JO-B1_a at 545 **[V]**. This exactly reproduces Ache et al. 2019 — GF = linear(angular
   velocity from LC4) + Gaussian(angular size from LPLC2). **Implement looming escape as this
   two-term sum.**
2. **Sound can trigger escape.** JO auditory neurons make 703 synapses onto DNp01 (JO-B1_a 545 +
   JO-B1_c 148 + JO-CA1 10) **[V]**. A loud in-game noise near the fly should be able to fire the
   giant fiber. Great mechanic.
3. **DNp20/DNp22 (DNOVS1/2) are dominated by OCELLAR input, not VS**: OCG01d alone gives DNp20
   1,481 synapses vs VS 936 **[V]**. Do not model gaze stabilization from compound-eye optic flow
   alone — add a fast, low-resolution ocellar luminance channel (3 ocelli, dorsal-facing).
4. **MDN (backward walking) gets its main visual drive from LT51** (353 synapses), not from LC16
   **[V]**, though LC16 activation causes backward walking behaviourally (Wu 2016) **[L]**.
5. **DNa02 (steering) integrates LLPC1 (523) + LT51 (320) + HSS (83) + H2 (44)** **[V]** — plus
   `SNpp45` (a hair-plate proprioceptor) at 47, i.e. steering is genuinely multimodal.
6. **pIP10 (the courtship song command neuron) receives almost no direct visual input** — top
   source MeVP48 at 15 synapses **[V]**. Courtship visual drive (LC10a) must route indirectly,
   through P1/pC1. Do not wire LC10a straight to pIP10.

## c.5 Proposed Minecraft vision map [G]

**Performance first.** 892 columns × 2 eyes × 20 Hz = 35,680 raycasts/s. Too many. Recommendation:

- Cast a **coarse grid of ~120-150 rays per eye** (about 1 ray per 7 columns), then **assign each
  column to the nearest ray** by angular distance, precomputed once at load. Cost: ~6,000
  raycasts/s, acceptable on the server thread if you also stagger eyes on alternate ticks.
- Per ray, extract: **luminance** (block light + sky light + emissive), **hue bucket**
  (for R7/R8 channels), and **hit distance** (for looming).
- **Looming** is not raycast: compute it analytically per nearby entity/block from its angular size
  θ and expansion rate dθ/dt, then paint it onto the columns whose direction falls inside θ.

| Minecraft visual event | Column-level signal | Neuron injection | Result |
|---|---|---|---|
| Ambient brightness | luminance per column | **L1 (+ON), L2 (−OFF), L3** sign-inverted | baseline vision |
| Approaching entity/player | θ, dθ/dt within its angular footprint | **LC4 ∝ dθ/dt**, **LPLC2 ∝ Gaussian(θ)** | **DNp01 → takeoff** |
| Fast approach, small | LC4 high, LPLC2 low | GF short-mode | fast escape |
| Slow approach, large | LC4 low, LPLC2 high | GF long-mode | stable escape |
| Small moving object at distance | LC11, LC18 | tracking | orient toward |
| Vertical bar / tree trunk / fence | LC15 | fixation | approach/fixate |
| Self-motion (walking/flying) | global optic flow field | **HSE/HSN/HSS (yaw), VS (roll/pitch)** | **DNa02 steering, DNp20/22 gaze** |
| Sun / sky position | dorsal columns, UV-weighted (R7) | **MeTu1-4 → AOTU** | **compass heading** |
| Sky polarization (clear day) | **DRA columns (u=46..72)**, R7d/R8d | MeTu → AOTU | compass |
| Another fly, frontal field | small dark moving object | **LC10a** (gain × arousal) | **courtship pursuit** |
| Ocellar (dorsal luminance, 3 coarse channels) | sky brightness gradient | **OCG01a-f** | **DNp20/22 fast gaze** |

---

# (d) MECHANOSENSATION

## d.1 Johnston's organ inventory [V]

The dataset splits JO neurons by `subclass`, which encodes function directly:

| Subclass | Cells [V] | Types [V] |
|---|---:|---|
| **auditory** | **114** | JO-A1-A4, JO-A-unclear, JO-B1_a/b/c, JO-B2, JO-B3, JO-B-unclear, JO-CA1, JO-CA2 |
| **wind_gravity** | **475** | JO-CL 19, JO-CM 23, JO-DA 5, JO-DP 3, JO-ED1 18, JO-ED2_a/b/c 38/21/14, JO-EV1 50, JO-EV2 36, JO-EV3 45, JO-EV5 23, JO-EV6 21, JO-B4_a/b 3/12, JO-B2 7, JO-B3 7, JO-CA1 8, plus JO-unclear 96 |
| **grooming** | **65** | JO-FV 60, JO-FD1 4, JO-unclear 1 |
| (null) | 18 | JO-mz 9, JO-CM 7, JO-B3 1, JO-unclear 1 |

Note some JO-B types appear under **both** auditory and wind_gravity — subgroup boundaries are
genuinely fuzzy, matching the literature statement that subgroup-D responds to *both* vibration and
static deflection **[L]**.

### Frequency tuning [L] (from Kamikouchi 2009 Nature 458:165 and follow-ups)

| Subgroup | Stimulus | Tuning |
|---|---|---|
| **JO-A** | Sound (vibration) | **>100 Hz preferred** |
| **JO-B** | Sound (vibration) | **<100 Hz preferred**; JON-A+B together cover **100-400 Hz**, the courtship song band |
| **JO-C, JO-E** | **Static deflection** | **Gravity and wind** — "activated maximally by static deflection of the antennal receiver" |
| **JO-D** | Both | vibration + static deflection |
| **JO-F** | — | **Antennal grooming** (Hampel et al. 2020); activation also elicits avoidance |

Whole-JO frequency range ~10 Hz to 1,000 Hz **[L]**.

### JO output targets, summed synapse weights [V]

| Subclass | Top targets |
|---|---|
| **auditory** | SAD001 1,088; SAD108 799; SAD097 717; **DNp01 (GF) 703**; GNG301 696; SAD103 650; WED196 609; CB1076 586; DNg29 548 |
| **wind_gravity** | SAD004 6,195; SAD077 5,334; **AMMC026 4,596**; AMMC012 3,301; CB3320 2,938; SAD113 2,637; AMMC013 2,550; AMMC028 2,522; **DNg106 2,469** |
| **grooming** | **DNg15 (nagini) 1,322**; DNg84 937; DNg35 776; GNG516 757; JO-FV 605 (recurrent); AN05B009 489; DNge065 451 |

**DNg15 = "nagini"** is in the orchestrator's DN list — it is the top target of grooming-subclass
JO neurons **[V]**. Direct antennal-dust → grooming wire.

## d.2 Wind sensing and upwind orientation

Suver et al. 2019 Neuron **[P]**:
- *"Movements of single antennae are ambiguous with respect to wind direction, but the difference
  between left and right antennal displacements yields a linear code for wind direction in azimuth."*
- Second-order mechanosensory neurons *"share the ambiguous responses of a single antenna and
  receive input primarily from the ipsilateral antenna."*
- **Wedge projection neurons** integrate across both antennae to produce a linear wind-direction code.
- *"Both antennae contribute to odor-driven upwind orientation."*

**→ Implement as a bilateral difference.** Left and right `wind_gravity` JO populations get drive
proportional to their own antennal deflection; the azimuth code emerges from `L − R`. Do not
compute wind azimuth in Java and inject it directly — let the network do it. **[G]**

## d.3 Bristle mechanosensation (head) [V]

| Type | Cells [V] | Location | Drives |
|---|---:|---|---|
| **BM_InOm** | **745** | Interommatidial bristles on the eyes | **Eye grooming** (highest priority in the sequence) |
| BM_Taste | 40 | Taste bristles on the proboscis | **Proboscis grooming**; *"Optogenetic activation of BM-Taste neurons elicits proboscis grooming, during which the proboscis often extends."* [P] |
| BM_vOcci_vPoOr | 26 | Ventral occiput / post-orbital | Head grooming |
| BM_Vib | 22 | Vibrissae | Proboscis/head grooming |
| BM_MaPa | 15 | Maxillary palp | Head grooming |
| BM_Vt_PoOc | 8 | Vertex / post-ocellar | Head grooming |
| BM_Hau | 7 | Haustellum | Proboscis |
| BM (generic) | 69 | — | — |
| **TPMN1 / TPMN2** | 48 / 12 | Taste peg mechanosensory | Food texture |
| aPhM1-5 | 39 total | Pharyngeal mechanosensory | Swallowing |

Despite being the most numerous, BM_InOm contribute little synaptic output: *"405 eye BM-InOm
neurons innervating the interommatidial bristles on the eyes"* contribute minimal total output,
whereas *"35 BM-Taste neurons... accounted for 45% of all head BMN synapses, with an average of
1028 synapses per neuron"* **[P]**. → Weight BM_Taste far higher per cell than BM_InOm.

## d.4 Body/leg mechanosensation [V]

| Class | Cells | Notable types |
|---|---:|---|
| `mechanosensory_tactile` (SNta*) | **2,558** | SNta29 223, SNta37 223, SNta20 116/40, SNta21 113, SNta38 119, SNta04 83, SNta42 75, SNta43 72; subclasses: `mechanosensory bristle`, `leg`, `notum`, `wing` |
| `mechanosensory_proprioceptive` — chordotonal | ~400 | SNpp50 61, SNpp47 40, SNpp39 39, SNpp60 41, SNpp51 30, SNpp40 30 |
| — campaniform sensilla | ~380 | SApp09/SApp22 74, SApp08 47, SApp06/SApp15 37, SApp10 37 |
| — hair plate | 113 | SNpp45 52, SNpp19 35, SNpp52 26 |
| — **haltere** | **~195** | **SApp 148**, SNpp34 8, SNpp25 7, SNpp23 6 |
| — wing | 19 | SNpp61 10, SNpp62 9 |
| — abdomen | 66 | SNpp02 36, SNpp01 25 |

MANC naming scheme **[P]**: *"5927 sensory neurons (SN) relay information through a nerve from the
periphery and terminate in the VNC and 535 sensory ascending neurons (SA) ascend to the brain."*
Sensory neurons were *"broadly classified as chemosensory..., tactile..., proprioceptive..., or of
unknown modality."* Prefix `SN` = non-ascending, `SA` = ascending, followed by modality
abbreviation + cluster number. Nerves: **ProLN** prothoracic leg (T1), **MesoLN** mesothoracic (T2),
**MetaLN** metathoracic (T3), **ADMN** anterior dorsal mesothoracic, **DProN** dorsal prothoracic,
**AbN1-4** abdominal **[P]**. `MxLbN` (maxillary-labial) and `aPhN`/`PhN` (pharyngeal) appear in the
gustatory data **[V]**.

### Leg proprioception: the FeCO code [P]

Mamiya, Gurung & Tuthill 2018 Neuron: the femoral chordotonal organ has **152 neurons** in three
anatomical subtypes:

| Subtype | Encodes |
|---|---|
| **Claw** | **Tibia position** (flexion or extension) |
| **Hook** | **Directional tibia movement** (flexion or extension) |
| **Club** | **Tibia vibration** (bidirectional movement + vibration frequency) |

→ For the mod, synthesize a leg-phase oscillator and derive all three signals from it:
`claw ∝ joint angle`, `hook ∝ d(angle)/dt` (sign-split), `club ∝ |d(angle)/dt|` + high-frequency
component. **[G]**

Haltere: *"The haltere base is equipped with several fields of strain-sensitive campaniform
sensilla (~300 in Drosophila), a small number of sensory hairs and a chordotonal organ"* **[P]**.
During flight, drive at wingbeat frequency, amplitude-modulated by body angular velocity
(Coriolis). **[G]**

## d.5 Proposed Minecraft mechanosensory map [G]

| Minecraft event | Neuron population | Drive law |
|---|---|---|
| Sound event (any) 100-400 Hz band | **JO auditory** (114) | `rate ∝ amplitude`; JO-A weighted to >100 Hz content, JO-B to <100 Hz |
| Loud/close sound (explosion, thunder) | JO auditory, saturating | → **DNp01 GF → takeoff** |
| **Courtship song from another fly** | JO-B1_a/b/c preferentially | pulse train 200-400 Hz, IPI 35 ms |
| Wind (weather, elytra draft, self-motion airspeed) | **JO wind_gravity** (475), split L/R | `rate_L ∝ deflection_L`, `rate_R ∝ deflection_R`; azimuth emerges from L−R |
| Gravity / body tilt | JO-C/E static component | `∝ sin(pitch)`, `sin(roll)` |
| Falling / no ground contact | JO wind_gravity + leg hair plates go silent | tarsal-contact loss → flight initiation |
| Dust / powder snow / cobweb / rain on body | **BM_InOm** (eyes) → **BM_Taste** (proboscis) → antennal JO-F | drives the grooming sequence in priority order |
| Physical contact with block/entity | **SNta*** by body region | brief 50-100 ms burst |
| Taking damage | broad SNta + BM burst | escape |
| Walking (each step) | leg campaniform SApp*, hair plates SNpp19/45/52 | phase-locked to gait cycle |
| Flying | **haltere SApp (148)** at wingbeat freq, modulated by angular velocity | flight stabilization |
| Leg joint angles | **FeCO: claw/hook/club** (SNpp chordotonal) | from synthetic leg CPG |
| Food texture in proboscis | TPMN1/2, aPhM1-5 | during feeding |

---

# (e) FLY BODY AND BEHAVIOUR NUMBERS

## e.1 Morphometrics

| Quantity | Value | Conf |
|---|---|---|
| Body length, adult | **2-4 mm**, typically ~2.5-3 mm; ~2 mm wide | [L] |
| Male vs female | Females larger by **~0.2 mm** on average | [L] |
| Body mass | **~1 mg** | [L] |
| Wing length | not verified this session | — |

Sources for these were tertiary (Animal Diversity Web and similar). Adequate for choosing a mob
hitbox; do not cite them in anything scientific. A male at **2.5 mm** = **1/256 of a Minecraft
block**, so the mod will need a large scale exaggeration anyway. **[G]**

## e.2 Walking

| Quantity | Value | Source | Conf |
|---|---|---|---|
| Forward velocity range | **−1.3 to 30.4 mm/s** (2.5th-97.5th pct) | DeAngelis et al. 2019 eLife 46409 | [P] |
| Distribution peaks | **0 mm/s and ~17.5 mm/s** (bimodal) | same | [P] |
| Slow / medium / fast bins | **0-10.2 / 10.2-19 / >19 mm/s** | same | [P] |
| Gait | Tripod at all speeds | Wosnitza / DeAngelis | [L] |
| **Walking saccade threshold** | **200°/s yaw** | Geurten et al. 2014 | [P] |
| **Walking saccade magnitude** | **~15° heading change** in **40-120 ms (median 90 ms)** | Geurten 2014 | [P] |
| Saccade sample | 1,140 heading changes, 103 flies, 1,885 saccades | Geurten 2014 | [P] |

## e.3 Flight

| Quantity | Value | Source | Conf |
|---|---|---|---|
| **Wingbeat frequency** | **~189-230 Hz** depending on condition | various | [L] |
| **Flight saccade duration** | **49 ± 18 ms** (N=44), *"approximately nine wingbeats"* | Muijres et al. 2015 JEB 218:864 | [P] |
| **Flight saccade heading change** | **93 ± 27°**, range ~20° to almost 180° | Muijres 2015 | [P] |
| → implied mean yaw rate | ~1,900°/s | derived | [D] |
| Classic figure | ~90° in <100 ms | Tammero & Dickinson 2002 | [P] |
| Escape bank/counter-bank delay | **~25 ms (five wingbeats)** | Muijres 2015 | [P] |
| Wingbeat freq change during saccade | *"increases by only a few hertz"* | Muijres 2015 | [P] |
| Tethered "fictive" saccades | **~500 ms**, ~10× longer than free flight | Fry et al. 2003 | [P] |
| **Preferred visual pattern velocity** | **~0.15 m/s** front-to-back | Fry et al. 2009 JEB 212:1120 | [P] |
| Wind tunnel test speed | **0.29 m/s** | Fry 2009 | [P] |
| Acceleration response saturates | above **0.6 m/s** pattern velocity, plateau ~**3 m/s²** | Fry 2009 | [P] |
| Practical free-flight speed range | ~**0.1-0.6 m/s** cruise, up to ~1 m/s | inferred from above | [D] |

Fry 2009 measures *acceleration responses to pattern velocity*, not absolute airspeed, so the
"flight speed" figure has to be inferred. The commonly quoted 0.3-1.2 m/s range is consistent with
these data but I could not verify it directly. **[D]**

## e.4 Takeoff, escape, landing

| Quantity | Value | Source | Conf |
|---|---|---|---|
| **Postural adjustment before takeoff** | *"approximately 200 ms before takeoff, flies begin a series of postural adjustments that determine the direction of their escape"* | Card & Dickinson 2008 Curr Biol 18:1300 | [P] |
| Escape planning | Movements *"position their center of mass so that leg extension will push them away from the expanding visual stimulus"*; magnitude/direction depend on initial posture (not feed-forward) | Card & Dickinson 2008 | [P] |
| **Two escape modes** | **Short** (fast, sacrifices flight stability) vs **long** (slower, stable flight). Chosen by GF spike timing relative to parallel circuits | von Reyn et al. 2014 Nat Neurosci | [P] |
| Mode bias | *"When looming is faster... flies bias escape toward short takeoffs"* | von Reyn 2014 | [P] |
| GF looming model | GF response = **linear(angular velocity, from LC4) + Gaussian(angular size, from LPLC2)** | Ache et al. 2019 Curr Biol | [P] |
| GF synapse counts (hemibrain) | 55 LC4 → 2,442 synapses; 108 LPLC2 → 1,366 synapses | Ache 2019 | [P] |
| GF synapse counts (**male-cns**) | **LC4 → DNp01 6,362; LPLC2 → DNp01 4,862** | this session | **[V]** |
| Landing | Three distinct behavioural modules: steer toward target, decelerate, extend legs | van Breugel & Dickinson 2012 JEB 215:1783 | [P] |

Exact short/long takeoff latencies in ms were not obtainable — the von Reyn paper is paywalled and
my search budget ran out. **Gap.** [L]

## e.5 Grooming

| Quantity | Value | Source | Conf |
|---|---|---|---|
| **Sequence order** | **eyes > antennae > abdomen > wings > thorax** | Seeds et al. 2014 eLife 3:e02951 | [P] |
| Mechanism | *"motor programs that occur first suppress those that occur later"* — asymmetric suppression hierarchy | Seeds 2014 | [P] |
| Structure | *"each bout of body part cleaning featured cyclic transitions between sweeps of the targeted region and rubbing of the legs against each other"* | Seeds 2014 | [P] |
| **Leg sweep/rub frequency** | **5-7 Hz**, ~**200 ms per movement**; at 18°C ~**6 Hz** (~150 ms/movement) | Ravbar et al. 2021 eLife 71508 | [P] |
| **Bout length** | **150 ms to 2 s** | same | [P] |
| **Bout transition rhythm** | **0.3-0.6 Hz** (~2 s per bout) | same | [P] |
| Trigger | Coating flies with dust | Seeds 2014 | [P] |
| Head grooming detail | *"Dust-induced head grooming is performed by the forelegs that start with the eyes and progress to other locations such as the proboscis and antennae"* | eLife 108044 | [P] |

**Direct implementation:** a nested two-timescale oscillator — fast 6 Hz leg sweep inside a slow
0.5 Hz body-part selector, with the selector following the suppression hierarchy. **[G]**

## e.6 Courtship

| Quantity | Value | Source | Conf |
|---|---|---|---|
| **Sequence** | orienting → following → **tapping** (foreleg contact) → **wing extension + song** → **licking** → attempted copulation → copulation | multiple | [P] |
| **Sine song** | fundamental **140-170 Hz** (Clemens: **120-180 Hz**) | multiple | [P] |
| **Pulse song carrier** | **150-300 Hz**; split into **Pslow 200-250 Hz** and **Pfast 250-400 Hz** | Clemens et al. | [P] |
| **Inter-pulse interval** | **~35 ms** average | multiple | [P] |
| Pulse train length | **2-50 pulses** | multiple | [P] |
| **Distance drives song mode** | *"Males bias towards Pslow when close to the female (r²=0.92, p=1.0×10⁻⁹)"*; *"Distance is most predictive of pulse choice"* | Clemens et al. | [P] |
| Female response | *"Pfast was correlated with a reduction in female speed, but only when males were far away"*; *"Pslow and sine were correlated with reductions in female speed across a wide range of distances"* | Clemens et al. | [P] |
| Song patterning | Males use *"fast modulations in visual and self-motion signals to pattern their songs"* | Coen et al. 2014 Nature 507:233 | [P] |
| **Visual pursuit** | *"males pursue females from behind by maintaining them within the frontal field of view"*; LC10a gain *"selectively increased during courtship"* | Hindmarsh Sten et al. 2021 Nature | [P] |
| LC10 silencing | Males *"unable to orient toward or maintain proximity to the female and do not predominantly use the ipsilateral wing when singing"* | Ribeiro et al. 2018 Cell | [P] |

**Gap:** I could not obtain absolute inter-fly distances in mm — the Coen 2014 PDF mirror refused
the connection and my search budget ran out. Clemens gives the *relationship* (close → Pslow) but
not the metric threshold. For the mod, a reasonable placeholder is **song onset within ~5-10 mm**
(≈2-4 body lengths) with the Pslow/Pfast crossover around the middle of that range. **Explicitly a
guess — mark it as tunable.** [G]

---

# (f) UNIFIED STIMULUS → NEURON TYPE → RATE MAPPING

## f.1 The rate law

Every sensory population uses the same saturating Hill transfer, which matches ORN and GRN
dose-response shapes: **[G]**

```
rate_i(t) = r_spont + (r_max - r_spont) * S^n / (K^n + S^n)

S      = C(d) * g_i * lateralGain(theta, side)     // effective drive, 0..1+
C(d)   = exp(-d / lambda)                          // distance falloff, lambda in blocks
g_i    = per-type drive from the mapping tables above
n      = 1.5     (Hill coefficient)
K      = 0.2     (half-max)
```

Bilateral gain, so that gradients are computable by the network rather than by Java: **[G]**

```
lateralGain(theta, LEFT)  = 1 + A * sin(theta)
lateralGain(theta, RIGHT) = 1 - A * sin(theta)
A = 0.3;   theta = bearing of the source relative to fly heading
```

## f.2 Suggested rate constants

These are **[G]** design values, anchored loosely to published physiology **[L]**. Tune freely.

| Population | r_spont (Hz) | r_max (Hz) | lambda (blocks) | Notes |
|---|---:|---:|---:|---|
| ORNs (all `ORN_*`) | 8 | 250 | 6.0 | Hill n=1.5. Aversive channels (DA2, V, DL4) use K=0.35 so they engage only at close range |
| DM5 specifically | 5 | 250 | 6.0 | **K = 0.5** — reproduces the concentration-dependent valence flip |
| HRN_* (hygro) | 10 | 120 | n/a (ambient) | driven by biome humidity + rain + water proximity |
| TRN_* (thermo) | 10 | 150 | 3.0 | VP2 from fire/lava, VP3 from ice/snow |
| GRNs (LB, LgLG, WG, PhG) | 2 | 120 | contact only | binary contact gate × concentration |
| L1 / L2 / L3 (visual injection) | 25 | 200 | n/a | graded; **L2 sign-inverted vs L1** |
| R7/R8 (if driven directly) | 20 | 180 | n/a | chromatic; remember histamine = inhibitory |
| LC4 (looming velocity) | 3 | 200 | n/a | `∝ dθ/dt`, linear |
| LPLC2 (looming size) | 3 | 200 | n/a | `∝ Gaussian(θ; μ=60°, σ=25°)` |
| JO auditory | 5 | 300 | 8.0 | band-split A/B at 100 Hz |
| JO wind_gravity | 15 | 180 | n/a | tonic, deflection-proportional |
| JO grooming (JO-F) | 2 | 150 | contact | antennal dust |
| BM_* (bristle) | 1 | 200 | contact | 50-100 ms burst per contact |
| SNta* (tactile) | 1 | 200 | contact | per body region |
| SNpp/SApp (proprioceptive) | 20 | 150 | n/a | from leg CPG / haltere oscillator |

## f.3 Neurotransmitter sign map — required for the LIF network [V]+[L]

The orchestrator already established the `consensusNt` distribution. The sign each implies:

| consensusNt | Cells | Sign | Note |
|---|---:|---|---|
| acetylcholine | ~104,000 | **+ excitatory** | all ORNs, most GRNs, most VPNs |
| glutamate | ~29,000 | **− inhibitory** | fly GluCl is inhibitory — **not** like vertebrates |
| GABA | ~22,000 | **− inhibitory** | e.g. DNg60 |
| **histamine** | ~8,000 | **− inhibitory** | **the photoreceptors** — see c.3 |
| dopamine / octopamine / serotonin | smaller | **modulatory** | model as gain terms, not fast synapses |
| unclear | ~9,800 | default **+** or drop | 9,800 cells; consider zero-weighting |

Two of these are counterintuitive and will silently break the network if you get them wrong:
**glutamate is inhibitory in flies**, and **histamine is inhibitory** (making the photoreceptor →
lamina synapse a sign inversion).

---

# Appendix A: Reproducible queries

All against `POST https://neuprint.janelia.org/api/custom/custom`, body
`{"cypher": "...", "dataset": "male-cns:v1.0"}`, **no auth token required**.

```cypher
-- Full olfactory / hygro / thermo inventory
MATCH (n:Neuron) WHERE n.class IN ['olfactory','hygrosensory','thermosensory']
RETURN n.class, n.type, count(*), collect(DISTINCT n.consensusNt)[0..3] ORDER BY n.class, n.type

-- Gustatory types with organ and nerve
MATCH (n:Neuron) WHERE n.class = 'gustatory'
RETURN n.type, n.subclass, count(*), collect(DISTINCT n.entryNerve)[0..3] ORDER BY n.type

-- Mechanosensory, all three classes
MATCH (n:Neuron) WHERE n.class IN ['mechanosensory','mechanosensory_tactile','mechanosensory_proprioceptive']
RETURN n.class, n.subclass, n.type, count(*) ORDER BY n.class, n.subclass, n.type

-- Photoreceptors and visual projection neurons, with hex coverage
MATCH (n:Neuron) WHERE n.superclass IN ['ol_sensory','visual_projection']
RETURN n.superclass, n.class, n.type, count(*), count(n.assignedOlHex1) ORDER BY n.superclass, n.type

-- Hex column extents per type and side
MATCH (n:Neuron) WHERE n.assignedOlHex1 IS NOT NULL
RETURN n.type, n.somaSide, count(*), min(n.assignedOlHex1), max(n.assignedOlHex1),
       min(n.assignedOlHex2), max(n.assignedOlHex2) ORDER BY count(*) DESC

-- Hex axis derivation: L1 soma positions vs hex coords (then regress y on h1,h2)
MATCH (n:Neuron {type:'L1'}) WHERE n.assignedOlHex1 IS NOT NULL AND n.somaLocation IS NOT NULL
RETURN n.somaSide, n.assignedOlHex1, n.assignedOlHex2,
       n.somaLocation.x, n.somaLocation.y, n.somaLocation.z

-- DRA columns: which hex columns receive R7d/R8d input
MATCH (a:Neuron)-[c:ConnectsTo]->(b:Neuron)
WHERE a.type IN ['R7d','R8d'] AND b.assignedOlHex1 IS NOT NULL AND c.weight >= 5
RETURN b.somaSide, b.assignedOlHex1, b.assignedOlHex2, count(DISTINCT a) ORDER BY b.somaSide

-- Column ROI names (confirms ME_<side>_col_<hex1>_<hex2>)
MATCH (m:Meta) RETURN m.roiInfo

-- Sensory + visual drive onto key descending neurons
MATCH (a:Neuron)-[c:ConnectsTo]->(b:Neuron)
WHERE b.type IN ['DNp01','DNp02','DNp03','DNp04','DNp11','DNp09','DNp10','MDN','DNa02','DNp20','DNp22','pIP10']
  AND a.superclass IN ['visual_projection','ol_sensory','cb_sensory','vnc_sensory']
WITH b.type AS dn, a.type AS src, sum(c.weight) AS w ORDER BY dn, w DESC
WITH dn, collect({src:src, w:w})[0..8] AS top RETURN dn, top

-- JO output targets by functional subclass
MATCH (a:Neuron)-[c:ConnectsTo]->(b:Neuron) WHERE a.type STARTS WITH 'JO-'
WITH a.subclass AS sub, b.type AS tgt, sum(c.weight) AS w ORDER BY sub, w DESC
WITH sub, collect({t:tgt, w:w})[0..10] AS top RETURN sub, top
```

Companion data files written alongside this report:
- `columns_R.csv`, `columns_L.csv` — every hex column with `hex1, hex2, u_dv, w_ap, ncells`
  (892 and 879 rows). Directly loadable as the mod retinotopic index.

---

# Appendix B: Open questions and known gaps

1. **Exact per-column visual direction.** The linear u/w → (elevation, azimuth) map in c.2 is an
   approximation; true ΔΦ varies across the eye. The authoritative eyemap likely lives in the
   Reiser lab repo or the Male CNS Cell Type Explorer. Not located this session.
2. **Best-ligand table.** ~15 Or→odorant pairings are [L] recall, not re-verified against DoOR 2.0
   or Hallem & Carlson 2006. Both fetches were blocked (403 / search budget).
3. **VP1l vs VP1m.** Dataset class labels contradict Marin 2020 receptor assignments. Unresolved in
   the literature itself.
4. **Short vs long takeoff latencies (ms).** von Reyn 2014 is paywalled; only the qualitative
   mode distinction was obtained.
5. **Inter-fly courtship distances in mm.** Relationship (close → Pslow) verified; absolute
   thresholds not. The Coen 2014 PDF mirror refused connection.
6. **Grooming per-movement time budget.** Sequence order and oscillation frequencies verified; the
   percentage of time per body part was not in the accessible text.
7. **LB2a-d and LB4a-b modality.** Genuinely unknown in the source: *"no receptor-GAL4 driver line
   that matched the overall anatomies"*. 22 cells. Leave silent or treat as generic tactile.
8. **R1-R6 column assignment** is approximate by construction (neural superposition), and the
   lamina is under-reconstructed. Mitigated by injecting at L1/L2/L3.
9. **Wing/haltere GRN sexual dimorphism.** WG1-4 all show *"sexually dimorphic"* downstream
   connectivity per the taste connectome, but the male-specific targets were not enumerated here.

---

# Appendix C: Sources

**Primary, fetched and quoted this session:**
- Task, D. et al. (2022) Chemoreceptor co-expression in *Drosophila melanogaster* olfactory neurons. *eLife* 11:e72599. https://elifesciences.org/articles/72599 — **Table 3 is the receptor→glomerulus authority.**
- Marin, E.C. et al. (2020) Connectomics analysis reveals first-, second-, and third-order thermosensory and hygrosensory neurons in the adult *Drosophila* brain. *Curr Biol* 30:3167-3182. https://pmc.ncbi.nlm.nih.gov/articles/PMC7443704/
- Engert, S., Sterne, G.R., Bock, D.D. & Scott, K. (2022) *Drosophila* gustatory projections are segregated by taste modality and connectivity. *eLife* 11:e78110. https://elifesciences.org/articles/78110
- *From Sensory Detection to Motor Action: The Comprehensive Drosophila Taste-Feeding Connectome* (2025) bioRxiv 2025.08.25.671814. https://www.biorxiv.org/content/10.1101/2025.08.25.671814v1.full — **the LB/LgLG/WG/PhG naming authority.**
- Zhao, A. et al. (2025) Eye structure shapes neuron function in *Drosophila* motion vision. *Nature*. https://pmc.ncbi.nlm.nih.gov/articles/PMC12488493/ — **eye geometry and FOV authority.**
- Nern, A. et al. (2025) Connectome-driven neural inventory of a complete visual system. *Nature* 641:1225-1237. https://pmc.ncbi.nlm.nih.gov/articles/PMC12119369/
- Muijres, F.T. et al. (2015) Body saccades of *Drosophila* consist of stereotyped banked turns. *J Exp Biol* 218:864.
- Fry, S.N., Rohrseitz, N., Straw, A.D. & Dickinson, M.H. (2009) Visual control of flight speed in *Drosophila melanogaster*. *J Exp Biol* 212:1120-1130.
- Seeds, A.M. et al. (2014) A suppression hierarchy among competing motor programs drives sequential grooming in *Drosophila*. *eLife* 3:e02951.
- *A comprehensive mechanosensory connectome... head grooming* (2025) *eLife* 108044. https://elifesciences.org/articles/108044
- Clemens, J. et al. Discovery of a new song mode in *Drosophila*. bioRxiv 221044. https://www.biorxiv.org/content/10.1101/221044v1.full
- Marin, E.C. et al., MANC systematic annotation. *eLife* reviewed preprint 97766.
- Juusola, M. & Hardie, R.C. (2001) Light adaptation in *Drosophila* photoreceptors I & II. *J Gen Physiol* 117:3-25, 27-59.

**Primary, established via search-result summaries (headline results only):**
- Semmelhack, J.L. & Wang, J.W. (2009) Select *Drosophila* glomeruli mediate innate olfactory attraction and aversion. *Nature* 459:218. (Full text blocked by reCAPTCHA/403.)
- Stensmyr, M.C. et al. (2012) A conserved dedicated olfactory circuit for detecting harmful microbes in *Drosophila*. *Cell* 151:1345.
- Suh, G.S.B. et al. (2004) A single population of olfactory sensory neurons mediates an innate avoidance of CO2 in *Drosophila*. *Nature* 431:854.
- Dweck, H.K.M. et al. (2015) Pheromones mediating copulation and attraction in *Drosophila*. *PNAS* 112:E2829.
- Grosjean, Y. et al. (2011) An olfactory receptor for food-derived odours promotes male courtship in *Drosophila*. *Nature* 478:236-240.
- Min, S. et al. (2013) Dedicated olfactory neurons mediating attraction behavior to ammonia and amines in *Drosophila*. *PNAS* 110:E1321.
- Hussain, A. et al. (2016) Ionotropic chemosensory receptors mediate the taste and smell of polyamines. *PLoS Biol* 14:e1002454.
- Vulpe, A. et al. (2021) An ammonium transporter is a non-canonical olfactory receptor for ammonia. *Curr Biol* 31:3382.
- Ebrahim, S.A.M. et al. (2015) *Drosophila* avoids parasitoids by sensing their semiochemicals via a dedicated olfactory circuit. *PLoS Biol* 13:e1002318.
- Kamikouchi, A. et al. (2009) The neural basis of *Drosophila* gravity-sensing and hearing. *Nature* 458:165-171.
- Hampel, S. et al. (2020) Distinct subpopulations of mechanosensory chordotonal organ neurons elicit grooming of the fruit fly antennae. *eLife* 9:e59976.
- Suver, M.P. et al. (2019) Encoding of wind direction by central neurons in *Drosophila*. *Neuron* 102:828.
- Suver, M.P. et al. (2016) An array of descending visual interneurons encoding self-motion in *Drosophila*. *J Neurosci* 36:11768.
- Mamiya, A., Gurung, P. & Tuthill, J.C. (2018) Neural coding of leg proprioception in *Drosophila*. *Neuron* 100:636.
- Ache, J.M. et al. (2019) Neural basis for looming size and velocity encoding in the *Drosophila* giant fiber escape pathway. *Curr Biol* 29:1073.
- von Reyn, C.R. et al. (2014) A spike-timing mechanism for action selection. *Nat Neurosci* 17:962.
- Card, G. & Dickinson, M.H. (2008) Visually mediated motor planning in the escape response of *Drosophila*. *Curr Biol* 18:1300-1307.
- van Breugel, F. & Dickinson, M.H. (2012) The visual control of landing and obstacle avoidance in the fruit fly. *J Exp Biol* 215:1783-1798.
- Wu, M. et al. (2016) Visual projection neurons in the *Drosophila* lobula link feature detection to distinct behavioral programs. *eLife* 5:e21022.
- Klapoetke, N.C. et al. (2022) A functionally ordered visual feature map in the *Drosophila* brain. *Neuron* 110:1700.
- Ribeiro, I.M.A. et al. (2018) Visual projection neurons mediating directed courtship in *Drosophila*. *Cell* 174:607.
- Hindmarsh Sten, T. et al. (2021) Sexual arousal gates visual processing during *Drosophila* courtship. *Nature* 595:549.
- Coen, P. et al. (2014) Dynamic sensory cues shape song structure in *Drosophila*. *Nature* 507:233-237.
- Geurten, B.R.H. et al. (2014) Saccadic body turns in walking *Drosophila*. *Front Behav Neurosci*.
- DeAngelis, B.D. et al. (2019) The manifold structure of limb coordination in walking *Drosophila*. *eLife* 8:e46409.
- Ravbar, P. et al. (2021) Behavioral evidence for nested central pattern generator control of *Drosophila* grooming. *eLife* 10:e71508.
- Garner, D. et al. (2024) Connectomic reconstruction predicts visual features used for navigation. *Nature* 634:181.
- Münch, D. & Galizia, C.G. (2016) DoOR 2.0 — comprehensive mapping of *Drosophila melanogaster* odorant responses. *Sci Rep* 6:21841. http://neuro.uni.kn/DoOR

**Dataset:**
- Male CNS Connectome. https://male-cns.janelia.org/ · https://neuprint.janelia.org (dataset `male-cns:v1.0`) · preprint bioRxiv 2025.10.09.680999 · licensed CC-BY.
