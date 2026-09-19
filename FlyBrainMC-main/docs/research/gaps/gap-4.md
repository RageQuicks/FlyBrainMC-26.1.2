# Gap 4 — Per-column viewing directions for the male-cns:v1.0 medulla hex grid, and the anterior/posterior sign of the eye map

Date: 2026-09-03. All numbers below were computed in this session from primary data (GCS bucket, neuPrint, Reiser-lab and Zhao-lab GitHub repositories, Europe PMC full text) unless marked otherwise. Confidence tags: [HIGH] verified from data or exact quotes; [MED] inferred with one assumption; [LOW] not verifiable here.

## 0. Bottom line

* **Deliverable:** `gap-4_column_directions.csv` (same directory) — 1,772 rows = 892 ME(R) + 880 ME(L) columns of male-cns:v1.0, each with `az_deg_ipsi`, `az_deg_left_positive`, `el_deg`, the unit vector (`dir_front, dir_left, dir_up`), source flag (844 R / 838 L columns are direct matches to a µCT-measured ommatidium of Zhao et al. 2025; 48 R / 42 L peripheral columns are local-linear extrapolations), pale/yellow/DRA tag with evidence, R7/R8 bodyIds, the medulla pin end-points, and the lamina-cartridge centroid where available. Transformation summary in `gap-4_transform.json`; the per-lens Zhao right-eye table in `gap-4_zhao2025_right_eye_uCT_20240701.csv`; scripts `gap-4_decode_pins.py`, `gap-4_build_table.py`.
* **A/P sign [HIGH]:** in the male-cns hex grid, define `h = hex1 − hex2` and `v = hex1 + hex2 − 37`. **+h = anterior (frontal) visual field; +v = dorsal.** Four independent lines of evidence agree (Section 4): (i) Zhao 2025's statement plus their µCT grid, (ii) chiasm-free lamina geometry in the male-cns volume itself, (iii) the LC4→DNp02 (anterior) / LC4→DNp11 (posterior) synaptic gradient of Dombrovski 2023 reproduced in male-cns, (iv) DRA (R7d/R8d) columns lie at maximal v.
* **The naive "pin direction" method must not be used [HIGH]:** the medulla pins point the *opposite* way along A/P (corr(h, pin-direction azimuth) = +0.94 vs corr(h, true azimuth) = −0.92, because of the first optic chiasm), and the neuropil shells in this specimen are far flatter than the eye (lamina normals span only ~28° × 66°, medulla pin directions span az 16–74°, el −47..+38°, versus the real ~165° × 160° field). Angular scale therefore had to come from optics (Zhao's µCT eye map), aligned to the male grid by equator + central meridian + eye-boundary overlap.
* **Cross-checks vs Zhao 2025 (Nature, PMC12488493) [HIGH]:** equatorial-band azimuth extent −10.4° (contralateral) … 159°; 31 right-eye columns look across the midline (binocular overlap ≈ 7–10°, only one column beyond −10°); posterior blind spot ≈ 56–78°; nearest-neighbour inter-ommatidial angle mean 5.02° / median 4.79°, 4.11° in the frontal-equatorial region; elevation −80°…+87°; equator row (v = 0) at +1.7° elevation; DRA columns from el 8° (posterior rim) to 87° (pole). Zhao's stated values: "<10° into the opposite hemisphere in front to around 155° behind", "directly above to −70°", "binocular overlap zone of less than 20° and a posterior blind spot of around 50°", ΔΦ smallest frontally.

## 1. Sources decoded

### 1.1 Neuroglancer precomputed annotations (column pins) [HIGH]
* Public, no auth: `https://storage.googleapis.com/flyem-male-cns/v1.0/malecns-v1.0-optic-lobe-column-pins/info` (4,308 B). `@type: neuroglancer_annotations_v1`, `annotation_type: line`, dimensions 8 nm isotropic, `lower_bound [5618, 13361, 22097]`, `upper_bound [90421, 52250, 43340]`, description "Optic lobe column center pin lines". Properties in order: `depth int16`, `roi uint8` (enum of 97 ROI names; 47 = ME(L), 48 = ME(R), 43 = LO(L), 44 = LO(R), 45 = LOP(L), 46 = LOP(R)), `hex1 int8`, `hex2 int8`, `layer int8`. Relationships: `by_rel_neuropil`, `by_rel_ME(L)_column_segment`, `by_rel_LO(L)_…`, `by_rel_LOP(L)_…`, `by_rel_ME(R)_…`, `by_rel_LO(R)_…`, `by_rel_LOP(R)_…`. Spatial index: one level, `grid_shape [1,1,1]`, `chunk_size [84803, 38889, 21243]`, so **one chunk holds every annotation**.
* Files: `by_id/0.shard` 30.0 MB, `by_spatial_level_0/0.shard` 6.6 MB, six `by_rel_*/0.shard` (~6.6 MB each). Sharding: `neuroglancer_uint64_sharded_v1`, murmurhash3_x86_128, preshift_bits 10, shard_bits 0, minishard_bits 0 (spatial/rel) or 9 (by_id), gzip data and minishard index.
* Decoding (script `gap-4_decode_pins.py`): shard index = 2·2^minishard_bits uint64 (start/end of each minishard index, relative to the end of the shard index); minishard index (gzip) = uint64 triplets [Δid…, Δoffset…, size…] with offsets cumulative and each chunk's offset relative to the end of the previous chunk; spatial chunk 0 = `uint64 count`, then `count` fixed-size records (line: 2×3 float32 = 24 B, then properties sorted by size: int16 depth, then uint8 roi, int8 hex1, hex2, layer → 30 B, padded to 32 B), then `count` uint64 ids.
* Result: **428,190 line segments**: ME(R) 107,040 = 892 columns × 120 segments; ME(L) 105,600 = 880 × 120; LO(R) 65,625; LO(L) 65,025; LOP(R) 42,800; LOP(L) 42,100. hex1 ∈ 1..36, hex2 ∈ 1..39 (both sides). depth 0 is the **distal** end (layer −1 then 1, lateral, toward lamina); depth 119 is proximal (layer 10). ME(R) mean depth-0 point (14,640, 34,952, 30,851) vs depth-119 (19,866, 33,522, 35,937) voxels.
* Identity with Reiser's data: the decoded ME(R) pins are numerically identical (to the voxel) to `results/eyemap/ME_pindata.csv` of `reiserlab/male-drosophila-visual-system-connectome-code` (892 columns × 121 points, e.g. hex1=1, hex2=7, bin_depth 0 → (25358.4, 49154.7, 30777.5) vs decoded segment start (25358, 49154, 30777)); so optic-lobe:v1.x and male-cns:v1.0 share the voxel frame. [HIGH]

### 1.2 neuPrint (male-cns:v1.0, anonymous Cypher) [HIGH]
* Neuron `roiInfo` carries per-column keys `ME_{L|R}_col_{hex1:02d}_{hex2:02d}` and `LO_{L|R}_col_…`, `LOP_…`, plus `ME_{L|R}_layer_NN` (verified on R7/R8, Dm8, Tm5, LC4). This is what makes photoreceptor→column and LC4→column assignment possible although photoreceptors have no `assignedOlHex*`.
* `assignedOlHex1/2` present on 15 columnar types: L5 1773, C3 1770, L1 1767, L2 1767, Tm1 1767, T1 1764, Mi1 1762, Mi9 1760, Tm2 1758, Mi4 1758, Tm9 1743, Tm20 1732, L3 892, C2 874, Tm4 833 (both sides). `Meta.voxelSize = [8,8,8] nm`.
* Photoreceptor subtypes (all sides): R7p 332, R7y 482, R7d 82, R8p 330, R8y 481, R8d 76, R7_unclear 404, R8_unclear 442, R7R8_unclear 85; R1-R6 3,377 (incomplete: mean 4.2 distinct R1-R6 inputs per L1 on the right, so the classical 7/8-photoreceptor equator test is not reproducible here — see 3.3).

### 1.3 Reiser lab repository [HIGH]
`https://github.com/reiserlab/male-drosophila-visual-system-connectome-code` (Nern et al. 2025 code). Used: `docs/coordinate-systems.md`, `docs/assets/column_coord.png`, `params/Pale-Yellow_column-assignment.xlsx`, `params/pin_creation_parameters.xlsx` (ME: 121 pin points, n_neighbors 260, cell types L1, L2, L3, L5, Mi1, Mi4, Mi9, C2, C3, Tm1, Tm2, Tm9, Tm20, T1), `results/eyemap/ME_pindata.csv`, `src/eyemap/create_column_pins.py`. No direction table exists in the repo (confirmed).

### 1.4 Zhao et al. 2025 [HIGH]
"Eye structure shapes neuron function in Drosophila motion vision", Nature 2025, doi 10.1038/s41586-025-09276-5, PMC12488493 (full text via Europe PMC `…/PMC12488493/fullTextXML`). Code+data: `https://github.com/reiserlab/eyemap_T4` — used `data/microCT/20240701.RData` (lens/cone positions of one female head, 1,709 ommatidia, Hungarian cone→lens match `i_match`, unit viewing vectors `ucl_rot_sm`, equator markers `ind_Up`/`ind_Down`, canonical frame), `data/microCT/20240701_nb.RData` (hex grid `ind_xy_right` [row, p, q] from `reghex()`, neighbour angles `nb_dist_ucl_right`), `proc_uCT.R`, `proc_eyemap.R`, `eyemap_func.R` (read with the pure-Python `rdata` package installed into `scratchpad/pylib`; R is not installed). Their canonical frame (proc_uCT.R): z = +normal of the plane through the equatorial ("UP"/"DOWN" chirality) cones, x = front (3rd PC of both eyes' lens+cone cloud, orthogonalised to z, pointing toward the lens centroid), y = z × x = **left**; azimuth = atan2(y, x) so **positive azimuth = fly's left** (`sph2elaz`: "left side is +y in sph, same left side is positive for azim"), elevation = 90° − θ.

### 1.5 Other papers quoted
* Nern et al. 2025, Nature, PMC12119369 (Europe PMC XML): "p and q denote hexagonal coordinates; h and v indicate the horizontal and vertical axes of the eye"; "The darker hexagons correspond to locations along the equator of the eye (determined by counting photoreceptors in the corresponding lamina cartridges)"; "We assigned neurons to 892 hexagonal coordinates in the medulla"; pins have 121 (ME), 76 (LO), 51 (LOP) points; "We placed non-DRA R7 cells into two groups on the basis of their relative number of synaptic connections to two pairs of cell types: Tm5a plus Dm8a and Tm5b plus Dm8b"; "We identified DRA photoreceptors, R7d and R8d, on the basis of the unusual layer pattern of R8d cells … and the distinct synaptic connectivity of R7d and R8d"; "about 460 of the around 770 non-edge, non-DRA columns … house R7y cells".
* Dombrovski et al. 2023, Nature, PMC9849133: "A frontward looming stimulus activates anterior LC4 neurons that provide relatively more drive to DNp02, which produces backward body movements … For a stimulus looming from behind, posterior LC4 neurons become more active and drive DNp11 to generate forward postural shifts and a forward-directed takeoff." Also "DNp02-silenced flies … showed significant impairment in their ability to take off backwards in response to frontal stimuli"; "The A–P axis of the visual space is mapped onto the anatomical lateral–medial axis of the lobula neuropil"; "A–P gradients were not seen in LC4 connectivity onto the GF and DNp04".

## 2. Hex-coordinate conventions of the male-cns grid [HIGH]

From `docs/coordinate-systems.md` + `column_coord.png` (reproduced facts): "hex1 → q, hex2 → p; origin [hex1, hex2] = [18, 19] ↔ [p, q] = [0, 0]"; "The H axis is somewhat parallel to the perceived horizon"; v is "the virtual hex3". In the figure the p axis points up-left, q up-right, v up, h right; "eq" markers run horizontally through the origin, "16 h-rows" above/below and "17 v-rows" left/right.

Derived integer axes used everywhere in this report and in the CSV:

| symbol | definition | meaning |
|---|---|---|
| `p` | hex2 − 19 | Reiser p |
| `q` | hex1 − 18 | Reiser q |
| `h` | hex1 − hex2 (= q − p − 1 … i.e. q − p shifted so that origin has h = −1) | horizontal lattice coordinate; **+h = anterior** |
| `v` | hex1 + hex2 − 37 (= p + q) | vertical lattice coordinate; **+v = dorsal** |

Lattice facts: `h + v = 2·hex1 − 37` is always odd; vertical nearest neighbours differ by Δv = 2 (Δh = 0); the other four nearest neighbours are (Δh, Δv) = (±1, ±1); points on the same v row are Δh = 2 apart (√3 lattice spacings). Extents: h ∈ [−19, 16], v ∈ [−30, 35] (R); h ∈ [−19, 15], v ∈ [−30, 34] (L). Column counts per h are symmetric about h ≈ −1.5 (32–33 columns for h = −4…3); per v maximal (18) at v = 0…2.

Equator: Reiser's "eq" markers form a zig-zag row of touching hexagons at **v = 0 (odd h) and v = −1 (even h)**, through the origin (18,19) [HIGH, from zoomed `column_coord.png`]. The 7/8-photoreceptor cartridges in that figure straddle this zig-zag (above it posteriorly, below it anteriorly), which is why Nern/Zhao both note an inherent ±1-row ambiguity of the equator row.

Central meridian: Zhao's definition "+v divides approximately equal halves" gives **h = −1** for male-cns (median h of the 892 R columns = −1.0, mean −1.04; identical for L) — i.e. exactly Reiser's origin column. Zhao's own µCT right eye has median hZ = 0.

## 3. Anatomical frame of the male-cns volume [HIGH]

Soma landmarks (neuPrint `somaLocation`, 8-nm voxels): right-side somas mean x = 24,711 (n = 70,708), left-side mean x = 72,423 (n = 70,536) → **x increases toward the fly's left; midline x ≈ 48,567**. Kenyon cells (KCg-m: y ≈ 9.9–10.6k), MBON01/02 (y ≈ 7–9k), EPG (y ≈ 11–12k) are at low y; GNG somas y ≈ 45k; VNC T1/T2/T3 y ≈ 55–59k → **y increases dorsal→ventral**. Antennal-lobe PNs (DA1_lPN z ≈ 13.7–14.9k), VA1d_adPN (z ≈ 12.5k), DNa02 soma (z ≈ 14–16k) are at low z; KCs z ≈ 33–35k; VNC T1 z ≈ 75.6k, T2 94.2k, T3 120.4k, A1..A10 119–132k → **z increases anterior→posterior**. So `front = −z_vol, left = +x_vol, up = −y_vol` (right-handed). The brain's own A/P depth (z 10k–45k ≈ 280 µm) and D/V height (y 7k–45k ≈ 300 µm) match a frontal-view convention. The pitch between this brain frame and Zhao's eye-equator frame is unknown (assumed ≤ ~10°) [MED]; the direction table is expressed in Zhao's frame (Section 6).

## 4. Anterior/posterior and dorsal/ventral signs — four independent lines of evidence

### 4.1 Zhao 2025 (text and data) [HIGH]
Exact quote: "note that the +h axis points towards the posterior medulla, corresponding to anterior on the eye because of the optic chiasm". Their definition: "The +h axis is the line from the centre of two right neighbours to two left neighbours, and the +v axis is the line from bottom neighbour to top." In their µCT right-eye grid (`hZ = q − p`, `vZ = p + q` from `reghex`, 852 lenses), linear regression gives ∂az_left/∂hZ = +5.02°/unit (az_left is negative on the right eye, so +hZ moves the axis toward the front), ∂el/∂vZ = +2.23°/unit (4.5° per vertical neighbour); corr(hZ, az) = 0.89, corr(vZ, el) = 0.99. Left eye (`lefteye=TRUE` grid): ∂az_left/∂hZ = −4.95, ∂el/∂vZ = +2.24 (mirrored as expected).

### 4.2 Chiasm-free geometry inside male-cns [HIGH]
Per column, compared the medulla pin distal point with (a) lamina-cortex somas of L1/L2/L3/L5 carrying `assignedOlHex` (261 R / 201 L columns) and (b) the centroid of each hex-assigned L1/L2/L3's synapses inside LA(R)/LA(L) (4,426 neurons queried; 680 R / 468 L columns with ≥30 synapses; lamina cartridges sit directly under their ommatidia, no inversion):

| side | corr(h, pin distal z) | corr(h, lamina z) | corr(v, pin y) | corr(v, lamina y) |
|---|---|---|---|---|
| R | **+0.97** | **−0.63** | −0.99 | −1.00 |
| L | **+0.96** | **−0.73** | −0.99 | −0.99 |

Because z_vol = posterior, +h moves medulla pins posteriorly but lamina cartridges anteriorly → **+h = anterior on the eye, posterior in the medulla** (the first optic chiasm), exactly Zhao's statement. Because y_vol = ventral, **+v = dorsal** in both neuropils (no D/V inversion). Regression slopes (R, lamina centroid positions): ∂(x,y,z)/∂h = (+427, +30, −277) vox, ∂/∂v = (0, −584, −53) vox.

### 4.3 LC4 → DNp02 / DNp11 gradient [HIGH]
LC4 dendritic centroids in LO column ROIs (`LO_{L|R}_col_*` post-synapse-weighted mean hex; 55 R + 71 L LC4s) versus summed ConnectsTo weights to both copies of each DN:

| DN | side | r(weight, h1−h2 = h) | r(weight, az_ipsi from final table) | r(weight, el) |
|---|---|---|---|---|
| DNp02 | R | +0.85 | **−0.85** (Spearman −0.86) | −0.04 |
| DNp02 | L | +0.76 | **−0.78** | −0.11 |
| DNp11 | R | −0.79 | **+0.85** | +0.37 |
| DNp11 | L | −0.71 | **+0.77** | +0.42 |
| DNp04 | R/L | 0.28/0.11 | −0.25/−0.10 | −0.62/−0.67 |
| DNp01 (GF) | R/L | — | 0.15/−0.11 | 0.26/0.12 |

Total LC4→DN synapses (R+L): DNp04 11,597; DNp01 6,362; DNp02 4,209; DNp11 3,666; DNp03 2,507; DNp06 1,152. Dombrovski's result (anterior LC4 → DNp02, posterior → DNp11, no A–P gradient for GF/DNp04) is reproduced only if **+h = anterior**; with the table's azimuths, frontal LC4s drive DNp02 (backward takeoff) and posterior LC4s drive DNp11 (forward takeoff). LC4 receptive-field centres span az 5°–144°, el −63°…+69°.

### 4.4 Dorsal rim area [HIGH]
R7d/R8d (DRA photoreceptors; R7d→Dm-DRA1 4,201 syn, R7d→MeTu2a 2,414, R8d→Dm-DRA2 2,715) occupy columns with v = 9…34 forming an arch that peaks at (h = −1, v = 34) and descends to v ≈ 9–11 at |h| ≈ 14–18 — i.e. the dorsal rim of the eye, confirming +v = dorsal and matching Zhao's eye shape (dorsal margin slopes down anteriorly and posteriorly).

## 5. Why pins (or any internal neuropil geometry) cannot give the viewing directions [HIGH]

1. Chiasm: in the final table corr(h, az_ipsi) = −0.92 (R) but corr(h, pin-direction az_ipsi) = **+0.94** — the pin direction (distal − proximal, mapped to the fly frame) is inverted along A/P; along D/V it is not (corr(v, el) = 0.99, corr(v, pin el) = 0.98).
2. Scale: a least-squares sphere through the lamina-cartridge centroids has radius 252 µm (R; rms residual 4.8 µm) and 161 µm (L); the sphere-normal "directions" span only az 27.6–55.3°, el −34.6…+31.5° (R) — i.e. the lamina in this specimen is a nearly flat sheet ~300 µm (D/V) × 120 µm (A/P). The medulla pin directions span az 16–74°, el −47…+38° (R). The real eye covers ~165° × 160°. Nearest-neighbour angles from the lamina normals are 1.6° (R) / 2.7° (L) instead of ~5°. Conclusion: the data confirm hex-lattice *topology* and the axis *signs*, but the angular *scale* must come from measured optics (Zhao's µCT).

## 6. Construction of the direction table

1. **Zhao right eye:** `dir = ucl_rot_sm[argsort(i_match)][~ind_left_lens]` (852 unit vectors in the canonical frame; every lens has a hex coordinate from `ind_xy_right`). Equator markers on the right: 9 UP cones on row vZ = −1 (odd hZ −5…11), 10 DOWN cones on row vZ = −2 (even hZ −6…12) → the chirality equator is the zig-zag {vZ = −1, −2}; UP mean el +5.4°, DOWN +4.1° in Zhao's frame. Central meridian hZ = 0 (median). Nearest-neighbour angle mean 4.98°, median 4.78° (vertical 4.93°, p 5.03°, q 4.99°).
2. **Lattice alignment (translation only; both grids are the same hex lattice type):** our (h, v) = (hZ + ch, vZ + cv) with ch + cv odd (parity: ours h+v odd, theirs even). Candidates were scored by overlap between Zhao's 852 ommatidia and the 746 male-cns R columns that actually receive R7/R8 input (the male grid also contains edge columns without photoreceptors):

| (ch, cv) | matched | Zhao-only | omm-only | row-extent mismatch | equator relation |
|---|---|---|---|---|---|
| **(−1, +2)** | **744** | 108 | **2** | **168** | our v=0 ↔ Zhao DOWN row (vZ=−2); meridian h=−1 ↔ hZ=0 |
| (−2, +1) | 740 | 112 | 6 | 184 | equator zig-zags coincide; meridian off by one lattice column |
| (−1, 0) | 735 | 117 | 11 | 194 | our v=0 two rows above Zhao's equator |
| (0, +1) | 720 | 132 | 26 | 264 | equator zig-zags coincide; meridian off the other way |

   Chosen: **h = hZ − 1, v = vZ + 2** (equivalently hZ = h + 1, vZ = v − 2). Systematic uncertainty from this choice: ±1 lattice row ≈ ±2.4° elevation, ±1 half-column ≈ ±5° azimuth [MED]. Our equator row v = 0 then sits at mean el +1.7° (zig-zag {0, −1}: +1.1°), Zhao's chirality equator at ≈ +4.7°.
3. **Male columns outside Zhao's grid (48 R):** local weighted linear fit of the three direction components over Zhao points within |Δh| ≤ 4, |Δv| ≤ 4 (physical lattice coordinates x = h·√3/2, y = v/2; weights 1/(1+d²); ≥6 points; renormalised). Flag `source = extrapolated_local_linear`; they are the extreme ventral row (v = −30…−26), the posterior edge (h = −19…−16) and a few dorsal-rim/frontal edge columns; expect ±5° error there.
4. **ME(L):** mirror of ME(R) at the same (hex1, hex2): `dir_left → −dir_left`. Justified because male-cns L hex assignments mirror R (identical DRA column lists on both sides; lamina geometry gives the same signs) and Zhao's own left eye agrees with the mirrored right eye to median Δaz −1.3° (IQR −2.2…−0.5), Δel +1.6° (IQR 1.0…2.2) over 836 lenses. Zhao's left-eye extents: az_ipsi up to 164°, el −74.4…+86.9.
5. **Frame:** Zhao's canonical eye frame (Section 1.4). `az_deg_left_positive = atan2(dir_left, dir_front)`; `az_deg_ipsi = −az_left` for R, `+az_left` for L; `el_deg = asin(dir_up)`. Near the pole (el > ~80°) azimuth is ill-conditioned (e.g. column (35,36): az −142.7°, el 86.4°) — use the unit vector, not the angles, for such columns.

## 7. Results and cross-checks (right eye unless noted)

| quantity | this table | Zhao 2025 (female µCT / text) |
|---|---|---|
| azimuth extent, band |el| < 30° | −10.4° (h=15, v=−14, extrapolated) … 159.0° (h=−19, v=8, extrapolated); matched-only −7.9…151.8° | "<10° into the opposite hemisphere in front to around 155° behind" |
| columns looking contralateral (az < 0), |el| < 30° | 31 (11 beyond −5°, 1 beyond −10°) → binocular overlap ≈ 7–10° (2× the 3rd percentile, −3.3°) | "binocular overlap zone of less than 20°" |
| posterior blind spot | 360° − 2×(141…152°) ≈ 56–78° | "around 50°" |
| elevation extent | −80.1° … +86.6° (matched −75.8…+86.6) | "from directly above to −70°" |
| nearest-neighbour angle ΔΦ | mean 5.02°, median 4.79°, min 1.4° (near pole), max 11.3° (extrapolated edge); frontal-equatorial (|az|<40°, |el|<20°) 4.11° | mean 4.98°, median 4.78°; "ΔΦ is smallest at the front near the equator" |
| equator row v = 0 | mean el +1.7°; v = ±1 rows 0.4°/3.5° | chirality equator ≈ +4.7° (UP 5.4°, DOWN 4.1°) |
| central meridian h = −1 | mean az_ipsi 52.4° | (by definition roughly the vertical midline of the column set) |
| DRA columns (42 R / 41 L) | el 7.9° (posterior rim, az 152°) → 86.6° (pole) → 21° (anterior rim, az −2°); quartiles 45.8/61.6/75.3° | "Magenta dots, dorsal rim area (DRA) columns" along the dorsal margin |
| azimuth vs h (R, mean per h) | h=−19: 158°, −15: 132°, −10: 114°, −5: 88°, −1: 52°, 0: 52°, 5: 27°, 10: 10°, 13: 0.6°, 16: −8.5° | ~5°/unit hZ |
| elevation vs v (R, mean per v) | v=−30: −75°, −20: −42°, −10: −18°, 0: +1.7°, 10: +21°, 20: +44°, 30: +69°, 34: +83° | ~2.2°/unit vZ |

Example rows: origin (18,19): az_ipsi 57.5°, el 3.8°, pale. (14,32): h −18, v 9: az 151.8°, el 7.9°, DRA (posterior end of the rim). (31,17): h 14, v 11: az −2.2°, el 21.2°, DRA (anterior end). (1,7): h −6, v −29: az 103°, el −75°, extrapolated.

## 8. Pale / yellow / DRA tagging [HIGH]

Connectivity basis measured in male-cns (sum of ConnectsTo weights): R7p→Dm8b 13,590 vs Dm8a 2,917; R7p→Tm5b 2,950 vs Tm5a 319; R7y→Dm8a 24,186 vs Dm8b 768; R7y→Tm5a 6,507 vs Tm5b 114; R7d→Dm-DRA1 4,201, MeTu2a 2,414, Cm-DRA 323; R8d→Dm-DRA2 2,715; R8y→Tm5c 6,955 (R8p→Tm5c 1,317). So in male-cns nomenclature **Dm8a/Tm5a = yellow partners, Dm8b/Tm5b = pale partners**, Dm-DRA1/Dm-DRA2/Cm-DRA/MeTu2a = DRA network (50/33/7/70 cells).

Per column: the top `ME_*_col_*` ROI of every typed R7/R8 (R7 place ≥93% of their synapses in one column; R8 100%) gives the primary tag; columns without typed R7/R8 fall back to the home column (top ROI) of Dm8a/Tm5a (`yellow?`) or Dm8b/Tm5b (`pale?`). Counts:

| side | DRA | pale | yellow | yellow? | pale? | unclear |
|---|---|---|---|---|---|---|
| R | 42 | 159 | 252 | 74 | 192 | 173 |
| L | 41 | 173 | 231 | 80 | 170 | 185 |

Comparison with Reiser's right-eye `Pale-Yellow_column-assignment.xlsx` (893 rows: unclear 382, yellow 355, pale 114, DRA 42): DRA 42/42 agree; our R7-based `yellow` 252/252 agree; `pale` 101 agree + 58 are Reiser-unclear; `yellow?` 64 agree, 9 unclear, 1 pale; `pale?` 9 pale / 172 unclear / **11 Reiser-yellow** → treat `pale?` as unclear (Nern: Dm8b/Tm5b are not reliable pale markers; only R7-typed cells and the yellow markers are). Of 892 R columns, 746 receive any R7/R8 (incl. unclear) input; the remainder are edge columns presumably without ommatidia.

DRA column lists (hex1, hex2): R: (14,32) (16,33) (18,34) (20,35) (22,36) (23,36) (24,37) (25,37) (26,37) (26,38) (27,38) (28,38) (29,38) (30,37) (30,38) (31,37) (31,38) (32,37) (33,36) (33,37) (34,34) (34,35) (34,36) (35,35) (35,36) (35,34) (35,33) (36,33) (35,32) (36,32) (35,31) (36,31) (35,30) (35,29) (35,28) (35,27) (35,26) (34,25) (35,25) (34,24) (34,23) (31,17). L: (14,32) (16,33) (18,34) (20,35) (22,36) (23,36) (24,37) (25,37) (26,37) (26,38) (27,38) (27,39) (28,37) (28,38) (29,37) (29,38) (30,37) (30,38) (31,36) (31,37) (32,36) (32,37) (33,35) (33,36) (33,37) (34,33) (34,34) (34,35) (34,36) (35,36) (35,34) (35,33) (35,32) (35,31) (35,30) (36,31) (35,29) (36,29) (35,28) (35,27) (36,28). ((31,17) on R is isolated at h=14, v=11 — a single R7d/R8d pair; possibly a mis-typed cell.)

## 9. How to use this in the mod

* Ray per column: `d = dir_front·F + dir_left·L + dir_up·U` where F is the fly's horizontal forward unit vector in world space, U = (0,1,0), **L = U × F** (for Minecraft: facing +z/south, left is +x/east). Equivalent: yaw offset = `az_deg_left_positive` (positive = turn left / counter-clockwise from above), pitch = `el_deg`.
* Acceptance: sample each column with a cone of half-width ≈ ΔΦ ≈ 4.5–5° (Δρ ≈ ΔΦ in Drosophila); 1,772 rays per tick is cheap.
* Binocular zone: the ~31 right (and mirror-image left) columns with `az_deg_ipsi < 0` in the equatorial band look across the midline; the posterior ~50–70° behind the fly is unseen by either eye.
* Escape logic check: a looming object at az_ipsi 5–40° stimulates columns feeding anterior LC4s → DNp02 → backward takeoff (away); at 100–145° → DNp11 → forward takeoff. Optomotor: T4b PDs run along +h (Zhao: "PD vectors of T4b and T4d neurons are aligned mainly with the +h and −v axes"), i.e. front-to-back… note Zhao's +h points anterior on the eye, so back-to-front motion is the T4b PD? Zhao Fig. legend: "The PD vectors of T4b and T4d neurons are aligned mainly with the +h and −v axes" — take T4b PD = +h = **toward the front** (back-to-front on the ipsilateral eye), T4d PD = −v = downward. [HIGH from the quote; interpretation MED]
* Pale/yellow: use `col_type ∈ {pale, yellow, DRA}` (and `yellow?`) for chromatic input; treat `pale?`/`unclear` as unknown.
* Head-frame caveat: elevations are relative to Zhao's eye-equator plane (≈ the horizontal plane of a level head). If the mob's head pitches, rotate the rays with it.

## 10. Open questions / caveats
1. Exact equator row: ±1 lattice row (≈2.4° el) inherent in both Zhao's and Nern's definitions; our choice puts v = 0 at +1.7°.
2. Central meridian: Zhao hZ = 0 vs our h = −1 — the lattice parity forces a ±1 half-column (≈5° az) ambiguity; overlap scoring favoured the chosen offset (2 vs 6 unmatched photoreceptor-bearing columns).
3. The µCT eye is a **female** (Zhao: "we imaged whole heads of female flies with approximately the same number of ommatidia to match the EM dataset"); the male-cns male eye has 892 ME columns (746 with detected R7/R8) vs 852 µCT ommatidia; the ~46 extra male columns at the edges are extrapolated (or are edge columns without ommatidia). Sex differences in eye shape are not quantified here [LOW].
4. Pitch between the male-cns brain frame (Section 3) and the eye-equator frame is unknown; only matters if volume coordinates of other neurons are combined with the direction table.
5. The classical equator test (7–8 R1–R6 axons per cartridge) could not be reproduced in male-cns because only ~3.4k of ~9.6k R1–R6 are reconstructed (mean 4.2 inputs per L1); the equator row rests on Nern/Reiser's documented markers.
6. Near-pole columns (el > 80°): azimuth undefined; use unit vectors.

## 11. Reproduction
Scripts in `…/scratchpad/gap4/`: `decode.py` (shard decoder), `q*.py` (neuPrint queries: soma landmarks, R7/R8 column ROIs, LC4 columns and DN weights, lamina synapse centroids, marker-cell home columns), `geom1.py`/`geom2.py`/`model.py` (pin geometry, chiasm test, lamina sphere fit), `zhao_map.py` (Zhao RData extraction), `align.py`/`align2.py` (lattice offset search), `final.py` (table), `checks2.py` (FOV bands, left-eye mirror check). Pure-Python deps installed only into `…/scratchpad/pylib` (`rdata 0.11.2`, `xarray 2024.7.0`); system Python 3.9 has numpy/pandas/scipy 1.13.1. No files were written inside `C:\Users\drini\OneDrive\Documents\fal-dev`.

## 12. Sources
* GCS: `https://storage.googleapis.com/flyem-male-cns/v1.0/malecns-v1.0-optic-lobe-column-pins/{info,by_spatial_level_0/0.shard,by_id/0.shard,by_rel_ME(R)_column_segment/0.shard,…}`; scene `https://storage.googleapis.com/flyem-male-cns/v1.0/male-cns-v1.0.json` (layers `ME(R)-columns-v8`, `ME(L)-columns-v3`, `optic-column-pins`, `soma-points`, `micro-ct` with a 4×3 transform).
* neuPrint male-cns:v1.0 custom endpoint `https://neuprint.janelia.org/api/custom/custom`; companion site `https://male-cns.janelia.org/`.
* Reiser code repo `https://github.com/reiserlab/male-drosophila-visual-system-connectome-code` (files listed in 1.3).
* Zhao et al. 2025: `https://pmc.ncbi.nlm.nih.gov/articles/PMC12488493/`; code/data `https://github.com/reiserlab/eyemap_T4`; Data availability: "The male brain optic-lobe dataset can be accessed at https://neuprint.janelia.org/?dataset=optic-lobe:v1.1"; Code availability: "https://github.com/reiserlab/eyemap_T4".
* Nern et al. 2025: PMC12119369 (doi 10.1038/s41586-025-08746-0). Dombrovski et al. 2023: PMC9849133 (doi 10.1038/s41586-022-05562-8).
