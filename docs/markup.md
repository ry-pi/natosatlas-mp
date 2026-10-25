# Markup tools

Markup is a drawing layer on the full-screen map. Shapes live in memory, in world block coordinates, and are forgotten when markup ends unless you save a PNG. Nothing is written to `settings.txt` or the tile folder. The layout (Segregated or Integrated) is the only markup preference, stored as `markupIntegrated`.

Owner: `MapScreen` (input, colour, thickness, fill, current tool). Model: `client/ui/screens/map/markup/`.

## Segregated and Integrated

Both layouts use the same `MarkupModel`, the same tools, and the same keys. `MapScreen.markupMode` is the flag that turns the tools on. The layouts differ in when that flag becomes true and in where `MarkupToolbar` puts the buttons.

| | Segregated (`markupIntegrated = false`, the default) | Integrated (`markupIntegrated = true`) |
| --- | --- | --- |
| When tools exist | After a second press of the map key, or the pencil button | As soon as the map opens. `buildButtons` forces `markupMode` and selects Pan |
| Default tool | Pencil | Pan (also reapplied if you switch layout while the map is open) |
| Pencil button on the map | Shown, if Preferences has not hidden it | Never shown. Preferences greys out "Show Markup (pencil)" and the heading says the layout is always on |
| Second press of M | Enters markup (the opening press is ignored via `mapKeyArmed`) | Closes the map |
| Escape | Leaves markup, discards shapes, stays on the map. A second Escape closes the map | Closes the map. `markupKey` returns false for Escape so `UIScreen` handles it |
| Panel | Left side, under the top row: `MarkupToolbar.layout(4, 29, height - 4)`. Two columns, six rows, palette underneath. More columns if the screen is short | JourneyMap-style bar. Palette of 8×2 swatches centred. Left wing and right wing beside it |
| Where the bar sits | Fixed left panel | On the top row between the map buttons when it fits (`leftChromeEnd` / `rightChromeStart`); otherwise a second row; on a narrow screen the wings share that row and the palette drops under them |

`MarkupToolbar` is layout math only. `MapScreen.buildToolbar` turns each `Cell` into a `UIElementToolButton` (ids 300–314). Selected tools use the button's "active" callback. Undo is disabled when the stack is empty.

Segregated order (`MarkupToolbar.ORDER`), tool on the left of each row:

| Row | Left | Right |
| --- | --- | --- |
| 1 | Pencil | Pan |
| 2 | Line | Undo |
| 3 | Rectangle | Thickness |
| 4 | Ellipse | Fill |
| 5 | Shape eraser | Clear |
| 6 | Block eraser | Save |

Integrated wings (`LEFT_WING` / `RIGHT_WING`):

- Left: Pan, a 6 px gap, Pencil, Line, Rectangle, Ellipse, Shape eraser, Block eraser.
- Centre: the 16-colour palette.
- Right: Undo, Thickness, Fill, Clear, Save.

There is no close button on either layout (removed in mp16). Segregated closes with Escape or the pencil button. Integrated closes with Escape or M.

Switching the preference while the map is open is picked up the next time buttons are rebuilt. If the layout flag changed, the current stroke is cancelled and the tool resets to that layout's default.

## Tools

`MarkupTool` (`markup/MarkupTool.java`):

| Tool | Key | Stroke |
| --- | --- | --- |
| Pan | `` ` `` (the key left of 1, `Keyboard.KEY_GRAVE`) | Left drag pans. Spawner hover and click-to-copy still work, but only for Pan, and only then in Integrated (Segregated markup suppresses the spawner tooltip for every tool) |
| Pencil | 1 | One sample per block entered. Samples are joined with Bresenham so a fast drag stays solid |
| Line | 2 | Block-aligned. Shift constrains to 45 degrees. Length is labelled while drawing |
| Rectangle | 3 | Whole-block border, or every cell when Fill is on. Shift forces a square. Corner labels name the inclusive corner blocks and the size (`W x H` in blocks) |
| Ellipse | 4 | Symmetric midpoint ellipse on the block grid. Shift forces a circle |
| Shape eraser | 5 | Deletes every shape that contains a touched block. Brush size grows when you are zoomed out (`MarkupGeometry.shapeEraserBrush`) |
| Block eraser | 6 | Removes blocks from shapes, using the thickness as the brush. A shape left with no cells is deleted |

`0` does nothing. The hotkey label for Pan is `` ` ``, not `0` (mp17 moved it off the number row).

Also, while markup is on and Ctrl is not held:

| Key | Action |
| --- | --- |
| F | Toggle fill |
| `[` / `]` | Thickness 1, 2, 3, 5 blocks, and a toast |
| Ctrl+Z | Undo |
| Ctrl+S | Save a PNG (`MarkupExport`) |

Right drag still rotates and middle drag still pans with any tool. Those buttons are eaten by `markupMouseDown`, so they never fall through to waypoint creation.

## How a shape is stored

`MarkupShape` holds a type (`RECT`, `ELLIPSE`, `LINE`, `PENCIL`), the dimension it was drawn in, an `0xRRGGBB` colour, a thickness, a fill flag (fill is stored only for rect and ellipse), and a list of world-space points.

- Rect and ellipse: two corners. Edges are block-aligned; the max edge is exclusive. `cellBox()` is the inclusive block range.
- Line: two block centres.
- Pencil: the blocks the cursor entered.

`cells()` rasterises through `MarkupRaster` and subtracts anything the block eraser removed. The result is cached until the points change. `BlockCells` is a set of packed `(x, z)` longs. A shape never colours the same block twice, so the 0xA8 alpha (~66%) stays even where a thick stroke overlaps itself.

`MarkupRaster` is the pixel-art rasteriser: rectangle border or fill, midpoint ellipse, Bresenham line, and a square brush. `MarkupGeometry` is the gesture math: shift-square, shift-45° line, block snapping, and the export crop.

`MarkupModel` keeps the shapes and an undo stack capped at 200. Each edit pushes a closure: add, remove, clear-one-dimension, or one eraser drag. `erase` / `eraseShapes` record the stroke and `endErase` pushes a single undo step. `forDim` filters by the dimension the map was showing, so Overworld and Nether drawings can coexist until markup ends, and a save only writes the dimension on screen.

`discard` drops shapes and the undo stack. Segregated mode calls it from `exitMarkup` (Escape, or the pencil button) and stays on the same `MapScreen`. Closing the map, in either layout, does not call `exitMarkup`. The model is a field of that screen: Options and Help keep the screen as their parent, so shapes survive a trip to settings; closing back to the world drops the screen, and the next M constructs a new empty model. Integrated Escape takes that close path (`markupKey` does not consume it). Unsaved shapes are not written anywhere.

## Drawing and export

`MarkupRenderer.drawShapes` walks `markup.forDim(viewDim)` plus the in-progress shape. It projects each cell with `MapProjection.worldToScreen` and fills a square of `pixelsPerBlock` at alpha `CELL_ALPHA`. Rectangles also get the corner and size labels (bright text, shadow, near-opaque black box). Only cells on screen are submitted.

`MarkupExport.start` queues any unsaved tiles of the player's dimension, then on a worker thread stitches the visible area (or the markup bounds plus a margin) with `WorldMapImage` and paints the same cells and labels with `MarkupImage`. Output:

`.minecraft/screenshots/worldmap-markup-yyyy-MM-dd_HH.mm.ss.png`

North-up, at most 8 pixels per block, at most 4096 pixels on a side. With no shapes it saves the area on screen. Existing files are not overwritten (`_1`, `_2`, ...). Chat and the map status line report progress, same channel as the full-map export.
