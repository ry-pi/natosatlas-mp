package dev.natowb.natosatlas.client.ui.screens.map;

/** mp9: dimension names, Overworld/Nether coordinate conversion and the world map's dimension toggle order. */
public final class MapDimensions {
    public static final int OVERWORLD = 0;
    public static final int NETHER = -1;

    private MapDimensions() {
    }

    public static String name(int dim) {
        switch (dim) {
            case OVERWORLD:
                return "Overworld";
            case NETHER:
                return "Nether";
            case 1:
                return "The End";
            default:
                return "DIM" + dim;
        }
    }

    /** Factor to multiply a coordinate in {@code fromDim} by to get the matching place in {@code toDim}, or 0 if there is none. */
    public static double scale(int fromDim, int toDim) {
        if (fromDim == toDim) return 1;
        if (fromDim == NETHER && toDim == OVERWORLD) return 8;
        if (fromDim == OVERWORLD && toDim == NETHER) return 1 / 8.0;
        return 0;
    }

    /** Dimensions the toggle cycles through: Overworld, Nether, and the player's own if it is another one. */
    public static int[] order(int playerDim) {
        if (playerDim == OVERWORLD || playerDim == NETHER) return new int[]{OVERWORLD, NETHER};
        return new int[]{playerDim, OVERWORLD, NETHER};
    }

    /** The dimension shown after pressing the toggle while {@code viewDim} is shown. */
    public static int next(int viewDim, int playerDim) {
        int[] o = order(playerDim);
        for (int i = 0; i < o.length; i++) {
            if (o[i] == viewDim) return o[(i + 1) % o.length];
        }
        return playerDim;
    }

    /** Whether the map of this dimension is drawn from the cave layer only (no sky): the Nether. */
    public static boolean hasCeiling(int dim) {
        return dim == NETHER;
    }
}
