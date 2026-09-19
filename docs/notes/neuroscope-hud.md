# Neuroscope HUD, telemetry store and brain-cloud overlay

Files (all client-only, `src/client/java`):

| File | Role |
|---|---|
| `com/fruitfly/client/hud/TelemetryStore.java` | Latest `BrainTelemetryPayload` per fly + ring-buffer history (200 payloads) of spikes/tick, active neurons and every watched population's rate. `accept(payload)` is the only entry point for the network receiver. |
| `com/fruitfly/client/hud/NeuroscopeHud.java` | `register()` (HudRenderCallback + world overlay), `toggle()`, `isVisible()`, `selectedFly()`. Draws the panel. |
| `com/fruitfly/client/hud/HudStyle.java` | Colours, population colour classes, label shortening, number formatting. |
| `com/fruitfly/client/BrainWorldOverlay.java` | Optional in-world hologram of ~3000 sampled somata above the selected fly (single-player only). |

## Contract used by `FruitFlyClient`

```java
ClientPlayNetworking.registerGlobalReceiver(BrainTelemetryPayload.TYPE, (payload, ctx) -> TelemetryStore.accept(payload));
NeuroscopeHud.register();          // once, in onInitializeClient
... while (KEY.consumeClick()) NeuroscopeHud.toggle();
```

Optional but recommended: `TelemetryStore.clear()` on `ClientPlayConnectionEvents.DISCONNECT` so stale entries from a
previous world are not shown (stale entries are also pruned automatically after 50 s and never selected after 5 s).

## Fly selection

Every 250 ms: `level.getEntitiesOfClass(FlyEntity.class, player.getBoundingBox().inflate(32))`, keep the nearest fly
with telemetry younger than 5 s (`TelemetryStore.hasFresh`). Hysteresis keeps the current fly unless another is at
less than half its squared distance. When no fly qualifies a 3-line stub panel says so.

## Panel geometry

Everything is drawn in "panel units" (= font pixels, `Font.lineHeight` 9) at a `PoseStack` scale
`s = round(guiScale/2) / guiScale` (capped at 1). That keeps every font texel an integer number of screen pixels
(crisp text at GUI scale 1, 2, 3, 4) while the panel occupies ~15 % of the width at scale 1-2 and ~31 % at scale 3-4.
Two hard caps: `s <= 0.4 * guiWidth / 300` (40 % width rule) and `s <= 0.97 * guiHeight / panelHeight`.

Panel width is 300 units; the height depends on the number of watched populations (1 column up to 12, 2 columns
beyond) and the legend wrap; default config (22 populations) gives ~353 units.

Sections, top to bottom:

1. Title: `NEUROSCOPE  <dataset>  fly #<id>  male|female` plus the payload age at the right. Dataset comes from
   `FruitFlyMod.BRAIN.connectome().dataset` when loaded in this JVM, else the literal `male-cns:v1.0`.
2. Status: mode name in its mode colour, `[REFLEX]` (orange) when the reflex layer drives, `[NO BRAIN]` (red) when the
   fly has no brain runner, spikes/tick, active neurons, `RT x.xx` (red below 0.9, green otherwise).
3. MOTOR: 10 bars in 2 columns – fwd, yaw (bipolar, centre tick), back, stop = max(halt, brake), land, flight
   (`flightPower`), feed, groom = max of the four groom channels, song, court (`courtship`). Values from
   `payload.channel(name)`; missing channels read 0.
4. POPULATIONS: one row per `popNames` entry: shortened label in class colour, Hz, bar scaled to 100 Hz, and a
   1-px peak-hold tick at the max of the last 20 payloads (from the store's history). Colour classes: descending blue,
   motor orange, sensory / early visual (incl. LC/LPLC) green, KC/PN/MBON/DAN purple, pC1/GNG/CX/AN pink, unknown grey.
5. SPIKES / TICK: the last 100 payloads' `spikesThisTick` as a bar chart; y scale = max(1000, window max); bar colour
   lerps dark blue → yellow with height.
6. RETINA: two 60-unit-tall eye insets. With `FruitFlyMod.BRAIN.geometry()` available (single-player) and
   `retinaRays.length == rays(0).size() + rays(1).size()`, each ray is binned into a `ceil(sqrt(1.4 n)) x ceil(n/gridW)`
   grid cell by (azimuth, elevation) over that eye's ray extent (x grows with azimuth so the frontal field of both eyes
   is at the panel seam; up is up) and filled with its 0..255 luminance. Left-eye rays are indices `0..nL-1`, right eye
   `nL..`, matching `WorldSenses.sample` (`rayIdx` increments over `rays(0)` then `rays(1)`). Without geometry the rays
   are drawn as a 4-row strip.
7. KEY POPULATIONS legend (wrapped): DNp09 fwd · DNa02 L/R turn · MDN back · DNg60 halt · DNp01 GF jump · DNp07/10
   land · aDN1/2 groom · MN9 proboscis · pIP10 song · LC4/LPLC2 loom · KC mushroom body · PN antennal lobe.

The HUD hides itself when `options.hideGui` (F1) or the F3 debug overlay is up (both use the right edge).

## Brain-cloud overlay

`WorldRenderEvents.AFTER_TRANSLUCENT`, active only while the HUD is visible, a fly is selected, and the connectome is
loaded in this JVM. Build once (cached per `Connectome` instance): uniform stride over the 141k neurons with a soma →
3000 points; bounding box → centre and scale (longest axis = 0.8 blocks); axis mapping right = −voxel x, up = −voxel y,
forward = −voxel z (data-access.md §1.6: x grows toward the animal's left, y ventrally, z anterior→posterior — a
schematic, not a registration). Colour by superclass (DN blue, motor/efferent orange, sensory green, optic lobe teal,
ascending yellow, VNC intrinsic brown, KC/PN/MBON/DAN purple, CX pink, other central grey-blue).

Per frame: exponential decay of two traces (`act` τ 0.22 s, `hit` τ 0.15 s); when the store's `seq()` changed, for each
neuron in `spikeSample()`: direct hit if sampled (`hit = act = 1`, drawn yellow) and a regional glow (`act` up to 0.75,
linear falloff over 1.5 grid cells of a 16³ CSR grid) on sampled somata near the spiking neuron's soma — needed because
the 3000-point sample only contains ~2 % of the neurons, so direct hits alone (~4 per payload) would be invisible.
Rendering: one `Tesselator` QUADS/POSITION_COLOR batch of camera-facing billboards (half-size 0.010 + 0.028·act blocks),
additive blend (SRC_ALPHA, ONE), depth test on, depth write off, `GameRenderer::getPositionColorShader`,
camera-relative positions multiplied by `ctx.matrixStack().last().pose()` (identity in 1.21.1), GL state restored.
Hologram origin = `fly.getPosition(partialTick) + (0, bbHeight + 0.75, 0)`, rotated by `Mth.rotLerp(pt, yBodyRotO,
yBodyRot)` via `WorldSenses.forward/right`. Skipped beyond 48 blocks from the camera.

Cost: 3000 quads (12k vertices) per frame plus a 3000-float decay loop; the spike injection touches ≤ 256 × ~30 points.

## Verification

`./gradlew compileJava compileClientJava --console=plain -q` passes (JDK 25, release 21). Not run in-game (no display);
signatures used were javap-verified against the cached mojmap jars (`Level`/`EntityGetter.getEntitiesOfClass`,
`GuiGraphics.fill/drawString/renderOutline`, `Font.plainSubstrByWidth`, `FastColor.ARGB32.lerp/color`,
`Camera.getLeftVector/getUpVector`, `BufferBuilder.build`, `BufferUploader.drawWithShader`, `Mth.rotLerp`,
`Minecraft.gui`, `Gui.getDebugOverlay().showDebugScreen()`).

## Open points

- The brain-cloud axis mapping is inferred from soma-position averages (data-access.md §1.6 marks it [M]); if the
  hologram looks mirrored or upside down in game, flip the sign in `BrainWorldOverlay.build`.
- The two eye insets normalise each eye to its own azimuth/elevation extent, so the seam between them is not exactly
  azimuth 0.
- `spikeSample` is a strided sample of the tick's spike log; the raster of *which* neurons fire is only shown in-world
  (the HUD raster is spikes-per-tick over time).
