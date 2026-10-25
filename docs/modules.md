# Modules and packages

Four Gradle projects. `core` is a plain Java library. The other three are loaders that depend on it and on a Minecraft environment. Game types never leak into `core`; platforms implement the abstract types in `client.platform` and `server`.

```
settings.gradle.kts
  :core         natosatlas-core          shared map, UI, IO
  :stationapi   natosatlas-stationapi    Babric + StationAPI (the tested fork target)
  :bta          natosatlas-bta           Better Than Adventure + Halplibe
  :modloader    natosatlas-modloader     Risugami's ModLoader
```

`natosatlas-core/build.gradle.kts` still depends on Minecraft for LWJGL drawing. A comment there says rendering and input should be factored out so that dependency can go. Until that happens, headless tests of UI layout classes are possible only for the pure-math helpers (`MapTopBar`, `MarkupToolbar`, `MapProjection`, `WorldMapImage`).

## Platform adapters

Each loader does the same four jobs: install a `BlockAccess`, install a `ClientWorldAccess`, start `NACore`, and turn `UIScreen` into a real Minecraft screen.

| Concern | Core type | StationAPI | BTA | ModLoader |
| --- | --- | --- | --- | --- |
| Entry | `NACore` | `stationapi/client/STClientEntry` | `bta/client/BTAClient` | `mod_NatosMap` |
| Blocks and colours | `client/platform/BlockAccess` | `stationapi/STBlockAccess` | `bta/BTABlockAccess` | `modloader/BlockAccessML` |
| World, chunks, entities | `client/platform/ClientWorldAccess` | `stationapi/client/STClientWorldAccess` | `bta/client/BTAClientWorldAccess` | `modloader/client/MLClientWorldAccess` |
| Draw + open screens | `ClientPlatform`, `NAPainter`, `ScreenAccess` | `STClientPlatform`, `STPainter` | `BTAClientPlatform`, `BTAPainter` | `MLClientPlatform`, `MLPainter` |
| Tick, keys, death waypoint | mixins | `mixin/STMinecraftMixin` | `mixin/BTAMinecraftMixin` | `mod_NatosMap` tick + `KeyboardEvent` |
| Minimap HUD | `NAClient.renderGui` | `mixin/STHudMixin` | `mixin/BTAHudMixin` | GUI hook (no separate minimap mixin) |
| Server name | `NAClientSession.setServerName` | `mixin/STScreenConnectingMixin` | `mixin/BTAScreenConnectingMixin` | `MLClientWorldAccess` (see FIXMEs there) |
| Dedicated server | `server/ServerPlatform` | `stationapi/server/STServerPlatform`, `ServerEntry` | `bta/server/BTAServerEntry` | none |

`STClientPlatform.openNacScreen` wraps a `UIScreen` in an anonymous `net.minecraft.client.gui.screen.Screen`. That wrapper forwards init, tick, render, mouse, and keys, enables key repeat, and returns `shouldPause() == false`, so the world keeps ticking under the map. BTA and ModLoader do the same wrapping in their own screen classes.

`STWorldAccessorMixin` exposes the client world's storage so `STClientWorldAccess.getOfflineSaveName` can read the real save-folder name instead of the level display name.

True-colour sampling of `terrain.png` is implemented on `STBlockAccess.getTrueColor`. The core only asks `BlockAccess.getTrueColor` and falls back to `getColor` when the platform returns -1. BTA and ModLoader therefore stay on classic material colours unless they grow the same method.

## `dev.natowb.natosatlas.core` — shared types, no screens

| Path | Responsibility |
| --- | --- |
| `core/NACore.java` | Process-wide init. Holds the `NAClientSession`. `startServer` does not start anything. |
| `core/NAClientSession.java` | `tick()` and `setServerName()`. Implemented only by `NAClient`. |
| `core/NAPaths.java` | Resolves `.minecraft/natosatlas/...` and the per-world tile folders. Sanitizes multiplayer folder names. |
| `core/NAConstants.java` | 16 blocks/chunk, 32 chunks/region, 8 canvas pixels/block. |
| `core/LayerRegistry.java` | Static list of day and night layers. Cave is appended later by `NAClient`. |
| `core/NARegionGenerator.java` | Blocking per-region render used by the dormant `RegionFileWatcher`. The singleplayer Generate screen does not call it; that screen feeds `MapUpdater` instead. |
| `core/data/NAChunk.java` | One 16×16 column summary: height, block id, meta, fluid depth, block light, biome. |
| `core/data/NALayer.java` | Id, name, `NAChunkBuilder`, whether night shading uses block light, whether singleplayer generate should build it. |
| `core/data/NACoord.java` | Integer x/z pair plus a packed `long` key. Used for chunks and for regions. |
| `core/data/NAEntity.java` | Position, yaw, type (player / mob / animal), optional texture path and name. |
| `core/data/NABiome.java` | Grass colour, foliage colour, name. |
| `core/data/NAWorldInfo.java` | Height, name, time, seed, dimension id, ceiling flag, multiplayer flag. |
| `core/data/NARegionPixelData.java` | The 512×512 ARGB buffer and its GL texture id. `markTextureDirty` lets any thread ask the render thread to re-upload. |
| `core/data/NARegionFile.java` | A `.mcr`/`.mca` file plus a 32×32 "chunk exists" grid. Used by singleplayer generate, not by the mp13 importer. |
| `core/chunk/ChunkWrapper.java` | Platform-neutral chunk. Bounds checks, "is this chunk all air?", top-solid-block scan (skips glass), fluid depth. |
| `core/chunk/NAChunkBuilder.java` | `build(chunkCoord, wrapper) -> NAChunk`. |
| `core/chunk/NAChunkBuilderSurface.java` | Top solid block per column, snow pulled up one block, biome from `ClientWorldAccess`. |
| `core/chunk/ChunkRenderer.java` | Copies one `NAChunk` into the right 16×16 of a region buffer. |
| `core/chunk/ChunkColorEngine.java` | Classic or true colour, biome tint, water depth shade, height shade, optional block light. |
| `core/chunk/TrueColorCache.java` | Average colour of one `terrain.png` tile, keyed by block id and meta. |
| `core/mapper/ColorMapper.java` | Hard-coded classic overrides (gravel, netherrack, glowstone) and the 16 wool colours. |
| `core/storage/NARegionStorage.java` | Load/save one region PNG. Writes a `.tmp` then moves it into place. |
| `core/storage/TextStorage.java` | `key=value` files. Settings and the unused server config subclass it. |
| `core/io/MapDataFiles.java` | Rename-then-delete of a world's `regions/` tree. |
| `core/io/McRegionReader.java` | Reads Beta 1.7.3 region files without the game: header, chunk NBT, block arrays, mob-spawner tile entities. |
| `core/io/WorldMapImage.java` | Stitches stored tiles into one streamed PNG. No GL, safe on a worker thread. |
| `core/util/LogUtil.java`, `FileUtil.java`, `Profiler.java` | Logging, line IO, a small profiler. |

## `dev.natowb.natosatlas.client` — session, caches, import

| Path | Responsibility |
| --- | --- |
| `client/NAClient.java` | The session. Join/leave/dimension, per-tick updater/importer/saver, minimap, temp-waypoint removal when you walk into one. |
| `client/MapUpdater.java` | One chunk per tick inside an 8-chunk radius. Rebuilds every layer and marks the region dirty. |
| `client/NAChunkBuilderCave.java` | Cave columns: topmost air-with-no-skylight below the player, then the floor under it. Needs the live player, so it is not in `core.chunk`. |
| `client/RegionImporter.java` | Background `.mcr` import, ~6 ms per tick. Player's dimension goes through `MapUpdater.updateChunk`; other dimensions are rendered off-screen and queued on `MapSaver`. |
| `client/ImportIndex.java` | Per-file size, mtime, and deflated 8 KB header so unchanged chunks are skipped. |
| `client/SliceRenderer.java` | Layer view. On-demand Y slices, a few ms per frame, six Y levels cached. |
| `client/SpawnerStore.java` | Mob spawners collected during import. |
| `client/SlimeSeed.java` | Optional per-world seed override for the slime overlay. |
| `client/MapDataReset.java` | "Delete all map data": stop the saver, drop textures, detach `regions/`. |
| `client/FolderOpener.java` | Opens a directory with Explorer / `open` / `xdg-open`, then `java.awt.Desktop`, then LWJGL `Sys.openURL`. The Import tab no longer has a button for it; the class remains. |
| `client/cache/NARegionPixelCache.java` | LRU of live region buffers (256). Pins regions that are dirty or mid-save so they cannot be reloaded from an older PNG. |
| `client/cache/NAForeignRegionCache.java` | Read-only PNG cache for a dimension you are viewing but not standing in. Never marked dirty. |
| `client/cache/NARegionTextureCache.java` | Older texture-id cache still used by `TextureProvider` for some lookups. |
| `client/io/MapSaver.java` | Daemon worker. Batches up to 64 dirty regions per tick onto a queue. External (other-dimension) tiles can be enqueued too. |
| `client/io/WorldMapExport.java` | P / Shift+P and the settings buttons. Queues unsaved tiles, then `WorldMapImage` on a worker. |
| `client/io/MarkupExport.java` | Ctrl+S. Same stitch, then `MarkupImage` paints shapes and corner labels on top. |
| `client/io/MapExporter.java` | The pre-mp7 stitch that wrote `exported_map_layer_N.png` into the world folder and stopped the saver. Nothing in the UI calls it anymore. |
| `client/texture/TextureProvider.java` | Icon indices into `assets/natosatlas/textures/ui.png`, and `getTexture(region, layer)` for the live cache. |
| `client/texture/TextureUtils.java` | `glGenTextures` / `glTexSubImage2D` helpers. |
| `client/texture/NARegionTexture.java` | Small holder around one GL id. |

## `client.ui` — screens that do not know about Minecraft

`UIScreen` (`client/ui/elements/UIScreen.java`) is the base: a list of buttons, sliders, and text fields, plus `init`, `tick`, `render`, `keyPressed`, and raw mouse dispatch. Escape calls `openNacScreen(parent)`. `MapScreen` intercepts Escape first when Segregated markup is active.

Elements: `UIElement`, `UIElementButton`, `UIElementIconButton`, `UIElementToolButton` (icon button that can look selected), `UIElementOptionButton` (cycles a `SettingsOption`), `UIElementSlider`, `UIElementTextField` (Home/End, Ctrl+A/C/X/V), `UIScaleInfo`.

Layout: `UILayout`, `UIHorizontalLayout` (optional right-to-left), `UIVerticalLayout`, `UIPoint`.

Theme: `UITheme` colours, `UIThemeMinecraft` is the concrete palette the buttons use.

Screens live under `client/ui/screens/`:

| Package | Classes | Role |
| --- | --- | --- |
| `map` | `MapScreen`, `MapViewport`, `MapContext`, `MapProjection`, `MapConfig`, `MapTopBar`, `MapDimensions`, `MapLayerSlider`, `MapSpawners`, `MapTooltips`, `MapStage*` | Full-screen map. |
| `map.markup` | `MarkupModel`, `MarkupShape`, `MarkupRaster`, `MarkupGeometry`, `BlockCells`, `MarkupToolbar`, `MarkupRenderer`, `MarkupTool`, `MarkupImage` | In-memory drawings and the PNG overlay. |
| `settings` | `Settings`, `SettingsStorage`, `SettingsOption`, `SettingsScreen`, `MapButtons` | `settings.txt` and the options UI. |
| `waypoint` | `Waypoint`, `Waypoints`, `WaypointStorage`, `WaypointListScreen`, `WaypointCreateScreen`, `WaypointListElement` | Per-dimension `.points` files and their screens. |
| `help` | `HelpScreen` | Static control list. Its markup lines describe Segregated mode. |
| `generate` | `GenerateScreen` | Singleplayer "Generate Existing": walks the save's region files and feeds `MapUpdater`. |
| `hud` | `MinimapRenderer` | Corner map. Reuses `MapViewport` and the region/entity/waypoint stages. |

## `dev.natowb.natosatlas.server` — present, not started

`NAServer` would load `config.txt`, start `WebServer` (`com.sun.net.httpserver`) on `127.0.0.1:8080`, and run `RegionFileWatcher`. Routes: `/` (`IndexRoute`), static assets (`AssetsRoute`), `/players` (`PlayerRoute`, gated by config), `/tiles/{dim}/{layer}/{rx}/{rz}.png` (`TileRoute`, dimension 0 only). `NACore.startServer` never constructs `NAServer`, so none of this runs. `STServerPlatform` is still a full `ServerPlatform` implementation (level name, region directory, players, chunks) waiting for that call.
