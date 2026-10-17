package dev.natowb.natosatlas.client.ui.screens.map.markup;

import org.lwjgl.input.Keyboard;

import static dev.natowb.natosatlas.client.texture.TextureProvider.*;

/** mp9: markup toolbar tools (number keys 1-6 while markup mode is on). */
public enum MarkupTool {
    PAN("Pan (move the map)", ICON_TOOL_PAN, Keyboard.KEY_1),
    RECT("Rectangle (shows corner coords, Shift = square)", ICON_TOOL_RECT, Keyboard.KEY_2),
    ELLIPSE("Ellipse (Shift = circle)", ICON_TOOL_ELLIPSE, Keyboard.KEY_3),
    LINE("Line (Shift = 45 degree steps)", ICON_TOOL_LINE, Keyboard.KEY_4),
    PENCIL("Pencil", ICON_TOOL_PENCIL, Keyboard.KEY_5),
    ERASER("Eraser (click or drag over a shape)", ICON_TOOL_ERASER, Keyboard.KEY_6);

    public final String label;
    public final int icon;
    public final int key;

    MarkupTool(String label, int icon, int key) {
        this.label = label;
        this.icon = icon;
        this.key = key;
    }

    public String tooltip() {
        return label + " [" + (ordinal() + 1) + "]";
    }
}
