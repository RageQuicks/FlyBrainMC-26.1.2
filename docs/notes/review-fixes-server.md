# Server/brain review fixes (F1-F5) — applied 2026-09-03

Scope: `src/main/java/com/fruitfly/**` (not `brain/tools`, not `src/client`, not tests).
Verification: `./gradlew compileJava compileClientJava --console=plain -q` green; `./gradlew test --console=plain -q` green
(56 tests, 0 failures: BrainRunner 5, LifNetwork 10, MotorDecoder 11, PopulationIndex 9, RetinaGeometry 11, SensoryEncoders 10).

## F1 — flight deadlock (`entity/FlyBody.java`)

Body-side fix only; the decoder's tested FLYING->IDLE contract (MotorDecoderTest) is untouched.

- Hover branch: `decoderAirborne = mode == FLYING || mode == ESCAPE`. The body now descends at `vy = -0.09`
  whenever `mode == LANDING || !decoderAirborne`, i.e. also on the FLYING->IDLE exit and when there is no brain
  (idle command). The `horizontalCollision -> hoverY += 0.5` bump only applies while `decoderAirborne`, so a
  descending fly is not pushed back up by a wall.
- The non-LANDING clear (`st.flying && mode not in {FLYING, LANDING, ESCAPE}`) now also accepts `isInWater()`,
  matching the LANDING clear above it.

Trace after the fix: ESCAPE -> FLYING -> IDLE at ~600 ms; vy = -0.09/tick brings the fly down within ~1 s;
the clear resets `st.flying` and `decoder.setFlying(false)`; next tick the walking branch calls `setNoGravity(false)`;
the reflex layer, DATA_FLYING, isFlapping() and BEE_LOOP all return to their grounded state.

## F2 — queued brain action kills the brain thread (`brain/BrainRunner.java`, `FlyBrainService.java`, `server/FruitFlyCommands.java`, `entity/FlyEntity.java`)

- `BrainRunner.drainInbox()` wraps each action in `try/catch (RuntimeException)`. Errors go to a settable
  `setActionErrorHandler(Consumer<Throwable>)` (default: one stderr line with the thread name; the brain package
  still has no Minecraft/log4j dependency). `FlyBrainService.acquire` installs a handler that logs via
  `FruitFlyMod.LOGGER.warn`.
- `BrainRunner.watch()` resolves the spec **before** mutating `watched`, and grows `watchedIdx` with
  `Arrays.copyOf` + the one new entry instead of re-resolving every watched spec each time. A bad spec now throws
  inside the isolated action and leaves the watch list untouched.
- `FlyBrainService`: `config.hudPopulations` is validated once on the shared `PopulationIndex` (lazy, cached in
  `validHudPopulations`); each invalid entry is logged once with its message and dropped, so a config typo no longer
  crashes every new brain and triggers the release/re-acquire loop.
- `FruitFlyCommands.stim/watch`: new `resolveOrFail(src, pi, spec)` validates on the game thread against the fly's own
  runner index (also a cache warm-up for the brain thread) and reports `Bad population spec '...': <message>` via
  `sendFailure` instead of brigadier's generic error.
- `FlyEntity.stimulate/watch`: `validSpec()` resolves on the game thread when a brain is present and WARNs + drops
  invalid specs, so programmatic callers (mobInteract, demos) are covered too.

`PopulationIndex.computeSimpleTerm` `body:` was left as is: `NumberFormatException` already extends
`IllegalArgumentException`, so every catch above already handles it.

## F3 — `sides()` suffix per term (`brain/SensoryEncoders.java`)

`sides(spec)` now applies `/L` / `/R` to each comma-separated term via `withSide()` (trim, skip empties, join).
`hs = sides("HSE,HSN,HSS")` therefore resolves `HSE/L,HSN/L,HSS/L` and `HSE/R,HSN/R,HSS/R`; the existing
SensoryEncodersTest assertion `HS == 2` (previously "Bug B", failing) now passes. Single-term call sites and
`prefix:VS` are unaffected; `&` intersections still work because PopulationIndex strips the trailing `/X` per term.

## F4 — block odor bearing (`entity/WorldSenses.java`)

`State.blockOdorTotal` added, reset with the bearing sums at each cached block scan, accumulated with `conc` per
odorous block, and added to `total` next to `bx/bz`. Block-only sources (cake, berry bush, flowers, honey, campfire,
lava...) now pass the `total > 1e-6` gate and yield `frame.odorBearingDeg`, so the reflex chemotaxis/aversion branches
and the bilateral ORN gain work without a dropped item nearby. The optional inclusion of fly-pheromone / player CO2
contributions in the bearing sums was **not** applied (behavioural change beyond the confirmed defect).

## F5 — stale command after `releaseBrain()` (`entity/FlyEntity.java`)

Airtight variant: the `lastCommand` field is gone. `latestCommand()` returns
`decoder == null ? MotorCommand.idle() : decoder.latest()` (volatile in MotorDecoder), and the post-step hook is
reduced to `dec.update(net, tickMs)`. `customServerAiStep` and `travel` use `latestCommand()`. `releaseBrain()`
now also: nulls the post-step hook before releasing, resets `bodyState.escapeTicks/smoothedForward/smoothedYaw`, and
sets `DATA_HAS_BRAIN=false` and `DATA_ACTIVITY=0`. `bodyState.flying` is intentionally left alone: with F1 the idle
command makes the body descend and land instead of dropping under gravity. No reset is needed in `acquireBrain()`
(a fresh MotorDecoder starts from `idle()`).

## Extra (outside F1-F5): Bug A — bilateral ORN gain sign (`brain/SensoryEncoders.applyOlfaction`)

After F1-F5 the only remaining test failure was the pre-existing "Bug A" from docs/notes/embodied-bench.md:
`odorSourceOnTheRightDrivesTheRightAntennaHarder` (right 158.6 Hz vs left 287.1 Hz). `WorldSenses.toHead` returns
positive azimuth for the fly's right, and the code's own comment said a right-hand source favours the right antenna,
but `gainL = 1 + g*sin(bearing)` gave the boost to the left. Swapped the signs
(`gainL = 1 - ...`, `gainR = 1 + ...`), which is the fix documented in the note. Applied because the task required the
test run to be green and the file is within scope; flagged here and in the report so the owners can veto it.

## Follow-ups (not done here)

- `docs/notes/embodied-bench.md`: mark Bug A and Bug B as fixed (file not in this task's scope).
- Optional FlyBody regression coverage for F1 needs a Minecraft-free seam (interface over onGround/isInWater/getY/move);
  the existing EmbodiedBench does not exercise FlyBody.
