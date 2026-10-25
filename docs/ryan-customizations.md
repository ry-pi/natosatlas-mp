# How Ryan's customizations fit

Upstream Natos Atlas 1.5.4 (author natowb, plus earlier BTA work from ProdOrDev) is the modular client: `NACore` / `NAClient`, platform interfaces, `MapUpdater`, the pixel cache, `MapScreen` and its stages, waypoints, the minimap, and settings. Ryan Goose's commits on this fork (`git log --author=ryan-goose`, versions **1.5.4-mp1** through **1.5.4-mp18**) sit on that structure. They rarely add a new module. They harden the scan loop, then add features as extra stages, extra files, and extra branches inside `MapScreen`.

The user-facing history is the bottom of `README.md`. This page is the code map: which type owns each change, and what you have to keep working if you touch it. mp1 through mp6 landed in one commit (`9346a03`, message "mp6"); the README still describes them separately, and the code comments say `MP patch` or `mp3`/`mp6`.

Only `natosatlas-stationapi` was updated to match. Shared behaviour lives in `natosatlas-core`, so BTA and ModLoader pick it up when it goes through `ChunkWrapper` or `NAClient`, and miss it when it is an `instanceof EmptyChunk` check inside `STClientWorldAccess`.

## mp1–mp2 — do not crash, do not lose a dimension

The scan loop assumed every chunk was a real client chunk and that every column had a surface. On a server neither is true for the first seconds, and `getTopSolidBlockY` used to walk to `y = -1`.

| Change | Where |
| --- | --- |
| Block reads return 0 out of range | `ChunkWrapper.inBounds`, used by `STClientWorldAccess` and by `McRegionReader.ArrayChunk` |
| Skip unloaded and `EmptyChunk` placeholders | `STClientWorldAccess.getChunk` |
| Skip all-air chunks, catch per-chunk errors | `MapUpdater.tick` |
| Skip empty columns instead of sampling y = -1 | `NAChunkBuilderSurface` |
| Safe folder names | `NAPaths.sanitizeFolderName`, called from `setWorldPaths` |
| Null biomes / missing player | `STClientWorldAccess.getBiome` returns a fallback; `NAClient.onWorldTick` catches `Throwable` |
| Flush the **old** dimension before switching, and make the save queue's pending map thread-safe | `NAClient.tick` → `MapSaver.flushAll(oldDim)`; `MapSaver.pending` is a `ConcurrentHashMap` |

`NAClient.setServerName` was already the hook. The fork's `STScreenConnectingMixin` fills it with `host-port` so each server gets its own directory.

## mp3–mp4 — true colour, classic grass

`Settings.BlockColors` and `ChunkColorEngine` gained a branch that asks `BlockAccess.getTrueColor`. The default method returns -1, so platforms that do not override it stay on material colours. `STBlockAccess` loads `/terrain.png` from the selected texture pack into `TrueColorCache` and averages the top-face tile (`Block.getTexture(1, meta)`). Fluids always use classic water shading. Grass always uses the classic colour plus the existing biome mix (mp4). Leaves in true-colour mode multiply by biome foliage, with spruce and birch fixed tints.

Changing the colour rules means touching `ChunkColorEngine` and, for the atlas, `STBlockAccess`. `SliceRenderer.blockColor` has a smaller copy of the same true-colour-or-classic choice for layer view; it does not apply height shading or the grass exception.

## mp5, mp8, mp13 — region import

This is the largest fork subsystem. It is not a network client.

| Piece | Class | Added |
| --- | --- | --- |
| `.mcr` parser and bounds-checked chunk | `core/io/McRegionReader` | mp5 |
| Tick-sliced importer | `client/RegionImporter` | mp5, rewritten in spirit by mp8 and mp13 |
| Legacy `import/` and `import/DIM-1/` folders | `RegionImporter.getImportDir` / `ensureFolders` | mp5 |
| Do not skip chunks the client has loaded | `RegionImporter.tick` (the comment points at mp8) | mp8 |
| Re-upload textures without waiting for the saver; never evict dirty regions | `NARegionPixelData.markTextureDirty`, `NARegionPixelCache` pins, `MapSaver` pending lock | mp8 |
| Import from arbitrary Overworld and Nether folders | `Settings.importOverworldPath` / `importNetherPath`, `startFromPaths` | mp13 |
| Skip unchanged files and chunks | `client/ImportIndex` | mp13 |
| Other dimension rendered off-screen | `RegionImporter.renderForeign` + `MapSaver.enqueueExternal` | mp13 |

`SettingsScreen`'s Import tab is the UI. The old "Import .mcr" and "Open folder" buttons were removed in mp15; `FolderOpener` is still in the tree.

If you change import, keep three invariants: the player's dimension goes through `MapUpdater.updateChunk` (so live tiles and all three layers update together), any other dimension must not be put in the live cache (that cache is keyed only by region, and its save path uses the *current* dimension), and a chunk the index skipped must not have its spawners deleted.

## mp6 — slime seed and the vanilla formula

`MapStageSlime.isSlimeChunk` used to promote every multiply to `long`, which diverges from Beta once chunk X is past about ±21. The current expression casts the X terms the way `Chunk.getSlimeRandom` overflows an `int`, then widens. `SlimeSeed` stores an optional override in `slime_seed.txt` and is loaded from `NAClient.onWorldJoined`. The seed number is deliberately absent from tooltips (`SlimeSeed.source()`, `MapTooltips.SLIME`).

## mp7 — whole-map PNG and delete

`WorldMapImage` is a GL-free stitcher so it can run off-thread and in principle headless. `WorldMapExport` is the UI-facing queue (chat messages drained on the client tick). `MapDataReset` plus `MapDataFiles` is the delete path: stop the saver without flushing, drop GPU textures, rename `regions/` out of the way, delete in the background.

`MapExporter` is the previous implementation (one big `BufferedImage`, wrote `exported_map_layer_N.png`, stopped the saver). The P key does not call it. `NARegionStorage`'s temp-file write exists so the stitcher never reads a torn PNG.

## mp9–mp12 and mp16–mp18 — the map screen as a viewer and a drawing tool

`MapScreen` grew from "the map" into the place that coordinates viewing and drawing. The new types are small; the screen wires them.

**Dimension viewing (mp9, button moved in mp10).** `MapDimensions` (names, ×8, toggle order), `NAForeignRegionCache` (read-only tiles), `MapContext.tiles`, `Waypoints.readDimension`, and the ghost player in `MapStageEntities`. `MapScreen.syncDimension` handles a portal that fires while the GUI is open. Waypoint reload on dimension change in `NAClient.onDimensionChange` is part of the same fix: the live list must not be saved into the new dimension's file.

**Markup (mp9 geometry, mp11 block cells, mp12 two erasers, mp16–mp18 chrome).** The package `client/ui/screens/map/markup/` is entirely fork code. `MapScreen` owns the mode flag and the pointer state machine; the package owns layout, rasterisation, undo, and the export overlay. Segregated versus Integrated is `Settings.markupIntegrated` plus two `MarkupToolbar` factories. mp18 is specifically: Integrated sets `markupMode` inside `buildButtons`, selects `MarkupTool.PAN`, treats a second M as close, and does not consume Escape. The Preferences toggle for the pencil button is disabled in that mode so it cannot imply a mode that does not exist.

**Fixed top-right row (mp11) and Preferences (mp16).** `MapTopBar` was built so the Nether's disabled Time button occupies a slot. mp16 then changed `layout` to take a set of visible slots and pack them, which is why hidden buttons leave no gap while the *disabled* Time button still takes one. `MapButtons` must use the same names as `MapTopBar.Slot`. `MapTooltips` is the shared "Name (extra) [key]" formatter.

**Layer view and spawners (mp14)** are the other two fork stages. `SliceRenderer` is intentionally not a `NALayer`: slices are not saved and not produced by `MapUpdater`. `SpawnerStore` is filled only by the importer, so a world that has never been imported shows no spawners even if the player is standing on one. `MapSpawners` is the filter cycle and the hover test.

## mp15 — settings IA

No new map behaviour. `SettingsScreen.Tab` became `ATLAS`, `MINIMAP`, `IMPORT`, `PREFERENCES` (added in mp16), `DEBUG`. General's buttons were split: image export on World map, import on Import, delete and debug on Debug. Generate Existing stayed the singleplayer import.

## Fork files that did not exist upstream

These paths are new in the fork (they show up in `git log --author=ryan-goose` as additions). Touching them does not require an upstream merge to understand them; they have no 1.5.4 counterpart.

```
client/FolderOpener.java
client/ImportIndex.java
client/MapDataReset.java
client/RegionImporter.java
client/SliceRenderer.java
client/SlimeSeed.java
client/SpawnerStore.java
client/cache/NAForeignRegionCache.java
client/io/MarkupExport.java
client/io/WorldMapExport.java
client/ui/screens/map/MapDimensions.java
client/ui/screens/map/MapLayerSlider.java
client/ui/screens/map/MapProjection.java
client/ui/screens/map/MapSpawners.java
client/ui/screens/map/MapTileSource.java
client/ui/screens/map/MapTooltips.java
client/ui/screens/map/MapTopBar.java
client/ui/screens/map/MapStageSpawners.java
client/ui/screens/map/markup/*          (the whole package)
client/ui/screens/settings/MapButtons.java
core/chunk/TrueColorCache.java
core/io/MapDataFiles.java
core/io/McRegionReader.java
core/io/WorldMapImage.java
```

Heavily edited upstream files, if you are diffing against tag `1.5.4`: `MapScreen`, `NAClient`, `MapUpdater`, `MapSaver`, `NARegionPixelCache`, `NARegionPixelData`, `NARegionStorage`, `ChunkColorEngine`, `ChunkRenderer`, `ChunkWrapper`, `NAChunkBuilderSurface`, `NAPaths`, `Settings`, `SettingsScreen`, `SettingsStorage`, `STClientWorldAccess`, `STBlockAccess`, `STMinecraftMixin`.

## Comments you can trust

Fork code is marked in comments with the version that introduced the behaviour (`mp8`, `mp14`, `MP patch`). Those comments are the rationale, not a changelog duplicate: they usually say which bug the branch is preventing (stale PNG reload, wrong dimension file, loaded-chunk hole). When a comment and the README disagree, the comment next to the code is the one that has been updated with the control flow. The known drift the other way is `HelpScreen`, which still documents Segregated markup keys after mp18.

## What not to "fix" while you are in here

A few things look unfinished and are load-bearing:

- `NACore.startServer` returning immediately. The web map is not a hidden feature; it is unhooked on purpose.
- `ChunkColorEngine` reading `Settings` directly. Layers do not store their own palette; both the live scan and layer view consult the same static.
- Integrated markup discarding shapes by destroying the screen instead of calling `exitMarkup`. A new `MapScreen` starts from an empty `MarkupModel`. Persisting markup would be a new file format, not a flag on the existing model.
- `MapButtons` and `MapTopBar.Slot` sharing names via `valueOf`. Renaming one without the other throws when the map opens.
