# Provenance of the bundled connectome

This file records exactly where the data inside `src/main/resources/connectome/malecns-v1.0.flyb.gz` came from and every
transformation applied to it, so that the file can be regenerated, audited, and attributed as required by its CC BY 4.0
license. Counts below are copied from `docs/connectome-stats.json`, which `tools/build_flyb.py` writes alongside the binary.

## 1. Source dataset

| Field | Value |
|---|---|
| Dataset | neuPrint **`male-cns:v1.0`** — the complete adult male *Drosophila melanogaster* central nervous system (brain + ventral nerve cord) |
| Producers | FlyEM Project Team (HHMI Janelia Research Campus), Drosophila Connectomics Group (University of Cambridge / MRC LMB), Google Research Connectomics |
| Publication | Berg S, Beckett IR, Costa M, Schlegel P, Januszewski M, et al. *Sexual dimorphism in the complete Drosophila male central nervous system connectome.* Cell 189(18):5504-5526.e15 (2026). https://doi.org/10.1016/j.cell.2026.08.015 |
| Server | `https://neuprint.janelia.org` (neuPrintHTTP 1.9.3), REST endpoint `POST /api/custom/custom` |
| Access | **Anonymous** Cypher (no token, no login). The dataset is marked public; the Janelia project page states "supports anonymous access". Token-less `GET` returns 401, token-less `POST` works. |
| DVID segmentation UUID | `4b2087c0fbe046bfaf0d60bc970e3e5d` |
| Dataset release / last edit | released 2026-06-08; `lastDatabaseEdit` 2026-03-28 (connectivity) / 2026-06-08 (segment property update) |
| Voxel frame | 8 nm isotropic voxels (`voxelSize [8,8,8] nanometers`); `somaLocation` is stored in this frame |
| Whole-dataset totals (not all bundled) | 176,422 `:Neuron` nodes; 45,656,140 presynapses / 311,833,243 PSDs; 125,024,863 synapses in Neuron->Neuron `ConnectsTo` edges; ~25.9 M connections at weight >= 1 |
| Fetch date | **2026-09-03** local time (started 2026-09-04T02:32:05Z, finished 2026-09-04T02:33:56Z; 111 s with 6 workers) |
| Build date | 2026-09-04T03:05:27Z |
| Fetch tool | `tools/fetch_neuprint.py` (User-Agent `fruit-fly-minecraft/0.1`) |
| Build tool | `tools/build_flyb.py` (defaults: `--min-weight 5 --nt-conf 0.5`) |
| Data license | **CC BY 4.0** (https://creativecommons.org/licenses/by/4.0/), stated at https://male-cns.janelia.org/download and https://www.janelia.org/project-team/flyem/male-cns-connectome |

Canonical entry points: https://male-cns.janelia.org/ , https://neuprint.janelia.org/?dataset=male-cns%3Av1.0 ,
bulk files at `gs://flyem-male-cns/v1.0/` (public Google Cloud Storage bucket).

## 2. Queries issued

Neuron table (paged by 20,000 rows, ordered by `bodyId`):

```cypher
MATCH (n:Neuron) WITH n ORDER BY n.bodyId SKIP $skip LIMIT 20000
RETURN n.bodyId AS bodyId, n.type AS type, n.instance AS instance, n.superclass AS superclass, n.class AS class,
       n.subclass AS subclass, n.consensusNt AS consensusNt, n.predictedNt AS predictedNt,
       n.predictedNtConfidence AS predictedNtConfidence, n.celltypePredictedNt AS celltypePredictedNt,
       n.somaSide AS somaSide, n.rootSide AS rootSide, n.somaLocation.x AS somaX, n.somaLocation.y AS somaY,
       n.somaLocation.z AS somaZ, n.assignedOlHex1 AS hex1, n.assignedOlHex2 AS hex2, n.dimorphism AS dimorphism,
       n.fruDsx AS fruDsx, n.somaNeuromere AS somaNeuromere, n.entryNerve AS entryNerve, n.exitNerve AS exitNerve,
       n.status AS status, n.pre AS pre, n.post AS post, n.upstream AS upstream, n.downstream AS downstream,
       n.synweight AS synweight, n.size AS size, n.flywireType AS flywireType, n.mancType AS mancType,
       n.hemibrainType AS hemibrainType, n.synonyms AS synonyms
```

Edge list (1,184 chunks of presynaptic bodyIds, each chunk ~250,000 downstream synapses or at most 400 ids):

```cypher
MATCH (a:Neuron)-[e:ConnectsTo]->(b:Neuron)
WHERE a.bodyId IN [...] AND e.weight >= 5
RETURN a.bodyId AS pre, b.bodyId AS post, e.weight AS w
```

`e.weight` is neuPrint's synapse count for the connection at the dataset's default PSD confidence (0.5). The higher-confidence
`weightHP` column was not used.

Raw result (`data/raw/male-cns_v1.0/fetch_meta.json`, not committed): 176,422 neurons; 6,287,789 connections carrying
90,297,299 synapses.

## 3. Transformations (in the order `tools/build_flyb.py` applies them)

1. **Neuron ordering.** All 176,422 neurons kept (no superclass excluded, `--drop-orphans` off), sorted by `bodyId`; the dense
   index 0..176,421 used everywhere in the mod is this sort order. `bodyId` is stored so every neuron can be traced back to
   neuPrint.
2. **Synapse-count threshold.** Only connections with **>= 5 synapses** (applied in the Cypher query and again at build time).
   This keeps 6,287,749 of ~25.9 M connections (24 %) and 90,296,905 of 125,024,863 synapses (72 %). Shiu et al. 2024 used no
   threshold; the threshold here is for size and speed and is compensated by the global gain (section 4 of
   `docs/VALIDATION.md`).
3. **Endpoints.** Both endpoints must be in the neuron table (all were).
4. **Autapses dropped.** 40 self-connections (`pre == post`, 394 synapses) are removed because a point-neuron model has no
   use for them.
5. **Weights.** Stored as unsigned 16-bit synapse counts (clipped at 65,535; no connection reached the clip). No
   normalisation, no sign folded into the weight: the sign lives on the presynaptic neuron.
6. **Neurotransmitter and sign (Dale's law, whole-neuron sign).**
   - Transmitter: `consensusNt` if it is one of {acetylcholine, glutamate, gaba, histamine, dopamine, octopamine, serotonin};
     else `predictedNt` if its `predictedNtConfidence >= 0.5`; else `celltypePredictedNt`; else `unclear`.
   - Sign: acetylcholine **+1**; glutamate **-1**; GABA **-1**; histamine **-1**; dopamine, octopamine, serotonin **+1**;
     unclear or missing **+1**. Glutamate is inhibitory in the adult fly (GluCl), histamine is the photoreceptor -> lamina
     transmitter (histamine-gated chloride channels), both following Shiu et al. 2024, who also assign the monoamines to the
     excitatory category.
   - Result: acetylcholine 104,511; glutamate 29,763; GABA 22,248; unclear 10,900; histamine 8,021; serotonin 415;
     dopamine 399; octopamine 165. Signs: 116,390 excitatory, 60,032 inhibitory.
7. **Side.** `somaSide`, falling back to `rootSide`, else empty.
8. **Retina table.** Photoreceptors (`superclass = ol_sensory`, types `R1-R6`, `R7*`, `R8*`, `R7R8_unclear`) carry no column
   coordinates in neuPrint. Each is assigned the `(side, assignedOlHex1, assignedOlHex2)` of its **strongest hex-bearing
   postsynaptic target** (summed weight over the kept edges). 5,193 of 6,091 photoreceptors received a column (3,324 R1-R6,
   598 R7, 1,271 R8). Every `L1`, `L2`, `L3` lamina cell with a hex coordinate is listed with its own column (1,767 / 1,767 /
   892). Total 9,619 entries; 3,572 left, 6,047 right (the right optic lobe is more completely reconstructed).
9. **Soma coordinates.** Raw neuPrint voxel coordinates as float32, NaN when absent (141,781 neurons have a soma location).
10. **Metadata.** The build statistics (everything in `docs/connectome-stats.json` except `class_counts`) are embedded in the
    file header as JSON and printed by `gradlew brainBench`.

Nothing else is altered: no neuron is merged, split, renamed or re-typed; type, class, subclass, dimorphism, fruDsx,
neuromere and nerve strings are stored verbatim in string tables.

## 4. File format (FLYB v1)

From the header of `tools/build_flyb.py`; the Java reader is `com.fruitfly.brain.Connectome.parse`. Little-endian, whole file
gzip-compressed.

```
char[4] "FLYB", u32 version=1, u32 nNeurons, u32 nEdges, u32 nRetina
str16 dataset                         (str16 = u16 byteLen + utf8)
str32 metaJson                        (u32 byteLen + utf8; provenance + stats)
string tables (u16 count, then count x str16), index 0 is always "" (missing):
    types, superclasses, classes, subclasses, nts, sides, dimorphisms, fruDsx, neuromeres, nerves
neuron arrays (struct of arrays, n = nNeurons, sorted by bodyId):
    i64[n] bodyId
    i32[n] typeIdx
    u8[n]  superclassIdx, u8[n] classIdx, u16[n] subclassIdx
    u8[n]  ntIdx, i8[n] ntSign          (+1 excitatory, -1 inhibitory, 0 unknown/neuromodulatory)
    u8[n]  sideIdx
    i8[n]  hex1, i8[n] hex2             (-1 when absent; medulla column coordinates)
    u8[n]  dimorphismIdx, u8[n] fruDsxIdx, u8[n] neuromereIdx, u8[n] nerveIdx
    f32[3n] soma x,y,z (raw neuPrint voxel coordinates; NaN when absent)
    i32[n] preSynapses, i32[n] postSynapses
edges as CSR over presynaptic neuron index:
    i32[n+1] rowPtr, i32[nEdges] postIdx, u16[nEdges] weight (synapse count, clipped to 65535)
retina table (nRetina entries): i32 neuronIdx, u8 sideIdx, i8 hex1, i8 hex2, u8 kind
    kind: 0 = R1-R6, 1 = R7, 2 = R8, 3 = L1, 4 = L2, 5 = L3
```

(The `ntSign = 0` case is supported by the format and the reader but is not produced by the default sign map, which assigns
+1 to unclear and monoaminergic neurons.)

## 5. Bundled file identity

| File | Bytes | SHA-256 |
|---|---:|---|
| `src/main/resources/connectome/malecns-v1.0.flyb.gz` (as shipped) | 22,964,094 | `e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3` |
| same file, decompressed (FLYB v1 stream) | 46,555,448 | `bc1cdcaf443cdf4ca51d00d32ce20d3b2d51c0854a985dbacdf0285a49be5936` |

MD5 of the gzip file, for tools that still want it: `f2bf882355460bded51739d309958026`.

Raw cache the file was built from (in `data/raw/male-cns_v1.0/`, gitignored, regenerate with `tools/fetch_neuprint.py`):

| File | Bytes | SHA-256 |
|---|---:|---|
| `neurons.csv.gz` | 7,783,941 | `ee0d1539734f2dc276d7cd02023195f4c05f7de36062f3fdebeb7305338d8101` |
| `edges.csv.gz` | 32,265,328 | `7e8c2e0a6b1b3a25d8ca052a6e9d6a06a9a72cd529c24fcd1e3417c89c9394dd` |

Verify on Windows: `certutil -hashfile src\main\resources\connectome\malecns-v1.0.flyb.gz SHA256`
Verify on Linux/macOS: `sha256sum src/main/resources/connectome/malecns-v1.0.flyb.gz`
Verify anywhere: `python -c "import hashlib,sys;print(hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest())" src/main/resources/connectome/malecns-v1.0.flyb.gz`

A rebuild will **not** be byte-identical: the embedded `metaJson` carries the build timestamp and the gzip header carries a
modification time. Compare the counts printed by `gradlew brainBench` (neurons, edges, synapses, retina entries) against the
table in section 1 and `docs/connectome-stats.json` instead. Neuron count, edge count and synapse total will only change if
Janelia edits the dataset (check `lastDatabaseEdit` with `MATCH (m:Meta) RETURN m.lastDatabaseEdit`).

## 6. Headline counts of the bundled derivative

| Quantity | Value |
|---|---:|
| Neurons | 176,422 |
| Connections (>= 5 synapses) | 6,287,749 |
| Synapses in those connections | 90,296,905 |
| Neurons with soma location | 141,781 |
| Descending neurons (`descending_neuron`) | 1,314 |
| Motor neurons (`vnc_motor` 708 + `cb_motor` 107) | 815 |
| Optic-lobe intrinsic / central-brain intrinsic / VNC intrinsic | 89,403 / 32,164 / 13,161 |
| Sensory: visual 6,091 (photoreceptors); olfactory 2,639; gustatory 1,428; mechanosensory 1,733 + tactile 2,558 + proprioceptive 1,454; thermo 25; hygro 66 | |
| Kenyon cells / antennal-lobe PNs / CX | 4,064 / 686 / 2,950 |
| Retina table entries / photoreceptors mapped | 9,619 / 5,193 |

## 7. License and attribution

- **Data** (this file's subject): CC BY 4.0. Attribution: "Derived from the male CNS connectome, neuPrint dataset
  male-cns:v1.0, by the FlyEM Project Team (HHMI Janelia Research Campus), the Drosophila Connectomics Group (University of
  Cambridge / MRC LMB) and Google Research; licensed CC BY 4.0. Modified: connections thresholded at >= 5 synapses,
  autapses removed, neurons re-indexed, neurotransmitter signs assigned, photoreceptors assigned to medulla columns."
  Full citations are in `README.md` (Data sources and citations).
- **Code** (everything under `src/`, `tools/`, `docs/`): MIT, see `LICENSE`.
- Type names, class labels and literature synonyms used throughout the code and documentation are facts from the dataset
  and its companion papers; they are attributed in `README.md` and `docs/research/`.
- This project is not affiliated with or endorsed by HHMI, Janelia, Google, the University of Cambridge, the MRC LMB, or
  Mojang/Microsoft.
