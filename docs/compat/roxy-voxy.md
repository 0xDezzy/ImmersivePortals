# Roxy / Voxy integration

## Installation target

- Minecraft 1.21.1, Java 21, native NeoForge Immersive Portals CE.
- [Roxy 0.4.0 for Minecraft 1.21.1](https://modrinth.com/mod/roxy/version/cDmwpuYr).
  Install its required Forgified Fabric API **0.116.14+2.3.0+1.21.1**
  ([`dAxle9F7`](https://modrinth.com/mod/forgified-fabric-api/version/dAxle9F7)).
  CE's NeoForge 21.1.228 baseline satisfies this release's 21.1.224 minimum.
  The supplied Roxy 0.4.1-d-1 source checkout was also used as a reference; that
  development build targets NeoForge 21.1.241 and is not a tested release here.
- Sodium 0.8.12+mc1.21.1 for NeoForge, not Embeddium.
- Original **Fabric Voxy 0.2.16-beta for Minecraft 1.21.11**, Modrinth
  version [`H3w2nVdU`](https://modrinth.com/mod/voxy/version/H3w2nVdU).
- Optional official Iris 1.8.14-beta.1+1.21.1-neoforge and a Voxy-compatible pack.

Place these separately downloaded mods into the client's `mods` directory.
Roxy translates that specific newer Fabric Voxy jar to NeoForge/1.21.1; this
does not turn CE into a Fabric mod or require Sinytra Connector. Do not substitute
the current Voxy development branch (Minecraft 26.2) or a random 1.21.1 Voxy build.
Voxy is All Rights Reserved; this repository neither vendors nor publishes it.

## Integration behavior

The optional pseudo mixins are client-only and gated by NeoForge's loading mod
list: both `roxy` and translated `voxy` must be present. The pinned version and
required target classes fail fast rather than silently leaving partial patches.
No Voxy classes appear in CE's compile or runtime dependencies.

- **ViewportSelector:** each immutable outer-to-inner portal path receives its
  own temporal/HiZ viewport. The cache holds at most ten portal entries plus the
  outer view. Voxy's own Vivecraft/shadow selections pass through untouched.
  A same-dimension teleport transfers the destination viewport to the outer camera
  and the previous outer to the reverse portal, when present. Only viewports
  allocated by CE are freed by CE, including displaced, evicted and promoted views.
- **RenderDistanceTracker:** the union of all active cameras' level-four cells
  is reference-counted, so one camera cannot unload another's LODs. Each camera's
  updates respect Voxy's cell processing budget. Changes of radius reconcile
  outstanding requests; negative fractional coordinates use floor semantics.
  Portal views are LRU-bounded to ten and expire after 30 seconds without rendering.
- **MipGen:** scratch storage and the flood-fill queue are thread-local. Both
  redirects are mandatory for the pinned ABI, not best-effort `require = 0` hooks.
- **Forgified networking:** CE also accepts the exact `4.3.1+30a980d919` module
  shipped by Roxy's required API. Existing registration concurrency patches remain
  enabled; the older owo-lib `4.2.2+a92978fd19` target is still supported.
- **Renderer selection:** only normal mode without active Iris shaders falls
  back to the framebuffer compatibility renderer. This restricts nesting to one
  layer. The user setting is not overwritten, and debug/none modes are preserved.
- Disconnect cleanup clears queued teleport references. Cross-dimension teleports
  do not exchange viewport ownership between render systems.

The hooks adapt the Apache-2.0 work in DigitalWolf1313/ImmersivePortalsMod-Updated
at `c7e751c3`; ownership and request bookkeeping live in independently testable
helpers rather than private nested mixin classes.

## Automated verification

With Java 21:

```sh
./gradlew test build -PverificationSable=false
```

JUnit covers full-path cache isolation, LRU eviction, one-way and unseen
destinations, reverse crossings, exactly-once cleanup, shared LOD ownership,
pending request cancellation, radius changes, negative coordinates, expiry,
processing budgets, a randomized independent camera-union model, optional-mod
gates, renderer fallback policy, chunk-promotion recovery against rewritten
chunk holders, and release-jar launcher metadata.
Contract tests inspect the pinned original Voxy jar using ASM; `voxyContract`
is test-only and never added to the game classpath or release jar. They check
selector/tracker descriptors, mip scratch field reads, client-only pseudo mixin
registration and thread-local isolation.
The separate `roxyNetworkingContract` compares all registration/dispatch method
bytecode against the older module exercised by CE's concurrency tests. Only the
upstream native-map constructor changes from HashMap to ConcurrentHashMap.

Build result: **220 JUnit tests passed, two platform-specific reader-lock tests
skipped, no failures**. The release jar contains no Roxy/Voxy classes or GameTest
drivers.

A live client probe with the pinned Roxy/API/Voxy artifacts, NeoForge 21.1.228
and Sodium 0.8.12 applied all three Voxy mixins, joined a dedicated server,
created overworld/Nether/End render systems and passed the first
`before-reload:solid-visible` pixel check with `RendererUsingFrameBuffer`.
GPU: NVIDIA RTX 2060, OpenGL 4.6, driver 615.78.08; Iris and Sable absent.
The client was manually closed during the next scene; no further graphical runs
were attempted. This is **partial startup/rendering evidence, not a full graphical pass**.
The probe used compatibility mode explicitly, so automatic fallback still needs
live coverage. Existing CE renderer certification is not Voxy certification.

The separate `coreCheck -PverificationSable=false` attempt reached the GameTest
server but failed on missing `sable:physicstest.gravity` fixture data. It is not
reported as a passing GameTest run; the ordinary `build`/JUnit command above passes.

## Chunk promotion with rewritten chunk schedulers

Independent of Voxy, the report from
`crash-reports/crash-2026-10-11_21.15.29-server.txt` in a C2ME
`0.4.0-alpha.0.122+1.21.1` instance showed a server-side
`NullPointerException: Cannot invoke CompletableFuture.getNow(Object) because
future is null` in `ChunkLoadingRecovery.failed`, reached through
`ChunkHolder.ip_retryFailedFutures` and `ImmPtlChunkTickets.flushThrottling`.

C2ME's `c2me-rewrites-chunk-system` replaces the vanilla `ChunkHolder` promotion
futures. Its `failFastIncompatibility` mixin constructor clears the raw
`accessibleFuture`, `tickingFuture` and `entityTickingFuture` fields and the
holder overrides `getAccessibleFuture()`/`getTickingFuture()` to return
scheduler-owned futures. CE reads those raw Mojang fields directly, so after C2ME
the fields are `null` and the old code called `getNow(null)` on them.

CE now treats an absent vanilla future as "not a failed promotion" rather than
dereferencing it: a null-safe `ChunkLoadingRecovery.peek` helper is used by the
failed checks and the entity-promotion tick, and `ip_retryFailedFutures` returns
early when all three raw futures are null so it leaves promotion to the owning
scheduler. CE deliberately keeps reading the raw fields instead of the getters,
because writing vanilla state back through the overridden getters would install
vanilla futures inside a C2ME-owned holder. The exact crash is covered by JUnit
without launching Minecraft; an in-game retest with C2ME is still recommended.

## Launcher mod metadata

On some launchers the mod appeared as the raw filename `immersive_portals-6.1.0`
instead of "Immersive Portals - CE". The in-game mod list was always correct:
`neoforge.mods.toml` has declared the `displayName` for all three entry points
all along.

The cause was packaging, not metadata. An empty `src/main/resources/fabric.mod.json`
(0 bytes, added by an old upstream sync commit and no longer referenced by the
build) was packaged *before* `META-INF/neoforge.mods.toml`. Launchers such as
PrismLauncher scan the jar's metadata entries in order and stop at the first
recognised one; parsing an empty `fabric.mod.json` yields an empty name and the
launcher falls back to the filename, so the real NeoForge metadata was never read.
The empty file has been removed, and a regression test asserts the release jar
carries no launcher metadata that could preempt `neoforge.mods.toml`
(`fabric.mod.json`, `quilt.mod.json`, `forgeversion.properties`, `litemod.json`,
`META-INF/nil/mappings.json` or `*.nilmod.css`).

Metadata was also hardened: the jar manifest now carries `Implementation-` and
`Specification-Title/Version/Vendor`, the correctly spelled top-level
`issueTrackerURL` is used in `neoforge.mods.toml`, the resource pack description
matches the mod name, and a generated `mcmod.info` provides the same descriptive
fields for legacy launchers. `ModMetadataTest` asserts all of this against the
actual release jar.

## Remaining graphical acceptance checks

Use a separate instance with the installation target above and pre-ingest terrain
so the destination contains actual LODs, not only vanilla chunks. Check:

1. Two distant same-dimension portals and a mirror; alternate camera views and
   verify LODs/occlusion history do not bleed between cameras.
2. Walk through paired and one-way portals, including an initially unseen
   destination; look back and cross repeatedly without blank or ghost LODs.
3. Vary Voxy render distance, visit negative coordinates, then hide a portal for
   over 30 seconds. Check for missing shared terrain or runaway GPU memory.
4. Resource reload, window resize, disconnect/reconnect and world changes: no
   double-delete or stale portal references.
5. Shader-off fallback, explicit debug/none modes, then Iris shader enable/disable.
   Nested views are only expected on the active-Iris normal renderer.
6. Cross-dimension rendering/travel, and then Sable/Create/Iris combinations.
   Roxy's global render-generation/world lifecycle patches are outside the scope
   of these per-renderer caches; multi-world behavior needs runtime validation.

Attach exact mod versions, GPU/driver, shaderpack and `latest.log` to reports.
