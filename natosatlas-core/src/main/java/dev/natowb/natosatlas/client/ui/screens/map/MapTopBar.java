package dev.natowb.natosatlas.client.ui.screens.map;

import dev.natowb.natosatlas.client.ui.layout.UIHorizontalLayout;
import dev.natowb.natosatlas.client.ui.layout.UIPoint;

import java.util.EnumMap;
import java.util.Map;

/**
 * mp11: the world map's top-right button row, as pure layout (unit tested headless). Every slot exists in every
 * state, so no button ever moves: in the Nether the map mode button stays in its place, greyed out, instead of
 * disappearing (which used to shift the row and made it look like the entities button had vanished).
 */
public final class MapTopBar {
    private MapTopBar() {
    }

    /** Right to left. */
    public enum Slot {WAYPOINTS, SLIME, GRID, ENTITIES, MAP_MODE, DIMENSION}

    public static final int SIZE = 20, GAP = 5, CENTER_Y = 15;

    /** Top-left corner of every slot for a screen width (all slots, always). */
    public static Map<Slot, int[]> layout(int screenWidth) {
        UIHorizontalLayout l = new UIHorizontalLayout(screenWidth - 25, CENTER_Y, GAP, true);
        Map<Slot, int[]> m = new EnumMap<>(Slot.class);
        for (Slot s : Slot.values()) {
            UIPoint p = l.next(SIZE, SIZE);
            m.put(s, new int[]{p.x, p.y});
        }
        return m;
    }

    /**
     * Does the map currently show a dimension with a ceiling (the Nether)? foreign = the map shows another
     * dimension than the player's; playerWorldHasCeiling = the player's world has one.
     */
    public static boolean viewHasCeiling(boolean foreign, boolean playerWorldHasCeiling, int viewDim) {
        return foreign ? MapDimensions.hasCeiling(viewDim) : playerWorldHasCeiling;
    }

    /** Map mode (Day / Night / Cave / Auto) only applies in the Overworld; the Nether is always the cave layer. */
    public static boolean mapModeEnabled(boolean viewHasCeiling) {
        return !viewHasCeiling;
    }

    public static final String MAP_MODE_DISABLED_TOOLTIP =
            "Map mode only applies in the Overworld";

    /** Icon tint of the greyed-out map mode button. */
    public static final int DISABLED_ICON_COLOR = 0xFFC8C8C8; // the cave icon is dark already; the flat disabled button shows the state

    public static String mapModeTooltip(boolean viewHasCeiling, String modeName) {
        return viewHasCeiling ? MAP_MODE_DISABLED_TOOLTIP : "Mode: " + modeName;
    }
}
