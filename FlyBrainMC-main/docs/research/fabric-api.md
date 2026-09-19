# Verified Fabric 1.21.1 + mojmap + Fabric API 0.107.0 API reference for the fruit-fly mob mod

Date: 2026-09-03. Toolchain: Minecraft 1.21.1, Fabric Loader 0.17.3, Fabric API 0.107.0+1.21.1, fabric-loom 1.12-SNAPSHOT, official Mojang mappings (mojmap), Java release 21, Gradle 9.1.0 wrapper, JDK 25.0.1 (Temurin) on PATH.

## 0. How this was verified

Every signature marked **javap: yes** was read with `javap -p` (and where noted `javap -c`) from these local jars:

- `C:\Users\drini\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-common\1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2\minecraft-common-1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2.jar` (mojmap, server+common classes)
- `C:\Users\drini\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-clientonly\1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2\minecraft-clientonly-1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2.jar` (mojmap, client-only classes: renderers, models, GuiGraphics, Minecraft, KeyMapping...)
- Fabric API 0.107.0+1.21.1 fat jar, nested module jars unzipped from `META-INF/jars/*.jar` (48 modules) to the scratch dir `scratchpad/fapi/mods/`. **Fabric API class files are in intermediary names** (`net.minecraft.class_1299` etc.); Loom remaps them to mojmap in the dev environment. Every intermediary name quoted below was translated with the Loom mappings file `C:\Users\drini\.gradle\caches\fabric-loom\1.21.1\loom.mappings.1_21_1.layered+hash.2198-v2\mappings.tiny` (header `tiny 2 0 official intermediary named`). The translation table is in section 13.
- `fabric-loader-0.17.3.jar` (for `FabricLoader`, `ModContainer`), `brigadier-1.3.10.jar` (for argument types).
- The Fabric API `-sources.jar` in the Gradle cache is only 6 KB (metadata only, 0 .java files) so Javadoc could not be read; behavior notes that come from Javadoc are marked as such.
- Raw javap dumps for ~190 classes are kept at `scratchpad/javap/<fully.qualified.Name>.txt` (`$` replaced with `_`).

Note on javap under Git Bash on Windows: a multi-entry `-cp` must use `:` as separator (MSYS converts it to `;`); using `;` silently yields "class not found" for everything.

## 1. Entity type registration

| Class | Member | Exact signature (mojmap) | javap |
|---|---|---|---|
| `net.minecraft.world.entity.EntityType$Builder<T extends Entity>` | `of` | `public static <T extends Entity> EntityType.Builder<T> of(EntityType.EntityFactory<T>, MobCategory)` | yes |
| `EntityType$Builder` | `sized` | `public EntityType.Builder<T> sized(float width, float height)` | yes |
| `EntityType$Builder` | `eyeHeight` | `public EntityType.Builder<T> eyeHeight(float)` | yes |
| `EntityType$Builder` | `clientTrackingRange` | `public EntityType.Builder<T> clientTrackingRange(int)` (default **5** chunks, from ctor bytecode `iconst_5`) | yes |
| `EntityType$Builder` | `updateInterval` | `public EntityType.Builder<T> updateInterval(int)` (default **3** ticks, `iconst_3`) | yes |
| `EntityType$Builder` | `fireImmune`, `noSummon`, `noSave`, `canSpawnFarFromPlayer`, `immuneTo(Block...)`, `spawnDimensionsScale(float)`, `requiredFeatures(FeatureFlag...)` | all return `EntityType.Builder<T>` | yes |
| `EntityType$Builder` | `build` | `public EntityType<T> build(String)` — the String is only a DataFixerUpper "choice type" id (`Util.fetchChoiceType(References.ENTITY_TREE, id)`), NOT the registry name | yes (javap -c) |
| `EntityType$Builder` (Fabric interface injection, `FabricEntityType.Builder`) | `build` | `public default EntityType<T> build()` — calls `build((String) null)`; Fabric's `EntityTypeBuilderMixin.allowNullId` wraps `Util.fetchChoiceType` and returns `null` when id is null (verified `ifnonnull / aconst_null / areturn`), so no DFU lookup and no error log | yes |
| `EntityType$Builder` (Fabric injected) | `alwaysUpdateVelocity` | `public default EntityType.Builder<T> alwaysUpdateVelocity(boolean)` | yes |
| `net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType$Builder` | `createMob` | `public static <T extends Mob> EntityType.Builder<T> createMob(EntityType.EntityFactory<T>, MobCategory, UnaryOperator<FabricEntityType.Builder.Mob<T>>)`; `Builder.Mob<T>` has `defaultAttributes(Supplier<AttributeSupplier.Builder>)` and `spawnRestriction(SpawnPlacementType, Heightmap.Types, SpawnPlacements.SpawnPredicate<T>)` | yes |
| `FabricEntityTypeBuilder` | class | **@Deprecated** in 0.107 (Deprecated attribute present) — do not use | yes (javap -v) |
| `EntityType$EntityFactory<T>` | `create` | `T create(EntityType<T>, Level)` — matches constructor ref `FlyEntity::new` with ctor `(EntityType<? extends FlyEntity>, Level)` | yes |
| `net.minecraft.core.Registry` | `register` | `public static <V, T extends V> T register(Registry<V>, ResourceLocation, T)`; also `(Registry<? super T>, String, T)` and `(Registry<V>, ResourceKey<V>, T)` | yes |
| `net.minecraft.core.registries.BuiltInRegistries` | fields | `DefaultedRegistry<EntityType<?>> ENTITY_TYPE`, `Registry<Item> ITEM` (exists; grep matched `ITEM `), `Registry<SoundEvent> SOUND_EVENT`, `Registry<ParticleType<?>> PARTICLE_TYPE`, `Registry<Attribute> ATTRIBUTE` | yes |
| `net.minecraft.resources.ResourceLocation` | `fromNamespaceAndPath` | `public static ResourceLocation fromNamespaceAndPath(String namespace, String path)`; also `parse(String)`, `withDefaultNamespace(String)`, `tryParse(String)`, `tryBuild(String,String)`. **Constructor is private** in 1.21.1 | yes |
| `net.minecraft.world.entity.MobCategory` | enum | `MONSTER, CREATURE, AMBIENT, AXOLOTLS, UNDERGROUND_WATER_CREATURE, WATER_CREATURE, WATER_AMBIENT, MISC` | yes |
| `net.minecraft.world.entity.EntityType` | misc | `public T create(Level)`; `public T spawn(ServerLevel, BlockPos, MobSpawnType)`; `public T spawn(ServerLevel, ItemStack, Player, BlockPos, MobSpawnType, boolean, boolean)`; `public static ResourceLocation getKey(EntityType<?>)`; `public boolean canSummon()`; `public int clientTrackingRange()`; `public int updateInterval()`; `public boolean trackDeltas()`; `public String getDescriptionId()` | yes |
| `net.minecraft.world.entity.MobSpawnType` | enum | `NATURAL, CHUNK_GENERATION, SPAWNER, STRUCTURE, BREEDING, MOB_SUMMONED, JOCKEY, EVENT, CONVERSION, REINFORCEMENT, TRIGGERED, BUCKET, SPAWN_EGG, COMMAND, DISPENSER, PATROL, TRIAL_SPAWNER` | yes |

DFU behavior of `build("fruitfly")` (verified by bytecode of `Util.doFetchChoiceType`): catches the missing-type exception, logs `LOGGER.error("No data fixer registered for {}")`, then throws only `if (SharedConstants.IS_RUNNING_IN_IDE)`. Nothing in the vanilla `net.minecraft.client.main.Main`, `net.minecraft.server.Main`, `SharedConstants` static init, or Fabric Loader 0.17.3 (`grep field_1125` over the loader jar = no hits) sets `IS_RUNNING_IN_IDE`, so with a string id you get one ERROR log line per registration and no crash. Using the Fabric-injected no-arg `build()` avoids the log entirely. Interface injection is enabled by default in Loom (`interfaceInjection.enableDependencyInterfaceInjection = true`, per docs.fabricmc.net/develop/loom/options).

## 2. Attributes

| Class | Member | Exact signature | javap |
|---|---|---|---|
| `net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry` | `register` | `public static void register(EntityType<? extends LivingEntity>, AttributeSupplier.Builder)`; overload `register(EntityType<? extends LivingEntity>, AttributeSupplier)` | yes |
| `net.minecraft.world.entity.Mob` | `createMobAttributes` | `public static AttributeSupplier.Builder createMobAttributes()` | yes |
| `net.minecraft.world.entity.LivingEntity` | `createLivingAttributes` | `public static AttributeSupplier.Builder createLivingAttributes()` | yes |
| `net.minecraft.world.entity.ai.attributes.AttributeSupplier$Builder` | `add` | `public AttributeSupplier.Builder add(Holder<Attribute>, double)`; `add(Holder<Attribute>)`; `public AttributeSupplier build()`; static `AttributeSupplier.builder()` | yes |
| `net.minecraft.world.entity.ai.attributes.Attributes` | fields (all `Holder<Attribute>`) | `MAX_HEALTH, MOVEMENT_SPEED, FLYING_SPEED, FOLLOW_RANGE, GRAVITY, SCALE, STEP_HEIGHT, SAFE_FALL_DISTANCE, FALL_DAMAGE_MULTIPLIER, KNOCKBACK_RESISTANCE, EXPLOSION_KNOCKBACK_RESISTANCE, ATTACK_DAMAGE, JUMP_STRENGTH` | yes |
| `LivingEntity` | attribute access | `public AttributeInstance getAttribute(Holder<Attribute>)`; `public double getAttributeValue(Holder<Attribute>)`; `public AttributeMap getAttributes()`; `AttributeInstance.setBaseValue(double)/getBaseValue()/getValue()` | yes |
| `LivingEntity` | gravity | `protected double getDefaultGravity()` reads `Attributes.GRAVITY` (javap -c); `Entity.getGravity()` is `public final double`; `Entity.setNoGravity(boolean)` / `isNoGravity()` | yes |

## 3. Entity class hierarchy and overrides (1.21.1 mojmap)

Constructors:

| Class | Signature | javap |
|---|---|---|
| `net.minecraft.world.entity.Mob` | `protected Mob(EntityType<? extends Mob>, Level)` | yes |
| `net.minecraft.world.entity.PathfinderMob` | `protected PathfinderMob(EntityType<? extends PathfinderMob>, Level)` | yes |
| `net.minecraft.world.entity.animal.Animal` | `protected Animal(EntityType<? extends Animal>, Level)`; requires `public abstract boolean isFood(ItemStack)` | yes |
| `net.minecraft.world.entity.LivingEntity` | `protected LivingEntity(EntityType<? extends LivingEntity>, Level)` | yes |
| `net.minecraft.world.entity.ambient.AmbientCreature` | exists (dumped) — Bat's parent; use for a non-breedable ambient mob | yes |

Overridable members (exact 1.21.1 signatures):

| Owner | Signature | javap |
|---|---|---|
| Mob | `protected void registerGoals()` (leave empty to have no vanilla goals) | yes |
| Entity/Mob | `public void tick()` (Mob overrides) | yes |
| LivingEntity/Mob | `public void aiStep()` | yes |
| Mob | `protected void customServerAiStep()` | yes |
| Mob | `protected final void serverAiStep()` — **final**; order (javap -c): `sensing.tick()`, `goalSelector.tick()`, `targetSelector.tick()`, `tickRunningGoals` x2, `navigation.tick()`, **`customServerAiStep()`**, then `moveControl.tick()`, `lookControl.tick()`, `jumpControl.tick()`, `sendDebugPackets()` | yes |
| LivingEntity | `public void travel(Vec3 travelVector)` — called from `LivingEntity.aiStep()` as `travel(new Vec3(xxa, yya, zza))` only when the entity is effective-AI (server) or locally controlled (javap -c) | yes |
| LivingEntity | `public Vec3 handleRelativeFrictionAndCalculateMovement(Vec3, float)`; `protected float getFlyingSpeed()`; `public float getSpeed()`; `public void setSpeed(float)` | yes |
| Entity | `protected abstract void defineSynchedData(SynchedEntityData.Builder)` — **Builder parameter in 1.21.x** (Mob overrides with same signature) | yes |
| Entity/Mob | `public void addAdditionalSaveData(CompoundTag)`; `public void readAdditionalSaveData(CompoundTag)` (declared `protected abstract` in Entity, `public` in Mob) | yes |
| Mob | `protected SoundEvent getAmbientSound()`; `public int getAmbientSoundInterval()`; `public void playAmbientSound()` | yes |
| LivingEntity | `protected SoundEvent getHurtSound(DamageSource)`; `protected SoundEvent getDeathSound()`; `protected float getSoundVolume()`; `public float getVoicePitch()` | yes |
| LivingEntity | `public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource)`; Entity: `protected void checkFallDamage(double, boolean, BlockState, BlockPos)` (Bat/Bee/Parrot/Allay override this as no-op) | yes |
| Entity/LivingEntity | `public boolean isPushable()`; `protected void doPush(Entity)`; `protected void pushEntities()` | yes |
| Mob | `public boolean removeWhenFarAway(double)`; `public boolean requiresCustomPersistence()`; `public void setPersistenceRequired()`; `public boolean isPersistenceRequired()`; `public void checkDespawn()`; `protected boolean shouldDespawnInPeaceful()` | yes |
| Mob | `public boolean checkSpawnRules(LevelAccessor, MobSpawnType)`; `public boolean checkSpawnObstruction(LevelReader)`; Animal: `public static boolean checkAnimalSpawnRules(EntityType<? extends Animal>, LevelAccessor, MobSpawnType, BlockPos, RandomSource)` | yes |
| Mob | `protected InteractionResult mobInteract(Player, InteractionHand)` (Animal makes it public) | yes |
| Mob | `public int getMaxHeadYRot()`; `public int getMaxHeadXRot()`; `public int getHeadRotSpeed()` — all **int** | yes |
| Mob | `public void setNoAi(boolean)`; `public boolean isNoAi()`; `public MoveControl getMoveControl()`; `public LookControl getLookControl()`; `public PathNavigation getNavigation()`; `protected PathNavigation createNavigation(Level)`; fields `protected MoveControl moveControl; protected LookControl lookControl; protected PathNavigation navigation; protected final GoalSelector goalSelector, targetSelector` | yes |
| Mob | `public void setXxa(float)`, `setYya(float)`, `setZza(float)`; LivingEntity public fields `xxa, yya, zza` | yes |
| Bat/Bee/Parrot/Allay/Vex | `public boolean isFlapping()` (Parrot: protected) — used by `Entity` for flap sounds/game events; LivingEntity default exists | yes |
| Entity | `public void setDeltaMovement(Vec3)`; `setDeltaMovement(double,double,double)`; `public Vec3 getDeltaMovement()`; `public void addDeltaMovement(Vec3)` | yes |
| Entity | `public void move(MoverType, Vec3)`; `MoverType.SELF, PLAYER, PISTON, SHULKER_BOX, SHULKER` | yes |
| Entity | `public float getYRot()`, `setYRot(float)`, `getXRot()`, `setXRot(float)`; LivingEntity public fields `yBodyRot, yBodyRotO, yHeadRot, yHeadRotO`; `public void setYBodyRot(float)`, `setYHeadRot(float)`, `getYHeadRot()`; Entity public `yRotO, xRotO, xo, yo, zo` | yes |
| Entity | `public final Vec3 getEyePosition()`; `getEyePosition(float partialTick)`; `public final Vec3 getViewVector(float partialTick)`; `public final Vec3 calculateViewVector(float xRot, float yRot)`; `public Vec3 getLookAngle()`; `public Vec3 getForward()`; `public double getEyeY()`; `public final float getEyeHeight()` | yes |
| Entity | `public Vec3 position()`; `public BlockPos blockPosition()`; `public ChunkPos chunkPosition()`; `public final double getX()/getY()/getZ()`; `public final AABB getBoundingBox()`; `public final float getBbWidth()/getBbHeight()` | yes |
| Entity | `public Level level()` (method) | yes |
| Entity | `public boolean onGround()`; public fields `horizontalCollision, verticalCollision, verticalCollisionBelow, minorHorizontalCollision, fallDistance, noPhysics, tickCount`; `public boolean isInWater()`, `isInWaterOrRain()`, `isUnderWater()`, `isInLava()` | yes |
| Entity | `public void setPos(double,double,double)`; `public final void setPos(Vec3)`; `public final void setPosRaw(double,double,double)`; `moveTo(double,double,double)`, `moveTo(Vec3)`, `moveTo(double,double,double,float yRot,float xRot)`, `moveTo(BlockPos,float,float)`; `absMoveTo(...)`; `teleportTo(double,double,double)` | yes |
| Entity | `public double distanceToSqr(Entity)`, `distanceToSqr(Vec3)`, `distanceToSqr(double,double,double)`; `public float distanceTo(Entity)` | yes |
| Entity | `public boolean hurt(DamageSource, float)`; LivingEntity `public void heal(float)`, `getHealth()`, `setHealth(float)`, `isDeadOrDying()`, `isAlive()`; `public DamageSources damageSources()` on Entity (delegates to level) | yes |
| Entity | `public void remove(Entity.RemovalReason)`; `public final boolean isRemoved()`; `public final void discard()`; `public void kill()`; `RemovalReason.KILLED, DISCARDED, UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER, CHANGED_DIMENSION` | yes |
| Entity | `public void playSound(SoundEvent, float volume, float pitch)`; `playSound(SoundEvent)` | yes |
| Entity | `protected final SynchedEntityData entityData`; `public SynchedEntityData getEntityData()`; `protected final RandomSource random`; `public void onSyncedDataUpdated(EntityDataAccessor<?>)` | yes |
| Entity | `public boolean isControlledByLocalInstance()`; `public boolean isEffectiveAi()` (false on client) | yes |
| Entity | `public boolean isInvulnerableTo(DamageSource)`; `public boolean fireImmune()`; `public boolean isPickable()`; `public boolean isAttackable()`; `public boolean isSilent()`/`setSilent(boolean)`; `setCustomName(Component)`, `setCustomNameVisible(boolean)` | yes |
| `net.minecraft.world.entity.ai.control.FlyingMoveControl` | `public FlyingMoveControl(Mob, int maxTurn, boolean hoversInPlace)`; `public void tick()` | yes |
| `MoveControl` | `public MoveControl(Mob)`; `setWantedPosition(double x,double y,double z,double speed)`; `strafe(float forward,float strafe)`; `hasWanted()`; `getSpeedModifier()`; `tick()` | yes |
| `LookControl` | `setLookAt(Vec3)`, `setLookAt(Entity)`, `setLookAt(Entity,float,float)`, `setLookAt(double,double,double)`, `setLookAt(double,double,double,float,float)` | yes |
| `FlyingPathNavigation` / `GroundPathNavigation` | `(Mob, Level)` ctors; `PathNavigation.moveTo(double,double,double,double speed)`, `moveTo(Entity,double)`, `stop()`, `isDone()`, `setSpeedModifier(double)` | yes |
| `GoalSelector` | `addGoal(int, Goal)`, `removeGoal(Goal)`, `removeAllGoals(Predicate<Goal>)` | yes |

World / sensing APIs:

| Class | Member | Exact signature | javap |
|---|---|---|---|
| `net.minecraft.world.level.Level` | `isClientSide` | `public final boolean isClientSide` (field) AND `public boolean isClientSide()` (method) — both exist | yes |
| `Level` (via `BlockGetter`) | `clip` | `public default BlockHitResult clip(ClipContext)` | yes |
| `net.minecraft.world.level.ClipContext` | ctor | `public ClipContext(Vec3 from, Vec3 to, ClipContext.Block, ClipContext.Fluid, Entity)`; also `(Vec3, Vec3, Block, Fluid, CollisionContext)`; `ClipContext.Block.COLLIDER, OUTLINE, VISUAL, FALLDAMAGE_RESETTING`; `ClipContext.Fluid.NONE, SOURCE_ONLY, ANY, WATER` | yes |
| `net.minecraft.world.phys.HitResult` | | `public abstract HitResult.Type getType()`; `public Vec3 getLocation()`; `public double distanceTo(Entity)`; `HitResult.Type.MISS, BLOCK, ENTITY` | yes |
| `net.minecraft.world.phys.BlockHitResult` | | `public BlockPos getBlockPos()`; `public Direction getDirection()`; `public boolean isInside()`; `HitResult.Type getType()` | yes |
| `Level` | `getBlockState` | `public BlockState getBlockState(BlockPos)`; `public FluidState getFluidState(BlockPos)`; `public boolean isEmptyBlock(BlockPos)` (LevelReader default) | yes |
| `BlockBehaviour.BlockStateBase` (= BlockState) | | `public Block getBlock()`; `public MapColor getMapColor(BlockGetter, BlockPos)`; `public boolean isAir()`; `public boolean isSolid()`; `public boolean blocksMotion()`; `public boolean liquid()`; `public int getLightEmission()`; `public boolean is(Block)`, `is(TagKey<Block>)`, `is(Holder<Block>)`; `public FluidState getFluidState()`; `public SoundType getSoundType()`; `getCollisionShape(BlockGetter, BlockPos)` | yes |
| `net.minecraft.world.level.block.state.BlockBehaviour` (Block extends it) | `defaultMapColor` | `public MapColor defaultMapColor()` | yes |
| `net.minecraft.world.level.material.MapColor` | | `public final int col` (0xRRGGBB), `public final int id`; `public int calculateRGBColor(MapColor.Brightness)`; `public static MapColor byId(int)`; `Brightness.LOW, NORMAL, HIGH, LOWEST` | yes |
| `Level` (via `BlockAndTintGetter`) | `getBrightness` | `public default int getBrightness(LightLayer, BlockPos)`; `public default int getRawBrightness(BlockPos, int)`; `LightLayer.SKY, BLOCK` | yes |
| `Level` (via `LevelReader`) | `getMaxLocalRawBrightness` | `public default int getMaxLocalRawBrightness(BlockPos)`; `(BlockPos, int)`; `public default boolean canSeeSky(BlockPos)`; `public int getSkyDarken()`; `public boolean isDay()/isNight()`; `getDayTime()`, `getGameTime()`, `isRaining()`, `isThundering()` | yes |
| `Level` (via `EntityGetter`) | `getEntitiesOfClass` | `public default <T extends Entity> List<T> getEntitiesOfClass(Class<T>, AABB, Predicate<? super T>)`; `getEntitiesOfClass(Class<T>, AABB)`; `getEntities(Entity except, AABB, Predicate<? super Entity>)`; `public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity,T>, AABB, Predicate<? super T>)`; `EntityTypeTest.forClass(Class<T>)` | yes |
| `EntityGetter` | `getNearestPlayer` | `public default Player getNearestPlayer(Entity, double)`; `(double x,double y,double z,double range,boolean creativeOk)`; `(double,double,double,double,Predicate<Entity>)`; `(TargetingConditions, LivingEntity)` | yes |
| `net.minecraft.server.level.ServerLevel` | entities | `public <T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity,T>, Predicate<? super T>)`; `public Iterable<Entity> getAllEntities()`; `public Entity getEntity(int)`; `getEntity(UUID)`; `public List<ServerPlayer> players()`; `public boolean addFreshEntity(Entity)`; `tryAddFreshEntityWithPassengers(Entity)`; `public MinecraftServer getServer()` | yes |
| `ServerLevel` | `sendParticles` | `public <T extends ParticleOptions> int sendParticles(T, double x, double y, double z, int count, double dx, double dy, double dz, double speed)`; per-player overload `sendParticles(ServerPlayer, T, boolean force, double,double,double,int,double,double,double,double)` | yes |
| `net.minecraft.core.particles.ParticleTypes` | | `SimpleParticleType HEART, HAPPY_VILLAGER, ANGRY_VILLAGER, SMOKE, WHITE_SMOKE, POOF, CLOUD, CRIT, ELECTRIC_SPARK, GLOW, NOTE, ENCHANT, END_ROD, SOUL, FLAME, BUBBLE, SPLASH, SNEEZE, WAX_ON, WAX_OFF, COMPOSTER, FALLING_HONEY, DRIPPING_HONEY, LANDING_HONEY, FLASH, SONIC_BOOM, SCULK_SOUL, EFFECT, INSTANT_EFFECT, WITCH, GUST, SMALL_GUST, ITEM_SLIME, MYCELIUM, CHERRY_LEAVES, CAMPFIRE_COSY_SMOKE, SPORE_BLOSSOM_AIR`; `ParticleType<DustParticleOptions> DUST`; `ParticleType<ColorParticleOption> ENTITY_EFFECT`; `new DustParticleOptions(org.joml.Vector3f color, float scale)`; `SimpleParticleType implements ParticleOptions` (pass directly) | yes |
| `Level` | `playSound` | `public void playSound(Player except, Entity source, SoundEvent, SoundSource, float volume, float pitch)`; `playSound(Player, double,double,double, SoundEvent, SoundSource, float, float)`; `playSound(Player, BlockPos, SoundEvent, SoundSource, float, float)`; `playSound(Entity, BlockPos, SoundEvent, SoundSource, float, float)`; client-only `playLocalSound(...)` | yes |
| `Level` | `addParticle` | `public void addParticle(ParticleOptions, double,double,double,double,double,double)` (client-side visual only) | yes |
| `net.minecraft.sounds.SoundEvents` | | `SoundEvent BEE_LOOP, BEE_LOOP_AGGRESSIVE, BEE_HURT, BEE_DEATH, BEE_STING, BEE_POLLINATE, BAT_TAKEOFF, BAT_LOOP, BAT_AMBIENT, BAT_HURT, BAT_DEATH, PARROT_FLY, SILVERFISH_AMBIENT, ENDERMITE_AMBIENT/HURT/DEATH/STEP, GENERIC_EAT, GENERIC_DRINK, PLAYER_BURP, HONEY_DRINK, GENERIC_SMALL_FALL, GENERIC_HURT, FOX_EAT, PANDA_EAT, CAMEL_EAT, SNIFFER_SNIFFING, SLIME_SQUISH_SMALL, ...`. NOTE some are `Holder.Reference<SoundEvent>` not `SoundEvent`: `GENERIC_EXPLODE, NOTE_BLOCK_HARP, NOTE_BLOCK_FLUTE, NOTE_BLOCK_BIT, SOUL_ESCAPE` — use `.value()` or the `Holder<SoundEvent>` overload of `Level.playSound` | yes |
| `net.minecraft.sounds.SoundSource` | enum | `MASTER, MUSIC, RECORDS, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE` | yes |
| `SoundEvent` | | `public static SoundEvent createVariableRangeEvent(ResourceLocation)`; `createFixedRangeEvent(ResourceLocation, float)`; `getLocation()` | yes |
| `net.minecraft.world.entity.item.ItemEntity` | | `public ItemStack getItem()`; `public int getAge()`; `hasPickUpDelay()`; ctor `ItemEntity(Level, double, double, double, ItemStack)` | yes |
| `net.minecraft.world.item.ItemStack` | | `public boolean is(Item)`; `is(TagKey<Item>)`; `is(Holder<Item>)`; `public Item getItem()`; `getCount()`; `isEmpty()`; `shrink(int)`; `copy()`; `getHoverName()`; `ItemStack(ItemLike)`, `ItemStack(ItemLike,int)`; `ItemStack.EMPTY` | yes |
| `net.minecraft.world.item.Items` | | `APPLE, SUGAR, HONEY_BOTTLE, HONEYCOMB, MELON_SLICE, SWEET_BERRIES, GLOW_BERRIES, BREAD, COOKIE, CAKE, PUMPKIN_PIE, GOLDEN_APPLE, COCOA_BEANS, BEETROOT, CARROT, POTATO, SUGAR_CANE, WHEAT, ROTTEN_FLESH, BEEF, PORKCHOP, MUSHROOM_STEW` all `Item` | yes |
| `net.minecraft.tags.ItemTags` | | `TagKey<Item> FLOWERS, SMALL_FLOWERS, MEAT, FISHES, LEAVES, LOGS, SAPLINGS, DIRT, SAND, WOOL, CANDLES` | yes |
| `net.minecraft.core.component.DataComponents` | `FOOD` | `DataComponentType<FoodProperties> FOOD`; `FoodProperties.nutrition()`, `saturation()`, `canAlwaysEat()`, `eatSeconds()` — use `stack.has(DataComponents.FOOD)` / `stack.get(DataComponents.FOOD)` to detect edible items generically | yes |
| `net.minecraft.world.phys.AABB` | | ctors `(double x1,y1,z1,x2,y2,z2)`, `(Vec3,Vec3)`, `(BlockPos)`; `inflate(double)`, `inflate(double,double,double)`; `move(double,double,double)`, `move(Vec3)`; `intersects(AABB)`; `contains(Vec3)`; `getCenter()`; `expandTowards(Vec3)`; `static ofSize(Vec3 center, double, double, double)`; `Optional<Vec3> clip(Vec3 from, Vec3 to)` | yes |
| `net.minecraft.world.phys.Vec3` | | `public final double x, y, z`; `Vec3(double,double,double)`; `ZERO`; `add`, `subtract`, `scale(double)`, `normalize()`, `length()`, `lengthSqr()`, `horizontalDistance()`, `horizontalDistanceSqr()`, `dot`, `cross`, `lerp(Vec3,double)`, `xRot(float)`, `yRot(float)`, `zRot(float)`, `multiply(double,double,double)`, `distanceTo(Vec3)`, `distanceToSqr(Vec3)`; `static directionFromRotation(float xRot, float yRot)`, `atCenterOf(Vec3i)`, `atBottomCenterOf(Vec3i)`, `fromRGB24(int)`; `toVector3f()` | yes |
| `net.minecraft.core.BlockPos` | | `BlockPos(int,int,int)`; `static containing(double,double,double)`; `above()/below()/north()/south()/east()/west()` (+int overloads); `offset(int,int,int)`; `relative(Direction[,int])`; `getCenter()` Vec3; `getX/getY/getZ` | yes |
| `net.minecraft.util.Mth` | | `sin(float)`, `cos(float)`, `sqrt(float)`, `floor(double)`, `ceil`, `clamp(int/long/float/double)`, `wrapDegrees(float/double/int)`, `degreesDifference(float,float)`, `approachDegrees(float,float,float)`, `atan2(double,double)`, `invSqrt(float)`, `inverseLerp`, `nextInt(RandomSource,int,int)`, `nextFloat(RandomSource,float,float)`, `nextDouble(...)`, `positiveModulo(...)`, `frac(...)` | yes |
| `net.minecraft.util.RandomSource` | | `nextInt()`, `nextInt(int)`, `nextInt(int,int)`, `nextIntBetweenInclusive(int,int)`, `nextFloat()`, `nextDouble()`, `nextGaussian()`, `nextBoolean()`, `triangle(double,double)`; `static RandomSource create()`, `create(long)` | yes |
| `net.minecraft.world.damagesource.DamageSources` | | `generic()`, `fall()`, `flyIntoWall()`, `inWall()`, `drown()`, `starve()`, `magic()`, `freeze()`, `cactus()`, `mobAttack(LivingEntity)`, `playerAttack(Player)`, `sting(LivingEntity)`, `genericKill()`, `outOfBorder()`; `DamageSource.is(TagKey<DamageType>)`, `is(ResourceKey<DamageType>)`, `getEntity()`, `getDirectEntity()`, `type()` | yes |
| `net.minecraft.world.InteractionResult` | enum | `SUCCESS, SUCCESS_NO_ITEM_USED, CONSUME, CONSUME_PARTIAL, PASS, FAIL`; `static sidedSuccess(boolean isClientSide)`; `InteractionHand.MAIN_HAND, OFF_HAND` | yes |
| `net.minecraft.nbt.CompoundTag` | | `putInt/putFloat/putDouble/putBoolean/putByte/putLong/putString/putUUID/putIntArray/putByteArray/putLongArray(String, ...)`; `getInt/getFloat/getDouble/getBoolean/getByte/getLong/getString/getUUID/getIntArray/getByteArray/getLongArray(String)`; `contains(String)`, `contains(String,int)`; `hasUUID(String)`; `getCompound(String)`; `getList(String,int)`; `put(String, Tag)` | yes |
| `net.minecraft.world.entity.player.Player` | | `displayClientMessage(Component, boolean actionBar)`; `getInventory()`; `getMainHandItem()`; `getItemInHand(InteractionHand)` (LivingEntity); `isCreative()`; `getAbilities()`; `addItem(ItemStack)`; `isLocalPlayer()` | yes |
| `net.minecraft.server.level.ServerPlayer` | | `public ServerLevel serverLevel()`; `public ServerGamePacketListenerImpl connection`; `public final MinecraftServer server`; `sendSystemMessage(Component)`; `sendSystemMessage(Component, boolean)`; `teleportTo(ServerLevel,double,double,double,float,float)` | yes |
| `net.minecraft.server.MinecraftServer` | | `overworld()` (exists; grep only matched `getLevel`), `getLevel(ResourceKey<Level>)`, `getAllLevels()`, `getPlayerList()`, `execute(Runnable)` (inherited), `getTickCount()`, `isSingleplayer()`, `getServerDirectory()` Path, `getWorldPath(LevelResource)`, `registryAccess()` (`RegistryAccess.Frozen`), `getCommands()`, `createCommandSourceStack()`, `sendSystemMessage(Component)`, `getRunningThread()`, `getCurrentSmoothedTickTime()`; `PlayerList.getPlayers()`, `getPlayer(UUID)`, `getPlayerByName(String)`, `broadcastSystemMessage(Component, boolean)`, `broadcastAll(Packet<?>)`, `getPlayerCount()` | yes |

Vanilla flying mobs as reference implementations (all verified as overriding these in 1.21.1): `Bat` overrides `isPushable()` (false), `doPush`, `pushEntities`, `checkFallDamage` (no-op), `isIgnoringBlockTriggers()`, `tick()`, `customServerAiStep()`; `Allay` overrides `travel(Vec3)` for hover flight and `createNavigation` (FlyingPathNavigation); `Bee`/`Parrot`/`Allay` override `checkFallDamage` as no-op and `isFlapping()`. `FlyingAnimal` is an interface with a single `boolean isFlying()`.

## 4. Synched entity data (network-synced fields)

| Class | Member | Exact signature | javap |
|---|---|---|---|
| `net.minecraft.network.syncher.SynchedEntityData` (**package `network.syncher`, not `world.entity`**) | `defineId` | `public static <T> EntityDataAccessor<T> defineId(Class<? extends SyncedDataHolder>, EntityDataSerializer<T>)` | yes |
| `SynchedEntityData` | get/set | `public <T> T get(EntityDataAccessor<T>)`; `public <T> void set(EntityDataAccessor<T>, T)`; `set(EntityDataAccessor<T>, T, boolean force)`; `isDirty()` | yes |
| `SynchedEntityData$Builder` | `define` | `public <T> SynchedEntityData.Builder define(EntityDataAccessor<T>, T defaultValue)`; `build()` | yes |
| `net.minecraft.network.syncher.EntityDataSerializers` | fields | `EntityDataSerializer<Byte> BYTE`, `<Integer> INT`, `<Long> LONG`, `<Float> FLOAT`, `<String> STRING`, `<Boolean> BOOLEAN`, `<Component> COMPONENT`, `<Optional<Component>> OPTIONAL_COMPONENT`, `<ItemStack> ITEM_STACK`, `<BlockState> BLOCK_STATE`, `<ParticleOptions> PARTICLE`, `<BlockPos> BLOCK_POS`, `<Optional<BlockPos>> OPTIONAL_BLOCK_POS`, `<Direction> DIRECTION`, `<Optional<UUID>> OPTIONAL_UUID`, `<CompoundTag> COMPOUND_TAG`, `<Pose> POSE`, `<org.joml.Vector3f> VECTOR3` (no generic byte[]/float[] serializer — pack into INT/COMPOUND_TAG or use a custom payload) | yes |
| `net.minecraft.network.syncher.EntityDataAccessor<T>` | record | `EntityDataAccessor(int id, EntityDataSerializer<T> serializer)` | yes |
| `EntityDataSerializer<T>` | | `StreamCodec<? super RegistryFriendlyByteBuf, T> codec()`; `T copy(T)`; `static <T> EntityDataSerializer<T> forValueType(StreamCodec<? super RegistryFriendlyByteBuf, T>)` (custom serializer; needs registering in `EntityDataSerializers` via Fabric helper — not covered) | yes |

Pattern: `private static final EntityDataAccessor<Float> DATA_ACTIVITY = SynchedEntityData.defineId(FlyEntity.class, EntityDataSerializers.FLOAT);` then `@Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(DATA_ACTIVITY, 0f); }`, `this.entityData.set(DATA_ACTIVITY, v)`, `this.entityData.get(DATA_ACTIVITY)`.

## 5. Items: spawn egg, creative tab, item model

| Class | Member | Exact signature | javap |
|---|---|---|---|
| `net.minecraft.world.item.SpawnEggItem` | ctor | `public SpawnEggItem(EntityType<? extends Mob>, int backgroundColor, int highlightColor, Item.Properties)` — **not deprecated in 1.21.1**; ctor stores `defaultType`, both colors and does `BY_ID.put(type, this)` (javap -c) | yes |
| `SpawnEggItem` | | `public int getColor(int tintIndex)`; `public static SpawnEggItem byId(EntityType<?>)`; `public static Iterable<SpawnEggItem> eggs()`; `public EntityType<?> getType(ItemStack)`; `useOn(UseOnContext)`, `use(Level, Player, InteractionHand)` | yes |
| `net.minecraft.client.color.item.ItemColors` | `createDefault` | iterates `SpawnEggItem.eggs()` and registers `SpawnEggItem::getColor` for each — so a modded egg registered during mod init is tinted automatically with the two colors (javap -c) | yes |
| `net.minecraft.world.item.Item$Properties` | | `public Item.Properties()`; `stacksTo(int)`, `rarity(Rarity)`, `food(FoodProperties)`, `durability(int)`, `fireResistant()`, `component(DataComponentType<T>, T)`, `requiredFeatures(...)`, `attributes(ItemAttributeModifiers)` — **no `.group()`/tab method** (removed 1.19.3) | yes |
| `net.minecraft.world.item.Item` | | `public Item(Item.Properties)`; `Item asItem()` (ItemLike); `getDescriptionId()`; `getDefaultInstance()` | yes |
| `net.minecraft.world.item.CreativeModeTabs` | fields | `SPAWN_EGGS`, `TOOLS_AND_UTILITIES`, `INGREDIENTS`, `FOOD_AND_DRINKS`, `COMBAT`, `BUILDING_BLOCKS`, `NATURAL_BLOCKS` are declared **`private static final ResourceKey<CreativeModeTab>`** in the mojmap jar — Fabric API's `fabric-transitive-access-wideners-v1` module widens them to public in the dev/runtime environment (this is how vanilla Fabric templates use `CreativeModeTabs.SPAWN_EGGS`); if compilation fails, use `ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("spawn_eggs"))` | yes (private); widening: not verified by javap (AW file not parsed) |
| `net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents` | `modifyEntriesEvent` | `public static Event<ItemGroupEvents.ModifyEntries> modifyEntriesEvent(ResourceKey<CreativeModeTab>)`; `ModifyEntries.modifyEntries(FabricItemGroupEntries)`; also `MODIFY_ENTRIES_ALL` | yes |
| `net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries` | | `accept(ItemLike)` / `accept(ItemStack)` (inherited from `CreativeModeTab.Output`; grep found `prepend(ItemLike)`, `prepend(ItemStack)`, `addAfter(ItemLike, ItemStack...)`, `addAfter(ItemLike, ItemLike...)`, `addBefore(...)`, with `CreativeModeTab.TabVisibility` overloads) | yes (prepend/addAfter); accept inherited (yes, standard) |
| `net.minecraft.core.registries.Registries` | | `ResourceKey<Registry<CreativeModeTab>> CREATIVE_MODE_TAB`, `ENTITY_TYPE`, `ITEM`, `SOUND_EVENT`; `ResourceKey.create(ResourceKey<? extends Registry<T>>, ResourceLocation)` | yes |

Item model JSON (verified from the 1.21.1 client jar `assets/minecraft/models/item/bee_spawn_egg.json`, 51 bytes):

```json
{
  "parent": "minecraft:item/template_spawn_egg"
}
```

`template_spawn_egg.json` (139 bytes) is `{"parent":"item/generated","textures":{"layer0":"item/spawn_egg","layer1":"item/spawn_egg_overlay"}}`; layer0 is tinted with `backgroundColor`, layer1 with `highlightColor`. Put the file at `src/main/resources/assets/<modid>/models/item/<item_id>.json`. No item texture is needed. Add `"item.<modid>.<item_id>": "Fruit Fly Spawn Egg"` and `"entity.<modid>.<entity_id>": "Fruit Fly"` to `assets/<modid>/lang/en_us.json`.

## 6. Client: renderer, model, texture, HUD, world overlay, keys

| Class | Member | Exact signature | javap |
|---|---|---|---|
| `net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry` | `register` | `public static <E extends Entity> void register(EntityType<? extends E>, EntityRendererProvider<E>)` | yes |
| `net.minecraft.client.renderer.entity.EntityRendererProvider<T>` | | functional: `EntityRenderer<T> create(EntityRendererProvider.Context)` → lambda `ctx -> new FlyRenderer(ctx)` or `FlyRenderer::new` | yes |
| `EntityRendererProvider$Context` | | `public ModelPart bakeLayer(ModelLayerLocation)`; `getModelSet()` EntityModelSet; `getItemRenderer()`; `getBlockRenderDispatcher()`; `getResourceManager()`; `getFont()`; `getEntityRenderDispatcher()`; `getModelManager()` | yes |
| `net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry` | `registerModelLayer` | `public static void registerModelLayer(ModelLayerLocation, EntityModelLayerRegistry.TexturedModelDataProvider)`; the functional interface is **`TexturedModelDataProvider`** with single method **`LayerDefinition createModelData()`** → pass `FlyModel::createBodyLayer` | yes |
| `net.minecraft.client.model.geom.ModelLayerLocation` | ctor | `public ModelLayerLocation(ResourceLocation model, String layer)` (layer usually `"main"`); `getModel()`, `getLayer()` | yes |
| `net.minecraft.client.renderer.entity.MobRenderer<T extends Mob, M extends EntityModel<T>>` | ctor | `public MobRenderer(EntityRendererProvider.Context, M model, float shadowRadius)` — **two type params in 1.21.1** (no RenderState; that is 1.21.2+) | yes |
| `LivingEntityRenderer<T,M>` | | `public void render(T, float entityYaw, float partialTicks, PoseStack, MultiBufferSource, int packedLight)`; `protected RenderType getRenderType(T, boolean bodyVisible, boolean translucent, boolean glowing)`; `protected void scale(T, PoseStack, float partialTick)`; `protected void setupRotations(T, PoseStack, float ageInTicks, float rotationYaw, float partialTicks, float scale)`; `protected float getBob(T, float)`; `protected boolean shouldShowName(T)`; `public M getModel()`; `protected M model`; `protected final List<RenderLayer<T,M>> layers` | yes |
| `net.minecraft.client.renderer.entity.EntityRenderer<T>` | | `public abstract ResourceLocation getTextureLocation(T)`; `public boolean shouldRender(T, Frustum, double camX, double camY, double camZ)`; `protected int getBlockLightLevel(T, BlockPos)`; `protected int getSkyLightLevel(T, BlockPos)`; `public final int getPackedLightCoords(T, float)`; `public Vec3 getRenderOffset(T, float)`; `protected void renderNameTag(T, Component, PoseStack, MultiBufferSource, int, float)`; fields `protected float shadowRadius, shadowStrength`; `protected final EntityRenderDispatcher entityRenderDispatcher` | yes |
| `net.minecraft.client.model.HierarchicalModel<E extends Entity>` | | `public abstract ModelPart root()`; `public void renderToBuffer(PoseStack, VertexConsumer, int packedLight, int packedOverlay, int color)` (**color is one ARGB int in 1.21**, not 4 floats); ctors `HierarchicalModel()` (uses `RenderType::entityCutoutNoCull`) and `HierarchicalModel(Function<ResourceLocation, RenderType>)`; `Optional<ModelPart> getAnyDescendantWithName(String)`; `protected void animate(AnimationState, AnimationDefinition, float ageInTicks[, float speed])`; `animateWalk(AnimationDefinition, float limbSwing, float limbSwingAmount, float maxSpeed, float scale)` | yes |
| `net.minecraft.client.model.EntityModel<T extends Entity>` | | `public abstract void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)`; `public void prepareMobModel(T, float limbSwing, float limbSwingAmount, float partialTick)`; public fields `attackTime, riding, young` | yes |
| `net.minecraft.client.model.Model` | | `public final RenderType renderType(ResourceLocation)`; `public abstract void renderToBuffer(PoseStack, VertexConsumer, int, int, int)`; `public final void renderToBuffer(PoseStack, VertexConsumer, int, int)` | yes |
| `net.minecraft.client.model.geom.builders.LayerDefinition` | `create` | `public static LayerDefinition create(MeshDefinition, int textureWidth, int textureHeight)`; `public ModelPart bakeRoot()` | yes |
| `MeshDefinition` | | `public MeshDefinition()`; `public PartDefinition getRoot()` | yes |
| `PartDefinition` | | `public PartDefinition addOrReplaceChild(String, CubeListBuilder, PartPose)`; `getChild(String)`; `bake(int,int)` | yes |
| `CubeListBuilder` | | `public static CubeListBuilder create()`; `texOffs(int u, int v)`; `mirror()`; `addBox(float x, float y, float z, float w, float h, float d)`; `addBox(float,float,float,float,float,float, CubeDeformation)`; `addBox(float,...,CubeDeformation, float uScale, float vScale)`; `addBox(String, ...)` variants | yes |
| `CubeDeformation` | | `CubeDeformation.NONE`; `new CubeDeformation(float)`; `(float,float,float)`; `extend(float)` | yes |
| `net.minecraft.client.model.geom.PartPose` | | `PartPose.ZERO`; `static offset(float x, float y, float z)`; `static rotation(float xRot, float yRot, float zRot)`; `static offsetAndRotation(float x, float y, float z, float xRot, float yRot, float zRot)` (radians) | yes |
| `net.minecraft.client.model.geom.ModelPart` | | public fields `x, y, z, xRot, yRot, zRot, xScale, yScale, zScale, visible, skipDraw`; `render(PoseStack, VertexConsumer, int packedLight, int packedOverlay)`; `render(PoseStack, VertexConsumer, int, int, int color)`; `getChild(String)`; `hasChild(String)`; `setPos(float,float,float)`; `setRotation(float,float,float)`; `translateAndRotate(PoseStack)`; `resetPose()`, `loadPose(PartPose)`, `getInitialPose()`; `getAllParts()` Stream | yes |
| `net.minecraft.client.renderer.RenderType` | | `static RenderType entityCutoutNoCull(ResourceLocation)`; `entityCutoutNoCull(ResourceLocation, boolean outline)`; `entityCutout(ResourceLocation)`; `entitySolid`; `entityTranslucent(ResourceLocation[, boolean])`; `entityTranslucentEmissive`; `eyes(ResourceLocation)`; `lines()`; `lineStrip()`; `debugLineStrip(double)`; `debugFilledBox()`; `debugQuads()`; `text(ResourceLocation)`; `gui()` | yes |
| `com.mojang.blaze3d.vertex.VertexConsumer` | | `addVertex(float,float,float)`; `addVertex(PoseStack.Pose, float,float,float)`; `addVertex(Matrix4f, float,float,float)`; `addVertex(Vector3f)`; `setColor(int r,int g,int b,int a)`; `setColor(float,float,float,float)`; `setColor(int argb)`; `setUv(float,float)`; `setUv1(int,int)` (overlay); `setUv2(int,int)` (light); `setOverlay(int)`; `setLight(int)`; `setNormal(float,float,float)`; `setNormal(PoseStack.Pose, float,float,float)` (all builder-style, return VertexConsumer) | yes |
| `com.mojang.blaze3d.vertex.PoseStack` | | `pushPose()`, `popPose()`, `translate(double,double,double)`/`(float,float,float)`, `scale(float,float,float)`, `mulPose(Quaternionf)`, `mulPose(Matrix4f)`, `rotateAround(Quaternionf, float,float,float)`, `last()` → `PoseStack.Pose` with `pose()` Matrix4f, `normal()` Matrix3f | yes |
| `net.minecraft.client.renderer.MultiBufferSource` | | `VertexConsumer getBuffer(RenderType)`; `MultiBufferSource.BufferSource.endBatch()`, `endBatch(RenderType)`, `endLastBatch()` | yes |
| `net.minecraft.client.renderer.LevelRenderer` | statics | `public static void renderLineBox(PoseStack, VertexConsumer, AABB, float r, float g, float b, float a)`; `renderLineBox(PoseStack, VertexConsumer, double x1,y1,z1,x2,y2,z2, float r,g,b,a)`; `addChainedFilledBoxVertices(...)`; `renderVoxelShape(...)` — convenient for debug boxes on `RenderType.lines()` | yes |
| `net.minecraft.client.renderer.debug.DebugRenderer` | statics | `public static void renderFloatingText(PoseStack, MultiBufferSource, String, double x, double y, double z, int color)`; `(..., int color, float scale)`; `(..., int color, float scale, boolean center, float yOffset, boolean transparent)`; `renderFilledBox(PoseStack, MultiBufferSource, AABB, float r,g,b,a)` | yes |
| `net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback` | | `Event<HudRenderCallback> EVENT`; `void onHudRender(GuiGraphics, DeltaTracker)` → lambda `(guiGraphics, deltaTracker) -> ...` (**DeltaTracker, not float**, in 0.107 for 1.21.1; reference mod uses exactly this) | yes |
| `net.minecraft.client.DeltaTracker` | | `float getGameTimeDeltaTicks()`; `float getGameTimeDeltaPartialTick(boolean runsNormally)`; `float getRealtimeDeltaTicks()`; constants `ZERO`, `ONE` | yes |
| `net.minecraft.client.gui.GuiGraphics` | | `public int drawString(Font, String, int x, int y, int color, boolean dropShadow)`; `drawString(Font, String, int, int, int)`; `drawString(Font, Component, int, int, int[, boolean])`; `drawCenteredString(Font, String, int, int, int)`; `drawStringWithBackdrop(Font, Component, int, int, int width, int color)`; `fill(int x1,int y1,int x2,int y2,int color)`; `fill(int,int,int,int,int z,int color)`; `fillGradient(int,int,int,int,int,int)`; `hLine(int,int,int,int)`, `vLine(...)`; `renderOutline(int,int,int,int,int)`; `blit(ResourceLocation, int x, int y, int w, int h, float u, float v, int uW, int vH, int texW, int texH)`; `blit(ResourceLocation, int x, int y, int u, int v, int w, int h)` (256x256 assumed); `renderItem(ItemStack, int, int)`; `pose()` PoseStack; `guiWidth()`, `guiHeight()`; `enableScissor(int,int,int,int)`, `disableScissor()`; `setColor(float,float,float,float)`; `flush()` | yes |
| `net.minecraft.client.gui.Font` | | `public final int lineHeight` (= 9); `int width(String)`; `int width(FormattedText)` | yes |
| `net.minecraft.client.Minecraft` | | `static Minecraft getInstance()`; public fields `ClientLevel level` (nullable), `LocalPlayer player` (nullable), `final Font font`, `final Options options`, `final GameRenderer gameRenderer`, `final LevelRenderer levelRenderer`, `final Gui gui`, `final File gameDirectory`, `Screen screen`, `HitResult hitResult`, `Entity crosshairPickEntity`; methods `Window getWindow()`, `EntityRenderDispatcher getEntityRenderDispatcher()`, `EntityModelSet getEntityModels()`, `DeltaTracker getTimer()`, `IntegratedServer getSingleplayerServer()`, `hasSingleplayerServer()`, `isLocalServer()`, `getConnection()`, `getTextureManager()`, `isPaused()`, `getCameraEntity()` | yes |
| `com.mojang.blaze3d.platform.Window` | | `int getGuiScaledWidth()`; `int getGuiScaledHeight()`; `int getWidth()`; `int getHeight()`; `double getGuiScale()`; `long getWindow()` (GLFW handle) | yes |
| `net.minecraft.client.Camera` | | `Vec3 getPosition()`; `BlockPos getBlockPosition()`; `float getXRot()`, `getYRot()`; `Quaternionf rotation()`; `Entity getEntity()`; `isDetached()`; `isInitialized()`; `GameRenderer.getMainCamera()` | yes |
| `net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents` | fields | `START, AFTER_SETUP, BEFORE_ENTITIES, AFTER_ENTITIES, BEFORE_BLOCK_OUTLINE, BLOCK_OUTLINE, BEFORE_DEBUG_RENDER, AFTER_TRANSLUCENT, LAST, END`; handler methods `afterEntities(WorldRenderContext)`, `afterTranslucent(WorldRenderContext)`, `beforeDebugRender(WorldRenderContext)`, `onLast(WorldRenderContext)` | yes |
| `net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext` | | `LevelRenderer worldRenderer()`; `PoseStack matrixStack()`; `DeltaTracker tickCounter()`; `boolean blockOutlines()`; `Camera camera()`; `GameRenderer gameRenderer()`; `LightTexture lightmapTextureManager()`; `Matrix4f projectionMatrix()`; `Matrix4f positionMatrix()`; `ClientLevel world()`; `ProfilerFiller profiler()`; `boolean advancedTranslucency()`; `MultiBufferSource consumers()`; `Frustum frustum()`. `matrixStack()` is camera-relative: translate by `-camera().getPosition()` before drawing world coordinates (reference mod does this) | yes |
| `net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents` | | `END_CLIENT_TICK` / `START_CLIENT_TICK` → `void onEndTick(Minecraft)`; `END_WORLD_TICK` → `void onEndTick(ClientLevel)` | yes |
| `net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents` | | `ENTITY_LOAD` → `onLoad(Entity, ClientLevel)`; `ENTITY_UNLOAD` | yes |
| `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper` | | `public static KeyMapping registerKeyBinding(KeyMapping)`; `InputConstants.Key getBoundKeyOf(KeyMapping)` | yes |
| `net.minecraft.client.KeyMapping` | ctor | `public KeyMapping(String name, InputConstants.Type type, int keyCode, String category)`; also `(String, int, String)`; `boolean isDown()`; `boolean consumeClick()`; `InputConstants.Type.KEYSYM, SCANCODE, MOUSE`; key codes are GLFW constants (`org.lwjgl.glfw.GLFW.GLFW_KEY_F`, or `InputConstants.KEY_F` etc.) | yes |
| `net.minecraft.client.multiplayer.ClientLevel` | | `Iterable<Entity> entitiesForRendering()`; `Entity getEntity(int)`; `List<AbstractClientPlayer> players()`; `addParticle(...)`; `addAlwaysVisibleParticle(...)` | yes |
| `net.minecraft.client.renderer.LightTexture` | | `static final int FULL_BRIGHT` (0xF000F0); `static int pack(int block, int sky)`; `OverlayTexture.NO_OVERLAY` | yes |
| `net.minecraft.client.renderer.culling.Frustum` | | `boolean isVisible(AABB)` | yes |
| `net.minecraft.client.renderer.entity.EntityRenderers` | | only `createEntityRenderers(Context)` and `validateRegistrations()` are public; **there is no public `EntityRenderers.register` in the 1.21.1 mojmap jar** (the wiki example uses it via Fabric's access widener) — use `EntityRendererRegistry.register` | yes |

## 7. Networking (custom S2C payloads, 1.21.1)

| Class | Member | Exact signature | javap |
|---|---|---|---|
| `net.minecraft.network.protocol.common.custom.CustomPacketPayload` | | `CustomPacketPayload.Type<? extends CustomPacketPayload> type()` (abstract; your record overrides it returning your static TYPE); `static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B,T> codec(StreamMemberEncoder<B,T> writer, StreamDecoder<B,T> reader)` (i.e. `codec(MyPayload::write, MyPayload::new)`); `static <T> Type<T> createType(String)` | yes |
| `CustomPacketPayload$Type<T>` | record | `public Type(ResourceLocation id)`; `ResourceLocation id()` | yes |
| `net.minecraft.network.codec.StreamCodec<B,V>` | statics | `of(StreamEncoder<B,V>, StreamDecoder<B,V>)`; `ofMember(StreamMemberEncoder<B,V>, StreamDecoder<B,V>)`; `unit(V)`; `composite(StreamCodec<? super B,T1>, Function<C,T1>, Function<T1,C>)` up to 6 fields (`composite(c1,g1,c2,g2,...,BiFunction/Function3.../Function6 ctor)`) | yes |
| `net.minecraft.network.codec.ByteBufCodecs` | fields | `StreamCodec<ByteBuf,...>`: `BOOL, BYTE, SHORT, INT, VAR_INT, VAR_LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING_UTF8, COMPOUND_TAG, VECTOR3F`; methods `byteArray(int max)`, `stringUtf8(int max)`, `fromCodec(Codec<T>)`, `optional(StreamCodec)`, `collection(IntFunction<C>, StreamCodec)`, `list()`, `list(int)`, `map(IntFunction, keyCodec, valueCodec)`. Note `BlockPos.STREAM_CODEC`, `ResourceLocation.STREAM_CODEC` exist as constants | yes |
| `net.minecraft.network.FriendlyByteBuf` | | `writeInt/readInt`, `writeFloat/readFloat`, `writeDouble/readDouble`, `writeBoolean/readBoolean`, `writeByte(int)/readByte`, `writeLong/readLong`, `writeVarInt/readVarInt`, `writeUtf(String)/readUtf()`, `writeUtf(String,int)`, `writeByteArray(byte[])/readByteArray()`, `writeVarIntArray(int[])/readVarIntArray()`, `writeLongArray(long[])/readLongArray()`, `writeUUID/readUUID`, `writeBlockPos/readBlockPos`, `readVec3()`, `readVector3f()`, `writeResourceLocation/readResourceLocation`, `writeEnum(Enum<?>)`, `writeCollection(Collection<T>, StreamEncoder)`, `readList(StreamDecoder)`, `writeNullable/readNullable`; `RegistryFriendlyByteBuf extends FriendlyByteBuf` with `registryAccess()` | yes |
| `net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry<B extends FriendlyByteBuf>` | | `static PayloadTypeRegistry<RegistryFriendlyByteBuf> playS2C()`; `playC2S()`; `static PayloadTypeRegistry<FriendlyByteBuf> configurationS2C()/configurationC2S()`; `<T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(CustomPacketPayload.Type<T>, StreamCodec<? super B, T>)` — so a S2C play codec typed `StreamCodec<RegistryFriendlyByteBuf, MyPayload>` or `StreamCodec<FriendlyByteBuf, MyPayload>` (via `? super`) both work | yes |
| `net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking` | | `public static void send(ServerPlayer, CustomPacketPayload)`; `static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T>, ServerPlayNetworking.PlayPayloadHandler<T>)`; `canSend(ServerPlayer, CustomPacketPayload.Type<?>)`; `getSender(ServerPlayer)`; `PlayPayloadHandler.receive(T, ServerPlayNetworking.Context)`; `Context.server()` MinecraftServer, `player()` ServerPlayer, `responseSender()` PacketSender | yes |
| `net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking` | | `static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T>, ClientPlayNetworking.PlayPayloadHandler<T>)`; `static void send(CustomPacketPayload)`; `canSend(Type<?>)`; `PlayPayloadHandler.receive(T payload, ClientPlayNetworking.Context context)`; `Context.client()` Minecraft, `player()` LocalPlayer, `responseSender()` | yes |
| `net.fabricmc.fabric.api.networking.v1.PlayerLookup` | | `Collection<ServerPlayer> tracking(Entity)`; `tracking(ServerLevel, ChunkPos)`; `tracking(ServerLevel, BlockPos)`; `tracking(BlockEntity)`; `around(ServerLevel, Vec3, double radius)`; `around(ServerLevel, Vec3i, double)`; `world(ServerLevel)`; `all(MinecraftServer)` | yes |
| `net.fabricmc.fabric.api.networking.v1.PacketSender` | | `sendPacket(CustomPacketPayload)`; `sendPacket(Packet<?>)`; `createPacket(CustomPacketPayload)`; `disconnect(Component)` | yes |
| `net.fabricmc.fabric.api.event.Event<T>` | | `T invoker()`; `abstract void register(T)`; `register(ResourceLocation phase, T)`; `addPhaseOrdering(ResourceLocation, ResourceLocation)` | yes |

Payload registration must happen on both sides: `PayloadTypeRegistry.playS2C().register(TYPE, CODEC)` in the common initializer (runs on both client and server), then `ClientPlayNetworking.registerGlobalReceiver(TYPE, handler)` in the client initializer. Fabric docs state play handlers run on the game main thread (medium confidence — Javadoc not available locally).

## 8. Commands (server-side, brigadier)

| Class | Member | Exact signature | javap |
|---|---|---|---|
| `net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback` | | `Event<CommandRegistrationCallback> EVENT`; `void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment)` | yes |
| `net.minecraft.commands.Commands` | statics | `static LiteralArgumentBuilder<CommandSourceStack> literal(String)`; `static <T> RequiredArgumentBuilder<CommandSourceStack,T> argument(String, ArgumentType<T>)`; `Commands.CommandSelection.ALL, DEDICATED, INTEGRATED` | yes |
| `com.mojang.brigadier.arguments.IntegerArgumentType` | | `integer()`, `integer(int min)`, `integer(int min,int max)`; `static int getInteger(CommandContext<?>, String)` | yes |
| `FloatArgumentType` | | `floatArg()`, `floatArg(float)`, `floatArg(float,float)`; `static float getFloat(CommandContext<?>, String)` | yes |
| `DoubleArgumentType` | | `doubleArg(...)`; `getDouble(...)` | yes |
| `StringArgumentType` | | `word()`, `string()`, `greedyString()`; `static String getString(CommandContext<?>, String)` | yes |
| `BoolArgumentType` | | `bool()`; `getBool(...)` | yes |
| `net.minecraft.commands.arguments.EntityArgument` | | `entity()`, `entities()`, `player()`, `players()`; `static Entity getEntity(CommandContext<CommandSourceStack>, String) throws CommandSyntaxException`; `getEntities(...)`; `getPlayer(...)`; `getPlayers(...)` | yes |
| `net.minecraft.commands.arguments.coordinates.Vec3Argument` | | `vec3()`, `vec3(boolean centerCorrect)`; `static Vec3 getVec3(CommandContext<CommandSourceStack>, String)` | yes |
| `BlockPosArgument` | | `blockPos()`; `getLoadedBlockPos(ctx, String) throws CommandSyntaxException`; `getBlockPos(ctx, String)` | yes |
| `ResourceLocationArgument` | | `id()`; `getId(ctx, String)` | yes |
| `net.minecraft.commands.SharedSuggestionProvider` | | `static CompletableFuture<Suggestions> suggest(Iterable<String>, SuggestionsBuilder)`; `suggest(String[], ...)`; `suggest(Stream<String>, ...)` | yes |
| `com.mojang.brigadier.builder.ArgumentBuilder<S,T>` | | `T then(ArgumentBuilder<S,?>)`; `T executes(Command<S>)`; `T requires(Predicate<S>)`; `RequiredArgumentBuilder.suggests(SuggestionProvider<S>)`; `CommandDispatcher.register(LiteralArgumentBuilder<S>)`; `Command.SINGLE_SUCCESS` = 1; `CommandContext.getSource()`, `getArgument(String, Class<V>)` | yes |
| `net.minecraft.commands.CommandSourceStack` | | `ServerPlayer getPlayerOrException() throws CommandSyntaxException`; `ServerPlayer getPlayer()` (nullable); `ServerLevel getLevel()`; `Vec3 getPosition()`; `Vec2 getRotation()`; `Entity getEntity()`; `getEntityOrException()`; `void sendSuccess(Supplier<Component>, boolean broadcastToOps)`; `void sendFailure(Component)`; `sendSystemMessage(Component)`; `boolean hasPermission(int)`; `isPlayer()`; `MinecraftServer getServer()`; `RegistryAccess registryAccess()` | yes |
| `net.minecraft.network.chat.Component` | | `static MutableComponent literal(String)`; `translatable(String[, Object...])`; `empty()`; `MutableComponent.append(String|Component)`, `withStyle(ChatFormatting...)`, `withStyle(Style)`, `withColor(int)`; `ChatFormatting.GREEN, RED, YELLOW, GOLD, AQUA, GRAY, WHITE, BOLD, ITALIC, RESET, ...` | yes |
| `ServerLevel` | spawn | `boolean addFreshEntity(Entity)`; `EntityType.create(Level)` then `moveTo(x,y,z,yRot,xRot)` then `addFreshEntity`; or `EntityType.spawn(ServerLevel, BlockPos, MobSpawnType.COMMAND)` | yes |

Also verified: the reference mod registers **client** commands with `net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> ...)` and `ClientCommandManager.literal/argument` with `FabricClientCommandSource.sendFeedback(Component)`; that is a different API from the server-side `CommandRegistrationCallback` above.

## 9. Lifecycle events

| Class | Member | Exact signature | javap |
|---|---|---|---|
| `net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents` | | `SERVER_STARTING`, `SERVER_STARTED` → `void onServerStarted(MinecraftServer)`, `SERVER_STOPPING` → `void onServerStopping(MinecraftServer)`, `SERVER_STOPPED`, `SYNC_DATA_PACK_CONTENTS`, `START/END_DATA_PACK_RELOAD`, `BEFORE_SAVE`, `AFTER_SAVE` | yes |
| `ServerTickEvents` | | `START_SERVER_TICK`, `END_SERVER_TICK` → `void onEndTick(MinecraftServer)`; `START_WORLD_TICK`, `END_WORLD_TICK` → `void onEndTick(ServerLevel)` | yes |
| `ServerEntityEvents` | | `ENTITY_LOAD` → `void onLoad(Entity, ServerLevel)`; `ENTITY_UNLOAD` → `void onUnload(Entity, ServerLevel)`; `EQUIPMENT_CHANGE` | yes |
| `net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents` | | `ALLOW_DAMAGE` → `boolean allowDamage(LivingEntity, DamageSource, float)`; `AFTER_DAMAGE`; `ALLOW_DEATH`; `AFTER_DEATH` → `void afterDeath(LivingEntity, DamageSource)`; `MOB_CONVERSION` | yes |
| `net.minecraft.world.entity.Entity` | removal | `remove(Entity.RemovalReason)`; `isRemoved()`; `getRemovalReason()`; `discard()` (= remove(DISCARDED)); `kill()` | yes |

## 10. Bundled resources, config dir

| Class | Member | Exact signature | javap |
|---|---|---|---|
| `net.fabricmc.loader.api.FabricLoader` | | `static FabricLoader getInstance()`; `Optional<ModContainer> getModContainer(String modId)`; `Path getConfigDir()`; `Path getGameDir()`; `boolean isDevelopmentEnvironment()`; `EnvType getEnvironmentType()` (`EnvType.CLIENT, SERVER`); `boolean isModLoaded(String)`; `Object getGameInstance()` | yes |
| `net.fabricmc.loader.api.ModContainer` | | `Optional<Path> findPath(String file)` (default); `List<Path> getRootPaths()`; `ModMetadata getMetadata()`; deprecated-style `getRootPath()`, `getPath(String)` | yes |
| `net.fabricmc.api.Environment` | annotation | `@Environment(EnvType.CLIENT)` | yes |

Two working ways to load a bundled binary from `src/main/resources/<path>`:
1. `InputStream in = FruitFlyMod.class.getResourceAsStream("/data/fruitfly/connectome.bin")` — works in dev (Loom puts `build/resources/main` on the classpath) and in production (Fabric's classloader serves the mod jar). Wrap with `BufferedInputStream`/`GZIPInputStream` as needed.
2. `FabricLoader.getInstance().getModContainer("fruitfly").orElseThrow().findPath("data/fruitfly/connectome.bin")` → `Path` (a ZipFS path in prod; a real file in dev) → `Files.newInputStream(path)` / `Files.size(path)`.
Config/cache files go under `FabricLoader.getInstance().getConfigDir().resolve("fruitfly")` (= `run/config/fruitfly` in dev, `.minecraft/config/fruitfly` in prod; the reference mod uses `Minecraft.getInstance().gameDirectory.toPath().resolve("config")` client-side, which resolves to the same directory).

## 11. Build (verified from the reference mod C:\Users\drini\OneDrive\Documents\fal-dev\minecraft\falcraft)

- `settings.gradle`: `pluginManagement { repositories { maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }; mavenCentral(); gradlePluginPortal() } }`.
- `gradle.properties`: `org.gradle.jvmargs=-Xmx1G`, `org.gradle.parallel=true`, `org.gradle.configuration-cache=false` (Loom issue #1349), `minecraft_version=1.21.1`, `loader_version=0.17.3`, `loom_version=1.12-SNAPSHOT`, `fabric_version=0.107.0+1.21.1`.
- `gradle/wrapper/gradle-wrapper.properties`: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.0-bin.zip`.
- `build.gradle`: `plugins { id 'fabric-loom' version "${loom_version}"; id 'maven-publish' }`; `loom { splitEnvironmentSourceSets(); mods { "falcraft" { sourceSet sourceSets.main; sourceSet sourceSets.client } } }`; `dependencies { minecraft "com.mojang:minecraft:${project.minecraft_version}"; mappings loom.officialMojangMappings(); modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"; modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}" }`; jar-in-jar: `implementation "org.jcodec:jcodec:0.2.5"` + `include "org.jcodec:jcodec:0.2.5"`; `processResources { inputs.property "version", project.version; filesMatching("fabric.mod.json") { expand "version": inputs.properties.version } }`; `tasks.withType(JavaCompile).configureEach { it.options.release = 21 }`; `java { withSourcesJar(); sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }`.
- With `splitEnvironmentSourceSets()`, client code lives in `src/client/java` and client resources in `src/client/resources` (falcraft: `src/client/java/com/falcraft/FalcraftClient.java`, `src/client/resources/falcraft.client.mixins.json`); common code in `src/main/java`, `src/main/resources/fabric.mod.json`.
- `fabric.mod.json` (falcraft, verified): `"schemaVersion": 1`, `"environment": "*"`, `"entrypoints": { "main": ["com.falcraft.Falcraft"], "client": ["com.falcraft.FalcraftClient"] }`, `"mixins": ["falcraft.mixins.json", {"config": "falcraft.client.mixins.json", "environment": "client"}]` (both mixin lists are empty — the `mixins` entry can be omitted if you have none), `"depends": { "fabricloader": ">=0.17.3", "minecraft": "~1.21.1", "java": ">=21", "fabric-api": "*" }`. Mixin configs use `"compatibilityLevel": "JAVA_21"`.
- Build outputs: `gradlew build` → `build/libs/falcraft-1.0.0.jar` (252,740 bytes, remapped to intermediary) + `-sources.jar`. Run: `gradlew runClient` (Loom generates `runClient`/`runServer`; run dir `run/`, logs in `run/logs/latest.log`).
- JDK: the only JDK on the machine is `C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot` (Temurin 25.0.1+8-LTS, on PATH, `JAVA_HOME` unset; `~/.gradle/jdks` and `~/.jdks` absent), so the 2026-03-28 falcraft build and the 2026-07-14 client run used JDK 25 with Gradle 9.1.0 and Loom 1.12-SNAPSHOT. Gradle's compatibility matrix (docs.gradle.org/current/userguide/compatibility.html): running Gradle on Java 25 requires **Gradle 9.1.0+** (Java 24 → 8.14+, Java 21 → 8.5+). Loom 1.12 release notes: "The plan is for this to be the last version of Loom to support Gradle 8.x"; Loom 1.14 requires Gradle 9.2, 1.16 requires 9.4 — so keep Loom 1.12 with Gradle 9.1.0, or bump both together. No `-Dorg.gradle.java.home` was needed. Loom 1.12 also disables the legacy Mixin annotation processor by default (release notes) — irrelevant unless you add mixins with refmaps.
- Large binary resource: put it under `src/main/resources/` (e.g. `data/fruitfly/connectome.bin`); Gradle's `jar`/Loom `remapJar` copy resources through (DEFLATE compressed). Nothing in Fabric Loader imposes a jar size limit (no evidence found; unverified). Java's zip handles up to 4 GiB / 65,535 entries without ZIP64; set `jar { zip64 = true }` only if exceeding that. If `remapJar` runs out of memory on a very large jar, raise `org.gradle.jvmargs=-Xmx2G` or more (precaution, not verified as necessary). In dev, `runClient` loads resources from `build/resources/main`, not the jar, so iteration is fast. Consider shipping the connectome pre-quantized/compressed (e.g. int16 weights, gzip) since the raw 6.29M edges at weight>=5 are ~50-75 MB as int32 triples.

## 12. Gotchas when writing 1.21.1 mojmap code

1. `defineSynchedData(SynchedEntityData.Builder)` — 1.20.5+ changed from `defineSynchedData()` with `this.entityData.define(...)`; now call `builder.define(accessor, default)` and call `super.defineSynchedData(builder)` first. `SynchedEntityData` lives in `net.minecraft.network.syncher`.
2. `ResourceLocation` has a private constructor in 1.21.x; use `ResourceLocation.fromNamespaceAndPath(ns, path)` (or `parse`, `withDefaultNamespace`). The 1.21.2+ rename to `Identifier` does not apply to 1.21.1 mojmap.
3. `EntityType.Builder.build(String)` (1.21.1) vs `build(ResourceKey<EntityType<?>>)` (1.21.2+). The String is a DFU id: with a plain id you get an `ERROR No data fixer registered for fruitfly` log line at startup (no crash; nothing sets `SharedConstants.IS_RUNNING_IN_IDE`). Use Fabric's injected no-arg `build()` to skip the DFU lookup silently; the deprecated `FabricEntityTypeBuilder` is not needed.
4. `SpawnEggItem(EntityType<? extends Mob>, int bg, int hi, Item.Properties)` in 1.21.1 — colors are `0xRRGGBB` ints; 1.21.4+ dropped the color ints. The egg's `EntityType` must be a non-null, already-registered static (register entity types before items; `BY_ID.put(type, this)` runs in the ctor). Item tabs: no `Properties.group()`; use `ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(FLY_SPAWN_EGG))`.
5. `HudRenderCallback` lambda is `(GuiGraphics guiGraphics, DeltaTracker deltaTracker)` in Fabric API 0.107 (1.21.1); earlier 1.20.x used `float tickDelta`. `WorldRenderContext.tickCounter()` also returns `DeltaTracker`; partial tick = `tickCounter().getGameTimeDeltaPartialTick(true)`.
6. `MobRenderer<T extends Mob, M extends EntityModel<T>>` and `EntityModel<T extends Entity>.setupAnim(T, float, float, float, float, float)` — the 1.21.2+ `EntityRenderState`/`createRenderState()` API does not exist in 1.21.1; the Fabric wiki entity tutorial is written for 1.21.2+ and will not compile.
7. `Model.renderToBuffer(PoseStack, VertexConsumer, int packedLight, int packedOverlay, int color)` — color is a single ARGB int (use `-1`/`0xFFFFFFFF` for white), not `float r,g,b,a` (1.20.x). Same for `ModelPart.render(..., int color)`.
8. `VertexConsumer` builder API is `addVertex(...).setColor(...).setUv(...).setOverlay(...).setLight(...).setNormal(...)` (1.21); the 1.20 names `vertex/color/uv/overlayCoords/uv2/normal/endVertex()` are gone and there is no `endVertex()`. `RenderType.lines()` needs a normal per vertex: pass the normalized line direction via `setNormal(pose, nx, ny, nz)`, and use `Pose`-taking overloads `addVertex(PoseStack.Pose, x, y, z)`. Line width comes from `RenderSystem.lineWidth(float)` only for debug line types; `RenderType.lines()` uses the shader's default width.
9. World render events supply a camera-relative `matrixStack()`: `poseStack.pushPose(); Vec3 cam = ctx.camera().getPosition(); poseStack.translate(-cam.x, -cam.y, -cam.z); ... poseStack.popPose();`. `consumers()` is a `MultiBufferSource.BufferSource` for `AFTER_ENTITIES`/`AFTER_TRANSLUCENT`; for `LAST`/`END` it may be null (Fabric Javadoc, not verified locally) — check for null.
10. Custom movement: `LivingEntity.aiStep()` calls `travel(new Vec3(xxa, yya, zza))` only when `isEffectiveAi()` (server) or locally controlled; on the client the mob is interpolated from server packets (`lerpTo`), so run the brain server-side only (`if (!level().isClientSide)`). `Mob.serverAiStep()` (final) runs `customServerAiStep()` BEFORE `moveControl.tick()`, so anything written to `xxa/zza` in `customServerAiStep` can be overwritten by the default `MoveControl`; the robust approach is to override `travel(Vec3)` fully: compute velocity from the decoded motor output, `setDeltaMovement(v)`, `move(MoverType.SELF, getDeltaMovement())`, apply your own drag, then `calculateEntityAnimation(false)` for limb swing. Leave `registerGoals()` empty; no goals + no navigation path = no vanilla AI. Gravity: `setNoGravity(true)` while flying, or override `getDefaultGravity()`/set `Attributes.GRAVITY` (LivingEntity reads it).
11. `Level.isClientSide` is both a public final field and a method `isClientSide()`; `Entity.level()` is a method (not a field) in 1.21.
12. `Mob.getMaxHeadYRot()/getMaxHeadXRot()/getHeadRotSpeed()` return `int`.
13. Some `SoundEvents` constants are `Holder.Reference<SoundEvent>` (e.g. `GENERIC_EXPLODE`, `NOTE_BLOCK_*`, `SOUL_ESCAPE`); `Level.playSound(Player, double,double,double, Holder<SoundEvent>, SoundSource, float, float)` accepts holders, or call `.value()`.
14. Fabric API 0.107 nested-module class files are in intermediary names; if you javap them you must translate (table below). In the dev workspace they appear with mojmap names.
15. `CreativeModeTabs.SPAWN_EGGS` etc. are `private` in the mojmap jar; they are usable from mod code only thanks to Fabric's transitive access wideners (module `fabric-transitive-access-wideners-v1`, present in 0.107). Same for a number of other vanilla members — if a symbol is private in javap but public in Fabric templates, that is why.
16. Networking: register the payload type once per direction on the common side (`PayloadTypeRegistry.playS2C().register(TYPE, CODEC)` in `onInitialize`, which runs on both client and dedicated server), and the receiver on the client (`ClientPlayNetworking.registerGlobalReceiver`) — registering the receiver before the type throws. The record's `type()` return type must be `CustomPacketPayload.Type<? extends CustomPacketPayload>`.
17. `Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceLocation, EntityType<T>)` returns the same `EntityType<T>` so it can initialize a `public static final EntityType<FlyEntity> FLY = ...` field; make sure the class holding it is loaded (touched) from `onInitialize()`.
18. `EntityRendererRegistry.register` must run in the **client** entrypoint (`ClientModInitializer.onInitializeClient`, `src/client/java`), as must `EntityModelLayerRegistry.registerModelLayer`, `HudRenderCallback`, `WorldRenderEvents`, `KeyBindingHelper`; referencing `net.minecraft.client.*` from `src/main/java` fails to compile under `splitEnvironmentSourceSets()`.
19. Default `EntityType` tracking: `clientTrackingRange` 5 chunks (80 blocks), `updateInterval` 3 ticks. For a tiny fast mob whose velocity is set every tick, use `updateInterval(1)` or `2`, and consider Fabric's `alwaysUpdateVelocity(true)` so clients interpolate smoothly.
20. Bash-on-Windows javap: use `:` between classpath entries, and never put `\\$"` inside a double-quoted grep pattern (bash parses `$"` as a locale string and the command silently breaks).

## 13. Intermediary → mojmap translation used for Fabric API signatures (from Loom mappings.tiny)

| intermediary | mojmap |
|---|---|
| class_1299 / class_1299$class_1300 / class_1299$class_4049 | EntityType / EntityType.Builder / EntityType.EntityFactory |
| class_1297 / class_1309 / class_1308 / class_1311 | Entity / LivingEntity / Mob / MobCategory |
| class_5132 / class_5132$class_5133 | AttributeSupplier / AttributeSupplier.Builder |
| class_5617 / class_5601 / class_5607 | EntityRendererProvider / ModelLayerLocation / LayerDefinition |
| class_332 / class_9779 / class_310 / class_638 / class_746 | GuiGraphics / DeltaTracker / Minecraft / ClientLevel / LocalPlayer |
| class_304 / class_3675$class_306 | KeyMapping / InputConstants.Key |
| class_2540 / class_9129 / class_9139 | FriendlyByteBuf / RegistryFriendlyByteBuf / StreamCodec |
| class_8710 / class_8710$class_9154 / class_8710$class_9155 | CustomPacketPayload / CustomPacketPayload.Type / CustomPacketPayload.TypeAndCodec |
| class_3222 / class_3218 / class_3244 | ServerPlayer / ServerLevel / ServerGamePacketListenerImpl |
| class_1923 / class_2338 / class_243 / class_2382 / class_2586 | ChunkPos / BlockPos / Vec3 / Vec3i / BlockEntity |
| class_2168 / class_7157 / class_2170$class_5364 | CommandSourceStack / CommandBuildContext / Commands.CommandSelection |
| class_2960 / class_2561 / class_1282 | ResourceLocation / Component / DamageSource |
| class_761 / class_4587 / class_4184 / class_757 / class_765 / class_3695 / class_4597 / class_4604 | LevelRenderer / PoseStack / Camera / GameRenderer / LightTexture / ProfilerFiller / MultiBufferSource / Frustum |
| class_5321 / class_1761 / class_1761$class_7705 / class_1799 / class_1935 | ResourceKey / CreativeModeTab / CreativeModeTab.TabVisibility / ItemStack / ItemLike |
| class_9168 / class_2902$class_2903 / class_1317$class_4306 | SpawnPlacementType / Heightmap.Types / SpawnPlacements.SpawnPredicate |
| class_2596 / class_7648 | Packet / PacketSendListener |

## 14. Skeletons (mojmap, compile-shaped against the verified signatures)

```java
// src/main/java — common
public final class FruitFlyMod implements ModInitializer {
    public static final String MOD_ID = "fruitfly";
    public static ResourceLocation id(String p) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, p); }

    public static final EntityType<FlyEntity> FLY = Registry.register(
        BuiltInRegistries.ENTITY_TYPE, id("fruit_fly"),
        EntityType.Builder.of(FlyEntity::new, MobCategory.CREATURE)
            .sized(0.35f, 0.25f).eyeHeight(0.15f)
            .clientTrackingRange(10).updateInterval(1)
            .build()); // Fabric-injected no-arg build(); or .build("fruit_fly") (logs one DFU error)

    public static final Item FLY_SPAWN_EGG = Registry.register(
        BuiltInRegistries.ITEM, id("fruit_fly_spawn_egg"),
        new SpawnEggItem(FLY, 0xB5651D, 0xE0242E, new Item.Properties()));

    public record BrainStatePayload(int entityId, float[] rates) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BrainStatePayload> TYPE = new CustomPacketPayload.Type<>(id("brain_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BrainStatePayload> CODEC =
            CustomPacketPayload.codec(BrainStatePayload::write, BrainStatePayload::new);
        BrainStatePayload(RegistryFriendlyByteBuf buf) { this(buf.readVarInt(), readFloats(buf)); }
        void write(RegistryFriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeVarInt(rates.length); for (float f : rates) buf.writeFloat(f); }
        static float[] readFloats(FriendlyByteBuf buf) { float[] a = new float[buf.readVarInt()]; for (int i = 0; i < a.length; i++) a[i] = buf.readFloat(); return a; }
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @Override public void onInitialize() {
        FabricDefaultAttributeRegistry.register(FLY, FlyEntity.createAttributes());
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(e -> e.accept(FLY_SPAWN_EGG));
        PayloadTypeRegistry.playS2C().register(BrainStatePayload.TYPE, BrainStatePayload.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("fly")
                .then(Commands.literal("spawn").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerLevel level = src.getLevel(); Vec3 p = src.getPosition();
                    FlyEntity fly = FLY.create(level);
                    fly.moveTo(p.x, p.y + 1, p.z, 0f, 0f);
                    level.addFreshEntity(fly);
                    src.sendSuccess(() -> Component.literal("spawned fly #" + fly.getId()), false);
                    return Command.SINGLE_SUCCESS; }))
                .then(Commands.literal("stim").then(Commands.argument("gain", FloatArgumentType.floatArg(0f, 10f))
                    .executes(ctx -> { float g = FloatArgumentType.getFloat(ctx, "gain"); /*...*/ return 1; })))));
        ServerTickEvents.END_SERVER_TICK.register(server -> { /* broadcast: ServerPlayNetworking.send(player, payload) for player in PlayerLookup.tracking(fly) */ });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> { if (entity instanceof FlyEntity f) f.releaseBrain(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { /* shutdown executor */ });
    }
}

public class FlyEntity extends Mob {
    private static final EntityDataAccessor<Float> DATA_ACTIVITY = SynchedEntityData.defineId(FlyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_BEHAVIOR = SynchedEntityData.defineId(FlyEntity.class, EntityDataSerializers.BYTE);
    public FlyEntity(EntityType<? extends FlyEntity> type, Level level) { super(type, level); this.setNoGravity(false); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.FLYING_SPEED, 0.6).add(Attributes.FOLLOW_RANGE, 24.0); }
    @Override protected void registerGoals() { /* none: brain drives everything */ }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(DATA_ACTIVITY, 0f); b.define(DATA_BEHAVIOR, (byte) 0); }
    @Override protected void customServerAiStep() { /* sample senses (level().clip(new ClipContext(getEyePosition(), getEyePosition().add(getViewVector(1f).scale(16)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)) ...), step LIF network, decode motor -> desiredVelocity, yawRate */ }
    @Override public void travel(Vec3 ignored) {
        if (!isEffectiveAi()) return;
        setYRot(getYRot() + yawRateDegPerTick); yBodyRot = getYRot(); yHeadRot = getYRot();
        Vec3 v = getDeltaMovement().scale(0.8).add(desiredVelocity.scale(0.2));
        if (!flying) v = v.add(0, -getGravity(), 0);
        setDeltaMovement(v); move(MoverType.SELF, getDeltaMovement());
        calculateEntityAnimation(false);
    }
    @Override public boolean isPushable() { return false; }
    @Override protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) { }
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override public boolean isFlapping() { return flying; }
    @Override protected SoundEvent getAmbientSound() { return flying ? SoundEvents.BEE_LOOP : null; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.BEE_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.BEE_DEATH; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putLong("Seed", seed); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); seed = tag.getLong("Seed"); }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) { /* feed: player.getItemInHand(hand).is(Items.APPLE) ... */ return super.mobInteract(player, hand); }
}
```

```java
// src/client/java — client
public final class FruitFlyClient implements ClientModInitializer {
    public static final ModelLayerLocation FLY_LAYER = new ModelLayerLocation(FruitFlyMod.id("fruit_fly"), "main");
    static KeyMapping TOGGLE_HUD;
    @Override public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(FLY_LAYER, FlyModel::createBodyLayer);
        EntityRendererRegistry.register(FruitFlyMod.FLY, FlyRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(FruitFlyMod.BrainStatePayload.TYPE, (payload, context) -> {
            ClientLevel level = context.client().level; if (level == null) return;
            if (level.getEntity(payload.entityId()) instanceof FlyEntity f) BrainHud.update(f, payload.rates()); });
        TOGGLE_HUD = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.fruitfly.hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.fruitfly"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> { while (TOGGLE_HUD.consumeClick()) BrainHud.toggle(); });
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance(); if (mc.player == null || mc.options.hideGui) return;
            int w = mc.getWindow().getGuiScaledWidth();
            guiGraphics.fill(w - 110, 10, w - 10, 60, 0x80000000);
            guiGraphics.drawString(mc.font, "DNa02 " + rate, w - 105, 15, 0xFFFFFF, true); });
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            PoseStack ps = ctx.matrixStack(); Vec3 cam = ctx.camera().getPosition();
            ps.pushPose(); ps.translate(-cam.x, -cam.y, -cam.z);
            VertexConsumer vc = ctx.consumers().getBuffer(RenderType.lines());
            PoseStack.Pose pose = ps.last();
            // one debug ray: from -> to, normal = direction
            vc.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(255, 0, 0, 255).setNormal(pose, nx, ny, nz);
            vc.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(255, 0, 0, 255).setNormal(pose, nx, ny, nz);
            LevelRenderer.renderLineBox(ps, vc, fly.getBoundingBox(), 1f, 1f, 0f, 1f);
            ps.popPose(); });
    }
}

public class FlyModel extends HierarchicalModel<FlyEntity> {
    private final ModelPart root, body, head, leftWing, rightWing;
    public FlyModel(ModelPart root) { this.root = root; body = root.getChild("body"); head = body.getChild("head"); leftWing = body.getChild("left_wing"); rightWing = body.getChild("right_wing"); }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition(); PartDefinition r = mesh.getRoot();
        PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2f, -2f, -3f, 4f, 4f, 6f), PartPose.offset(0f, 21f, 0f));
        body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 10).addBox(-1.5f, -1.5f, -2f, 3f, 3f, 2f), PartPose.offset(0f, -0.5f, -3f));
        body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(20, 0).addBox(0f, 0f, -1f, 6f, 0f, 3f), PartPose.offsetAndRotation(1f, -2f, -1f, 0f, 0f, 0f));
        body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(20, 4).mirror().addBox(-6f, 0f, -1f, 6f, 0f, 3f), PartPose.offset(-1f, -2f, -1f));
        return LayerDefinition.create(mesh, 64, 32); }
    @Override public ModelPart root() { return root; }
    @Override public void setupAnim(FlyEntity fly, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * ((float) Math.PI / 180f); head.xRot = headPitch * ((float) Math.PI / 180f);
        float flap = fly.isFlapping() ? Mth.cos(ageInTicks * 2.1f) * 1.2f : 0.3f; leftWing.zRot = -flap; rightWing.zRot = flap; }
}

public class FlyRenderer extends MobRenderer<FlyEntity, FlyModel> {
    private static final ResourceLocation TEXTURE = FruitFlyMod.id("textures/entity/fruit_fly.png");
    public FlyRenderer(EntityRendererProvider.Context ctx) { super(ctx, new FlyModel(ctx.bakeLayer(FruitFlyClient.FLY_LAYER)), 0.2f); }
    @Override public ResourceLocation getTextureLocation(FlyEntity e) { return TEXTURE; }
}
```

Texture at `src/main/resources/assets/fruitfly/textures/entity/fruit_fly.png` (64x32 for the layer above); spawn egg model at `assets/fruitfly/models/item/fruit_fly_spawn_egg.json` = `{"parent":"minecraft:item/template_spawn_egg"}`.

## 15. Not verified / open

- Fabric Javadoc statements (payload handler thread, `consumers()` nullability in `LAST`/`END`) could not be read because the cached `-sources.jar` is empty; treat as medium confidence.
- `fabric-transitive-access-wideners-v1` making `CreativeModeTabs.SPAWN_EGGS` public was inferred from the module's existence and common template usage, not by parsing the `.accesswidener` file.
- Exact `FlyingMoveControl` arguments used by Bee/Parrot (e.g. `new FlyingMoveControl(this, 20, true)`) were not read from bytecode.
- No hard evidence on Fabric Loader jar-size limits; ZIP64/`-Xmx` notes are precautions.
- Loom 1.12 with JDK 25 is verified only empirically (reference mod built/ran on this machine with the only installed JDK = 25.0.1); Loom's own release notes do not mention Java 25.
