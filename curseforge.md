# Immersive Portals - CE

> **Notice:** **Immersive Portals - CE** is an independently community-maintained distribution of Immersive Portals for NeoForge Minecraft 1.21.1. It is not an official release by the original creators.

See through portals and travel between dimensions seamlessly—without a loading screen.

## Features

- Portal-in-portal rendering
- Seamless cross-dimensional travel
- Mirrors and non-Euclidean spaces
- Custom portals through commands and datapacks
- Dimension stacks and world wrapping
- Player scale changes through configured scaling portals
- Modding APIs for portals, dimensions, remote chunks, and world rendering

## Requirements

- Minecraft 1.21.1
- NeoForge for Minecraft 1.21.1; tested baseline 21.1.228
- Java 21
- Cloth Config API 15.0+ for NeoForge (tested: 15.0.140+neoforge)

## Mod compatibility

These are **Minecraft 1.21.1 / NeoForge** targets for this CE build, not a promise of compatibility with every version or modpack. Optional mods below are not required for ordinary portals. Use the listed versions when reproducing a problem.

### Renderers and shader loaders

Choose **one** terrain renderer. Shader loaders are optional client additions.

| Install | What works with portals | Verified configuration |
| --- | --- | --- |
| Vanilla renderer | See-through portals, nested views, mirrors and dimension travel without an extra renderer mod. | With and without Sable. |
| **Sodium 0.8.12+mc1.21.1** | Optimized terrain rendering keeps destination chunks, transparency and nested portal views visible. | With Sable; shaders off or with the Iris build below. |
| **Official Iris 1.8.14-beta.1+1.21.1-neoforge** + Sodium above | Shaderpack rendering through portals, including clipping geometry at the portal surface, nested views, mirrors and dimension travel. | With Sable, both shaders off and active-pack tests. |
| **Embeddium 1.0.15+mc1.21.1** | Alternative optimized terrain renderer with destination-chunk, transparency and nested-view integration. | Without Sable; shaders off or with NeOculus below. |
| **NeOculus 1.8.7** + Embeddium above | Shaderpack portal rendering on the Embeddium stack, with the same targeted clipping and world-view integration. | Without Sable, both shaders off and active-pack tests. |

Do not combine Sodium with Embeddium, or official Iris with NeOculus. Iris requires Sodium; NeOculus requires Embeddium. **Classic Oculus is not the supported NeOculus build.** Other Embeddium/NeOculus versions are rejected by the compatibility checks. Other Sodium/Iris versions and unlisted combinations are unverified. The tested Sable presence/absence above is coverage, not a requirement to add or remove Sable.

### Shaderpacks

Both of these unmodified packs passed graphical portal tests on **Iris + Sodium** and **NeOculus + Embeddium**:

| Pack | Tested preset | Coverage |
| --- | --- | --- |
| **Complementary Reimagined r5.5.1** | `POTATO` | Portal clipping, nesting, mirrors, dimension crossing and Create rendering. |
| **MakeUp Ultra Fast 9.4a** | `shadowless_high` | The same portal scenes with this exact shadowless preset. |

Tests repeat after resource reload, resize and shader disable/enable. These results do **not** certify other presets, Complementary Unbound, arbitrary shaderpacks or pack-specific shadows. Separate test packs cover clipping and shadow regressions. Install your shaderpack separately; none is bundled. Normal portal rendering supports nesting; the compatibility/debug portal renderers are limited to one layer.

### Gameplay and rendering integrations

| Mod / tested version | What the integration does | Scope and limits |
| --- | --- | --- |
| **Sable 2.0.5** | Moving sublevels cross portals and dimension stacks while keeping their identity, velocity, riders and remote tracking. Rotated crossings preserve the tested sublevel camera/gravity behavior. | Dedicated-server + real-client tests cover UDP and TCP, a Create seat, gravity-driven recrossing and dismount. Verification uses Sable Companion 1.6.0 alongside Sable. This does not certify every Sable add-on or vehicle. |
| **Create 6.0.10-280 / Flywheel 1.0.6** | Create blocks and contraptions remain visible and clipped through portals; rotating machinery and block-breaking overlays are tested. | Portal, alternate-world and clipped views use Create's vanilla rendering fallback. A main view with a portal-colliding entity also falls back for that view; otherwise the selected Flywheel backend is preserved. Off, instancing and indirect have graphical coverage, not a universal hardware/FPS guarantee. |
| **Veil 4.3.2** | Keeps portal clipping working when Veil recompiles Minecraft shaders, so geometry does not leak across the portal plane. | Tested as standalone Veil with Sodium and without Sable; Sable's bundled Veil is also exercised in Sable lanes. This does not certify every mod using Veil. |
| **owo-lib 0.12.15-beta.12+1.21 / Forgified Fabric networking 4.2.2+a92978fd19** | Protects parallel packet registration from races that can break startup or login in mixed-mod installations. | Targeted concurrency tests and the runtime stack cover the pinned networking module bundled by owo-lib. Other versions of that module are rejected; this is not general Fabric-mod support. |

### Limited or unverified support

- **Pehkui 3.8.3+1.21-neoforge** is present in the test stack, but has no dedicated Pehkui interaction acceptance test. This port uses Minecraft's scale attribute for living-entity portal scaling; Pehkui is not required for that feature.
- **Mekanism 10.7.14.79** has targeted Sable QIO-dashboard inventory regression coverage. This is not a blanket claim for every Mekanism machine or network through portals.
- **Aether, Architectury and GeckoLib** are present as development/test dependencies. Loading alongside them does not establish complete gameplay compatibility.
- **Gravity Changer / Gravity API** integration is unfinished in this NeoForge port. Do not expect general player-gravity switching from it; Sable's tested sublevel gravity behavior above is a separate integration.
- **Requiem and Cardinal Components** have optional source hooks, but no verified Minecraft 1.21.1 NeoForge runtime combination here. **Aeronautics, Simulated and Offroad** are local-development inputs without reproducible hosted-CI coverage.
- Mods or versions not listed here are **unverified**, rather than automatically compatible or incompatible. This jar is not a Fabric or legacy Forge build.

For exact artifacts and technical limits, see the [renderer contract](https://github.com/UpperMoon0/ImmersivePortals/blob/main/docs/renderer-backends.md), [Create/Flywheel audit](https://github.com/UpperMoon0/ImmersivePortals/blob/main/docs/compat/flywheel-1.0.6.md) and [networking audit](https://github.com/UpperMoon0/ImmersivePortals/blob/main/docs/compat/fabric-network-registration.md). The [verified integration CI run](https://github.com/UpperMoon0/ImmersivePortals/actions/runs/38136969940) records the tested combinations; it does not cover every possible cross-combination.

## Attribution & Credits

Immersive Portals was created by **qouteall** and is licensed under Apache-2.0. This Community Edition distribution is maintained independently by **NsTut** and contributors based on the official NeoForge port. It preserves the upstream license and contributor attribution and is not affiliated with or endorsed by the original maintainers.

Source and issues for this build: https://github.com/UpperMoon0/ImmersivePortals

Upstream documentation: https://qouteall.fun/immptl/
