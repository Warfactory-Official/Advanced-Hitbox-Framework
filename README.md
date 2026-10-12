# Advanced Hitbox Framework (AHF)

Posed per-limb OBB rig + hit-part classification (`HitboxPart`) for humanoid `LivingEntity` targets.

| Minecraft | Loader | Project | Status |
|---|---|---|---|
| 1.21.1 | NeoForge | `:1.21.1` | maintained (reference) |
| 1.20.1 | Forge | `:1.20.1` | parity; TACZ hooks wired |
| 26.1 | NeoForge | `:26.1` | parity minus PlayerAnimator layer (drawn pose vanilla) |

`:common` = pure Java, compiled into each version jar.

## Entry points (all versions)
- `HitboxApi.classifyRay` / `classifyRayPierced(victim, from, to)` -> `RayHit(part, t, limb)` / entry-ordered list. Segment-limited. Rig limb box first; else nearest part to the registration-box entry (`limb` false; PRECISE: only within `hitGapRejectTolerance` of a limb box).
- `HitboxApi.classifyHit` / `classifyHitPierced(victim, src, cat)`, `HitSampler.pick` (weighted fallback), `shouldRejectGap`. Source implementing `AhfHitSource` (segment, tick, optional part) => classified + gap-tested on that segment at that tick; part given => never a gap.
- `HitRegistration.registrationBox(entity[, box])`; applied to vanilla `ProjectileUtil.getEntityHitResult` (both overloads; eye-inside test keeps the vanilla box).
- Lag comp: `EntityHistory.snapshot` / `bounds` / `has` / `candidates(level, gameTime, from, to[, pad <= MAX_CANDIDATE_PAD], out)` / `segmentHits(box, pad, from, to)` / `broadcastTick`; `HitboxApi.classifyRay[Pierced](victim, from, to, gameTime | Snapshot)`, `rayHitsWithin(victim, from, to, gameTime, pad)`; `RttProbe.rttNanos` / `maxRewindTicks` / `boundViewTick(player, viewTick, now)`.
- Stance (server): `AhfStance.request(player, StanceRequest)`, `setDowned`, `setCrawl`, `weaponsBlocked`, `posture`, `get` (state view), `getOrCreate`, `eyeOffset`, `addRule(StanceRule)` (runs on owner + server). Owner: `StanceClient.request`.
- `AhfStances.register(Stance)`; `HumanoidRig.setAnimSampler` (PlayerAnimator adapter + `ahf:stance` layer registered client-side when `playeranimator` is loaded).
- `AhfHooks` (any-of / priority, never replace): `addHeldGun`, `addEnvelopeTarget` (players always targets), `addGunAimProgress(priority, fn)` (NaN = no opinion; all NaN => 0).
- Jointed body (client): `BodyRenderer` draws players as `assets/ahf/models/player_{wide,slim}.glb` with PA bends (waist, elbows, knees); `AhfBody.overrideArm` for consumer arm IK; `BodyRenderer.skin` = joint matrices.
- Pose library: `PoseLibrary.register(id, json)` (PlayerAnimator Bedrock/GeckoLib player-animation file, parsed and evaluated as PA 2.0.4, no PA classes); `AhfHooks.addPoseSet(fn)` names a gun holder's set; `GunPoses.select` = shared clip choice (rig + owner's render layers). `HumanoidRig.computeServer` = rig without the client animation sampler.

## Config
- `config/ahf-common.toml` (`AhfConfigSpec`) -> `AhfConfig`; setters package-private. Reload re-binds, reloads `config/ahf_hitbox_rig.json`, re-sends hit authority.
- Client pose stream on iff `hitAuthority = CLIENT_HINT || animatedHitboxes` (server-sent at login).

## Build
Launcher JVM >= 21 (Prism 0.5.17); `gradle/gradle-daemon-jvm.properties` selects a local JDK 21 when `JAVA_HOME` is older. Gradle 9.4.0: Prism's fabric-loom 1.16.1 needs plugin API 9.4; 9.2.0 fails to resolve.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew :1.21.1:build                # jar + JUnit (src/test); same for :1.20.1, :26.1
./gradlew :1.21.1:runGameTestServer    # src/gametest; not in the jar; same for :1.20.1, :26.1
```

## Credits
Rig extracted from WFMedical.
