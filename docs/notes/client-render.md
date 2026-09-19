# Client: fly model, renderer, textures, icon

Files: `src/client/java/com/fruitfly/client/FruitFlyClient.java`, `src/client/java/com/fruitfly/client/render/{FlyModel,FlyRenderer,FlyWingLayer,FlyGlowLayer}.java`,
`src/main/resources/assets/fruitfly/{textures/entity/fruit_fly.png, textures/entity/fruit_fly_female.png, models/item/fruit_fly_spawn_egg.json, lang/en_us.json, icon.png}`,
`tools/gen_fly_texture.py`, `tools/gen_icon.py`. Design source: `docs/research/fly-model-art.md` (§2.4–2.8), signatures from `docs/research/fabric-api.md` §6 (all re-checked with javap).

## Entrypoint (`FruitFlyClient`)
* `FLY_LAYER = new ModelLayerLocation(fruitfly:fruit_fly, "main")`, registered with `EntityModelLayerRegistry.registerModelLayer(FLY_LAYER, FlyModel::createBodyLayer)`.
* `EntityRendererRegistry.register(FruitFlyMod.FRUIT_FLY, FlyRenderer::new)`.
* `ClientPlayNetworking.registerGlobalReceiver(BrainTelemetryPayload.TYPE, (payload, ctx) -> TelemetryStore.accept(payload))` (type itself is registered in `FruitFlyMod.onInitialize`, which runs first on both sides).
* Key `H` (`key.fruitfly.neuroscope`, category `category.fruitfly`) polled with `consumeClick()` on `ClientTickEvents.END_CLIENT_TICK` → `NeuroscopeHud.toggle()`; `NeuroscopeHud.register()` called once.

## Model (`FlyModel extends HierarchicalModel<FlyEntity>`)
Built at "bee scale" (~20 px antenna→tip) on a 64×64 atlas, shrunk by `0.6 * getFlyScale()` in `FlyRenderer.scale`. Body pivot at y=18, ground y=24, faces −Z, +X = the fly's left.
Hierarchy: `body → { thorax, head → {left/right_eye, left/right_antenna → arista, proboscis → labellum}, abdomen → abdomen_tip, left/right_wing, left/right_haltere, 6 legs → tibia → tarsus }`.
The exact `texOffs`/`addBox` table is in the class Javadoc and duplicated at the top of `tools/gen_fly_texture.py` — keep them in sync.

Leg order is `L1, R1, L2, R2, L3, R3`; tripod groups `{L1,R2,L3}` (phase 0) and `{R1,L2,R3}` (phase π). Rest pose puts the claws on the ground (femur −0.35, tibia +1.55, tarsus −1.0 rad; right legs negated). Sign conventions: for a left leg (box along +x) +yRot swings the tip forward, +zRot tilts it down; for a wing (box along +z) `yRot = +1.25` swings the left wing out to +x, then +zRot beats it down (right wing: negated).

`setupAnim` resets every part, then layers behaviours as 0..1 blends:
* **Head** yaw/pitch × 0.4 (flies barely turn their heads). **Idle**: antenna twitch (`cos(age·0.18)`), abdominal breathing (`zScale`, `yScale`).
* **Walk** (tripod): `f = limbSwing·3.0`, protraction/retraction ±0.5·limbSwingAmount, lift 0.35 during the swing half (`max(0, −sin)` so the leg is up while moving forward), knee flexes with the lift, small body bounce.
* **Flight** (blend `flight`): wings `yRot ±1.25`, stroke `cos(age·2.1)·1.15` on zRot (±66°), `sin(age·2.1)·0.35` on xRot (figure-8-ish tip path), halteres antiphase, legs tucked (front folded by the head, hind trailing), body nose-up (−0.25 rad minus climb rate), bank into turns from `yBodyRot − yBodyRotO`, hover bob.
* **Song** (`getWingExtension()` 1 = left, 2 = right): that wing `yRot = side·1.1` (~63°), 13 Hz visual tremor with a 1 s pulse/sine envelope, abdomen and head turn slightly toward it.
* **Groom** (`getGroomState()`; 6 Hz `sin(age·1.9)`): 1 antennal sweep (front legs forward/up, head bows, antennae flick), 2 head rub (front legs in opposition), 3 leg rubbing (front legs below the head), 4 abdomen sweep (hind legs, nose-down, wings lifted a little).
* **Feed** (`getProboscis()` 0..1, already smooth from the server): proboscis `xRot 1.2 → −0.15`, `yScale 1 → 1.8` (+2.9 Hz pumping), labellum counter-scaled so it stays 1 px tall at the moving tip and spreads ×1.4, head tips forward.

Blends are smoothed per entity in a `WeakHashMap<FlyEntity, AnimState>` (the model instance is shared by all flies) with `Mth.approach` at 0.35/tick (flight) and 0.2/tick (groom/song), advanced by the `ageInTicks` delta.

## Renderer and layers
* `FlyRenderer extends MobRenderer<FlyEntity, FlyModel>`: shadow 0.15 (scaled by `getFlyScale`), `scale()` = `0.6·flyScale`, texture by `isMale()`.
* `FlyWingLayer`: wings are `visible = false` in the model so the opaque `entityCutoutNoCull` pass skips them; the layer applies `body.translateAndRotate`, flips them visible, renders both through `RenderType.entityTranslucent(tex)`, and while flapping adds two ghost strokes per wing at ±0.65 rad with tint alpha 80/255 (80/255 × membrane 110/255 = 0.135 > the shaders' 0.1 discard).
* `FlyGlowLayer`: re-renders the head (eyes/antennae/proboscis included) through `RenderType.entityTranslucentEmissive(tex)`, full-bright, tinted pink with alpha 40..150 following `getActivity()` (+ a 1 Hz shimmer); skipped when `!hasBrain()` or activity < 0.04. Same geometry ⇒ LEQUAL depth test puts it on top without z-fighting.

## Textures (`tools/gen_fly_texture.py`)
Procedural, deterministic (seeded noise). Palette: tan `#C8A165` base with `#8B5A2B` shading, thorax `#B8905A` with dorsal bristle sockets, head `#C39A62` with ocelli, eyes `#B22222` checkered with `#7A1E12` facets and an `#E04A2C` highlight, transverse bands `#2A1E14` at abdomen slices z=2,4,6 (top/sides only; ventral pale), male: slice z=7 and the whole `abdomen_tip` `#1A1410`; female: tan tip with a posterior band. Wing: shaped by fully transparent texels (rounded tip, narrow hinge), membrane `#DCE8F0` @ alpha 110, costa/L3/cross veins `#6E5A3C` @ 150–200. Legs dark brown with darker joints and a black claw texel.
The script implements the `ModelPart$Cube` face→model-cell mapping (top/bottom: tip at the top row; side faces: front at the left column of the +X face and the right column of the −X face), which is how the bands land at the correct z on all four wrapping faces. `.mirror()` flips U, so one painted region serves both sides.

## Icon (`tools/gen_icon.py`)
128×128 from a 512 draw (LANCZOS): dark plum tile, faint connectome ring, top-down fly (spread translucent wings, banded abdomen with black tip, red faceted eyes) and pink glowing brain lobes with cyan spike sparks above the head.

## Possible follow-ups
* Female at ×1.1–1.15 scale (real females are larger); currently both sexes use `0.6·flyScale`.
* A dedicated eyes texture + `EyesLayer(RenderType.eyes)` for full-bright red eyes at night.
* `FlyEntity.shouldRenderAtSqrDistance` override (default cutoff ≈ 49 blocks for the 0.5×0.3 box).
* A `FlyBuzzSound` tickable client sound instance (see fly-model-art.md §3.3) instead of the server-side `BEE_LOOP` ambient.
