# Screens and UI flow

Every screen is a `UIScreen`. The platform (`STClientPlatform`, `BTAClientPlatform`, `MLClientPlatform`) is the only place that constructs a Minecraft `Screen`. Game code calls `NAClient.get().getPlatform().screen.openNacScreen(...)`. Passing `null` closes the GUI and returns to the world. Passing the screen's `parent` is "back".

The map does not pause the game. `MapUpdater` keeps scanning while the map is open.

```
world (no screen)
  M  -> MapScreen(null)
  J  -> WaypointListScreen(null)          StationAPI and BTA only
  K  -> WaypointCreateScreen(null)        StationAPI and BTA only

MapScreen
  gear        -> SettingsScreen(map)
  ?           -> HelpScreen(map)
  waypoints   -> WaypointListScreen(map) -> WaypointCreateScreen(list)
  double-click left on the terrain -> WaypointCreateScreen(map, x, z)
  Esc (no segregated markup) -> parent, usually null (close)
  Esc (segregated markup)    -> leave markup, stay on the map
  second M, Integrated       -> close the map
  second M, Segregated       -> enter markup

SettingsScreen
  tabs: World map | Minimap | Import | Preferences | Debug
  Import > Generate Existing (singleplayer only) -> GenerateScreen(settings)
  Esc -> back to the map (or whoever opened settings)
```

`MapScreen.onClose` drops the other dimension's GL textures. Coming back from Options or Help calls `init` again, which reloads that cache if a foreign dimension was showing. Pan, zoom, and rotation live on `MapViewport` and survive that re-init (`firstInit` centres on the player only once).

## Map screen layout

`MapScreen.buildButtons` runs on init and again whenever a toggle needs a new icon or tooltip (dimension, markup, layer view, spawner filter, entities).

**Top-left row**, left to right, 20×20 icons with a 5 px gap, from `UIHorizontalLayout(5, 15, ...)`:

1. Close (X) — `MapButtons.CLOSE`
2. Options (gear) — opens `SettingsScreen`
3. Help (?)
4. Markup (pencil) — **only in the Segregated layout**. Integrated has no mode to enter, so the button is not created even if Preferences still says "show Markup".

**Top-right row**, packed from the right by `MapTopBar.layout`. Slot order, right to left:

Waypoints, Slime chunks, Spawners, Grid, Entities, Time (map mode), Layer view, Dimension.

Hidden Preferences buttons are omitted and the row closes up. There is no empty slot. The Time button is still shown in the Nether, greyed out (`active = false`, cave icon, tooltip from `MapTopBar.mapModeTooltip`), because removing it used to shift the row.

`leftChromeEnd` and `rightChromeStart` are the inner edges of those two rows. The Integrated markup bar uses them to decide whether it fits on the top row.

**Layer-view extras**, only while layer view is on: `+` and `-` at the right edge (`MapLayerSlider.upButton` / `downButton`) and a vertical track drawn in `renderLayerSlider` (Y 127 at the top, a blue tick at sea level 64). The track is not a `UIElement`; `MapScreen` hit-tests it itself.

**Bottom centre:** a box with the block under the cursor. In the player's dimension it includes the biome name. In another dimension it includes the converted coordinate in the dimension you are standing in (×8 or ÷8).

**Top centre status lines**, stacked: import progress, "Viewing: Nether (you are in the Overworld)", map-image export progress, "Layer view: Y n", and a short toast (copied spawner coords, foreign-dimension waypoint refusal).

## How a frame is drawn

`MapScreen.render`:

1. Arm the map key once it has been released, so the M that opened the screen cannot also toggle markup.
2. `syncDimension` — if the player changed dimension under the open map, follow them.
3. Fill the background.
4. `viewport.begin` pushes a GL transform: translate to centre, rotate, zoom, scroll.
5. Build a `MapContext`. `ctx.tiles` is the `NAForeignRegionCache` when viewing another dimension, otherwise null (live cache).
6. `viewport.computeVisibleRegions` — the set of region keys overlapping the screen.
7. Terrain: either `SliceRenderer` (layer view) or `MapStageRegions` (the stored layer in `activeLayer`).
8. Overlays, in order: slime squares, grid, waypoints, entities **or** a translucent ghost player, spawner markers, yellow block highlight.
9. `viewport.end` pops the transform.
10. Markup shapes are drawn in *screen* space by `MarkupRenderer` (it projects each block cell itself, so rotation is already applied).
11. Chrome: debug text, coordinate box, status lines, toolbar panels, then `super.render` for the buttons, then the layer slider and hover tooltips.

`activeLayer` is chosen in `tick`, not in `render`. Foreign dimensions use `viewLayerGuess` (Nether → cave, otherwise the selected mode; Auto follows the clock only).

## Pointer and keys on the map

Handled in `MapScreen`, after `UIScreen` gives buttons the first chance at a left click.

| Input | Outside markup, or Integrated with Pan selected | Markup tool other than Pan |
| --- | --- | --- |
| Left drag | Pan (`MapViewport.drag*`) | Draw or erase |
| Left double-click | Create waypoint at the block (refused in a foreign dimension) | Consumed by the tool |
| Right drag | Rotate | Still rotates (markup swallows the button so a double-right-click cannot make a temp waypoint) |
| Right double-click | Temp waypoint `Temp #n` at the block, y = 0 | — |
| Middle drag | Pan | Pan |
| Wheel | Zoom between `MapConfig.MIN_ZOOM` (0.05) and `MAX_ZOOM` (4), around the default from settings | Same, unless layer view and Shift (then Y changes) |
| Space | Rotation 0 and centre on the player, or on the ghost | Passed through (`markupKey` returns false) |

`MapProjection` is the pure math behind this: screen ↔ world, screen ↔ block (floor, no +1 — an mp9 fix), pixels per block, the visible world rectangle, and centring the viewport on a block. Markup, the ghost, and exports all call it so they share one definition of "the block under the cursor".

Rotation is a horizontal drag mapped to radians (`dx * π / 200`). Panning is un-rotated by the current angle so the map follows the cursor.

## Dimension toggle

`viewDim` is what the map shows. `playerDim` is where the player is. They differ after D or the dimension button.

`setViewDim`:

- Drops any in-progress stroke.
- Frees the previous `NAForeignRegionCache`.
- For a foreign dimension, builds a new cache (`dim`, `useSaveFolder = !multiplayer`) and loads that dimension's `.points` with `Waypoints.readDimension` (read-only, does not replace the live list).
- Scales the view centre by `MapDimensions.scale` (Nether → Overworld is ×8, the other way is ÷8, anything else is 0 and the centre stays).
- Rebuilds buttons so the dimension icon and the greyed Time button update.

Live scanning never writes the foreign cache. Import still targets the dimension you are physically in, except the Import tab, which can fill both dimensions at once (the other one through `renderForeign`).

You cannot create waypoints while `viewDim != playerDim`. The toast says so. Space centres on the ghost. P / Shift+P export the dimension on screen.

`MapDimensions` (`client/ui/screens/map/MapDimensions.java`) is the whole policy: names, the ×8 scale, the toggle order (Overworld, Nether, plus the player's dimension if it is some other id), and "the Nether has a ceiling".

## Other screens

**Settings** is documented in [settings.md](settings.md). It is a tabbed `UIScreen`. Opening it does not discard map pan/zoom. Closing it saves settings.

**Help** (`HelpScreen`) is a centred two-column list of control strings. It is not generated from `MarkupTool` or `MapButtons`, so it still describes M as "markup mode" and Esc as "close markup". Integrated layout does not match those two lines. The keys that do match are in [keybinds.md](keybinds.md).

**Waypoint list** (`WaypointListScreen` + `WaypointListElement`) shows the live dimension's waypoints. Edits go through `Waypoints` and are written immediately.

**Waypoint create** (`WaypointCreateScreen`) edits name, x, y, z, colour, visibility. Opened from the list, from K, or from a double-click (x/z filled, y defaults to the player). Saving calls `Waypoints.add`, which refuses an exact duplicate and writes the `.points` file.

**Generate Existing** (`GenerateScreen`) is singleplayer only (the button is inactive in multiplayer). It lists region files in the current dimension's save directory via `ClientWorldAccess.getRegionFiles` / `getChunkFromDisk`, and each tick feeds a few chunks to `MapUpdater.updateChunk` while drawing the map underneath so you can watch tiles appear. It does not use `RegionImporter` or the import index.

## Minimap

Not a screen. `STHudMixin` / `BTAHudMixin` call `NAClient.renderGui` at the end of HUD render, after skipping the F3 debug HUD. `MinimapRenderer` builds its own `MapViewport` (size `100 * minimapScale`), centres it on the player every frame, optionally rotates with yaw, and draws `MapStageRegions`, waypoints, and entities for the minimap's own layer choice (`Settings.minimapRenderMode`). Waypoints outside the box get edge ticks. It does not draw slime, grid, spawners, markup, or layer view.

## Tooltips

`MapTooltips` builds strings of the form `Name (extra) [hotkey]`. Icon buttons draw them in `UIScreen.render` after the buttons. Spawner and palette tooltips are drawn by `MapScreen` itself because those hits are not buttons. Spawner colours and the cycle order live in `MapSpawners`: Off → All → Spider → Zombie → Skeleton → Pig, drawn as small squares (red, green, white, pink). Left-click copies `x y z` via AWT clipboard.
