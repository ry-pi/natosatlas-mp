package dev.natowb.natosatlas.client.ui.screens.map.markup;

import java.util.ArrayList;
import java.util.List;

/**
 * mp9: layout of the markup toolbar on the left of the world map, Paint.NET "Tools" panel style: a narrow column
 * of square icon buttons (2 wide, 3 or 4 when the screen is short), then the colour swatch and a 16 colour palette.
 * Pure layout math (unit tested); MapScreen turns the cells into the map's normal icon buttons.
 */
public final class MarkupToolbar {

    public static final int[] PALETTE = {
            0xFFFFFF, 0xA0A0A0, 0x505050, 0x000000,
            0xFF3030, 0xFF9020, 0xFFE030, 0x80FF30,
            0x20A030, 0x30E0E0, 0x50A0FF, 0x2040E0,
            0x9040E0, 0xFF40E0, 0xFFA0C0, 0x8A5A30};
    public static final int DEFAULT_COLOR = 0xFF3030;
    public static final int[] WIDTHS = {1, 2, 3, 5};

    public static final int CELL = 20, GAP = 2, PAD = 3, SECTION = 3, SWATCH = 9, SWATCH_GAP = 2;

    /** mp12: palette colour names (swatch tooltips). */
    public static final String[] PALETTE_NAMES = {
            "White", "Light grey", "Dark grey", "Black",
            "Red", "Orange", "Yellow", "Lime",
            "Green", "Cyan", "Light blue", "Blue",
            "Purple", "Magenta", "Pink", "Brown"};

    /** Things in the toolbar, in order. */
    public enum Item {
        TOOL_PAN, TOOL_RECT, TOOL_ELLIPSE, TOOL_LINE, TOOL_PENCIL, TOOL_ERASER_BLOCK, TOOL_ERASER_SHAPE,
        FILL, WIDTH, UNDO, CLEAR, SAVE
    }

    /** mp16: fixed order, two per row: tool on the left, its neighbour on the right (no close button). */
    public static final Item[] ORDER = {
            Item.TOOL_PENCIL, Item.TOOL_PAN,
            Item.TOOL_LINE, Item.UNDO,
            Item.TOOL_RECT, Item.WIDTH,
            Item.TOOL_ELLIPSE, Item.FILL,
            Item.TOOL_ERASER_SHAPE, Item.CLEAR,
            Item.TOOL_ERASER_BLOCK, Item.SAVE};

    public static final class Cell {
        public final Item item;
        public final int x, y, w, h;

        Cell(Item item, int x, int y, int w, int h) {
            this.item = item;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    /** mp17: Integrated (top bar) groups: Pan alone, then the drawing tools; options on the right of the palette. */
    public static final Item[] LEFT_WING = {Item.TOOL_PAN, Item.TOOL_PENCIL, Item.TOOL_LINE, Item.TOOL_RECT, Item.TOOL_ELLIPSE,
            Item.TOOL_ERASER_SHAPE, Item.TOOL_ERASER_BLOCK};
    public static final Item[] RIGHT_WING = {Item.UNDO, Item.WIDTH, Item.FILL, Item.CLEAR, Item.SAVE};
    /** mp17: space between Pan and Pencil, and between the three panels (left wing / colours / right wing). */
    public static final int PAN_GAP = 6, WING_GAP = 6;

    public int panelX, panelY, panelW, panelH, cols;
    public boolean integrated;
    /** Background panels {x, y, w, h}: one for Segregated, three for Integrated. */
    public final List<int[]> panels = new ArrayList<>();
    public final List<Cell> cells = new ArrayList<>();
    public final int[][] swatches = new int[PALETTE.length][]; // {x, y} per palette entry

    /** Lay the toolbar out at (x, y) so it ends above maxBottom (uses more columns if it has to). */
    public static MarkupToolbar layout(int x, int y, int maxBottom) {
        MarkupToolbar t = null;
        for (int cols = 2; cols <= 6; cols++) {
            t = new MarkupToolbar(x, y, cols);
            if (t.panelY + t.panelH <= maxBottom) break;
        }
        return t;
    }

    private MarkupToolbar() {
    }

    /** Width of a row of cells. */
    private static int rowW(int n) {
        return n * CELL + (n - 1) * GAP;
    }

    public static int leftWingW() {
        return rowW(LEFT_WING.length) + PAN_GAP + PAD * 2;
    }

    public static int rightWingW() {
        return rowW(RIGHT_WING.length) + PAD * 2;
    }

    public static int paletteW() {
        return 8 * SWATCH + 7 * SWATCH_GAP + PAD * 2;
    }

    /**
     * mp17: Integrated layout, JourneyMap style. The palette (8 x 2 swatches) is centred on the screen; the left wing
     * (Pan, gap, Pencil Line Rect Ellipse Shape-eraser Block-eraser) and right wing (Undo Thickness Fill Clear Save)
     * sit either side with a gap. On the top row between the map buttons when it fits (leftEnd / rightStart = edges of
     * the top-left / top-right button rows), else on a second row under them; on narrow screens the wings share the
     * second row (centred) and the palette goes centred under them.
     */
    public static MarkupToolbar integrated(int screenW, int leftEnd, int rightStart) {
        MarkupToolbar t = new MarkupToolbar();
        t.integrated = true;
        t.cols = 0;
        int cx = screenW / 2, ph = CELL + PAD * 2;
        int lw = leftWingW(), rw = rightWingW(), pw = paletteW();
        int palX = cx - pw / 2;
        int lx = palX - WING_GAP - lw, rx = palX + pw + WING_GAP;
        int y;
        if (lx >= leftEnd + 4 && rx + rw <= rightStart - 4) y = 2; // top row, between the map buttons
        else if (lx >= 2 && rx + rw <= screenW - 2) y = 28;        // second row
        else {                                                      // narrow: wings, then palette under them
            int total = lw + WING_GAP + rw;
            lx = cx - total / 2;
            rx = lx + lw + WING_GAP;
            t.wing(LEFT_WING, lx, 28, true);
            t.wing(RIGHT_WING, rx, 28, false);
            t.palette(palX, 28 + ph + WING_GAP);
            t.bounds();
            return t;
        }
        t.wing(LEFT_WING, lx, y, true);
        t.palette(palX, y);
        t.wing(RIGHT_WING, rx, y, false);
        t.bounds();
        return t;
    }

    private void wing(Item[] items, int px, int py, boolean panGap) {
        int x = px + PAD;
        for (int i = 0; i < items.length; i++) {
            cells.add(new Cell(items[i], x, py + PAD, CELL, CELL));
            x += CELL + GAP + (panGap && i == 0 ? PAN_GAP : 0);
        }
        panels.add(new int[]{px, py, x - GAP + PAD - px, CELL + PAD * 2});
    }

    private void palette(int px, int py) {
        for (int i = 0; i < PALETTE.length; i++) {
            int r = i / 8, c = i % 8;
            swatches[i] = new int[]{px + PAD + c * (SWATCH + SWATCH_GAP), py + PAD + r * (SWATCH + SWATCH_GAP)};
        }
        panels.add(new int[]{px, py, paletteW(), CELL + PAD * 2});
    }

    private void bounds() {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int[] p : panels) {
            x0 = Math.min(x0, p[0]); y0 = Math.min(y0, p[1]);
            x1 = Math.max(x1, p[0] + p[2]); y1 = Math.max(y1, p[1] + p[3]);
        }
        panelX = x0; panelY = y0; panelW = x1 - x0; panelH = y1 - y0;
    }

    private MarkupToolbar(int x, int y, int cols) {
        this.cols = cols;
        panelX = x;
        panelY = y;
        int inner = cols * CELL + (cols - 1) * GAP;
        panelW = inner + PAD * 2;
        int ix = x + PAD;
        int cy = y + PAD;
        // mp16: one grid in ORDER (6 rows of 2), every cell one icon wide; no close button (Esc / the markup button)
        cy = grid(ORDER, ix, cy, inner) + SECTION;
        int perRow = cols * 2; // two 9 px swatches per 20 px cell column: 2*9 + 2 = 20
        for (int i = 0; i < PALETTE.length; i++) {
            int r = i / perRow, c = i % perRow;
            swatches[i] = new int[]{ix + c * (SWATCH + SWATCH_GAP), cy + r * (SWATCH + SWATCH_GAP)};
        }
        int rows = (PALETTE.length + perRow - 1) / perRow;
        cy += rows * SWATCH + (rows - 1) * SWATCH_GAP;
        panelH = cy + PAD - y;
        panels.add(new int[]{panelX, panelY, panelW, panelH});
    }

    private int grid(Item[] items, int ix, int cy, int inner) {
        int slot = 0;
        for (int i = 0; i < items.length; i++, slot++) {
            int r = slot / cols, c = slot % cols;
            cells.add(new Cell(items[i], ix + c * (CELL + GAP), cy + r * (CELL + GAP), CELL, CELL));
        }
        int rows = (slot + cols - 1) / cols;
        return cy + rows * CELL + (rows - 1) * GAP;
    }

    public boolean inside(int mx, int my) {
        for (int[] p : panels) if (mx >= p[0] && mx < p[0] + p[2] && my >= p[1] && my < p[1] + p[3]) return true;
        return false;
    }

    /** Palette index under the mouse, or -1. */
    public int swatchAt(int mx, int my) {
        for (int i = 0; i < swatches.length; i++) {
            int[] s = swatches[i];
            if (mx >= s[0] && mx < s[0] + SWATCH && my >= s[1] && my < s[1] + SWATCH) return i;
        }
        return -1;
    }

    public static int nextWidth(int w) {
        for (int i = 0; i < WIDTHS.length; i++) if (WIDTHS[i] == w) return WIDTHS[(i + 1) % WIDTHS.length];
        return WIDTHS[0];
    }

    public static int prevWidth(int w) {
        for (int i = 0; i < WIDTHS.length; i++) if (WIDTHS[i] == w) return WIDTHS[(i + WIDTHS.length - 1) % WIDTHS.length];
        return WIDTHS[0];
    }

    public static int widthIcon(int w) {
        for (int i = 0; i < WIDTHS.length; i++) if (WIDTHS[i] == w) return dev.natowb.natosatlas.client.texture.TextureProvider.ICON_WIDTH_1 + i;
        return dev.natowb.natosatlas.client.texture.TextureProvider.ICON_WIDTH_1;
    }
}
