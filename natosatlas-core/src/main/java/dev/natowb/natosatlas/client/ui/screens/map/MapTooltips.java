package dev.natowb.natosatlas.client.ui.screens.map;

import dev.natowb.natosatlas.client.ui.screens.map.markup.MarkupToolbar;

/**
 * mp12: every world map tooltip, in one format: {@code Name (other useful keys) [hotkey]}. The hotkey is always last in
 * square brackets; round brackets only hold extra keys (Shift, Ctrl, ...) and are left out when there are none.
 * No descriptions of what a button obviously does, and never the slime seed.
 */
public final class MapTooltips {
    private MapTooltips() {
    }

    public static String format(String name, String keys, String hotkey) {
        StringBuilder b = new StringBuilder(name);
        if (keys != null && !keys.isEmpty()) b.append(" (").append(keys).append(')');
        if (hotkey != null && !hotkey.isEmpty()) b.append(" [").append(hotkey).append(']');
        return b.toString();
    }

    // top-left
    public static final String CLOSE = format("Close", null, "Esc");
    public static final String OPTIONS = "Options";
    public static final String HELP = "Help";

    public static String markup(boolean on) {
        return on ? format("Close markup", null, "Esc") : format("Markup", null, "B");
    }

    // top-right
    public static final String WAYPOINTS = "Waypoints";
    public static final String SLIME = "Slime chunks";
    public static final String GRID = "Grid";

    /** mp14: Off / All / one mob type; clicking cycles. */
    public static String spawners(String filter) {
        return "Spawners: " + filter;
    }

    public static String layer(boolean on, int y) {
        return format(on ? "Layer view: Y " + y : "Layer view", null, "L");
    }

    public static final String LAYER_UP = format("Layer up", "Shift+wheel", "Up");
    public static final String LAYER_DOWN = format("Layer down", "Shift+wheel", "Down");

    public static String layerSlider(int y) {
        return format("Y " + y, "drag, Shift+wheel", "Up/Down");
    }

    public static String spawner(String mob, int x, int y, int z) {
        return mob + " spawner: " + x + " " + y + " " + z + " (click to copy)";
    }

    public static String entities(String mode) {
        return "Entities: " + mode;
    }

    public static String mapMode(String mode) {
        return "Map mode: " + mode;
    }

    public static final String MAP_MODE_DISABLED = "Map mode only applies in the Overworld";

    public static String dimension(int viewDim) {
        return format("Showing: " + MapDimensions.name(viewDim), null, "D");
    }

    // markup toolbar (tools: MarkupTool.tooltip())
    public static String fill(boolean filled) {
        return format(filled ? "Filled" : "Outline", null, "F");
    }

    public static String thickness(int blocks) {
        return format("Thickness: " + blocks + (blocks == 1 ? " block" : " blocks"), "[ = thinner", "]");
    }

    public static final String UNDO = format("Undo", null, "Ctrl+Z");
    public static final String CLEAR = "Clear all";
    public static final String SAVE = format("Save PNG", null, "Ctrl+S");
    public static final String CLOSE_MARKUP = format("Close markup", null, "Esc");

    public static String colour(int paletteIndex) {
        return MarkupToolbar.PALETTE_NAMES[paletteIndex];
    }
}
