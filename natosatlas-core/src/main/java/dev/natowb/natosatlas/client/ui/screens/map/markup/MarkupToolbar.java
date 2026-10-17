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

    public static final int CELL = 20, GAP = 2, PAD = 3, TITLE = 10, SECTION = 3, SWATCH = 9, SWATCH_GAP = 2, BAR = 10;

    /** Things in the toolbar, in order. */
    public enum Item {
        TOOL_PAN, TOOL_RECT, TOOL_ELLIPSE, TOOL_LINE, TOOL_PENCIL, TOOL_ERASER,
        FILL, WIDTH, UNDO, CLEAR, SAVE, EXIT
    }

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

    public int panelX, panelY, panelW, panelH, cols;
    public int titleY;
    public int barX, barY, barW;
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

    private MarkupToolbar(int x, int y, int cols) {
        this.cols = cols;
        panelX = x;
        panelY = y;
        int inner = cols * CELL + (cols - 1) * GAP;
        panelW = inner + PAD * 2;
        int ix = x + PAD;
        int cy = y + PAD;
        titleY = cy + 1;
        cy += TITLE;
        Item[] tools = {Item.TOOL_PAN, Item.TOOL_RECT, Item.TOOL_ELLIPSE, Item.TOOL_LINE, Item.TOOL_PENCIL, Item.TOOL_ERASER};
        Item[] opts = {Item.FILL, Item.WIDTH, Item.UNDO, Item.CLEAR, Item.SAVE, Item.EXIT};
        cy = grid(tools, ix, cy) + SECTION;
        cy = grid(opts, ix, cy) + SECTION;
        barX = ix;
        barY = cy;
        barW = inner;
        cy += BAR + 3;
        int perRow = cols * 2; // two 9 px swatches per 20 px cell column: 2*9 + 2 = 20
        for (int i = 0; i < PALETTE.length; i++) {
            int r = i / perRow, c = i % perRow;
            swatches[i] = new int[]{ix + c * (SWATCH + SWATCH_GAP), cy + r * (SWATCH + SWATCH_GAP)};
        }
        int rows = (PALETTE.length + perRow - 1) / perRow;
        cy += rows * SWATCH + (rows - 1) * SWATCH_GAP;
        panelH = cy + PAD - y;
    }

    private int grid(Item[] items, int ix, int cy) {
        for (int i = 0; i < items.length; i++) {
            int r = i / cols, c = i % cols;
            cells.add(new Cell(items[i], ix + c * (CELL + GAP), cy + r * (CELL + GAP), CELL, CELL));
        }
        int rows = (items.length + cols - 1) / cols;
        return cy + rows * CELL + (rows - 1) * GAP;
    }

    public boolean inside(int mx, int my) {
        return mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH;
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
