# Keybinds

Two layers of keys: the ones Minecraft shows in Options, and the ones the map screen handles itself while it is open. The second group is not rebindable. They are LWJGL key codes in `MapScreen` and `MarkupTool`.

## Minecraft key bindings

Registered with the loader, so the player can rebind them in the game's controls screen.

| Action | Default | StationAPI | BTA | ModLoader |
| --- | --- | --- | --- | --- |
| Open world map | M | `STClientEntry.KEY_MAP` (`natosatlas.key.map`) | `BTAClient.KEY_MAP`, also listed on an options page | `mod_NatosMap.KEY_BINDING_MAP` ("Natos Atlas") |
| Waypoint list | J | `KEY_WAYPOINTS` | `KEY_WAYPOINTS` | not registered |
| Create waypoint | K | `KEY_ADD_WAYPOINT` | `KEY_ADD_WAYPOINT` | not registered |

StationAPI registers them on `KeyBindingRegisterEvent`. BTA registers an options page (`natosatlas.options.title`) with a keybind category. ModLoader only registers the map key, via `ModLoader.RegisterKey`.

The open-map check is "key is down and no screen is open", polled from the client tick (`STMinecraftMixin`, `BTAMinecraftMixin`), not an edge trigger. `MapScreen` then ignores keys until that key has been seen *up* (`mapKeyArmed`), so holding M does not instantly re-enter markup or, in the Integrated layout, instantly close the map.

`MapScreen.mapKeyCode` is updated every tick from `KEY_MAP.code` **only in `STMinecraftMixin`**. BTA and ModLoader leave it at the default `Keyboard.KEY_M`. If those loaders rebind the open-map key, the second press inside the map still listens for M.

Death waypoints are not a key. The same tick mixin, on StationAPI and BTA, adds a red "Last Death" waypoint once when `health <= 0`.

## Keys while the map is open

Handled in `MapScreen.keyPressed`. Buttons that Preferences has hidden obey `MapButtons.keyWorks()`: the key still works when `Settings.hiddenButtonKeys` is true (the default), and stops working when that preference is off. Escape always closes, except that Segregated markup consumes the first Escape.

| Key | Action | Hidden-button gate |
| --- | --- | --- |
| Esc | Segregated markup: leave markup and forget it. Otherwise close the screen (`UIScreen` opens `parent`) | no |
| M (the map key) | Segregated, markup off: enter markup. Integrated: close the map | `MapButtons.MARKUP` (Segregated only; Integrated ignores the gate and always closes) |
| D | Toggle Overworld / Nether view | `MapButtons.DIMENSION` |
| L | Toggle layer view | `MapButtons.LAYER` |
| Up / Down | Layer view: Y ± 1, or ± 8 with Shift | only while layer view is already on |
| Shift + wheel | Layer view: Y ± 1 | same |
| Space | Reset rotation and centre on the player, or on the ghost | no |
| P | Save the full map image of the dimension and layer on screen | no |
| Shift+P | Save the small (4×4 averaged) image | no |

D and L are ignored while Ctrl is held, so they do not fire on top of a shortcut the text fields use. The map screen has no text fields; settings does.

Mouse controls (pan, rotate, zoom, double-click waypoints) are in [ui-flow.md](ui-flow.md).

## Markup keys

Active whenever `markupMode` is true. In the Integrated layout that is the whole time the map is open, so these keys always work there, including while Pan is selected. `markupKey` runs before the map keys, and a handled key is not passed on. Ctrl+something other than Z or S returns false so the map can still see it.

| Key | Action |
| --- | --- |
| `` ` `` | Pan |
| 1 | Pencil |
| 2 | Line |
| 3 | Rectangle (the tooltip says Square in some older notes; the tool is `MarkupTool.RECT`) |
| 4 | Ellipse |
| 5 | Shape eraser |
| 6 | Block eraser |
| F | Fill on/off |
| `[` / `]` | Thinner / thicker (1, 2, 3, 5) |
| Ctrl+Z | Undo |
| Ctrl+S | Save markup PNG |
| Shift (held) | Square, circle, or 45° line while dragging |
| Esc | Segregated: leave markup. Integrated: not consumed; the map closes |

`HelpScreen` still says M opens markup and Esc closes markup. That matches Segregated mode only. The table above is what `MapScreen` does.

## Keys in settings

`SettingsScreen.keyPressed` gives a focused `UIElementTextField` the key first. The slime-seed box and the two import path boxes support typing, Home, End, and Ctrl+A / C / X / V. Escape still closes the screen, because `UIScreen.keyPressed` handles Escape before the field loop.

Import is Shift-click on the Import button (force), not a key. Delete-all-map-data is a second click within 4 seconds, not a key.
