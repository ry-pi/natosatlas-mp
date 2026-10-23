package dev.natowb.natosatlas.client.ui.screens.map;

/**
 * mp14: the Layer view's vertical Y slider at the right edge, under the top-right buttons: [+] button, track
 * (Y 127 at the top, 0 at the bottom), [-] button. Pure layout and maths (unit tested headless).
 */
public final class MapLayerSlider {
    private MapLayerSlider() {}

    public static final int BUTTON = 20, TRACK_W = 8;

    /** {x, y} of the up (+) button. */
    public static int[] upButton(int w, int h) { return new int[]{w - 25, 32}; }

    /** {x, y} of the down (-) button. */
    public static int[] downButton(int w, int h) { return new int[]{w - 25, h - 52}; }

    /** {x0, y0, x1, y1} of the track. */
    public static int[] track(int w, int h) {
        int cx = w - 25 + BUTTON / 2;
        return new int[]{cx - TRACK_W / 2, 32 + BUTTON + 6, cx + TRACK_W / 2, h - 52 - 6};
    }

    public static int handleY(int w, int h, int y) {
        int[] t = track(w, h);
        return t[3] - Math.round((t[3] - t[1]) * y / 127f);
    }

    public static int yAt(int w, int h, int mouseY) {
        int[] t = track(w, h);
        float f = (t[3] - mouseY) / (float) Math.max(1, t[3] - t[1]);
        return Math.max(0, Math.min(127, Math.round(f * 127)));
    }

    /** Over the track (with a few pixels of slack for the handle)? */
    public static boolean onTrack(int w, int h, int mx, int my) {
        int[] t = track(w, h);
        return mx >= t[0] - 6 && mx <= t[2] + 6 && my >= t[1] - 4 && my <= t[3] + 4;
    }
}
