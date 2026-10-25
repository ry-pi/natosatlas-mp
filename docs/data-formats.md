# Data formats

Everything the mod writes is either a line-oriented text file or a PNG. There is no NBT of its own and no database. Region *input* is vanilla Beta 1.7.3 `.mcr`, read by `McRegionReader` and never modified.

## Directory layout

`NAPaths` (`core/NAPaths.java`) is the only place that should invent these paths.

```
<game dir>/
  natosatlas/
    settings.txt                          global
    servers/<sanitized host-port>/        multiplayer worldDataPath
      <name>.DIM0.points
      <name>.DIM-1.points
      slime_seed.txt
      import/                             legacy drop folder (Overworld .mcr)
      import/DIM-1/                       legacy Nether drop folder
      regions/
        import-index.txt
        spawners.txt
        DIM0/layer_0/region_<rx>_<rz>.png
        DIM0/layer_1/...
        DIM0/layer_2/...
        DIM-1/layer_0/...
    worlds/<save name>/                   singleplayer worldDataPath
      <save>.DIM<n>.points
      slime_seed.txt
      regions/                            index + spawners only (see below)
        import-index.txt
        spawners.txt
  saves/<save name>/                      Minecraft's own save
    natosatlas/regions/DIM<n>/layer_<id>/region_<rx>_<rz>.png
  screenshots/
    worldmap-<timestamp>.png
    worldmap-<timestamp>-small.png
    worldmap-markup-<timestamp>.png
```

`<sanitized host-port>` is `NAPaths.sanitizeFolderName`. The connect screen stores `host-port` before sanitizing (`100.73.7.65-25567` stays readable; `::1` does not).

**Singleplayer tiles do not sit next to waypoints.** `NARegionPixelCache` calls `getWorldMapStoragePath(layer, dim, useSaveFolder=true)` when the world is not remote, which resolves under `worldSavePath` (`saves/<name>/natosatlas`). Waypoints, the slime seed, `ImportIndex`, and `SpawnerStore` use `worldDataPath` (`natosatlas/worlds/<name>`). `getWorldMapRootPath(false)` is that worlds folder plus `regions/`. In multiplayer `worldDataPath` and `worldSavePath` are the same directory, so tiles, index, and spawners share one `regions/` tree.

`RegionImporter.tileFile` (other-dimension import) always passes `useSaveFolder=false`. In singleplayer, tiles for the dimension you are standing in go through the live cache into the save folder, and tiles for the other dimension are written under `natosatlas/worlds/<name>/regions/`.

## `settings.txt`

`TextStorage`: one `key=value` per line, `=` split once. Implemented by `SettingsStorage`. Keys:

```
blockColors=Classic|TrueColor
mapGrid=true|false
debugInfo=true|false
defaultZoom=<float>
entityDisplayMode=All|Player|Nothing
mapRenderMode=Day|Night|Cave|Auto
showSlimeChunks=true|false
minimapEnabled, minimapRotateWithPlayer, minimapZoom, minimapScale
minimapEntityDisplayMode, minimapRenderMode
minimapPosition=TopLeft|TopRight|BottomLeft|BottomRight
importOverworldPath=<string>
importNetherPath=<string>
spawnerFilter=Off|All|Spider|Zombie|Skeleton|Pig
layerY=<int 0..127>
hiddenButtonKeys=true|false
markupIntegrated=true|false
showButton.close, showButton.options, showButton.help, showButton.markup
showButton.waypoints, showButton.slime, showButton.spawners, showButton.grid
showButton.entities, showButton.mapMode, showButton.layer, showButton.dimension
```

Enums are stored as the Java enum name (`TrueColor`, not "True colour"). A bad enum value falls back to the default inside `getEnum`.

## Waypoints — `<world>.DIM<id>.points`

`WaypointStorage`, one waypoint per line, colon-separated:

```
name:x:y:z:visible:RRGGBB:temp
```

`visible` and `temp` are `true`/`false`. Colour is 6 hex digits, no `#`. Lines with fewer than 6 fields are skipped; a missing 7th field means not temporary. Names cannot contain `:`, because the split is not quoted. `Waypoints.add` refuses a waypoint that matches an existing one on name, x, y, z, and colour.

The live file follows `ClientWorldAccess`'s current dimension. `Waypoints.readDimension` opens another dimension's file without replacing the live list (the dimension toggle). On a dimension change `NAClient` calls `Waypoints.load()` before anything can save, so a death waypoint cannot be written into the wrong file.

Temp waypoints are removed when the player is within 4 blocks on XZ (`NAClient.onWorldTickUnsafe`).

## Slime seed — `slime_seed.txt`

A single string, the raw text the player typed. Blank or missing file → use `NAWorldInfo`'s seed. `SlimeSeed.parse`: if the whole string is a `long`, use it; otherwise `String.hashCode()` (Beta's create-world rule). Loaded on world join, saved immediately on edit.

## Import index — `regions/import-index.txt`

`ImportIndex`. UTF-8, one region file per line:

```
<dim>|<canonical path><TAB><size><TAB><mtime><TAB><base64 of deflated 8192-byte header>
```

The header is the `.mcr` 8 KB location table plus timestamp table. Chunk `i` is unchanged when both the 4-byte location at `i*4` and the 4-byte timestamp at `4096 + i*4` match, and the new timestamp is not zero. Written via a `.tmp` file and `Files.move`. A short or corrupt line is skipped.

## Spawners — `regions/spawners.txt`

`SpawnerStore`. One spawner per line:

```
<dim><TAB><x><TAB><y><TAB><z><TAB><mob>
```

Tabs inside the mob name are replaced with spaces. In memory the store is keyed by dimension and chunk (`cx << 32 | cz`), so replacing one re-read chunk does not touch its neighbours. Saved as a temp file then moved. Dirty flag avoids rewriting when nothing changed.

## Map tiles — `region_<rx>_<rz>.png`

`NARegionStorage`. 512×512 `TYPE_INT_ARGB`. Index in the `int[]` is `z * 512 + x`, world block `(rx*512 + x, rz*512 + z)`. Alpha 0 means unexplored; exports keep that transparency. The image has no custom chunks; it is a plain ImageIO PNG.

Writes go to `region_X_Z.png.tmp` and then replace the destination (atomic move, then non-atomic move, then a direct write if both moves fail). Readers (`WorldMapImage`) therefore do not see a half-written file from the normal path.

`MapSaver` is the only writer for the live cache. `enqueueExternal` is the writer for off-screen import tiles.

### Delete

`MapDataReset.deleteCurrentWorld`:

1. Cancel the importer.
2. `MapSaver.stopAndDiscard` (drop the queue; do not flush it back).
3. `NARegionPixelCache.clearAndDeleteTextures`.
4. `MapDataFiles.detach` renames `regions/` to `regions.deleting-<millis>` beside it, then a daemon thread deletes the tree. If the rename fails, it deletes in place.
5. The root it detaches is `getWorldMapRootPath(!multiplayer)`: the **tile** root. In multiplayer that includes `import-index.txt` and `spawners.txt`. In singleplayer the tile root is `saves/<name>/natosatlas/regions`, and the index/spawners under `natosatlas/worlds/<name>/regions` are a different directory.

Kept on purpose: `import/`, `slime_seed.txt`, `*.points`, and `settings.txt`. Leftover `regions.deleting-*` directories are swept on the next delete.

## Exported images

`WorldMapImage` stitches tiles on a worker thread, one horizontal band at a time, with a tiny PNG writer (IHDR, tEXt, IDAT, IEND). It does not use ImageIO for the output, so a huge map does not have to sit in a `BufferedImage`.

| Export | Trigger | File | Scale |
| --- | --- | --- | --- |
| Full | P, or "Save map image (full)" | `screenshots/worldmap-<yyyy-MM-dd_HH.mm.ss>.png` | 1 pixel per block |
| Small | Shift+P, or "Save map image (small)" | `worldmap-<ts>-small.png` | 1 pixel per 4×4 blocks, box-averaged |
| Markup | Ctrl+S or the Save tool | `worldmap-markup-<ts>.png` | up to 8 px/block, long side capped at 4096, north-up |

The explored bounding box is cropped. A side longer than 32768 pixels is refused; the chat message tells you to use the small export. Names collide by appending `_1`, `_2`, ...

PNG `tEXt` comment (`keyword = Comment`):

```
Natos Atlas world map. Top-left pixel = block X <minBlockX>, Z <minBlockZ>. <scale> block(s) per pixel.
```

`WorldMapExport` flushes the live cache to disk on the render thread first (textures must be uploaded there), waits up to 20 s for the saver, then stitches. The dimension argument is the one the map is showing, which may not be the player's. `MarkupExport` does the same and then `MarkupImage` draws cells and labels over the bitmap. Markup shapes themselves are not a file format.

## `.mcr` input

Not written by the mod. `McRegionReader` understands:

- File names `r.<rx>.<rz>.mcr` (`RegionImporter.parseRegion`).
- The 8 KB header described above.
- Chunk payloads: compressed NBT, `Level.Blocks` (32768 bytes), `Data`, `BlockLight`, `SkyLight` as nibble arrays, `Level.TileEntities[]` compounds with `id`, `EntityId` (the mob), `x`, `y`, `z`.

`NARegionFile` plus `ClientWorldAccess.getRegionFiles` is a different reader, used by singleplayer generate. It asks the game's `RegionFile.hasChunkData` and then `getChunkFromDisk`. It accepts `.mca` names in the listing on StationAPI, but the mp importer's `listMcr` does not.

## Server `config.txt` (unused)

`ServerConfig` would load `<game>/natosatlas/config.txt` if `NAServer.start` ran. Keys: `webHost`, `webPort`, `regionUpdateTimerMs`, `logLevel`, `showPlayers`. Nothing in the current client reads this file. See [multiplayer.md](multiplayer.md).
