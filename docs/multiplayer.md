# Multiplayer and networking

The mod does not speak a custom protocol. A remote server does not need the mod installed. "Multiplayer support" in this fork is: do not crash when chunks are missing, keep per-server files apart, and optionally paint a map from the server's region files on disk.

The tested path is the StationAPI client. BTA and ModLoader share `NAClient` and `RegionImporter`, but the chunk-safety checks below are written against StationAPI's `EmptyChunk` and `World.isPosLoaded`.

## What the client uses from the vanilla connection

`STClientWorldAccess.getWorldInfo` reads the client world: height, level name, time, seed, dimension id, `dimension.hasCeiling`, and `world.isRemote`. That seed is the slime-overlay default. The level *name* is not the folder name; the folder comes from the connect screen.

`STScreenConnectingMixin` runs at the end of `ConnectScreen`'s constructor and calls `session.setServerName(host + "-" + port)`, for example `100.73.7.65-25567`. `NAClient.tick` will not call `onWorldJoined` for a remote world until that string is set. `NAPaths.sanitizeFolderName` then turns it into a single path segment: anything outside `[A-Za-z0-9._-]` becomes `_`, leading dots are stripped, and the name is capped at 100 characters. IPv6 colons and percent-encoding cannot break out of `natosatlas/servers/`.

`STClientWorldAccess.getChunk` (`stationapi/client/STClientWorldAccess.java`):

1. Return null if the world is gone.
2. Return null unless `world.isPosLoaded(chunkX << 4, 0, chunkZ << 4)`. This is the "do not touch chunks the client has not received" check, the same idea as Rei's Minimap Babric port.
3. Return null if the chunk is an `EmptyChunk` placeholder.
4. Return null if the chunk's own x/z do not match the request.
5. Wrap the chunk. Every block read goes through `ChunkWrapper.inBounds` (x and z in 0..15, y in `0 .. height-1`). Out of range reads return 0 instead of throwing. That is the mp1 fix for the `y = -1` crash on join.

`MapUpdater` also treats `isAllAir()` as "not received yet" and skips the chunk. `NAChunkBuilderSurface` bails out of a column whose top solid Y is ≤ 0, so it never samples below the world.

Per-tick failures in `NAClient.onWorldTick` and per-chunk failures in `MapUpdater` are logged and swallowed. A bad chunk does not take down the client thread.

Dimension changes flush the saver with the **old** dimension id before the cache is cleared (`NAClient.tick`). Disconnect (`world == null` after having been in a world) cancels the importer, flushes, and stops the saver.

## Region import (the other way to fill the map)

Import reads Beta 1.7.3 `.mcr` files from a directory the player chooses. It does not open a socket. `.mca` (Anvil) files are ignored. The reader's own files are never modified.

Entry points:

- **Settings → Import → Import** calls `RegionImporter.startFromPaths(overworld, nether, shiftClick)`.
- The older `RegionImporter.start()` reads `import/` (or `import/DIM-1/`) for the current dimension. The buttons that called it were removed in mp15; the method is still there.

`McRegionReader` (`core/io/McRegionReader.java`) parses the format itself:

- 8 KB header: 1024 big-endian sector locations, then 1024 chunk timestamps.
- Each present chunk is a zlib-compressed NBT compound. The reader pulls `Blocks`, `Data`, `BlockLight`, `SkyLight` and walks `Level.TileEntities` for `id = MobSpawner` (mob name and x/y/z).
- `ArrayChunk` is a `ChunkWrapper` over those arrays, with the same bounds checks as the live wrapper.

`RegionImporter.tick` spends at most ~6 ms (`BUDGET_NANOS`). Work is queued as `{file, localChunkX, localChunkZ}`.

- A chunk in the **player's current dimension** is passed to `MapUpdater.updateChunk`. Since mp8 that includes chunks the client has loaded. Imported pixels overwrite the live buffer and the PNG; the 8-chunk live scan repaints the neighbourhood afterwards. Before mp8 the importer skipped every loaded chunk, which left a blank square the size of the server view distance, because live scanning only covers a smaller circle.
- A chunk in the **other dimension** is rendered off-screen (`renderForeign`) into buffers loaded from that dimension's PNGs, then `MapSaver.enqueueExternal` writes them. Only one foreign region is held at a time, and the tick pauses if the save queue is deeper than 24 so those full-size buffers cannot pile up.
- Corrupt chunks increment a failure count and are skipped.

The importer cancels itself if the player's dimension changes mid-run.

### Skipping unchanged data

`ImportIndex` (`client/ImportIndex.java`) remembers, per absolute path and dimension:

```
dim|canonicalPath <TAB> size <TAB> lastModified <TAB> base64(deflate(8192-byte header))
```

A file whose size and mtime match is not opened. Inside a changed file, chunk `i` (`x + z*32`) is skipped when its timestamp is non-zero and both the location int and the timestamp int match the stored header. Shift-click Import ignores the index. "Delete all map data" removes the index in multiplayer, because the index file sits inside that world's `regions/` folder. See [data-formats.md](data-formats.md).

Spawners from chunks that were actually read replace that chunk's previous list (`SpawnerStore.setChunk`). Unchanged chunks keep theirs. A forced reimport clears the dimension first.

### Where the folder boxes point

`resolveRegionDir` accepts either the `region` directory or the world directory above it.

- Overworld: the path itself, or `<path>/region`.
- Nether: the same, plus `<path>/DIM-1/region` and `<path>/DIM-1`.

Empty quotes and a surrounding pair of quotes are stripped. An empty box is skipped. A missing folder or a folder with no `.mcr` files sets `RegionImporter.getError()`, which the Import tab draws in red.

`ensureFolders` on multiplayer join creates `import/` and `import/DIM-1/` under the server data directory. Those folders are the legacy drop location. The Import tab does not require them; it reads whatever path you paste. `FolderOpener` can still open a directory, but no current button calls it.

## Slime chunks on a server

The server seed arrives on the vanilla world and is readable from `NAWorldInfo`. `SlimeSeed` can replace it per server (or per singleplayer world) with a number or a string. Strings use `String.hashCode()`, the same conversion as the Beta create-world screen. The number is never shown on the map or in a tooltip; the settings button says "custom seed" or "server seed". The formula itself is in `MapStageSlime.isSlimeChunk` and is documented in [rendering.md](rendering.md).

## Singleplayer versus multiplayer in the client

`NAWorldInfo.isMultiplayer()` (from `world.isRemote`) chooses paths:

| | Multiplayer | Singleplayer |
| --- | --- | --- |
| Data root | `.minecraft/natosatlas/servers/<sanitized host-port>/` | `.minecraft/natosatlas/worlds/<save name>/` for waypoints, slime seed, import index, spawners |
| Terrain PNGs | that same root's `regions/` | `<save>/natosatlas/regions/` inside the Minecraft saves folder |
| Import button | enabled | enabled (you can still point it at a folder) |
| Generate Existing | button inactive | walks the save with `getChunkFromDisk` |
| `import/` folders | created on join | not created |

`getChunkFromDisk` on StationAPI uses `FlattenedWorldChunkLoader` against the dimension directory. That is the singleplayer generate path. The mp importer does not use it; it uses `McRegionReader` so it can run against a server directory that is not a loaded world.

## The disabled dedicated-server map

`NACore.startServer` logs "NatosAtlas Serverside is currently disabled and or unimplemented" and returns. `ServerEntry` (StationAPI) and `BTAServerEntry` still call it, so a dedicated server loads the mod, sets `BlockAccess`, and then does nothing else useful.

If `startServer` were changed to construct `NAServer`, the dormant pieces are:

| Class | Role |
| --- | --- |
| `server/NAServer.java` | Daemon thread: load `natosatlas/config.txt`, start the web server, start the overworld watcher. |
| `server/ServerConfig.java` | `webHost` (default `127.0.0.1`), `webPort` (8080), `showPlayers`, `regionUpdateTimerMs` (5000), `logLevel`. |
| `server/RegionFileWatcher.java` | Polls region files and rebuilds tiles. The class comment says `dimDir` is wrong and the author wants it rewritten. |
| `server/web/WebServer.java` | `com.sun.net.httpserver` on a fixed pool of 8 daemon threads. |
| `server/web/routes/TileRoute.java` | `GET /tiles/{dim}/{layerId}/{rx}/{rz}.png`. Refuses any dimension other than 0. |
| `server/web/routes/PlayerRoute.java` | Player positions, also overworld-only in practice (same FIXME). |
| `server/web/routes/IndexRoute.java`, `AssetsRoute.java` | A small browser page and its assets. |
| `stationapi/server/STServerPlatform.java` | Level name from `server.properties`, region directories, players, chunks. It redirects `System.out` around the chunk loader (FIXME in that file). |

`TileRoute` reads the same `region_X_Z.png` files the client writes, via `NAPaths.getWorldMapStoragePath(..., useSaveFolder = true)`. Nobody on the client talks to this port. Layer view and the dimension toggle are not a network feature; they read local files and the client's loaded chunks.
