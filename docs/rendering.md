# Map rendering and layers

A stored map tile is a 512×512 ARGB image, one pixel per block column, covering one Minecraft region (32×32 chunks). Three of those exist per region, one per layer. The screen never renders blocks directly except in layer view.

## From a loaded chunk to a pixel

`MapUpdater.tick` (`client/MapUpdater.java`) runs once per client tick while a world is joined:

1. Walk a fixed list of offsets inside a circle of radius 8 chunks, one offset per tick, then wrap.
2. Ask `ClientWorldAccess.getChunk` for that chunk. Null and all-air chunks are skipped and retried next cycle. Exceptions are logged, not thrown into the game.
3. `updateChunk` maps the chunk to its region (`chunk >> 5`) and, for every `NALayer`, calls the layer's builder and `ChunkRenderer.render`.
4. `NARegionPixelCache.markDirty` queues the region for `MapSaver`.

`ChunkRenderer` (`core/chunk/ChunkRenderer.java`) writes 16×16 colours into the region buffer at `((chunk & 31) * 16)`. It then calls `region.markTextureDirty()`. The GL upload happens later, on the render thread, inside `NARegionPixelData.getTextureId`, the first time that tile is drawn. A save does not have to finish before the map updates.

### Surface columns (`NAChunkBuilderSurface`)

For each of the 256 columns, `ChunkWrapper.getTopSolidBlockY` walks down from the top of the world (capped at 128) and stops on a block that blocks movement or is a fluid. Glass is skipped so the block under it is mapped. Snow sitting on the top solid block replaces it. A column with no solid block is left unset (`NAChunk` defaults: height 0, block id 0). The builder never reads `y = -1`. `set` clears `isEmpty` only when the block id is non-air, and `MapUpdater` then skips `ChunkRenderer` for a chunk that is still empty. A mixed chunk is rendered in full, so unset columns in an otherwise solid chunk do go through `ChunkColorEngine` at height 0.

Biome comes from `ClientWorldAccess.getBiome`. That is a client call inside a "core" builder; the class has a FIXME about moving it.

### Cave columns (`NAChunkBuilderCave`)

Uses the live player's Y. From that height downward, the first air block with sky light 0 is treated as a cave, and the solid block under it is the mapped floor. If none exists the column is stored with height -1, and `ChunkColorEngine` paints it opaque black. Cave building needs the player, so this builder lives in `client`, not `core.chunk`. The Nether has a ceiling, so `MapScreen` forces layer 2 there; the cave builder still keys off the player's Y.

### Colour (`ChunkColorEngine`)

Per column:

1. Height &lt; 0 → `0xFF000000`. Only the cave builder writes a negative height, and it does that for columns with no cave floor. Surface columns that were skipped stay at height 0. `MapUpdater` skips `ChunkRenderer` entirely when `NAChunk.isEmpty` is still true (no column had a non-air block id), so a fully empty surface chunk leaves the tile's existing pixels alone, including the transparent zeros of a brand-new buffer.
2. **True colour** (`Settings.blockColors == TrueColor`): `BlockAccess.getTrueColor(id, meta)`, which on StationAPI averages the top-face tile of the selected texture pack (`STBlockAccess`, cached in `TrueColorCache`). Fluids and grass skip this and use classic colours (mp4 keeps grass classic, then biome-tinted). Leaves multiply by the biome foliage colour, except spruce (`meta & 3 == 1`, `0x619961`) and birch (`== 2`, `0x80A755`).
3. **Classic**: `BlockAccess.getColor`, which is the block's `material.mapColor` plus `ColorMapper` overrides for gravel, netherrack, glowstone, and wool. Grass is mixed 10% toward the biome grass colour.
4. Water is shaded by `NAChunk.waterDepths` (three brightness steps).
5. Height shading compares this column with the column to the north (`localZ - 1`) and picks one of `BRIGHTNESS = {180, 220, 255, 135}`.
6. Night and cave layers (`NALayer.usesBlockLight`) multiply by block light, with a floor of 0.20 so unlit caves stay barely visible.

Classic colours are looked up through the platform `BlockAccess` so BTA block ids can differ. True colour is optional on that same type; platforms that return -1 stay classic.

## Cache and save

`NARegionPixelCache` is an access-ordered map of `NARegionPixelData[layerCount]`, cap 256 regions. `getRegion` loads `region_X_Z.png` from disk on a miss.

Eviction will not drop a region that is in the dirty set or pinned by `MapSaver`. That pin is the mp8 fix: evicting a dirty region, reloading the old PNG, and saving it again used to wipe an import. `trim()` walks eldest-first and skips pinned entries.

`MapSaver` is a single daemon thread plus a `ConcurrentHashMap` of pending tasks:

- `saveNextBatch` (client tick) moves up to 64 dirty regions onto the queue and pins them.
- The worker removes the task from `pending` *before* it reads pixels. A change that arrives during the write queues another save instead of being merged into the snapshot already in flight.
- `flushAll(dim, multiplayer)` is called before a dimension change and on disconnect, so the old dimension id is the one in the path.
- `enqueueExternal` saves a buffer that is not in the live cache (the importer's other-dimension tiles).
- `NARegionStorage.saveRegionBlocking` writes `file.tmp` and moves it over the PNG (atomic move, then plain move, then a direct write).

Singleplayer tile paths use the world save (`saves/<name>/natosatlas/regions/...`, `useSaveFolder = true`). Multiplayer tile paths use `.minecraft/natosatlas/servers/<sanitized-host-port>/regions/...`. The exact layout is in [data-formats.md](data-formats.md).

## Drawing stored tiles

`MapStageRegions.draw` asks `TextureProvider.getTexture` (live cache) or `ctx.tiles.getTexture` (foreign cache) and draws the GL texture at the region's canvas origin, 4096 canvas pixels on a side. Missing tiles are skipped, so unexplored space is the flat panel background.

`MapViewport.begin` sets the GL matrix from `MapContext` (scroll, zoom, rotation). Stages draw in that space and do not know about the screen size. `computeVisibleRegions` expands the screen corners through the inverse transform and collects every region the rotated rectangle touches.

`NAForeignRegionCache` is a smaller LRU (48 regions) of PNGs for one dimension. It never calls `markDirty`. `MapScreen.onClose` and `setViewDim` call `clear()`, which deletes the GL textures.

## Overlay stages

All implement `MapStage.draw(ctx, visibleRegions, activeLayer)` except spawners, which take the spawner list.

| Stage | File | What it draws |
| --- | --- | --- |
| Regions | `MapStageRegions` | Terrain textures. |
| Slime | `MapStageSlime` | Translucent green 16×16 squares. `isSlimeChunk` matches Beta 1.7.3 `Chunk.getSlimeRandom`, including the `int` overflow on the X terms. Seed is `SlimeSeed.getEffectiveSeed()` (custom override, else the world seed). Only drawn where a terrain tile exists. |
| Grid | `MapStageGrid` | Chunk/region lines. A FIXME notes rotation is only partly handled. |
| Waypoints | `MapStageWaypoints` | Icons from the live list, or from `source` when viewing another dimension. |
| Entities | `MapStageEntities` | Players and mobs/animals inside 128 blocks. Mobs below the player fade over 32 blocks of depth. The local player is drawn last. Crouching players are flagged. `drawGhostPlayer` is the foreign-dimension marker. |
| Spawners | `MapStageSpawners` | Squares from `SpawnerStore`, filtered by `Settings.spawnerFilter`. |

`MapScreen` skips `MapStageEntities.draw` in a foreign dimension and draws the ghost instead. Entity positions always come from the loaded world, so other players in the dimension you are only viewing do not appear.

## Layer view (Y slice)

Separate from layers 0–2. `SliceRenderer` (`client/SliceRenderer.java`):

- Keyed by `dim:y`. Keeps 6 Y levels, 64 region tiles each.
- Each frame, `update(dim, y, live, visibleRegions, centreX, centreZ)` spends about 7 ms reading the visible regions nearest the view centre.
- A chunk the client has loaded (and only when `live` is true, i.e. you are in that dimension) is read through `ClientWorldAccess.getChunk`. Otherwise the chunk is read from the `.mcr` directory resolved for that dimension (Import-tab paths, or the old `import/` folder).
- `sliceColor`: solid blocks use the map colour, brighter when the block above is air, slightly darker when buried. Air is a dark tint of the floor one block below, warmed by block light. Unknown block ids become grey.
- Textures are temporary GL ids held by the slice cache, not `NARegionPixelData`, and they are not saved.

Changing the import paths or toggling layer view calls `SliceRenderer.clear()`.

## Minimap

`MinimapRenderer` reuses `MapStageRegions`, `MapStageWaypoints`, and `MapStageEntities` with the minimap's own mode, zoom, scale, and rotation. It does not use layer view or the foreign cache. Layer choice mirrors the full map (ceiling → cave, otherwise Day/Night/Cave/Auto with the same sky-light test).

## Singleplayer generate

`GenerateScreen` does not have its own renderer. It calls `MapUpdater.updateChunk` for chunks read with `getChunkFromDisk`, which goes through the same builders, `ChunkRenderer`, cache, and saver as walking around. `NARegionGenerator` is the blocking bulk path (one region, all layers, save immediately). The only caller is `RegionFileWatcher` on the unstarted server.
