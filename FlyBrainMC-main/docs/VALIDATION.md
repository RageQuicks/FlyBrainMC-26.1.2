# Validation of the connectome brain (headless benches)

This document records what the simulated male-CNS network does when its real sensory neurons are driven the way the
game drives them, how those numbers were obtained, and why the free parameters have the values they have. Every table
below was produced by the commands shown, on the machine described in section 0, on 2026-09-03. Re-run them to check a
change; the Poisson generators are seeded (`LifConfig.seed = 20260903`) so results are close to reproducible.

Reference facts the results are compared against come from `docs/research/lif-model.md` (Shiu et al. 2024),
`docs/research/dn-behavior.md` and `docs/research/sensory-mapping.md`.

## 0. Set-up

| Item | Value |
|---|---|
| Machine | Intel Core i9-14900KF (32 logical CPUs), 64 GB RAM, Windows 11 |
| JDK | Temurin 25.0.1 (code targets Java 21); `gradlew` 9.1, fabric-loom 1.12 |
| Connectome | `malecns-v1.0.flyb.gz`: 176,422 neurons, 6,287,749 connections (>= 5 synapses), 90.3 M synapses |
| LIF parameters | dt **0.5 ms**, tau_m 20 ms, tau_syn 5 ms, v_rest = v_reset -52 mV, threshold -45 mV, refractory 2.2 ms, delay 1.8 ms, 0.275 mV/synapse x gain **0.65**, no adaptation, no input normalisation (exactly `LifConfig` defaults plus `dtMs=0.5`, which is what `config/fruitfly.json` uses in game) |
| Threads | `threads = 0` -> auto = min(8, cores - 2) = **8** worker threads |
| Tick | 50 ms brain tick = 1 game tick; each run 600 ms (12 ticks) unless stated |
| Reporting | `brainBench` prints the **mean firing rate of the population over the last 50 ms tick**. For a type with 2 cells (one per hemisphere: MN9, DNp01, DNg62 ...) one spike in the tick is 10 Hz, so those columns are quantised to 10 Hz steps. The `active` column is the size of the active set (neurons away from rest or with pending input); `spikes` is whole-brain spikes in the tick; `wall` is compute time per tick in ms. |
| In-game differences | The game additionally scales Kenyon-cell inputs by 0.25 (`kenyonCellInputGain`); `brainBench` only does so when `-PpostGain=prefix:KC:0.25` is passed (the odor run below). Sensory rates in game come from `SensoryEncoders` (Hill functions of stimulus strength); the benches inject fixed rates. |

Run any bench with `./gradlew <task> ... --console=plain -q`. A first Gradle start takes ~30 s; each bench then loads the
connectome in ~0.2 s and simulates 600 ms in 0.3-0.9 s.

## 1. Silent brain

```
./gradlew brainBench -Pms=600 "-Preport=superclass:descending_neuron;superclass:vnc_motor;MN9" "-Pcfg=dtMs=0.5"
```

0 spikes, 0 active neurons for the whole run. The model has no spontaneous activity (as in Shiu et al. 2024: baseline 0 Hz),
so every spike in the following runs is caused by the stimulus.

## 2. Feeding: sugar GRNs -> MN9 (proboscis extension)

The game's `/fruitfly feed` demo and `TasteTable.SUGAR` drive labellar sugar GRNs `LB3b`/`LB3c`, pharyngeal `PhG1a-c` and
tarsal `LgLG3`. Reference: sugar GRNs -> G2N-1 (`GNG232`) -> Fudog (`DNg67`), Rounddown (`DNge080`) -> `MN9`; Shiu et al.
chose their synaptic weight so that 100 Hz sugar input gives ~80 % of maximal MN9 firing.

```
./gradlew brainBench -Pms=600 "-Pstim=LB3b:120;LB3c:120;PhG1a:100;PhG1b:100;PhG1c:100;LgLG3:80" "-Preport=MN9;GNG232;DNg67;DNge080;GNG087;superclass:cb_motor;class:Kenyon_Cell" "-Pcfg=dtMs=0.5"
```

Stimulated: LB3b 11, LB3c 23, PhG1a 2, PhG1b 2, PhG1c 4, LgLG3 162 neurons.

| t (ms) | active | spikes | wall ms | MN9 | G2N-1 (GNG232) | Fudog (DNg67) | Rounddown (DNge080) | Scapula (GNG087) | cb_motor (107) | KCs (4,064) |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 50 | 15,617 | 1,824 | 48.2 | 30 | 30 | 10 | 60 | 0 | 5.2 | 0 |
| 100 | 56,763 | 2,744 | 22.9 | 80 | 30 | 20 | 120 | 0 | 11.6 | 0 |
| 150 | 49,213 | 2,720 | 24.5 | 90 | 30 | 40 | 100 | 13.3 | 9.3 | 0 |
| 200 | 62,598 | 3,199 | 26.2 | 70 | 40 | 20 | 110 | 6.7 | 9.7 | 0 |
| 250 | 79,393 | 4,206 | 28.1 | 70 | 40 | 20 | 90 | 0 | 7.1 | 0 |
| 300 | 90,968 | 6,147 | 35.9 | 60 | 20 | 20 | 100 | 6.7 | 14.8 | 0 |
| 350 | 92,982 | 6,728 | 37.6 | 30 | 20 | 20 | 50 | 6.7 | 23.9 | 0 |
| 400 | 89,484 | 6,510 | 36.6 | 70 | 20 | 20 | 80 | 20 | 22.6 | 0 |
| 450 | 87,717 | 6,425 | 35.6 | 60 | 60 | 20 | 110 | 20 | 24.7 | 0 |
| 500 | 91,292 | 6,950 | 35.1 | 40 | 40 | 20 | 70 | 20 | 28.0 | 0 |
| 550 | 92,174 | 6,209 | 35.2 | 40 | 20 | 30 | 90 | 0 | 24.5 | 0 |
| 600 | 90,942 | 6,295 | 35.3 | 40 | 40 | 20 | 80 | 26.7 | 22.8 | 0 |

600 ms simulated in 0.40 s wall (1.49x real time), 59,957 spikes.

Result: **MN9 fires at 30-90 Hz in bursts** (median ~60 Hz) from the first tick; G2N-1 20-60 Hz, Fudog 10-40 Hz, Rounddown
50-120 Hz. Scapula (the bitter interneuron) is weakly co-activated (0-27 Hz). The whole proboscis motor pool (`cb_motor`)
climbs to ~25 Hz mean. Kenyon cells stay silent. This reproduces the sugar -> proboscis-extension pathway of the paper and is
the calibration target for the gain (section 9). In game, `MotorDecoder` enters `FEED` mode (proboscis extension) once
MN9 exceeds ~20 Hz: the `feed` channel is 0.6 x MN9 / 60 Hz plus small DN terms, and the mode threshold is 0.2.

## 3. Bitter rejection and suppression of feeding

Bitter labellar GRNs `LB1a-d` (`TasteTable.BITTER`, `/fruitfly bitter`). Reference: bitter -> Scapula (`GNG087`) ->
inhibition of Roundup/Rounddown -> MN9 silenced.

```
./gradlew brainBench -Pms=600 "-Pstim=LB1a:120;LB1b:120;LB1c:120;LB1d:120" "-Preport=GNG087;MN9;GNG232" "-Pcfg=dtMs=0.5"
```

Stimulated: LB1a 11, LB1b 6, LB1c 16, LB1d 5 neurons.

| t (ms) | active | spikes | wall ms | Scapula (GNG087, 3 cells) | MN9 | G2N-1 |
|---:|---:|---:|---:|---:|---:|---:|
| 50 | 26,801 | 1,620 | 47.1 | 273 | 0 | 0 |
| 100 | 76,003 | 13,977 | 34.2 | 293 | 0 | 0 |
| 150-600 | 100,111-104,402 | 33,520-36,574 | 62.3-66.5 | 280-307 | 0 | 0 |

600 ms in 0.73 s (0.83x real time), 373,866 spikes. Bitter is the most expensive stimulus tested: ~100 k active neurons and
~36 k spikes per tick (the bitter pathway recruits a large SEZ/GNG population).

Bitter together with the full sugar stimulus of section 2:

```
./gradlew brainBench -Pms=600 "-Pstim=LB3b:120;LB3c:120;PhG1a:100;PhG1b:100;PhG1c:100;LgLG3:80;LB1a:120;LB1b:120;LB1c:120;LB1d:120" "-Preport=MN9;GNG232;GNG087;DNg67" "-Pcfg=dtMs=0.5"
```

| t (ms) | active | spikes | MN9 | G2N-1 (GNG232) | Scapula (GNG087) | Fudog (DNg67) |
|---:|---:|---:|---:|---:|---:|---:|
| 50 | 25,009 | 2,567 | 0 | 50 | 253 | 20 |
| 100 | 79,619 | 14,734 | 0 | 10 | 280 | 20 |
| 150-600 | 102,184-104,928 | 35,062-38,174 | **0** | 10-40 | 247-320 | 20-60 |

Result: with bitter present **MN9 is silent for the entire run** (vs 30-90 Hz with sugar alone), while the sugar
second-order neuron G2N-1 (10-50 Hz) and Fudog (20-60 Hz) keep firing. The suppression therefore acts downstream of the
second-order layer, at the premotor level, as in the paper. In game this is why a fly standing on cake will not extend its
proboscis while it also tastes a spider eye.

## 4. Looming escape: LC4 + LPLC2 -> giant fibre -> TTMn

Reference: DNp01 (giant fibre) input is almost purely LC4 (6,362 synapses) + LPLC2 (4,862); a single GF spike is sufficient
for a short-mode takeoff; TTMn is the jump-muscle motor neuron. `/fruitfly loom` drives the right eye's LC4 and LPLC2.

```
./gradlew brainBench -Pms=600 "-Pstim=LC4/R:150;LPLC2/R:150" "-Preport=DNp01;DNp04;DNp02;DNp11;TTMn;class:Kenyon_Cell" "-Pcfg=dtMs=0.5"
```

Stimulated: LC4/R 55, LPLC2/R 91 neurons (the right-side cells of the 126 LC4 and 185 LPLC2).

| t (ms) | active | spikes | wall ms | DNp01 (GF) | DNp04 | DNp02 | DNp11 | TTMn | KCs |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 50 | 55,287 | 5,878 | 63.9 | 350 | 220 | 150 | 140 | 60 | 0 |
| 100 | 67,763 | 6,726 | 30.2 | 330 | 210 | 160 | 140 | 70 | 0 |
| 150-600 | 68,069-74,056 | 7,107-7,646 | 31.0-33.6 | 330-360 | 230-240 | 160-170 | 140-160 | 60-80 | 0 |

600 ms in 0.42 s (1.43x real time), 86,433 spikes.

Result: the **giant fibre fires at 330-370 Hz** (near the 2.2 ms refractory ceiling of ~450 Hz), the takeoff DNs DNp04
(210-240 Hz), DNp02 (150-170 Hz) and DNp11 (140-160 Hz) follow, and the jump muscle motor neuron **TTMn fires at 60-80 Hz**.
Kenyon cells stay silent. The first GF spike arrives inside the first tick; in game the `jump` event channel switches the
decoder to `ESCAPE` on that spike (see the vision bench in section 6 for the end-to-end latency).

## 5. Grooming: Johnston's organ -> aDN1 / aDN2

Reference: JO-C/E (wind/gravity) and JO-F (grooming) neurons -> aDN1 (`DNg62`) and aDN2 (`DNge078`) antennal-grooming
descending neurons (Hampel 2015; reproduced by Shiu 2024). `/fruitfly groom` and rain/dust in game drive these
subclasses.

```
./gradlew brainBench -Pms=600 "-Pstim=subclass:wind_gravity:150;subclass:grooming:150" "-Preport=DNg62;DNge078;DNg60;class:Kenyon_Cell" "-Pcfg=dtMs=0.5"
```

Stimulated: wind_gravity 475, grooming 65 neurons.

| t (ms) | active | spikes | wall ms | aDN1 (DNg62) | aDN2 (DNge078) | bluebell (DNg60) | KCs |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 50 | 81,778 | 8,392 | 68.7 | 120 | 80 | 0 | 0 |
| 100 | 92,321 | 11,004 | 45.8 | 190 | 140 | 0 | 0 |
| 150-600 | 86,998-90,508 | 10,891-13,727 | 40.1-45.3 | 180-210 | 130-160 | 0 | 0 |

600 ms in 0.53 s (1.12x real time), 133,674 spikes.

Result: **aDN1 ~200 Hz, aDN2 ~150 Hz**, sustained; halting DN bluebell silent; Kenyon cells silent. In game the
`groomAntenna` channel (0.5 x DNg62/50 + 0.5 x DNge078/50, clipped) saturates and the decoder enters `GROOM` with
`getGroomState() = 1` (antennal).

## 6. Vision through the retina encoder (`visionBench`)

`visionBench` paints synthetic luminance onto the 1,769 retina columns (877 left + 892 right; 1,407 have mapped
photoreceptors, 1,767 have an L1), runs `SensoryEncoders` (vision only) and the real `MotorDecoder`. Lamina cells receive
the tonic drive of 0.5 mV/ms that the histaminergic (inhibitory) photoreceptors suppress in light.

### 6.1 Flash: dark 0-200 ms, bright 200-500 ms, dark 500-800 ms

```
./gradlew visionBench -Pscene=flash -Pms=800
```

| Phase | L1/R | L2/R | L3/R | Mi1, Tm1-3, T4, T5 | LC4, LPLC2, LC11 | DNs |
|---|---:|---:|---:|---:|---:|---:|
| dark (t = 150-200, 600-800 ms) | 39-40 Hz | 25-28 Hz | 31-33 Hz | 0 | 0 | 0 |
| light (t = 300-500 ms) | 9-10 Hz | 9-10 Hz | 16 Hz | 0 | 0 | 0 |
| transitions (t = 250, 550 ms) | 17 -> 28 Hz | 10 -> 19 Hz | 17 -> 31 Hz | 0 | 0 | 0 |

Whole-brain spikes per tick: ~8 k in darkness (lamina), ~41 k in light (the 3,324 mapped R1-R6 fire Poisson at up to
150 Hz). Compute 16-55 ms per tick.

Result: **lamina monopolar cells fire tonically at ~35 Hz in darkness and drop to ~10 Hz in light**, i.e. the sign-inverting
photoreceptor -> L1/L2/L3 synapse behaves as intended (real L1-L3 hyperpolarise to light). The medulla columnar cells
(Mi1, Tm1, Tm2, Tm3) and the motion detectors T4/T5 do **not** fire in any phase: the uniform LIF with count-based weights
does not carry the lamina signal into the medulla. This is the reason the game paints looming, small-object and optic-flow
signals directly onto LC4/LPLC2/LC11/LC18/LC10a/HS/VS (section 6.2) and is listed as a limitation in the README.

### 6.2 Loom: dark disc at azimuth +60 deg expanding from 5 to 90 deg between 200 and 700 ms

```
./gradlew visionBench -Pscene=loom -Pms=800
```

| t (ms) | active | spikes | LC4/R | LPLC2/R | DNp01 (GF) | DNp04 | decoder |
|---:|---:|---:|---:|---:|---:|---:|---|
| 50-250 (background 0.8) | 32.6-33.8 k | 32.1-33.2 k | 0 | 0 | 0 | 0 | IDLE |
| 300 | 83,123 | 35,183 | 76 | 49 | **270** | 170 | **ESCAPE, JUMP** |
| 350 | 92,224 | 36,793 | 130 | 62 | 310 | 210 | ESCAPE |
| 400-750 | 94-101 k | 32-38 k | 132-180 | 88-187 | 300-370 | 210-250 | ESCAPE |
| 800 (disc gone) | 91,307 | 32,375 | 0 | 0 | 50 | 20 | ESCAPE (lockout) |

Result: the object channel drives LC4 (expansion velocity) and LPLC2 (angular size) with the Ache-2019 two-term tuning;
the **giant fibre reaches 270 Hz in the first tick after the loom starts and the decoder emits the JUMP event within
100 ms of loom onset** (the frame rendered at 250 ms is the first with non-zero expansion). DNp01 falls to 50 Hz within one
tick of the disc disappearing. Lamina rates (L1 8-17 Hz, L2 6-13 Hz, L3 13-21 Hz) are the "light" regime of section 6.1 for
the 0.8 background.

## 7. Olfaction (documented limitation)

```
./gradlew brainBench -Pms=600 "-Pstim=prefix:ORN_DM1:40;prefix:ORN_VA2:40" "-Preport=DM1_lPN;class:ALPN;class:Kenyon_Cell;superclass:descending_neuron" "-Pcfg=dtMs=0.5" "-PpostGain=prefix:KC:0.25"
```

Stimulated: ORN_DM1 74, ORN_VA2 83 neurons at only 40 Hz (an apple-scale stimulus in game), Kenyon-cell input gain 0.25 as in
game.

| t (ms) | active | spikes | DM1_lPN (2) | ALPN mean (686) | KCs (4,064) | all DNs mean (1,314) |
|---:|---:|---:|---:|---:|---:|---:|
| 50 | 88,443 | 15,503 | 370 | 93 | 0.3 | 5.2 |
| 100-600 | 98,419-102,069 | 26,630-27,898 | 400-430 | 133-135 | 0.9-1.1 | 8.4-9.4 |

Result: the **DM1 projection neurons saturate at ~410 Hz** and the whole antennal-lobe PN population runs at ~134 Hz mean
from a 40 Hz input to two glomeruli: the antennal lobe has far more convergent synapses per PN than a uniform LIF can
handle without gain control. With the 0.25 input gain the Kenyon cells stay near 1 Hz (sparse, as they should be); without
it they fire at ~20 Hz (`docs/ARCHITECTURE.md` section 6). Descending neurons show a diffuse ~9 Hz mean with no clear
steering signal, which is why odor-directed walking in game is handled by the hand-built reflex layer (`FlyBody`) when the
locomotor DN channels are quiet.

## 8. Performance

Single brain, 8 worker threads, dt 0.5 ms, measured above:

| Condition | active neurons | spikes / 50 ms tick | compute per tick | real-time factor |
|---|---:|---:|---:|---:|
| silent | 0 | 0 | < 0.1 ms | > 1000x |
| sugar feeding | 15-93 k | 1.8-7 k | 23-37 ms | 1.49x |
| looming (right eye) | 55-74 k | 5.9-7.6 k | 30-34 ms | 1.43x |
| grooming | 82-92 k | 8-14 k | 40-46 ms | 1.12x |
| bitter (+/- sugar) | 100-105 k | 34-38 k | 62-67 ms | 0.82-0.83x |
| odor (2 glomeruli, 40 Hz) | 88-102 k | 15-28 k | 60-75 ms | 0.80x |
| vision, flash / loom | 33-101 k | 7-42 k | 16-55 ms | ~1x |

The first tick of each run is slower (48-106 ms) because of JIT warm-up. A real-time factor below 1 does not skip neural
time: `BrainRunner` keeps stepping full 50 ms ticks and reports `realTimeFactor` (visible in `/fruitfly stats` and the HUD)
so the body can be slowed instead. With `maxBrains = 4` (default) four flies share the machine's cores; `brainThreads`
can be lowered per brain on smaller CPUs, and `brainMsPerTick = 25` halves the load ("bullet time"). `dtMs = 0.1`
(Brian2's default) is ~4x slower and changes no fixed point because the integrator is exact per step.

## 9. Gain sweep: why 0.65

Shiu et al. calibrated their single free parameter, 0.275 mV per synapse, on the FlyWire v630 graph (52.8 M synapses
over 127 k neurons, no threshold). The male CNS carries more synapses per neuron (125 M over 176 k at weight >= 1; 90.3 M
in the >= 5 graph) so the literal weight over-drives the network. Following the paper's own recipe, `gain` was chosen as
the smallest value at which 100-120 Hz sugar input drives MN9 robustly while grooming and escape stay stable and Kenyon
cells stay silent. The feeding and grooming runs of sections 2 and 5 were repeated at gain 0.55 and 0.75
(`-Pcfg=dtMs=0.5,gain=0.55` etc.):

| gain | sugar -> MN9 | KCs (sugar) | total spikes (sugar, 600 ms) | JO -> aDN1 / aDN2 | KCs (grooming) | total spikes (grooming) |
|---:|---|---:|---:|---|---:|---:|
| 0.55 | 10-60 Hz, intermittent (four ticks at <= 20 Hz), Fudog 0-20 Hz | 0 | 22,907 | not re-run | | |
| **0.65** | **30-90 Hz sustained** | **0** | **59,957** | **180-210 / 130-160 Hz sustained** | **0** | **133,674** |
| 0.75 | 40-110 Hz | 0 | 100,201 | 150-210 / 90-170 Hz for 250 ms, then **collapse to 0 Hz** | **39 Hz** from t = 250 ms | 481,561 (52 k spikes/tick, 111 k active) |

At 0.75 the grooming stimulus tips the network into runaway excitation: whole-brain spikes per tick quintuple within 200 ms,
Kenyon cells (which should be nearly silent) fire at 39 Hz, and the antennal-grooming DNs are shut off by the resulting
inhibition. At 0.55 feeding is too weak and irregular for a reliable proboscis readout. 0.65 (an effective 0.179 mV per
synapse) is the smallest gain that satisfies all three constraints; escape (section 4) is robust across the whole range.

## 10. Rejected alternatives

These were tried during tuning (recorded in `docs/ARCHITECTURE.md` section 6 and the `LifConfig` javadoc); they remain
available as `LifConfig` options (all off by default) so the experiments can be repeated with `-Pcfg=...`:

| Alternative | Setting | Outcome |
|---|---|---|
| Delta synapses (weight added to v instantly instead of through the 5 ms synaptic variable) | `synTauMs=0` | Removes the low-pass filtering of the alpha synapse; coincident inputs sum without attenuation and activity runs away at any gain that still drives MN9. Kept only for the unit tests, where single-spike arithmetic must be exact. |
| Divisive in-degree normalisation (inputs to neurons with more than N incoming synapses scaled by N / total) | `inputNormSynapses=300` | Fixes the antennal lobe (PNs ~110 Hz, KCs ~1 Hz, DNs < 1 Hz) but also scales down the heavily innervated cells the behaviours depend on: MN9 and the giant fibre stop responding. Rejected in favour of a per-population postsynaptic gain on Kenyon cells only. |
| Literal FlyWire weight | `gain=1.0` | Over-drives the male-CNS graph (see section 9). |
| Spike-frequency adaptation, per-spike jump clamp, asymmetric inhibitory gain | `adaptIncMv`, `maxJumpMv`, `inhibitoryGain` | Implemented as stabilisers but not needed at gain 0.65; left at 0 / 1.0 to stay as close as possible to the published model. |

## 11. Unit tests

```
./gradlew test --console=plain -q
```

`LifNetworkTest`: 10 tests, 0 failures (results in `build/test-results/test/TEST-com.fruitfly.brain.LifNetworkTest.xml`).
On hand-made synthetic connectomes they check: `exponentialSynapseFiltersInputLikeShiuModel` (a 100-synapse spike gives the
analytic alpha-PSP peak of 0.157 x jump and no spike, 200 synapses give exactly one spike after the synaptic rise),
`poissonDrivenNeuronFiresAtRequestedRate`, `strongExcitatoryConnectionDrivesTargetAfterDelay`,
`weakConnectionDoesNotReachThresholdButIntegrates`, `inhibitionPreventsFiring`, `refractoryPeriodLimitsRate`,
`endTickUpdatesEmaAndResetsCounts`, `chainPropagatesThroughManyNeuronsAndActiveSetStaysSmall`, `populationSpecsResolve`
(the spec grammar), and `flybRoundTrip` (FLYB write/read).

## 12. What has not been validated

- Odor-directed walking from DN activity (section 7): no reliable emergent steering signal yet.
- Courtship: pC1/pIP10 hooks exist in the decoder but no bench drives LC10a/pheromone GRNs to song.
- The flight state machine, landing (DNp07/DNp10 are read but only the loom bench reaches `FLYING` via the escape jump).
- Optomotor responses: T4/T5 are silent (section 6.1); HS/VS are driven analytically from self-rotation.
- Absolute firing rates in general. As Shiu et al. note, the model's absolute rates are not trustworthy; the behaviourally
  relevant results above are differences between stimulated and unstimulated conditions and orderings between pathways.
