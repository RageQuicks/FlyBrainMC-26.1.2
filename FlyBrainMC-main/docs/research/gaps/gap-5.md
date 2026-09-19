# GAP 5 — Internal state and context gating for the male-cns LIF fly

**Question:** name the neurons, magnitudes and time constants that supply hunger/satiety, courtship arousal, flight-vs-walking state, and feeding-linked walk-OFF, and give one concrete LIF implementation per state.

**Method:** primary literature via Europe PMC REST + PMC full text; all connectome numbers from live anonymous Cypher POST to `https://neuprint.janelia.org/api/custom/custom`, dataset `male-cns:v1.0`. Every synapse count below was queried in this session and is reproducible with the helper at `.../scratchpad/research/np.py`.

> **WebSearch was unavailable** (session budget exhausted at 200 calls). All literature was recovered through the Europe PMC REST API and PMC full text via WebFetch, which is why some secondary claims below are marked *unverified*.

---

## 0. Unit system — the LIF calibration all magnitudes below are expressed in

Adopt the Shiu/Sterne 2024 whole-brain LIF convention. This is the only published connectome-scale fly LIF model, it is by the same lab that named Fudog/Fdg/Roundup, and it fixes the mV units so "a bias current of X mV" is meaningful.

| Parameter | Value | Confidence |
|---|---|---|
| `V_rest` = `V_reset` | **-52 mV** | high |
| `V_threshold` | **-45 mV** (=> **7 mV** swing to spike) | high |
| `R_mbr` | 10 kOhm cm^2 | high |
| `C_mbr` | 2 uF cm^-2 | high |
| `tau_mbr` = R*C | **20 ms** | high |
| `T_refractory` | **2.2 ms** | high |
| `tau_syn` (synaptic decay) | **5 ms** | high |
| `W_syn` (per anatomical synapse) | **0.275 mV** | high |
| `T_dly` (spike->membrane) | **1.8 ms** | high |
| Sign rule | GABA + **glutamate** inhibitory; ACh, dopamine, octopamine, serotonin excitatory | high |
| Sensory drive used | Poisson, **10-200 Hz** (sugar GRN), 20-260 Hz (water GRN) | high |

Source: Shiu PK, Sterne GR, et al. *A Drosophila computational brain model reveals sensorimotor processing.* Nature 634(8032):210-219 (2024). DOI 10.1038/s41586-024-07763-9, PMID 39358519, PMC11446845 — Methods.

**Two derived conversion constants used throughout this report:**
- **1 mV of tonic bias = 3.64 synapses** of standing excitatory drive (1 / 0.275).
- **A full 7 mV bias takes a neuron from rest to threshold**, i.e. 100% bias = free-running. Keep every gate below 7 mV unless you intend a seizure.

WARNING on the sign rule: **glutamate is inhibitory** in this convention. Several neurons below (DNb01, PS188, CL184/185/186, GNG022, LT51, DNge055, Scapula/GNG087) are glutamatergic and will be *inhibitory*.

---

## 1. FLIGHT vs WALKING — the best-supported gate, with real numbers

### 1.1 Literature

**Ache JM, Namiki S, Lee A, Branson K, Card GM. *State-dependent decoupling of sensory and motor circuits underlies behavioral flexibility in Drosophila.* Nature Neuroscience 22(7):1132-1139 (2019). DOI 10.1038/s41593-019-0413-4, PMID 31182867, PMC7444277.**

Abstract, verbatim: *"we identify two descending neuron (DN) types that control landing... For each, silencing impairs visually evoked landing, activation drives landing, and spike rate determines leg extension amplitude. Critically, visual responses of both DNs are severely attenuated during non-flight periods, effectively decoupling visual stimuli from the landing motor pathway when landing is inappropriate. The flight-dependence mechanism differs between DN types. Octopamine exposure mimics flight effects in one, whereas the other probably receives neuronal feedback from flight motor circuits."*

Extracted quantities (full text, PMC7444277) — **confidence high** for the two headline numbers, **medium** for the rest (single-pass extraction):

| Quantity | Value |
|---|---|
| DNp07 response, flight -> non-flight | **92 Hz -> 27 Hz** (~**70%** attenuation; ratio **3.41x**) at 1000 deg/s front-to-back bar |
| DNp10 response in non-flight | **supra-threshold visual response eliminated** |
| DNp07 + octopamine (non-flight) | **more than doubled** (~2x) the non-flight visual response; OA at **1.6 mM**; p << 0.001 |
| DNp10 + octopamine | **no effect (p = 0.24)** |
| DNp10 tonic depolarisation during flight | **3.33 mV** (p = 3e-57), repolarises on flight cessation |
| Optogenetic activation -> first leg movement | DNp07 **26 +/- 5 ms**; DNp10 **21 +/- 4 ms** |

This is the cleanest handle in the whole gap: one DN wants a **multiplicative sensory gain**, the other wants a literal **tonic depolarisation in mV**, and the paper states both.

**Liessem S, ... Ache JM. *Control of walking direction by descending and dopaminergic neurons in Drosophila.* Current Biology 36(15):3808-3822.e6 (2026). DOI 10.1016/j.cub.2026.06.045, PMID 42442357.** (preprint: bioRxiv 10.1101/2025.07.22.666129)

Abstract, verbatim: *"both MDN and DopaMeander are gated out during flight, suggesting that neuronal populations across levels of control are modulated by the behavioral state to minimize crosstalk between motor programs."* MDN integrates **antennal touch** to switch forward->backward walking. **Confidence high** for the gating claim; **no effect size or time constant is given in the abstract**, and the full text is not in PMC. *The magnitude of the MDN flight gate is therefore unverified and must be hand-set.*

**Bonus, same lab, uses this connectome:** *Parallel neuronal ensembles control behavior across sensorimotor levels in Drosophila*, bioRxiv 2025, DOI 10.64898/2025.12.13.693955 — reconstructs the **complete feedforward landing pathway** (visual feature detectors -> DN ensemble -> VNC premotor core) **from the whole-CNS connectome**, contrasting landing vs takeoff. Mine this if the landing demo needs more than DNp07/DNp10. Also *A dedicated brain circuit controls forward walking* (bioRxiv 2026, DOI 10.64898/2026.01.04.697356): the forward-walking drive is *"suppressed during flight"* — a third independent statement of the same gate.

### 1.2 male-cns handles (all verified this session)

```
DNp07  bodyId 11704 (L), 11513 (R)   acetylcholine   pre 2443/2546  post 6319/6083
DNp10  bodyId 10425 (L), 10433 (R)   acetylcholine   pre 2844/2973  post 11460/11268
MDN    bodyId 11288, 12348 (L); 11332, 10763 (R)   acetylcholine
       synonym "Carreira-Rosario 2018: DNp50"
```

**DNp07 visual input is dominated by one type — this is the edge set to apply gain to:**

| -> DNp07 (w>=10) | NT | Sum synapses |
|---|---|---|
| **LPLC4** | ACh | **2211** |
| LoVP18 | ACh | 799 |
| GNG302 | GABA | 551 |
| PLP214 | Glu (inhib.) | 468 |
| PLP092 | ACh | 453 |
| CB4072 | ACh | 249 |
| PS188 | Glu (inhib.) | 247 |

**DNp10 has no single dominant visual driver** (consistent with Ache: gated by motor feedback, not sensory-side neuromodulation):

| -> DNp10 (w>=10) | NT | Sum |
|---|---|---|
| PS088 | GABA | 782 |
| CB4072 | ACh | 668 |
| CB4073 | ACh | 558 |
| PS058 | ACh | 517 |
| SAD044 | ACh | 483 |
| AN06B002 | GABA | 406 |
| CL184 | Glu (inhib.) | 389 |
| JO-ED2_b | ACh (mechanosensory) | 171 |

**Direct aminergic input is negligible** — checked explicitly, because it decides whether octopamine can be an edge or must be a global scalar:

| target | aminergic source | NT | Sum |
|---|---|---|---|
| MDN | **OA-VUMa1** | octopamine | **103** |
| MDN | PPM1205 | dopamine | 67 |
| DNp10 | OA-VUMa6 | octopamine | 15 |
| DNp07 | OA-VUMa4 | octopamine | 12 |
| DNp07 | LoVCLo3 | octopamine | 6 |

12 synapses onto DNp07 = 3.3 mV nominal — far too little to carry a 3.4x gain change. **Conclusion: octopaminergic flight state is volume transmission and must be implemented as a global scalar, not as connectome edges.** This also exposes a sign trap: under the Shiu convention octopamine is *excitatory*, so OA-VUMa1->MDN (103 syn) would **excite** MDN during flight — the **opposite** of the observed "MDN gated out during flight". Do not let the OA edges drive the MDN gate.

Genuine flight-state proprioceptors exist, if you prefer a sensory-side source over a mod-side boolean:

| subclass | count | representative types |
|---|---|---|
| **haltere** | **201** | SApp (148), SNpp34 (8), SNpp25 (7), SNpp15/20/23/14/35 (6 each) |
| campaniform sensilla | 426 | SApp09/22 (74), SApp08 (47), SApp06/15 (37), SApp10 (37) |
| wing | 19 | SNxx26 (12), SNpp61 (10), SNpp62 (9) |

### 1.3 Concrete LIF implementation — FLIGHT

Drive everything from one mod-side scalar `flight` in [0,1] (1 while the mob's isFlying/takeoff state is set), smoothed first-order, **tau_rise = 100 ms, tau_fall = 500 ms** (hand-set; Ache reports DNp10 "repolarises upon flight cessation" but gives no decay tau — *unverified*).

```java
// 1a. DNp10 -- LITERATURE-EXACT tonic depolarisation (Ache 2019: 3.33 mV)
bias[DNp10] = 3.33f * flight;          // mV, added to V each tick before leak
// = 47.6% of the 7 mV rest->threshold gap = 12.1 equivalent synapses

// 1b. DNp07 -- multiplicative gain on its visual input edges (Ache 2019: 92/27)
gain(LPLC4 -> DNp07) = 0.293f + 0.707f * flight;   // 0.293 grounded, 1.0 when flying
// full weight in flight, x0.293 on the ground => 3.41x ratio
// apply to the LPLC4->DNp07 edge set (2211 syn) and optionally LoVP18 (799 syn)

// 1c. MDN + forward-walking drive -- gated OUT during flight (Liessem/Ache 2026;
//     magnitude NOT reported -> hand-built, least-invasive choice)
bias[MDN]            = -3.0f * flight;   // mV
bias[DNa02]          = -2.0f * flight;   // turning
bias[DNg97 /*oDN1*/] = -2.0f * flight;   // forward walking
```

Rationale for -3.0 mV: 43% of the rest->threshold gap, so it roughly halves excitability rather than hard-clamping, matching "gated out" without making the neuron unrecoverable if a strong antennal-touch transient arrives. **Mark as tuned, not measured.**

**Why this fixes the stated bug:** DNp07/DNp10 stop commanding landing while walking, and — exactly as the biology does — the block sits on the *sensory side of the DN* (DNp07 gain) or as a *standing bias* (DNp10), not as a veto in the motor decoder. A looming stimulus while walking still produces sub-threshold DNp07 depolarisation; it just never reaches spike.

---

## 2. HUNGER / SATIETY

### 2.1 Literature — where hunger acts, and where it does NOT

**Shiu PK, Sterne GR, Engert S, Dickson BJ, Scott K. *Taste quality and hunger interactions in a feeding sensorimotor circuit.* eLife 11:e79887 (2022).**

The most useful result for this mod is a **negative** one (**confidence high**, direct quotes):

- *"CsChrimson-mediated activation of sugar GRNs caused higher proboscis extension rates in food-deprived flies than in fed flies"* -> **hunger acts at the sensory periphery.**
- *"Activation of two second-order neurons, G2N-1, and Clavicle, increased proboscis extension in food-deprived flies, whereas activation of all other neural classes did not"* -> **hunger acts at exactly two second-order nodes.**
- *"activation of MN9 elicited the same proboscis extension rate in food-deprived and fed flies"* -> **hunger does NOT act at the motor neuron.**
- Starvation protocol: *"wet-starved... for 18-24 hr"*.
- *"The 15 second-order neurons... receive 21% of sugar GRN synaptic outputs"*; premotor neurons are *"approximately 13% of the synaptic input onto MN9"*; Scapula *"receives over 150 synapses from bitter GRNs"*.

That is a precise instruction: **put the satiety gain on the sugar GRNs and on G2N-1 / Clavicle. Do not put it on MN9.**

**Correction to the project brief:** the framing "hunger-gated Fudog responses" is *not* what the paper shows — Fudog (DNg67) is a third-order neuron with no isolated hunger-dependence reported. **Confidence high that MN9 is state-independent; confidence high that Fudog is not the documented hunger node.** Fudog nevertheless *inherits* the gate because it is directly sugar-driven (below), so nothing is lost.

**Satiety physiology, for the satiety variable itself:**
- **Held M, Bisen RS, ... Ache JM. *Nutritional state-dependent modulation of insulin-producing cells in Drosophila.* eLife 13:RP98514 (2025). PMID 39878318, PMC11778929.** Verbatim: *"IPC activity decreased with increasing periods of starvation. Refeeding flies with glucose or fructose, two nutritive sugars, significantly increased IPC activity, whereas non-nutritive sugars had no effect... activating IPCs had a small, satiety-like effect on food-searching behavior and reduced starvation-induced hyperactivity, whereas activating DH44Ns strongly increased hyperactivity."* **Confidence high.** Gives a principled semantics: **IPC firing = satiety; DH44 = hunger drive**, and only *nutritive* food counts.
- **Bisen RS, ... Ache JM. Curr Biol 33(3):449-463.e5 (2023). PMID 36580915.** IPCs are *"strongly inhibited during walking and flight"* with rebound/overshoot after. Optional flavour.

**NOT VERIFIED:** I could **not** retrieve Inagaki 2012 Cell / Inagaki 2014 Nature (sNPF + dopamine presynaptic gain on sugar GRNs) or Marella 2012 Neuron (dopaminergic TH-VUM) through Europe PMC — author queries returned unrelated modern homonyms. **The specific sNPF/NPF/AstA/dopamine magnitudes the gap question asks for are UNVERIFIED here; do not cite numbers for them.** The Shiu 2022 locus result is sufficient to build the gate and is directly about this circuit.

**Also not verified:** the Cell paper at DOI 10.1016/j.cell.2026.08.016 (Tastekin 2026) does not appear in Europe PMC. What exists, clearly the same work, are two 2025 bioRxiv preprints:
- *From Sensory Detection to Motor Action: The Comprehensive Drosophila Taste-Feeding Connectome*, DOI **10.1101/2025.08.25.671814**
- *Connectomics Reveals a Feed-Forward Swallowing Circuit Driving Protein Appetite*, DOI **10.1101/2025.08.25.671815** — identifies a **"Sustain" neuron** coordinating swallowing MNs; *"protein deprivation prolongs protein-specific feeding bursts"*.

Treat the Cell DOI as plausible-but-unconfirmed.

### 2.2 male-cns handles

**Sugar vs bitter GRNs are not a dataset field.** I resolved them *operationally* by asking which gustatory types feed the known sugar second-order neurons vs the known bitter neuron. Reproducible; **confidence high** for the sugar set, which is unambiguous.

**Sugar GRNs = LB3a, LB3b, LB3c, LB3d** (labellar bristle):

| -> G2N-1 (GNG232) | Sum | | -> Clavicle (ANXXX462a) | Sum |
|---|---|---|---|---|
| LB3c | 519 | | LB3d | 616 |
| LB3d | 485 | | LB3c | 511 |
| LB3a | 30 | | LB3b | 273 |
| LB3b | 18 | | LB3a | 221 |
| LB4b | 18 | | LB1e | 37 |

**Bitter GRNs = LB1a, LB1b, LB1c, LB1d + leg LgAG5/LgAG1** (via Scapula GNG087, the bitter node):

| -> Scapula (GNG087) | Sum |
|---|---|
| **LB1c** | **1738** |
| LgAG5 (leg bristle) | 734 |
| LB1b | 351 |
| LB1a | 123 |
| LB1d | 90 |
| LgAG1 | 34 |

(LB1e appears weakly in both lists — ambiguous; leave it out of both gates.)

Gustatory subclass census: leg bristle 768, wing bristle 385, labellar bristle 163, taste peg 60, pharyngeal sensillum 48.

**Named Shiu-2022 feeding neurons, all present with bodyIds (verified):**

| Name | Type | bodyIds | NT |
|---|---|---|---|
| **G2N-1** | GNG232 | 31018, 89638 | ACh |
| **Clavicle** | ANXXX462a | 514625, 19480 | ACh |
| Phantom | GNG229 | 13083, 18590 | GABA |
| Usnea | GNG175 | 17709, 518540 | GABA |
| Zorro | GNG215 | 519441, 21828 | ACh |
| FMIn | GNG197 | 18880, 26027 | ACh |
| Rattle | GNG132 | 15259, 18474 | ACh |
| Fuchs | GNG230 | 25783, 534419 | ACh |
| Fdg | GNG588 | 14321, 12617 | ACh |
| Roundup | GNG108 | 26764, 523040 | ACh |
| Roundtree | GNG120 | 513162, 10881 | ACh |
| Scapula | GNG087 | 12900, 12811, 11896 | **glutamate (inhibitory)** |
| Billiards / Specter | GNG038 | 21746 / 512550 | GABA |
| Quasimodo | GNG042 | 15321, 15734 | GABA |
| Sternum | GNG585 | 10961, 12402, 13611 | ACh |
| Dandelion | AN13B002 | 30088, 21763 | GABA |
| **Fudog** | **DNg67** | **516215 (L), 22758 (R)** | ACh |

**Fudog is directly sugar-driven** (so it inherits the peripheral gate automatically — no separate Fudog gate needed):
LB3a **372**, LB3c **126**, LB3d **123**, LB3b **69** (Sum LB3 = **690**), plus GNG354 (GABA) 60, LgAG9 (Glu) 37, SMP545 (GABA) 34.

**MN9 — data caveat, important:** `MN9 bodyId 10331 (L) post=6358` but `bodyId 16949 (R) post=633`. The right MN9 is an order of magnitude less reconstructed. **Use the left MN9 (10331) as the proboscis-extension readout**, or sum both and accept the asymmetry.

Top MN9 inputs: DNge062 556, GNG015 (GABA) 478, **GNG120/Roundtree 442**, GNG095 (GABA) 432, GNG117 410, GNG130 (GABA) 407, **GNG108/Roundup 380**, GNG234 362, GNG180 (GABA) 287, **DNge080/Rounddown 219**.

**Satiety endocrine handles:** `IPC` **16 cells** (cb_endocrine), `DH44` **6**, `Hugin-RG` **4**, `AstA1` 2 (GABA), `NPFL1-I` 2, `CRZ01/02` 2 each, `LK` 12 (vnc_endocrine), `ITP` 7, `DMS` 6. **All cb_endocrine neurons have `consensusNt = "unclear"`** — no usable sign under the LIF rule, so **drive the satiety variable in software rather than simulating IPCs as LIF units.** IPC top inputs if ever needed: AN27X024 (Glu) 2551, PRW075 2206, PRW004 (Glu) 1330, PRW016 920.

**Ingestion signal (what should increment satiety):** pharyngeal sensilla (`PhG*`, 48 neurons) are the swallow/ingestion detectors — they fire only when food actually passes the pharynx, exactly the "ingested nutrition" event. Top targets: GNG035 (GABA) 3531, GNG406 3042, GNG039 (GABA) 2742, GNG320 (GABA) 2462.

### 2.3 Concrete LIF implementation — HUNGER

```java
// SATIETY VARIABLE (software, 0 = starved, 1 = fully fed)
// Grounded in Held/Ache 2025: only NUTRITIVE food raises it; it decays with time.
float satiety;                        // persisted on the mob's NBT
// increment on each pharyngeal-swallow event (PhG* burst) while feeding
satiety += foodNutrition * 0.02f;     // Minecraft FoodProperties.nutrition (1..20)
satiety  = clamp(satiety, 0f, 1f);
// decay: Shiu 2022 starved flies 18-24 h; map to a playable timescale.
// 20 real minutes (1 MC day) from sated -> starved:
satiety -= (1f / (20f * 60f * 20f));  // per tick @20 tps
float hunger = 1f - satiety;

// GATE 2a. Sugar GRN gain -- the peripheral node Shiu 2022 identifies.
// SENSORY SIDE: scale the Poisson rate injected into LB3a..LB3d.
rate(LB3a..LB3d) = (40f + 160f * hunger) * sugarContact;   // Hz, inside Shiu's 10-200 Hz band
// starved -> 200 Hz, fully fed -> 40 Hz (5x dynamic range)

// GATE 2b. The two documented hunger-modulated second-order neurons.
bias[GNG232    /*G2N-1*/]    = 1.5f * hunger;   // mV
bias[ANXXX462a /*Clavicle*/] = 1.5f * hunger;   // mV

// GATE 2c. MN9 -- DELIBERATELY UNGATED (Shiu 2022: identical PER fed vs starved)
// bias[MN9] = 0;   // <- do NOT add a hunger term here
```

1.5 mV = 21% of the rest->threshold gap ~ 5.5 equivalent synapses: a nudge, not a command — appropriate, since Shiu 2022 shows these neurons *modulate* PER probability rather than trigger it. **The 5x sensory range is grounded in direction and locus but tuned in magnitude; the 1.5 mV is entirely tuned.**

**Why this fixes the stated bug:** the fly feeds forever because the loop is `sugar GRN -> ... -> MN9` with no state. Now a sated fly drives LB3 at 40 Hz instead of 200 Hz, the sugar pathway falls below the level that recruits Roundup/Roundtree, and MN9 stops firing — *without touching MN9*, exactly as the biology requires. Eating raises `satiety`, closing the loop.

---

## 3. COURTSHIP AROUSAL — persistence with real time constants

### 3.1 Literature

**Hoopfer ED, Jung Y, Inagaki HK, Rubin GM, Anderson DJ. *P1 interneurons promote a persistent internal state that enhances inter-male aggression in Drosophila.* eLife 4:e11346 (2015). PMID 26714106, PMC4749567.**

Verbatim: *"P1 activation in the absence of wing extension triggered persistent aggression via an internal state that could endure for minutes."* Extracted (**confidence medium-high**): persistent state lasts **>=10 min** after a **1-minute** photostimulation; wing-extension decay t1/2 = **9 +/- 3 s** (paired males) vs **56 +/- 7 s** (solitary); stimulation 10-50 Hz, with **<=20 Hz promoting aggression and >=30 Hz promoting wing extension**; *"Activation of P1 neurons themselves did not appear to trigger long-lasting persistent activity within this population"* — i.e. **P1 itself is not the integrator.**

**Jung Y, Kennedy A, Chiu H, Mohammad F, Claridge-Chang A, Anderson DJ. *Neurons that Function within an Integrator to Promote a Persistent Behavioral State in Drosophila.* Neuron 105(2):322-333.e5 (2020). PMID 31810837, PMC6981076.** This supplies **the numbers the gap question asks for** (**confidence medium-high**):

| Quantity | Value |
|---|---|
| **pCd** persistent response to P1 activation | **tau ~ 83 s** (median) |
| **P1** own response | **tau ~ 15 s** |
| pCd direct stimulation (no P1) | tau ~ 13.4 s — pCd needs upstream network support |
| Model | leaky integrator **dr/dt = -r/tau + I**, solution r(t) = (r0 - tau*I)e^(-t/tau) + tau*I |
| Effect of silencing pCd | *"inhibition of pCd neurons increases the leak rate constant"* |
| Role | pCd is **necessary but not sufficient**; P1 transient, pCd sustains |

**Hindmarsh Sten T, Li R, Otopalik A, Ruta V. *Sexual arousal gates visual processing during Drosophila courtship.* Nature 595:549-553 (2021). PMID 34234348, PMC8973426.** This supplies **the form of the gate** (**confidence high** — the model description is explicit):

- *"The gain of LC10a visual projection neurons is selectively increased during courtship, enhancing their sensitivity to moving targets."*
- The model is **multiplicative**: *"simply scaling the net input current of LC10a neurons by the experimentally measured activity of P1 neurons"*, i.e. `I_LC10a <- I_LC10a * f(P1)`.
- *"the magnitude of P1 activity was near linearly (**m = 0.86**) predictive of the amplitude of LC10a responses evoked by the visual target (**r = 0.68**, p < 0.00001)"*; correlation with Tracking Index **r = 0.69 +/- 0.075**.
- **No absolute fold-change and no P1 decay tau are reported here** — which is why Jung 2020's tau ~ 83 s is the number to use.

**Also confirmed to exist:** Li X, Thieringer K, Gao Y, Murthy M, *Sequencing of distinct wing behaviors during Drosophila courtship*, Curr Biol 36:1291-1299.e4 (2026), PMC12952713 — *"Optogenetic activation of specific P1/pC1 neuron subsets in solitary males, without any female cues, is sufficient to recapitulate the entire stillness-to-waggling-to-singing progression."* Directly supports driving the whole courtship demo from a pC1 bias. Zhang SX, Rogulja D, Crickmore MA, Curr Biol 29:3216-3228 (2019), PMC6783369 — recurrent loop + CREB2 satiety, but on an **hours-to-days** timescale, so *not* the right tau for a demo.

### 3.2 male-cns handles

**pC1 (= P1 in males): 156 neurons**, all `acetylcholine`, `dimorphism = male-specific`, across ~50 subtypes (pC1_1a ... pC1_19, pC1x_b/c/d). Synonyms confirm identity: `Lee 2002, Rideout 2010, Nojima 2021: pC1`; many also `Cachero 2010: pMP-e; Yu 2010: pMP4`.

**pCd IS present** — it is not typed "pCd", which is why it looks absent. Found via synonym search (**confidence high**; useful discovery for the implementer):

| male-cns type | count | NT | synonym |
|---|---|---|---|
| SCL002m | 10 | ACh | `Zhou 2014: pCd; Nojima 2021: pCd-1` |
| SMP726m | 8 | ACh | pCd-1 |
| SMP721m | 8 | ACh | pCd-1 |
| CB0975 | 8 | ACh | pCd-1 |
| SMP710m | 7 | ACh | pCd-1 |
| CB1379 | 5 | ACh | pCd-1 |
| SMP700m | 4 | ACh | pCd-1 |
| SMP727m | 2 | ACh | pCd-1 |
| DNpe034 | 2 | ACh | pCd-1 (sexually dimorphic) |
| **SMP285, SMP286, SMP720m, CB0405** | 2 each | **GABA** | `Nojima 2021: pCd-2` |

**The P1<->pCd recurrent loop exists in the connectome and is strong** — this is what should generate tau ~ 83 s natively:

| pC1 -> pCd-1 | Sum | | pCd-1 -> pC1 | Sum |
|---|---|---|---|---|
| SMP710m | **1104** | | SMP726m | **723** |
| DNpe034 | 850 | | SMP700m | 578 |
| SMP726m | 381 | | SMP710m | 566 |
| SMP721m | 270 | | SMP721m | 470 |
| SCL002m | 168 | | SCL002m | 296 |

pC1 also has **massive recurrence onto itself and onto aIPg**: pC1->pC1_18b 4516, pC1->aIPg5 2909, pC1->pC1x_d 2215, pC1->pC1_4a 2128, pC1->aIPg_m1 1913; and back pC1_18b->pC1 1919, pC1_1a->pC1 1536, pC1_4a->pC1 1433.

**Courtship output pathway (all verified):**
- **pC1 -> pIP10 = 1941 synapses**; pC1 -> aIPg7 = 1058; pC1 -> DNp09 = 104; pC1 -> DNa02 = 64.
- `pIP10` bodyIds **523998 (L), 11116 (R)**, ACh, male-specific (`Kimura 2008, Kohatsu 2010: P2b; Cachero 2010: pIP-a; Yu 2010: pIP1`).
- Top pIP10 inputs: aIPg7 **1280**, ICL008m (GABA) 916, AVLP710m (GABA) 754, AVLP717m 754, AVLP718m 723, VES024_a (GABA) 578, AVLP256 (GABA) 567, **pC1_14a 565**.
- `vPR6` (song, VNC): 8 cells — 805945, 803749, 907401, 805750 (L); 800534, 806235, 806246, 807650 (R).

**LC10a: 275 neurons, `fruDsx = fru_high`, cholinergic.** (Siblings: LC10b 95, LC10c-1 130, LC10c-2 125, LC10d 214, LC10e 110.) Output is overwhelmingly AOTU/TuTu:
TuTuA_2 **22080**, AOTU019 (GABA) 13904, AOTU041 (GABA) 12541, AOTU014 8009, AOTU008 7091, AOTU025 6620, AOTU016_b 6427, AOTU023 6211.

**How does pC1 reach LC10a?** Checked: **there is no direct pC1->LC10a connection.** pC1 reaches the AOTU region instead (AOTU103m 1528, AOTU062 1232, AOTU059 1056, AOTU101m 843). The aminergic inputs to LC10a are **LoVC22 (dopamine) 305, OA-ASM1 (octopamine) 202, LoVC18 (dopamine) 192, LoVCLo3 (OA) 96, 5-HTPMPV03 (5-HT) 82.** This matches Hindmarsh Sten's framing of arousal as a *neuromodulatory gain* rather than a synaptic drive — so **implement it as a gain scalar, exactly as they modelled it.** (**Confidence high** on the connectivity; the identity of the physiological modulator is *unverified*.)

### 3.3 Concrete LIF implementation — COURTSHIP

Two options; prefer (A), fall back to (B).

**(A) Let the connectome do it, and only seed the state.** The pC1<->pCd loop is present and strong:

```java
// Trigger: female/target detected (or player holding the courtship item)
if (courtshipTrigger) bias[pC1_*] += 4.0f;   // mV, 1-second pulse
                                             // Hoopfer 2015: 1 min stim @ >=30 Hz -> wing extension
// then release. If the loop sustains, persistence is free.
```

**Risk:** an unclamped excitatory recurrent loop of 156 pC1 + ~54 pCd cholinergic neurons can latch permanently or blow up. Add per-neuron spike-frequency adaptation and verify decay. If it does not decay, use (B).

**(B) Explicit leaky integrator — the safe, literature-exact route.** Use Jung 2020's own model:

```java
// Arousal state, two cascaded leaks matching the measured taus.
float p1State;    // tau = 15 s  (Jung 2020: P1 transient)
float arousal;    // tau = 83 s  (Jung 2020: pCd integrator)  <- the courtship state

p1State += (courtshipDrive - p1State / 15.0f) * dt;
arousal += (p1State        - arousal  / 83.0f) * dt;   // dt in seconds
arousal  = clamp(arousal, 0f, 1f);
// tau 83 s => ~63% decay in 83 s, ~95% in 4 min; consistent with Hoopfer's ">=10 min" state

// GATE 3a. LC10a GAIN -- multiplicative, exactly as Hindmarsh Sten 2021 modelled it
gain(input -> LC10a) = 0.2f + 0.8f * arousal;    // 5x range; m=0.86 near-linear
// "scaling the net input current of LC10a neurons by the activity of P1 neurons"

// GATE 3b. Song pathway -- pIP10 needs the state to fire at all
bias[pIP10] = 3.0f * arousal;      // mV
bias[pC1_*] = 2.0f * arousal;      // self-sustaining nudge; feeds the 1941-syn pC1->pIP10 edge
```

The 0.2 floor is deliberate: Hindmarsh Sten report *"only weak responses"* in unaroused males, not zero — LC10a should still track targets faintly when the fly is not courting.

**Why this fixes the stated bug:** LC10a pursuit and pIP10 song never start because nothing supplies the arousal term the biology multiplies in. With `arousal`, seeing a target briefly raises `p1State`, which charges `arousal` over ~83 s, opening LC10a gain and biasing pIP10 — and the fly keeps pursuing for minutes after the target leaves, which is the observable that makes it read as behaviour rather than reflex.

---

## 4. FEEDING-LINKED WALK-OFF

### 4.1 Literature

**Sapkal N, Mancini N, Kumar DS, Spiller N, Murakami K, Vitelli G, Bargeron B, Maier K, Eichler K, Jefferis GSXE, Shiu PK, Sterne GR, Bidaye SS. *Neural circuit mechanisms underlying context-specific halting in Drosophila.* Nature (2024). DOI 10.1038/s41586-024-07854-7, PMID 39358520, PMC11446846.** (preprint bioRxiv 10.1101/2023.09.25.559438)

Abstract, verbatim: *"The first mechanism ('walk-OFF') relies on GABAergic neurons that inhibit specific descending walking commands in the brain, whereas the second mechanism ('brake') relies on excitatory cholinergic neurons in the nerve cord that lead to an active arrest of stepping movements... the walk-OFF mechanism was engaged for halting during feeding and the brake mechanism was engaged for halting and stability during grooming."*

Full text (**confidence medium** — single extraction pass; the paper does not print connectome IDs in the body):
- **FG (Foxglove) and BB (Bluebell) are both GABAergic**, described as *"SEZ neurons that stochastically descend until the anterior tip of the nerve cord"*.
- **FG** *"specifically inhibits a single DN named oDN1"* and partially suppresses **BDN2**; suppresses BPN-driven forward walking.
- **BB** inhibits *"most DNs downstream of P9"* (turning); *"The P9 pathway is more broadly and strongly inhibited by BB"*.
- **BRK** = *"six ascending neurons with somata in leg-segment neuromeres of the VNC, projecting to brain SEZ"*, excitatory/cholinergic; acts via **(1) brain SEZ outputs inhibiting walking DNs** and **(2) VNC outputs upregulating postural resistance reflexes**.
- **Hunger-dependence, directly relevant:** *"FG showed stronger responses in starved flies compared to fed flies... BB only responded (weakly) in starved flies."*
- *"FG and BB are downstream to the sugar sensory pathway, and directly connected to Fdg neurons previously shown to drive halting and proboscis extension."*
- oDN1 = forward velocity, not rotational; BDN2 *"strongly correlated to forward (but not angular) velocity"*; P9 drives forward turning; BPN drives straight forward walking.

### 4.2 male-cns handles — better than the brief suggests

The Sapkal cell types **are annotated in male-cns via `synonyms`** (verified):

| Sapkal name | male-cns type | count | bodyIds | NT |
|---|---|---|---|---|
| **BRK (Brake)** | **AN19A018** | **12** (6 tagged) | 14973, 17510, 523843 (L); 14113, 16542, 14863 (R) | **ACh** |
| **BB (Bluebell)** | **DNg60** | 2 | **11374 (L), 188947 (R)** | **GABA** |
| oDN1 | DNg97 | 2 | 13805 (L), 230783 (R) | ACh |
| cDN1 | DNg75 | 2 | 10192 (L), 520300 (R) | ACh |
| BDN1 | DNge053 | 2 | 10844, 11965 | ACh |
| BDN2 | DNg100 | 2 | 10045, 10056 | ACh |
| BDN3 | DNg55 | 1 | 15105 (**M**, midline) | GABA |
| BDN4 | DNge050 | 2 | 10301, 256420 | ACh |

DNg60 also carries `Sterne 2021: mesa; Sterne 2021: snail`.

**Foxglove is genuinely absent.** I searched `synonyms` for `foxglove`/`brake`/`sapkal`: FG is the one name returning nothing while BRK, BB, oDN1, cDN1 and BDN1-4 all return hits. **Confidence high that FG has no male-cns annotation.** I then searched for it structurally — a GABAergic neuron that is sugar-driven *and* inhibits both oDN1 and BDN2:

| GABAergic -> oDN1 & BDN2 | superclass | ->oDN1 | ->BDN2 | sugar-driven? |
|---|---|---|---|---|
| VES104 | cb_intrinsic | 2016 | 346 | no |
| GNG127 | cb_intrinsic | 974 | 2190 | no |
| AN00A006 | ascending | 912 | 1832 | no |
| **DNge054** | DN | 690 | 1620 | **no** (mechanosensory BM 721, AL-AST1 576, LPLC4 264) |
| **DNge065** | DN | 244 | 885 | only 5 syn from LB3 |
| DNge046 | DN | 812 | 36 | no |

The only GABAergic neuron two hops from sugar GRNs that inhibits oDN1/BDN2 is **DNge065** (bodyIds 11482 R, 11889 L), and its sugar linkage is a negligible 5 synapses. **No convincing FG homologue exists. Do not fabricate one.**

**But the walk-OFF still works, because Bluebell IS fully wired to the sugar pathway in male-cns.** This is the key constructive finding of this section (**confidence high**, direct query):

```
LB3a-d (sugar GRN) --> FMIn  (GNG197)     579 syn --> DNg60  1654 syn
LB3a-d (sugar GRN) --> Fuchs (GNG230)     226 syn --> DNg60  1412 syn
LB3a-d (sugar GRN) --> ANXXX462b          534 syn --> DNg60   721 syn
                       GNG128              57 syn --> DNg60   656 syn
```

And Bluebell inhibits a broad set of walking DNs:

| DNg60 -> | Sum | note |
|---|---|---|
| DNg75 (**cDN1**) | 350 | walking-promotion |
| DNg88 | 281 | |
| DNge050 (**BDN4**) | 280 | |
| DNge073 | 279 | |
| DNa11 | 244 | |
| DNa13 | 198 | |
| DNg97 (**oDN1**) | **162** | forward walking |
| DNg19 | 145 | |
| DNge053 (**BDN1**) | 70 | |
| DNa02 | 69 | turning |

**Brake's brain-side relay is DNge046** (GABA, 4 cells: 83661, 242405 R; 513057, 13445 L). `AN19A018 -> DNge046 = 2050 synapses`, and DNge046 receives 1939 from AN19A018 — its single largest input. DNge046 then inhibits walking DNs broadly: DNg16 **1331**, DNge050 **1011**, DNg96 826, DNge037 701, DNa01 **533**, DNg88 459, DNg97 406, DNg75 289. **This is Sapkal's "brain SEZ outputs inhibiting walking DNs" arm of the brake mechanism, realised in male-cns** — a concrete grooming-halt handle the brief did not have. **Confidence medium-high** (structural inference: BRK's strongest target is a GABAergic DN that broadly inhibits walking DNs; the paper does not print the ID).

### 4.3 Concrete LIF implementation — WALK-OFF

**No new gate is needed for the feeding halt — it is already in the connectome.** Route sugar contact into LB3 and let it run. The only additions are the hunger scaling from section 2 and the missing-FG patch:

```java
// 4a. Feeding halt -- EMERGENT, no hand gate required.
// sugar contact -> LB3a..d -> FMIn/Fuchs -> DNg60 (GABA) -> oDN1/cDN1/BDN4/DNa02
// Just wire DNg60's output with the GABA/glutamate inhibitory sign rule.

// 4b. FG replacement -- least-invasive hand-built gate.
// FG is absent; its documented role is a hunger-scaled extra inhibition of oDN1 + BDN2.
// Add it as a bias on the two target DNs, NOT as a fabricated neuron:
float sugarContact = ...;   // 1 while tasting food
bias[DNg97  /*oDN1*/] -= 2.0f * sugarContact * (0.4f + 0.6f * hunger);
bias[DNg100 /*BDN2*/] -= 1.2f * sugarContact * (0.4f + 0.6f * hunger);
// hunger scaling is GROUNDED: "FG showed stronger responses in starved flies" (Sapkal 2024)
// the 2.0/1.2 mV magnitudes are TUNED; the 0.4 floor keeps a fed fly still halting a little

// 4c. Grooming halt (brake) -- real pathway, use it
// dust/damage event -> AN19A018 (12 cells) -> DNge046 (GABA) -> walking DNs
// plus, per Sapkal, raise leg-joint stiffness in the decoder while AN19A018 fires:
if (rate[AN19A018] > threshold) legStiffness = 1.0f;   // "active arrest", not merely no-drive
```

Note 4c captures something the movement decoder would otherwise miss: the brake is *not* "stop commanding walking", it is **actively resisting** — the fly plants its legs. Rendering that as a stiffness flag rather than a velocity-zero is what makes grooming look right.

---

## 5. Master table — every gate in one place

| State | Variable | Named male-cns handle | Rule | Magnitude | Grounding |
|---|---|---|---|---|---|
| Flight | `flight` | **DNp10** (10425/10433) | tonic bias | **+3.33 mV** | **measured** (Ache 2019) |
| Flight | `flight` | **LPLC4->DNp07** (2211 syn) | multiplicative gain | **x0.293 grounded -> x1.0 flying (3.41x)** | **measured** (92->27 Hz) |
| Flight | `flight` | MDN (11288/12348/11332/10763) | tonic bias | -3.0 mV | direction measured (Liessem 2026), magnitude **tuned** |
| Flight | `flight` | DNa02, DNg97 | tonic bias | -2.0 mV | direction measured, magnitude **tuned** |
| Hunger | `hunger` | **LB3a-d** (sugar GRNs) | sensory Poisson rate | **40->200 Hz** | locus **measured** (Shiu 2022), range tuned within 10-200 Hz |
| Hunger | `hunger` | **G2N-1 (GNG232)**, **Clavicle (ANXXX462a)** | tonic bias | +1.5 mV | locus **measured**, magnitude tuned |
| Hunger | — | **MN9 (10331)** | **NO GATE** | — | **measured negative** (Shiu 2022) |
| Satiety | `satiety` | IPC (16 cells) — software, not LIF | integrator | +0.02/nutrition, -1 per 20 min | semantics from Held/Ache 2025 |
| Courtship | `arousal` | pC1 (156) -> pCd (SMP726m etc., ~54) | leaky integrator | **tau_P1 = 15 s, tau_pCd = 83 s** | **measured** (Jung 2020) |
| Courtship | `arousal` | **input->LC10a** (275 cells) | **multiplicative gain** | x0.2 -> x1.0 | **form measured** (Hindmarsh Sten 2021), range tuned |
| Courtship | `arousal` | **pIP10** (523998/11116) | tonic bias | +3.0 mV | tuned; pathway measured (pC1->pIP10 1941 syn) |
| Walk-OFF | — | LB3 -> GNG197/GNG230 -> **DNg60** | **emergent, no gate** | — | **fully present in connectome** |
| Walk-OFF | `hunger` | DNg97, DNg100 (FG proxy) | tonic bias | -2.0 / -1.2 mV | FG **absent**; hunger-scaling measured |
| Grooming | — | AN19A018 (12) -> **DNge046** (GABA) | emergent + stiffness flag | — | pathway measured; ID inferred |

---

## 6. What I could NOT verify — stated explicitly

1. **sNPF / NPF / AstA / dopaminergic magnitudes on sugar GRNs.** Inagaki 2012 Cell, Inagaki 2014 Nature and Marella 2012 Neuron were **not retrievable** through Europe PMC in this session (author queries returned modern homonyms — an "Inagaki HK" publishing on cortico-basal ganglia, a "Marella S" publishing on dermatology). **No numbers for these should be cited.** The Shiu 2022 locus result makes them unnecessary for the mod.
2. **Tastekin 2026 Cell, DOI 10.1016/j.cell.2026.08.016** — not in Europe PMC. The corresponding bioRxiv preprints (10.1101/2025.08.25.671814 and ...815) **do** exist. Treat the Cell citation as unconfirmed.
3. **Foxglove has no male-cns type or synonym**, and no structural candidate survives the "GABAergic + sugar-driven + inhibits oDN1 and BDN2" test. Confirmed absent, not merely unfound.
4. **MDN/DopaMeander flight-gate magnitude and time constant** — the 2026 Curr Biol abstract states the gating but gives no numbers, and the full text is not in PMC. My -3.0 mV is invented.
5. **DNp10's flight-depolarisation decay tau** — Ache reports it "repolarises upon flight cessation" without a constant. My 500 ms fall time is invented.
6. **Hindmarsh Sten gives no absolute LC10a fold-change** — only that the modulation is multiplicative and near-linear (m = 0.86). My x0.2->x1.0 range is chosen to leave a non-zero floor, consistent with their "weak responses" wording.
7. **DNge046 = Brake's brain relay** is my structural inference (BRK's largest target, GABAergic, broadly inhibits walking DNs), not a published identification.
8. **Sapkal FG/BB target split** (FG->oDN1, BB->P9-downstream) came from one full-text extraction pass; the male-cns data shows DNg60/BB inhibiting oDN1 at 162 synapses, consistent but not independent confirmation.
9. **Ache 2019 secondary numbers** (OA 1.6 mM, latencies 26/21 ms, the "more than doubled" figure) came from one extraction pass and are medium confidence; the two headline numbers (92->27 Hz, 3.33 mV) are high confidence.

---

## 7. Data caveats for the implementer

- **MN9 right-side is under-reconstructed:** bodyId 10331 (L) has **6358** postsynapses; 16949 (R) has **633**. Use the left cell as the PER readout.
- **All `cb_endocrine` neurons (IPC, DH44, Hugin-RG, CAPA, ITP, DMS) have `consensusNt = "unclear"`** — no sign under the LIF rule. Model satiety in software.
- **Glutamate is inhibitory** in the Shiu convention. Scapula (GNG087) is glutamatergic, so the bitter pathway inhibits by design; likewise PLP214->DNp07 and CL184/185/186->DNp10.
- **Sugar/bitter GRN identity is not a dataset field.** The LB3=sugar / LB1=bitter assignment above is derived from second-order targets; label it as inferred in code comments.
- **DNg55 (BDN3) is a single midline cell** (`somaSide = M`), not a bilateral pair.
- **AN19A018 has 12 cells but only 6 carry the `Sapkal 2024: BRK` synonym** — the other 6 are the same type, untagged. Use all 12 or filter on the synonym, but be consistent.

---

## 8. Reproducing every connectome number in this report

```python
# scratchpad/research/np.py
import requests, sys
def q(cypher):
    r = requests.post("https://neuprint.janelia.org/api/custom/custom",
        json={"cypher": cypher, "dataset": "male-cns:v1.0"}, timeout=120)
    r.raise_for_status(); d = r.json(); return d["columns"], d["data"]
```

No token required. Example — the sugar -> Bluebell walk-OFF chain:

```cypher
MATCH (g:Neuron)-[w1:ConnectsTo]->(x:Neuron)-[w2:ConnectsTo]->(o:Neuron)
WHERE g.type IN ['LB3a','LB3b','LB3c','LB3d'] AND o.type='DNg60'
  AND w1.weight>=5 AND w2.weight>=20
RETURN x.type, head(collect(x.consensusNt)),
       sum(DISTINCT w1.weight) AS fromSugar, sum(w2.weight) AS toBB
ORDER BY toBB DESC
```
