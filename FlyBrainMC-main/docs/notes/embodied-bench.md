# Embodied bench and brain-layer tests

Owner: brain-layer tests + headless embodied scenario bench. Files:

- `src/test/java/com/fruitfly/brain/{RetinaGeometryTest,SensoryEncodersTest,MotorDecoderTest,PopulationIndexTest,BrainRunnerTest}.java`
- `src/test/java/com/fruitfly/brain/AnnotatedConnectome.java` (test helper: like `SyntheticConnectome` but with
  class / subclass / superclass / nt / nerve annotations and a retina table, so class-based specs and the column
  lattice can be exercised without the 23 MB FLYB)
- `src/main/java/com/fruitfly/brain/tools/EmbodiedBench.java` (gradle task `embodiedBench`)

## 1. Unit tests (`./gradlew test`)

56 tests, 54 pass. The two failures are deliberate: they pin two bugs in `SensoryEncoders` (not owned by this task)
and will go green with the one-line fixes below.

| Class | Tests | What is pinned |
|---|---|---|
| `RetinaGeometryTest` | 11 | u = hex1+hex2 → elevation (dorsal high), w = hex1−hex2 → azimuth (posterior high); left eye = mirrored azimuth, dz sign; unit direction vectors; `nearestColumn`; `columnsWithin` against a brute-force reference; rays partition all columns exactly once and `column.ray` back-references; grouping with few rays; photoreceptor/L1–L3 attachment; bogus entries ignored; flip params; clamping outside the calibrated (u, w) range |
| `SensoryEncodersTest` | 10 | bilateral ORN gain by odor bearing (**fails, bug A**); symmetric drive without bearing; sugar GRN ≈ 120 Hz ± 25 % and off on release; looming object drives LC4/LPLC2 on the seeing eye only, static object does not; ORN adaptation depth over 5 s (measured ratio 0.85 vs analytic 0.84); JO auditory / wind-gravity / grooming subclasses; HS optic flow per eye (**fails, bug B**); lamina tonic ≈ 38 Hz in the dark, silenced by light, photoreceptors the other way round, NaN luminance keeps the last value; disabled modalities; `populationSizes()` |
| `MotorDecoderTest` | 11 | idle; DNp09 @50 Hz → forward rises → FORWARD, release → IDLE; DNa02/R → yaw > 0, /L → yaw < 0, bilateral ≈ 0; MDN gates forward (BACKWARD), resumes FORWARD; single forced DNp01 spike → `jump` + ESCAPE, 200 ms lockout, then FLYING until flightMinMs + dwell, then IDLE; escape overrides walking; hysteresis at 0.6 × threshold with a deterministic custom `MotorMap`; minimum dwell; MN9 → FEED, aDN1 → GROOM; `populationSizes()`; `setFlying` |
| `PopulationIndexTest` | 9 | exact types, `/L /R /M`, unknown → empty, unknown kind → `IllegalArgumentException`, `&` intersections (incl. the exact specs `SensoryEncoders` uses), side suffix after intersection, union dedup + sort, every annotation kind, `all`, caching by literal spec |
| `BrainRunnerTest` | 5 | `tickNow` advances `simTimeMs` by `tickMs`, snapshot tick/time; `submit` runs before integration (spike log offsets 0 and delaySteps); watched rates/counts (20 Hz for one spike); post-step hook sees counts before `endTick`; background thread ticks, pauses, resumes, stops |

### Bug A — bilateral ORN gain inverted (`SensoryEncoders.applyOlfaction`)

`WorldSenses.toHead` returns azimuth = atan2(right, forward): a positive `odorBearingDeg` is a source on the fly's
right. The encoder computes `gainL = 1 + g·sin(bearing)`, `gainR = 1 − g·sin(bearing)`, so the *left* antenna gets
1.3× for a right-hand source (measured: right 158.6 Hz, left 287.1 Hz for bearing +90°). The comment on that line
states the opposite intent. Fix: swap the signs (`gainL = 1 − …`, `gainR = 1 + …`). In the `apple` scenario the
odor at +40° drives DNa02/L (20–100 Hz) and yaw −0.5…−0.9, i.e. a turn *away* from the source; with the fix the
asymmetry flips.

### Bug B — `sides()` appends the side suffix only to the last comma term

`sides("HSE,HSN,HSS")` resolves `"HSE,HSN,HSS/L"`, where `/L` restricts only `HSS`; HSE and HSN come back bilateral.
Both `hs[L]` and `hs[R]` therefore contain all HS cells and the second `setRates` (right eye) overwrites the first:
a right turn (`yawRateDegPerS > 0`) never drives any HS cell, a left turn drives both hemispheres. Fix:

```java
private int[][] sides(String spec) {
    return new int[][]{pi.resolve(withSide(spec, "L")), pi.resolve(withSide(spec, "R"))};
}
private static String withSide(String spec, String side) {
    return Arrays.stream(spec.split(",")).map(String::trim).filter(s -> !s.isEmpty())
            .map(s -> s + "/" + side).collect(Collectors.joining(","));
}
```

`lc4`, `lplc2`, `lc11`, `lc18`, `lc10a`, `lc15` and `prefix:VS` are single terms and unaffected.

## 2. EmbodiedBench

```
gradlew embodiedBench                          # all six scenarios (quiet control first), ambient luminance 0.6
gradlew embodiedBench -Pscenario=apple         # one scenario
gradlew embodiedBench -Pscenario=all/dark      # same scenarios in darkness (isolates the non-visual modality)
gradlew embodiedBench -Pscenario=rain/lum=0.8,rain -Pms=1500 -Preport="subclass:wm;prefix:DNg02"
```

Loop per 50 ms tick, in the order FlyEntity/BrainRunner use: `SensoryEncoders.apply` → `LifNetwork.runMs(50)` →
`MotorDecoder.update` → `endTick`. Brain parameters mirror the in-game defaults (dt 0.5 ms, gain 0.65, threads auto,
Kenyon-cell postsynaptic gain 0.25 on `prefix:KC`, ORN rMax 120 Hz). A fresh `LifNetwork`/encoder/decoder is built
per scenario (fixed seed → each scenario reproducible on its own). The retina is always on, as in game; every column
sees `--lum` (default 0.6) unless the scene paints it. Prints per-tick active count, spikes, wall ms, key population
rates and the decoded command, then a summary table; exit status is always 0.

| Scenario | Script | Expected |
|---|---|---|
| `quiet` (1.5 s) | no stimulus; retina at the ambient luminance only | IDLE throughout, no wing-motor drive (control for the visual periphery) |
| `apple` (5 s) | DM1 1.0 / DM2 0.9 / VA2 0.6 ramp 0→1 over 2 s, bearing +40° (0° from 3 s); LgLG3 tarsal contact at 3 s; LB3b, LB3c, PhG1a-c at 3.5 s | FEED, MN9 30–90 Hz, G2N-1 (GNG232) / Fudog (DNg67) / Rounddown (DNge080) active |
| `loom` (1.2 s) | background 0.8; from 200 ms a 0.05-luminance disc at az 60°, el 0 growing 5→90° over 0.5 s plus the analytic `VisualObject` (expansion d(size)/dt) | ESCAPE via a DNp01 spike, TTMn |
| `rain` (2 s) | groomDust 0.6, moist 0.5, VP5 0.5 | GROOM, aDN1 (DNg62) ~200 Hz, aDN2 (DNge078) ~150 Hz |
| `wind` (2 s) | windLeft 1 for 1 s, then windRight 1 | ipsilateral `subclass:wind_gravity` drive; observe DNs |
| `song` (2 s) | song 1.0, soundHigh 0.5 | JO-A/B (`subclass:auditory`) ~275 Hz; observe GF / pIP10 / pC1 |

## 3. Results — ambient luminance 0.6 (in-game-like daylight), 2026-09-03

Machine: 32 logical cores, 8 worker threads, but other build jobs were running concurrently; wall times in this
run are 5–15× slower than the 25–60 ms/tick documented in ARCHITECTURE.md §6. They are contention, not a
regression: the dark run in section 4 (uncontended, identical `loom` work spike-for-spike) is the timing reference.
Behaviour is deterministic per scenario (fixed seed): the lit pass repeated later reproduced these results exactly.

| Scenario | Achieved | Observed |
|---|---|---|
| apple | **partial** | MN9 max 60 Hz (mean 24 Hz after 3.5 s), G2N-1 40, Fudog 40, Rounddown 70 Hz; feed channel 0.44 (> feedOn 0.2) — the feeding circuit does exactly what §6 validated — but the mode is FLYING from t = 150 ms for the rest of the run (`wingMotor` = 1.00), so FEED is never selected. DNp09 0 Hz throughout (no walking command from odor, as documented). yaw −0.5…−0.9 from DNa02/L only (bug A: the right-hand source drives the left antenna). ALPN saturate at 136 Hz from ORN ≥ 6 Hz (known); KCs 1.3 Hz. |
| loom | **yes** | IDLE for 250 ms, then JUMP + ESCAPE ×19: DNp01 270→370 Hz, DNp04 170→240, DNp02 110→180, DNp03 40→80, TTMn 60–90 Hz, LC4/R 75→176, LPLC2/R 49→193, LC4/L 0. Matches the §6 looming row. takeoff channel 1.00. |
| rain | **partial** | JO-F 125 Hz, BM_InOm 170, BM_Taste 148 → aDN1 80 Hz at t = 50 then 20–40 Hz, aDN2 0, DNg12 23→4 Hz, nagini (DNg15) 30→10; groomAntenna 0.31–0.34 (> groomOn 0.2) but mode BACKWARD (t = 50, MDN 0.28 — head-bristle drive of the moonwalker DNs, cf. Bidaye 2014) then FLYING ×39 (`wingMotor` 1.00). §6 measured aDN1 180–210 Hz with JO wind/gravity **and** JO-F at 150 Hz; groomDust alone drives only JO-F/BM. |
| wind | **yes** | JO wind L/R 164/0 Hz then 8/149 Hz; AMMC026 94–116 Hz (left phase) / 40–46 Hz (right phase); DNg106 ≤ 4 Hz; no DNa02, DNg60 or DNp09 response; the wind/gravity JOs drive aDN1/aDN2 (groomAntenna 0.81→1.00 — the documented JO→aDN grooming pathway) but the mode is FLYING ×27 / LANDING ×11 (land 0.23–0.43 from DNp07/DNp10). |
| song | **yes** | JO auditory 273–289 Hz → GNG301 110–150 Hz, DNp01 90 Hz sustained → JUMP every tick for ~1 s (ESCAPE ×22; the "loud sound → GF → takeoff" startle predicted in sensory-mapping §d.5), then FLYING ×18. pIP10 10–40 Hz, song channel 0.32 (> songOn 0.2) but masked; pC1 0.3 Hz; bwd 0.6–0.9 (MDN). |

Per-scenario cost (contended machine): apple 100 ticks / 5.17 M spikes / max 121 813 active / 88.5 s;
loom 24 / 0.82 M / 98 901 / 8.2 s; rain 40 / 2.33 M / 122 855 / 32.8 s; wind 40 / 1.39 M / 117 564 / 19.5 s;
song 40 / 1.66 M / 122 173 / 15.4 s. "Spikes" is dominated by the 5 193 photoreceptor Poisson generators
(~82 Hz at luminance 0.6 → ~21 k of the ~24 k spikes in the first tick; they are inhibitory and only shape the lamina).

### The cross-cutting finding: every strong stimulus puts the decoder into FLYING

In apple, rain and wind the decoder is in FLYING (or LANDING) from t = 100–150 ms to the end, because `wingMotor`
(`subclass:wm`, normalised at 30 Hz) saturates at 1.00 as soon as the stimulus arrives; the flight state machine then
owns the mode and masks every stationary behaviour (FEED, GROOM, SONG) although their channels do cross their
thresholds (feed 0.44, groomAntenna 0.34–1.00, song 0.32).

It is **not** the light. The `quiet` control (no stimulus, 1.5 s) at luminance 0, 0.6 and 0.8 stays IDLE on all
30 ticks with `wingMotor` ≤ 0.01, wing MNs ≤ 0.3 Hz, DN mean 0.05 Hz, VNC MN mean ~1 Hz, Mi1/T4 silent — the
retina alone (L1–L3 at 40 Hz in the dark, 12–20 Hz at 0.6–0.8; photoreceptors 82–118 Hz) does not reach the
motor system, exactly as §6 says. The dark run (section 4) then shows the same FLYING lock without any light. What
recruits the wing motor neurons is the central-brain activity that odor (ALPN saturating at 136 Hz), head
mechanosensation (JO-F + BM at 120–170 Hz) or antennal wind (JO wind/gravity 160 Hz) set off. In game a fly will
therefore take off the moment it smells an apple or gets rained on, and never feed or groom; this is a brain/decoder
finding, not tuned here — see §5.

| Control | Achieved | Observed |
|---|---|---|
| quiet/dark | yes | IDLE ×30; L1/R 40 Hz, Mi1/R 0; wm MNs 0.30 Hz, wingMotor 0.01; DN mean 0.05 Hz; 40–45 k active, 7–9 k spikes/tick |
| quiet (0.6) | yes | IDLE ×30; L1/R 13, L2 11, L3 20 Hz, Mi1/R 0; wm MNs 0.30 Hz, wingMotor 0.01; DN mean 0.05 Hz; 40–43 k active, 23–25 k spikes/tick (photoreceptor generators) |
| quiet/lum=0.8 | yes | IDLE ×30; L1/R 12 Hz, Mi1/R 0; wm MNs 0.30 Hz; 39–43 k active, 29–34 k spikes/tick |

## 4. Results — darkness (`-Pscenario=all/dark`)

Same scripts with every retina column at luminance 0 (lamina tonic 0.5 mV/ms → L1–L3 ~40 Hz, medulla silent per §6);
the loom scene paints its own 0.8 background and is therefore identical to the lit run. This run was not contended:
the wall times are representative.

| Scenario | Achieved | Observed |
|---|---|---|
| apple/dark | **partial** | Baseline t = 50 ms: 40 k active, 7 k spikes (lamina only), IDLE. From ORN ≥ 8 Hz (t = 100) ALPN 73→135 Hz, 104–119 k active; FLYING from t = 150 (`wingMotor` 1.00) for the rest of the run. Sugar contact: G2N-1 20–50, Fudog 20–40, Rounddown 20–60, MN9 10–50 Hz, feed channel 0.47. DNa02/L 40–100 Hz, DNa02/R 0–20 → yaw −0.5…−0.9 (bug A). DNp09 0. |
| loom/dark | **yes** | identical to the lit run (816 346 spikes both): DNp01 370, DNp04 240, TTMn 90 Hz, ESCAPE ×19. |
| rain/dark | **partial** | JO-F 123, BM_InOm 168, BM_Taste 152 Hz → aDN1 80 Hz (t = 50) then 20–40, aDN2 0, DNg12 26→4, nagini 30→0; groomAntenna 0.32; BACKWARD (t = 50, MDN) then FLYING ×39 (`wingMotor` 0.99). |
| wind/dark | **yes** | JO wind L/R 164/0 then 8/149 Hz; AMMC026 98–132 / 80 Hz; DNg106 ≤ 2.5 Hz; groomAntenna 0.81–0.96; FLYING ×16 / LANDING ×22 (land 0.40–0.50 from DNp07/DNp10); DNa02/L 20–60 Hz in the right-wind phase → yaw −0.83 at 2 s. |
| song/dark | **yes** | JO auditory 261–276 Hz → GNG301 120–140, DNp01 50–90 Hz **sustained for the whole 2 s** → JUMP on every tick, ESCAPE ×40; pIP10 0, pC1 0, song channel 0 (in the lit run pIP10 reached 40 Hz — the song channel needs the visually driven background activity). |

Cost (uncontended, 8 threads): apple 100 ticks / 3.70 M spikes / max 119 588 active / 9.5 s (0.53× real time);
loom 24 / 0.82 M / 98 901 / 1.2 s (1.00×); rain 40 / 1.70 M / 120 495 / 4.1 s (0.49×); wind 40 / 1.55 M / 122 781 /
4.0 s (0.51×); song 40 / 0.56 M / 113 519 / 2.3 s (0.87×). I.e. 50–100 ms per 50 ms tick at 115–123 k active
neurons — in line with the §6 worst case (62 ms at 100 k active) once the extra activity is accounted for; the
runner would fall behind real time by ~2× in the apple/rain/wind conditions.

**Conclusion of the dark run:** the FLYING lock is not a lighting artefact. Any strong sensory drive (odor,
head bristles + JO-F, antennal wind) recruits enough `subclass:wm` wing motor neurons to saturate `wingMotor`
within 100–150 ms, and the decoder's flight state machine then owns the mode. Only the two scenarios whose expected
behaviour sits *above* flight in the priority ladder (loom → ESCAPE, song → GF startle) are decoded as expected.

## 5. Open issues / options for the owners of the brain layer

1. Bug A and bug B in `SensoryEncoders` (section 1); both are one-liners and both are pinned by a failing test.
2. `wingMotor` (`subclass:wm` @ 30 Hz) saturating under ambient light hijacks the mode arbitration. Candidates,
   in order of intrusiveness: gate `flightOn` on `flightPower` (DNg02) only and treat `wingMotor` as a readout;
   raise the wm normalisation (the §6 flight MN rates during genuine flight are not documented yet); or require
   `takeoff`/`jump` before `flying` can be set by wing activity. Whichever is chosen, `MotorDecoderTest` covers the
   escape → flight → landing sequence and hysteresis, so the change can be made safely.
3. The photoreceptor Poisson generators (5 193 cells at 82–118 Hz under daylight) are 21–30 k of the spikes per
   tick and ~40 k of the active set even in a quiet brain, but they are histaminergic and stop at the lamina; they
   cost wall time without influencing behaviour. If tick time matters, driving the lamina current directly (as the
   encoder already does for columns without reconstructed photoreceptors) would remove them.
4. The `song` scenario's JO auditory → DNp01 startle (50–90 Hz sustained, JUMP on every tick for 1–2 s) means any
   loud sound in game makes the fly jump repeatedly; the auditory rMax (300 Hz) or the JO-A/B split may want
   revisiting.
5. Odor still produces no DNp09 forward command (as in §6) — the reflex layer stays necessary for odor taxis. The
   DNa02 asymmetry it does produce points the wrong way until bug A is fixed.
6. Uncontended cost is 50–100 ms per 50 ms tick at 115–123 k active neurons (0.5–1.0× real time), i.e. the
   `BrainRunner` will run apple/rain/wind conditions at about half speed on this machine with 8 threads.
