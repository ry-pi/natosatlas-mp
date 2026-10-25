package dev.natowb.natosatlas.client.ui.screens.settings;

/**
 * mp16: Preferences tab: which world map buttons are shown (default all). Hidden buttons are left out and the rest of
 * their row closes up. Stored in settings.txt as {@code showButton.<key>=true/false}.
 */
public enum MapButtons {
    // top-left, left to right
    CLOSE("close", "Close (X)", true),
    OPTIONS("options", "Options (gear)", true),
    HELP("help", "Help (?)", true),
    MARKUP("markup", "Markup (pencil)", true),
    // top-right, right to left (same names as the tooltips)
    WAYPOINTS("waypoints", "Waypoints", false),
    SLIME("slime", "Slime chunks", false),
    SPAWNERS("spawners", "Spawners", false),
    GRID("grid", "Grid", false),
    ENTITIES("entities", "Entities", false),
    MAP_MODE("mapMode", "Map mode", false),
    LAYER("layer", "Layer view", false),
    DIMENSION("dimension", "Dimension", false);

    public final String key, label;
    public final boolean topLeft;
    public boolean shown = true;

    MapButtons(String key, String label, boolean topLeft) {
        this.key = key;
        this.label = label;
        this.topLeft = topLeft;
    }

    /** Keyboard shortcut of a button's action (M markup, D dimension, L layer view): allowed if shown, or if the
     *  "Keybinds for hidden buttons" preference is on. */
    public boolean keyWorks() {
        return shown || Settings.hiddenButtonKeys;
    }
}
