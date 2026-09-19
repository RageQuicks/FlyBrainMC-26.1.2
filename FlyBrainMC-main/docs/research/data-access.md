# Data access, licensing, citation and provenance: male CNS connectome (male-cns:v1.0) and companions

Research date: 2026-09-03 (the Cell publication day). Confidence tags: **[H]** verified against a primary source or by a live probe I ran; **[M]** single secondary source or inferred from consistent evidence; **[L]** plausible but not verified. Every live probe below was run from this machine against the public endpoints with no token; raw outputs are in `..\probe\` next to this file (`meta.json`, `datasets.json`, `gcs_conn.json`, `ng.json`, `swagger.yaml`, `edge_sample.npy`, `column_coord.png`).

---

## 0. Decisions in one screen

| Question | Answer | Conf. |
|---|---|---|
| License of male-cns:v1.0 data | **CC BY 4.0** (stated on male-cns.janelia.org/download, janelia.org project page, bioRxiv/PMC preprint) | H |
| Can a compact derived subset ship inside the mod jar? | **Yes.** CC BY 4.0 permits redistribution and adaptation with attribution, a license link, and a note of modifications. Bundle edge list + node table; keep bodyIds for provenance. | H |
| Anonymous neuPrint access official? | **Yes.** Janelia's project page: "neuPrint dataset name: male-cns:v1.0 (supports anonymous access)". neuPrintHTTP docs: anonymous callers get read-only access to datasets marked public. Verified live: token-less `POST /api/custom/custom` works; `GET` returns 401. | H |
| neuprint-python without a token? | **No.** `Client()` raises `RuntimeError("No token provided...")` if neither `token=` nor `NEUPRINT_APPLICATION_CREDENTIALS` is set. Use raw HTTP (`requests`) for anonymous scripts, or get a free token at https://neuprint.janelia.org/account (Google login). | H |
| Rate limits | None documented anywhere (neuPrintHTTP README, swagger, neuprint-python). Server-side Cypher timeout is a config value (`"timeout": 600` s in the README example). Be polite: bulk files for bulk needs, one-off Cypher for curation, never query at game runtime. | H (absence) / M (600 s applies to janelia server) |
| FlyWire FAFB v783 license | **CC BY-NC 4.0** per https://flywire.ai/guidelines ("FlyWire's public release data is made available under license CC BY-NC 4.0"). NOTE the Zenodo dump 10.5281/zenodo.10676866 is tagged `cc-by-4.0` — a conflict; treat as NC. | H (both facts) |
| Ship FlyWire data in the jar? | **No** by default. NC clause conflicts with Modrinth/CurseForge monetisation and any commercial use. Provide a fetch script (public GCS URLs need no login — verified HEAD 200). | H |
| Better CC-BY female counterpart | **BANC v888** (female brain + VNC; Bates et al., Nature 2026, 10.1038/s41586-026-10735-w). Harvard Dataverse doi:10.7910/DVN/7WTH1N is **CC BY 4.0**, files `banc_888_edgelist_simple_v3.feather` (359 MB), `banc_888_meta.feather` (57.6 MB). | H |
| Size of the w>=5 graph in the jar | 6,287,789 edges; CSR + delta + varint ≈ 3.1 B/edge raw, ≈ 2.1 B/edge after deflate → **~13 MB in the jar**; ~38 MB on heap as `int[]`+`short[]` CSR. | H (measured on 1.16 M real edges, extrapolated) |

---

## 1. neuPrint `male-cns:v1.0`

### 1.1 Canonical URLs [H]

| Resource | URL |
|---|---|
| Janelia project page (states license, anonymous access) | https://www.janelia.org/project-team/flyem/male-cns-connectome |
| MaleCNS site (home / explore / download / dimorphism explorer) | https://male-cns.janelia.org/ (mirror of https://janelia-flyem.github.io/male-cns ; source repo github.com/janelia-flyem/male-cns, GPL-3.0 for the site code) |
| Download page | https://male-cns.janelia.org/download/ |
| neuPrint explorer, dataset preselected | https://neuprint.janelia.org/?dataset=male-cns%3Av1.0&qt=findneurons |
| neuPrint REST base | https://neuprint.janelia.org/api/ (version endpoint returns `{"Version":"1.9.3"}`) |
| Clio (annotation view) | https://clio.janelia.org/ws/annotate?dataset=male-cns:v1.0&tab=bodies |
| Neuroglancer base scene | https://neuroglancer-demo.appspot.com/#!gs://flyem-male-cns/v1.0/male-cns-v1.0.json (scene JSON is a 60 KB public object) |
| Cell Type Explorer (male CNS) | https://reiserlab.github.io/celltype-explorer-drosophila-male-cns (repo license CC-BY-4.0; snapshot generated 2026-08-25 from neuPrint uuid 4b2087c0…) |
| NeuronBridge (light-microscopy matching) | https://neuronbridge.janelia.org/ (male CNS added 2025-11-07) |
| GCS bucket | `gs://flyem-male-cns/` — public; HTTPS form `https://storage.googleapis.com/flyem-male-cns/<object>`; JSON listing `https://storage.googleapis.com/storage/v1/b/flyem-male-cns/o?prefix=v1.0/` |
| natverse R package | https://github.com/natverse/malecns (GPL-3.0; default dataset `male-cns:v1.0`; needs `NEUPRINT_TOKEN`) |
| neuprint-python docs | https://connectome-neuprint.github.io/neuprint-python/docs/ |
| neuPrintHTTP server source | https://github.com/connectome-neuprint/neuPrintHTTP |

Datasets exposed anonymously by `/api/dbmeta/datasets` on 2026-09-03 [H]: `fib19:v1.0, hemibrain:v1.1, hemibrain:v1.2.1, male-cns:v0.9, male-cns:v1.0, manc:v1.0, manc:v1.2.1, manc:v1.2.3, mushroombody, optic-lobe:v1.0.1, optic-lobe:v1.1`. (`male-cns:v0.9` is the preprint-era snapshot, released 2025-10-03; `v1.0` released 2026-06-08.)

### 1.2 Dataset metadata (`MATCH (m:Meta) RETURN m`) [H]

| Field | Value |
|---|---|
| `dataset` / `tag` | `male-cns` / `v1.0` |
| `uuid` | `4b2087c0fbe046bfaf0d60bc970e3e5d` (DVID segmentation UUID; also cited by Cell Type Explorer) |
| `lastDatabaseEdit` | `2026-03-28 11:56:30 -04:00 / 2026-06-08T01:31:39-04:00 (segment property update)` |
| `latestMutationId` | 1006591300 |
| `voxelSize` / `voxelUnits` | `[8, 8, 8]` / `nanometers` — Meta description: "Synapse coordinates are stored in voxel units (8nm)." `somaLocation` is the same frame (see 1.6). |
| `totalPreCount` / `totalPostCount` | 45,656,140 / 311,833,243 — matches the paper's "46 million presynapses connected to 312 million PSDs". |
| `postHighAccuracyThreshold` | 0.5 (the bulk files are named `minconf-0.5`) |
| `postHPThreshold` | 0.7 (edge property `weightHP` counts PSDs at confidence >= 0.7) |
| `primaryRois` | 144 primary ROIs; `superLevelRois` are the neuropil-level ROIs |
| `roiInfo` | 5,619 ROIs: 144 primary, 38 nerve ROIs, 5,212 optic-lobe column ROIs (`{ME,LO,LOP}_{L,R}_col_<hex1>_<hex2>`), plus layers (`ME_R_layer_01..10`, `LO_R_layer_1..7`, `LOP_R_layer_1..4`), AL glomeruli (`AL-DA1(R)`...), CX sub-compartments, VNC neuropils (`LegNp(T1)(L)`, `NTct(UTct-T1)(L)`, `WTct(UTct-T2)`, `HTct(UTct-T3)`, `IntTct`, `LTct`, `ANm`, `mVAC(T1..3)`), `GNG`, `SAD`, `AMMC`, `PRW`, `FLA`, `CAN`, `CV` (cervical connective) |
| `roiHierarchy` top level | `CNS` → `CentralBrain` (28 children), `Optic(L)` (6), `Optic(R)` (6), `CV` (1), `VNC` (60) |
| `neuronColumnsVisible` | `bodyId, type, consensusNt, post, pre, <ROI columns>, roiBarGraph` |

### 1.3 Neuron property fields (`Meta.neuronProperties`, types as stored) [H]

```
bodyId long          type string         instance string      group long        supertype string
superclass string    class string        subclass string      status string     statusLabel string
somaSide string      rootSide string     somaNeuromere string somaLocation point{srid:9157}
tosomaLocation point{srid:9157}          locationType string  size long (voxels)
pre int  post int  upstream int  downstream int  synweight int
consensusNt string   predictedNt string  predictedNtConfidence float  totalNtPredictions float
celltypePredictedNt string  celltypePredictedNtConfidence float  celltypeTotalNtPredictions float
dimorphism string    fruDsx string       matchingNotes string  synonyms string
assignedOlHex1 float assignedOlHex2 float
itoleeHl string      trumanHl string     birthtime string      serialMotif string
mancBodyid float  mancGroup float  mancType string  mancSerial float  mcnsSerial float
hemibrainType string flywireType string  vfbId string
entryNerve string    exitNerve string    receptorType string   roiInfo string (JSON)
```
Per-ROI membership is also stored as boolean properties named after the ROI (e.g. `n["LegNp(T1)(R)"] = true`), so `[k IN keys(n) WHERE k CONTAINS '_col_']` lists a neuron's columns.

Controlled vocabularies (from `Meta.neuronColumnsOrdered.choices`) [H]:

* `superclass`: `ENS, ascending_neuron, cb_efferent, cb_endocrine, cb_intrinsic, cb_motor, cb_sensory, cb_sensory_tbc, descending_neuron, descending_neuron_tbc, efferent_ascending, efferent_descending, ol_intrinsic, ol_sensory, sensory_ascending, sensory_ascending_tbc, sensory_descending, visual_centrifugal, visual_projection, visual_projection_tbc, vnc_efferent, vnc_endocrine, vnc_intrinsic, vnc_motor, vnc_sensory, vnc_sensory_tbc, vnc_tbc` (`_tbc` = to be confirmed).
* `class`: `ALIN, ALLN, ALON, ALPN, CX, DAN, Kenyon_Cell, MBON, SEZPN, chemosensory, gustatory, hygrosensory, mechanosensory, mechanosensory_proprioceptive, mechanosensory_tactile, mechanosensory_tbc, ol_bilateral, olfactory, thermosensory, unknown_sensory, visual`.
* `subclass`: sensory organs/regions (`auditory, wind_gravity, campaniform sensilla, chordotonal organ, hair plate, haltere, labellar bristle, leg bristle, wing bristle, mechanosensory bristle, taste bristle, taste peg, pharyngeal sensillum, strand receptor, leg, wing, neck, notum, abdomen, grooming`) and DN/AN neuropil-target codes (`BA BI BR CA CI CR IA II IR XA ad am fl hl hm ht it lt ml nm nt pm rm ut wm wt xl xm xn`).
* `status`: `Anchor, Assign, Glia, Orphan, Traced, Unimportant`; `statusLabel` finer (`Roughly traced 71,979; Reviewed 54,066; Prelim Roughly traced 36,387; Out of scope 4,035; Orphan 3,461; RT Hard to trace 2,019; Orphan-artifact 1,962; Orphan hotknife 975; Leaves 528; Anchor 283; Soma Anchor 201; PRT Orphan 140; Sensory Anchor 101; Hard to trace 66; null 179`).
* `consensusNt` / `predictedNt` / `celltypePredictedNt`: `acetylcholine, dopamine, gaba, glutamate, histamine, octopamine, serotonin, unclear`.
* `dimorphism`: `male-specific (1,258 neurons), sexually dimorphic (771), potentially sexually dimorphic (177), potentially male-specific (162)`; all others null (= isomorphic or unmatched).
* `fruDsx`: `fru_high (2,611), fru_low (1,989), coexpress_high (193), dsx_high (138), coexpress_low (65), dsx_low (16)` — "high"/"low" = confidence of the expression call, per the paper's description.
* `somaSide`: `L, M, R`; `somaNeuromere`: `A1..A10, CG, DC, GNG, LB, MD, MX, NA, T1, T2, T3, TC` (brain neuromeres: CG = cerebral ganglion, DC deutocerebrum, TC tritocerebrum, MD mandibular, MX maxillary, LB labial; VNC: T1-T3, A1-A10).
* `entryNerve`: `ADMN, AN, AbN1-4, AbNT, DMetaN, DProN, MesoLN, MetaLN, MxLbN, ON, PDMN, PhN, PrN, ProAN, ProCN, ProLN, VProN, aPhN`; `exitNerve` similar plus `CvN, NCC, MesoAN, PDMNa, PDMNp`.
* `itoleeHl` (Ito/Lee brain hemilineages, e.g. `ALad1, SMPpv2_ventral, DM1_CX_p...`) and `trumanHl` (Truman VNC hemilineages `00A..27X`, `_put1` = putative).
* `synonyms`: literature names, formatted `"Author Year: name; Author Year: name"` (e.g. `Cachero 2010: pIP-e; Yu 2010: pIP5; Nojima 2021: pC2l`).

Node-count reconciliation [H]: `MATCH (n:Neuron)` = 176,422 nodes (88,404,403 `:Segment` nodes in total). By `status`: Traced 165,122; Orphan 6,464; null 4,214; Anchor 611; Assign 11. Neurons with a non-`_tbc` `superclass`: 166,606 — within 0.05 % of the paper's 166,691. **Recommendation:** define the mod's neuron set as `status = 'Traced' AND superclass IS NOT NULL` (or simply superclass set and not `_tbc`) and drop the rest; 11,916 nodes have null `type`, 9,722 null `superclass`, 34,641 null `somaLocation` (sensory axons whose somata lie outside the volume).

### 1.4 Edge (`ConnectsTo`) properties [H]

`weight` (all PSDs at conf >= 0.5), `weightHP` (PSDs at conf >= 0.7), `weightHR` (high-recall variant), `roiInfo` JSON `{ROI: {post: n}}` giving the per-ROI split. Example: `{"weight":5,"weightHP":4,"weightHR":5,"roiInfo":"{\"LTct\":{\"post\":3},\"LegNp(T3)(R)\":{\"post\":2},\"VNC\":{\"post\":5}}"}`.

Full Neuron→Neuron weight histogram (one 133 s scan, 2026-09-03) [H]:

| Threshold | Edges | Synapses carried | % of synapses |
|---|---|---|---|
| w >= 1 | 25,862,574 | 125,024,863 | 100 % |
| w >= 2 | 15,437,563 | — | — |
| w >= 3 | 10,615,306 | — | — |
| **w >= 5** | **6,287,789** | **90,297,299** | **72.2 %** |
| w >= 10 | 2,767,506 | 67,523,438 | 54.0 % |
| w >= 20 | 1,067,232 | — | — |
| w >= 50 | 228,220 | — | — |
| max weight | 2,591 | fits `short` | |

(The orchestrator's "6.29M edges with weight>=5" is confirmed: 6,287,789. Codex lists MCNS v1.0 as "166,700 neurons; 6,242,118 connections" — consistent with a >=5 threshold over the 166.7 k neuron set [M].)

`bodyId` range 10,001 … 1,471,062,202; **zero bodyIds exceed 2^31−1**, so bodyIds can be stored as `int32` [H].

### 1.5 Optic-lobe hex columns (`assignedOlHex1/2`) [H unless noted]

* Present on 23,720 neurons of exactly 15 columnar types: `L5 1773, C3 1770, L1 1767, L2 1767, Tm1 1767, T1 1764, Mi1 1762, Mi9 1760, Tm2 1758, Mi4 1758, Tm9 1743, Tm20 1732, L3 892, C2 874, Tm4 833` (L3/C2/Tm4 only assigned in one eye). Ranges hex1 ∈ [1,36], hex2 ∈ [1,39]. Photoreceptors R1-R8 carry no hex fields (confirmed by absence in the type list).
* Column ROIs per neuropil/side: `ME_R 892, ME_L 880, LO_R 875, LO_L 867, LOP_R 856, LOP_L 842` (≈ 870 columns ≈ ommatidia per eye). The ROI name encodes the hex pair with zero-padding: neuron 547265 (Mi1, L, hex 21/6) has synapses in `ME_L_col_21_06` (and neighbours), so `assignedOlHex1/2 == (hex1, hex2)` of the `..._col_<hex1>_<hex2>` ROIs.
* Semantics (Reiser lab `docs/coordinate-systems.md` + `docs/assets/column_coord.png` in github.com/reiserlab/male-drosophila-visual-system-connectome-code, GPL-3.0) [H]: ommatidia form a hex grid with axes hex1, hex2, hex3 (any two suffice); neuPrint stores positive hex1/hex2 with an arbitrary origin outside the eye. Biologically centred axes: **`hex1 → q`, `hex2 → p`, origin `[hex1,hex2] = [18,19] ↔ [p,q] = [0,0]`** (centre of the eye, on the equator). `v` = "virtual hex3" points dorsally; `h` is "somewhat parallel to the perceived horizon". The figure shows p at +120° and q at +60° from h, an equator row of ommatidia through the origin, 16 h-rows above/below and 17 v-rows left/right, and the 7-vs-8-photoreceptor ommatidia marking the equator. So, in a standard axial-hex embedding: `v ∝ p + q` (dorsal positive), `h ∝ q − p`. Whether +h is anterior or posterior for a given eye is **not** labelled in that figure — verify with `LO/LOP` layer positions or the paper's Extended Data before hard-coding [L for the a/p sign].
* Nern et al. 2025 Methods (PMC12119369): coordinates were built in the medulla by assigning the 15 columnar types to hex coordinates "following the approach developed for the FAFB dataset", mapping "one-for-one to lenses on the compound eye (except for some lenses on the edge)"; axes p, q with h, v for eye axes; equator identified as a global anatomical reference [H].
* neuroglancer layer `optic-column-pins` (`gs://flyem-male-cns/v1.0/malecns-v1.0-optic-lobe-column-pins/`) gives 3-D pin lines per column if you want to render an eye map [H].

### 1.6 `somaLocation` coordinate frame [H for numbers, M for anatomical axis semantics]

* Type `point{srid:9157}` (Neo4j cartesian-3d); units are **8 nm voxels**, same frame as the EM/segmentation/neuroglancer (`dimensions: x,y,z = 8e-9 m`). Returned over JSON as `{"coordinates":[x,y,z], "crs":{...,"srid":9157}, "type":"Point"}`; in Cypher use `n.somaLocation.x`.
* 141,781 neurons have a soma; extents x 2,468–93,668, y 4,758–68,996, z 10,154–134,531 (i.e. ~0.73 × 0.51 × 1.0 mm).
* Axis semantics inferred from averages: `somaSide='R'` mean x = 24,711, `'L'` mean x = 72,423 → **x increases toward the animal's LEFT** (right hemisphere at low x). Mean z by neuromere increases monotonically brain → VNC: DC 15,216; CG 29,319; LB 34,264; T1 75,609; T2 94,168; A1 119,101; T3 120,382; A10 132,463 → **z runs anterior → posterior along the neuraxis**. Mean y: cb_intrinsic 19,942, ol_intrinsic 33,180, vnc_intrinsic 56,333 → y is dorso-ventral-ish (the CNS is bent, so brain and VNC differ). Treat these as "good enough for a schematic 3-D layout", not as a rigorous anatomical registration.
* Alternative canonical frames in the bucket: skeletons in JRC2018 unisex template space (1 µm units), mirrored skeletons, meshes transformed into FAFB/FlyWire space (`v1.0/male-cns-meshes-transformed-to-fafb-flywire/`) [H from download page/bucket].

### 1.7 REST API semantics (neuPrintHTTP 1.9.3) [H, live-probed]

* `POST https://neuprint.janelia.org/api/custom/custom` with `Content-Type: application/json` body `{"cypher": "<read-only Cypher>", "dataset": "male-cns:v1.0"}` (optional `"version"` for a neuPrint data-model version check). Response `{"columns":[...], "data":[[row],...], "debug":"<cypher echoed>"}`. Nulls are JSON `null`; points are GeoJSON-like objects; node returns (`RETURN n`) are flat property maps; relationship returns include `startNodeId/endNodeId/id/type` plus properties.
* Anonymous (no `Authorization` header) POST **works** for public datasets. `GET /api/custom/custom?cypher=...` → **401** `{"message":"authentication required"}` (the swagger file says `get` but the server needs POST + JSON body). Query string on POST is ignored; body is required.
* A **bad token is worse than no token**: `Authorization: Bearer garbage` → 401 `"invalid or expired token — neuPrint has moved to a new authorization system; log in at https://neuprint.janelia.org/account to obtain a new token"`. Scripts should omit the header unless a valid token is configured.
* `/api/custom/arrow` (POST, same body) returns Apache Arrow IPC stream — useful for large pulls if you have pyarrow; neuprint-python's docs say JSON currently performs better.
* `/api/version`, `/api/dbmeta/datasets` (per-dataset `ROIs, superLevelROIs, uuid, last-mod, info, description, hidden, logo`), `/api/dbmeta/database`, `/api/help/swagger.yaml` (32 KB; contact neuprint@janelia.hhmi.org). Swagger "version: 0.1.0" refers to the API doc, not the server.
* Authorization model (neuPrintHTTP README): auth delegates to Janelia DatasetGateway; **"Anonymous access: limited to datasets DSG marks as public (read-only)"**; all mutations need grants. Server config example has `"timeout": 600` seconds for Cypher; no rate limiting is described anywhere.
* Observed performance: 4.2 M-row edge pull (8,000 source neurons, all weights) 39.9 s; full 25.9 M-edge aggregate scan 133 s; typical filtered queries 0.3–0.8 s. Etiquette: do bulk extraction from the GCS feather files or one cached one-off Cypher run at build time; never from the game client; send a descriptive `User-Agent`; keep result sets < ~5 M rows per call.

### 1.8 neuprint-python (0.5.x) [H from docs/source]

```python
Client(server, dataset=None, token=None, verify=True, progress=True)   # https:// enforced; token REQUIRED (env NEUPRINT_APPLICATION_CREDENTIALS)
c = Client('neuprint.janelia.org', dataset='male-cns:v1.0')
c.fetch_version(); c.fetch_datasets(reload_cache=False)
c.fetch_custom(cypher, dataset='', format='pandas'|'json', use_arrow=False)
fetch_meta(); fetch_all_rois(); fetch_roi_hierarchy(include_subprimary=True, mark_primary=True, format='dict')
fetch_neurons(criteria=None, *, omit_rois=False, returned_columns='all')  -> (neurons_df, roi_counts_df)
fetch_adjacencies(sources=None, targets=None, rois=None, min_roi_weight=1, min_total_weight=1,
                  include_nonprimary=False, export_dir=None, batch_size=200,
                  properties=['type','instance'], *, weight_props='all', omit_rois=False, threads=4)
fetch_simple_connections(upstream_criteria=None, downstream_criteria=None, rois=None, min_weight=1, properties=[...])
fetch_synapse_connections(source_criteria=None, target_criteria=None, synapse_criteria=None, min_total_weight=1, batch_size=10000, *, nt=None)
NeuronCriteria(matchvar=None, bodyId=None, type=None, instance=None, inputRois=None, outputRois=None, status=None, cropped=None, ...)
```
`min_total_weight` filters on the summed weight across ROIs; `min_roi_weight` on the per-ROI weight. Retries: urllib3 `Retry(connect=2, backoff_factor=0.1)`. For an anonymous build script, a 12-line `requests.post` wrapper (as used for the probes here) is simpler than neuprint-python.

### 1.9 Bulk downloads (`gs://flyem-male-cns/`, public, listed 2026-09-03) [H]

Flat connectome, Apache Arrow Feather (`v1.0/connectome-data/flat-connectome/`):

| File | Size | Content |
|---|---|---|
| `body-annotations-male-cns-v1.0-minconf-0.5.feather` | 14.5 MB | neuron annotations (type, class, side...) — HTTPS HEAD 200 verified |
| `body-neurotransmitters-male-cns-v1.0.feather` | 43.3 MB | per-body NT predictions |
| `body-stats-male-cns-v1.0-minconf-0.5.feather` | 778 MB | synapse counts for all segments |
| `connectome-weights-male-cns-v1.0-minconf-0.5.feather` | 1,051 MB | segment→segment weights, all bodies |
| `...-traced-only.feather` / `...-significant-only.feather` | 508 / 502 MB | same, restricted to traced / "significant" bodies (**best source for a full w>=1 edge list**) |
| `syn-partners-male-cns-v1.0-minconf-0.5.feather` (+ traced/significant variants) | 6,777 MB (2,965 MB) | synapse-level partner pairs |
| `syn-points-male-cns-v1.0-minconf-0.5.feather` | 13,061 MB | every pre/post point with ROI |
| `tbar-neurotransmitters-male-cns-v1.0.feather` | 2,652 MB | per-presynapse NT probabilities |

Other prefixes under `v1.0/`: `database/neo4j` (complete Neo4j 4.4.16 dump), `database/neuprint-inputs` (CSV inputs), `segmentation/` (precomputed, plus `skeletons-*` in SWC 8 nm / precomputed 1 nm / mirrored / JRC2018 1 µm), `malecns-v1.0-soma-points/`, `malecns-v1.0-optic-lobe-column-pins/`, `male-cns-v1.0-synapses-precomputed/`, `nblasts/`, `supervoxels/`, `synapse-ground-truth/`, `male-cns-meshes-transformed-to-fafb-flywire/`. Image volumes: `gs://flyem_cns_z0720_07m_dvidcoords_n5` (raw EM, N5, 8 nm), `gs://flyem-male-cns/em/em-clahe-jpeg` (precomputed). ROIs: `rois/fullbrain-roi-v5`, `rois/malecns-vnc-neuropil-roi-v0`, `rois/{ME,LO,LOP}(R)-columns-v8`, `(L)-columns-v3`, etc. Also in the bucket: `flywire2mcns_meshes/783/`, `banc2mcns_meshes/626`, `manc2mcns_meshes/v1.2/`, `hemibrain2mcns_meshes/v1.2/` (other connectomes registered into male-CNS space — handy for male/female overlays). No Zenodo deposit and no "male-cns-data" GitHub repo exist for the data itself (searched; none found) [M].

Reading Feather needs `pyarrow` (not installed in the local Python 3.9; `pip install pyarrow`).

---

## 2. Publications and exact citations

### 2.1 The flagship Cell paper (published online 2026-09-03) [H via Crossref]

* **Title (as published in Cell):** *Sexual dimorphism in the complete Drosophila male central nervous system connectome*. **Note the word order differs from the preprint title** (*Sexual dimorphism in the complete connectome of the Drosophila male central nervous system*), which Google's blog and Janelia's news page still use.
* Berg S, Beckett IR, Costa M, Schlegel P, Januszewski M, Marin EC, Nern A, Preibisch S, Qiu W, Takemura S-y, … Scheffer LK, Waddell S, Card GM, Ribeiro C, Reiser MB, Hess HF, Rubin GM, Jefferis GSXE (111 authors). *Cell* **189**(18): 5504–5526.e15 (September 2026). DOI **10.1016/j.cell.2026.08.015**. Article license: **CC BY 4.0** (Crossref `vor` license). Elsevier full-text URL: https://www.cell.com/cell/fulltext/S0092-8674(26)00942-6 (blocked to bots; not indexed by Europe PMC yet). Corresponding authors: not verifiable from accessible sources [L].
* Preprint: bioRxiv 10.1101/2025.10.09.680999 (v1 2025-10-09, v2 2025-10-30; CC BY 4.0; PMC12636603). Key numbers from the preprint abstract: 166,691 neurons; 11,691 cell types; vs female: 7,205 isomorphic, 114 dimorphic, 262 male-specific, 69 female-specific types (4.8 % of male / 2.4 % of female neurons); "46 million presynapses connected to 312 million PSDs".
* Press: Google Research blog "A connectomics milestone: Mapping the complete male fruit fly brain" (Januszewski & Jain, 2026-09-03) https://research.google/blog/a-connectomics-milestone-mapping-the-complete-male-fruit-fly-brain/ ; Janelia news https://www.janelia.org/news/researchers-reveal-connectome-of-the-male-fruit-fly-central-nervous-system (page originally dated 2025-10-06 for the preprint) ; University of Cambridge news (2026-09-03) https://www.cam.ac.uk/research/news/comparison-of-male-and-female-fly-brains-is-unlocking-the-secret-workings-of-the-mind ; Science news "New 'connectome' shows all 124 million contact points in the fruit fly's nervous system" ; phys.org "Completing the connectome" (2026-09). News numbers: 166,700 neurons, 124.2 M synapses (Science/Janelia) vs "125 million" (Google) — the neuPrint Neuron→Neuron sum is 125,024,863.

### 2.2 Same-day companion papers (the "package of four") [H via Crossref]

1. Hoeller J, Zhao A, Nern A, Rogers EM, Romani S, Reiser MB. *The organization of visual pathways in the Drosophila brain.* Cell 189(18): 5552–5570.e10 (2026). DOI 10.1016/j.cell.2026.08.014. CC BY 4.0. Preprint bioRxiv 10.64898/2025.12.22.696097.
2. Tastekin I, de Haan Vicente I, Beresford RJ, Morris BJ, Beckett I, Schlegel P, Gkantia M, Marin EC, Costa M, Jefferis GSXE, Ribeiro C. *The complete gustatory connectome of adult Drosophila reveals how taste guides feeding, foraging, and social behavior.* Cell 189(18): 5527–5551.e5 (2026). DOI 10.1016/j.cell.2026.08.016. (Crossref lists no CC license; preprint "From Sensory Detection to Motor Action: The Comprehensive Drosophila Taste-Feeding Connectome", bioRxiv 10.1101/2025.08.25.671814, was CC BY-NC-ND.) Relevant to the mod's taste/feeding decoding.
3. Rubin GM, Managan CM, Dreher M, Kim E, Miller SW, Boone KN, Robie AA, Taylor AL, Branson K, Schretter CE, Otopalik AG. *Networks of sexually dimorphic neurons that regulate social behaviors in Drosophila.* Current Biology (September 2026). DOI 10.1016/j.cub.2026.08.013. CC BY 4.0. Preprint bioRxiv 10.1101/2025.10.21.683766. Covers the 48 pC1/pC1x cell types — relevant to the courtship-song decoding.

### 2.3 Prior male-fly datasets the male CNS builds on [H]

* Optic lobe (right, male): Nern A, Loesche F, Takemura S-y, … Berg S, Rubin GM, Reiser MB. *Connectome-driven neural inventory of a complete visual system.* Nature 641: 1225–1237 (2025). DOI 10.1038/s41586-025-08746-0. CC BY 4.0. neuPrint `optic-lobe:v1.1`. Cell Type Explorer https://reiserlab.github.io/male-drosophila-visual-system-connectome/ .
* MANC (male VNC): Takemura S-y, Hayworth KJ, Huang GB, … Jefferis GSXE, Berg S. *A Connectome of the Male Drosophila Ventral Nerve Cord.* eLife 13:RP97769 (2024). DOI 10.7554/eLife.97769. CC BY 4.0. — Marin EC, Morris BJ, Stürner T, … Jefferis GSXE. *Systematic annotation of a complete adult male Drosophila nerve cord connectome reveals principles of functional organisation.* eLife 13:RP97766 (2024). DOI 10.7554/eLife.97766. — Cheong HSJ, Eichler K, Stürner T, … Jefferis GSXE, Card GM. *Organization of circuits linking descending input to motor output in the Drosophila Male Adult Nerve Cord connectome.* eLife 13:RP96084 (VOR 2026-07-20). DOI 10.7554/eLife.96084. CC BY 4.0. (Cite these if you use `mancType`/`mancBodyid` or MANC-derived DN→MN circuit logic.)
* Hemibrain (female central brain): Scheffer LK et al. eLife 9:e57443 (2020). DOI 10.7554/eLife.57443. CC BY 4.0. (`hemibrainType` field.)
* neuPrint itself: Plaza SM, Clements J, Dolafi T, et al. *neuPrint: An open access tool for EM connectomics.* Front. Neuroinform. 16:896292 (2022). DOI 10.3389/fninf.2022.896292 [M — DOI seen in search URL, article details from memory].

### 2.4 Method precedents worth citing for the LIF approach [H]

* Shiu PK, Sterne GR, Spiller N, … Seeds AM, Scott K. *A Drosophila computational brain model reveals sensorimotor processing.* Nature 634: 210–219 (2024). DOI 10.1038/s41586-024-07763-9. CC BY 4.0. Code github.com/philshiu/Drosophila_brain_model (Brian2 LIF on FlyWire v630; NT-signed weights). Also its 2023 bioRxiv 10.1101/2023.05.02.539144.
* Lappalainen JK et al. *Connectome-constrained networks predict neural activity across the fly visual system.* Nature 634: 1132–1140 (2024). DOI 10.1038/s41586-024-07939-3 (flyvis, TuragaLab/flyvis).
* Vaxenburg R et al. *Whole-body physics simulation of fruit fly locomotion.* Nature 643: 1312–1320 (2025). DOI 10.1038/s41586-025-09029-4 (flybody, MuJoCo).
* Wang-Chen S et al. *NeuroMechFly v2.* Nat Methods (2024). DOI 10.1038/s41592-024-02497-y.

---

## 3. FlyWire female brain (FAFB v783) as the female counterpart

### 3.1 Papers [H via Crossref]
* Dorkenwald S, Matsliah A, Sterling AR, Schlegel P, Yu S-c, McKellar CE, Lin A, Costa M, Eichler K, Yin Y, … *Neuronal wiring diagram of an adult brain.* Nature 634: 124–138 (2024). DOI 10.1038/s41586-024-07558-y. CC BY 4.0. (294 authors.)
* Schlegel P, Yin Y, Bates AS, Dorkenwald S, Eichler K, … Bock DD, Jefferis GSXE. *Whole-brain annotation and multi-connectome cell typing of Drosophila.* Nature 634: 139–152 (2024). DOI 10.1038/s41586-024-07686-5. CC BY 4.0.
* Matsliah A, Yu S-c, Kruk K, … Schlegel P, Jefferis GSXE. *Neuronal parts list and wiring diagram for a visual system.* Nature 634: 166–180 (2024). DOI 10.1038/s41586-024-07981-1. (Required by flywire.ai when using visual-system types.)
* Female dimorphism annotations: Deutsch D, Matsliah A, Crown A, Wang K, Dorkenwald S, … Dickson BJ, Seung HS, Murthy M. *Sexually dimorphic neurons in the Drosophila whole-brain connectome.* bioRxiv 10.1101/2025.06.10.658788 (2025; CC BY-ND). Codex file `dsx_fru_types.csv.gz`.

### 3.2 License and access [H]
* **Official:** https://flywire.ai/guidelines — "FlyWire's public release data is made available under license **CC BY-NC 4.0**"; latest public release **v783 = October 2023 snapshot**. Terms of service (flywire.ai/tos) put user edits/annotations under CC BY-NC 4.0 too. Codex asks to cite Dorkenwald 2024 + Schlegel 2024 (+ Matsliah 2024 for visual types); Codex itself: DOI 10.13140/RG.2.2.35928.67844.
* **Conflict:** Zenodo record 10.5281/zenodo.10676866 "FlyWire Whole-brain Connectome Connectivity Data" v783.0 (FlyWire Consortium, 2024-06-02) is tagged **cc-by-4.0** and holds `proofread_connections_783.feather` (852 MB), `flywire_synapses_783.feather` (9.5 GB), `per_neuron_neuropil_count_{pre,post}_783.feather`, `proofread_root_ids_783.npy`; this is the deposit cited in Dorkenwald 2024's Data-availability section. Schlegel 2024 supplemental Zenodo 10.5281/zenodo.10877326 (skeletons `sk_lod1_783_healed_ds2.parquet` 5.4 GB, NBLAST scores) is also cc-by-4.0. The flyconnectome/flywire_annotations GitHub repo has **no LICENSE file** (GitHub license field null). Given the explicit NC statement on flywire.ai, the conservative reading is: **treat all FlyWire-derived data as CC BY-NC 4.0** unless the FlyWire team confirms the Zenodo CC BY tag is intentional (email flywire@princeton.edu).
* **Login:** Codex web apps (including the "Download Data" app) require Google sign-in ("any feature that performs computations on our servers requires you to be signed in"), but the underlying files are **public GCS objects — no auth needed** (HEAD 200 verified 2026-09-03):

| `https://storage.googleapis.com/flywire-data/codex/data/fafb/783/<file>` | Size | Header (verified) |
|---|---|---|
| `connections.csv.gz` | 50.3 MB | `pre_root_id,post_root_id,neuropil,syn_count,nt_type` (Buhmann synapses, ≥5 threshold per Codex convention; one row per pre/post/neuropil) |
| `connections_princeton.csv.gz` | 68.5 MB | same columns, Princeton synapse table |
| `connections_no_threshold.csv.gz` / `connections_princeton_no_threshold.csv.gz` | 212 / 276 MB | all weights |
| `neurons.csv.gz` | 1.7 MB | `root_id,group,nt_type,nt_type_score,da_avg,ser_avg,gaba_avg,glut_avg,ach_avg,oct_avg` |
| `classification.csv.gz` | 0.9 MB | `root_id,flow,super_class,class,sub_class,hemilineage,side,nerve` |
| `consolidated_cell_types.csv.gz` | 0.9 MB | `root_id,primary_type,additional_type(s)` |
| `coordinates.csv.gz` | 5.3 MB | `root_id,position,supervoxel_id` (position `[x y z]` in FAFB voxel units 4×4×40 nm [M]) |
| `cell_stats.csv.gz` | 2.5 MB | `root_id,length_nm,area_nm,size_nm` |
| `labels.csv.gz` | 4.8 MB | community labels with user names — **contains personal names; do not redistribute** |
| `column_assignment.csv.gz`, `visual_neuron_types.csv.gz`, `dsx_fru_types.csv.gz`, `synapse_coordinates.csv.gz` (317 MB), `fafb_v783_princeton_synapse_table.csv.gz` (2.7 GB), `nblast.csv.gz`, `neuropil_synapse_table.csv.gz` | — | also present (full listing in `probe` run) |

Codex's FAQ lists FAFB v783 as 139,255 neurons / 3,732,460 connections [M]. FlyWire root_ids are 64-bit (e.g. 720575940629970489) — need `long`, unlike male-CNS bodyIds.

### 3.3 Alternative female counterpart with a permissive license [H]
**BANC v888** (Brain And Nerve Cord, one adult female; Bates AS, Phelps JS, Kim M, Yang HH, Matsliah A, … Murthy M, Drugowitsch J, Wilson RI, Lee W-CA. *Distributed control circuits across a brain-and-cord connectome.* Nature 656: 957–970 (2026-06-08). DOI 10.1038/s41586-026-10735-w; CC BY 4.0 article). Harvard Dataverse doi:10.7910/DVN/7WTH1N (version 3, **license CC BY 4.0**, 379 files) includes `banc_888_edgelist_simple_v3.feather` (359 MB), `banc_888_edgelist_split_v3.feather` (940 MB), `banc_888_meta.feather` (57.6 MB), `banc_888_metrics.feather`. Also on Codex (login) and via `pip install banc`. BANC covers brain + VNC like the male CNS (≈188 k neurons, 199 M predicted synapses per the FlyWire blog [M]) — the natural female twin for a male-vs-female mode, and redistributable. Caveat: different EM modality/segmentation (GridTape-TEM), so synapse counts are not directly comparable to FIB-SEM counts without normalisation.

---

## 4. Prior art: connectomes in games, desktops and interactive sims [H unless noted]

| Project | What | Data | License | Notes |
|---|---|---|---|---|
| **DesktopFly** — github.com/DenisSergeevitch/desktop-fly (761 stars, 49 forks; fork kulikov0/desktop-vibe-fly) | 3-D fly on the macOS desktop driven by a live 1 kHz LIF sim of 668 neurons / ~19 k synapses; renders 23,210 soma positions | FlyWire FAFB v783 CSVs from the public GCS URLs (`classification, coordinates, connections, consolidated_cell_types`) | code MIT; `data/` CC BY-NC 4.0 with DATA_LICENSE.md citing Dorkenwald & Schlegel | Closest precedent for the mod's I/O mapping: cursor → looming on LC4/LPLC2; Giant Fiber → escape; DNp09 rate → walk speed; DNa01/DNa02 difference → steering; DNg11 → grooming; MDN → backward. Its weight sign convention: synapse counts signed by NT prediction. |
| **Embodied Drosophila** — github.com/erojasoficial-byte/fly-brain (32 stars) | 138,639-neuron whole-brain FlyWire v783 spiking sim (15.1 M synapses) in NeuroMechFly v2/MuJoCo body; vision/olfaction/gustation/flight | FlyWire v783 | MIT (code) | Whole-brain scale on GPU (PyTorch). |
| **Shiu et al. 2024 Drosophila_brain_model** | Brian2 LIF whole-brain model; taste → feeding MN predictions | FlyWire v630 | — | The scientific template for "activate GRNs → read out MN9 etc.". |
| **Eyewire** (eyewire.org, Seung lab, 2012–) and **FlyWire** proofreading game | Citizen-science tracing games (WebGL); ~350 k players, 6 k retinal neurons traced [M] | — | — | Games *producing* connectomes rather than running them. "Connectome Circuits" WebGL demo (alexnortn.github.io/gl_test) shows a retinal circuit. |
| **Neuromorphic Loihi 2 FlyWire sim** (arXiv 2508.16792), **flyvis**, **flybody**, **FlyBrainLab**, **Virtual Fly Brain** | Research platforms | mixed | — | Acknowledge as context; not games. |
| Minecraft / Unity / Godot connectome projects | **None found** for any fly connectome (searched GitHub topics `connectome`, `flywire`, web). | | | The mod appears to be first in Minecraft. |

---

## 5. Sizes, compression and Java loading

### 5.1 Measured on real data [H]
Sample: all 4,198,781 outgoing Neuron→Neuron edges (w>=1) of the 8,000 lowest bodyIds (158,223 distinct bodies), pulled via Cypher; bodyIds remapped to dense `int32` indices (as the mod would). Bytes per edge (gzip = `java.util.zip` deflate level 6/9; zstd via python-zstandard 0.25):

| Layout (w>=5 subset, 1,159,372 edges) | raw | gzip-6 | gzip-9 | zstd-3 | zstd-19 |
|---|---|---|---|---|---|
| int64 bodyId pairs + int16 w | 18.00 | 3.45 | 3.42 | 3.17 | 2.46 |
| row-major records `int32,int32,int16` | 10.00 | 4.06 | 4.08 | 3.95 | 3.74 |
| columnar `int32[] pre | int32[] post | int16[] w` | 10.00 | 3.21 | 3.17 | 3.08 | 2.38 |
| CSR `int32[] rowptr + int32[] post + int16[] w` | 6.55 | 3.20 | 3.16 | 3.08 | 2.36 |
| CSR, post delta-coded within row | 6.55 | 2.72 | 2.59 | 2.61 | 2.30 |
| **CSR, delta + LEB128 varint (post and w)** | **3.09** | **2.08** | 2.08 | 2.03 | 1.99 |

(w>=10 subset is ~0.15 B/edge worse per layout because deltas are larger.) Row-major interleaving compresses *worst*; sort by (pre, post) and store column-wise or CSR. zstd-19 beats gzip by only ~4 % on the varint layout, so **a native zstd dependency is not worth it** — use JDK `Inflater`/`GZIPInputStream`, or simply store the raw varint file in the jar and let the jar's own deflate do the work.

### 5.2 Projections [H arithmetic on measured ratios]

| Graph | Edges | raw `int32,int32,int16` | CSR `int32+int16` | CSR-delta-varint raw | in jar (deflate) |
|---|---|---|---|---|---|
| w>=5 (recommended default) | 6,287,789 | 62.9 MB | 41 MB | ~19.4 MB | **~13 MB** |
| w>=10 | 2,767,506 | 27.7 MB | 19.6 MB | ~10.3 MB | ~6.3 MB |
| w>=3 | 10,615,306 | 106 MB | 67 MB | ~32 MB | ~22 MB |
| w>=1 (everything) | 25,862,574 | 259 MB | 158 MB | ~78 MB | ~52 MB |

Node table for the whole 176 k set: `int32 bodyId, int16 typeId, uint8 superclass, uint8 class, uint8 nt, uint8 side, int32 x,y,z (8 nm voxels), int8 hex1, int8 hex2, uint8 flags(dimorphism, fruDsx)` ≈ 27 B × 176 k ≈ 4.8 MB raw (~2 MB deflated) plus a UTF-8 string table of 11.7 k type names (~150 KB) and optional `synonyms` for the ~300 named DN/feeding neurons.

### 5.3 Java-side loading and heap [H for JDK facts, M for MC defaults]
* Heap footprint of the w>=5 CSR as primitives: `int[] post` 25.2 MB + `short[] w` 12.6 MB + `int[] rowptr` 0.7 MB ≈ **38.5 MB**; with `float[] w` instead 51 MB; LIF state (`float v, gE, gI; int lastSpike` per neuron) ≈ 2.8 MB. Even the full w>=1 graph is ~158 MB. The vanilla launcher's default is `-Xmx2G` [M]; Loom `runClient` uses JVM ergonomics (¼ of RAM) unless `jvmArgs` set. So the data fits easily — **as long as it stays in primitive arrays**. A `HashMap<Long, List<Edge>>` for 6 M edges would cost ~500 MB+ of boxed objects and stall GC; avoid.
* Load path for a jar resource: `InputStream in = FruitFlyMod.class.getResourceAsStream("/data/<modid>/connectome/edges.bin")` → `byte[] b = in.readAllBytes()` (jar deflate is transparent) → decode varints in a tight loop into `int[]/short[]` (6.3 M varints ≈ tens of ms) or `ByteBuffer.wrap(b).order(LITTLE_ENDIAN).asIntBuffer().get(int[])` for fixed-width layouts. Prefer little-endian + explicit magic/version header (`"FFCN", u8 version, u32 nNodes, u32 nEdges, u16 threshold`). Avoid `DataInputStream.readInt()` per element on multi-million arrays (slow, big-endian); bulk `IntBuffer.get` or a manual varint loop is 10× faster.
* For optional user-downloaded larger files (full w>=1 graph, FlyWire/BANC), place them under `FabricLoader.getInstance().getConfigDir().resolve("<modid>/data")` and use `FileChannel.map(READ_ONLY, ...)` → `MappedByteBuffer` (off-heap, not counted against `-Xmx`, ≤ 2 GB per buffer) with `asIntBuffer()/asShortBuffer()` views; or read once into arrays. Gate large loads behind a config flag and load on a worker thread during world load, not on the render thread.
* Data-pack layout: resources under `assets/`/`data/` are also visible to `ResourceManager`; a plain classpath resource is simpler and avoids reload churn. Keep the jar < ~30 MB total (CurseForge/Modrinth have no hard blocker at this size, but downloads and Fabric's jar scanning are faster).

---

## 6. Redistribution decision, license summary, README block

### 6.1 What ships in the jar (all CC BY 4.0 → allowed) [H]
1. Derived edge list from `male-cns:v1.0` (w>=5, remapped indices, `weight` as int16, optionally `weightHP`), node table (bodyId, type, superclass/class/subclass, consensusNt, somaSide, somaLocation, hex1/hex2, dimorphism, fruDsx, entry/exit nerve, selected synonyms). Record provenance in a `PROVENANCE.md`: dataset `male-cns:v1.0`, uuid `4b2087c0fbe046bfaf0d60bc970e3e5d`, lastDatabaseEdit 2026-06-08, extraction date, threshold, exact Cypher/feather source, and "modified: thresholded, re-indexed, quantised".
2. Optionally BANC v888 derived subset (CC BY 4.0) for a female mode.
3. Type names, ROI names, and literature synonyms (facts; attribute anyway).

### 6.2 What is fetched by a user script (not bundled) [H]
* FlyWire FAFB v783 (`connections.csv.gz`, `classification.csv.gz`, `neurons.csv.gz`, `consolidated_cell_types.csv.gz`, `coordinates.csv.gz`, `dsx_fru_types.csv.gz`) — CC BY-NC 4.0; public GCS URLs, no login; the script writes a local `flywire-783.bin` and a `LICENSE-FlyWire.txt` (CC BY-NC 4.0). Never bundle `labels.csv.gz` (contains contributor names). If the project is ever monetised or distributed by a commercial party, FlyWire data must stay opt-in/user-fetched.
* Anything large (full w>=1 male graph 52 MB, syn-partners, skeletons/meshes) — CC BY 4.0, so bundling is legal but wasteful; fetch from `gs://flyem-male-cns` when needed.
* neuPrint tokens: never shipped; the build script runs anonymously (POST, no header) or reads `NEUPRINT_APPLICATION_CREDENTIALS` if the user has one.

### 6.3 License summary table

| Source | License | Attribution parties | Can bundle? |
|---|---|---|---|
| male-cns:v1.0 (neuPrint, GCS, Clio) | CC BY 4.0 | FlyEM Project Team (HHMI Janelia), Cambridge Drosophila Connectomics Group (Univ. Cambridge / MRC LMB), Google Research Connectomics | Yes (attribution + link + changes noted) |
| optic-lobe:v1.1, manc:v1.2.x, hemibrain (neuPrint) | CC BY 4.0 (same FlyEM policy) [M for optic-lobe/manc pages not re-checked today] | HHMI Janelia FlyEM (+ Cambridge for MANC annotations) | Yes |
| FlyWire FAFB v783 (Codex/GCS) | CC BY-NC 4.0 (official); Zenodo dump tagged CC BY 4.0 | FlyWire Consortium, Princeton University | Only for strictly non-commercial distribution; default: user-fetched |
| BANC v888 (Harvard Dataverse) | CC BY 4.0 | Lee lab (HMS), Wilson lab, Princeton, FlyWire | Yes |
| Shiu 2024 model code | see repo (not needed at runtime) | — | n/a |
| Reiser lab hex/eyemap code | GPL-3.0 | Reiser lab | Do not copy code into an MIT/LGPL mod; re-derive the trivial hex→p,q mapping (facts) |
| natverse/malecns, neuprint-python | GPL-3.0 / BSD-3 | — | Not bundled (Python/R only) |

### 6.4 README citation block (paste-ready)

```markdown
## Data sources and citations

This mod embeds a compact, thresholded derivative (connections with >= 5 synapses, re-indexed) of the
**male Drosophila central nervous system connectome, neuPrint dataset `male-cns:v1.0`**
(segmentation UUID 4b2087c0fbe046bfaf0d60bc970e3e5d, database edit 2026-06-08), produced by the
FlyEM Project Team at HHMI Janelia Research Campus, the Drosophila Connectomics Group (University of
Cambridge / MRC Laboratory of Molecular Biology) and Google Research. The dataset is licensed under
**CC BY 4.0** (https://creativecommons.org/licenses/by/4.0/). Source: https://male-cns.janelia.org/ and
https://www.janelia.org/project-team/flyem/male-cns-connectome . Modifications: synapse-count threshold,
integer re-indexing, quantised weights; see PROVENANCE.md.

If you use this mod or its data in research or teaching, please cite:

- Berg S, Beckett IR, Costa M, Schlegel P, Januszewski M, Marin EC, Nern A, Preibisch S, Qiu W,
  Takemura S, et al. **Sexual dimorphism in the complete Drosophila male central nervous system
  connectome.** *Cell* 189(18):5504–5526.e15 (2026). https://doi.org/10.1016/j.cell.2026.08.015
  (preprint: bioRxiv 2025, https://doi.org/10.1101/2025.10.09.680999)
- Nern A, Loesche F, Takemura S, et al. **Connectome-driven neural inventory of a complete visual
  system.** *Nature* 641:1225–1237 (2025). https://doi.org/10.1038/s41586-025-08746-0
- Takemura S, Hayworth KJ, Huang GB, et al. **A Connectome of the Male Drosophila Ventral Nerve Cord.**
  *eLife* 13:RP97769 (2024). https://doi.org/10.7554/eLife.97769
- Marin EC, Morris BJ, Stürner T, et al. **Systematic annotation of a complete adult male Drosophila
  nerve cord connectome reveals principles of functional organisation.** *eLife* 13:RP97766 (2024).
  https://doi.org/10.7554/eLife.97766
- Cheong HSJ, Eichler K, Stürner T, et al. **Organization of circuits linking descending input to motor
  output in the Drosophila Male Adult Nerve Cord connectome.** *eLife* 13:RP96084 (2026).
  https://doi.org/10.7554/eLife.96084
- Hoeller J, Zhao A, Nern A, Rogers EM, Romani S, Reiser MB. **The organization of visual pathways in
  the Drosophila brain.** *Cell* 189(18):5552–5570.e10 (2026). https://doi.org/10.1016/j.cell.2026.08.014
- Tastekin I, de Haan Vicente I, Beresford RJ, et al. **The complete gustatory connectome of adult
  Drosophila reveals how taste guides feeding, foraging, and social behavior.** *Cell*
  189(18):5527–5551.e5 (2026). https://doi.org/10.1016/j.cell.2026.08.016
- Rubin GM, Managan CM, Dreher M, et al. **Networks of sexually dimorphic neurons that regulate social
  behaviors in Drosophila.** *Current Biology* (2026). https://doi.org/10.1016/j.cub.2026.08.013
- Plaza SM, Clements J, Dolafi T, et al. **neuPrint: An open access tool for EM connectomics.**
  *Front. Neuroinform.* 16:896292 (2022). https://doi.org/10.3389/fninf.2022.896292

The spiking (leaky integrate-and-fire) approach follows:
- Shiu PK, Sterne GR, Spiller N, et al. **A Drosophila computational brain model reveals sensorimotor
  processing.** *Nature* 634:210–219 (2024). https://doi.org/10.1038/s41586-024-07763-9

### Optional female-brain comparison data (not bundled)
`scripts/fetch_flywire.py` downloads the FlyWire FAFB v783 public release (Princeton University and the
FlyWire Consortium), licensed **CC BY-NC 4.0** (https://flywire.ai/guidelines). Cite:
- Dorkenwald S, Matsliah A, Sterling AR, et al. **Neuronal wiring diagram of an adult brain.** *Nature*
  634:124–138 (2024). https://doi.org/10.1038/s41586-024-07558-y
- Schlegel P, Yin Y, Bates AS, et al. **Whole-brain annotation and multi-connectome cell typing of
  Drosophila.** *Nature* 634:139–152 (2024). https://doi.org/10.1038/s41586-024-07686-5
- Matsliah A, Yu S, Kruk K, et al. **Neuronal parts list and wiring diagram for a visual system.**
  *Nature* 634:166–180 (2024). https://doi.org/10.1038/s41586-024-07981-1
Alternatively `scripts/fetch_banc.py` fetches BANC v888 (female brain + nerve cord, **CC BY 4.0**,
https://doi.org/10.7910/DVN/7WTH1N): Bates AS, Phelps JS, Kim M, et al. **Distributed control circuits
across a brain-and-cord connectome.** *Nature* 656:957–970 (2026). https://doi.org/10.1038/s41586-026-10735-w

This project is not affiliated with or endorsed by HHMI, Janelia, Google, the University of Cambridge,
the MRC LMB, Princeton University or Mojang/Microsoft.
```

---

## 7. Open questions / things I could not verify

1. Corresponding authors and the exact "How to cite" wording on the Cell article page (cell.com and sciencedirect blocked automated access; Europe PMC has not indexed it yet). Crossref metadata (title, volume, pages, CC BY 4.0) is authoritative enough for a README.
2. Whether the FlyWire Zenodo `cc-by-4.0` tag is intentional (would make bundling FlyWire connectivity legal) — ask flywire@princeton.edu; until then treat as CC BY-NC 4.0.
3. Anterior/posterior sign of the eye-map `h` axis per eye (dorsal `v` is clear); derive from the column-pin geometry (`malecns-v1.0-optic-lobe-column-pins`) or Nern 2025 Extended Data before mapping raycasts to columns.
4. Exact anatomical meaning of the y axis (dorsal–ventral) in the male-CNS voxel frame — inferred from averages only.
5. Whether Janelia applies any undocumented per-IP throttling on neuPrint (none encountered across ~30 queries including two >30 s scans).
6. The `connectome-weights-...-traced-only.feather` column names (not opened; pyarrow missing locally) — expect `body_pre, body_post, weight` per FlyEM flat-connectome convention [L].
7. Codex's exact synapse threshold behind "6,242,118 connections" for MCNS v1.0 [M: assumed >=5].
