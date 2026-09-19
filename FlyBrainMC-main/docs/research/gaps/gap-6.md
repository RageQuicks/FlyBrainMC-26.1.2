# Gap 6 â€” pC1/P1 subtypes: splitting `courtship_gain` into song / courtship / aggression channels

**Date:** 2026-09-03 Â· **Dataset:** `male-cns:v1.0` (neuPrint, anonymous Cypher POST to `https://neuprint.janelia.org/api/custom/custom`)
**Scope:** per-type roles for pC1_1aâ€¦pC1_19, pC1x_aâ€“d, aIPg*, aSP10*, pIP1; sensory inputs; DN output weights; recommended decoder change; P1 stimulation protocol.

Every connectome number below was produced by a live Cypher query against `male-cns:v1.0` today and is reproducible with the helper at
`C:\Users\drini\AppData\Local\Temp\claude\C--Users-drini-OneDrive-Documents-fal-dev-fruit-fly-minecraft\793798d7-139a-4671-8d90-a5256c761f25\scratchpad\research\np.py`.
Confidence tags: **[C-high]** = my own query output or verbatim primary text; **[C-med]** = extracted from the preprint full text by the WebFetch summarizer (I did not read the PDF byte-for-byte, and three independent prompts against the same URL agreed); **[C-low]** = inference.

---

## 0. Bottom line

1. **The single most important fix is not re-partitioning pC1 â€” it is to stop averaging pC1 at all and read out `pIP10` directly.** pIP10 (2 cells, bodyIds 523998 L, 11116 R) already integrates all 49 pC1 types, 13 aIPg types and 5 aSP10 types *with the correct synaptic weights*, plus 7,047 units of GABAergic veto. Averaging 156 pC1 cells throws that away. **[C-high]**
2. If a population scalar is still wanted, the current unweighted mean is quantitatively wrong by roughly **2Ã—**: the 9 song-promoting types are 37/156 = 23.7% of pC1 cells but supply **45.6%** of all pC1â†’pIP10 synaptic weight (885/1941). The 9 "chase/wing-extension" types are 31/156 = 19.9% of cells but only **6.9%** (133/1941) of the drive. The 3 aggression types (pC1x_a/b/d) are 6 cells and only **1.4%** (28/1941). **[C-high]**
3. **Aggression and courtship really are separable in the wiring, but not along a pC1_/pC1x_ line.** pC1x_a/b/d route to DNpe034 / DNpe053 / DNp68, not to pIP10; pC1_16a/16b route to aSP22 (DNa12) and DNp36; the song types route to pIP10 + pMP2. Three output channels, not two. **[C-high]**
4. **`PPN1` does not exist under that name in male-cns:v1.0.** The male foreleg contact-pheromone ascending pathway is `AN09B017aâ€“g` (synonym **`Yu 2010: vAB3`**, male-specific, fru_high, **glutamate**) plus `AN03A008` (synonym **`Cachero 2010: vPr-j`**, male-specific, fru_high, acetylcholine). Use those type names. **[C-high]**
5. `LgLG5-8 â†’ PPN1 â†’ pIP10` as literally written **does not exist**: AN09B017 makes essentially zero direct contact with pIP10 (max 1 synapse). The real path is 3 hops and lands on the *wrong* P1 types for song. `LC10a â†’ P1 â†’ pIP10` also does not exist as one hop into a song type â€” LC10a hits pC1_1b/1a/9a, which are the chase types, not the song types. Details in Â§4. **[C-high]**

---

## 1. Papers â€” what is actually verifiable today

| Source | Status | What it gave me |
|---|---|---|
| Rubin GM, Managan C, Dreher M, Kim E, Miller S, Boone K, Robie AA, Taylor AL, Branson K, Schretter CE, Otopalik AG. *"Networks of sexually dimorphic neurons that regulate social behaviors in Drosophila."* bioRxiv 2025.10.21.683766, posted 2025-10-22. Europe PMC `PPR1106358`. | **Full text retrieved** at `https://www.biorxiv.org/content/10.1101/2025.10.21.683766v1.full-text` (the `.full` variant 429s; `.full-text` works). | All per-type behavioural assignments in Â§2. **[C-med]** |
| Same work, VOR: Current Biology, doi `10.1016/j.cub.2026.08.013` | DOI resolves (302 â†’ `https://linkinghub.elsevier.com/retrieve/pii/S0960982226010237`); **cell.com fulltext returns HTTP 403, linkinghub returns a redirect stub.** Not readable without a subscription. Not in Europe PMC. | Nothing beyond the preprint. **[C-high]** that it is unreadable from here. |
| "Tastekin et al. 2026 (Cell `10.1016/j.cell.2026.08.016`)" | DOI resolves (302 â†’ PII `S0092867426009438`) but content is **not retrievable** and **not indexed in Europe PMC**. | **Correction for the plan:** the matching Tastekin preprint is *"From Sensory Detection to Motor Action: The Comprehensive Drosophila Taste-Feeding Connectome"*, bioRxiv `10.1101/2025.08.25.671814` (Tastekin I, de Haan Vicente I, Beresford RJ, â€¦ Jefferis GS, Ribeiro C). **That paper is about the gustatory/feeding connectome â€” it contains nothing about pC1/P1.** It is the right source for LgLG/WG/PhG GRN naming (gap 3-ish), not for gap 6. **[C-high]** |
| Berg S, Beckett IR, Costa M, Schlegel P, Januszewski M, Marin EC, Nern A, Preibisch S, Qiu W, Takemura S, *et al.* (86 authors). *"Sexual dimorphism in the complete connectome of the Drosophila male central nervous system."* bioRxiv `10.1101/2025.10.09.680999`, 2025. | Abstract retrieved via Europe PMC REST. States **166,691 neurons**, "fully proofread and annotated", "first comprehensive comparison between male and female brain connectomes to synaptic resolution". | This is the male-CNS resource paper. Note its 166,691 differs from the 176,422 `Neuron` nodes the orchestrator counted â€” the node count includes segments the paper may not class as neurons. Worth a footnote in the mod's provenance text. **[C-high]** |

### Rubin 2026 abstract (verbatim, from the preprint full text)

> "Neural mechanisms underlying sexually dimorphic social behaviors remain enigmatic in most species. In Drosophila, sexually dimorphic P1/pC1x neurons have been described as a site of sensory integration that regulates mating and aggressive behaviors. We show that the male P1/pC1x population forms a highly intertwined network with male-specific mAL and aSP-a neurons that is poised to regulate male behavior." **[C-med]**

Key structural facts from the preprint: males have **44 P1 + 4 pC1x = 48 cell types**; the authors made **32 split-GAL4 driver lines covering ~34 of the 48 types**. **[C-med]**
neuPrint disagrees slightly: `male-cns:v1.0` has **49 distinct `pC1*` type strings over 156 cells** â€” 45 `pC1_*` strings / 148 cells and 4 `pC1x_*` / 8 cells. The extra string is the single-cell hybrid annotation **`pC1_2a/2b` (bodyId 17387, right side)**, which is one neuron the annotators could not split between 2a and 2b. So 48 real types + 1 ambiguous cell. **[C-high]** â€” the decoder must not treat `pC1_2a/2b` as a 49th population.

---

## 2. Per-type table

Weights are `sum(ConnectsTo.weight)` from **all cells of the source type** to **all cells of the target type** (i.e. summed over both L/R target cells). `out syn` / `in syn` are the type's total pre-/post-synaptic weight across the whole CNS, for normalisation. `%out->4DN` = (pIP10+pMP2+aSP22+pIP1)/out. **[C-high]** for all numbers; **[C-med]** for the role column.

| type | cells | out syn | in syn | ->pIP10 | ->pMP2 | ->aSP22 | ->pIP1 | %out->pIP10 | %out->4DN | Rubin 2026 role | evidence |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|
| pC1_10a | 2 | 10902 | 4374 | 0 | 0 | 0 | 0 | 0.00 | 0.00 | unknown / not tested | connectomic only |
| pC1_10b | 4 | 12915 | 5024 | 26 | 2 | 26 | 0 | 0.20 | 0.42 | courtship interaction (chase/wing ext) | optogenetic (Rubin 2026) |
| pC1_10c | 4 | 20926 | 8728 | 5 | 0 | 0 | 0 | 0.02 | 0.02 | courtship interaction (chase/wing ext) | optogenetic (Rubin 2026) |
| pC1_10d | 3 | 11496 | 4013 | 1 | 1 | 1 | 0 | 0.01 | 0.03 | courtship interaction (chase/wing ext) | optogenetic (Rubin 2026) |
| pC1_11a | 2 | 15682 | 6223 | 1 | 3 | 33 | 8 | 0.01 | 0.29 | unknown / not tested | connectomic only |
| pC1_11b | 2 | 17103 | 8040 | 71 | 38 | 23 | 0 | 0.42 | 0.77 | unknown / not tested | connectomic only |
| pC1_12a | 2 | 11647 | 3226 | 0 | 7 | 0 | 0 | 0.00 | 0.06 | unknown / not tested | connectomic only |
| pC1_12b | 4 | 29300 | 8343 | 4 | 13 | 3 | 2 | 0.01 | 0.08 | SONG + late aggression (mixed) | optogenetic (Rubin 2026) |
| pC1_13a | 2 | 9413 | 2426 | 45 | 68 | 6 | 3 | 0.48 | 1.30 | SONG-promoting | optogenetic (Rubin 2026) |
| pC1_13b | 4 | 16917 | 4451 | 22 | 14 | 27 | 4 | 0.13 | 0.40 | unknown / not tested | connectomic only |
| pC1_13c | 2 | 8716 | 2133 | 0 | 0 | 48 | 1 | 0.00 | 0.56 | unknown / not tested | connectomic only |
| pC1_14a | 6 | 20131 | 6395 | 565 | 962 | 79 | 0 | 2.81 | 7.98 | SONG-promoting | optogenetic (Rubin 2026) |
| pC1_14b | 2 | 8245 | 2409 | 66 | 225 | 6 | 0 | 0.80 | 3.60 | SONG-promoting | optogenetic (Rubin 2026) |
| pC1_15a | 2 | 8487 | 2566 | 28 | 0 | 61 | 0 | 0.33 | 1.05 | courtship interaction (chase/wing ext) | optogenetic (Rubin 2026) |
| pC1_15b | 2 | 8029 | 3618 | 21 | 0 | 57 | 0 | 0.26 | 0.97 | courtship interaction (chase/wing ext) | optogenetic (Rubin 2026) |
| pC1_15c | 3 | 11815 | 5470 | 31 | 1 | 26 | 0 | 0.26 | 0.49 | courtship interaction (chase/wing ext) | optogenetic (Rubin 2026) |
| pC1_16a | 5 | 20578 | 7445 | 57 | 14 | 341 | 1 | 0.28 | 2.01 | unknown / not tested | connectomic only |
| pC1_16b | 8 | 28411 | 9477 | 7 | 13 | 278 | 0 | 0.02 | 1.05 | unknown / not tested | connectomic only |
| pC1_17a | 3 | 13729 | 3861 | 31 | 1 | 58 | 1 | 0.23 | 0.66 | SONG-promoting | optogenetic (Rubin 2026) |
| pC1_17b | 5 | 12444 | 5465 | 5 | 0 | 5 | 0 | 0.04 | 0.08 | SONG-promoting | optogenetic (Rubin 2026) |
| pC1_18a | 2 | 15386 | 3248 | 0 | 0 | 0 | 0 | 0.00 | 0.00 | unknown / not tested | connectomic only |
| pC1_18b | 4 | 34322 | 14721 | 9 | 2 | 0 | 0 | 0.03 | 0.03 | unknown / not tested | connectomic only |
| pC1_19 | 7 | 10235 | 2710 | 165 | 2 | 5 | 0 | 1.61 | 1.68 | SONG-promoting | optogenetic (Rubin 2026) |
| pC1_1a | 7 | 27428 | 8663 | 2 | 5 | 0 | 0 | 0.01 | 0.03 | courtship interaction (chase/wing ext) | optogenetic (Rubin 2026) |
| pC1_1b | 2 | 8728 | 3897 | 1 | 2 | 1 | 0 | 0.01 | 0.05 | unknown / not tested | connectomic only |
| pC1_2a | 4 | 16978 | 6901 | 14 | 3 | 0 | 4 | 0.08 | 0.12 | courtship interaction (chase/wing ext) | optogenetic (Rubin 2026) |
| pC1_2a/2b | 1 | 4094 | 1416 | 3 | 1 | 0 | 0 | 0.07 | 0.10 | unknown / not tested | connectomic only |
| pC1_2b | 2 | 9789 | 2844 | 5 | 33 | 0 | 2 | 0.05 | 0.41 | courtship interaction (chase/wing ext) | optogenetic (Rubin 2026) |
| pC1_2c | 2 | 11703 | 3460 | 2 | 23 | 0 | 1 | 0.02 | 0.22 | unknown / not tested | connectomic only |
| pC1_3a | 2 | 15163 | 5491 | 4 | 0 | 39 | 0 | 0.03 | 0.28 | unknown / not tested | connectomic only |
| pC1_3b | 2 | 15955 | 5077 | 4 | 0 | 33 | 0 | 0.03 | 0.23 | unknown / not tested | connectomic only |
| pC1_3c | 4 | 23178 | 7453 | 2 | 1 | 4 | 0 | 0.01 | 0.03 | unknown / not tested | connectomic only |
| pC1_4a | 6 | 33773 | 15479 | 3 | 8 | 5 | 0 | 0.01 | 0.05 | SONG + late aggression (mixed) | optogenetic (Rubin 2026) |
| pC1_4b | 2 | 15861 | 5177 | 1 | 1 | 3 | 1 | 0.01 | 0.04 | SONG + late aggression (mixed) | optogenetic (Rubin 2026) |
| pC1_5a | 2 | 5505 | 2240 | 59 | 0 | 3 | 0 | 1.07 | 1.13 | unknown / not tested | connectomic only |
| pC1_5b | 4 | 11488 | 3600 | 215 | 5 | 27 | 0 | 1.87 | 2.15 | unknown / not tested | connectomic only |
| pC1_6a | 6 | 15744 | 5386 | 17 | 6 | 0 | 0 | 0.11 | 0.15 | unknown / not tested | connectomic only |
| pC1_6b | 2 | 6627 | 2264 | 0 | 1 | 0 | 0 | 0.00 | 0.02 | unknown / not tested | connectomic only |
| pC1_7a | 4 | 22567 | 4560 | 163 | 1 | 11 | 0 | 0.72 | 0.78 | unknown / not tested | connectomic only |
| pC1_7b | 4 | 19225 | 5054 | 190 | 0 | 14 | 0 | 0.99 | 1.06 | unknown / not tested | connectomic only |
| pC1_8a | 2 | 7548 | 1892 | 2 | 0 | 0 | 0 | 0.03 | 0.03 | unknown / not tested | connectomic only |
| pC1_8b | 2 | 8771 | 2841 | 2 | 1 | 1 | 0 | 0.02 | 0.05 | unknown / not tested | connectomic only |
| pC1_8c | 2 | 8603 | 2200 | 0 | 0 | 0 | 0 | 0.00 | 0.00 | unknown / not tested | connectomic only |
| pC1_9a | 4 | 20054 | 6896 | 0 | 0 | 6 | 1 | 0.00 | 0.03 | unknown / not tested | connectomic only |
| pC1_9b | 2 | 8651 | 2528 | 0 | 0 | 21 | 3 | 0.00 | 0.28 | unknown / not tested | connectomic only |
| pC1x_a | 2 | 24466 | 6682 | 4 | 0 | 0 | 0 | 0.02 | 0.02 | AGGRESSION, no song | optogenetic (Rubin 2026) |
| pC1x_b | 2 | 26469 | 11340 | 2 | 0 | 0 | 0 | 0.01 | 0.01 | AGGRESSION, no song | optogenetic (Rubin 2026) |
| pC1x_c | 2 | 32906 | 8243 | 64 | 263 | 2 | 0 | 0.19 | 1.00 | unknown / not tested | connectomic only |
| pC1x_d | 2 | 34916 | 10933 | 22 | 22 | 1 | 0 | 0.06 | 0.13 | AGGRESSION, no song | optogenetic (Rubin 2026) |
| aIPg1 | 8 | 38532 | 12011 | 4 | 8 | 20 | 4 | 0.01 | 0.09 | aggression + proximity | optogenetic SS86900 (3 types pooled) |
| aIPg10 | 4 | 22005 | 10847 | 44 | 22 | 4 | 0 | 0.20 | 0.32 | unknown / not tested | connectomic only |
| aIPg2 | 6 | 26759 | 10734 | 6 | 7 | 38 | 7 | 0.02 | 0.22 | unknown / not tested | connectomic only |
| aIPg4 | 2 | 9395 | 2790 | 0 | 0 | 0 | 0 | 0.00 | 0.00 | unknown / not tested | connectomic only |
| aIPg5 | 6 | 32142 | 12090 | 94 | 49 | 0 | 0 | 0.29 | 0.44 | unknown / not tested | connectomic only |
| aIPg6 | 5 | 16408 | 11452 | 10 | 1 | 20 | 1 | 0.06 | 0.20 | unknown / not tested | connectomic only |
| aIPg7 | 7 | 24008 | 10145 | 1280 | 3 | 161 | 0 | 5.33 | 6.01 | unknown / not tested | connectomic only |
| aIPg8 | 3 | 9875 | 5606 | 0 | 0 | 2 | 0 | 0.00 | 0.02 | unknown / not tested | connectomic only |
| aIPg9 | 3 | 14849 | 4305 | 1 | 0 | 0 | 0 | 0.01 | 0.01 | unknown / not tested | connectomic only |
| aIPg_m1 | 4 | 33988 | 8371 | 19 | 59 | 0 | 0 | 0.06 | 0.23 | unknown / not tested | connectomic only |
| aIPg_m2 | 4 | 30168 | 7459 | 1 | 5 | 3 | 0 | 0.00 | 0.03 | aggression + proximity | optogenetic SS86900 (3 types pooled) |
| aIPg_m3 | 2 | 9505 | 3499 | 0 | 0 | 0 | 0 | 0.00 | 0.00 | unknown / not tested | connectomic only |
| aIPg_m4 | 2 | 23442 | 12957 | 0 | 0 | 2 | 1 | 0.00 | 0.01 | aggression + proximity | optogenetic SS86900 (3 types pooled) |
| aSP10A_a | 6 | 30666 | 10487 | 40 | 7 | 15 | 0 | 0.13 | 0.20 | unknown / not tested | connectomic only |
| aSP10A_b | 10 | 42709 | 10389 | 192 | 9 | 153 | 1 | 0.45 | 0.83 | unknown / not tested | connectomic only |
| aSP10B | 11 | 47153 | 21168 | 6 | 0 | 1 | 2 | 0.01 | 0.02 | unknown / not tested | connectomic only |
| aSP10C_a | 9 | 23537 | 5622 | 0 | 0 | 0 | 0 | 0.00 | 0.00 | unknown / not tested | connectomic only |
| aSP10C_b | 6 | 19846 | 4757 | 0 | 0 | 0 | 0 | 0.00 | 0.00 | unknown / not tested | connectomic only |

### bodyIds (all 156 pC1 + pIP1/pIP10/pMP2/aSP22/vPR6)

All are `consensusNt = acetylcholine`, i.e. **excitatory â€” every pC1/aIPg/aSP10 term enters the LIF with a `+` sign.** The courtship/aggression split is about *which gain variable they feed*, never about sign. **[C-high]**

```
pC1_1a  7 [20255,15208,29280,519914,19085,524296,526918]   pC1_1b  2 [16262,26993]
pC1_2a  4 [524747,17564,519297,15329]  pC1_2a/2b 1 [17387]  pC1_2b  2 [19705,18913]   pC1_2c 2 [20392,22093]
pC1_3a  2 [18974,19650]   pC1_3b 2 [13266,514925]   pC1_3c 4 [19948,18017,19894,19136]
pC1_4a  6 [23968,20803,519518,20117,522419,12442]          pC1_4b  2 [17867,16719]
pC1_5a  2 [102224,32950]  pC1_5b 4 [29143,557035,70011,29483]
pC1_6a  6 [20597,528986,17098,521453,26543,21507]          pC1_6b  2 [517735,24623]
pC1_7a  4 [53355,25542,29043,71385]                        pC1_7b  4 [513652,513651,43592,26746]
pC1_8a  2 [25521,24727]   pC1_8b 2 [30391,513129]   pC1_8c 2 [41518,47325]
pC1_9a  4 [267996,555751,555488,15876]                     pC1_9b  2 [16364,25345]
pC1_10a 2 [18276,517337]  pC1_10b 4 [30699,515977,26153,517519]
pC1_10c 4 [518589,55136,17434,17702]                       pC1_10d 3 [30546,20580,19968]
pC1_11a 2 [13495,18990]   pC1_11b 2 [13655,514077]
pC1_12a 2 [513650,512011] pC1_12b 4 [17851,13562,18554,17089]
pC1_13a 2 [25204,19672]   pC1_13b 4 [23924,220200,21538,555746]   pC1_13c 2 [23787,555747]
pC1_14a 6 [55986,44515,29214,516522,23695,33553]           pC1_14b 2 [46570,25740]
pC1_15a 2 [32437,108816]  pC1_15b 2 [25690,28972]   pC1_15c 3 [180724,29388,18934]
pC1_16a 5 [20751,23952,59527,23789,67624]
pC1_16b 8 [33041,25746,535835,32283,26833,513654,66610,58786]
pC1_17a 3 [22818,23651,70815]   pC1_17b 5 [41427,23661,31309,35202,36545]
pC1_18a 2 [12448,532011]  pC1_18b 4 [87497,32788,15035,518253]
pC1_19  7 [125721,170209,127881,68624,79642,98113,156865]
pC1x_a  2 [521150,12366]  pC1x_b 2 [10217,512580]   pC1x_c 2 [10666,12257]   pC1x_d 2 [12893,13449]
pIP1    2 [10038,10030]   pIP10  2 [523998,11116]   pMP2   2 [10930,10765]   aSP22  2 [10090,10104]
vPR6    8 [800534,805945,806235,806246,803749,907401,805750,807650]
aIPg7   7 [30029,41288,23504,27329,23193,28834,19415]      aIPg5 6 [29416,26722,22839,16933,19912,67816]
aIPg_m1 4 [17550,520889,22881,17192]  aIPg_m2 4 [15807,305105,76910,555385]
aIPg_m3 2 [20446,525615]  aIPg_m4 2 [10498,12079]   aIPg1 8 [15990,17805,16804,16577,74363,15464,16665,525455]
aSP10A_b 10 [28724,35669,559780,521708,14282,24595,519121,515056,524274,107194]
```

`dimorphism` / `fruDsx`: all `pC1_*` are `male-specific` or `potentially male-specific` (pC1_9a/9b/13a/13b/13c/14a/14b are `potentially male-specific`); all four `pC1x_*` are `sexually dimorphic` + `dsx_high`. aIPg1/2/5/6/7/8/10 are `sexually dimorphic` + `fru_high`; aIPg_m1â€“m4 are `male-specific` + `fru_high`; aIPg4/aIPg9 are `fru_low` with no dimorphism tag. aSP10A_a, aSP10C_a, aSP10C_b are `sexually dimorphic`, aSP10A_b `potentially sexually dimorphic`, aSP10B untagged; all aSP10 are `fru_high`. pIP1/pIP10/pMP2 are `male-specific` + `fru_high`; aSP22 is `sexually dimorphic` (no fruDsx value). **[C-high]**

---

## 3. Behavioural assignments from Rubin 2026 â€” what the optogenetics actually showed

**[C-med]** throughout this section (summarizer extraction from the preprint full text; three independent prompts against the same URL returned mutually consistent lists).

**Song-producing on activation (LED / CsChrimson):**

| Line contents (co-activated) | Phenotype (quoted from the preprint) |
|---|---|
| P1_4a, P1_4b, P1_12b | "variable patterns of sine and pulse song that persisted well beyond stimulus offset"; also "wing extensions during the stimulus period, followed by aggressive tussling at stimulus offset" |
| P1_13a, P1_14a, P1_14b | "complex combination of early pulse song and later sine song epochs during the stimulus period" |
| P1_17a, P1_17b | "reliable, yet transient, pulse song at LED onset" |
| P1_19 | "sine song with variable latency and duration during the LED stimulus period" |

**Social-interaction-promoting (chasing + wing extension, courtship-like, no strong song):** P1_1a; P1_2a + P1_2b (together); P1_15a/15b/15c; P1_10b/10c/10d. P1_1a and P1_2a/b showed "the most remarkable persistent phenotypes".

**Aggression-promoting:** pC1x_a + pC1x_b + pC1x_d co-activated â†’ "highly aggressive interactions during the stimulus period, including boxing, lunging, tussling, and wing flicks". **No pC1x type produced any song.** Note **pC1x_c is not in that triple** â€” it was not part of the aggressive line, and connectomically it is the odd one out (see Â§5).

**aIPg in males:** "optogenetic activation of SS86900" (expressing **aIPg_m2, aIPg_m4 and aIPg1**) "results in a decrease in inter-fly distance and an increase in aggressive behaviors." Three male-specific + four dimorphic aIPg types together supply ~25% of the input to **SMP054**, described as a key node for visual attention / aggression.

**Important negative result:** "none [of the P1 cell types activated] phenocopied pIP10 song production" â€” pIP10 was the positive control, and no P1 type reproduced its song, "despite several P1 types being directly upstream of pIP10". The authors frame this as **degeneracy**: different P1 types converge on similar outputs via monosynaptic *or* disynaptic routes to pIP10/pMP2.

**Types with no reported phenotype** (not covered by the 32 lines, or tested with no effect): P1_3a-c, P1_5a/5b, P1_6a/6b, P1_7a/7b, P1_8a-c, P1_9a/9b, P1_11a/11b, P1_16a/16b, P1_18a/18b, and pC1x_c. This is a real limitation â€” **several of the strongest connectomic pIP10 drivers (pC1_5b w=215, pC1_7b w=190, pC1_7a w=163) have no behavioural label at all.** **[C-high]** on the connectivity, **[C-med]** on the absence of a phenotype.

---

## 4. Sensory input pathways â€” verified with weights

### 4.1 Contact pheromone: the `PPN1` name is wrong for this dataset

`MATCH (n:Neuron) WHERE n.type CONTAINS 'PPN'` returns **zero rows** in `male-cns:v1.0`. **[C-high]**

The male foreleg contact-pheromone ascending neurons are, with their literature synonyms straight out of the `synonyms` property: **[C-high]**

| type | cells | consensusNt | fruDsx | dimorphism | synonyms |
|---|---|---|---|---|---|
| AN09B017aâ€“d | 2 each | **glutamate** | fru_high | male-specific | (none) |
| AN09B017e, f | 2 each | **glutamate** | fru_high | male-specific | `Yu 2010: vAB3` |
| AN09B017g | 2 | **glutamate** | fru_high | male-specific | `Yu 2010, von Phillipsborn 2011: vAB3` |
| AN03A008 | 2 | acetylcholine | fru_high | male-specific | `Cachero 2010: vPr-j` |
| AN05B035 | 2 | gaba | â€” | potentially male-specific | (none) |

âš ï¸ **These are glutamatergic, not cholinergic.** In the fly CNS glutamate is frequently *inhibitory* via GluClÎ±. If gap 1's `W_syn` assigns a negative sign to glutamate by default, the entire contact-pheromone â†’ P1 pathway will invert and taste-on-contact will *suppress* courtship. This needs an explicit decision. The Rubin preprint describes AN09B017 as "responsive to female pheromone 7,11-HD" providing "their strongest input to P1_3c neurons", which reads as a drive, not a veto. **Recommendation: hard-code AN09B017aâ€“g as excitatory (+) regardless of the global glutamate rule, and log the exception.** **[C-high]** on the NT label, **[C-med]** on the functional sign.

**GRN â†’ ascending, direct, `sum(weight)`:** LgLG6â†’AN05B035 1478, LgLG6â†’AN09B017b 971, LgLG8â†’AN09B017g 1433, LgLG5â†’AN05B035 934, LgLG5â†’AN09B017f 701, LgLG6â†’AN09B017c 631, LgLG6â†’AN09B017f 539, LgLG6â†’AN09B017d 533, LgLG7â†’AN09B017e 550, LgLG6â†’AN03A008 138, LgLG8â†’AN13B002 174. **[C-high]**

**Ascending â†’ P1, direct, `sum(weight)`:** **[C-high]**

| source | target | w |
|---|---|---:|
| AN03A008 (vPr-j) | **pIP1** | **1151** |
| AN09B017f | pC1_3c | 349 |
| AN09B017g | pC1_3c | 310 |
| AN03A008 | pC1_13b | 274 |
| AN09B017f | pC1_3b | 173 |
| AN09B017b | pC1_5b | 145 |
| AN09B017c | pC1_3a | 136 |
| AN09B017c | aIPg_m4 | 120 |
| AN03A008 | pC1_13c | 116 |
| AN09B017e | aSP10B | 102 |
| AN03A008 | pC1_9a | 93 |
| AN09B017c | aIPg2 | 92 |
| AN09B017b | pC1_11a | 74 |
| AN09B017c | aIPg1 | 60 |
| AN09B017e | pC1_2a | 40 |

**Verdict on `LgLG5-8 â†’ PPN1 â†’ pIP10`: FALSE as stated.** AN09B017* â†’ pIP10 is at most **1 synapse** (AN09B017e). AN09B017*â†’pMP2 max 14, â†’aSP22 0, â†’pIP1 max 4. **[C-high]**

The **shortest real route from foreleg contact pheromone to song** is 3 hops, and it is not the strongest branch of the pheromone pathway:

```
LgLG6 --971--> AN09B017b --145--> pC1_5b --215--> pIP10 --940--> TN1a_g --771--> hg3 MN
LgLG8 --1433-> AN09B017g --?--->  (mostly pC1_3c, a dead end for song)
```

The dominant branch, `LgLG5/6/8 â†’ AN09B017f/g â†’ pC1_3c` (349+310), is a **dead end for song**: pC1_3c's biggest outputs are mAL_m3b 725, DNp62 166, DNp13 166, and it sends only **2** synapses to pIP10. So contact pheromone in this connectome mostly drives a *non-song* P1 branch. **[C-high]** This is a genuine modelling problem for the courtship demo: taste-on-contact will not by itself light up pIP10.

Also note `AN03A008 â†’ pIP1 = 1151` is the single largest pheromoneâ†’male-DN connection in the whole set. pIP1 is a male-specific fru_high DN whose outputs are heavily leg-motor (`Sternal anterior rotator MN` 425, `Tr flexor MN` 278, plus DNg105 938, DNge031 697). **pIP1 is a much better contact-pheromone readout than pIP10** â€” plausibly the "mount / leg action on touching a female" channel. **[C-high]** on wiring, **[C-low]** on the behavioural label.

### 4.2 Vision: `LC10a â†’ P1` verified, but it lands on the chase types

**[C-high]**

| source | target | w |
|---|---|---:|
| **LC16** | **pC1_2a** | **1661** |
| LC16 | pIP1 | 1296 |
| **vpoEN** | **pC1_6a** | **687** |
| **LC10a** | **pC1_1b** | **496** |
| LC16 | pC1_2b | 219 |
| M_lvPNm45 | pC1x_c | 191 |
| vpoEN | pC1_10c | 186 |
| LC11 | aIPg2 | 181 |
| LC10a | pC1_1a | 110 |
| LC16 | pC1_2a/2b | 93 |
| LC10a | pC1_9a | 84 |
| M_lvPNm24 | pC1x_a | 83 |
| M_lvPNm45 | pC1x_a | 77 |
| M_lvPNm24 | pC1x_c | 61 |
| vpoEN | pC1_10d | 39 |
| LC10a | pIP1 | 22 |
| vpoEN | pC1_5a | 27 |
| LC10a | pC1_8a | 16 |
| LC10a | aIPg4 | 16 |
| LC10a | pC1_11a | 13 |

Preprint statements this confirms **[C-med]**: LC10a gives "strong input exclusively to P1_1b"; LC16 (looming) is "remarkably strong presynaptic input to P1_2a â€¦ constituting 25% of their total input" (1661 of pC1_2a's 6901 total input weight = **24.1%** â€” the preprint's figure checks out **[C-high]**); vpoEN (song-detecting) is "13% of input to P1_6a" (687/5386 = **12.8%** â€” also checks out **[C-high]**); cVA ORNs DA1/DL3 reach pC1x_a and pC1x_c indirectly via `M_lvPNm24` and `M_lvPNm45`.

**Verdict on `LC10a â†’ P1 â†’ pIP10`: the first hop exists and is strong; the second hop is essentially absent.** pC1_1bâ†’pIP10 = **1**, pC1_1aâ†’pIP10 = **2**, pC1_9aâ†’pIP10 = **0**. The visual (LC10a) P1 types are exactly the ones Rubin found drive *chasing and wing extension*, not song. Wing extension without song is behaviourally coherent â€” but if scene 4 expects "sees a female â†’ sings", **that path does not exist monosynaptically and must be routed through the P1 recurrent network** (pC1_1aâ†’pC1_4a = 371, pC1_4aâ†’pC1_18a/18b = 373/393) or accepted as a 2-stage behaviour (approach/chase first, song later). **[C-high]**

### 4.3 cVA / olfaction

`ORN_DA1 â†’ DA1_lPN = 30067`, `ORN_DA1 â†’ DA1_vPN = 1949`, `ORN_DA1 â†’ M_lvPNm45 = 1065`, `ORN_DA1 â†’ M_lvPNm24 = 316`; `ORN_DL3 â†’ DL3_lPN = 7331`. Then `M_lvPNm45 â†’ pC1x_c 191, â†’ pC1x_a 77`; `M_lvPNm24 â†’ pC1x_a 83, â†’ pC1x_c 61`. **[C-high]**

So **cVA (male pheromone) preferentially reaches the pC1x cluster â€” the aggression cluster.** That is a clean, defensible mechanism for the mod: *smelling another male* â†’ pC1x â†’ aggression, *touching a female* (LgLG) â†’ pC1_3/5/13 + pIP1, *seeing a moving object* (LC10a) â†’ pC1_1a/1b â†’ chase. **[C-high]** on wiring; **[C-med]** on the behavioural gloss.

---

## 5. Output channels â€” three, not one

`sum(weight)` totals into the four male DNs: **[C-high]**

| target DN | from all pC1 (49 types) | from aIPg (13) | from aSP10 (5) |
|---|---:|---:|---:|
| pIP10 | 1941 | 1459 | 238 |
| pMP2 | 1742 | 154 | 16 |
| aSP22 | 1254 | 250 | 169 |
| pIP1 | ~35 | ~13 | ~3 |

**Channel A â€” song (pIP10 + pMP2).** Dominated by **pC1_14a** (â†’pMP2 **962**, â†’pIP10 **565**; 6 cells; that is 4.8% + 2.8% of its entire output) and **aIPg7** (â†’pIP10 **1280** â€” the single largest input to pIP10 of any type, 10.7% of pIP10's whole cholinergic budget). Then pC1_5b 215, pC1_7b 190, pC1_19 165, pC1_7a 163, pC1_14b 66/225, pC1x_c 64/263, pC1_13a 45/68.

**pIP10 downstream confirms this is the song channel [C-high]:** AN08B061 2113, dPR1 1112, IN12A030 979, **TN1a_g 940, TN1a_i 479, TN1a_d 401, TN1a_h 350, TN1a_b 321, TN1a_a 314, TN1a_f 311, TN1a_c 209, TN1a_e 115**, vPR9_a 608, vPR9_c 607, **vPR6 65**. And the motor layer: `TN1a_g â†’ hg3 MN 771, DLMn c-f 720, hg4 MN 495, DVMn 2a,b 211, DLMn a,b 206`; `TN1a_d â†’ hg3 484, hg4 290`; `dPR1 â†’ hg1 MN 593, b3 MN 435`; `vPR6 â†’ hg1 MN 1123, ps2 MN 156, dMS5 1743`. So **the full P1 â†’ pIP10 â†’ {TN1a_*, dPR1, vPR6} â†’ {hg1, hg3, hg4, DLMn, DVMn, ps2, b3} chain exists end-to-end in male-cns and is the correct song decoder.** `pMP2 â†’ dPR1 1750` and `pMP2 â†’ pIP10 166` â€” pMP2 is a parallel/reinforcing song DN.

**Channel B â€” late courtship / copulation attempt (aSP22 = DNa12).** Dominated by **pC1_16a (341)** and **pC1_16b (278)**, then aIPg7 161, aSP10A_b 153, pC1_14a 79. Rubin says aSP22 receives P1_16 input and coordinates "late-stage courtship actions". aSP22 downstream: IN12A001 742, IN12B018 697, DNge026 516, **`Tergopleural/Pleural promotor MN` 290**, pMP2 180, TN1c_c 182. **[C-high]** â€” this is a leg/wing postural channel, i.e. mounting, not song. **pC1_16a/16b belong in a courtship channel, not an aggression channel**, despite pC1_16a also being the 3rd-largest DNp36 driver (571).

**Channel C â€” aggression (no pIP10).** pC1x_a/b/d go to **DNpe034** (pC1x_b 339, pC1x_a 149, pC1x_d 64), **DNpe053** (pC1x_b 92, pC1x_a 74, pC1x_d 71), **DNp68** (pC1x_d 157, pC1x_c 46), DNp62 (pC1x_b 61). aIPg_m1/m2/m4 and aIPg1/9/10 converge on the same DNp68 / DNpe053 / DNpe034 / DNp27 set (aIPg_m1â†’DNp68 176, â†’DNp27 152; aIPg_m4â†’DNpe053 143, â†’DNp68 112, â†’DNpe034 98; aIPg5â†’DNp27 354, â†’DNp68 254). **DNp68/DNpe053/DNpe034/DNp27 is the aggression DN bus, and it is completely disjoint from pIP10.** **[C-high]**

**SMP054 hub (Rubin's aggression node) â€” verified [C-high]:** aIPg2â†’SMP054 627, aIPg1 505, aIPg_m4 398, aIPg10 342, aIPg8 337, aIPg_m2 229, aIPg4 225, aIPg_m3 218, aIPg6 115 (total aIPgâ†’SMP054 â‰ˆ 2996). SMP054 feeds back to pC1_17b 143, pC1_17a 66, pC1_10b 57, pC1_15a 39, aIPg6 83.

**The odd one out: pC1x_c.** It was *not* in Rubin's aggressive line (a,b,d), and connectomically it behaves like a song type: â†’pMP2 **263**, â†’pIP10 **64**, â†’DNp68 only 46. It also receives the cVA-PN input (M_lvPNm45 191). **Treat pC1x_c as courtship/song, not aggression.** **[C-high]** on wiring, **[C-med]** on the exclusion from the aggressive line.

### Inhibitory brake â€” do not omit it

pIP10's input budget by transmitter: **ACh 11,957 (743 cells) / GABA 7,047 (310 cells) / glutamate 2,415 (201) / unclear 156 / DA 26 / OA 16 / HA 2.** GABA is **37%** of the total. The largest single inhibitors are ICL008m 916, AVLP710m 754, VES024_a 578, AVLP256 575, vPR9_c 519, AN00A006 460, CRE021 372, vPR9_a 334. Plus the male-specific **mAL_m\*** GABA cluster hammers pC1 itself (mAL_m1â†’pC1_3c 739, mAL_m8â†’pC1_4a 629, mAL_m2bâ†’pMP2 491, mAL_m1â†’pC1_1a 466, mAL_m1â†’pC1_18b 461) â€” matching the Rubin abstract's "intertwined network with male-specific mAL and aSP-a neurons". **If the LIF omits GABA, pIP10 will sing constantly.** **[C-high]**

---

## 6. Recommendation for the decoder

### 6.1 Replace the average with pIP10 itself

```
song_drive        = rate(pIP10)                    // 2 cells: 523998, 11116
song_reinforce    = rate(pMP2)                     // 2 cells: 10930, 10765
copulation_drive  = rate(aSP22)                    // 2 cells: 10090, 10104  (DNa12)
leg_pheromone_act = rate(pIP1)                     // 2 cells: 10038, 10030
aggression_drive  = mean(rate(DNp68), rate(DNpe053), rate(DNpe034), rate(DNp27))
```

Emit the song animation when `song_drive` crosses threshold; emit `hg1/hg3/DLMn` wing motion from `TN1a_g` + `dPR1` + `vPR6` if the mod simulates the VNC layer. This needs **no hand-tuned weights** â€” the connectome supplies them.

### 6.2 If a population scalar is still wanted, use these sets and weights

All signs **`+`** (every member is cholinergic).

```
courtship_song_gain   = Î£ w_iÂ·r_i / Î£ w_i   over  { pC1_14a:565, pC1_5b:215, pC1_7b:190, pC1_19:165,
                                                    pC1_7a:163, pC1_11b:71, pC1_14b:66, pC1x_c:64,
                                                    pC1_5a:59, pC1_13a:45, pC1_17a:31, pC1_13b:22,
                                                    pC1_4a:3, pC1_4b:1, pC1_12b:4, pC1_17b:5 }
                        (weights = the type's â†’pIP10 synapse total; add aIPg7:1280 if you want the
                         empirical largest driver, but see the caveat in Â§7)

courtship_late_gain   = Î£ w_iÂ·r_i / Î£ w_i   over  { pC1_16a:341, pC1_16b:278, aSP10A_b:153,
                                                    pC1_14a:79, pC1_15a:61, pC1_17a:58, pC1_15b:57,
                                                    pC1_13c:48, pC1_3a:39, pC1_3b:33, pC1_11a:33 }
                        (weights = â†’aSP22)

courtship_chase_gain  = unweighted mean over { pC1_1a, pC1_1b, pC1_2a, pC1_2b, pC1_2c, pC1_9a, pC1_9b,
                                               pC1_15a, pC1_15b, pC1_15c, pC1_10b, pC1_10c, pC1_10d }
                        (Rubin's chase/wing-extension set + the LC10a/LC16 visual recipients;
                         these have almost no direct DN weight, so an unweighted mean is honest here)

aggression_gain       = unweighted mean over { pC1x_a, pC1x_b, pC1x_d,
                                               aIPg1, aIPg2, aIPg_m1, aIPg_m2, aIPg_m3, aIPg_m4,
                                               aIPg5, aIPg6, aIPg8, aIPg9, aIPg10 }
```

Leave `pC1_3a/3b/3c, pC1_6a/6b, pC1_8a-c, pC1_11a/11b, pC1_12a/12b, pC1_18a/18b, pC1_2a/2b` out of all four, or put them in a fifth `courtship_arousal_unlabelled` bucket. They are unlabelled in Rubin 2026 and connectomically heterogeneous â€” pC1_18a/18b in particular are mostly a *feed into pC1x* (pC1_18bâ†’pC1x_b 751, pC1_18aâ†’pC1x_d 745, pC1_18bâ†’pC1x_d 624), i.e. they gate aggression rather than courtship. **[C-high]**

### 6.3 Numbers that justify the change

| set | cells | % of 156 pC1 cells | weight â†’pIP10 | % of pC1â†’pIP10 |
|---|---:|---:|---:|---:|
| Rubin song set (9 types) | 37 | 23.7% | 885 | **45.6%** |
| Rubin chase set (9 types) | 31 | 19.9% | 133 | **6.9%** |
| pC1x_a/b/d (aggression) | 6 | 3.8% | 28 | **1.4%** |

The current unweighted mean under-weights song drive by ~1.9Ã— and over-weights the chase + aggression cells by ~3Ã—. **[C-high]**

---

## 7. P1 stimulation protocol for the LIF

Mean-field steady state for a current-based LIF: a presynaptic type firing at `r` Hz through total per-target-cell weight `Î£w` produces a depolarisation `Î”V = Ï„_m Â· W_syn Â· Î£w Â· r`. Threshold is reached when

```
r_required  =  (V_th âˆ’ V_rest) / (Ï„_m Â· W_syn Â· Î£w_per_cell)
```

Below, `Î£w_per_cell` = the typeâ†’type totals above divided by 2 (pIP10 has 2 cells). Taking `V_th âˆ’ V_rest = 15 mV`, `Ï„_m = 20 ms`. Plug in gap 1's actual `W_syn` (mV of PSP per unit connectome weight per spike):

| driven population | Î£w per pIP10 cell | W_syn=0.02 | 0.05 | 0.10 | 0.20 |
|---|---:|---:|---:|---:|---:|
| P1 song set only (9 types, 37 cells) | 442 | 84.7 Hz | 33.9 Hz | 16.9 Hz | 8.5 Hz |
| P1 song set **+ aIPg7** | 1082 | 34.6 Hz | 13.9 Hz | **6.9 Hz** | 3.5 Hz |
| all 49 pC1 types (156 cells) | 970 | 38.6 Hz | 15.5 Hz | 7.7 Hz | 3.9 Hz |
| all pC1 + aIPg + aSP10 | 1819 | 20.6 Hz | 8.2 Hz | 4.1 Hz | 2.1 Hz |
| every cholinergic input to pIP10 | 5978 | 6.3 Hz | 2.5 Hz | 1.3 Hz | 0.6 Hz |
| net ACh âˆ’ GABA âˆ’ Glu, all inputs | 1248 | 30.1 Hz | 12.0 Hz | 6.0 Hz | 3.0 Hz |

**[C-high]** on the arithmetic and the Î£w values; **[C-low]** on whether 15 mV / 20 ms match gap 1's actual LIF constants â€” **re-run `lif.py` with the real `V_th âˆ’ V_rest` and `Ï„_m` before committing.**

**Recommended protocol:**

- **Drive set:** the 9 Rubin song types *plus* `aIPg7`. Driving the song set alone needs an implausible 85 Hz at `W_syn = 0.02`; adding aIPg7 halves it to 35 Hz, and at `W_syn â‰¥ 0.05` it lands in the physiological 7â€“14 Hz band.
- **Rate:** target **20â€“30 Hz** on the driven P1/aIPg cells. Fly central neurons rarely sustain >50 Hz; if your `W_syn` demands more than ~50 Hz, `W_syn` is too small, not the protocol.
- **Ramp:** 200 ms linear ramp to full rate. An instantaneous step makes the 2-cell pIP10 population spike synchronously and the decoder emits a single click instead of a song bout.
- **Duration:** hold **3 s**, then release. Rubin's LED epochs produced song "during the stimulus period" with several types persisting past offset, so 3 s is enough for a recognisable bout and matches the paper's regime.
- **Refractory/cooldown:** 2 s minimum between bouts.

**Persistence and runaway.** The pC1 population is heavily recurrent: **28,823 synapses across 5,758 pC1â†’pC1 edges = 184.8 units of recurrent weight received per pC1 cell.** The self-sustaining rate at which recurrence alone holds the pool at threshold is: **[C-high]**

| W_syn | r* (latch point) |
|---:|---:|
| 0.02 | 203 Hz (no latch â€” recurrence too weak) |
| 0.05 | 81 Hz |
| **0.10** | **41 Hz** |
| 0.20 | 20 Hz |

So at `W_syn = 0.10` a 20â€“30 Hz drive sits *just below* the 41 Hz latch â€” good, you get decay-with-a-tail (the "persistent phenotype" Rubin describes for P1_1a/2a/b) without a stuck-on fly. **At `W_syn â‰¥ 0.20` the P1 pool will latch permanently ON and the fly will sing forever.** Add a hard clamp: cap per-population mean rate at 50 Hz, or add a global divisive normalisation on the pC1 pool. Also make sure the GABA inputs (mAL_m*, ICL008m, AVLP710m, VES024_a, AVLP256, vPR9_a/c, AN00A006) are actually wired with negative sign â€” they are the biological brake and 37% of pIP10's input budget.

---

## 8. Open questions / things I could not verify

1. **The Current Biology VOR is paywalled from here** (403 from cell.com, redirect stub from linkinghub, absent from Europe PMC). Everything in Â§3 is from the October 2025 preprint. If the VOR changed any type assignment between preprint and print, I would not know. **Retry `https://www.cell.com/current-biology/fulltext/S0960-9822(26)01023-7` from an authenticated session.**
2. **`10.1016/j.cell.2026.08.016` is very probably not a Tastekin pC1 paper.** The DOI resolves to Elsevier PII `S0092867426009438` but is unreadable and unindexed. Tastekin's actual preprint is the taste-feeding connectome. Whatever that Cell DOI is, it contributed nothing to gap 6 and the plan should stop citing it for pC1 claims.
3. **aIPg7 is the elephant.** It is the largest single input to pIP10 in the entire dataset (1280, 10.7% of pIP10's cholinergic budget) and it also feeds aSP22 (161), DNp45 (449), DNp43 (142), DNp60 (126). The Rubin preprint **never mentions aIPg7** in anything I retrieved, and aIPg neurons are canonically *female aggression*. Either male aIPg7 has been repurposed for song, or the assignment deserves a caveat in the mod's docs. I could not resolve this. **[C-high]** that the connection exists; **[C-high]** that the paper is silent on it.
4. **Sign of the glutamatergic AN09B017/vAB3 pathway** (Â§4.1) is a genuine biological unknown that changes whether taste-on-contact excites or suppresses courtship.
5. **No behavioural label for pC1_5a/5b, pC1_7a/7b, pC1_11b** â€” collectively 639 units of pIP10 drive, 33% of all pC1â†’pIP10. Rubin's 32 lines did not cover them. Assigning them to `courtship_song_gain` (as I recommend in Â§6.2) is a **connectomic inference, not an experimental result.**
6. **`pC1_2a/2b` (bodyId 17387)** is one un-splittable cell. Decide whether to assign it to 2a, to 2b, or to drop it; do not create a 49th type.
7. I did not verify the split-GAL4 SS line â†’ cell type mapping table; the summarizer reported the preprint has no consolidated table, only Figure 2A-T and Supp. Figs S1â€“S2. Only **SS86900** (aIPg_m2 + aIPg_m4 + aIPg1) was named explicitly.

---

## 9. Reproduction

Helper: `np.py` (POSTs Cypher to `https://neuprint.janelia.org/api/custom/custom`, dataset `male-cns:v1.0`, no token).
Table/statistics builders: `build.py` â†’ `table_main.md`; `lif.py` â†’ the Â§7 tables.
All in `C:\Users\drini\AppData\Local\Temp\claude\C--Users-drini-OneDrive-Documents-fal-dev-fruit-fly-minecraft\793798d7-139a-4671-8d90-a5256c761f25\scratchpad\research\`.

Representative query (per-type DN output):

```cypher
MATCH (a:Neuron)-[c:ConnectsTo]->(b:Neuron)
WHERE (a.type STARTS WITH 'pC1' OR a.type STARTS WITH 'aIPg' OR a.type STARTS WITH 'aSP10')
  AND b.type IN ['pIP10','pMP2','aSP22','pIP1','vPR6']
RETURN a.type AS src, b.type AS dst, sum(c.weight) AS w ORDER BY dst, w DESC
```

### Sources

- Rubin GM et al. (2025) *Networks of sexually dimorphic neurons that regulate social behaviors in Drosophila.* bioRxiv â€” https://doi.org/10.1101/2025.10.21.683766 (full text: https://www.biorxiv.org/content/10.1101/2025.10.21.683766v1.full-text ; Europe PMC PPR1106358)
- Rubin GM et al. (2026) Current Biology â€” https://doi.org/10.1016/j.cub.2026.08.013 (paywalled, not read)
- Berg S et al. (2025) *Sexual dimorphism in the complete connectome of the Drosophila male central nervous system.* bioRxiv â€” https://doi.org/10.1101/2025.10.09.680999
- Tastekin I et al. (2025) *From Sensory Detection to Motor Action: The Comprehensive Drosophila Taste-Feeding Connectome.* bioRxiv â€” https://doi.org/10.1101/2025.08.25.671814
- neuPrint `male-cns:v1.0` â€” https://neuprint.janelia.org/ (API: https://neuprint.janelia.org/api/custom/custom)
- Europe PMC REST â€” https://www.ebi.ac.uk/europepmc/webservices/rest/
