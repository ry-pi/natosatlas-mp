# Preferences and settings

All client preferences are static fields on `Settings` (`client/ui/screens/settings/Settings.java`), loaded once when `NAClient` is constructed and written by `Settings.save()`. The file is `.minecraft/natosatlas/settings.txt`, a `key=value` list via `TextStorage` / `SettingsStorage`. It is global, not per world. Missing keys keep the defaults in `onLoad`. Unknown keys are dropped on the next save because `onSave` rewrites the whole map.

The slime seed, waypoints, import index, and tiles are **not** in this file. They are per world. Only the two import *paths* are global settings.

## Tabs

`SettingsScreen` opens on **World map**. Tabs: World map, Minimap, Import, Preferences, Debug. The old General tab is gone (mp15). Tab buttons are rebuilt in `init`; the body is `rebuildContent()`.

Closing the screen calls `Settings.save()` and `applySlimeSeed()` / `applyImportPaths()` so a half-edited box is kept.

### World map

Left column, `SettingsOption` cycles:

| Option | Field | Values | Default in code | Default if the file has no key |
| --- | --- | --- | --- | --- |
| Block colours | `blockColors` | `Classic`, `TrueColor` | Classic | Classic |
| Grid | `mapGrid` | on/off | on | on (`getBoolean` default true) |
| Slimes | `showSlimeChunks` | on/off | off | off |
| Entities | `entityDisplayMode` | Player → All → Nothing | All in the field initializer | **Player** (`onLoad` default) |
| Mode | `mapRenderMode` | Day → Night → Cave → Auto | Auto | Auto |
| (slider) | `defaultZoom` | `MapConfig` range | 0.5 | **1.0** if the key is absent |

The slime-seed text field under that column is not a `Settings` field. It reads and writes `slime_seed.txt` through `SlimeSeed`. Blank means "use the world seed".

Right column: **Save map image (full)** and **Save map image (small)**, which call `WorldMapExport.start`. A line under them says images go to `.minecraft/screenshots`.

The map's own Time / Grid / Entities / Slime buttons write the same fields and call `Settings.save()` immediately. The settings buttons and the map buttons stay in sync because they share the statics.

### Minimap

| Field | Meaning | File default |
| --- | --- | --- |
| `minimapEnabled` | Draw the HUD map | false |
| `minimapRotateWithPlayer` | Rotate with yaw | false |
| `minimapZoom` | Zoom inside the HUD map | 1.0 if absent (field initializer is 0.5) |
| `minimapScale` | HUD widget size, `100 * scale` pixels | 1.0 |
| `minimapPosition` | `TopLeft`, `TopRight`, `BottomLeft`, `BottomRight` | TopRight |
| `minimapEntityDisplayMode` | Same cycle as the world map | Player if the key is absent |
| `minimapRenderMode` | Day / Night / Cave / Auto, independent of the world map | Auto |

Off by default. The HUD mixin also skips the minimap while the F3 debug HUD is up.

### Import

**Multiplayer section.** Two `UIElementTextField`s bound to `importOverworldPath` and `importNetherPath`, and one button.

- Click: `RegionImporter.startFromPaths(overworld, nether, false)`.
- Shift-click: the third argument is true (ignore the index).
- While an import is running the label is **Stop import** and a click calls `cancel()`.
- Status (`getStatus()`) and errors (`getError()`) are drawn under the button. The hint shows how paths are resolved.

**Singleplayer section.** **Generate Existing** opens `GenerateScreen`. The button's `active` flag is false when `NAWorldInfo.isMultiplayer()` is true.

Paths are stored exactly as typed, with CR/LF stripped. Resolution rules are in [multiplayer.md](multiplayer.md).

### Preferences

This tab is mp16–mp18. It does not use `SettingsOption`.

**Show buttons.** One toggle per `MapButtons` constant. Left column is the top-left map row (Close, Options, Help, Markup). Right column is the top-right row, right to left (Waypoints, Slime, Spawners, Grid, Entities, Time, Layer view, Dimension). Each writes `showButton.<key>=true|false`. Hidden buttons are left out of `MapScreen.buildButtons` and `MapTopBar.visibleSlots()`, and the row closes up.

**Markup layout.** `markupIntegrated`.

- Off: label "Layout: Segregated". The pencil button can be shown. Second M enters markup.
- On: label "Layout: Integrated". "Show Markup (pencil)" is greyed (`markupToggle.active = false`) because there is no markup mode to hide. The layout heading on the map side is the "(always on)" behaviour described in [markup.md](markup.md).

**Keys.** `hiddenButtonKeys`, default true, label "Keys: On/Off". When off, a hidden button's shortcut (M in Segregated, D, L) stops working. `MapButtons.keyWorks()` is `shown || hiddenButtonKeys`. Escape is not in that enum.

Tool keys (`` ` ``, 1–6, F, brackets, Ctrl+Z, Ctrl+S) are not button-visibility keys. They work in both layouts whenever markup is on. In Integrated, markup is on for the whole time the map is open.

### Debug

- **Debug Info** (`debugInfo`): the canvas/cache overlay in `MapScreen.renderDebugInfo`.
- **Delete all map data**: first click arms the button for 4 seconds ("Click again to confirm"), second click calls `MapDataReset.deleteCurrentWorld`. Details of what is kept are in [data-formats.md](data-formats.md).

## What is stored, and what is only memory

| State | Stored? | Where |
| --- | --- | --- |
| Everything in `Settings` and `MapButtons.shown` | yes | `settings.txt` |
| Slime seed text | yes | per-world `slime_seed.txt` |
| Waypoints | yes | per-world `*.DIM*.points` |
| Import paths | yes | `settings.txt` |
| Import index, spawners | yes | per-world `regions/` |
| Map pan, zoom, rotation | no | `MapViewport`, kept on the open `MapScreen` only |
| `viewDim` (dimension toggle) | no | resets when the map is constructed |
| Layer view on/off | no | `MapScreen.layerView` is static, so it survives a new map screen in the same session, and it is not written to disk |
| `layerY` | yes | `settings.txt` |
| Spawner filter | yes | `spawnerFilter` string (`Off`, `All`, or a mob name) |
| Markup shapes, colour, thickness, fill, tool | no | die with the screen |
| `markupIntegrated` | yes | `settings.txt` |

`layerView` being a static boolean is easy to miss: closing the map and reopening it in the same session keeps layer view on, but restarting the game turns it off, while Y is restored.

## Adding a setting

1. Add a field on `Settings`.
2. Read it in `SettingsStorage.onLoad` and write it in `onSave`. `TextStorage` already has boolean, int, float, enum, and string helpers.
3. If it is a cycle button, add a `SettingsOption` constant and an `UIElementOptionButton` in the right `build*` method.
4. If it is a map-chrome button, add a `MapButtons` entry **and** a `MapTopBar.Slot` with the same enum name. `visibleSlots()` does `MapButtons.valueOf(slot.name())`. The two enums must stay in lockstep.
5. Call `Settings.save()` from the control. The screen's `onClose` also saves, but map buttons that are used with settings closed have to save themselves. The existing map toggles do.
