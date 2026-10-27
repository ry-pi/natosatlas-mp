VIBE CODE WARNING:
THIS WAS MADE ENTIRELY BY CURSOR CLOUD AGENTS. I ONLY WATCHED IT CODE ENOUGH TO BE ABLE TO FIX THE BUGS IT CREATEED
I WILL NOT BE MAINTAINING THIS CODE
USE AT YOUR OWN RISK





> **Unofficial multiplayer fork (1.5.4-mp) of [Natos Atlas](https://github.com/natowb/bm-natosatlas) by natowb, MIT licensed (see `LICENSE`, unchanged).**
> This fork is based on upstream tag `1.5.4` and adds multiplayer fixes, a True colour block mode, Classic grass in True colour mode, server region-file import, a manual slime seed and a corrected slime-chunk formula. See the change notes at the bottom.
> Only the **StationAPI** build was patched and tested (Babric, StationAPI 2.0.0-alpha.6.4 at runtime). The ModLoader/BTA modules share the core code but were not tested.
> Fork builds are on this repo's Releases page; the upstream releases link below is the original mod.

<p align="center">
  <img src="assets/banner.png" alt="Natos Atlas Banner">
</p>

A world map mod for **Minecraft Beta 1.7.3**, currently available for **StAPI**,  **ModLoader**, and **BTA**.

## 🚧 Early Development Notice
This project is in a very early stage. Designs are experimental, features will change frequently, and instability is expected while work progresses toward version 1.0.

## Requirements

**ModLoader Requirements:**
* [Mod Loader b1.7.3](<https://mcarchive.net/mods/modloader>)

**StAPI Requirements:**
* [Babric Prism/MultiMC Instance](<https://github.com/babric/prism-instance>)
* [StationAPI >=2.0.0-alpha.3](<https://modrinth.com/mod/stationapi>)

**BTA Requirements**
* [BTA Fabric Loader](<https://github.com/Turnip-Labs/fabric-loader/releases>)
* [Halplibe >= 5.3.3](<https://github.com/Turnip-Labs/bta-halplibe/releases/tag/v5.3.3>)

## 📦 Installation
If you already have a Minecraft Beta 1.7.3 instance set up with either **ModLoader** or **StAPI**,
simply download the latest build from the [Releases page](https://github.com/natowb/bm-natosatlas/releases) and place the file into your instance’s `mods` folder.

## 🖼️ Preview

### Map Modes
<p align="center">
  <img src="/assets/modeDay.png" width="30%" alt="Day Mode">
  <img src="/assets/modeNight.png" width="30%" alt="Night Mode">
  <img src="/assets/modeCave.png" width="30%" alt="Cave Mode">
</p>

### Waypoints
<p align="center">
  <img src="/assets/waypointList.png" width="30%" alt="Waypoint List">
  <img src="/assets/waypointCreate.png" width="30%" alt="Create Waypoint">
  <img src="/assets/waypointEdit.png" width="30%" alt="Edit Waypoint">
</p>

### Map Controls
<p align="center">
  <img src="/assets/mapPanCreateWaypoint.gif" width="40%" alt="Map Panning & Waypoint Creation">
  <img src="/assets/mapRotate.gif" width="40%" alt="Map Rotation">
  <img src="/assets/mapToggles.gif" width="40%" alt="Map Toggles">
  <img src="/assets/mapEntities.gif" width="40%" alt="Map Entities">
</p>




---
## 1.5.4-mp1 (multiplayer patch)
Unofficial fork of Natos Atlas 1.5.4 by **natowb** (https://github.com/natowb/bm-natosatlas), MIT licensed (see LICENSE, unchanged).
Changes: block reads clamped to valid chunk bounds (fixes the Y=-1 crash on join), empty/unreceived chunks are skipped and retried,
per-chunk and per-tick errors are logged instead of crashing, server folder names sanitized (IPv6/special characters), null-safe player/world/biome access.
## 1.5.4-mp2
Unloaded/placeholder chunks are detected with `World.isPosLoaded` + `instanceof EmptyChunk` (pattern from Rei's Minimap Babric port by FarnGitHub, MIT).
Dirty map regions are saved under the old dimension before a dimension change or disconnect; the save queue's pending map is now thread-safe.
## 1.5.4-mp3
Settings > World map > "Block colours": Classic (default, original material colours) or True colour (average of each block's top texture from the active texture pack's terrain.png, cached per block id+meta, grass/leaves biome-tinted, fluids keep classic water colours).
## 1.5.4-mp4
In True colour mode the grass block keeps its Classic colour and biome tint; all other True colour behaviour is unchanged.
## 1.5.4-mp5: import a server's region files (multiplayer)
1. On the server machine, find the world's region folder: `<server folder>/<level-name>/region/` (Overworld, usually `world/region/`) and `<level-name>/DIM-1/region/` (Nether).
2. Join the server once with this mod; it creates `.minecraft/natosatlas/servers/<server folder>/import/` and `import/DIM-1/` (server folder = `host-port`, e.g. `100.73.7.65-25567`).
3. Copy the `r.X.Z.mcr` files into `import/` (Overworld) or `import/DIM-1/` (Nether). Putting them in an `import/region/` subfolder also works. Your files are only read, never changed or deleted.
4. In-game, stand in that dimension, open the map, press Options, then General, then **Import .mcr** (called **Import region files** before mp8). Progress (chunks done / total) shows on the settings and map screens. It runs in the background in small slices each tick, and you can keep playing. Press the button again to cancel.
Imported chunks overwrite what the map had for them, including the area around you; live scanning then repaints chunks you're near as usual (since mp8). Corrupt chunks or files are skipped and logged. `.mca` (Anvil) files are ignored.
## 1.5.4-mp6
Options > World map > "Slime seed (blank = server seed)": a per-server manual world seed for the slime chunk overlay (number or text seed). Also fixes the slime chunk formula to match b1.7.3's own check exactly (the original was wrong far out on the X axis).

## 1.5.4-mp7: save the whole map as a PNG, delete map data
Options > General has three new buttons in the right-hand column:
- **Save map image (full)**: every explored part of the current world/server and dimension, in the map mode you're viewing (Day, Night or Cave; Auto picks the same one the map shows), 1 pixel per block. Saved to `.minecraft/screenshots/worldmap-yyyy-MM-dd_HH.mm.ss.png`.
- **Save map image (small)**: the same at 1 pixel per 4x4 blocks (box-averaged, not nearest), saved as `worldmap-<timestamp>-small.png`.
- **Delete all map data**: click twice within 4 seconds. Deletes every stored map tile (all dimensions and layers) for the current world/server only, clears the tiles in memory, and the map starts filling in again as you move. Waypoints, the slime seed, the `import/` folder and settings are kept.

On the map screen, **P** saves the full image and **Shift+P** saves the small one (P used to write a blocking `exported_map_layer_N.png` into the server folder).
Images are cropped to the explored area. Unexplored parts are transparent. Each image records its top-left world block in its PNG comment. Saving runs on a background thread and writes the PNG in strips, so memory stays low. Images over 32768 px on a side are refused with a chat message suggesting the small version. Existing files are never overwritten (`_1`, `_2`, ... is added). Progress and results show in chat and on the options/map screens.
Tile saves now go to a temp file and are then moved into place, so the export never reads half-written tiles.

## 1.5.4-mp8: import fills the area you're in, "Open folder" button
- **Fix:** importing no longer leaves a big black square around you. The importer used to skip every chunk your client had loaded, which is the server's whole view-distance square, but live mapping only scans an 8-chunk circle, so the rest of that square stayed blank. Imported chunks now overwrite those too, in memory and on disk, and the map updates right away. Pixels of chunks that aren't in the import are left alone. Live mapping still repaints chunks you're near afterwards.
- **Safer saving:** a map tile with unsaved changes, or with a save queued or in progress, is never dropped from the in-memory cache, so it can't be reloaded from an older PNG and saved over newer data. A change made while its tile is being written now queues another save instead of being lost. Tile textures re-upload on the next frame they're drawn instead of waiting for the save queue.
- **Open folder:** Options > General, next to **Import .mcr**. Opens this server's `import/` folder (or `import/DIM-1/` in the Nether) in your file manager, creating `import/` and `import/DIM-1/` if missing. It uses Explorer on Windows, `open` on macOS and `xdg-open` on Linux, falling back to Java's Desktop and then LWJGL's `Sys.openURL` (like vanilla's texture pack folder button). If none of them work, the path is shown under the buttons. Multiplayer only, like the import.

## 1.5.4-mp9: dimension toggle, markup mode
**Dimension toggle.** A new button (or press **D**; since mp10 it sits at the left end of the top-right row of map buttons) that switches the map between the Overworld and the Nether without moving you. The other dimension is drawn from its saved tiles and waypoints, read-only: live mapping keeps writing only to the dimension you're actually in, and the import still uses your dimension. The view stays on the matching place (x8 or /8), your arrow is replaced by a see-through "ghost" at your converted position, a "Viewing: Nether (you are in the Overworld)" label shows at the top, and the bottom bar shows the matching coordinates in your dimension. Space centres on the ghost. You can't add waypoints there. P / Shift+P save the dimension you're viewing. Going through a portal while the map is open switches the view back to where you are.

**Markup mode.** The pencil button after Help in the top-left row (or **B**) opens a toolbar on the left, laid out like Paint.NET's Tools panel and drawn with the map's own buttons:

<p align="center"><img src="assets/mp9-toolbar-preview.png" width="70%" alt="Markup toolbar"></p>

- Tools (**1**-**7** since mp12): Pan, Rectangle, Ellipse, Line, Pencil, Block eraser, Shape eraser. Rectangles snap to whole blocks and show the block coordinates of all four corners plus the size (e.g. `63 x 34`), while drawing and afterwards. Lines show their length while drawing. Hold **Shift** for squares, circles and 45 degree lines.
- Fill on/off (**F**), thickness 1/2/3/5 (blocks since mp11; **[** and **]**), Undo (**Ctrl+Z**), Clear all (undoable), Save (**Ctrl+S**), Close (X).
- Colour: 16 colour palette under the tools; the bar above it is the current colour.
- Shapes are stored in world coordinates, so they stay in place when you pan, zoom or rotate. Right drag still rotates and middle drag pans with any tool.
- **Save** writes the map (the dimension and mode shown) around your markup, plus a margin, with the markup and corner coordinates drawn on it, to `.minecraft/screenshots/worldmap-markup-yyyy-MM-dd_HH.mm.ss.png`: up to 8 px per block, 4096 px max per side, north up. With no markup it saves the area on screen.
- Markup lives in memory only: **Esc** (or the X, or closing the map) closes markup mode and forgets it unless you saved it. The first Esc closes markup; the next one closes the map.

Also fixed: the coordinates at the bottom of the map (and new waypoints from a double click) were one block off towards +X/+Z; they now show the block under the cursor. Waypoints are reloaded when you change dimension, so a death or temp waypoint can no longer write the old dimension's list into the new dimension's file. Middle drag pans the map.

## 1.5.4-mp10
The dimension toggle button moved from the top-left row (next to Markup) to the left end of the top-right row, next to the map mode, entities, grid, slime and waypoint buttons. D still toggles it.

## 1.5.4-mp11: whole-block markup, readable labels, fixed top-right buttons
<p align="center"><img src="assets/mp11-preview.png" width="70%" alt="mp11 markup and top-right buttons"></p>

- **Top-right buttons no longer shift.** In the Nether the map mode button (Day / Night / Cave / Auto, the "A") used to disappear, which slid every button one slot over and made it look like the entities button had gone. Every button now has a fixed slot. In the Nether the map mode button stays in place, greyed out, with the tooltip "Map mode only applies in the Overworld", because the Nether always shows the cave map.
- **Readable labels.** Rectangle corner coordinates and the `W x H` size are bright text with a shadow on a near-opaque black box with padding, in the map and in saved PNGs. They had been drawn with the game font's darkened shadow pass, which is why they looked dark red or grey.
- **Markup colours whole map blocks.** Every tool now fills block cells, like a pixel-art editor:
  - Rectangle: the border cells (or every cell when filled).
  - Ellipse / circle: a symmetric midpoint ellipse on the block grid, with no gaps or doubled corners.
  - Line: Bresenham.
  - Pencil: the block under the cursor, with gaps between mouse samples joined up.
  - Eraser: removes the blocks under it from any shape.
- Thickness is 1, 2, 3 or 5 blocks. Each block is a see-through square (about 66 % opacity). A shape never colours the same block twice, so the opacity stays even.
- Cells are stored in world blocks, so they follow pan, zoom and rotation. Only cells that are on screen are drawn, and each shape's cells are cached.
- Corner labels sit on the outer corners of the rectangle's corner blocks and name those blocks (inclusive). `25 x 29` is the number of blocks.
- Saved markup PNGs show the same blocks.

## 1.5.4-mp12: two erasers, consistent tooltips
<p align="center"><img src="assets/mp12-preview.png" width="70%" alt="mp12 toolbar and tooltips"></p>

- **Two erasers.** Block eraser (**6**) removes just the blocks under it (mp11). Shape eraser (**7**) deletes every shape it touches, in one piece (mp9). Each drag is one undo step for both. In the 2-wide toolbar, Pan now spans the first row so the two erasers sit side by side. To fit at GUI height 240 I removed the "Markup" title and the colour bar; the chosen colour is now the palette swatch with a white frame.
- **Tooltips** all follow `Name (other useful keys) [hotkey]` and no longer describe what a button obviously does, e.g. `Rectangle (Shift = square) [2]`, `Pencil [5]`, `Showing: Nether [D]`, `Thickness: 2 blocks ([ = thinner) []]`. Palette swatches show the colour name.
- **No slime seed on screen** outside the seed box: the slime button just says `Slime chunks`, and Settings says "Slimes using the server seed / custom seed" without the number. The "Numbers or text ... Ctrl+V pastes." hint is gone.
- **Import hint** shows the folder from the game folder down, e.g. `\minecraft\natosatlas\servers\100.73.7.65-25567\import` (Nether: `...\import\DIM-1`), or the full path if it isn't inside the game folder.

## 1.5.4-mp13: import straight from the server folder
<p align="center"><img src="assets/mp13-preview.png" width="70%" alt="mp13 Import tab"></p>

- **New Import tab** in Settings with two wide path boxes, **Overworld region folder** and **Nether region folder**, and one **Import** button that imports both into the world map of the server you're on. Nether data goes into the Nether map even while you're in the Overworld (and the other way round), so the dimension toggle (**D**) shows it straight away.
- The boxes take the region folder itself or the world folder above it: `...\regen\world` finds `world\region`, and `...\regen\world_nether` finds `DIM-1\region`. Quotes around a pasted path are ignored. An empty box is skipped.
- Both paths are saved in `settings.txt`. The boxes scroll for long paths and support Ctrl+V / Ctrl+C / Ctrl+X / Ctrl+A, Home and End.
- **Re-imports only do what changed.** An index (`regions/import-index.txt` in the server's map folder) keeps each region file's size, modified time and header. Unchanged files are skipped without being opened, and in a changed file only chunks whose save timestamp changed are re-read. The status says e.g. `Imported 4 changed regions, skipped 120 unchanged`. **Shift-click** Import to redo everything. "Delete all map data" also clears the index.
- Errors are shown in red under the button (`Overworld folder not found`, `Nether: no .mcr region files`). The old Import .mcr / Open folder buttons on the General tab still work and use the index too.

## 1.5.4-mp14: spawners and Layer view
<p align="center"><img src="assets/mp14-preview.png" width="70%" alt="mp14 spawners and Layer view"></p>

- **Spawners.** The region import now also reads each chunk's tile entities and keeps every mob spawner (mob type and X Y Z) per dimension in `regions/spawners.txt`. Unchanged regions and chunks keep their spawners; a re-read chunk replaces only its own; Shift-click Import rebuilds them all. Run Import once after updating so the spawners get picked up (Shift-click to read every chunk, since unchanged chunks are skipped).
- New **Spawners** button in the top-right row. Clicking it cycles `Off`, `All`, `Spider`, `Zombie`, `Skeleton`, `Pig` (tooltip `Spawners: Spider`). Each spawner is a small square: red = Spider, green = Zombie, white = Skeleton, pink = Pig. Hover one for `Spider spawner: 120 34 -56 (click to copy)`; left-click copies `120 34 -56` to the clipboard.
- **Layer view** (button in the top-right row, or **L**): the map shows the exact horizontal slice of blocks at one Y level. Solid blocks use their map colour (a bit darker when buried, full when open above), air is dark with a faint view of the floor one block below, and torch-lit air is tinted warm, so caves and tunnels stand out.
- Pick Y with the slider at the right edge (drag it, Y 127 at the top, blue tick at sea level 64), the **+ / -** buttons, **Shift + mouse wheel**, or **Up / Down** (Shift = 8 levels). The current Y is shown next to the handle.
- Data: chunks your client has loaded (in the dimension you're in) plus the server region files set on the Import tab (or the old import folder). Works for the Overworld and the Nether (**D**). Tiles are rendered on demand, nearest first, a few milliseconds per frame, and the last 6 Y levels stay cached.

## 1.5.4-mp15: Settings reorganised
<p align="center"><img src="assets/mp15-preview.png" width="90%" alt="mp15 Import and Debug tabs"></p>

- Tabs are now **World map, Minimap, Import, Debug**, and Settings opens on World map. The General tab is gone.
- **World map** tab: the old options are in the left column, and **Save map image (full)** and **Save map image (small)** are in the right column, with the "Map images save to .minecraft/screenshots" line underneath.
- **Import** tab has two sections. **Multiplayer** holds the Overworld and Nether region folder boxes, the Import button (it shows **Stop import** while an import runs, and clicking it then cancels), the status and the hint. **Singleplayer** holds **Generate Existing**, which is greyed out in multiplayer.
- **Debug** tab: **Debug Info** and **Delete all map data** (still asks you to click twice).
- The old **Import .mcr** and **Open folder** buttons, and the "copy region files into" hint, are removed. Use the Import tab.

## 1.5.4-mp18

- **Integrated markup is part of the map, not a mode**: with Markup layout = Integrated, the markup bar (tools, colours, undo/save) is there as soon as the world map opens and drawing works straight away. Pan is selected (drag to move the map, spawner tooltips still show). Esc closes the map; pressing M again (the map key) also closes it. There is no Markup button and M doesn't enter a markup mode. Unsaved markup is forgotten when the map closes (Ctrl+S saves).
- **Segregated** is unchanged: second M press or the Markup button enters markup mode (left panel, Pencil), Esc leaves it, then closes the map.
- **Preferences**: with Integrated, "Show Markup (pencil)" is greyed out and the Markup layout heading says "(always on)". Tool keys (`, 1-6, F, [ ], Ctrl+Z, Ctrl+S) work in both layouts; "Keybinds for hidden buttons" still applies to the map buttons.

## 1.5.4-mp17

- **Settings > Preferences > Markup layout**: *Segregated* (default, the left-side markup panel) or *Integrated* (JourneyMap-style bar along the top: Pan, a gap, then Pencil / Line / Square / Circle / Shape eraser / Block eraser; the 16-colour palette (8 x 2) dead centre; Undo / Thickness / Fill / Clear / Save on the right, each group in its own panel with space between). The Integrated bar sits on the top row between the map buttons when there is room, otherwise on a second row under them (narrow screens: tools on the second row, palette centred under them). Saved in settings.txt (`markupIntegrated`).
- Markup opens with **Pencil** selected in Segregated and **Pan** in Integrated (also when the layout is switched).
- **Pan key is now `** (the key left of 1); 1-6 stay Pencil, Line, Square, Circle, Shape eraser, Block eraser. 0 does nothing.
- Wording: the map mode button is now **Time** (tooltip and Preferences), and the dimension button says **Dimension: Overworld / Nether**.

## 1.5.4-mp16

- **Markup toolbar**: 6 rows of 2 - Pencil / Pan, Line / Undo, Square / Thickness, Circle / Fill, Shape eraser / Clear, Block eraser / Save. The close button is gone (Esc or the markup button closes markup).
- **Markup keys** (number row): 0 pan, 1 pencil, 2 line, 3 square, 4 circle, 5 shape eraser, 6 block eraser.
- **Markup on M**: the world map key (M) opens the map; pressing it again while the map is open starts markup (the press that opened the map doesn't count). B no longer opens markup. Esc closes markup, then the map.
- The "Markup: pick a tool..." and "Markup closed..." messages are gone (closing still forgets unsaved markup; Ctrl+S saves).
- **Settings > Preferences** (tabs: World map, Minimap, Import, Preferences, Debug): show/hide each world map button - Close, Options, Help, Markup, Waypoints, Slime chunks, Spawners, Grid, Entities, Map mode, Layer view, Dimension. Hidden buttons leave no gap. **Keybinds for hidden buttons** (default on): off = a hidden button's key (M, D, L) stops working too. Esc always closes the map. Saved in settings.txt.

## Building this fork
Built with JDK 21. From the repo root:
```bash
./gradlew :natosatlas-stationapi:build
```
The jar lands in `natosatlas-stationapi/build/libs/`. The fork is compiled against StationAPI 2.0.0-alpha.5.4 (see `gradle.properties`) and runs fine on alpha.6.4.

## Credit
Natos Atlas is by natowb (https://github.com/natowb/bm-natosatlas), MIT. The unloaded-chunk detection in mp2 follows the pattern from Rei's Minimap Babric port by FarnGitHub (MIT).
