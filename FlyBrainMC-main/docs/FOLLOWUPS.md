# Follow-ups (state as of 2026-09-03 evening)

## Done in this session (after the implementation agents)
- `/flybrain build|link|status|clear` wired (in-world block connectome, spiking neurons flash).
- Vision ON/OFF transient channels (`SensoryEncoders.onOffRMax`, `derivativeScale`): luminance decrements drive
  L2/L3 (OFF → Tm1/Tm2 → T5), increments drive Mi1 (ON → T4). Moving-bar scene now shows Mi1/Tm3/T4 and HS activity.
- Measured eye map bundled (`assets/fruitfly/brain/column_directions.csv`, from docs/research/gaps/gap-4.md) and
  used by `RetinaGeometry` (1,772 columns; linear fallback has the corrected +h = anterior sign).
- Flight is gated on DNg02 wingbeat-power / take-off DNs only (the wing motor-neuron pool saturates under many
  stimuli); giant-fibre jumps have a 1.5 s refractory; backward walking needs MDN > 20 Hz sustained; song is
  weighted 0.3 into the JO drive.

## Open
1. **Gap-1 tension.** An independent Brian2-equivalent replication (docs/research/gaps/gap-1.md, 300+ full-network
   runs) recommends W_syn ≈ 0.125 mV on the ≥5 graph (gain ≈ 0.45) plus an antennal-lobe fix, because the Shiu
   80 %-MN9 criterion (≈ 0.21 mV) sits inside the runaway regime for non-sugar pathways. The Java calibration keeps
   gain 0.65 (validated on sugar, bitter, looming, grooming; KC input gain 0.25 tames the mushroom body) and treats
   odor saturation with the reflex layer. Worth a sweep of `synapticGain` 0.45–0.65 with `kenyonCellInputGain` and a
   projection-neuron gain (`projectionNeuronInputGain`) against the EmbodiedBench scenarios.
2. **Gap-2 recipe.** Looming escape emerges from L2/L3 OFF Poisson drive alone (DNp01 28–53 Hz, loom-selective;
   DNp02 anterior / DNp11 posterior crossover emergent). Test in Java with `gradlew visionBench -Pscene=loom
   -PobjectChannels=false` and consider adopting the "sustained 0.3·(1−L) + transient" L2/L3 rate law instead of injected
   tonic current. L1 must never be Poisson-driven.
3. **Gap-3 performance.** A dense vectorised kernel with a spin barrier reached 9 ms/tick on 4 P-cores in the
   ignited regime; ours is 25–60 ms with ForkJoin. Also flush subnormal floats in `g` (Java has no FTZ).
4. **Gap-5 internal state.** Hunger/satiety, courtship arousal (P1), flight-vs-walking gating (Ache 2019 landing
   DNs decoupled when not flying) as tonic biases < 7 mV; not implemented (hunger only scales feeding in FlyBody).
5. **Gap-6 courtship.** Read pIP10 directly (done); do not average pC1 (the `courtship` channel is informational
   only). The male foreleg pheromone pathway is AN09B017a–g (vAB3) + AN03A008, not "PPN1".
6. Spike delivery is single-threaded; per-worker delay buffers would remove the last serial section.
7. Female flies use the male connectome; BANC v888 (CC BY 4.0) could provide a female CNS via the same pipeline.
8. First live run happened 2026-09-04 (`/flybrain build` confirmed working). Still unverified in-game: neuroscope
   layout, model animation constants, brain-cloud axis orientation, sounds, and the new pinned **brain view**
   (`B` / `/brainview`, `FlyFocus`, identity name tags and the rotating focus ring) — check that the dorsal map has the
   optic lobes at the top with L on the left and the nerve cord below, and that spikes light up where expected
   (`/fruitfly feed` → GNG/central brain + nerve-cord motor pool; `/fruitfly loom` → optic lobes then descending).
