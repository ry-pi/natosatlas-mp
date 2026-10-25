package dev.natowb.natosatlas.client.ui.screens.map.markup;

import dev.natowb.natosatlas.client.ui.screens.map.MapTooltips;
import org.lwjgl.input.Keyboard;

import static dev.natowb.natosatlas.client.texture.TextureProvider.*;

/** Markup toolbar tools (number keys 1-7 while markup mode is on). */
public enum MarkupTool {
    PAN("Pan", null, ICON_TOOL_PAN, Keyboard.KEY_0),
    RECT("Rectangle", "Shift = square", ICON_TOOL_RECT, Keyboard.KEY_3),
    ELLIPSE("Ellipse", "Shift = circle", ICON_TOOL_ELLIPSE, Keyboard.KEY_4),
    LINE("Line", "Shift = 45 degrees", ICON_TOOL_LINE, Keyboard.KEY_2),
    PENCIL("Pencil", null, ICON_TOOL_PENCIL, Keyboard.KEY_1),
    ERASER_BLOCK("Block eraser", null, ICON_TOOL_ERASER_BLOCK, Keyboard.KEY_6), // mp11 eraser: removes blocks
    ERASER_SHAPE("Shape eraser", null, ICON_TOOL_ERASER_SHAPE, Keyboard.KEY_5); // mp9 eraser: removes whole shapes

    public final String label;
    public final String keys;
    public final int icon;
    public final int key;

    MarkupTool(String label, String keys, int icon, int key) {
        this.label = label;
        this.keys = keys;
        this.icon = icon;
        this.key = key;
    }

    public boolean isEraser() {
        return this == ERASER_BLOCK || this == ERASER_SHAPE;
    }

    /** mp16: number row key as shown, e.g. "0" for pan (Keyboard.KEY_1..KEY_9 = 2..10, KEY_0 = 11). */
    public String hotkey() {
        return key == Keyboard.KEY_0 ? "0" : String.valueOf(key - Keyboard.KEY_1 + 1);
    }

    /** "Name (modifier keys) [hotkey]". */
    public String tooltip() {
        return MapTooltips.format(label, keys, hotkey());
    }
}
