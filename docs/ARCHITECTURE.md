# Natos Atlas architecture (1.5.4-mp18)

Natos Atlas is a world-map mod for Minecraft Beta 1.7.3. Upstream (`natowb/bm-natosatlas`, tag `1.5.4`) is the original client map: live chunk scanning, day/night/cave layers, waypoints, a minimap, and a small settings UI. This repository (`natosatlas-mp`) is Ryan Goose's unofficial multiplayer fork on top of that tag. The fork version in `gradle.properties` is `1.5.4-mp18`.

The fork keeps the same Gradle modules and the same core types. Almost every mp1–mp18 change lives in `natosatlas-core`, with a thin StationAPI adapter change where the game has to be touched (chunk reads, the connect screen, the map key). Only the StationAPI build was patched and tested. BTA and ModLoader still compile against the same core, but they were not exercised for the fork.

This page is the map of the system. Topic files go deeper:

| Topic | File |
| --- | --- |
| Gradle modules and packages | [modules.md](modules.md) |
| Screens and HUD | [ui-flow.md](ui-flow.md) |
| How a chunk becomes pixels | [rendering.md](rendering.md) |
| Markup tools (Segregated vs Integrated) | [markup.md](markup.md) |
| Multiplayer, import, and the unused server | [multiplayer.md](multiplayer.md) |
| Keys | [keybinds.md](keybinds.md) |
| Settings and Preferences | [settings.md](settings.md) |
| Files on disk, PNG tiles, import/export | [data-formats.md](data-formats.md) |
| Where each mp patch sits in the code | [ryan-customizations.md](ryan-customizations.md) |

## What the mod actually does at runtime

There is no custom network protocol. On a server the client never asks the mod on the other side for map data. It paints whatever chunks the vanilla client has already received, and it can optionally read the server's `.mcr` region files from a folder the player points at.

```
StationAPI / BTA / ModLoader
        |  mixins + keybinds call NACore.tick() and open screens
        v
NACore  ---- starts one NAClient (ClientPlatform)
        |
        +-- each tick, while a world exists:
        |     MapUpdater          scan an 8-chunk radius, one chunk per tick
        |     RegionImporter      optional .mcr import, a few ms per tick
        |     MapSaver            background PNG writer for dirty regions
        |     MinimapRenderer     if enabled
        |
        +-- MapScreen (full map) draws cached region textures + overlays
```

`NACore.startServer` is a stub. It logs that the server side is disabled and returns. The classes under `dev.natowb.natosatlas.server` (web map, region-file watcher) are leftover upstream code and are not started. See [multiplayer.md](multiplayer.md).

## Lifecycle

1. **Mod init.** The platform entrypoint sets `BlockAccess` and `ClientWorldAccess`, then calls `NACore.init(gameDir)` and `NACore.startClient(platform)`.
   - StationAPI: `natosatlas-stationapi/.../client/STClientEntry.java` on `InitFinishedEvent`.
   - BTA: `natosatlas-bta/.../client/BTAClient.java` from `BTANatosAtlas.afterGameStart`.
   - ModLoader: `natosatlas-modloader/.../mod_NatosMap.java` in `ModsLoaded`.
2. **`NAClient` constructor** (`natosatlas-core/.../client/NAClient.java`) registers the cave layer (id 2) on `LayerRegistry` and loads `settings.txt`.
3. **Every client tick** the platform mixin calls `NACore.tick()` → `NAClient.tick()`.
4. **World join.** The first tick where `ClientWorldAccess.getWorldInfo()` is non-null (and, in multiplayer, `setServerName` has already run) calls `onWorldJoined`: `NAPaths.setWorldPaths`, `RegionImporter.ensureFolders` (multiplayer only), `SlimeSeed.load`, `Waypoints.load`, `MapSaver.start`.
5. **Dimension change.** Dirty regions are flushed under the *old* dimension id, the in-memory pixel cache is cleared, and waypoints are reloaded for the new dimension. The map screen, if open, snaps the view back to the player (`MapScreen.syncDimension`).
6. **World leave.** Import is cancelled, the saver is flushed and stopped, and the pixel cache is cleared.

The server name is captured before the world exists. `STScreenConnectingMixin` (and the BTA equivalent) reads the connect screen's host and port and calls `NAClientSession.setServerName(host-port)`. `NAClient` waits for that string before it treats a remote world as joined, so the folder name is known.

## Two coordinate spaces

Keep these apart or the 8× scale will look like a bug.

| Space | Size | Where |
| --- | --- | --- |
| World block | 1 block | Markup, waypoints, the coordinate readout, export comments |
| Chunk | 16×16 blocks | `MapUpdater` scan, slime overlay |
| Minecraft region | 32×32 chunks = 512×512 blocks | `.mcr` files, one map tile |
| Stored tile | 512×512 ARGB pixels, **1 pixel per block** | `region_X_Z.png` |
| Canvas | **8 canvas pixels per block** (`NAConstants.PIXELS_PER_CANVAS_UNIT`) | `MapViewport`, entity icons, slime squares |

`ChunkRenderer` writes one pixel per column into a 512×512 `int[]`. `MapStageRegions` draws that texture at `(regionX * 512 * 8, regionZ * 512 * 8)` with a size of `PIXELS_PER_CANVAS_REGION` (4096). The GPU stretches the 512 texture across 4096 canvas pixels, which is why overlays that are drawn in canvas space at `block * 8` line up with the terrain.

`NAConstants` is `natosatlas-core/.../core/NAConstants.java`.

## Layers are not the same thing as "map mode"

Three stored layers, created in `LayerRegistry` plus `NAClient`:

| Id | Name | Builder | Block light | Pre-generated from a singleplayer save |
| --- | --- | --- | --- | --- |
| 0 | Day | `NAChunkBuilderSurface` | no | yes |
| 1 | Night | `NAChunkBuilderSurface` | yes | yes |
| 2 | Cave | `NAChunkBuilderCave` | yes | no |

`Settings.mapRenderMode` (Day / Night / Cave / Auto) picks which stored layer `MapScreen` draws. Auto uses the world time and the sky light at the player's feet: sky light 0 selects the cave layer, otherwise day before time 12000 and night after. A world with a ceiling (the Nether) forces layer 2 and disables the mode button.

**Layer view** (mp14, key L) is a different pipeline. It does not read those PNGs. `SliceRenderer` builds a temporary texture of one Y level from loaded chunks and from `.mcr` files. See [rendering.md](rendering.md).

## Where to start reading

If you are changing the full-screen map, start at `MapScreen` and follow the fields at the top: `MapViewport`, the `MapStage*` painters, `MarkupModel`. `MapScreen` is large because it owns input, the button rows, dimension viewing, layer view, and markup. The actual pixel work is not in that file.

If you are changing colours or what a column shows, start at `MapUpdater` → `NALayer.builder` → `ChunkRenderer` → `ChunkColorEngine`.

If you are changing multiplayer import, start at `RegionImporter`, then `McRegionReader` and `ImportIndex`.

If you are adding a platform, implement `ClientPlatform`, `ClientWorldAccess`, `BlockAccess`, and `NAPainter`, and call `NACore` the way `STClientEntry` does. The core must not reference Minecraft classes. That split is the reason `natosatlas-core` exists.

## Build

JDK 21. From the repo root:

```bash
./gradlew :natosatlas-stationapi:build
```

The jar is `natosatlas-stationapi/build/libs/`. The project compiles against StationAPI `2.0.0-alpha.5.4` (`gradle.properties`) and is what the fork runs on alpha.6.4. Root `settings.gradle.kts` includes four projects: `core`, `stationapi`, `bta`, `modloader`, each living in a `natosatlas-*` directory.
