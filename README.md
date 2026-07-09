# Advanced Hitbox Framework (AHF)

A standalone Minecraft library that provides server-authoritative, deterministic oriented-bounding-box (OBB) hit classification for humanoid `LivingEntity` targets. AHF computes a fully posed per-limb rig — six OBBs matching the player model's actual animation state — and classifies which body part a ray, point, or damage event strikes, including through-and-through pierce across multiple limbs. The central contract for consumers is `HitboxPart`: a six-constant enum (HEAD, TORSO, LEFT\_ARM, RIGHT\_ARM, LEFT\_LEG, RIGHT\_LEG) that AHF returns from every classification call. AHF contains **no** medical logic; damage application, trauma, armor, and any notion of "downed" are consumer responsibilities.

Extracted and generalised from the WFMedical mod.

---

## Supported Versions

| Minecraft | Loader | Project |
|-----------|--------|---------|
| 1.20.1 | Forge | `:1.20.1` |
| 1.21.1 | NeoForge | `:1.21.1` |
| 26.1 | NeoForge | `:26.1` |

Built with the [Prism](https://github.com/PrismMC/Prism) multi-version Gradle plugin. The `:common` subproject is pure Java (no Minecraft on the classpath) and is shared by all three versions.

---

## Features

- **Posed per-limb OBBs** — six oriented bounding boxes (head, torso, arms, legs) computed from the live Minecraft animation state: standing, crouching, and prone/swimming/elytra poses, plus vanilla arm/item/attack animations (bow, gun aim, crossbow charge/hold, spear, block, spyglass, etc.).
- **Ray and point classification** — `HitboxApi.classifyRay(victim, from, to)` and `classifyHit(victim, src, cat)` return the `HitboxPart` struck by a world-space ray or `DamageSource`.
- **Through-and-through pierce** — `classifyHitPierced(victim, src, cat)` returns an ordered `List<HitboxPart>` of all OBBs the ray penetrates, consuming a configurable penetration budget per limb.
- **Weighted fallback sampling** — `HitSampler.pick(victim, src, cat, rand)` falls back to weighted random limb selection (with category biases for BALLISTIC, FALL, EXPLOSION, etc.) when geometry cannot be reconstructed.
- **Envelope hit-registration** — configurable AABB expansion per pose (STANDING/CROUCHING/PRONE) used for gap-shot detection and hit-reg in `HitRegistration`.
- **Debug overlay** — client-side OBB visualiser rendered in entity-local space; toggle with **K**, scroll mouse wheel to cycle EDGES / FILLED styles.
- **CLIENT\_HINT pose streaming** — client sends its true rendered OBBs to the server via `PoseStreamPacket` (C2S); the server uses them when `hitAuthority = CLIENT_HINT`, falling back to server-recomputed pose when the hint is stale or implausible.
- **Custom stances** — mods register a `Stance` implementation via `AhfStances.register(...)` to override the posed OBBs for any entity state (e.g. downed, prone-crawl, ragdoll). AHF ships with no registered stances.
- **JSON rig spec** — per-server OBB geometry overrides loaded from `<config>/ahf_hitbox_rig.json` via `RigSpecIO`.
- **Pluggable hooks** (`AhfHooks`) — seams for gun-item detection, bullet hit-position injection, envelope-target predicate, and gun aim-progress, all with safe defaults.
- **TACZ integration** — on 1.20.1 only, the hooks are wired to the Timeless & Classics Zero gun mod (compileOnly dependency) for accurate gun arm pose and bullet hit-position. Dropped on 1.21.1 and 26.1; hooks remain at defaults.

---

## Quickstart — Determine Which Limb Was Hit

Add AHF as a dependency, then call into `HitboxApi` or `HitSampler` from your damage event handler:

```java
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.hit.HitCategory;
import com.norwood.ahf.hit.HitSampler;
import com.norwood.ahf.part.HitboxPart;

// Option A — classify a DamageSource geometrically, fall back to weighted sampling:
HitboxPart part = HitSampler.pick(victim, damageSource, HitCategory.BALLISTIC, level.random);

// Option B — classify a damage source directly (returns null if untraceable):
@Nullable HitboxPart part = HitboxApi.classifyHit(victim, damageSource, HitCategory.BALLISTIC);

// Option C — classify an explicit world-space ray:
@Nullable HitboxPart part = HitboxApi.classifyRay(victim, eyePos, eyePos.add(lookDir.scale(64)));

// Option D — pierce: ordered list of every limb the ray passes through:
List<HitboxPart> pierced = HitboxApi.classifyHitPierced(victim, damageSource, HitCategory.BALLISTIC);
```

### Mapping HitboxPart to your own enum

`HitboxPart` constants are stable and ordered (HEAD=0, TORSO=1, LEFT\_ARM=2, RIGHT\_ARM=3, LEFT\_LEG=4, RIGHT\_LEG=5). Map to your own limb type by ordinal or name:

```java
// By ordinal (fastest — keep your enum in the same order):
MyLimb limb = MyLimb.VALUES[part.ordinal()];

// By name (order-independent):
MyLimb limb = MyLimb.valueOf(part.name());  // both use identical constant names
```

`HitboxPart` also exposes `isVital()`, `isLeg()`, `isArm()`, `getHitWeight()`, and `getDisplayName()` for common consumer needs.

---

## Custom Stances

A **stance** overrides the posed OBBs for a specific entity state. The intended use case is non-upright or non-standard body positions — downed, prone-crawl, unconscious, vehicle-mounted, etc. Implement the `Stance` interface (per-version, not in `:common`) and register it early in mod initialisation.

### Stance interface

```java
public interface Stance {

    /** Unique identifier for this stance (namespaced, e.g. "mymod:downed"). */
    String id();

    /** Higher priority wins when multiple stances apply. Default: 0. */
    default int priority() { return 0; }

    /** Whether this stance applies to the given entity this tick. */
    boolean appliesTo(LivingEntity entity);

    /**
     * Whether the entity is still roughly upright in this stance.
     * Return false for lying/prone stances — suppresses vertical band classification
     * and defers limb selection to HitSampler weighted fallback.
     */
    default boolean upright() { return false; }

    /** Horizontal envelope expansion used by HitRegistration (metres). Default: AhfConfig.stanceEnvelopeReachHorizontal() = 1.0 */
    default double envelopeReachHorizontal() { return AhfConfig.stanceEnvelopeReachHorizontal(); }

    /** Vertical envelope expansion used by HitRegistration (metres). Default: AhfConfig.stanceEnvelopeReachVertical() = 0.3 */
    default double envelopeReachVertical()   { return AhfConfig.stanceEnvelopeReachVertical(); }

    /**
     * Transform the six posed OBBs into entity-local space for this stance.
     * Called by HumanoidRig.compute after the base animation is applied.
     * Use LocalRig.Slot.set(rig, newObb) to replace individual OBBs.
     */
    void apply(LivingEntity entity, HumanoidRig.LocalRig rig);
}
```

### Registering a stance

```java
// In your mod initialiser (e.g. FMLCommonSetupEvent or mod constructor):
AhfStances.register(new MyDownedStance());

// Query at runtime:
Stance active = AhfStances.active(entity);   // highest-priority match, or null
boolean any   = AhfStances.isActive(entity);
List<Stance> all = AhfStances.all();
```

---

## Pluggable Hooks (`AhfHooks`)

`AhfHooks` provides four injection points that let consumer mods (or loader-specific compat code) extend AHF behaviour without patching internals.

| Hook setter | Signature | Default | Purpose |
|---|---|---|---|
| `setHeldGunPredicate` | `Predicate<ItemStack>` | always `false` | Marks a held item as a gun — activates `GUN` arm pose |
| `setBulletHitPos` | `Function<DamageSource, Optional<Vec3>>` | `Optional.empty()` | Injects a precise bullet hit position from a gun mod's own hit-scan |
| `setEnvelopeTargetPredicate` | `Predicate<Entity>` | `e instanceof Player` | Determines which non-player entities receive envelope hit-reg |
| `setGunAimProgress` | `Function<LivingEntity, Float>` | `0.0f` | Returns 0–1 aim-down-sights progress for interpolating gun arm pose |

On **1.20.1**, `AhfForge` wires all four hooks to the TACZ compat classes (`TaczCompat`, `TaczGunState`) at startup. On **1.21.1** and **26.1** the hooks stay at their defaults (no TACZ dependency).

```java
// Example: wire a custom gun mod
AhfHooks.setHeldGunPredicate(stack -> stack.getItem() instanceof MyGunItem);
AhfHooks.setBulletHitPos(src -> MyGunMod.getBulletHitPos(src));
AhfHooks.setGunAimProgress(e -> MyGunMod.getAimProgress(e));
```

---

## Configuration (`AhfConfig`)

`AhfConfig` is a plain static holder in `:common` (no file I/O — integrate with your config system or leave at defaults). All fields are settable at runtime via paired `set*` methods.

| Key | Default | Description |
|---|---|---|
| `geometricHitLocation` | `true` | Use OBB/ray geometry for hit classification; false = always weighted random |
| `poseAwareArms` | `true` | Adjust arm classification for aiming poses (bow, gun, spear) |
| `headBandBottom` | `0.74` | Relative Y threshold below which a hit is not HEAD (0–1 of entity height) |
| `legBandTop` | `0.40` | Relative Y threshold above which a hit is not a LEG |
| `armSideThreshold` | `0.80` | Normalised X distance from centre beyond which a hit is an ARM |
| `meleeReach` | `3.0` | Ray length (metres) used for melee hit classification |
| `riggedLimbBoxes` | `true` | Enable OBB rig classification for Players |
| `limbBoxPadding` | `0.02` | Half-extent padding (metres) added to every OBB |
| `hitboxDebug` | `false` | Enable debug renderer by default |
| `gunArmPose` | `true` | Apply the baked `GunArmPose` when a gun is held (vs bow fallback) |
| `hitRegistrationMode` | `ENVELOPE` | `OFF` / `ENVELOPE` / `PRECISE` — controls gap-shot rejection |
| `hitEnvelopeReachHorizontal(STANDING)` | `0.4` | Envelope horizontal reach for standing pose |
| `hitEnvelopeReachHorizontal(CROUCHING)` | `0.5` | Envelope horizontal reach for crouching pose |
| `hitEnvelopeReachHorizontal(PRONE)` | `1.0` | Envelope horizontal reach for prone/swimming/elytra |
| `hitEnvelopeReachVertical(STANDING)` | `0.2` | Envelope vertical reach for standing pose |
| `hitEnvelopeReachVertical(CROUCHING)` | `0.1` | Envelope vertical reach for crouching pose |
| `hitEnvelopeReachVertical(PRONE)` | `0.3` | Envelope vertical reach for prone/swimming/elytra |
| `stanceEnvelopeReachHorizontal` | `1.0` | Fallback horizontal reach for custom stances that don't override it |
| `stanceEnvelopeReachVertical` | `0.3` | Fallback vertical reach for custom stances that don't override it |
| `hitAuthority` | `SERVER` | `SERVER` = always recompute on server; `CLIENT_HINT` = prefer client-streamed pose |
| `poseStreamMinIntervalTicks` | `2` | Minimum tick interval between `PoseStreamPacket` sends |
| `poseStreamMaxIntervalTicks` | `10` | Maximum tick interval between `PoseStreamPacket` sends |
| `poseHintMaxAgeTicks` | `30` | Server discards a CLIENT\_HINT after this many ticks |
| `poseHintMargin` | `0.6` | Plausibility margin (metres) for sanity-checking received OBBs |
| `penetrationEnabled` | `false` | Enable through-and-through pierce (used by `classifyHitPierced`) |
| `penetrationBudget` | `1.0` | Total energy available to pierce limbs |
| `penetrationEnergyFalloff` | `0.5` | Energy multiplier applied after each limb is pierced |
| `penetrationResistance(HEAD)` | `0.5` | Energy cost to pierce the head |
| `penetrationResistance(TORSO)` | `0.8` | Energy cost to pierce the torso |
| `penetrationResistance(ARM)` | `0.25` | Energy cost to pierce an arm |
| `penetrationResistance(LEG)` | `0.4` | Energy cost to pierce a leg |

---

## Building

There is **no `gradlew`**. Use the system Gradle installation with Java 21.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"

# Compile the shared pure-Java module only:
gradle :common:compileJava --console=plain

# Build a specific version (compiles :common first):
gradle :1.20.1:build --console=plain
gradle :1.21.1:build --console=plain
gradle :26.1:build   --console=plain

# Launch a client (best-effort; requires a display):
gradle :1.20.1:runClient --console=plain
```

`org.gradle.daemon=false` is set in `gradle.properties` — single-use daemon behaviour is expected.

The 1.20.1 build fetches the TACZ CurseMaven artifact (`curse.maven:timeless-and-classics-zero-1028108:8141310`) as a `compileOnly` dependency on first build; network access is required.

---

## Package Layout

```
ahf/
├── common/src/main/java/com/norwood/ahf/          # Pure Java — no Minecraft
│   ├── Ahf.java                                   # MOD_ID + SLF4J logger
│   ├── part/HitboxPart.java                       # The six-limb enum (consumer contract)
│   ├── hit/HitCategory.java                       # Damage category enum (BALLISTIC, SLASHING, …)
│   ├── hit/HitAuthority.java                      # SERVER / CLIENT_HINT
│   ├── config/AhfConfig.java                      # Static config holder + defaults
│   ├── config/HitRegMode.java                     # OFF / ENVELOPE / PRECISE
│   └── rig/RigTuning.java                         # Live OBB delta tuning (runtime adjustment)
│       RigSpec.java                               # Rig geometry arrays
│
└── versions/1.20.1/src/main/java/com/norwood/ahf/ # Forge 1.20.1 (mirrored in 1.21.1 / 26.1)
    ├── AhfForge.java                              # @Mod entrypoint
    ├── geometry/Obb.java                          # Oriented bounding box + ray/point tests
    ├── rig/
    │   ├── HumanoidRig.java                       # Animation + OBB computation; LocalRig inner class
    │   ├── RigCache.java                          # Per-tick cache + CLIENT_HINT hint store
    │   ├── RigSpecIO.java                         # ahf_hitbox_rig.json loader
    │   └── GunArmPose.java                        # Baked HOLD/AIM arm pose (no TACZ import)
    ├── hit/
    │   ├── HitboxApi.java                         # classifyRay / classifyHit / classifyHitPierced
    │   ├── HitSampler.java                        # pick / pickPierced with weighted fallback
    │   └── HitRegistration.java                   # Envelope AABB + gap-shot rejection
    ├── hook/AhfHooks.java                         # Pluggable seams (gun, bullet pos, envelope, aim)
    ├── stance/
    │   ├── Stance.java                            # Custom-stance interface
    │   └── AhfStances.java                        # Registry (register / active / isActive / all)
    ├── client/
    │   ├── HitboxDebugRenderer.java               # OBB overlay (EDGES / FILLED; K to toggle)
    │   ├── HitboxRenderType.java                  # ahf_hitbox_filled RenderType
    │   ├── AhfKeybinds.java                       # key.ahf.toggle_hitbox (default K)
    │   ├── AhfClientEvents.java                   # Keybind poll + scroll-to-cycle-style
    │   └── PoseStreamClient.java                  # Rate-limited C2S pose sender
    ├── network/
    │   ├── AhfNetwork.java                        # SimpleChannel ahf:main registration
    │   ├── PoseStreamPacket.java                  # C2S — 6 OBBs → RigCache.submitHint
    │   └── HitAuthorityPacket.java                # S2C — enable/disable pose streaming
    └── compat/tacz/                               # 1.20.1 only
        ├── TaczCompat.java                        # isHeldGun / bulletHitPos (no hard TACZ ref)
        └── TaczGunState.java                      # aimingProgress (imports IGunOperator)
```

---

## Credits

The OBB hitbox system was designed and originally implemented as part of the **WFMedical** Minecraft mod. AHF extracts it into a reusable forge/neoforge library.
