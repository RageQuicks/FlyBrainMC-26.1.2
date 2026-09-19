# Fruit-fly mob: model, animation, texture, sound, brain-viz and icon (MC 1.21.1, mojmap, Fabric API 0.107.0)

Research report. Every fact is tagged **[H]** high / **[M]** medium / **[L]** low confidence and carries its source.
"javap" means verified with `javap -p` (optionally `-c -constants`) against the cached mojmap jars
`minecraft-common-1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2.jar` and `minecraft-clientonly-...jar`
(paths in the task context). Fabric API facts were verified by unzipping the nested module jars out of
`fabric-api-0.107.0+1.21.1.jar` (`META-INF/jars/fabric-rendering-v1-0.107.0.jar`, `fabric-lifecycle-events-v1-0.107.0.jar`,
`fabric-particles-v1-0.107.0.jar`) and by fetching the 1.21.1 branch javadoc on GitHub. Note: the Fabric jars in the Gradle
cache are intermediary-mapped (`class_4587` etc.); loom remaps them to mojmap at build time, so the mojmap names below are
the ones you type.

Scratch artefacts produced while researching (reusable): vanilla bee/parrot OGGs decoded to WAV under
`…\scratchpad\snd\` (`loop1.ogg`, `loop2.ogg`, `aggressive1.ogg`, `fly1.ogg`), unpacked Fabric module jars under
`…\scratchpad\jars\`.

---

## 0. Executive summary (what to build)

* **Hitbox** `EntityType.Builder.sized(0.3F, 0.2F).eyeHeight(0.12F)`; smaller than any vanilla mob (smallest vanilla: Silverfish/Endermite/Tadpole 0.4×0.3, Allay 0.35×0.6, Bee 0.7×0.6 eye 0.3) **[H, javap EntityType static init]**. Override `Entity.shouldRenderAtSqrDistance` – the default cutoff is `bbox.getSize()*64` = ~30 blocks for a 0.3×0.2×0.3 box **[H, javap]**.
* **Model**: build it "bee-sized" (≈20 px long) on a 64×64 texture for texel density, then shrink in `MobRenderer.scale(T, PoseStack, float)` with `poseStack.scale(0.35F,0.35F,0.35F)` (female 0.40F). Full cube-by-cube spec in §2.4, UV atlas in §2.5.
* **Wings**: 4×0×14 zero-thickness boxes (with `CubeDeformation(0.001F)` exactly as vanilla BeeModel does) rendered by a `RenderLayer` through `RenderType.entityTranslucent(tex)` (translucent + NO_CULL + lightmap + overlay, verified) with alpha ≈ 0.40 in the PNG; "buzz blur" = re-render the wing ModelPart 2 extra times at ±0.6 rad with `ModelPart.render(pose, vc, light, overlay, 0x46FFFFFF)` (the 5th int arg is packed ARGB) **[H, javap]**.
* **Animation constants** (radians/tick, 20 tps): wing stroke `Mth.cos(ageInTicks*2.1F)*1.15F` (vanilla bee uses 120.32113°/tick = 2.1 rad/tick and amplitude `PI*0.15`), tripod gait phases {0,π}, grooming rubs `Mth.sin(ageInTicks*1.9F)` (= 6.0 Hz, real 5–7 Hz), song wing extension 1.1 rad (real 50–70°). Formulas in §2.7.
* **Sound**: vanilla `SoundEvents.BEE_LOOP` files are mono 48 kHz Vorbis with a **fundamental of ~206–212 Hz** (measured by FFT of `mob/bee/loop1.ogg`, `loop2.ogg`); Drosophila beats at ~200–260 Hz, so use **pitch 1.0–1.2, not 1.4–1.8** (1.4–1.8 gives 300–380 Hz = house-fly/mosquito territory). Custom OGG recipe (numpy + `ffmpeg -c:a libvorbis`, ffmpeg with libvorbis is installed) in §3.4. Register with `Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))` **[H]**.
* **Brain viz**: in `WorldRenderEvents.AFTER_TRANSLUCENT` `consumers()` is **null** (Fabric javadoc) – draw with `Tesselator.getInstance().begin(QUADS, POSITION_COLOR)` + `RenderSystem.setShader(GameRenderer::getPositionColorShader)` + `BufferUploader.drawWithShader(...)`, camera-relative coordinates from `context.camera().getPosition()`; the camera rotation is already in `RenderSystem.getModelViewStack()` (1.21.1 `LevelRenderer.renderLevel` pushes the frustum matrix there and hands out an **identity** `PoseStack`) **[H, javap]**. 1000s of billboard quads = one draw call. Particle alternative: `ClientLevel.addParticle(opts, true /*force*/, …)`; cap is 16384 particles per texture layer **[H]**.
* **Icon**: `fabric.mod.json` `"icon": "assets/<id>/icon.png"`, square PNG, 128×128 conventional (power-of-two recommended, not required) **[H, Fabric spec]**; PIL script in §5.

---

## 1. Anatomy reference for a low-poly male *Drosophila melanogaster*

### 1.1 Sizes (verified numbers)
| Quantity | Value | Conf. | Source |
|---|---|---|---|
| Total body length | 2–3 mm; females "about 2.5 mm", males smaller; females up to 30 % larger than males | H | Wikipedia *D. melanogaster*; Animal Diversity Web ("2–3 mm") |
| Thorax length | males 0.85–0.95 mm, females 0.90–1.15 mm | H | Animal Diversity Web / Heredity 2008 body-size review (search summary) |
| Head / abdomen | not directly measured in fetched sources; by subtraction head ≈ 0.5 mm, abdomen ≈ 1.0–1.3 mm | L | inference |
| Wings | "span approximately 4 mm" → each wing ≈ 2 mm, i.e. ~0.8 body lengths, tips extend past the abdomen at rest; clear, membranous, veins visible | M | Wikipedia |
| Wingbeat frequency | 202 ± 2.8 Hz (lab WT); free-flight 215.6–261.2 Hz small→large wild flies | H | Behavior Genetics 1998 (PubMed 9583239); ResearchGate 2024 Indian population study |
| Stroke amplitude / AoA | ~130° stroke amplitude, 40–50° angle of attack during hover; U-shaped wing tip path | H | Fry, Sayaman & Dickinson, JEB 2005 208:2303 |
| Halteres | beat at wing frequency, in antiphase, amplitude 140–220°, unidirectionally coupled to wings | H | "Wings and halteres act as coupled dual oscillators" (PMC8629423) |
| Compound eye | 760 ommatidia, 8 photoreceptors each; brick-red = xanthommatin (brown) + drosopterins (red) | H | Wikipedia |
| Walking | 7.2–44.7 mm/s, typical 28 mm/s (≈ 3–18 body lengths/s); step frequency ≈ 16 Hz at 30 mm/s (60 ms period); speed is controlled almost entirely by step frequency/stance duration, swing duration constant | H | Mendes et al. 2013 eLife (PMC3545443); Wosnitza 2013 JEB; DeAngelis 2019 eLife |
| Tripod gait | 3 legs in stance / 3 in swing: fore+hind of one side + contralateral midleg, i.e. {L1,R2,L3} vs {R1,L2,R3}; "modified tripod" persists across all speeds | H | Mendes 2013; Chun et al. 2021 eLife 65878 |
| Grooming | leg rubs & head sweeps at 5–7 Hz (~6 Hz at 18 °C, ~200 ms/movement); bouts alternate head-sweep (legs synchronous) ↔ leg-rub (legs in opposition) every ~2 s (0.3–0.6 Hz) | H | Mueller/Seeds 2021 eLife 71508 ("nested CPG"); Seeds 2014 eLife 02951 |
| Courtship song | unilateral wing extension; sine song 140–170 Hz with wing at 50–70°; Pslow pulses (200–250 Hz) at ~60°, Pfast pulses at 5–30°; pulse trains 2–50 pulses, carrier 150–300 Hz, IPI ≈ 35 ms | H | Clemens et al. 2018 Curr Biol ("new song mode"); von Schilcher 1976; Neuron 2011 review |
| Proboscis extension (PER) | labellar contact 0.2–0.6 s; full extension + feeding ≥ 1 s | M | PER assay protocols (PMC9647644, Shiraiwa & Carlson JoVE) |
| Leg segments | coxa, trochanter, femur, tibia, 5 tarsomeres + claw; femora of the 3 leg pairs similar length, foreleg tibia shorter than mid/hind. Absolute mm values were NOT obtainable (bioRxiv rate-limited); use femur ≈ tibia ≈ 0.55–0.65 mm, tarsus ≈ 0.7–0.9 mm, hind legs longest | L | Appendometer bioRxiv 2025 abstract; general morphology |

### 1.2 Sex differences you can actually render
* **Male abdomen**: tergites A5 and A6 fully dark-pigmented (looks like a solid black tip) plus dark, rounded genital arch → "stubbier, rounder, darker" abdomen; 6 visible tergites (A1–A6) **[H, Wikipedia "Abdominal pigmentation in D. melanogaster"; Vanderbilt phenotypes guide]**.
* **Female abdomen**: longer, pointed, 7 visible tergites (A1–A7), pigmentation restricted to a posterior stripe on each tergite (A2–A6) **[H, same]**. Both sexes have the transverse black stripes on A2–A4.
* **Sex combs** on male foreleg tarsomere 1 – invisible at voxel scale, skip **[H]**. Caveat: freshly eclosed flies are pale with elongated abdomens (pigmentation darkens over hours) **[H]**.
* Body length: render the female at ×1.15 the male scale (matches "up to 30 % larger" upper bound = 1.3, typical ~1.1–1.2) **[M]**.

### 1.3 Colour palette (design choice; grounded in "yellow-brown body, brick-red eyes, transverse black abdominal rings")
| Part | Hex | Notes |
|---|---|---|
| Thorax base | `#B8905A` | slightly darker than abdomen; add 4–6 dark dots `#6B4A2B` for dorsal bristle sockets |
| Abdomen base | `#D2A96E` (tan) | |
| Tergite stripes A2–A4 | `#2A1E14` | 1-px rows across the dorsal & lateral faces |
| Male tip (A5–A6 + genitalia) | `#1A1410` | whole `abdomen_tip` cube + last 2 px of `abdomen` |
| Head | `#C39A62` | |
| Eyes | base `#B8321E`, highlight `#E04A2C`, shadow `#7A1E12` | dither the two reds in a checker to fake ommatidia |
| Proboscis / labellum | `#A98555` / `#8B6A3E` | |
| Antenna / arista | `#A67F4E` / `#3A2A1A` | |
| Legs | `#A4824F`, joints `#6E5232` | |
| Wing membrane | `#DCE8F0` @ alpha 100/255 (≈0.4) | slight blue-white tint reads as "glassy" |
| Wing veins | `#6E5A3C` @ alpha 210/255 | L1–L5 longitudinal veins + 2 cross-veins |
| Halteres | `#C9A26B`, knob `#8B6A3E` | |

---

## 2. Minecraft model math (mojmap 1.21.1)

### 2.1 API surface (all javap-verified [H])
```java
// net.minecraft.client.model.geom.builders
MeshDefinition mesh = new MeshDefinition(); PartDefinition root = mesh.getRoot();
PartDefinition addOrReplaceChild(String name, CubeListBuilder cubes, PartPose pose);
CubeListBuilder.create().texOffs(int u,int v).mirror()
   .addBox(float x,float y,float z,float w,float h,float d)                       // floats allowed
   .addBox(float x,float y,float z,float w,float h,float d, CubeDeformation grow) // grow = inflate
   .addBox(String comment,float x,float y,float z,int w,int h,int d,int u,int v)  // per-cube texOffs
PartPose.ZERO; PartPose.offset(x,y,z); PartPose.rotation(xRot,yRot,zRot); PartPose.offsetAndRotation(x,y,z,xRot,yRot,zRot);
LayerDefinition.create(mesh, texWidth, texHeight);          // e.g. 64, 64
// net.minecraft.client.model.geom.ModelPart (public fields)
float x,y,z,xRot,yRot,zRot,xScale,yScale,zScale; boolean visible, skipDraw;
void resetPose(); PartPose getInitialPose(); void setInitialPose(PartPose); ModelPart getChild(String);
Stream<ModelPart> getAllParts(); void translateAndRotate(PoseStack);
void render(PoseStack, VertexConsumer, int packedLight, int packedOverlay);            // white
void render(PoseStack, VertexConsumer, int packedLight, int packedOverlay, int argb);   // tint+alpha
// Models
abstract class EntityModel<T extends Entity> extends Model { setupAnim(T, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch); prepareMobModel(T, limbSwing, limbSwingAmount, partialTick); }
abstract class HierarchicalModel<E> extends EntityModel<E> { abstract ModelPart root(); renderToBuffer(PoseStack, VertexConsumer, int light, int overlay, int color); animate(AnimationState, AnimationDefinition, float ageInTicks[, float speed]); }
EntityModel()  -> renderType function defaults to RenderType::entityCutoutNoCull; EntityModel(Function<ResourceLocation,RenderType>) to override
// Renderer
MobRenderer(EntityRendererProvider.Context ctx, M model, float shadowRadius); protected void scale(T, PoseStack, float partialTick);
LivingEntityRenderer.getRenderType(T, boolean bodyVisible, boolean translucent, boolean glowing) -> model.renderType(tex) | itemEntityTranslucentCull | outline
RenderLayer<T,M>(RenderLayerParent<T,M>) { abstract void render(PoseStack, MultiBufferSource, int light, T, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch); }
EyesLayer<T,M> { abstract RenderType renderType(); }   // glowing eyes helper, uses RenderType.eyes(tex) (additive, no cull, full-bright)
EntityRendererProvider.Context.bakeLayer(ModelLayerLocation), getModelSet()
new ModelLayerLocation(ResourceLocation model, String layer)
// Fabric (mojmap names after loom remap)
EntityModelLayerRegistry.registerModelLayer(ModelLayerLocation, TexturedModelDataProvider /* LayerDefinition createModelData() */);
EntityRendererRegistry.register(EntityType<? extends E>, EntityRendererProvider<E>);
```

### 2.2 Coordinate conventions (javap of LivingEntityRenderer.render / ModelPart.translateAndRotate [H])
* Model space: 16 units = 1 block; **+Y is down**; ground plane is **y = 24**; the mob faces **−Z** (north). Head at −Z, tail at +Z. Model −X = the creature's right.
* `LivingEntityRenderer.render` does, in order: `setupRotations(...)`, `poseStack.scale(-1,-1,1)`, `this.scale(entity, poseStack, partialTick)`, `poseStack.translate(0, -1.501F, 0)`, then `model.prepareMobModel(...)`, `model.setupAnim(...)`, `model.renderToBuffer(...)`, then each `RenderLayer.render(...)` with the same PoseStack state. Because the −1.501 translate happens *after* your `scale()`, any uniform scale keeps the feet (y=24) on the ground.
* `ModelPart.translateAndRotate`: `translate(x/16,y/16,z/16)`, then `mulPose(new Quaternionf().rotationZYX(zRot,yRot,xRot))` (vertex is rotated X first, then Y, then Z), then `scale(xScale,yScale,zScale)`. Children inherit scale.
* `setupAnim` inputs: `ageInTicks = tickCount + partialTick` (`getBob`), `limbSwing = walkAnimation.position(pt)`, `limbSwingAmount = min(walkAnimation.speed(pt), 1)` (babies: limbSwing×3). `WalkAnimationState.update(newSpeed, alpha)` does `speed += (target-speed)*alpha; position += speed` **[H]**. Vanilla quadrupeds/spiders use `limbSwing*0.6662F` for one gait cycle per ~9.4 units of limbSwing – far too slow for a fly; use a larger multiplier (§2.7).
* `Mth.sin/cos` take radians; `Mth.PI`, `Mth.HALF_PI`, `Mth.DEG_TO_RAD = 0.017453292F`, `Mth.RAD_TO_DEG`, `Mth.lerp`, `Mth.clamp`, `Mth.triangleWave`, `Mth.wrapDegrees` all exist **[H]**.

### 2.3 How vanilla does it (decompiled constants, javap -c -constants [H])
**BeeModel** (64×64, `AgeableListModel`): `bone` at offset(0,19,0) (body centre 5 px above ground) → `body` texOffs(0,0) box(−3.5,−4,−5, 7,7,10); `stinger` texOffs(26,7) box(0,−1,5, 0,1,2); antennae texOffs(2,0)/(2,3) box(±1.5…,−2,−3, 1,2,3) at offset(0,−2,−5); `right_wing` texOffs(0,18) box(−9,0,0, 9,0,6, **CubeDeformation(0.001F)**) at offsetAndRotation(−1.5,−4,−3, 0,−0.2618,0); `left_wing` mirror, box(0,0,0, 9,0,6) at (1.5,−4,−3, 0,+0.2618,0); legs `addBox("front_legs",−5,0,0, 7,2,0, 26,1)` at (1.5,3,−2), middle (26,3) at z=0, back (26,5) at z=2.
`setupAnim`: if `onGround() && deltaMovement.lengthSqr() < 1.0E-7` → wings yRot ±0.2618 (15°), zRot 0, legs xRot 0; else (flying) `f = ageInTicks * 120.32113F * DEG_TO_RAD` (`Bee.FLAP_DEGREES_PER_TICK = 120.32113F`), `rightWing.yRot = 0; rightWing.zRot = Mth.cos(f) * PI * 0.15F` (±27°), `leftWing.zRot = -rightWing.zRot`, all legs xRot = 0.7853982 (45°). If not angry: `f = Mth.cos(ageInTicks*0.18F)`; `bone.xRot = 0.1F + f*PI*0.025F`; antennae `xRot = f*PI*0.03F`; `frontLeg.xRot = -f*PI*0.1F + 0.3926991F`; `backLeg.xRot = -f*PI*0.05F + 0.7853982F`; `bone.y = 19 - Mth.cos(ageInTicks*0.18F)*0.9F`. Roll handled via `rollAmount` from `prepareMobModel`. `Bee.isFlying() = !onGround()`.
**ParrotModel**: flying state wings `leftWing.zRot = -0.0873F - ageInTicks…` style (wings held out with ±0.0873 rad), legs `xRot = Mth.cos(limbSwing*0.6662F [+π])*1.4F*limbSwingAmount`, tail `xRot = 1.015F + Mth.cos(limbSwing*0.6662F)*0.3F*limbSwingAmount`, head bob `y = 15.69F + …`.
**SilverfishModel** (7 segments): `bodyParts[i].yRot = Mth.cos(ageInTicks*0.9F + i*0.15F*PI) * PI * 0.05F * (1 + |i-2|)`; `bodyParts[i].x = Mth.sin(ageInTicks*0.9F + i*0.15F*PI) * PI * 0.2F * |i-2|`.
**SpiderModel** (64×32, 8 one-piece legs box(−15,−1,−1,16,2,2) / mirror (−1,−1,−1,16,2,2) at (±4,15,z)): rest `zRot = ∓0.7853982` (hind/front) / `∓0.58119464` (middle pairs), `yRot = ±0.7853982 / ±0.3926991`; gait: `yaw_k = -Mth.cos(limbSwing*0.6662F*2 + phase_k)*0.4F*limbSwingAmount` with phase_k ∈ {0, π, π/2, 3π/2} per pair; `lift_k = |Mth.sin(limbSwing*0.6662F + phase_k)*0.4F|*limbSwingAmount`; `leg.yRot += ±yaw_k; leg.zRot += ±lift_k`. Head `yRot = netHeadYaw*DEG_TO_RAD; xRot = headPitch*DEG_TO_RAD`.

### 2.4 Fruit-fly model spec (64×64 texture, designed at "bee scale", shrunk in the renderer)
Hierarchy = `root → body → {thorax, head→{eyes, antennae→aristae, proboscis→labellum}, abdomen→abdomen_tip, wings, halteres, 6 legs→tibia→tarsus}`.
All sizes in px (1/16 block). Pivots are relative to the parent pivot. "UV" = top-left of the box's texture footprint; footprint = `2·(w+d)` wide × `(d+h)` tall.

| Part (name) | Parent | `texOffs` | `addBox(x,y,z, w,h,d[,grow])` | `PartPose` (x,y,z, xRot,yRot,zRot) | UV footprint (x0..x1, y0..y1) |
|---|---|---|---|---|---|
| `body` | root | – | (none; pivot only) | offset(0, 18, 0) | – |
| `thorax` | body | (0,0) | (−3,−3,−3, 6,6,6) | ZERO | 0..23, 0..11 |
| `head` | body | (24,0) | (−2.5,−2,−4, 5,4,4) | offset(0, −0.5, −3) | 24..41, 0..7 |
| `left_eye` | head | (42,0) | (1.5,−1.5,−3.5, 1,3,3, CubeDeformation(0.25F)) | ZERO | 42..49, 0..5 |
| `right_eye` | head | (42,0).mirror() | (−2.5,−1.5,−3.5, 1,3,3, CubeDeformation(0.25F)) | ZERO | shares |
| `left_antenna` | head | (56,0) | (−0.5,−0.5,−1, 1,1,1) | offsetAndRotation(1, −1, −4, −0.5F,0,0) | 56..59, 0..1 |
| `left_arista` | left_antenna | (60,0) | (0,−2,−1, 0,2,1, CubeDeformation(0.001F)) | offsetAndRotation(0,0,0, −0.6F,0,0) | 60..61, 0..2 |
| `right_antenna`/`right_arista` | head | same .mirror() | x mirrored (−1 pivot) | | shares |
| `proboscis` | head | (50,0) | (−0.5,0,−0.5, 1,3,1) | offsetAndRotation(0, 2, −2, 1.2F,0,0) (folded back under head) | 50..53, 0..3 |
| `labellum` | proboscis | (50,4) | (−1,3,−1, 2,1,2) | ZERO | 50..57, 4..6 |
| `abdomen` | body | (0,12) | (−2.5,−2.5,0, 5,5,8) | offset(0, 0.5, 3) | 0..25, 12..24 |
| `abdomen_tip` | abdomen | (26,12) | (−1.5,−1.5,8, 3,3,2) (male: painted black) | ZERO | 26..35, 12..16 |
| `left_wing` | body | (0,26) | (−0.5,0,0, 4,0,14, CubeDeformation(0.001F)) | offsetAndRotation(1, −3, 0, 0, 0.12F, 0) | 0..35, 26..39 (top face at (14,26) 4×14, bottom face at (18,26) 4×14) |
| `right_wing` | body | (0,26).mirror() | (−3.5,0,0, 4,0,14, CubeDeformation(0.001F)) | offsetAndRotation(−1, −3.1, 0, 0, −0.12F, 0) (0.1 px lower → no z-fight at rest) | shares |
| `left_haltere` | body | (36,12) | (0,−0.5,0, 1,1,2) | offset(2.5, 0, 2.5) | 36..41, 12..14 |
| `right_haltere` | body | (36,12).mirror() | (−1,−0.5,0, 1,1,2) | offset(−2.5, 0, 2.5) | shares |
| `left_front_leg` (femur) | body | (42,12) | (0,−0.5,−0.5, 4,1,1) | offsetAndRotation(2, 2, −2, 0, +0.7F, −0.35F) | 42..51, 12..13 |
| `left_middle_leg` | body | (42,12) | same | offsetAndRotation(2.5, 2.5, 0, 0, 0, −0.35F) | shares |
| `left_hind_leg` | body | (42,12) | same | offsetAndRotation(2.5, 2.5, 2, 0, −0.7F, −0.35F) | shares |
| `*_tibia` | each femur | (42,14) | (0,−0.5,−0.5, 4,1,1) | offsetAndRotation(4,0,0, 0,0, +1.10F) | 42..51, 14..15 |
| `*_tarsus` | each tibia | (42,16) | (0,−0.5,−0.5, 4,1,1) | offsetAndRotation(4,0,0, 0,0, −0.20F) | 42..51, 16..17 |
| right legs | body | same texOffs .mirror() | (−4,−0.5,−0.5, 4,1,1), children at (−4,0,0) | x negated, yRot and zRot **negated** (front: yRot −0.7, hind +0.7; femur zRot +0.35, tibia −1.10, tarsus +0.20) | shares |
| free atlas space | | | ghost/blurred wing variant (36,26) is too wide (would end at x=71) → put it at (0,40): 0..35, 40..53; female abdomen variant at (36,40) 26×13 → 36..61, 40..52; rows 54..63 free | | |

Sanity: body length antenna→tip = 4 (head) + 6 (thorax) + 10 (abdomen) ≈ 20 px → ×0.35 = 0.44 block; height at rest ≈ 9.5 px → 0.21 block (matches the 0.2 hitbox); in flight the wings (14 px, swung out) give a span of ≈ 27 px → 2× body length ≈ the real 4 mm span vs 2.5 mm body. Ratio head:thorax:abdomen 4:6:10 ≈ 0.5:0.75:1.25 mm. Leg reach check (left leg): root at y=20.5 (3.5 px above ground); femur 4 px at −0.35 rad rises 1.37 → knee y=19.13, x=6.26; tibia at cumulative +0.75 rad drops 2.73 → y=21.86, x=9.19; tarsus at cumulative +0.55 rad drops 2.09 → y≈23.95 ≈ ground. Legs total 12 px (real ≈ 16 px equivalent) – lengthen tibia/tarsus to 5 if you want true proportions (footprints become 12×2).

Sign conventions used above (from `rotationZYX`, +Y down): for a LEFT leg whose box runs along +x, **positive `yRot` swings the tip forward (−Z)** and **positive `zRot` tilts the tip down**; for a RIGHT leg (box along −x) both are reversed. For the wing (box along +z): `yRot=+π/2` points the left wing out to +x (right wing −π/2 to −x); once out, `zRot` flaps it (positive = down for the left wing, negative = down for the right wing).

### 2.5 Texture atlas layout math (`ModelPart.Cube`, 1.21.1) [H]
For `texOffs(u,v)` and box size `(w,h,d)` the six faces occupy (in texture pixels, y grows downward; verified against the player-skin head layout and the polygon Direction order DOWN,UP,WEST,NORTH,EAST,SOUTH in `ModelPart$Cube`):

| In-game face | Direction (model space) | Rect (x, y, width, height) |
|---|---|---|
| top (seen from above) | DOWN (model −Y) | (u+d, v, w, d) |
| bottom | UP | (u+d+w, v, w, d) |
| creature's right side | WEST (−X) | (u, v+d, d, h) |
| front | NORTH (−Z) | (u+d, v+d, w, h) |
| creature's left side | EAST (+X) | (u+d+w, v+d, d, h) |
| back | SOUTH (+Z) | (u+2d+w, v+d, w, h) |

`mirror()` flips the U direction of every face (use it for the right-hand copies so one painted region serves both sides). For a 0-height wing (w,0,d) only the two `w×d` regions on the top row are used; paint both (the underside is visible through NO_CULL). Texture sampling is nearest-neighbour, entity textures are not mipmapped. Alpha: both `rendertype_entity_cutout.fsh` and `rendertype_entity_translucent.fsh` in the 1.21.1 client jar contain `if (color.a < 0.1) discard;` **[H, extracted from the cached client jar]** – so unused texels must be fully transparent, cutout draws any texel with alpha ≥ 0.1 fully opaque, and translucent blends alpha but still drops anything below 0.1 (final alpha = texel alpha × vertex/tint alpha, so keep wing membrane ≥ ~100/255 and ghost-stroke tint ≥ ~70/255 so the product stays above 0.1).

Python generator (PIL 10.4 / numpy 1.26 are installed for Python 3.9):
```python
from PIL import Image, ImageDraw
W = H = 64
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
def faces(u, v, w, h, dp):
    return {"top": (u+dp, v, w, dp), "bottom": (u+dp+w, v, w, dp), "right": (u, v+dp, dp, h),
            "front": (u+dp, v+dp, w, h), "left": (u+dp+w, v+dp, dp, h), "back": (u+2*dp+w, v+dp, w, h)}
def fill(rect, rgba):
    x, y, w, h = rect
    if w > 0 and h > 0: d.rectangle([x, y, x+w-1, y+h-1], fill=rgba)
TAN, THX, DARK, RED = (210,169,110,255), (184,144,90,255), (26,20,16,255), (184,50,30,255)
for r in faces(0, 0, 6, 6, 6).values(): fill(r, THX)          # thorax
for r in faces(24, 0, 5, 4, 4).values(): fill(r, (195,154,98,255))   # head
for k, r in faces(42, 0, 1, 3, 3).items(): fill(r, RED)       # eye (dither with (224,74,44) later)
ab = faces(0, 12, 5, 5, 8)
for r in ab.values(): fill(r, TAN)
for name in ("top", "left", "right"):                         # tergite stripes A2..A4 at z=2,4,6 px
    x, y, w, h = ab[name]
    for zz in (2, 4, 6): d.line([(x if name!="top" else x, y+zz)] if False else [(x, y+zz), (x+w-1, y+zz)], fill=DARK)
for r in faces(26, 12, 3, 3, 2).values(): fill(r, DARK)       # male black tip
wing = faces(0, 26, 4, 0, 14)
for key in ("top", "bottom"):
    x, y, w, h = wing[key]
    fill((x, y, w, h), (220,232,240,100))                     # membrane alpha ~0.4
    d.line([(x+1, y), (x+1, y+h-1)], fill=(110,90,60,210))    # vein L3
    d.line([(x+2, y), (x+3, y+h-1)], fill=(110,90,60,210))    # vein L4
for r in faces(42, 12, 4, 1, 1).values(): fill(r, (164,130,79,255))  # femur; tibia (42,14), tarsus (42,16) likewise
img.save("fly.png")
```
(Fix the stripe loop to draw a horizontal line at rows y+zz across each side/top face; the snippet shows the atlas math, not final art.) Save as 8-bit RGBA PNG; Minecraft loads it via `assets/<modid>/textures/entity/fly.png`.

### 2.6 Render types for wings and eyes (javap of RenderType static init [H])
* `RenderType.entityTranslucent(tex)` = `NEW_ENTITY`, QUADS, shader `rendertype_entity_translucent`, `TRANSLUCENT_TRANSPARENCY`, **`NO_CULL`**, `LIGHTMAP`, `OVERLAY`, depth write on (default), affectsOutline. Exactly what a two-sided, 40 %-alpha wing needs. Vanilla precedent: `SlimeOuterLayer` renders with `RenderType.entityTranslucent`.
* `RenderType.entityCutoutNoCull(tex)` (default for all `EntityModel`s): `NO_TRANSPARENCY` (alpha test only) + no cull. `entityCutout(tex)` culls back faces.
* `RenderType.entityTranslucentEmissive(tex)`: translucent + NO_CULL, ignores lightmap (glowing wing/eye tint).
* `RenderType.eyes(tex)`: additive (`ADDITIVE_TRANSPARENCY`), `COLOR_WRITE` only (no depth write), NO_CULL, full-bright – paint an eyes-only texture (black elsewhere) and add an `EyesLayer` subclass returning `RenderType.eyes(FLY_EYES_TEX)` for glowing red eyes.
* Colour/alpha per draw: `ModelPart.render(pose, vc, light, overlay, int argb)`; build with `FastColor.ARGB32.color(a, r, g, b)` or `FastColor.ARGB32.colorFromFloat(a, r, g, b)` (1.21.1 has `FastColor`, not `ARGB`) **[H]**. `OverlayTexture.NO_OVERLAY`, `LightTexture.FULL_BRIGHT = 15728880` **[H]**.

Wing layer pattern (recommended over making the whole model translucent, which puts the body into the sorted translucent batch):
```java
public final class FlyWingLayer extends RenderLayer<FlyEntity, FlyModel> {
    public FlyWingLayer(RenderLayerParent<FlyEntity, FlyModel> p) { super(p); }
    @Override public void render(PoseStack ps, MultiBufferSource buf, int light, FlyEntity fly,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float yaw, float pitch) {
        FlyModel m = getParentModel();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(getTextureLocation(fly)));
        ps.pushPose();
        m.body.translateAndRotate(ps);                 // wings are children of body -> apply parent transform
        m.leftWing.visible = m.rightWing.visible = true;
        m.leftWing.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        m.rightWing.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        if (fly.isFlying()) {                          // cheap motion blur: 2 ghost strokes per wing
            int ghost = FastColor.ARGB32.color(70, 255, 255, 255);
            for (float dz : new float[]{-0.6F, 0.6F}) {
                float l = m.leftWing.zRot, r = m.rightWing.zRot;
                m.leftWing.zRot = l + dz; m.rightWing.zRot = r - dz;
                m.leftWing.render(ps, vc, light, OverlayTexture.NO_OVERLAY, ghost);
                m.rightWing.render(ps, vc, light, OverlayTexture.NO_OVERLAY, ghost);
                m.leftWing.zRot = l; m.rightWing.zRot = r;
            }
        }
        m.leftWing.visible = m.rightWing.visible = false;   // keep them out of the cutout pass
        ps.popPose();
    }
}
```
In `FlyModel`'s constructor set `leftWing.visible = rightWing.visible = false`. `ModelPart.render` skips invisible parts (and their children). Alternative (simplest): `super(RenderType::entityTranslucent)` in the model constructor and paint wing alpha in the same PNG – works because `LivingEntityRenderer.getRenderType` calls `model.renderType(tex)` for visible mobs.

### 2.7 setupAnim formulas (radians; 20 ticks/s; call `root().getAllParts().forEach(ModelPart::resetPose)` first)
Behaviour state comes from the entity (synced byte: IDLE, WALK, FLY, FEED, SING, GROOM…); `p` values are 0..1 blend factors you lerp over a few ticks (store in `AnimationState`s or floats updated in `prepareMobModel`).

**Head**: `head.yRot = netHeadYaw*DEG_TO_RAD*0.4F; head.xRot = headPitch*DEG_TO_RAD*0.4F;` (flies barely turn their heads; keep it subtle).

**Flight (wing flutter)** – vanilla-style, aliasing-safe (200 Hz cannot be displayed; ~6.7 Hz visual + ghost strokes reads as a buzz):
```java
float t = ageInTicks * 2.1F;                      // 2.1 rad/tick = 120.3°/tick, same cadence as vanilla Bee (FLAP_DEGREES_PER_TICK = 120.32113F)
leftWing.yRot  =  1.25F;  rightWing.yRot = -1.25F;             // swing out to the sides (~72° from body axis)
float stroke   = Mth.cos(t) * 1.15F;                            // ±66° -> 132° total, Drosophila ~130° (Fry 2005)
leftWing.zRot  = -0.35F + stroke;  rightWing.zRot =  0.35F - stroke;   // stroke plane tilted 20° above horizontal
leftWing.xRot  =  Mth.sin(t) * 0.35F; rightWing.xRot = -Mth.sin(t) * 0.35F; // wing rotation 90° out of phase -> figure-8 tip path
leftHaltere.zRot = -Mth.cos(t) * 0.8F; rightHaltere.zRot = Mth.cos(t) * 0.8F; // antiphase to wings (verified biology)
// legs tucked: femur toward body, tibia folded, hind legs trailing
for (left legs)  { femur.zRot = 0.6F;  tibia.zRot = 1.7F; } for (right legs) { femur.zRot = -0.6F; tibia.zRot = -1.7F; }
leftHind.yRot = -1.2F; rightHind.yRot = 1.2F;
body.xRot = -0.25F - Mth.clamp((float) fly.getDeltaMovement().y * 3F, -0.5F, 0.5F);  // nose-up hover, pitch with climb
body.y = 18F - 1.0F + Mth.sin(ageInTicks * 0.35F) * 0.4F;      // hover bob
```
Takeoff: legs extend downward for 2 ticks (`femur.zRot -= 0.5`), then wings unfold `yRot = Mth.lerp(pTakeoff, 0.12F, 1.25F)` over 2–3 ticks. Landing = reverse.

**Rest**: `leftWing.yRot = 0.12F; rightWing.yRot = -0.12F; zRot = 0` (flat over the abdomen, right wing 0.1 px lower). Antennae twitch: `xRot = -0.5F + Mth.cos(ageInTicks*0.18F)*0.08F` (the bee uses `cos(age*0.18)*PI*0.03`). Abdomen "breathing": `abdomen.zScale = 1F + Mth.sin(ageInTicks*0.18F)*0.03F`.

**Walking – tripod gait** (phase A = {L1, R2, L3} at 0, phase B = {R1, L2, R3} at π):
```java
float f    = limbSwing * 2.5F;                 // vanilla uses 0.6662F; 2.5F gives ~4x the cadence (still << 16 Hz real, but reads as scurrying)
float amp  = 0.5F  * limbSwingAmount;          // protraction/retraction ±0.5 rad
float lift = 0.35F * limbSwingAmount;
// per leg: side = +1 left / -1 right ; phase = 0 (L1,R2,L3) or PI (R1,L2,R3) ; restYaw = +0.7 / 0 / -0.7 (front/mid/hind, times side)
float ph    = f + phase;
float swing = Mth.cos(ph) * amp;
float up    = Math.max(0F, Mth.sin(ph)) * lift;          // leg lifts only during its swing half-cycle
femur.yRot  = side * (restYaw + swing);
femur.zRot  = side * (-0.35F - up);
tibia.zRot  = side * ( 1.10F + up * 0.8F);               // knee flexes more while lifted
tarsus.zRot = side * (-0.20F);
body.y      = 18F + Math.abs(Mth.cos(f)) * 0.15F * limbSwingAmount;   // tiny bounce
```
If you prefer speed-independent cadence when moving: `f = ageInTicks * 1.9F` (≈6 Hz) gated by `limbSwingAmount > 0.05F`.

**Feeding – proboscis extension** (`pFeed` ramps 0→1 over ~5 ticks = 0.25 s, matching the 0.2–0.6 s PER):
```java
proboscis.xRot   = Mth.lerp(pFeed, 1.2F, -0.15F);      // folded back under the head -> pointing down/forward
proboscis.yScale = Mth.lerp(pFeed, 1.0F, 1.8F);        // rostrum/haustellum telescope (3 px -> 5.4 px); labellum child inherits the scale
labellum.xScale  = labellum.zScale = Mth.lerp(pFeed, 1.0F, 1.4F);   // labellar lobes spread
head.xRot       += pFeed * 0.35F;                      // head tips toward the food
proboscis.yScale += pFeed * Mth.sin(ageInTicks * 0.9F) * 0.08F;      // pumping (~2.9 Hz visual; real cibarial pump rate not verified)
```

**Courtship song – unilateral wing extension** (`side` = wing nearest the female; real angles 50–70° for sine/Pslow, 5–30° for Pfast):
```java
ModelPart w = side > 0 ? leftWing : rightWing;
w.yRot  = side * 1.1F;                                   // ~63° extension
w.zRot  = side * 0.15F + Mth.sin(ageInTicks * 4.0F) * 0.08F * songEnvelope;   // fast tremor (~13 Hz visual stand-in for 150–250 Hz)
// songEnvelope = 0.5F + 0.5F*Mth.sin(ageInTicks*0.6F) alternates pulse/sine bouts every ~1 s; other wing stays folded
```
Optionally swing the abdomen tip toward the female (`abdomen.yRot = side*0.15F`) while the male orients/taps.

**Grooming – front-leg rubbing** (6 Hz rubs, bouts alternating every ~2 s / 40 ticks):
```java
float g = Mth.sin(ageInTicks * 1.9F);                    // 1.9 rad/tick = 6.05 Hz (real 5–7 Hz)
boolean rubBout = ((fly.tickCount / 40) & 1) == 0;       // alternate leg-rubbing <-> head-sweeping bouts (0.3–0.6 Hz)
leftFront.yRot  =  1.3F;  rightFront.yRot = -1.3F;       // both front legs forward under the head
leftFrontTibia.zRot = 1.9F; rightFrontTibia.zRot = -1.9F; // strongly flexed
if (rubBout) { leftFront.zRot = -0.2F + g*0.25F; rightFront.zRot = 0.2F + g*0.25F; }   // same-sign offsets = legs move in opposition (rubbing)
else         { leftFront.zRot = -0.6F + g*0.2F;  rightFront.zRot = 0.6F - g*0.2F; head.xRot = 0.35F + g*0.15F; } // synchronous head sweeps
body.xRot = 0.15F;                                       // crouched
```
Middle/hind legs keep the rest pose; the tripod formula is not applied while grooming.

### 2.8 Renderer & registration skeleton (mojmap)
```java
public class FlyRenderer extends MobRenderer<FlyEntity, FlyModel> {
    private static final ResourceLocation TEX   = ResourceLocation.fromNamespaceAndPath(MODID, "textures/entity/fly.png");
    private static final ResourceLocation TEX_F = ResourceLocation.fromNamespaceAndPath(MODID, "textures/entity/fly_female.png");
    public FlyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new FlyModel(ctx.bakeLayer(FlyModel.LAYER)), 0.12F);
        addLayer(new FlyWingLayer(this));
        addLayer(new FlyEyesLayer(this));          // extends EyesLayer, renderType() -> RenderType.eyes(EYES_TEX)
    }
    @Override protected void scale(FlyEntity fly, PoseStack ps, float pt) { float s = fly.isFemale() ? 0.40F : 0.35F; ps.scale(s, s, s); }
    @Override public ResourceLocation getTextureLocation(FlyEntity fly) { return fly.isFemale() ? TEX_F : TEX; }
}
// client entrypoint
EntityModelLayerRegistry.registerModelLayer(FlyModel.LAYER, FlyModel::createBodyLayer);   // LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MODID,"fly"), "main")
EntityRendererRegistry.register(ModEntities.FLY, FlyRenderer::new);
// entity type (common)
EntityType<FlyEntity> FLY = Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MODID,"fly"),
    EntityType.Builder.of(FlyEntity::new, MobCategory.CREATURE).sized(0.3F, 0.2F).eyeHeight(0.12F).clientTrackingRange(10).updateInterval(2).build("fly"));
// in FlyEntity: @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 64*64; }   // default cutoff ~30 blocks for this bbox
```
Flying helpers that exist: `FlyingMoveControl(Mob, int maxTurn, boolean hoversInPlace)`, `FlyingPathNavigation(Mob, Level)` (`setCanOpenDoors/PassDoors`), `Attributes.FLYING_SPEED`, interface `FlyingAnimal.isFlying()` **[H]**.

---

## 3. Sound

### 3.1 Vanilla facts (javap [H])
* `SoundEvents.BEE_LOOP`, `BEE_LOOP_AGGRESSIVE`, `BEE_POLLINATE`, `BEE_HURT`, `BEE_DEATH`, `BEE_STING`, `PARROT_FLY`, `SILVERFISH_AMBIENT/STEP`, `SPIDER_AMBIENT`, `ELYTRA_FLYING` all exist in 1.21.1.
* `SoundEvent.createVariableRangeEvent(ResourceLocation)` and `createFixedRangeEvent(ResourceLocation, float range)`; `BuiltInRegistries.SOUND_EVENT` is `Registry<SoundEvent>`; `Registry.register(Registry<? super T>, String|ResourceLocation, T)`.
* The bee's loop is **client-side**: `ClientPacketListener.postAddEntitySoundInstance(Entity)` does `if (entity instanceof Bee bee) soundManager.queueTickingSound(bee.isAngry() ? new BeeAggressiveSoundInstance(bee) : new BeeFlyingSoundInstance(bee))`. `BeeSoundInstance extends AbstractTickableSoundInstance` (`ctor(SoundEvent, SoundSource, RandomSource)`, sets `looping=true; delay=0; volume=0`), `tick()`: if bee removed → `stop()`; else follow `x,y,z`; `pitch = lerp(clamp(horizontalSpeed,0,0.5)/0.5?, minPitch, maxPitch)`; `volume = lerp(clamp(speed,0,0.5), 0, 1.2)` (i.e. silent when hovering still, 1.2 at ≥0.5 blocks/tick); adult `minPitch 0.7 / maxPitch 1.1`, baby `1.1 / 1.5`; `canStartSilent() = true`; `canPlaySound() = !bee.isSilent()`; switches to the other instance via `shouldSwitchSounds()`.
* `Bee.getAmbientSound()` returns **null**, `Bee.getSoundVolume() = 0.4F` (applies to hurt/death). `Mob.getAmbientSoundInterval() = 80`; `Mob.baseTick` plays the ambient sound when `random.nextInt(1000) < ambientSoundTime++`. `LivingEntity.getVoicePitch() = (rand-rand)*0.2 + 1.0` (baby +1.5).
* `Entity.move` calls `if (isFlapping()) onFlap()` and emits `GameEvent.FLAP`; `Bee.isFlapping() = isFlying() && tickCount % TICKS_PER_FLAP == 0` with `TICKS_PER_FLAP = Mth.ceil(1.4959966F) = 2`; `Parrot.onFlap()` = `playSound(SoundEvents.PARROT_FLY, 0.15F, getPitch(random))`. **The renderer (`MobRenderer`) never triggers sounds**; sounds are entity-side (`Entity.playSound(SoundEvent, float volume, float pitch)` broadcasts from the server) or client `SoundInstance`s.
* `SoundManager.play(SoundInstance)`, `queueTickingSound(TickableSoundInstance)`, `stop(SoundInstance)`, `isActive(SoundInstance)`; `Minecraft.getInstance().getSoundManager()`. `SoundEngine.calculatePitch` clamps to `[0.5, 2.0]` **[M – 0.5F literal seen; 2.0 upper bound standard]**.
* `SoundSource.NEUTRAL` (mobs), `AMBIENT`, `HOSTILE`, … exist.

### 3.2 Measured spectrum of the vanilla bee loop (this machine, ffmpeg decode → numpy FFT)
| File | Format | Peaks (Hz, relative amplitude) |
|---|---|---|
| `mob/bee/loop1.ogg` | mono, 48 kHz Vorbis, 5.94 s | **212.0 (1.00)**, 104.5 (0.65), 74.2 (0.43), 316.3 (0.40), 148.5 (0.26), 630 (0.18) |
| `mob/bee/loop2.ogg` | mono, 48 kHz, 4.21 s | **206.7 (1.00)**, 103.4 (0.98), 74.6 (0.59), 310.1 (0.49) |
| `mob/bee/aggressive1.ogg` | mono, 48 kHz, 3.85 s | 252.9 (1.00), 126.6 (0.87), 91.2 (0.48), 379.5 (0.41) |
| `mob/parrot/fly1.ogg` | mono, 0.50 s | broadband flutter 520–1700 Hz (a wing "whoosh", not a buzz) |

Conclusion **[H]**: the bee loop's fundamental (~210 Hz) already sits at Drosophila's 200–220 Hz. Recommended `BEE_LOOP` pitch for the fly: **1.0–1.2** (210–255 Hz, covering the 215–261 Hz free-flight range); speed-modulated like the bee (`lerp(speed, 1.0, 1.25)`). Pitch 1.4–1.8 (300–380 Hz) sounds like a house fly/mosquito – use it only if you want a cartoonish "small" cue. `BEE_LOOP_AGGRESSIVE` (~253 Hz) at pitch 0.85–1.0 is an alternative timbre for "excited/courting". Note the sub-harmonics at f/2 and f/3 in the vanilla files: keep them in a custom synth for a similar warmth.

### 3.3 Client loop instance for the fly (mirror of BeeSoundInstance)
```java
public class FlyBuzzSound extends AbstractTickableSoundInstance {
    private final FlyEntity fly;
    public FlyBuzzSound(FlyEntity fly, SoundEvent ev) {
        super(ev, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());   // ev = SoundEvents.BEE_LOOP or ModSounds.FLY_BUZZ
        this.fly = fly; this.looping = true; this.delay = 0; this.volume = 0F;
        this.x = fly.getX(); this.y = fly.getY(); this.z = fly.getZ();
    }
    @Override public void tick() {
        if (fly.isRemoved()) { stop(); return; }
        x = fly.getX(); y = fly.getY(); z = fly.getZ();
        if (fly.isFlying()) {
            float spd = Mth.clamp((float) fly.getDeltaMovement().horizontalDistance() / 0.4F, 0F, 1F);
            volume = Mth.lerp(spd, 0.25F, 0.6F);
            pitch  = Mth.lerp(spd, 1.0F, 1.25F);
        } else { volume = 0F; }                         // silent when landed; keep instance alive so takeoff is instant
    }
    @Override public boolean canStartSilent() { return true; }
    @Override public boolean canPlaySound() { return !fly.isSilent(); }
}
// hook (client entrypoint): fabric-lifecycle-events-v1
ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
    if (entity instanceof FlyEntity fly) Minecraft.getInstance().getSoundManager().queueTickingSound(new FlyBuzzSound(fly, SoundEvents.BEE_LOOP));
});
```
`ClientEntityEvents.ENTITY_LOAD` / `ENTITY_UNLOAD` signatures: `onLoad(Entity, ClientLevel)` / `onUnload(Entity, ClientLevel)` **[H, javap fabric-lifecycle-events-v1]**. For one-shots (takeoff click, landing, song): override `getAmbientSound()`/`playAmbientSound()` on the entity, or call `fly.playSound(event, vol, pitch)` from server-side behaviour code.

### 3.4 Custom OGG buzz (ffmpeg N-118328 with `libvorbis` is on PATH; numpy/scipy available)
Requirements **[H, minecraft.wiki Sounds.json + Fabric docs]**: OGG Vorbis; **mono** (stereo files are never attenuated by distance); `assets/<ns>/sounds/<path>.ogg`; `assets/<ns>/sounds.json`; `stream:true` recommended only for clips longer than a few seconds (keep the loop ≤ 3 s and `stream:false`). Make the loop length an exact number of cycles so `looping=true` is seamless.

numpy → WAV → ffmpeg:
```python
import numpy as np, wave, subprocess
sr, f0, dur = 48000, 220.0, 2.0                 # 220 Hz * 2.0 s = 440 whole cycles -> seamless loop
t = np.arange(int(sr*dur)) / sr
ph = 2*np.pi*f0*t
sig = sum((1.0/k**1.3) * np.sin(k*ph) for k in range(1, 9))       # sawtooth-like harmonic stack (wing-tone timbre)
sig += 0.30*np.sin(ph/2) + 0.15*np.sin(ph/3)                     # f/2, f/3 sub-harmonics like the vanilla bee
am  = 0.85 + 0.15*np.sin(2*np.pi*5.5*t)                          # 5.5 Hz = 11 cycles in 2 s -> still seamless
x = np.tanh(1.4 * sig/np.max(np.abs(sig))) * am
x += np.random.default_rng(7).normal(0, 0.015, x.size)           # air noise
x *= 0.8 / np.max(np.abs(x))
with wave.open("fly_buzz.wav", "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes((x*32767).astype("<i2").tobytes())
subprocess.run(["ffmpeg","-y","-i","fly_buzz.wav","-ac","1","-c:a","libvorbis","-q:a","5","fly_buzz.ogg"], check=True)
```
ffmpeg-only one-liner (no Python): `ffmpeg -f lavfi -i "aevalsrc='0.5*sin(2*PI*220*t)+0.25*sin(2*PI*440*t)+0.12*sin(2*PI*660*t)+0.08*sin(2*PI*880*t)+0.15*sin(2*PI*110*t)':s=48000:d=2" -af "tremolo=f=5.5:d=0.3,volume=0.8" -ac 1 -c:a libvorbis -q:a 5 fly_buzz.ogg`.

`assets/<modid>/sounds.json` (fields per minecraft.wiki: sound object `name` (required), `volume` >0 default 1, `pitch` >0 default 1, `weight` ≥1, `stream` false, `attenuation_distance` 16, `preload` false, `type` "file"|"event" (javap `Sound$Type`: "file", "event"); event object: `replace`, `subtitle`, `sounds`):
```json
{ "entity.fly.buzz": { "subtitle": "subtitles.<modid>.fly_buzz",
                       "sounds": [ { "name": "<modid>:entity/fly/fly_buzz", "stream": false, "attenuation_distance": 16 } ] } }
```
Registration (Fabric docs pattern, mojmap names): 
```java
public static final SoundEvent FLY_BUZZ = Registry.register(BuiltInRegistries.SOUND_EVENT,
    ResourceLocation.fromNamespaceAndPath(MODID, "entity.fly.buzz"),
    SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "entity.fly.buzz")));
```
Register in the common initializer before the client tries to play it.

---

## 4. Brain visualisation in-world (Fabric 0.107, 1.21.1)

### 4.1 Which event, which API (Fabric javadoc 1.21.1 branch + javap [H])
* `WorldRenderContext` (mojmap): `LevelRenderer worldRenderer()`, `PoseStack matrixStack()` (non-null from `AFTER_ENTITIES` on), `DeltaTracker tickCounter()`, `Camera camera()`, `GameRenderer gameRenderer()`, `LightTexture lightmapTextureManager()`, `Matrix4f projectionMatrix()`, `Matrix4f positionMatrix()`, `ClientLevel world()`, `ProfilerFiller profiler()`, `boolean advancedTranslucency()`, `MultiBufferSource consumers()` (**null before BEFORE_ENTITIES and after BEFORE_DEBUG_RENDER**), `Frustum frustum()` (null in START).
* Events: `START, AFTER_SETUP, BEFORE_ENTITIES, AFTER_ENTITIES` ("append block-related quads" – consumers available), `BEFORE_BLOCK_OUTLINE, BLOCK_OUTLINE, BEFORE_DEBUG_RENDER, AFTER_TRANSLUCENT` ("after entity, terrain and particle translucent layers … before translucency combine in fabulous mode; **vertex consumers unavailable, rendering must be direct to framebuffer**"), `LAST, END`. Functional method: `void afterTranslucent(WorldRenderContext ctx)` etc.
* 1.21.1 world PoseStack: `LevelRenderer.renderLevel` does `RenderSystem.getModelViewStack().pushMatrix().mul(frustumMatrix); RenderSystem.applyModelViewMatrix(); PoseStack poseStack = new PoseStack();` – i.e. **`context.matrixStack()` is identity** and the camera rotation lives in `RenderSystem.getModelViewStack()`, which both `BufferUploader.drawWithShader` (via the shader's ModelViewMat) and the world `MultiBufferSource` batches pick up automatically. `frustumMatrix = new Matrix4f().rotation(camera.rotation().conjugate())` is built in `GameRenderer.renderLevel` and is what Fabric exposes as `positionMatrix()`. So: use **camera-relative coordinates** (`world − context.camera().getPosition()`), multiply by `context.matrixStack().last().pose()` for forward-compat, and **do not** also multiply by `positionMatrix()` (double rotation). This is exactly what the working falcraft `GhostBlockRenderer`/`ImageCanvasRenderer` do (`C:\Users\drini\OneDrive\Documents\fal-dev\minecraft\falcraft\src\client\java\com\falcraft\render\`).
* Immediate-mode API (javap): `Tesselator.getInstance().begin(VertexFormat.Mode, VertexFormat) -> BufferBuilder`; `BufferBuilder.build()` (null if empty) / `buildOrThrow()` → `MeshData`; `BufferUploader.drawWithShader(MeshData)`; `RenderSystem.setShader(GameRenderer::getPositionColorShader | getPositionTexShader | getPositionTexColorShader | getRendertypeLinesShader)`, `setShaderTexture(int, ResourceLocation)`, `enableBlend/disableBlend/defaultBlendFunc/blendFunc(SourceFactor,DestFactor)`, `depthMask(bool)`, `enableDepthTest/disableDepthTest`, `disableCull/enableCull`, `lineWidth(float)`, `setShaderColor(r,g,b,a)`. `VertexConsumer`: `addVertex(float,float,float) | addVertex(Matrix4f,x,y,z) | addVertex(PoseStack.Pose,x,y,z)`, `setColor(int r,int g,int b,int a) | setColor(float,float,float,float) | setColor(int argb)`, `setUv`, `setUv1/setOverlay`, `setUv2/setLight`, `setNormal(x,y,z) | setNormal(Pose,x,y,z)`. Formats: `DefaultVertexFormat.POSITION, POSITION_COLOR, POSITION_COLOR_NORMAL, POSITION_TEX, POSITION_TEX_COLOR, POSITION_COLOR_LIGHTMAP, NEW_ENTITY, PARTICLE`; modes `LINES, LINE_STRIP, DEBUG_LINES, DEBUG_LINE_STRIP, TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN, QUADS`.
* Batched RenderTypes (for `AFTER_ENTITIES` via `context.consumers().getBuffer(...)`): `RenderType.debugQuads()` = POSITION_COLOR, QUADS, `TRANSLUCENT_TRANSPARENCY`, `NO_CULL` (depth test on, depth write on); `RenderType.debugFilledBox()` = TRIANGLE_STRIP + `VIEW_OFFSET_Z_LAYERING` + translucent; `RenderType.lines()`/`lineStrip()` = **POSITION_COLOR_NORMAL**, `rendertype_lines` shader (needs `setNormal` = line direction), translucent, `ITEM_ENTITY_TARGET`, `COLOR_DEPTH_WRITE`, NO_CULL, default width `max(2.5, width/1920*2.5)` px; `RenderType.debugLineStrip(double width)` = POSITION_COLOR, DEBUG_LINE_STRIP, no transparency. Helper: `LevelRenderer.renderLineBox(PoseStack, VertexConsumer, AABB|minmax, r,g,b,a)` shows the normal-setting pattern.
* `Camera`: `getPosition()`, `rotation()`, `getLookVector()`, `getUpVector()`, `getLeftVector()` (Vector3f) – use the last two to build camera-facing billboards.

### 4.2 Recommended implementation: one draw call of additive billboards (AFTER_TRANSLUCENT)
```java
WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
    BrainViz viz = BrainViz.get(); if (viz == null || viz.count == 0) return;   // positions float[3n] in world space, activity float[n]
    Vec3 cam = ctx.camera().getPosition();
    Matrix4f pose = ctx.matrixStack().last().pose();            // identity in 1.21.1, kept for forward compat
    Vector3f L = ctx.camera().getLeftVector(), U = ctx.camera().getUpVector();
    RenderSystem.setShader(GameRenderer::getPositionColorShader);
    RenderSystem.enableBlend();
    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);   // additive "glow"
    RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
    BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    for (int i = 0; i < viz.count; i++) {
        float x = (float)(viz.pos[3*i] - cam.x), y = (float)(viz.pos[3*i+1] - cam.y), z = (float)(viz.pos[3*i+2] - cam.z);
        float a = viz.act[i];                                    // 0..1 (e.g. exponentially decayed spike trace)
        float s = 0.015F + 0.05F * a;                            // half-size in blocks
        int col = viz.color[i];                                  // RGB by neurotransmitter/superclass
        int alpha = (int)(30 + 225 * a);
        bb.addVertex(pose, x - L.x*s - U.x*s, y - L.y*s - U.y*s, z - L.z*s - U.z*s).setColor(FastColor.ARGB32.red(col), FastColor.ARGB32.green(col), FastColor.ARGB32.blue(col), alpha);
        bb.addVertex(pose, x + L.x*s - U.x*s, y + L.y*s - U.y*s, z + L.z*s - U.z*s).setColor(...);
        bb.addVertex(pose, x + L.x*s + U.x*s, y + L.y*s + U.y*s, z + L.z*s + U.z*s).setColor(...);
        bb.addVertex(pose, x - L.x*s + U.x*s, y - L.y*s + U.y*s, z - L.z*s + U.z*s).setColor(...);
    }
    MeshData md = bb.build(); if (md != null) BufferUploader.drawWithShader(md);
    // synapse lines: Tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR), 2 vertices/segment, 1 px wide
    RenderSystem.depthMask(true); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend(); RenderSystem.enableCull();
});
```
Performance guidance **[M – based on API structure and typical numbers]**: 5 000 billboards = 20 000 vertices ≈ 0.4 MB, well under `RenderType.BIG_BUFFER_SIZE`; the Java loop (~5k iterations) costs ~0.1–0.3 ms; 50–100k points are still one draw call but consider a cached `VertexBuffer`. Keep synapse lines ≤ ~20k segments/frame (DEBUG_LINES are 1 px GL lines; `RenderType.lines()` gives thick lines but needs a normal per vertex). Restore GL state exactly as shown (falcraft does the same) or later HUD rendering breaks. With Fabulous graphics, AFTER_TRANSLUCENT runs before the translucency combine; if the glow ends up behind water use `WorldRenderEvents.LAST`. To fade with distance from the fly, scale alpha by `1/(1+dist²)`. Put the point cloud where the fly's head is (`fly.getEyePosition(partialTick)`) or as a floating "hologram" 1.5 blocks above it, and rotate the neuron cloud with `fly.getYRot()`.

### 4.3 Particle alternative (javap [H])
* `ParticleTypes.END_ROD` (SimpleParticleType): lifetime `60 + random.nextInt(12)` ticks, drift velocity ×0.0125, quadSize scale 0.75, fades to colour `0xF2DFB9` (15916745). Bright white-yellow, no colour control.
* `ParticleTypes.DUST` with `new DustParticleOptions(new Vector3f(r,g,b), float scale)`, `scale` clamped to `[0.01, 4.0]` (`ScalableParticleOptionsBase.MIN_SCALE/MAX_SCALE`); lifetime `(int)(8.0/(random*0.8+0.2)) * scale` ticks (≈ 8–40 at scale 1), quadSize ≈ `0.1*(random*0.5+0.5)*0.75*scale`… – colourable, cheap, short-lived. `DustColorTransitionOptions(from, to, scale)` for spike→decay colour.
* `ParticleTypes.ELECTRIC_SPARK`, `GLOW`, `SCRAPE`, `WAX_ON/OFF`, `SOUL`, `ENCHANT`, `FLAME/SMALL_FLAME` also exist.
* Client: `ClientLevel.addParticle(ParticleOptions, double x,y,z, double dx,dy,dz)` or `addParticle(options, boolean force, …)` / `addAlwaysVisibleParticle(...)`. Non-forced particles are subject to the Particles setting (`LevelRenderer.calculateParticleLevel`: `ParticleStatus.MINIMAL` drops them, `DECREASED` drops 1/3) and to a ~32-block distance check – pass `force = true` for the viz.
* Hard cap: `ParticleEngine.MAX_PARTICLES_PER_LAYER = 16384` per texture layer (oldest evicted). Budget: 500 particles/tick × END_ROD's ~66-tick life ≈ 33k live → over cap; 500/tick × DUST(scale 0.6, ~5–24 ticks) ≈ 7k live → fine. So for "~500 points per tick" use DUST with scale ≤ 0.6, or emit END_ROD at ≤ 200/tick.
* Server: `ServerLevel.sendParticles(T options, x,y,z, int count, dx,dy,dz, double speed)` sends one `ClientboundLevelParticlesPacket` per call to players within 32 blocks (512 with `force`) – do not call it 500×/tick; ship neuron activity in one custom payload and spawn client-side.

---

## 5. Mod icon (procedural, PIL)
* `fabric.mod.json` `"icon": "assets/<modid>/icon.png"`; spec: "a square .PNG file (Minecraft resource packs use 128×128, but that is not a hard requirement – a power of two is recommended)"; alternatively a `{ "128": "…", "256": "…" }` size map **[H, Fabric fabric.mod.json spec]**. falcraft ships 256×256 RGB.
* Recipe: draw at 512×512 with anti-aliasing, then `resize((128,128), Image.LANCZOS)`.
```python
from PIL import Image, ImageDraw, ImageFilter
S = 512; im = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.rounded_rectangle([0, 0, S-1, S-1], radius=96, fill=(28, 22, 34, 255))                 # dark plum background
# wings (translucent, drawn first)
for sign in (-1, 1):
    wing = Image.new("RGBA", (S, S), (0,0,0,0)); wd = ImageDraw.Draw(wing)
    wd.ellipse([S*0.5 + sign*S*0.02 - (S*0.30 if sign<0 else 0), S*0.30, S*0.5 + sign*S*0.02 + (S*0.30 if sign>0 else 0), S*0.60],
               fill=(220, 232, 240, 110), outline=(110, 90, 60, 200), width=4)
    im.alpha_composite(wing.rotate(sign*-25, center=(S*0.5, S*0.45), resample=Image.BICUBIC))
d.ellipse([S*0.38, S*0.42, S*0.62, S*0.86], fill=(210,169,110,255))                        # abdomen
for y in (0.56, 0.64, 0.72): d.line([(S*0.40, S*y), (S*0.60, S*y)], fill=(26,20,16,255), width=8)  # stripes
d.pieslice([S*0.38, S*0.62, S*0.62, S*0.86], 0, 180, fill=(26,20,16,255))                  # male black tip
d.ellipse([S*0.40, S*0.30, S*0.60, S*0.50], fill=(184,144,90,255))                         # thorax
d.ellipse([S*0.41, S*0.16, S*0.59, S*0.34], fill=(195,154,98,255))                         # head
d.ellipse([S*0.40, S*0.19, S*0.48, S*0.31], fill=(184,50,30,255)); d.ellipse([S*0.52, S*0.19, S*0.60, S*0.31], fill=(184,50,30,255))  # eyes
# brain: pink lobes + glowing spikes above the head
brain = Image.new("RGBA", (S, S), (0,0,0,0)); bd = ImageDraw.Draw(brain)
for cx, cy, r in ((0.40,0.10,0.09),(0.50,0.07,0.10),(0.60,0.10,0.09),(0.45,0.15,0.07),(0.55,0.15,0.07)):
    bd.ellipse([S*(cx-r), S*(cy-r), S*(cx+r), S*(cy+r)], fill=(255,150,170,255), outline=(190,80,110,255), width=5)
glow = brain.filter(ImageFilter.GaussianBlur(18)); im.alpha_composite(glow); im.alpha_composite(brain)
import random; rnd = random.Random(3)
for _ in range(40):
    x, y = S*rnd.uniform(0.33, 0.67), S*rnd.uniform(0.0, 0.2); r = rnd.uniform(3, 7)
    d.ellipse([x-r, y-r, x+r, y+r], fill=(120, 255, 200, 255))                            # spikes
im.resize((128, 128), Image.LANCZOS).save("icon.png")
```

---

## 6. Vanilla size table for hitbox calibration (javap EntityType [H])
Bee 0.7×0.6 eye 0.3 (tracking 8); Silverfish 0.4×0.3 eye 0.13; Endermite 0.4×0.3 eye 0.13; Tadpole 0.4×0.3 eye 0.195; Cod 0.5×0.3 eye 0.195; Allay 0.35×0.6 eye 0.36; Rabbit 0.4×0.5; Chicken 0.4×0.7 eye 0.644; Parrot 0.5×0.9 eye 0.54; Bat 0.5×0.9 eye 0.45; Axolotl 0.75×0.42; Fox 0.6×0.7. Bee model body centre sits 5 px above ground for a 0.6-block hitbox and uses `clientTrackingRange(8)`; entities with `sized()` are `EntityDimensions.scalable`. `EntityType.Builder`: `sized, eyeHeight, spawnDimensionsScale, passengerAttachments, clientTrackingRange(int chunks), updateInterval(int ticks), fireImmune, noSummon, build(String)`.

## 7. Open questions / not verified
1. Absolute leg-segment lengths in mm (bioRxiv "Appendometer" 2025 and the 2024 leg-model preprint were rate-limited; only relative statements obtained).
2. Cibarial pump / proboscis pumping frequency during feeding – not found; 2–3 Hz visual is a guess.
3. Exact upper pitch clamp in `SoundEngine.calculatePitch` (only the 0.5F literal was seen; 2.0F is the long-standing value).
4. The wiki's `type` field value: javap shows the JSON strings `"file"` and `"event"` (the wiki text sometimes says "sound"); use `"file"` or omit.
5. Free-flight cruising speed of Drosophila (search budget exhausted before verification; literature values are roughly 0.2–0.5 m/s [L]).

## 8. Sources
* javap of the cached mojmap jars (see header) – all Minecraft API signatures, constants, render-state shards, vanilla model constants, EntityType dimensions, particle constants.
* Fabric API 0.107.0+1.21.1 nested jars (javap) and https://raw.githubusercontent.com/FabricMC/fabric/1.21.1/fabric-rendering-v1/src/client/java/net/fabricmc/fabric/api/client/rendering/v1/WorldRenderContext.java and WorldRenderEvents.java (javadoc quotes).
* Fabric docs: https://docs.fabricmc.net/develop/sounds/custom ; fabric.mod.json spec: https://wiki.fabricmc.net/documentation:fabric_mod_json_spec
* Minecraft wiki sounds.json: https://minecraft.wiki/w/Sounds.json
* Wikipedia *Drosophila melanogaster*: https://en.wikipedia.org/wiki/Drosophila_melanogaster ; Abdominal pigmentation: https://en.wikipedia.org/wiki/Abdominal_pigmentation_in_Drosophila_melanogaster ; Vanderbilt Drosophila phenotypes guide: https://researchguides.library.vanderbilt.edu/c.php?g=156859&p=1515661 ; Animal Diversity Web: https://animaldiversity.org/accounts/Drosophila_melanogaster/
* Wingbeat: https://pubmed.ncbi.nlm.nih.gov/9583239/ (202 Hz); https://www.researchgate.net/publication/382230438 (215–261 Hz); Fry, Sayaman & Dickinson 2005 https://journals.biologists.com/jeb/article/208/12/2303/15492 (~130° stroke).
* Halteres: https://pmc.ncbi.nlm.nih.gov/articles/PMC8629423/
* Walking: Mendes et al. 2013 https://pmc.ncbi.nlm.nih.gov/articles/PMC3545443/ ; Wosnitza 2013 https://journals.biologists.com/jeb/article/216/3/480/11966 ; Chun 2021 https://elifesciences.org/articles/65878 ; DeAngelis 2019 https://elifesciences.org/articles/46409
* Grooming: https://elifesciences.org/articles/71508 ; https://elifesciences.org/articles/02951
* Courtship song: Clemens et al. 2018 https://www.sciencedirect.com/science/article/pii/S0960982218307735 ; https://www.sciencedirect.com/science/article/pii/S0896627311000572 ; https://www.sciencedirect.com/science/article/abs/pii/S0003347276800760
* PER: https://pmc.ncbi.nlm.nih.gov/articles/PMC9647644/ ; https://pubmed.ncbi.nlm.nih.gov/18978998/
* Leg morphology (relative only): https://www.biorxiv.org/content/10.1101/2025.01.21.634122v1 ; https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0060261
* Reference working code (identical toolchain): `C:\Users\drini\OneDrive\Documents\fal-dev\minecraft\falcraft\src\client\java\com\falcraft\render\GhostBlockRenderer.java`, `ImageCanvasRenderer.java`, `FalcraftClient.java` (WorldRenderEvents.AFTER_TRANSLUCENT + camera().getPosition()).
