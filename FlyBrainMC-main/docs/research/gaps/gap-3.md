# Gap 3 — Can 50 ms of male-CNS brain run per 50 ms Minecraft tick in Java? (measured)

Research report, 2026-09-03/04. Confidence tags: **[H]** measured in this session or verified against a primary source; **[M]** single source / partially verified; **[L]** inference or recommendation.

Everything below comes from a standalone JDK 25 benchmark (no Minecraft) written for this question:

* Source: `C:\Users\drini\AppData\Local\Temp\claude\C--Users-drini-OneDrive-Documents-fal-dev-fruit-fly-minecraft\793798d7-139a-4671-8d90-a5256c761f25\scratchpad\bench\src\LifBench.java` (kernel + harness), `LoopMicro.java`, `ZeroMicro.java` (loop-level micro-benchmarks); compiled classes in `..\bench\out3`.
* Data: `..\bench\data\` — `neurons.json` (176,422 Neuron rows), `edges_00..23.npz` (24 anonymous Cypher batches), `nodes.bin`, `edges_raw.bin` (258.6 MB, all 25,862,574 edges), `csr_w5.varint.gz`, `csr_w1.varint.gz`, `results_*_clean_*.csv` (quiet machine — authoritative), `results_*_pcore.csv` (loaded machine), `results_quick.csv` (first quiet run), `counts_*.bin` (per-neuron spike counts of the dynamic runs), `gc_*.log`, `log_*.txt`, `load_*.txt` (5 s CPU-load trace per run).
* Scripts: `fetch_edges.py`, `prep.py`, `analyze.py` (`python analyze.py clean_pcore clean_pcore16 clean_ecore16`), `parity.py`, `run_all.sh`, `run_clean.sh`, `run_par.sh`, `run_ecore.sh`.

Run with: `java -Xmx8g -Xms4g -XX:+UseG1GC -cp out3 LifBench data <resource|single|dynamic|parallel|pardyn|quick>` (launched via `cmd /c start /high /affinity <mask>` to pin to P-cores `FFFF` or E-cores `FFFF0000`).

---

## 1. TL;DR

1. **Real time (50 ms of brain per 50 ms tick) is reachable in plain Java on this machine — but only for a sensible configuration, not for "dt = 0.1 ms, all 25.9 M edges, one thread".** Dense exact-integration of all 176,422 neurons costs **~72 µs per step on one P-core** (27.5 µs vectorised state update + ~45 µs threshold scan) → integration alone is **36 ms per tick at dt = 0.1 ms, 7.3 ms at 0.5 ms, 3.7 ms at 1.0 ms** (quiet machine). **[H]**
2. **Synaptic delivery costs 1.3–1.6 ns per event** single-threaded (`g[post] += W·count`, CSR by presynaptic index). At the imposed activity levels asked for (0.3 / 1 / 3 % of neurons at 30 Hz = 0.8 k / 2.6 k / 7.9 k spikes per tick) that is 0.03–1.2 M events = **0.05–1.9 ms per tick**, negligible. **[H]**
3. **The real graph with Shiu's parameters does not stay at the imposed activity.** With the real 7 mV threshold and unmodified weights, driving 0.3 % of neurons (529 random sensory neurons at 30 Hz) ignites a *stable* state of **16.6–27 k neurons (9–15 % of the CNS) firing at 55–99 Hz: ~58 k spikes / 6.2 M events per tick at w ≥ 5, ~107 k spikes / 36.6 M events per tick at w ≥ 1**, nearly independent of drive strength. Single-threaded totals in that regime: **w ≥ 5: 55 / 19 / 12 ms per tick at dt 0.1 / 0.5 / 1.0; w ≥ 1: 90 / 48 / 39 ms.** This runaway-excitation regime (predicted analytically in `lif-model.md`) is the load the design must budget for unless an activity governor is added. **[H]**
4. **Threading works, and the barrier is the whole story at small dt.** Quiet P-cores, dense kernel, dt 0.5, w ≥ 5: 1 thread 7.3 ms → spin-barrier × 4 **2.1 ms** → × 16 1.5 ms; ignited network 19.9 ms → **9.1 ms (× 4)** → 7.3 (× 8) → 5.8 (× 16). ForkJoin `invokeAll` costs ~40 µs per step, so with 500 steps per tick (dt 0.1) FJ never gets below ~20 ms regardless of thread count; the spin barrier costs a few µs. Sixteen E-cores ≈ eight P-threads (ignited w ≥ 5 dt 0.5: 8.0 ms on 16 E-cores). Delivery parallelises only ~3.3x on 8 cores with the post-range partitioning used (replicated row traffic); integration scales ~linearly. **[H]**
5. **Two Java-specific hazards were found and fixed:** (a) *subnormal floats* — after ~0.43 s without input a neuron's `g` decays into the denormal range and every FP op on it becomes 25–50x slower (48 µs → 1.5–2.9 ms per step measured); Java has no FTZ/DAZ, so the kernel uses the branch-free flush `(x + 1e-6f) - 1e-6f` (zero cost). (b) C2 does not vectorise float `Math.max` reductions and the `Math.max` intrinsic has a 2.5x slow path when operands are equal (all-zero arrays 129–149 µs vs 54 µs), so the threshold scan must be a plain `if (u[i] > thr)` loop (~46 µs/step, data-independent). **[H]**
6. **Resources and memory:** delta+zigzag varint CSR of the w ≥ 5 graph = **17.9 MB (2.85 B/edge), 15.8 MB gzipped, decodes in ~100 ms** (75 ms gunzip + 27 ms varint); w ≥ 1 = 67.8 MB / 54.0 MB gz, ~0.4 s. Fixed-width little-endian arrays via `FileChannel.map` + bulk `IntBuffer.get`: 16 ms / 50 ms. In-memory CSR 38.4 MB (w ≥ 5) / 155.9 MB (w ≥ 1); per-brain state 2.1 MB (+ 13.4 MB ring buffer as benchmarked, shrinkable to < 1 MB). **No GC activity during simulation**: 0 collections inside all ~470 timed configurations (the kernel is allocation-free). **[H]**
7. **Recommendation (section 8): ship dt = 0.5 ms, w ≥ 5, dense vectorised kernel with plain compare threshold scan, 4 worker threads with a spin-then-park barrier, `brainMsPerTick = 50`.** Measured cost: 2.1–2.4 ms per tick at imposed activity, **9.1 ms (p95 10.4, max 11.3) in the ignited regime on 4 P-threads** (14.6 ms on 4 E-cores; 19.9 ms single-threaded) — ≥ 4.8x margin against the 50 ms tick while leaving ≥ 4 P-cores to the Minecraft server/client. dt 0.1 and w ≥ 1 stay opt-in fidelity modes: dt 0.1 / w ≥ 5 needs 4 spinning threads (18.8 ms ignited); w ≥ 1 ignited needs ≥ 8 threads (17.1 ms at dt 0.5) or slow motion (`brainMsPerTick ≈ 25` single-threaded at dt 0.1). Add an activity governor before wiring sensors (section 7).

---

## 2. Data actually used (verified)

| Item | Value | Source / verification |
|---|---|---|
| Dataset | `male-cns:v1.0`, anonymous `POST https://neuprint.janelia.org/api/custom/custom` | 24 Cypher pulls by `bodyId` range (`MATCH (a:Neuron)-[c:ConnectsTo]->(b:Neuron) WHERE a.bodyId >= lo AND a.bodyId <= hi RETURN a.bodyId, b.bodyId, c.weight`), 3 in parallel, **87 s total**, 0.65–1.1 M rows / 11–20 MB JSON / 7–10 s per batch **[H]** |
| Neurons | **176,422** `:Neuron` nodes (status Traced 165,122 / Orphan 6,464 / null 4,214 / Anchor 611 / Assign 11) | `neurons.json` **[H]** |
| Edges w ≥ 1 | **25,862,574** edges, **125,024,863** synapses | exact match to the orchestrator's numbers **[H]** |
| Edges w ≥ 5 | **6,287,789** edges, **90,297,299** synapses (72.2 %) | **[H]** |
| Max weight | 2,591 (fits `short`) | **[H]** |
| Out-degree | mean 146.6 (w ≥ 1) / 35.6 (w ≥ 5); max 11,209 / 7,570; in-degree max 11,526 | `prep.py` **[H]** |
| Sensory neurons (superclass `ol_sensory`, `cb_sensory`, `vnc_sensory`, `sensory_ascending`, `*_sensory_tbc`, `sensory_descending`) | 17,937; mean out-degree 66.9 (w ≥ 1) / 20.2 (w ≥ 5) | **[H]** |
| Sign rule (Shiu) | inhibitory iff `consensusNt` (fallback `predictedNt`) ∈ {gaba, glutamate} → **51,638 inhibitory (29.3 %)**; ACh 104,182, histamine 8,007, unclear 9,793, dopamine 396, octopamine 101, serotonin 48, null 2,257 treated as excitatory | **[H]** counts; **[L]** histamine/unclear as +1 |
| Cross-check | public GCS file `connectome-weights-male-cns-v1.0-minconf-0.5-traced-only.feather` (508 MB; `body_pre, body_post, weight, type_pre, type_post`) has **25,563,197** rows = traced-only subset, 1.2 % fewer edges than the `:Neuron` set | downloaded, read with pyarrow 21 **[H]** |

Gap-1 activity data: no gap-1 output existed when this ran, so imposed activity is **synthetic**: K = 0.3 / 1 / 3 % of N neurons drawn uniformly (or uniformly among the 17,937 sensory neurons for "dynamic" runs), each an independent 30 Hz Poisson process. Trains are generated in continuous time from a fixed seed and binned to steps, so every dt receives the same input up to binning **[H]**.

---

## 3. The kernel that was benchmarked (exact)

State per neuron: `float u` (v − v_rest, mV), `float g` (mV), `short refr` (steps left), `boolean imposed`. Parameters (Shiu et al. 2024): τ_m 20 ms, τ_syn 5 ms, threshold 7 mV above rest, reset to rest with g = 0, refractory 2.2 ms (u and g frozen), delay 1.8 ms, W_syn = 0.275 mV per synapse, weight = W_syn × signed synapse count.

Exact (Rotter–Diesmann / Brian2 `linear`) step: `u' = A·u + C·g`, `g' = B·g`, `A = e^{−dt/20}`, `B = e^{−dt/5}`, `C = (5/(5−20))·(e^{−dt/5} − e^{−dt/20})`:

| dt | A | B | C | delay steps D (1.8 ms) | refractory steps R (2.2 ms) | steps / 50 ms tick |
|---|---|---|---|---|---|---|
| 0.1 ms | 0.99501248 | 0.98019867 | 0.00493794 | 18 | 22 | 500 |
| 0.5 ms | 0.97530991 | 0.90483742 | 0.02349 | 4 (= 2.0 ms) | 4 (= 2.0 ms, from 4.4) | 100 |
| 1.0 ms | 0.95122942 | 0.81873075 | 0.04416622 | 2 (= 2.0 ms) | 2 (= 2.0 ms) | 50 |

(`Math.round`; both coarser dt values round delay and refractory to 2.0 ms — the approximation Wang et al. 2025 also used for their 1 ms Loihi 2 variant.) **[H]**

Per step, per partition `[lo,hi)` of the neuron index range (one fused phase, one barrier per step):
1. **Deliver** ring slot `t mod (D+1)`: for every presynaptic id in the slot walk its CSR row (`int[] post`, `short[] cnt` = sign·count): `g[post] += W·cnt`. With P > 1 each partition binary-searches the row (rows sorted by post) for its own post range → no atomics, no per-thread accumulation buffers, deterministic.
2. **Integrate** its range, three variants:
   * `DENSE_SCALAR`: one branchy loop (refractory test, update, threshold, reset).
   * `DENSE_SIMD`: (a) branch-free loop `u=(A·u+C·g+TINY)−TINY; g=(B·g+TINY)−TINY` (C2 auto-vectorises; TINY = 1e−6 flushes subnormals); (b) undo the update for the few refractory neurons held in a per-partition list (`u=0; g/=B; refr--`); (c) threshold scan in 512-element chunks (max reduction per chunk, scalar scan only where max > 7 mV) — *to be replaced by a plain compare loop, §4*.
   * `ACTIVE`: only neurons in a per-partition active list are integrated; a neuron enters when it receives an event and leaves when |u| < 1e−3 mV, |g| < 1e−3 mV and not refractory.
3. **Imposed (sensory) spikes** for this step from a pre-generated schedule → `u=g=0`, refractory 0 (Shiu's Poisson-driven neurons); `spikeCount[i]++` when recording.
4. Spikes are appended to the partition's list in ring slot `(t+D) mod (D+1)`. D+1 slots (19 / 5 / 3) are needed so readers of slot t and writers of slot t+D never share an array.

Ordering vs Brian2: Brian2 integrates → thresholds → delivers → resets; this kernel delivers first, so spike→PSP latency is D steps instead of D+1 (0.1 ms less at dt 0.1). Irrelevant for cost; noted for parity. **[H]**

Executors: `SINGLE`; `FJ` = `ForkJoinPool(P)` + one `RecursiveAction.invokeAll` of P reusable (`reinitialize()`) tasks per step, invoked from the main thread; `SPIN` = P−1 persistent daemon threads spinning on a `volatile int` generation (`Thread.onSpinWait`, `Thread.yield` every 20 k spins) with an `AtomicInteger` completion counter; the main thread computes partition 0.

Modes: **clamped** — threshold +∞ for every neuron, so exactly the imposed neurons spike (isolates the cost of "X % at 30 Hz"); **dynamic** — real 7 mV threshold, the network responds.

Cost model that fits every measurement below (P-core, quiet): `T_tick ≈ steps × (27.5 µs + T_scan)/P_eff + 1.5 ns × events / P_deliv + barrier × steps`, with T_scan ≈ 45 µs (compare loop) … 68 µs (chunked max), P_eff ≈ threads (≤ 8 P-cores), P_deliv ≈ 3.3 at 8–16 threads, barrier ≈ 2–8 µs (spin) or ~40 µs (FJ). **[H]**

---

## 4. Loop-level primitives (LoopMicro / ZeroMicro, 176,422 floats, HIGH priority, pinned) **[H]**

| Loop over N neurons (one step) | P-core (logical CPU 1) | E-core (CPU 17) | Notes |
|---|---|---|---|
| `u=A·u+C·g; g=B·g` | **27.5 µs (0.156 ns/elem)** | 46 µs | `-XX:-UseSuperWord`: 93 µs → SuperWord gives 3.4x (AVX2, `MaxVectorSize=32`) |
| same + subnormal flush `(x+TINY)−TINY` | 27.2 µs | 53 µs | flush is free on P-cores |
| `if (u[i] > thr) c++` (compare scan) | **46 µs (0.26 ns)** | 53 µs | not vectorised; data-independent (zero/sparse/dense arrays 52–62 µs under load) |
| `Math.max` float reduction | 48–53 µs | 82 µs | **not vectorised**; **129–149 µs on all-zero data** (equal-operand slow path of the intrinsic) |
| chunked max-scan (512) | 68 µs | 136 µs | 142–156 µs on all-zero data → explains the 0 %-activity rows in §5 |
| `Math.max(Float.floatToRawIntBits(..))` int reduction | 76 µs | 134 µs | no gain from the int trick |
| branchy scalar LIF loop | 81.5 µs (0.46 ns) | 140 µs | = `DENSE_SCALAR` |
| **decay into subnormals** | 48 µs → **1,536–2,930 µs per step** once g < 1.2e−38 | | after ~4,300 silent steps at dt 0.1 (≈ 430 ms); u follows ~1.7 s later; flush fixes it |

CPU: i9-14900KF = 8 P-cores (logical CPUs 0–15) + 16 E-cores (16–31), L2 2 MB per P-core, L3 36 MB; enumeration confirmed by pinned runs (E-cores 1.7x slower on these loops). `u`+`g` = 1.41 MB fit in one P-core's L2, which is why the vectorised pass reaches 0.16 ns/element. JDK 25.0.1 Temurin defaults (`-XX:+PrintFlagsFinal`): `UseSuperWord=true`, `SuperWordReductions=true`, `UseFMA=true`, `UseAVX=2`, `MaxVectorSize=32`, G1 GC. **[H]**

---

## 5. Single-threaded results, clamped (imposed activity only), quiet machine **[H]**

`results_single_clean_pcore.csv`; median of 10 timed ticks after 2 warm-up ticks per configuration (plus a global JIT warm-up), HIGH priority, affinity = P-cores. Milliseconds of wall time per 50 ms of simulated brain:

| w ≥ | dt (ms) | imposed | spikes/tick | events/tick | DENSE_SCALAR | **DENSE_SIMD** | ACTIVE (mean active set) |
|---|---|---|---|---|---|---|---|
| 1 | 0.1 | 0 % | 0 | 0 | 53.4 | 58.7 (†) | 0.0 (0) |
| 1 | 0.1 | 0.3 % | 800 | 0.12 M | 53.8 | **35.9** | 38.3 (51 k) |
| 1 | 0.1 | 1 % | 2,639 | 0.39 M | 54.0 | **35.7** | 76.6 (101 k) |
| 1 | 0.1 | 3 % | 7,906 | 1.17 M | 56.2 | **37.2** | 109.5 (141 k) |
| 1 | 0.5 | 0 % | 0 | 0 | 10.8 | 11.9 (†) | 0.0 |
| 1 | 0.5 | 0.3 % | 796 | 0.12 M | 10.8 | **7.2** | 7.8 (52 k) |
| 1 | 0.5 | 1 % | 2,625 | 0.38 M | 11.1 | **7.5** | 16.1 (101 k) |
| 1 | 0.5 | 3 % | 7,861 | 1.16 M | 12.2 | **8.4** | 23.3 (141 k) |
| 1 | 1.0 | 0 % | 0 | 0 | 5.3 | 5.8 (†) | 0.0 |
| 1 | 1.0 | 0.3 % | 790 | 0.12 M | 5.4 | **3.7** | 4.0 (52 k) |
| 1 | 1.0 | 1 % | 2,605 | 0.38 M | 5.8 | **3.9** | 8.0 (101 k) |
| 1 | 1.0 | 3 % | 7,799 | 1.15 M | 6.6 | **4.8** | 12.8 (141 k) |
| 5 | 0.1 | 0 % | 0 | 0 | 54.8 | 58.5 (†) | 0.0 |
| 5 | 0.1 | 0.3 % | 800 | 0.03 M | 53.6 | **36.5** | 11.7 (16 k) |
| 5 | 0.1 | 1 % | 2,639 | 0.10 M | 53.1 | **35.6** | 31.0 (41 k) |
| 5 | 0.1 | 3 % | 7,906 | 0.29 M | 54.9 | **35.9** | 60.5 (79 k) |
| 5 | 0.5 | 0 % | 0 | 0 | 10.7 | 11.7 (†) | 0.0 |
| 5 | 0.5 | 0.3 % | 796 | 0.03 M | 10.9 | **7.3** | 2.4 (16 k) |
| 5 | 0.5 | 1 % | 2,625 | 0.10 M | 10.9 | **7.3** | 6.2 (41 k) |
| 5 | 0.5 | 3 % | 7,861 | 0.28 M | 11.2 | **7.7** | 12.5 (79 k) |
| 5 | 1.0 | 0 % | 0 | 0 | 5.3 | 6.0 (†) | 0.0 |
| 5 | 1.0 | 0.3 % | 790 | 0.03 M | 5.4 | **3.8** | 1.2 (16 k) |
| 5 | 1.0 | 1 % | 2,605 | 0.10 M | 5.5 | **3.7** | 3.3 (41 k) |
| 5 | 1.0 | 3 % | 7,799 | 0.28 M | 5.9 | **4.2** | 7.0 (79 k) |

(†) 0 %-activity rows of DENSE_SIMD are *slower* than active rows because the chunked `Math.max` scan hits the intrinsic's equal-operand slow path on all-zero arrays (§4); with the compare-loop scan they would be ≈ 36 / 7.2 / 3.6 ms like the other rows. Per-tick spread (p95 − median) was < 0.2 ms everywhere on the quiet machine.

Reading: per step, DENSE_SIMD ≈ 72 µs (36 ms / 500 steps), DENSE_SCALAR ≈ 107 µs; the 0.3 % → 3 % difference at w ≥ 1 (1.05 M extra events → 1.3–1.7 ms) gives **1.3–1.6 ns per synaptic event**; ACTIVE ≈ 1.5 ns per active neuron-step, and its active set (1e−3 mV cut-off; a neuron stays active 80–180 ms after its last input because τ_m = 20 ms) is 16 k / 41 k / 79 k at w ≥ 5 and 51 k / 101 k / 141 k at w ≥ 1 for 0.3 / 1 / 3 %. **ACTIVE beats DENSE_SIMD only below ~45 k active neurons (≤ 1 % imposed activity at w ≥ 5, ≤ 0.3 % at w ≥ 1) and is 1.5–3x worse when the network is ignited (118–164 k active).** **[H]**

GC: 0 collections and 0 ms GC time inside all 72 timed regions; the whole 20 s run logged one 0.9 ms young pause during graph construction (`gc_single_clean_pcore.log`). Heap after `System.gc()` with both CSRs resident: 202 MB. **[H]**

---

## 6. Parallel results (4 / 8 / 16 threads), quiet machine **[H]**

### 6a. Clamped (imposed activity), 16 logical P-core CPUs (`results_parallel_clean_pcore16.csv`; load trace 23–25 % = this process alone)

Median ms per tick; SPIN × 4 in bold is the recommended operating point.

| w ≥ | dt | imposed | variant | 1 thread | FJ ×4 | FJ ×8 | FJ ×16 | **SPIN ×4** | SPIN ×8 | SPIN ×16 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 0.1 | 0.3 % | DENSE_SIMD | 35.9 | 22.9 | 20.8 | 19.8 | **10.0** | 5.8 | 7.0 |
| 1 | 0.1 | 0.3 % | ACTIVE | 38.3 | 21.9 | 20.7 | 18.7 | **14.6** | 11.0 | 6.6 |
| 1 | 0.1 | 3 % | DENSE_SIMD | 37.2 | 23.8 | 21.5 | 21.4 | **10.8** | 6.5 | 7.6 |
| 1 | 0.1 | 3 % | ACTIVE | 109.5 | 42.8 | 34.6 | 30.5 | **30.0** | 14.4 | 14.0 |
| 1 | 0.5 | 0.3 % | DENSE_SIMD | 7.2 | 4.8 | 4.1 | 4.0 | **2.0** | 1.1 | 1.4 |
| 1 | 0.5 | 0.3 % | ACTIVE | 7.8 | 4.9 | 4.5 | 4.0 | **2.9** | 1.8 | 1.3 |
| 1 | 0.5 | 3 % | DENSE_SIMD | 8.4 | 5.1 | 5.0 | 5.1 | **3.0** | 3.1 | 2.1 |
| 1 | 0.5 | 3 % | ACTIVE | 23.3 | 9.0 | 7.7 | 6.9 | **6.6** | 5.1 | 3.2 |
| 1 | 1.0 | 0.3 % | DENSE_SIMD | 3.7 | 2.5 | 2.2 | 2.1 | **1.0** | 0.8 | 0.7 |
| 1 | 1.0 | 3 % | DENSE_SIMD | 4.8 | 3.2 | 3.4 | 3.3 | **1.8** | 1.9 | 1.3 |
| 5 | 0.1 | 0.3 % | DENSE_SIMD | 36.5 | 24.1 | 20.4 | 19.5 | **10.0** | 11.6 | 6.9 |
| 5 | 0.1 | 0.3 % | ACTIVE | 11.7 | 13.3 | 12.4 | 12.3 | **5.4** | 4.0 | 3.4 |
| 5 | 0.1 | 3 % | DENSE_SIMD | 35.9 | 23.5 | 21.1 | 20.8 | **10.3** | 9.2 | 7.6 |
| 5 | 0.1 | 3 % | ACTIVE | 60.5 | 28.4 | 26.6 | 23.0 | **21.4** | 12.1 | 10.0 |
| 5 | 0.5 | 0.3 % | DENSE_SIMD | 7.3 | 4.5 | 4.2 | 4.1 | **2.1** | 2.6 | 1.5 |
| 5 | 0.5 | 0.3 % | ACTIVE | 2.4 | 2.6 | 2.6 | 2.7 | **1.1** | 0.8 | 0.7 |
| 5 | 0.5 | 3 % | DENSE_SIMD | 7.7 | 5.1 | 4.8 | 4.9 | **2.4** | 2.9 | 1.7 |
| 5 | 0.5 | 3 % | ACTIVE | 12.5 | 6.0 | 6.0 | 5.3 | **4.6** | 2.9 | 2.1 |
| 5 | 1.0 | 0.3 % | DENSE_SIMD | 3.8 | 2.4 | 2.1 | 2.1 | **1.0** | 0.6 | 0.9 |
| 5 | 1.0 | 3 % | DENSE_SIMD | 4.2 | 2.8 | 2.9 | 2.9 | **1.4** | 1.7 | 1.6 |

(DENSE_SCALAR rows omitted: 1.3–1.5x slower than DENSE_SIMD throughout; full table via `analyze.py`.) p95 ≤ median + 1 ms in all SPIN rows except two outliers (max 7.7 / 11.4 ms in DENSE_SCALAR runs).

What the numbers say: **ForkJoin has a floor of ~40 µs per step** (dt 0.1: 19.5–24 ms per tick regardless of thread count = 500 × ~40 µs; dt 0.5: ≥ 4.0 ms; dt 1.0: ≥ 2.1 ms), so FJ never beats ~2x. **The spin barrier scales the dense kernel almost linearly to 4 threads (36 → 10 ms, 7.3 → 2.0 ms) and to ~6x at 16 threads (36 → 6.9–7.6 ms); 8 spinning threads on 16 logical CPUs are sometimes slower than 4 (11.6 vs 10.0) because Windows lands two of them on hyper-thread siblings.** ACTIVE scales worse (list-walking gather/scatter) but stays best in the sparse w ≥ 5 cases (0.7–1.1 ms). **[H]**

### 6b. Ignited network (real threshold, 1 % sensory drive), P-cores (`results_pardyn_clean_pcore16.csv`)

| w ≥ | dt | 1 thread | FJ ×4 | FJ ×8 | FJ ×16 | **SPIN ×4** (p95 / max) | SPIN ×8 | SPIN ×16 | spikes/tick | events/tick |
|---|---|---|---|---|---|---|---|---|---|---|
| 5 | 0.5 | 19.9 | 9.6 | 11.0 | 11.5 | **9.1** (10.4 / 11.3) | 7.3 | 5.8 | 61 k | 6.2 M |
| 5 | 0.1 | 56.3 | 29.4 | 30.0 | 31.2 | **18.8** (19.5 / 24.8) | 13.0 | 11.2 | 61 k | 6.3 M |
| 1 | 0.5 | 51.5 | 23.6 | 22.9 | 28.5 | **22.6** (25.1 / 29.7) | 17.1 | 14.4 | 109 k | 36.6 M |
| 1 | 0.1 | 96.5 | 43.0 | 44.4 | 45.9 | **35.6** (36.7 / 40.3) | 30.5 | 21.2 | 110 k | 37.0 M |

Delivery-dominated cases scale sub-linearly: w ≥ 1, dt 0.5 → 1 thread 51.5 (≈ 7 integration + 44 delivery) vs 16 threads 14.4 (≈ 1 + 13.5): **3.3x on 8 physical cores**. Cause: with post-range partitioning every thread binary-searches every spiking row (~110 k rows/tick × P), so CSR row traffic is replicated ~P/2 times and the shared `g` array is written from all cores. A row-partitioned delivery (each thread owns a subset of spiking rows, writes into a private `float[N]` slice or uses `VarHandle` atomic adds, then a vectorised reduction) would remove the replication; not benchmarked. **[H for numbers, L for the fix]**

### 6c. E-cores only (mask `FFFF0000`, 16 E-cores; `results_*_clean_ecore16.csv`) — the "leave all P-cores to Minecraft" option

| case (DENSE_SIMD) | E: 1 thread | E: SPIN ×4 | E: SPIN ×8 | E: SPIN ×16 | E: FJ ×4/8/16 | P-core SPIN ×4 / ×8 / ×16 for comparison |
|---|---|---|---|---|---|---|
| clamped w ≥ 5, dt 0.5, 0.3 % | — | 4.7 | 2.5 | **1.6** | 14.7 / 13.6 / 13.1 | 2.1 / 2.6 / 1.5 |
| clamped w ≥ 5, dt 0.1, 0.3 % | — | 23.1 | 14.6 | **7.8** | 66.7 / 62.7 / 61.6 | 10.0 / 11.6 / 6.9 |
| clamped w ≥ 1, dt 0.5, 3 % | — | 6.4 | 3.6 | **2.3** | 11.0 / 16.2 / 11.0 | 3.0 / 3.1 / 2.1 |
| ignited w ≥ 5, dt 0.5 | 35.2 | 14.6 | 9.9 | **8.0** | 26.5 / 27.1 / 25.4 | 9.1 / 7.3 / 5.8 |
| ignited w ≥ 5, dt 0.1 | 103.5 | 35.4 | 23.0 | **14.7** | 73.2 / 72.7 / 69.2 | 18.8 / 13.0 / 11.2 |
| ignited w ≥ 1, dt 0.5 | 97.7 | 46.1 | 29.8 | **22.0** | 71.7 / 62.4 / 40.8 | 22.6 / 17.1 / 14.4 |
| ignited w ≥ 1, dt 0.1 | 178.9 | 68.8 | 44.6 | **29.3** | 126.1 / 98.4 / 101.7 | 35.6 / 30.5 / 21.2 |

An E-core is ~1.8x slower than a P-core here (single thread 35.2 vs 19.9 ms), FJ on E-cores is 3–5x worse than spin (its ~40 µs floor grows to ~120 µs), and **16 spinning E-cores ≈ 8 spinning P-threads** — i.e. the whole brain can live on the E-cores at dt 0.5 / w ≥ 5 for 8 ms per tick in the ignited regime, leaving every P-core to the game. **[H]**

### 6d. Failure mode observed: more spinning threads than logical CPUs

The 8-physical-core run (mask `5555`, 8 logical CPUs) stalled on the 16-thread SPIN configurations — 16 threads spinning for each other on 8 CPUs progressed at < 1 % speed for 9 minutes before it was killed (`results_parallel_clean_pcore8phys_PARTIAL.csv`, 17 rows: SPIN ×4 = 14.2 ms and SPIN ×8 = 8.1 ms for DENSE_SCALAR w ≥ 5 dt 0.1 — consistent with the P-core table). Never run a spin barrier with more threads than dedicated logical CPUs; in the mod use spin-then-`LockSupport.park` (spin ≤ 50 µs) and cap threads at the number of cores the game is not using. **[H]**

---

## 7. Dynamic runs (real threshold) — what the unmodified network does, quiet machine, 1 P-core **[H]**

`results_dynamic_clean_pcore.csv`, DENSE_SIMD, 12 ticks (2 warm-up + 10 timed); identical Poisson input trains at every dt.

| w ≥ | drive (30 Hz Poisson) | ms/tick dt 0.1 | dt 0.5 | dt 1.0 | spikes/tick (dt 0.1) | events/tick | neurons that fired in 600 ms | mean rate of those |
|---|---|---|---|---|---|---|---|---|
| 5 | 529 sensory (0.3 %) | 54.6 | 18.6 | 12.3 | 58.4 k | 6.2 M | 16,589 (9.4 %) | 67 Hz |
| 5 | 1,764 sensory (1 %) | 53.7 | 18.6 | 13.2 | 61.0 k | 6.3 M | 19,026 (10.8 %) | 61 Hz |
| 5 | 5,293 sensory (3 %) | 55.0 | 21.0 | 12.7 | 69.0 k | 6.7 M | 23,670 (13.4 %) | 56 Hz |
| 5 | 529 random (0.3 %) | 54.3 | 18.1 | 12.0 | 54.9 k | 5.9 M | 17,829 (10.1 %) | 52 Hz |
| 5 | 1,764 random (1 %) | 54.2 | 19.0 | 12.7 | 59.4 k | 6.3 M | 19,230 (10.9 %) | 58 Hz |
| 5 | 5,293 random (3 %) | 54.0 | 19.0 | 12.8 | 65.6 k | 6.8 M | 23,053 (13.1 %) | 55 Hz |
| 1 | 529 sensory (0.3 %) | 89.6 | 47.3 | 38.9 | 107.1 k | 36.6 M | 20,484 (11.6 %) | 99 Hz |
| 1 | 1,764 sensory (1 %) | 90.8 | 49.6 | 39.0 | 110.0 k | 37.0 M | 22,905 (13.0 %) | 91 Hz |
| 1 | 5,293 sensory (3 %) | 94.0 | 51.6 | 39.1 | 117.3 k | 38.1 M | 27,286 (15.5 %) | 83 Hz |
| 1 | 529 random (0.3 %) | 89.5 | 49.6 | 38.8 | 103.5 k | 35.6 M | 21,320 (12.1 %) | 82 Hz |
| 1 | 1,764 random (1 %) | 89.4 | 48.4 | 37.6 | 104.7 k | 36.1 M | 22,786 (12.9 %) | 86 Hz |
| 1 | 5,293 random (3 %) | 90.1 | 50.6 | 39.3 | 110.9 k | 37.6 M | 26,421 (15.0 %) | 80 Hz |

ACTIVE in the same runs: 107–114 ms (w ≥ 5, dt 0.1), 28–31 (dt 0.5), 18–20 (dt 1.0); 174–187 / 72–77 / 56–58 ms at w ≥ 1; active set 117–125 k (w ≥ 5) and 159–164 k (w ≥ 1) of 176 k.

Trajectories (`spikes_per_tick_trajectory` column) show ignition inside the first tick (14–46 k spikes in tick 0) and a flat plateau thereafter — a stable high-activity attractor, not divergence. 529 driven neurons at 30 Hz inject ~800 spikes/tick; the network amplifies them ~75x. Distribution at w ≥ 5, 0.3 % sensory: 16,589 neurons fired, 12,819 at ≥ 16.7 Hz, 3,640 at ≥ 100 Hz, maximum 423 Hz (the refractory ceiling is 455 Hz); the 1,000 busiest neurons carry 25 % of all spikes. **[H]**

Why: mean in-degree 147 (w ≥ 1) with 71 % excitatory presynaptic neurons at 0.275 mV × count per spike; `lif-model.md` §3 computed that 5 Hz on every excitatory input already puts 4 % of neurons above threshold. Shiu's experiments stayed sparse (~400 active neurons) because only ~20–30 GRNs were driven. A game driving thousands of photoreceptors, JONs and bristles at once will be ignited unless governed. **Budget for ~6 M events/tick at w ≥ 5 (≈ 10 ms single-threaded) or ~37 M at w ≥ 1 (≈ 44 ms) as the normal load, not 0.1–1 M.** **[H for measurements, L for the game extrapolation]**

Governor options (untested, simplest first) **[L]**: (i) divisive normalisation of W_syn by a running estimate of spikes/step (target ≤ ~5 k spikes/tick ≈ 100 k spikes/s ≈ 1 % of neurons at 30 Hz); (ii) adaptive threshold (+x mV per spike, τ ≈ 100 ms) to cap single-neuron rates; (iii) drive only the sensory classes that matter and treat photoreceptors as graded/inhibitory (`lif-model.md` §6); (iv) w ≥ 10 inside the optic lobe only (89 k `ol_intrinsic` neurons are 51 % of N).

### 7a. Cross-dt parity (same input trains; per-neuron spike counts over 600 ms; `parity.py`) **[H]**

| graph | dt pair | Pearson r of per-neuron counts | Spearman ρ | total-spike ratio | Jaccard of "fired" sets |
|---|---|---|---|---|---|
| w ≥ 5 | 0.1 vs 0.5 | 0.987–0.996 | 0.967–0.986 | 0.94–1.01 | 0.86–0.95 |
| w ≥ 5 | 0.1 vs 1.0 | 0.983–0.990 | 0.961–0.974 | 0.92–0.94 | 0.87–0.93 |
| w ≥ 1 | 0.1 vs 0.5 | 0.997–0.999 | 0.991–0.996 | 0.95–1.00 | 0.93–0.97 |
| w ≥ 1 | 0.1 vs 1.0 | 0.989–0.994 | 0.976–0.989 | 0.90–0.94 | 0.90–0.95 |

(6 configurations per row.) **dt = 0.5 ms reproduces dt = 0.1 ms rates at r ≥ 0.99 and total spikes within ±5 %; dt = 1.0 ms loses 6–10 % of spikes** (delay and refractory both rounded to 2 ms, and 20 % of τ_syn per step). Spike-for-spike identity across dt is impossible anyway (different binning); rate parity is the practical criterion. Variant parity: DENSE_SIMD and ACTIVE give bit-identical spike counts in 12 of 18 configurations and r ≥ 0.9992 in the rest (the 1e−3 mV cut-off occasionally shifts a threshold crossing by one step). **[H]**

---

## 8. Which (dt, threshold, threading) meets brainMsPerTick = 50 with margin

Definition used: the brain runs on its own thread(s); real time means wall time per 50 ms of simulated brain ≤ 50 ms, "with margin" ≤ 25 ms (2x), and on this 8P+16E machine the brain should take ≤ 4 P-cores (or the E-cores) when a client + integrated server run alongside. Loaded-machine measurements (§9) showed 3–5x slow-downs when the brain shared saturated P-cores, so margin is not optional. **[L]**

All values measured on the quiet machine (ms per 50 ms of brain, median; "ignited" = 1 % sensory drive with the real threshold):

| Configuration (dense vectorised kernel) | imposed 0.3–3 % | ignited | verdict |
|---|---|---|---|
| dt 0.1, w ≥ 1, 1 thread | 36–37 | **96** | fails (0.52x real time when ignited) |
| dt 0.1, w ≥ 5, 1 thread | 36 | **56** | fails (no margin even when sparse) |
| dt 0.1, w ≥ 5, SPIN ×4 P-cores | 10.0–10.3 | 18.8 (max 24.8) | passes, 2.7x margin, 4 P-cores busy |
| dt 0.1, w ≥ 1, SPIN ×16 P-cores | 7.0–7.6 | 21.2 | passes only with every P-core spinning |
| dt 0.5, w ≥ 1, 1 thread | 7.2–8.4 | **51.5** | passes sparse, fails ignited |
| dt 0.5, w ≥ 1, SPIN ×8 P-cores | 1.1–3.1 | 17.1 | passes, 2.9x margin, 8 threads |
| **dt 0.5, w ≥ 5, 1 thread** | **7.3–7.7** | **19.9** | passes, 2.5x margin, one core |
| **dt 0.5, w ≥ 5, SPIN ×4 P-cores** | **2.1–2.4** | **9.1 (p95 10.4, max 11.3)** | **recommended default: 4.8x margin, 4 P-cores left free** |
| dt 0.5, w ≥ 5, SPIN ×16 E-cores | 1.6–1.9 | 8.0 | equivalent alternative using no P-core at all |
| dt 1.0, w ≥ 5, 1 thread | 3.7–4.2 | 12–13 | passes 4x; −8 % spikes vs dt 0.1 |
| any dt 0.1 with ForkJoin | ≥ 19.5 | 29–46 | FJ's 40 µs/step floor makes dt 0.1 pointless |

Slow-motion if a fidelity configuration is insisted on, single-threaded: dt 0.1 / w ≥ 1 ignited → 96 ms → `brainMsPerTick ≈ 25` (0.5x); dt 0.1 / w ≥ 5 → 56 ms → `brainMsPerTick ≈ 40` (0.9x); dt 0.5 / w ≥ 1 → 51.5 ms → `brainMsPerTick ≈ 45`.

References for scale: Brian2 C++ standalone on the eon `fly-brain` benchmark runs the FlyWire model at 0.35–0.43x real time single-trial (dt 0.1, `eon_bench.csv`, RTX-4070 workstation under WSL2); Wang et al. 2025 measured Brian2 at 4.42 ± 0.24 s per simulated second (0.23x) for the sugar experiment and 10–14 s with 0.5–40 Hz background, Loihi 2 at 54 ms (dt 0.1) / 12 ms (dt 1) per simulated second (`loihi.txt` Table 1); Shiu's paper states ~5 min per 1 s trial per CPU thread for the numpy/Cython version. The Java dense kernel at dt 0.1 in the sparse regime (36 ms per 50 ms = 1.4x real time) is ~3–4x faster than Brian2 C++ and ~400x faster than Shiu's original setup, mostly from vectorised, allocation-free primitive arrays; the further 5x comes from dt = 0.5 ms and another 3.5–6x from spinning worker threads. **[H for references, M for the comparison]**

Implementation notes for the mod **[L, grounded in §4–§7]**:
* Plain compare threshold loop; keep the `(x+TINY)−TINY` flush in every dense loop; `float` state (u,g = 1.4 MB, L2-resident), `short` signed counts, one global `W_syn`.
* Persistent worker threads with a spin-then-park barrier (spin ≤ ~50 µs, then `LockSupport.park`) so idle brains do not burn cores; never more spinning threads than logical CPUs you own; default `brainThreads = min(4, availableProcessors()/4)`; optionally set the brain threads' affinity to E-cores on hybrid CPUs (not possible from pure Java — accept OS placement).
* Ring buffer: compact growable `int[]` per slot, not `slots × N` (13 MB per brain as benchmarked).
* Deliver-then-integrate ordering gives latency D steps; D = 18/4/2 and R = 22/4/2 at dt 0.1/0.5/1.0 (`Math.ceil` → 5/3 at dt 0.5 if closer parity to Brian2's 2.2 ms refractory is wanted).
* If delivery ever dominates (w ≥ 1 or several flies), switch to row-partitioned delivery with per-thread accumulation slices (§6b) — integration is not the bottleneck there.
* Monitor spikes/tick every tick and engage the governor above ~5 k spikes/tick; expose `brainDt`, `brainMinWeight`, `brainThreads`, `brainMsPerTick` in config.
* Load the w ≥ 5 varint resource from the jar (100 ms); make w ≥ 1 an optional user download (`FileChannel.map`, 50 ms).

---

## 9. Measurement conditions and remaining gaps

* Machine: i9-14900KF (8P+16E, 32 logical), 64 GB, Windows 11 Pro 26200, JDK 25.0.1 Temurin, G1, `-Xmx8g -Xms4g`. Pinning via `cmd /c start /high /affinity <mask>`. **[H]**
* From 20:39 another agent ran a 22–24-process Python job (`launch3/5/7.py` + workers, 80–100 % total CPU) in ~10-minute bursts. All tables above come from quiet windows (21:03–21:04 for single/dynamic, 21:09–21:12 for the P-core parallel phases, 21:21–21:22 for the E-core phases; each run's `load_*.txt` trace stays at the level produced by the benchmark itself). The loaded-machine copies (`results_single_pcore.csv`, `results_dynamic_pcore.csv`) were 3–5x slower and noisy (e.g. 28 vs 7.3 ms; ACTIVE up to 25x worse) even at HIGH priority pinned to the P-cores — hyper-thread-sibling and L3 contention, not scheduling, which is exactly what a saturating Minecraft client would do to a brain thread. **[H]**
* Thread CPU time per tick (`med_cpu_ms` column) is useless on Windows (15.625 ms quantum) — ignore it.
* Not measured: Vector API (`jdk.incubator.vector`) for the threshold scan (needs `--add-modules` at runtime, unattractive for a mod); GPU; row-partitioned delivery; a governor's effect on activity; the cost of generating Poisson sensory input in-game (schedules were pre-generated; a geometric-interval generator costs O(spikes), ~1 µs per 100 spikes); Brian2 spike-for-spike verification of this kernel (only the analytic coefficients were cross-checked against Brian2 in `lif-model.md` §3, 3.5e−13 mV agreement); several flies at once (cost is linear in the number of brains; state per brain is small, the CSR is shared).
* Java has no way to enable FTZ/DAZ; the additive flush is the only branch-free option and must stay in every variant that integrates idle neurons.
